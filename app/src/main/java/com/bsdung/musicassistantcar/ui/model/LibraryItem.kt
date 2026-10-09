package com.bsdung.musicassistantcar.ui.model

import androidx.media3.common.MediaItem

sealed class LibraryItem {
    abstract val id: String

    data class Category(
        override val id: String,
        val title: String,
        val subtitle: String,
        val iconResId: Int,
        val mediaItem: MediaItem,
    ) : LibraryItem()

    data class Folder(
        override val id: String,
        val title: String,
        val subtitle: String,
        val mediaItem: MediaItem,
    ) : LibraryItem()

    data class Track(
        override val id: String,
        val title: String,
        val artist: String,
        val album: String?,
        val artworkUri: String?,
        val durationMs: Long?,
        val isCurrent: Boolean,
        val isPlaying: Boolean,
        val mediaItem: MediaItem,
        val format: String? = null,
        val sizeBytes: Long? = null,
        val codec: String? = null,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val channels: Int? = null,
        val sourceBadge: String? = null,
        val playbackAction: String? = null,
        val isPlayable: Boolean = true,
        val externalUrl: String? = null,
    ) : LibraryItem()
}
