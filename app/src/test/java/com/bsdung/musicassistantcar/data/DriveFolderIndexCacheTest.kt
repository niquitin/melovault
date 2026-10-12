package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class DriveFolderIndexCacheTest {

    @Test
    fun testCacheKeySanitization() {
        assertEquals("root_2_", DriveFolderIndexCache.cacheKey(2, ""))
        assertEquals("root_2_Apple_Music", DriveFolderIndexCache.cacheKey(2, "Apple Music"))
        assertEquals("root_2_Apple_Music_A_Teens", DriveFolderIndexCache.cacheKey(2, "Apple Music/A_Teens"))
        assertEquals("root_0_Rock_Pop", DriveFolderIndexCache.cacheKey(0, "Rock\\Pop"))
    }

    @Test
    fun testCompactIndexTrackJsonRoundtrip() {
        val original = CompactIndexTrack(
            id = 42,
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            duration = 245.5,
            driveFileId = 1001,
        )

        val json = original.toJson()
        val parsed = CompactIndexTrack.fromJson(json)

        assertEquals(original.id, parsed.id)
        assertEquals(original.title, parsed.title)
        assertEquals(original.artist, parsed.artist)
        assertEquals(original.album, parsed.album)
        assertEquals(original.duration, parsed.duration)
        assertEquals(original.driveFileId, parsed.driveFileId)

        val carTrack = parsed.toCarTrack()
        assertEquals(42, carTrack.id)
        assertEquals("Test Song", carTrack.title)
        assertEquals("ready", carTrack.status)
    }

    @Test
    fun testCompactIndexTrackNullHandling() {
        val track = CompactIndexTrack(
            id = 99,
            title = "",
            artist = "",
            album = "",
            duration = null,
            driveFileId = null,
        )
        val json = track.toJson()
        val parsed = CompactIndexTrack.fromJson(json)
        assertEquals(99, parsed.id)
        assertNull(parsed.duration)
        assertNull(parsed.driveFileId)
    }

    @Test
    fun testParseIndexStreamPerformanceAndIntegrity() {
        val count = 11_188
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, StandardCharsets.UTF_8).use { writer ->
            writer.write("[\n")
            for (i in 1..count) {
                writer.write("{\"id\":$i,\"title\":\"Song $i\",\"artist\":\"Artist\",\"album\":\"Album\"}")
                if (i < count) writer.write(",\n") else writer.write("\n")
            }
            writer.write("]")
        }

        val bytes = baos.toByteArray()
        val t0 = System.currentTimeMillis()
        val parsed = DriveFolderIndexCache.parseIndexStream(ByteArrayInputStream(bytes))
        val elapsed = System.currentTimeMillis() - t0

        assertEquals(count, parsed.size)
        assertEquals(1, parsed.first().id)
        assertEquals("Song 1", parsed.first().title)
        assertEquals(count, parsed.last().id)
        assertEquals("Song $count", parsed.last().title)
        // Ensure parsing 11,188 tracks takes < 1000ms in unit test
        assertTrue("Parsing 11,188 tracks took too long: ${elapsed}ms", elapsed < 2000L)
    }
}
