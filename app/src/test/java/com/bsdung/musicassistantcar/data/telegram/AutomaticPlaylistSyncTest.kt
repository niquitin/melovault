package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AutomaticPlaylistSyncTest {
    @get:Rule val folder = TemporaryFolder()
    private val account = TelegramUserId(1001)

    private class Timer {
        data class Task(val at: Long, val work: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        val tasks = mutableListOf<Task>()
        fun schedule(delay: Long, work: () -> Unit): SyncScheduledTask {
            val task = Task(now + delay, work)
            tasks.add(task)
            return SyncScheduledTask { task.cancelled = true }
        }
        fun advance(ms: Long = 0) {
            val until = now + ms
            var limit = 1000
            while (true) {
                val task = tasks.filter { !it.cancelled && it.at <= until }.minByOrNull { it.at } ?: break
                check(limit-- > 0) { "unbounded timer loop" }
                tasks.remove(task); now = task.at; task.work()
            }
            now = until
        }
    }

    private inner class Rig(val name: String, channel: FakeTelegramChannel = FakeTelegramChannel(), enabled: Boolean = true) {
        val timer = Timer()
        val device = FakeSyncDevice(name, folder.newFolder(), account, channel, name.length + 12)
        var who: TelegramUserId? = account
        var epoch = 1L
        var failSave = false
        var failTransmitSave = false
        var discoveries = 0
        var closes = 0
        var changes = 0
        var discovery: Gated<ChannelDiscovery> = Gated.Done(ChannelDiscovery.Found(channel.channelId))
        var onDiscover: (() -> Unit)? = null
        var onRun: (() -> Unit)? = null
        var onClose: (() -> Unit)? = null
        var forced: SyncReport? = null
        val budgets = mutableListOf<Int>()
        val log = SyncRunLog()
        val memory = InMemorySyncConsentStore()
        val store = object : SyncConsentStore {
            override fun load(account: TelegramUserId) = memory.load(account)
            override fun save(account: TelegramUserId, consent: SyncConsent): Boolean =
                if (failSave || (failTransmitSave && consent.mayTransmitInitial)) false else memory.save(account, consent)
        }
        val actions = object : SyncActionsPort {
            override fun discover(account: TelegramUserId): Gated<ChannelDiscovery> {
                discoveries++; onDiscover?.invoke(); return discovery
            }
            override fun create(account: TelegramUserId): Gated<CreateOutcome> = error("never create a channel")
            override fun bind(account: TelegramUserId, chatId: Long): Gated<BindResult> {
                device.bindChannel(chatId); return Gated.Done(BindResult.Bound)
            }
            override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int): Gated<SyncReport> {
                budgets.add(sendBudget); onRun?.invoke()
                device.nowMs = timer.now
                return Gated.Done(forced ?: device.coordinator.runOnce(account, mode, sendBudget))
            }
            override fun close() { closes++; onClose?.invoke() }
        }
        val sync = AutomaticPlaylistSync(enabled, actions, store, device.session, { who }, { epoch },
            timer::schedule, { timer.now }, log) { changes++ }

        fun visible() { sync.setForeground(true); timer.advance() }
        fun permit(version: Int = SyncConsent.WARNING_VERSION) {
            store.save(account, SyncConsent(enabled = true, mayTransmitInitial = true, acceptedWarningVersion = version))
        }
        fun toggle(n: Int) { device.toggle(account.value, n * 4_194_304L); sync.afterSave() }
        fun enable(): Boolean {
            var result = false
            sync.enable(sync.pendingReview()!!, true) { ok, _ -> result = ok }
            timer.advance()
            return result
        }
    }

    @Test fun oldManualConsentDoesNotDiscoverOrSendAndNewConfirmationIsRequired() {
        val r = Rig("old")
        r.permit(2); r.toggle(1); r.visible(); r.timer.advance(60_000)
        assertEquals(0, r.discoveries); assertTrue(r.budgets.isEmpty())
        assertTrue(r.enable())
        assertTrue(r.store.load(account).automatic)
        assertEquals(1, r.device.channel.sendCalls)
    }

    @Test fun disabledBuildAndSignedOutSessionRemainInert() {
        val off = Rig("off", enabled = false)
        off.permit(); off.visible(); off.sync.requestNow(); off.timer.advance(60_000)
        assertEquals(0, off.discoveries); assertNull(off.sync.pendingReview())
        val out = Rig("out")
        out.who = null; out.visible(); out.sync.requestNow(); out.timer.advance()
        assertEquals(0, out.discoveries); assertNull(out.sync.pendingReview())
    }

    @Test fun exactPendingReviewIncludesAddThenRemoveAndCannotApproveLaterChanges() {
        val r = Rig("review")
        r.visible(); r.toggle(1); r.toggle(1)
        val review = r.sync.pendingReview()!!
        assertEquals(listOf(OpType.ADD, OpType.REMOVE), review.entries.map { it.op.type })
        assertTrue(r.device.present().isEmpty())
        r.toggle(2)
        var result: Boolean? = null
        r.sync.enable(review, true) { ok, _ -> result = ok }; r.timer.advance()
        assertEquals(false, result); assertFalse(r.store.load(account).automatic)
        assertEquals(0, r.discoveries); assertTrue(r.budgets.isEmpty())
    }

    @Test fun setupFailureOrChangedReviewDuringDiscoveryNeverEnablesSending() {
        val r = Rig("setup")
        r.visible(); r.toggle(1)
        r.onDiscover = { r.toggle(2) }
        assertFalse(r.enable()); assertFalse(r.store.load(account).automatic)
        assertTrue(r.budgets.isEmpty())
        r.onDiscover = null
        r.discovery = Gated.Done(ChannelDiscovery.Ambiguous(2))
        assertFalse(r.enable()); assertFalse(r.store.load(account).automatic)
        r.discovery = Gated.Done(ChannelDiscovery.Found(r.device.channel.channelId))
        r.failSave = true
        assertFalse(r.enable()); assertFalse(r.store.load(account).automatic)
        r.failSave = false; r.failTransmitSave = true
        assertFalse(r.enable()); assertFalse(r.store.load(account).automatic)
        assertTrue(r.budgets.isEmpty())
    }

    @Test fun successfulSetupBindsOnlyTheUniqueVerifiedExistingChannel() {
        val r = Rig("bind")
        val v = r.device.session.view(account)!!
        r.device.session.update(account, v.epoch) { it.copy(checkpoint = SyncCheckpoint(0, 0)) }
        r.visible(); assertTrue(r.enable())
        assertEquals(r.device.channel.channelId, r.device.stored().checkpoint.channelId)
        assertEquals(SyncStatus.OK, r.log.last(account.value)?.status)
    }

    @Test fun addRemoveReaddConvergesWithoutManualSyncOrRestartAndKeepsBaseline() {
        val channel = FakeTelegramChannel()
        val a = Rig("device-a", channel); val b = Rig("device-b", channel)
        a.permit(); b.permit(); a.visible(); b.visible()
        a.toggle(1); a.toggle(2); a.timer.advance(500); b.timer.advance(15_000)
        val baseline = a.device.present().toSet()
        a.toggle(3); a.timer.advance(500); b.timer.advance(15_000)
        assertEquals(a.device.present(), b.device.present()); assertEquals(3, b.device.present().size)
        b.toggle(3); b.timer.advance(500); a.timer.advance(15_000)
        assertEquals(baseline, a.device.present().toSet())
        a.toggle(3); a.timer.advance(500); b.timer.advance(15_000)
        assertEquals(a.device.present(), b.device.present())
        assertEquals(3, b.device.present().distinct().size)
        assertTrue(b.device.present().containsAll(baseline))
        a.timer.advance(30_000); b.timer.advance(30_000)
        assertTrue(a.device.pendingIds().isEmpty()); assertTrue(b.device.pendingIds().isEmpty())
        assertEquals(a.device.present(), b.device.present())
    }

    @Test fun debounceAndContinuationDrainMoreThanThreeWithoutRaisingPerRunCap() {
        val r = Rig("batch")
        r.permit(); r.visible()
        repeat(8) { r.toggle(it + 1) }
        r.timer.advance(499)
        assertEquals(0, r.device.channel.sendCalls)
        r.timer.advance(1)
        assertEquals(5, r.device.pendingIds().size)
        r.timer.advance(2_000)
        assertTrue(r.device.pendingIds().isEmpty())
        assertTrue(r.budgets.all { it == 3 })
    }

    @Test fun backgroundDoesNotPollAndReturningReadsMissedChangesImmediately() {
        val channel = FakeTelegramChannel()
        val a = Rig("foreground", channel); val b = Rig("background", channel)
        a.permit(); b.permit(); a.visible(); b.visible()
        b.sync.setForeground(false)
        val previous = b.discoveries
        a.toggle(1); a.timer.advance(500)
        b.timer.advance(60_000)
        assertEquals(previous, b.discoveries); assertTrue(b.device.present().isEmpty())
        b.visible()
        assertEquals(a.device.present(), b.device.present())
    }

    @Test fun stopOrEpochChangeDuringDiscoveryPreventsRunAndLateSuccess() {
        for (mode in listOf("background", "off", "epoch", "account")) {
            val r = Rig(mode)
            r.permit(); r.toggle(1)
            r.onDiscover = {
                r.onDiscover = null
                when (mode) {
                    "background" -> r.sync.setForeground(false)
                    "off" -> r.sync.turnOff()
                    "epoch" -> r.epoch++
                    "account" -> r.who = TelegramUserId(2002)
                }
            }
            r.visible()
            assertTrue(mode, r.budgets.isEmpty())
            assertNull(mode, r.log.last(account.value))
            assertEquals(1, r.device.pendingIds().size)
        }
    }

    @Test fun repeatedManualRequestsDuringRunCoalesceIntoOneScheduledRun() {
        val r = Rig("coalesce")
        r.permit()
        r.onRun = { repeat(20) { r.sync.requestNow(); r.sync.afterSave() }; r.onRun = null }
        r.visible()
        assertEquals(1, r.budgets.size)
        r.timer.advance(500)
        assertEquals(2, r.budgets.size)
        r.timer.advance(14_999)
        assertEquals(2, r.budgets.size)
    }

    @Test fun floodWaitFromDiscoverySurvivesManualRequestAndForegroundReentry() {
        val r = Rig("discovery-wait")
        r.permit(); r.discovery = Gated.Done(ChannelDiscovery.Failed(SyncErrorKind.FLOOD_WAIT, 35))
        r.visible()
        r.sync.requestNow(); r.sync.setForeground(false); r.sync.setForeground(true)
        r.timer.advance(34_999)
        assertEquals(1, r.discoveries); assertTrue(r.budgets.isEmpty())
        r.discovery = Gated.Done(ChannelDiscovery.Found(r.device.channel.channelId))
        r.timer.advance(1)
        assertEquals(2, r.discoveries); assertEquals(1, r.budgets.size)
    }

    @Test fun networkBackoffRetainsPendingAndEventuallySendsOnce() {
        val r = Rig("network")
        r.device.channel.fetchFaults.add(FetchResult.Failed(SyncErrorKind.NETWORK))
        r.permit(); r.toggle(1); r.visible()
        assertEquals(1, r.device.pendingIds().size); assertEquals(0, r.device.channel.sendCalls)
        r.timer.advance(120_000)
        assertTrue(r.device.pendingIds().isEmpty()); assertEquals(1, r.device.channel.sendCalls)
    }

    @Test fun unknownDeliveryIsReadBackAndNeverSentTwice() {
        val r = Rig("uncertain")
        r.device.channel.sendFaults.add(FakeTelegramChannel.SendFault.LOST_ACK)
        r.device.channel.lostAckHiddenFetches = 2
        r.permit(); r.toggle(1); r.visible()
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, r.log.last(account.value)?.status)
        assertEquals(1, r.device.channel.sendCalls)
        r.timer.advance(180_000)
        assertEquals(1, r.device.channel.sendCalls)
        assertTrue(r.device.pendingIds().isEmpty())
    }

    @Test fun unsafeChannelDoesNotSendOrKeepOldSyncedLabel() {
        val r = Rig("unsafe")
        r.permit(); r.visible()
        assertEquals(SyncStatus.OK, r.log.last(account.value)?.status)
        r.discovery = Gated.Done(ChannelDiscovery.Unsafe(UnsafeChannel.PUBLIC))
        r.toggle(1); r.timer.advance(500)
        assertEquals(0, r.device.channel.sendCalls)
        assertNotEquals(SyncStatus.OK, r.log.last(account.value)?.status)
        assertNotNull(r.sync.notice)
    }

    @Test fun failedTurnOffStillInhibitsThisProcessAndPreservesPlaylistAndOutbox() {
        val r = Rig("off-failure")
        r.permit(); r.visible(); r.toggle(1)
        val before = r.device.stored()
        r.failSave = true
        assertFalse(r.sync.turnOff())
        r.sync.requestNow(); r.sync.setForeground(false); r.sync.setForeground(true); r.timer.advance(60_000)
        assertEquals(before, r.device.stored())
        assertFalse(r.sync.context(account).consent.automatic)
        assertTrue(r.sync.needsDisableRetry)
        assertEquals(0, r.device.channel.sendCalls)
        r.failSave = false
        assertTrue(r.sync.turnOff())
        assertFalse(r.sync.needsDisableRetry)
        assertFalse(r.store.load(account).automatic)
    }

    @Test fun lateRunResultDoesNotPublishAfterEpochChanges() {
        val r = Rig("late-result")
        r.permit(); r.forced = SyncReport(SyncStatus.OK)
        r.onRun = { r.epoch++ }
        r.visible()
        assertNull(r.log.last(account.value))
    }

    @Test fun leavingBeforeSetupDispatchDoesNotEnableOrLeaveTheCoordinatorBusy() {
        val r = Rig("cancel-setup")
        r.visible()
        var result: Boolean? = null
        r.sync.enable(r.sync.pendingReview()!!, true) { ok, _ -> result = ok }
        r.sync.setForeground(false); r.timer.advance()
        assertEquals(false, result); assertFalse(r.sync.isRunning())
        assertFalse(r.store.load(account).automatic); assertEquals(0, r.discoveries)
        r.visible(); assertTrue(r.enable())
    }

    @Test fun anUnfinishedHistoricalReplayIsCompletedBeforeIncrementalPolling() {
        val r = Rig("replay")
        val channel = r.device.channel
        channel.inject(envelopeText(account.value, "remote", addOp("remote", 1, account.value, songId(1))))
        channel.inject(envelopeText(account.value, "remote", addOp("remote", 2, account.value, songId(2))))
        val view = r.device.session.view(account)!!
        r.device.session.update(account, view.epoch) {
            it.copy(checkpoint = SyncCheckpoint(channel.channelId, channel.maxId(), replayPosition = 0))
        }
        r.permit(); r.visible()
        assertEquals(2, r.device.present().size)
        assertEquals(SyncCheckpoint.NO_REPLAY, r.device.stored().checkpoint.replayPosition)
        assertEquals(SyncStatus.OK, r.log.last(account.value)?.status)
    }

    @Test fun turningOffCannotRevokeADifferentAccountThatAppearsDuringCancellation() {
        val r = Rig("off-account-race")
        val other = TelegramUserId(2002)
        val consent = SyncConsent(true, false, true, SyncConsent.WARNING_VERSION)
        r.store.save(other, consent)
        r.permit(); r.visible()
        r.onClose = { r.who = other; r.epoch++ }
        assertFalse(r.sync.turnOff())
        assertEquals(consent, r.store.load(other))
    }

    @Test fun aNewAuthEpochOrAccountRoundTripCannotUndoAFailedTurnOff() {
        val r = Rig("new-epoch")
        r.permit(); r.visible()
        r.failSave = true; assertFalse(r.sync.turnOff()); r.failSave = false
        r.epoch++
        val before = r.budgets.size
        r.sync.requestNow(); r.timer.advance()
        assertEquals(before, r.budgets.size)
        r.who = TelegramUserId(2002); r.sync.context(r.who)
        r.who = account; r.epoch++; r.sync.requestNow(); r.timer.advance()
        assertEquals(before, r.budgets.size)
        assertTrue(r.sync.needsDisableRetry)
        assertTrue(r.enable()) // only a new explicit opt-in can resume this account
        assertEquals(before + 1, r.budgets.size)
    }

    @Test fun invalidatingAClosingPlaylistNeverRestartsTheObsoleteSession() {
        val r = Rig("closing-playlist")
        r.permit(); r.toggle(1)
        r.onDiscover = { r.onDiscover = null; r.sync.invalidate() }
        r.visible(); r.timer.advance(60_000)
        assertEquals(1, r.discoveries); assertTrue(r.budgets.isEmpty())
        assertNull(r.log.last(account.value))
        r.device.session.close(); r.device.session.open(account)
        r.sync.requestNow(); r.timer.advance()
        assertEquals(1, r.budgets.size)
        assertTrue(r.device.pendingIds().isEmpty())
    }

    @Test fun playlistReadyDuringOldRunCancellationIsNotLost() {
        val r = Rig("ready-after-cancel")
        r.permit(); r.toggle(1)
        r.onDiscover = {
            r.onDiscover = null
            r.sync.invalidate()
            r.device.session.close(); r.device.session.open(account)
            r.sync.requestNow()
        }
        r.visible()
        assertEquals(2, r.discoveries)
        assertEquals(1, r.budgets.size)
        assertTrue(r.device.pendingIds().isEmpty())
    }
}
