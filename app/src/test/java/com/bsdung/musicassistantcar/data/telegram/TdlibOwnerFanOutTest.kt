package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch

/**
 * E96 P3-01 / P3-05: the sync sink rides on the ONE existing TDLib owner. UI and playback keep their listeners, nobody
 * steals or duplicates events, and the sync does not keep the client alive.
 */
class TdlibOwnerFanOutTest {
    @get:Rule val folder = TemporaryFolder()
    private val world = FakeTdlibWorld()
    private val rigs = ArrayList<TdlibRig>()

    private fun rig(withPlayback: Boolean = true): TdlibRig =
        TdlibRig(world, 1001L, folder.newFolder()).start(withPlayback = withPlayback).also { rigs.add(it) }

    @After fun tearDown() = rigs.forEach { it.stop() }

    private fun update(type: String) = JSONObject().put("@type", type)
    private fun newMessage(i: Int) = update("updateNewMessage").put("message", JSONObject().put("id", i).put("chat_id", 5))
    private fun syncThreads() = Thread.getAllStackTraces().keys.count { it.isAlive && it.name == "MeloVault-Sync-Dispatch" }

    @Test fun f01_uiPlaybackAndSyncEachSeeAnUntaggedUpdateExactlyOnceInOrder() {
        val r = rig()
        val sink = RecordingSink()
        assertTrue(r.core.bindSync(sink))
        for (i in 1..5) r.gateway.emit(newMessage(i))
        assertTrue(waitUntil { sink.events.size == 5 && r.uiEvents.size == 5 && r.playbackEvents.size == 5 })
        val ids = { l: List<JSONObject> -> l.map { it.getJSONObject("message").getInt("id") } }
        assertEquals((1..5).toList(), ids(sink.events))
        assertEquals((1..5).toList(), ids(r.uiEvents))
        assertEquals((1..5).toList(), ids(r.playbackEvents))
        r.core.unbindSync(sink)
    }

    @Test fun f02_syncTaggedAnswersGoToTheSyncSinkOnlyNeverToUiOrPlayback() {
        val r = rig()
        val sink = RecordingSink()
        r.core.bindSync(sink)
        r.core.send("getMe", SYNC_TAG_PREFIX + "getMe:1:1")
        assertTrue(waitUntil { sink.events.size == 1 })
        r.gateway.emit(update("updateOption"))
        assertTrue(waitUntil { r.uiEvents.size == 1 })
        assertTrue("the UI saw no sync answer", r.uiEvents.none { it.optString("@extra").startsWith(SYNC_TAG_PREFIX) })
        assertTrue(r.playbackEvents.none { it.optString("@extra").startsWith(SYNC_TAG_PREFIX) })
        assertEquals("only the sync answer, no untagged update", 1, sink.events.size)
    }

    @Test fun f03_answersToUiRequestsNeverReachTheSyncSink() {
        val r = rig()
        val sink = RecordingSink()
        r.core.bindSync(sink)
        r.core.send("getMe", "identity")
        assertTrue(waitUntil { r.uiEvents.any { it.optString("@extra") == "identity" } })
        assertTrue(r.playbackEvents.any { it.optString("@extra") == "identity" })
        Thread.sleep(60)
        assertEquals("sync did not steal or duplicate a UI answer", 0, sink.events.size)
    }

    @Test fun f04_oneClientNoMatterHowManyBindings() {
        val r = rig()
        val sink = RecordingSink()
        r.core.bindSync(sink)
        r.core.attachUi(r.dir, r.ui) // the same slot again: no new client
        r.core.unbindSync(sink)
        r.core.bindSync(RecordingSink())
        assertEquals(1, r.gateway.clientsCreated)
    }

    @Test fun f05_unbindReleasesTheSinkAndItsThreadWhileUiAndPlaybackKeepWorking() {
        val r = rig()
        val before = syncThreads()
        val sink = RecordingSink()
        r.core.bindSync(sink)
        assertEquals(before + 1, syncThreads())
        r.gateway.emit(newMessage(1))
        assertTrue(waitUntil { sink.events.size == 1 })
        r.core.unbindSync(sink)
        assertTrue("the dispatch thread ended", waitUntil { syncThreads() == before })
        r.gateway.emit(newMessage(2))
        assertTrue(waitUntil { r.uiEvents.size == 2 && r.playbackEvents.size == 2 })
        assertEquals("nothing reaches an unbound sink", 1, sink.events.size)
        assertFalse(r.core.syncIsBound())
        assertTrue(r.core.clientIsActive())
    }

    @Test fun f06_theSyncAloneNeverKeepsTheClientAlive() {
        val r = rig()
        val sink = RecordingSink()
        r.core.bindSync(sink)
        r.core.detachUi(r.ui)
        assertTrue("playback still owns it", r.core.clientIsActive())
        assertEquals(0, r.gateway.closeRequests)
        r.core.unbindPlayback()
        assertFalse(r.core.clientIsActive())
        assertEquals("the client was asked to close", 1, r.gateway.closeRequests)
        assertEquals("the sink was told", 1, sink.closed)
        assertFalse(r.core.syncIsBound())
        assertEquals(null, r.core.authorizedAccount())
    }

    @Test fun f07_aSecondSinkIsRefusedAndTheFirstKeepsWorking() {
        val r = rig()
        val first = RecordingSink()
        val second = RecordingSink()
        assertTrue(r.core.bindSync(first))
        assertFalse(r.core.bindSync(second))
        r.core.unbindSync(second) // not the bound one: no effect
        r.gateway.emit(newMessage(1))
        assertTrue(waitUntil { first.events.size == 1 })
        assertEquals(0, second.events.size)
    }

    @Test fun f08_aStuckSinkNeverBlocksTheReceiveLoopAndOverflowIsReported() {
        val r = rig()
        val gate = CountDownLatch(1)
        val sink = RecordingSink { gate.await() }
        r.core.bindSync(sink)
        for (i in 1..900) r.gateway.emit(newMessage(i))
        assertTrue("UI and playback got everything while the sink was stuck", waitUntil(5_000) { r.uiEvents.size == 900 && r.playbackEvents.size == 900 })
        assertTrue("the full queue was reported", waitUntil { sink.overflow >= 1 })
        gate.countDown()
    }

    @Test fun f09_theAuthEpochChangesOnEveryLoginLogoutAndSameAccountLogin() {
        val r = rig()
        val a = r.core.authEpoch()
        r.core.revokeAuthorization()
        val b = r.core.authEpoch()
        r.core.markGetMeVerified(1001L)
        val c = r.core.authEpoch()
        assertTrue(a < b && b < c)
        assertEquals(1001L, r.core.authorizedAccount())
        r.relogin(1001L)
        assertNotEquals("logout then login of the SAME account is a new epoch", c, r.core.authEpoch())
    }

    @Test fun f10_noiseTheSyncDoesNotNeedIsNotQueuedForIt() {
        val r = rig()
        val sink = RecordingSink()
        r.core.bindSync(sink)
        r.gateway.emit(update("updateFile"))
        r.gateway.emit(update("updateOption"))
        r.gateway.emit(newMessage(9))
        assertTrue(waitUntil { r.uiEvents.size == 3 })
        assertTrue(waitUntil { sink.events.size == 1 })
        Thread.sleep(40)
        assertEquals(listOf("updateNewMessage"), sink.events.map { it.getString("@type") })
    }

    @Test fun f11_withTheBuildSwitchOffNormalLaunchNeverSendsOrCreatesOrFetchesAnything() {
        val s = SyncRig("dev-a", folder.newFolder(), world, TelegramUserId(1001L), channelId = 0L, seed = 3, buildEnabled = false, consent = true)
        // The normal app flow: UI and playback talk to TDLib as usual.
        s.tdlib.core.send("getAuthorizationState", "state")
        s.tdlib.core.send("getMe", "identity")
        assertTrue(waitUntil { s.tdlib.uiEvents.size >= 2 })
        // Every sync entry point is closed.
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), s.service.discoverChannel(s.account))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), s.service.createChannel(s.account))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), s.service.bindChannel(s.account, 5L))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), s.service.runSync(s.account, SyncMode.FULL_REPLAY))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), s.service.attach(s.account))
        val forbidden = setOf("sendMessage", "createNewSupergroupChat", "getChatHistory", "searchChats", "getChats", "getChat", "getSupergroup", "getSupergroupFullInfo", "loadChats")
        assertEquals("no sync request of any kind", emptyList<String>(), s.tdlib.gateway.typesSeen().filter { it in forbidden })
        assertFalse("the sync sink was never bound", s.tdlib.core.syncIsBound())
        assertEquals(5, s.service.metrics.get(SyncMetrics.Event.GATE_REFUSED))
        s.stop()
    }

    @Test fun f12_productFeatureStillRequiresAccountConsent() {
        assertTrue(TelegramPlaylistFlags.SYNC_ENABLED)
        val denied = SyncGate(store = InMemorySyncConsentStore()).mayRun(TelegramUserId(1L))
        assertEquals(SyncDenied.NOT_ENABLED, denied)
        assertEquals(SyncDenied.BUILD_OFF, SyncGate(false, InMemorySyncConsentStore()).mayRun(TelegramUserId(1L)))
    }
}
