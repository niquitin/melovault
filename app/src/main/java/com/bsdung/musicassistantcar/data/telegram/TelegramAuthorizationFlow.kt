package com.bsdung.musicassistantcar.data.telegram

/**
 * TDLib-independent authorization state reducer.
 * Native callback implementation will adapt official UpdateAuthorizationState
 * variants to these steps. This class never handles OTP/2FA contents and never
 * considers a typed phone number or an app's API ID to be user authorization.
 */
enum class TelegramAuthStep {
    NO_CLIENT,
    WAIT_PARAMETERS,
    WAIT_PHONE_NUMBER,
    WAIT_EMAIL_ADDRESS,
    WAIT_EMAIL_CODE,
    WAIT_CODE,
    WAIT_PASSWORD,
    WAIT_OTHER_DEVICE_CONFIRMATION,
    WAIT_REGISTRATION,
    AUTHORIZED_AWAITING_GET_ME,
    AUTHORIZED,
    LOGGING_OUT,
    CLOSED,
    ERROR,
}

class TelegramAuthorizationFlow(
    private val library: TelegramAccountLibrary,
    private val epochs: TelegramSessionEpoch = TelegramSessionEpoch(library),
) {
    var step: TelegramAuthStep = TelegramAuthStep.NO_CLIENT
        private set

    private var currentEpoch = 0L
    private var open = false

    fun startClient(): Long {
        currentEpoch = epochs.beginNewClient()
        open = true
        step = TelegramAuthStep.WAIT_PARAMETERS
        return currentEpoch
    }

    /**
     * Refuse stale native notifications and arbitrary READY signals.
     * READY must be proven via TDLib getMe() for this exact client epoch.
     */
    fun onNativeState(epoch: Long, state: TelegramAuthStep): Boolean {
        if (!open || epoch != currentEpoch) return false
        if (state == TelegramAuthStep.AUTHORIZED ||
            state == TelegramAuthStep.NO_CLIENT) return false
        if (state == TelegramAuthStep.CLOSED ||
            state == TelegramAuthStep.LOGGING_OUT ||
            state == TelegramAuthStep.ERROR) {
            epochs.closeClient(epoch)
            open = false
            step = state
            return true
        }
        // A fresh auth step clears any old authorized state and session.
        // The native TDLib adapter will create a new epoch when switching users.
        if (step == TelegramAuthStep.AUTHORIZED) return false
        step = state
        return true
    }

    fun onGetMe(epoch: Long, verifiedUser: TelegramUserId): Boolean {
        if (!open || epoch != currentEpoch ||
            step != TelegramAuthStep.AUTHORIZED_AWAITING_GET_ME) return false
        if (!epochs.onAuthenticatedGetMe(epoch, verifiedUser)) return false
        step = TelegramAuthStep.AUTHORIZED
        return true
    }

    fun isReady(): Boolean =
        open && step == TelegramAuthStep.AUTHORIZED && epochs.isReady(currentEpoch)

    fun logout() {
        if (open) epochs.closeClient(currentEpoch)
        open = false
        step = TelegramAuthStep.NO_CLIENT
    }
}
