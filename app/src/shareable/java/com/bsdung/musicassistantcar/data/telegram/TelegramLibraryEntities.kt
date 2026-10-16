package com.bsdung.musicassistantcar.data.telegram

/**
 * Domain entity representing an indexed Telegram audio track.
 * Identified by the composite primary key (accountId, chatId, messageId).
 */
data class IndexedTelegramSong(
    val accountId: Long,
    val chatId: Long,
    val messageId: Long,
    val songKey: Long,
    val fileId: Int,
    val title: String,
    val filename: String,
    val performer: String? = null,
    val durationSeconds: Int = 0,
    val fileSize: Long = -1L,
    val mimeType: String? = null,
    val mediaType: String = "audio",
    val date: Long = 0L,
    val lastSeenAtMs: Long = System.currentTimeMillis(),
    val sourceTitle: String? = null,
    val isDeleted: Boolean = false,
) {
    init {
        require(accountId > 0L) { "Invalid Telegram account ID: $accountId" }
        require(chatId != 0L) { "Invalid chat ID: $chatId" }
        require(messageId > 0L) { "Invalid message ID: $messageId" }
    }
}

/**
 * Domain entity representing a discovered Telegram source (channel, group, or chat).
 */
data class IndexedTelegramSource(
    val accountId: Long,
    val chatId: Long,
    val title: String,
    val type: String = "unknown",
    val trackCount: Int = 0,
    val lastUpdatedAtMs: Long = System.currentTimeMillis(),
) {
    init {
        require(accountId > 0L) { "Invalid Telegram account ID: $accountId" }
        require(chatId != 0L) { "Invalid chat ID: $chatId" }
    }
}

/**
 * Checkpoint and cursor state for Telegram library synchronization.
 */
data class TelegramSyncState(
    val accountId: Long,
    val syncScope: String = SCOPE_GLOBAL,
    val phase: String = PHASE_IDLE,
    val nextOffset: String = "",
    val pagesSynced: Int = 0,
    val totalItemsSynced: Int = 0,
    val status: String = STATUS_IDLE,
    val highWaterDate: Long = 0L,
    val lastSyncCompletedAtMs: Long = 0L,
    val lastErrorMessage: String? = null,
) {
    companion object {
        const val SCOPE_GLOBAL = "global"
        const val SCOPE_SAVED = "saved"

        const val PHASE_IDLE = "idle"
        const val PHASE_AUDIO = "audio"
        const val PHASE_DOCUMENT = "document"

        const val STATUS_IDLE = "idle"
        const val STATUS_SYNCING = "syncing"
        const val STATUS_PAUSED = "paused"
        const val STATUS_PARTIAL = "partial"
        const val STATUS_COMPLETE = "complete"
        const val STATUS_ERROR = "error"
    }

    init {
        require(accountId > 0L) { "Invalid Telegram account ID: $accountId" }
    }
}
