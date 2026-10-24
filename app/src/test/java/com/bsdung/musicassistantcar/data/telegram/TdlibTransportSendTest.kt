package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * E96 P3-01 / P3-03: request correlation, stale-identity gates, and the send acknowledgement rules, all against a
 * fake native TDLib. Nothing here touches a real Telegram.
 */
class TdlibTransportSendTest {
    @get:Rule val folder = TemporaryFolder()
    private val user = 1001L
    private val world = FakeTdlibWorld()
    private val pool = Executors.newCachedThreadPool()
    private lateinit var rig: TdlibRig
    private var chat = 0L
    private lateinit var hub: TdlibSyncHub

    @Before fun setUp() {
        rig = TdlibRig(world, user, folder.newFolder()).start()
        chat = world.createSyncChannel(user).id
        hub = TdlibSyncHub(rig.core).also { check(it.open()) }
    }

    @After fun tearDown() {
        hub.close()
        pool.shutdownNow()
        rig.stop()
    }

    private fun transport(
        epoch: Long = rig.core.authEpoch(), allowed: () -> Boolean = { true }, sendAckMs: Long = 500L,
    ): TdlibPlaylistSyncTransport {
        val reader = TdlibHistoryReader(hub, user, epoch, chat, HistoryLimits(requestTimeoutMs = 1_500L))
        return TdlibPlaylistSyncTransport(hub, user, epoch, chat, reader, TdlibTransportTimeouts(1_500L, sendAckMs), allowed)
    }

    private fun text(n: Int = 1) = envelopeText(user, "dev-a", addOp("dev-a", n.toLong(), user, songId(n)))
    private fun <T> async(block: () -> T): Future<T> = pool.submit(Callable { block() })
    private fun syncMessages() = world.texts(chat).filter { it.startsWith(PlaylistSyncProtocol.MARKER) }

    // ---- hub: correlation and gates ----------------------------------------------------------------

    @Test fun h01_concurrentRequestsGetTheirOwnAnswersEvenWhenAnswersArriveInTheOtherOrder() {
        rig.gateway.held = true
        val epoch = rig.core.authEpoch()
        val a = async { hub.call(user, epoch, "getMe", 2_000L) }
        val b = async { hub.call(user, epoch, "getChat", 2_000L) { it.put("chat_id", chat) } }
        assertTrue(waitUntil { rig.gateway.heldCount() == 2 })
        rig.gateway.held = false
        rig.gateway.releaseReversed()
        val ra = a.get(3, TimeUnit.SECONDS) as TdlibSyncHub.Reply.Ok
        val rb = b.get(3, TimeUnit.SECONDS) as TdlibSyncHub.Reply.Ok
        assertEquals("user", ra.json.getString("@type"))
        assertEquals("chat", rb.json.getString("@type"))
    }

    @Test fun h02_anAnswerAfterTheTimeoutIsThrownAwayNotUsed() {
        rig.gateway.held = true
        val reply = hub.call(user, rig.core.authEpoch(), "getMe", 80L)
        assertEquals(TdlibSyncHub.Reply.Timeout, reply)
        rig.gateway.held = false
        rig.gateway.release()
        assertTrue(waitUntil { hub.metrics.get(SyncMetrics.Event.LATE_RESPONSE_DROPPED) == 1 })
    }

    @Test fun h03_aBlockingCallIsRefusedOnTheDispatchThread() {
        hub.watchChannel(chat)
        val refused = AtomicReference<Boolean?>(null)
        hub.onRemoteMessage = {
            refused.set(try { hub.call(user, rig.core.authEpoch(), "getMe", 100L); false } catch (_: IllegalStateException) { true })
        }
        rig.gateway.emit(JSONObject().put("@type", "updateNewMessage").put("message", JSONObject().put("chat_id", chat).put("id", 1)))
        assertTrue(waitUntil { refused.get() != null })
        assertEquals(true, refused.get())
    }

    @Test fun h04_aDelayedAnswerOfAccountAIsNeverUsedAfterAThenBThenA() {
        val epochA1 = rig.core.authEpoch()
        rig.gateway.held = true
        val call = async { hub.call(user, epochA1, "getMe", 2_000L) }
        assertTrue(waitUntil { rig.gateway.heldCount() == 1 })
        rig.relogin(2002L) // B signs in
        rig.relogin(user)  // A signs in again
        rig.gateway.held = false
        rig.gateway.release()
        assertEquals("the answer belongs to an epoch that is gone", TdlibSyncHub.Reply.Aborted, call.get(3, TimeUnit.SECONDS))
        assertFalse(hub.identityHolds(user, epochA1))
        assertTrue(hub.identityHolds(user, rig.core.authEpoch()))
    }

    @Test fun h05_logoutAndLoginOfTheSameAccountMakesAnOldTransportUseless() {
        val t = transport()
        rig.relogin(user)
        val sent = t.send(chat, text())
        assertEquals(SendResult.Failed(SyncErrorKind.NETWORK), sent)
        assertEquals("nothing was handed to TDLib", 0, rig.gateway.count("sendMessage"))
        assertEquals(FetchResult.Failed(SyncErrorKind.NETWORK), t.fetchAfter(chat, 0L, 100))
        assertEquals(0, rig.gateway.count("getChatHistory"))
    }

    @Test fun h06_aTransportOfAnotherAccountIsRefusedBeforeDispatch() {
        val t = transport()
        rig.relogin(2002L)
        assertEquals(SendResult.Failed(SyncErrorKind.UNAUTHORIZED), t.send(chat, text()))
        assertEquals(FetchResult.Failed(SyncErrorKind.UNAUTHORIZED), t.fetchAfter(chat, 0L, 100))
        assertEquals(0, rig.gateway.count("sendMessage") + rig.gateway.count("getChatHistory"))
    }

    @Test fun h07_getMeMustAgreeWithTheAccountTheSyncWasStartedFor() {
        rig.gateway.userId = 9999L // TDLib says it is somebody else
        val t = transport()
        assertEquals(SendResult.Failed(SyncErrorKind.UNAUTHORIZED), t.send(chat, text()))
        assertEquals(0, rig.gateway.count("sendMessage"))
    }

    @Test fun h08_anAuthorizationStateThatIsNotReadyStopsInFlightWorkAndBlocksNewWork() {
        rig.gateway.held = true
        val epoch = rig.core.authEpoch()
        val call = async { hub.call(user, epoch, "getMe", 2_000L) }
        assertTrue(waitUntil { rig.gateway.heldCount() == 1 })
        rig.gateway.held = false // the update is delivered; the held getMe answer stays held
        rig.gateway.emit(JSONObject().put("@type", "updateAuthorizationState").put("authorization_state", JSONObject().put("@type", "authorizationStateLoggingOut")))
        assertEquals(TdlibSyncHub.Reply.Aborted, call.get(3, TimeUnit.SECONDS))
        assertFalse(hub.identityHolds(user, epoch))
        assertEquals(TdlibSyncHub.Reply.Aborted, hub.call(user, epoch, "getMe", 100L))
    }

    @Test fun h09_whenTheConnectionIsNotReadyNothingIsSent() {
        rig.gateway.connectionState("connectionStateWaitingForNetwork")
        assertTrue(waitUntil { !hub.connectionIsUsable() })
        assertEquals(SendResult.Failed(SyncErrorKind.NETWORK), transport().send(chat, text()))
        assertEquals(0, rig.gateway.count("sendMessage"))
        rig.gateway.connectionState("connectionStateReady")
        assertTrue(waitUntil { hub.connectionIsUsable() })
        assertTrue(transport().send(chat, text()) is SendResult.Sent)
    }

    @Test fun h10_errorMappingFollowsTheServerExactly() {
        assertEquals(SyncErrorKind.FLOOD_WAIT to 35, TdlibSyncErrors.kindOf(429, "Too Many Requests: retry after 35"))
        assertEquals(SyncErrorKind.FLOOD_WAIT to 7, TdlibSyncErrors.kindOf(420, "FLOOD_WAIT_7"))
        assertEquals("a huge number is not cut", SyncErrorKind.FLOOD_WAIT to 86_400, TdlibSyncErrors.kindOf(429, "Too Many Requests: retry after 86400"))
        assertEquals("no number named: a default, never zero", SyncErrorKind.FLOOD_WAIT to TdlibSyncErrors.DEFAULT_FLOOD_SECONDS, TdlibSyncErrors.kindOf(429, "Too Many Requests"))
        assertEquals(SyncErrorKind.SESSION_REVOKED to 0, TdlibSyncErrors.kindOf(401, "AUTH_KEY_UNREGISTERED"))
        assertEquals(SyncErrorKind.CHANNEL_MISSING to 0, TdlibSyncErrors.kindOf(400, "Chat not found"))
        assertEquals(SyncErrorKind.CHANNEL_MISSING to 0, TdlibSyncErrors.kindOf(403, "CHAT_WRITE_FORBIDDEN"))
        assertEquals(SyncErrorKind.UNAUTHORIZED to 0, TdlibSyncErrors.kindOf(403, "Forbidden"))
        assertEquals(SyncErrorKind.NETWORK to 0, TdlibSyncErrors.kindOf(500, "Internal error"))
    }

    // ---- send: provisional is not proof -------------------------------------------------------------

    @Test fun s01_theProvisionalMessageIsNotAnAckOnlyTheCorrelatedSuccessIs() {
        val sent = transport().send(chat, text()) as SendResult.Sent
        val stored = world.ids(chat).last()
        assertEquals("the id is the SERVER's, not the temporary one", stored, sent.serverMessageId)
        assertEquals(1, syncMessages().size)
        assertEquals(1, hub.metrics.get(SyncMetrics.Event.SEND_ACKED))
    }

    @Test fun s02_theSuccessUpdateMayArriveBeforeTheProvisionalAnswer() {
        rig.gateway.sendModes.add(SendMode.OK_UPDATE_BEFORE)
        val sent = transport().send(chat, text())
        assertTrue(sent is SendResult.Sent)
        assertEquals(world.ids(chat).last(), (sent as SendResult.Sent).serverMessageId)
    }

    @Test fun s03_aProvisionalAnswerWithoutAnySuccessIsUnknownNeverSent() {
        rig.gateway.sendModes.add(SendMode.NO_UPDATE_STORED)
        assertEquals(SendResult.Unknown(), transport().send(chat, text()))
        assertEquals(1, hub.metrics.get(SyncMetrics.Event.SEND_UNKNOWN))
    }

    @Test fun s04_aLostAnswerAndAnUnstoredMessageAreBothUnknown() {
        rig.gateway.sendModes.add(SendMode.NO_REPLY_STORED)
        assertEquals(SendResult.Unknown(), transport(sendAckMs = 100L).send(chat, text(1)))
        rig.gateway.sendModes.add(SendMode.NO_REPLY_NOT_STORED)
        val t = TdlibPlaylistSyncTransport(hub, user, rig.core.authEpoch(), chat,
            TdlibHistoryReader(hub, user, rig.core.authEpoch(), chat), TdlibTransportTimeouts(requestMs = 150L, sendAckMs = 100L))
        assertEquals(SendResult.Unknown(), t.send(chat, text(2)))
    }

    @Test fun s05_aFailedSendIsAClearFailureAndAFloodWaitKeepsTheServersNumber() {
        rig.gateway.sendModes.add(SendMode.FAIL_UPDATE)
        assertEquals(SendResult.Failed(SyncErrorKind.FLOOD_WAIT, 35), transport().send(chat, text()))
        rig.gateway.sendError = 500 to "Internal error"
        rig.gateway.sendModes.add(SendMode.FAIL_UPDATE)
        // A 5xx after the message was handed over may have been processed by the server: doubt, never "failed".
        val doubtful = transport().send(chat, text(2))
        assertEquals(SendResult.Unknown(), doubtful)
        assertEquals(SyncStage.SEND_ACK_FAILED, (doubtful as SendResult.Unknown).stage)
        // A definite refusal (4xx) after the provisional answer is a failure, with its stage and error class.
        rig.gateway.sendError = 400 to "Bad Request: message is too long"
        rig.gateway.sendModes.add(SendMode.FAIL_UPDATE)
        val refused = transport().send(chat, text(3))
        assertEquals(SendResult.Failed(SyncErrorKind.REJECTED, 0), refused)
        assertEquals(SyncStage.SEND_ACK_FAILED, (refused as SendResult.Failed).stage)
        assertEquals(ErrorBucket.E400, refused.bucket)
        assertEquals("nothing stored", 0, syncMessages().size)
    }

    @Test fun s06_anErrorAnswerToSendMessageIsPassedOnExactly() {
        rig.gateway.sendError = 429 to "Too Many Requests: retry after 61"
        rig.gateway.sendModes.add(SendMode.ERROR_REPLY)
        assertEquals(SendResult.Failed(SyncErrorKind.FLOOD_WAIT, 61), transport().send(chat, text()))
        rig.gateway.sendError = 400 to "Chat not found"
        rig.gateway.sendModes.add(SendMode.ERROR_REPLY)
        assertEquals(SendResult.Failed(SyncErrorKind.CHANNEL_MISSING, 0), transport().send(chat, text(2)))
    }

    @Test fun s07_aDuplicateSuccessAndALateFailureAfterSuccessChangeNothing() {
        rig.gateway.sendModes.add(SendMode.DUPLICATE_SUCCESS)
        val first = transport().send(chat, text(1))
        rig.gateway.sendModes.add(SendMode.FAIL_AFTER_SUCCESS)
        val second = transport().send(chat, text(2))
        assertTrue(first is SendResult.Sent && second is SendResult.Sent)
        assertNotEquals((first as SendResult.Sent).serverMessageId, (second as SendResult.Sent).serverMessageId)
        Thread.sleep(80) // the extra updates are swallowed without effect
        assertEquals(2, syncMessages().size)
    }

    @Test fun s08_aSuccessThatIsNotOurMessageIsNeverAnAck() {
        rig.gateway.sendModes.add(SendMode.WRONG_TEXT)
        assertEquals(SendResult.Unknown(), transport().send(chat, text(1)))
        rig.gateway.sendModes.add(SendMode.WRONG_CHAT)
        assertEquals(SendResult.Unknown(), transport().send(chat, text(2)))
        rig.gateway.sendModes.add(SendMode.SAME_ID)
        assertEquals("a final id equal to the temporary one is not final", SendResult.Unknown(), transport().send(chat, text(3)))
        // The wrong-chat success is not even correlated (it belongs to no watch), so only two count as a payload mismatch.
        assertEquals(2, hub.metrics.get(SyncMetrics.Event.SEND_PAYLOAD_MISMATCH))
        assertEquals(3, hub.metrics.get(SyncMetrics.Event.SEND_UNKNOWN))
    }

    @Test fun s09_onlyAPlainTextSyncMessageIsEverSent() {
        val t = transport()
        assertTrue(t.send(chat, text()) is SendResult.Sent)
        val request = rig.gateway.requests.first { it.optString("@type") == "sendMessage" }
        val content = request.getJSONObject("input_message_content")
        assertEquals("inputMessageText", content.getString("@type"))
        assertEquals(0, rig.gateway.nonTextSends)
        assertTrue(rig.gateway.typesSeen().none { it in setOf("editMessageText", "editMessageMedia", "sendMessageAlbum", "deleteMessages", "forwardMessages") })
        for (bad in listOf("hello", "password 123", "/storage/emulated/0/Music/song.flac", "")) {
            try { t.send(chat, bad); org.junit.Assert.fail("sent: $bad") } catch (_: IllegalArgumentException) {}
        }
        assertEquals("the refused texts never reached TDLib", 1, rig.gateway.count("sendMessage"))
    }

    @Test fun s10_withoutConsentToTransmitNothingIsSent() {
        assertEquals(SendResult.Failed(SyncErrorKind.UNAUTHORIZED), transport(allowed = { false }).send(chat, text()))
        assertEquals(0, rig.gateway.count("sendMessage"))
    }

    @Test fun s11_aSendToAnotherChatThanTheBoundOneIsRefused() {
        assertEquals(SendResult.Failed(SyncErrorKind.CHANNEL_MISSING), transport().send(chat + 1, text()))
        assertEquals(FetchResult.Failed(SyncErrorKind.CHANNEL_MISSING), transport().fetchAfter(chat + 1, 0L, 10))
        assertEquals(0, rig.gateway.count("sendMessage"))
    }

    @Test fun s12_theAccountChangingWhileASendIsInFlightNeverGivesAnAck() {
        val t = transport()
        assertTrue("identity verified by a first send", t.send(chat, text(1)) is SendResult.Sent)
        rig.gateway.held = true
        val result = async { t.send(chat, text(2)) }
        assertTrue(waitUntil { rig.gateway.heldCount() >= 1 })
        rig.relogin(2002L)
        rig.relogin(user)
        rig.gateway.held = false
        rig.gateway.release()
        assertEquals(SendResult.Unknown(), result.get(3, TimeUnit.SECONDS))
    }

    @Test fun s13_twoSendsAtTheSameTimeEachGetTheirOwnServerId() {
        val t1 = transport()
        val t2 = transport()
        val a = async { t1.send(chat, text(1)) }
        val b = async { t2.send(chat, text(2)) }
        val ra = a.get(3, TimeUnit.SECONDS) as SendResult.Sent
        val rb = b.get(3, TimeUnit.SECONDS) as SendResult.Sent
        assertNotEquals(ra.serverMessageId, rb.serverMessageId)
        val byId = world.chats.getValue(chat).messages
        assertEquals(text(1), byId.getValue(ra.serverMessageId).text)
        assertEquals(text(2), byId.getValue(rb.serverMessageId).text)
    }

    @Test fun s15_aLateFailureNeverOverwritesTheEvidenceThatTheServerStoredIt() {
        val watch = hub.openSendWatch(chat)
        fun message(id: Long) = JSONObject().put("id", id).put("chat_id", chat)
        // Delivered in this order before anybody waits: success, then a contradicting failure for the same provisional id.
        hub.onEvent(JSONObject().put("@type", "updateMessageSendSucceeded").put("message", message(5L shl 20)).put("old_message_id", 77L))
        hub.onEvent(JSONObject().put("@type", "updateMessageSendFailed").put("message", message(77L)).put("old_message_id", 77L)
            .put("error", JSONObject().put("code", 500).put("message", "late")))
        val outcome = watch.await(77L, 200L)
        assertTrue(outcome is SendOutcome.Succeeded)
        // Other chats' updates are not for this watch.
        hub.onEvent(JSONObject().put("@type", "updateMessageSendSucceeded").put("message", JSONObject().put("id", 9L).put("chat_id", chat + 1)).put("old_message_id", 78L))
        assertEquals(null, watch.await(78L, 50L))
        watch.close()
    }

    @Test fun s14_aTimeoutOfTheProvisionalAnswerIsUnknownAndNothingWaitsForever() {
        rig.gateway.staySilent("sendMessage")
        val t = TdlibPlaylistSyncTransport(hub, user, rig.core.authEpoch(), chat, TdlibHistoryReader(hub, user, rig.core.authEpoch(), chat),
            TdlibTransportTimeouts(requestMs = 100L, sendAckMs = 100L))
        val started = System.nanoTime()
        assertEquals(SendResult.Unknown(), t.send(chat, text()))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 1_500)
        assertNotNull(rig.gateway.requests.firstOrNull { it.optString("@type") == "sendMessage" })
    }
}
