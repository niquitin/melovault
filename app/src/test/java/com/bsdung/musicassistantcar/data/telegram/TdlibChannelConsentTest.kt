package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * E96 P3-04: consent defaults OFF, a channel is found by protocol identity (never by title), it is never created
 * automatically, and it is bound only after it verifies. Everything runs against fake TDLib; no real channel exists.
 */
class TdlibChannelConsentTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val world = FakeTdlibWorld()
    private val rigs = ArrayList<SyncRig>()

    private fun rig(name: String = "dev-a", channelId: Long = 0L, consent: Boolean = true, build: Boolean = true, w: FakeTdlibWorld = world): SyncRig =
        SyncRig(name, folder.newFolder(), w, acct, channelId, seed = name.sumOf { it.code } % 90 + 1, consent = consent, buildEnabled = build)
            .also { rigs.add(it) }

    @After fun tearDown() = rigs.forEach { it.stop() }

    private fun <T> Gated<T>.value(): T = (this as Gated.Done).value
    private fun SyncRig.requestsOf(vararg types: String) = tdlib.gateway.typesSeen().count { it in types }
    private val creates get() = rigs.sumOf { it.requestsOf("createNewSupergroupChat") }

    // ---- consent: default OFF, three separate permissions --------------------------------------------

    @Test fun c01_everythingIsOffByDefaultForEveryAccount() {
        val store = InMemorySyncConsentStore()
        assertEquals(SyncConsent(), store.load(acct))
        assertFalse(SyncConsent().enabled || SyncConsent().mayCreateChannel || SyncConsent().mayTransmitInitial || SyncConsent().warningAccepted)
        val gate = SyncGate(true, store)
        assertEquals(SyncDenied.NOT_ENABLED, gate.mayRun(acct))
        assertEquals(SyncDenied.NOT_ENABLED, gate.mayCreate(acct))
        assertEquals(SyncDenied.NOT_ENABLED, gate.mayTransmit(acct))
        assertEquals(SyncDenied.BUILD_OFF, SyncGate(false, store).mayRun(acct))
    }

    @Test fun c02_enablingNeedsTheWarningToHaveBeenShownAndAccepted() {
        val store = InMemorySyncConsentStore()
        val actions = SyncConsentActions(store)
        assertFalse(actions.enable(acct, warningShownAndAccepted = false))
        assertEquals(SyncConsent(), store.load(acct))
        assertTrue(actions.enable(acct, true))
        assertEquals(null, SyncGate(true, store).mayRun(acct))
        // An older, different warning version does not count.
        store.save(acct, SyncConsent(enabled = true, acceptedWarningVersion = 0))
        assertEquals(SyncDenied.WARNING_NOT_ACCEPTED, SyncGate(true, store).mayRun(acct))
    }

    @Test fun c03_creatingAChannelAndTransmittingAreSeparatePermissionsBehindEnabling() {
        val store = InMemorySyncConsentStore()
        val actions = SyncConsentActions(store)
        assertFalse(actions.allowChannelCreation(acct))
        assertFalse(actions.allowInitialTransmit(acct))
        actions.enable(acct, true)
        val gate = SyncGate(true, store)
        assertEquals(SyncDenied.CREATE_NOT_ALLOWED, gate.mayCreate(acct))
        assertEquals(SyncDenied.TRANSMIT_NOT_ALLOWED, gate.mayTransmit(acct))
        assertTrue(actions.allowChannelCreation(acct))
        assertEquals(null, gate.mayCreate(acct))
        assertEquals("(b) does not grant (c)", SyncDenied.TRANSMIT_NOT_ALLOWED, gate.mayTransmit(acct))
        assertTrue(actions.allowInitialTransmit(acct))
        assertEquals(null, gate.mayTransmit(acct))
    }

    @Test fun c04_revokingTurnsEverythingOffAndTheFileStoreIsPerAccountAndFailsClosed() {
        val store = FileSyncConsentStore(folder.newFolder(), TestPlainFileIo)
        val actions = SyncConsentActions(store)
        val other = TelegramUserId(2002L)
        actions.enable(acct, true)
        actions.allowChannelCreation(acct)
        actions.allowInitialTransmit(acct)
        assertEquals(SyncConsent(true, true, true, SyncConsent.WARNING_VERSION), store.load(acct))
        assertEquals("another account is untouched", SyncConsent(), store.load(other))
        assertTrue(actions.revoke(acct))
        assertEquals(SyncConsent(), store.load(acct))
        // A damaged file means OFF.
        actions.enable(acct, true)
        val file = folder.root.walkTopDown().first { it.isFile && it.name.endsWith(".txt") }
        file.writeText("garbage\nenabled 1\n\u0000")
        assertFalse(store.load(acct).warningAccepted)
        file.delete()
        assertEquals(SyncConsent(), store.load(acct))
    }

    // ---- discovery: identity is the marker, not the title ---------------------------------------------

    @Test fun c05_noChannelFoundMeansNotFoundAndNothingIsCreated() {
        val r = rig()
        assertEquals(ChannelDiscovery.NotFound, r.service.discoverChannel(acct).value())
        assertEquals("never creates on its own", 0, creates)
        assertEquals(0, r.requestsOf("sendMessage"))
    }

    @Test fun c06_exactlyOneVerifiedChannelIsFoundAndLookalikesAreIgnored() {
        val mine = world.createSyncChannel(acct.value)
        world.createChannel(description = "just a channel named the same") // title alone: not trusted
        world.createChannel(description = SyncChannelSpec.descriptionOf(999L)) // another account's marker
        world.createChannel().also { it.kind = "chatTypePrivate" }          // e.g. a private chat / Saved Messages style chat
        val r = rig()
        assertEquals(ChannelDiscovery.Found(mine.id), r.service.discoverChannel(acct).value())
    }

    @Test fun c07_aChannelWithTheRightTitleButNoMarkerIsNotTheSyncChannel() {
        world.createChannel(description = "")
        val r = rig()
        assertEquals(ChannelDiscovery.NotFound, r.service.discoverChannel(acct).value())
    }

    @Test fun c08_twoChannelsClaimingTheMarkerAreAmbiguousAndNothingIsCreatedOrChosen() {
        world.createSyncChannel(acct.value)
        world.createSyncChannel(acct.value)
        val r = rig()
        assertEquals(ChannelDiscovery.Ambiguous(2), r.service.discoverChannel(acct).value())
        val created = r.service.createChannel(acct).value()
        assertEquals(CreateOutcome.Exists(ChannelDiscovery.Ambiguous(2)), created)
        assertEquals(0, creates)
        assertEquals(2, world.chats.size)
    }

    @Test fun c09_aMarkedChatThatIsNotAPrivateChannelOfTheirOwnIsNotTrusted() {
        data class Case(val reason: UnsafeChannel, val mutate: (FakeChatData) -> Unit)
        val cases = listOf(
            Case(UnsafeChannel.NOT_A_CHANNEL) { it.isChannel = false },
            Case(UnsafeChannel.PUBLIC) { it.username = "melovault_public" },
            Case(UnsafeChannel.NOT_OWNER) { it.status = "chatMemberStatusAdministrator" },
            Case(UnsafeChannel.HAS_OTHER_MEMBERS) { it.members = 3 },
            Case(UnsafeChannel.AUTO_DELETE_ON) { it.autoDelete = 86_400 },
            Case(UnsafeChannel.LINKED_CHAT) { it.linkedChat = true },
        )
        for (case in cases) {
            val w = FakeTdlibWorld()
            case.mutate(w.createSyncChannel(acct.value))
            val r = rig("dev-${case.reason.name.take(3).lowercase()}", w = w)
            assertEquals(case.reason.name, ChannelDiscovery.Unsafe(case.reason), r.service.discoverChannel(acct).value())
            assertEquals("not bound either", BindResult.NotVerified, r.service.bindChannel(acct, w.chats.keys.first()).value())
        }
    }

    // ---- creation only on explicit permission ----------------------------------------------------------

    @Test fun c10_withoutPermissionToCreateNothingIsAskedOfTelegramAtAll() {
        val r = rig(consent = false)
        r.actions.enable(acct, true) // (a) only
        assertEquals(Gated.Denied(SyncDenied.CREATE_NOT_ALLOWED), r.service.createChannel(acct))
        assertEquals(0, r.tdlib.gateway.requests.count { it.optString("@type") in setOf("createNewSupergroupChat", "searchChats", "getChat") })
    }

    @Test fun c11_aCreatedChannelIsPrivateABroadcastChannelWithNoAutoDeleteAndIsVerifiedAfterwards() {
        val r = rig()
        val created = r.service.createChannel(acct).value() as CreateOutcome.Created
        val request = r.tdlib.gateway.requests.first { it.optString("@type") == "createNewSupergroupChat" }
        assertEquals(true, request.getBoolean("is_channel"))
        assertEquals(false, request.getBoolean("is_forum"))
        assertEquals(0, request.getInt("message_auto_delete_time"))
        assertFalse("no location", request.has("location"))
        assertEquals(SyncChannelSpec.TITLE, request.getString("title"))
        val description = request.getString("description")
        assertTrue(description.length <= 255)
        assertTrue(description.startsWith(SyncChannelSpec.markerOf(acct.value)))
        assertTrue(description.contains("not end-to-end encrypted"))
        assertEquals(ChannelDiscovery.Found(created.chatId), r.service.discoverChannel(acct).value())
        assertEquals(1, creates)
    }

    @Test fun c12_anExistingChannelIsNeverDuplicated() {
        val mine = world.createSyncChannel(acct.value)
        val r = rig()
        assertEquals(CreateOutcome.Exists(ChannelDiscovery.Found(mine.id)), r.service.createChannel(acct).value())
        assertEquals(0, creates)
    }

    @Test fun c13_twoDevicesBootstrappingAtTheSameMomentEndWithTwoChannelsAndNobodyBinds() {
        val a = rig("dev-a")
        val b = rig("dev-b")
        var bResult: CreateOutcome? = null
        // While A's create request is in flight, B looks, finds nothing, and creates its own.
        a.tdlib.gateway.onCreate = { bResult = b.service.createChannel(acct).value(); a.tdlib.gateway.onCreate = null }
        val aResult = a.service.createChannel(acct).value()
        assertEquals(2, world.chats.size)
        assertTrue("B was first to finish and saw only its own", bResult is CreateOutcome.Created)
        assertEquals("A sees two and does not pick one", CreateOutcome.NeedsPerson(ChannelDiscovery.Ambiguous(2)), aResult)
        // From now on both see the ambiguity, and neither can bind.
        assertEquals(ChannelDiscovery.Ambiguous(2), b.service.discoverChannel(acct).value())
        assertEquals(BindResult.NotVerified, b.service.bindChannel(acct, (bResult as CreateOutcome.Created).chatId).value())
        assertEquals(0L, a.device.session.view(acct)!!.state.checkpoint.channelId)
        assertEquals(0L, b.device.session.view(acct)!!.state.checkpoint.channelId)
        assertEquals("no further automatic creation", 2, creates)
    }

    @Test fun c14_aCreateRequestWithoutAnAnswerIsUnknownAndIsNeverRepeated() {
        val r = rig()
        r.tdlib.gateway.staySilent("createNewSupergroupChat")
        val svc = TdlibPlaylistSyncService(r.tdlib.core, r.device.session, SyncGate(true, r.consentStore), { 0L },
            timeouts = TdlibTransportTimeouts(requestMs = 150L, sendAckMs = 100L), channelCreationUnlocked = true)
        assertEquals(CreateOutcome.Unknown, (svc.createChannel(acct) as Gated.Done).value)
        assertEquals(1, creates)
        // Nothing retries by itself; a later look finds whatever exists.
        Thread.sleep(150)
        assertEquals(1, creates)
    }

    // ---- binding ---------------------------------------------------------------------------------------

    @Test fun c15_theChannelIsBoundOnlyAfterItVerifiesAndSurvivesARestartWithoutTouchingTheCursor() {
        val mine = world.createSyncChannel(acct.value)
        val r = rig()
        r.device.toggle(acct.value, songId(1)) // some local state to protect
        val before = r.device.stored()
        assertEquals(BindResult.Bound, r.service.bindChannel(acct, mine.id).value())
        assertEquals(BindResult.Bound, r.service.bindChannel(acct, mine.id).value()) // idempotent
        r.restart()
        val after = r.device.stored()
        assertEquals(mine.id, after.checkpoint.channelId)
        assertEquals(before.checkpoint.lastAppliedServerId, after.checkpoint.lastAppliedServerId)
        assertEquals(before.outbox, after.outbox)
    }

    @Test fun c16_aChatThatDoesNotVerifyIsNotBound() {
        val r = rig()
        val stranger = world.createChannel(description = "nope")
        assertEquals(BindResult.NotVerified, r.service.bindChannel(acct, stranger.id).value())
        assertEquals(BindResult.NotVerified, r.service.bindChannel(acct, 0L).value())
        assertEquals(0L, r.device.stored().checkpoint.channelId)
    }

    @Test fun c17_aDifferentChannelIsNeverSubstitutedForTheBoundOne() {
        val first = world.createSyncChannel(acct.value)
        val r = rig()
        assertEquals(BindResult.Bound, r.service.bindChannel(acct, first.id).value())
        world.chats.remove(first.id)
        val second = world.createSyncChannel(acct.value)
        assertEquals(BindResult.ChannelConflict, r.service.bindChannel(acct, second.id).value())
        assertEquals("not replaced, not reset", first.id, r.device.stored().checkpoint.channelId)
    }

    // ---- running: gates before any traffic -------------------------------------------------------------

    @Test fun c18_withoutABoundChannelARunIsNoChannelAndAsksTelegramNothing() {
        val r = rig()
        assertEquals(SyncStatus.NO_CHANNEL, r.run().status)
        assertEquals(emptyList<String>(), r.tdlib.gateway.typesSeen())
        assertFalse(r.tdlib.core.syncIsBound())
    }

    @Test fun c19_revokingConsentStopsTheNextRunBeforeAnyTraffic() {
        val mine = world.createSyncChannel(acct.value)
        val r = rig(channelId = mine.id)
        r.device.toggle(acct.value, songId(1))
        assertEquals(SyncStatus.OK, r.run().status)
        val seen = r.tdlib.gateway.requests.size
        r.actions.revoke(acct)
        assertEquals(Gated.Denied(SyncDenied.NOT_ENABLED), r.service.runSync(acct))
        assertEquals(seen, r.tdlib.gateway.requests.size)
        assertEquals("the stored playlist and outbox are untouched", listOf(songId(1)), r.device.present())
    }

    @Test fun c20_workWaitingToBeSentNeedsTheSeparatePermissionToTransmit() {
        val mine = world.createSyncChannel(acct.value)
        val r = rig(channelId = mine.id, consent = false)
        r.actions.enable(acct, true)
        r.device.toggle(acct.value, songId(1))
        // (REWORK-02) only a run that may SEND needs (c); it is refused before TDLib is asked anything
        assertEquals(Gated.Denied(SyncDenied.TRANSMIT_NOT_ALLOWED), r.service.runSync(acct, SyncMode.INCREMENTAL, 1))
        assertEquals(emptyList<String>(), r.tdlib.gateway.typesSeen())
        assertEquals(1, r.device.pendingIds().size)
        // a read-only run (budget 0) is allowed with (a) alone and sends nothing
        assertTrue(r.service.runSync(acct) is Gated.Done)
        assertEquals(0, r.tdlib.gateway.count("sendMessage"))
        assertEquals(1, r.device.pendingIds().size)
        r.actions.allowInitialTransmit(acct)
        assertEquals(SyncStatus.OK, r.run().status)
        assertEquals(1, world.texts(mine.id).count { it.startsWith(PlaylistSyncProtocol.MARKER) })
    }

    @Test fun c21_aChannelThatIsGoneIsReportedNeverRecreatedAndNothingIsLost() {
        val mine = world.createSyncChannel(acct.value)
        val r = rig(channelId = mine.id)
        r.device.toggle(acct.value, songId(1))
        world.chats.remove(mine.id) // deleted / access revoked
        val report = r.run()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.CHANNEL_MISSING, report.recovery)
        assertEquals(0, creates)
        assertEquals(0, r.requestsOf("sendMessage"))
        assertEquals(mine.id, r.device.stored().checkpoint.channelId)
        assertEquals(1, r.device.pendingIds().size)
    }

    @Test fun c22_aLogoutAndAnotherAccountMidRunChangesNothingOfEitherPlaylist() {
        val mine = world.createSyncChannel(acct.value)
        world.postMany(mine.id, 30)
        val r = rig(channelId = mine.id)
        r.device.toggle(acct.value, songId(1))
        val before = r.device.stored()
        r.tdlib.gateway.beforeHistory = { r.tdlib.relogin(2002L); r.tdlib.gateway.beforeHistory = null }
        val report = r.run()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, report.status)
        assertEquals(RecoveryReason.UNAUTHORIZED, report.recovery)
        assertEquals(0, r.requestsOf("sendMessage"))
        assertEquals(before, r.device.stored())
    }

    @Test fun c23_attachKeepsTheSinkBetweenCallsAndDetachReleasesIt() {
        val r = rig()
        assertEquals(Gated.Done(true), r.service.attach(acct))
        r.service.discoverChannel(acct)
        assertTrue("still bound after a call", r.tdlib.core.syncIsBound())
        r.service.detach()
        assertFalse(r.tdlib.core.syncIsBound())
        r.service.discoverChannel(acct)
        assertFalse("a call alone binds only for its duration", r.tdlib.core.syncIsBound())
    }

    @Test fun c24_nothingInTheAppReachesTheSyncServiceExceptTheGuardedProvider() {
        val srcRoot = listOf(File("src"), File("app/src")).first { it.isDirectory }
        val own = setOf(
            "TdlibPlaylistSyncService.kt", "TdlibSyncChannel.kt", "TdlibSyncHub.kt", "TdlibHistoryReader.kt",
            "TdlibPlaylistSyncTransport.kt", "ShareableTdlibOwner.kt",
            // E96 P5-A: the single, switch-guarded composition root (see SyncServiceProvider and P5ReadinessInertnessTest).
            "SyncServiceProvider.kt",
        )
        val offenders = listOf("main", "shareable").flatMap { set ->
            File(srcRoot, set).walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") && it.name !in own }
                .filter { f ->
                    val text = f.readText()
                    listOf("TdlibPlaylistSyncService", "SyncTrigger", "TdlibChannelDirectory", "SyncConsentActions", "TdlibHistoryReader", "TdlibSyncHub")
                        .any { text.contains(it) }
                }.map { it.name }.toList()
        }
        assertEquals("production code that reaches the sync", emptyList<String>(), offenders)
        assertNotEquals(0, srcRoot.walkTopDown().count())
    }
}
