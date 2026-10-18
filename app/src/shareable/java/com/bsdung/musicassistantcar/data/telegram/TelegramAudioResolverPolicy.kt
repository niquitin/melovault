package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject

/**
 * Pure policy and parser for resolving Telegram audio messages to genuine,
 * session-scoped file objects and download/local playback paths.
 *
 * TDLib dynamically assigns file_id integers within each native client session.
 * This policy extracts the session-valid file_id directly from the message object
 * retrieved via getMessage, ensuring stale IDs from prior app sessions are never
 * used for playback or prefetch.
 */
internal object TelegramAudioResolverPolicy {

    sealed class Result {
        data class Resolved(
            val freshFileId: Int,
            val isDownloadingCompleted: Boolean,
            val localPath: String,
            val size: Long,
            val title: String,
            val filename: String,
            val mimeType: String?,
        ) : Result()

        /** [code] is the TDLib error code when TDLib refused the request, null for the app's own errors. */
        data class Error(val message: String, val code: Int? = null) : Result()
    }

    private val AUDIO_EXTENSIONS = setOf(
        ".mp3", ".flac", ".wav", ".m4a", ".ogg", ".opus", ".aac"
    )

    fun parseMessage(messageJson: JSONObject): Result {
        val kind = messageJson.optString("@type")
        if (kind != "message") {
            return Result.Error("Đối tượng TDLib không phải tin nhắn (@type=$kind)")
        }
        val content = messageJson.optJSONObject("content")
            ?: return Result.Error("Tin nhắn không có nội dung")
        val mediaType = content.optString("@type")
        val attachment = when (mediaType) {
            "messageAudio" -> content.optJSONObject("audio")
            "messageDocument" -> content.optJSONObject("document")
            else -> return Result.Error("Nội dung tin nhắn không phải âm thanh hay tài liệu (@type=$mediaType)")
        } ?: return Result.Error("Không tìm thấy thông tin tệp đính kèm trong tin nhắn")

        val filename = attachment.optString("file_name", "audio")
        val title = attachment.optString("title").ifBlank { filename }

        if (mediaType != "messageAudio") {
            val hasAudioExt = AUDIO_EXTENSIONS.any { filename.endsWith(it, ignoreCase = true) }
            if (!hasAudioExt) {
                return Result.Error("Tệp tài liệu '$filename' không phải định dạng âm thanh hỗ trợ")
            }
        }

        val fileObj = attachment.optJSONObject(if (mediaType == "messageAudio") "audio" else "document")
            ?: return Result.Error("Không tìm thấy đối tượng tệp trong nội dung")

        val fileId = fileObj.optInt("id")
        if (fileId <= 0) {
            return Result.Error("file_id của phiên hiện tại không hợp lệ ($fileId)")
        }

        val local = fileObj.optJSONObject("local")
        val isCompleted = local?.optBoolean("is_downloading_completed") == true
        val path = local?.optString("path").orEmpty()
        val size = fileObj.optLong("size").takeIf { it > 0 }
            ?: fileObj.optLong("expected_size").takeIf { it > 0 }
            ?: -1L
        val mimeType = attachment.optString("mime_type").takeIf { it.isNotBlank() }

        return Result.Resolved(
            freshFileId = fileId,
            isDownloadingCompleted = isCompleted,
            localPath = path,
            size = size,
            title = title,
            filename = filename,
            mimeType = mimeType
        )
    }
}
