package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.AddTarget
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.Screen

/**
 * Nhiều Playlist có tên, S13: the bridge between the screens and the [NamedPlaylistSessionApi]. It holds what the UI thread may read
 * (the playlists, the songs of the playlist on screen, which screen of the Playlist tab is shown) and does every read and write of the
 * session on the single playlist thread, never on the UI thread.
 *
 * Rules that keep it safe:
 *  - Every write goes through [submit] (`TelegramPlaylistRuntime.submit`), so it queues behind a Save of the favourites in progress.
 *  - A result comes back on the UI thread through [toUi] and is dropped unless it still belongs to the account and the open/close
 *    generation it was asked for ([MeloSavePolicy.acceptsCallback] is the same rule for the favourites): a late answer of account A is
 *    never shown for account B and a closed session never redraws.
 *  - What is shown is what the session COMMITTED: a change appears after its file was written, never before.
 *  - It never starts a sync; it only tells the scheduler that a write happened ([afterWrite]).
 */
internal class NamedPlaylistController(
    private val session: NamedPlaylistSessionApi,
    private val submit: (() -> Unit) -> Unit,
    private val toUi: (() -> Unit) -> Unit,
    private val alive: () -> Boolean,
    private val currentAccount: () -> TelegramUserId?,
    private val afterWrite: () -> Unit = {},
    private val changed: () -> Unit = {},
) {
    enum class Status { CLOSED, OPENING, READY, BLOCKED }

    var status: Status = Status.CLOSED
        private set
    var blockedReason: PlaylistUnreadableReason? = null
        private set
    var lists: List<NamedListView> = emptyList()
        private set
    var screen: Screen = Screen.Index
        private set

    /** The songs of the playlist on screen, in display order; empty unless [screen] is a detail. */
    var detailEntries: List<NamedEntry> = emptyList()
        private set

    private var account: TelegramUserId? = null
    private var generation = 0L

    val ready: Boolean get() = status == Status.READY

    private fun accepts(token: Long, who: TelegramUserId): Boolean =
        alive() && token == generation && account == who && currentAccount() == who

    // ---- open / close ---------------------------------------------------------------------------------

    /** Called when the favourites are opened for [who]. Whatever was shown for another account is forgotten at once. */
    fun open(who: TelegramUserId) {
        generation++
        val token = generation
        account = who
        status = Status.OPENING
        blockedReason = null
        lists = emptyList()
        detailEntries = emptyList()
        screen = Screen.Index
        submit {
            val opened = try {
                session.open(who)
            } catch (_: Exception) {
                NamedOpenResult.Blocked(PlaylistUnreadableReason.IO)
            }
            val read = (opened as? NamedOpenResult.Ready)?.let { session.snapshotFor(who) ?: it.lists }
            toUi {
                if (!accepts(token, who)) return@toUi
                when (opened) {
                    is NamedOpenResult.Ready -> {
                        status = Status.READY
                        lists = read.orEmpty()
                    }
                    is NamedOpenResult.Blocked -> {
                        status = Status.BLOCKED
                        blockedReason = opened.reason
                    }
                }
                changed()
            }
        }
    }

    /** Called when the favourites are closed (logout, account switch). The session file is kept; only the memory is dropped. */
    fun close() {
        generation++
        account = null
        status = Status.CLOSED
        blockedReason = null
        lists = emptyList()
        detailEntries = emptyList()
        screen = Screen.Index
        submit { session.close() }
    }

    /** Re-reads what the session holds now (after a sync applied another device's changes, or on return to the screen). */
    fun refresh() {
        val who = account ?: return
        if (status != Status.READY) return
        val token = generation
        val detail = (screen as? Screen.Detail)?.id
        submit {
            val read = session.snapshotFor(who)
            val songs = detail?.let { session.entries(who, it) }
            toUi {
                if (!accepts(token, who) || read == null) return@toUi
                apply(read, songs)
            }
        }
    }

    private fun apply(read: List<NamedListView>, songs: List<NamedEntry>?) {
        val before = Triple(lists, screen, detailEntries)
        lists = read
        screen = MeloNamedPlaylistPolicy.resolve(screen, read)
        detailEntries = if (screen is Screen.Detail) songs.orEmpty() else emptyList()
        // A refresh that found nothing new (the usual case after a sync) redraws nothing.
        if (Triple(lists, screen, detailEntries) != before) changed()
    }

    // ---- which screen ------------------------------------------------------------------------------------

    fun show(next: Screen) {
        screen = MeloNamedPlaylistPolicy.resolve(next, lists)
        detailEntries = emptyList()
        changed()
        if (screen is Screen.Detail) refresh()
    }

    /** The Playlist tab was left: the next visit starts at the index again. */
    fun resetScreen() {
        if (screen == Screen.Index && detailEntries.isEmpty()) return
        screen = Screen.Index
        detailEntries = emptyList()
    }

    // ---- reading for a dialog -------------------------------------------------------------------------------

    /** Which playlists hold [item] right now, asked of the session on the playlist thread. [done] runs on the UI thread, or never if dropped. */
    fun containing(item: PlaylistItemId, done: (Set<ListId>) -> Unit) {
        val who = account ?: return
        if (status != Status.READY) return
        val token = generation
        submit {
            val ids = session.listsContaining(who, item).orEmpty()
            toUi { if (accepts(token, who)) done(ids) }
        }
    }

    // ---- writing ------------------------------------------------------------------------------------------------

    private fun write(op: (TelegramUserId) -> NamedOutcome, done: (NamedOutcome) -> Unit) {
        val who = account
        if (who == null || status != Status.READY) {
            done(if (status == Status.BLOCKED) NamedOutcome.Blocked(blockedReason ?: PlaylistUnreadableReason.IO) else NamedOutcome.NotOpen)
            return
        }
        val token = generation
        val detail = (screen as? Screen.Detail)?.id
        submit {
            val outcome = try {
                op(who)
            } catch (_: Exception) {
                NamedOutcome.Failed
            }
            val read = session.snapshotFor(who)
            val songs = detail?.let { session.entries(who, it) }
            toUi {
                if (!accepts(token, who)) return@toUi
                if (outcome is NamedOutcome.Blocked) {
                    status = Status.BLOCKED
                    blockedReason = outcome.reason
                    lists = emptyList()
                    detailEntries = emptyList()
                    screen = Screen.Index
                    changed()
                } else if (read != null) {
                    apply(read, songs)
                }
                if (outcome is NamedOutcome.Ok && outcome.changed) afterWrite()
                done(outcome)
            }
        }
    }

    fun create(name: String, done: (NamedOutcome) -> Unit) = write({ session.createList(it, name) }, done)

    fun rename(id: ListId, name: String, done: (NamedOutcome) -> Unit) = write({ session.renameList(it, id, name) }, done)

    fun delete(id: ListId, done: (NamedOutcome) -> Unit) = write({ session.deleteList(it, id) }, done)

    fun add(id: ListId, target: AddTarget, done: (NamedOutcome) -> Unit) = write({ addOne(it, id, target) }, done)

    fun remove(id: ListId, item: PlaylistItemId, done: (NamedOutcome) -> Unit) = write({ who ->
        when (item) {
            is PlaylistItemId.Telegram -> session.removeTelegram(who, id, item)
            is PlaylistItemId.Local -> session.removeLocal(who, id, item.localId)
            is PlaylistItemId.Legacy -> NamedOutcome.Refused(NamedRefusal.NO_SUCH_ITEM)
        }
    }, done)

    /** Puts [item] at [index] of the display order (one stored change, one operation at most). */
    fun move(id: ListId, item: PlaylistItemId, index: Int, done: (NamedOutcome) -> Unit) = write({ who ->
        when (item) {
            is PlaylistItemId.Telegram -> session.moveItem(who, id, item, index)
            is PlaylistItemId.Local -> session.moveLocal(who, id, item.localId, index)
            is PlaylistItemId.Legacy -> NamedOutcome.Refused(NamedRefusal.NO_SUCH_ITEM)
        }
    }, done)

    /** Result of making a playlist and filling it in ONE queued task, so nothing else can slip in between. */
    data class Created(val outcome: NamedOutcome, val listId: ListId?, val added: Int, val stoppedBy: NamedOutcome?)

    /** "Lưu hàng chờ thành Playlist": the playlist first, then every target in order. The first refusal stops the filling and is reported. */
    fun createWith(name: String, targets: List<AddTarget>, done: (Created) -> Unit) {
        var result = Created(NamedOutcome.NotOpen, null, 0, null)
        write({ who ->
            val made = session.createList(who, name)
            val id = (made as? NamedOutcome.Ok)?.listId
            if (id == null) {
                result = Created(made, null, 0, null)
                made
            } else {
                var added = 0
                var stop: NamedOutcome? = null
                for (target in targets) {
                    val outcome = addOne(who, id, target)
                    if (outcome is NamedOutcome.Ok) added++ else { stop = outcome; break }
                }
                result = Created(made, id, added, stop)
                made
            }
        }, { outcome -> done(if (outcome is NamedOutcome.Ok) result else Created(outcome, null, 0, null)) })
    }

    private fun addOne(who: TelegramUserId, id: ListId, target: AddTarget): NamedOutcome = when (target) {
        is AddTarget.Telegram -> session.addTelegram(who, id, target.item)
        is AddTarget.Local -> session.addLocal(who, id, target.localId, target.fingerprint)
        is AddTarget.Refused -> NamedOutcome.Refused(NamedRefusal.NO_SUCH_ITEM)
    }
}
