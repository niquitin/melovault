package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.Random

/** Nhiều Playlist có tên, S7: the sync run over a shared fake channel (no TDLib). */
class NamedPlaylistSyncCoordinatorTest {
    @get:Rule val folder = TemporaryFolder()

    private val account = TelegramUserId(1001L)

    private class MemoryStore : NamedPlaylistPersistence {
        val files = HashMap<Long, NamedPlaylistState>()
        var failSaves = false
        override fun load(user: TelegramUserId): NamedLoadResult = files[user.value]?.let { NamedLoadResult.Valid(it) } ?: NamedLoadResult.Missing
        override fun save(user: TelegramUserId, state: NamedPlaylistState) {
            if (failSaves) throw IOException("disk full")
            files[user.value] = state
        }
    }

    /** One device with its own named-playlist store, clock and coordinator, all on the shared [channel]. */
    private inner class Device(
        val name: String,
        val channel: FakeTelegramChannel,
        private val config: SyncConfig = SyncConfig(),
        private val transportFactory: ((FakeTelegramChannel, TransportHooks) -> PlaylistSyncTransport)? = null,
    ) {
        val store = MemoryStore()
        val hooks = TransportHooks()
        var nowMs = 0L
        val backoff = SyncBackoff()
        lateinit var session: NamedPlaylistSession
        lateinit var coordinator: NamedPlaylistSyncCoordinator

        init { boot() }

        fun boot() {
            session = NamedPlaylistSession(store) { name }
            check(session.open(account) is NamedOpenResult.Ready)
            coordinator = NamedPlaylistSyncCoordinator(
                session, transportFactory?.invoke(channel, hooks) ?: channel.transport(hooks), { nowMs }, config, backoff,
            )
        }

        fun sync(mode: SyncMode = SyncMode.INCREMENTAL, budget: Int = TEST_SEND_BUDGET, channelId: Long = channel.channelId) =
            coordinator.runOnce(account, channelId, mode, budget)

        fun state(): NamedPlaylistState = checkNotNull(session.view(account)).state
        fun lists() = state().lists
        fun create(name: String): ListId = checkNotNull((session.createList(account, name) as NamedOutcome.Ok).listId)
        fun add(id: ListId, n: Long) = session.addTelegram(account, id, tg(n))
        fun pending() = state().outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED }
        fun order(id: ListId) = state().entries(id).map { it.item }
    }

    private fun tg(n: Long) = PlaylistItemId.Telegram(-1001234L, n)
    private val channel0 get() = FakeTelegramChannel()

    private fun assertSameContent(a: Device, b: Device) {
        assertEquals(a.lists(), b.lists())
        for (v in a.state().lists()) assertEquals(a.order(v.id), b.order(v.id))
    }

    // ---- two devices -----------------------------------------------------------------------------

    @Test fun twoDevicesCreateRenameAddMoveAndDeleteAndConverge() {
        val ch = channel0
        val a = Device("dev-a", ch)
        val b = Device("dev-b", ch)
        val id = a.create("Nhạc đi xe")
        for (n in 1L..4L) a.add(id, n)
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(SyncStatus.OK, b.sync().status)
        assertEquals(listOf("Nhạc đi xe"), b.state().lists().map { it.name })
        assertSameContent(a, b)
        // B renames, moves and adds
        b.session.renameList(account, id, "Đi xa")
        b.session.moveItem(account, id, tg(4), 0)
        b.add(id, 9)
        assertEquals(SyncStatus.OK, b.sync().status)
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(listOf("Đi xa"), a.state().lists().map { it.name })
        assertEquals(listOf<PlaylistItemId>(tg(4), tg(1), tg(2), tg(3), tg(9)), a.order(id))
        assertSameContent(a, b)
        // A removes a song and then deletes the playlist
        a.session.removeTelegram(account, id, tg(2))
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(SyncStatus.OK, b.sync().status)
        assertFalse(b.state().contains(id, tg(2)))
        a.session.deleteList(account, id)
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(SyncStatus.OK, b.sync().status)
        assertTrue(b.state().lists().isEmpty())
        assertSameContent(a, b)
        assertTrue(a.pending().isEmpty())
        assertTrue("acknowledged entries are pruned", a.state().outbox.isEmpty())
    }

    @Test fun changesMadeOnBothSidesAtTheSameTimeMerge() {
        val ch = channel0
        val a = Device("dev-a", ch)
        val b = Device("dev-b", ch)
        val id = a.create("Chung")
        a.sync(); b.sync()
        a.add(id, 1); a.session.renameList(account, id, "Tên A")
        b.add(id, 2); b.session.renameList(account, id, "Tên B")
        val other = b.create("Riêng của B")
        a.sync(); b.sync(); a.sync(); b.sync()
        assertSameContent(a, b)
        assertEquals(setOf(tg(1), tg(2)), a.order(id).toSet())
        assertEquals("the later device id wins a tie of names", "Tên B", a.state().nameOf(id))
        assertTrue(a.state().exists(other))
    }

    @Test fun aNewDeviceReplaysTheWholeHistoryFromZero() {
        val ch = channel0
        val a = Device("dev-a", ch)
        val id = a.create("Lịch sử")
        for (n in 1L..30L) a.add(id, n)
        a.session.deleteList(account, a.create("Sẽ xóa"))
        a.sync()
        val fresh = Device("dev-c", ch)
        assertEquals(0L, fresh.state().checkpoint.lastAppliedServerId)
        val report = fresh.sync()
        assertEquals(SyncStatus.OK, report.status)
        assertEquals(a.order(id), fresh.order(id))
        assertSameContent(a, fresh)
        assertEquals(ch.maxId(), fresh.state().checkpoint.lastAppliedServerId)
        val again = fresh.sync(SyncMode.FULL_REPLAY)
        assertEquals(SyncStatus.OK, again.status)
        assertSameContent(a, fresh)
        assertEquals(SyncCheckpoint.NO_REPLAY, fresh.state().checkpoint.replayPosition)
    }

    @Test fun aReplayLongerThanOneRunResumesFromItsStoredPosition() {
        val ch = channel0
        val a = Device("dev-a", ch, SyncConfig(maxEnvelopesPerRun = 1))
        val id = a.create("Dài")
        repeat(15) { round ->
            for (n in 1L..3L) a.add(id, round * 10L + n)
            assertTrue(a.sync().status != SyncStatus.RECOVERY_REQUIRED)
        }
        while (a.pending().isNotEmpty()) a.sync()
        val small = Device("dev-c", ch, SyncConfig(pageSize = 2, maxPagesPerRun = 2))
        var runs = 0
        var last = small.sync(SyncMode.FULL_REPLAY)
        while (last.status == SyncStatus.CATCH_UP_INCOMPLETE) {
            runs++
            assertTrue("a replay in progress keeps its position", small.state().checkpoint.replayPosition > 0L)
            small.boot() // a restart between runs
            last = small.sync(SyncMode.FULL_REPLAY)
            assertTrue(runs < 100)
        }
        assertTrue(runs > 1)
        assertEquals(SyncStatus.OK, last.status)
        assertSameContent(a, small)
    }

    @Test fun ownOperationsComeBackAcknowledgedAndPrunedNotAppliedTwice() {
        val ch = channel0
        val a = Device("dev-a", ch)
        val id = a.create("L")
        a.add(id, 1)
        assertEquals(SyncStatus.OK, a.sync().status)
        val before = a.lists()
        val second = a.sync()
        assertEquals(SyncStatus.OK, second.status)
        assertEquals(before, a.lists())
        assertTrue(a.state().outbox.isEmpty())
        assertEquals("both operations travelled in ONE message", 1, ch.texts().count { it.startsWith("#mvlist") })
    }

    // ---- the channel -----------------------------------------------------------------------------

    @Test fun theFirstRunBindsTheChannelAndAnotherOneLaterIsRecoveryRequired() {
        val ch = channel0
        val a = Device("dev-a", ch)
        assertEquals(0L, a.state().checkpoint.channelId)
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(ch.channelId, a.state().checkpoint.channelId)
        a.create("L")
        val fetches = ch.fetchCalls
        val sends = ch.sendCalls
        val wrong = a.sync(channelId = -1_009_999L)
        assertEquals(SyncStatus.RECOVERY_REQUIRED, wrong.status)
        assertEquals(RecoveryReason.CHANNEL_MISSING, wrong.recovery)
        assertEquals(fetches, ch.fetchCalls)
        assertEquals(sends, ch.sendCalls)
        assertEquals(ch.channelId, a.state().checkpoint.channelId)
    }

    @Test fun noChannelAndNotOpenNeverTouchTheTransport() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.create("L")
        assertEquals(SyncStatus.NO_CHANNEL, a.sync(channelId = 0L).status)
        a.session.close()
        assertEquals(SyncStatus.NOT_OPEN, a.sync().status)
        assertEquals(0, ch.fetchCalls)
        assertEquals(0, ch.sendCalls)
    }

    // ---- reading ---------------------------------------------------------------------------------

    @Test fun otherTextAndFavouritesMessagesAreSkippedAndTheCursorMovesOn() {
        val ch = channel0
        val a = Device("dev-a", ch)
        val id = a.create("L")
        a.add(id, 1)
        a.sync()
        val favouriteOp = PlaylistState.empty(account.value, "dev-z").addTelegram(tg(77)).op!!
        ch.inject(envelopeText(account.value, "dev-z", favouriteOp))
        ch.inject("melovault-sync v1 something else")
        ch.inject("hello")
        val b = Device("dev-b", ch)
        val report = b.sync()
        assertEquals(SyncStatus.OK, report.status)
        assertEquals(3, report.foreignSkipped)
        assertEquals(ch.maxId(), report.cursor)
        assertEquals(a.order(id), b.order(id))
    }

    @Test fun aDamagedNamedMessageStopsTheReaderBeforeItAfterTheGoodOnesWereKept() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.create("Một")
        a.sync()
        val goodId = ch.maxId()
        val good = ch.texts().last()
        val badId = ch.inject(good.dropLast(1) + (if (good.last() == '0') '1' else '0')) // same text, wrong checksum
        // a good message AFTER the damaged one must not be applied
        val later = NamedPlaylistState.empty(account.value, "dev-x").createList("Sau").state.outbox.map { it.op }
        ch.inject(NamedPlaylistSyncProtocol.encode(NamedPlaylistSyncProtocol.Envelope(
            PlaylistSyncProtocol.scopeOf(account.value), "dev-x", "dev-x-1", later,
        )))
        val reader = Device("dev-b", ch)
        val report = reader.sync()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.CORRUPT_MESSAGE, report.recovery)
        assertEquals(PlaylistSyncProtocol.Error.BAD_CHECKSUM, report.protocolError)
        assertEquals(badId, report.badMessageId)
        assertEquals(goodId, reader.state().checkpoint.lastAppliedServerId)
        assertEquals(listOf("Một"), reader.state().lists().map { it.name })
    }

    @Test fun aFutureVersionAndAnotherAccountsMessageStopTheReaderWithTheirOwnReasons() {
        val ch = channel0
        ch.inject("#mvlist v2\nsomething new")
        val a = Device("dev-a", ch)
        val future = a.sync()
        assertEquals(RecoveryReason.FUTURE_PROTOCOL, future.recovery)
        assertEquals(0L, a.state().checkpoint.lastAppliedServerId)
        val ch2 = channel0
        val other = NamedPlaylistState.empty(2002L, "dev-o").createList("Của người khác").state.outbox.map { it.op }
        ch2.inject(NamedPlaylistSyncProtocol.encode(NamedPlaylistSyncProtocol.Envelope(PlaylistSyncProtocol.scopeOf(2002L), "dev-o", "dev-o-1", other)))
        val b = Device("dev-b", ch2)
        val wrong = b.sync()
        assertEquals(RecoveryReason.WRONG_ACCOUNT, wrong.recovery)
        assertTrue(b.state().lists().isEmpty())
    }

    @Test fun ownOperationThatComesBackChangedIsADamagedLog() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.create("Gốc")
        val mine = a.pending().single().op
        val forged = NamedOp(mine.opId, mine.type, mine.listId, null, mine.seq, mine.dots, "Đã bị sửa", null)
        ch.inject(NamedPlaylistSyncProtocol.encode(NamedPlaylistSyncProtocol.Envelope(PlaylistSyncProtocol.scopeOf(account.value), "dev-a", "dev-a-1", listOf(forged))))
        val report = a.sync()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.CORRUPT_MESSAGE, report.recovery)
        assertEquals("Gốc", a.state().nameOf(mine.listId))
        assertEquals(1, a.pending().size)
        assertEquals(0, ch.sendCalls)
    }

    @Test fun aPageThatCannotProveItselfIsRefusedWithoutMovingTheCursor() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.create("L")
        a.sync()
        val unproven = Device("dev-b", ch, transportFactory = { c, _ ->
            object : PlaylistSyncTransport {
                override fun send(channelId: Long, text: String) = c.send(text) {}
                override fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int): FetchResult {
                    val page = c.fetchAfter(afterServerId, limit) as FetchResult.Page
                    return FetchResult.Page(page.messages, page.hasMore, FetchResult.Page.UNPROVEN)
                }
            }
        })
        val report = unproven.sync()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.TRANSPORT_CONTRACT, report.recovery)
        assertEquals(0L, unproven.state().checkpoint.lastAppliedServerId)
        assertTrue(unproven.state().lists().isEmpty())
        val stuck = Device("dev-c", ch, transportFactory = { c, _ ->
            object : PlaylistSyncTransport {
                override fun send(channelId: Long, text: String) = c.send(text) {}
                override fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int): FetchResult =
                    FetchResult.Page(emptyList(), true, afterServerId)
            }
        })
        assertEquals(RecoveryReason.TRANSPORT_CONTRACT, stuck.sync().recovery)
    }

    @Test fun shuffledAndRepeatedPagesGiveTheSameResult() {
        val ch = channel0
        val a = Device("dev-a", ch)
        val id = a.create("L")
        for (n in 1L..12L) {
            a.add(id, n)
            a.sync()
        }
        ch.shufflePages = true
        ch.duplicateInPage = true
        ch.maxPageSize = 1
        val b = Device("dev-b", ch, SyncConfig(pageSize = 1))
        assertEquals(SyncStatus.OK, b.sync().status)
        assertSameContent(a, b)
    }

    // ---- sending ---------------------------------------------------------------------------------

    @Test fun aFloodWaitIsObeyedAndNothingIsLostOrSentTwice() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.sync()
        a.create("L")
        ch.sendFaults.add(FakeTelegramChannel.SendFault.FLOOD_35)
        val first = a.sync()
        assertEquals(SyncStatus.BACKOFF, first.status)
        assertEquals(35_000L, first.retryAfterMs)
        assertTrue("a refusal leaves the operation exactly as it was", a.pending().single().status == OutboxStatus.PENDING)
        val calls = ch.sendCalls + ch.fetchCalls
        val early = a.sync()
        assertEquals(SyncStatus.BACKOFF_ACTIVE, early.status)
        assertEquals(calls, ch.sendCalls + ch.fetchCalls)
        a.nowMs = 36_000L
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(1, ch.texts().count { it.startsWith("#mvlist") })
    }

    @Test fun aSendWhoseAnswerWasLostIsNeverSentAgain() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.sync()
        val id = a.create("L")
        a.add(id, 1)
        ch.lostAckHiddenFetches = 3
        ch.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK)
        val lost = a.sync()
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, lost.status)
        assertTrue(a.pending().all { it.status == OutboxStatus.RETRY_REQUIRED })
        val ids = a.pending().map { it.op.opId }
        var status = lost.status
        var rounds = 0
        while (status != SyncStatus.OK) {
            a.nowMs += 600_000L
            status = a.sync().status
            assertTrue(rounds++ < 10)
            assertEquals("never sent again", 1, ch.sendCalls)
        }
        assertTrue("found in the history, acknowledged", a.pending().isEmpty())
        assertEquals(ids.toSet(), ch.texts().single { it.startsWith("#mvlist") }.lines().filter { it.startsWith("op ") }.map { it.split(' ')[1] }.toSet())
    }

    @Test fun aSendThatWasDefinitelyNotStoredIsRetriedLater() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.sync()
        a.create("L")
        ch.sendFaults.add(FakeTelegramChannel.SendFault.NETWORK)
        assertEquals(SyncStatus.BACKOFF, a.sync().status)
        assertEquals(OutboxStatus.PENDING, a.pending().single().status)
        a.nowMs += 600_000L
        assertEquals(SyncStatus.OK, a.sync().status)
        assertEquals(2, ch.sendCalls)
    }

    @Test fun theDoubtIsWrittenBeforeTheDispatchSoACrashLeavesItFlaggedNotRepeatable() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.sync()
        a.create("L")
        a.hooks.beforeSend = { throw SimulatedCrash() }
        try {
            a.sync()
            fail("the crash must not be swallowed")
        } catch (_: SimulatedCrash) {
        }
        a.hooks.beforeSend = null
        a.boot() // the process was killed: everything is reopened from the store
        assertTrue(a.pending().all { it.status == OutboxStatus.RETRY_REQUIRED })
        assertEquals(0, ch.sendCalls)
        a.nowMs += 600_000L
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, a.sync().status)
        assertEquals("blocked, never duplicated", 0, ch.sendCalls)
    }

    @Test fun ifTheDoubtCannotBeWrittenNothingIsSent() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.sync()
        a.create("L")
        a.store.failSaves = true
        val report = a.sync()
        assertEquals(SyncStatus.SAVE_FAILED, report.status)
        assertEquals(SyncStage.SEND_PRECHECK_PERSIST, report.stage)
        assertEquals(0, ch.sendCalls)
        assertEquals(OutboxStatus.PENDING, a.pending().single().status)
    }

    @Test fun theSendBudgetCountsOperationsAndZeroMeansReadOnly() {
        val ch = channel0
        val a = Device("dev-a", ch)
        val id = a.create("L")
        for (n in 1L..7L) a.add(id, n)
        val readOnly = a.sync(budget = 0)
        assertEquals(SyncStatus.SEND_LIMIT_REACHED, readOnly.status)
        assertEquals(0, ch.sendCalls)
        assertEquals(8, a.pending().size)
        val five = a.sync(budget = 5)
        assertEquals(SyncStatus.SEND_LIMIT_REACHED, five.status)
        assertEquals(5, five.opsDispatched)
        assertEquals(3, a.pending().size)
        assertEquals(1, ch.sendCalls)
        assertEquals(SyncStatus.OK, a.sync().status)
    }

    @Test fun oneMessagePerRunLikeProductionNeedsSeveralRunsForABigQueue() {
        val ch = channel0
        val config = SyncConfig(maxEnvelopesPerRun = NamedSyncLimits.PRODUCTION_ENVELOPES)
        val a = Device("dev-a", ch, config)
        val id = a.create("Hàng chờ 200 bài")
        for (n in 1L..200L) assertTrue(a.add(id, 1_000_000L + n) is NamedOutcome.Ok)
        assertEquals(201, a.pending().size)
        var runs = 0
        var report = a.sync(budget = NamedSyncLimits.OPS_PER_ENVELOPE)
        while (report.status != SyncStatus.OK) {
            assertEquals(SyncStatus.OUTBOX_INCOMPLETE, report.status)
            assertEquals(1, report.envelopesSent)
            assertTrue(report.opsDispatched in 1..NamedSyncLimits.OPS_PER_ENVELOPE)
            runs++
            report = a.sync(budget = NamedSyncLimits.OPS_PER_ENVELOPE)
            assertTrue(runs < 40)
        }
        val messages = ch.texts().filter { it.startsWith("#mvlist") }
        assertEquals(runs + 1, messages.size)
        assertTrue(messages.all { it.length <= NamedPlaylistSyncProtocol.MAX_CHARS })
        assertTrue("about 10 messages for 201 operations", messages.size in 10..12)
        val b = Device("dev-b", ch)
        assertEquals(SyncStatus.OK, b.sync().status)
        assertEquals(a.order(id), b.order(id))
        assertEquals(200, b.order(id).size)
    }

    @Test fun aRemovalThatWouldReachTheChannelBeforeItsAdditionIsBlocked() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.sync()
        val id = a.create("L")
        a.add(id, 1)
        a.session.removeTelegram(account, id, tg(1))
        val view = checkNotNull(a.session.view(account))
        a.session.update(account, view.epoch) { it.copy(outbox = it.outbox.reversed()) }
        val report = a.sync()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.DEPENDENCY_BLOCKED, report.recovery)
        assertEquals(0, ch.sendCalls)
    }

    // ---- sessions --------------------------------------------------------------------------------

    @Test fun aSessionThatChangesDuringTheRunStopsItWithoutTouchingTheNewOne() {
        val ch = channel0
        val a = Device("dev-a", ch)
        val b = Device("dev-b", ch)
        b.create("Từ B")
        b.sync()
        a.hooks.beforeFetch = { a.session.open(account) }
        val report = a.sync()
        assertEquals(SyncStatus.STALE_SESSION, report.status)
        assertEquals(0L, a.state().checkpoint.lastAppliedServerId)
        assertTrue(a.state().lists().isEmpty())
        assertEquals("only B's message was ever sent", 1, ch.sendCalls)
    }

    @Test fun aRunForAnotherAccountFindsNothingOpen() {
        val ch = channel0
        val a = Device("dev-a", ch)
        a.session.open(TelegramUserId(2002L))
        assertEquals(SyncStatus.NOT_OPEN, a.sync().status)
        assertEquals(0, ch.fetchCalls)
    }

    // ---- next to the favourites sync -------------------------------------------------------------

    @Test fun theFavouritesSyncOnTheSameChannelIgnoresNamedMessagesAndGivesTheUsualResult() {
        val ch = channel0
        val ctl = FakeTelegramChannel()
        val root = folder.newFolder()
        val control = FakeSyncDevice("dev-f", folder.newFolder(), account, ctl, 31)
        val withNamed = FakeSyncDevice("dev-f", root, account, ch, 31)
        val namedA = Device("dev-a", ch)
        val id = namedA.create("Trên cùng kênh")
        for (n in 1L..25L) namedA.add(id, n)
        for (round in 1..3) {
            namedA.sync(budget = NamedSyncLimits.OPS_PER_ENVELOPE)
            for (d in listOf(control, withNamed)) {
                d.toggle(-1001234L, round * 10L + 1)
                d.toggle(-1001234L, round * 10L + 2)
            }
            val expected = control.sync()
            val actual = withNamed.sync()
            assertEquals(expected.status, actual.status)
            assertEquals(SyncStatus.OK, actual.status)
            assertEquals(expected.opsApplied, actual.opsApplied)
            assertEquals(expected.opsAcknowledged, actual.opsAcknowledged)
            assertEquals(control.present(), withNamed.present())
            assertEquals(control.crdt(), withNamed.crdt())
        }
        assertTrue(ch.texts().any { it.startsWith("#mvlist") })
        val fresh = FakeSyncDevice("dev-g", folder.newFolder(), account, ch, 32)
        val freshReport = fresh.sync()
        assertEquals(SyncStatus.OK, freshReport.status)
        assertTrue(freshReport.foreignSkipped > 0)
        assertEquals(withNamed.present(), fresh.present())
    }

    @Test fun namedMessagesDoNotMoveTheFavouritesStateAndTheOtherWayRound() {
        val ch = channel0
        val fav = FakeSyncDevice("dev-f", folder.newFolder(), account, ch, 41)
        val named = Device("dev-n", ch)
        fav.toggle(-1001234L, 5)
        fav.sync()
        val id = named.create("L")
        named.add(id, 1)
        val first = named.sync()
        assertEquals(SyncStatus.OK, first.status)
        assertEquals("the named store read the favourites message as foreign", 1, first.foreignSkipped)
        assertEquals(1, named.state().lists().size)
        assertEquals(0, named.pending().size)
        assertEquals(1, fav.present().size)
        assertEquals(SyncStatus.OK, fav.sync().status)
        assertEquals(1, fav.present().size)
        assertEquals(0, fav.pendingIds().size)
    }

    // ---- many devices ----------------------------------------------------------------------------

    @Test fun randomWorkOnThreeDevicesThroughOneChannelConverges() {
        for (seed in 1L..12L) {
            val random = Random(seed)
            val ch = FakeTelegramChannel(seed)
            val devices = listOf(Device("dev-a", ch), Device("dev-b", ch), Device("dev-c", ch))
            repeat(50) {
                val d = devices[random.nextInt(devices.size)]
                val lists = d.state().lists().map { it.id }
                val pick = if (lists.isEmpty()) null else lists[random.nextInt(lists.size)]
                val song = 1L + random.nextInt(8)
                when (random.nextInt(9)) {
                    0 -> d.session.createList(account, "Playlist ${random.nextInt(5)}")
                    1 -> pick?.let { d.session.renameList(account, it, "Tên ${random.nextInt(5)}") }
                    2 -> if (random.nextInt(4) == 0) pick?.let { d.session.deleteList(account, it) }
                    3, 4 -> pick?.let { d.add(it, song) }
                    5 -> pick?.let { d.session.removeTelegram(account, it, tg(song)) }
                    6 -> pick?.let { d.session.moveItem(account, it, tg(song), random.nextInt(5)) }
                    else -> { d.nowMs += 400_000L; d.sync(budget = NamedSyncLimits.OPS_PER_ENVELOPE) }
                }
            }
            repeat(4) { for (d in devices) { d.nowMs += 400_000L; d.sync() } }
            for (d in devices.drop(1)) assertSameContent(devices[0], d)
            assertTrue("seed=$seed nothing left waiting", devices.all { it.pending().isEmpty() })
            assertNotEquals(0, ch.count())
        }
    }
}
