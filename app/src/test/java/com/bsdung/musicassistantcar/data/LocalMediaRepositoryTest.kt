package com.bsdung.musicassistantcar.data

import com.bsdung.musicassistantcar.playback.GainAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaRepositoryTest {

    @Test
    fun testPermissionDeniedReturnsEmptyList() {
        val repo = LocalMediaRepository(
            permissionChecker = { false },
            contentResolverSupplier = { null },
        )

        assertFalse(repo.hasPermission())
        val tracks = repo.getLocalTracks()
        assertTrue(tracks.isEmpty())
    }

    @Test
    fun testGetLocalTrackInvalidPrefixReturnsNull() {
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            contentResolverSupplier = { null },
        )

        assertNull(repo.getLocalTrack("track:11209"))
        assertNull(repo.getLocalTrack("downloaded:11209"))
        assertNull(repo.getLocalTrack("device:not_a_number"))
        assertNull(repo.getLocalTrack(""))
    }

    @Test
    fun testLocalMediaUnityGainGuarantee() {
        // When local tracks are played, absence of loudness extras must guarantee unity gain (0.0 dB)
        val processor = GainAudioProcessor()
        processor.isNormalizationEnabled = true

        // Simulate a device local item with only standard metadata
        val localItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId("${LocalMediaRepository.DEVICE_PREFIX}999")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Device Song")
                    .setArtist("Local Artist")
                    .setExtras(android.os.Bundle().apply {
                        putBoolean("is_device_local", true)
                        putLong("duration_ms", 180_000L)
                    })
                    .build()
            )
            .build()

        processor.applyMediaItem(localItem, normalizationEnabled = true)

        assertEquals(0.0f, processor.appliedGainDb, 0.001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.001f)
    }
}
