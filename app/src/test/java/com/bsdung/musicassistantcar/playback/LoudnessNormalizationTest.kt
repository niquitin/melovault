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

    // Test 14: negative gain not incorrectly peak-capped
    @Test
    fun testNegativeGainNotIncorrectlyPeakCapped() {
        // raw = -16 - (-14) = -2.0 dB
        // safe = -1.0 - (+0.5) = -1.5 dB
        // Since raw <= 0, applied = raw = -2.0 dB
        val gain = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = -14.0,
            truePeakDbtp = 0.5,
            analysisVersion = 1,
        )
        assertEquals(-2.0f, gain, 0.0001f)
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
