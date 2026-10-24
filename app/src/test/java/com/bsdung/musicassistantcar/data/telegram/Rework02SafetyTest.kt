package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * E96 P5B3 REWORK-02: the two safety gaps ChatGPT found after REWORK-01, plus the read-only reconciliation and the
 * grant across an epoch swap.
 *
 *  1. A TDLib `sendMessage` error that is not a definite 4xx is DOUBT, not a refusal.
 *  2. The doubt is written down BEFORE anything is handed over (write-ahead): if that write fails nothing is sent;
 *     after a crash the operations are already flagged and are never sent again, only read back.
 *  3. A read-only run (budget 0) works with consent (c) off; only a budget above zero needs (c).
 *     A grant cannot come back after the sign-in epoch changed during a run.
 * Fakes only; nothing here touches Telegram.
 */
class Rework02SafetyTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val rigs = ArrayList<SyncRig>()
    private lateinit var channel: FakeTelegramChannel

    @Before fun setUp() {
        channel = FakeTelegramChannel()
    }

    @After fun tearDown() = rigs.forEach { it.stop() }

    private fun saved(n: Int): Long = n * 4_194_304L
    private fun device(name: String) = FakeSyncDevice(name, folder.newFolder(name), acct, channel, seed = name.sumOf { it.code } % 90 + 1)
    private fun FakeSyncDevice.song(n: Int) = toggle(account.value, saved(n))
    private fun FakeSyncDevice.run(budget: Int = TEST_SEND_BUDGET, mode: SyncMode = SyncMode.INCREMENTAL) = coordinator.runOnce(account, mode, budget)
    private fun FakeSyncDevice.later() { nowMs += 3_600_000L }
    private fun FakeSyncDevice.statuses() = stored().outbox.map { it.status }

    private fun envelopeOf(d: FakeSyncDevice) = PlaylistSyncProtocol.Envelope(
        PlaylistSyncProtocol.scopeOf(acct.value), d.stored().deviceId,
        d.stored().outbox.first().op.opId.replace('#', '-'), d.stored().outbox.map { it.op },
    )

    // ======================================================================================================
    // 1. sendMessage errors: only a definite 4xx is a refusal
    // ======================================================================================================

    private fun tdlibRig(songs: Int = 1): Pair<SyncRig, FakeChatData> {
        val world = FakeTdlibWorld()
        val chat = world.createSyncChannel(acct.value)
        val r = SyncRig("r02-dev", folder.newFolder(), world, acct, chat.id, seed = 23).also { rigs.add(it) }
        for (n in 1..songs) r.device.toggle(acct.value, songId(n))
        return r to chat
    }

    @Test fun b1_aServerSideErrorOnTheFirstSendMessageAnswerIsDoubtAndTheOperationIsNeverSentAgain() {
        for (code in listOf(500, 502, 503, 504)) {
            val (r, chat) = tdlibRig()
            r.tdlib.gateway.sendError = code to "Internal server error"
            r.tdlib.gateway.defaultSendMode = SendMode.ERROR_REPLY
            val first = r.run()
            assertEquals("code $code", SyncStatus.DELIVERY_UNCONFIRMED, first.status)
            assertEquals(SyncStage.SEND_DISPATCH_ERROR, first.stage)
            assertEquals(ErrorBucket.E5XX, first.errorBucket)
            assertEquals(1, first.opsDispatched)
            assertEquals(listOf(OutboxStatus.RETRY_REQUIRED), r.device.stored().outbox.map { it.status })
            // the network heals; the operation is NOT sent again - not now, not later
            r.tdlib.gateway.defaultSendMode = SendMode.OK_UPDATE_AFTER
            repeat(3) {
                r.device.nowMs += 3_600_000L
                assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, r.run().status)
            }
            assertEquals("code $code: exactly one request ever", 1, r.tdlib.gateway.count("sendMessage"))
            // it turns out the server had stored it: the read-back acknowledges it, still one request in total
            world(r).post(chat.id, PlaylistSyncProtocol.encode(envelopeOf(r.device)))
            r.device.nowMs += 3_600_000L
            val done = r.run()
            assertEquals(SyncStatus.OK, done.status)
            assertEquals(1, done.opsAcknowledged)
            assertEquals(1, r.tdlib.gateway.count("sendMessage"))
            assertEquals(emptyList<String>(), r.device.pendingIds())
        }
    }

    private fun world(r: SyncRig) = r.world

    @Test fun b1_aDefiniteClientErrorIsStillARefusalAndFloodWaitKeepsItsNumber() {
        for ((code, kind) in listOf(400 to SyncErrorKind.REJECTED, 403 to SyncErrorKind.UNAUTHORIZED, 429 to SyncErrorKind.FLOOD_WAIT)) {
            val (r, _) = tdlibRig()
            r.tdlib.gateway.sendError = code to (if (code == 429) "Too Many Requests: retry after 35" else "Bad Request: refused")
            r.tdlib.gateway.defaultSendMode = SendMode.ERROR_REPLY
            val report = r.run()
            assertTrue("code $code ${report.status}", report.status == SyncStatus.BACKOFF || report.status == SyncStatus.RECOVERY_REQUIRED)
            assertEquals("code $code: nothing is in doubt", listOf(OutboxStatus.PENDING), r.device.stored().outbox.map { it.status })
            if (code == 429) assertEquals(35_000L, report.retryAfterMs)
            assertEquals("code $code", 0, r.device.stored().outbox.single().attempts)
        }
    }

    // ======================================================================================================
    // 2. write-ahead of the doubt
    // ======================================================================================================

    @Test fun b2_i_ifTheDoubtCannotBeWrittenNothingIsSentAtAll() {
        val a = device("dev-a")
        a.song(1); a.song(2)
        val before = a.stored().outbox
        a.v2Io.failNextWrite()
        val report = a.run()
        assertEquals("no network call", 0, channel.sendCalls)
        assertEquals(SyncStatus.SAVE_FAILED, report.status)
        assertEquals(0, report.opsDispatched)
        assertEquals("the outbox is exactly as it was", before, a.stored().outbox)
        // the disk heals: the very same operations go out once
        a.v2Io.heal()
        val ok = a.run()
        assertEquals(SyncStatus.OK, ok.status)
        assertEquals(1, channel.sendCalls)
        assertEquals(2, ok.opsAcknowledged)
    }

    @Test fun b2_ii_aCrashRightAfterTheDispatchNeverLetsTheOperationsGoOutAgain() {
        val a = device("dev-a")
        a.song(1); a.song(2)
        val ids = a.stored().outbox.map { it.op.opId }
        // the server stores the message, then the process dies before anything is written down
        a.hooks.afterStoreBeforeReply = { throw SimulatedCrash("killed after dispatch") }
        try {
            a.run()
            org.junit.Assert.fail("the crash must reach the test")
        } catch (_: SimulatedCrash) {
        }
        a.hooks.afterStoreBeforeReply = null
        a.restart()
        assertEquals("flagged on disk by the write-ahead", listOf(OutboxStatus.RETRY_REQUIRED, OutboxStatus.RETRY_REQUIRED), a.statuses())
        assertEquals(ids, a.stored().outbox.map { it.op.opId })
        a.later()
        val again = a.run()
        assertEquals("the stored copy is read back and acknowledged", SyncStatus.OK, again.status)
        assertEquals("no second send", 1, channel.sendCalls)
        assertEquals(emptyList<String>(), a.pendingIds())
    }

    @Test fun b2_ii_aCrashBeforeTheServerSawAnythingStillNeverResendsWithoutProof() {
        val a = device("dev-a")
        a.song(1)
        a.hooks.beforeSend = { throw SimulatedCrash("killed before dispatch") }
        try {
            a.run()
            org.junit.Assert.fail("the crash must reach the test")
        } catch (_: SimulatedCrash) {
        }
        a.hooks.beforeSend = null
        a.restart()
        repeat(3) {
            a.later()
            val report = a.run()
            assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, report.status)
            assertEquals(0, report.opsDispatched)
        }
        assertEquals("nothing was ever sent and nothing is sent on a guess", 0, channel.sendCalls)
        assertEquals(listOf("dev-a#1"), a.pendingIds())
    }

    @Test fun b2_iii_aDoubtThatIsWrittenIsEndedByAReadBackOfTheCurrentOrTheCleanedLegacyText() {
        for (legacy in listOf(false, true)) {
            val c = FakeTelegramChannel()
            val a = FakeSyncDevice("dev-a${if (legacy) "l" else "c"}", folder.newFolder(), acct, c, seed = 41)
            a.toggle(acct.value, saved(1)); a.toggle(acct.value, saved(2))
            c.sendFaults.add(FakeTelegramChannel.SendFault.TIMEOUT_NOT_STORED) // answer lost, nobody knows
            assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, a.run().status)
            a.restart()
            val env = envelopeOf(a)
            c.inject(if (legacy) FakeTdlibClientText.clean(PlaylistSyncProtocol.encodeWith(env, '\t')) else PlaylistSyncProtocol.encode(env))
            a.later()
            val done = a.run()
            assertEquals("legacy=$legacy", SyncStatus.OK, done.status)
            assertEquals(2, done.opsAcknowledged)
            assertEquals("legacy=$legacy: still the one dispatch", 1, c.sendCalls)
        }
    }

    @Test fun b2_iv_aWriteFailureAfterAConfirmedSendIsEndedByTheNextCompleteRead() {
        val a = device("dev-a")
        a.song(1); a.song(2)
        a.hooks.afterStoreBeforeReply = { a.v2Io.failNextWrite() } // the server has it; the note of it cannot be written
        val first = a.run()
        a.hooks.afterStoreBeforeReply = null
        a.v2Io.heal()
        assertEquals(SyncStatus.SAVE_FAILED, first.status)
        assertEquals("still flagged, never back to a state that could be sent", listOf(OutboxStatus.RETRY_REQUIRED, OutboxStatus.RETRY_REQUIRED), a.statuses())
        a.later()
        val second = a.run()
        assertEquals(SyncStatus.OK, second.status)
        assertEquals(2, second.opsAcknowledged)
        assertEquals(1, channel.sendCalls)
    }

    @Test fun b2_v_nothingIsLostWhateverHappens_idsTombstonesAndCursorSurviveTheWholeStory() {
        val a = device("dev-a")
        for (n in 1..6) a.song(n)
        a.song(1) // a tombstone
        val idsBefore = a.stored().outbox.map { it.op.opId }
        val orBefore = a.stored().orSet
        val presentBefore = a.present()
        channel.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK)
        a.run()
        a.restart()
        a.v2Io.failNextWrite()
        a.later(); a.run()
        a.v2Io.heal()
        a.later()
        assertEquals(SyncStatus.OK, a.run().status)
        assertEquals(1, channel.sendCalls)
        assertEquals(idsBefore.toSet(), channel.texts().flatMap { text ->
            (PlaylistSyncProtocol.decode(text, PlaylistSyncProtocol.scopeOf(acct.value)) as PlaylistSyncProtocol.Decoded.Ok).envelope.ops.map { it.opId }
        }.toSet())
        assertEquals(orBefore, a.stored().orSet)
        assertEquals(presentBefore, a.present())
        assertTrue("the cursor moved to the message it read back", a.stored().checkpoint.lastAppliedServerId > 0L)
    }

    @Test fun b2_aDefiniteRefusalGivesTheOperationsBackExactlyAsTheyWere() {
        val a = device("dev-a")
        a.song(1); a.song(2)
        val before = a.stored().outbox
        channel.sendFaults.add(FakeTelegramChannel.SendFault.FLOOD_35)
        val report = a.run()
        assertEquals(SyncStatus.BACKOFF, report.status)
        assertEquals("ids, order, status AND attempts are as before the write-ahead", before, a.stored().outbox)
        a.nowMs += 35_000
        assertEquals(SyncStatus.OK, a.run().status)
    }

    @Test fun b2_ifTheStepBackAfterARefusalCannotBeWrittenTheOperationsStayFlaggedAndAreNotResent() {
        val a = device("dev-a")
        a.song(1)
        channel.sendFaults.add(FakeTelegramChannel.SendFault.FLOOD_35)
        a.hooks.beforeSend = { a.v2Io.failNextWrite() } // the write after the refusal fails
        a.run()
        a.hooks.beforeSend = null
        a.v2Io.heal()
        assertEquals(listOf(OutboxStatus.RETRY_REQUIRED), a.statuses())
        a.nowMs += 3_600_000L
        val again = a.run()
        assertEquals("safe: blocked, not sent", SyncStatus.DELIVERY_UNCONFIRMED, again.status)
        assertEquals(1, channel.sendCalls)
    }

    @Test fun b2_aSessionSwitchBetweenPlanningAndTheWriteAheadSendsNothing() {
        val a = device("dev-a")
        a.song(1)
        var calls = 0
        val stale = object : PlaylistSyncHost {
            override fun view(account: TelegramUserId) = a.session.view(account)
            override fun update(account: TelegramUserId, epoch: Long, transform: (PlaylistState) -> PlaylistState): UpdateResult =
                if (++calls == 1) UpdateResult.Stale else a.session.update(account, epoch, transform)
        }
        val coordinator = PlaylistSyncCoordinator(stale, channel.transport(), { 0L })
        val report = coordinator.runOnce(acct, SyncMode.INCREMENTAL, 3)
        assertTrue("calls=$calls", calls >= 1)
        assertEquals(SyncStatus.STALE_SESSION, report.status)
        assertEquals(0, channel.sendCalls)
        assertEquals(listOf(OutboxStatus.PENDING), a.statuses())
    }

    @Test fun b2_partialAckStillWorksWithTheWriteAhead() {
        val a = device("dev-a")
        for (n in 1..25) a.song(n)
        var calls = 0
        a.hooks.beforeSend = { if (++calls == 2) channel.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK) }
        val first = a.run()
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, first.status)
        assertEquals(20, first.opsAcknowledged)
        assertEquals(5, a.stored().outbox.count { it.status == OutboxStatus.RETRY_REQUIRED })
        a.hooks.beforeSend = null
        a.later()
        assertEquals(SyncStatus.OK, a.run().status)
        assertEquals(2, channel.sendCalls)
    }

    // ======================================================================================================
    // 3. read-only reconciliation without consent (c); grant across an epoch swap
    // ======================================================================================================

    private fun rigWithoutTransmitConsent(): Pair<SyncRig, FakeChatData> {
        val world = FakeTdlibWorld()
        val chat = world.createSyncChannel(acct.value)
        val r = SyncRig("r02-ro", folder.newFolder(), world, acct, chat.id, seed = 29, consent = false).also { rigs.add(it) }
        assertTrue(r.actions.enable(acct, true))
        return r to chat
    }

    @Test fun b3_aReadOnlyRunAcknowledgesWhatIsAlreadyInTheChannelWithConsentToTransmitOff() {
        val (r, chat) = rigWithoutTransmitConsent()
        r.device.toggle(acct.value, songId(1)); r.device.toggle(acct.value, songId(2))
        // the historical operations were delivered earlier (cleaned legacy text) and are in doubt locally
        r.device.session.update(acct, r.device.session.view(acct)!!.epoch) { s -> s.outbox.fold(s) { acc, e -> acc.markRetryRequired(e.op.opId) } }
        r.world.post(chat.id, FakeTdlibClientText.clean(PlaylistSyncProtocol.encodeWith(envelopeOf(r.device), '\t')))

        val answer = r.service.runSync(acct) // budget 0
        assertTrue("a read-only run is allowed: $answer", answer is Gated.Done)
        val report = (answer as Gated.Done).value
        assertEquals(SyncStatus.OK, report.status)
        assertEquals(2, report.opsAcknowledged)
        assertEquals(0, report.opsDispatched)
        assertEquals(0, r.tdlib.gateway.count("sendMessage"))
        assertEquals(emptyList<String>(), r.device.pendingIds())
        assertFalse("consent (c) was never switched on by this", r.consentStore.load(acct).mayTransmitInitial)
    }

    @Test fun b3_anyBudgetAboveZeroStillNeedsConsentToTransmit() {
        val (r, _) = rigWithoutTransmitConsent()
        r.device.toggle(acct.value, songId(1))
        for (budget in listOf(1, 3, 1_000)) {
            assertEquals(Gated.Denied(SyncDenied.TRANSMIT_NOT_ALLOWED), r.service.runSync(acct, SyncMode.INCREMENTAL, budget))
        }
        assertEquals(0, r.tdlib.gateway.count("sendMessage"))
        assertEquals("denied before TDLib was asked anything", emptyList<String>(), r.tdlib.gateway.typesSeen())
        // read-only is fine, and sends nothing
        val ro = (r.service.runSync(acct) as Gated.Done).value
        assertEquals(SyncStatus.SEND_LIMIT_REACHED, ro.status)
        assertEquals(0, r.tdlib.gateway.count("sendMessage"))
        assertEquals(1, r.device.pendingIds().size)
    }

    private class EpochActions(val onRun: () -> Unit) : SyncActionsPort {
        val budgets = ArrayList<Int>()
        var answer: Gated<SyncReport> = Gated.Done(SyncReport(SyncStatus.SEND_LIMIT_REACHED, opsDispatched = 1))
        override fun discover(account: TelegramUserId): Gated<ChannelDiscovery> = Gated.Done(ChannelDiscovery.NotFound)
        override fun create(account: TelegramUserId): Gated<CreateOutcome> = Gated.Done(CreateOutcome.Created(-1L))
        override fun bind(account: TelegramUserId, chatId: Long): Gated<BindResult> = Gated.Done(BindResult.Bound)
        override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int): Gated<SyncReport> {
            budgets += sendBudget
            onRun()
            return answer
        }
    }

    private fun epochController(port: SyncActionsPort, epoch: () -> Long): SyncSettingsController {
        val store = InMemorySyncConsentStore()
        val facts = PlaylistStatusFacts(1001L, 5, 5, 0, 0, true, 9L, false, false, null, null)
        val c = SyncSettingsController(store, true, port, { acct }, { facts }, epoch = epoch)
        assertEquals(ActionResult.Done, c.enable(true))
        assertEquals(ActionResult.Done, c.setAllowTransmit(true))
        return c
    }

    @Test fun b3_aSignInEpochSwapWhileARunIsInFlightNeverBringsTheLeftoverGrantBack() {
        var epoch = 1L
        val port = EpochActions { epoch = 2L; epoch = 3L } // A -> B -> A during the call
        val c = epochController(port) { epoch }
        assertEquals(ActionResult.Done, c.grantSend(3, true))
        c.syncNow()
        assertEquals(3, port.budgets.single())
        assertEquals("one was dispatched, two were left - and they are gone with the old epoch", 0, c.sendGrant())
        assertEquals(0, c.screen().sendGrant)
        c.syncNow()
        assertEquals("the next run is read-only", 0, port.budgets.last())
    }

    @Test fun b3_aRefusalAtTheGateDuringAnEpochSwapDoesNotRestoreTheGrantEither() {
        var epoch = 1L
        val port = EpochActions { epoch = 2L }
        port.answer = Gated.Denied(SyncDenied.NOT_AUTHORIZED)
        val c = epochController(port) { epoch }
        c.grantSend(2, true)
        c.syncNow()
        assertEquals(0, c.sendGrant())
    }

    @Test fun b3_withoutAnEpochChangeTheLeftoverGrantStillComesBack() {
        val port = EpochActions { }
        val c = epochController(port) { 1L }
        c.grantSend(3, true)
        c.syncNow()
        assertEquals(2, c.sendGrant())
    }
}
