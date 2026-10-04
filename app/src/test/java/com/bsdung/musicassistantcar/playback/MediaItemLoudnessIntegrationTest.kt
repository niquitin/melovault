package com.bsdung.musicassistantcar.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.CarTrack
import com.bsdung.musicassistantcar.data.RemoteCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaItemLoudnessIntegrationTest {

    private fun createMediaItemWithExtras(
        id: String,
        lufs: Double? = null,
        truePeak: Double? = null,
        lra: Double? = null,
        version: Int? = null,
    ): MediaItem {
        val extras = Bundle()
        lufs?.let { extras.putDouble("loudness_lufs", it) }
        truePeak?.let { extras.putDouble("true_peak_dbtp", it) }
        lra?.let { extras.putDouble("loudness_range_lu", it) }
        version?.let { extras.putInt("loudness_analysis_version", it) }

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Test Track $id")
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    // Test 38: API fields parsed
    @Test
    fun testApiFieldsParsed() {
        val json = """
            {
                "id": 101,
                "title": "Audiophile Master",
                "artist": "Diana Krall",
                "album": "The Girl in the Other Room",
                "status": "OWNED_AUDIO",
                "duration": 245.5,
                "loudness_lufs": -18.5,
                "true_peak_dbtp": -1.2,
                "loudness_range_lu": 6.8,
                "loudness_analysis_version": 1
            }
        """.trimIndent()

        val client = CarApiClient()
        val track = client.parseTrackJson(json)

        assertEquals(101, track.id)
        assertEquals(-18.5, track.loudnessLufs!!, 0.0001)
        assertEquals(-1.2, track.truePeakDbtp!!, 0.0001)
        assertEquals(6.8, track.loudnessRangeLu!!, 0.0001)
        assertEquals(1, track.loudnessAnalysisVersion)
    }

    // Test 39: API missing fields remain null
    @Test
    fun testApiMissingFieldsRemainNull() {
        val jsonMissing = """
            {
                "id": 102,
                "title": "Legacy Track",
                "artist": "Unknown",
                "album": "Unknown",
                "status": "OWNED_AUDIO",
                "duration": 180.0
            }
        """.trimIndent()

        val client = CarApiClient()
        val track = client.parseTrackJson(jsonMissing)

        assertNull(track.loudnessLufs)
        assertNull(track.truePeakDbtp)
        assertNull(track.loudnessRangeLu)
        assertNull(track.loudnessAnalysisVersion)

        // Also test explicit JSON nulls
        val jsonExplicitNull = """
            {
                "id": 103,
                "title": "Unmeasured Track",
                "artist": "Unknown",
                "album": "Unknown",
                "status": "OWNED_AUDIO",
                "duration": 180.0,
                "loudness_lufs": null,
                "true_peak_dbtp": null,
                "loudness_range_lu": null,
                "loudness_analysis_version": null
            }
        """.trimIndent()

        val trackNull = client.parseTrackJson(jsonExplicitNull)
        assertNull(trackNull.loudnessLufs)
        assertNull(trackNull.truePeakDbtp)
        assertNull(trackNull.loudnessRangeLu)
        assertNull(trackNull.loudnessAnalysisVersion)
    }

    // Test 40: fields propagated into MediaMetadata extras
    @Test
    fun testFieldsPropagatedIntoMediaMetadataExtras() {
        val catalog = RemoteCatalog()
        val measuredTrack = CarTrack(
            id = 201,
            title = "Test Measured",
            artist = "Artist A",
            album = "Album A",
            status = "OWNED_AUDIO",
            duration = 200.0,
            loudnessLufs = -20.0,
            truePeakDbtp = -3.0,
            loudnessRangeLu = 4.5,
            loudnessAnalysisVersion = 1,
        )

        val extrasMeasured = catalog.trackExtras(measuredTrack)
        val itemMeasured = MediaItem.Builder()
            .setMediaId("track:${measuredTrack.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(measuredTrack.title)
                    .setExtras(extrasMeasured)
                    .build()
            )
            .build()

        val extrasFromItem = itemMeasured.mediaMetadata.extras
        assertNotNull(extrasFromItem)
        assertTrue(extrasFromItem!!.containsKey("loudness_lufs"))
        assertTrue(extrasFromItem.containsKey("true_peak_dbtp"))
        assertTrue(extrasFromItem.containsKey("loudness_range_lu"))
        assertTrue(extrasFromItem.containsKey("loudness_analysis_version"))
        assertEquals(-20.0, extrasFromItem.getDouble("loudness_lufs"), 0.0001)
        assertEquals(-3.0, extrasFromItem.getDouble("true_peak_dbtp"), 0.0001)
        assertEquals(4.5, extrasFromItem.getDouble("loudness_range_lu"), 0.0001)
        assertEquals(1, extrasFromItem.getInt("loudness_analysis_version"))

        // Unmeasured track must not put null fields as 0.0 into extras
        val unmeasuredTrack = CarTrack(
            id = 202,
            title = "Test Unmeasured",
            artist = "Artist B",
            album = "Album B",
            status = "OWNED_AUDIO",
            duration = 180.0,
            loudnessLufs = null,
            truePeakDbtp = null,
            loudnessRangeLu = null,
            loudnessAnalysisVersion = null,
        )
        val extrasUnmeasured = catalog.trackExtras(unmeasuredTrack)
        val itemUnmeasured = MediaItem.Builder()
            .setMediaId("track:${unmeasuredTrack.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(unmeasuredTrack.title)
                    .setExtras(extrasUnmeasured)
                    .build()
            )
            .build()

        val extrasFromUnmeasured = itemUnmeasured.mediaMetadata.extras
        assertNotNull(extrasFromUnmeasured)
        assertFalse(extrasFromUnmeasured!!.containsKey("loudness_lufs"))
        assertFalse(extrasFromUnmeasured.containsKey("true_peak_dbtp"))
        assertFalse(extrasFromUnmeasured.containsKey("loudness_range_lu"))
        assertFalse(extrasFromUnmeasured.containsKey("loudness_analysis_version"))
    }

    // Test 41: valid MediaItem computes correct gain
    @Test
    fun testValidMediaItemComputesCorrectGain() {
        val processor = GainAudioProcessor()
        // raw = -16 - (-20) = +4.0 dB
        // safe = -1.0 - (-5.0) = +4.0 dB
        // min = +4.0 dB
        val item = createMediaItemWithExtras("track:1", lufs = -20.0, truePeak = -5.0, version = 1)

        processor.applyMediaItem(item)

        assertEquals(4.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(LoudnessNormalization.dbToLinearMultiplier(4.0f), processor.gainMultiplier, 0.0001f)
    }

    // Test 42: missing metadata item -> unity
    @Test
    fun testMissingMetadataItemReturnsUnity() {
        val processor = GainAudioProcessor()
        val itemWithoutMeta = createMediaItemWithExtras("track:2")

        processor.applyMediaItem(itemWithoutMeta)

        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Test 43: unsupported analysis version -> unity
    @Test
    fun testUnsupportedAnalysisVersionReturnsUnity() {
        val processor = GainAudioProcessor()
        val itemUnsupportedVersion = createMediaItemWithExtras("track:3", lufs = -20.0, truePeak = -5.0, version = 99)

        processor.applyMediaItem(itemUnsupportedVersion)

        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Test 44: A measured -> B missing resets to unity
    @Test
    fun testMeasuredToMissingResetsToUnity() {
        val processor = GainAudioProcessor()
        val itemA = createMediaItemWithExtras("track:A", lufs = -24.0, truePeak = -10.0, version = 1)
        val itemB = createMediaItemWithExtras("track:B") // Missing metadata

        processor.applyMediaItem(itemA)
        assertEquals(8.0f, processor.appliedGainDb, 0.0001f)

        // Transition to Track B
        processor.applyMediaItem(itemB)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Test 45: A missing -> B measured applies B gain
    @Test
    fun testMissingToMeasuredAppliesMeasuredGain() {
        val processor = GainAudioProcessor()
        val itemA = createMediaItemWithExtras("track:A") // Missing metadata
        val itemB = createMediaItemWithExtras("track:B", lufs = -13.0, truePeak = -0.5, version = 1)

        processor.applyMediaItem(itemA)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)

        // Transition to Track B: raw = -16 - (-13) = -3.0 dB
        processor.applyMediaItem(itemB)
        assertEquals(-3.0f, processor.appliedGainDb, 0.0001f)
    }

    // Test 46: rapid A -> B -> C uses C gain
    @Test
    fun testRapidTransitionsAlwaysAppliesLatestItem() {
        val processor = GainAudioProcessor()
        val itemA = createMediaItemWithExtras("track:A", lufs = -20.0, truePeak = -5.0, version = 1) // +4 dB
        val itemB = createMediaItemWithExtras("track:B", lufs = -12.0, truePeak = -0.5, version = 1) // -4 dB
        val itemC = createMediaItemWithExtras("track:C", lufs = -18.0, truePeak = -4.0, version = 1) // +2 dB

        processor.applyMediaItem(itemA)
        processor.applyMediaItem(itemB)
        processor.applyMediaItem(itemC)

        assertEquals(2.0f, processor.appliedGainDb, 0.0001f)
    }

    // Test 47: default setting ON
    @Test
    fun testDefaultSettingOn() {
        val processor = GainAudioProcessor()
        assertTrue(processor.isNormalizationEnabled)
        assertEquals("loudness_normalization", MusicService.KEY_LOUDNESS_NORMALIZATION)
    }

    // Test 48: saved OFF persists
    @Test
    fun testSavedSettingOffPersists() {
        val processor = GainAudioProcessor()
        processor.isNormalizationEnabled = false
        assertFalse(processor.isNormalizationEnabled)

        val item = createMediaItemWithExtras("track:1", lufs = -20.0, truePeak = -5.0, version = 1)
        processor.applyMediaItem(item)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
    }

    // Test 49: ON -> OFF current playback -> unity
    @Test
    fun testOnToOffCurrentPlaybackResetsToUnity() {
        val processor = GainAudioProcessor()
        val item = createMediaItemWithExtras("track:1", lufs = -20.0, truePeak = -5.0, version = 1)

        processor.applyMediaItem(item, normalizationEnabled = true)
        assertEquals(4.0f, processor.appliedGainDb, 0.0001f)

        // User switches OFF during playback
        processor.applyMediaItem(item, normalizationEnabled = false)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Test 50: OFF -> ON current item recomputed
    @Test
    fun testOffToOnCurrentItemRecomputed() {
        val processor = GainAudioProcessor()
        val item = createMediaItemWithExtras("track:1", lufs = -20.0, truePeak = -5.0, version = 1)

        // Started with OFF
        processor.applyMediaItem(item, normalizationEnabled = false)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)

        // User switches ON during playback
        processor.applyMediaItem(item, normalizationEnabled = true)
        assertEquals(4.0f, processor.appliedGainDb, 0.0001f)
    }

    // Test 51: missing metadata remains unity even when ON
    @Test
    fun testMissingMetadataRemainsUnityEvenWhenOn() {
        val processor = GainAudioProcessor()
        val item = createMediaItemWithExtras("track:missing")

        processor.applyMediaItem(item, normalizationEnabled = true)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }

    // Test Source Safety Contract:
    // Null/suppressed metadata must never accidentally reuse previous track's gain.
    @Test
    fun testSourceSafetyContractNullNeverReusesPreviousGain() {
        val processor = GainAudioProcessor()
        val trackA = createMediaItemWithExtras("track:A", lufs = -25.0, truePeak = -10.0, version = 1) // +9 dB
        processor.applyMediaItem(trackA)
        assertEquals(9.0f, processor.appliedGainDb, 0.0001f)

        // Drive or unverified asset B has null metadata
        val trackB = createMediaItemWithExtras("drive-file:999", lufs = null, truePeak = null, version = null)
        processor.applyMediaItem(trackB)
        assertEquals(0.0f, processor.appliedGainDb, 0.0001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.0001f)
    }
}
