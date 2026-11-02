package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloEqPolicy.Biquad
import com.bsdung.musicassistantcar.data.telegram.MeloEqPolicy.Preset
import com.bsdung.musicassistantcar.data.telegram.MeloEqPolicy.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MeloEqPolicyTest {

    @Test
    fun defaultsAreOffAndFlat() {
        val s = Settings()
        assertFalse(s.enabled)
        assertEquals(Preset.FLAT, s.preset)
        assertEquals(List(10) { 0 }, s.gains())
        assertTrue(s.isFlat())
        assertTrue(s.isBypass())
    }

    @Test
    fun everyPresetHasTenBandsInRange() {
        for (p in Preset.entries) {
            assertEquals(p.key, 10, p.gains.size)
            assertTrue(p.key, p.gains.all { it in MeloEqPolicy.MIN_DB..MeloEqPolicy.MAX_DB })
            assertEquals(p, Preset.fromKey(p.key))
        }
        assertEquals(Preset.FLAT, Preset.fromKey(null))
        assertEquals(Preset.FLAT, Preset.fromKey("bogus"))
        assertEquals(10, MeloEqPolicy.BANDS_HZ.size)
    }

    @Test
    fun loadFromEmptyOrDamagedValuesFallsBack() {
        assertEquals(Settings(), MeloEqPolicy.fromRaw(false, null, null))
        assertEquals(List(10) { 0 }, MeloEqPolicy.parseCsv(null))
        assertEquals(List(10) { 0 }, MeloEqPolicy.parseCsv(""))
        assertEquals(List(10) { 0 }, MeloEqPolicy.parseCsv("1,2,3"))
        assertEquals(List(10) { 0 }, MeloEqPolicy.parseCsv("1,2,3,4,5,6,7,8,9,10,11"))
        assertEquals(List(10) { 0 }, MeloEqPolicy.parseCsv("1,2,3,4,5,x,7,8,9,10"))
        assertEquals(List(10) { 0 }, MeloEqPolicy.parseCsv("1,2,3,4,5,,7,8,9,10"))
        assertEquals(Preset.FLAT, MeloEqPolicy.fromRaw(true, "no_such_preset", null).preset)
    }

    @Test
    fun loadClampsOutOfRangeValues() {
        assertEquals(
            listOf(-12, 12, -12, 12, 0, 1, -1, 12, -12, 5),
            MeloEqPolicy.parseCsv("-99, 99,-13,13,0,1,-1,12,-12,5"),
        )
    }

    @Test
    fun saveThenLoadRoundTrips() {
        val original = Settings(true, Preset.CUSTOM, listOf(1, -2, 3, -4, 5, -6, 7, -8, 9, -10))
        val back = MeloEqPolicy.fromRaw(original.enabled, original.preset.key, MeloEqPolicy.toCsv(original.custom))
        assertEquals(original, back)
        assertEquals("1,-2,3,-4,5,-6,7,-8,9,-10", MeloEqPolicy.toCsv(original.custom))
    }

    @Test
    fun settingsCompareByValue() {
        assertEquals(Settings(true, Preset.ROCK), Settings(true, Preset.ROCK))
        assertEquals(Settings(true, Preset.CUSTOM, List(10) { 2 }).hashCode(), Settings(true, Preset.CUSTOM, List(10) { 2 }).hashCode())
    }

    @Test
    fun eqKeysAreRecognised() {
        assertTrue(MeloEqPolicy.isEqKey("eq_enabled"))
        assertTrue(MeloEqPolicy.isEqKey("eq_preset"))
        assertTrue(MeloEqPolicy.isEqKey("eq_custom_db"))
        assertFalse(MeloEqPolicy.isEqKey("prefetch_next"))
        assertFalse(MeloEqPolicy.isEqKey(null))
    }

    @Test
    fun preampFollowsLoudestBoost() {
        assertEquals(-6.0, Settings(true, Preset.BASS_UP).preampDb(), 0.0)
        assertEquals(0.0, Settings(true, Preset.BASS_DOWN).preampDb(), 0.0)
        assertEquals(0.0, Settings(true, Preset.FLAT).preampDb(), 0.0)
        assertEquals(-5.0, Settings(true, Preset.ROCK).preampDb(), 0.0)
        assertEquals(-12.0, Settings(true, Preset.CUSTOM, List(10) { 12 }).preampDb(), 0.0)
    }

    @Test
    fun bypassWhenOffOrFlat() {
        assertTrue(Settings(false, Preset.ROCK).isBypass())
        assertTrue(Settings(true, Preset.FLAT).isBypass())
        assertTrue(Settings(true, Preset.CUSTOM, List(10) { 0 }).isBypass())
        assertFalse(Settings(true, Preset.ROCK).isBypass())
    }

    @Test
    fun labelsUseVietnameseTextAndRealMinus() {
        assertEquals("EQ", MeloEqPolicy.chipLabel(Settings(false, Preset.ROCK)))
        assertEquals("EQ · Rock", MeloEqPolicy.chipLabel(Settings(true, Preset.ROCK)))
        assertEquals("EQ · Tùy chỉnh", MeloEqPolicy.chipLabel(Settings(true, Preset.CUSTOM)))
        assertEquals("Bộ chỉnh âm, đang tắt", MeloEqPolicy.chipDescription(Settings()))
        assertEquals("Bộ chỉnh âm, đang bật, mẫu Rock", MeloEqPolicy.chipDescription(Settings(true, Preset.ROCK)))
        assertEquals("31 Hz", MeloEqPolicy.bandLabel(31))
        assertEquals("1 kHz", MeloEqPolicy.bandLabel(1000))
        assertEquals("16 kHz", MeloEqPolicy.bandLabel(16000))
        assertEquals("+3 dB", MeloEqPolicy.gainLabel(3))
        assertEquals("0 dB", MeloEqPolicy.gainLabel(0))
        assertEquals("−4 dB", MeloEqPolicy.gainLabel(-4))
        assertEquals("Dải 31 Hz, cộng 3 dB", MeloEqPolicy.bandDescription(31, 3))
        assertEquals("Dải 1 kHz, trừ 4 dB", MeloEqPolicy.bandDescription(1000, -4))
        assertEquals("Dải 16 kHz, 0 dB", MeloEqPolicy.bandDescription(16000, 0))
    }

    @Test
    fun draggingFromAPresetKeepsItsOtherBands() {
        val rock = Settings(true, Preset.ROCK)
        val dragged = MeloEqPolicy.onBandDragged(rock, 5, 4)
        assertEquals(Preset.CUSTOM, dragged.preset)
        assertTrue(dragged.enabled)
        val expected = Preset.ROCK.gains.toMutableList().also { it[5] = 4 }
        assertEquals(expected, dragged.custom)
        assertEquals(expected, dragged.gains())
    }

    @Test
    fun draggingWhileOffDoesNotTurnItOn() {
        val dragged = MeloEqPolicy.onBandDragged(Settings(false, Preset.FLAT), 0, 6)
        assertFalse(dragged.enabled)
        assertEquals(6, dragged.gains()[0])
    }

    @Test
    fun draggingClampsAndIgnoresBadIndex() {
        assertEquals(12, MeloEqPolicy.onBandDragged(Settings(), 0, 40).gains()[0])
        assertEquals(-12, MeloEqPolicy.onBandDragged(Settings(), 0, -40).gains()[0])
        val s = Settings(true, Preset.POP)
        assertEquals(s, MeloEqPolicy.onBandDragged(s, 10, 3))
        assertEquals(s, MeloEqPolicy.onBandDragged(s, -1, 3))
    }

    @Test
    fun resetGoesFlatAndKeepsOnOff() {
        val r = MeloEqPolicy.reset(Settings(true, Preset.CUSTOM, List(10) { 7 }))
        assertTrue(r.enabled)
        assertEquals(Preset.FLAT, r.preset)
        assertTrue(r.isFlat())
        assertFalse(MeloEqPolicy.reset(Settings(false, Preset.ROCK)).enabled)
    }

    @Test
    fun biquadZeroGainIsNeutral() {
        assertTrue(Biquad.peaking(1000.0, 0.0, MeloEqPolicy.Q, 44100.0).isNeutral)
    }

    @Test
    fun biquadPeaksAtItsCentreAndIsFlatFarAway() {
        for (sr in doubleArrayOf(44100.0, 48000.0, 96000.0)) {
            for (gain in doubleArrayOf(6.0, 12.0, -6.0, -12.0)) {
                for (f in doubleArrayOf(125.0, 1000.0, 4000.0)) {
                    val c = Biquad.peaking(f, gain, MeloEqPolicy.Q, sr)
                    assertEquals("sr=$sr g=$gain f=$f centre", gain, Biquad.magnitudeDb(c, f, sr), 0.5)
                    // Three octaves away the curve is back to ~0 dB.
                    assertTrue(abs(Biquad.magnitudeDb(c, f * 8, sr)) < 1.0 || f * 8 > sr * 0.4)
                    assertEquals("sr=$sr g=$gain f=$f low", 0.0, Biquad.magnitudeDb(c, f / 8, sr), 1.0)
                }
            }
        }
    }
}
