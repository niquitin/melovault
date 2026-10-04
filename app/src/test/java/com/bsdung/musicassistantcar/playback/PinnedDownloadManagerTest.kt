package com.bsdung.musicassistantcar.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PinnedDownloadManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore
    private lateinit var manager: PinnedDownloadManager

    @Before
    fun setup() {
        storeDir = tempFolder.newFolder("melovault_test_offline")
        store = OfflineMetadataStore(storeDir)
        manager = PinnedDownloadManager(store)
    }

    private fun sampleRecord(
        trackId: Long = 11209L,
        title: String = "Measured Track",
        loudnessLufs: Double? = -22.33,
        truePeakDbtp: Double? = -7.33,
        analysisVersion: Int? = 1,
    ): OfflineTrackRecord {
        val audioFile = File(storeDir, "track_$trackId.mp3")
        audioFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        return OfflineTrackRecord(
            trackId = trackId,
            title = title,
            artist = "Audiophile Artist",
            album = "Test Album",
            durationMs = 240_000L,
            relativeFileName = "track_$trackId.mp3",
            fileSizeBytes = 5L,
            sha256 = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
            loudnessLufs = loudnessLufs,
            truePeakDbtp = truePeakDbtp,
            loudnessRangeLu = 6.2,
            analysisVersion = analysisVersion,
            downloadedAtMs = 1700000000000L,
            format = "mp3",
            codec = "mp3",
            bitrate = 320,
            sampleRate = 44100,
            channels = 2,
        )
    }

    @Test
    fun testToMediaItemPreservesLoudnessAndMetadata() {
        val record = sampleRecord(trackId = 11209L, loudnessLufs = -22.33, truePeakDbtp = -7.33)
        store.saveRecord(record)

        val mediaItem = manager.toMediaItem(record)
        assertEquals("track:11209", mediaItem.mediaId)
        assertEquals("Measured Track", mediaItem.mediaMetadata.title)
        assertEquals("Audiophile Artist", mediaItem.mediaMetadata.artist)

        val extras = mediaItem.mediaMetadata.extras
        assertNotNull(extras)
        assertTrue(extras!!.getBoolean("is_offline_download", false))
        assertEquals(-22.33, extras.getDouble("loudness_lufs"), 0.001)
        assertEquals(-7.33, extras.getDouble("true_peak_dbtp"), 0.001)
        assertEquals(1, extras.getInt("loudness_analysis_version"))

        // Verify GainAudioProcessor applies exact +6.33 dB gain on offline playback
        val processor = GainAudioProcessor()
        processor.isNormalizationEnabled = true
        processor.applyMediaItem(mediaItem, normalizationEnabled = true)

        assertEquals(6.33f, processor.appliedGainDb, 0.01f)
    }

    @Test
    fun testToMediaItemUnmeasuredDefaultsToUnityGain() {
        val unmeasured = sampleRecord(
            trackId = 11210L,
            title = "Unmeasured Track",
            loudnessLufs = null,
            truePeakDbtp = null,
            analysisVersion = null,
        )
        store.saveRecord(unmeasured)

        val mediaItem = manager.toMediaItem(unmeasured)
        assertEquals("track:11210", mediaItem.mediaId)

        // Unmeasured offline track must safely produce 0.0 dB unity gain
        val processor = GainAudioProcessor()
        processor.isNormalizationEnabled = true
        processor.applyMediaItem(mediaItem, normalizationEnabled = true)

        assertEquals(0.0f, processor.appliedGainDb, 0.001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.001f)
    }

    @Test
    fun testIsDownloadedAndGetRecord() {
        assertFalse(manager.isDownloaded(11209L))
        assertNull(manager.getRecord(11209L))

        val record = sampleRecord(11209L)
        store.saveRecord(record)

        assertTrue(manager.isDownloaded(11209L))
        assertEquals(11209L, manager.getRecord(11209L)?.trackId)
    }

    @Test
    fun testDeleteDownloadRemovesRecordAndNotifies() {
        val record = sampleRecord(11209L)
        store.saveRecord(record)
        assertTrue(manager.isDownloaded(11209L))

        var failNotified = false
        var notifiedTrackId = -1L
        val listener = object : PinnedDownloadManager.DownloadListener {
            override fun onDownloadStarted(trackId: Long) {}
            override fun onDownloadProgress(trackId: Long, bytesDownloaded: Long) {}
            override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {}
            override fun onDownloadFailed(trackId: Long, error: String) {
                failNotified = true
                notifiedTrackId = trackId
            }
        }
        manager.addListener(listener)

        val deleted = manager.deleteDownload(11209L)
        assertTrue(deleted)
        assertFalse(manager.isDownloaded(11209L))
        assertTrue(failNotified)
        assertEquals(11209L, notifiedTrackId)

        manager.removeListener(listener)
    }

    @Test
    fun testGetAllDownloadedMediaItems() {
        store.saveRecord(sampleRecord(1L, "Track 1"))
        store.saveRecord(sampleRecord(2L, "Track 2"))

        val items = manager.getAllDownloadedMediaItems()
        assertEquals(2, items.size)
        assertTrue(items.any { it.mediaId == "track:1" })
        assertTrue(items.any { it.mediaId == "track:2" })
    }
}
