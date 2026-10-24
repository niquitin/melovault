package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * E96 P4-03: Settings > Đồng bộ Playlist as a state machine. A recording fake stands in for P3 (and, in the last tests, the
 * real service runs on a fake TDLib). No real Telegram exists anywhere in this file.
 */
class SyncSettingsControllerTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val other = TelegramUserId(2002L)
    private val rigs = ArrayList<SyncRig>()

    @After fun tearDown() = rigs.forEach { it.stop() }

    private class FakeActions : SyncActionsPort {
        val calls = ArrayList<String>()
        var discovery: Gated<ChannelDiscovery> = Gated.Done(ChannelDiscovery.NotFound)
        var created: Gated<CreateOutcome> = Gated.Done(CreateOutcome.Created(-1_000L))
        var bound: Gated<BindResult> = Gated.Done(BindResult.Bound)
        var report: Gated<SyncReport> = Gated.Done(SyncReport(SyncStatus.OK))
        var modes = ArrayList<SyncMode>()
        val budgets = ArrayList<Int>()
        var onRun: (() -> Unit)? = null
        override fun discover(account: TelegramUserId): Gated<ChannelDiscovery> { calls += "discover"; return discovery }
        override fun create(account: TelegramUserId): Gated<CreateOutcome> { calls += "create"; return created }
        override fun bind(account: TelegramUserId, chatId: Long): Gated<BindResult> { calls += "bind:$chatId"; return bound }
        override fun run(account: TelegramUserId, mode: SyncMode, sendBudget: Int): Gated<SyncReport> { calls += "run"; modes += mode; budgets += sendBudget; onRun?.invoke(); return report }
    }

    private var factsNow: PlaylistStatusFacts? = PlaylistStatusFacts(1001L, 2, 1, 0, 0, true, 3L, false, false, null, null)
    private var current: TelegramUserId? = acct
    private val store = InMemorySyncConsentStore()
    private val actions = FakeActions()

    private fun controller(build: Boolean = true, port: SyncActionsPort = actions, s: SyncConsentStore = store) =
        SyncSettingsController(s, build, port, { current }, { factsNow }, channelCreationUnlocked = true)

    private fun SyncSettingsController.optIn(create: Boolean = false, transmit: Boolean = false) {
        assertEquals(ActionResult.Done, enable(true))
        if (create) assertEquals(ActionResult.Done, setAllowCreate(true))
        if (transmit) assertEquals(ActionResult.Done, setAllowTransmit(true))
    }

    // ---- the shipped P4 build: nothing can be turned on ------------------------------------------------------------

    @Test fun k01_withTheBuildSwitchOffEveryActionIsRefusedNothingIsSavedAndNothingIsCalled() {
        val c = controller(build = false)
        val refused = ActionResult.Refused(ActionResult.Reason.BUILD_OFF)
        assertEquals(refused, c.enable(true))
        assertEquals(refused, c.setAllowCreate(true))
        assertEquals(refused, c.setAllowTransmit(true))
        assertEquals(refused, c.turnOff())
        assertEquals(refused, c.discover())
        assertEquals(refused, c.createChannel(true))
        assertEquals(refused, c.bindFoundChannel(true))
        assertEquals(refused, c.syncNow())
        assertEquals(refused, c.fullReplay(true))
        assertEquals("a mock tap never becomes a stored consent", SyncConsent(), store.load(acct))
        assertTrue(actions.calls.isEmpty())
    }

    @Test fun k02_withTheBuildSwitchOffTheScreenSaysSoAndLocksEveryControl() {
        val screen = controller(build = false).screen()
        assertTrue(screen.buildOff)
        assertEquals("Đồng bộ đám mây chưa được kích hoạt trong bản thử nghiệm", screen.buildNotice)
        for (state in listOf(
            screen.enable, screen.allowCreate, screen.allowTransmit, screen.discover, screen.createNow,
            screen.bindNow, screen.syncNow, screen.fullReplay,
        )) assertEquals(ControlState.BUILD_OFF, state)
    }

    @Test fun k03_theShippedWiringReachesNoTdlibEvenWhenEveryConsentWasWrittenToTheFile() {
        // A consent file that says "all on" (hand-edited) does not matter: the build switch and DisabledSyncActions answer first.
        store.save(acct, SyncConsent(true, true, true, SyncConsent.WARNING_VERSION))
        val c = controller(build = false, port = DisabledSyncActions)
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), DisabledSyncActions.discover(acct))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), DisabledSyncActions.create(acct))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), DisabledSyncActions.bind(acct, 5L))
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), DisabledSyncActions.run(acct, SyncMode.INCREMENTAL))
        assertEquals(ActionResult.Refused(ActionResult.Reason.BUILD_OFF), c.syncNow())
    }

    // ---- three independent consents ------------------------------------------------------------------------------------

    @Test fun k04_enablingNeedsTheWarningToBeAcceptedInTheSameAction() {
        val c = controller()
        assertEquals(ActionResult.Refused(ActionResult.Reason.WARNING_NOT_ACCEPTED), c.enable(false))
        assertEquals(SyncConsent(), store.load(acct))
        assertEquals(ActionResult.Done, c.enable(true))
        assertTrue(store.load(acct).enabled && store.load(acct).warningAccepted)
    }

    @Test fun k05_enablingDoesNotGrantCreateOrTransmit() {
        val c = controller()
        c.optIn()
        assertFalse(store.load(acct).mayCreateChannel)
        assertFalse(store.load(acct).mayTransmitInitial)
        assertEquals(ControlState.AVAILABLE, c.screen().allowCreate)
        assertEquals(ControlState.AVAILABLE, c.screen().allowTransmit)
    }

    @Test fun k06_createAndTransmitAreSeparateAndNeedSyncToBeEnabledFirst() {
        val c = controller()
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_ENABLED), c.setAllowCreate(true))
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_ENABLED), c.setAllowTransmit(true))
        c.optIn()
        assertEquals(ActionResult.Done, c.setAllowCreate(true))
        assertTrue(store.load(acct).mayCreateChannel)
        assertFalse("allowing creation never allows sending", store.load(acct).mayTransmitInitial)
        assertEquals(ActionResult.Done, c.setAllowTransmit(true))
        assertEquals(ControlState.ACTIVE, c.screen().allowTransmit)
    }

    @Test fun k07_eachConsentCanBeWithdrawnAloneAndTurningOffClearsAllWithoutTouchingTheChannelOrQueue() {
        val c = controller()
        c.optIn(create = true, transmit = true)
        assertEquals(ActionResult.Done, c.setAllowCreate(false))
        assertEquals(SyncConsent(true, false, true, SyncConsent.WARNING_VERSION), store.load(acct))
        assertEquals(ActionResult.Done, c.setAllowTransmit(false))
        assertEquals(SyncConsent(true, false, false, SyncConsent.WARNING_VERSION), store.load(acct))
        c.setAllowCreate(true); c.setAllowTransmit(true)
        val before = factsNow
        assertEquals(ActionResult.Done, c.turnOff())
        assertEquals(SyncConsent(), store.load(acct))
        assertTrue("turning off calls nothing and deletes nothing", actions.calls.isEmpty())
        assertEquals(before, factsNow)
    }

    @Test fun k08_aMalformedOrTruncatedConsentFileMeansEverythingOffAndNeverPartlyOn() {
        val root = folder.newFolder()
        val fileStore = FileSyncConsentStore(root, TestPlainFileIo)
        val c = controller(s = fileStore)
        c.optIn(create = true, transmit = true)
        assertTrue(fileStore.load(acct).mayTransmitInitial)
        val file = File(File(root, "tg_sync_consent_v1"), TelegramAccountStoragePolicy.directoryName(acct) + ".txt")
        assertTrue(file.isFile)
        file.writeText("mvconsent v1\nenabled 1\ncreate 1\ntransmit 1\nwarn") // cut off
        assertEquals(SyncConsent(), fileStore.load(acct))
        assertEquals(ControlState.AVAILABLE, c.screen().enable)
        file.writeText("garbage")
        assertEquals(SyncConsent(), c.consent())
        file.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        assertEquals(SyncConsent(), c.consent())
    }

    // ---- channel: found / proven absent / incomplete / ambiguous / unsafe / failed ----------------------------------

    @Test fun k09_openingTheScreenAndReadingItNeverStartsAnyCall() {
        val c = controller()
        c.optIn(create = true, transmit = true)
        repeat(5) { c.screen(); c.syncContext(); c.consent() }
        assertTrue("no discovery, creation or sync on open, rotate or resume", actions.calls.isEmpty())
    }

    @Test fun k10_everyDiscoveryAnswerHasItsOwnViewAndOnlyProvenAbsenceAllowsCreation() {
        val c = controller()
        c.optIn(create = true, transmit = true)
        val cases = mapOf(
            ChannelDiscovery.Found(-55L) to ChannelView.Found(-55L),
            ChannelDiscovery.NotFound to ChannelView.NotFound,
            ChannelDiscovery.Incomplete(IncompleteReason.BUDGET) to ChannelView.Incomplete(IncompleteReason.BUDGET),
            ChannelDiscovery.Ambiguous(2) to ChannelView.Ambiguous(2),
            ChannelDiscovery.Unsafe(UnsafeChannel.PUBLIC) to ChannelView.Unsafe(UnsafeChannel.PUBLIC),
            ChannelDiscovery.Failed(SyncErrorKind.NETWORK) to ChannelView.Failed(SyncErrorKind.NETWORK),
        )
        for ((answer, view) in cases) {
            actions.discovery = Gated.Done(answer)
            factsNow = factsNow!!.copy(channelBound = false)
            assertEquals(ActionResult.Done, c.discover())
            assertEquals(view, c.screen().channel)
            val createState = c.screen().createNow
            if (answer == ChannelDiscovery.NotFound) assertEquals(ControlState.AVAILABLE, createState)
            else assertEquals(answer.toString(), ControlState.LOCKED, createState)
        }
    }

    @Test fun k11_createIsRefusedAfterIncompleteAmbiguousUnsafeOrNoDiscoveryAndNeverCallsTheService() {
        val c = controller()
        c.optIn(create = true)
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), c.createChannel(true))
        for (answer in listOf(
            ChannelDiscovery.Incomplete(IncompleteReason.LOAD_NOT_FINISHED), ChannelDiscovery.Ambiguous(3),
            ChannelDiscovery.Unsafe(UnsafeChannel.HAS_OTHER_MEMBERS), ChannelDiscovery.Found(-9L), ChannelDiscovery.Failed(SyncErrorKind.TIMEOUT),
        )) {
            actions.discovery = Gated.Done(answer)
            c.discover()
            assertEquals(answer.toString(), ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), c.createChannel(true))
        }
        assertFalse(actions.calls.contains("create"))
    }

    @Test fun k12_creatingNeedsConsentBAndAFreshConfirmationAndTheConfirmationIsSpentByTheAttempt() {
        val c = controller()
        c.optIn(create = false)
        c.discover()
        assertEquals("consent (b) is missing", ActionResult.Denied(SyncDenied.CREATE_NOT_ALLOWED), c.createChannel(true))
        c.setAllowCreate(true)
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_CONFIRMED), c.createChannel(false))
        assertEquals(ActionResult.Done, c.createChannel(true))
        assertEquals(1, actions.calls.count { it == "create" })
        // A second tap with no new discovery cannot create another channel.
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), c.createChannel(true))
        assertEquals(1, actions.calls.count { it == "create" })
    }

    @Test fun k13_anUnconfirmedCreateAnswerIsShownAsNeedsAPersonAndIsNeverRepeated() {
        val c = controller()
        c.optIn(create = true)
        factsNow = factsNow!!.copy(channelBound = false)
        c.discover()
        actions.created = Gated.Done(CreateOutcome.Unknown)
        assertEquals(ActionResult.Done, c.createChannel(true))
        assertEquals(ChannelView.NeedsPerson, c.screen().channel)
        assertEquals(ControlState.LOCKED, c.screen().createNow)
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), c.createChannel(true))
    }

    @Test fun k14_bindingNeedsAFoundChannelAndAConfirmation() {
        val c = controller()
        c.optIn()
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), c.bindFoundChannel(true))
        actions.discovery = Gated.Done(ChannelDiscovery.Found(-77L))
        factsNow = factsNow!!.copy(channelBound = false)
        c.discover()
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_CONFIRMED), c.bindFoundChannel(false))
        assertEquals(ActionResult.Done, c.bindFoundChannel(true))
        assertTrue(actions.calls.contains("bind:-77"))
        assertEquals(ChannelView.Bound, c.screen().channel)
    }

    // ---- manual sync -------------------------------------------------------------------------------------------------

    @Test fun k15_syncNowNeedsSyncOnAndABoundChannelAndNeverCreatesOne() {
        val c = controller()
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_ENABLED), c.syncNow())
        c.optIn()
        factsNow = factsNow!!.copy(channelBound = false)
        assertEquals(ActionResult.Refused(ActionResult.Reason.NO_CHANNEL), c.syncNow())
        assertTrue(actions.calls.isEmpty())
        factsNow = factsNow!!.copy(channelBound = true)
        assertEquals(ActionResult.Done, c.syncNow())
        assertEquals(listOf("run"), actions.calls)
        assertEquals(listOf(SyncMode.INCREMENTAL), actions.modes)
    }

    @Test fun k16_retryCannotSkipTheServicesBackoffItIsShownAsWaitNotAsSynced() {
        val c = controller()
        c.optIn(transmit = true)
        actions.report = Gated.Done(SyncReport(SyncStatus.BACKOFF_ACTIVE, retryAfterMs = 20_000L))
        assertEquals(ActionResult.Done, c.syncNow())
        val line = PlaylistSyncStatusPolicy.evaluate(1001L, factsNow, c.syncContext())
        assertEquals(PlaylistSyncStatusPolicy.Kind.OFFLINE_BACKOFF, line.kind)
        assertTrue(line.detail.contains("20 giây"))
        // The same retry tap does not turn a denial into success.
        actions.report = Gated.Denied(SyncDenied.TRANSMIT_NOT_ALLOWED)
        assertEquals(ActionResult.Denied(SyncDenied.TRANSMIT_NOT_ALLOWED), c.syncNow())
    }

    @Test fun k17_aFullReplayIsExplicitAndConfirmedAndUsesTheReplayMode() {
        val c = controller()
        c.optIn()
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_CONFIRMED), c.fullReplay(false))
        assertTrue(actions.calls.isEmpty())
        assertEquals(ActionResult.Done, c.fullReplay(true))
        assertEquals(listOf(SyncMode.FULL_REPLAY), actions.modes)
        // Recovery is a read; it removes nothing from the stored playlist.
        assertEquals(2, factsNow!!.savedCount)
    }

    @Test fun k18_aSecondActionWhileOneIsRunningIsRefusedAsBusy() {
        val c = controller()
        c.optIn()
        val inside = CountDownLatch(1)
        val release = CountDownLatch(1)
        actions.onRun = { inside.countDown(); release.await(5, TimeUnit.SECONDS) }
        val first = Thread { c.syncNow() }.also { it.start() }
        assertTrue(inside.await(5, TimeUnit.SECONDS))
        assertTrue(c.screen().busy)
        assertEquals(ControlState.LOCKED, c.screen().syncNow)
        assertEquals(ActionResult.Refused(ActionResult.Reason.BUSY), c.discover())
        assertEquals(ActionResult.Refused(ActionResult.Reason.BUSY), c.syncNow())
        release.countDown()
        first.join(5_000)
        assertEquals(1, actions.calls.count { it == "run" })
        assertFalse(c.screen().busy)
    }

    // ---- accounts -----------------------------------------------------------------------------------------------------

    @Test fun k19_switchingAccountForgetsTheChannelViewAndTheLastRunOfTheOtherAccount() {
        val c = controller()
        c.optIn(transmit = true)
        actions.discovery = Gated.Done(ChannelDiscovery.Found(-5L))
        factsNow = factsNow!!.copy(channelBound = false)
        c.discover()
        c.syncContext()
        assertEquals(ChannelView.Found(-5L), c.screen().channel)
        current = other
        assertEquals("B starts clean", ChannelView.Unchecked, c.screen().channel)
        assertEquals(SyncConsent(), c.consent())
        assertNull(c.syncContext().lastRun)
        current = acct
        assertEquals("A does not get its old view back by accident", ChannelView.Unchecked, c.screen().channel)
        assertTrue("A's consent is still A's", c.consent().enabled)
    }

    @Test fun k20_withoutASignedInAccountNothingIsPersistedOrCalled() {
        current = null
        val c = controller()
        assertEquals(ActionResult.Refused(ActionResult.Reason.NO_ACCOUNT), c.enable(true))
        assertEquals(ActionResult.Refused(ActionResult.Reason.NO_ACCOUNT), c.discover())
        assertEquals(ControlState.LOCKED, c.screen().enable)
        assertEquals(SyncConsent(), c.consent())
        assertTrue(actions.calls.isEmpty())
    }

    // ---- the real service on a fake TDLib ------------------------------------------------------------------------------

    private fun rig(build: Boolean, consent: Boolean = true) = SyncRig(
        "ui-dev", folder.newFolder(), FakeTdlibWorld(), acct, channelId = 0L, seed = 33, buildEnabled = build, consent = consent,
    ).also { rigs.add(it) }

    @Test fun k21_withTheBuildFlagOffNoUiPathSendsAnyTdlibRequest() {
        val r = rig(build = false, consent = true) // consent granted AND service built: only the flag stands in the way
        val before = r.tdlib.gateway.requests.size
        // The UI's controller is allowed to try (a test-only gate), as are the service's own entry points.
        val ui = SyncSettingsController(r.consentStore, true, TdlibSyncActions(r.service), { acct }, { r.device.session.statusFacts(acct) }, channelCreationUnlocked = true)
        assertEquals(ActionResult.Denied(SyncDenied.BUILD_OFF), ui.discover())
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), ui.createChannel(true))
        assertEquals(ActionResult.Refused(ActionResult.Reason.NO_CHANNEL), ui.syncNow())
        assertEquals(ActionResult.Refused(ActionResult.Reason.NO_CHANNEL), ui.fullReplay(true))
        assertTrue(r.service.discoverChannel(acct) is Gated.Denied)
        assertTrue(r.service.createChannel(acct) is Gated.Denied)
        assertTrue(r.service.runSync(acct) is Gated.Denied)
        assertEquals("not one request reached the fake TDLib", before, r.tdlib.gateway.requests.size)
        assertEquals(0, r.tdlib.gateway.count("createNewSupergroupChat"))
    }

    @Test fun k22_withTheFlagOnAndThreeConsentsTheScreenDrivesDiscoverCreateBindAndSyncOnTheFakeCloud() {
        val r = rig(build = true, consent = false)
        val ui = SyncSettingsController(r.consentStore, true, TdlibSyncActions(r.service), { acct }, { r.device.session.statusFacts(acct) }, channelCreationUnlocked = true)
        ui.optIn(create = true, transmit = true)
        assertEquals(ActionResult.Done, ui.discover())
        assertEquals(ChannelView.NotFound, ui.screen().channel)
        assertEquals(ActionResult.Done, ui.createChannel(true))
        assertEquals(1, r.tdlib.gateway.count("createNewSupergroupChat"))
        assertEquals(ActionResult.Done, ui.discover())
        assertTrue(ui.screen().channel is ChannelView.Found)
        assertEquals(ActionResult.Done, ui.bindFoundChannel(true))
        assertEquals(ChannelView.Bound, ui.screen().channel)
        assertEquals(ActionResult.Done, ui.syncNow())
        val line = PlaylistSyncStatusPolicy.evaluate(acct.value, r.device.session.statusFacts(acct), ui.syncContext())
        assertEquals(PlaylistSyncStatusPolicy.Kind.SYNCED, line.kind)
        assertEquals("still exactly one channel was ever created", 1, r.tdlib.gateway.count("createNewSupergroupChat"))
    }

    @Test fun k23_theScreensConsentWritesAreExactlyWhatTheP3ConsentActionsWrite() {
        val reference = InMemorySyncConsentStore()
        val p3 = SyncConsentActions(reference)
        val c = controller()
        // Same moves, same order, same stored result at every step.
        assertEquals(p3.allowChannelCreation(acct), c.setAllowCreate(true) == ActionResult.Done)
        assertEquals(reference.load(acct), store.load(acct))
        assertEquals(p3.enable(acct, true), c.enable(true) == ActionResult.Done)
        assertEquals(reference.load(acct), store.load(acct))
        assertEquals(p3.allowChannelCreation(acct), c.setAllowCreate(true) == ActionResult.Done)
        assertEquals(p3.allowInitialTransmit(acct), c.setAllowTransmit(true) == ActionResult.Done)
        assertEquals(reference.load(acct), store.load(acct))
        assertEquals(p3.revoke(acct), c.turnOff() == ActionResult.Done)
        assertEquals(reference.load(acct), store.load(acct))
    }
}
