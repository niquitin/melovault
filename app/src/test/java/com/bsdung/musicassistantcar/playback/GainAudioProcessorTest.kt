package com.bsdung.musicassistantcar.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GainAudioProcessorTest {

    private fun createPcm16Format(sampleRate: Int = 44100, channelCount: Int = 2): AudioProcessor.AudioFormat {
        return AudioProcessor.AudioFormat(sampleRate, channelCount, C.ENCODING_PCM_16BIT)
    }

    private fun createPcmFloatFormat(sampleRate: Int = 44100, channelCount: Int = 2): AudioProcessor.AudioFormat {
        return AudioProcessor.AudioFormat(sampleRate, channelCount, C.ENCODING_PCM_FLOAT)
    }

    private fun createPcm24Format(sampleRate: Int = 48000, channelCount: Int = 2): AudioProcessor.AudioFormat {
        return AudioProcessor.AudioFormat(sampleRate, channelCount, C.ENCODING_PCM_24BIT)
    }

    private fun createPcm32Format(sampleRate: Int = 48000, channelCount: Int = 2): AudioProcessor.AudioFormat {
        return AudioProcessor.AudioFormat(sampleRate, channelCount, C.ENCODING_PCM_32BIT)
    }

    // Test 18: PCM16 unity is bit-identical
    @Test
    fun testPcm16UnityIsBitIdentical() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainDb(0.0f)

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putShort(100)
        input.putShort(-200)
        input.putShort(3000)
        input.putShort(-4000)
        input.flip()

        val expectedBytes = ByteArray(8)
        input.duplicate().get(expectedBytes)

        processor.queueInput(input)
        val output = processor.output

        assertEquals(8, output.remaining())
        val actualBytes = ByteArray(8)
        output.get(actualBytes)
        for (i in 0 until 8) {
            assertEquals(expectedBytes[i], actualBytes[i])
        }
    }

    // Test 19: PCM16 attenuation correct
    @Test
    fun testPcm16AttenuationCorrect() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainMultiplier(0.5f)

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(20000)
        input.putShort(-10000)
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(10000.toShort(), output.short)
        assertEquals((-5000).toShort(), output.short)
    }

    // Test 20: PCM16 positive gain correct
    @Test
    fun testPcm16PositiveGainCorrect() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainMultiplier(2.0f)

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(10000)
        input.putShort(-8000)
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(20000.toShort(), output.short)
        assertEquals((-16000).toShort(), output.short)
    }

    // Test 21: PCM16 positive overflow saturates, never wraps
    @Test
    fun testPcm16PositiveOverflowSaturatesNeverWraps() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainMultiplier(2.0f)

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(30000) // 30000 * 2 = 60000 -> must saturate to 32767
        input.putShort(32767) // 32767 * 2 = 65534 -> must saturate to 32767
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        val s1 = output.short
        val s2 = output.short
        assertEquals(32767.toShort(), s1)
        assertEquals(32767.toShort(), s2)
        assertTrue("Must be positive and saturated", s1 > 0)
        assertTrue("Must be positive and saturated", s2 > 0)
    }

    // Test 22: PCM16 negative full-scale handled
    @Test
    fun testPcm16NegativeFullScaleHandled() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainMultiplier(2.0f)

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort((-20000).toShort()) // -20000 * 2 = -40000 -> must saturate to -32768
        input.putShort(Short.MIN_VALUE)    // -32768 * 2 = -65536 -> must saturate to -32768
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals((-32768).toShort(), output.short)
        assertEquals((-32768).toShort(), output.short)
    }

    // Test 23: PCM16 silence remains zero
    @Test
    fun testPcm16SilenceRemainsZero() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainMultiplier(3.5f)

        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input.putShort(0)
        input.putShort(0)
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(0.toShort(), output.short)
        assertEquals(0.toShort(), output.short)
    }

    // Test 24: PCM float unity
    @Test
    fun testPcmFloatUnityIsBitIdentical() {
        val processor = GainAudioProcessor()
        processor.configure(createPcmFloatFormat())
        processor.flush()
        processor.setGainDb(0.0f)

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.25f)
        input.putFloat(-0.75f)
        input.flip()

        val expectedBytes = ByteArray(8)
        input.duplicate().get(expectedBytes)

        processor.queueInput(input)
        val output = processor.output

        assertEquals(8, output.remaining())
        val actualBytes = ByteArray(8)
        output.get(actualBytes)
        for (i in 0 until 8) {
            assertEquals(expectedBytes[i], actualBytes[i])
        }
    }

    // Test 25: PCM float attenuation
    @Test
    fun testPcmFloatAttenuationCorrect() {
        val processor = GainAudioProcessor()
        processor.configure(createPcmFloatFormat())
        processor.flush()
        processor.setGainMultiplier(0.5f)

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.8f)
        input.putFloat(-0.6f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(0.4f, output.float, 0.0001f)
        assertEquals(-0.3f, output.float, 0.0001f)
    }

    // Test 26: PCM float positive gain
    @Test
    fun testPcmFloatPositiveGainCorrect() {
        val processor = GainAudioProcessor()
        processor.configure(createPcmFloatFormat())
        processor.flush()
        processor.setGainMultiplier(2.0f)

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putFloat(0.3f)
        input.putFloat(-0.4f)
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(0.6f, output.float, 0.0001f)
        assertEquals(-0.8f, output.float, 0.0001f)
    }

    // Test 27: non-finite defensive behavior
    @Test
    fun testPcmFloatNonFiniteDefensiveBehavior() {
        val processor = GainAudioProcessor()
        processor.configure(createPcmFloatFormat())
        processor.flush()
        processor.setGainMultiplier(2.0f)

        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        input.putFloat(Float.NaN)
        input.putFloat(Float.POSITIVE_INFINITY)
        input.putFloat(Float.NEGATIVE_INFINITY)
        input.putFloat(0.9f) // 0.9 * 2 = 1.8 -> clamped to 1.0f
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(0.0f, output.float, 0.0001f)
        assertEquals(1.0f, output.float, 0.0001f)
        assertEquals(-1.0f, output.float, 0.0001f)
        assertEquals(1.0f, output.float, 0.0001f)
    }

    // Test 28: input buffer position/limit obey Media3 contract
    @Test
    fun testInputBufferPositionLimitContract() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainMultiplier(1.5f)

        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        input.putShort(10)
        input.putShort(20)
        input.putShort(30)
        input.putShort(40)
        input.flip()

        assertEquals(8, input.remaining())
        processor.queueInput(input)
        // Position must reach limit
        assertEquals(0, input.remaining())
        assertEquals(input.limit(), input.position())
    }

    // Test 29: output buffer length correct
    @Test
    fun testOutputBufferLengthCorrect() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainMultiplier(1.2f)

        val byteCount = 64
        val input = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder())
        for (i in 0 until (byteCount / 2)) {
            input.putShort(i.toShort())
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertEquals(byteCount, output.remaining())
    }

    // Test 30: no channel/sample reordering
    @Test
    fun testNoChannelSampleReordering() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format(sampleRate = 44100, channelCount = 2))
        processor.flush()
        processor.setGainMultiplier(1.0f)

        val samples = shortArrayOf(10, 20, 30, 40, 50, 60)
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        for (s in samples) input.putShort(s)
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        for (expected in samples) {
            assertEquals(expected, output.short)
        }
    }

    // Test 31: default processor gain unity
    @Test
    fun testDefaultProcessorGainUnity() {
        val processor = GainAudioProcessor()
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Test 32: set gain persists through normal queueInput
    @Test
    fun testSetGainPersistsThroughNormalQueueInput() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()
        processor.setGainDb(3.0f)

        val initialGain = processor.appliedGainDb
        val initialMult = processor.gainMultiplier

        val input1 = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input1.putShort(100)
        input1.putShort(200)
        input1.flip()
        processor.queueInput(input1)
        processor.output

        val input2 = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        input2.putShort(300)
        input2.putShort(400)
        input2.flip()
        processor.queueInput(input2)
        processor.output

        assertEquals(initialGain, processor.appliedGainDb, 0.0001f)
        assertEquals(initialMult, processor.gainMultiplier, 0.0001f)
    }

    // Test 33: flush behavior correct
    @Test
    fun testFlushPreservesGain() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.setGainDb(-5.0f)
        val gainBefore = processor.appliedGainDb
        val multBefore = processor.gainMultiplier

        // Flush must preserve intended gain
        processor.flush()

        assertEquals(gainBefore, processor.appliedGainDb, 0.0001f)
        assertEquals(multBefore, processor.gainMultiplier, 0.0001f)
    }

    // Test 34: seek does not cause wrong gain
    @Test
    fun testSeekDoesNotCauseWrongGain() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.setGainDb(2.5f)

        // Multiple seeks/flushes within track
        processor.flush()
        assertEquals(2.5f, processor.appliedGainDb, 0.0001f)
        processor.flush()
        assertEquals(2.5f, processor.appliedGainDb, 0.0001f)
    }

    // Test 35: reset returns to safe state
    @Test
    fun testResetReturnsToSafeState() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.setGainDb(4.0f)
        assertEquals(4.0f, processor.appliedGainDb, 0.0001f)

        processor.reset()

        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Test 36: EOS handled
    @Test
    fun testEndOfStreamHandled() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm16Format())
        processor.flush()

        assertFalse(processor.isEnded)
        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
    }

    private fun putPcm24Sample(buf: ByteBuffer, value: Int) {
        buf.put((value and 0xFF).toByte())
        buf.put(((value ushr 8) and 0xFF).toByte())
        buf.put(((value ushr 16) and 0xFF).toByte())
    }

    private fun getPcm24Sample(buf: ByteBuffer): Int {
        val b0 = buf.get().toInt() and 0xFF
        val b1 = buf.get().toInt() and 0xFF
        val b2 = buf.get().toInt()
        return (b2 shl 16) or (b1 shl 8) or b0
    }

    // Test 37: PCM24 unity is bit-identical
    @Test
    fun testPcm24UnityIsBitIdentical() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm24Format())
        processor.flush()
        processor.setGainDb(0.0f)

        val input = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder())
        putPcm24Sample(input, 1000)
        putPcm24Sample(input, -2000)
        putPcm24Sample(input, 500000)
        putPcm24Sample(input, -500000)
        input.flip()

        val expectedBytes = ByteArray(12)
        input.duplicate().get(expectedBytes)

        processor.queueInput(input)
        val output = processor.output

        assertEquals(12, output.remaining())
        val actualBytes = ByteArray(12)
        output.get(actualBytes)
        for (i in 0 until 12) {
            assertEquals(expectedBytes[i], actualBytes[i])
        }
    }

    // Test 38: PCM24 scaling and saturation protection
    @Test
    fun testPcm24ScalingAndSaturation() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm24Format())
        processor.flush()
        processor.setGainMultiplier(2.0f)

        val input = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder())
        putPcm24Sample(input, 100000)     // * 2 = 200000
        putPcm24Sample(input, -80000)     // * 2 = -160000
        putPcm24Sample(input, 5000000)    // * 2 = 10000000 -> saturate to 8388607
        putPcm24Sample(input, -5000000)   // * 2 = -10000000 -> saturate to -8388608
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(200000, getPcm24Sample(output))
        assertEquals(-160000, getPcm24Sample(output))
        assertEquals(8388607, getPcm24Sample(output))
        assertEquals(-8388608, getPcm24Sample(output))
    }

    // Test 39: PCM32 unity is bit-identical
    @Test
    fun testPcm32UnityIsBitIdentical() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm32Format())
        processor.flush()
        processor.setGainDb(0.0f)

        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        input.putInt(10000)
        input.putInt(-20000)
        input.putInt(500000000)
        input.putInt(-500000000)
        input.flip()

        val expectedBytes = ByteArray(16)
        input.duplicate().get(expectedBytes)

        processor.queueInput(input)
        val output = processor.output

        assertEquals(16, output.remaining())
        val actualBytes = ByteArray(16)
        output.get(actualBytes)
        for (i in 0 until 16) {
            assertEquals(expectedBytes[i], actualBytes[i])
        }
    }

    // Test 40: PCM32 scaling and saturation protection
    @Test
    fun testPcm32ScalingAndSaturation() {
        val processor = GainAudioProcessor()
        processor.configure(createPcm32Format())
        processor.flush()
        processor.setGainMultiplier(2.0f)

        val input = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        input.putInt(1000000)        // * 2 = 2000000
        input.putInt(-800000)        // * 2 = -1600000
        input.putInt(1500000000)     // * 2 = 3000000000 -> saturate to Int.MAX_VALUE
        input.putInt(-1500000000)    // * 2 = -3000000000 -> saturate to Int.MIN_VALUE
        input.flip()

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())

        assertEquals(2000000, output.int)
        assertEquals(-1600000, output.int)
        assertEquals(Int.MAX_VALUE, output.int)
        assertEquals(Int.MIN_VALUE, output.int)
    }

    // Test 41: Unsupported format returns NOT_SET and bypasses gracefully
    @Test
    fun testUnsupportedFormatBypassesGracefully() {
        val processor = GainAudioProcessor()
        val unsupported = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_8BIT)
        val result = processor.configure(unsupported)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, result)
        assertFalse("Processor must be inactive when format is unsupported", processor.isActive)
    }

    // Test 42: Missing loudness metadata yields unity gain
    @Test
    fun testMissingLoudnessMetadataYieldsUnityGain() {
        val processor = GainAudioProcessor()
        processor.setGainDb(5.0f)
        assertEquals(5.0f, processor.appliedGainDb, 0.0001f)

        val emptyItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId("device:test:1")
            .build()
        processor.applyMediaItem(emptyItem)

        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)

        processor.setGainDb(-3.0f)
        processor.applyMediaItem(null)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Test 43: Gain multiplier never zero for valid track or invalid inputs
    @Test
    fun testGainMultiplierNeverZeroForValidTrack() {
        val processor = GainAudioProcessor()
        processor.setGainMultiplier(0.0f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)

        processor.setGainMultiplier(-2.5f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)

        processor.setGainDb(Float.NaN)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
    }
}
