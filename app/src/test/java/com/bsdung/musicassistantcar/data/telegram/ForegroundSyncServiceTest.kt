package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real service/owner/host with a fake TDLib, including cancellation during a network reply. */
class ForegroundSyncServiceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun backgroundRefusesDiscoveryAndRunBeforeAnyTdlibRequest() {
        val account = TelegramUserId(1001)
        val world = FakeTdlibWorld()
        val channel = world.createSyncChannel(account.value).id
        val r = SyncRig("foreground-gate", folder.newFolder(), world, account, channel, 71)
        try {
            val service = TdlibPlaylistSyncService(r.tdlib.core, r.device.session, SyncGate(true, r.consentStore), isActive = { false })
            val before = r.tdlib.gateway.requests.size
            assertEquals(Gated.Denied(SyncDenied.NOT_OPEN), service.discoverChannel(account))
            assertEquals(Gated.Denied(SyncDenied.NOT_OPEN), service.runSync(account, sendBudget = 3))
            assertEquals(before, r.tdlib.gateway.requests.size)
        } finally { r.stop() }
    }

    @Test fun pausingDuringHistoryDoesNotApplyLateRemoteChangesOrSendLocalPending() {
        val account = TelegramUserId(1001)
        val world = FakeTdlibWorld()
        val channel = world.createSyncChannel(account.value).id
        val r = SyncRig("cancel-fetch", folder.newFolder(), world, account, channel, 72)
        try {
            var foreground = true
            val service = TdlibPlaylistSyncService(r.tdlib.core, r.device.session, SyncGate(true, r.consentStore),
                timeouts = TdlibTransportTimeouts(requestMs = 2_000, sendAckMs = 400), isActive = { foreground })
            r.device.toggle(account.value, songId(1))
            world.post(channel, envelopeText(account.value, "remote", addOp("remote", 1, account.value, songId(2))))
            val before = r.device.stored()
            r.tdlib.gateway.beforeHistory = {
                foreground = false
                service.cancel()
                // Even a quick return to foreground does not revive the old run's cancellation token.
                foreground = true
            }
            val result = service.runSync(account, sendBudget = 3)
            assertNotEquals(SyncStatus.OK, (result as? Gated.Done)?.value?.status)
            assertEquals(before, r.device.stored())
            assertEquals(0, r.tdlib.gateway.count("sendMessage"))
        } finally { r.stop() }
    }
}
