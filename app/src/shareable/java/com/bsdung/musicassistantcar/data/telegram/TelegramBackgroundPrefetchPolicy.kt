package com.bsdung.musicassistantcar.data.telegram

/**
 * Bounds background TDLib prefetch when network responses are delayed or lost.
 * IDs and authorization must be checked again on EVERY attempt and response.
 */
internal object TelegramBackgroundPrefetchPolicy {
    const val MAX_ATTEMPTS = 2
    const val TIMEOUT_MS = 20_000L
    const val RETRY_DELAY_MS = 1_000L

    fun canRetry(
        completedAttempt: Int,
        expectedAccount: Long?,
        verifiedAccount: Long?,
        pendingMessageId: Long,
        nextMessageId: Long?
    ): Boolean = completedAttempt in 1 until MAX_ATTEMPTS &&
        expectedAccount != null && expectedAccount > 0L &&
        expectedAccount == verifiedAccount &&
        pendingMessageId > 0L && pendingMessageId == nextMessageId

    fun isCurrentResult(
        pendingFileId: Int?,
        resultFileId: Int,
        expectedAccount: Long?,
        verifiedAccount: Long?,
        pendingMessageId: Long?,
        nextMessageId: Long?
    ): Boolean = pendingFileId != null && pendingFileId > 0 &&
        pendingFileId == resultFileId &&
        expectedAccount != null && expectedAccount > 0L &&
        expectedAccount == verifiedAccount &&
        pendingMessageId != null && pendingMessageId > 0L &&
        pendingMessageId == nextMessageId
}
