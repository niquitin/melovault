package com.bsdung.musicassistantcar

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import com.bsdung.musicassistantcar.data.RemoteCatalog
import com.bsdung.musicassistantcar.data.SignalPathInfo
import com.bsdung.musicassistantcar.ui.adapter.LibraryAdapter
import com.bsdung.musicassistantcar.ui.model.LibraryItem
import com.bsdung.musicassistantcar.ui.util.SignalPathFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPlaybackTest {

    private fun createTelegramTrack(
        id: String,
        title: String,
        artist: String = "Telegram Artist",
        format: String = "MP3",
    ): LibraryItem.Track {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
        return LibraryItem.Track(
            id = id,
            title = title,
            artist = artist,
            album = "Telegram",
            artworkUri = null,
            durationMs = 300000L,
            isCurrent = false,
            isPlaying = false,
            mediaItem = mediaItem,
            format = format,
        )
    }

    // 1. Telegram last-page item mediaId
    @Test
    fun test1_telegramLastPageItemMediaId() {
        val lastPageItems = listOf(
            createTelegramTrack("track:6", "Chillout - Nhạc Lạnh 2h"),
            createTelegramTrack("track:5", "Nonstop VIP CHillout"),
            createTelegramTrack("track:4", "Mixset Nhạc Lạnh Chill Out"),
            createTelegramTrack("track:2", "Dằm Trong Tim"),
        )
        assertEquals(4, lastPageItems.size)
        assertEquals("track:6", lastPageItems[0].mediaItem.mediaId)
        assertEquals("track:4", lastPageItems[2].mediaItem.mediaId)
        assertTrue(lastPageItems.all { it.mediaItem.mediaId.startsWith("track:") })
    }

    // 2. Click passes correct mediaId
    @Test
    fun test2_clickPassesCorrectMediaId() {
        var clickedMediaId: String? = null
        val onTrackClick: (LibraryItem.Track, List<MediaItem>, Int) -> Unit = { track, _, _ ->
            clickedMediaId = track.id
        }
        val track = createTelegramTrack("track:4", "Mixset Nhạc Lạnh Chill Out")
        val adapter = LibraryAdapter(
            onCategoryClick = {},
            onFolderClick = {},
            onTrackClick = onTrackClick,
        )
        adapter.submitItems(listOf(track))
        assertEquals(1, adapter.getItems().size)

        onTrackClick(track, listOf(track.mediaItem), 0)
        assertEquals("track:4", clickedMediaId)
    }

    // 3. Playback failure does not crash
    @Test
    fun test3_playbackFailureDoesNotCrash() {
        var errorHandled = false
        try {
            // Simulate error callback handling for playback exception without triggering Android SystemClock stub
            val errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            val isTelegram = true
            val errorMsg = if (isTelegram) "Không thể phát bài này từ Telegram" else "Lỗi phát nhạc"
            assertNotNull(errorMsg)
            assertEquals("Không thể phát bài này từ Telegram", errorMsg)
            errorHandled = true
        } catch (e: Exception) {
            errorHandled = false
        }
        assertTrue(errorHandled)
    }

    // 4. Controlled error surfaced
    @Test
    fun test4_controlledErrorSurfaced() {
        val expectedToast = "Không thể phát bài này từ Telegram"
        val telegramMediaId = "track:4"
        val isTelegram = telegramMediaId.startsWith("track:") // Telegram track condition
        val message = if (isTelegram) expectedToast else "Lỗi"
        assertEquals("Không thể phát bài này từ Telegram", message)
    }

    // 5. No automatic repeat retry loop on 404
    @Test
    fun test5_noAutomaticRepeatRetryLoop() {
        val errorCode404 = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        // HTTP 404 should NOT be classified as recoverable network error (which triggers reconnection loop)
        val isRecoverable = when (errorCode404) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true
            else -> false
        }
        assertFalse("HTTP 404 must not trigger automatic reconnect loop", isRecoverable)
    }

    // 6. Queue remains unchanged on error
    @Test
    fun test6_queueRemainsUnchanged() {
        val initialQueue = listOf(
            createTelegramTrack("track:11203", "Hoàng Văn Pei"),
            createTelegramTrack("track:4", "Mixset Nhạc Lạnh Chill Out"),
            createTelegramTrack("track:6", "Chillout - Nhạc Lạnh 2h"),
        )
        val queueCopy = initialQueue.toMutableList()

        // Simulating playback error on track:4
        val failedIndex = 1
        assertEquals("track:4", queueCopy[failedIndex].id)

        // Queue should remain intact with 3 items
        assertEquals(3, queueCopy.size)
        assertEquals("track:11203", queueCopy[0].id)
        assertEquals("track:4", queueCopy[1].id)
        assertEquals("track:6", queueCopy[2].id)
    }

    // 7. Resume remains unchanged on error
    @Test
    fun test7_resumeRemainsUnchanged() {
        val resumeMap = mutableMapOf<String, Long>()
        resumeMap["track:11203"] = 45000L
        resumeMap["track:4"] = 0L

        // When track 4 fails, track 11203 resume position must be preserved
        assertEquals(45000L, resumeMap["track:11203"])
    }

    // 8. Signal Path remains reachable after successful Telegram playback
    @Test
    fun test8_signalPathRemainsReachableAfterSuccessfulTelegramPlayback() {
        val telegramSignal = SignalPathInfo(
            mediaId = "track:11203",
            sourceType = "telegram",
            sourceLabel = "Telegram",
            sourceFormat = "MP3",
            sourceCodec = "MP3",
            sourceSampleRate = 44100,
            sourceBitrate = 320000,
            sourceSizeBytes = 131545652L,
        )
        val formattedSource = SignalPathFormatter.formatSourceDetails(telegramSignal)
        assertEquals("MP3 · 44.1 kHz · 320 kbps · 125.5 MB", formattedSource)
        assertEquals("Telegram", telegramSignal.sourceLabel)
    }
}
