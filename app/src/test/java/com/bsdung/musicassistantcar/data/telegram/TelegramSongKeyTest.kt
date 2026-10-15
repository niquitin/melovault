package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSongKeyTest {
    @Test fun savedMessageIdRemainsCompatibleWithOldPlaylist() {
        assertEquals(123456789L, TelegramSongKey.forMessage(5, 123456789L, 5))
    }
    @Test fun sameMessageIdFromDifferentChatsDoesNotCollide() {
        val a = TelegramSongKey.forMessage(-10, 123456789L, 99)
        val b = TelegramSongKey.forMessage(-11, 123456789L, 99)
        assertNotEquals(a, b)
        assertTrue(a > 0L && b > 0L)
    }
    @Test fun stableAcrossRebuildAndPaging() {
        assertEquals(
            TelegramSongKey.forMessage(-44, 567890L, 88),
            TelegramSongKey.forMessage(-44, 567890L, 88))
    }
    @Test(expected = IllegalArgumentException::class)
    fun refusesMissingChat() { TelegramSongKey.forMessage(0, 10, 10) }
}
