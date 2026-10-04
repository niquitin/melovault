package com.bsdung.musicassistantcar.playback

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaButtonHandlerTest {

    @Test
    fun playPauseKeyTogglesBasedOnPlaybackState() {
        // When playing: should pause
        val actionWhenPlaying = MediaButtonHandler.determineAction(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            isPlaying = true,
        )
        assertEquals(MediaButtonHandler.HandledAction.PAUSE, actionWhenPlaying)

        // When paused: should play
        val actionWhenPaused = MediaButtonHandler.determineAction(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            isPlaying = false,
        )
        assertEquals(MediaButtonHandler.HandledAction.PLAY, actionWhenPaused)
    }

    @Test
    fun headsetHookKeyTogglesBasedOnPlaybackState() {
        val actionWhenPlaying = MediaButtonHandler.determineAction(
            KeyEvent.KEYCODE_HEADSETHOOK,
            isPlaying = true,
        )
        assertEquals(MediaButtonHandler.HandledAction.PAUSE, actionWhenPlaying)

        val actionWhenPaused = MediaButtonHandler.determineAction(
            KeyEvent.KEYCODE_HEADSETHOOK,
            isPlaying = false,
        )
        assertEquals(MediaButtonHandler.HandledAction.PLAY, actionWhenPaused)
    }

    @Test
    fun dedicatedPlayAndPauseKeysMapDirectly() {
        assertEquals(
            MediaButtonHandler.HandledAction.PLAY,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_MEDIA_PLAY, isPlaying = true),
        )
        assertEquals(
            MediaButtonHandler.HandledAction.PLAY,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_MEDIA_PLAY, isPlaying = false),
        )
        assertEquals(
            MediaButtonHandler.HandledAction.PAUSE,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_MEDIA_PAUSE, isPlaying = true),
        )
        assertEquals(
            MediaButtonHandler.HandledAction.PAUSE,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_MEDIA_PAUSE, isPlaying = false),
        )
    }

    @Test
    fun navigationKeysMapToNextAndPrevious() {
        assertEquals(
            MediaButtonHandler.HandledAction.NEXT,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_MEDIA_NEXT, isPlaying = true),
        )
        assertEquals(
            MediaButtonHandler.HandledAction.PREVIOUS,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_MEDIA_PREVIOUS, isPlaying = true),
        )
        assertEquals(
            MediaButtonHandler.HandledAction.STOP,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_MEDIA_STOP, isPlaying = true),
        )
    }

    @Test
    fun unhandledKeysMapToNone() {
        assertEquals(
            MediaButtonHandler.HandledAction.NONE,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_VOLUME_UP, isPlaying = true),
        )
        assertEquals(
            MediaButtonHandler.HandledAction.NONE,
            MediaButtonHandler.determineAction(KeyEvent.KEYCODE_UNKNOWN, isPlaying = false),
        )
    }
}
