package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * E96 P5B3 REWORK-03: after a sync in Settings the visible Playlist must follow the committed V2 state when the screen
 * comes back, without restarting the app and without any network call. The Activity glue is [PlaylistLiveRefresh];
 * everything it decides is tested here with the real V2 session.
 */
class PlaylistLiveRefreshTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val other = TelegramUserId(2002L)
    private lateinit var channel: FakeTelegramChannel
    private val pool = Executors.newSingleThreadExecutor()

    @Before fun setUp() {
        channel = FakeTelegramChannel()
    }

    @After fun tearDown() {
        pool.shutdownNow()
    }

    private fun saved(n: Int): Long = n * 4_194_304L
    private fun device(name: String) = FakeSyncDevice(name, folder.newFolder(name), acct, channel, seed = name.sumOf { it.code } % 90 + 1)
    private fun FakeSyncDevice.song(n: Int) = toggle(account.value, saved(n))

    private class Screen(var shown: List<Long> = emptyList(), var account: TelegramUserId?, var alive: Boolean = true) {
        val results = ArrayList<Pair<Int?, Boolean>>()
        val applied = ArrayList<List<Long>>()
        val queuedUi = ArrayList<() -> Unit>()
        var deferUi = false
    }

    private fun refresh(session: PlaylistSessionApi, screen: Screen, submit: (() -> Unit) -> Unit = { it() }) = PlaylistLiveRefresh(
        session = session,
        submit = submit,
        toUi = { block -> if (screen.deferUi) screen.queuedUi.add(block) else block() },
        alive = { screen.alive },
        currentAccount = { screen.account },
        shown = { screen.shown },
        apply = { ids -> screen.shown = ids; screen.applied.add(ids) },
        onResult = { count, changed -> screen.results.add(count to changed) },
    )

    // ---- the defect: a remote change is committed to V2 but the list on screen is stale ---------------------------------

    @Test fun r3_aRemoteUpdateCommittedWhileSettingsWasOpenShowsOnResumeWithoutARestart() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.song(2)
        a.sync()
        val screen = Screen(shown = b.present(), account = acct)
        assertEquals(emptyList<Long>(), screen.shown)

        assertEquals(SyncStatus.OK, b.sync().status) // "Settings": the sync commits the remote songs into B's V2 state

        refresh(b.session, screen).requestRefresh(acct) // onResume
        assertEquals(1, screen.applied.size)
        assertEquals(setOf(saved(1), saved(2)), screen.shown.toSet())
        assertEquals(2, screen.shown.size)
    }

    @Test fun r3_theSavedOrderAndNoDuplicatesAreKept() {
        val a = device("dev-a"); val b = device("dev-b")
        for (n in listOf(3, 1, 2)) a.song(n)
        a.sync(); b.sync()
        val screen = Screen(account = acct)
        refresh(b.session, screen).requestRefresh(acct)
        assertEquals(b.session.snapshot(), screen.shown)
        assertEquals(screen.shown.size, screen.shown.toSet().size)
    }

    @Test fun r3_ifNothingChangedNothingIsRedrawn_soScrollAndSearchStay() {
        val a = device("dev-a")
        a.song(1); a.song(2)
        val screen = Screen(shown = a.session.snapshot(), account = acct)
        val r = refresh(a.session, screen)
        repeat(5) { r.requestRefresh(acct) }
        assertTrue("no redraw for an identical list", screen.applied.isEmpty())
    }

    @Test fun r3_aRemoteRemovalShowsToo() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.song(2); a.sync(); b.sync()
        val screen = Screen(shown = b.session.snapshot(), account = acct)
        a.song(1); a.sync() // A removes song 1
        b.sync()
        refresh(b.session, screen).requestRefresh(acct)
        assertEquals(listOf(saved(2)), screen.shown)
    }

    @Test fun r3_theLogEvidenceCarriesOnlyACountAndAFlag() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.song(2); a.sync(); b.sync()
        val screen = Screen(account = acct)
        val r = refresh(b.session, screen)
        r.requestRefresh(acct)
        r.requestRefresh(acct)
        assertEquals(listOf<Pair<Int?, Boolean>>(2 to true, 2 to false), screen.results)
    }

    // ---- guards -----------------------------------------------------------------------------------------------------------

    @Test fun r3_aResultForAnAccountThatIsNoLongerOnScreenIsIgnored() {
        val a = device("dev-a")
        a.song(1)
        val screen = Screen(account = acct).also { it.deferUi = true }
        refresh(a.session, screen).requestRefresh(acct)
        screen.account = other // signed out / switched before the UI callback ran
        screen.queuedUi.forEach { it() }
        assertTrue(screen.applied.isEmpty())
        assertEquals(emptyList<Long>(), screen.shown)
    }

    @Test fun r3_aDestroyedScreenIsLeftAlone() {
        val a = device("dev-a")
        a.song(1)
        val screen = Screen(account = acct).also { it.deferUi = true }
        refresh(a.session, screen).requestRefresh(acct)
        screen.alive = false
        screen.queuedUi.forEach { it() }
        assertTrue(screen.applied.isEmpty())
    }

    @Test fun r3_anOlderResultNeverOverwritesANewerOne() {
        val a = device("dev-a")
        a.song(1)
        val screen = Screen(account = acct).also { it.deferUi = true }
        val r = refresh(a.session, screen)
        r.requestRefresh(acct) // first look: [1]
        a.song(2)
        r.requestRefresh(acct) // second look: [1, 2]
        // the UI delivers them in the WRONG order
        screen.queuedUi.reversed().forEach { it() }
        assertEquals(1, screen.applied.size)
        assertEquals(2, screen.shown.size)
    }

    @Test fun r3_invalidateDropsWhateverWasInFlight() {
        val a = device("dev-a")
        a.song(1)
        val screen = Screen(account = acct).also { it.deferUi = true }
        val r = refresh(a.session, screen)
        r.requestRefresh(acct)
        r.invalidate() // account change / logout
        screen.queuedUi.forEach { it() }
        assertTrue(screen.applied.isEmpty())
    }

    @Test fun r3_aSessionThatIsNotOpenForThatAccountAnswersNothingAndTheListIsKept() {
        val a = device("dev-a")
        a.song(1)
        val screen = Screen(shown = listOf(saved(1)), account = other)
        refresh(a.session, screen).requestRefresh(other)
        assertTrue(screen.applied.isEmpty())
        assertEquals(listOf(saved(1)), screen.shown)
        assertNull(a.session.snapshotFor(other))
        assertEquals(listOf(saved(1)), a.session.snapshotFor(acct))
        a.session.close()
        assertNull(a.session.snapshotFor(acct))
    }

    // ---- a local Save in progress ---------------------------------------------------------------------------------------------

    @Test fun r3_aLocalSaveQueuedBeforeTheResumeIsSeenByIt() {
        val a = device("dev-a")
        val screen = Screen(account = acct)
        val gate = CountDownLatch(1)
        val done = CountDownLatch(1)
        // the playlist thread is busy writing a Save; the refresh queues behind it, as TelegramPlaylistRuntime does
        pool.execute { gate.await(2, TimeUnit.SECONDS); a.song(7) }
        val r = refresh(a.session, screen, submit = { task -> pool.execute { task(); done.countDown() } })
        r.requestRefresh(acct)
        assertTrue("nothing was applied before the Save finished", screen.applied.isEmpty())
        gate.countDown()
        assertTrue(done.await(3, TimeUnit.SECONDS))
        assertEquals(listOf(saved(7)), screen.shown)
    }

    // ---- size, flags, network ----------------------------------------------------------------------------------------------

    @Test fun r3_moreThanThreeHundredSongsAreSafe() {
        // one device can save at most 300 (the local policy); a second device's songs bring the shared list past 300
        val a = device("dev-a"); val c = device("dev-c"); val b = device("dev-b")
        for (n in 1..300) a.song(n)
        for (n in 301..350) c.song(n)
        var guard = 0
        while ((a.pendingIds().isNotEmpty() || c.pendingIds().isNotEmpty()) && guard++ < 40) { a.sync(); c.sync() }
        guard = 0
        while (b.present().size < 350 && guard++ < 40) b.sync()
        val screen = Screen(account = acct)
        refresh(b.session, screen).requestRefresh(acct)
        assertEquals(350, screen.shown.size)
        assertEquals(350, screen.shown.toSet().size)
    }

    @Test fun r3_theRefreshMakesNoNetworkCallAndTouchesNoQueue() {
        val a = device("dev-a")
        a.song(1); a.song(2)
        val fetches = channel.fetchCalls
        val sends = channel.sendCalls
        val outbox = a.stored().outbox
        val screen = Screen(account = acct)
        repeat(10) { refresh(a.session, screen).requestRefresh(acct) }
        assertEquals(fetches, channel.fetchCalls)
        assertEquals(sends, channel.sendCalls)
        assertEquals("the outbox is untouched", outbox, a.stored().outbox)
    }

    @Test fun r3_onlyTheSavedListIsHandedOver_notDownloadedOrPlayingState() {
        val sources = File(listOf(File("src"), File("app/src")).first { it.isDirectory }, "shareable/java/com/bsdung/musicassistantcar/data/telegram")
        val helper = File(sources, "PlaylistLiveRefresh.kt").readText().replace("\r\n", "\n")
        assertTrue(helper.contains("snapshotFor(account)"))
        for (forbidden in listOf("runSync", "runOnce", "TdlibPlaylistSyncService", "SyncRuntime", "send(", "fetchAfter", "ExoPlayer", "Player", "download")) {
            assertFalse("helper must not use $forbidden", helper.contains(forbidden))
        }
    }

    @Test fun r3_theActivityResumesTheListLocallyAndNeverStartsASync() {
        val sources = File(listOf(File("src"), File("app/src")).first { it.isDirectory }, "shareable/java/com/bsdung/musicassistantcar/data/telegram")
        val activity = File(sources, "TelegramPreviewActivity.kt").readText().replace("\r\n", "\n")
        val onResume = activity.substringAfter("override fun onResume() {").substringBefore("private val playbackListener")
        assertTrue("onResume reloads the saved list", onResume.contains("reloadSavedPlaylistLive"))
        for (forbidden in listOf("runSync", "runOnce", "SyncRuntime", "actions()", "TdlibPlaylistSyncService", "grantSend")) {
            assertFalse("onResume must not call $forbidden", onResume.contains(forbidden))
        }
        val reload = activity.substringAfter("private fun reloadSavedPlaylistLive").substringBefore("private fun refreshPlaylistStatus")
        for (forbidden in listOf("runSync", "runOnce", "SyncRuntime", "TdlibPlaylistSyncService")) {
            assertFalse("reload must not call $forbidden", reload.contains(forbidden))
        }
        assertTrue("an account change drops what is in flight", activity.contains("liveRefresh.invalidate()"))
        assertTrue("a remote-saved song missing from the index is a placeholder, not dropped", activity.contains("placeholderSong"))
    }
}
