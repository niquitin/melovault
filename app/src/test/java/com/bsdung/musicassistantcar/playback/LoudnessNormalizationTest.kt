package com.bsdung.musicassistantcar.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessNormalizationTest {

    // Test 1: disabled -> 0 dB
    @Test
    fun testDisabledReturnsZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = -3.0,
            analysisVersion = 1,
            enabled = false,
        )
        assertEquals(0.0f, gain, 0.0001f)
    }

    // Test 2: missing LUFS -> 0 dB
    @Test
    fun testMissingLufsReturnsZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = null,
            truePeakDbtp = -3.0,
            analysisVersion = 1,
        )
        assertEquals(0.0f, gain, 0.0001f)
    }

    // Test 3: missing TP -> 0 dB
    @Test
    fun testMissingTruePeakReturnsZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = null,
            analysisVersion = 1,
        )
        assertEquals(0.0f, gain, 0.0001f)
    }

    // Test 4: missing version -> 0 dB
    @Test
    fun testMissingVersionReturnsZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = -3.0,
            analysisVersion = null,
        )
        assertEquals(0.0f, gain, 0.0001f)
    }

    // Test 5: unsupported version -> 0 dB
    @Test
    fun testUnsupportedVersionReturnsZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = -3.0,
            analysisVersion = 2,
        )
        assertEquals(0.0f, gain, 0.0001f)
    }

    // Test 6: NaN LUFS -> 0 dB
    @Test
    fun testNanLufsReturnsZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = Double.NaN,
            truePeakDbtp = -3.0,
            analysisVersion = 1,
        )
        assertEquals(0.0f, gain, 0.0001f)
    }

    // Test 7: Inf LUFS -> 0 dB
    @Test
    fun testInfLufsReturnsZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = Double.POSITIVE_INFINITY,
            truePeakDbtp = -3.0,
            analysisVersion = 1,
        )
        assertEquals(0.0f, gain, 0.0001f)

        val gainNegInf = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = Double.NEGATIVE_INFINITY,
            truePeakDbtp = -3.0,
            analysisVersion = 1,
        )
        assertEquals(0.0f, gainNegInf, 0.0001f)
    }

    // Test 8: NaN TP -> 0 dB
    @Test
    fun testNanTruePeakReturnsZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = Double.NaN,
            analysisVersion = 1,
        )
        assertEquals(0.0f, gain, 0.0001f)

        val gainInfTp = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = Double.POSITIVE_INFINITY,
            analysisVersion = 1,
        )
        assertEquals(0.0f, gainInfTp, 0.0001f)
    }

    // Test 9: -16 LUFS -> raw 0 dB
    @Test
    fun testTargetLufsReturnsRawZeroDb() {
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -16.0,
            truePeakDbtp = -1.0,
            analysisVersion = 1,
        )
        assertEquals(0.0f, gain, 0.0001f)
    }

    // Test 10: -20 LUFS / safe peak -> +4 dB
    @Test
    fun testQuieterTrackWithSafePeakReceivesFullPositiveGain() {
        // raw = -16 - (-20) = +4.0 dB
        // safe = -1.0 - (-6.0) = +5.0 dB
        // min(4.0, 5.0) = +4.0 dB
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = -6.0,
            analysisVersion = 1,
        )
        assertEquals(4.0f, gain, 0.0001f)
    }

    // Test 11: -12 LUFS -> -4 dB
    @Test
    fun testLouderTrackReceivesAttenuation() {
        // raw = -16 - (-12) = -4.0 dB
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -12.0,
            truePeakDbtp = -0.5,
            analysisVersion = 1,
        )
        assertEquals(-4.0f, gain, 0.0001f)
    }

    // Test 12: positive gain capped by true peak ceiling
    @Test
    fun testPositiveGainCappedByTruePeakCeiling() {
        // raw = -16 - (-20) = +4.0 dB
        // safe = -1.0 - (-2.0) = +1.0 dB
        // applied = min(4.0, 1.0) = +1.0 dB
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = -2.0,
            analysisVersion = 1,
        )
        assertEquals(1.0f, gain, 0.0001f)
    }

    // Test 13: positive raw gain may become negative due to peak ceiling
    @Test
    fun testPositiveRawGainBecomesNegativeDueToTruePeakCeiling() {
        // raw = -16 - (-18) = +2.0 dB
        // safe = -1.0 - (+0.5) = -1.5 dB
        // applied = min(2.0, -1.5) = -1.5 dB
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -18.0,
            truePeakDbtp = 0.5,
            analysisVersion = 1,
        )
        assertEquals(-1.5f, gain, 0.0001f)
    }

    // Test 14: negative raw gain when raw attenuation is deeper than peak ceiling
    @Test
    fun testNegativeRawGainDeeperThanPeakCeiling() {
        // raw = -16 - (-14) = -2.0 dB
        // safe = -1.0 - (+0.5) = -1.5 dB
        // min(-2.0, -1.5) = -2.0 dB (raw is already deeper attenuation)
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -14.0,
            truePeakDbtp = 0.5,
            analysisVersion = 1,
        )
        assertEquals(-2.0f, gain, 0.0001f)
    }

    // --- Mandatory Hotfix Cases (v0.6.18) ---

    // Case A: positive gain, peak limited
    @Test
    fun testMandatoryCaseA_PositiveGainPeakLimited() {
        // LUFS = -23.59, TP = -7.33
        // raw = -16.0 - (-23.59) = +7.59 dB
        // safe = -1.0 - (-7.33) = +6.33 dB
        // applied = min(7.59, 6.33) = +6.33 dB
        // predicted output TP = -7.33 + 6.33 = -1.00 dBTP
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -23.59,
            truePeakDbtp = -7.33,
            analysisVersion = 1,
        )
        assertEquals(6.33f, gain, 0.0001f)
        val outputTp = -7.33f + gain
        assertEquals(-1.0f, outputTp, 0.0001f)
    }

    // Case B: normal attenuation already safer than ceiling
    @Test
    fun testMandatoryCaseB_NormalAttenuationAlreadySaferThanCeiling() {
        // LUFS = -12.0, TP = -1.0
        // raw = -16.0 - (-12.0) = -4.0 dB
        // safe = -1.0 - (-1.0) = 0.0 dB
        // applied = min(-4.0, 0.0) = -4.0 dB
        // predicted output TP = -1.0 + (-4.0) = -5.0 dBTP
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -12.0,
            truePeakDbtp = -1.0,
            analysisVersion = 1,
        )
        assertEquals(-4.0f, gain, 0.0001f)
        val outputTp = -1.0f + gain
        assertEquals(-5.0f, outputTp, 0.0001f)
    }

    // Case C: mild attenuation insufficient for hot peak (Critical v0.6.17 regression fix)
    @Test
    fun testMandatoryCaseC_MildAttenuationInsufficientForHotPeak() {
        // LUFS = -15.5, TP = +1.0
        // raw = -16.0 - (-15.5) = -0.5 dB
        // safe = -1.0 - (+1.0) = -2.0 dB
        // applied = min(-0.5, -2.0) = -2.0 dB
        // predicted output TP = +1.0 + (-2.0) = -1.0 dBTP
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -15.5,
            truePeakDbtp = 1.0,
            analysisVersion = 1,
        )
        assertEquals(-2.0f, gain, 0.0001f)
        val outputTp = 1.0f + gain
        assertEquals(-1.0f, outputTp, 0.0001f)
    }

    // Case D: near-target loudness with very hot peak
    @Test
    fun testMandatoryCaseD_NearTargetLoudnessWithVeryHotPeak() {
        // LUFS = -15.9, TP = +2.0
        // raw = -16.0 - (-15.9) = -0.1 dB
        // safe = -1.0 - (+2.0) = -3.0 dB
        // applied = min(-0.1, -3.0) = -3.0 dB
        // predicted output TP = +2.0 + (-3.0) = -1.0 dBTP
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -15.9,
            truePeakDbtp = 2.0,
            analysisVersion = 1,
        )
        assertEquals(-3.0f, gain, 0.0001f)
        val outputTp = 2.0f + gain
        assertEquals(-1.0f, outputTp, 0.0001f)
    }

    // Case E: louder source with safe peak headroom
    @Test
    fun testMandatoryCaseE_PositiveGainBoundedByPeakCeiling() {
        // LUFS = -20.0, TP = -3.0
        // raw = -16.0 - (-20.0) = +4.0 dB
        // safe = -1.0 - (-3.0) = +2.0 dB
        // applied = min(4.0, 2.0) = +2.0 dB
        // predicted output TP = -3.0 + (+2.0) = -1.0 dBTP
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -20.0,
            truePeakDbtp = -3.0,
            analysisVersion = 1,
        )
        assertEquals(2.0f, gain, 0.0001f)
        val outputTp = -3.0f + gain
        assertEquals(-1.0f, outputTp, 0.0001f)
    }

    // Case F: hot source attenuation deeper than peak constraint
    @Test
    fun testMandatoryCaseF_HotSourceAttenuationDeeperThanPeakConstraint() {
        // LUFS = -12.0, TP = -0.5
        // raw = -16.0 - (-12.0) = -4.0 dB
        // safe = -1.0 - (-0.5) = -0.5 dB
        // applied = min(-4.0, -0.5) = -4.0 dB
        // predicted output TP = -0.5 + (-4.0) = -4.5 dBTP
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -12.0,
            truePeakDbtp = -0.5,
            analysisVersion = 1,
        )
        assertEquals(-4.0f, gain, 0.0001f)
        val outputTp = -0.5f + gain
        assertEquals(-4.5f, outputTp, 0.0001f)
    }

    // Invariant Property Test: grid over representative finite values
    @Test
    fun testGridPropertyInvariantHardCeiling() {
        val lufsValues = doubleArrayOf(
            -36.0, -30.0, -25.0, -23.59, -20.0, -18.0,
            -16.0, -15.9, -15.5, -14.0, -12.0, -8.0, -4.0, 0.0
        )
        val tpValues = doubleArrayOf(
            -20.0, -15.0, -10.0, -7.33, -5.0, -3.0, -2.0,
            -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 6.0
        )

        for (lufs in lufsValues) {
            for (tp in tpValues) {
                val applied = LoudnessNormalization.calculateAppliedGainDb(
                    integratedLufs = lufs,
                    truePeakDbtp = tp,
                    analysisVersion = 1,
                    enabled = true,
                )
                val expectedApplied = kotlin.math.min(
                    LoudnessNormalization.TARGET_LUFS - lufs,
                    LoudnessNormalization.TRUE_PEAK_CEILING_DBTP - tp,
                ).toFloat()

                assertEquals(
                    "Applied gain mismatch for LUFS=$lufs, TP=$tp",
                    expectedApplied,
                    applied,
                    0.0001f,
                )

                val predictedOutputTp = tp + applied.toDouble()
                assertTrue(
                    "Output TP ($predictedOutputTp) must be <= ceiling (-1.0) for LUFS=$lufs, TP=$tp",
                    predictedOutputTp <= LoudnessNormalization.TRUE_PEAK_CEILING_DBTP + 0.0001,
                )
            }
        }
    }

    // Test 15: dB->linear 0 dB = 1.0
    @Test
    fun testDbToLinearZeroDbIsUnity() {
        val mult = LoudnessNormalization.dbToLinearMultiplier(0.0f)
        assertEquals(1.0f, mult, 0.0001f)
    }

    // Test 16: dB->linear -6.0206 ≈ 0.5
    @Test
    fun testDbToLinearNegativeSixDbIsApproximatelyHalf() {
        val mult = LoudnessNormalization.dbToLinearMultiplier(-6.0206f)
        assertEquals(0.5f, mult, 0.001f)
    }

    // Test 17: finite calculations only
    @Test
    fun testFiniteCalculationsOnly() {
        val multNan = LoudnessNormalization.dbToLinearMultiplier(Float.NaN)
        assertEquals(1.0f, multNan, 0.0001f)

        val multInf = LoudnessNormalization.dbToLinearMultiplier(Float.POSITIVE_INFINITY)
        assertEquals(1.0f, multInf, 0.0001f)

        val multNegInf = LoudnessNormalization.dbToLinearMultiplier(Float.NEGATIVE_INFINITY)
        assertEquals(1.0f, multNegInf, 0.0001f)

        val dbNan = LoudnessNormalization.linearMultiplierToDb(Float.NaN)
        assertEquals(0.0f, dbNan, 0.0001f)

        val dbZero = LoudnessNormalization.linearMultiplierToDb(0.0f)
        assertEquals(0.0f, dbZero, 0.0001f)

        val dbNeg = LoudnessNormalization.linearMultiplierToDb(-1.0f)
        assertEquals(0.0f, dbNeg, 0.0001f)

        val dbUnity = LoudnessNormalization.linearMultiplierToDb(1.0f)
        assertEquals(0.0f, dbUnity, 0.0001f)

        assertTrue(multNan.isFinite())
        assertTrue(multInf.isFinite())
        assertTrue(dbNan.isFinite())
    }
}
