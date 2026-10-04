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

class OfflineMetadataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore

    @Before
    fun setup() {
        storeDir = tempFolder.newFolder("melovault_offline")
        store = OfflineMetadataStore(storeDir)
    }

    private fun sampleRecord(
        trackId: Long = 11209L,
        title: String = "Test Track",
        loudnessLufs: Double? = -20.33,
        truePeakDbtp: Double? = -1.5,
    ): OfflineTrackRecord {
        val audioFile = File(storeDir, "track_$trackId.audio")
        audioFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        return OfflineTrackRecord(
            trackId = trackId,
            title = title,
            artist = "Test Artist",
            album = "Test Album",
            durationMs = 180_000L,
            relativeFileName = "track_$trackId.audio",
            fileSizeBytes = 4L,
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            loudnessLufs = loudnessLufs,
            truePeakDbtp = truePeakDbtp,
            loudnessRangeLu = 6.2,
            analysisVersion = 1,
            downloadedAtMs = 1700000000000L,
            format = "mp3",
            codec = "mp3",
            bitrate = 320,
            sampleRate = 44100,
            channels = 2,
        )
    }

    @Test
    fun testSaveAndRetrieveRecord() {
        val record = sampleRecord()
        store.saveRecord(record)

        assertTrue(store.hasTrack(11209L))
        val retrieved = store.getRecord(11209L)
        assertNotNull(retrieved)
        assertEquals(11209L, retrieved?.trackId)
        assertEquals("Test Track", retrieved?.title)
        assertEquals("Test Artist", retrieved?.artist)
        assertEquals(-20.33, retrieved?.loudnessLufs!!, 0.001)
        assertEquals(-1.5, retrieved?.truePeakDbtp!!, 0.001)
        assertEquals(1, retrieved?.analysisVersion)
        assertEquals(4L, retrieved?.fileSizeBytes)
    }

    @Test
    fun testGetAllRecords() {
        store.saveRecord(sampleRecord(trackId = 1L, title = "Track 1"))
        store.saveRecord(sampleRecord(trackId = 2L, title = "Track 2"))

        val all = store.getAllRecords()
        assertEquals(2, all.size)
        assertTrue(all.any { it.trackId == 1L })
        assertTrue(all.any { it.trackId == 2L })
    }

    @Test
    fun testRemoveRecordAndFileDeletion() {
        val record = sampleRecord(trackId = 11209L)
        store.saveRecord(record)
        val audioFile = store.getTrackFile(record)
        assertTrue(audioFile.exists())

        val removed = store.removeRecord(11209L)
        assertTrue(removed)
        assertFalse(store.hasTrack(11209L))
        assertNull(store.getRecord(11209L))
        assertFalse(audioFile.exists())
    }

    @Test
    fun testCorruptJsonRecoveryDoesNotCrash() {
        val metadataFile = File(storeDir, "offline_metadata.json")
        metadataFile.writeText("INVALID JSON CONTENT {{{", Charsets.UTF_8)

        // Instantiating store should recover without throwing
        val recoveringStore = OfflineMetadataStore(storeDir)
        assertEquals(0, recoveringStore.getAllRecords().size)

        // Backup file was created
        val backupFile = File(storeDir, "offline_metadata.json.bak")
        assertTrue(backupFile.exists())
        assertEquals("INVALID JSON CONTENT {{{", backupFile.readText(Charsets.UTF_8))
    }

    @Test
    fun testNoSecretsPersistedInJson() {
        val record = sampleRecord()
        store.saveRecord(record)

        val metadataFile = File(storeDir, "offline_metadata.json")
        val jsonText = metadataFile.readText(Charsets.UTF_8)

        assertFalse(jsonText.contains("token", ignoreCase = true))
        assertFalse(jsonText.contains("bearer", ignoreCase = true))
        assertFalse(jsonText.contains("password", ignoreCase = true))
        assertFalse(jsonText.contains("authorization", ignoreCase = true))
    }
}
