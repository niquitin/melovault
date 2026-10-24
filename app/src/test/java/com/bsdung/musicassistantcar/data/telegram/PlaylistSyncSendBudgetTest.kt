package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * E96 P5B3 REWORK-01 (c): the explicit per-run budget of OUTBOX OPERATIONS, and "unknown delivery is never sent again".
 * The coordinator against the in-memory channel; nothing here touches Telegram.
 */
class PlaylistSyncSendBudgetTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private lateinit var channel: FakeTelegramChannel

    @Before fun setUp() {
        channel = FakeTelegramChannel()
    }

    private fun saved(n: Int): Long = n * 4_194_304L
    private fun device(name: String) = FakeSyncDevice(name, folder.newFolder(name), acct, channel, seed = name.sumOf { it.code } % 90 + 1)
    private fun FakeSyncDevice.song(n: Int) = toggle(account.value, saved(n))
    private fun FakeSyncDevice.run(budget: Int, mode: SyncMode = SyncMode.INCREMENTAL) = coordinator.runOnce(account, mode, budget)

    private fun FakeSyncDevice.outboxView() = stored().outbox.map { Triple(it.op.opId, it.status, it.attempts) }

    // ---- t04 / t05: the budget ------------------------------------------------------------------------------------------

    @Test fun t04_fiveWaitingOperationsWithABudgetOfThreeSendExactlyTheFirstThreeInOneEnvelope() {
        val a = device("dev-a")
        for (n in 1..5) a.song(n)
        val before = a.stored().outbox
        assertEquals(5, before.size)

        val report = a.run(3)

        assertEquals(SyncStatus.SEND_LIMIT_REACHED, report.status)
        assertEquals(3, report.opsDispatched)
        assertEquals(3, report.opsAcknowledged)
        assertEquals(2, report.pendingLeft)
        assertEquals("one envelope, not one per operation", 1, channel.sendCalls)
        assertEquals(1, channel.count())
        val sentOps = (PlaylistSyncProtocol.decode(channel.texts().single(), PlaylistSyncProtocol.scopeOf(acct.value)) as PlaylistSyncProtocol.Decoded.Ok)
            .envelope.ops.map { it.opId }
        assertEquals(before.take(3).map { it.op.opId }, sentOps)
        // the other two are untouched: same ids, same order, still PENDING
        val left = a.stored().outbox
        assertEquals(before.drop(3).map { it.op.opId }, left.map { it.op.opId })
        assertTrue(left.all { it.status == OutboxStatus.PENDING && it.attempts == 0 })
    }

    @Test fun theRestIsSentByALaterRunWithItsOwnBudgetAndNothingIsSentTwice() {
        val a = device("dev-a")
        for (n in 1..5) a.song(n)
        a.run(3)
        val second = a.run(3)
        assertEquals(SyncStatus.OK, second.status)
        assertEquals(2, second.opsDispatched)
        assertEquals(2, channel.count())
        assertEquals(emptyList<String>(), a.pendingIds())
        val all = channel.texts().flatMap {
            (PlaylistSyncProtocol.decode(it, PlaylistSyncProtocol.scopeOf(acct.value)) as PlaylistSyncProtocol.Decoded.Ok).envelope.ops.map { op -> op.opId }
        }
        assertEquals(all.toSet().size, all.size)
        assertEquals(5, all.size)
    }

    @Test fun t05_noBudgetOrAnInvalidOneReadsAndAcknowledgesButNeverSends() {
        val a = device("dev-a")
        for (n in 1..5) a.song(n)
        for (budget in listOf(0, -1, Int.MIN_VALUE)) {
            val report = a.run(budget)
            assertEquals("budget $budget", SyncStatus.SEND_LIMIT_REACHED, report.status)
            assertEquals(SyncStage.SEND_BUDGET_ZERO, report.stage)
            assertEquals(0, report.opsDispatched)
            assertEquals(5, report.pendingLeft)
        }
        // the default of runOnce() itself is "no budget"
        assertEquals(SyncStatus.SEND_LIMIT_REACHED, a.coordinator.runOnce(acct).status)
        assertEquals(0, channel.sendCalls)
        assertEquals(0, channel.count())
        assertTrue(a.stored().outbox.all { it.status == OutboxStatus.PENDING && it.attempts == 0 })
    }

    @Test fun aRunWithoutABudgetStillAcknowledgesWhatIsAlreadyInTheChannel() {
        val a = device("dev-a")
        a.song(1); a.song(2)
        val ops = a.stored().outbox.map { it.op }
        // the channel already holds them (for example sent earlier, answer lost)
        channel.inject(envelopeText(acct.value, "dev-a", *ops.toTypedArray()))
        val report = a.run(0)
        assertEquals(SyncStatus.OK, report.status)
        assertEquals(2, report.opsAcknowledged)
        assertEquals(0, channel.sendCalls)
        assertEquals(emptyList<String>(), a.pendingIds())
    }

    @Test fun t17_aBudgetIsSpentAtDispatchAcrossEnvelopesWithinOneRun() {
        val a = device("dev-a")
        for (n in 1..25) a.song(n)
        val report = a.run(23) // 20 + 3
        assertEquals(23, report.opsDispatched)
        assertEquals(2, channel.sendCalls)
        assertEquals(SyncStatus.SEND_LIMIT_REACHED, report.status)
        assertEquals(2, report.pendingLeft)
    }

    // ---- unknown delivery ---------------------------------------------------------------------------------------------------

    @Test fun t06_oneEnvelopeAcknowledgedAndTheNextOneUnknownKeepsTheSecondAndReadsItBackLaterWithoutResending() {
        val a = device("dev-a")
        for (n in 1..25) a.song(n) // envelope 1 = 20 operations, envelope 2 = 5
        var calls = 0
        a.hooks.beforeSend = { if (++calls == 2) channel.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK) }
        val first = a.run(TEST_SEND_BUDGET)
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, first.status)
        assertEquals(25, first.opsDispatched)
        assertEquals(20, first.opsAcknowledged)
        val rest = a.stored().outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED }
        assertEquals(5, rest.size)
        assertTrue(rest.all { it.status == OutboxStatus.RETRY_REQUIRED })
        assertEquals(2, channel.sendCalls)

        a.nowMs += first.retryAfterMs
        val second = a.run(TEST_SEND_BUDGET) // reads the history: the lost-answer message is there
        assertEquals(SyncStatus.OK, second.status)
        assertTrue("the five are acknowledged (the earlier twenty, not yet pruned, are read again harmlessly)", second.opsAcknowledged >= 5)
        assertEquals("never sent a third time", 2, channel.sendCalls)
        assertEquals(emptyList<String>(), a.pendingIds())
    }

    @Test fun t08_t09_unknownAndNotSeenInTheHistoryIsNeverSentAgain_notByBudgetNotByTimeNotByFullReplay() {
        val a = device("dev-a")
        a.song(1); a.song(2)
        channel.sendFaults.add(FakeTelegramChannel.SendFault.TIMEOUT_NOT_STORED) // the message really never arrived - nobody can know
        val first = a.run(3)
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, first.status)
        val kept = a.outboxView()
        assertTrue(a.stored().outbox.all { it.status == OutboxStatus.RETRY_REQUIRED })

        repeat(4) { round ->
            for (mode in listOf(SyncMode.INCREMENTAL, SyncMode.FULL_REPLAY)) {
                a.nowMs += 24L * 3600 * 1000 // a day later, every time: the wait is never the reason
                val again = a.run(3, mode)
                assertEquals("round $round $mode", SyncStatus.DELIVERY_UNCONFIRMED, again.status)
                assertEquals(SyncStage.READBACK_ABSENT, again.stage)
                assertEquals(0, again.opsDispatched)
            }
        }
        assertEquals("not one more send", 1, channel.sendCalls)
        assertEquals("the outbox is exactly as it was after the doubt (ids, order, status, attempts)", kept, a.outboxView())
    }

    @Test fun anEmptyLocalLookIsNotProofOfAbsence_aLaterAppearanceIsStillAcknowledged() {
        val a = device("dev-a")
        a.song(1)
        channel.lostAckHiddenFetches = 3
        channel.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK)
        a.run(3)
        repeat(2) {
            a.nowMs += 3_600_000L
            assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, a.run(3).status)
        }
        a.nowMs += 3_600_000L
        val shows = a.run(3)
        assertEquals(SyncStatus.OK, shows.status)
        assertEquals(1, shows.opsAcknowledged)
        assertEquals(1, channel.sendCalls)
    }

    @Test fun newChangesQueueBehindAnUnconfirmedOneAndAreNotSentAheadOfIt() {
        val a = device("dev-a")
        a.song(1)
        channel.sendFaults.add(FakeTelegramChannel.SendFault.TIMEOUT_NOT_STORED)
        a.run(3)
        a.song(2); a.song(3)
        a.nowMs += 3_600_000L
        val report = a.run(3)
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, report.status)
        assertEquals(1, channel.sendCalls)
        assertEquals(3, a.stored().outbox.size)
        assertEquals(listOf(OutboxStatus.RETRY_REQUIRED, OutboxStatus.PENDING, OutboxStatus.PENDING), a.stored().outbox.map { it.status })
    }

    // ---- definite failures stay pending ---------------------------------------------------------------------------------

    @Test fun t10_aDefiniteRefusalKeepsTheOperationPendingAndObeysTheServersWait() {
        val a = device("dev-a")
        a.song(1)
        channel.sendFaults.add(FakeTelegramChannel.SendFault.FLOOD_35)
        val first = a.run(3)
        assertEquals(SyncStatus.BACKOFF, first.status)
        assertEquals(35_000L, first.retryAfterMs)
        assertEquals("charged at dispatch even though it was refused", 1, first.opsDispatched)
        assertEquals(OutboxStatus.PENDING, a.stored().outbox.single().status)
        a.nowMs += 34_999
        assertEquals(SyncStatus.BACKOFF_ACTIVE, a.run(3).status)
        assertEquals(1, channel.sendCalls)
        a.nowMs += 1
        assertEquals(SyncStatus.OK, a.run(3).status)
        assertEquals(2, channel.sendCalls)
        assertEquals(1, channel.count())
    }

    @Test fun aRefusalBeforeAnythingWasHandedOverIsNotChargedToTheBudget() {
        val a = device("dev-a")
        val refuse = object : PlaylistSyncTransport {
            override fun send(channelId: Long, text: String) = SendResult.Failed(SyncErrorKind.UNAUTHORIZED, stage = SyncStage.SEND_PRECHECK_CONSENT)
            override fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int) = channel.fetchAfter(afterServerId, limit)
        }
        val coordinator = PlaylistSyncCoordinator(a.session, refuse, { 0L })
        a.song(1)
        val report = coordinator.runOnce(acct, SyncMode.INCREMENTAL, 3)
        assertEquals(0, report.opsDispatched)
        assertEquals(SyncStage.SEND_PRECHECK_CONSENT, report.stage)
        assertEquals(OutboxStatus.PENDING, a.stored().outbox.single().status)
    }

    // ---- t15: the same envelope twice ------------------------------------------------------------------------------------

    @Test fun t15_theSameEnvelopeTwiceInTheChannelIsAppliedOnceAndAcknowledgedOnce() {
        val a = device("dev-a"); val b = device("dev-b")
        a.song(1); a.song(2)
        val text = envelopeText(acct.value, "dev-a", *a.stored().outbox.map { it.op }.toTypedArray())
        channel.inject(text)
        channel.inject(text)
        val ra = a.run(0)
        assertEquals(SyncStatus.OK, ra.status)
        assertEquals(2, ra.opsAcknowledged)
        assertEquals(0, channel.sendCalls)
        val rb = b.run(0)
        assertEquals(SyncStatus.OK, rb.status)
        assertEquals(listOf(saved(1), saved(2)), b.present().sorted())
        assertEquals(a.crdt(), b.crdt())
    }

    // ---- t03: the Sony's message ------------------------------------------------------------------------------------------

    @Test fun t03_theTabMessageTdlibCleanedIsFoundAgainAcknowledgedAndNothingIsSent() {
        val a = device("dev-a")
        for (n in 1..5) a.song(n)
        val env = PlaylistSyncProtocol.Envelope(
            PlaylistSyncProtocol.scopeOf(acct.value), "dev-a", a.stored().outbox.first().op.opId.replace('#', '-'), a.stored().outbox.map { it.op },
        )
        // the doubt is already recorded, as on the Sony
        a.session.update(acct, a.session.view(acct)!!.epoch) { s -> s.outbox.fold(s) { acc, e -> acc.markRetryRequired(e.op.opId) } }
        assertTrue(a.stored().outbox.all { it.status == OutboxStatus.RETRY_REQUIRED })
        channel.inject(FakeTdlibClientText.clean(PlaylistSyncProtocol.encodeWith(env, '\t')))

        val report = a.run(0)

        assertEquals(SyncStatus.OK, report.status)
        assertEquals(5, report.opsAcknowledged)
        assertEquals(0, channel.sendCalls)
        assertEquals(emptyList<String>(), a.pendingIds())
    }

    @Test fun aCleanedTabMessageWithAnAlteredChecksumIsRecoveryNotAnAcknowledgement() {
        val a = device("dev-a")
        a.song(1)
        val env = PlaylistSyncProtocol.Envelope(
            PlaylistSyncProtocol.scopeOf(acct.value), "dev-a", "dev-a-1", a.stored().outbox.map { it.op },
        )
        a.session.update(acct, a.session.view(acct)!!.epoch) { s -> s.markRetryRequired("dev-a#1") }
        val cleaned = FakeTdlibClientText.clean(PlaylistSyncProtocol.encodeWith(env, '\t'))
        val hash = cleaned.substringAfterLast("sum ")
        channel.inject(cleaned.dropLast(hash.length) + (if (hash.first() == 'a') 'b' else 'a') + hash.drop(1))

        val report = a.run(3)

        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.CORRUPT_MESSAGE, report.recovery)
        assertEquals(0, report.opsAcknowledged)
        assertEquals(0, channel.sendCalls)
        assertEquals(OutboxStatus.RETRY_REQUIRED, a.stored().outbox.single().status)
    }

    @Test fun aSameLooksButForeignMessageIsNeverTakenAsOurAcknowledgement() {
        val a = device("dev-a")
        a.song(1)
        a.session.update(acct, a.session.view(acct)!!.epoch) { s -> s.markRetryRequired("dev-a#1") }
        // another device wrote an operation with a similar id and the same item: that is not ours
        channel.inject(envelopeText(acct.value, "dev-b", addOp("dev-b", 1, acct.value, saved(1))))
        val report = a.run(3)
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, report.status)
        assertEquals(0, report.opsAcknowledged)
        assertEquals(0, channel.sendCalls)
        assertEquals(listOf("dev-a#1"), a.pendingIds())
    }

    // ---- report bookkeeping -------------------------------------------------------------------------------------------------

    @Test fun theReportCarriesOnlyAClosedStageAndCoarseBuckets() {
        val a = device("dev-a")
        a.song(1)
        channel.sendFaults.add(FakeTelegramChannel.SendFault.FLOOD_35)
        val report = a.run(3)
        assertNotEquals(SyncStage.NONE, report.stage)
        assertEquals(DurationBucket.UNDER_1S, report.sendDuration)
        assertTrue(Regex("[A-Z0-9_]+").matches(report.stage.name))
        // a run that sent nothing has no send duration
        val b = device("dev-b")
        assertEquals(null, b.run(3).sendDuration)
    }
}
