package com.bsdung.musicassistantcar.data.telegram

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * The one authoritative, in-memory copy of the saved playlist of the signed-in account, and the only
 * writer of its file. Both Save paths (now-playing button, song menu) go through [toggle], so they
 * share the same safety rules:
 *  - a file that cannot be read is never replaced (no empty list is invented from a read error);
 *  - a full playlist refuses a new song instead of dropping the oldest one;
 *  - success is reported only after the file was written;
 *  - every toggle starts from the latest committed list, so quick repeated taps never lose an update.
 *
 * Not thread-confined on its own: it is synchronized, and the app calls it from [TelegramPlaylistRuntime]'s
 * single thread so that taps are also applied in the order they were made.
 */
internal class TelegramPlaylistSession(private val persistence: PlaylistPersistence) : PlaylistSessionApi {

    sealed class OpenResult {
        /** [warning]: set when part of the work had to wait (V2 only); the playlist shown is still the stored one. */
        data class Ready(val ids: List<Long>, val warning: PlaylistWarning? = null) : OpenResult()
        data class Blocked(val reason: PlaylistUnreadableReason) : OpenResult()
    }

    sealed class ToggleOutcome {
        data class Added(val ids: List<Long>) : ToggleOutcome()
        data class Removed(val ids: List<Long>) : ToggleOutcome()
        data class Full(val size: Int) : ToggleOutcome()
        data class Blocked(val reason: PlaylistUnreadableReason) : ToggleOutcome()
        /** No playlist is open for this account (not loaded yet, signed out, or another account). */
        object NotOpen : ToggleOutcome()
        /** The file could not be written. The previous file and the in-memory list are unchanged. */
        object Failed : ToggleOutcome()
        /** (V2) too many changes are waiting to be synced; nothing waiting is ever dropped, so the new change is refused. */
        object OutboxFull : ToggleOutcome()
    }

    private var user: TelegramUserId? = null
    private var ids: List<Long> = emptyList()
    private var blocked: PlaylistUnreadableReason? = null

    @Synchronized
    override fun open(account: TelegramUserId): OpenResult {
        user = account
        ids = emptyList()
        blocked = null
        return when (val loaded = persistence.load(account)) {
            is PlaylistLoadResult.Missing -> OpenResult.Ready(emptyList())
            is PlaylistLoadResult.Ok -> {
                ids = loaded.ids
                OpenResult.Ready(ids)
            }
            is PlaylistLoadResult.Unreadable -> {
                blocked = loaded.reason
                OpenResult.Blocked(loaded.reason)
            }
        }
    }

    @Synchronized
    override fun close() {
        user = null
        ids = emptyList()
        blocked = null
    }

    @Synchronized
    override fun snapshot(): List<Long> = ids

    // V1-only session: there is no index, no legacy keys and no outbox.
    override fun attachIndex(index: PlaylistIndex) = Unit
    override fun detachIndex(index: PlaylistIndex) = Unit
    override fun resolveLegacyKeys(account: TelegramUserId): Boolean = false
    override fun toggle(account: TelegramUserId, target: PlaylistTarget): ToggleOutcome = toggle(account, target.songKey)

    @Synchronized
    fun toggle(account: TelegramUserId, target: Long): ToggleOutcome {
        if (user != account) return ToggleOutcome.NotOpen
        if (blocked != null) {
            // A read error can be transient (Keystore not ready yet). Look again, never write blindly.
            when (val again = persistence.load(account)) {
                is PlaylistLoadResult.Unreadable -> {
                    blocked = again.reason
                    return ToggleOutcome.Blocked(again.reason)
                }
                is PlaylistLoadResult.Ok -> ids = again.ids
                is PlaylistLoadResult.Missing -> ids = emptyList()
            }
            blocked = null
        }
        val next = when (val planned = TelegramPlaylistPolicy.toggle(ids, target)) {
            is TelegramPlaylistPolicy.ToggleResult.Full -> return ToggleOutcome.Full(planned.size)
            is TelegramPlaylistPolicy.ToggleResult.Added -> planned.ids
            is TelegramPlaylistPolicy.ToggleResult.Removed -> planned.ids
        }
        try {
            persistence.save(account, next)
        } catch (e: PlaylistUnreadableException) {
            blocked = e.reason
            return ToggleOutcome.Blocked(e.reason)
        } catch (_: Exception) {
            return ToggleOutcome.Failed
        }
        ids = next
        return if (target in next) ToggleOutcome.Added(next) else ToggleOutcome.Removed(next)
    }
}

/**
 * Process-wide single writer thread + session. Process-wide on purpose: a rotated or recreated
 * activity queues behind the previous one's pending Save instead of racing it.
 */
internal object TelegramPlaylistRuntime {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "MeloVault-Playlist").apply { isDaemon = true }
    }
    private var session: PlaylistSessionApi? = null

    @Synchronized
    fun session(noBackupDir: File): PlaylistSessionApi = session ?: run {
        val v1 = TelegramSavedPlaylistStore(noBackupDir)
        val built: PlaylistSessionApi = if (TelegramPlaylistFlags.V2_ENABLED) {
            TelegramPlaylistV2Session(v1, TelegramPlaylistStoreV2(noBackupDir)) { java.util.UUID.randomUUID().toString() }
        } else {
            TelegramPlaylistSession(v1)
        }
        built.also { session = it }
    }

    fun submit(task: () -> Unit) {
        try {
            executor.execute {
                try {
                    task()
                } catch (_: Exception) {
                    // A failed task must never kill the writer thread; its outcome handler reports errors.
                }
            }
        } catch (_: RejectedExecutionException) {
        }
    }
}

/**
 * Which saved songs the Playlist screen shows. The list must come from what was saved (all keys, in
 * saved order), never from the few hundred songs that happen to be loaded in memory: a saved song
 * that is not found stays as a placeholder entry and is never removed from the playlist.
 */
internal object TelegramPlaylistViewPolicy {
    data class Entry<T>(val key: Long, val item: T?) {
        val isPlaceholder: Boolean get() = item == null
    }

    /** Keys not answered by [inMemory] are looked up in [indexed] (the library database). */
    fun <T> resolve(keys: List<Long>, inMemory: (Long) -> T?, indexed: Map<Long, T>): List<Entry<T>> =
        keys.distinct().map { key -> Entry(key, inMemory(key) ?: indexed[key]) }

    /** Keys the in-memory list cannot answer: only these need a database lookup. */
    fun <T> missingFromMemory(keys: List<Long>, inMemory: (Long) -> T?): List<Long> =
        keys.distinct().filter { inMemory(it) == null }
}
