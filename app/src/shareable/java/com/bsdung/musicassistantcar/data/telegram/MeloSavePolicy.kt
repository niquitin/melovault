package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P4: what the one-tap Lưu / Bỏ lưu button may do for the song that is playing, how it reads, and how
 * quick repeated taps are folded into one write at a time. Pure JVM. The write itself is NOT here: it is
 * still [PlaylistSessionApi.toggle] through the activity's single `requestPlaylistToggle`.
 */
internal object MeloSavePolicy {

    enum class Availability {
        /** Telegram song whose (chat, message) is known and agrees with its key: a normal Save. */
        READY,
        /** Telegram song of this account whose chat/message could not be named: saved on this device only, never offered to the cloud. */
        LEGACY_ONLY,
        /** A "Nhạc trên máy" song: no Telegram identity exists, nothing is invented. */
        LOCAL_ONLY,
        NOTHING_PLAYING,
        /** Playing, but the library cannot say which Telegram song it is; refused instead of guessing. */
        NOT_IDENTIFIED,
        PLAYLIST_UNREADABLE,
        PLAYLIST_NOT_LOADED,
    }

    const val HINT_LOCAL_ONLY = "Chỉ lưu được bài Telegram"
    const val HINT_LEGACY = "Chưa rõ nguồn Telegram · chỉ lưu trên máy"
    const val HINT_NOT_IDENTIFIED = "Chưa xác định được bài đang phát"
    const val HINT_UNREADABLE = "Không đọc được Playlist trên máy"
    const val HINT_NOT_LOADED = "Playlist chưa sẵn sàng"
    const val HINT_NOTHING = "Chưa có bài đang phát"

    /** [chatId]/[rawMessageId] are 0 when the song row did not carry them. */
    fun availability(
        playing: Boolean,
        local: Boolean,
        songKey: Long?,
        songKnown: Boolean,
        chatId: Long,
        rawMessageId: Long,
        accountId: Long,
        playlistUnreadable: Boolean,
        playlistLoaded: Boolean,
    ): Availability = when {
        !playing -> Availability.NOTHING_PLAYING
        local -> Availability.LOCAL_ONLY
        songKey == null || !songKnown -> Availability.NOT_IDENTIFIED
        playlistUnreadable -> Availability.PLAYLIST_UNREADABLE
        !playlistLoaded -> Availability.PLAYLIST_NOT_LOADED
        isResolved(songKey, chatId, rawMessageId, accountId) -> Availability.READY
        else -> Availability.LEGACY_ONLY
    }

    /** The same agreement V2 demands before it names a Telegram item: the key must be the one derived from (chat, message). */
    fun isResolved(songKey: Long, chatId: Long, rawMessageId: Long, accountId: Long): Boolean =
        chatId != 0L && rawMessageId > 0L && accountId > 0L &&
            TelegramSongKey.forMessage(chatId, rawMessageId, accountId) == songKey

    data class ButtonState(
        val label: String,
        /** A tap starts a write. False = a tap only explains [hint]. */
        val actionable: Boolean,
        val saved: Boolean,
        val busy: Boolean,
        val hint: String?,
        val contentDescription: String,
        /** For accessibility "state description": what is true right now. */
        val stateDescription: String,
    )

    fun hintFor(availability: Availability): String? = when (availability) {
        Availability.READY -> null
        Availability.LEGACY_ONLY -> HINT_LEGACY
        Availability.LOCAL_ONLY -> HINT_LOCAL_ONLY
        Availability.NOTHING_PLAYING -> HINT_NOTHING
        Availability.NOT_IDENTIFIED -> HINT_NOT_IDENTIFIED
        Availability.PLAYLIST_UNREADABLE -> HINT_UNREADABLE
        Availability.PLAYLIST_NOT_LOADED -> HINT_NOT_LOADED
    }

    /**
     * [saved] is what the committed Playlist says. While a write is running ([busy]) the label stays what is TRUE
     * (no optimistic flip); it only says that the save is being written.
     */
    fun buttonState(availability: Availability, saved: Boolean, busy: Boolean): ButtonState {
        val actionable = availability == Availability.READY || availability == Availability.LEGACY_ONLY
        val shownSaved = saved && availability != Availability.LOCAL_ONLY && availability != Availability.NOTHING_PLAYING
        val label = if (shownSaved) "★ Bỏ lưu" else "☆ Lưu"
        val hint = hintFor(availability)
        val description = buildString {
            append(if (shownSaved) "Bỏ lưu bài đang phát khỏi Playlist" else "Lưu bài đang phát vào Playlist")
            if (busy) append(", đang ghi")
            if (hint != null) append(". ").append(hint)
        }
        val state = when {
            busy -> "Đang ghi vào Playlist"
            availability == Availability.LOCAL_ONLY -> HINT_LOCAL_ONLY
            shownSaved -> "Đã lưu trên máy"
            else -> "Chưa lưu"
        }
        return ButtonState(label, actionable, shownSaved, busy, hint, description, state)
    }

    /** Short label for the chips of the other screens (Gọn, Tablet, Studio Deck). */
    fun chipLabel(saved: Boolean): String = if (saved) "★ Bỏ lưu" else "☆ Lưu"

    /**
     * Height of the Car Save row: at least the larger of the minimum touch target and the page buttons, so it is a
     * big target but never taller than a page button.
     */
    fun carButtonHeightPx(sizes: MeloDesignTokens.Sizes): Int = maxOf(sizes.minTouch, sizes.pageButtonHeight)
}

/**
 * Folds quick repeated Save taps into one write at a time without losing the last intent.
 *
 * - The first tap starts a write for its target ([tap] returns true: the caller runs it).
 * - Taps for ANY target while a write runs are only counted; they start nothing.
 * - When the write ends, [finish] returns the next target whose extra taps are ODD in number (an even number
 *   cancels out), so two quick taps on one song never write twice and three taps write exactly the final state.
 * - A failed write drops the extra taps of that target (the person is told it failed) and goes on with the others.
 *
 * Not thread-safe on purpose: use it from the UI thread only. One instance belongs to one account; the owner
 * replaces it when the account changes, so a late callback of the old one finds a different instance.
 */
internal class SaveToggleGuard<T : Any> {
    private var inFlight: T? = null
    private val extra = LinkedHashMap<T, Int>()

    val busy: Boolean get() = inFlight != null

    fun isBusyWith(target: T): Boolean = inFlight == target

    /** True = start the write for [target] now. False = counted (or ignored); [finish] will pick it up. */
    fun tap(target: T): Boolean {
        if (inFlight == null) {
            inFlight = target
            return true
        }
        extra[target] = (extra[target] ?: 0) + 1
        return false
    }

    /** The write of the in-flight target ended. Returns the next target to write, or null. */
    fun finish(ok: Boolean): T? {
        val done = inFlight ?: return null
        inFlight = null
        // Extra taps on the target that just finished: odd = the person wants it toggled once more.
        val againCount = extra.remove(done) ?: 0
        if (ok && againCount % 2 == 1) {
            inFlight = done
            return done
        }
        val next = extra.entries.firstOrNull { it.value % 2 == 1 }?.key
        extra.entries.removeAll { it.value % 2 == 0 }
        if (next == null) {
            extra.clear()
            return null
        }
        extra.remove(next)
        inFlight = next
        return next
    }

    fun reset() {
        inFlight = null
        extra.clear()
    }
}
