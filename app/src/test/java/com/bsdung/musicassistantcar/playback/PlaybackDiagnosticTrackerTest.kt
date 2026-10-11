package com.bsdung.musicassistantcar.playback

import android.net.Uri
import androidx.media3.common.Player
import com.bsdung.musicassistantcar.data.CarApiClient
import com.bsdung.musicassistantcar.data.ConnectionStateManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * E6 R7 Unit Tests for PlaybackDiagnosticTracker & Real Playback Buffering Classification.
 *
 * Verifies:
 * 1. Exact reconstruction of Shanling M3 Ultra 20:36:40–20:36:58 timeline:
 *    - 902ms initial prepare -> INITIAL_PREPARE (normal, not counted as audible stall)
 *    - 14ms resume seek in cached span -> SEEK_BUFFER (normal, not counted as audible stall)
 *    - 8991ms mid-track buffer underrun at cache boundary -> MID_TRACK_REBUFFER_STALL (real audible stall!)
 * 2. Independence of Data Plane PlaybackStabilityVerdict from Control Plane Backend ONLINE state.
 * 3. Strict zero-token sanitization across URIs, error messages, and exported diagnostic reports.
 * 4. Deduplication of PlaybackTransport transitions in ConnectionStateManager.
 * 5. Immediate yield/cancellation of prefetch when active playback enters STATE_BUFFERING.
 */
class PlaybackDiagnosticTrackerTest {

    private var fakeNowMs: Long = 1_791_725_800_000L

    @Before
    fun setUp() {
        PlaybackDiagnosticTracker.resetForTesting()
        StreamCacheManager.releaseForTesting()
        fakeNowMs = 1_791_725_800_000L
        PlaybackDiagnosticTracker.timeProvider = { fakeNowMs }
    }

    @After
    fun tearDown() {
        PlaybackDiagnosticTracker.resetForTesting()
        StreamCacheManager.releaseForTesting()
    }

    @Test
    fun testShanlingM3UltraTimeline_DistinguishesNormalTransitionsFromRealRebufferStall() {
        val mediaId = "track:11218"
        val rawUriWithSecret = "http://100.82.19.36:8790/v1/tracks/11218/stream?quality=original&token=super_secret_jwt_999"

        // 1. Track selected at 20:36:40.673 -> Initial Prepare Buffering
        fakeNowMs = 1_791_725_800_673L
        PlaybackDiagnosticTracker.onTrackTransition(
            mediaId = mediaId,
            uriString = rawUriWithSecret,
            reasonName = "SEEK",
            positionMs = 0L,
            bufferedPositionMs = 0L,
        )
        PlaybackDiagnosticTracker.onPlayerStateUpdate(
            mediaId = mediaId,
            uriString = rawUriWithSecret,
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            isPlaying = false,
            positionMs = 0L,
            bufferedPositionMs = 0L,
        )
        assertEquals(
            PlaybackDiagnosticTracker.BufferReason.INITIAL_PREPARE,
            PlaybackDiagnosticTracker.snapshot().activeBufferReason,
        )

        // 2. Reaches STATE_READY at 20:36:41.575 (902ms later)
        fakeNowMs = 1_791_725_801_575L
        PlaybackDiagnosticTracker.onPlayerStateUpdate(
            mediaId = mediaId,
            uriString = rawUriWithSecret,
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            isPlaying = true,
            positionMs = 0L,
            bufferedPositionMs = 160_000L,
        )

        var snap = PlaybackDiagnosticTracker.snapshot()
        assertEquals("Initial prepare must be counted", 1, snap.initialPrepareCount)
        assertEquals(902L, snap.lastInitialPrepareMs)
        assertEquals("Initial prepare is NOT a mid-track rebuffer stall", 0, snap.realRebufferCount)
        assertEquals(
            PlaybackDiagnosticTracker.PlaybackStabilityVerdict.STABLE_AUDIBLE_STREAM,
            snap.stabilityVerdict,
        )

        // 3. Deferred resume seek at 20:36:42.500 -> 20:36:42.514 (14ms seek buffer inside cached span)
        fakeNowMs = 1_791_725_802_500L
        PlaybackDiagnosticTracker.onSeekDiscontinuity(
            mediaId = mediaId,
            oldPositionMs = 900L,
            newPositionMs = 157_000L,
            bufferedPositionMs = 160_000L,
        )
        PlaybackDiagnosticTracker.onPlayerStateUpdate(
            mediaId = mediaId,
            uriString = rawUriWithSecret,
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            isPlaying = false,
            positionMs = 157_000L,
            bufferedPositionMs = 160_000L,
        )
        assertEquals(
            "Buffering right after seek must be classified as SEEK_BUFFER",
            PlaybackDiagnosticTracker.BufferReason.SEEK_BUFFER,
            PlaybackDiagnosticTracker.snapshot().activeBufferReason,
        )

        // Seek completes 14ms later from SimpleCache at 20:36:42.514
        fakeNowMs = 1_791_725_802_514L
        PlaybackDiagnosticTracker.onPlayerStateUpdate(
            mediaId = mediaId,
            uriString = rawUriWithSecret,
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            isPlaying = true,
            positionMs = 157_000L,
            bufferedPositionMs = 160_077L,
        )

        snap = PlaybackDiagnosticTracker.snapshot()
        assertEquals("Seek buffer must be counted separately", 1, snap.seekBufferCount)
        assertEquals(14L, snap.lastSeekBufferMs)
        assertEquals("14ms cached seek is NOT an audible mid-track stall", 0, snap.realRebufferCount)
        assertEquals(
            PlaybackDiagnosticTracker.PlaybackStabilityVerdict.STABLE_AUDIBLE_STREAM,
            snap.stabilityVerdict,
        )

        // 4. Plays 3.077s until hitting end of cached span at 20:36:45.591 -> REAL MID-TRACK REBUFFER STALL!
        fakeNowMs = 1_791_725_805_591L
        PlaybackDiagnosticTracker.onPlayerStateUpdate(
            mediaId = mediaId,
            uriString = rawUriWithSecret,
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true,
            isPlaying = false,
            positionMs = 160_077L,
            bufferedPositionMs = 160_077L,
        )
        assertEquals(
            "Mid-track buffer underrun without seek must be classified as MID_TRACK_REBUFFER_STALL",
            PlaybackDiagnosticTracker.BufferReason.MID_TRACK_REBUFFER_STALL,
            PlaybackDiagnosticTracker.snapshot().activeBufferReason,
        )

        // Recovers 8,991ms later at 20:36:54.582
        fakeNowMs = 1_791_725_814_582L
        PlaybackDiagnosticTracker.onPlayerStateUpdate(
            mediaId = mediaId,
            uriString = rawUriWithSecret,
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            isPlaying = true,
            positionMs = 160_077L,
            bufferedPositionMs = 163_800L,
        )

        snap = PlaybackDiagnosticTracker.snapshot()
        assertEquals("Must record 1 real audible rebuffer stall", 1, snap.realRebufferCount)
        assertEquals(8991L, snap.totalRealRebufferMs)
        assertEquals(8991L, snap.maxRealRebufferMs)
        assertEquals(
            "8.99s mid-track stall must mark playback UNSTABLE_AUDIBLE_STALLS even if backend is ONLINE",
            PlaybackDiagnosticTracker.PlaybackStabilityVerdict.UNSTABLE_AUDIBLE_STALLS,
            snap.stabilityVerdict,
        )
    }

    @Test
    fun testBackendOnlineDoesNotDeclarePlaybackStableWhenAudioStreamStalls() {
        val stateManager = ConnectionStateManager.createForTesting(
            context = null,
            apiClientSupplier = {
                object : CarApiClient() {
                    override fun probe(): String = "http://100.82.19.36:8790"
                }
            },
            timeProvider = { fakeNowMs },
        )
        stateManager.reportApiCall("/health", "http://100.82.19.36:8790", success = true, latencyMs = 3100L)
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)

        // Simulate track playing then suffering a 3.5s mid-track stall
        PlaybackDiagnosticTracker.onTrackTransition("track:11218", "http://100.82.19.36:8790/v1/tracks/11218/stream", "AUTO", 0L, 10_000L)
        PlaybackDiagnosticTracker.onPlayerStateUpdate("track:11218", null, Player.STATE_READY, true, true, 5_000L, 15_000L)
        fakeNowMs += 5_000L
        PlaybackDiagnosticTracker.onPlayerStateUpdate("track:11218", null, Player.STATE_BUFFERING, true, false, 15_000L, 15_000L)
        fakeNowMs += 3_500L
        PlaybackDiagnosticTracker.onPlayerStateUpdate("track:11218", null, Player.STATE_READY, true, true, 15_000L, 25_000L)

        val report = stateManager.exportDiagnosticsReport()
        assertTrue(report.contains("UI Status:              ONLINE"))
        assertTrue(report.contains("UNSTABLE_REBUFFERING"))
        assertTrue(report.contains("Real Rebuffer Stalls:   1 (total=3500 ms"))
    }

    @Test
    fun testZeroTokenLeakageInSanitizedPlaybackDiagnostics() {
        val secretUri = "http://100.82.19.36:8790/v1/tracks/99/stream?token=my_secret_token_12345&quality=original&api_key=xyz987"
        PlaybackDiagnosticTracker.onTrackTransition("track:99", secretUri, "AUTO", 0L, 0L)
        PlaybackDiagnosticTracker.onPlaybackError(
            mediaId = "track:99",
            error = IOException("HTTP 403 Forbidden for $secretUri with Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.secret"),
        )

        val section = PlaybackDiagnosticTracker.formatDiagnosticsSection()
        assertFalse("Must not leak token value", section.contains("my_secret_token_12345"))
        assertFalse("Must not leak api_key value", section.contains("xyz987"))
        assertFalse("Must not leak Bearer JWT", section.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue("Must contain [REDACTED] placeholder", section.contains("[REDACTED]"))
    }

    @Test
    fun testPlaybackTransportDeduplicationPreventsDuplicateLogSpam() {
        val stateManager = ConnectionStateManager.createForTesting(
            context = null,
            apiClientSupplier = { CarApiClient() },
            timeProvider = { fakeNowMs },
        )

        // Call onPlaybackTransportChanged twice with the same state (simulating onPlaybackStateChanged + onIsPlayingChanged)
        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.BUFFERING)
        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.BUFFERING)
        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.NETWORK_STREAM)
        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.NETWORK_STREAM)

        val report = stateManager.exportDiagnosticsReport()
        val bufferTransitions = Regex("Playback transport changed: .* -> BUFFERING").findAll(report).count()
        val streamTransitions = Regex("Playback transport changed: .* -> NETWORK_STREAM").findAll(report).count()
        assertEquals("BUFFERING transition must be logged only once, not duplicated", 1, bufferTransitions)
        assertEquals("NETWORK_STREAM transition must be logged only once, not duplicated", 1, streamTransitions)
    }

    @Test
    fun testPrefetchYieldsImmediatelyWhenActiveTrackEntersBuffering() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "melovault_prefetch_yield_" + System.nanoTime())
        tempDir.mkdirs()
        try {
            StreamCacheManager.initForTesting(tempDir, capGiB = 10)
            StreamCacheManager.onPlaybackBufferingChanged(true)
            assertTrue(StreamCacheManager.isPlaybackActivelyBuffering())

            val dummyFactory = androidx.media3.datasource.DataSource.Factory {
                throw AssertionError("Upstream DataSource must NOT be opened while active track is buffering!")
            }
            val bytes = StreamCacheManager.prefetchIntroDirect(
                Uri.parse("http://100.82.19.36:8790/v1/tracks/11219/stream?quality=original"),
                dummyFactory,
                3L * 1024 * 1024,
            )
            assertEquals("Prefetch must immediately return 0 when active player is buffering", 0L, bytes)
        } finally {
            StreamCacheManager.releaseForTesting()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testVerdictDoesNotContradictZeroRebuffersWhenStreamErrorOccurs() {
        // Track starts and plays smoothly
        PlaybackDiagnosticTracker.onTrackTransition("track:11220", "http://100.82.19.36:8790/v1/tracks/11220/stream", "AUTO", 0L, 0L)
        PlaybackDiagnosticTracker.onPlayerStateUpdate("track:11220", null, Player.STATE_READY, true, true, 5_000L, 25_000L)

        // Stream error occurs without any mid-track rebuffer stall
        PlaybackDiagnosticTracker.onPlaybackError("track:11220", IOException("Backend connection reset by peer"))

        val snap = PlaybackDiagnosticTracker.snapshot()
        assertEquals("Must record 0 rebuffers", 0, snap.realRebufferCount)
        assertEquals("Must record 1 HTTP stream error", 1, snap.httpErrorCount)
        // Must NEVER claim DEGRADED_MINOR_REBUFFER when realRebufferCount is 0!
        assertEquals(
            "Verdict must be PLAYBACK_ERROR, never DEGRADED_MINOR_REBUFFER when zero rebuffers occurred",
            PlaybackDiagnosticTracker.PlaybackStabilityVerdict.PLAYBACK_ERROR,
            snap.stabilityVerdict,
        )

        val report = PlaybackDiagnosticTracker.formatDiagnosticsSection()
        assertTrue(report.contains("Real Rebuffer Stalls:   0"))
        assertTrue(report.contains("PLAYBACK_ERROR"))
        assertFalse("Must not falsely declare mid-track rebuffer in description", report.contains("1 brief mid-track rebuffer"))
    }

    @Test
    fun testPrefetchAndArtworkErrorsDoNotCorruptPlaybackStreamErrorCounter() {
        // Active audio playback is completely stable
        PlaybackDiagnosticTracker.onTrackTransition("track:11221", "http://100.82.19.36:8790/v1/tracks/11221/stream", "AUTO", 0L, 0L)
        PlaybackDiagnosticTracker.onPlayerStateUpdate("track:11221", null, Player.STATE_READY, true, true, 10_000L, 30_000L)

        // Non-fatal auxiliary failures: artwork load times out, background prefetch times out
        PlaybackDiagnosticTracker.onArtworkError("http://100.82.19.36:8790/v1/tracks/11221/cover", IOException("SocketTimeoutException: timeout"))
        PlaybackDiagnosticTracker.onPrefetchError("track:11222", IOException("SocketTimeoutException: timeout"))

        val snap = PlaybackDiagnosticTracker.snapshot()
        assertEquals("Active stream error count must remain 0", 0, snap.httpErrorCount)
        assertEquals("Artwork error count must be 1", 1, snap.artworkErrorCount)
        assertEquals("Prefetch error count must be 1", 1, snap.prefetchErrorCount)
        assertEquals("Real rebuffer stalls must remain 0", 0, snap.realRebufferCount)
        assertEquals(
            "Active audio stream verdict must remain STABLE despite non-fatal auxiliary failures",
            PlaybackDiagnosticTracker.PlaybackStabilityVerdict.STABLE_AUDIBLE_STREAM,
            snap.stabilityVerdict,
        )

        val report = PlaybackDiagnosticTracker.formatDiagnosticsSection()
        assertTrue(report.contains("HTTP / Stream Errors:   0"))
        assertTrue(report.contains("Auxiliary Errors:       1 artwork errors, 1 prefetch warnings"))
        assertTrue(report.contains("STABLE (Zero mid-track rebuffer stalls, zero HTTP stream errors)"))
    }
}
