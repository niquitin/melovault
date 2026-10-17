package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.ColorRole
import com.bsdung.musicassistantcar.data.telegram.MeloComponentPolicy.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloComponentPolicyTest {

    @Test
    fun everyStatusHasWordsAndRoleFromSpec() {
        val playing = MeloComponentPolicy.presentStatus(Status.PLAYING)
        assertEquals("Đang phát", playing.label)
        assertEquals(ColorRole.ACCENT, playing.color)
        assertTrue(playing.highlightRow)

        assertEquals("Đã tải · MP3 · 320 kbps",
            MeloComponentPolicy.presentStatus(Status.DOWNLOADED, "MP3 · 320 kbps").label)
        assertEquals("Đã tải", MeloComponentPolicy.presentStatus(Status.DOWNLOADED, null).label)
        assertEquals("Đang tải 45%", MeloComponentPolicy.presentStatus(Status.DOWNLOADING, progressPercent = 45).label)
        assertEquals("Đang tải 100%", MeloComponentPolicy.presentStatus(Status.DOWNLOADING, progressPercent = 140).label)
        assertEquals("Đang tải", MeloComponentPolicy.presentStatus(Status.DOWNLOADING).label)

        val telegram = MeloComponentPolicy.presentStatus(Status.TELEGRAM)
        assertEquals("Telegram · Chưa tải", telegram.label)
        assertEquals(ColorRole.SECONDARY, telegram.color)

        val error = MeloComponentPolicy.presentStatus(Status.ERROR)
        assertEquals("Tải lỗi – bấm thử lại", error.label)
        assertEquals(ColorRole.WARNING, error.color)
        for (s in Status.entries) {
            val p = MeloComponentPolicy.presentStatus(s)
            assertTrue(p.label.isNotBlank())
            assertEquals(s == Status.PLAYING, p.highlightRow)
        }
    }

    @Test
    fun formatComesFromMimeThenExtensionNeverGuessed() {
        assertEquals("MP3", MeloComponentPolicy.formatOf("audio/mpeg", "x.flac"))
        assertEquals("FLAC", MeloComponentPolicy.formatOf("audio/flac", null))
        assertEquals("FLAC", MeloComponentPolicy.formatOf(null, "Bài.FLAC"))
        assertEquals("M4A", MeloComponentPolicy.formatOf("audio/mp4", null))
        assertNull(MeloComponentPolicy.formatOf("application/octet-stream", "noext"))
        assertNull(MeloComponentPolicy.formatOf(null, null))
    }

    @Test
    fun qualityLabelShowsOnlyKnownParts() {
        assertEquals("MP3 · 44.1 kHz · 320 kbps", MeloComponentPolicy.qualityLabel("MP3", 44_100, null, 320))
        assertEquals("MP3", MeloComponentPolicy.qualityLabel("MP3", null, null, null))
        assertNull(MeloComponentPolicy.qualityLabel(null, null, null, null))
        assertEquals("FLAC · 24bit · 96 kHz · Hi-Res", MeloComponentPolicy.qualityLabel("FLAC", 96_000, 24, 2_800))
    }

    @Test
    fun hiResOnlyForLosslessFrom24bit48kHz() {
        assertFalse(MeloComponentPolicy.isHiRes("MP3", 96_000, 24))
        assertFalse(MeloComponentPolicy.isHiRes("FLAC", 44_100, 16))
        assertFalse(MeloComponentPolicy.isHiRes("FLAC", 44_100, 24))
        assertFalse(MeloComponentPolicy.isHiRes("FLAC", 48_000, null))
        assertTrue(MeloComponentPolicy.isHiRes("FLAC", 48_000, 24))
        assertTrue(MeloComponentPolicy.isHiRes("wav", 192_000, 32))
    }

    @Test
    fun searchHintUsesRealCountWithVietnameseSeparator() {
        assertEquals("Tìm trong 27.971 bài hát…", MeloComponentPolicy.searchHint(27_971))
        assertEquals("Tìm trong 1.234.567 bài hát…", MeloComponentPolicy.searchHint(1_234_567))
        assertEquals("Tìm trong 9 bài hát…", MeloComponentPolicy.searchHint(9))
        assertEquals("Tìm bài hát…", MeloComponentPolicy.searchHint(null))
        assertEquals("Tìm bài hát…", MeloComponentPolicy.searchHint(0))
    }

    @Test
    fun searchIgnoresVietnameseAccentsAndCase() {
        assertEquals("su thanh hoa", MeloComponentPolicy.searchKey("Sử Thanh Hóa"))
        assertEquals("duong dua", MeloComponentPolicy.searchKey("  ĐƯỜNG   đua "))
        assertTrue(MeloComponentPolicy.matchesSearch("su thanh hoa", "Sử Thanh Hóa", null))
        assertTrue(MeloComponentPolicy.matchesSearch("hoa su", "Sử Thanh Hóa"))
        assertTrue(MeloComponentPolicy.matchesSearch("chillout emi", "CHILLOUT #9", "Nhạc Lạnh – Chillout (EMI)"))
        assertFalse(MeloComponentPolicy.matchesSearch("lakhouse", "Sử Thanh Hóa"))
        assertTrue(MeloComponentPolicy.matchesSearch("  ", "anything"))
    }

    @Test
    fun chipAndProgressText() {
        assertEquals("Đã tải (1.204)", MeloComponentPolicy.chipLabel("Đã tải", 1_204))
        assertEquals("Tất cả", MeloComponentPolicy.chipLabel("Tất cả", null))
        assertEquals("01:23 / 04:56", MeloComponentPolicy.progressText(83, 296))
        assertEquals("00:00 / 04:56", MeloComponentPolicy.progressText(-5, 296))
        assertEquals("01:23", MeloComponentPolicy.progressText(83, 0))
    }

    @Test
    fun placeholderInitialAndStableColor() {
        assertEquals("N", MeloComponentPolicy.initialOf("Nhạc Lạnh – Chillout", "x"))
        assertEquals("C", MeloComponentPolicy.initialOf(null, "  #CHILLOUT"))
        assertEquals("♪", MeloComponentPolicy.initialOf(" ", "  "))
        val p = MeloDesignTokens.PALETTE
        assertEquals(MeloComponentPolicy.placeholderColor("Kênh A", p), MeloComponentPolicy.placeholderColor("Kênh A", p))
    }

    @Test
    fun khzFormatting() {
        assertEquals("48 kHz", MeloComponentPolicy.formatKhz(48_000))
        assertEquals("44.1 kHz", MeloComponentPolicy.formatKhz(44_100))
    }
}
