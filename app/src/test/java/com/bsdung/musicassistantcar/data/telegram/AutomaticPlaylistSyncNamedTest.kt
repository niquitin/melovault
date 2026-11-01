package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Nhiều Playlist có tên, S8: the foreground scheduler runs the named playlists after the favourites. Fake actions, no TDLib. */
class AutomaticPlaylistSyncNamedTest {
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

        fun nextAt(): Long? = tasks.filter { !it.cancelled }.minOfOrNull { it.at }
    }

    private fun done(status: SyncStatus, retryAfterMs: Long = 0L, acked: Int = 0, sent: Int = 0, recovery: RecoveryReason? = null) =
        Gated.Done(SyncReport(status, recovery = recovery, retryAfterMs = retryAfterMs, opsAcknowledged = acked, envelopesSent = sent))

    private inner class Rig(val name: String, enabled: Boolean = true) {
        val timer = Timer()
        val channel = FakeTelegramChannel()
        val device = FakeSyncDevice(name, folder.newFolder(), account, channel, name.length + 20)
        var who: TelegramUserId? = account
        val calls = mutableListOf<String>()
        val namedModes = mutableListOf<SyncMode>()
        val namedEnvelopes = mutableListOf<Int>()
        var forcedFavourite: SyncReport? = null
        var namedAnswer: () -> Gated<SyncReport> = { done(SyncStatus.OK) }
        var onFavouriteRun: (() -> Unit)? = null
        val log = SyncRunLog()
        val memory = InMemorySyncConsentStore()
        val actions = object : SyncActionsPort {
            override fun discover(account: TelegramUserId): Gated<ChannelDiscovery> = Gated.Done(ChannelDiscovery.Found(channel.channelId))
            override fun create(account: TelegramUserId): Gated<CreateOutcome> = error("never create a channel")
            override fun bind(account: TelegramUserId, chatId: Long): Gated<BindResult> = Gated.Done(BindResult.Bound)
            override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int): Gated<SyncReport> {
                calls += "fav"
                onFavouriteRun?.invoke()
                device.nowMs = timer.now
                return Gated.Done(forcedFavourite ?: device.coordinator.runOnce(account, mode, sendBudget))
            }

            override fun runNamed(account: TelegramUserId, mode: SyncMode, maxEnvelopes: Int): Gated<SyncReport> {
                calls += "named"
                namedModes += mode
                namedEnvelopes += maxEnvelopes
                return namedAnswer()
            }
        }
        val sync = AutomaticPlaylistSync(enabled, actions, memory, device.session, { who }, { 1L }, timer::schedule, { timer.now }, log)

        fun permit() {
            memory.save(account, SyncConsent(enabled = true, mayTransmitInitial = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION))
        }

        fun start() { permit(); sync.setForeground(true); timer.advance() }
    }

    @Test fun theNamedPlaylistsRunAfterTheFavouritesInTheSameTurnWithOneEnvelope() {
        val r = Rig("order")
        r.start()
        assertEquals(listOf("fav", "named"), r.calls)
        assertEquals(listOf(SyncMode.INCREMENTAL), r.namedModes)
        assertEquals(listOf(1), r.namedEnvelopes)
        assertEquals(SyncStatus.OK, r.log.last(account.value)?.status)
        r.timer.advance(15_000)
        assertEquals(listOf("fav", "named", "fav", "named"), r.calls)
    }

    @Test fun aFloodWaitOfTheFavouritesStopsTheWholeTurnAndTheNextOne() {
        val r = Rig("flood")
        r.forcedFavourite = SyncReport(SyncStatus.BACKOFF, retryAfterMs = 35_000L)
        r.start()
        assertEquals("the named playlists did not run", listOf("fav"), r.calls)
        r.forcedFavourite = null
        r.timer.advance(20_000)
        assertEquals("still waiting for the server's number of seconds", listOf("fav"), r.calls)
        r.timer.advance(20_000)
        assertEquals(listOf("fav", "fav", "named"), r.calls)
    }

    @Test fun aFavouritesRecoveryProblemKeepsTheNamedPlaylistsOutOfTheTurnToo() {
        val r = Rig("recovery")
        r.forcedFavourite = SyncReport(SyncStatus.RECOVERY_REQUIRED, recovery = RecoveryReason.CHANNEL_MISSING)
        r.start()
        r.timer.advance(120_000)
        assertEquals(listOf("fav"), r.calls)
        for (status in listOf(SyncStatus.SAVE_FAILED, SyncStatus.STALE_SESSION, SyncStatus.NO_CHANNEL, SyncStatus.NOT_OPEN, SyncStatus.BACKOFF_ACTIVE)) {
            val x = Rig("skip-${status.name.replace('_', '-')}")
            x.forcedFavourite = SyncReport(status)
            x.start()
            assertFalse(status.name, "named" in x.calls)
        }
    }

    @Test fun aNamedProblemDoesNotSpoilTheFavouritesReportNorStopThem() {
        val r = Rig("named-bad")
        r.namedAnswer = { done(SyncStatus.RECOVERY_REQUIRED, recovery = RecoveryReason.CORRUPT_MESSAGE) }
        r.start()
        assertEquals(SyncStatus.OK, r.log.last(account.value)?.status)
        assertNotNull("the next poll is still scheduled", r.timer.nextAt())
        r.timer.advance(15_000)
        assertEquals(listOf("fav", "named", "fav", "named"), r.calls)
    }

    @Test fun aNamedRunThatThrowsOrIsDeniedChangesNothing() {
        val thrown = Rig("named-throws")
        thrown.namedAnswer = { throw IllegalStateException("boom") }
        thrown.start()
        assertEquals(SyncStatus.OK, thrown.log.last(account.value)?.status)
        thrown.timer.advance(15_000)
        assertEquals(4, thrown.calls.size)
        val denied = Rig("named-denied")
        denied.namedAnswer = { Gated.Denied(SyncDenied.NOT_OPEN) }
        denied.start()
        assertEquals(15_000L, denied.timer.nextAt())
        assertEquals(SyncStatus.OK, denied.log.last(account.value)?.status)
    }

    @Test fun workLeftOnTheNamedSideBringsTheNextTurnForward() {
        val busy = Rig("named-busy")
        busy.namedAnswer = { done(SyncStatus.OUTBOX_INCOMPLETE, sent = 1, acked = 20) }
        busy.start()
        assertEquals(1_000L, busy.timer.nextAt())
        busy.timer.advance(1_000)
        assertEquals(listOf("fav", "named", "fav", "named"), busy.calls)
        val catching = Rig("named-catching")
        catching.namedAnswer = { done(SyncStatus.CATCH_UP_INCOMPLETE) }
        catching.start()
        assertEquals(1_000L, catching.timer.nextAt())
        val idle = Rig("named-idle")
        idle.start()
        assertEquals(15_000L, idle.timer.nextAt())
        val stuck = Rig("named-no-progress")
        stuck.namedAnswer = { done(SyncStatus.SEND_LIMIT_REACHED) }
        stuck.start()
        assertEquals("no progress, no hurry", 15_000L, stuck.timer.nextAt())
    }

    @Test fun aConnectionProblemOfTheNamedRunHoldsBackBothButOtherNamedProblemsDoNot() {
        val flood = Rig("named-flood")
        flood.namedAnswer = { done(SyncStatus.BACKOFF, retryAfterMs = 35_000L) }
        flood.start()
        assertEquals(35_000L, flood.timer.nextAt())
        flood.namedAnswer = { done(SyncStatus.OK) }
        flood.timer.advance(34_000)
        assertEquals(listOf("fav", "named"), flood.calls)
        flood.timer.advance(2_000)
        assertEquals(listOf("fav", "named", "fav", "named"), flood.calls)
        val unknown = Rig("named-unconfirmed")
        unknown.namedAnswer = { done(SyncStatus.DELIVERY_UNCONFIRMED, retryAfterMs = 5_000L) }
        unknown.start()
        assertEquals("a delivery in doubt of the named playlists holds nothing back", 15_000L, unknown.timer.nextAt())
    }

    @Test fun turningTheSyncOffStopsBothAndKeepsTheData() {
        val r = Rig("off")
        r.device.toggle(-1001234L, 5)
        r.start()
        val stored = r.device.stored()
        assertEquals(listOf("fav", "named"), r.calls.take(2))
        assertTrue(r.sync.turnOff())
        val callsAfterOff = r.calls.size
        r.timer.advance(120_000)
        r.sync.requestNow()
        r.timer.advance(120_000)
        assertEquals(callsAfterOff, r.calls.size)
        assertEquals("nothing was deleted", stored.copy(checkpoint = stored.checkpoint), r.device.stored().copy(checkpoint = stored.checkpoint))
        assertEquals(1, r.device.present().size)
    }

    @Test fun consentWithdrawnDuringTheFavouritesRunKeepsTheNamedRunAway() {
        val r = Rig("withdrawn")
        r.onFavouriteRun = { r.memory.save(account, SyncConsent()) }
        r.start()
        assertEquals(listOf("fav"), r.calls)
    }

    @Test fun anAccountSwitchedDuringTheFavouritesRunKeepsTheNamedRunAway() {
        val r = Rig("switched")
        r.onFavouriteRun = { r.who = TelegramUserId(2002L) }
        r.start()
        assertEquals(listOf("fav"), r.calls)
        r.who = null
        r.timer.advance(120_000)
        assertEquals(listOf("fav"), r.calls)
    }

    @Test fun aBuildWithTheSwitchOffCallsNeitherSide() {
        val r = Rig("build-off", enabled = false)
        r.permit()
        r.sync.setForeground(true)
        r.sync.requestNow()
        r.sync.afterSave()
        r.timer.advance(120_000)
        assertTrue(r.calls.isEmpty())
    }

    // ---- the ports ---------------------------------------------------------------------------------

    @Test fun aPortThatKnowsNothingOfNamedPlaylistsAnswersBuildOffAndSoDoesTheDisabledOne() {
        val plain = object : SyncActionsPort {
            override fun discover(account: TelegramUserId): Gated<ChannelDiscovery> = error("unused")
            override fun create(account: TelegramUserId): Gated<CreateOutcome> = error("unused")
            override fun bind(account: TelegramUserId, chatId: Long): Gated<BindResult> = error("unused")
            override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int): Gated<SyncReport> = error("unused")
        }
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), plain.runNamed(account, SyncMode.INCREMENTAL))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), DisabledSyncActions.runNamed(account, SyncMode.INCREMENTAL))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), DisabledSyncActions.runNamed(account, SyncMode.FULL_REPLAY, 5))
    }

    @Test fun aProviderWithTheSwitchOffNeverReadsTheNamedHostOrTheOwner() {
        var namedReads = 0
        val provider = SyncServiceProvider(
            false, { error("owner must not be read") }, { error("host must not be read") }, InMemorySyncConsentStore(),
            namedHost = { namedReads++; null },
        )
        val port = provider.actions()
        assertSame(DisabledSyncActions, port)
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), port.runNamed(account, SyncMode.INCREMENTAL))
        assertEquals(0, namedReads)
        assertFalse(provider.created())
    }
}
