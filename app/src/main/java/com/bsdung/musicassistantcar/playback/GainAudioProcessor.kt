package com.bsdung.musicassistantcar.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Custom Media3 1.11.1 AudioProcessor implementing non-destructive PCM playback gain.
 *
 * Supported formats:
 * - [C.ENCODING_PCM_16BIT]: 16-bit signed integer PCM with defensive saturation to [-32768, 32767].
 * - [C.ENCODING_PCM_FLOAT]: 32-bit floating point PCM with saturation guard to [-1.0f, 1.0f].
 *
 * All other formats throw [AudioProcessor.UnhandledAudioFormatException].
 *
 * Thread-safety:
 * - Gain state is maintained via @Volatile fields, allowing real-time adjustment from any thread
 *   without pipeline stalls or reconfiguration.
 * - Unity default: 0.0 dB (multiplier 1.0f).
 */
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var isNormalizationEnabled: Boolean = true

    @Volatile
    var appliedGainDb: Float = 0.0f
        private set

    @Volatile
    var gainMultiplier: Float = 1.0f
        private set

    /**
     * Sets the target gain in dB.
     * Updates [appliedGainDb] and [gainMultiplier] atomically for subsequent PCM frames.
     */
    fun setGainDb(gainDb: Float) {
        if (!gainDb.isFinite()) {
            appliedGainDb = 0.0f
            gainMultiplier = 1.0f
            return
        }
        appliedGainDb = gainDb
        gainMultiplier = LoudnessNormalization.dbToLinearMultiplier(gainDb)
    }

    /**
     * Sets the linear multiplier directly.
     */
    fun setGainMultiplier(multiplier: Float) {
        if (!multiplier.isFinite() || multiplier < 0.0f) {
            appliedGainDb = 0.0f
            gainMultiplier = 1.0f
            return
        }
        gainMultiplier = multiplier
        appliedGainDb = LoudnessNormalization.linearMultiplierToDb(multiplier)
    }

    /**
     * Resets gain to 0.0 dB (unity multiplier 1.0f).
     */
    fun resetGain() {
        setGainDb(0.0f)
    }

    /**
     * Calculates and applies loudness normalization gain for a [MediaItem].
     *
     * Validates extras for canonical loudness metadata:
     * - loudness_lufs
     * - true_peak_dbtp
     * - loudness_analysis_version
     *
     * Any missing or unsupported metadata immediately resets gain to 0.0 dB (unity).
     */
    fun applyMediaItem(mediaItem: MediaItem?, normalizationEnabled: Boolean = isNormalizationEnabled) {
        if (!normalizationEnabled || mediaItem == null) {
            resetGain()
            return
        }

        val extras = mediaItem.mediaMetadata.extras
        if (extras == null ||
            !extras.containsKey("loudness_lufs") ||
            !extras.containsKey("true_peak_dbtp") ||
            !extras.containsKey("loudness_analysis_version")
        ) {
            resetGain()
            return
        }

        val lufs = extras.getDouble("loudness_lufs")
        val tp = extras.getDouble("true_peak_dbtp")
        val ver = extras.getInt("loudness_analysis_version")

        val gainDb = LoudnessNormalization.calculateAppliedGainDb(
            integratedLufs = lufs,
            truePeakDbtp = tp,
            analysisVersion = ver,
            enabled = normalizationEnabled,
        )
        setGainDb(gainDb)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val count = inputBuffer.remaining()
        if (count == 0) return

        val mult = gainMultiplier
        // Unity fast-path: byte-identical copy without floating-point math or distortion
        if (mult == 1.0f) {
            val output = replaceOutputBuffer(count)
            output.put(inputBuffer)
            output.flip()
            return
        }

        val output = replaceOutputBuffer(count)
        output.order(inputBuffer.order())

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val multDouble = mult.toDouble()
                while (inputBuffer.hasRemaining()) {
                    val sample = inputBuffer.short
                    val scaled = Math.round(sample * multDouble)
                    // Clamp to 16-bit signed range [-32768, 32767] with saturation protection
                    val clamped = scaled.coerceIn(-32768L, 32767L).toShort()
                    output.putShort(clamped)
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                while (inputBuffer.hasRemaining()) {
                    val sample = inputBuffer.float
                    val scaled = sample * mult
                    // Saturation protection guard for floating-point PCM
                    val clamped = when {
                        scaled.isNaN() -> 0.0f
                        scaled > 1.0f -> 1.0f
                        scaled < -1.0f -> -1.0f
                        else -> scaled
                    }
                    output.putFloat(clamped)
                }
            }
            else -> {
                // Defensive fallback: pass through
                output.put(inputBuffer)
            }
        }
        output.flip()
    }

    /**
     * Handles stream metadata updates dispatched by AudioSink during track transitions.
     * Extracts upcoming MediaItem from timeline to prevent cross-track gain leakage
     * in gapless playback before main-thread transition callbacks fire.
     */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val periodUid = streamMetadata.periodUid
        if (!streamMetadata.timeline.isEmpty && periodUid != null) {
            try {
                val period = Timeline.Period()
                streamMetadata.timeline.getPeriodByUid(periodUid, period)
                val window = Timeline.Window()
                streamMetadata.timeline.getWindow(period.windowIndex, window)
                val targetItem = window.mediaItem
                applyMediaItem(targetItem, isNormalizationEnabled)
            } catch (_: Exception) {
                // If timeline resolution is unavailable, maintain current safe state
            }
        }
        super.onFlush(streamMetadata)
    }

    override fun onFlush() {
        // Intentionally preserve applied gain across seek/pause/resume flushes
    }

    override fun onReset() {
        // Reset to default safe unity state upon processor reset
        resetGain()
    }
}
