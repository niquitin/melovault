package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * E96 P5B3 REWORK-01 (c)+(f): the send grant (a number of operations the person allowed, in memory only) and the
 * lock on channel creation, from the Settings controller down to the TDLib service. Fakes only; no Telegram.
 */
class SyncSendGrantTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val other = TelegramUserId(2002L)
    private val rigs = ArrayList<SyncRig>()

    @After fun tearDown() = rigs.forEach { it.stop() }

    private class FakeActions : SyncActionsPort {
        val budgets = ArrayList<Int>()
        var creates = 0
        var answer: Gated<SyncReport> = Gated.Done(SyncReport(SyncStatus.OK))
        var boom: Throwable? = null
        override fun discover(account: TelegramUserId): Gated<ChannelDiscovery> = Gated.Done(ChannelDiscovery.NotFound)
        override fun create(account: TelegramUserId): Gated<CreateOutcome> { creates++; return Gated.Done(CreateOutcome.Created(-1_000L)) }
        override fun bind(account: TelegramUserId, chatId: Long): Gated<BindResult> = Gated.Done(BindResult.Bound)
        override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int): Gated<SyncReport> {
            budgets += sendBudget
            boom?.let { throw it }
            return answer
        }
    }

    private var current: TelegramUserId? = acct
    private var facts: PlaylistStatusFacts? = PlaylistStatusFacts(1001L, 5, 5, 0, 0, true, 9L, false, false, null, null)
    private val store = InMemorySyncConsentStore()
    private val port = FakeActions()

    private fun controller(
        build: Boolean = true, unlocked: Boolean = false, s: SyncConsentStore = store, p: SyncActionsPort = port,
    ) = SyncSettingsController(s, build, p, { current }, { facts }, channelCreationUnlocked = unlocked)

    private fun SyncSettingsController.optIn(create: Boolean = false, transmit: Boolean = true) {
        assertEquals(ActionResult.Done, enable(true))
        if (create) assertEquals(ActionResult.Done, setAllowCreate(true))
        if (transmit) assertEquals(ActionResult.Done, setAllowTransmit(true))
    }

    private fun report(dispatched: Int, status: SyncStatus = SyncStatus.SEND_LIMIT_REACHED) = Gated.Done(SyncReport(status, opsDispatched = dispatched))

    // ---- the grant itself -------------------------------------------------------------------------------------------------

    @Test fun aGrantNeedsOptInConsentToTransmitABoundChannelAndAFreshTap() {
        assertEquals(ActionResult.Refused(ActionResult.Reason.BUILD_OFF), controller(build = false).grantSend(3, true))
        val c = controller()
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_ENABLED), c.grantSend(3, true))
        c.optIn(transmit = false)
        assertEquals(ActionResult.Refused(ActionResult.Reason.TRANSMIT_NOT_ALLOWED), c.grantSend(3, true))
        assertEquals(ActionResult.Done, c.setAllowTransmit(true))
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_CONFIRMED), c.grantSend(3, false))
        facts = facts!!.copy(channelBound = false)
        assertEquals(ActionResult.Refused(ActionResult.Reason.NO_CHANNEL), c.grantSend(3, true))
        facts = facts!!.copy(channelBound = true)
        assertEquals(0, c.sendGrant())
        assertEquals(ActionResult.Done, c.grantSend(3, true))
        assertEquals(3, c.sendGrant())
    }

    @Test fun aGrantIsAWholeNumberFromOneToThreeNothingElse() {
        val c = controller().also { it.optIn() }
        for (bad in listOf(0, -1, 4, 10, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals("grant $bad", ActionResult.Refused(ActionResult.Reason.BAD_SEND_BUDGET), c.grantSend(bad, true))
            assertEquals(0, c.sendGrant())
        }
        for (good in 1..3) {
            assertEquals(ActionResult.Done, c.grantSend(good, true))
            assertEquals(good, c.sendGrant())
        }
    }

    @Test fun aNewGrantReplacesTheOldOneItNeverAddsUp() {
        val c = controller().also { it.optIn() }
        assertEquals(ActionResult.Done, c.grantSend(3, true))
        assertEquals(ActionResult.Done, c.grantSend(1, true))
        assertEquals(1, c.sendGrant())
        assertEquals(ActionResult.Done, c.grantSend(2, true))
        assertEquals(ActionResult.Done, c.grantSend(2, true))
        assertEquals(2, c.sendGrant())
    }

    // ---- what a run is given and what it costs -------------------------------------------------------------------------------

    @Test fun aRunWithoutAGrantIsGivenZeroAndTheServiceReadsOnly() {
        val c = controller().also { it.optIn() }
        assertEquals(ActionResult.Done, c.syncNow())
        assertEquals(listOf(0), port.budgets)
    }

    @Test fun aRunIsGivenExactlyTheGrantAndIsChargedWhatItDispatchedAtDispatch() {
        val c = controller().also { it.optIn() }
        c.grantSend(3, true)
        port.answer = report(dispatched = 2)
        c.syncNow()
        assertEquals(3, port.budgets.last())
        assertEquals(1, c.sendGrant())
        c.syncNow()
        assertEquals("the next run gets only what is left", 1, port.budgets.last())
        assertEquals("charged again by what it dispatched", 0, c.sendGrant())
        c.syncNow()
        assertEquals(0, port.budgets.last())
        assertTrue("never anything outside 0..3", port.budgets.all { it in 0..SyncSendBudget.PRODUCTION_MAX })
    }

    @Test fun t17_aDispatchWhoseAnswerWasLostStillSpendsTheBudget() {
        val c = controller().also { it.optIn() }
        c.grantSend(3, true)
        port.answer = Gated.Done(SyncReport(SyncStatus.DELIVERY_UNCONFIRMED, stage = SyncStage.SEND_ACK_TIMEOUT, opsDispatched = 3))
        c.syncNow()
        assertEquals(0, c.sendGrant())
        port.answer = report(0)
        c.syncNow()
        assertEquals(0, port.budgets.last())
    }

    @Test fun aRunRefusedAtTheGateIsNotChargedAndKeepsTheGrant() {
        val c = controller().also { it.optIn() }
        c.grantSend(2, true)
        port.answer = Gated.Denied(SyncDenied.BUSY)
        assertEquals(ActionResult.Denied(SyncDenied.BUSY), c.syncNow())
        assertEquals(2, c.sendGrant())
    }

    @Test fun t16_withoutConsentToTransmitTheGrantIsGoneAndNothingIsChargedOrSent() {
        val c = controller().also { it.optIn() }
        c.grantSend(3, true)
        assertEquals(ActionResult.Done, c.setAllowTransmit(false))
        assertEquals(0, c.sendGrant())
        c.syncNow()
        assertEquals(0, port.budgets.last())
        assertEquals(ActionResult.Refused(ActionResult.Reason.TRANSMIT_NOT_ALLOWED), c.grantSend(3, true))
    }

    @Test fun aRunThatDiesHalfWayLeavesNoGrantBehind() {
        val c = controller().also { it.optIn() }
        c.grantSend(3, true)
        port.boom = IllegalStateException("native crash")
        try { c.syncNow() } catch (_: IllegalStateException) {}
        assertEquals(0, c.sendGrant())
        port.boom = null
        c.syncNow()
        assertEquals(0, port.budgets.last())
    }

    @Test fun t17_theGrantEndsWithTurnOffAccountChangeLogoutAndANewScreen() {
        val c = controller().also { it.optIn() }
        c.grantSend(3, true)
        assertEquals(ActionResult.Done, c.turnOff())
        assertEquals(0, c.sendGrant())

        val c2 = controller(s = InMemorySyncConsentStore()).also { it.optIn() }
        c2.grantSend(3, true)
        current = other
        c2.consent() // the screen looks again as another account
        assertEquals("another account never inherits it", 0, c2.sendGrant())
        current = acct
        assertEquals("and the first account does not get it back", 0, c2.sendGrant())

        val c3 = controller(s = InMemorySyncConsentStore()).also { it.optIn() }
        c3.grantSend(3, true)
        current = null // logout
        c3.consent()
        assertEquals(0, c3.sendGrant())
        current = acct

        val c4 = controller().also { it.optIn() }
        c4.grantSend(3, true)
        assertEquals("a screen opened later starts from nothing", 0, controller().sendGrant())
    }

    @Test fun theScreenShowsAGrantOnlyWhileConsentToTransmitHolds() {
        val c = controller().also { it.optIn() }
        c.grantSend(2, true)
        assertEquals(2, c.screen().sendGrant)
        assertEquals(ControlState.ACTIVE, c.screen().grantSend)
        store.save(acct, store.load(acct).copy(mayTransmitInitial = false)) // withdrawn behind the controller's back
        assertEquals(0, c.screen().sendGrant)
    }

    // ---- a service wired like production ----------------------------------------------------------------------------------

    /** [bound] = the account already has its one real channel, bound in the checkpoint, with [songs] saved songs waiting. */
    private fun rig(
        cap: Int = SyncSendBudget.PRODUCTION_MAX, createUnlocked: Boolean = false, bound: Boolean = false, songs: Int = 0,
    ): SyncRig {
        val world = FakeTdlibWorld()
        val chat = if (bound) world.createSyncChannel(acct.value).id else 0L
        val r = SyncRig("grant-dev", folder.newFolder(), world, acct, channelId = chat, seed = 17, sendBudgetCap = cap, createUnlocked = createUnlocked)
        rigs.add(r)
        for (n in 1..songs) r.device.toggle(acct.value, songId(n))
        return r
    }

    @Test fun theServiceCutsAnyBudgetToTheProductionCap() {
        val r = rig(bound = true, songs = 5)
        val report = r.run(budget = 1_000)
        assertEquals(3, report.opsDispatched)
        assertEquals(1, r.tdlib.gateway.count("sendMessage"))
        assertEquals(SyncStatus.SEND_LIMIT_REACHED, report.status)
        assertEquals(2, report.pendingLeft)
    }

    @Test fun theServiceWithNoNumberOrAnInvalidOneNeverSends() {
        val r = rig(bound = true, songs = 5)
        for (bad in listOf(0, -1, Int.MIN_VALUE)) {
            val report = r.run(budget = bad)
            assertEquals(0, report.opsDispatched)
            assertEquals(SyncStatus.SEND_LIMIT_REACHED, report.status)
        }
        assertEquals("the default argument is zero too", 0, ((r.service.runSync(acct) as Gated.Done).value).opsDispatched)
        assertEquals(0, r.tdlib.gateway.count("sendMessage"))
    }

    @Test fun t16_withoutConsentToTransmitTheServiceDeniesBeforeAnyRequestAndTheControllerChargesNothing() {
        val r = rig(bound = true, songs = 2)
        val c = SyncSettingsController(r.consentStore, true, TdlibSyncActions(r.service), { acct }, { r.device.session.statusFacts(acct) })
        assertEquals(ActionResult.Done, c.grantSend(3, true))
        // consent (c) is withdrawn behind the screen, with operations waiting
        r.consentStore.save(acct, r.consentStore.load(acct).copy(mayTransmitInitial = false))
        assertEquals(ActionResult.Denied(SyncDenied.TRANSMIT_NOT_ALLOWED), c.syncNow())
        assertEquals("nothing was charged and nothing is left to spend", 0, c.sendGrant())
        assertEquals(0, r.tdlib.gateway.count("sendMessage"))
        assertEquals("the service refused before it asked TDLib anything", 0, r.tdlib.gateway.count("getChatHistory"))
    }

    // ---- (f) channel creation is locked ---------------------------------------------------------------------------------------

    @Test fun t12_aLockedBuildNeverCreatesEvenWithAllThreeConsentsAndAProvenAbsence() {
        val r = rig(createUnlocked = false)
        val gate = r.service.createChannel(acct)
        assertEquals(Gated.Denied(SyncDenied.CREATE_LOCKED), gate)
        assertEquals(0, r.tdlib.gateway.count("createNewSupergroupChat"))
        // the controller says the same, before it even asks the service
        val c = controller(unlocked = false).also { it.optIn(create = true) }
        c.discover()
        assertEquals(ControlState.LOCKED, c.screen().createNow)
        assertEquals(ActionResult.Denied(SyncDenied.CREATE_LOCKED), c.createChannel(true))
        assertEquals(0, port.creates)
    }

    @Test fun theProductionProviderIsLockedByDefault() {
        val r = rig()
        val provider = SyncServiceProvider(
            buildEnabled = true, owner = { r.tdlib.core }, host = { r.device.session }, store = r.consentStore,
            clock = { r.device.nowMs }, timeouts = TdlibTransportTimeouts(requestMs = 2_000L, sendAckMs = 400L),
        )
        assertEquals(Gated.Denied(SyncDenied.CREATE_LOCKED), provider.actions().create(acct))
        assertEquals(0, r.tdlib.gateway.count("createNewSupergroupChat"))
    }

    @Test fun t13_whenTheChannelIsIncompleteAmbiguousOrAbsentALockedBuildBlocksAndCreatesNothing() {
        // absent
        val none = rig()
        val absent = (none.service.discoverChannel(acct) as Gated.Done).value
        assertEquals(ChannelDiscovery.NotFound, absent)
        assertEquals(Gated.Denied(SyncDenied.CREATE_LOCKED), none.service.createChannel(acct))
        assertEquals(0, none.tdlib.gateway.count("createNewSupergroupChat"))
        // ambiguous
        val two = rig()
        two.world.createSyncChannel(acct.value)
        two.world.createSyncChannel(acct.value)
        val ambiguous = (two.service.discoverChannel(acct) as Gated.Done).value
        assertTrue(ambiguous is ChannelDiscovery.Ambiguous)
        assertEquals(Gated.Denied(SyncDenied.CREATE_LOCKED), two.service.createChannel(acct))
        assertEquals(0, two.tdlib.gateway.count("createNewSupergroupChat"))
        // the controller only ever offers "find and bind" for what it found
        assertFalse(ControlState.AVAILABLE == controller(unlocked = false).also { it.optIn(create = true) }.screen().createNow)
    }

    @Test fun anExistingBoundChannelIsNeverReplacedEvenWhenCreationIsUnlocked() {
        val r = rig(createUnlocked = true, bound = true, songs = 1)
        val outcome = (r.service.createChannel(acct) as Gated.Done).value
        assertTrue("$outcome", outcome is CreateOutcome.AlreadyBound)
        assertEquals(0, r.tdlib.gateway.count("createNewSupergroupChat"))
    }
}
