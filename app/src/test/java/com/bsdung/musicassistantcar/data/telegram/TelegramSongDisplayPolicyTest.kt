package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSongDisplayPolicyTest {

    @Test
    fun titleAndFileNeverRepeatTogether() {
        assertEquals(
            "CHILLOUT #9 ~ TERRY x EMI",
            TelegramSongDisplayPolicy.label(
                "CHILLOUT #9 ~ TERRY x EMI",
                "CHILLOUT #9 ~ TERRY x EMI.mp3"
            )
        )
    }

    @Test
    fun fallbackToFileNameIfNoTitle() {
        assertEquals("demo.flac", TelegramSongDisplayPolicy.label("", "demo.flac"))
        assertEquals("Bài hát không tên", TelegramSongDisplayPolicy.label(" ", " "))
    }

    @Test
    fun unicodeTitlePreserved() {
        assertEquals(
            "Biển Gọi Tên Gió",
            TelegramSongDisplayPolicy.label("Biển Gọi Tên Gió", "SongFile.mp3")
        )
    }

    @Test
    fun formatDurationCalculations() {
        assertNull(TelegramSongDisplayPolicy.formatDuration(0))
        assertNull(TelegramSongDisplayPolicy.formatDuration(-10))
        assertEquals("00:05", TelegramSongDisplayPolicy.formatDuration(5))
        assertEquals("03:45", TelegramSongDisplayPolicy.formatDuration(225))
        assertEquals("59:59", TelegramSongDisplayPolicy.formatDuration(3599))
        assertEquals("1:00:00", TelegramSongDisplayPolicy.formatDuration(3600))
        assertEquals("1:23:45", TelegramSongDisplayPolicy.formatDuration(5025))
    }

    @Test
    fun formatFileSizeCalculations() {
        assertNull(TelegramSongDisplayPolicy.formatFileSize(0L))
        assertNull(TelegramSongDisplayPolicy.formatFileSize(-1L))
        assertEquals("8.2 MB", TelegramSongDisplayPolicy.formatFileSize(8_200_000L))
        assertEquals("45.0 MB", TelegramSongDisplayPolicy.formatFileSize(45_000_000L))
        assertEquals("120.5 MB", TelegramSongDisplayPolicy.formatFileSize(120_500_000L))
    }

    @Test
    fun subtitleFormattingCombinations() {
        // Complete metadata with performer
        val s1 = TelegramSongDisplayPolicy.subtitle(
            source = "Nhạc Trẻ Hay",
            isDownloaded = true,
            fileSizeBytes = 8_200_000L,
            durationSeconds = 225,
            performer = "Sơn Tùng M-TP"
        )
        assertEquals("Sơn Tùng M-TP · Nhạc Trẻ Hay · 03:45 · 8.2 MB · Đã tải", s1)

        // Fallback to Telegram default source, no performer, uncached
        val s2 = TelegramSongDisplayPolicy.subtitle(
            source = null,
            isDownloaded = false,
            fileSizeBytes = -1L,
            durationSeconds = 0,
            performer = null
        )
        assertEquals("Telegram · Chưa tải", s2)

        // Downloaded with duration but no file size
        val s3 = TelegramSongDisplayPolicy.subtitle(
            source = "Saved Messages",
            isDownloaded = true,
            fileSizeBytes = 0L,
            durationSeconds = 180,
            performer = null
        )
        assertEquals("Saved Messages · 03:00 · Đã tải", s3)
    }

    @Test
    fun computeSampleWithinBounds() {
        assertTrue(TelegramSongDisplayPolicy.computeSample(100_000, 96, 96) >= 1)
        assertTrue(TelegramSongDisplayPolicy.computeSample(5_000_000, 96, 96) in 2..16)
    }
}
