package com.bsdung.musicassistantcar.data.telegram

/**
 * Nhiều Playlist có tên, S12: every decision and every sentence of the named-playlist screens, as pure JVM rules (no Android, no
 * clock, no I/O). The views and the activity only draw what this says and call the session; they decide nothing themselves.
 * Wording follows Phụ lục A of the plan: the old list is "Yêu thích", a named one is "Playlist".
 */
internal object MeloNamedPlaylistPolicy {

    // ---- which of the three screens of the Playlist tab is shown ---------------------------------------

    sealed class Screen {
        /** Level 1: "★ Yêu thích · N bài", the named playlists, "+ Tạo Playlist". */
        object Index : Screen()

        /** Level 2: the old saved list. */
        object Favourites : Screen()

        /** Level 2: the songs of one named playlist. */
        data class Detail(val id: ListId) : Screen()
    }

    /** A playlist that vanished (deleted here or on another device) takes the person back to the index; it never shows an empty ghost. */
    fun resolve(screen: Screen, lists: List<NamedListView>): Screen =
        if (screen is Screen.Detail && lists.none { it.id == screen.id }) Screen.Index else screen

    /** The back control of level 2 goes to level 1 and nowhere else. */
    fun back(screen: Screen): Screen = if (screen is Screen.Index) screen else Screen.Index

    // ---- level 1 ----------------------------------------------------------------------------------------

    enum class RowKind { FAVOURITES, NAMED, CREATE }

    data class IndexRow(
        val kind: RowKind,
        val id: ListId?,
        val title: String,
        val description: String,
        val enabled: Boolean = true,
    )

    const val FAVOURITES_NAME = "Yêu thích"

    /** The name box stops typing here; longer than the 60 a name may have, so that "too long" can be told instead of silently cut. */
    const val MAX_NAME_INPUT = 120

    fun countText(count: Int): String = "$count bài"

    /** [favourites] null = the old list has not been read yet (no number is guessed). */
    fun indexRows(favourites: Int?, lists: List<NamedListView>): List<IndexRow> {
        val rows = ArrayList<IndexRow>()
        val favText = favourites?.let { countText(it) } ?: "đang đọc…"
        rows += IndexRow(RowKind.FAVOURITES, null, "★ $FAVOURITES_NAME · $favText", "$FAVOURITES_NAME, $favText. Chạm để mở.")
        for (list in lists) {
            rows += IndexRow(RowKind.NAMED, list.id, "${list.name} · ${countText(list.count)}", "Playlist ${list.name}, ${countText(list.count)}. Chạm để mở.")
        }
        rows += if (lists.size >= NamedPlaylistState.MAX_LISTS) {
            IndexRow(RowKind.CREATE, null, "Đã đủ ${NamedPlaylistState.MAX_LISTS} Playlist", "Đã đủ ${NamedPlaylistState.MAX_LISTS} Playlist. Hãy xóa bớt một Playlist để tạo mới.", enabled = false)
        } else {
            IndexRow(RowKind.CREATE, null, "+ Tạo Playlist", "Tạo Playlist mới")
        }
        return rows
    }

    /** The title above the songs of level 2. */
    fun detailTitle(name: String, count: Int): String = "$name · ${countText(count)}"

    const val BACK_LABEL = "‹ Playlist"
    const val EMPTY_DETAIL = "Playlist này chưa có bài. Dùng “≡ Thêm vào Playlist…” ở menu của một bài."

    // ---- names -------------------------------------------------------------------------------------------

    sealed class NameCheck {
        data class Ok(val name: String) : NameCheck()
        object Empty : NameCheck()
        object TooLong : NameCheck()
        object BadCharacters : NameCheck()
        object Duplicate : NameCheck()
    }

    /** [except] is the playlist being renamed: it may keep its own name (or change only the case). */
    fun checkName(raw: String, lists: List<NamedListView>, except: ListId? = null): NameCheck {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return NameCheck.Empty
        if (trimmed.codePointCount(0, trimmed.length) > NamedPlaylistNames.MAX_CHARS) return NameCheck.TooLong
        val name = NamedPlaylistNames.clean(trimmed) ?: return NameCheck.BadCharacters
        val key = NamedPlaylistNames.comparisonKey(name)
        if (lists.any { it.id != except && NamedPlaylistNames.comparisonKey(it.name) == key }) return NameCheck.Duplicate
        return NameCheck.Ok(name)
    }

    fun nameMessage(check: NameCheck): String? = when (check) {
        is NameCheck.Ok -> null
        NameCheck.Empty -> "Hãy nhập tên Playlist."
        NameCheck.TooLong -> "Tên dài tối đa ${NamedPlaylistNames.MAX_CHARS} ký tự."
        NameCheck.BadCharacters -> "Tên có ký tự không dùng được."
        NameCheck.Duplicate -> "Đã có Playlist tên này."
    }

    // ---- what can be put in a playlist ------------------------------------------------------------------------

    sealed class AddTarget {
        data class Telegram(val item: PlaylistItemId.Telegram) : AddTarget()
        data class Local(val localId: Long, val fingerprint: String) : AddTarget()
        data class Refused(val message: String) : AddTarget()
    }

    const val REFUSED_LEGACY = "Chưa rõ nguồn Telegram của bài này nên chưa thêm được vào Playlist."
    const val REFUSED_PLACEHOLDER = "Bài này chưa có trong thư viện máy nên chưa thêm được vào Playlist."
    const val REFUSED_NOT_READY = "Playlist chưa sẵn sàng. Hãy thử lại sau giây lát."
    const val REFUSED_UNREADABLE = "Không đọc được các Playlist trên máy."
    const val REFUSED_NOTHING = "Chưa có bài nào để thêm."

    /**
     * A Telegram song goes into a playlist only with a verified (chat, message) whose key agrees with the one the library holds - the
     * same bar as the favourites. A legacy or unknown song, a placeholder row, a playlist that is not open: refused with the reason.
     */
    fun telegramTarget(
        songKey: Long, chatId: Long, rawMessageId: Long, accountId: Long, placeholder: Boolean, playlistsReady: Boolean,
    ): AddTarget = when {
        placeholder -> AddTarget.Refused(REFUSED_PLACEHOLDER)
        !playlistsReady -> AddTarget.Refused(REFUSED_NOT_READY)
        !MeloSavePolicy.isResolved(songKey, chatId, rawMessageId, accountId) -> AddTarget.Refused(REFUSED_LEGACY)
        else -> AddTarget.Telegram(PlaylistItemId.Telegram(chatId, rawMessageId))
    }

    fun localTarget(mediaStoreId: Long, displayName: String, durationMs: Long, sizeBytes: Long, playlistsReady: Boolean): AddTarget = when {
        !playlistsReady -> AddTarget.Refused(REFUSED_NOT_READY)
        mediaStoreId <= 0L -> AddTarget.Refused(REFUSED_NOTHING)
        else -> AddTarget.Local(mediaStoreId, LocalFingerprint.of(displayName, durationMs, sizeBytes))
    }

    // ---- the "add to playlist" box ------------------------------------------------------------------------------

    data class ChoiceRow(val id: ListId?, val label: String, val checked: Boolean, val description: String)

    const val NEW_PLAYLIST_LABEL = "+ Playlist mới…"

    /** One row per playlist with a check mark where the song already is, then the "new playlist" row (id null). */
    fun choiceRows(lists: List<NamedListView>, containing: Set<ListId>, songTitle: String): List<ChoiceRow> {
        val rows = lists.map { list ->
            val has = list.id in containing
            ChoiceRow(
                list.id, (if (has) "✓ " else "") + list.name, has,
                if (has) "${list.name}: đã có $songTitle. Chạm để bỏ." else "${list.name}: chưa có $songTitle. Chạm để thêm.",
            )
        }
        return rows + ChoiceRow(null, NEW_PLAYLIST_LABEL, false, "Tạo Playlist mới và thêm $songTitle")
    }

    /** What a tap on a row does: take the song out when it is in, put it in when it is not. */
    fun tapAdds(row: ChoiceRow): Boolean = !row.checked

    // ---- messages ---------------------------------------------------------------------------------------------------

    fun added(title: String, list: String) = "Đã thêm '$title' vào ‘$list’."
    fun removed(title: String, list: String) = "Đã bỏ '$title' khỏi ‘$list’."
    fun listFull(list: String) = "Playlist ‘$list’ đã đủ ${NamedPlaylistState.MAX_ITEMS} bài."
    fun created(list: String) = "Đã tạo Playlist ‘$list’."
    fun renamed(list: String) = "Đã đổi tên thành ‘$list’."
    fun deleted(list: String) = "Đã xóa Playlist ‘$list’."
    fun deleteQuestion(list: String) = "Xóa Playlist ‘$list’? Bài hát không bị xóa khỏi Telegram hay máy."
    const val TOO_MANY_PLAYLISTS = "Đã đủ ${NamedPlaylistState.MAX_LISTS} Playlist. Hãy xóa bớt một Playlist trước."
    const val OUTBOX_FULL = "Có quá nhiều thay đổi đang chờ đồng bộ nên chưa thể thay đổi Playlist. Dữ liệu cũ vẫn được giữ nguyên."
    const val FAILED = "Không lưu được Playlist an toàn trên thiết bị."
    const val NO_SUCH_LIST = "Playlist này không còn nữa."
    const val NO_SUCH_ITEM = "Bài này không còn trong Playlist."

    /** The sentence for a finished change that is NOT the happy path of the caller (the caller words its own success). */
    fun failureMessage(outcome: NamedOutcome, list: String?): String? = when (outcome) {
        is NamedOutcome.Ok -> null
        is NamedOutcome.Full ->
            if (outcome.limit == NamedRefusal.LIST_LIMIT) TOO_MANY_PLAYLISTS else listFull(list ?: "Playlist")
        NamedOutcome.OutboxFull -> OUTBOX_FULL
        is NamedOutcome.Blocked -> REFUSED_UNREADABLE
        NamedOutcome.NotOpen -> REFUSED_NOT_READY
        NamedOutcome.Failed -> FAILED
        NamedOutcome.DuplicateName -> nameMessage(NameCheck.Duplicate)
        is NamedOutcome.Refused -> when (outcome.why) {
            NamedRefusal.BAD_NAME -> nameMessage(NameCheck.BadCharacters)
            NamedRefusal.NO_SUCH_LIST -> NO_SUCH_LIST
            NamedRefusal.NO_SUCH_ITEM -> NO_SUCH_ITEM
            NamedRefusal.LIST_LIMIT -> TOO_MANY_PLAYLISTS
            NamedRefusal.ITEM_LIMIT -> listFull(list ?: "Playlist")
            NamedRefusal.DUPLICATE_NAME -> nameMessage(NameCheck.Duplicate)
        }
    }

    // ---- level 2: rows and order -------------------------------------------------------------------------------------

    enum class DetailKind { TELEGRAM, TELEGRAM_UNKNOWN, LOCAL, LOCAL_GONE }

    const val LOCAL_GONE_LABEL = "Bài trên máy không còn · chạm để bỏ"
    const val LOCAL_TAG = "trên máy"

    /** A song of the device matches its entry only when what the person sees (name, length, size) is still the same. */
    fun localMatches(entry: NamedEntry, displayName: String?, durationMs: Long, sizeBytes: Long): Boolean =
        displayName != null && entry.fingerprint == LocalFingerprint.of(displayName, durationMs, sizeBytes)

    /** The new place of an entry moved up (-1) or down (+1); null when it is already at that end. */
    fun moveTo(index: Int, delta: Int, size: Int): Int? {
        if (index !in 0 until size || (delta != -1 && delta != 1)) return null
        val to = index + delta
        return if (to in 0 until size) to else null
    }

    const val SORT_TITLE = "Sắp xếp"
    const val SORT_DONE = "Xong"
    const val SORT_UP = "↑"
    const val SORT_DOWN = "↓"
    fun sortUpDescription(title: String) = "Đưa $title lên"
    fun sortDownDescription(title: String) = "Đưa $title xuống"

    // ---- level 2: playing ----------------------------------------------------------------------------------------------

    data class PlayEntry(val item: PlaylistItemId, val kind: DetailKind)

    sealed class PlayPlan {
        /** Start at row [index]; the queue is the playlist's Telegram songs in order ([note] says what is left out). */
        data class FromTelegram(val index: Int, val note: String?) : PlayPlan()

        /** Only songs of the device can play: start at row [index]. */
        data class FromLocal(val index: Int) : PlayPlan()
        data class NothingToPlay(val message: String) : PlayPlan()
    }

    const val NOTHING_TO_PLAY = "Playlist này chưa có bài phát được."
    const val LOCAL_NOT_QUEUED = "Bài trên máy không nằm trong hàng chờ khi phát cả Playlist; chạm từng bài để phát."

    /**
     * "▶ Phát" ([shuffle] false: the first song that can play) or "⤨ Phát ngẫu nhiên" ([pick] chooses among them, e.g. a random
     * number; it is reduced modulo the count so any number is safe). A placeholder and a vanished song of the device never play. The
     * queue the player gets is the playlist's Telegram songs in order; songs of the device play when tapped one by one, because
     * the player keeps a Telegram queue and a device queue apart. Nothing is invented when only one kind is present.
     */
    fun playPlan(entries: List<PlayEntry>, shuffle: Boolean, pick: Int = 0): PlayPlan {
        val telegram = entries.indices.filter { entries[it].kind == DetailKind.TELEGRAM }
        val local = entries.indices.filter { entries[it].kind == DetailKind.LOCAL }
        val hasLocal = local.isNotEmpty()
        if (telegram.isNotEmpty()) {
            val index = if (shuffle) telegram[Math.floorMod(pick, telegram.size)] else telegram.first()
            return PlayPlan.FromTelegram(index, LOCAL_NOT_QUEUED.takeIf { hasLocal })
        }
        if (local.isNotEmpty()) return PlayPlan.FromLocal(if (shuffle) local[Math.floorMod(pick, local.size)] else local.first())
        return PlayPlan.NothingToPlay(NOTHING_TO_PLAY)
    }

    /** What a tap on row [index] does. */
    enum class RowTap { PLAY_TELEGRAM, PLAY_LOCAL, EXPLAIN_UNKNOWN, OFFER_REMOVE_LOCAL, NONE }

    fun rowTap(entries: List<PlayEntry>, index: Int): RowTap = when (entries.getOrNull(index)?.kind) {
        DetailKind.TELEGRAM -> RowTap.PLAY_TELEGRAM
        DetailKind.LOCAL -> RowTap.PLAY_LOCAL
        DetailKind.TELEGRAM_UNKNOWN -> RowTap.EXPLAIN_UNKNOWN
        DetailKind.LOCAL_GONE -> RowTap.OFFER_REMOVE_LOCAL
        null -> RowTap.NONE
    }

    // ---- "Lưu hàng chờ thành Playlist" ------------------------------------------------------------------------------------

    data class QueueCandidate(
        val local: Boolean,
        val songKey: Long,
        val title: String,
        val chatId: Long = 0L,
        val rawMessageId: Long = 0L,
        val placeholder: Boolean = false,
        val mediaStoreId: Long = 0L,
        val durationMs: Long = 0L,
        val sizeBytes: Long = 0L,
    )

    sealed class QueueSavePlan {
        /** [items] in queue order, each already a valid target; [skipped] = candidates that were left out. */
        data class Ready(val items: List<AddTarget>, val saved: Int, val skipped: Int) : QueueSavePlan()
        object Empty : QueueSavePlan()
        data class TooMany(val count: Int) : QueueSavePlan()
    }

    const val QUEUE_EMPTY = "Hàng chờ đang trống."
    fun queueTooMany(count: Int) = "Hàng chờ có $count bài, nhiều hơn ${NamedPlaylistState.MAX_ITEMS} bài một Playlist nên chưa lưu được."
    fun queueSaved(saved: Int, skipped: Int) = "Đã lưu $saved bài, bỏ qua $skipped bài."
    const val QUEUE_NOTHING_SAVABLE = "Hàng chờ không có bài nào lưu được vào Playlist."
    const val SAVE_QUEUE_LABEL = "≡ Lưu hàng chờ thành Playlist"

    /**
     * The queue as it stands, in order. A Telegram song needs the verified identity (like [telegramTarget]); a song of the device is kept
     * with its fingerprint; a placeholder, a legacy song, an unknown song and a repeat are left out and counted. More than the
     * 1000 songs a playlist holds is refused outright, never cut.
     */
    fun planQueueSave(queue: List<QueueCandidate>, accountId: Long): QueueSavePlan {
        if (queue.isEmpty()) return QueueSavePlan.Empty
        if (queue.size > NamedPlaylistState.MAX_ITEMS) return QueueSavePlan.TooMany(queue.size)
        val taken = ArrayList<AddTarget>()
        val seen = HashSet<PlaylistItemId>()
        for (c in queue) {
            val target = if (c.local) {
                localTarget(c.mediaStoreId, c.title, c.durationMs, c.sizeBytes, playlistsReady = true)
            } else {
                telegramTarget(c.songKey, c.chatId, c.rawMessageId, accountId, c.placeholder, playlistsReady = true)
            }
            val id: PlaylistItemId = when (target) {
                is AddTarget.Telegram -> target.item
                is AddTarget.Local -> PlaylistItemId.Local(target.localId)
                is AddTarget.Refused -> continue
            }
            if (seen.add(id)) taken += target
        }
        return QueueSavePlan.Ready(taken, taken.size, queue.size - taken.size)
    }
}
