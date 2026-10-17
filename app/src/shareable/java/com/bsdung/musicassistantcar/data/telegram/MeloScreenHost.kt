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

    data class Row(
        val title: String,
        val sourceTitle: String?,
        val filename: String,
        val durationSeconds: Int,
        val songKey: Long,
        val downloaded: Boolean,
        val downloadingPercent: Int?,
        val art: Bitmap?
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
        val note: String?
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
