package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.OpenResult
import com.bsdung.musicassistantcar.data.telegram.TelegramPlaylistSession.ToggleOutcome

/** Code-level switch for the V2 playlist. Off = the P0 (V1-only) session, V1/V2 files untouched. */
internal object TelegramPlaylistFlags {
    const val V2_ENABLED = true
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
}

/** Pure rules for reading a V1 file that something else changed after we last wrote it. */
internal object PlaylistV1Reconciler {
    data class Result(val state: PlaylistState, val imported: Int, val skipped: Int, val ignoredRemovals: Int)

    /**
     * [current] is what the V1 file holds now. Songs the old app added are imported as new additions.
     * A song V2 has removed (or ever knew and removed) is never brought back. Songs the old app took
     * out of V1 are NOT removed from V2: losing a song is worse than showing one the user already
     * deleted, so those are only counted.
     */
    fun reconcile(
        state: PlaylistState,
        current: List<Long>,
        selfChatId: Long,
        resolved: Map<Long, PlaylistItemId.Telegram>,
    ): Result {
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
            next = if (item != null) next.addTelegram(item).state else next.addLegacy(key)
            imported++
        }
        val window = TelegramPlaylistV2Session.projectionIds(state, selfChatId).toSet()
        val ignored = window.count { it !in current.toSet() }
        return Result(next, imported, skipped, ignored)
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
) : PlaylistSessionApi {
    private var user: TelegramUserId? = null
    private var state: PlaylistState? = null
    private var blocked: PlaylistUnreadableReason? = null
    private var index: PlaylistIndex? = null

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
        user = account
        state = null
        blocked = null
        return load(account)
    }

    @Synchronized
    override fun close() {
        user = null
        state = null
        blocked = null
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

    private fun ready(account: TelegramUserId, s: PlaylistState): OpenResult {
        state = s
        blocked = null
        return OpenResult.Ready(s.presentSongKeys(account.value))
    }

    private fun adopt(account: TelegramUserId, stored: PlaylistState): OpenResult {
        var s = stored
        if (s.migration.status != MigrationStatus.COMPLETE) s = completeMigration(account, s) ?: return block(PlaylistUnreadableReason.FORMAT)
        return ready(account, reconcileProjection(account, s))
    }

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
    private fun reconcileProjection(account: TelegramUserId, s: PlaylistState): PlaylistState {
        val self = account.value
        val expected = projectionIds(s, self)
        val expectedDigest = TelegramPlaylistCodec.listDigest(expected)
        val truncated = s.presentSongKeys(self).size > TelegramPlaylistPolicy.MAX_IDS
        val onDisk = v1.load(account)
        if (onDisk is PlaylistLoadResult.Unreadable) return withProjection(s, ProjectionStatus.STALE, truncated)
        val cur = (onDisk as? PlaylistLoadResult.Ok)?.ids.orEmpty()
        val curDigest = TelegramPlaylistCodec.listDigest(cur)
        val p = s.projection
        return when {
            onDisk is PlaylistLoadResult.Ok && curDigest == expectedDigest ->
                s.copy(projection = p.copy(lastDigest = curDigest, pendingDigest = curDigest, status = ProjectionStatus.IN_SYNC, truncated = truncated))
            onDisk is PlaylistLoadResult.Missing || curDigest == p.lastDigest || curDigest == p.pendingDigest ->
                writeProjection(account, s.copy(projection = p.copy(truncated = truncated)))
            else -> reconcileDivergence(account, s, cur, truncated)
        }
    }

    private fun reconcileDivergence(account: TelegramUserId, s: PlaylistState, cur: List<Long>, truncated: Boolean): PlaylistState {
        val self = account.value
        val result = PlaylistV1Reconciler.reconcile(s, cur, self, resolveKeys(account, cur))
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
        if (!trySave(account, merged)) return s
        return writeProjection(account, merged)
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
        state = writeProjection(account, toSave)
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

    companion object {
        /** What the V1 copy holds: the 300 most recently added songs. Older APKs cut at 300 and would show the oldest otherwise. */
        fun projectionIds(s: PlaylistState, selfChatId: Long): List<Long> =
            s.presentSongKeys(selfChatId).takeLast(TelegramPlaylistPolicy.MAX_IDS)
    }
}
