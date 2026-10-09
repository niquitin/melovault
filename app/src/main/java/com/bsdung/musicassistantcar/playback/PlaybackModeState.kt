package com.bsdung.musicassistantcar.playback

import androidx.media3.common.Player

/** Playback modes survive Media3 service recreation without altering stored queue IDs. */
internal data class PlaybackModeState(
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
) {
    companion object {
        fun restore(shuffleEnabled: Boolean, storedRepeatMode: Int): PlaybackModeState =
            PlaybackModeState(
                shuffleEnabled = shuffleEnabled,
                repeatMode = when (storedRepeatMode) {
                    Player.REPEAT_MODE_OFF,
                    Player.REPEAT_MODE_ALL,
                    Player.REPEAT_MODE_ONE -> storedRepeatMode
                    else -> Player.REPEAT_MODE_OFF
                },
            )
    }
}
