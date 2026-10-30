package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test

class MeloQueueUiPolicyTest {
    private fun row(id: Long = 1, download: MeloDownloadUiPolicy.Presentation? = null) =
        MeloScreenHost.QueueItem("Tên bài rất dài $id", "Nguồn", 7201, id, false, null, download = download)
    private fun key() = MeloQueueUiPolicy.Key(1, listOf(row()), 1, true, false, false)

    @Test fun pausedBufferingAndPlayingHaveDistinctRenderingKeys() {
        val playing = key()
        assertNotEquals(playing, playing.copy(playing = false, paused = true))
        assertNotEquals(playing, playing.copy(playing = false, buffering = true))
        assertNotEquals(playing.copy(paused = true), playing.copy(buffering = true))
    }
    @Test fun sameCurrentStillRefreshesOnQueueContentOrDownloadChange() {
        val key = key()
        assertNotEquals(key, key.copy(rows = key.rows + row(2)))
        assertNotEquals(key, key.copy(rows = listOf(row().copy(title = "Tên mới"))))
        assertNotEquals(key, key.copy(rows = listOf(row(download = MeloDownloadUiPolicy.Presentation(MeloDownloadUiPolicy.State.DOWNLOADING, 50)))))
        assertNotEquals(key, key.copy(owner = 2))
        assertNotEquals(key, key.copy(shuffle = true))
    }
    @Test fun captionNamesThePlayOrderWhileShuffleIsOnAndNeverClaimsTheSourceOrder() {
        assertEquals("Thứ tự phát · Đảo bật", MeloQueueUiPolicy.caption(shuffle = true, preview = false))
        assertEquals("Thứ tự phát · Đảo bật", MeloQueueUiPolicy.caption(shuffle = true, preview = true))
        assertEquals("Hàng đợi", MeloQueueUiPolicy.caption(shuffle = false, preview = false))
        assertEquals("Tiếp theo", MeloQueueUiPolicy.caption(shuffle = false, preview = true))
        assertFalse(MeloQueueUiPolicy.caption(true, false).contains("gốc"))
    }
    @Test fun positionTickDoesNotChangeKeyAndPauseDoesNotScroll() {
        val key = key()
        assertEquals(key, key())
        assertFalse(MeloQueueUiPolicy.scrollToCurrent(key, key.copy(playing = false, paused = true)))
        assertFalse(MeloQueueUiPolicy.scrollToCurrent(key, key.copy(rows = key.rows + row(2))))
        assertTrue(MeloQueueUiPolicy.scrollToCurrent(key, key.copy(current = 2)))
        assertFalse(MeloQueueUiPolicy.scrollToCurrent(key, key.copy(current = null)))
        assertTrue(MeloQueueUiPolicy.scrollToCurrent(null, key))
        assertTrue(MeloQueueUiPolicy.scrollToCurrent(key, key.copy(owner = 2)))
    }
    @Test fun portraitWindowIsBoundedAndNotTheWholeQueue() {
        assertEquals((0..11).toList(), MeloQueueUiPolicy.window(100, -1).toList())
        assertEquals((49..60).toList(), MeloQueueUiPolicy.window(100, 50).toList())
        assertEquals((98..99).toList(), MeloQueueUiPolicy.window(100, 99).toList())
        assertEquals((0..2).toList(), MeloQueueUiPolicy.window(3, 0).toList())
        assertTrue(MeloQueueUiPolicy.window(0, -1).isEmpty())
    }
    @Test fun pausedAndBufferingUseDifferentQueueStatusWithoutChangingGlobalEnum() {
        assertEquals(MeloComponentPolicy.Status.PAUSED, MeloNowPlayingPolicy.queueStatus(true, true, null, paused = true))
        assertEquals(MeloComponentPolicy.Status.OPENING, MeloNowPlayingPolicy.queueStatus(true, true, null, buffering = true))
        assertEquals(MeloComponentPolicy.Status.PLAYING, MeloNowPlayingPolicy.queueStatus(true, false, 80))
        assertEquals(MeloComponentPolicy.Status.DOWNLOADED, MeloNowPlayingPolicy.queueStatus(false, true, null, paused = true, buffering = true))
        assertEquals(MeloComponentPolicy.Status.DOWNLOADING, MeloNowPlayingPolicy.queueStatus(false, false, 50))
    }
    @Test fun bufferingCaptionDoesNotClaimPausedOrReady() {
        val fact = MeloDownloadUiPolicy.Presentation(MeloDownloadUiPolicy.State.DOWNLOADING, 90)
        assertEquals("Đang đệm nhạc… · Đang tải 90%", MeloDownloadUiPolicy.rowText(fact, true, false, false, true))
    }
    @Test fun dockStacksBeforeAnySideButtonFallsBelowTouchOrMeasuredTextWidth() {
        for (font in listOf(1f, 1.3f, 1.5f, 2f)) {
            val side = maxOf(48, (40 * font).toInt())
            val threshold = side * 4 + 72 + 16
            assertTrue(MeloQueueUiPolicy.dockStacked(threshold - 1, 48, 72, 8, side))
            assertFalse(MeloQueueUiPolicy.dockStacked(threshold, 48, 72, 8, side))
        }
        assertTrue(MeloQueueUiPolicy.dockStacked(220, 48, 72, 8, 48))
    }
    @Test fun localHighlightedTextMeetsContrastInBothThemesWithoutChangingBasePalette() {
        for (base in listOf(MeloDesignTokens.PALETTE, MeloDesignTokens.TEAL_PALETTE)) {
            val highlight = MeloDesignTokens.libraryHighlightedPalette(base)
            for (foreground in listOf(highlight.text, highlight.textSecondary, highlight.accent)) {
                assertTrue("contrast=${MeloDesignTokens.contrast(foreground, highlight.accentSoft)}",
                    MeloDesignTokens.contrast(foreground, highlight.accentSoft) >= 4.5)
            }
            assertTrue(MeloDesignTokens.contrast(base.textSecondary, base.surface) >= 4.5)
            assertNotEquals(base.accentSoft, highlight.accentSoft)
        }
        assertEquals(MeloDesignTokens.PALETTE, MeloDesignTokens.palette(MeloDesignTokens.Profile.COMPACT))
        assertEquals(MeloDesignTokens.CAR_PALETTE, MeloDesignTokens.palette(MeloDesignTokens.Profile.CAR))
    }
    @Test fun durationKeepsHoursAndUnknownIsNotInvented() {
        assertNull(TelegramSongDisplayPolicy.formatDuration(0))
        assertNull(TelegramSongDisplayPolicy.formatDuration(-1))
        assertEquals("00:59", TelegramSongDisplayPolicy.formatDuration(59))
        assertEquals("1:00:00", TelegramSongDisplayPolicy.formatDuration(3600))
        assertEquals("2:00:01", TelegramSongDisplayPolicy.formatDuration(7201))
    }
}
