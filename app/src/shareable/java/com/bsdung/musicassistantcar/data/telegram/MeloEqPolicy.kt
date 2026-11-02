package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rules for the equalizer opened from the Studio Deck "EQ" chip. Pure JVM except the
 * SharedPreferences helpers; the audio side is [MeloEqAudioProcessor], which reads the same
 * [Settings] so the panel only ever writes preferences.
 */
internal object MeloEqPolicy {

    /** Rollback switch: false keeps the stock player pipeline and the old "EQ sắp có" chip. */
    const val FEATURE_ENABLED = true

    const val KEY_ENABLED = "eq_enabled"
    const val KEY_PRESET = "eq_preset"
    const val KEY_CUSTOM_DB = "eq_custom_db"

    /** Preferences file shared with the playback service, same as the prefetch switch. */
    const val PREFS = MeloNowPlayingPolicy.PLAYBACK_PREFS

    /** ISO centre frequencies, one peaking filter each. */
    val BANDS_HZ = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val BAND_COUNT get() = BANDS_HZ.size
    const val MIN_DB = -12
    const val MAX_DB = 12
    /** About one octave wide. */
    const val Q = 1.41

    enum class Preset(val key: String, val label: String, val gains: List<Int>) {
        FLAT("flat", "Phẳng", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
        BASS_UP("bass_up", "Tăng bass", listOf(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
        BASS_DOWN("bass_down", "Giảm bass", listOf(-6, -5, -4, -2, 0, 0, 0, 0, 0, 0)),
        TREBLE_UP("treble_up", "Tăng treble", listOf(0, 0, 0, 0, 0, 1, 2, 4, 5, 6)),
        VOCAL("vocal", "Giọng hát", listOf(-2, -2, -1, 0, 2, 4, 4, 2, 0, -1)),
        ROCK("rock", "Rock", listOf(5, 4, 2, 0, -1, -1, 1, 3, 4, 5)),
        POP("pop", "Pop", listOf(-1, 0, 2, 4, 4, 2, 0, -1, -1, -1)),
        JAZZ("jazz", "Jazz", listOf(3, 2, 1, 2, -1, -1, 0, 1, 2, 3)),
        CLASSICAL("classical", "Cổ điển", listOf(4, 3, 2, 1, 0, 0, 0, 2, 3, 4)),
        /** The user's own bars; the values live in [Settings.custom]. */
        CUSTOM("custom", "Tùy chỉnh", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        companion object {
            fun fromKey(key: String?): Preset = entries.firstOrNull { it.key == key } ?: FLAT
        }
    }

    private val FLAT_GAINS = List(BANDS_HZ.size) { 0 }

    /** Immutable, so the audio thread can read one reference and compare it by value. */
    data class Settings(
        val enabled: Boolean = false,
        val preset: Preset = Preset.FLAT,
        val custom: List<Int> = FLAT_GAINS,
    ) {
        /** The dB of every band as currently selected. */
        fun gains(): List<Int> = if (preset == Preset.CUSTOM) custom else preset.gains

        fun isFlat(): Boolean = gains().all { it == 0 }

        /** Off, or on with every bar at 0 dB: the processor passes the audio through byte for byte. */
        fun isBypass(): Boolean = !enabled || isFlat()

        /** Automatic input trim, so the loudest boost cannot push the signal past full scale. */
        fun preampDb(): Double = -(gains().maxOrNull() ?: 0).coerceAtLeast(0).toDouble()
    }

    // --- preferences -------------------------------------------------------------------------

    /** Never throws: a damaged or missing value falls back to the default (off, flat). */
    fun load(prefs: SharedPreferences): Settings = try {
        fromRaw(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            presetKey = prefs.getString(KEY_PRESET, null),
            customCsv = prefs.getString(KEY_CUSTOM_DB, null),
        )
    } catch (_: Exception) {
        Settings()
    }

    fun save(editor: SharedPreferences.Editor, settings: Settings): SharedPreferences.Editor = editor
        .putBoolean(KEY_ENABLED, settings.enabled)
        .putString(KEY_PRESET, settings.preset.key)
        .putString(KEY_CUSTOM_DB, toCsv(settings.custom))

    fun isEqKey(key: String?): Boolean = key == KEY_ENABLED || key == KEY_PRESET || key == KEY_CUSTOM_DB

    internal fun fromRaw(enabled: Boolean, presetKey: String?, customCsv: String?): Settings =
        Settings(enabled, Preset.fromKey(presetKey), parseCsv(customCsv))

    internal fun toCsv(values: List<Int>): String = values.joinToString(",")

    /** Exactly [BAND_COUNT] integers, each clamped to the range; anything else is all zeros. */
    internal fun parseCsv(csv: String?): List<Int> {
        val parts = csv?.split(',')?.map { it.trim().toIntOrNull() }
        if (parts == null || parts.size != BAND_COUNT || parts.any { it == null }) return FLAT_GAINS
        return parts.map { clampDb(it!!) }
    }

    fun clampDb(db: Int): Int = db.coerceIn(MIN_DB, MAX_DB)

    // --- panel / chip text -------------------------------------------------------------------

    fun chipLabel(settings: Settings): String =
        if (settings.enabled) "EQ · ${settings.preset.label}" else "EQ"

    fun chipDescription(settings: Settings): String =
        if (settings.enabled) "Bộ chỉnh âm, đang bật, mẫu ${settings.preset.label}" else "Bộ chỉnh âm, đang tắt"

    fun bandLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

    /** "+3 dB", "0 dB", "−4 dB" (real minus sign, U+2212). */
    fun gainLabel(db: Int): String = when {
        db > 0 -> "+$db dB"
        db < 0 -> "−${-db} dB"
        else -> "0 dB"
    }

    fun bandDescription(hz: Int, db: Int): String = when {
        db > 0 -> "Dải ${bandLabel(hz)}, cộng $db dB"
        db < 0 -> "Dải ${bandLabel(hz)}, trừ ${-db} dB"
        else -> "Dải ${bandLabel(hz)}, 0 dB"
    }

    // --- edits -------------------------------------------------------------------------------

    /** Dragging a bar switches to "Tùy chỉnh", starting from the values that were showing. */
    fun onBandDragged(settings: Settings, index: Int, db: Int): Settings {
        if (index !in 0 until BAND_COUNT) return settings
        val values = settings.gains().toMutableList()
        values[index] = clampDb(db)
        return settings.copy(preset = Preset.CUSTOM, custom = values)
    }

    /** "Đặt lại": flat bars, the On/Off switch stays where it was. */
    fun reset(settings: Settings): Settings = settings.copy(preset = Preset.FLAT, custom = FLAT_GAINS)

    // --- filter maths (shared with the processor and its tests) ----------------------------------

    object Biquad {
        /** Normalised so that a0 = 1; y = b0·x + z1, z1 = b1·x − a1·y + z2, z2 = b2·x − a2·y. */
        data class Coeffs(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
            val isNeutral: Boolean get() = b0 == 1.0 && b1 == 0.0 && b2 == 0.0 && a1 == 0.0 && a2 == 0.0
        }

        val NEUTRAL = Coeffs(1.0, 0.0, 0.0, 0.0, 0.0)

        /** RBJ Audio EQ Cookbook peaking filter. A gain of 0 dB gives exactly [NEUTRAL]. */
        fun peaking(fHz: Double, gainDb: Double, q: Double, sampleRate: Double): Coeffs {
            if (gainDb == 0.0) return NEUTRAL
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * fHz / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW = cos(w0)
            val a0 = 1.0 + alpha / a
            return Coeffs(
                b0 = (1.0 + alpha * a) / a0,
                b1 = (-2.0 * cosW) / a0,
                b2 = (1.0 - alpha * a) / a0,
                a1 = (-2.0 * cosW) / a0,
                a2 = (1.0 - alpha / a) / a0,
            )
        }

        /** Magnitude response in dB at [fHz], for tests and for sanity checks of the curve. */
        fun magnitudeDb(c: Coeffs, fHz: Double, sampleRate: Double): Double {
            val w = 2.0 * PI * fHz / sampleRate
            val cos1 = cos(w)
            val sin1 = sin(w)
            val cos2 = cos(2 * w)
            val sin2 = sin(2 * w)
            val numRe = c.b0 + c.b1 * cos1 + c.b2 * cos2
            val numIm = -(c.b1 * sin1 + c.b2 * sin2)
            val denRe = 1.0 + c.a1 * cos1 + c.a2 * cos2
            val denIm = -(c.a1 * sin1 + c.a2 * sin2)
            val num = sqrt(numRe * numRe + numIm * numIm)
            val den = sqrt(denRe * denRe + denIm * denIm)
            return 20.0 * log10(abs(num / den))
        }
    }
}
