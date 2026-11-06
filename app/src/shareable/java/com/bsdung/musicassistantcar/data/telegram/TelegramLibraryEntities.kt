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
    /** Unknown for legacy rows or messages without forum metadata. Never part of song identity. */
    val forumTopicId: Int? = null,
) {
    init {
        require(accountId > 0L) { "Invalid Telegram account ID: $accountId" }
        require(chatId != 0L) { "Invalid chat ID: $chatId" }
        require(messageId > 0L) { "Invalid message ID: $messageId" }
        require(forumTopicId == null || forumTopicId > 0)
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
    /** null = not checked; false/true only after a confirmed supergroup response. */
    val isForum: Boolean? = null,
) {
    init {
        require(accountId > 0L) { "Invalid Telegram account ID: $accountId" }
        require(chatId != 0L) { "Invalid chat ID: $chatId" }
    }
}

data class IndexedTelegramForumTopic(
    val accountId: Long, val chatId: Long, val forumTopicId: Int, val title: String,
    val isGeneral: Boolean = false, val isHidden: Boolean = false, val isClosed: Boolean = false,
    val isPinned: Boolean = false, val order: Long = 0L,
    val lastUpdatedAtMs: Long = System.currentTimeMillis(),
) {
    init { require(accountId > 0L && chatId != 0L && forumTopicId > 0) }
}

/** Independent older-history and head-refresh cursors. Neither changes the global scan checkpoint. */
data class TelegramForumHistoryState(
    val accountId: Long, val chatId: Long, val forumTopicId: Int,
    val olderCursor: Long = 0L, val olderComplete: Boolean = false, val newestMessageId: Long = 0L,
    val refreshCursor: Long = 0L, val refreshUntilMessageId: Long = 0L, val refreshPending: Boolean = false,
    val retryAfterMs: Long = 0L,
) {
    init {
        require(accountId > 0L && chatId != 0L && forumTopicId > 0)
        require(olderCursor >= 0L && newestMessageId >= 0L && refreshCursor >= 0L && refreshUntilMessageId >= 0L)
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
