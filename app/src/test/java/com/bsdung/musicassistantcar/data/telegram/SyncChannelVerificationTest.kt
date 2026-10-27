package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** E105 REWORK: all data and calls are fake; no real account or Telegram writes. */
class SyncChannelVerificationTest {
    private val a = TelegramUserId(1001L)
    private val b = TelegramUserId(2002L)
    private var current: TelegramUserId? = a
    private var epoch = 1L
    private var facts = PlaylistStatusFacts(a.value, 2, 1, 0, 0, true, 3L, false, false, null, null)
    private val store = InMemorySyncConsentStore().also {
        for (who in listOf(a, b)) it.save(who, SyncConsent(true, false, true, SyncConsent.WARNING_VERSION))
    }
    private val budgets = mutableListOf<Int>()
    private var discoveries = 0
    private var answer: Gated<ChannelDiscovery> = Gated.Done(ChannelDiscovery.Found(-77L))
    private var onDiscover: (() -> Unit)? = null
    private val port = object : SyncActionsPort {
        override fun discover(account: TelegramUserId): Gated<ChannelDiscovery> {
            discoveries++
            val result = answer
            onDiscover?.invoke()
            return result
        }
        override fun bind(account: TelegramUserId, chatId: Long) = Gated.Done(BindResult.Bound)
        override fun create(account: TelegramUserId): Gated<CreateOutcome> = error("creation is locked")
        override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int): Gated<SyncReport> {
            budgets += sendBudget
            return Gated.Done(SyncReport(SyncStatus.OK, opsDispatched = sendBudget))
        }
    }
    private fun controller() = SyncSettingsController(store, true, port, { current }, { facts }, epoch = { epoch })
    private fun assertLocked(c: SyncSettingsController) {
        val screen = c.screen()
        assertEquals(ControlState.LOCKED, screen.grantSend)
        assertEquals(ControlState.LOCKED, screen.syncNow)
        assertEquals(ControlState.LOCKED, screen.fullReplay)
        val before = c.sendGrant()
        val calls = budgets.toList()
        assertTrue(c.grantSend(1, true) is ActionResult.Refused)
        assertTrue(c.syncNow() is ActionResult.Refused)
        assertTrue(c.fullReplay(true) is ActionResult.Refused)
        assertEquals(before, c.sendGrant())
        assertEquals(calls, budgets)
    }

    @Test fun savedBindingNeedsExplicitDiscoveryOnEveryScreen() {
        val c = controller()
        repeat(3) { c.screen(); c.consent(); c.syncContext() }
        assertEquals(0, discoveries)
        assertEquals(ChannelView.Unchecked, c.screen().channel)
        assertLocked(c)
        assertEquals(ActionResult.Done, c.discover())
        assertEquals(ChannelView.Bound, c.screen().channel)
        assertEquals(ControlState.AVAILABLE, c.screen().syncNow)
        assertTrue(budgets.isEmpty())
        assertEquals(0, c.sendGrant())
        assertEquals(ActionResult.Done, c.grantSend(2, true))
        c.close()
        assertEquals(0, c.sendGrant())
        assertLocked(c)
        assertLocked(controller())
    }

    @Test fun everyUnsuccessfulRediscoveryOverridesBindingAndKeepsGrantUnspent() {
        val cases = listOf(
            ChannelDiscovery.Unsafe(UnsafeChannel.PUBLIC) to ChannelView.Unsafe(UnsafeChannel.PUBLIC),
            ChannelDiscovery.Failed(SyncErrorKind.NETWORK) to ChannelView.Failed(SyncErrorKind.NETWORK),
            ChannelDiscovery.Incomplete(IncompleteReason.BUDGET) to ChannelView.Incomplete(IncompleteReason.BUDGET),
            ChannelDiscovery.Ambiguous(2) to ChannelView.Ambiguous(2),
            ChannelDiscovery.NotFound to ChannelView.NotFound,
        )
        for ((result, view) in cases) {
            val c = controller()
            answer = Gated.Done(ChannelDiscovery.Found(-77L))
            c.discover()
            assertEquals(ActionResult.Done, c.grantSend(3, true))
            answer = Gated.Done(result)
            c.discover()
            assertEquals(view, c.screen().channel)
            assertLocked(c)
            assertEquals(3, c.sendGrant())
            answer = Gated.Done(ChannelDiscovery.Found(-77L))
            c.discover()
            assertEquals(ControlState.AVAILABLE, c.screen().syncNow)
            assertTrue(budgets.isEmpty())
            assertEquals(3, c.sendGrant())
        }
    }

    @Test fun discoveryDenialOrExceptionCannotReuseEarlierVerification() {
        val c = controller()
        c.discover()
        answer = Gated.Denied(SyncDenied.NOT_AUTHORIZED)
        assertEquals(ActionResult.Denied(SyncDenied.NOT_AUTHORIZED), c.discover())
        assertLocked(c)
        answer = Gated.Done(ChannelDiscovery.Found(-77L))
        c.discover()
        onDiscover = { throw IllegalStateException("fake failure") }
        try { c.discover(); fail("expected fake failure") } catch (_: IllegalStateException) { }
        assertLocked(c)
    }

    @Test fun discoveryWithoutBindingOrMatchingFactsDoesNotUnlockActions() {
        val c = controller()
        facts = facts.copy(channelBound = false)
        c.discover()
        assertEquals(ChannelView.Found(-77L), c.screen().channel)
        assertLocked(c)
        assertEquals(ActionResult.Done, c.bindFoundChannel(true))
        facts = facts.copy(channelBound = true, accountId = b.value)
        assertLocked(c)
        facts = facts.copy(accountId = a.value)
        assertEquals(ActionResult.Done, c.syncNow())
        assertEquals(listOf(0), budgets)
    }

    @Test fun turnOffAccountAndEpochChangesRequireFreshVerificationWithoutRestoringGrants() {
        for (change in listOf("off", "account", "epoch")) {
            current = a
            facts = facts.copy(accountId = a.value)
            val c = controller()
            c.enable(true); c.setAllowTransmit(true); c.discover(); c.grantSend(2, true)
            when (change) {
                "off" -> { c.turnOff(); c.enable(true) }
                "account" -> { current = b; c.screen(); current = a }
                "epoch" -> epoch++
            }
            assertLocked(c)
            assertEquals(0, c.sendGrant())
            c.discover()
            assertEquals(0, c.sendGrant())
            if (change == "off") assertFalse(c.consent().mayTransmitInitial)
            assertTrue(budgets.isEmpty())
        }
    }

    @Test fun findingLocksDirectCallsAndLateAnswersNeverUnlockANewerScope() {
        for (change in listOf("none", "account", "epoch", "off", "close")) {
            current = a
            val c = controller()
            c.enable(true); c.setAllowTransmit(true); c.discover(); c.grantSend(2, true)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            onDiscover = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            val worker = Thread { try { c.discover() } catch (t: Throwable) { failure.set(t) } }
            worker.start()
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertLocked(c)
                when (change) {
                    "account" -> current = b // no screen refresh before the late answer
                    "epoch" -> epoch++
                    "off" -> { c.turnOff(); c.enable(true) }
                    "close" -> c.close()
                }
            } finally { release.countDown(); worker.join(5_000); onDiscover = null }
            assertFalse(worker.isAlive)
            assertNull(failure.get())
            if (change == "none") {
                assertEquals(ControlState.AVAILABLE, c.screen().syncNow)
                assertEquals(2, c.sendGrant())
            } else {
                assertEquals(ChannelView.Unchecked, c.screen().channel)
                assertLocked(c)
                assertEquals(0, c.sendGrant())
            }
        }
    }

    @Test fun signedOutAccountLocksAllControlsAndNeverStartsDiscoveryOrRun() {
        current = null
        val c = controller()
        val s = c.screen()
        for (state in listOf(s.enable, s.allowCreate, s.allowTransmit, s.discover, s.bindNow, s.createNow))
            assertEquals(ControlState.LOCKED, state)
        assertLocked(c)
        assertEquals(ActionResult.Refused(ActionResult.Reason.NO_ACCOUNT), c.discover())
        assertEquals(0, discoveries)
    }
}
