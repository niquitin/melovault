package com.bsdung.musicassistantcar.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bsdung.musicassistantcar.R

object DemoCatalog {
    const val ROOT_ID = "root"

    private data class DemoTrack(
        val id: String,
        val title: String,
        val note: String,
        val rawRes: Int,
    )

    private val tracks = listOf(
        DemoTrack("demo_440", "Demo A · 440 Hz", "MediaSession test", R.raw.demo_440),
        DemoTrack("demo_523", "Demo B · 523 Hz", "Steering key test", R.raw.demo_523),
        DemoTrack("demo_659", "Demo C · 659 Hz", "Queue test", R.raw.demo_659),
    )

    fun rootItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("MeloVault")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    fun items(context: Context): List<MediaItem> =
        tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(resourceUri(context, track.rawRes))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.note)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }

    fun find(context: Context, mediaId: String): MediaItem? =
        items(context).firstOrNull { it.mediaId == mediaId }

    fun mediaIds(): List<String> = tracks.map { it.id }

    private fun resourceUri(context: Context, rawRes: Int): Uri =
        Uri.parse(
            "android.resource://${context.packageName}/$rawRes"
        )
}
