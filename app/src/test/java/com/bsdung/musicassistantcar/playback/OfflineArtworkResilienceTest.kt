package com.bsdung.musicassistantcar.playback

import android.net.Uri
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.ui.util.MobilePlayerLayoutPolicy
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
import java.io.IOException

class OfflineArtworkResilienceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeDir: File
    private lateinit var store: OfflineMetadataStore
    private lateinit var manager: PinnedDownloadManager

    @Before
    fun setup() {
        storeDir = tempFolder.newFolder("artwork_resilience_test_dir")
        store = OfflineMetadataStore(storeDir)
        manager = PinnedDownloadManager(store)
    }

    private fun sampleRecord(trackId: Long = 11200L): OfflineTrackRecord {
        val fileName = "track_$trackId.m4a"
        val audioFile = File(storeDir, fileName)
        audioFile.writeBytes(ByteArray(1024) { 0x55 })
        return OfflineTrackRecord(
            trackId = trackId,
            title = "Duyên Kiếp Hóa Ngàn Mây",
            artist = "Host Nguyên Khang",
            album = "The Khang Show Remix",
            durationMs = 240_000L,
            relativeFileName = fileName,
            fileSizeBytes = 1024L,
            sha256 = "05d7ba99eb58f21c8dbf24b0aaf1f3012a134f161d0ac28a0fdd9f0c820dcc86",
            loudnessLufs = -18.0,
            truePeakDbtp = -1.0,
            loudnessRangeLu = 5.0,
            analysisVersion = 1,
            downloadedAtMs = System.currentTimeMillis(),
            format = "m4a",
            codec = "aac",
            bitrate = 256,
            sampleRate = 48000,
            channels = 2,
        )
    }

    @Test
    fun testDeterministicTrackCoverUriWithoutBackendProbing() {
        // Cold-started CarApiClient has no activeBaseUrl
        val client = CarApiClient()
        val coverUri = client.trackCoverUri(11200)
        assertNotNull(coverUri)
        assertTrue("Cover URI must contain track ID", coverUri.toString().contains("11200"))
        assertTrue("Cover URI must end with /cover", coverUri.toString().endsWith("/v1/tracks/11200/cover"))

        // Repeated calls must produce the exact identical URI string for Coil disk cache lookup
        val secondUri = client.trackCoverUri(11200)
        assertEquals(coverUri.toString(), secondUri.toString())
    }

    @Test
    fun testColdStartOfflinePlaybackSucceedsWithCoverUri() {
        val record = sampleRecord(11200L)
        store.saveRecord(record)

        val items = manager.getAllDownloadedMediaItems()
        assertEquals(1, items.size)
        val item = items.first()

        assertEquals("Duyên Kiếp Hóa Ngàn Mây", item.mediaMetadata.title)
        assertEquals("Host Nguyên Khang", item.mediaMetadata.artist)
        assertNotNull("Artwork URI must be populated for Coil cache lookup", item.mediaMetadata.artworkUri)
        assertTrue(item.mediaMetadata.artworkUri.toString().contains("/v1/tracks/11200/cover"))
    }

    @Test
    fun testOfflinePlaybackResilientToBrokenArtworkClient() {
        val brokenClient = object : CarApiClient() {
            override fun trackCoverUri(trackId: Int): Uri {
                throw IOException("Simulated network failure on broken client")
            }
        }
        val record = sampleRecord(11201L)
        store.saveRecord(record)

        manager.onNetworkStateChanged(brokenClient)
        val items = manager.getAllDownloadedMediaItems()
        assertEquals(1, items.size)
        val item = items.first()

        // Audio metadata remains fully playable even if artwork resolution threw
        assertEquals("Duyên Kiếp Hóa Ngàn Mây", item.mediaMetadata.title)
        assertNull("Artwork URI gracefully falls back to null without crashing", item.mediaMetadata.artworkUri)
    }

    @Test
    fun testMobilePlayerLayoutPolicyScreenClassification() {
        // Sony Xperia 5 II: 1080x2520 @ 420 dpi -> widthDp = 411, heightDp = 960
        assertTrue("Sony Xperia 5 II portrait must be classified as portrait phone",
            MobilePlayerLayoutPolicy.isPortraitPhone(411, 960))

        // Standard phones
        assertTrue(MobilePlayerLayoutPolicy.isPortraitPhone(360, 780))
        assertTrue(MobilePlayerLayoutPolicy.isPortraitPhone(393, 851))

        // FYT 7870 Car Head Units (widescreen / landscape) must NOT be portrait phone
        assertFalse("FYT 7870 800x450 must not be portrait phone",
            MobilePlayerLayoutPolicy.isPortraitPhone(800, 450))
        assertFalse("FYT 7870 1024x600 must not be portrait phone",
            MobilePlayerLayoutPolicy.isPortraitPhone(1024, 600))
        assertFalse("Wide car head unit 1920x1080 must not be portrait phone",
            MobilePlayerLayoutPolicy.isPortraitPhone(1920, 1080))
    }
}
