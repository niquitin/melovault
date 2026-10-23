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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * E96 P3-06: the P2 coordinator, the real V2 session and the real owner core, over a fake native TDLib that several
 * devices share. Convergence, recovery and the manual hooks - nothing here is a real Telegram.
 */
class TdlibSyncIntegrationTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val world = FakeTdlibWorld()
    private val chat by lazy { world.createSyncChannel(acct.value).id }
    private val rigs = ArrayList<SyncRig>()
    private val pool = Executors.newCachedThreadPool()

    private fun rig(name: String, w: FakeTdlibWorld = world, channel: Long = chat, config: SyncConfig = SyncConfig(), account: TelegramUserId = acct) =
        SyncRig(name, folder.newFolder(), w, account, channel, seed = name.sumOf { it.code } % 90 + 1, config = config).also { rigs.add(it) }

    @After fun tearDown() {
        pool.shutdownNow()
        rigs.forEach { it.stop() }
    }

    private fun syncTexts(w: FakeTdlibWorld = world, c: Long = chat) = w.texts(c).filter { it.startsWith(PlaylistSyncProtocol.MARKER) }

    @Test fun i01_twoDevicesConvergeIncludingAnOfflineRemoveAndAnOfflineAdd() {
        val a = rig("dev-a")
        val b = rig("dev-b")
        for (n in 1..3) a.device.toggle(acct.value, songId(n))
        assertEquals(SyncStatus.OK, a.run().status)
        assertEquals(SyncStatus.OK, b.run().status)
        assertEquals(a.device.present().toSet(), b.device.present().toSet())
        assertEquals(3, b.device.present().size)
        // Both go "offline": B removes, A adds. Then both sync twice.
        b.device.toggle(acct.value, songId(2))
        a.device.toggle(acct.value, songId(4))
        repeat(2) { assertEquals(SyncStatus.OK, a.run().status); assertEquals(SyncStatus.OK, b.run().status) }
        assertEquals(setOf(songId(1), songId(3), songId(4)).map { TelegramSongKey.forMessage(acct.value, it, acct.value) }.toSet(),
            a.device.present().toSet())
        assertEquals(a.device.present().toSet(), b.device.present().toSet())
        assertEquals("tombstones converge too", a.device.crdt(), b.device.crdt())
        assertEquals(emptyList<String>(), a.device.pendingIds() + b.device.pendingIds())
        assertEquals(3, syncTexts().size)
    }

    @Test fun i02_aDifferentAccountSharesNothingAndAForeignScopeMessageIsRefusedNotApplied() {
        val a = rig("dev-a")
        a.device.toggle(acct.value, songId(1))
        assertEquals(SyncStatus.OK, a.run().status)
        // Another account in its own cloud: its device never sees account 1001's channel.
        val otherWorld = FakeTdlibWorld(5)
        val otherAcct = TelegramUserId(2002L)
        val otherChat = otherWorld.createSyncChannel(otherAcct.value).id
        val other = rig("dev-o", otherWorld, otherChat, account = otherAcct)
        assertEquals(SyncStatus.OK, other.run().status)
        assertEquals(emptyList<Long>(), other.device.present())
        assertEquals(0, syncTexts(otherWorld, otherChat).size)
        // A message written for another account that lands in this channel is not applied.
        world.post(chat, envelopeText(2002L, "dev-x", addOp("dev-x", 1, 2002L, songId(9))))
        val bad = a.run()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, bad.status)
        assertEquals(RecoveryReason.WRONG_ACCOUNT, bad.recovery)
        assertEquals(listOf(TelegramSongKey.forMessage(acct.value, songId(1), acct.value)), a.device.present())
    }

    @Test fun i03_aSendWhoseAnswerIsLostIsFoundAgainByReadingAndNeverSentTwice() {
        val a = rig("dev-a")
        a.device.toggle(acct.value, songId(1))
        val idsBefore = a.device.pendingIds()
        a.tdlib.gateway.sendModes.add(SendMode.NO_UPDATE_STORED)
        val first = a.run()
        assertEquals("not acknowledged on a provisional answer", SyncStatus.BACKOFF, first.status)
        assertEquals("same operation ids, still waiting", idsBefore, a.device.pendingIds())
        // The process is killed and restarted; the next run reads the history and finds its own operation.
        a.restart()
        val second = a.run()
        assertEquals(SyncStatus.OK, second.status)
        assertEquals(emptyList<String>(), a.device.pendingIds())
        assertEquals("the message exists exactly once", 1, syncTexts().size)
        assertEquals("the second run sent nothing", 1, a.tdlib.gateway.count("sendMessage"))
    }

    @Test fun i04_aFloodWaitIsObeyedExactlyAndNothingIsSentUntilItIsOver() {
        val a = rig("dev-a")
        a.device.toggle(acct.value, songId(1))
        a.tdlib.gateway.sendModes.add(SendMode.FAIL_UPDATE)
        val first = a.run()
        assertEquals(SyncStatus.BACKOFF, first.status)
        assertEquals(35_000L, first.retryAfterMs)
        val sends = a.tdlib.gateway.count("sendMessage")
        val early = a.run()
        assertEquals(SyncStatus.BACKOFF_ACTIVE, early.status)
        a.device.nowMs = 34_999L
        assertEquals(SyncStatus.BACKOFF_ACTIVE, a.run().status)
        assertEquals("no request while waiting", sends, a.tdlib.gateway.count("sendMessage"))
        a.device.nowMs = 35_000L
        assertEquals(SyncStatus.OK, a.run().status)
        assertEquals(1, syncTexts().size)
    }

    @Test fun i05_aFullReplayContinuesFromItsStoredPositionAcrossRunsAndRestarts() {
        world.postMany(chat, 250)
        val a = rig("dev-a", config = SyncConfig(pageSize = 100, maxPagesPerRun = 1))
        val positions = ArrayList<Long>()
        var report = a.run(SyncMode.FULL_REPLAY)
        var guard = 0
        while (report.status == SyncStatus.CATCH_UP_INCOMPLETE && guard++ < 10) {
            assertTrue("moves forward", report.replayPosition > (positions.lastOrNull() ?: 0L))
            positions.add(report.replayPosition)
            if (guard == 1) a.restart()
            report = a.run(SyncMode.FULL_REPLAY)
        }
        assertEquals(SyncStatus.OK, report.status)
        assertEquals(world.ids(chat).last(), report.cursor)
        assertEquals(SyncCheckpoint.NO_REPLAY, report.replayPosition)
        assertEquals("three pages of at most 100", 3, positions.size + 1)
    }

    @Test fun i06_aHistoryOfOverTenThousandMessagesIsReadWithoutALossAndWithBoundedTraffic() {
        world.postMany(chat, 10_500)
        val a = rig("dev-a")
        var report = a.run(SyncMode.FULL_REPLAY)
        var runs = 1
        var lastCursor = 0L
        var totalRead = report.messagesRead
        while (report.status == SyncStatus.CATCH_UP_INCOMPLETE && runs < 6) {
            assertTrue(report.cursor >= lastCursor)
            lastCursor = report.cursor
            report = a.run(SyncMode.FULL_REPLAY)
            totalRead += report.messagesRead
            runs++
        }
        assertEquals(SyncStatus.OK, report.status)
        assertEquals(world.ids(chat).last(), report.cursor)
        assertTrue("runs=$runs", runs <= 3)
        val history = a.tdlib.gateway.count("getChatHistory")
        assertTrue("TDLib history requests: $history", history < 700)
        assertEquals("every message was read exactly once across the runs, none skipped", world.ids(chat).size, totalRead)
    }

    @Test fun i07_aMessageThatShowsUpBelowTheCursorIsFoundOnlyByAManualFullReplay() {
        val a = rig("dev-a")
        world.postMany(chat, 5)
        assertEquals(SyncStatus.OK, a.run().status)
        val cursor = a.device.stored().checkpoint.lastAppliedServerId
        // A late operation of another device, stored by the server with an id BELOW the cursor.
        val lateId = cursor - (1L shl 19)
        val text = envelopeText(acct.value, "dev-late", addOp("dev-late", 1, acct.value, songId(7)))
        synchronized(world) { world.chats.getValue(chat).messages[lateId] = FakeMsg(lateId, chat, text) }
        assertEquals(SyncStatus.OK, a.run().status)
        assertEquals("the incremental read cannot see it - by design", emptyList<Long>(), a.device.present())
        assertEquals(SyncStatus.OK, a.run(SyncMode.FULL_REPLAY).status)
        assertEquals(listOf(TelegramSongKey.forMessage(acct.value, songId(7), acct.value)), a.device.present())
    }

    @Test fun i08_evidenceOfADeletedMessageStopsTheReadWithRecoveryAndKeepsEverything() {
        val a = rig("dev-a")
        a.device.toggle(acct.value, songId(1))
        assertEquals(SyncStatus.OK, a.run().status)
        a.device.toggle(acct.value, songId(2))
        val stateBefore = a.device.stored()
        assertEquals(Gated.Done(true), a.service.attach(acct))
        a.tdlib.gateway.emit(JSONObject().put("@type", "updateDeleteMessages").put("chat_id", chat)
            .put("message_ids", org.json.JSONArray().put(1)).put("is_permanent", true).put("from_cache", false))
        assertTrue(waitUntil { a.service.continuityAlert() == ContinuityAlert.MESSAGES_DELETED })
        val sendsBefore = a.tdlib.gateway.count("sendMessage")
        val report = a.run()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.TRANSPORT_CONTRACT, report.recovery)
        assertEquals("nothing is sent while the log is in doubt", sendsBefore, a.tdlib.gateway.count("sendMessage"))
        assertEquals(stateBefore.outbox, a.device.stored().outbox)
        a.service.acknowledgeContinuity()
        a.device.nowMs = 10_000_000L
        assertEquals(SyncStatus.OK, a.run().status)
    }

    @Test fun i09_onlyOneRunAtATimeASecondCallIsRefusedAsBusy() {
        val a = rig("dev-a")
        world.postMany(chat, 5)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        a.tdlib.gateway.beforeHistory = { entered.countDown(); release.await(5, TimeUnit.SECONDS); a.tdlib.gateway.beforeHistory = null }
        val first = pool.submit<SyncReport> { a.run() }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        assertEquals(Gated.Denied(SyncDenied.BUSY), a.service.runSync(acct))
        release.countDown()
        assertEquals(SyncStatus.OK, first.get(5, TimeUnit.SECONDS).status)
    }

    @Test fun i10_theTriggerMergesRequestsDuringARunIntoOneMoreRunAndStopsAfterAFailure() {
        val a = rig("dev-a")
        world.postMany(chat, 5)
        val results = CopyOnWriteArrayList<Pair<SyncReason, SyncStatus?>>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val trigger = SyncTrigger(a.service, executor) { reason, result -> results.add(reason to (result as? Gated.Done)?.value?.status) }
        a.tdlib.gateway.beforeHistory = { entered.countDown(); release.await(5, TimeUnit.SECONDS); a.tdlib.gateway.beforeHistory = null }
        trigger.request(acct, SyncReason.MANUAL)
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        trigger.request(acct, SyncReason.AFTER_SAVE)
        trigger.request(acct, SyncReason.RESUME)
        trigger.request(acct, SyncReason.FULL_REPLAY)
        release.countDown()
        assertTrue(waitUntil { results.size == 2 })
        assertEquals(listOf(SyncReason.MANUAL, SyncReason.FULL_REPLAY), results.map { it.first })
        // A failing run is not repeated by a merged request.
        a.tdlib.gateway.failNext("getChat", 500, "boom")
        val entered2 = CountDownLatch(1)
        val release2 = CountDownLatch(1)
        a.tdlib.gateway.beforeHistory = { entered2.countDown(); release2.await(5, TimeUnit.SECONDS) }
        a.device.nowMs = 1_000L
        a.tdlib.gateway.requests.clear()
        trigger.request(acct, SyncReason.MANUAL)
        Thread.sleep(300)
        trigger.request(acct, SyncReason.RESUME)
        release2.countDown()
        Thread.sleep(300)
        executor.shutdown()
        assertTrue(results.size <= 4)
    }

    @Test fun i11_aTriggerWithTheSwitchOffDoesNothingAtAll() {
        val a = SyncRig("dev-off", folder.newFolder(), world, acct, chat, 5, buildEnabled = false)
        rigs.add(a)
        val results = CopyOnWriteArrayList<Gated<SyncReport>>()
        val executor = Executors.newSingleThreadExecutor()
        SyncTrigger(a.service, executor) { _, r -> results.add(r) }.request(acct, SyncReason.MANUAL)
        assertTrue(waitUntil { results.size == 1 })
        executor.shutdown()
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), results.single())
        assertEquals(emptyList<String>(), a.tdlib.gateway.typesSeen())
    }

    @Test fun i12_aSessionRevokedAnswerBecomesRecoveryNotARetryLoop() {
        val a = rig("dev-a")
        a.device.toggle(acct.value, songId(1))
        a.tdlib.gateway.failNext("getChat", 401, "AUTH_KEY_UNREGISTERED")
        val report = a.run()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.SESSION_REVOKED, report.recovery)
        assertNotEquals(0L, report.retryAfterMs)
        assertEquals(0, a.tdlib.gateway.count("sendMessage"))
        assertFalse(a.device.pendingIds().isEmpty())
    }
}
