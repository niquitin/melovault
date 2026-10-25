package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * E96-P2-REWORK-02: "OK" means the history is read AND everything that was waiting has been sent.
 * A run that stops because its send budget ran out, with operations still waiting, is not OK.
 */
class TelegramPlaylistSyncOutboxCompletionTest {
    @get:Rule val folder = TemporaryFolder()

    private val acct = TelegramUserId(1001L)
    private lateinit var channel: FakeTelegramChannel

    @Before fun setUp() {
        channel = FakeTelegramChannel()
    }

    private fun saved(n: Int): Long = n * 4_194_304L

    private fun device(name: String, budget: Int) = FakeSyncDevice(
        name, folder.newFolder(name), acct, channel, seed = name.sumOf { it.code } % 90 + 1,
        config = SyncConfig(maxEnvelopesPerRun = budget),
    )

    private fun FakeSyncDevice.save(count: Int) {
        for (n in 1..count) check(toggle(acct.value, saved(n)) is TelegramPlaylistSession.ToggleOutcome.Added)
    }

    /** Operation ids of every operation the server holds, in server order. */
    private fun serverOpIds(): List<String> = channel.texts().flatMap { text ->
        (PlaylistSyncProtocol.decode(text, PlaylistSyncProtocol.scopeOf(acct.value)) as PlaylistSyncProtocol.Decoded.Ok)
            .envelope.ops.map { it.opId }
    }

    /** The one rule: OK is never said while something is still waiting. */
    private fun assertOkMeansDrained(report: SyncReport, d: FakeSyncDevice) {
        if (report.status == SyncStatus.OK) {
            assertEquals("OK while operations still wait", emptyList<String>(), d.pendingIds())
            assertEquals(0, report.pendingLeft)
        }
    }

    // ---- the bug, in the shapes the audit named ---------------------------------------------------

    @Test fun o01_aRunThatSentOnlyItsBudgetIsNotOkAndThenFinishesWithTheLastPartialBatch() {
        val d = device("dev-a", budget = 5)
        d.save(130) // 7 messages: 20 x 6 + 10
        val r1 = d.sync()
        assertOkMeansDrained(r1, d)
        assertEquals("100 operations went out in 5 messages", 5, r1.envelopesSent)
        assertEquals(30, r1.pendingLeft)
        assertEquals(SyncStatus.OUTBOX_INCOMPLETE, r1.status)
        val r2 = d.sync()
        assertEquals("the last, partial batches: 20 + 10", 2, r2.envelopesSent)
        assertEquals(SyncStatus.OK, r2.status)
        assertEquals(0, r2.pendingLeft)
        assertEquals(7, channel.count())
    }

    @Test fun o02_aBudgetOfOneMessageNeedsAsManyRunsAsThereAreMessagesAndOnlyTheLastIsOk() {
        val d = device("dev-a", budget = 1)
        d.save(45) // 20 + 20 + 5
        val statuses = ArrayList<SyncStatus>()
        val left = ArrayList<Int>()
        repeat(6) {
            if (statuses.lastOrNull() == SyncStatus.OK) return@repeat
            val r = d.sync()
            assertOkMeansDrained(r, d)
            statuses.add(r.status)
            left.add(r.pendingLeft)
        }
        assertEquals(listOf(SyncStatus.OUTBOX_INCOMPLETE, SyncStatus.OUTBOX_INCOMPLETE, SyncStatus.OK), statuses)
        assertEquals(listOf(25, 5, 0), left)
        assertEquals("every operation once, in the order it was made", (1..45).map { "dev-a#$it" }, serverOpIds())
    }

    @Test fun o03_theBoundaryIsExact() {
        val exact = device("dev-a", budget = 2)
        exact.save(40) // exactly two full messages
        val r = exact.sync()
        assertEquals("everything fitted the budget: that IS complete", SyncStatus.OK, r.status)
        assertEquals(0, r.pendingLeft)

        val over = device("dev-b", budget = 2)
        over.save(41)
        val r1 = over.sync()
        assertEquals(SyncStatus.OUTBOX_INCOMPLETE, r1.status)
        assertEquals(1, r1.pendingLeft)
        assertEquals(SyncStatus.OK, over.sync().status)
    }

    @Test fun o04_runningUntilOkDrainsEverythingWithoutAckingOrReorderingWhatWasNotSent() {
        val d = device("dev-a", budget = 3)
        d.save(200)
        val all = (1..200).map { "dev-a#$it" }
        var runs = 0
        var report: SyncReport
        val left = ArrayList<Int>()
        do {
            report = d.sync()
            runs++
            left.add(report.pendingLeft)
            assertOkMeansDrained(report, d)
            if (report.status != SyncStatus.OK) {
                // what is left is exactly the tail, untouched: same ids, same order, still waiting with no attempts
                val sent = serverOpIds().size
                assertEquals(all.drop(sent), d.pendingIds())
                assertTrue(d.stored().outbox.all { it.status == OutboxStatus.PENDING && it.attempts == 0 })
            }
        } while (report.status != SyncStatus.OK && runs < 10)
        assertEquals(listOf(140, 80, 20, 0), left)
        assertEquals(4, runs)
        assertEquals(all, serverOpIds())
    }

    @Test fun o05_aRestartBetweenRunsKeepsTheWaitingOperationsAndTheirIds() {
        val d = device("dev-a", budget = 2)
        d.save(100)
        val r1 = d.sync()
        assertOkMeansDrained(r1, d)
        assertEquals(60, r1.pendingLeft)
        val waitingBefore = d.stored().outbox
        d.restart()
        assertEquals("the same entries in the same order after a restart", waitingBefore, d.stored().outbox)
        var runs = 1
        var report = r1
        while (report.status != SyncStatus.OK && runs < 10) {
            report = d.sync()
            runs++
            assertOkMeansDrained(report, d)
            if (runs == 3) d.restart()
        }
        assertEquals(SyncStatus.OK, report.status)
        assertEquals((1..100).map { "dev-a#$it" }, serverOpIds())
        assertEquals(emptyList<String>(), d.pendingIds())
    }

    @Test fun o06_aSaveMadeWhileTheLastAllowedMessageWasGoingOutIsNotHiddenByAnOk() {
        val d = device("dev-a", budget = 1)
        d.save(10) // one message, which uses the whole budget
        d.hooks.afterStoreBeforeReply = {
            d.hooks.afterStoreBeforeReply = null
            d.toggle(acct.value, saved(500)) // the user taps Save while that send is in flight
        }
        val r = d.sync()
        assertOkMeansDrained(r, d)
        assertEquals(1, r.pendingLeft)
        assertEquals(SyncStatus.OUTBOX_INCOMPLETE, r.status)
        assertEquals(SyncStatus.OK, d.sync().status)
        assertEquals((1..11).map { "dev-a#$it" }, serverOpIds())
    }

    @Test fun o06b_withBudgetToSpareASaveMadeDuringASendIsSentInTheSameRun() {
        val d = device("dev-a", budget = 5)
        d.save(10)
        d.hooks.afterStoreBeforeReply = {
            d.hooks.afterStoreBeforeReply = null
            d.toggle(acct.value, saved(500))
        }
        val r = d.sync()
        assertEquals(SyncStatus.OK, r.status)
        assertEquals(2, r.envelopesSent)
        assertEquals((1..11).map { "dev-a#$it" }, serverOpIds())
    }

    /** Puts a Save right after the acknowledged entries were pruned, the last write before the verdict. */
    private class RacingHost(private val inner: TelegramPlaylistV2Session, private val afterPrune: () -> Unit) : PlaylistSyncHost {
        override fun view(account: TelegramUserId) = inner.view(account)
        override fun update(account: TelegramUserId, epoch: Long, transform: (PlaylistState) -> PlaylistState): UpdateResult {
            val before = inner.view(account)?.state
            val result = inner.update(account, epoch, transform)
            if (result is UpdateResult.Committed && before != null &&
                before.outbox.any { it.status == OutboxStatus.ACKNOWLEDGED } && result.state.outbox.none { it.status == OutboxStatus.ACKNOWLEDGED }
            ) afterPrune()
            return result
        }
    }

    @Test fun o08_aSaveThatArrivesAfterThePruneStillPreventsOk() {
        val d = device("dev-a", budget = 5)
        d.save(10)
        var armed = true
        val racing = PlaylistSyncCoordinator(
            RacingHost(d.session) { if (armed) { armed = false; d.toggle(acct.value, saved(500)) } },
            channel.transport(d.hooks), { 0L }, SyncConfig(maxEnvelopesPerRun = 5),
        )
        val r = racing.runOnce(acct, SyncMode.INCREMENTAL, TEST_SEND_BUDGET)
        assertEquals("the last write of the run was followed by a Save", 1, d.pendingIds().size)
        assertEquals(SyncStatus.OUTBOX_INCOMPLETE, r.status)
        assertEquals(1, r.pendingLeft)
        assertEquals(SyncStatus.OK, racing.runOnce(acct, SyncMode.INCREMENTAL, TEST_SEND_BUDGET).status)
    }

    @Test fun o07_aBudgetThatCouldNeverDoAnyWorkIsRejectedNotReportedAsOk() {
        for (bad in listOf(0, -1, Int.MIN_VALUE)) {
            try {
                SyncConfig(maxEnvelopesPerRun = bad)
                fail("maxEnvelopesPerRun=$bad must be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
        for (bad in listOf(0, -3)) {
            try {
                SyncConfig(maxPagesPerRun = bad)
                fail("maxPagesPerRun=$bad must be rejected")
            } catch (_: IllegalArgumentException) {
            }
            try {
                SyncConfig(pageSize = bad)
                fail("pageSize=$bad must be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertNotEquals(0, SyncConfig().maxEnvelopesPerRun)
    }
}
