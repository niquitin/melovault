package com.bsdung.musicassistantcar.data.telegram

/** Why opening the named playlists gave an answer: a screen can always show it, nothing is swallowed. */
internal sealed class NamedOpenResult {
    data class Ready(val lists: List<NamedListView>) : NamedOpenResult()

    /** The file exists but cannot be trusted. It is left byte-for-byte alone and every write is refused. */
    data class Blocked(val reason: PlaylistUnreadableReason) : NamedOpenResult()
}

/** What happened to one change asked of the session. Nothing is cut or dropped silently. */
internal sealed class NamedOutcome {
    /** Done and stored. [changed] is false when there was nothing to do (already in, already named so, ...). */
    data class Ok(val listId: ListId? = null, val changed: Boolean = true) : NamedOutcome()

    /** [limit] is [NamedRefusal.LIST_LIMIT] or [NamedRefusal.ITEM_LIMIT]. */
    data class Full(val limit: NamedRefusal) : NamedOutcome()

    /** The sync queue already holds its maximum of waiting changes. */
    object OutboxFull : NamedOutcome()
    data class Blocked(val reason: PlaylistUnreadableReason) : NamedOutcome()

    /** No readable named-playlist session is open for that account. */
    object NotOpen : NamedOutcome()

    /** The change could not be written. The stored and the in-memory playlists are as before. */
    object Failed : NamedOutcome()
    object DuplicateName : NamedOutcome()

    /** [why] is one of BAD_NAME, NO_SUCH_LIST, NO_SUCH_ITEM. */
    data class Refused(val why: NamedRefusal) : NamedOutcome()
}

internal data class NamedSyncView(val epoch: Long, val state: NamedPlaylistState)

internal sealed class NamedUpdateResult {
    data class Committed(val state: NamedPlaylistState) : NamedUpdateResult()

    /** The account was switched or closed since [NamedSyncView] was taken: nothing was changed. */
    object Stale : NamedUpdateResult()
    object NotOpen : NamedUpdateResult()

    /** The change could not be written. The stored and the in-memory playlists are as before. */
    object Failed : NamedUpdateResult()
}

/** Same contract as [PlaylistSyncHost], for the named playlists: look, then change only if nothing was switched meanwhile. */
internal interface NamedPlaylistSyncHost {
    fun view(account: TelegramUserId): NamedSyncView?
    fun update(account: TelegramUserId, epoch: Long, transform: (NamedPlaylistState) -> NamedPlaylistState): NamedUpdateResult
}

/** The named playlists of the signed-in account, as the UI sees them. Every call names the account it is meant for. */
internal interface NamedPlaylistSessionApi {
    fun open(account: TelegramUserId): NamedOpenResult
    fun close()

    /** The live playlists of the open account, in the order they were made. Empty when nothing is open. */
    fun snapshot(): List<NamedListView>

    /** The playlists of [account] as committed right now, or null if no readable session is open for exactly that account. */
    fun snapshotFor(account: TelegramUserId): List<NamedListView>?
    fun entries(account: TelegramUserId, id: ListId): List<NamedEntry>?
    fun listsContaining(account: TelegramUserId, item: PlaylistItemId): Set<ListId>?

    fun createList(account: TelegramUserId, name: String): NamedOutcome
    fun renameList(account: TelegramUserId, id: ListId, name: String): NamedOutcome
    fun deleteList(account: TelegramUserId, id: ListId): NamedOutcome
    fun addTelegram(account: TelegramUserId, id: ListId, item: PlaylistItemId.Telegram): NamedOutcome
    fun removeTelegram(account: TelegramUserId, id: ListId, item: PlaylistItemId.Telegram): NamedOutcome
    fun moveItem(account: TelegramUserId, id: ListId, item: PlaylistItemId.Telegram, index: Int): NamedOutcome
    fun addLocal(account: TelegramUserId, id: ListId, localId: Long, fingerprint: String): NamedOutcome
    fun removeLocal(account: TelegramUserId, id: ListId, localId: Long): NamedOutcome
    fun moveLocal(account: TelegramUserId, id: ListId, localId: Long, index: Int): NamedOutcome
    fun rebalance(account: TelegramUserId, id: ListId): NamedOutcome
}

/**
 * Nhiều Playlist có tên, S4: the one writer of the named playlists of the signed-in account.
 *
 * Meant to run on the single `TelegramPlaylistRuntime` thread like the Playlist V2 session; every method is
 * synchronized anyway. A change is stored in ONE atomic file write before it is adopted; a failed write leaves
 * the state as it was. A file that exists but can not be read blocks every write and is never replaced.
 */
internal class NamedPlaylistSession(
    private val store: NamedPlaylistPersistence,
    private val newDeviceId: () -> String,
) : NamedPlaylistSessionApi, NamedPlaylistSyncHost {
    private var user: TelegramUserId? = null
    private var state: NamedPlaylistState? = null
    private var blocked: PlaylistUnreadableReason? = null

    /** Changes every time an account is opened or closed, so a late result of an earlier one can be recognised. */
    private var epoch: Long = 0L

    @Synchronized
    fun currentEpoch(): Long = epoch

    @Synchronized
    override fun open(account: TelegramUserId): NamedOpenResult {
        epoch++
        user = account
        state = null
        blocked = null
        // Whatever goes wrong while opening must come back as an answer the screen can show, never as a swallowed exception.
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
    }

    private fun load(account: TelegramUserId): NamedOpenResult = when (val stored = store.load(account)) {
        is NamedLoadResult.Unreadable -> block(stored.reason)
        // Nothing is written until the first change: opening alone never creates a file or a key.
        is NamedLoadResult.Missing -> ready(NamedPlaylistState.empty(account.value, newDeviceId()))
        is NamedLoadResult.Valid -> ready(stored.state)
    }

    private fun block(reason: PlaylistUnreadableReason): NamedOpenResult {
        state = null
        blocked = reason
        return NamedOpenResult.Blocked(reason)
    }

    private fun ready(s: NamedPlaylistState): NamedOpenResult {
        state = s
        blocked = null
        return NamedOpenResult.Ready(s.lists())
    }

    // ---- reading -------------------------------------------------------------------------------

    @Synchronized
    override fun snapshot(): List<NamedListView> = state?.lists().orEmpty()

    @Synchronized
    override fun snapshotFor(account: TelegramUserId): List<NamedListView>? = readable(account)?.lists()

    @Synchronized
    override fun entries(account: TelegramUserId, id: ListId): List<NamedEntry>? = readable(account)?.entries(id)

    @Synchronized
    override fun listsContaining(account: TelegramUserId, item: PlaylistItemId): Set<ListId>? =
        readable(account)?.listsContaining(item)

    private fun readable(account: TelegramUserId): NamedPlaylistState? {
        val current = state ?: return null
        return current.takeIf { user == account && blocked == null && it.accountId == account.value }
    }

    // ---- changes -------------------------------------------------------------------------------

    @Synchronized
    override fun createList(account: TelegramUserId, name: String) = mutate(account) { it.createList(name) }

    @Synchronized
    override fun renameList(account: TelegramUserId, id: ListId, name: String) = mutate(account) { it.renameList(id, name) }

    @Synchronized
    override fun deleteList(account: TelegramUserId, id: ListId) = mutate(account) { it.deleteList(id) }

    @Synchronized
    override fun addTelegram(account: TelegramUserId, id: ListId, item: PlaylistItemId.Telegram) =
        mutate(account) { it.addTelegram(id, item) }

    @Synchronized
    override fun removeTelegram(account: TelegramUserId, id: ListId, item: PlaylistItemId.Telegram) =
        mutate(account) { it.removeTelegram(id, item) }

    @Synchronized
    override fun moveItem(account: TelegramUserId, id: ListId, item: PlaylistItemId.Telegram, index: Int) =
        mutate(account) { it.moveItem(id, item, index) }

    @Synchronized
    override fun addLocal(account: TelegramUserId, id: ListId, localId: Long, fingerprint: String) =
        mutate(account) { it.addLocal(id, localId, fingerprint) }

    @Synchronized
    override fun removeLocal(account: TelegramUserId, id: ListId, localId: Long) =
        mutate(account) { it.removeLocal(id, localId) }

    @Synchronized
    override fun moveLocal(account: TelegramUserId, id: ListId, localId: Long, index: Int) =
        mutate(account) { it.moveLocal(id, localId, index) }

    @Synchronized
    override fun rebalance(account: TelegramUserId, id: ListId) = mutate(account) { it.rebalance(id) }

    private fun mutate(account: TelegramUserId, change: (NamedPlaylistState) -> NamedMutation): NamedOutcome {
        if (user != account) return NamedOutcome.NotOpen
        if (state == null) {
            if (blocked == null) return NamedOutcome.NotOpen
            // A read error can be transient (Keystore not ready yet). Look again, never write blindly.
            when (val again = try { load(account) } catch (_: Exception) { block(PlaylistUnreadableReason.IO) }) {
                is NamedOpenResult.Blocked -> return NamedOutcome.Blocked(again.reason)
                is NamedOpenResult.Ready -> Unit
            }
        }
        val current = state ?: return NamedOutcome.NotOpen
        val made = try {
            change(current)
        } catch (_: OutboxFullException) {
            return NamedOutcome.OutboxFull
        } catch (_: Exception) {
            return NamedOutcome.Failed
        }
        made.refusal?.let { return refused(it) }
        if (made.state == current) return NamedOutcome.Ok(made.listId, changed = false)
        try {
            store.save(account, made.state)
        } catch (e: PlaylistUnreadableException) {
            state = null
            blocked = e.reason
            return NamedOutcome.Blocked(e.reason)
        } catch (_: Exception) {
            return NamedOutcome.Failed
        }
        state = made.state
        return NamedOutcome.Ok(made.listId, changed = true)
    }

    private fun refused(why: NamedRefusal): NamedOutcome = when (why) {
        NamedRefusal.LIST_LIMIT, NamedRefusal.ITEM_LIMIT -> NamedOutcome.Full(why)
        NamedRefusal.DUPLICATE_NAME -> NamedOutcome.DuplicateName
        NamedRefusal.BAD_NAME, NamedRefusal.NO_SUCH_LIST, NamedRefusal.NO_SUCH_ITEM -> NamedOutcome.Refused(why)
    }

    // ---- sync host -----------------------------------------------------------------------------

    @Synchronized
    override fun view(account: TelegramUserId): NamedSyncView? {
        if (user != account) return null
        return state?.let { NamedSyncView(epoch, it) }
    }

    /**
     * Runs [transform] on the current state and stores the result in ONE write before adopting it.
     * Refuses (changing nothing) if the session is not the one the caller looked at.
     */
    @Synchronized
    override fun update(account: TelegramUserId, epoch: Long, transform: (NamedPlaylistState) -> NamedPlaylistState): NamedUpdateResult {
        if (user != account || this.epoch != epoch) return NamedUpdateResult.Stale
        val current = state ?: return NamedUpdateResult.NotOpen
        val next = try {
            transform(current)
        } catch (_: Exception) {
            return NamedUpdateResult.Failed
        }
        if (next == current) return NamedUpdateResult.Committed(current)
        // A transform may change what the playlists hold, never whose they are or which device is writing.
        if (next.accountId != account.value || next.deviceId != current.deviceId) return NamedUpdateResult.Failed
        try {
            store.save(account, next)
        } catch (e: PlaylistUnreadableException) {
            state = null
            blocked = e.reason
            return NamedUpdateResult.Failed
        } catch (_: Exception) {
            return NamedUpdateResult.Failed
        }
        state = next
        return NamedUpdateResult.Committed(next)
    }
}
