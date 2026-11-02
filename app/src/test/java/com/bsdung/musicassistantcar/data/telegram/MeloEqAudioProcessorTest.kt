package com.bsdung.musicassistantcar.data.telegram

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.bsdung.musicassistantcar.data.telegram.MeloEqPolicy.Preset
import com.bsdung.musicassistantcar.data.telegram.MeloEqPolicy.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class MeloEqAudioProcessorTest {

    private val encodings = intArrayOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT)

    private fun bytesPer(encoding: Int) = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        else -> 4
    }

    private fun processor(sr: Int, channels: Int, encoding: Int, settings: Settings): MeloEqAudioProcessor =
        MeloEqAudioProcessor().also {
            it.settings = settings
            val out = it.configure(AudioProcessor.AudioFormat(sr, channels, encoding))
            assertEquals(encoding, out.encoding)
            it.flush(AudioProcessor.StreamMetadata.DEFAULT)
        }

    /** Samples are interleaved, in [-1, 1]. */
    private fun encode(samples: DoubleArray, encoding: Int): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(samples.size * bytesPer(encoding)).order(ByteOrder.nativeOrder())
        for (v in samples) when (encoding) {
            C.ENCODING_PCM_16BIT -> buf.putShort((v * 32767.0).toInt().toShort())
            C.ENCODING_PCM_24BIT -> {
                val n = (v * 8388607.0).toInt()
                buf.put((n and 0xFF).toByte()); buf.put(((n ushr 8) and 0xFF).toByte()); buf.put(((n ushr 16) and 0xFF).toByte())
            }
            C.ENCODING_PCM_32BIT -> buf.putInt((v * 2147483647.0).toLong().toInt())
            else -> buf.putFloat(v.toFloat())
        }
        buf.flip()
        return buf
    }

    private fun decode(buf: ByteBuffer, encoding: Int): DoubleArray {
        buf.order(ByteOrder.nativeOrder())
        val out = ArrayList<Double>()
        while (buf.hasRemaining()) out.add(
            when (encoding) {
                C.ENCODING_PCM_16BIT -> buf.short / 32768.0
                C.ENCODING_PCM_24BIT -> {
                    val b0 = buf.get().toInt() and 0xFF
                    val b1 = buf.get().toInt() and 0xFF
                    val b2 = buf.get().toInt()
                    ((b2 shl 16) or (b1 shl 8) or b0) / 8388608.0
                }
                C.ENCODING_PCM_32BIT -> buf.int / 2147483648.0
                else -> buf.float.toDouble()
            }
        )
        return out.toDoubleArray()
    }

    /** Feeds [samples] in 1024-frame buffers and returns everything the processor produced. */
    private fun run(p: MeloEqAudioProcessor, samples: DoubleArray, channels: Int, encoding: Int): DoubleArray {
        val result = ArrayList<Double>()
        val step = 1024 * channels
        var i = 0
        while (i < samples.size) {
            val end = minOf(samples.size, i + step)
            p.queueInput(encode(samples.copyOfRange(i, end), encoding))
            result.addAll(decode(p.output, encoding).toList())
            i = end
        }
        return result.toDoubleArray()
    }

    private fun sine(freq: Double, sr: Int, seconds: Double, amp: Double, channels: Int = 1): DoubleArray {
        val frames = (sr * seconds).toInt()
        return DoubleArray(frames * channels) { amp * sin(2.0 * PI * freq * (it / channels) / sr) }
    }

    private fun rms(a: DoubleArray, from: Int): Double {
        var sum = 0.0
        for (i in from until a.size) sum += a[i] * a[i]
        return sqrt(sum / (a.size - from))
    }

    private fun db(x: Double) = 20.0 * log10(x)

    /** Level change in dB over the second half of the signal (the filter has settled by then). */
    private fun gainDb(input: DoubleArray, output: DoubleArray) = db(rms(output, output.size / 2) / rms(input, input.size / 2))

    private fun custom(vararg pairs: Pair<Int, Int>): Settings {
        val values = MutableList(10) { 0 }
        for ((index, db) in pairs) values[index] = db
        return Settings(true, Preset.CUSTOM, values)
    }

    @Test
    fun offAndFlatAreByteIdentical() {
        val inputs = listOf(Settings(false, Preset.ROCK), Settings(true, Preset.FLAT), Settings(true, Preset.CUSTOM, List(10) { 0 }))
        for (enc in encodings) for (settings in inputs) {
            val p = processor(44100, 2, enc, settings)
            val src = sine(1000.0, 44100, 0.05, 0.8, 2)
            val input = encode(src, enc)
            val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
            p.queueInput(input)
            val out = p.output
            assertEquals(expected.size, out.remaining())
            val actual = ByteArray(out.remaining()).also { out.get(it) }
            assertTrue("enc=$enc $settings", expected.contentEquals(actual))
        }
    }

    @Test
    fun unsupportedFormatIsSkipped() {
        val p = MeloEqAudioProcessor()
        val result = p.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_8BIT))
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, result)
        assertFalse(p.isActive)
    }

    @Test
    fun supportedFormatKeepsFormatAndStaysActive() {
        for (enc in encodings) {
            val p = MeloEqAudioProcessor()
            val fmt = AudioProcessor.AudioFormat(48000, 2, enc)
            assertEquals(fmt, p.configure(fmt))
            assertTrue(p.isActive)
        }
    }

    @Test
    fun boostedBandIsTrimmedByThePreamp() {
        val settings = custom(5 to 6) // +6 dB at 1 kHz, so the preamp is -6 dB
        for (enc in encodings) {
            val p = processor(44100, 1, enc, settings)
            val src = sine(1000.0, 44100, 1.0, 0.4)
            assertEquals("enc=$enc 1 kHz", 0.0, gainDb(src, run(p, src, 1, enc)), 1.0)

            val p2 = processor(44100, 1, enc, settings)
            val low = sine(62.0, 44100, 1.5, 0.4)
            assertEquals("enc=$enc 62 Hz", -6.0, gainDb(low, run(p2, low, 1, enc)), 1.0)
        }
    }

    @Test
    fun cutBandNeedsNoPreamp() {
        val p = processor(48000, 1, C.ENCODING_PCM_FLOAT, custom(5 to -6))
        val src = sine(1000.0, 48000, 1.0, 0.4)
        assertEquals(-6.0, gainDb(src, run(p, src, 1, C.ENCODING_PCM_FLOAT)), 1.0)
        val far = processor(48000, 1, C.ENCODING_PCM_FLOAT, custom(5 to -6))
        val high = sine(8000.0, 48000, 1.0, 0.4)
        assertEquals(0.0, gainDb(high, run(far, high, 1, C.ENCODING_PCM_FLOAT)), 1.0)
    }

    @Test
    fun fullScaleInputIsLimitedAndNeverNan() {
        val allUp = Settings(true, Preset.CUSTOM, List(10) { 12 })
        for (enc in encodings) {
            val p = processor(44100, 2, enc, allUp)
            val square = DoubleArray(44100 * 2) { if ((it / 2 / 22) % 2 == 0) 1.0 else -1.0 }
            val out = run(p, square, 2, enc)
            assertTrue("enc=$enc", out.all { it.isFinite() && abs(it) <= 1.0 })
        }
        val p = processor(44100, 1, C.ENCODING_PCM_FLOAT, allUp)
        val dirty = DoubleArray(4096) { if (it == 10) Double.NaN else if (it == 20) Double.POSITIVE_INFINITY else 0.5 }
        val out = run(p, dirty, 1, C.ENCODING_PCM_FLOAT)
        assertTrue(out.all { it.isFinite() && abs(it) <= 1.0 })
    }

    @Test
    fun changingSampleRateRecomputesTheCoefficients() {
        val settings = custom(5 to 6)
        val p = MeloEqAudioProcessor().also { it.settings = settings }
        for (sr in intArrayOf(44100, 96000, 22050)) {
            p.configure(AudioProcessor.AudioFormat(sr, 1, C.ENCODING_PCM_FLOAT))
            p.flush(AudioProcessor.StreamMetadata.DEFAULT)
            val src = sine(1000.0, sr, 1.0, 0.4)
            assertEquals("sr=$sr", 0.0, gainDb(src, run(p, src, 1, C.ENCODING_PCM_FLOAT)), 1.0)
        }
    }

    @Test
    fun bandsNearNyquistAreSkippedWithoutError() {
        // At 32 kHz the 16 kHz band is above 0.45 x sample rate: skipped, only the -12 dB preamp remains.
        val p = processor(32000, 1, C.ENCODING_PCM_FLOAT, custom(9 to 12))
        val src = sine(1000.0, 32000, 0.5, 0.4)
        val out = run(p, src, 1, C.ENCODING_PCM_FLOAT)
        assertTrue(out.all { it.isFinite() })
        assertEquals(-12.0, gainDb(src, out), 0.2)
    }

    @Test
    fun channelsStaySeparate() {
        val p = processor(44100, 2, C.ENCODING_PCM_FLOAT, custom(5 to 6, 2 to 4))
        val mono = sine(1000.0, 44100, 0.3, 0.5)
        val stereo = DoubleArray(mono.size * 2) { if (it % 2 == 0) mono[it / 2] else 0.0 }
        val out = run(p, stereo, 2, C.ENCODING_PCM_FLOAT)
        for (i in 1 until out.size step 2) assertEquals(0.0, out[i], 0.0)
        assertTrue((0 until out.size step 2).any { abs(out[it]) > 0.1 })
    }

    @Test
    fun flushDropsTheTailOfThePreviousSong() {
        val p = processor(44100, 1, C.ENCODING_PCM_FLOAT, custom(0 to 12, 1 to 12))
        val impulse = DoubleArray(2048).also { it[0] = 0.5 }
        val first = run(p, impulse, 1, C.ENCODING_PCM_FLOAT)
        assertTrue("sanity: the filter rings", first.drop(1024).any { abs(it) > 1e-6 })

        // Leave 1 sample of the impulse's ring in the filter, then flush (seek / next song).
        p.queueInput(encode(doubleArrayOf(0.5), C.ENCODING_PCM_FLOAT)); p.output
        p.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val silence = run(p, DoubleArray(2048), 1, C.ENCODING_PCM_FLOAT)
        assertTrue(silence.all { it == 0.0 })
    }

    @Test
    fun switchingOffAndBackOnStartsClean() {
        val p = processor(44100, 1, C.ENCODING_PCM_FLOAT, custom(0 to 12))
        run(p, sine(40.0, 44100, 0.2, 0.5), 1, C.ENCODING_PCM_FLOAT)
        p.settings = Settings(false, Preset.FLAT)
        run(p, DoubleArray(512), 1, C.ENCODING_PCM_FLOAT)
        p.settings = custom(0 to 12)
        val silence = run(p, DoubleArray(4096), 1, C.ENCODING_PCM_FLOAT)
        assertTrue(silence.all { it == 0.0 })
    }

    @Test
    fun changingPresetMidStreamStaysSmooth() {
        val p = processor(44100, 1, C.ENCODING_PCM_FLOAT, Settings(true, Preset.BASS_UP))
        val src = sine(200.0, 44100, 1.0, 0.4)
        val half = src.size / 2
        val before = run(p, src.copyOfRange(0, half), 1, C.ENCODING_PCM_FLOAT)
        p.settings = Settings(true, Preset.ROCK)
        val after = run(p, src.copyOfRange(half, src.size), 1, C.ENCODING_PCM_FLOAT)
        assertTrue((before + after).all { it.isFinite() && abs(it) <= 1.0 })
        // No sample-to-sample jump anywhere near the change (a 200 Hz sine moves < 0.05 per sample).
        val joined = before + after
        for (i in half - 50 until half + 50) assertTrue("i=$i", abs(joined[i + 1] - joined[i]) < 0.1)
    }

    @Test
    fun settingsAssignmentIsLoggedInDebugSink() {
        val lines = ArrayList<String>()
        val p = MeloEqAudioProcessor().also { it.log = { line -> lines.add(line) } }
        p.settings = Settings(true, Preset.BASS_UP)
        assertTrue(lines.single().contains("enabled=true preset=bass_up") && lines.single().contains("preampDb=-6"))
    }
}
