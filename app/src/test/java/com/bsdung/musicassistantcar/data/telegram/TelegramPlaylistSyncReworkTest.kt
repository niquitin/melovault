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
 * E96-P2-REWORK-01. R01 pages must be a complete prefix of the history, R02 running out of pages is not "OK"
 * and a long FULL_REPLAY must keep going, R03 a FLOOD_WAIT is obeyed as given.
 */
class TelegramPlaylistSyncReworkTest {
    @get:Rule val folder = TemporaryFolder()

    private val acct = TelegramUserId(1001L)
    private lateinit var channel: FakeTelegramChannel

    @Before fun setUp() {
        channel = FakeTelegramChannel()
    }

    private fun saved(n: Int): Long = n * 4_194_304L

    private fun device(
        name: String, config: SyncConfig = SyncConfig(),
        transport: ((FakeTelegramChannel, TransportHooks) -> PlaylistSyncTransport)? = null,
    ) = FakeSyncDevice(name, folder.newFolder(name), acct, channel, seed = name.sumOf { it.code } % 90 + 1, config = config, transportFactory = transport)

    private fun injectAdds(from: Int, to: Int): List<Long> =
        (from..to).map { n -> channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", n.toLong(), acct.value, saved(n)))) }

    /**
     * The one rule every page must keep: the stored cursor never stands above a message whose operations
     * are not in the stored state. (A message the cursor has passed would never be read again.)
     */
    private fun assertCursorNeverPassesAnUnappliedMessage(d: FakeSyncDevice, label: String = "") {
        val stored = d.stored()
        val cursor = stored.checkpoint.lastAppliedServerId
        val scope = PlaylistSyncProtocol.scopeOf(acct.value)
        var checked = 0
        channel.allMessages().filter { it.first <= cursor }.forEach { (id, text) ->
            val decoded = PlaylistSyncProtocol.decode(text, scope) as? PlaylistSyncProtocol.Decoded.Ok ?: return@forEach
            for (op in decoded.envelope.ops) {
                checked++
                when (op.type) {
                    OpType.ADD -> assertTrue("$label: message $id is below the cursor $cursor but ${op.opId} is not applied",
                        stored.orSet.allAdds()[op.item]?.containsKey(op.dots.single()) == true)
                    OpType.REMOVE -> assertTrue("$label: message $id is below the cursor $cursor but ${op.opId} is not applied",
                        stored.orSet.allRemoved()[op.item].orEmpty().containsAll(op.dots))
                }
            }
        }
        assertTrue(checked >= 0)
    }

    /** A transport that breaks the contract: every page is the NEWEST messages, not the next ones after the cursor. */
    private fun newestFirst(): (FakeTelegramChannel, TransportHooks) -> PlaylistSyncTransport = { ch, _ ->
        object : PlaylistSyncTransport {
            override fun send(channelId: Long, text: String) = ch.send(text) { }
            override fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int): FetchResult {
                val all = (ch.fetchAfter(afterServerId, Int.MAX_VALUE) as FetchResult.Page).messages.sortedByDescending { it.id }
                return FetchResult.Page(all.take(limit), hasMore = all.size > limit)
            }
        }
    }

    // ---- R01 -------------------------------------------------------------------------------------

    @Test fun r01_aTransportThatPagesNewestFirstNeverMakesTheCursorSkipUnreadMessages() {
        injectAdds(1, 250)
        val d = device("dev-a", transport = newestFirst())
        repeat(6) { round ->
            val report = d.sync()
            d.waitOut(report)
            assertCursorNeverPassesAnUnappliedMessage(d, "round $round")
        }
    }

    private fun FakeSyncDevice.waitOut(report: SyncReport) {
        nowMs += report.retryAfterMs
    }

    // ---- R02 -------------------------------------------------------------------------------------

    @Test fun r02_runningOutOfPagesWhileMoreRemainsIsNotReportedAsOk() {
        injectAdds(1, 45)
        val d = device("dev-a", config = SyncConfig(pageSize = 10, maxPagesPerRun = 2))
        val first = d.sync()
        assertEquals("only 20 of 45 were read", 20, first.messagesRead)
        assertNotEquals("20 of 45 is not 'synchronised'", SyncStatus.OK, first.status)
    }

    @Test fun r02_catchUpOverSeveralRunsEndsInARealOkAndNotBefore() {
        injectAdds(1, 45)
        val d = device("dev-a", config = SyncConfig(pageSize = 10, maxPagesPerRun = 1))
        var runs = 0
        var report: SyncReport
        do {
            report = d.sync()
            runs++
            if (report.status == SyncStatus.OK) {
                assertEquals("OK only when everything was read", 45, d.present().size)
            }
        } while (report.status != SyncStatus.OK && runs < 12)
        assertEquals(5, runs)
        assertEquals(45, d.present().size)
    }

    @Test fun r02_aFullReplayLongerThanOneRunContinuesAcrossRunsAndRestartsAndFindsTheLateMessage() {
        val first = injectAdds(1, 30)
        val lateId = channel.inject(envelopeText(acct.value, "dev-x", addOp("dev-x", 99, acct.value, saved(99))), hiddenFetches = 100_000)
        injectAdds(31, 45)
        val d = device("dev-a", config = SyncConfig(pageSize = 10, maxPagesPerRun = 2))
        repeat(4) { d.waitOut(d.sync()) } // incremental: everything visible is read, the late one is not there yet
        assertEquals(45, d.present().size)
        channel.reveal(lateId)
        // From now on only a full replay can find it, and one run reads just 20 messages of 46.
        var runs = 0
        do {
            if (runs == 1) d.restart() // killed between two runs
            val report = d.sync(SyncMode.FULL_REPLAY)
            d.waitOut(report)
            runs++
        } while (report.status != SyncStatus.OK && runs < 12)
        assertTrue("the late message was reached by replaying across runs", saved(99) in d.present())
        assertTrue("a full replay needed more than one run", runs >= 3)
        assertTrue(first.isNotEmpty())
    }

    // ---- R01 after the fix: the contract, in every shape ----------------------------------------

    private fun allAfter(ch: FakeTelegramChannel, after: Long): List<ServerMessage> =
        (ch.fetchAfter(after, Int.MAX_VALUE) as FetchResult.Page).messages.sortedBy { it.id }

    private fun transportOf(fetch: (FakeTelegramChannel, Long, Int) -> FetchResult): (FakeTelegramChannel, TransportHooks) -> PlaylistSyncTransport = { ch, hooks ->
        object : PlaylistSyncTransport {
            override fun send(channelId: Long, text: String) = ch.send(text) { hooks.afterStoreBeforeReply?.invoke() }
            override fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int) = fetch(ch, afterServerId, limit)
        }
    }

    @Test fun r01_newestFirstWithoutAProofIsRefusedTheCursorStaysAndNothingIsLostOnceTheTransportIsRight() {
        injectAdds(1, 250)
        var conforming = false
        val d = device("dev-a", transport = transportOf { ch, after, limit ->
            if (conforming) ch.fetchAfter(after, limit)
            else {
                val all = allAfter(ch, after).sortedByDescending { it.id }
                FetchResult.Page(all.take(limit), hasMore = all.size > limit) // no proof
            }
        })
        d.toggle(acct.value, saved(900)) // something waiting to be sent
        val r = d.sync()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, r.status)
        assertEquals(RecoveryReason.TRANSPORT_CONTRACT, r.recovery)
        assertEquals(0L, d.stored().checkpoint.lastAppliedServerId)
        assertEquals("nothing from the refused page was applied", listOf(saved(900)), d.present())
        assertEquals("and nothing was sent into a history that cannot be read", 0, channel.sendCalls)
        assertCursorNeverPassesAnUnappliedMessage(d)
        conforming = true
        d.waitOut(r)
        assertEquals(SyncStatus.OK, d.sync().status)
        assertEquals(251, d.present().size)
        assertCursorNeverPassesAnUnappliedMessage(d)
    }

    @Test fun r01_aTransportThatLiesAboutBeingCompleteIsCaughtByTheSecondLook() {
        injectAdds(1, 250)
        val d = device("dev-a", transport = transportOf { ch, after, limit ->
            val newest = allAfter(ch, after).sortedByDescending { it.id }.take(limit)
            FetchResult.Page(newest, hasMore = true, completeThroughId = newest.maxOf { it.id }) // newest first, claiming a proof
        })
        val r = d.sync()
        assertEquals(RecoveryReason.TRANSPORT_CONTRACT, r.recovery)
        assertEquals(0L, d.stored().checkpoint.lastAppliedServerId)
        assertEquals(emptyList<Long>(), d.present())
        assertCursorNeverPassesAnUnappliedMessage(d)
    }

    @Test fun r01_aPageThatLeavesOutTheOldestUnreadMessageIsCaught() {
        injectAdds(1, 30)
        val d = device("dev-a", transport = transportOf { ch, after, limit ->
            val all = allAfter(ch, after)
            if (limit == 1) FetchResult.Page(all.take(1), hasMore = all.size > 1, completeThroughId = all.firstOrNull()?.id ?: after)
            else {
                val dropped = all.drop(1).take(limit) // the oldest one is missing from the page
                FetchResult.Page(dropped, hasMore = false, completeThroughId = dropped.last().id)
            }
        })
        val r = d.sync()
        assertEquals(RecoveryReason.TRANSPORT_CONTRACT, r.recovery)
        assertEquals(0L, d.stored().checkpoint.lastAppliedServerId)
        assertEquals(emptyList<Long>(), d.present())
    }

    @Test fun r01_anHonestPartialPageLosesNothing() {
        val ids = injectAdds(1, 120)
        val d = device("dev-a", config = SyncConfig(pageSize = 20), transport = transportOf { ch, after, limit ->
            val all = allAfter(ch, after)
            val k = (minOf(limit, all.size) + 1) / 2 // proves only the first half; the page also carries unproven extras
            FetchResult.Page(all.take(limit), hasMore = all.size > k, completeThroughId = if (all.isEmpty()) after else all[k - 1].id)
        })
        val r = d.sync()
        assertEquals(SyncStatus.OK, r.status)
        assertEquals(120, d.present().size)
        assertEquals("read in halves", true, r.pagesFetched > 6)
        assertEquals(ids.last(), d.stored().checkpoint.lastAppliedServerId)
        assertCursorNeverPassesAnUnappliedMessage(d)
    }

    @Test fun r01_sparseIdsAndEmptyPagesMakeProgressThroughTheProof() {
        val window = 3_000_000_000_000L
        val history = listOf(
            10L to envelopeText(acct.value, "dev-x", addOp("dev-x", 1, acct.value, saved(1))),
            7_000_000_000_000L to envelopeText(acct.value, "dev-x", addOp("dev-x", 2, acct.value, saved(2))),
        )
        var calls = 0
        val d = device("dev-a", transport = transportOf { _, after, _ ->
            calls++
            val upTo = after + window
            val inWindow = history.filter { it.first > after && it.first <= upTo }.map { ServerMessage(it.first, it.second) }
            FetchResult.Page(inWindow, hasMore = history.any { it.first > upTo }, completeThroughId = upTo)
        })
        val r = d.sync()
        assertEquals(SyncStatus.OK, r.status)
        assertEquals(setOf(saved(1), saved(2)), d.present().toSet())
        assertTrue("one page was empty yet moved the cursor", r.pagesFetched >= 3)
        assertTrue(d.stored().checkpoint.lastAppliedServerId >= 7_000_000_000_000L)
        assertTrue(calls >= 3)
    }

    @Test fun r01_everyBrokenPromiseIsRefusedWithoutMovingTheCursor() {
        injectAdds(1, 5)
        var forced: (() -> FetchResult)? = null
        val d = device("dev-a", transport = transportOf { ch, after, limit -> forced?.invoke() ?: ch.fetchAfter(after, limit) })
        assertEquals(SyncStatus.OK, d.sync().status)
        val cursor = d.stored().checkpoint.lastAppliedServerId
        val message = ServerMessage(cursor + 10, envelopeText(acct.value, "dev-x", addOp("dev-x", 77, acct.value, saved(77))))
        val broken = mapOf(
            "no proof" to FetchResult.Page(listOf(message), hasMore = false),
            "proof below the cursor" to FetchResult.Page(listOf(message), hasMore = false, completeThroughId = cursor - 1),
            "more claimed, no progress" to FetchResult.Page(emptyList(), hasMore = true, completeThroughId = cursor),
            "more claimed, proof behind" to FetchResult.Page(listOf(message), hasMore = true, completeThroughId = cursor),
        )
        for ((name, page) in broken) {
            d.coordinator.resetBackoff()
            forced = { page }
            val r = d.sync()
            assertEquals(name, SyncStatus.RECOVERY_REQUIRED, r.status)
            assertEquals(name, RecoveryReason.TRANSPORT_CONTRACT, r.recovery)
            assertEquals(name, cursor, d.stored().checkpoint.lastAppliedServerId)
            assertFalse(name, saved(77) in d.present())
        }
        forced = null
        d.coordinator.resetBackoff()
        assertEquals(SyncStatus.OK, d.sync().status)
    }

    @Test fun r01_messagesAboveTheProofCountForNothing() {
        val ids = injectAdds(1, 6)
        val d = device("dev-a", transport = transportOf { ch, after, limit ->
            val all = allAfter(ch, after)
            // proves only the first two, but hands over all of them
            FetchResult.Page(all.take(limit), hasMore = all.size > 2, completeThroughId = if (all.isEmpty()) after else all[minOf(1, all.size - 1)].id)
        })
        val r = d.sync()
        assertEquals(SyncStatus.OK, r.status)
        assertEquals(6, d.present().size) // read two at a time, each message exactly once above the proof
        assertEquals(ids.last(), d.stored().checkpoint.lastAppliedServerId)
    }

    // ---- R02 after the fix ------------------------------------------------------------------------

    @Test fun r02_theBudgetBoundaryIsExact() {
        injectAdds(1, 20)
        val exact = device("dev-a", config = SyncConfig(pageSize = 10, maxPagesPerRun = 2))
        assertEquals("20 messages in exactly 2 pages: all read", SyncStatus.OK, exact.sync().status)
        injectAdds(21, 21)
        val over = device("dev-b", config = SyncConfig(pageSize = 10, maxPagesPerRun = 2))
        val r = over.sync()
        assertEquals("21 messages: one is left", SyncStatus.CATCH_UP_INCOMPLETE, r.status)
        assertEquals(20, over.present().size)
        assertEquals(r.cursor, over.stored().checkpoint.lastAppliedServerId)
        assertEquals(SyncStatus.OK, over.sync().status)
        assertEquals(21, over.present().size)
    }

    @Test fun r02_nothingIsSentWhileTheHistoryIsNotReadAndItIsSentAfterwards() {
        injectAdds(1, 45)
        val d = device("dev-a", config = SyncConfig(pageSize = 10, maxPagesPerRun = 2))
        d.toggle(acct.value, saved(900))
        val r1 = d.sync()
        assertEquals(SyncStatus.CATCH_UP_INCOMPLETE, r1.status)
        assertEquals(0, r1.envelopesSent)
        assertEquals(0, channel.sendCalls)
        assertEquals(1, r1.pendingLeft)
        d.sync()
        assertEquals(SyncStatus.OK, d.sync().status)
        assertEquals(1, channel.sendCalls)
        assertEquals(emptyList<String>(), d.pendingIds())
    }

    @Test fun r02_aReplayRemembersWhereItStoodAndClearsItselfWhenDone() {
        injectAdds(1, 45)
        val d = device("dev-a", config = SyncConfig(pageSize = 10, maxPagesPerRun = 2))
        repeat(3) { d.sync() } // incremental, to the end
        assertEquals(SyncCheckpoint.NO_REPLAY, d.stored().checkpoint.replayPosition)
        val r1 = d.sync(SyncMode.FULL_REPLAY)
        assertEquals(SyncStatus.CATCH_UP_INCOMPLETE, r1.status)
        val mid = d.stored().checkpoint.replayPosition
        assertTrue("the replay stands somewhere in the middle", mid > 0L && mid < d.stored().checkpoint.lastAppliedServerId)
        assertEquals(mid, r1.replayPosition)
        // a normal read in between does not disturb it
        assertEquals(SyncStatus.OK, d.sync().status)
        assertEquals(mid, d.stored().checkpoint.replayPosition)
        d.restart()
        assertEquals("kept across a restart", mid, d.stored().checkpoint.replayPosition)
        val r2 = d.sync(SyncMode.FULL_REPLAY)
        assertEquals(SyncStatus.CATCH_UP_INCOMPLETE, r2.status)
        assertTrue(r2.replayPosition > mid)
        assertEquals(SyncStatus.OK, d.sync(SyncMode.FULL_REPLAY).status)
        assertEquals(SyncCheckpoint.NO_REPLAY, d.stored().checkpoint.replayPosition)
        // the next replay starts from the beginning again
        assertEquals(SyncStatus.CATCH_UP_INCOMPLETE, d.sync(SyncMode.FULL_REPLAY).status)
        assertTrue(d.stored().checkpoint.replayPosition in 1 until mid + 1)
    }

    @Test fun r02_aReplayThatEndsRightOnAnEmptyPageStillClearsItsPosition() {
        injectAdds(1, 20)
        val d = device("dev-a", config = SyncConfig(pageSize = 10, maxPagesPerRun = 2))
        assertEquals(SyncStatus.OK, d.sync().status)
        // a replay that was left standing at the very end
        val view = d.session.view(acct)!!
        d.session.update(acct, view.epoch) { it.copy(checkpoint = it.checkpoint.copy(replayPosition = it.checkpoint.lastAppliedServerId)) }
        assertEquals(SyncStatus.OK, d.sync(SyncMode.FULL_REPLAY).status)
        assertEquals(SyncCheckpoint.NO_REPLAY, d.stored().checkpoint.replayPosition)
    }

    @Test fun r02_theCheckpointFileFormatIsUnchangedUnlessAReplayIsRunning() {
        val none = PlaylistState.empty(acct.value, "dev-a").copy(checkpoint = SyncCheckpoint(-100777L, 41L))
        val text = String(TelegramPlaylistCodec.encode(none))
        assertTrue("same three-field line as before", text.contains("checkpoint\t-100777\t41\n"))
        val running = none.copy(checkpoint = SyncCheckpoint(-100777L, 41L, 17L))
        assertTrue(String(TelegramPlaylistCodec.encode(running)).contains("checkpoint\t-100777\t41\t17\n"))
        for (state in listOf(none, running)) {
            val back = TelegramPlaylistCodec.decode(TelegramPlaylistCodec.encode(state), acct.value)
            assertEquals(state, (back as TelegramPlaylistCodec.Decoded.Ok).state)
        }
        val negative = text.replace("checkpoint\t-100777\t41\n", "checkpoint\t-100777\t41\t-3\n")
        val sealed = negative.substringBefore("END\t").let { it + "END\t" + TelegramPlaylistCodec.sha256Hex(it) + "\n" }
        assertEquals(
            TelegramPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.FORMAT),
            TelegramPlaylistCodec.decode(sealed.toByteArray(), acct.value)
        )
    }

    // ---- R03 -------------------------------------------------------------------------------------

    @Test fun r03_aFloodWaitOfThirtySixHoursIsObeyedAsGiven() {
        assertEquals(129_600_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, 36 * 3600))
    }

    @Test fun r03_floodWaitBoundaries() {
        val day = 24 * 3600
        assertEquals(86_400_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, day))
        assertEquals(86_401_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, day + 1))
        assertEquals(86_399_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, day - 1))
        assertEquals(2_147_483_647_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, Int.MAX_VALUE))
        assertEquals("at least a second, never zero", 1_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, 0))
        assertEquals(1_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, -5))
        assertEquals(1_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, 1))
        assertEquals(2_000L, SyncBackoff().onFailure(0, SyncErrorKind.FLOOD_WAIT, 2))
    }

    @Test fun r03_theWaitNeverOverflowsTheClock() {
        val b = SyncBackoff()
        b.onFailure(Long.MAX_VALUE - 10, SyncErrorKind.FLOOD_WAIT, 36 * 3600)
        assertEquals("saturates instead of wrapping to the past", Long.MAX_VALUE, b.notBeforeMs)
    }

    @Test fun r03_aThirtySixHourWaitHoldsTheCoordinatorForThirtySixHours() {
        val d = device("dev-a")
        d.toggle(acct.value, saved(1))
        channel.fetchFaults.add(FetchResult.Failed(SyncErrorKind.FLOOD_WAIT, 36 * 3600))
        val r = d.sync()
        assertEquals(129_600_000L, r.retryAfterMs)
        d.nowMs += 24L * 3600 * 1000
        assertEquals("a day later it still waits", SyncStatus.BACKOFF_ACTIVE, d.sync().status)
        assertEquals(1, channel.fetchCalls)
        d.nowMs += 12L * 3600 * 1000 - 1
        assertEquals(SyncStatus.BACKOFF_ACTIVE, d.sync().status)
        d.nowMs += 1
        assertEquals(SyncStatus.OK, d.sync().status)
    }
}
