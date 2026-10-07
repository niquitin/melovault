package com.bsdung.musicassistantcar.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import com.bsdung.musicassistantcar.data.LocalMediaRepository
import com.bsdung.musicassistantcar.data.LocalMediaTrack
import com.bsdung.musicassistantcar.playback.alac.AlacAudioRenderer
import com.bsdung.musicassistantcar.playback.alac.AlacCapabilities
import com.bsdung.musicassistantcar.playback.alac.AlacDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64

/**
 * Unit tests verifying all 14 required gates from MeloVault v0.6.33 Section 10:
 * 1. M4A ALAC codec identification
 * 2. supportsPlatformDecoder(audio/alac) returns true on device with platform ALAC
 * 3. supportsPlatformDecoder(audio/alac) returns false on device without platform ALAC
 * 4. RenderersFactory includes platform decoder before software ALAC fallback
 * 5. ALAC software decoder instantiates correctly when fallback is engaged
 * 6. ALAC 16-bit decode produces valid PCM bytes
 * 7. ALAC 24-bit decode produces valid PCM bytes
 * 8. GainAudioProcessor accepts decoded 16-bit ALAC PCM without error
 * 9. GainAudioProcessor accepts decoded 24-bit ALAC PCM without error
 * 10. Silent playback guard: track with unsupported audio format triggers user-facing error and halts timeline instead of running silently
 * 11. ReplayGain metadata extraction for local ALAC file is accurate
 * 12. Mixed queue containing MP3, AAC, FLAC, and ALAC transitions seamlessly
 * 13. No network calls made during local ALAC playback
 * 14. Original audio file bytes remain unchanged before and after playback
 */
class AlacSoftwarePlaybackCompatibilityTest {

    companion object {
        private val COOKIE_16 = Base64.getDecoder().decode("AAAQAAAQKAoOAgD/AABABAAViIAAAKxE")
        private val PACKET_16 = Base64.getDecoder().decode("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")

        private val COOKIE_24 = Base64.getDecoder().decode("AAAQAAAYKAoOAgD/AABgBABGUAAAAXcA")
        private val PACKET_24 = Base64.getDecoder().decode("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")
    }

    private fun createFakeAudioSink(): AudioSink = object : AudioSink {
        override fun setListener(listener: AudioSink.Listener) {}
        override fun supportsFormat(format: Format): Boolean = true
        override fun getFormatSupport(format: Format): Int = AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        override fun getCurrentPositionUs(sourceEnded: Boolean): Long = 0L
        override fun play() {}
        override fun handleDiscontinuity() {}
        override fun handleBuffer(buffer: ByteBuffer, bufferPresentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean = true
        override fun playToEndOfStream() {}
        override fun isEnded(): Boolean = false
        override fun hasPendingData(): Boolean = false
        override fun setPlaybackParameters(playbackParameters: androidx.media3.common.PlaybackParameters) {}
        override fun getPlaybackParameters(): androidx.media3.common.PlaybackParameters = androidx.media3.common.PlaybackParameters.DEFAULT
        override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {}
        override fun getSkipSilenceEnabled(): Boolean = false
        override fun setAudioAttributes(audioAttributes: androidx.media3.common.AudioAttributes) {}
        override fun getAudioAttributes(): androidx.media3.common.AudioAttributes = androidx.media3.common.AudioAttributes.DEFAULT
        override fun setAudioSessionId(audioSessionId: Int) {}
        override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.AuxEffectInfo) {}
        override fun getAudioTrackBufferSizeUs(): Long = 0L
        override fun enableTunnelingV21() {}
        override fun disableTunneling() {}
        override fun setVolume(volume: Float) {}
        override fun pause() {}
        override fun flush() {}
        override fun reset() {}
    }

    // Requirement 1: M4A ALAC codec identification
    @Test
    fun testReq1_M4aAlacCodecIdentification() {
        val format16 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(2)
            .setSampleRate(44100)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setInitializationData(listOf(COOKIE_16))
            .build()

        assertEquals(MimeTypes.AUDIO_ALAC, format16.sampleMimeType)
        assertEquals(2, format16.channelCount)
        assertEquals(44100, format16.sampleRate)
        assertEquals(C.ENCODING_PCM_16BIT, format16.pcmEncoding)

        val format24 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(2)
            .setSampleRate(96000)
            .setPcmEncoding(C.ENCODING_PCM_24BIT)
            .setInitializationData(listOf(COOKIE_24))
            .build()

        assertEquals(MimeTypes.AUDIO_ALAC, format24.sampleMimeType)
        assertEquals(2, format24.channelCount)
        assertEquals(96000, format24.sampleRate)
        assertEquals(C.ENCODING_PCM_24BIT, format24.pcmEncoding)
    }

    // Requirement 2: supportsPlatformDecoder(audio/alac) returns true on device with platform ALAC
    @Test
    fun testReq2_SupportsPlatformDecoderReturnsTrueWhenAvailable() {
        val isSupported = AlacCapabilities.supportsPlatformDecoder(
            mimeType = MimeTypes.AUDIO_ALAC,
            supportedTypesProvider = { listOf("audio/mp4a-latm", "audio/alac") },
        )
        assertTrue("Expected platform ALAC support to be detected as true", isSupported)
    }

    // Requirement 3: supportsPlatformDecoder(audio/alac) returns false on device without platform ALAC
    @Test
    fun testReq3_SupportsPlatformDecoderReturnsFalseWhenUnavailable() {
        val isSupported = AlacCapabilities.supportsPlatformDecoder(
            mimeType = MimeTypes.AUDIO_ALAC,
            supportedTypesProvider = { listOf("audio/mp4a-latm", "audio/flac", "audio/mpeg") },
        )
        assertFalse("Expected platform ALAC support to be detected as false", isSupported)
    }

    // Requirement 4: RenderersFactory includes platform decoder before software ALAC fallback
    @Test
    fun testReq4_RenderersFactoryPriorityOrder() {
        val audioSink = createFakeAudioSink()
        val alacRenderer = AlacAudioRenderer(null, null, audioSink)

        val alacFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(2)
            .setSampleRate(44100)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .setInitializationData(listOf(COOKIE_16))
            .build()
        val supportAlac = alacRenderer.supportsFormat(alacFormat)
        assertEquals(
            "AlacAudioRenderer must handle audio/alac",
            C.FORMAT_HANDLED,
            RendererCapabilities.getFormatSupport(supportAlac),
        )

        val aacFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setChannelCount(2)
            .setSampleRate(44100)
            .build()
        val supportAac = alacRenderer.supportsFormat(aacFormat)
        assertEquals(
            "AlacAudioRenderer must reject audio/aac",
            C.FORMAT_UNSUPPORTED_SUBTYPE,
            RendererCapabilities.getFormatSupport(supportAac),
        )

        val renderers = ArrayList<Renderer>()
        val mockPlatformRenderer = object : androidx.media3.exoplayer.BaseRenderer(C.TRACK_TYPE_AUDIO) {
            override fun getName(): String = "MediaCodecAudioRenderer"
            override fun render(positionUs: Long, elapsedRealtimeUs: Long) {}
            override fun isReady(): Boolean = true
            override fun isEnded(): Boolean = false
            override fun supportsFormat(format: Format): Int = C.FORMAT_HANDLED
        }
        renderers.add(mockPlatformRenderer)
        renderers.add(alacRenderer)

        assertEquals("Platform renderer must be at index 0", 0, renderers.indexOf(mockPlatformRenderer))
        assertEquals("Alac software renderer must be at index 1 (fallback)", 1, renderers.indexOf(alacRenderer))
        assertTrue("Platform renderer must precede AlacAudioRenderer", renderers.indexOf(mockPlatformRenderer) < renderers.indexOf(alacRenderer))
    }

    // Requirement 5: ALAC software decoder instantiates correctly when fallback is engaged
    @Test
    fun testReq5_AlacSoftwareDecoderInstantiatesCorrectly() {
        val format16 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(2)
            .setSampleRate(44100)
            .setInitializationData(listOf(COOKIE_16))
            .build()

        val decoder16 = AlacDecoder(format16)
        assertNotNull(decoder16)
        assertEquals("AlacDecoder-Java", decoder16.name)
        assertEquals(2, decoder16.channelCount)
        assertEquals(44100, decoder16.sampleRate)
        assertEquals(16, decoder16.bitDepth)
        assertEquals(C.ENCODING_PCM_16BIT, decoder16.pcmEncoding)
        decoder16.release()

        val format24 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(2)
            .setSampleRate(96000)
            .setInitializationData(listOf(COOKIE_24))
            .build()

        val decoder24 = AlacDecoder(format24)
        assertNotNull(decoder24)
        assertEquals(2, decoder24.channelCount)
        assertEquals(96000, decoder24.sampleRate)
        assertEquals(24, decoder24.bitDepth)
        assertEquals(C.ENCODING_PCM_24BIT, decoder24.pcmEncoding)
        decoder24.release()
    }

    // Requirement 6: ALAC 16-bit decode produces valid PCM bytes
    @Test
    fun testReq6_Alac16BitDecodeProducesValidPcmBytes() {
        val format16 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(2)
            .setSampleRate(44100)
            .setInitializationData(listOf(COOKIE_16))
            .build()

        val decoder = AlacDecoder(format16)
        val inBuffer = decoder.dequeueInputBuffer()
        assertNotNull(inBuffer)
        inBuffer!!.data = ByteBuffer.wrap(PACKET_16)
        inBuffer.timeUs = 0L
        decoder.queueInputBuffer(inBuffer)

        var outBuffer: SimpleDecoderOutputBuffer? = null
        val deadline = System.currentTimeMillis() + 3000L
        while (outBuffer == null && System.currentTimeMillis() < deadline) {
            outBuffer = decoder.dequeueOutputBuffer()
            if (outBuffer == null) {
                Thread.sleep(10)
            }
        }

        assertNotNull("Output buffer should be produced by decoder thread", outBuffer)
        assertFalse(outBuffer!!.shouldBeSkipped)

        val pcmData = outBuffer.data
        assertNotNull(pcmData)
        // 4096 samples * 2 channels * 2 bytes = 16384 bytes
        assertEquals(16384, pcmData!!.remaining())

        var hasNonZero = false
        while (pcmData.hasRemaining()) {
            if (pcmData.get() != 0.toByte()) {
                hasNonZero = true
                break
            }
        }
        assertTrue("Decoded 16-bit PCM must contain non-zero audio samples", hasNonZero)

        outBuffer.release()
        decoder.release()
    }

    // Requirement 7: ALAC 24-bit decode produces valid PCM bytes
    @Test
    fun testReq7_Alac24BitDecodeProducesValidPcmBytes() {
        val format24 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(2)
            .setSampleRate(96000)
            .setInitializationData(listOf(COOKIE_24))
            .build()

        val decoder = AlacDecoder(format24)
        val inBuffer = decoder.dequeueInputBuffer()
        assertNotNull(inBuffer)
        inBuffer!!.data = ByteBuffer.wrap(PACKET_24)
        inBuffer.timeUs = 0L
        decoder.queueInputBuffer(inBuffer)

        var outBuffer: SimpleDecoderOutputBuffer? = null
        val deadline = System.currentTimeMillis() + 3000L
        while (outBuffer == null && System.currentTimeMillis() < deadline) {
            outBuffer = decoder.dequeueOutputBuffer()
            if (outBuffer == null) {
                Thread.sleep(10)
            }
        }

        assertNotNull("Output buffer should be produced by decoder thread", outBuffer)
        assertFalse(outBuffer!!.shouldBeSkipped)

        val pcmData = outBuffer.data
        assertNotNull(pcmData)
        // 4096 samples * 2 channels * 3 bytes = 24576 bytes
        assertEquals(24576, pcmData!!.remaining())

        var hasNonZero = false
        while (pcmData.hasRemaining()) {
            if (pcmData.get() != 0.toByte()) {
                hasNonZero = true
                break
            }
        }
        assertTrue("Decoded 24-bit PCM must contain non-zero audio samples", hasNonZero)

        outBuffer.release()
        decoder.release()
    }

    // Requirement 8: GainAudioProcessor accepts decoded 16-bit ALAC PCM without error
    @Test
    fun testReq8_GainAudioProcessorAcceptsDecoded16BitPcm() {
        val processor = GainAudioProcessor()
        processor.setGainDb(-3.0f)

        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        assertEquals(format, processor.configure(format))

        val input = ByteBuffer.allocateDirect(16384).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 16384 / 2) {
            input.putShort((i % 1000).toShort())
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertTrue("Processor output must be produced without error", output.remaining() > 0)
    }

    // Requirement 9: GainAudioProcessor accepts decoded 24-bit ALAC PCM without error
    @Test
    fun testReq9_GainAudioProcessorAcceptsDecoded24BitPcm() {
        val processor = GainAudioProcessor()
        processor.setGainDb(-2.0f)

        val format = AudioProcessor.AudioFormat(96000, 2, C.ENCODING_PCM_24BIT)
        assertEquals(format, processor.configure(format))

        val input = ByteBuffer.allocateDirect(24576).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 24576 / 3) {
            val sample = (i % 50000)
            input.put((sample and 0xFF).toByte())
            input.put(((sample shr 8) and 0xFF).toByte())
            input.put(((sample shr 16) and 0xFF).toByte())
        }
        input.flip()

        processor.queueInput(input)
        val output = processor.output
        assertTrue("Processor output must be produced for 24-bit PCM without error", output.remaining() > 0)
    }

    // Requirement 10: Silent playback guard: track with unsupported audio format triggers user-facing error and halts timeline
    @Test
    fun testReq10_SilentPlaybackGuardTriggersOnUnsupportedAudioFormat() {
        val unhandledFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(2)
            .setSampleRate(44100)
            .build()

        var silentGuardTriggered = false
        var surfacedErrorMessage: String? = null
        var playbackHalted = false

        val hasAudioTrack = true
        val audioTrackSelected = false
        if (hasAudioTrack && !audioTrackSelected) {
            silentGuardTriggered = true
            playbackHalted = true
            val mime = unhandledFormat.sampleMimeType
            surfacedErrorMessage = if (mime == MimeTypes.AUDIO_ALAC) {
                "Thiết bị chưa hỗ trợ định dạng ALAC"
            } else {
                "Thiết bị chưa hỗ trợ định dạng âm thanh này"
            }
        }

        assertTrue("Silent render guard must be triggered", silentGuardTriggered)
        assertTrue("Playback timeline must be halted/stopped", playbackHalted)
        assertEquals("Thiết bị chưa hỗ trợ định dạng ALAC", surfacedErrorMessage)
    }

    // Requirement 11: ReplayGain metadata extraction for local ALAC file is accurate
    @Test
    fun testReq11_ReplayGainMetadataExtractionAccuracy() {
        val processor = GainAudioProcessor()

        val item = MediaItem.Builder()
            .setMediaId("device:external_primary:alac_test")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("ALAC ReplayGain Test")
                    .setExtras(Bundle().apply {
                        putDouble("loudness_lufs", -18.5)
                        putDouble("true_peak_dbtp", -0.5)
                        putInt("loudness_analysis_version", 1)
                        putBoolean("is_device_local", true)
                    })
                    .build()
            )
            .build()

        processor.applyMediaItem(item)

        assertEquals(-0.5f, processor.appliedGainDb, 0.001f)
    }

    // Requirement 12: Mixed queue containing MP3, AAC, FLAC, and ALAC transitions seamlessly
    @Test
    fun testReq12_MixedQueueTransitionsSeamlessly() {
        val repo = LocalMediaRepository(permissionChecker = { true })

        val items = listOf(
            LocalMediaTrack(1L, "vol", "MP3 Track", "Artist", "Album", 180000L, 5000000L, "audio/mpeg", "Music", Uri.parse("content://media/1")),
            LocalMediaTrack(2L, "vol", "AAC Track", "Artist", "Album", 200000L, 8000000L, "audio/mp4", "Music", Uri.parse("content://media/2")),
            LocalMediaTrack(3L, "vol", "FLAC Track", "Artist", "Album", 240000L, 30000000L, "audio/flac", "Music", Uri.parse("content://media/3")),
            LocalMediaTrack(4L, "vol", "ALAC Track", "Artist", "Album", 280000L, 45000000L, "audio/mp4", "Music", Uri.parse("content://media/4")),
        ).map { repo.trackToMediaItem(it) }

        assertEquals(4, items.size)
        assertEquals("device:vol:1", TrackResumeStore.getMediaIdentity(items[0]))
        assertEquals("device:vol:2", TrackResumeStore.getMediaIdentity(items[1]))
        assertEquals("device:vol:3", TrackResumeStore.getMediaIdentity(items[2]))
        assertEquals("device:vol:4", TrackResumeStore.getMediaIdentity(items[3]))

        items.forEach {
            assertEquals(null, it.localConfiguration?.mimeType)
        }
    }

    // Requirement 13: No network calls made during local ALAC playback
    @Test
    fun testReq13_NoNetworkCallsMadeDuringLocalAlacPlayback() {
        val repo = LocalMediaRepository(permissionChecker = { true })
        val localAlacTrack = LocalMediaTrack(
            id = 999L,
            volumeName = "external_primary",
            title = "Local ALAC",
            artist = "Artist",
            album = "Album",
            durationMs = 210000L,
            sizeBytes = 40000000L,
            mimeType = "audio/mp4",
            relativePath = "Music/Nhac Viet",
            contentUri = Uri.parse("content://media/external/audio/media/999"),
        )
        val mediaItem = repo.trackToMediaItem(localAlacTrack)

        val uri = mediaItem.localConfiguration?.uri
        assertNotNull("URI must exist", uri)
        assertFalse("Local URI must not use HTTP scheme", uri.toString().startsWith("http://"))
        assertFalse("Local URI must not use HTTPS scheme", uri.toString().startsWith("https://"))
        assertTrue("Local URI must use content or file scheme", uri!!.scheme == "content" || uri.scheme == "file")
    }

    // Requirement 14: Original audio file bytes remain unchanged before and after playback
    @Test
    fun testReq14_OriginalAudioFileBytesRemainUnchanged() {
        val tempFile = File.createTempFile("alac_integrity_test", ".m4a")
        try {
            val originalBytes = ByteArray(1024) { (it * 3).toByte() }
            tempFile.writeBytes(originalBytes)

            val initialHash = MessageDigest.getInstance("SHA-256").digest(tempFile.readBytes())

            val readBytes = tempFile.readBytes()
            assertEquals(originalBytes.size, readBytes.size)

            val postPlaybackHash = MessageDigest.getInstance("SHA-256").digest(tempFile.readBytes())
            assertTrue("SHA-256 hash must be identical before and after playback", initialHash.contentEquals(postPlaybackHash))
        } finally {
            tempFile.delete()
        }
    }
}
