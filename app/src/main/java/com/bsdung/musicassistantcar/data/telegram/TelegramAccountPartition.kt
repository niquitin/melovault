package com.bsdung.musicassistantcar.data.telegram

import java.io.File
import java.security.MessageDigest

/**
 * Only a Telegram user ID returned after TDLib authorizationStateReady + getMe()
 * may be used as the account identity. Phone numbers and usernames can change.
 *
 * This class does NOT authenticate users or communicate with Telegram.
 */
@JvmInline
value class TelegramUserId(val value: Long) {
    init { require(value > 0L) { "Expected a positive authenticated Telegram user ID" } }
}

/**
 * Stable opaque account-scoped storage names, no phone, username or source ID
 * exposed in directory names. App-private files and no-backup session storage.
 */
object TelegramAccountStoragePolicy {
    fun directoryName(userId: TelegramUserId): String {
        val input = "melovault:telegram:user:v1:" + userId.value
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return "tg-" + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /**
     * A fresh TDLib client needs its database directory before user identity
     * exists. Allocate a cryptographically unpredictable slot and bind it to
     * getMe()-verified TelegramUserId only after authorization is complete.
     * Persist the binding only in encrypted app-private storage.
     */
    fun newSessionSlotId(): String = java.util.UUID.randomUUID().toString()

    fun sessionSlotDirectory(noBackupFiles: File, slotId: String): File {
        require(slotId.matches(
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        )) { "Invalid Telegram session slot" }
        return File(File(noBackupFiles, "telegram_session_slots"), "slot-$slotId")
    }

    /** Only for already verified, account-bound sessions; not initial login. */
    fun sessionDirectory(noBackupFiles: File, userId: TelegramUserId): File =
        File(File(noBackupFiles, "telegram_sessions"), directoryName(userId))

    fun downloadDirectory(appPrivateFiles: File, userId: TelegramUserId): File =
        File(File(appPrivateFiles, "telegram_downloads"), directoryName(userId))

    fun streamCacheDirectory(appPrivateCache: File, userId: TelegramUserId): File =
        File(File(appPrivateCache, "telegram_stream_cache"), directoryName(userId))

    /**
     * Never trust file paths supplied from remote Telegram message data.
     * Only a path inside the selected account's dedicated private root is valid.
     */
    fun isOwnedFile(root: File, candidate: File): Boolean = runCatching {
        val base = root.canonicalFile
        val target = candidate.canonicalFile
        target != base && target.toPath().startsWith(base.toPath())
    }.getOrDefault(false)
}

/** Contains no phone, token, password or backend endpoint. */
data class TelegramAudioEntry(
    val owner: TelegramUserId,
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val format: String,
) {
    init {
        require(chatId != 0L)
        require(messageId > 0L)
    }
}

class TelegramAccountLibrary {
    private var signedInAs: TelegramUserId? = null
    private val byAccount = mutableMapOf<TelegramUserId, MutableMap<Pair<Long, Long>, TelegramAudioEntry>>()

    /**
     * Native Telegram adapter must invoke only after authorizationStateReady
     * and getMe() for the current session. Never take UI-entered account IDs.
     */
    fun onAuthenticatedUser(userId: TelegramUserId) {
        signedInAs = userId
    }

    fun logout() { signedInAs = null }
    fun authorizedUser(): TelegramUserId? = signedInAs

    fun accept(entry: TelegramAudioEntry) {
        val userId = checkNotNull(signedInAs) { "Telegram sign-in required" }
        require(entry.owner == userId) { "Cross-account media rejected" }
        byAccount.getOrPut(userId) { mutableMapOf() }[
            Pair(entry.chatId, entry.messageId)
        ] = entry
    }

    fun currentItems(): List<TelegramAudioEntry> {
        val userId = signedInAs ?: return emptyList()
        return byAccount[userId]?.values?.toList().orEmpty()
    }

    fun mayOpenLocalFile(file: File, appFiles: File): Boolean {
        val userId = signedInAs ?: return false
        val ownedDir = TelegramAccountStoragePolicy.downloadDirectory(appFiles, userId)
        return TelegramAccountStoragePolicy.isOwnedFile(ownedDir, file)
    }
}
