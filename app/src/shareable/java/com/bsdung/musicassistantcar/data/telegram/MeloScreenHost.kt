package com.bsdung.musicassistantcar.data.telegram

import android.graphics.Bitmap
import android.view.View
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode

/**
 * What the new screens (Gọn, Car Mode) may read and do. TelegramPreviewActivity
 * implements it by mapping straight onto its existing library, download and
 * playback logic, so the new screens never touch the database or TDLib.
 */
internal interface MeloScreenHost {
    fun snapshot(): Snapshot
    fun setMode(mode: Mode)
    fun showSources()
    fun openSource(chatId: Long?)
    fun openBrowseScope(scope: MeloBrowseUiPolicy.Scope) = Unit
    fun filterInSource(mode: Mode) = setMode(mode)
    fun closeSources() = openSource(null)
    fun selectedBrowseSource(): Long? = null

    /** "Nhạc trên máy": opens the device music list; asks for audio access only here (the owner tapped it). */
    fun openLocal()
    /** Leaves "Nhạc trên máy" back to the Telegram screen the owner came from. */
    fun closeLocal()
    fun setQuery(query: String)
    /** Retry only the list currently being browsed; never starts a sync or changes playback. */
    fun retryBrowse() = Unit
    fun loadMore()
    fun playAt(index: Int)

    /** The song just tapped while it is still being opened ("Đang mở…"), null otherwise. */
    fun playStatus(): TelegramPlayFeedbackPolicy.PlayStatus?
    fun openSongMenu(index: Int, anchor: View)
    fun nowPlaying(): NowPlaying?
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleFavorite()
    fun toggleRowFavorite(songKey: Long) = Unit

    /**
     * E96 P4: the honest one-line state of the saved Playlist (saved on this device / waiting / synced / why not),
     * computed off the UI thread and cached; null until the first read. Never a guess, never another account's.
     */
    fun playlistStatus(): PlaylistSyncStatusPolicy.Line? = null

    /** Opens Settings > Đồng bộ Playlist. */
    fun openSyncSettings() = Unit

    fun toggleShuffle()
    fun cycleRepeat()
    fun openQueue()
    fun openLegacyOverflow(anchor: View)
    fun openUiLab()

    /** Starts speech input; false when this device has no speech recognizer. */
    fun startVoiceSearch(onText: (String) -> Unit): Boolean

    /**
     * Where the song list was scrolled to when its screen was torn down by a window-size change
     * that needs another screen type (E95). Kept in memory only; the next screen restores it
     * when it shows the same group ([groupKey]) and the song is loaded.
     */
    fun saveListAnchor(groupKey: String, songKey: Long?, offsetPx: Int)
    fun listAnchor(): ListAnchor?
    fun setBrowseCapture(listener: (() -> Unit)?) = Unit
    fun browseContext(): MeloBrowseState.Context? = null
    fun saveBrowsePosition(generation: Long, songKey: Long?, offsetPx: Int, sourceScrollY: Int) = Unit
    fun saveSourcePosition(generation: Long, chatId: Long?, offsetPx: Int) = Unit
    fun deckState(): MeloBrowseState.Deck = MeloBrowseState.Deck()
    fun saveDeckState(state: MeloBrowseState.Deck) = Unit

    // ---- Library screens (Điện thoại / Tablet / Hai khung) ---------------------------------

    /** "Phát hết": plays the group on screen from its first loaded song; [shuffle] turns shuffle on first. */
    fun playAll(shuffle: Boolean)

    // ---- E100: the bottom card while nothing is loaded (defaults keep Car, Hai khung and fakes as they were) ----

    /** The Telegram song this account listened to last, for the "Nghe tiếp" card; null when there is none. */
    fun resumeCard(): Resume? = null
    /** "Phát hết" would start a song now: the list on screen is a playable song list. */
    fun canPlayAll(): Boolean = false
    /** Plays the remembered song (from where it stopped when its file is fully downloaded). */
    fun playResume() = Unit

    fun openNowPlaying()
    /** The same sync line the legacy header shows (real counts), or null when there is none. */
    fun syncSubtitle(): String?
    /** Offline groups found by the last scan, null while unknown. */
    fun downloadedCount(): Int?
    /** Library-wide numbers per connected source; never narrowed by chip, channel or search. */
    fun sourceSummaries(): List<MeloSourceCardPolicy.Source>
    fun hideZeroSources(): Boolean
    fun toggleHideZeroSources()
    fun rescan()

    /**
     * Every channel with its song count (most songs first), not narrowed by the search box and
     * without the "all sources" entry; empty while the first load is pending.
     */
    fun channels(): List<SourceRow>

    /** Sort of the list on screen (always NEWEST on screens without the A–Z bar). */
    fun sort(): SongSort
    fun setSort(sort: SongSort)
    /**
     * Jumps to the first song whose search key is >= [lowerBound] (null = start). Returns the list
     * index to scroll to when the whole list is in memory, or null when the list was reloaded
     * from the database starting at that letter.
     */
    fun jumpToLetter(lowerBound: String?): Int?

    // ---- E98 row covers (teal theme only; the defaults keep every other screen as it was) -----

    /** The "Tải ảnh bìa từ Telegram" switch of Cài đặt giao diện (default on). */
    fun rowArtEnabled(): Boolean = false
    /** Visible rows plus the few below them; the host fetches their covers in the background. */
    fun requestRowArt(rows: List<Row>, rowArtPx: Int) = Unit
    /** The cover to show now for [key] (a blur counts), or null. */
    fun peekRowArt(key: String?): Bitmap? = null
    /** True when [key] has its final cover or is known to have none. */
    fun rowArtSettled(key: String?): Boolean = false
    /** Called on the main thread, coalesced, when new covers are ready. */
    fun setRowArtListener(listener: (() -> Unit)?) = Unit

    // ---- Studio Deck (Now Playing) --------------------------------------------------------

    /** Current playback queue in play order (the service's authoritative order). */
    fun queue(): List<QueueItem>
    fun playQueueAt(index: Int)
    /** Same song menu as the library rows, for the queue song at [index]. */
    fun openQueueSongMenu(index: Int, anchor: View)
    /** Real decoder format of the current track; [bitDepth] from the file when known. */
    fun qualityLabel(bitDepth: Int?): String?
    /**
     * Quality pills of the current track (format, bit/kHz, kbps, size, Hi-Res); only the numbers
     * that are known. Empty when nothing is known or nothing is playing.
     */
    fun qualityChips(bitDepth: Int?): List<MeloQualityChipPolicy.Chip>
    /** The fully downloaded, account-owned file of the current track, or null. */
    fun currentLocalFile(): java.io.File?
    fun accountId(): Long
    fun prefetchEnabled(): Boolean
    fun setPrefetchEnabled(enabled: Boolean)
    /** (deadline in elapsedRealtime millis or null, stop at end of track). */
    fun sleepTimer(): Pair<Long?, Boolean>
    fun setSleepTimer(option: MeloNowPlayingPolicy.SleepOption)

    data class QueueItem(
        val title: String,
        val sourceTitle: String?,
        val durationSeconds: Int,
        val songKey: Long,
        val downloaded: Boolean,
        val downloadingPercent: Int?,
        /** A "Nhạc trên máy" song: its row says "Trên máy", not "Đã tải". */
        val local: Boolean = false
    )

    data class Row(
        val title: String,
        val sourceTitle: String?,
        val filename: String,
        val durationSeconds: Int,
        val songKey: Long,
        val downloaded: Boolean,
        val downloadingPercent: Int?,
        val art: Bitmap?,
        /** Search key, only filled while sorted by name (drives the A–Z bar). */
        val sortKey: String = "",
        /** MediaStore MIME type of a local song (format text); null for Telegram rows. */
        val mimeType: String? = null,
        val local: Boolean = false,
        /** E98 teal theme: identity of the cover ([MeloRowArtPolicy] keys); null for the current theme. */
        val artKey: String? = null,
        val chatId: Long = 0L,
        /** The genuine Telegram message id (not the song key). */
        val rawMessageId: Long = 0L,
        /** Path of the downloaded file, when the song is on the device; the loader checks it is the account's own. */
        val localFile: String? = null,
        val mediaStoreId: Long = 0L,
        val performer: String? = null,
        val sourceIsFolder: Boolean = false,
        val saved: Boolean = false,
        val saveBusy: Boolean = false,
        val saveAvailability: MeloSavePolicy.Availability = MeloSavePolicy.Availability.NOT_IDENTIFIED
    )

    data class SourceRow(val chatId: Long, val title: String, val count: Int)

    /** The remembered last song of this account; [art] only when a cover is already in memory. */
    data class Resume(val songKey: Long, val title: String, val sourceTitle: String?, val art: Bitmap?)

    /** The first visible song of a group and how far its top edge sits from the list's top. */
    data class ListAnchor(val groupKey: String, val songKey: Long?, val offsetPx: Int)

    data class Snapshot(
        val showingSources: Boolean,
        val mode: Mode,
        val rows: List<Row>,
        val sources: List<SourceRow>,
        val total: Int?,
        val sourceTitle: String?,
        val playingKey: Long?,
        /** The current song is paused (play-when-ready off); its row says "Tạm dừng". */
        val paused: Boolean = false,
        val query: String,
        val note: String?,
        val sort: SongSort = SongSort.NEWEST,
        val letterFloor: String? = null,
        /** The channel open in the list, null for all sources, Saved Messages or Playlist. */
        val selectedChatId: Long? = null,
        /** The list on screen is "Nhạc trên máy" (not Telegram). */
        val local: Boolean = false,
        val browseScope: MeloBrowseUiPolicy.Scope = MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.LIBRARY),
        val browseLoadState: MeloBrowseUiPolicy.LoadState = MeloBrowseUiPolicy.LoadState.READY,
        val browseOwnerId: Long = 0L,
        val browseGeneration: Long = 0L,
        /** True only when the current query has no further page. */
        val browseComplete: Boolean = true
    )

    data class NowPlaying(
        val songKey: Long?,
        val title: String,
        val sourceTitle: String?,
        val filename: String?,
        val positionMs: Long,
        val durationMs: Long,
        val playing: Boolean,
        val art: Bitmap?,
        val favorite: Boolean,
        val shuffle: Boolean,
        val repeatLabel: String,
        val downloadNote: String?,
        /** Paused by the user (play-when-ready off); false while merely buffering. */
        val paused: Boolean = false,
        /** The playing song comes from "Nhạc trên máy"; [sourceTitle] is then its artist or folder. */
        val local: Boolean = false,
        /** Local song with no artist tag: [sourceTitle] is its folder name, so the line says "Thư mục:" (E95 F08). */
        val sourceIsFolder: Boolean = false,
        /** E96 P4: whether and how the Save button may act on this song (see [MeloSavePolicy]). */
        val saveAvailability: MeloSavePolicy.Availability = MeloSavePolicy.Availability.READY,
        /** A Save write for this song is running; [favorite] still shows what is committed. */
        val saveBusy: Boolean = false
    )
}
