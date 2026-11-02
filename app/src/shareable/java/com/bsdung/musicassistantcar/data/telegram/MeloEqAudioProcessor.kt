package com.bsdung.musicassistantcar.data.telegram

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Ten-band equalizer as a Media3 [AudioProcessor] (the same shape as the Personal build's
 * GainAudioProcessor): one peaking biquad per band in series, then an automatic input trim so a
 * boost cannot clip. Output keeps the input format, so the sink never reconfigures.
 *
 * Supported formats: PCM 16/24/32-bit and float. Anything else returns [AudioProcessor.AudioFormat.NOT_SET],
 * which skips the processor without failing the sink.
 *
 * The processor is always active for PCM; "off" is a byte-for-byte copy inside [queueInput] (toggling
 * isActive would make Media3 flush the sink and could cut the sound).
 *
 * Threading: the UI/service thread only assigns [settings], an immutable value. The audio thread
 * reads that one reference at the top of each [queueInput]; no locks.
 */
internal class MeloEqAudioProcessor : BaseAudioProcessor() {

    /** Debug-only sink (tag MeloEq in the service); null means no logging and no statistics work. */
    @Volatile
    var log: ((String) -> Unit)? = null

    @Volatile
    var settings: MeloEqPolicy.Settings = MeloEqPolicy.Settings()
        set(value) {
            field = value
            log?.invoke(
                "settings enabled=${value.enabled} preset=${value.preset.key} " +
                    "gains=${value.gains().joinToString(",")} preampDb=${value.preampDb().toInt()}"
            )
        }

    // Audio-thread state.
    private var appliedSettings: MeloEqPolicy.Settings? = null
    private var appliedSampleRate = 0
    private var appliedChannels = 0
    private var coeffs = Array(MeloEqPolicy.BAND_COUNT) { MeloEqPolicy.Biquad.NEUTRAL }
    private var active = BooleanArray(MeloEqPolicy.BAND_COUNT)
    private var z1 = DoubleArray(0)
    private var z2 = DoubleArray(0)
    private var preampCurrent = 1.0
    private var preampTarget = 1.0
    private var wasBypass = true

    // Statistics (only while [log] is set).
    private var statFrames = 0L
    private var statSumIn = 0.0
    private var statSumOut = 0.0
    private var statSamples = 0L
    private var statClipped = 0L
    private var statNanos = 0L
    private var statBuffers = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT -> inputAudioFormat.channelCount in 1..MAX_CHANNELS
            else -> false
        }
        log?.invoke(
            "configure sr=${inputAudioFormat.sampleRate} enc=${inputAudioFormat.encoding} " +
                "ch=${inputAudioFormat.channelCount} supported=$supported"
        )
        return if (supported) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val count = inputBuffer.remaining()
        if (count == 0) return

        val s = settings
        if (s.isBypass()) {
            // Off or flat: identical bytes, nothing to compute.
            wasBypass = true
            preampCurrent = 1.0
            val output = replaceOutputBuffer(count)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val format = inputAudioFormat
        val channels = format.channelCount
        val sampleRate = format.sampleRate
        if (wasBypass || channels != appliedChannels || z1.size != MeloEqPolicy.BAND_COUNT * channels) {
            // Coming back from bypass (or a new layout): start from silence so no old tail leaks in.
            z1 = DoubleArray(MeloEqPolicy.BAND_COUNT * channels)
            z2 = DoubleArray(MeloEqPolicy.BAND_COUNT * channels)
            appliedChannels = channels
            appliedSettings = null
            wasBypass = false
        }
        if (s !== appliedSettings || sampleRate != appliedSampleRate) {
            applySettings(s, sampleRate, channels)
        }

        val startNanos = if (log != null) System.nanoTime() else 0L
        val encoding = format.encoding
        val frameBytes = format.bytesPerFrame
        val frames = count / frameBytes
        val output = replaceOutputBuffer(count)
        output.order(inputBuffer.order())

        val rampFrames = if (preampCurrent == preampTarget) 0 else min(frames, max(1, sampleRate / RAMP_DIVISOR))
        val rampStart = preampCurrent
        val collect = log != null
        var sumIn = 0.0
        var sumOut = 0.0
        var clipped = 0L

        val bandCount = MeloEqPolicy.BAND_COUNT
        for (frame in 0 until frames) {
            val gain = if (frame < rampFrames) rampStart + (preampTarget - rampStart) * (frame + 1) / rampFrames
            else preampTarget
            for (ch in 0 until channels) {
                val x0 = readSample(inputBuffer, encoding)
                var x = x0
                for (b in 0 until bandCount) {
                    if (!active[b]) continue
                    val c = coeffs[b]
                    val i = b * channels + ch
                    val y = c.b0 * x + z1[i]
                    z1[i] = c.b1 * x - c.a1 * y + z2[i]
                    z2[i] = c.b2 * x - c.a2 * y
                    x = y
                }
                var out = x * gain
                if (out.isNaN()) out = 0.0
                val limited = if (out > 1.0) 1.0 else if (out < -1.0) -1.0 else out
                if (collect) {
                    sumIn += x0 * x0
                    sumOut += limited * limited
                    if (limited != out) clipped++
                }
                writeSample(output, encoding, limited)
            }
        }
        preampCurrent = preampTarget
        // A trailing part of a frame (not expected from the sink) is passed through untouched.
        if (inputBuffer.hasRemaining()) output.put(inputBuffer)
        output.flip()

        if (collect) {
            statSumIn += sumIn
            statSumOut += sumOut
            statSamples += frames.toLong() * channels
            statClipped += clipped
            statFrames += frames
            statBuffers++
            statNanos += System.nanoTime() - startNanos
            if (statFrames >= sampleRate.toLong() * STATS_SECONDS) emitStats()
        }
    }

    /** Recomputes the 10 coefficient sets; the filter state is kept so the change does not click. */
    private fun applySettings(s: MeloEqPolicy.Settings, sampleRate: Int, channels: Int) {
        val gains = s.gains()
        for (b in 0 until MeloEqPolicy.BAND_COUNT) {
            val hz = MeloEqPolicy.BANDS_HZ[b]
            val on = gains[b] != 0 && hz < sampleRate * MAX_BAND_FRACTION
            if (on) {
                coeffs[b] = MeloEqPolicy.Biquad.peaking(hz.toDouble(), gains[b].toDouble(), MeloEqPolicy.Q, sampleRate.toDouble())
            } else {
                coeffs[b] = MeloEqPolicy.Biquad.NEUTRAL
                for (ch in 0 until channels) {
                    z1[b * channels + ch] = 0.0
                    z2[b * channels + ch] = 0.0
                }
            }
            active[b] = on
        }
        preampTarget = 10.0.pow(s.preampDb() / 20.0)
        appliedSettings = s
        appliedSampleRate = sampleRate
    }

    private fun emitStats() {
        val sink = log
        if (sink != null && statSamples > 0 && statBuffers > 0) {
            val inDb = toDb(statSumIn / statSamples)
            val outDb = toDb(statSumOut / statSamples)
            sink(
                "stats inRmsDb=${"%.1f".format(inDb)} outRmsDb=${"%.1f".format(outDb)} clipped=$statClipped " +
                    "avgUsPerBuffer=${statNanos / statBuffers / 1000}"
            )
        }
        resetStats()
    }

    private fun toDb(meanSquare: Double): Double = if (meanSquare <= 1e-20) -200.0 else 10.0 * log10(meanSquare)

    private fun resetStats() {
        statFrames = 0; statSumIn = 0.0; statSumOut = 0.0; statSamples = 0
        statClipped = 0; statNanos = 0; statBuffers = 0
    }

    /** Seek, track change, reconfigure: drop the filter memory so the old song's tail never plays. */
    override fun onFlush() {
        z1.fill(0.0)
        z2.fill(0.0)
        wasBypass = true
        preampCurrent = 1.0
        appliedSettings = null
        resetStats()
    }

    override fun onReset() {
        onFlush()
        z1 = DoubleArray(0)
        z2 = DoubleArray(0)
        appliedSampleRate = 0
        appliedChannels = 0
    }

    private fun readSample(buf: ByteBuffer, encoding: Int): Double = when (encoding) {
        C.ENCODING_PCM_16BIT -> buf.short / 32768.0
        C.ENCODING_PCM_24BIT -> {
            val b0 = buf.get().toInt() and 0xFF
            val b1 = buf.get().toInt() and 0xFF
            val b2 = buf.get().toInt() // sign-extended
            ((b2 shl 16) or (b1 shl 8) or b0) / 8388608.0
        }
        C.ENCODING_PCM_32BIT -> buf.int / 2147483648.0
        else -> buf.float.toDouble().let { if (it.isFinite()) it else 0.0 } // NaN would stick in the filters
    }

    /** [v] is already limited to [-1, 1]. */
    private fun writeSample(buf: ByteBuffer, encoding: Int, v: Double) {
        when (encoding) {
            C.ENCODING_PCM_16BIT -> buf.putShort((v * 32768.0).roundToLong().coerceIn(-32768L, 32767L).toShort())
            C.ENCODING_PCM_24BIT -> {
                val n = (v * 8388608.0).roundToLong().coerceIn(-8388608L, 8388607L).toInt()
                buf.put((n and 0xFF).toByte())
                buf.put(((n ushr 8) and 0xFF).toByte())
                buf.put(((n ushr 16) and 0xFF).toByte())
            }
            C.ENCODING_PCM_32BIT ->
                buf.putInt((v * 2147483648.0).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
            else -> buf.putFloat(v.toFloat())
        }
    }

    private companion object {
        const val MAX_CHANNELS = 8
        /** Bands at or above this share of the sample rate are skipped (the filter turns unstable near Nyquist). */
        const val MAX_BAND_FRACTION = 0.45
        /** The preamp change ramps over 1/50 s (20 ms), or the buffer if shorter. */
        const val RAMP_DIVISOR = 50
        const val STATS_SECONDS = 10
    }
}
