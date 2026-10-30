package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncStatusPolicy.Kind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * E96 P5-A (B, C, F): the production composition ([SyncServiceProvider] + [SyncSettingsController] + the shared run log)
 * with the switch ON as a TEST gate, over the fake TDLib world. Proves consent gating, account/epoch freshness,
 * fail-closed discovery and history reading, ACK discipline, backoff memory and resource release. It does NOT prove real
 * TDLib behaviour: that stays a P5-B gate.
 */
class P5ReadinessEnabledFlowTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val other = TelegramUserId(2002L)
    private val world = FakeTdlibWorld()
    private val rigs = ArrayList<SyncRig>()
    private val chat = -1_001_000_000_900L

    @After fun tearDown() = rigs.forEach { it.stop() }

    private class Stack(val rig: SyncRig, val provider: SyncServiceProvider, val log: SyncRunLog, val ui: SyncSettingsController)

    private fun stack(config: SyncConfig = SyncConfig()): Stack {
        val r = SyncRig("p5-dev", folder.newFolder(), world, acct, channelId = 0L, seed = 51, config = config, buildEnabled = true, consent = false)
        rigs.add(r)
        return stackOn(r, config)
    }

    private fun stackOn(r: SyncRig, config: SyncConfig, log: SyncRunLog = SyncRunLog(), provider: SyncServiceProvider? = null): Stack {
        val p = provider ?: SyncServiceProvider(
            buildEnabled = true, owner = { r.tdlib.core }, host = { r.device.session }, store = r.consentStore,
            clock = { r.device.nowMs }, config = config,
            timeouts = TdlibTransportTimeouts(requestMs = 2_000L, sendAckMs = 400L),
            historyLimits = HistoryLimits(requestTimeoutMs = 2_000L),
            channelCreationUnlocked = true,
        )
        val ui = SyncSettingsController(r.consentStore, true, p.actions(), { acct }, { r.device.session.statusFacts(acct) }, log, channelCreationUnlocked = true)
        return Stack(r, p, log, ui)
    }

    private fun Stack.optIn(create: Boolean = true, transmit: Boolean = true) {
        assertEquals(ActionResult.Done, ui.enable(true))
        if (create) assertEquals(ActionResult.Done, ui.setAllowCreate(true))
        if (transmit) assertEquals(ActionResult.Done, ui.setAllowTransmit(true))
    }

    /** A real-looking bound channel in the fake cloud and [songs] saved songs waiting in the outbox. */
    private fun Stack.boundWithSongs(songs: Int): FakeChatData {
        val channel = world.createSyncChannel(acct.value)
        rig.device.bindChannel(channel.id)
        for (i in 1..songs) rig.device.toggle(chat, songId(i))
        assertEquals(ActionResult.Done, ui.discover())
        return channel
    }

    /** The person allows the next run to send up to [n] operations (the production cap is 3). */
    private fun Stack.grant(n: Int = 3) = assertEquals(ActionResult.Done, ui.grantSend(n, true))

    private fun Stack.line(): PlaylistSyncStatusPolicy.Line =
        PlaylistSyncStatusPolicy.evaluate(acct.value, rig.device.session.statusFacts(acct), SyncContext(true, ui.consent(), false, log.last(acct.value)))

    private fun Stack.requests(type: String) = rig.tdlib.gateway.count(type)
    private fun Stack.lastStatus() = log.last(acct.value)!!.status

    // ---- B: consent, account, epoch, discovery ---------------------------------------------------------------------------

    @Test fun b01_withoutConsentTheServiceDeniesEverythingAndTdlibIsNeverAsked() {
        val s = stack()
        val port = s.provider.actions()
        assertEquals(Gated.Denied(SyncDenied.NOT_ENABLED), port.discover(acct))
        assertEquals(Gated.Denied(SyncDenied.NOT_ENABLED), port.create(acct))
        assertEquals(Gated.Denied(SyncDenied.NOT_ENABLED), port.run(acct, SyncMode.INCREMENTAL))
        assertEquals(ActionResult.Refused(ActionResult.Reason.NOT_ENABLED), s.ui.discover())
        assertEquals(0, s.requests("getChats"))
        assertEquals(0, s.requests("createNewSupergroupChat"))
    }

    @Test fun b02_theThreeConsentsAreIndependentAtTheServiceBoundary() {
        val s = stack()
        s.optIn(create = false, transmit = false)
        assertEquals(ActionResult.Done, s.ui.discover()) // (a) alone lets it LOOK
        assertEquals(ChannelView.NotFound, s.ui.screen().channel)
        assertEquals("(b) is missing", ActionResult.Denied(SyncDenied.CREATE_NOT_ALLOWED), s.ui.createChannel(true))
        assertEquals(0, s.requests("createNewSupergroupChat"))
        // (b) given, (c) still missing: a bound channel with waiting songs must not be written to.
        assertEquals(ActionResult.Done, s.ui.setAllowCreate(true))
        s.boundWithSongs(2)
        // (REWORK-02) "Đồng bộ ngay" without a grant is a READ-ONLY run: allowed with (a), it sends nothing; a grant needs (c).
        assertEquals(ActionResult.Refused(ActionResult.Reason.TRANSMIT_NOT_ALLOWED), s.ui.grantSend(3, true))
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(0, s.requests("sendMessage"))
        assertEquals(Kind.NEEDS_TRANSMIT_CONSENT, s.line().kind)
    }

    @Test fun b03_twoMarkedChannelsAreAmbiguousNothingIsChosenBoundOrCreated() {
        val s = stack()
        s.optIn()
        world.createSyncChannel(acct.value)
        world.createSyncChannel(acct.value)
        assertEquals(ActionResult.Done, s.ui.discover())
        assertEquals(ChannelView.Ambiguous(2), s.ui.screen().channel)
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), s.ui.createChannel(true))
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), s.ui.bindFoundChannel(true))
        assertEquals(0, s.requests("createNewSupergroupChat"))
        assertEquals(0L, s.rig.device.session.view(acct)!!.state.checkpoint.channelId)
    }

    @Test fun b04_aChatListThatNeverFinishesLoadingIsIncompleteAndNeverALicenceToCreate() {
        val s = stack()
        s.optIn()
        s.rig.tdlib.gateway.neverFinishLoad = true
        assertEquals(ActionResult.Done, s.ui.discover())
        assertTrue(s.ui.screen().channel is ChannelView.Incomplete)
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), s.ui.createChannel(true))
        assertEquals(0, s.requests("createNewSupergroupChat"))
    }

    @Test fun b05_afterALogoutTheServiceRefusesTheOldAccountAndSendsNothing() {
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        s.rig.tdlib.relogin(other.value) // the owner now belongs to another account
        assertEquals(ActionResult.Denied(SyncDenied.NOT_AUTHORIZED), s.ui.syncNow())
        assertEquals(0, s.requests("sendMessage"))
        assertEquals(Kind.NEEDS_LOGIN, s.line().kind)
    }

    @Test fun b06_aThenBThenAGivesAFreshEpochAndTheLateWorkOfTheFirstSessionIsNotReused() {
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        s.grant()
        val before = s.rig.tdlib.core.authEpoch()
        s.rig.tdlib.relogin(other.value)
        assertEquals(ActionResult.Denied(SyncDenied.NOT_AUTHORIZED), s.ui.syncNow())
        s.rig.tdlib.relogin(acct.value)
        assertNotEquals("every login is a new epoch", before, s.rig.tdlib.core.authEpoch())
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(SyncStatus.OK, s.lastStatus())
        assertEquals(1, s.requests("sendMessage"))
    }

    @Test fun b07_theConsentScreenFlowDiscoversCreatesBindsAndSyncsExactlyOneChannel() {
        val s = stack()
        s.optIn()
        s.rig.device.toggle(chat, songId(1))
        assertEquals(ActionResult.Done, s.ui.discover())
        assertEquals(ChannelView.NotFound, s.ui.screen().channel)
        assertEquals(ActionResult.Done, s.ui.createChannel(true))
        assertEquals(ActionResult.Done, s.ui.discover())
        assertEquals(ActionResult.Done, s.ui.bindFoundChannel(true))
        s.grant()
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(1, s.requests("createNewSupergroupChat"))
        assertEquals(Kind.SYNCED, s.line().kind)
        s.rig.device.toggle(chat, songId(2)) // a new Save after the OK report
        assertEquals(Kind.LOCAL_PENDING, s.line().kind)
        s.log.clear() // logout
        assertNotEquals(Kind.SYNCED, s.line().kind)
    }

    // ---- F: lifecycle ------------------------------------------------------------------------------------------------------

    @Test fun f01_aRunBindsTheSinkOnlyForItsDurationAndClosingReleasesEverything() {
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertFalse("a call alone binds only for its duration", s.rig.tdlib.core.syncIsBound())
        s.ui.close()
        assertFalse(s.rig.tdlib.core.syncIsBound())
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), s.ui.syncNow())
        assertEquals(ActionResult.Done, s.ui.discover())
        assertEquals("the screen can be opened again after verification", ActionResult.Done, s.ui.syncNow())
    }

    @Test fun f02_closingTheSyncDoesNotAffectTheUiOrPlaybackEventStreams() {
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        s.rig.tdlib.core.bindPlayback(s.rig.tdlib.playback) // the media service as a second listener
        s.ui.syncNow()
        s.ui.close()
        val uiBefore = s.rig.tdlib.uiEvents.size
        val playbackBefore = s.rig.tdlib.playbackEvents.size
        s.rig.tdlib.gateway.connectionState("connectionStateReady")
        assertTrue(waitUntil { s.rig.tdlib.uiEvents.size > uiBefore && s.rig.tdlib.playbackEvents.size > playbackBefore })
    }

    @Test fun f03_theBackoffOfAFailedRunSurvivesClosingAndReopeningTheScreen() {
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        s.grant()
        s.rig.tdlib.gateway.defaultSendMode = SendMode.ERROR_REPLY // 429, retry after 35 s
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(SyncStatus.BACKOFF, s.lastStatus())
        assertEquals(Kind.OFFLINE_BACKOFF, s.line().kind)
        val sends = s.requests("sendMessage")
        s.ui.close()
        val reopened = stackOn(s.rig, SyncConfig(), s.log, s.provider) // the same process, a new Settings screen
        assertSame("one service for the process", s.provider.actions(), s.provider.actions())
        assertEquals(ActionResult.Refused(ActionResult.Reason.NEEDS_DISCOVERY), reopened.ui.syncNow())
        assertEquals(ActionResult.Done, reopened.ui.discover())
        assertEquals(ActionResult.Done, reopened.ui.syncNow())
        assertEquals("a reopened screen cannot skip the wait", SyncStatus.BACKOFF_ACTIVE, reopened.lastStatus())
        assertEquals(sends, s.requests("sendMessage"))
    }

    // ---- C: chronology and delivery ------------------------------------------------------------------------------------------

    @Test fun c01_moreThanOneHundredMessagesAreReadInPagesAndTheCursorReachesTheNewestOne() {
        val s = stack()
        s.optIn()
        val channel = s.boundWithSongs(0)
        world.postMany(channel.id, 250)
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(SyncStatus.OK, s.lastStatus())
        assertEquals(world.ids(channel.id).max(), s.rig.device.session.view(acct)!!.state.checkpoint.lastAppliedServerId)
        assertTrue("about one request per page, not per message", s.requests("getChatHistory") < 30)
    }

    @Test fun c02_shortPagesAreNotTheEndOfTheHistory() {
        val s = stack()
        s.optIn()
        val channel = s.boundWithSongs(0)
        world.postMany(channel.id, 120)
        s.rig.tdlib.gateway.pageCap = 7
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(SyncStatus.OK, s.lastStatus())
        assertEquals(world.ids(channel.id).max(), s.rig.device.session.view(acct)!!.state.checkpoint.lastAppliedServerId)
    }

    @Test fun c03_aHistoryOfOverTenThousandMessagesIsReplayedAcrossRunsAndOnlyTheLastRunIsSynced() {
        val s = stack(SyncConfig(maxPagesPerRun = 40))
        s.optIn()
        val channel = s.boundWithSongs(0)
        world.postMany(channel.id, 10_300)
        assertEquals(ActionResult.Done, s.ui.fullReplay(true))
        assertEquals(SyncStatus.CATCH_UP_INCOMPLETE, s.lastStatus())
        assertEquals("a half-read history is never 'synced'", Kind.CATCHING_UP, s.line().kind)
        var runs = 1
        while (s.lastStatus() == SyncStatus.CATCH_UP_INCOMPLETE && runs < 8) {
            assertEquals(ActionResult.Done, s.ui.fullReplay(true))
            runs++
        }
        assertEquals(SyncStatus.OK, s.lastStatus())
        assertTrue("it needed more than one run", runs > 1)
        assertEquals(Kind.SYNCED, s.line().kind)
        assertEquals(world.ids(channel.id).max(), s.rig.device.session.view(acct)!!.state.checkpoint.lastAppliedServerId)
    }

    @Test fun c04_aFailedIndependentForwardCheckIsNeverServedAsAnOkHistory() {
        val s = stack()
        s.optIn()
        val channel = s.boundWithSongs(0)
        world.postMany(channel.id, 30)
        s.rig.tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.ERROR_REPLY)
        s.rig.tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.MALFORMED)
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertNotEquals(SyncStatus.OK, s.lastStatus())
        assertNotEquals(Kind.SYNCED, s.line().kind)
        // Once the source is healthy the same call converges, with nothing changed in between.
        s.rig.tdlib.gateway.forwardFaults.clear()
        s.rig.device.nowMs += 10 * 60_000L
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(SyncStatus.OK, s.lastStatus())
    }

    @Test fun c05_aMessageOfAFutureProtocolStopsTheReadWithRecoveryAndKeepsEverything() {
        val s = stack()
        s.optIn()
        val channel = s.boundWithSongs(2)
        world.post(channel.id, "#mvsync v2\nscope\tabcd")
        val before = s.rig.device.stored()
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(SyncStatus.RECOVERY_REQUIRED, s.lastStatus())
        assertEquals(Kind.RECOVERY_FUTURE_PROTOCOL, s.line().kind)
        val after = s.rig.device.stored()
        assertEquals("nothing saved was touched", before.presentSongKeys(acct.value), after.presentSongKeys(acct.value))
        assertEquals(before.outbox.size, after.outbox.size)
    }

    @Test fun c06_aDamagedSyncMessageIsRecoveryNotSkipped() {
        val s = stack()
        s.optIn()
        val channel = s.boundWithSongs(0)
        val good = envelopeText(acct.value, "dev-x", addOp("dev-x", 1, chat, songId(77)))
        world.post(channel.id, good.dropLast(3) + "000") // checksum no longer matches
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(SyncStatus.RECOVERY_REQUIRED, s.lastStatus())
        assertTrue(s.log.last(acct.value)!!.recovery in listOf(RecoveryReason.CORRUPT_MESSAGE, RecoveryReason.FUTURE_PROTOCOL))
        assertEquals(0, s.rig.device.present().size)
    }

    @Test fun c07_aSendWhoseAnswerWasLostIsFoundAgainByReadingAndNeverSentTwice() {
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        s.grant()
        s.rig.tdlib.gateway.defaultSendMode = SendMode.NO_REPLY_STORED // the server has it, the answer never came
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertNotEquals("silence is not an ACK", Kind.SYNCED, s.line().kind)
        s.rig.tdlib.gateway.defaultSendMode = SendMode.OK_UPDATE_AFTER
        s.rig.device.nowMs += 10 * 60_000L
        assertEquals(ActionResult.Done, s.ui.syncNow())
        assertEquals(SyncStatus.OK, s.lastStatus())
        assertEquals("exactly one message in the channel", 1, world.texts(s.rig.device.stored().checkpoint.channelId).size)
        assertEquals(Kind.SYNCED, s.line().kind)
    }

    @Test fun c08_aFloodWaitIsShownAsTryLaterAndNothingMoreIsSentUntilItIsOver() {
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        s.grant()
        s.rig.tdlib.gateway.defaultSendMode = SendMode.ERROR_REPLY
        s.ui.syncNow()
        val line = s.line()
        assertEquals(Kind.OFFLINE_BACKOFF, line.kind)
        assertTrue(line.text.contains("Thử lại sau"))
        val sends = s.requests("sendMessage")
        s.ui.syncNow() // a retry tap inside the wait
        assertEquals(sends, s.requests("sendMessage"))
        assertEquals(SyncStatus.BACKOFF_ACTIVE, s.lastStatus())
    }

    @Test fun c09_aTimedOutSendThatWasNotSeenIsNeverSentAgainEvenWithAFreshGrant() {
        // REWORK-01: "the message is not in the channel" is not proof that it never left. Only reading it back ends the doubt.
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        s.grant()
        s.rig.tdlib.gateway.defaultSendMode = SendMode.NO_REPLY_NOT_STORED
        s.ui.syncNow()
        assertNotEquals(Kind.SYNCED, s.line().kind)
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, s.lastStatus())
        assertEquals(Kind.DELIVERY_UNCONFIRMED, s.line().kind)
        assertTrue(s.rig.device.session.statusFacts(acct)!!.pendingCount > 0)
        val sends = s.requests("sendMessage")
        s.rig.tdlib.gateway.defaultSendMode = SendMode.OK_UPDATE_AFTER
        s.rig.device.nowMs += 10 * 60_000L
        s.grant()
        s.ui.syncNow()
        assertEquals(SyncStatus.DELIVERY_UNCONFIRMED, s.lastStatus())
        assertEquals("nothing was sent again", sends, s.requests("sendMessage"))
        assertEquals(0, world.texts(s.rig.device.stored().checkpoint.channelId).size)
        assertEquals("the operation is kept, flagged, unchanged", 1, s.rig.device.stored().outbox.count { it.status == OutboxStatus.RETRY_REQUIRED })
    }

    @Test fun c10_aProvisionalAnswerWithoutTheServersSuccessUpdateIsNotAnAck() {
        val s = stack()
        s.optIn()
        s.boundWithSongs(1)
        s.grant()
        s.rig.tdlib.gateway.defaultSendMode = SendMode.NO_UPDATE_NOT_STORED
        s.ui.syncNow()
        assertNotEquals(Kind.SYNCED, s.line().kind)
        assertTrue(s.rig.device.session.statusFacts(acct)!!.pendingCount > 0)
    }
}
