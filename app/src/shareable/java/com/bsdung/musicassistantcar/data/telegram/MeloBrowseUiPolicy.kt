package com.bsdung.musicassistantcar.data.telegram

/** Presentation only: no queries, playback, download or persistent browse state. */
internal object MeloBrowseUiPolicy {
    enum class Kind { LIBRARY, CHANNEL, DOWNLOADED, PLAYLIST, SOURCES, SAVED_MESSAGES, LOCAL }
    enum class LoadState { INITIAL_LOADING, UPDATING, READY, ERROR }

    /** Titles are deliberately excluded from identity (two channels may have the same name). */
    data class Scope(val kind: Kind, val chatId: Long? = null, val downloadedOnly: Boolean = false) {
        init {
            require(kind != Kind.CHANNEL || chatId != null)
            require(chatId != 0L)
            require(chatId == null || kind == Kind.CHANNEL || kind == Kind.DOWNLOADED)
            require(!downloadedOnly || kind == Kind.PLAYLIST)
        }
    }

    data class Request(val ownerId: Long, val generation: Long, val scope: Scope)
    data class Presentation(val hint: String, val scopeLabel: String, val message: String?, val retry: Boolean)

    fun scope(
        sources: Boolean, playlist: Boolean, downloaded: Boolean, allChats: Boolean, chatId: Long?
    ): Scope = when {
        sources -> Scope(Kind.SOURCES)
        playlist -> Scope(Kind.PLAYLIST, downloadedOnly = downloaded)
        downloaded -> Scope(Kind.DOWNLOADED, chatId.takeIf { allChats })
        !allChats -> Scope(Kind.SAVED_MESSAGES)
        chatId != null -> Scope(Kind.CHANNEL, chatId)
        else -> Scope(Kind.LIBRARY)
    }

    fun accepts(request: Request?, ownerId: Long, generation: Long, active: Boolean): Boolean =
        active && ownerId > 0L && request?.ownerId == ownerId && request.generation == generation

    fun canKeepRows(previous: Request?, next: Request): Boolean =
        previous?.ownerId == next.ownerId && previous.scope == next.scope

    fun loading(hasRows: Boolean): LoadState = if (hasRows) LoadState.UPDATING else LoadState.INITIAL_LOADING

    /** A count belongs to a completed query, never to its empty initial buffer or a partial scan. */
    fun authoritativeCount(state: LoadState, count: Int?): Int? =
        count?.takeIf { state == LoadState.READY && it >= 0 }

    fun present(scope: Scope, title: String?, state: LoadState, rowCount: Int, query: String): Presentation {
        val channel = title?.trim()?.takeIf { it.isNotEmpty() }?.let { "kênh $it" } ?: "kênh đã chọn"
        val where = when (scope.kind) {
            Kind.LIBRARY -> "thư viện đã đồng bộ"
            Kind.CHANNEL -> channel
            Kind.DOWNLOADED -> if (scope.chatId != null) "Đã tải · $channel" else "Đã tải"
            Kind.PLAYLIST -> if (scope.downloadedOnly) "Playlist · Đã tải" else "Playlist"
            Kind.SOURCES -> "nguồn Telegram"
            Kind.SAVED_MESSAGES -> "Tin đã lưu"
            Kind.LOCAL -> "Nhạc trên máy"
        }
        val hint = when (scope.kind) {
            Kind.SOURCES -> "Tìm nguồn Telegram…"
            Kind.DOWNLOADED -> "Tìm trong Đã tải…"
            else -> "Tìm trong $where…"
        }
        val scanning = scope.kind == Kind.DOWNLOADED || scope.downloadedOnly
        val message = when (state) {
            LoadState.INITIAL_LOADING, LoadState.UPDATING -> when {
                scanning -> "Đang kiểm tra tệp trên máy…"
                state == LoadState.UPDATING -> "Đang cập nhật danh sách…"
                else -> "Đang tải danh sách…"
            }
            LoadState.ERROR -> "Chưa tải được danh sách. Thử lại" +
                if (rowCount > 0) "\nĐang hiển thị dữ liệu trước đó." else ""
            LoadState.READY -> if (rowCount > 0) null else {
                if (scope.kind == Kind.SOURCES) {
                    if (query.isNotBlank()) "Không tìm thấy nguồn Telegram" else "Chưa có nguồn Telegram"
                } else if (query.isNotBlank()) "Không tìm thấy bài trong $where"
                else "Chưa có bài trong $where"
            }
        }
        return Presentation(hint, "Phạm vi: $where", message, state == LoadState.ERROR)
    }

    /** A channel is a source, never an inferred artist. Omit absent/placeholder tags. */
    fun rowMetadata(performer: String?, source: String?, local: Boolean, sourceIsFolder: Boolean): String? {
        fun real(text: String?): String? = text?.trim()?.takeIf {
            it.isNotEmpty() && it.lowercase(java.util.Locale.ROOT) !in
                setOf("<unknown>", "unknown artist", "nghệ sĩ không rõ")
        }
        val from = real(source)
        return if (local) {
            from?.let { if (sourceIsFolder) "Thư mục: $it" else it }
        } else {
            listOfNotNull(real(performer), from?.let { "Kênh: $it" }).joinToString(" · ").ifEmpty { null }
        }
    }
}
