package com.bsdung.musicassistantcar.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioInfoFormatterTest {

    @Test
    fun testFormatFileSize() {
        assertEquals("38.4 MB", AudioInfoFormatter.formatFileSize(40265318L))
        assertEquals("8.5 MB", AudioInfoFormatter.formatFileSize(8912896L))
        assertEquals("23.4 MB", AudioInfoFormatter.formatFileSize(24536678L))
        assertEquals("1.00 GB", AudioInfoFormatter.formatFileSize(1024L * 1024L * 1024L))
        assertEquals("1.50 GB", AudioInfoFormatter.formatFileSize((1.5 * 1024.0 * 1024.0 * 1024.0).toLong()))
        assertNull(AudioInfoFormatter.formatFileSize(null))
        assertNull(AudioInfoFormatter.formatFileSize(0L))
        assertNull(AudioInfoFormatter.formatFileSize(-100L))
    }

    @Test
    fun testFormatSampleRate() {
        assertEquals("44.1 kHz", AudioInfoFormatter.formatSampleRate(44100))
        assertEquals("48 kHz", AudioInfoFormatter.formatSampleRate(48000))
        assertEquals("88.2 kHz", AudioInfoFormatter.formatSampleRate(88200))
        assertEquals("96 kHz", AudioInfoFormatter.formatSampleRate(96000))
        assertEquals("176.4 kHz", AudioInfoFormatter.formatSampleRate(176400))
        assertEquals("192 kHz", AudioInfoFormatter.formatSampleRate(192000))
        assertNull(AudioInfoFormatter.formatSampleRate(null))
        assertNull(AudioInfoFormatter.formatSampleRate(0))
        assertNull(AudioInfoFormatter.formatSampleRate(-1))
    }

    @Test
    fun testFormatBitrate() {
        assertEquals("912 kbps", AudioInfoFormatter.formatBitrate(911742))
        assertEquals("320 kbps", AudioInfoFormatter.formatBitrate(320000))
        assertEquals("256 kbps", AudioInfoFormatter.formatBitrate(256143))
        assertEquals("128 kbps", AudioInfoFormatter.formatBitrate(128000))
        assertNull(AudioInfoFormatter.formatBitrate(null))
        assertNull(AudioInfoFormatter.formatBitrate(0))
        assertNull(AudioInfoFormatter.formatBitrate(-1))
    }

    @Test
    fun testClassificationRules() {
        assertEquals("Lossless", AudioInfoFormatter.classifyQuality("FLAC", null))
        assertEquals("Lossless", AudioInfoFormatter.classifyQuality("flac", null))
        assertEquals("Lossless", AudioInfoFormatter.classifyQuality("WAV", null))
        assertEquals("Lossless", AudioInfoFormatter.classifyQuality("ALAC", null))
        assertEquals("Lossless", AudioInfoFormatter.classifyQuality(null, "ALAC"))
        assertEquals("Lossy", AudioInfoFormatter.classifyQuality("MP3", null))
        assertEquals("Lossy", AudioInfoFormatter.classifyQuality("mp3", null))
        assertEquals("Lossy", AudioInfoFormatter.classifyQuality("AAC", null))

        // M4A rules: must NOT assume Lossless or Lossy without codec
        assertNull(AudioInfoFormatter.classifyQuality("M4A", null))
        assertNull(AudioInfoFormatter.classifyQuality("m4a", ""))
        assertEquals("Lossless", AudioInfoFormatter.classifyQuality("M4A", "ALAC"))
        assertEquals("Lossless", AudioInfoFormatter.classifyQuality("m4a", "alac"))
        assertEquals("Lossy", AudioInfoFormatter.classifyQuality("M4A", "AAC"))
        assertEquals("Lossy", AudioInfoFormatter.classifyQuality("m4a", "aac"))
    }

    @Test
    fun testResolveDisplayCodec() {
        assertEquals("FLAC", AudioInfoFormatter.resolveDisplayCodec("FLAC", null))
        assertEquals("FLAC", AudioInfoFormatter.resolveDisplayCodec("FLAC", "FLAC"))
        assertEquals("MP3", AudioInfoFormatter.resolveDisplayCodec("MP3", "MP3"))
        assertEquals("AAC", AudioInfoFormatter.resolveDisplayCodec("M4A", "AAC"))
        assertEquals("ALAC", AudioInfoFormatter.resolveDisplayCodec("M4A", "ALAC"))
        assertEquals("M4A", AudioInfoFormatter.resolveDisplayCodec("M4A", null))
        assertEquals("WAV", AudioInfoFormatter.resolveDisplayCodec("WAV", null))
        assertNull(AudioInfoFormatter.resolveDisplayCodec(null, null))
    }

    @Test
    fun testPowerampStyleFormatting() {
        // Examples from user specification:
        // 44.1 kHz • 912 kbps • FLAC • 30.8 MB
        assertEquals(
            "44.1 kHz • 912 kbps • FLAC • 30.8 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "FLAC",
                sizeBytes = 32296140L,
                codec = "FLAC",
                bitrate = 911742,
                sampleRate = 44100,
            )
        )

        // 44.1 kHz • 256 kbps • AAC • 12.4 MB
        assertEquals(
            "44.1 kHz • 256 kbps • AAC • 12.4 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "M4A",
                sizeBytes = (12.4 * 1024 * 1024).toLong(),
                codec = "AAC",
                bitrate = 256000,
                sampleRate = 44100,
            )
        )

        // 44.1 kHz • 850 kbps • ALAC • 25.6 MB
        assertEquals(
            "44.1 kHz • 850 kbps • ALAC • 25.6 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "M4A",
                sizeBytes = (25.6 * 1024 * 1024).toLong(),
                codec = "ALAC",
                bitrate = 850000,
                sampleRate = 44100,
            )
        )

        // 96 kHz • 2684 kbps • FLAC • 105.2 MB
        assertEquals(
            "96 kHz • 2684 kbps • FLAC • 105.2 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "FLAC",
                sizeBytes = (105.2 * 1024 * 1024).toLong(),
                codec = "FLAC",
                bitrate = 2684000,
                sampleRate = 96000,
            )
        )

        // Fallback when technical values are still unavailable:
        // FLAC • Lossless • 30.8 MB
        assertEquals(
            "FLAC • Lossless • 30.8 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "FLAC",
                sizeBytes = 32296140L,
            )
        )

        // M4A • 28.8 MB (codec unknown)
        assertEquals(
            "M4A • 28.8 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "M4A",
                sizeBytes = (28.8 * 1024 * 1024).toLong(),
            )
        )
    }

    @Test
    fun testCompoundAudioInfoFormatting() {
        // Examples from user specification
        assertEquals(
            "FLAC • Lossless • 38.4 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "FLAC",
                sizeBytes = 40265318L,
            )
        )

        assertEquals(
            "MP3 • Lossy • 8.5 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "MP3",
                sizeBytes = 8912896L,
            )
        )

        assertEquals(
            "M4A • 23.4 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "M4A",
                sizeBytes = 24536678L,
            )
        )

        assertEquals(
            "FLAC • Lossless • 96 kHz • 38.4 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "FLAC",
                sizeBytes = 40265318L,
                sampleRate = 96000,
            )
        )

        assertEquals(
            "MP3 • Lossy • 320 kbps • 8.5 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "MP3",
                sizeBytes = 8912896L,
                bitrate = 320000,
            )
        )

        assertEquals(
            "M4A • Lossless • 24.5 MB",
            AudioInfoFormatter.formatAudioInfo(
                format = "M4A",
                sizeBytes = (24.5 * 1024 * 1024).toLong(),
                codec = "ALAC",
            )
        )

        // Safety: all null/empty
        assertNull(AudioInfoFormatter.formatAudioInfo(null, null))
        assertNull(AudioInfoFormatter.formatAudioInfo("", 0L))
        assertNull(AudioInfoFormatter.formatAudioInfo(null, null, null, null, null, null))
    }
}
