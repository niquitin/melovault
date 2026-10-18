package com.bsdung.musicassistantcar.data.telegram

import java.util.Locale

/**
 * Which quality pills ("viên") the Now Playing screens show under the title
 * (docs/ui-spec.md, "Hàng viên thông số chất lượng"). Pure JVM; views live in MeloQualityChips.
 * A missing number drops its pill: never "?", never "0", never a guessed value.
 */
internal object MeloQualityChipPolicy {

    enum class Kind { FORMAT, SAMPLING, BITRATE, SIZE, HI_RES }

    data class Chip(val kind: Kind, val text: String)

    const val HI_RES_LABEL = "Hi-Res"

    /** Read aloud by TalkBack for the Hi-Res pill; says what earned the badge. */
    const val HI_RES_DESCRIPTION = "Hi-Res: âm thanh lossless từ 24bit và 48kHz trở lên"

    /**
     * Pills in the fixed order Định dạng · bit/kHz · kbps · dung lượng · Hi-Res.
     * Lossless files show bit depth and rate but no bitrate; lossy files show the rate
     * and bitrate but no bit depth (the decoder's depth says nothing about a lossy source).
     */
    fun chips(format: String?, bitDepth: Int?, sampleRateHz: Int?, bitrateKbps: Int?, sizeBytes: Long?): List<Chip> {
        val chips = mutableListOf<Chip>()
        val name = format?.trim()?.takeIf { it.isNotEmpty() }
        val lossless = MeloComponentPolicy.isLossless(name)
        name?.let { chips.add(Chip(Kind.FORMAT, it)) }
        samplingLabel(lossless, bitDepth, sampleRateHz)?.let { chips.add(Chip(Kind.SAMPLING, it)) }
        if (!lossless) bitrateKbps?.takeIf { it > 0 }?.let { chips.add(Chip(Kind.BITRATE, "$it kbps")) }
        sizeBytes?.let { sizeLabel(it) }?.let { chips.add(Chip(Kind.SIZE, it)) }
        if (MeloComponentPolicy.isHiRes(name, sampleRateHz, bitDepth)) chips.add(Chip(Kind.HI_RES, HI_RES_LABEL))
        return chips
    }

    /** "24bit · 96kHz", "44.1kHz" or "24bit"; null when neither number is known. */
    fun samplingLabel(lossless: Boolean, bitDepth: Int?, sampleRateHz: Int?): String? {
        val depth = bitDepth?.takeIf { lossless && it > 0 }?.let { "${it}bit" }
        val rate = sampleRateHz?.takeIf { it > 0 }?.let { MeloComponentPolicy.formatKhz(it).replace(" ", "") }
        return listOfNotNull(depth, rate).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** "34,2 MB" (decimal comma, 1 MB = 1.000.000 bytes as in the song details); GB from 1.000 MB; null when unknown. */
    fun sizeLabel(sizeBytes: Long): String? {
        if (sizeBytes <= 0L) return null
        val megabytes = sizeBytes / 1_000_000.0
        val text = if (megabytes >= 1000.0) {
            String.format(Locale.US, "%.1f GB", megabytes / 1000.0)
        } else {
            String.format(Locale.US, "%.1f MB", megabytes)
        }
        return text.replace('.', ',')
    }

    /** One spoken/plain line for the whole row ("FLAC · 24bit · 96kHz · 34,2 MB · Hi-Res"); null when no pill. */
    fun describe(chips: List<Chip>): String? =
        chips.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.text }
}
