package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * E96 P5B3 FINAL LIVE QA: the synthetic Playlist item that lets a real two-device sync be proven without naming any song of the
 * person's library. Inert outside a Sync-QA build; one marker = at most one change; each step once per install; never sends.
 */
class PlaylistQaFixtureTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val key = PlaylistQaFixture.target(acct).songKey
    private val sources = File(listOf(File("src"), File("app/src")).first { it.isDirectory }, "shareable/java/com/bsdung/musicassistantcar/data/telegram")

    private fun text(name: String) = File(sources, name).readText().replace("\r\n", "\n")

    private val real = listOf(4_194_304L, 8_388_608L)

    // ---- the decision ------------------------------------------------------------------------------

    @Test fun f1_aStandardOrReleaseBuildNeverActs() {
        for (add in listOf(true, false)) for (remove in listOf(true, false)) {
            assertNull(PlaylistQaFixture.decide(false, add, remove, real, key, emptySet()))
            assertNull(PlaylistQaFixture.decide(false, add, remove, real + key, key, emptySet()))
        }
    }

    @Test fun f2_oneMarkerIsOneStep_andOnlyWhenTheListAllowsIt() {
        assertEquals(PlaylistQaFixture.Step.ADD, PlaylistQaFixture.decide(true, true, false, real, key, emptySet()))
        assertEquals(PlaylistQaFixture.Step.REMOVE, PlaylistQaFixture.decide(true, false, true, real + key, key, emptySet()))
        assertNull("nothing to add twice", PlaylistQaFixture.decide(true, true, false, real + key, key, emptySet()))
        assertNull("nothing to remove that is not there", PlaylistQaFixture.decide(true, false, true, real, key, emptySet()))
        assertNull("no marker, no step", PlaylistQaFixture.decide(true, false, false, real, key, emptySet()))
        assertNull("both markers is ambiguous", PlaylistQaFixture.decide(true, true, true, real, key, emptySet()))
    }

    @Test fun f3_eachStepRunsOnlyOncePerInstall() {
        assertNull(PlaylistQaFixture.decide(true, true, false, real, key, setOf(PlaylistQaFixture.Step.ADD)))
        assertNull(PlaylistQaFixture.decide(true, false, true, real + key, key, setOf(PlaylistQaFixture.Step.REMOVE)))
        // a used ADD does not use up the REMOVE (they are the two operations of the whole test)
        assertEquals(
            PlaylistQaFixture.Step.REMOVE,
            PlaylistQaFixture.decide(true, false, true, real + key, key, setOf(PlaylistQaFixture.Step.ADD)),
        )
    }

    @Test fun f4_theSyntheticItemIsNotAnyRealSong() {
        val t = PlaylistQaFixture.target(acct)
        assertEquals(PlaylistQaFixture.CHAT_ID, t.chatId)
        assertEquals(PlaylistQaFixture.MESSAGE_ID, t.rawMessageId)
        assertTrue("a supergroup-style id no real account has", PlaylistQaFixture.CHAT_ID < -1_000_000_000_000L)
        assertEquals(0L, PlaylistQaFixture.MESSAGE_ID % 1_048_576L)
        assertTrue(key !in real)
        assertEquals(t.songKey, PlaylistItemId.Telegram(t.chatId, t.rawMessageId).songKey(acct.value))
    }

    // ---- what the item does: two devices, one real channel stand-in ---------------------------------

    @Test fun f5_addOnOneDevice_thenRemoveOnTheOther_isExactlyTwoOperations_andTheRealSongsNeverMove() {
        val channel = FakeTelegramChannel()
        val a = FakeSyncDevice("dev-a", folder.newFolder("dev-a"), acct, channel, seed = 5)
        val b = FakeSyncDevice("dev-b", folder.newFolder("dev-b"), acct, channel, seed = 9)
        a.toggle(acct.value, 4_194_304L); a.toggle(acct.value, 8_388_608L)
        a.sync()
        b.coordinator.runOnce(acct, SyncMode.INCREMENTAL, 0)
        assertEquals(a.present(), b.present())
        val realOnly = a.present()
        val sendsBefore = channel.sendCalls

        // device A: ADD the synthetic item (queues one operation) and send exactly it
        val added = a.session.toggle(acct, PlaylistQaFixture.target(acct))
        assertTrue(added is TelegramPlaylistSession.ToggleOutcome.Added)
        assertEquals(1, a.session.statusFacts(acct)!!.pendingCount)
        val sentA = a.coordinator.runOnce(acct, SyncMode.INCREMENTAL, 1)
        assertEquals(1, sentA.opsDispatched)
        assertEquals(sendsBefore + 1, channel.sendCalls)

        // device B: read-only catch-up (budget 0) shows 3 without sending anything
        val readB = b.coordinator.runOnce(acct, SyncMode.INCREMENTAL, 0)
        assertEquals(0, readB.opsDispatched)
        assertEquals(sendsBefore + 1, channel.sendCalls)
        assertEquals(realOnly + key, b.present())

        // device B: REMOVE the same item (one operation) and send exactly it
        val removed = b.session.toggle(acct, PlaylistQaFixture.target(acct))
        assertTrue(removed is TelegramPlaylistSession.ToggleOutcome.Removed)
        assertEquals(1, b.session.statusFacts(acct)!!.pendingCount)
        val sentB = b.coordinator.runOnce(acct, SyncMode.INCREMENTAL, 1)
        assertEquals(1, sentB.opsDispatched)
        assertEquals(sendsBefore + 2, channel.sendCalls)

        // device A: read-only catch-up shows the two real songs again, in the original order
        val readA = a.coordinator.runOnce(acct, SyncMode.INCREMENTAL, 0)
        assertEquals(0, readA.opsDispatched)
        assertEquals(realOnly, a.present())
        assertEquals(realOnly, b.present())
        assertEquals("exactly two operations in total", sendsBefore + 2, channel.sendCalls)
        assertEquals(0, a.session.statusFacts(acct)!!.pendingCount)
        assertEquals(0, b.session.statusFacts(acct)!!.pendingCount)
    }

    // ---- wiring (source guards) --------------------------------------------------------------------

    @Test fun f6_theFixtureNeverSendsAndNeverTouchesConsent() {
        val fixture = text("PlaylistQaFixture.kt")
        for (forbidden in listOf("TdlibPlaylistSyncService", "runSync", "runOnce", "SyncConsent", "FileSyncConsentStore", "grantSend", "sendMessage", "writeText", "delete(")) {
            assertFalse("fixture must not use $forbidden", fixture.contains(forbidden))
        }
    }

    @Test fun f7_theActivityRunsItOnlyInASyncQaBuild_consumesTheMarker_andRecordsTheStepBeforeTheWrite() {
        val activity = text("TelegramPreviewActivity.kt")
        val fn = activity.substringAfter("private fun runQaFixtureIfRequested(").substringBefore("/** Local only: asks the playlist thread")
        assertTrue(fn.indexOf("if (!TelegramPlaylistFlags.SYNC_QA_ENABLED) return") in 0 until fn.indexOf("val addMarker"))
        assertTrue(fn.indexOf("addMarker.delete()") in 0 until fn.indexOf("playlistSession.toggle("))
        assertTrue("the step is recorded before the change", fn.indexOf("usedFile.writeText") in 0 until fn.indexOf("playlistSession.toggle("))
        assertEquals("one toggle only", 1, Regex("playlistSession\\.toggle\\(").findAll(fn).count())
        assertTrue(activity.contains("runQaFixtureIfRequested(it)"))
    }
}
