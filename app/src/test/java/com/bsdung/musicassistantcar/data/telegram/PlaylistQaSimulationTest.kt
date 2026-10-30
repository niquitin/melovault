package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * E96 P5B3 REWORK-04 (blocker B): the display-only simulation that lets a real device show `changed=true` without a Telegram write
 * and without changing the person's playlist. It must be inert everywhere except a Sync-QA build with its marker file, and it must
 * never write anything.
 */
class PlaylistQaSimulationTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val sources = File(listOf(File("src"), File("app/src")).first { it.isDirectory }, "shareable/java/com/bsdung/musicassistantcar/data/telegram")

    private fun text(name: String) = File(sources, name).readText().replace("\r\n", "\n")

    @Test fun r4_aStandardOrReleaseBuildIgnoresTheMarkerCompletely() {
        val ids = listOf(1L, 2L)
        assertSame(ids, PlaylistQaSimulation.apply(ids, enabled = false, markerPresent = true))
        assertSame(ids, PlaylistQaSimulation.apply(ids, enabled = false, markerPresent = false))
        assertSame(ids, PlaylistQaSimulation.apply(ids, enabled = true, markerPresent = false))
        assertNull(PlaylistQaSimulation.apply(null, enabled = true, markerPresent = true))
    }

    @Test fun r4_inAQaBuildWithTheMarkerOneUnknownSongIsAddedOnce() {
        val ids = listOf(1L, 2L)
        val sim = PlaylistQaSimulation.apply(ids, enabled = true, markerPresent = true)!!
        assertEquals(ids + PlaylistQaSimulation.SIMULATED_KEY, sim)
        assertEquals("not added twice", sim, PlaylistQaSimulation.apply(sim, enabled = true, markerPresent = true))
    }

    @Test fun r4_theLiveRefreshShowsChangedTrueThenBackToTheRealList_andNothingIsWritten() {
        val channel = FakeTelegramChannel()
        val dev = FakeSyncDevice("dev-a", folder.newFolder("dev-a"), acct, channel, seed = 5)
        dev.toggle(acct.value, 4_194_304L); dev.toggle(acct.value, 8_388_608L)
        val before = dev.stored()
        val stateFiles = dev.root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dev.root).path to it.readBytes().toList() }
        var marker = false
        var shown = dev.session.snapshot()
        val results = ArrayList<Pair<Int?, Boolean>>()
        val refresh = PlaylistLiveRefresh(
            session = dev.session, submit = { it() }, toUi = { it() }, alive = { true }, currentAccount = { acct },
            shown = { shown }, apply = { shown = it }, onResult = { n, c -> results.add(n to c) },
            transform = { PlaylistQaSimulation.apply(it, enabled = true, markerPresent = marker) },
        )
        refresh.requestRefresh(acct)
        assertEquals(listOf<Pair<Int?, Boolean>>(2 to false), results)
        marker = true
        refresh.requestRefresh(acct)
        assertEquals(3, shown.size)
        assertTrue(PlaylistQaSimulation.SIMULATED_KEY in shown)
        marker = false
        refresh.requestRefresh(acct)
        assertEquals(2, shown.size)
        assertEquals(listOf<Pair<Int?, Boolean>>(2 to false, 3 to true, 2 to true), results)
        // the stored playlist, the outbox and every file on disk are byte-identical
        assertEquals(before.outbox, dev.stored().outbox)
        assertEquals(before.orSet, dev.stored().orSet)
        assertEquals(stateFiles, dev.root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dev.root).path to it.readBytes().toList() })
        assertEquals(0, channel.sendCalls)
        assertEquals(0, channel.fetchCalls)
    }

    @Test fun r4_theSimulationCodeWritesNothingAndIsOnlyReadFromTheCacheMarker() {
        val sim = text("PlaylistQaSimulation.kt")
        for (forbidden in listOf("toggle(", "update(", "writeText", "writeBytes", "createNewFile", "delete(", "TdlibPlaylistSyncService", "runSync")) {
            assertFalse("simulation must not use $forbidden", sim.contains(forbidden))
        }
        val activity = text("TelegramPreviewActivity.kt")
        assertTrue(activity.contains("if (!TelegramPlaylistFlags.SYNC_QA_ENABLED) ids else PlaylistQaSimulation.apply("))
        assertTrue(activity.contains("java.io.File(cacheDir, PlaylistQaSimulation.MARKER_FILE).exists()"))
        assertTrue("only a Sync-QA build can switch it on", activity.contains("ids, TelegramPlaylistFlags.SYNC_QA_ENABLED, java.io.File(cacheDir"))
    }

    @Test fun r4_theCompactStatusLineMayUseTwoLines_soTheLongTruthfulLabelIsNotCut() {
        val compact = text("MeloCompactScreen.kt")
        assertTrue(compact.contains("private val downloadedLine = c.text(\"\", s.secondaryText, MeloCompactPolicy.DOWNLOADED_LINE_COLOR, maxLines = 2)"))
    }
}
