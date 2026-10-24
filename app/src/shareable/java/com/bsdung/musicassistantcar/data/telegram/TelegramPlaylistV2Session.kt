package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.OpenResult
import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.ToggleOutcome

/** Code-level switch for the V2 playlist. Off = the P0 (V1-only) session, V1/V2 files untouched. */
internal object TelegramPlaylistFlags {
    const val V2_ENABLED = true

    /**
     * E96 P5B3: false for EVERY standard Debug/Release build. Only the separately requested
     * -PmeloSyncQa=true Debug QA build may reach the user-consent and TDLib sync gates.
     * Release BuildConfig hardcodes false; this flag alone NEVER grants per-account consent.
     */
    val SYNC_ENABLED: Boolean get() = com.bsdung.musicassistantcar.BuildConfig.MELO_SYNC_QA_BUILD
}

/** What the UI knows about the song the user taps. [chatId]/[rawMessageId] are 0 when unknown. */
internal data class PlaylistTarget(val songKey: Long, val chatId: Long = 0L, val rawMessageId: Long = 0L)

/** Maps UI keys (songKey) back to (chatId, rawMessageId) using the library index. May throw; callers treat that as "unavailable". */
internal interface PlaylistIndex {
    fun resolveAll(account: TelegramUserId, keys: Collection<Long>): Map<Long, PlaylistItemId.Telegram>
}

/** The one authoritative playlist of the signed-in account, as the UI sees it. */
internal interface PlaylistSessionApi {
    fun open(account: TelegramUserId): OpenResult
    fun close()
    fun snapshot(): List<Long>
    fun toggle(account: TelegramUserId, target: PlaylistTarget): ToggleOutcome
    fun attachIndex(index: PlaylistIndex)
    fun detachIndex(index: PlaylistIndex)
    /** Gives legacy keys their real (chat, message) once the index can tell. Returns true if something changed. */
    fun resolveLegacyKeys(account: TelegramUserId): Boolean
    /** E96 P4: counts for the status line, from the committed state (V1-only sessions have none). Call from the playlist thread. */
    fun statusFacts(account: TelegramUserId): PlaylistStatusFacts? = null
}

/** Why importing the changes an older app made to V1 was put off. Nothing is lost: V1 and V2 are left as they were. */
internal enum class PlaylistWarning {
    /** The sync queue already holds its maximum of waiting operations, so the new songs cannot be queued yet. */
    IMPORT_DEFERRED_OUTBOX_FULL,
    /** The merged playlist could not be written (disk or encryption error). */
    IMPORT_NOT_SAVED,
}

/** Pure rules for reading a V1 file that something else changed after we last wrote it. */
internal object PlaylistV1Reconciler {
    sealed class Outcome {
        data class Merged(val state: PlaylistState, val imported: Int, val skipped: Int, val ignoredRemovals: Int) : Outcome()

        /** Not even one song was imported: it is all or nothing, so V1 can safely stay as it is until later. */
        object OutboxFull : Outcome()
    }

    /**
     * [current] is what the V1 file holds now. Songs the old app added are imported as new additions.
     * A song V2 has removed (or ever knew and removed) is never brought back. Songs the old app took
     * out of V1 are NOT removed from V2: losing a song is worse than showing one the user already
     * deleted, so those are only counted. If the queue of waiting operations has no room for the
     * additions, nothing at all is imported ([Outcome.OutboxFull]); the caller must then leave V1 alone.
     */
    fun reconcile(
        state: PlaylistState,
        current: List<Long>,
        selfChatId: Long,
        resolved: Map<Long, PlaylistItemId.Telegram>,
    ): Outcome {
        val present = state.presentSongKeys(selfChatId).toSet()
        val known = state.orSet.knownItems().map { it.songKey(selfChatId) }.toSet()
        var next = state
        var imported = 0
        var skipped = 0
        for (key in current) {
            if (key in present) continue
            if (key in known || key in state.legacyRemoved) {
                skipped++
                continue
            }
            val item = resolved[key]
            next = try {
                if (item != null) next.addTelegram(item).state else next.addLegacy(key)
            } catch (_: OutboxFullException) {
                return Outcome.OutboxFull
            }
            imported++
        }
        val window = TelegramPlaylistV2Session.projectionIds(state, selfChatId).toSet()
        val ignored = window.count { it !in current.toSet() }
        return Outcome.Merged(next, imported, skipped, ignored)
    }
}

/**
 * Playlist V2 on top of two stores. V2 is the source of truth; V1 is a copy for older APKs.
 *
 * Crash safety (each line is one durable step; a kill between any two is recovered on the next [open]):
 *  1. V1 untouched during migration.  2. V2 written with migration=IN_PROGRESS.  3. V2 read back and
 *  compared with V1.  4. V2 rewritten with migration=COMPLETE.  After that, every change is
 *  (a) V2 state + outbox in ONE atomic file write, then (b) the V1 copy. V1 and V2 are two files, so
 *  (a) and (b) are NOT one transaction; the digests in the V2 state tell the next start which
 *  of them is missing.
 */
internal class TelegramPlaylistV2Session(
    private val v1: PlaylistPersistence,
    private val v2: PlaylistStoreV2,
    private val newDeviceId: () -> String,
) : PlaylistSessionApi, PlaylistSyncHost {
    private var user: TelegramUserId? = null
    private var state: PlaylistState? = null
    private var blocked: PlaylistUnreadableReason? = null
    private var index: PlaylistIndex? = null

    /** Changes every time an account is opened or closed, so a late result of an earlier one can be recognised. */
    private var epoch: Long = 0L

    /** Set while changes an older app made to V1 are waiting to be imported: V1 must not be replaced meanwhile. */
    private var importDeferred: PlaylistWarning? = null

    @Synchronized
    override fun attachIndex(index: PlaylistIndex) {
        this.index = index
    }

    @Synchronized
    override fun detachIndex(index: PlaylistIndex) {
        if (this.index === index) this.index = null
    }

    @Synchronized
    override fun open(account: TelegramUserId): OpenResult {
        epoch++
        user = account
        state = null
        blocked = null
        importDeferred = null
        // Whatever goes wrong while opening must come back as an answer the screen can show, never as a
        // swallowed exception. Nothing has been written at the point an unexpected error can occur here.
        return try {
            load(account)
        } catch (_: Exception) {
            block(PlaylistUnreadableReason.IO)
        }
    }

    @Synchronized
    override fun close() {
        epoch++
        user = null
        state = null
        blocked = null
        importDeferred = null
    }

    @Synchronized
    override fun snapshot(): List<Long> = state?.presentSongKeys(user?.value ?: 0L).orEmpty()

    // ---- open / migrate ------------------------------------------------------------------------

    private fun load(account: TelegramUserId): OpenResult = when (val stored = v2.load(account)) {
        is V2LoadResult.Unreadable -> block(stored.reason)
        is V2LoadResult.Missing -> migrate(account)
        is V2LoadResult.Valid -> adopt(account, stored.state)
    }

    private fun block(reason: PlaylistUnreadableReason): OpenResult {
        state = null
        blocked = reason
        return OpenResult.Blocked(reason)
    }

    private fun ready(account: TelegramUserId, s: PlaylistState, warning: PlaylistWarning? = null): OpenResult {
        state = s
        blocked = null
        importDeferred = warning
        return OpenResult.Ready(s.presentSongKeys(account.value), warning)
    }

    private fun adopt(account: TelegramUserId, stored: PlaylistState): OpenResult {
        var s = stored
        if (s.migration.status != MigrationStatus.COMPLETE) s = completeMigration(account, s) ?: return block(PlaylistUnreadableReason.FORMAT)
        val pass = reconcileProjection(account, s)
        return ready(account, pass.state, pass.warning)
    }

    /** The playlist after looking at the V1 copy, and whether importing the old app's changes had to wait. */
    private class Pass(val state: PlaylistState, val warning: PlaylistWarning? = null)

    /** V2 exists but its migration was never marked complete (killed after step 2). V1 is only read. */
    private fun completeMigration(account: TelegramUserId, s: PlaylistState): PlaylistState? {
        val v1Now = v1.load(account)
        if (v1Now is PlaylistLoadResult.Ok &&
            v1Now.ids != s.presentSongKeys(account.value) &&
            TelegramPlaylistCodec.listDigest(v1Now.ids) == s.migration.v1Digest
        ) {
            return null // V1 is what the migration read, yet V2 does not show it: never claim success
        }
        val done = s.copy(migration = s.migration.copy(status = MigrationStatus.COMPLETE))
        return if (trySave(account, done)) done else s
    }

    private fun migrate(account: TelegramUserId): OpenResult {
        val ids = when (val old = v1.load(account)) {
            is PlaylistLoadResult.Unreadable -> return block(old.reason) // stop; V1 and V2 untouched
            is PlaylistLoadResult.Missing -> emptyList()
            is PlaylistLoadResult.Ok -> old.ids
        }
        val self = account.value
        val resolved = resolveKeys(account, ids)
        var s = PlaylistState.empty(self, newDeviceId())
        for (key in ids) {
            val item = resolved[key]
            s = if (item != null) s.addTelegram(item).state else s.addLegacy(key)
        }
        val digest = TelegramPlaylistCodec.listDigest(ids)
        s = s.copy(
            migration = MigrationRecord(MigrationStatus.IN_PROGRESS, digest, ids.size),
            projection = ProjectionRecord(digest, digest, ProjectionStatus.IN_SYNC, false, 0, 0, 0),
        )
        if (s.presentSongKeys(self) != ids) return block(PlaylistUnreadableReason.FORMAT)
        if (!trySave(account, s)) return block(PlaylistUnreadableReason.IO)
        val back = v2.load(account)
        if (back !is V2LoadResult.Valid || back.state.presentSongKeys(self) != ids) return block(PlaylistUnreadableReason.FORMAT)
        val done = back.state.copy(migration = back.state.migration.copy(status = MigrationStatus.COMPLETE))
        return ready(account, if (trySave(account, done)) done else back.state)
    }

    private fun trySave(account: TelegramUserId, s: PlaylistState): Boolean = try {
        v2.save(account, s)
        true
    } catch (_: Exception) {
        false
    }

    // ---- the V1 copy ---------------------------------------------------------------------------

    /** Compares the V1 file with what we last put there and repairs or reconciles it. V1 unreadable is left alone. */
    private fun reconcileProjection(account: TelegramUserId, s: PlaylistState): Pass {
        val self = account.value
        val expected = projectionIds(s, self)
        val expectedDigest = TelegramPlaylistCodec.listDigest(expected)
        val truncated = s.presentSongKeys(self).size > TelegramPlaylistPolicy.MAX_IDS
        val onDisk = v1.load(account)
        if (onDisk is PlaylistLoadResult.Unreadable) return Pass(withProjection(s, ProjectionStatus.STALE, truncated))
        val cur = (onDisk as? PlaylistLoadResult.Ok)?.ids.orEmpty()
        val curDigest = TelegramPlaylistCodec.listDigest(cur)
        val p = s.projection
        return when {
            onDisk is PlaylistLoadResult.Ok && curDigest == expectedDigest ->
                Pass(s.copy(projection = p.copy(lastDigest = curDigest, pendingDigest = curDigest, status = ProjectionStatus.IN_SYNC, truncated = truncated)))
            onDisk is PlaylistLoadResult.Missing || curDigest == p.lastDigest || curDigest == p.pendingDigest ->
                Pass(writeProjection(account, s.copy(projection = p.copy(truncated = truncated))))
            else -> reconcileDivergence(account, s, cur, truncated)
        }
    }

    /**
     * V1 holds something neither we nor our last write put there. Import it into V2 first (V2 saved,
     * then V1 replaced). If that cannot be done now - the sync queue is full, or V2 cannot be written -
     * BOTH files stay exactly as they were, the stored playlist is still shown, and the caller is told
     * to warn the user; the next start tries again from the same V1.
     */
    private fun reconcileDivergence(account: TelegramUserId, s: PlaylistState, cur: List<Long>, truncated: Boolean): Pass {
        val self = account.value
        val result = when (val outcome = PlaylistV1Reconciler.reconcile(s, cur, self, resolveKeys(account, cur))) {
            is PlaylistV1Reconciler.Outcome.OutboxFull -> return Pass(s, PlaylistWarning.IMPORT_DEFERRED_OUTBOX_FULL)
            is PlaylistV1Reconciler.Outcome.Merged -> outcome
        }
        val merged = result.state.copy(
            projection = s.projection.copy(
                lastDigest = TelegramPlaylistCodec.listDigest(cur),
                pendingDigest = TelegramPlaylistCodec.listDigest(projectionIds(result.state, self)),
                status = ProjectionStatus.DIVERGED_ACK,
                truncated = truncated,
                conflictImported = s.projection.conflictImported + result.imported,
                conflictSkipped = s.projection.conflictSkipped + result.skipped,
                conflictIgnoredRemovals = s.projection.conflictIgnoredRemovals + result.ignoredRemovals,
            )
        )
        // Commit the merged V2 first; only then is V1 (a copy) replaced, so nothing the old app added is lost.
        if (!trySave(account, merged)) return Pass(s, PlaylistWarning.IMPORT_NOT_SAVED)
        return Pass(writeProjection(account, merged))
    }

    /** Writes the V1 copy. A failure never touches the committed V2; it only marks the copy stale (in memory). */
    private fun writeProjection(account: TelegramUserId, s: PlaylistState): PlaylistState {
        val expected = projectionIds(s, account.value)
        val digest = TelegramPlaylistCodec.listDigest(expected)
        return try {
            v1.save(account, expected)
            s.copy(
                projection = s.projection.copy(
                    lastDigest = digest, pendingDigest = digest,
                    status = if (s.projection.status == ProjectionStatus.DIVERGED_ACK) ProjectionStatus.DIVERGED_ACK else ProjectionStatus.IN_SYNC,
                )
            )
        } catch (_: Exception) {
            s.copy(projection = s.projection.copy(pendingDigest = digest, status = ProjectionStatus.STALE))
        }
    }

    private fun withProjection(s: PlaylistState, status: ProjectionStatus, truncated: Boolean) =
        s.copy(projection = s.projection.copy(status = status, truncated = truncated))

    // ---- toggle --------------------------------------------------------------------------------

    @Synchronized
    override fun toggle(account: TelegramUserId, target: PlaylistTarget): ToggleOutcome {
        if (user != account) return ToggleOutcome.NotOpen
        if (state == null) {
            if (blocked == null) return ToggleOutcome.NotOpen
            // A read error can be transient (Keystore not ready yet). Look again, never write blindly.
            when (val again = load(account)) {
                is OpenResult.Blocked -> return ToggleOutcome.Blocked(again.reason)
                is OpenResult.Ready -> Unit
            }
        }
        val current = state ?: return ToggleOutcome.NotOpen
        val self = account.value
        val key = target.songKey
        val present = current.presentSongKeys(self)
        val next = try {
            if (key in present) {
                val item = current.telegramItemFor(key, self)
                if (item != null) current.removeTelegram(item).state else current.removeLegacy(key)
            } else {
                if (present.size >= TelegramPlaylistPolicy.MAX_IDS) return ToggleOutcome.Full(present.size)
                val item = telegramTarget(account, target)
                if (item != null) current.addTelegram(item).state else current.addLegacy(key)
            }
        } catch (_: OutboxFullException) {
            return ToggleOutcome.OutboxFull
        }
        val newPresent = next.presentSongKeys(self)
        val toSave = next.copy(
            projection = next.projection.copy(
                pendingDigest = TelegramPlaylistCodec.listDigest(projectionIds(next, self)),
                truncated = newPresent.size > TelegramPlaylistPolicy.MAX_IDS,
            )
        )
        try {
            v2.save(account, toSave)
        } catch (e: PlaylistUnreadableException) {
            state = null
            blocked = e.reason
            return ToggleOutcome.Blocked(e.reason)
        } catch (_: Exception) {
            return ToggleOutcome.Failed
        }
        // While the old app's changes to V1 still wait to be imported, V1 is not replaced: they would be lost.
        state = if (importDeferred == null) writeProjection(account, toSave) else toSave
        return if (key in newPresent) ToggleOutcome.Added(newPresent) else ToggleOutcome.Removed(newPresent)
    }

    private fun telegramTarget(account: TelegramUserId, target: PlaylistTarget): PlaylistItemId.Telegram? {
        val self = account.value
        if (target.chatId != 0L && target.rawMessageId > 0L &&
            TelegramSongKey.forMessage(target.chatId, target.rawMessageId, self) == target.songKey
        ) return PlaylistItemId.Telegram(target.chatId, target.rawMessageId)
        return resolveKeys(account, listOf(target.songKey))[target.songKey]
    }

    /** Index answers that agree with the key; any failure or disagreement means "unknown", never a guess. */
    private fun resolveKeys(account: TelegramUserId, keys: Collection<Long>): Map<Long, PlaylistItemId.Telegram> {
        val idx = index ?: return emptyMap()
        if (keys.isEmpty()) return emptyMap()
        val answers = try {
            idx.resolveAll(account, keys.distinct())
        } catch (_: Exception) {
            return emptyMap()
        }
        return answers.filter { (key, item) -> item.songKey(account.value) == key }
    }

    @Synchronized
    override fun resolveLegacyKeys(account: TelegramUserId): Boolean {
        val current = state ?: return false
        if (user != account || current.legacy.isEmpty()) return false
        val resolved = resolveKeys(account, current.legacy.map { it.songKey })
        if (resolved.isEmpty()) return false
        var next = current
        var changed = false
        for (entry in current.legacy) {
            val item = resolved[entry.songKey] ?: continue
            next = try {
                next.upgradeLegacy(entry, item).state
            } catch (_: OutboxFullException) {
                continue
            }
            changed = true
        }
        if (!changed) return false
        if (!trySave(account, next)) return false
        state = next
        return true
    }

    @Synchronized
    override fun statusFacts(account: TelegramUserId): PlaylistStatusFacts? {
        if (user != account) return null
        state?.let { return PlaylistStatusFacts.of(it, importDeferred) }
        return blocked?.let { PlaylistStatusFacts.unreadable(account.value, it) }
    }

    // ---- sync host (E96 P2) ---------------------------------------------------------------------

    @Synchronized
    override fun view(account: TelegramUserId): SyncView? {
        if (user != account) return null
        return state?.let { SyncView(epoch, it) }
    }

    /**
     * Runs [transform] on the current state and stores the result in ONE V2 write before adopting it.
     * Refuses (changing nothing) if the session is not the one the caller looked at. The V1 copy is
     * brought along afterwards unless the old app's changes are still waiting to be imported.
     */
    @Synchronized
    override fun update(account: TelegramUserId, epoch: Long, transform: (PlaylistState) -> PlaylistState): UpdateResult {
        if (user != account || this.epoch != epoch) return UpdateResult.Stale
        val current = state ?: return UpdateResult.NotOpen
        val next = try {
            transform(current)
        } catch (_: Exception) {
            return UpdateResult.Failed
        }
        if (next == current) return UpdateResult.Committed(current)
        val self = account.value
        val toSave = next.copy(
            projection = next.projection.copy(
                pendingDigest = TelegramPlaylistCodec.listDigest(projectionIds(next, self)),
                truncated = next.presentSongKeys(self).size > TelegramPlaylistPolicy.MAX_IDS,
            )
        )
        try {
            v2.save(account, toSave)
        } catch (e: PlaylistUnreadableException) {
            state = null
            blocked = e.reason
            return UpdateResult.Failed
        } catch (_: Exception) {
            return UpdateResult.Failed
        }
        state = if (importDeferred == null) writeProjection(account, toSave) else toSave
        return UpdateResult.Committed(state!!)
    }

    companion object {
        /** What the V1 copy holds: the 300 most recently added songs. Older APKs cut at 300 and would show the oldest otherwise. */
        fun projectionIds(s: PlaylistState, selfChatId: Long): List<Long> =
            s.presentSongKeys(selfChatId).takeLast(TelegramPlaylistPolicy.MAX_IDS)
    }
}
