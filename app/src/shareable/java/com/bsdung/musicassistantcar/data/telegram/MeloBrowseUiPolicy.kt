package com.bsdung.musicassistantcar.data.telegram

/** Presentation only: no queries, playback, download or persistent browse state. */
internal object MeloBrowseUiPolicy {
    enum class Kind { LIBRARY, CHANNEL, DOWNLOADED, PLAYLIST, SOURCES, SAVED_MESSAGES, LOCAL, FORUM_TOPICS, FORUM_TOPIC }
    enum class LoadState { INITIAL_LOADING, UPDATING, READY, ERROR }

    /** Titles are deliberately excluded from identity (two channels may have the same name). */
    data class Scope(val kind: Kind, val chatId: Long? = null, val downloadedOnly: Boolean = false, val forumTopicId: Int? = null) {
        init {
            require(kind != Kind.CHANNEL || chatId != null)
            require(chatId != 0L)
            require(chatId == null || kind in setOf(Kind.CHANNEL, Kind.DOWNLOADED, Kind.FORUM_TOPICS, Kind.FORUM_TOPIC))
            require(kind !in setOf(Kind.FORUM_TOPICS, Kind.FORUM_TOPIC) || chatId != null)
            require(!downloadedOnly || kind == Kind.PLAYLIST || kind == Kind.FORUM_TOPIC)
            require(if (kind == Kind.FORUM_TOPIC) forumTopicId != null && forumTopicId > 0 else forumTopicId == null)
        }
    }

    data class Request(val ownerId: Long, val generation: Long, val scope: Scope)
    data class Presentation(val hint: String, val scopeLabel: String, val message: String?, val retry: Boolean)

    fun scope(
        sources: Boolean, playlist: Boolean, downloaded: Boolean, allChats: Boolean, chatId: Long?,
        forumTopicId: Int? = null, forumTopics: Boolean = false
    ): Scope = when {
        sources -> Scope(Kind.SOURCES)
        playlist -> Scope(Kind.PLAYLIST, downloadedOnly = downloaded)
        allChats && chatId != null && forumTopics -> Scope(Kind.FORUM_TOPICS, chatId)
        allChats && chatId != null && forumTopicId != null -> Scope(Kind.FORUM_TOPIC, chatId, downloaded, forumTopicId)
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

    fun present(scope: Scope, title: String?, state: LoadState, rowCount: Int, query: String, indexComplete: Boolean = true): Presentation {
        val channel = title?.trim()?.takeIf { it.isNotEmpty() }?.let { "kênh $it" } ?: "kênh đã chọn"
        val where = when (scope.kind) {
            Kind.LIBRARY -> "thư viện đã đồng bộ"
            Kind.CHANNEL -> channel
            Kind.DOWNLOADED -> if (scope.chatId != null) "Đã tải · $channel" else "Đã tải"
            Kind.PLAYLIST -> if (scope.downloadedOnly) "Playlist · Đã tải" else "Playlist"
            Kind.SOURCES -> "nguồn Telegram"
            Kind.SAVED_MESSAGES -> "Tin đã lưu"
            Kind.LOCAL -> "Nhạc trên máy"
            Kind.FORUM_TOPICS -> "chủ đề · ${title ?: "nhóm đã chọn"}"
            Kind.FORUM_TOPIC -> "chủ đề ${title ?: "đã chọn"}" + if (scope.downloadedOnly) " · Đã tải" else ""
        }
        val hint = when (scope.kind) {
            Kind.SOURCES -> "Tìm nguồn Telegram…"
            Kind.FORUM_TOPICS -> "Tìm tên chủ đề…"
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
                } else if (scope.kind == Kind.FORUM_TOPICS) {
                    if (query.isNotBlank()) "Không tìm thấy chủ đề" else if (indexComplete) "Chưa có chủ đề" else "Chưa lập chỉ mục chủ đề"
                } else if (query.isNotBlank()) "Không tìm thấy bài trong $where"
                else if (scope.kind == Kind.FORUM_TOPIC && !indexComplete) "Chưa lập chỉ mục"
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
