package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Nhiều Playlist có tên, S8: the real service, owner core and transport over the fake TDLib cloud. */
class NamedPlaylistTdlibSyncTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val rigs = ArrayList<SyncRig>()

    @After fun tearDown() = rigs.forEach { it.stop() }

    private class MemoryStore : NamedPlaylistPersistence {
        val files = HashMap<Long, NamedPlaylistState>()
        override fun load(user: TelegramUserId): NamedLoadResult = files[user.value]?.let { NamedLoadResult.Valid(it) } ?: NamedLoadResult.Missing
        override fun save(user: TelegramUserId, state: NamedPlaylistState) { files[user.value] = state }
    }

    /** One device: the favourites rig of the existing tests plus a named-playlist session wired into a service. */
    private inner class Side(
        val name: String, val world: FakeTdlibWorld, channel: Long, seed: Int,
        consent: Boolean = true, build: Boolean = true, wired: Boolean = true,
    ) {
        val rig = SyncRig(name, folder.newFolder(), world, acct, channel, seed, consent = consent, buildEnabled = build).also { rigs.add(it) }
        val named = NamedPlaylistSession(MemoryStore()) { "$name-n" }.also { check(it.open(acct) is NamedOpenResult.Ready) }
        val service = TdlibPlaylistSyncService(
            rig.tdlib.core, rig.device.session, SyncGate(build, rig.consentStore), { rig.device.nowMs }, SyncConfig(),
            TdlibTransportTimeouts(requestMs = 2_000L, sendAckMs = 400L), HistoryLimits(requestTimeoutMs = 2_000L), DiscoveryLimits(),
            namedHost = if (wired) named else null,
        )

        fun runNamed(maxEnvelopes: Int = NamedSyncLimits.PRODUCTION_ENVELOPES, mode: SyncMode = SyncMode.INCREMENTAL) =
            service.runNamedSync(acct, mode, maxEnvelopes)

        fun report(maxEnvelopes: Int = NamedSyncLimits.PRODUCTION_ENVELOPES): SyncReport = (runNamed(maxEnvelopes) as Gated.Done).value
        fun state() = checkNotNull(named.view(acct)).state
        fun sends() = rig.tdlib.gateway.count("sendMessage")
        fun create(n: String): ListId = checkNotNull((named.createList(acct, n) as NamedOutcome.Ok).listId)
        fun add(id: ListId, n: Long) = named.addTelegram(acct, id, PlaylistItemId.Telegram(-1001234L, n))
    }

    private fun worldWithChannel(): Pair<FakeTdlibWorld, Long> {
        val world = FakeTdlibWorld()
        return world to world.createSyncChannel(acct.value).id
    }

    private fun textsOf(world: FakeTdlibWorld, channel: Long) = world.chats.getValue(channel).messages.values.map { it.text }

    @Test fun n01_aNamedPlaylistTravelsAsAMvlistMessageThroughTheRealTransportToAnotherDevice() {
        val (world, channel) = worldWithChannel()
        val a = Side("dev-a", world, channel, 51)
        val b = Side("dev-b", world, channel, 52)
        val id = a.create("Nhạc đi xe")
        for (n in 1L..3L) a.add(id, n)
        val sent = a.report()
        assertEquals(SyncStatus.OK, sent.status)
        assertEquals(1, a.sends())
        assertEquals(channel, a.state().checkpoint.channelId)
        val mvlist = textsOf(world, channel).filter { it.startsWith("#mvlist v1") }
        assertEquals(1, mvlist.size)
        assertEquals(TdlibText.normalize(mvlist[0]), mvlist[0])
        val received = b.report()
        assertEquals(SyncStatus.OK, received.status)
        assertEquals(0, b.sends())
        assertEquals(a.state().lists, b.state().lists)
        assertEquals(listOf("Nhạc đi xe"), b.state().lists().map { it.name })
        assertEquals(3, b.state().itemCount(id))
        // B answers with a rename and a move
        b.named.renameList(acct, id, "Đi xa")
        b.named.moveItem(acct, id, PlaylistItemId.Telegram(-1001234L, 3L), 0)
        assertEquals(SyncStatus.OK, b.report().status)
        assertEquals(SyncStatus.OK, a.report().status)
        assertEquals(b.state().lists, a.state().lists)
        assertEquals("Đi xa", a.state().nameOf(id))
    }

    @Test fun n02_favouritesAndNamedPlaylistsShareOneChannelWithoutDisturbingEachOther() {
        val (world, channel) = worldWithChannel()
        val a = Side("dev-a", world, channel, 53)
        val b = Side("dev-b", world, channel, 54)
        a.rig.device.toggle(-1_001_000_000_900L, songId(1))
        assertEquals(SyncStatus.OK, a.rig.run().status)
        val id = a.create("Tập gym")
        a.add(id, 7)
        assertEquals(SyncStatus.OK, a.report().status)
        a.rig.device.toggle(-1_001_000_000_900L, songId(2))
        assertEquals(SyncStatus.OK, a.rig.run().status)
        // the other device reads the favourites with the named message in the middle of the history
        val favourites = b.rig.run()
        assertEquals(SyncStatus.OK, favourites.status)
        assertTrue("the named message and the channel service message were skipped", favourites.foreignSkipped >= 1)
        assertEquals(2, b.rig.device.present().size)
        assertEquals(SyncStatus.OK, b.report().status)
        assertEquals(1, b.state().lists().size)
        assertEquals(a.rig.device.present(), b.rig.device.present())
        assertEquals(a.state().lists, b.state().lists)
    }

    @Test fun n03_anEditedNamedMessageIsContinuityEvidenceAndBlocksReading() {
        val (world, channel) = worldWithChannel()
        val a = Side("dev-a", world, channel, 55)
        val b = Side("dev-b", world, channel, 56)
        a.create("Gốc")
        assertEquals(SyncStatus.OK, a.report().status)
        val messageId = world.chats.getValue(channel).messages.values.first { it.text.startsWith("#mvlist") }.id
        world.edit(channel, messageId, world.chats.getValue(channel).messages.getValue(messageId).text)
        val read = b.report()
        assertEquals(SyncStatus.RECOVERY_REQUIRED, read.status)
        assertEquals(RecoveryReason.TRANSPORT_CONTRACT, read.recovery)
        assertEquals(ContinuityAlert.MESSAGE_EDITED, b.service.continuityAlert())
        assertTrue(b.state().lists().isEmpty())
    }

    @Test fun n04_aRunThatHasToSendNeedsTheConsentToTransmitAndSendsNothingWithout() {
        val (world, channel) = worldWithChannel()
        val a = Side("dev-a", world, channel, 57, consent = false)
        assertTrue(a.rig.actions.enable(acct, true))
        a.create("L")
        assertEquals(Gated.Denied(SyncDenied.TRANSMIT_NOT_ALLOWED), a.runNamed())
        assertEquals(0, a.sends())
        assertEquals(1, a.state().outbox.size)
        // read only is allowed with the first consent alone
        assertEquals(SyncStatus.SEND_LIMIT_REACHED, (a.runNamed(maxEnvelopes = 0) as Gated.Done).value.status)
        assertEquals(0, a.sends())
    }

    @Test fun n05_aBuildWithTheSwitchOffAnAbsentNamedHostAndAnUnboundChannelNeverReachTdlib() {
        val (world, channel) = worldWithChannel()
        val off = Side("dev-a", world, channel, 58, build = false)
        val before = off.rig.tdlib.gateway.requests.size
        assertEquals(Gated.Denied(SyncDenied.BUILD_OFF), off.runNamed())
        assertEquals(before, off.rig.tdlib.gateway.requests.size)
        val unwired = Side("dev-b", world, channel, 59, wired = false)
        assertEquals(Gated.Denied(SyncDenied.NOT_OPEN), unwired.runNamed())
        val (world2, _) = worldWithChannel()
        val unbound = Side("dev-c", world2, 0L, 60)
        val requests = unbound.rig.tdlib.gateway.requests.size
        val answer = (unbound.runNamed() as Gated.Done).value
        assertEquals(SyncStatus.NO_CHANNEL, answer.status)
        assertEquals(requests, unbound.rig.tdlib.gateway.requests.size)
    }

    @Test fun n06_oneMessageIsSentPerRunWhateverTheCallerAsksFor() {
        val (world, channel) = worldWithChannel()
        val a = Side("dev-a", world, channel, 61)
        val id = a.create("Hàng chờ")
        for (n in 1L..44L) a.add(id, 1_000L + n)
        assertEquals(45, a.state().outbox.size)
        val first = a.report(maxEnvelopes = 5)
        assertEquals(SyncStatus.OUTBOX_INCOMPLETE, first.status)
        assertEquals(1, a.sends())
        assertEquals(NamedSyncLimits.OPS_PER_ENVELOPE, first.opsDispatched)
        assertEquals(25, a.state().outbox.count { it.status != OutboxStatus.ACKNOWLEDGED })
        assertEquals(SyncStatus.OUTBOX_INCOMPLETE, a.report(maxEnvelopes = 99).status)
        assertEquals(2, a.sends())
        assertEquals(SyncStatus.OK, a.report().status)
        assertEquals(3, a.sends())
        assertTrue(textsOf(world, channel).filter { it.startsWith("#mvlist") }.all { it.length <= NamedPlaylistSyncProtocol.MAX_CHARS })
        val b = Side("dev-b", world, channel, 62)
        assertEquals(SyncStatus.OK, b.report().status)
        assertEquals(44, b.state().itemCount(id))
    }

    @Test fun n07_theFavouritesRunDoesNotSeeTheNamedHostAtAll() {
        val (world, channel) = worldWithChannel()
        val a = Side("dev-a", world, channel, 63)
        a.create("L")
        a.rig.device.toggle(-1_001_000_000_900L, songId(1))
        val report = a.rig.run()
        assertEquals(SyncStatus.OK, report.status)
        assertEquals("only the favourite operation was sent", 1, a.sends())
        assertEquals("the named operation still waits", 1, a.state().outbox.size)
        assertNotEquals(OutboxStatus.ACKNOWLEDGED, a.state().outbox.single().status)
        assertTrue(textsOf(world, channel).none { it.startsWith("#mvlist") })
    }
}
