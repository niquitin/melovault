package com.bsdung.musicassistantcar.data.telegram

/**
 * Nhiều Playlist có tên, S9: how the changes of the named playlists that wait to be sent are shown to the person who is about to allow
 * automatic sync ("Thay đổi chờ gửi"). Pure text rules; the screen only supplies song titles from the local library.
 *
 * A row says WHAT will be sent: the kind of change, the playlist's name and the song's title. It never shows a list id, an operation id,
 * a tag, a chat or a message number, and it never carries a song of the device itself (those are not sent at all).
 */
internal object NamedPendingReviewPolicy {
    const val UNKNOWN_SONG = "Bài chưa có tên trong thư viện máy này"
    const val UNCERTAIN_NOTE = "Chưa xác nhận đã tới kênh; chỉ đối chiếu, không gửi lại."
    const val UNKNOWN_LIST = "Playlist không rõ tên"

    /** One text per waiting operation, in outbox order. [names] maps a playlist to its current name; [titleOf] a song to its title (null = unknown). */
    fun rows(
        entries: List<NamedOutboxEntry>,
        names: Map<ListId, String>,
        titleOf: (PlaylistItemId.Telegram) -> String?,
    ): List<String> = entries.filter { it.status != OutboxStatus.ACKNOWLEDGED }.map { entry ->
        val op = entry.op
        val list = "‘${names[op.listId]?.takeIf { it.isNotBlank() } ?: UNKNOWN_LIST}’"
        val song = op.item?.let { titleOf(it)?.takeIf { title -> title.isNotBlank() } ?: UNKNOWN_SONG }.orEmpty()
        val action = when (op.type) {
            NamedOpType.LCREATE -> "Tạo Playlist $list"
            NamedOpType.LRENAME -> "Đổi tên Playlist thành ‘${op.name.orEmpty()}’"
            NamedOpType.LDELETE -> "Xóa Playlist $list"
            NamedOpType.IADD -> "Thêm vào $list: $song"
            NamedOpType.IREMOVE -> "Bỏ khỏi $list: $song"
            NamedOpType.IMOVE -> "Đổi vị trí trong $list: $song"
        }
        if (entry.status == OutboxStatus.RETRY_REQUIRED) "$action\n$UNCERTAIN_NOTE" else action
    }

    /** The names a review needs: only live-or-deleted playlists that have one. */
    fun namesOf(state: NamedPlaylistState): Map<ListId, String> =
        state.lists.mapNotNull { (id, rec) -> rec.name?.value?.let { id to it } }.toMap()

    /** The songs a review needs titles for. */
    fun songsOf(entries: List<NamedOutboxEntry>): List<PlaylistItemId.Telegram> =
        entries.filter { it.status != OutboxStatus.ACKNOWLEDGED }.mapNotNull { it.op.item }.distinct()
}
