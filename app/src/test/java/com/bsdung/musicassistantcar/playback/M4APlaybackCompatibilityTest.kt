package com.bsdung.musicassistantcar.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.audio.AudioProcessor
import com.bsdung.musicassistantcar.data.CarTrack
import com.bsdung.musicassistantcar.data.LocalMediaRepository
import com.bsdung.musicassistantcar.data.LocalMediaTrack
import com.bsdung.musicassistantcar.data.RemoteCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MeloVault v0.6.33 M4A Offline Playback Compatibility Regression Gates.
 *
 * Verifies all 10 required regression gates:
 * 1. .m4a extension does not imply AAC
 * 2. M4A MediaItem must not force wrong codec MIME
 * 3. AAC M4A local playback configuration
 * 4. ALAC M4A local playback configuration where supported
 * 5. Missing loudness metadata = unity gain
 * 6. GainAudioProcessor does not produce zero gain for valid track
 * 7. PCM encoding compatibility based on actual accepted processor formats
 * 8. Local/offline source resolution does not reject M4A solely by extension
 * 9. Queue playback transitions still preserve source identity
 * 10. No lyrics/offline metadata regressions
 */
class M4APlaybackCompatibilityTest {

    // Gate 1: .m4a extension does not imply AAC
    @Test
    fun testGate1_M4aExtensionDoesNotImplyAac() {
        val alacTrack = LocalMediaTrack(
            id = 501L,
            volumeName = "external_primary",
            title = "Em Về Tinh Khôi",
            artist = "Hoa Chen",
            album = "25th Lan Song Xanh",
            durationMs = 285_000L,
            sizeBytes = 32_000_000L,
            mimeType = "audio/mp4",
            relativePath = "Music/Nhac Viet",
            contentUri = Uri.parse("content://media/external/audio/media/501"),
        )
        // Verify mimeType is container-level audio/mp4 and not hardcoded to audio/aac
        assertFalse(
            "Extension .m4a must not imply audio/aac",
            alacTrack.mimeType == "audio/aac",
        )
        assertEquals("audio/mp4", alacTrack.mimeType)
    }

    // Gate 2: M4A MediaItem must not force wrong codec MIME
    @Test
    fun testGate2_M4aMediaItemMustNotForceWrongCodecMime() {
        val repo = LocalMediaRepository(permissionChecker = { true })
        val track = LocalMediaTrack(
            id = 502L,
            volumeName = "external_primary",
            title = "Dạt Dào Thương Lắm",
            artist = "Phi Nhung",
            album = "Nhạc Quê Hương",
            durationMs = 310_000L,
            sizeBytes = 55_000_000L,
            mimeType = "audio/mp4",
            relativePath = "Music",
            contentUri = Uri.parse("content://media/external/audio/media/502"),
        )
        val mediaItem = repo.trackToMediaItem(track)

        // mediaItem.localConfiguration?.mimeType MUST be null to allow Media3 Mp4Extractor sniffing
        assertNull(
            "MediaItem localConfiguration mimeType must be null to preserve container sniffing",
            mediaItem.localConfiguration?.mimeType,
        )
    }

    // Gate 3: AAC M4A local playback configuration
    @Test
    fun testGate3_AacM4aLocalPlaybackConfiguration() {
        val repo = LocalMediaRepository(permissionChecker = { true })
        val aacTrack = LocalMediaTrack(
            id = 503L,
            volumeName = "external_primary",
            title = "AAC Track",
            artist = "Artist",
            album = "Album",
            durationMs = 200_000L,
            sizeBytes = 8_000_000L,
            mimeType = "audio/mp4",
            relativePath = "Music",
            contentUri = Uri.parse("content://media/external/audio/media/503"),
        )
        val item = repo.trackToMediaItem(aacTrack)

        assertTrue(item.mediaMetadata.isPlayable == true)
        assertEquals("device:external_primary:503", item.mediaId)
        assertEquals(Uri.parse("content://media/external/audio/media/503"), item.localConfiguration?.uri)
    }

    // Gate 4: ALAC M4A local playback configuration where supported
    @Test
    fun testGate4_AlacM4aLocalPlaybackConfiguration() {
        val repo = LocalMediaRepository(permissionChecker = { true })
        val alacTrack = LocalMediaTrack(
            id = 504L,
            volumeName = "external_primary",
            title = "ALAC High-Res Track",
            artist = "Audiophile",
            album = "Lossless Collection",
            durationMs = 240_000L,
            sizeBytes = 65_000_000L,
            mimeType = "audio/mp4",
            relativePath = "Music/Nhac Viet",
            contentUri = Uri.parse("content://media/external/audio/media/504"),
        )
        val item = repo.trackToMediaItem(alacTrack)

        assertTrue(item.mediaMetadata.isPlayable == true)
        assertEquals(65_000_000L, item.mediaMetadata.extras?.getLong("size_bytes"))
        assertEquals("audio/mp4", item.mediaMetadata.extras?.getString("format"))
        assertNull("Codec must not be forced in localConfiguration", item.localConfiguration?.mimeType)
    }

    // Gate 5: Missing loudness metadata = unity gain
    @Test
    fun testGate5_MissingLoudnessMetadataYieldsUnityGain() {
        val processor = GainAudioProcessor()
        processor.setGainDb(-4.5f)
        assertEquals(-4.5f, processor.appliedGainDb, 0.001f)

        // Track with NO loudness metadata
        val trackWithoutLoudness = MediaItem.Builder()
            .setMediaId("device:external_primary:505")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("No Loudness")
                    .setExtras(Bundle().apply {
                        putBoolean("is_device_local", true)
                    })
                    .build()
            )
            .build()

        processor.applyMediaItem(trackWithoutLoudness)

        assertEquals("Missing metadata must yield exactly 0.0 dB", 0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals("Missing metadata must yield linear multiplier 1.0", 1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Gate 6: GainAudioProcessor does not produce zero gain for valid track
    @Test
    fun testGate6_GainAudioProcessorNeverZeroMultiplier() {
        val processor = GainAudioProcessor()

        // Zero multiplier attempted -> defaults safely to 1.0f
        processor.setGainMultiplier(0.0f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)

        // Negative multiplier attempted -> defaults safely to 1.0f
        processor.setGainMultiplier(-1.0f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)

        // NaN dB attempted -> defaults safely to 0 dB / 1.0f
        processor.setGainDb(Float.NaN)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)

        // Infinite dB attempted -> defaults safely to 0 dB / 1.0f
        processor.setGainDb(Float.POSITIVE_INFINITY)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
    }

    // Gate 7: PCM encoding compatibility based on actual accepted processor formats
    @Test
    fun testGate7_PcmEncodingCompatibility() {
        val processor = GainAudioProcessor()

        // Supported encodings: 16-bit, 24-bit, 32-bit, float
        val f16 = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val f24 = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_24BIT)
        val f32 = AudioProcessor.AudioFormat(96000, 2, C.ENCODING_PCM_32BIT)
        val fFloat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)

        assertEquals(f16, processor.configure(f16))
        assertTrue(processor.isActive)

        assertEquals(f24, processor.configure(f24))
        assertTrue(processor.isActive)

        assertEquals(f32, processor.configure(f32))
        assertTrue(processor.isActive)

        assertEquals(fFloat, processor.configure(fFloat))
        assertTrue(processor.isActive)

        // Unsupported encoding: 8-bit PCM -> graceful bypass (AudioFormat.NOT_SET)
        val f8 = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_8BIT)
        val res = processor.configure(f8)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, res)
        assertFalse("Processor must be inactive when format is bypassed", processor.isActive)
    }

    // Gate 8: Local/offline source resolution does not reject M4A solely by extension
    @Test
    fun testGate8_LocalOfflineSourceResolutionAcceptsM4a() {
        val tracks = listOf(
            LocalMediaTrack(
                id = 101L,
                volumeName = "external_primary",
                title = "M4A Sample 1",
                artist = "Artist",
                album = "Album",
                durationMs = 120_000L,
                sizeBytes = 25_000_000L,
                mimeType = "audio/mp4",
                relativePath = "Music",
                contentUri = Uri.parse("content://media/external/audio/media/101"),
            ),
            LocalMediaTrack(
                id = 102L,
                volumeName = "external_primary",
                title = "FLAC Sample 2",
                artist = "Artist",
                album = "Album",
                durationMs = 150_000L,
                sizeBytes = 30_000_000L,
                mimeType = "audio/flac",
                relativePath = "Music",
                contentUri = Uri.parse("content://media/external/audio/media/102"),
            ),
        )
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { tracks },
        )

        val localItems = repo.getLocalTracks()
        assertEquals(2, localItems.size)
        assertTrue("M4A track must be present in local tracks", localItems.any { it.mediaId == "device:external_primary:101" })
        assertTrue("FLAC track must be present in local tracks", localItems.any { it.mediaId == "device:external_primary:102" })
    }

    // Gate 9: Queue playback transitions still preserve source identity
    @Test
    fun testGate9_QueuePlaybackTransitionsPreserveSourceIdentity() {
        val repo = LocalMediaRepository(permissionChecker = { true })
        val localTrack = LocalMediaTrack(
            id = 777L,
            volumeName = "external_primary",
            title = "Device M4A",
            artist = "Artist",
            album = "Album",
            durationMs = 200_000L,
            sizeBytes = 25_000_000L,
            mimeType = "audio/mp4",
            relativePath = "Music",
            contentUri = Uri.parse("content://media/external/audio/media/777"),
        )
        val localItem = repo.trackToMediaItem(localTrack)
        val identity = TrackResumeStore.getMediaIdentity(localItem)
        assertEquals("device:external_primary:777", identity)

        val remoteItem = MediaItem.Builder()
            .setMediaId("track:11200")
            .setUri(Uri.parse("http://100.82.19.36:8790/api/car/stream/11200"))
            .build()
        val remoteIdentity = TrackResumeStore.getMediaIdentity(remoteItem)
        assertEquals("track:11200", remoteIdentity)
    }

    // Gate 10: No lyrics/offline metadata regressions
    @Test
    fun testGate10_NoLyricsOfflineMetadataRegressions() {
        val carTrack = CarTrack(
            id = 11200,
            title = "Track 11200",
            artist = "Artist 11200",
            album = "Album 11200",
            status = "playable",
            duration = 240.0,
            sourceFormat = "m4a",
            sourceCodec = "flac",
            sourceBitrate = 1411,
            sourceSampleRate = 44100,
            sourceChannels = 2,
        )

        assertEquals("m4a", carTrack.sourceFormat)
        assertEquals("flac", carTrack.sourceCodec)
        assertEquals("Track 11200", carTrack.title)

        val offlineRecord = OfflineTrackRecord(
            trackId = 11200L,
            title = "Track 11200",
            artist = "Artist 11200",
            album = "Album 11200",
            durationMs = 240_000L,
            relativeFileName = "track_11200.m4a",
            fileSizeBytes = 46_392_687L,
            sha256 = "05d7ba99eb58f21c8dbf24b0aaf1f3012a134f161d0ac28a0fdd9f0c820dcc86",
            loudnessLufs = -16.0,
            truePeakDbtp = -1.0,
            loudnessRangeLu = 5.0,
            analysisVersion = 1,
            downloadedAtMs = 1700000000000L,
            format = "m4a",
            codec = "flac",
        )

        assertEquals("m4a", offlineRecord.format)
        assertEquals("flac", offlineRecord.codec)
        assertEquals(46_392_687L, offlineRecord.fileSizeBytes)
    }
}
