package com.bsdung.musicassistantcar.data.telegram

/**
 * Deterministic policy controlling automatic recovery scan of Telegram music sources.
 *
 * Requirements (E9.1-T.2):
 * 1. Automatic non-blocking re-scan if auth READY, allChatsMode active/restored,
 *    and in-memory snapshot is empty (0 songs).
 * 2. Guard against infinite loops: at most one auto-recovery attempt per authenticated
 *    account session (completed empty scan prevents further auto-rescans).
 * 3. Never trigger duplicate concurrent scans (if isScanning is true).
 * 4. Never trigger scan for Saved Messages-only mode (!allChatsMode) unless explicitly requested.
 * 5. Account isolation: account change or explicit logout resets the attempt guard.
 * 6. Explicit manual scan / refresh resets the attempt guard so manual refresh is always permitted.
 */
internal class TelegramAutoRecoveryPolicy {

    private var currentAccountId: Long? = null
    private var scanAttempted: Boolean = false
    private var scanCompletedEmpty: Boolean = false

    /**
     * Determine whether an automatic recovery scan should be triggered from lifecycle transitions
     * (e.g. after auth ready, activity resume/restore).
     *
     * @param authReady true if TDLib is fully authenticated and ready
     * @param allChatsMode true if user/savedState is in all-chats mode (not Saved Messages)
     * @param isScanning true if a scan is already running (globalScanLoading)
     * @param songCount current number of in-memory tracks
     * @param accountId currently authenticated Telegram user ID (if any)
     */
    fun shouldAutoRecover(
        authReady: Boolean,
        allChatsMode: Boolean,
        isScanning: Boolean,
        songCount: Int,
        accountId: Long?
    ): Boolean {
        if (!authReady || !allChatsMode || isScanning || songCount > 0) {
            return false
        }
        if (accountId == null || accountId <= 0L) {
            return false
        }

        // Account switch resets state
        if (accountId != currentAccountId) {
            currentAccountId = accountId
            scanAttempted = false
            scanCompletedEmpty = false
        }

        if (scanCompletedEmpty || scanAttempted) return false
        return true
    }

    /**
     * Checks if tapping a navigation tab ("Thư viện" or "Nguồn Telegram") should trigger a scan.
     *
     * - If !allChatsMode: switching into allChats mode always triggers a scan (existing behavior).
     * - If allChatsMode is already true: triggers scan only if snapshot is empty,
     *   no scan is in progress, and scan has not already failed/completed empty this session.
     */
    fun shouldTabTriggerScan(
        authReady: Boolean,
        allChatsMode: Boolean,
        isScanning: Boolean,
        songCount: Int,
        accountId: Long? = null
    ): Boolean {
        if (!authReady || isScanning) return false
        if (!allChatsMode) return true // Switching from Saved Messages into all-chats triggers scan
        if (songCount > 0) return false

        if (accountId != null && accountId > 0L && accountId != currentAccountId) {
            currentAccountId = accountId
            scanAttempted = false
            scanCompletedEmpty = false
        }

        if (scanCompletedEmpty || scanAttempted) return false
        return true
    }

    /** Record that a scan has been dispatched for this account. */
    fun recordScanDispatched(accountId: Long) {
        currentAccountId = accountId
        scanAttempted = true
    }

    /**
     * Record the completion of a scan with the resulting song count.
     * If the resulting song count is 0, record scanCompletedEmpty = true
     * so that we do not enter an infinite loop on truly empty accounts.
     */
    fun recordScanCompleted(songCount: Int) {
        if (songCount == 0) {
            scanCompletedEmpty = true
        } else {
            scanCompletedEmpty = false
            scanAttempted = false // Successful snapshot allows recovery if songs are later lost
        }
    }

    /** Explicit user-initiated refresh or account logout resets all guards. */
    fun reset() {
        currentAccountId = null
        scanAttempted = false
        scanCompletedEmpty = false
    }

    /** Reset attempt guard when user explicitly invokes manual scan. */
    fun onManualScanRequested() {
        scanAttempted = false
        scanCompletedEmpty = false
    }

    // Diagnostics / state inspection for unit tests
    val isScanAttempted: Boolean get() = scanAttempted
    val isScanCompletedEmpty: Boolean get() = scanCompletedEmpty
    val activeAccountId: Long? get() = currentAccountId
}
