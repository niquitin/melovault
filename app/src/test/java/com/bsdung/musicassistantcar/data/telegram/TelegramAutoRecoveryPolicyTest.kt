package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TelegramAutoRecoveryPolicy (E9.1-T.2).
 * Verifies auto-recovery eligibility, one-shot guards, account isolation,
 * duplicate scan prevention, and tab-triggered recovery.
 */
class TelegramAutoRecoveryPolicyTest {

    private lateinit var policy: TelegramAutoRecoveryPolicy

    @Before
    fun setUp() {
        policy = TelegramAutoRecoveryPolicy()
    }

    @Test
    fun authNotReady_ineligibleForAutoRecovery() {
        assertFalse(policy.shouldAutoRecover(
            authReady = false,
            allChatsMode = true,
            isScanning = false,
            songCount = 0,
            accountId = 1001L
        ))
    }

    @Test
    fun savedMessagesMode_ineligibleForAutoRecovery() {
        // Saved messages view must not trigger global all-chats recovery
        assertFalse(policy.shouldAutoRecover(
            authReady = true,
            allChatsMode = false,
            isScanning = false,
            songCount = 0,
            accountId = 1001L
        ))
    }

    @Test
    fun scanAlreadyInProgress_ineligible() {
        assertFalse(policy.shouldAutoRecover(
            authReady = true,
            allChatsMode = true,
            isScanning = true,
            songCount = 0,
            accountId = 1001L
        ))
    }

    @Test
    fun existingSongsInSnapshot_ineligible() {
        // Already has tracks, no recovery scan needed
        assertFalse(policy.shouldAutoRecover(
            authReady = true,
            allChatsMode = true,
            isScanning = false,
            songCount = 1589,
            accountId = 1001L
        ))
    }

    @Test
    fun nullOrInvalidAccountId_ineligible() {
        assertFalse(policy.shouldAutoRecover(
            authReady = true,
            allChatsMode = true,
            isScanning = false,
            songCount = 0,
            accountId = null
        ))
        assertFalse(policy.shouldAutoRecover(
            authReady = true,
            allChatsMode = true,
            isScanning = false,
            songCount = 0,
            accountId = 0L
        ))
        assertFalse(policy.shouldAutoRecover(
            authReady = true,
            allChatsMode = true,
            isScanning = false,
            songCount = 0,
            accountId = -5L
        ))
    }

    @Test
    fun restoredAllChats_emptySnapshot_eligible() {
        // Core scenario: Activity restored with allChatsMode=true, authReady=true, but 0 songs in RAM
        assertTrue(policy.shouldAutoRecover(
            authReady = true,
            allChatsMode = true,
            isScanning = false,
            songCount = 0,
            accountId = 1001L
        ))
    }

    @Test
    fun dispatchedScan_blocksRepeatedAutoRecovery() {
        val account = 1001L
        assertTrue(policy.shouldAutoRecover(true, true, false, 0, account))

        policy.recordScanDispatched(account)
        assertTrue(policy.isScanAttempted)
        assertEquals(account, policy.activeAccountId)

        // Immediate subsequent check must be blocked
        assertFalse(policy.shouldAutoRecover(true, true, false, 0, account))
    }

    @Test
    fun genuineEmptyLibrary_oneShotGuardedAgainstInfiniteLoop() {
        val account = 1001L
        policy.recordScanDispatched(account)

        // Scan completes and genuinely finds 0 songs
        policy.recordScanCompleted(0)
        assertTrue(policy.isScanCompletedEmpty)

        // Must not trigger infinite re-scans
        assertFalse(policy.shouldAutoRecover(true, true, false, 0, account))
        assertFalse(policy.shouldTabTriggerScan(true, true, false, 0, account))
    }

    @Test
    fun manualScanRequest_resetsOneShotGuard() {
        val account = 1001L
        policy.recordScanDispatched(account)
        policy.recordScanCompleted(0)
        assertFalse(policy.shouldAutoRecover(true, true, false, 0, account))

        // User manually taps "Tìm nhạc mọi chat Telegram"
        policy.onManualScanRequested()
        assertFalse(policy.isScanAttempted)
        assertFalse(policy.isScanCompletedEmpty)

        // Now eligible again
        assertTrue(policy.shouldAutoRecover(true, true, false, 0, account))
    }

    @Test
    fun successfulScan_allowsFutureRecoveryIfSongsClearedOnActivityRecreate() {
        val account = 1001L
        policy.recordScanDispatched(account)

        // Scan completes successfully with 1589 songs
        policy.recordScanCompleted(1589)
        assertFalse(policy.isScanCompletedEmpty)
        assertFalse(policy.isScanAttempted)

        // If activity is recreated and songs memory is wiped back to 0:
        assertTrue(policy.shouldAutoRecover(true, true, false, 0, account))
    }

    @Test
    fun accountSwitch_resetsGuardsForNewAccount() {
        val accountA = 1001L
        val accountB = 2002L

        policy.recordScanDispatched(accountA)
        policy.recordScanCompleted(0)
        assertFalse(policy.shouldAutoRecover(true, true, false, 0, accountA))

        // New account logs in
        assertTrue(policy.shouldAutoRecover(true, true, false, 0, accountB))
        assertEquals(accountB, policy.activeAccountId)
        assertFalse(policy.isScanCompletedEmpty)
    }

    @Test
    fun tabTriggerScan_switchingFromSavedMessages() {
        // Tapping library tab while in Saved Messages mode (!allChatsMode) always triggers scan
        assertTrue(policy.shouldTabTriggerScan(
            authReady = true,
            allChatsMode = false,
            isScanning = false,
            songCount = 0
        ))
    }

    @Test
    fun tabTriggerScan_withEmptySnapshot_firstTapEligible() {
        // In allChatsMode with 0 songs: tab tap should trigger recovery scan
        assertTrue(policy.shouldTabTriggerScan(
            authReady = true,
            allChatsMode = true,
            isScanning = false,
            songCount = 0,
            accountId = 1001L
        ))
    }

    @Test
    fun tabTriggerScan_blockedWhileScanning() {
        assertFalse(policy.shouldTabTriggerScan(
            authReady = true,
            allChatsMode = true,
            isScanning = true,
            songCount = 0,
            accountId = 1001L
        ))
    }

    @Test
    fun tabTriggerScan_withExistingSongs_doesNotScan() {
        // When songs already exist, tab tap only refreshes rows, no TDLib scan
        assertFalse(policy.shouldTabTriggerScan(
            authReady = true,
            allChatsMode = true,
            isScanning = false,
            songCount = 500,
            accountId = 1001L
        ))
    }

    @Test
    fun reset_clearsAllState() {
        policy.recordScanDispatched(1001L)
        policy.recordScanCompleted(0)

        policy.reset()
        assertNull(policy.activeAccountId)
        assertFalse(policy.isScanAttempted)
        assertFalse(policy.isScanCompletedEmpty)
    }
}
