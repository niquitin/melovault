package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloQualityChipPolicy.Chip
import com.bsdung.musicassistantcar.data.telegram.MeloQualityChipPolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloQualityChipPolicyTest {

    private fun kinds(chips: List<Chip>) = chips.map { it.kind }
    private fun texts(chips: List<Chip>) = chips.map { it.text }

    @Test
    fun hiResFlacShowsAllFivePillsInSpecOrder() {
        val chips = MeloQualityChipPolicy.chips("FLAC", 24, 96_000, 2_800, 34_200_000L)
        assertEquals(
            listOf(Kind.FORMAT, Kind.SAMPLING, Kind.SIZE, Kind.HI_RES),
            kinds(chips)
        )
        assertEquals(listOf("FLAC", "24bit · 96kHz", "34,2 MB", "Hi-Res"), texts(chips))
    }

    @Test
    fun losslessNeverShowsBitrate() {
        val chips = MeloQualityChipPolicy.chips("FLAC", 16, 44_100, 1_000, 30_000_000L)
        assertFalse(Kind.BITRATE in kinds(chips))
        assertEquals(listOf("FLAC", "16bit · 44.1kHz", "30,0 MB"), texts(chips))
    }

    @Test
    fun lossyShowsRateAndBitrateButNeverBitDepth() {
        val chips = MeloQualityChipPolicy.chips("MP3", 16, 44_100, 320, 9_500_000L)
        assertEquals(listOf("MP3", "44.1kHz", "320 kbps", "9,5 MB"), texts(chips))
        assertFalse(chips.any { it.text.contains("bit") })
    }

    @Test
    fun missingNumbersDropTheirPillInsteadOfShowingPlaceholders() {
        assertEquals(listOf("MP3"), texts(MeloQualityChipPolicy.chips("MP3", null, null, null, null)))
        assertEquals(listOf("MP3"), texts(MeloQualityChipPolicy.chips("MP3", 0, 0, 0, 0L)))
        assertEquals(listOf("MP3"), texts(MeloQualityChipPolicy.chips("MP3", -1, -1, -1, -1L)))
        assertEquals(listOf("320 kbps"), texts(MeloQualityChipPolicy.chips(null, null, null, 320, null)))
        assertEquals(listOf("12,0 MB"), texts(MeloQualityChipPolicy.chips(null, null, null, null, 12_000_000L)))
        assertTrue(MeloQualityChipPolicy.chips(null, null, null, null, null).isEmpty())
        assertTrue(MeloQualityChipPolicy.chips("  ", null, null, null, null).isEmpty())
        MeloQualityChipPolicy.chips("FLAC", null, 96_000, null, null).let {
            assertEquals(listOf("FLAC", "96kHz"), texts(it))
        }
        assertEquals("24bit", MeloQualityChipPolicy.samplingLabel(true, 24, null))
    }

    @Test
    fun hiResPillOnlyForLosslessFrom24bit48kHz() {
        fun hasHiRes(format: String?, depth: Int?, rate: Int?) =
            Kind.HI_RES in kinds(MeloQualityChipPolicy.chips(format, depth, rate, null, null))

        assertTrue(hasHiRes("FLAC", 24, 48_000))
        assertTrue(hasHiRes("wav", 32, 192_000))
        assertTrue(hasHiRes("ALAC", 24, 96_000))
        // One number short, or unknown, never earns the badge.
        assertFalse(hasHiRes("FLAC", 24, 44_100))
        assertFalse(hasHiRes("FLAC", 16, 96_000))
        assertFalse(hasHiRes("FLAC", null, 96_000))
        assertFalse(hasHiRes("FLAC", 24, null))
        // Lossy is never Hi-Res, whatever the numbers.
        assertFalse(hasHiRes("MP3", 24, 96_000))
        assertFalse(hasHiRes("M4A", 24, 96_000))
        assertFalse(hasHiRes("OPUS", 24, 48_000))
        assertFalse(hasHiRes(null, 24, 96_000))
    }

    @Test
    fun hiResLabelIsFixedAndDescribesTheRule() {
        assertEquals("Hi-Res", MeloQualityChipPolicy.HI_RES_LABEL)
        val chip = MeloQualityChipPolicy.chips("FLAC", 24, 48_000, null, null).last()
        assertEquals(Chip(Kind.HI_RES, "Hi-Res"), chip)
        assertTrue(MeloQualityChipPolicy.HI_RES_DESCRIPTION.contains("24bit"))
        assertTrue(MeloQualityChipPolicy.HI_RES_DESCRIPTION.contains("48kHz"))
    }

    @Test
    fun sizeLabelUsesDecimalCommaAndSwitchesToGigabytes() {
        assertEquals("34,2 MB", MeloQualityChipPolicy.sizeLabel(34_200_000L))
        assertEquals("0,5 MB", MeloQualityChipPolicy.sizeLabel(500_000L))
        assertEquals("432,0 MB", MeloQualityChipPolicy.sizeLabel(432_000_000L))
        assertEquals("1,2 GB", MeloQualityChipPolicy.sizeLabel(1_234_000_000L))
        assertNull(MeloQualityChipPolicy.sizeLabel(0L))
        assertNull(MeloQualityChipPolicy.sizeLabel(-1L))
    }

    @Test
    fun describeJoinsPillsOrReturnsNull() {
        assertEquals(
            "FLAC · 24bit · 96kHz · 34,2 MB · Hi-Res",
            MeloQualityChipPolicy.describe(MeloQualityChipPolicy.chips("FLAC", 24, 96_000, null, 34_200_000L))
        )
        assertNull(MeloQualityChipPolicy.describe(emptyList()))
    }

    @Test
    fun nowPlayingChipsUseFilenameWhenDecoderReportsRawAudio() {
        // ExoPlayer reports audio/raw after decoding: the file name gives the source format.
        val chips = MeloNowPlayingPolicy.qualityChips("audio/raw", "song.flac", 96_000, -1, 24, 34_200_000L)
        assertEquals(listOf("FLAC", "24bit · 96kHz", "34,2 MB", "Hi-Res"), texts(chips))
    }

    @Test
    fun nowPlayingChipsDropUnknownNumbersFromTheDecoder() {
        // Streaming MP3 whose bitrate and size are not known yet: format and rate only.
        val chips = MeloNowPlayingPolicy.qualityChips("audio/mpeg", null, 44_100, -1, null, -1L)
        assertEquals(listOf("MP3", "44.1kHz"), texts(chips))
        // Lossy decoder bitrate is real data: 320000 bps -> 320 kbps.
        assertEquals(
            listOf("MP3", "44.1kHz", "320 kbps"),
            texts(MeloNowPlayingPolicy.qualityChips("audio/mpeg", null, 44_100, 320_000, null, 0L))
        )
        // Nothing known at all (no track yet, no file name, no size): no row.
        assertTrue(MeloNowPlayingPolicy.qualityChips(null, null, 0, 0, null, 0L).isEmpty())
    }

    @Test
    fun nowPlayingFlacWithoutKnownBitDepthIsNeverHiRes() {
        // Bit depth only comes from the downloaded file; until then 96 kHz FLAC must not claim Hi-Res.
        val chips = MeloNowPlayingPolicy.qualityChips("audio/flac", null, 96_000, -1, null, 40_000_000L)
        assertEquals(listOf("FLAC", "96kHz", "40,0 MB"), texts(chips))
    }
}
