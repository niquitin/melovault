package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.CompactSetting
import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloCompactPolicyTest {

    @Test
    fun autoTurnsOnForShanlingDisplayInBothOrientations() {
        assertTrue(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, 384, 640, authorized = true))
        assertTrue(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, 640, 384, authorized = true))
    }

    @Test
    fun autoStaysOffOnPhonesAndTablets() {
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, 411, 914, authorized = true)) // phone
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, 1024, 640, authorized = true)) // Y700-like
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, 1920, 1200, authorized = true)) // FYT 160
    }

    @Test
    fun autoUsesDisplayNotSplitWindow() {
        // A Y700 display is large even when the app window is split; callers pass display size.
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.AUTO, 1024, 640, authorized = true))
    }

    @Test
    fun manualSettingsOverrideAuto() {
        assertTrue(MeloCompactPolicy.shouldUseCompact(CompactSetting.ON, 1024, 640, authorized = true))
        assertTrue(MeloCompactPolicy.shouldUseCompact(CompactSetting.EMULATE_SHANLING, 1024, 640, authorized = true))
        assertFalse(MeloCompactPolicy.shouldUseCompact(CompactSetting.OFF, 384, 640, authorized = true))
    }

    @Test
    fun neverBeforeLogin() {
        for (s in CompactSetting.entries) {
            assertFalse(MeloCompactPolicy.shouldUseCompact(s, 384, 640, authorized = false))
        }
    }

    @Test
    fun settingKeysRoundTripAndDefaultToAuto() {
        for (s in CompactSetting.entries) assertEquals(s, CompactSetting.fromKey(s.key))
        assertEquals(CompactSetting.AUTO, CompactSetting.fromKey(null))
        assertEquals(CompactSetting.AUTO, CompactSetting.fromKey("bogus"))
    }

    @Test
    fun downloadedDefaultAppliesOnlyOnce() {
        assertTrue(MeloCompactPolicy.needsDefaultDownloadedFilter(alreadyApplied = false))
        assertFalse(MeloCompactPolicy.needsDefaultDownloadedFilter(alreadyApplied = true))
    }

    @Test
    fun headerTitleUsesRealCounts() {
        assertEquals("Đã tải · 76 bài", MeloCompactPolicy.headerTitle(Mode.DOWNLOADED, null, 76))
        assertEquals("Tất cả · 25.085 bài", MeloCompactPolicy.headerTitle(Mode.ALL, null, 25_085))
        assertEquals("Playlist · 12 bài", MeloCompactPolicy.headerTitle(Mode.PLAYLIST, "Kênh A", 12))
        assertEquals("Nhạc Lạnh · 77 bài", MeloCompactPolicy.headerTitle(Mode.ALL, " Nhạc Lạnh ", 77))
        assertEquals("Đã tải · Nhạc Lạnh · 3 bài", MeloCompactPolicy.headerTitle(Mode.DOWNLOADED, "Nhạc Lạnh", 3))
        assertEquals("Tất cả", MeloCompactPolicy.headerTitle(Mode.ALL, null, null))
    }

    @Test
    fun rowStatusPriority() {
        assertEquals(Status.PLAYING, MeloCompactPolicy.rowStatus(downloaded = true, downloadingPercent = 40, playing = true))
        assertEquals(Status.DOWNLOADING, MeloCompactPolicy.rowStatus(downloaded = false, downloadingPercent = 40, playing = false))
        assertEquals(Status.DOWNLOADED, MeloCompactPolicy.rowStatus(downloaded = true, downloadingPercent = null, playing = false))
        assertEquals(Status.TELEGRAM, MeloCompactPolicy.rowStatus(downloaded = false, downloadingPercent = null, playing = false))
    }

    @Test
    fun progressPercentNeverInvented() {
        assertEquals(45, MeloCompactPolicy.progressPercent(45, 100))
        assertEquals(100, MeloCompactPolicy.progressPercent(150, 100))
        assertNull(MeloCompactPolicy.progressPercent(10, 0))
        assertNull(MeloCompactPolicy.progressPercent(10, -1))
    }

    @Test
    fun loadMoreNearEnd() {
        assertTrue(MeloCompactPolicy.shouldLoadMore(180, 10, 200))
        assertFalse(MeloCompactPolicy.shouldLoadMore(0, 10, 200))
        assertFalse(MeloCompactPolicy.shouldLoadMore(0, 0, 0))
    }
}
