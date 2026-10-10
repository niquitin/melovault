package com.bsdung.musicassistantcar.playback

import androidx.media3.common.PlaybackException

/**
 * Pure policy for gating network playback recovery attempts.
 * Prevents recovery storms, protects against service shutdown,
 * and differentiates recoverable network errors from fatal errors.
 */
object NetworkRecoveryPolicy {
    const val NETWORK_RECOVERY_COOLDOWN_MS = 5_000L

    fun isRecoverableNetworkError(errorCode: Int): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
        else -> false
    }

    fun isRecoverableNetworkError(error: PlaybackException): Boolean =
        isRecoverableNetworkError(error.errorCode)

    fun canAttemptRecovery(
        isShuttingDown: Boolean,
        isPlayerInitialized: Boolean,
        queueSize: Int,
        networkRecoveryInProgress: Boolean,
        nowMs: Long,
        lastNetworkRecoveryAttemptMs: Long,
        errorCode: Int,
    ): Boolean {
        if (isShuttingDown || !isPlayerInitialized || queueSize <= 0) {
            return false
        }
        if (!isRecoverableNetworkError(errorCode)) {
            return false
        }
        if (networkRecoveryInProgress) {
            return false
        }
        if (nowMs - lastNetworkRecoveryAttemptMs < NETWORK_RECOVERY_COOLDOWN_MS) {
            return false
        }
        return true
    }
}
