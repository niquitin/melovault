package com.bsdung.musicassistantcar.lyrics

enum class LyricsStatus {
    SYNCED,
    PLAIN,
    INSTRUMENTAL,
    AMBIGUOUS,
    NOT_FOUND,
    TEMPORARY_ERROR,
    ;

    companion object {
        fun fromWire(value: String?): LyricsStatus {
            return when (value?.trim()?.uppercase()) {
                "SYNCED" -> SYNCED
                "PLAIN" -> PLAIN
                "INSTRUMENTAL" -> INSTRUMENTAL
                "AMBIGUOUS" -> AMBIGUOUS
                "NOT_FOUND" -> NOT_FOUND
                "TEMPORARY_ERROR" -> TEMPORARY_ERROR
                else -> TEMPORARY_ERROR
            }
        }
    }
}

enum class LyricsProvenance {
    AUTO,
    MANUAL,
    ;

    val badgeText: String
        get() = displayBadge()

    fun displayBadge(provider: String = "LRCLIB"): String {
        val cleanProvider = provider.trim().ifBlank { "LRCLIB" }.uppercase()
        return when (this) {
            AUTO -> "$cleanProvider · Tự động"
            MANUAL -> "$cleanProvider · Đã chọn thủ công"
        }
    }
}

data class LyricsLine(
    val startMs: Long,
    val text: String,
)

data class LyricsCandidate(
    val provider: String,
    val recordId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long? = null,
    val hasSynced: Boolean = false,
    val hasPlain: Boolean = true,
    val instrumental: Boolean = false,
    val confidence: Double = 0.0,
    val matchScore: Double = confidence * 100.0,
    val variantConflict: Boolean = false,
    val durationConflict: Boolean = false,
    val hasPlainLyrics: Boolean = hasPlain,
) {
    val providerRecordId: String
        get() = recordId

    val candidateId: String
        get() = "${provider.trim().lowercase().ifEmpty { "lrclib" }}:$recordId"

    fun badgeText(): String = when {
        hasSynced -> "Đồng bộ"
        instrumental -> "Nhạc cụ"
        else -> "Văn bản"
    }
}

data class LyricsCandidatesResult(
    val status: String = "OK",
    val provider: String = "lrclib",
    val candidates: List<LyricsCandidate> = emptyList(),
    val diagnostic: String? = null,
) {
    val errorMessage: String?
        get() = diagnostic
}

sealed class LyricsCandidatePickerState {
    object Hidden : LyricsCandidatePickerState()

    data class Loading(
        val trackKey: String = "",
        val title: String = "",
        val artist: String = "",
    ) : LyricsCandidatePickerState()

    data class Loaded(
        val trackKey: String = "",
        val candidates: List<LyricsCandidate>,
        val selectedRecordId: String? = null,
        val activeRecordId: String? = selectedRecordId,
    ) : LyricsCandidatePickerState()

    data class Empty(
        val trackKey: String = "",
        val message: String = "Không tìm thấy kết quả phù hợp",
    ) : LyricsCandidatePickerState()

    data class Error(
        val trackKey: String = "",
        val message: String = "Không thể tải danh sách lời bài hát",
        val diagnostic: String? = null,
    ) : LyricsCandidatePickerState()

    companion object {
        val Closed: Hidden
            get() = Hidden
    }
}

data class LyricsMatchedTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long? = null,
)

data class LyricsTrackMetadata(
    val trackKey: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long? = null,
    val isDeviceLocal: Boolean = false,
) {
    fun hasLookupMetadata(): Boolean {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank()) return false
        val invalidTitles = setOf("chưa xác định", "unknown", "unknown title", "<unknown>")
        if (cleanTitle.lowercase() in invalidTitles) return false
        if (isDeviceLocal) {
            val invalidArtists = setOf("", "chưa xác định", "unknown", "unknown artist", "<unknown>", "thiết bị")
            if (cleanArtist.lowercase() in invalidArtists) return false
        }
        return true
    }

    fun cacheSignature(): String = LyricsCache.computeMetadataSignature(
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
    )
}

data class LyricsLookupResult(
    val status: LyricsStatus,
    val provider: String = "lrclib",
    val providerRecordId: String? = null,
    val confidence: Double = 0.0,
    val matchedTrack: LyricsMatchedTrack? = null,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val syncedLines: List<LyricsLine> = emptyList(),
    val instrumental: Boolean = false,
    val candidates: List<LyricsCandidate> = emptyList(),
    val diagnostic: String? = null,
    val fromCache: Boolean = false,
    val provenance: LyricsProvenance = LyricsProvenance.AUTO,
)

sealed class LyricsUiState {
    object Idle : LyricsUiState()

    data class Loading(
        val title: String,
        val artist: String,
    ) : LyricsUiState()

    data class Synced(
        val lines: List<LyricsLine>,
        val activeLineIndex: Int = -1,
        val autoFollow: Boolean = true,
        val provider: String = "lrclib",
        val providerRecordId: String? = null,
        val fromCache: Boolean = false,
        val provenance: LyricsProvenance = LyricsProvenance.AUTO,
    ) : LyricsUiState() {
        val recordId: String?
            get() = providerRecordId
    }

    data class Plain(
        val text: String,
        val provider: String = "lrclib",
        val providerRecordId: String? = null,
        val fromCache: Boolean = false,
        val provenance: LyricsProvenance = LyricsProvenance.AUTO,
    ) : LyricsUiState() {
        val recordId: String?
            get() = providerRecordId
    }

    data class Instrumental(
        val provider: String = "lrclib",
        val fromCache: Boolean = false,
        val provenance: LyricsProvenance = LyricsProvenance.AUTO,
    ) : LyricsUiState()

    data class Ambiguous(
        val candidates: List<LyricsCandidate>,
        val provider: String = "lrclib",
    ) : LyricsUiState()

    data class NotFound(
        val provider: String = "lrclib",
    ) : LyricsUiState()

    data class TemporaryError(
        val message: String = "Không thể tải lời bài hát",
        val diagnostic: String? = null,
    ) : LyricsUiState()
}
