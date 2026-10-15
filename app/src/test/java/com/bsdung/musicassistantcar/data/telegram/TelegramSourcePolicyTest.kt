package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSourcePolicyTest {
    @Test fun groupsEveryAudioByContainingChat() {
        assertEquals(listOf(20L to 3, 10L to 2, 30L to 1),
            TelegramSourcePolicy.sortedCounts(listOf(10,20,20,30,10,20)))
    }
    @Test fun ignoresInvalidChatAndKeepsStableOrder() {
        assertEquals(listOf(-2L to 1, 10L to 1),
            TelegramSourcePolicy.sortedCounts(listOf(10,0,-2)))
    }
    @Test fun sourceNameSearchIsCaseInsensitive() {
        assertTrue(TelegramSourcePolicy.matchesSource("Nhạc Lạnh - Chillout (EMI)", "chillout"))
        assertFalse(TelegramSourcePolicy.matchesSource("Music Assistant", "trance"))
    }
    @Test fun emptySearchMatchesAnyGroup() {
        assertTrue(TelegramSourcePolicy.matchesSource("Telegram", " "))
    }
}
