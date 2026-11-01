package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.AddTarget
import com.bsdung.musicassistantcar.data.telegram.MeloNamedPlaylistPolicy.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Nhiều Playlist có tên, S13: the controller between the screens and the session (deferred playlist thread / UI thread, fake store). */
class NamedPlaylistControllerTest {
    private val a = TelegramUserId(1001L)
    private val b = TelegramUserId(2002L)

    private class MemoryStore : NamedPlaylistPersistence {
        val files = HashMap<Long, NamedPlaylistState>()
        var unreadable: PlaylistUnreadableReason? = null
        var failSaves = false
        override fun load(user: TelegramUserId): NamedLoadResult {
            unreadable?.let { return NamedLoadResult.Unreadable(it) }
            return files[user.value]?.let { NamedLoadResult.Valid(it) } ?: NamedLoadResult.Missing
        }

        override fun save(user: TelegramUserId, state: NamedPlaylistState) {
            unreadable?.let { throw PlaylistUnreadableException(it) }
            if (failSaves) throw IOException("disk full")
            files[user.value] = state
        }
    }

    /** The playlist thread and the UI thread are two queues that a test runs by hand, so the order of events is exact. */
    private class Harness(val store: MemoryStore = MemoryStore()) {
        var devices = 0
        val session = NamedPlaylistSession(store) { "dev-${++devices}" }
        val playlistThread = ArrayDeque<() -> Unit>()
        val uiThread = ArrayDeque<() -> Unit>()
        var alive = true
        var who: TelegramUserId? = null
        var writes = 0
        var redraws = 0
        var seenAtRedraw: List<List<String>> = emptyList()
        lateinit var controller: NamedPlaylistController

        init {
            controller = NamedPlaylistController(
                session, { playlistThread.addLast(it) }, { uiThread.addLast(it) }, { alive }, { who },
                afterWrite = { writes++ },
                changed = { redraws++; seenAtRedraw = seenAtRedraw + listOf(controller.lists.map { it.name }) },
            )
        }

        fun pump() {
            var guard = 0
            while (playlistThread.isNotEmpty() || uiThread.isNotEmpty()) {
                check(guard++ < 1000)
                playlistThread.removeFirstOrNull()?.invoke()
                uiThread.removeFirstOrNull()?.invoke()
            }
        }

        fun openAs(user: TelegramUserId) {
            who = user
            controller.open(user)
            pump()
        }
    }

    private fun tg(n: Long) = PlaylistItemId.Telegram(-1001234L, n)

    private fun NamedPlaylistController.createNow(h: Harness, name: String): ListId {
        var made: NamedOutcome? = null
        create(name) { made = it }
        h.pump()
        return checkNotNull((made as NamedOutcome.Ok).listId)
    }

    // ---- open / close -----------------------------------------------------------------------------------

    @Test fun openingReadsTheSessionOnThePlaylistThreadAndOnlyThenShowsIt() {
        val h = Harness()
        h.store.files[a.value] = NamedPlaylistState.empty(a.value, "dev-x").createList("Có sẵn").state
        h.who = a
        h.controller.open(a)
        assertEquals(NamedPlaylistController.Status.OPENING, h.controller.status)
        assertTrue("nothing ran on the caller's thread", h.session.snapshot().isEmpty())
        h.pump()
        assertEquals(NamedPlaylistController.Status.READY, h.controller.status)
        assertEquals(listOf("Có sẵn"), h.controller.lists.map { it.name })
        assertTrue(h.controller.ready)
        assertTrue(h.redraws >= 1)
    }

    @Test fun anUnreadableFileMakesItBlockedAndEveryWriteSaysSo() {
        val h = Harness()
        h.store.unreadable = PlaylistUnreadableReason.DECRYPT
        h.openAs(a)
        assertEquals(NamedPlaylistController.Status.BLOCKED, h.controller.status)
        assertEquals(PlaylistUnreadableReason.DECRYPT, h.controller.blockedReason)
        assertFalse(h.controller.ready)
        var outcome: NamedOutcome? = null
        h.controller.create("X") { outcome = it }
        assertEquals(NamedOutcome.Blocked(PlaylistUnreadableReason.DECRYPT), outcome)
        assertTrue("nothing was even queued", h.playlistThread.isEmpty())
        assertEquals(0, h.store.files.size)
    }

    @Test fun beforeOpenAWriteIsNotOpenAndQueuesNothing() {
        val h = Harness()
        var outcome: NamedOutcome? = null
        h.controller.create("X") { outcome = it }
        assertEquals(NamedOutcome.NotOpen, outcome)
        assertTrue(h.playlistThread.isEmpty())
    }

    @Test fun closeForgetsEverythingAndLateAnswersAreDropped() {
        val h = Harness()
        h.openAs(a)
        h.controller.createNow(h, "Một")
        h.controller.refresh()
        h.controller.close()
        assertEquals(NamedPlaylistController.Status.CLOSED, h.controller.status)
        assertTrue(h.controller.lists.isEmpty())
        val redraws = h.redraws
        h.pump() // the refresh asked before close answers now
        assertTrue(h.controller.lists.isEmpty())
        assertEquals(redraws, h.redraws)
        assertEquals("the session was closed too", emptyList<NamedListView>(), h.session.snapshot())
    }

    // ---- accounts and lifecycle -----------------------------------------------------------------------------------

    @Test fun anAnswerForAccountAIsNeverShownAfterAccountBWasOpened() {
        val h = Harness()
        h.store.files[a.value] = NamedPlaylistState.empty(a.value, "dev-x").createList("Của A").state
        h.store.files[b.value] = NamedPlaylistState.empty(b.value, "dev-y").createList("Của B").state
        h.who = a
        h.controller.open(a)
        h.who = b
        h.controller.open(b) // A's open is still queued
        assertTrue(h.controller.lists.isEmpty())
        h.pump()
        assertEquals(listOf("Của B"), h.controller.lists.map { it.name })
        assertEquals(listOf(listOf("Của B")), h.seenAtRedraw)
    }

    @Test fun aWriteAnsweringAfterTheAccountChangedDoesNotTouchTheScreenNorCallBack() {
        val h = Harness()
        h.openAs(a)
        var called = false
        h.controller.create("Muộn") { called = true }
        h.who = b // the signed-in account changed while the write was queued
        h.pump()
        assertFalse(called)
        assertTrue(h.controller.lists.isEmpty())
        assertEquals("the write itself was made for A and is stored", listOf("Muộn"), h.store.files.getValue(a.value).lists().map { it.name })
        assertEquals(0, h.writes)
    }

    @Test fun aDestroyedActivityGetsNoCallbacks() {
        val h = Harness()
        h.openAs(a)
        var called = false
        h.controller.create("Muộn") { called = true }
        h.alive = false
        h.pump()
        assertFalse(called)
        assertTrue(h.controller.lists.isEmpty())
    }

    // ---- writing --------------------------------------------------------------------------------------------------------------

    @Test fun aChangeShowsOnlyAfterItWasStoredAndTellsTheSchedulerOnce() {
        val h = Harness()
        h.openAs(a)
        var outcome: NamedOutcome? = null
        h.controller.create("Nhạc đi xe") { outcome = it }
        assertTrue("not shown yet", h.controller.lists.isEmpty())
        assertTrue(h.store.files.isEmpty())
        h.pump()
        assertTrue(outcome is NamedOutcome.Ok)
        assertEquals(listOf("Nhạc đi xe"), h.controller.lists.map { it.name })
        assertEquals(listOf("Nhạc đi xe"), h.store.files.getValue(a.value).lists().map { it.name })
        assertEquals(1, h.writes)
        // the screen saw the stored state when it was told to redraw
        assertEquals(listOf("Nhạc đi xe"), h.seenAtRedraw.last())
    }

    @Test fun aChangeThatChangedNothingDoesNotWakeTheScheduler() {
        val h = Harness()
        h.openAs(a)
        val id = h.controller.createNow(h, "Một")
        val before = h.writes
        var outcome: NamedOutcome? = null
        h.controller.rename(id, " Một ") { outcome = it }
        h.pump()
        assertEquals(NamedOutcome.Ok(id, false), outcome)
        assertEquals(before, h.writes)
    }

    @Test fun refusalsAndFailuresComeBackAsOutcomesAndLeaveTheScreenAsItWas() {
        val h = Harness()
        h.openAs(a)
        val id = h.controller.createNow(h, "Một")
        var outcome: NamedOutcome? = null
        h.controller.create("một") { outcome = it }
        h.pump()
        assertEquals(NamedOutcome.DuplicateName, outcome)
        h.store.failSaves = true
        h.controller.rename(id, "Hai") { outcome = it }
        h.pump()
        assertEquals(NamedOutcome.Failed, outcome)
        assertEquals(listOf("Một"), h.controller.lists.map { it.name })
    }

    @Test fun aFileThatTurnsUnreadableDuringAWriteBlocksTheControllerAndResetsTheScreen() {
        val h = Harness()
        h.openAs(a)
        h.controller.createNow(h, "Một")
        h.store.unreadable = PlaylistUnreadableReason.FORMAT
        var outcome: NamedOutcome? = null
        h.controller.create("Hai") { outcome = it }
        h.pump()
        assertEquals(NamedOutcome.Blocked(PlaylistUnreadableReason.FORMAT), outcome)
        assertEquals(NamedPlaylistController.Status.BLOCKED, h.controller.status)
        assertTrue(h.controller.lists.isEmpty())
        assertEquals(Screen.Index, h.controller.screen)
    }

    @Test fun addRemoveAndMoveGoThroughTheSessionAndUpdateTheDetail() {
        val h = Harness()
        h.openAs(a)
        val id = h.controller.createNow(h, "Gym")
        h.controller.show(Screen.Detail(id))
        h.pump()
        assertTrue(h.controller.detailEntries.isEmpty())
        for (n in 1L..3L) {
            h.controller.add(id, AddTarget.Telegram(tg(n))) { }
            h.pump()
        }
        h.controller.add(id, AddTarget.Local(900L, LocalFingerprint.of("x", 1, 1))) { }
        h.pump()
        assertEquals(listOf<PlaylistItemId>(tg(1), tg(2), tg(3), PlaylistItemId.Local(900L)), h.controller.detailEntries.map { it.item })
        h.controller.move(id, tg(3), 0) { }
        h.pump()
        assertEquals(tg(3), h.controller.detailEntries.first().item)
        h.controller.move(id, PlaylistItemId.Local(900L), 1) { }
        h.pump()
        assertEquals(PlaylistItemId.Local(900L), h.controller.detailEntries[1].item)
        h.controller.remove(id, tg(1)) { }
        h.controller.remove(id, PlaylistItemId.Local(900L)) { }
        h.pump()
        assertEquals(listOf<PlaylistItemId>(tg(3), tg(2)), h.controller.detailEntries.map { it.item })
        assertEquals(2, h.controller.lists.single().count)
        assertTrue(h.store.files.getValue(a.value).outbox.none { it.op.item is PlaylistItemId.Local })
    }

    @Test fun aRefusedTargetIsNeverSentToTheSession() {
        val h = Harness()
        h.openAs(a)
        val id = h.controller.createNow(h, "Gym")
        var outcome: NamedOutcome? = null
        h.controller.add(id, AddTarget.Refused("x")) { outcome = it }
        h.pump()
        assertEquals(NamedOutcome.Refused(NamedRefusal.NO_SUCH_ITEM), outcome)
        assertEquals(0, h.controller.lists.single().count)
    }

    @Test fun twoQuickChangesRunInTheOrderTheyWereAsked() {
        val h = Harness()
        h.openAs(a)
        val id = h.controller.createNow(h, "Gym")
        val order = ArrayList<String>()
        h.controller.add(id, AddTarget.Telegram(tg(1))) { order += "add" }
        h.controller.rename(id, "Gym 2") { order += "rename" }
        h.controller.delete(id) { order += "delete" }
        h.pump()
        assertEquals(listOf("add", "rename", "delete"), order)
        assertTrue(h.controller.lists.isEmpty())
    }

    // ---- screens ----------------------------------------------------------------------------------------------------------------

    @Test fun showingADetailLoadsItsSongsAndTheIndexDropsThem() {
        val h = Harness()
        h.openAs(a)
        val id = h.controller.createNow(h, "Gym")
        h.controller.add(id, AddTarget.Telegram(tg(5))) { }
        h.pump()
        h.controller.show(Screen.Detail(id))
        h.pump()
        assertEquals(Screen.Detail(id), h.controller.screen)
        assertEquals(listOf<PlaylistItemId>(tg(5)), h.controller.detailEntries.map { it.item })
        h.controller.show(Screen.Index)
        assertTrue(h.controller.detailEntries.isEmpty())
        h.controller.show(Screen.Favourites)
        assertEquals(Screen.Favourites, h.controller.screen)
        h.controller.resetScreen()
        assertEquals(Screen.Index, h.controller.screen)
    }

    @Test fun aPlaylistDeletedElsewhereTakesTheDetailBackToTheIndexOnRefresh() {
        val h = Harness()
        h.openAs(a)
        val id = h.controller.createNow(h, "Gym")
        h.controller.show(Screen.Detail(id))
        h.pump()
        // another device's delete is applied by the sync through the session's host door
        val view = checkNotNull(h.session.view(a))
        val del = view.state.deleteList(id).state
        assertTrue(h.session.update(a, view.epoch) { del } is NamedUpdateResult.Committed)
        h.controller.refresh()
        h.pump()
        assertEquals(Screen.Index, h.controller.screen)
        assertTrue(h.controller.lists.isEmpty())
        assertTrue(h.controller.detailEntries.isEmpty())
    }

    @Test fun showingAPlaylistThatDoesNotExistFallsBackToTheIndex() {
        val h = Harness()
        h.openAs(a)
        h.controller.show(Screen.Detail(ListId("dev-9", 9L)))
        assertEquals(Screen.Index, h.controller.screen)
    }

    @Test fun refreshWhileBlockedOrClosedAsksNothing() {
        val h = Harness()
        h.controller.refresh()
        assertTrue(h.playlistThread.isEmpty())
        h.store.unreadable = PlaylistUnreadableReason.IO
        h.openAs(a)
        h.controller.refresh()
        assertTrue(h.playlistThread.isEmpty())
    }

    @Test fun containingAsksTheSessionAndAnswersOnTheUiThreadOnlyForTheSameSession() {
        val h = Harness()
        h.openAs(a)
        val one = h.controller.createNow(h, "Một")
        val two = h.controller.createNow(h, "Hai")
        h.controller.add(two, AddTarget.Telegram(tg(1))) { }
        h.pump()
        var got: Set<ListId>? = null
        h.controller.containing(tg(1)) { got = it }
        assertNull(got)
        h.pump()
        assertEquals(setOf(two), got)
        assertFalse(one in got!!)
        got = null
        h.controller.containing(tg(1)) { got = it }
        h.controller.close()
        h.pump()
        assertNull("a closed session gets no answer", got)
    }

    // ---- saving the queue --------------------------------------------------------------------------------------------------------

    @Test fun createWithMakesThePlaylistThenAddsEveryTargetInOrderInOneTask() {
        val h = Harness()
        h.openAs(a)
        var result: NamedPlaylistController.Created? = null
        val targets = listOf(
            AddTarget.Telegram(tg(1)), AddTarget.Local(9L, LocalFingerprint.of("x", 1, 1)), AddTarget.Telegram(tg(2)),
        )
        h.controller.createWith("Hàng chờ", targets) { result = it }
        assertEquals("one queued task for the whole batch", 1, h.playlistThread.size)
        h.pump()
        val created = checkNotNull(result)
        assertTrue(created.outcome is NamedOutcome.Ok)
        assertEquals(3, created.added)
        assertNull(created.stoppedBy)
        assertEquals(listOf("Hàng chờ"), h.controller.lists.map { it.name })
        assertEquals(3, h.controller.lists.single().count)
        assertEquals(1, h.writes)
    }

    @Test fun createWithStopsAtTheFirstRefusalAndSaysWhatWasKept() {
        val h = Harness()
        h.openAs(a)
        var result: NamedPlaylistController.Created? = null
        h.controller.createWith("Hàng chờ", listOf(AddTarget.Telegram(tg(1)), AddTarget.Refused("x"), AddTarget.Telegram(tg(2)))) { result = it }
        h.pump()
        val created = checkNotNull(result)
        assertEquals(1, created.added)
        assertEquals(NamedOutcome.Refused(NamedRefusal.NO_SUCH_ITEM), created.stoppedBy)
        assertEquals("what was added stays", 1, h.controller.lists.single().count)
    }

    @Test fun createWithADuplicateNameChangesNothing() {
        val h = Harness()
        h.openAs(a)
        h.controller.createNow(h, "Một")
        var result: NamedPlaylistController.Created? = null
        h.controller.createWith("một", listOf(AddTarget.Telegram(tg(1)))) { result = it }
        h.pump()
        assertEquals(NamedOutcome.DuplicateName, result?.outcome)
        assertNull(result?.listId)
        assertEquals(0, result?.added)
        assertEquals(1, h.controller.lists.size)
        assertEquals(0, h.controller.lists.single().count)
    }
}
