package com.bsdung.musicassistantcar.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModeStateTest {
    @Test fun defaultsToNormalPlaybackForFreshInstall() {
        val state = PlaybackModeState.restore(shuffleEnabled = false, storedRepeatMode = Player.REPEAT_MODE_OFF)
        assertFalse(state.shuffleEnabled)
        assertEquals(Player.REPEAT_MODE_OFF, state.repeatMode)
    }

    @Test fun restoresShuffleAndRepeatAll() {
        val state = PlaybackModeState.restore(shuffleEnabled = true, storedRepeatMode = Player.REPEAT_MODE_ALL)
        assertTrue(state.shuffleEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, state.repeatMode)
    }

    @Test fun restoresRepeatOneIndependentlyFromShuffle() {
        val state = PlaybackModeState.restore(shuffleEnabled = false, storedRepeatMode = Player.REPEAT_MODE_ONE)
        assertFalse(state.shuffleEnabled)
        assertEquals(Player.REPEAT_MODE_ONE, state.repeatMode)
    }

    @Test fun rejectsInvalidOrCorruptRepeatModeWithoutCrashing() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackModeState.restore(true, 9999).repeatMode)
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackModeState.restore(false, -1).repeatMode)
        assertTrue(PlaybackModeState.restore(true, -1).shuffleEnabled)
    }
}
