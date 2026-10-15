package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramSongDisplayPolicyTest {
    @Test fun titleAndFileNeverRepeatTogether() {
        assertEquals("CHILLOUT #9 ~ TERRY x EMI",
            TelegramSongDisplayPolicy.label("CHILLOUT #9 ~ TERRY x EMI",
                "CHILLOUT #9 ~ TERRY x EMI.mp3"))
    }
    @Test fun fallbackToFileNameIfNoTitle() {
        assertEquals("demo.flac", TelegramSongDisplayPolicy.label("", "demo.flac"))
        assertEquals("Bài hát không tên", TelegramSongDisplayPolicy.label(" ", " "))
    }
    @Test fun unicodeTitlePreserved() {
        assertEquals("Biển Gọi Tên Gió",
            TelegramSongDisplayPolicy.label("Biển Gọi Tên Gió", "SongFile.mp3"))
    }
}
