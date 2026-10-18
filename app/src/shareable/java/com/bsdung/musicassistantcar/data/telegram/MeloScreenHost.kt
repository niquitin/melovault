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
    fun setQuery(query: String)
    fun loadMore()
    fun playAt(index: Int)
    fun openSongMenu(index: Int, anchor: View)
    fun nowPlaying(): NowPlaying?
    fun playPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleFavorite()
    fun toggleShuffle()
    fun cycleRepeat()
    fun openQueue()
    fun openLegacyOverflow(anchor: View)
    fun openUiLab()

    /** Starts speech input; false when this device has no speech recognizer. */
    fun startVoiceSearch(onText: (String) -> Unit): Boolean

    // ---- Library screens (Điện thoại / Tablet / Hai khung) ---------------------------------

    /** "Phát hết": plays the group on screen from its first loaded song; [shuffle] turns shuffle on first. */
    fun playAll(shuffle: Boolean)
    fun openNowPlaying()
    /** The same sync line the legacy header shows (real counts), or null when there is none. */
    fun syncSubtitle(): String?
    /** Offline groups found by the last scan, null while unknown. */
    fun downloadedCount(): Int?
    fun hideZeroSources(): Boolean
    fun toggleHideZeroSources()
    fun rescan()

    /** Sort of the list on screen (always NEWEST on screens without the A–Z bar). */
    fun sort(): SongSort
    fun setSort(sort: SongSort)
    /**
     * Jumps to the first song whose search key is >= [lowerBound] (null = start). Returns the list
     * index to scroll to when the whole list is in memory, or null when the list was reloaded
     * from the database starting at that letter.
     */
    fun jumpToLetter(lowerBound: String?): Int?

    // ---- Studio Deck (Now Playing) --------------------------------------------------------

    /** Current playback queue in play order (the service's authoritative order). */
    fun queue(): List<QueueItem>
    fun playQueueAt(index: Int)
    /** Real decoder format of the current track; [bitDepth] from the file when known. */
    fun qualityLabel(bitDepth: Int?): String?
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
        val downloadingPercent: Int?
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
        val sortKey: String = ""
    )

    data class SourceRow(val chatId: Long, val title: String, val count: Int)

    data class Snapshot(
        val showingSources: Boolean,
        val mode: Mode,
        val rows: List<Row>,
        val sources: List<SourceRow>,
        val total: Int?,
        val sourceTitle: String?,
        val playingKey: Long?,
        val query: String,
        val note: String?,
        val sort: SongSort = SongSort.NEWEST,
        val letterFloor: String? = null
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
        val downloadNote: String?
    )
}
