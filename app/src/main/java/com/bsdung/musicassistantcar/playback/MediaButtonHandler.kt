package com.bsdung.musicassistantcar.playback

import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.Player

object MediaButtonHandler {
    private const val TAG = "MediaButtonHandler"

    enum class HandledAction {
        NONE,
        PLAY,
        PAUSE,
        NEXT,
        PREVIOUS,
        STOP,
    }

    fun determineAction(keyCode: Int, isPlaying: Boolean): HandledAction =
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (isPlaying) HandledAction.PAUSE else HandledAction.PLAY
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> HandledAction.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> HandledAction.PAUSE
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> HandledAction.NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND -> HandledAction.PREVIOUS
            KeyEvent.KEYCODE_MEDIA_STOP -> HandledAction.STOP
            else -> HandledAction.NONE
        }

    fun handleKeyEvent(keyEvent: KeyEvent?, player: Player): Boolean {
        if (keyEvent == null) return false
        val keyCode = keyEvent.keyCode
        val action = determineAction(keyCode, player.isPlaying)
        if (action == HandledAction.NONE) {
            return false
        }

        // On ACTION_UP for media keys, report handled = true to prevent duplicate triggers
        if (keyEvent.action == KeyEvent.ACTION_UP) {
            Log.d(TAG, "Consuming ACTION_UP for keyCode=$keyCode")
            return true
        }

        if (keyEvent.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        Log.i(
            TAG,
            "Executing action=$action for keyCode=$keyCode (isPlaying=${player.isPlaying}, " +
                "playbackState=${player.playbackState}, playWhenReady=${player.playWhenReady}, " +
                "items=${player.mediaItemCount})"
        )

        when (action) {
            HandledAction.PLAY -> {
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                } else if (player.playbackState == Player.STATE_ENDED) {
                    player.seekToDefaultPosition(0)
                }
                player.play()
            }
            HandledAction.PAUSE -> {
                player.pause()
            }
            HandledAction.NEXT -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                }
            }
            HandledAction.PREVIOUS -> {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                }
            }
            HandledAction.STOP -> {
                player.stop()
            }
            HandledAction.NONE -> Unit
        }
        return true
    }
}
