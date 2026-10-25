package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * E96 P5B3 REWORK-01 (d): what the TDLib transport answers for each kind of trouble - a definite refusal, a doubt - and
 * the sanitised stage that goes with it. Against the fake native TDLib, which cleans the text like the real one.
 */
class TdlibSendDiagnosticsTest {
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

    private fun transport(allowed: () -> Boolean = { true }, sendAckMs: Long = 300L, requestMs: Long = 1_500L): TdlibPlaylistSyncTransport {
        val epoch = rig.core.authEpoch()
        val reader = TdlibHistoryReader(hub, user, epoch, chat, HistoryLimits(requestTimeoutMs = requestMs))
        return TdlibPlaylistSyncTransport(hub, user, epoch, chat, reader, TdlibTransportTimeouts(requestMs, sendAckMs), allowed)
    }

    private fun text(n: Int = 1) = envelopeText(user, "dev-a", addOp("dev-a", n.toLong(), user, songId(n)))
    private fun syncMessages() = world.texts(chat).filter { it.startsWith(PlaylistSyncProtocol.MARKER) }

    private fun unknown(result: SendResult): SyncStage = (result as SendResult.Unknown).stage
    private fun failed(result: SendResult): SendResult.Failed = result as SendResult.Failed

    // ---- the text TDLib keeps is the text that was checksummed ----------------------------------------------------------------

    @Test fun t01_aHealthyCurrentFormatSendIsAcknowledgedAndTheChannelHoldsExactlyWhatWasSent() {
        val sent = text(1)
        val result = transport().send(chat, sent)
        assertTrue("$result", result is SendResult.Sent)
        assertEquals("TDLib changed nothing, so the checksum still fits", listOf(sent), syncMessages())
        val back = PlaylistSyncProtocol.decode(syncMessages().single(), PlaylistSyncProtocol.scopeOf(user))
        assertTrue(back is PlaylistSyncProtocol.Decoded.Ok)
    }

    @Test fun aTextThatTdlibWouldRewriteIsRefusedBeforeAnythingIsHandedOver() {
        val withTab = "#mvsync v1\nscope\tabcdef0123456789\n"
        val result = failed(transport().send(chat, withTab))
        assertEquals(SyncErrorKind.REJECTED, result.kind)
        assertEquals(SyncStage.SEND_PRECHECK_TEXT, result.stage)
        assertEquals(0, rig.gateway.count("sendMessage"))
    }

    // ---- definite refusals: stage, kind and error class --------------------------------------------------------------------------

    @Test fun noWriteAccessIsTheChannelBeingGoneNotTheNetwork() {
        rig.gateway.sendError = 400 to "Bad Request: have no write access to the chat"
        rig.gateway.sendModes.add(SendMode.ERROR_REPLY)
        val result = failed(transport().send(chat, text()))
        assertEquals(SyncErrorKind.CHANNEL_MISSING, result.kind)
        assertEquals(SyncStage.SEND_DISPATCH_ERROR, result.stage)
        assertEquals(ErrorBucket.E400, result.bucket)
    }

    @Test fun anUnknown400IsARefusalNotTheNetwork() {
        rig.gateway.sendError = 400 to "Bad Request: something nobody has heard of"
        rig.gateway.sendModes.add(SendMode.ERROR_REPLY)
        val result = failed(transport().send(chat, text()))
        assertEquals(SyncErrorKind.REJECTED, result.kind)
        assertEquals(ErrorBucket.E400, result.bucket)
        assertEquals(SyncErrorKind.REJECTED to 0, TdlibSyncErrors.kindOf(400, "Bad Request: PEER_FLOOD_X"))
        assertEquals("a 5xx stays a network matter", SyncErrorKind.NETWORK to 0, TdlibSyncErrors.kindOf(500, "Internal error"))
    }

    @Test fun t10_aFloodWaitKeepsTheServersNumberAndTheErrorClass() {
        rig.gateway.sendError = 429 to "Too Many Requests: retry after 61"
        rig.gateway.sendModes.add(SendMode.ERROR_REPLY)
        val result = failed(transport().send(chat, text()))
        assertEquals(SyncErrorKind.FLOOD_WAIT, result.kind)
        assertEquals(61, result.retryAfterSeconds)
        assertEquals(ErrorBucket.E429, result.bucket)
    }

    @Test fun errorClassesCarryNoMessageText() {
        assertEquals(ErrorBucket.E401, ErrorBucket.of(401))
        assertEquals(ErrorBucket.E403, ErrorBucket.of(403))
        assertEquals(ErrorBucket.E404, ErrorBucket.of(404))
        assertEquals(ErrorBucket.E5XX, ErrorBucket.of(502))
        assertEquals(ErrorBucket.OTHER, ErrorBucket.of(0))
        assertEquals(ErrorBucket.OTHER, ErrorBucket.of(-5))
        assertEquals(DurationBucket.UNDER_1S, DurationBucket.of(999))
        assertEquals(DurationBucket.UNDER_20S, DurationBucket.of(1_000))
        assertEquals(DurationBucket.UNDER_60S, DurationBucket.of(20_000))
        assertEquals(DurationBucket.OVER_60S, DurationBucket.of(60_000))
    }

    @Test fun refusalsBeforeDispatchNameTheirStage() {
        assertEquals(SyncStage.SEND_PRECHECK_CONSENT, failed(transport(allowed = { false }).send(chat, text())).stage)
        rig.gateway.connectionState("connectionStateWaitingForNetwork")
        assertTrue(waitUntil { !hub.connectionIsUsable() })
        assertEquals(SyncStage.SEND_PRECHECK_CONNECTION, failed(transport().send(chat, text())).stage)
        assertEquals(SyncStage.SEND_PRECHECK_IDENTITY, failed(transport().send(chat + 1, text())).stage)
        assertEquals(0, rig.gateway.count("sendMessage"))
    }

    // ---- doubt: always Unknown, with the reason --------------------------------------------------------------------------------

    @Test fun noAnswerAtAllAndNoAckAreDifferentStages() {
        rig.gateway.sendModes.add(SendMode.NO_REPLY_NOT_STORED)
        assertEquals(SyncStage.SEND_DISPATCH_TIMEOUT, unknown(transport(requestMs = 150L).send(chat, text(1))))
        rig.gateway.sendModes.add(SendMode.NO_UPDATE_STORED)
        assertEquals(SyncStage.SEND_ACK_TIMEOUT, unknown(transport().send(chat, text(2))))
        rig.gateway.sendModes.add(SendMode.NO_UPDATE_NOT_STORED)
        assertEquals(SyncStage.SEND_ACK_TIMEOUT, unknown(transport().send(chat, text(3))))
    }

    @Test fun aProvisionalAnswerThatIsNotOursNamesWhatWasWrong() {
        rig.gateway.sendModes.add(SendMode.WRONG_PROVISIONAL_TYPE)
        assertEquals(SyncStage.SEND_PROVISIONAL_MISMATCH_TYPE, unknown(transport().send(chat, text(1))))
        rig.gateway.sendModes.add(SendMode.WRONG_PROVISIONAL_CHAT)
        assertEquals(SyncStage.SEND_PROVISIONAL_MISMATCH_CHAT, unknown(transport().send(chat, text(2))))
        rig.gateway.sendModes.add(SendMode.WRONG_PROVISIONAL_TEXT)
        assertEquals(SyncStage.SEND_PROVISIONAL_MISMATCH_TEXT, unknown(transport().send(chat, text(3))))
    }

    @Test fun aSuccessThatIsNotOurMessageIsAnAckMismatch() {
        rig.gateway.sendModes.add(SendMode.WRONG_TEXT)
        assertEquals(SyncStage.SEND_ACK_MISMATCH, unknown(transport().send(chat, text(1))))
        rig.gateway.sendModes.add(SendMode.WRONG_CHAT)
        // a success for another chat is not even correlated to this send: it never arrives, which is a timeout - doubt either way
        val otherChat = unknown(transport().send(chat, text(2)))
        assertTrue("$otherChat", otherChat == SyncStage.SEND_ACK_MISMATCH || otherChat == SyncStage.SEND_ACK_TIMEOUT)
        rig.gateway.sendModes.add(SendMode.SAME_ID)
        assertEquals(SyncStage.SEND_ACK_MISMATCH, unknown(transport().send(chat, text(3))))
    }

    @Test fun aFailureAfterTheProvisionalAnswerIsRefusalOnlyWhenItIs4xx() {
        rig.gateway.sendError = 400 to "Bad Request: nope"
        rig.gateway.sendModes.add(SendMode.FAIL_UPDATE)
        val refused = failed(transport().send(chat, text(1)))
        assertEquals(SyncStage.SEND_ACK_FAILED, refused.stage)
        assertEquals(SyncErrorKind.REJECTED, refused.kind)
        rig.gateway.sendError = 502 to "Bad Gateway"
        rig.gateway.sendModes.add(SendMode.FAIL_UPDATE)
        assertEquals(SyncStage.SEND_ACK_FAILED, unknown(transport().send(chat, text(2))))
    }

    @Test fun t14_theAccountChangingMidFlightIsADoubtWithANamedStageAndNoAck() {
        val t = transport()
        assertTrue(t.send(chat, text(1)) is SendResult.Sent)
        rig.gateway.held = true
        val result = pool.submit(Callable { t.send(chat, text(2)) })
        assertTrue(waitUntil { rig.gateway.heldCount() >= 1 })
        rig.relogin(2002L)
        rig.relogin(user)
        rig.gateway.held = false
        rig.gateway.release()
        val stage = unknown(result.get(3, TimeUnit.SECONDS))
        assertTrue("$stage", stage == SyncStage.SEND_IDENTITY_LOST || stage == SyncStage.SEND_DISPATCH_ABORTED)
    }

    // ---- sanitised (T19) ----------------------------------------------------------------------------------------------------------

    @Test fun t19_everyStageReachesThePersonAsAnEnumNameAndNeverAsData() {
        val source = File(listOf(File("src"), File("app/src")).first { it.isDirectory }, "shareable/java/com/bsdung/musicassistantcar/data/telegram")
        // the only logging in the sync code: a closed list of names
        val logCalls = source.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { it.name.startsWith("Tdlib") && it.name != "TdlibNativeJsonGateway.kt" || it.name.startsWith("TelegramPlaylistSync") || it.name.startsWith("Sync") || it.name == "MeloSyncSettingsActivity.kt" || it.name == "PlaylistSyncStatusPolicy.kt" || it.name == "TransmitPlanner.kt" }
            .flatMap { f -> f.readLines().filter { Regex("\\bLog\\.[a-z]\\(").containsMatchIn(it) }.map { f.name to it.trim() }.asSequence() }
            .toList()
        assertEquals(logCalls.toString(), 1, logCalls.size)
        assertEquals("MeloSyncSettingsActivity.kt", logCalls.single().first)
        assertTrue(logCalls.single().second.contains("stage=\${it.stage.name} status=\${it.status?.name}"))
        // and a status line built from any stage carries the name only, never a long number or a quote
        for (stage in SyncStage.values()) {
            val line = PlaylistSyncStatusPolicy.evaluate(
                1001L, PlaylistStatusFacts(1001L, 5, 5, 5, 0, true, 3L, false, false, null, null),
                SyncContext(true, SyncConsent(enabled = true, mayCreateChannel = false, mayTransmitInitial = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION), false,
                    LastSyncRun(1001L, SyncStatus.DELIVERY_UNCONFIRMED, stage = stage, opCounterAtRun = 3L)),
            )
            val shown = line.fullDetail()
            if (stage != SyncStage.NONE) assertTrue(shown.contains("Mã chẩn đoán: ${stage.name}"))
            assertFalse("no id-like number in: $shown", Regex("\\d{5,}").containsMatchIn(shown))
            assertFalse(shown.contains("tg:") || shown.contains("#mvsync") || shown.contains("dev-"))
        }
    }
}
