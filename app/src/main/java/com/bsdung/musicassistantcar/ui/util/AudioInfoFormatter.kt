package com.bsdung.musicassistantcar.ui.util

import java.util.Locale

object AudioInfoFormatter {

    /**
     * Determines quality classification deterministically.
     * Rules:
     * - FLAC -> "Lossless"
     * - WAV -> "Lossless"
     * - ALAC -> "Lossless" (only when codec or format explicitly says ALAC)
     * - MP3 -> "Lossy"
     * - AAC -> "Lossy"
     * - M4A -> null unless codec explicitly indicates ALAC (Lossless) or AAC (Lossy).
     *   Never assume Lossless or Lossy for raw M4A without codec proof.
     */
    fun classifyQuality(format: String?, codec: String?): String? {
        val fmt = format?.trim()?.uppercase(Locale.US)
        val cod = codec?.trim()?.uppercase(Locale.US)

        if (fmt == "ALAC" || cod == "ALAC") {
            return "Lossless"
        }
        if (fmt == "FLAC" || fmt == "WAV") {
            return "Lossless"
        }
        if (fmt == "MP3" || fmt == "AAC") {
            return "Lossy"
        }
        if (fmt == "M4A") {
            return when (cod) {
                "ALAC" -> "Lossless"
                "AAC" -> "Lossy"
                else -> null
            }
        }
        if (cod == "MP3" || cod == "AAC") {
            return "Lossy"
        }
        return null
    }

    /**
     * Formats file size in bytes to human-readable string:
     * - bytes < 1 GB -> MB with one decimal (e.g. 40265318 -> "38.4 MB")
     * - bytes >= 1 GB -> GB with two decimals
     * - omit if null or <= 0
     */
    fun formatFileSize(bytes: Long?): String? {
        if (bytes == null || bytes <= 0L) return null
        val gigabyte = 1024L * 1024L * 1024L
        return if (bytes >= gigabyte) {
            val gb = bytes.toDouble() / gigabyte.toDouble()
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    /**
     * Formats sample rate in Hz:
     * - 44100 -> "44.1 kHz"
     * - 48000 -> "48 kHz"
     * - 88200 -> "88.2 kHz"
     * - 96000 -> "96 kHz"
     * - 176400 -> "176.4 kHz"
     * - 192000 -> "192 kHz"
     * - omit if null or <= 0
     */
    fun formatSampleRate(hz: Int?): String? {
        if (hz == null || hz <= 0) return null
        val khz = hz.toDouble() / 1000.0
        val khzStr = if (hz % 1000 == 0) {
            "${hz / 1000}"
        } else if (hz % 100 == 0) {
            String.format(Locale.US, "%.1f", khz)
        } else if (hz % 10 == 0) {
            String.format(Locale.US, "%.2f", khz)
        } else {
            String.format(Locale.US, "%.3f", khz).trimEnd('0').trimEnd('.')
        }
        return "$khzStr kHz"
    }

    /**
     * Formats bitrate in bps:
     * - rounds to nearest kbps (e.g. 911742 -> "912 kbps", 256143 -> "256 kbps", 320000 -> "320 kbps")
     * - omit if null or <= 0
     */
    fun formatBitrate(bps: Int?): String? {
        if (bps == null || bps <= 0) return null
        val kbps = Math.round(bps.toDouble() / 1000.0).toInt()
        if (kbps <= 0) return null
        return "$kbps kbps"
    }

    /**
     * Resolves the display codec name:
     * - Prefers actual codec when known (FLAC, MP3, AAC, ALAC, WAV, PCM)
     * - Falls back to container format if codec is unknown
     * - Never assumes ALAC for unknown M4A
     */
    fun resolveDisplayCodec(format: String?, codec: String?): String? {
        val cod = codec?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
        val fmt = format?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
        return cod ?: fmt
    }

    /**
     * Builds the complete technical info string:
     * When sample rate and bitrate are both available, formats in Poweramp style:
     * Example: "44.1 kHz • 912 kbps • FLAC • 30.8 MB"
     * Example: "44.1 kHz • 256 kbps • AAC • 12.4 MB"
     * Example: "96 kHz • 2684 kbps • FLAC • 105.2 MB"
     *
     * Fallback when technical details are unextracted:
     * Example: "FLAC • Lossless • 30.8 MB"
     * Example: "M4A • 23.4 MB"
     */
    fun formatAudioInfo(
        format: String?,
        sizeBytes: Long?,
        codec: String? = null,
        bitrate: Int? = null,
        sampleRate: Int? = null,
        channels: Int? = null,
    ): String? {
        val fmt = format?.trim()?.uppercase(Locale.US)?.takeIf { it.isNotBlank() }
        val displayCodec = resolveDisplayCodec(fmt, codec)
        val bitrateStr = formatBitrate(bitrate)
        val sampleRateStr = formatSampleRate(sampleRate)
        val sizeStr = formatFileSize(sizeBytes)

        // When sample rate + bitrate are known, use compact Poweramp-style technical text
        if (sampleRateStr != null && bitrateStr != null) {
            val parts = listOfNotNull(
                sampleRateStr,
                bitrateStr,
                displayCodec,
                sizeStr,
            )
            return if (parts.isEmpty()) null else parts.joinToString(" • ")
        }

        // Fallback when technical values are still unavailable
        val qualityClass = classifyQuality(fmt, codec)
        val parts = listOfNotNull(
            fmt ?: displayCodec,
            qualityClass,
            bitrateStr,
            sampleRateStr,
            sizeStr,
        )

        return if (parts.isEmpty()) null else parts.joinToString(" • ")
    }
}
