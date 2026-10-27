package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * E96-P3-REWORK-01 regression tests, written BEFORE the fixes:
 *  R01 - channel discovery must PROVE absence (exhaustive scan of the chat lists, or the bound chat id), never infer it
 *        from a title search; creation is refused while discovery is incomplete.
 *  R02 - the consent file is parsed strictly; anything unexpected means everything OFF.
 *  R03 - an inconclusive second look at the history fails closed; it is never read as "no conflict".
 */
class TdlibRework01Test {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val world = FakeTdlibWorld()
    private val rigs = ArrayList<SyncRig>()

    @After fun tearDown() = rigs.forEach { it.stop() }

    private fun rig(
        name: String = "dev-a", channelId: Long = 0L, w: FakeTdlibWorld = world,
        limits: DiscoveryLimits = DiscoveryLimits(), timeouts: TdlibTransportTimeouts = TdlibTransportTimeouts(requestMs = 2_000L, sendAckMs = 400L),
        historyLimits: HistoryLimits = HistoryLimits(requestTimeoutMs = 2_000L),
    ): SyncRig = SyncRig(name, folder.newFolder(), w, acct, channelId, seed = name.sumOf { it.code } % 90 + 1, discoveryLimits = limits, timeouts = timeouts, limits = historyLimits)
        .also { rigs.add(it) }

    private fun <T> Gated<T>.value(): T = (this as Gated.Done).value
    private fun creates() = rigs.sumOf { r -> r.tdlib.gateway.count("createNewSupergroupChat") }

    private fun others(count: Int, archivedEvery: Int = 0, w: FakeTdlibWorld = world) {
        for (i in 1..count) w.createChannel(title = "Chat $i", description = "just chat $i", withCreateMessage = false).also {
            if (archivedEvery > 0 && i % archivedEvery == 0) it.archived = true
        }
    }

    // ================= R01: discovery completeness ==================================================

    @Test fun d01_aRenamedMarkedChannelIsStillFound() {
        val mine = world.createSyncChannel(acct.value).also { it.title = "Playlist backup (renamed)" }
        others(5)
        val r = rig()
        assertEquals(ChannelDiscovery.Found(mine.id), r.service.discoverChannel(acct).value())
    }

    @Test fun d02_anArchivedMarkedChannelThatIsNotLoadedYetIsStillFound() {
        val mine = world.createSyncChannel(acct.value).also { it.archived = true }
        others(12, archivedEvery = 3)
        val r = rig()
        r.tdlib.gateway.startUnloaded()
        r.tdlib.gateway.loadStep = 3
        assertEquals(ChannelDiscovery.Found(mine.id), r.service.discoverChannel(acct).value())
    }

    @Test fun d03_aMarkedChannelRenamedAndNotLoadedIsFoundAndCreateRefuses() {
        val mine = world.createSyncChannel(acct.value).also { it.title = "x" }
        others(9)
        val r = rig()
        r.tdlib.gateway.startUnloaded()
        r.tdlib.gateway.loadStep = 2
        assertEquals(CreateOutcome.Exists(ChannelDiscovery.Found(mine.id)), r.service.createChannel(acct).value())
        assertEquals(0, creates())
    }

    @Test fun d04_moreThanFiftyTitleMatchesDoNotHideTheRealChannel() {
        for (i in 1..60) world.createChannel(description = "lookalike $i", withCreateMessage = false) // same title, no marker
        val mine = world.createSyncChannel(acct.value)
        val r = rig()
        assertEquals(ChannelDiscovery.Found(mine.id), r.service.discoverChannel(acct).value())
        assertEquals(CreateOutcome.Exists(ChannelDiscovery.Found(mine.id)), r.service.createChannel(acct).value())
        assertEquals(0, creates())
    }

    @Test fun d05_aFailedChatListLoadIsNotAbsenceAndCreateIsRefused() {
        world.createSyncChannel(acct.value).also { it.archived = true }
        others(6)
        val r = rig()
        r.tdlib.gateway.startUnloaded()
        r.tdlib.gateway.failNext("loadChats", 500, "Internal error")
        assertEquals(ChannelDiscovery.Failed(SyncErrorKind.NETWORK), r.service.discoverChannel(acct).value())
        r.tdlib.gateway.failNext("loadChats", 500, "Internal error")
        assertEquals(CreateOutcome.Failed(SyncErrorKind.NETWORK), r.service.createChannel(acct).value())
        assertEquals("no channel was made on an uncertain scan", 0, creates())
    }

    @Test fun d06_aLoadThatNeverFinishesIsBoundedIncompleteAndCreateIsRefused() {
        others(4)
        val r = rig(limits = DiscoveryLimits(maxLoadRounds = 5))
        r.tdlib.gateway.neverFinishLoad = true
        assertEquals(ChannelDiscovery.Incomplete(IncompleteReason.LOAD_NOT_FINISHED), r.service.discoverChannel(acct).value())
        assertEquals(5, r.tdlib.gateway.count("loadChats"))
        assertEquals(CreateOutcome.DiscoveryIncomplete(ChannelDiscovery.Incomplete(IncompleteReason.LOAD_NOT_FINISHED)), r.service.createChannel(acct).value())
        assertEquals(0, creates())
    }

    @Test fun d07_aChatListTooLongToProveIsIncompleteNotNotFound() {
        others(1_001)
        val r = rig()
        assertEquals(ChannelDiscovery.Incomplete(IncompleteReason.TOO_MANY_CHATS), r.service.discoverChannel(acct).value())
        assertEquals(CreateOutcome.DiscoveryIncomplete(ChannelDiscovery.Incomplete(IncompleteReason.TOO_MANY_CHATS)), r.service.createChannel(acct).value())
        assertEquals(0, creates())
    }

    @Test fun d08_absenceIsProvenByAnExhaustiveScanAndThenExactlyOneChannelIsCreated() {
        others(25, archivedEvery = 5)
        val r = rig()
        r.tdlib.gateway.startUnloaded()
        r.tdlib.gateway.loadStep = 7
        assertEquals(ChannelDiscovery.NotFound, r.service.discoverChannel(acct).value())
        assertTrue("both chat lists were loaded to their end", r.tdlib.gateway.count("loadChats") >= 2)
        val created = r.service.createChannel(acct).value() as CreateOutcome.Created
        assertEquals(1, creates())
        assertEquals(CreateOutcome.Exists(ChannelDiscovery.Found(created.chatId)), r.service.createChannel(acct).value())
        assertEquals(1, creates())
    }

    @Test fun d09_theBoundChannelIsVerifiedDirectlyWithoutAnyScan() {
        others(30)
        val mine = world.createSyncChannel(acct.value).also { it.title = "renamed"; it.archived = true }
        val r = rig(channelId = mine.id)
        r.tdlib.gateway.startUnloaded()
        assertEquals(ChannelDiscovery.Found(mine.id), r.service.discoverChannel(acct).value())
        assertEquals(0, r.tdlib.gateway.count("loadChats") + r.tdlib.gateway.count("getChats") + r.tdlib.gateway.count("searchChats"))
        assertTrue("a handful of requests, not a scan", r.tdlib.gateway.requests.size <= 4)
    }

    @Test fun d10_aBoundChannelThatIsGoneOrNoLongerMarkedIsReportedNeverNotFound() {
        val mine = world.createSyncChannel(acct.value)
        val r = rig(channelId = mine.id)
        mine.description = "edited by somebody"
        assertEquals(ChannelDiscovery.Unsafe(UnsafeChannel.MARKER_MISSING), r.service.discoverChannel(acct).value())
        world.chats.remove(mine.id)
        assertEquals(ChannelDiscovery.Failed(SyncErrorKind.CHANNEL_MISSING), r.service.discoverChannel(acct).value())
        assertEquals(0, creates())
    }

    @Test fun d11_aCheckpointThatNamesAChannelNeverLetsAnotherBeCreated() {
        val mine = world.createSyncChannel(acct.value)
        val r = rig(channelId = mine.id)
        val before = r.tdlib.gateway.requests.size
        assertEquals(CreateOutcome.AlreadyBound(mine.id), r.service.createChannel(acct).value())
        assertEquals(before, r.tdlib.gateway.requests.size)
        assertEquals(0, creates())
    }

    @Test fun d12_aSecondDeviceWithAFreshClientFindsTheChannelTheFirstOneMade() {
        val a = rig("dev-a")
        val created = a.service.createChannel(acct).value() as CreateOutcome.Created
        world.chats.getValue(created.chatId).archived = true // the owner archived it meanwhile
        val b = rig("dev-b")
        b.tdlib.gateway.startUnloaded()
        b.tdlib.gateway.loadStep = 1
        assertEquals(ChannelDiscovery.Found(created.chatId), b.service.discoverChannel(acct).value())
        assertEquals(CreateOutcome.Exists(ChannelDiscovery.Found(created.chatId)), b.service.createChannel(acct).value())
        assertEquals("only A ever created", 1, creates())
    }

    @Test fun d13_aRenamedAndAnArchivedMarkedChannelAreAmbiguousNotChosen() {
        world.createSyncChannel(acct.value).also { it.title = "renamed" }
        world.createSyncChannel(acct.value).also { it.archived = true }
        val r = rig()
        r.tdlib.gateway.startUnloaded()
        assertEquals(ChannelDiscovery.Ambiguous(2), r.service.discoverChannel(acct).value())
        assertEquals(CreateOutcome.Exists(ChannelDiscovery.Ambiguous(2)), r.service.createChannel(acct).value())
        assertEquals(0, creates())
    }

    @Test fun d14_aScanThatWouldNeedTooManyRequestsStopsIncompleteAndCreateIsRefused() {
        others(40)
        val r = rig(limits = DiscoveryLimits(maxRequests = 12))
        assertEquals(ChannelDiscovery.Incomplete(IncompleteReason.BUDGET), r.service.discoverChannel(acct).value())
        assertTrue("bounded: never more than the budget plus the request in flight", r.tdlib.gateway.requests.size <= 14)
        assertEquals(CreateOutcome.DiscoveryIncomplete(ChannelDiscovery.Incomplete(IncompleteReason.BUDGET)), r.service.createChannel(acct).value())
        assertEquals(0, creates())
    }

    @Test fun d15_channelsOfOthersThatIsOnlyFollowedAreSkippedWithoutAskingForTheirDescription() {
        for (i in 1..30) world.createChannel(title = "Followed $i", description = "somebody else's", withCreateMessage = false).status = "chatMemberStatusMember"
        val mine = world.createSyncChannel(acct.value)
        val r = rig()
        assertEquals(ChannelDiscovery.Found(mine.id), r.service.discoverChannel(acct).value())
        assertEquals("only the one channel this account owns was read in full", 1, r.tdlib.gateway.count("getSupergroupFullInfo"))
    }

    // ================= R02: strict consent file =========================================================

    private fun consentFile(): Pair<FileSyncConsentStore, File> {
        val root = folder.newFolder()
        val store = FileSyncConsentStore(root, TestPlainFileIo)
        store.save(acct, SyncConsent())
        return store to root.walkTopDown().first { it.isFile && it.name.endsWith(".txt") }
    }

    private fun load(content: String): SyncConsent {
        val (store, file) = consentFile()
        file.writeBytes(content.toByteArray(Charsets.UTF_8))
        return store.load(acct)
    }

    private val good = "mvconsent v1\nenabled 1\ncreate 1\ntransmit 1\nwarning ${SyncConsent.WARNING_VERSION}\n"

    @Test fun p01_aCorruptedHeaderWithOtherwiseValidPermissionsGrantsNothing() {
        for (header in listOf("corrupted", "mvconsent", "mvconsent v2", "mvconsent v1 ", " mvconsent v1", "MVCONSENT V1", "mvconsent  v1", "")) {
            assertEquals("header '$header'", SyncConsent(), load(good.replaceFirst("mvconsent v1", header)))
        }
        // The very file from the audit.
        assertEquals(SyncConsent(), load("corrupted\nenabled 1\ncreate 1\ntransmit 1\nwarning 1\n"))
    }

    @Test fun p02_everyFileTheCurrentWriterProducesStillLoadsExactly() {
        val (store, _) = consentFile()
        for (enabled in listOf(false, true)) for (create in listOf(false, true)) for (transmit in listOf(false, true)) for (w in listOf(0, SyncConsent.WARNING_VERSION)) {
            if (!enabled && (create || transmit)) continue // the actions never produce these
            val c = SyncConsent(enabled, create, transmit, w)
            assertTrue(store.save(acct, c))
            assertEquals(c, store.load(acct))
        }
        assertEquals(SyncConsent(true, true, true, SyncConsent.WARNING_VERSION), load(good))
        val actions = SyncConsentActions(store)
        actions.enable(acct, true); actions.allowChannelCreation(acct); actions.allowInitialTransmit(acct)
        assertEquals(SyncConsent(true, true, true, SyncConsent.WARNING_VERSION), store.load(acct))
    }

    @Test fun p03_duplicateKeysAreRefused() {
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled 1\nenabled 0\ncreate 0\ntransmit 0\nwarning 1\n"))
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled 1\ncreate 1\ntransmit 1\nwarning 1\nwarning 1\n"))
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled 1\nenabled 1\ncreate 1\ntransmit 1\nwarning 1\n"))
    }

    @Test fun p04_missingKeysAndTruncatedFilesAreRefused() {
        val lines = good.trimEnd('\n').split('\n')
        for (drop in 1..4) assertEquals("without ${lines[drop]}", SyncConsent(), load((lines.filterIndexed { i, _ -> i != drop }).joinToString("\n") + "\n"))
        assertEquals(SyncConsent(), load("mvconsent v1\n"))
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled 1\ncreate 1\n"))
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled 1\ncreate 1\ntransmit 1\nwarning")) // cut inside the last line
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled 1\ncreate 1\ntransmit 1\nwarn"))
        assertEquals(SyncConsent(), load(""))
    }

    @Test fun p05_invalidValuesAreRefused() {
        for (bad in listOf("2", "yes", "", "-1", "01", " 1", "1 ", "true", "1.0", "٣")) {
            assertEquals("enabled '$bad'", SyncConsent(), load("mvconsent v1\nenabled $bad\ncreate 1\ntransmit 1\nwarning 1\n"))
            assertEquals("create '$bad'", SyncConsent(), load("mvconsent v1\nenabled 1\ncreate $bad\ntransmit 1\nwarning 1\n"))
            assertEquals("transmit '$bad'", SyncConsent(), load("mvconsent v1\nenabled 1\ncreate 1\ntransmit $bad\nwarning 1\n"))
        }
        for (bad in listOf("", "-1", "x", (SyncConsent.WARNING_VERSION + 1).toString(), "99", "01", "9999999999999999999", "1.0")) {
            assertEquals("warning '$bad'", SyncConsent(), load("mvconsent v1\nenabled 1\ncreate 1\ntransmit 1\nwarning $bad\n"))
        }
    }

    @Test fun p06_extraKeysExtraLinesAndOddBytesAreRefused() {
        assertEquals(SyncConsent(), load(good + "extra 1\n"))
        assertEquals(SyncConsent(), load(good + "\n"))
        assertEquals(SyncConsent(), load(good.replace("\n", "\r\n")))
        assertEquals(SyncConsent(), load("﻿" + good))
        assertEquals(SyncConsent(), load(good + "\u0000"))
        assertEquals(SyncConsent(), load(good.replace("create 1", "create 1 # comment")))
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled\t1\ncreate 1\ntransmit 1\nwarning 1\n"))
        assertEquals(SyncConsent(), load("mvconsent v1\nEnabled 1\ncreate 1\ntransmit 1\nwarning 1\n"))
        val invalidUtf8 = good.toByteArray(Charsets.UTF_8).also { it[14] = 0xC3.toByte(); it[15] = 0x28 }
        val (store, file) = consentFile()
        file.writeBytes(invalidUtf8)
        assertEquals(SyncConsent(), store.load(acct))
        assertEquals(SyncConsent(), load("mvconsent v1\n" + "enabled 1\n".repeat(5000)))
    }

    @Test fun p07_permissionsThatCannotBeProducedByTheActionsAreRefused() {
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled 0\ncreate 1\ntransmit 0\nwarning 1\n"))
        assertEquals(SyncConsent(), load("mvconsent v1\nenabled 0\ncreate 0\ntransmit 1\nwarning 1\n"))
    }

    @Test fun p08_anInvalidFileNeverLetsTheGateOpenCreationOrTransmission() {
        val root = folder.newFolder()
        val store = FileSyncConsentStore(root, TestPlainFileIo)
        store.save(acct, SyncConsent())
        val file = root.walkTopDown().first { it.isFile && it.name.endsWith(".txt") }
        file.writeText("badHeader\nenabled 1\ncreate 1\ntransmit 1\nwarning 1\n")
        val gate = SyncGate(true, store)
        assertEquals(SyncDenied.NOT_ENABLED, gate.mayRun(acct))
        assertEquals(SyncDenied.NOT_ENABLED, gate.mayCreate(acct))
        assertEquals(SyncDenied.NOT_ENABLED, gate.mayTransmit(acct))
        file.writeText(good)
        assertEquals(null, gate.mayCreate(acct))
        assertEquals(null, gate.mayTransmit(acct))
    }

    // ================= R03: an inconclusive second look fails closed ====================================

    private lateinit var tdlib: TdlibRig
    private lateinit var hub: TdlibSyncHub
    private var chat = 0L

    private fun direct(): TdlibHistoryReader {
        if (!::tdlib.isInitialized) {
            tdlib = TdlibRig(world, acct.value, folder.newFolder()).start()
            chat = world.createChannel().id
            hub = TdlibSyncHub(tdlib.core).also { check(it.open()) }
            hub.watchChannel(chat)
        }
        return TdlibHistoryReader(hub, acct.value, tdlib.core.authEpoch(), chat, HistoryLimits(requestTimeoutMs = 400L))
    }

    @After fun closeDirect() {
        if (::hub.isInitialized) hub.close()
        if (::tdlib.isInitialized) tdlib.stop()
    }

    private fun forwardCount() = tdlib.gateway.requests.count { it.optString("@type") == "getChatHistory" && it.optInt("offset") < 0 }

    private fun holeOnReverseScan(hole: Long, active: () -> Boolean) {
        tdlib.gateway.historyFilter = { req, list ->
            if (active() && req.optInt("offset") == 0) list.removeAll { it is JSONObject && it.optLong("id") == hole }
        }
    }

    @Test fun x01_aMissingMiddleMessageAndAFailedSecondLookIsNeverServed() {
        val reader = direct()
        world.postMany(chat, 150)
        holeOnReverseScan(world.ids(chat)[40]) { true }
        tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.ERROR_REPLY)
        val result = reader.fetchAfter(chat, 0L, 100)
        assertEquals("no page, no proof, so the cursor cannot move", FetchResult.Failed(SyncErrorKind.NETWORK), result)
    }

    @Test fun x02_aMalformedSecondLookIsInconclusiveNotVerified() {
        val reader = direct()
        world.postMany(chat, 150)
        holeOnReverseScan(world.ids(chat)[40]) { true }
        tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.MALFORMED)
        assertEquals(FetchResult.Failed(SyncErrorKind.NETWORK), reader.fetchAfter(chat, 0L, 100))
    }

    @Test fun x03_aSecondLookThatTimesOutIsInconclusiveNotVerified() {
        val reader = direct()
        world.postMany(chat, 150)
        holeOnReverseScan(world.ids(chat)[40]) { true }
        tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.SILENT)
        assertEquals(FetchResult.Failed(SyncErrorKind.TIMEOUT), reader.fetchAfter(chat, 0L, 100))
    }

    @Test fun x04_afterTheFailureTheNextValidSecondLookCatchesTheHoleAndALaterCleanScanConverges() {
        val reader = direct()
        world.postMany(chat, 150)
        var holeOn = true
        holeOnReverseScan(world.ids(chat)[40]) { holeOn }
        tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.ERROR_REPLY)
        assertTrue(reader.fetchAfter(chat, 0L, 100) is FetchResult.Failed)
        // The retry's second look is valid and shows the message the scan lacked: refused, nothing proven.
        val second = reader.fetchAfter(chat, 0L, 100) as FetchResult.Page
        assertEquals(FetchResult.Page.UNPROVEN, second.completeThroughId)
        assertTrue(second.messages.isEmpty())
        // The source behaves again: a fresh scan and a valid second look give the complete oldest hundred.
        holeOn = false
        val third = reader.fetchAfter(chat, 0L, 100) as FetchResult.Page
        assertEquals(world.ids(chat).take(100), third.messages.map { it.id })
        assertEquals(world.ids(chat)[99], third.completeThroughId)
    }

    @Test fun x05_withoutAHoleAFailedSecondLookIsRetriedAndThePageConverges() {
        val reader = direct()
        world.postMany(chat, 150)
        tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.ERROR_REPLY)
        assertTrue(reader.fetchAfter(chat, 0L, 100) is FetchResult.Failed)
        val before = forwardCount()
        val page = reader.fetchAfter(chat, 0L, 100) as FetchResult.Page
        assertEquals(world.ids(chat).take(100), page.messages.map { it.id })
        assertEquals("the check was asked again, not skipped", before + 1, forwardCount())
        // A verified snapshot is not checked again for the next full page (only the coordinator's own look does that).
        val after = forwardCount()
        reader.fetchAfter(chat, page.completeThroughId, 100)
        assertEquals(after, forwardCount())
    }

    @Test fun x06_theCoordinatorsOwnSecondLookIsAlsoInconclusiveWhenItFails() {
        val reader = direct()
        world.postMany(chat, 60)
        val page = reader.fetchAfter(chat, 0L, 100) as FetchResult.Page
        assertEquals(world.ids(chat), page.messages.map { it.id })
        tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.SILENT)
        assertEquals(FetchResult.Failed(SyncErrorKind.TIMEOUT), reader.fetchAfter(chat, 0L, 1))
        assertTrue(reader.fetchAfter(chat, 0L, 1) is FetchResult.Page)
    }

    @Test fun x07_aRunWithAHoleAndAFailedSecondLookChangesNothingThenConvergesOnceTheSourceIsHealthy() {
        val mine = world.createSyncChannel(acct.value)
        world.postMany(mine.id, 150)
        val r = rig(channelId = mine.id, timeouts = TdlibTransportTimeouts(requestMs = 400L, sendAckMs = 400L),
            historyLimits = HistoryLimits(requestTimeoutMs = 400L))
        r.device.toggle(acct.value, songId(1))
        val before = r.device.stored()
        val lastForeign = world.ids(mine.id).last()
        var holeOn = true
        val hole = world.ids(mine.id)[40]
        r.tdlib.gateway.historyFilter = { req, list ->
            if (holeOn && req.optInt("offset") == 0) list.removeAll { it is JSONObject && it.optLong("id") == hole }
        }
        r.tdlib.gateway.forwardFaults.add(FakeTdlibJsonGateway.ForwardFault.ERROR_REPLY)
        val first = r.run()
        assertEquals(SyncStatus.BACKOFF, first.status)
        assertEquals("the cursor did not move", before.checkpoint.lastAppliedServerId, r.device.stored().checkpoint.lastAppliedServerId)
        assertEquals("nothing was sent", 0, r.tdlib.gateway.count("sendMessage"))
        assertEquals(before.outbox, r.device.stored().outbox)
        holeOn = false
        r.device.nowMs = 10_000_000L
        var report = r.run()
        var guard = 0
        while (report.status != SyncStatus.OK && guard++ < 5) {
            r.device.nowMs += 10_000_000L
            report = r.run()
        }
        assertEquals(SyncStatus.OK, report.status)
        assertTrue("every message that existed was read", report.cursor >= lastForeign)
        assertEquals(1, world.texts(mine.id).count { it.startsWith(PlaylistSyncProtocol.MARKER) })
        assertEquals(emptyList<String>(), r.device.pendingIds())
    }
}
