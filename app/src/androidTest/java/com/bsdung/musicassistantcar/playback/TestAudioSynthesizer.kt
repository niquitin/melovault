package com.bsdung.musicassistantcar.playback

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * Generates synthetic, unencumbered PCM WAV audio fixtures for testing real ExoPlayer playback.
 * Does NOT require external network assets or copyrighted audio.
 */
object TestAudioSynthesizer {

    private const val SAMPLE_RATE = 44100
    private const val BITS_PER_SAMPLE = 16
    private const val CHANNELS = 1 // Mono
    private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    private const val BYTE_RATE = SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE

    /**
     * Generates a valid RIFF WAV file with a pure sine wave tone (or silence if frequency <= 0).
     *
     * @param context Android context for isolated cache directory access.
     * @param fileName Target file name (e.g., "test_tone_440hz.wav").
     * @param durationMs Duration in milliseconds.
     * @param frequencyHz Frequency of sine wave (e.g., 440.0 for musical note A4, or 0.0 for silence).
     * @return Generated File in the isolated test cache directory.
     */
    fun createWavFile(
        context: Context,
        fileName: String,
        durationMs: Long,
        frequencyHz: Double = 440.0,
    ): File {
        val testDir = File(context.cacheDir, "real_exoplayer_test_fixtures").apply { mkdirs() }
        val targetFile = File(testDir, fileName)

        val totalSamples = ((SAMPLE_RATE.toDouble() * durationMs) / 1000.0).toInt()
        val dataSize = totalSamples * CHANNELS * BYTES_PER_SAMPLE
        val totalSize = 36 + dataSize

        FileOutputStream(targetFile).use { fos ->
            // 44-byte RIFF WAV Header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1.toShort()) // AudioFormat = 1 (PCM)
            header.putShort(CHANNELS.toShort())
            header.putInt(SAMPLE_RATE)
            header.putInt(BYTE_RATE)
            header.putShort((CHANNELS * BYTES_PER_SAMPLE).toShort()) // BlockAlign
            header.putShort(BITS_PER_SAMPLE.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)

            fos.write(header.array())

            // Write PCM audio samples in 4KB chunks
            val chunkSamples = 2048
            val sampleBuffer = ByteBuffer.allocate(chunkSamples * BYTES_PER_SAMPLE).order(ByteOrder.LITTLE_ENDIAN)
            var sampleIndex = 0

            while (sampleIndex < totalSamples) {
                sampleBuffer.clear()
                val samplesToWrite = minOf(chunkSamples, totalSamples - sampleIndex)

                for (i in 0 until samplesToWrite) {
                    val currentSample = sampleIndex + i
                    val sampleValue: Short = if (frequencyHz > 0.0) {
                        val angle = 2.0 * Math.PI * frequencyHz * currentSample / SAMPLE_RATE
                        // Scale to ~25% amplitude to prevent loud output
                        (sin(angle) * 8192.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    } else {
                        0.toShort()
                    }
                    sampleBuffer.putShort(sampleValue)
                }

                fos.write(sampleBuffer.array(), 0, samplesToWrite * BYTES_PER_SAMPLE)
                sampleIndex += samplesToWrite
            }
            fos.flush()
        }

        return targetFile
    }

    /**
     * Cleans up all generated test fixtures in test cache.
     */
    fun cleanUp(context: Context) {
        val testDir = File(context.cacheDir, "real_exoplayer_test_fixtures")
        if (testDir.exists()) {
            testDir.deleteRecursively()
        }
    }
}
