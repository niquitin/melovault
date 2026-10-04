package com.bsdung.musicassistantcar.playback

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure, independently testable loudness normalization calculator for MeloVault v0.6.17.
 *
 * Implements EBU R128 / ITU-R BS.1770 based static gain calculation:
 * - TARGET_LUFS: -16.0 LUFS
 * - TRUE_PEAK_CEILING_DBTP: -1.0 dBTP
 * - SUPPORTED_ANALYSIS_VERSION: 1
 *
 * For valid metadata:
 *   raw_gain_db = TARGET_LUFS - integrated_lufs
 *   peak_safe_gain_db = TRUE_PEAK_CEILING_DBTP - true_peak_dbtp
 *   applied_gain_db = min(raw_gain_db, peak_safe_gain_db)
 *
 * Any missing, invalid, or unsupported metadata returns 0.0 dB (bypass / unity).
 */
object LoudnessNormalization {
    const val TARGET_LUFS: Double = -16.0
    const val TRUE_PEAK_CEILING_DBTP: Double = -1.0
    const val SUPPORTED_ANALYSIS_VERSION: Int = 1

    /**
     * Calculates applied gain in dB from canonical loudness measurements.
     *
     * Returns 0.0f (bypass / unity) if:
     * - [enabled] is false
     * - [integratedLufs] is null, NaN, or infinite
     * - [truePeakDbtp] is null, NaN, or infinite
     * - [analysisVersion] is null or != [SUPPORTED_ANALYSIS_VERSION] (1)
     * - calculated gain becomes non-finite
     */
    fun calculateAppliedGainDb(
        integratedLufs: Double?,
        truePeakDbtp: Double?,
        analysisVersion: Int?,
        enabled: Boolean = true,
    ): Float {
        if (!enabled) return 0.0f
        if (integratedLufs == null || !integratedLufs.isFinite()) return 0.0f
        if (truePeakDbtp == null || !truePeakDbtp.isFinite()) return 0.0f
        if (analysisVersion == null || analysisVersion != SUPPORTED_ANALYSIS_VERSION) return 0.0f

        val rawGainDb = TARGET_LUFS - integratedLufs
        val peakSafeGainDb = TRUE_PEAK_CEILING_DBTP - truePeakDbtp
        val appliedGainDb = min(rawGainDb, peakSafeGainDb)

        if (!appliedGainDb.isFinite()) return 0.0f
        return appliedGainDb.toFloat()
    }

    /**
     * Converts a gain in dB to a linear amplitude multiplier.
     *
     * multiplier = 10^(gainDb / 20)
     * Returns 1.0f if [gainDb] is non-finite or multiplier calculation fails.
     */
    fun dbToLinearMultiplier(gainDb: Float): Float {
        if (!gainDb.isFinite()) return 1.0f
        val multiplier = 10.0.pow(gainDb.toDouble() / 20.0).toFloat()
        return if (multiplier.isFinite() && multiplier >= 0.0f) multiplier else 1.0f
    }

    /**
     * Converts a linear amplitude multiplier to gain in dB.
     *
     * gainDb = 20 * log10(multiplier)
     * Returns 0.0f if [multiplier] is non-finite or non-positive.
     */
    fun linearMultiplierToDb(multiplier: Float): Float {
        if (!multiplier.isFinite() || multiplier <= 0.0f) return 0.0f
        val gainDb = (20.0 * log10(multiplier.toDouble())).toFloat()
        return if (gainDb.isFinite()) gainDb else 0.0f
    }
}
