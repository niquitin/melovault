package com.bsdung.musicassistantcar.playback

import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable thread-safe snapshot of ExoPlayer state captured strictly on the Main Application Looper.
 */
data class PlayerStateSnapshot(
    val playbackState: Int,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val currentPosition: Long,
    val duration: Long,
    val mediaItemCount: Int,
    val currentMediaItemIndex: Int,
    val shuffleModeEnabled: Boolean,
    val repeatMode: Int,
    val playerError: PlaybackException?,
    val isCurrentMediaItemSeekable: Boolean,
)

/**
 * Self-contained Android Instrumentation Test Harness for actual ExoPlayer playback.
 * Instantiates real ExoPlayer on the Android runtime, decodes actual PCM WAV audio fixtures,
 * and asserts real wall-clock playhead advancement, queue transitions, and fault handling.
 *
 * Thread Safety: All ExoPlayer interactions (gets and sets) are strictly dispatched to the
 * Application Looper via instrumentation.runOnMainSync to prevent thread violations.
 */
@RunWith(AndroidJUnit4::class)
class RealExoPlayerInstrumentationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private var player: ExoPlayer? = null
    private var fixtureTrack1: File? = null
    private var fixtureTrack2: File? = null
    private var fixtureTrack3: File? = null
    private var fakeServer: FakeHttpStreamServer? = null

    @Before
    fun setUp() {
        // Generate isolated, unencumbered synthetic WAV audio fixtures (5s each)
        fixtureTrack1 = TestAudioSynthesizer.createWavFile(context, "inst_track_1.wav", durationMs = 5000L, frequencyHz = 440.0)
        fixtureTrack2 = TestAudioSynthesizer.createWavFile(context, "inst_track_2.wav", durationMs = 5000L, frequencyHz = 880.0)
        fixtureTrack3 = TestAudioSynthesizer.createWavFile(context, "inst_track_3.wav", durationMs = 5000L, frequencyHz = 0.0) // Silence
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            player?.release()
            player = null
        }
        fakeServer?.stop()
        fakeServer = null
        TestAudioSynthesizer.cleanUp(context)
    }

    // =========================================================================
    // THREAD-SAFE HELPERS (MANDATORY MAIN LOOPER DISPATCH)
    // =========================================================================

    private fun runOnMain(action: (ExoPlayer) -> Unit) {
        instrumentation.runOnMainSync {
            val p = player ?: error("Player is not initialized")
            action(p)
        }
    }

    private fun getPlayerSnapshot(): PlayerStateSnapshot {
        var snapshot: PlayerStateSnapshot? = null
        instrumentation.runOnMainSync {
            val p = player ?: error("Player is not initialized")
            snapshot = PlayerStateSnapshot(
                playbackState = p.playbackState,
                playWhenReady = p.playWhenReady,
                isPlaying = p.isPlaying,
                currentPosition = p.currentPosition,
                duration = p.duration,
                mediaItemCount = p.mediaItemCount,
                currentMediaItemIndex = p.currentMediaItemIndex,
                shuffleModeEnabled = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
                playerError = p.playerError,
                isCurrentMediaItemSeekable = p.isCurrentMediaItemSeekable,
            )
        }
        return snapshot ?: error("Failed to capture PlayerStateSnapshot on main thread")
    }

    // =========================================================================
    // 1. REAL EXOPLAYER WALL-CLOCK PLAYHEAD & PLAY/PAUSE/SEEK
    // =========================================================================

    @Test
    fun testRealExoPlayer_playheadAdvancesProportionalToWallTime() {
        val readyLatch = CountDownLatch(1)
        val file = fixtureTrack1 ?: error("Fixture track missing")

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        readyLatch.countDown()
                    }
                }
            })
            exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            exo.prepare()
            exo.play()
        }

        assertTrue("Player must reach STATE_READY within 5s", readyLatch.await(5, TimeUnit.SECONDS))

        // Measure playhead progression over real wall time
        SystemClock.sleep(500) // Allow audio clock to settle
        val pos1 = getPlayerSnapshot().currentPosition
        val wallStart = SystemClock.elapsedRealtime()

        SystemClock.sleep(1500) // 1.5 seconds real elapsed time
        val pos2 = getPlayerSnapshot().currentPosition
        val wallElapsed = SystemClock.elapsedRealtime() - wallStart

        assertTrue("Playhead must advance monotonically: pos1=$pos1, pos2=$pos2", pos2 > pos1)
        val deltaPlayhead = pos2 - pos1
        assertTrue(
            "Playhead advancement ($deltaPlayhead ms) must be proportional to wall elapsed time ($wallElapsed ms)",
            deltaPlayhead in (wallElapsed - 500)..(wallElapsed + 500)
        )

        // Pause test: position must freeze
        runOnMain { it.pause() }
        SystemClock.sleep(300)
        val posPaused1 = getPlayerSnapshot().currentPosition
        SystemClock.sleep(600)
        val posPaused2 = getPlayerSnapshot().currentPosition
        assertTrue("Paused player must freeze playhead: $posPaused1 vs $posPaused2", Math.abs(posPaused2 - posPaused1) < 50)

        // Seek test: position moves near target
        runOnMain { it.seekTo(3500L) }
        SystemClock.sleep(300)
        val posSeeked = getPlayerSnapshot().currentPosition
        assertTrue("Seek target 3500ms must be reached: posSeeked=$posSeeked", Math.abs(posSeeked - 3500L) < 300)
    }

    // =========================================================================
    // 2. QUEUE TRANSITIONS & PLAYBACK MODES ON REAL EXOPLAYER
    // =========================================================================

    @Test
    fun testRealExoPlayer_queueTransitionsAndModes() {
        val f1 = fixtureTrack1 ?: error("Fixture 1 missing")
        val f2 = fixtureTrack2 ?: error("Fixture 2 missing")
        val f3 = fixtureTrack3 ?: error("Fixture 3 missing")

        val readyLatch = CountDownLatch(1)

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        readyLatch.countDown()
                    }
                }
            })
            exo.setMediaItems(
                listOf(
                    MediaItem.fromUri(Uri.fromFile(f1)),
                    MediaItem.fromUri(Uri.fromFile(f2)),
                    MediaItem.fromUri(Uri.fromFile(f3)),
                )
            )
            exo.prepare()
        }

        assertTrue("ExoPlayer queue must reach STATE_READY within 5s", readyLatch.await(5, TimeUnit.SECONDS))

        var snapshot = getPlayerSnapshot()
        assertEquals(3, snapshot.mediaItemCount)
        assertEquals(0, snapshot.currentMediaItemIndex)

        // Advance to next media item
        runOnMain { it.seekToNextMediaItem() }
        SystemClock.sleep(200)
        snapshot = getPlayerSnapshot()
        assertEquals(1, snapshot.currentMediaItemIndex)

        // Repeat mode ALL wraps to index 0 when skipping from last track
        runOnMain {
            it.repeatMode = Player.REPEAT_MODE_ALL
            it.seekToDefaultPosition(2) // Jump to last track
        }
        SystemClock.sleep(200)
        snapshot = getPlayerSnapshot()
        assertEquals(2, snapshot.currentMediaItemIndex)

        runOnMain { it.seekToNextMediaItem() }
        SystemClock.sleep(200)
        snapshot = getPlayerSnapshot()
        assertEquals("Repeat ALL must wrap to index 0", 0, snapshot.currentMediaItemIndex)

        // Shuffle mode toggles without modifying queue count
        runOnMain { it.shuffleModeEnabled = true }
        snapshot = getPlayerSnapshot()
        assertTrue(snapshot.shuffleModeEnabled)
        assertEquals(3, snapshot.mediaItemCount)
    }

    // =========================================================================
    // 3. FAULT INJECTION WITH IN-PROCESS FAKE HTTP STREAM SERVER
    // =========================================================================

    @Test
    fun testRealExoPlayer_faultInjection_http401_unrecoverableError() {
        val server = FakeHttpStreamServer().apply { start() }
        fakeServer = server

        val errorRef = AtomicReference<PlaybackException?>(null)
        val errorLatch = CountDownLatch(1)

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    errorRef.set(error)
                    errorLatch.countDown()
                }
            })
            exo.setMediaItem(MediaItem.fromUri("${server.baseUrl()}/stream/401"))
            exo.prepare()
            exo.play()
        }

        assertTrue("ExoPlayer must receive PlaybackException on HTTP 401 within 5s", errorLatch.await(5, TimeUnit.SECONDS))
        val err = errorRef.get()
        assertNotNull(err)
        assertFalse("HTTP 401 must not trigger endless network retry", NetworkRecoveryPolicy.isRecoverableNetworkError(err!!))
    }

    @Test
    fun testRealExoPlayer_faultInjection_http404_unrecoverableError() {
        val server = FakeHttpStreamServer().apply { start() }
        fakeServer = server

        val errorRef = AtomicReference<PlaybackException?>(null)
        val errorLatch = CountDownLatch(1)

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    errorRef.set(error)
                    errorLatch.countDown()
                }
            })
            exo.setMediaItem(MediaItem.fromUri("${server.baseUrl()}/stream/404"))
            exo.prepare()
            exo.play()
        }

        assertTrue("ExoPlayer must receive PlaybackException on HTTP 404 within 5s", errorLatch.await(5, TimeUnit.SECONDS))
        val err = errorRef.get()
        assertNotNull(err)
        assertFalse("HTTP 404 must not trigger endless network retry", NetworkRecoveryPolicy.isRecoverableNetworkError(err!!))
    }

    @Test
    fun testRealExoPlayer_faultInjection_http503_errorHandled() {
        val server = FakeHttpStreamServer().apply { start() }
        fakeServer = server

        val errorRef = AtomicReference<PlaybackException?>(null)
        val errorLatch = CountDownLatch(1)

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    errorRef.set(error)
                    errorLatch.countDown()
                }
            })
            exo.setMediaItem(MediaItem.fromUri("${server.baseUrl()}/stream/503"))
            exo.prepare()
            exo.play()
        }

        assertTrue("ExoPlayer must receive PlaybackException on HTTP 503 within 5s", errorLatch.await(5, TimeUnit.SECONDS))
        val err = errorRef.get()
        assertNotNull(err)
    }

    @Test
    fun testRealExoPlayer_faultInjection_partialStream_abruptTermination() {
        val server = FakeHttpStreamServer().apply { start() }
        fakeServer = server

        val errorRef = AtomicReference<PlaybackException?>(null)
        val errorLatch = CountDownLatch(1)

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    errorRef.set(error)
                    errorLatch.countDown()
                }
            })
            exo.setMediaItem(MediaItem.fromUri("${server.baseUrl()}/stream/partial"))
            exo.prepare()
            exo.play()
        }

        assertTrue("ExoPlayer must handle abrupt stream termination within 5s", errorLatch.await(5, TimeUnit.SECONDS))
        val err = errorRef.get()
        assertNotNull(err)
    }

    @Test
    fun testRealExoPlayer_faultInjection_validLocalhostStream_playsSuccessfully() {
        val f1 = fixtureTrack1 ?: error("Fixture missing")
        val server = FakeHttpStreamServer(f1).apply { start() }
        fakeServer = server

        val readyLatch = CountDownLatch(1)
        val errorRef = AtomicReference<PlaybackException?>(null)

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        readyLatch.countDown()
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    errorRef.set(error)
                }
            })
            exo.setMediaItem(MediaItem.fromUri("${server.baseUrl()}/stream/valid"))
            exo.prepare()
            exo.play()
        }

        assertTrue("Valid stream on localhost must reach STATE_READY within 5s", readyLatch.await(5, TimeUnit.SECONDS))
        assertNull("Valid stream must not emit player error", errorRef.get())

        SystemClock.sleep(800)
        val snapshot = getPlayerSnapshot()
        assertTrue("Valid stream must be playing or ready", snapshot.playbackState == Player.STATE_READY)
        assertTrue("Playhead must advance on valid stream", snapshot.currentPosition > 0L)
    }

    // =========================================================================
    // 4. POLICY GUARDS & LIFECYCLE TEARDOWN SAFETY
    // =========================================================================

    @Test
    fun testPolicyGuards_networkRecoveryTeardownAndFirstAttempt() {
        val errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

        // First attempt ever: nowMs = 1_000L, lastAttempt = 0L -> Allowed
        assertTrue(
            NetworkRecoveryPolicy.canAttemptRecovery(
                isShuttingDown = false,
                isPlayerInitialized = true,
                queueSize = 5,
                networkRecoveryInProgress = false,
                nowMs = 1_000L,
                lastNetworkRecoveryAttemptMs = 0L,
                errorCode = errorCode,
            )
        )

        // Shutdown guard: if service is tearing down -> Blocked
        assertFalse(
            NetworkRecoveryPolicy.canAttemptRecovery(
                isShuttingDown = true,
                isPlayerInitialized = true,
                queueSize = 5,
                networkRecoveryInProgress = false,
                nowMs = 1_000L,
                lastNetworkRecoveryAttemptMs = 0L,
                errorCode = errorCode,
            )
        )

        // Uninitialized player guard -> Blocked
        assertFalse(
            NetworkRecoveryPolicy.canAttemptRecovery(
                isShuttingDown = false,
                isPlayerInitialized = false,
                queueSize = 5,
                networkRecoveryInProgress = false,
                nowMs = 1_000L,
                lastNetworkRecoveryAttemptMs = 0L,
                errorCode = errorCode,
            )
        )

        // Empty queue guard -> Blocked
        assertFalse(
            NetworkRecoveryPolicy.canAttemptRecovery(
                isShuttingDown = false,
                isPlayerInitialized = true,
                queueSize = 0,
                networkRecoveryInProgress = false,
                nowMs = 1_000L,
                lastNetworkRecoveryAttemptMs = 0L,
                errorCode = errorCode,
            )
        )
    }

    @Test
    fun testRealExoPlayer_releaseTeardownSafety() {
        val f1 = fixtureTrack1 ?: error("Fixture missing")

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(f1)))
            exo.prepare()
            // Release immediately to verify lifecycle clean teardown
            exo.release()
            assertEquals("Released player playbackState should be STATE_IDLE", Player.STATE_IDLE, exo.playbackState)
        }
    }

    // =========================================================================
    // 5. PILOT SOAK (3 MINUTES REAL-TIME ON ANDROID TARGET — OFF BY DEFAULT)
    // =========================================================================

    /**
     * Real-time 3-minute pilot soak playing actual audio on Android target.
     * Skipped by default; trigger via:
     * adb shell am instrument -e pilotSoak true -w com.bsdung.musicassistantcar.test/...
     */
    @Test
    fun testRealExoPlayer_pilotSoak_manualTriggerOnly() {
        val args = InstrumentationRegistry.getArguments()
        val isPilotRequested = args.getString("pilotSoak") == "true"
        assumeTrue("3-minute Pilot Soak skipped by default. Run with -e pilotSoak true", isPilotRequested)

        val targetDurationMs = 180_000L // 3 minutes
        val f1 = fixtureTrack1 ?: error("Fixture 1 missing")
        val f2 = fixtureTrack2 ?: error("Fixture 2 missing")

        val readyLatch = CountDownLatch(1)
        val playerErrorRef = AtomicReference<PlaybackException?>(null)
        var transitions = 0
        var stalls = 0

        runOnMain { exo ->
            exo.repeatMode = Player.REPEAT_MODE_ALL
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        readyLatch.countDown()
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        stalls++
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    transitions++
                }

                override fun onPlayerError(error: PlaybackException) {
                    playerErrorRef.set(error)
                }
            })
            exo.setMediaItems(
                listOf(
                    MediaItem.fromUri(Uri.fromFile(f1)),
                    MediaItem.fromUri(Uri.fromFile(f2))
                )
            )
            exo.prepare()
            exo.play()
        }

        // 1. Must reach STATE_READY within bounded timeout (10s)
        assertTrue("ExoPlayer must reach STATE_READY within 10s", readyLatch.await(10, TimeUnit.SECONDS))

        val startTimeMs = SystemClock.elapsedRealtime()
        var lastSampledPosition = -1L
        var lastMediaItemIndex = -1

        // 2. Periodic sampling loop with heartbeats
        val sampleIntervalMs = 10_000L
        while (SystemClock.elapsedRealtime() - startTimeMs < targetDurationMs) {
            SystemClock.sleep(sampleIntervalMs)

            // Fail-fast on player error
            val currentError = playerErrorRef.get()
            assertNull("Player must not encounter PlaybackException during soak: ${currentError?.message}", currentError)

            val snapshot = getPlayerSnapshot()

            // Verify playback state is active (READY or transient BUFFERING)
            assertTrue(
                "Player state must be READY or BUFFERING during soak: actual=${snapshot.playbackState}",
                snapshot.playbackState == Player.STATE_READY || snapshot.playbackState == Player.STATE_BUFFERING
            )
            assertTrue("playWhenReady must remain true", snapshot.playWhenReady)

            // Verify playhead motion
            if (snapshot.currentMediaItemIndex == lastMediaItemIndex) {
                assertTrue(
                    "Playhead must advance monotonically within same track: last=$lastSampledPosition, curr=${snapshot.currentPosition}",
                    snapshot.currentPosition >= lastSampledPosition
                )
            }
            lastSampledPosition = snapshot.currentPosition
            lastMediaItemIndex = snapshot.currentMediaItemIndex

            // Memory metrics capture (measured: Debug.MemoryInfo.totalPss and NativeHeap)
            val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
            val memInfo = Debug.MemoryInfo().apply { Debug.getMemoryInfo(this) }
            val totalPssMb = memInfo.totalPss / 1024.0
            val elapsedSec = (SystemClock.elapsedRealtime() - startTimeMs) / 1000

            println(
                "[PilotSoak Heartbeat] ${elapsedSec}s / 180s | trackIndex=${snapshot.currentMediaItemIndex} | " +
                    "pos=${snapshot.currentPosition}ms | transitions=$transitions | stalls=$stalls | " +
                    "nativeHeap=${"%.2f".format(nativeHeapMb)}MB | totalPss=${"%.2f".format(totalPssMb)}MB"
            )
        }

        val totalWallElapsedMs = SystemClock.elapsedRealtime() - startTimeMs
        runOnMain { it.pause() }

        // 3. Substantive post-soak assertions
        assertNull("No player error during pilot soak", playerErrorRef.get())
        assertTrue("Wall duration must be at least 175s: actual=${totalWallElapsedMs}ms", totalWallElapsedMs >= 175_000L)
        assertTrue("Transitions must occur when looping 5s tracks for 3 minutes: actual=$transitions", transitions >= 10)
        assertTrue("Stall count must remain bounded: actual=$stalls", stalls <= 10)
    }

    // =========================================================================
    // 6. EXTENDED SOAK (30 MINUTES REAL-TIME ON ANDROID TARGET — OFF BY DEFAULT)
    // =========================================================================

    /**
     * Real-time 30-minute soak playing actual audio on Android target.
     * Skipped by default; trigger via:
     * adb shell am instrument -e soak30Min true -w com.bsdung.musicassistantcar.test/...
     */
    @Test
    fun testRealExoPlayer_30MinSoak_manualTriggerOnly() {
        val args = InstrumentationRegistry.getArguments()
        val is30MinRequested = args.getString("soak30Min") == "true"
        assumeTrue("30-minute Real Soak skipped by default. Run with -e soak30Min true", is30MinRequested)

        val targetDurationMs = 1800_000L // 30 minutes
        val f1 = fixtureTrack1 ?: error("Fixture 1 missing")
        val f2 = fixtureTrack2 ?: error("Fixture 2 missing")

        val readyLatch = CountDownLatch(1)
        val playerErrorRef = AtomicReference<PlaybackException?>(null)
        var transitions = 0
        var stalls = 0

        runOnMain { exo ->
            exo.repeatMode = Player.REPEAT_MODE_ALL
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        readyLatch.countDown()
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        stalls++
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    transitions++
                }

                override fun onPlayerError(error: PlaybackException) {
                    playerErrorRef.set(error)
                }
            })
            exo.setMediaItems(
                listOf(
                    MediaItem.fromUri(Uri.fromFile(f1)),
                    MediaItem.fromUri(Uri.fromFile(f2))
                )
            )
            exo.prepare()
            exo.play()
        }

        assertTrue("ExoPlayer must reach STATE_READY within 10s", readyLatch.await(10, TimeUnit.SECONDS))

        val startTimeMs = SystemClock.elapsedRealtime()
        var lastSampledPosition = -1L
        var lastMediaItemIndex = -1

        val sampleIntervalMs = 60_000L // 1-minute heartbeats
        while (SystemClock.elapsedRealtime() - startTimeMs < targetDurationMs) {
            SystemClock.sleep(sampleIntervalMs)

            val currentError = playerErrorRef.get()
            assertNull("Player must not encounter PlaybackException during soak: ${currentError?.message}", currentError)

            val snapshot = getPlayerSnapshot()

            assertTrue(
                "Player state must be READY or BUFFERING during soak: actual=${snapshot.playbackState}",
                snapshot.playbackState == Player.STATE_READY || snapshot.playbackState == Player.STATE_BUFFERING
            )
            assertTrue("playWhenReady must remain true", snapshot.playWhenReady)

            if (snapshot.currentMediaItemIndex == lastMediaItemIndex) {
                assertTrue(
                    "Playhead must advance monotonically within same track: last=$lastSampledPosition, curr=${snapshot.currentPosition}",
                    snapshot.currentPosition >= lastSampledPosition
                )
            }
            lastSampledPosition = snapshot.currentPosition
            lastMediaItemIndex = snapshot.currentMediaItemIndex

            val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
            val memInfo = Debug.MemoryInfo().apply { Debug.getMemoryInfo(this) }
            val totalPssMb = memInfo.totalPss / 1024.0
            val elapsedMin = (SystemClock.elapsedRealtime() - startTimeMs) / 60_000

            println(
                "[30MinSoak Heartbeat] ${elapsedMin}m / 30m | trackIndex=${snapshot.currentMediaItemIndex} | " +
                    "pos=${snapshot.currentPosition}ms | transitions=$transitions | stalls=$stalls | " +
                    "nativeHeap=${"%.2f".format(nativeHeapMb)}MB | totalPss=${"%.2f".format(totalPssMb)}MB"
            )
        }

        val totalWallElapsedMs = SystemClock.elapsedRealtime() - startTimeMs
        runOnMain { it.pause() }

        assertNull("No player error during 30-minute soak", playerErrorRef.get())
        assertTrue("Wall duration must be at least 1790s: actual=${totalWallElapsedMs}ms", totalWallElapsedMs >= 1790_000L)
        assertTrue("Transitions must occur during 30-minute loop: actual=$transitions", transitions >= 100)
        assertTrue("Stall count must remain bounded: actual=$stalls", stalls <= 50)
    }
}
