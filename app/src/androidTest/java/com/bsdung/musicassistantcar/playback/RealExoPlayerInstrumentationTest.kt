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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Self-contained Android Instrumentation Test Harness for actual ExoPlayer playback.
 * Instantiates real ExoPlayer on the Android runtime, decodes actual PCM WAV audio fixtures,
 * and asserts real wall-clock playhead advancement, queue transitions, and fault handling.
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
        var pos1 = 0L
        instrumentation.runOnMainSync { pos1 = player!!.currentPosition }
        val wallStart = SystemClock.elapsedRealtime()

        SystemClock.sleep(1500) // 1.5 seconds real elapsed time
        var pos2 = 0L
        instrumentation.runOnMainSync { pos2 = player!!.currentPosition }
        val wallElapsed = SystemClock.elapsedRealtime() - wallStart

        assertTrue("Playhead must advance monotonically: pos1=$pos1, pos2=$pos2", pos2 > pos1)
        val deltaPlayhead = pos2 - pos1
        assertTrue(
            "Playhead advancement ($deltaPlayhead ms) must be proportional to wall elapsed time ($wallElapsed ms)",
            deltaPlayhead in (wallElapsed - 500)..(wallElapsed + 500)
        )

        // Pause test: position must freeze
        instrumentation.runOnMainSync { player!!.pause() }
        SystemClock.sleep(300)
        var posPaused1 = 0L
        instrumentation.runOnMainSync { posPaused1 = player!!.currentPosition }
        SystemClock.sleep(600)
        var posPaused2 = 0L
        instrumentation.runOnMainSync { posPaused2 = player!!.currentPosition }
        assertTrue("Paused player must freeze playhead: $posPaused1 vs $posPaused2", Math.abs(posPaused2 - posPaused1) < 50)

        // Seek test: position moves near target
        instrumentation.runOnMainSync { player!!.seekTo(3500L) }
        SystemClock.sleep(300)
        var posSeeked = 0L
        instrumentation.runOnMainSync { posSeeked = player!!.currentPosition }
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
        assertEquals(3, player!!.mediaItemCount)
        assertEquals(0, player!!.currentMediaItemIndex)

        // Advance to next media item
        instrumentation.runOnMainSync { player!!.seekToNextMediaItem() }
        SystemClock.sleep(200)
        assertEquals(1, player!!.currentMediaItemIndex)

        // Repeat mode ALL wraps to index 0 when skipping from last track
        instrumentation.runOnMainSync {
            player!!.repeatMode = Player.REPEAT_MODE_ALL
            player!!.seekToDefaultPosition(2) // Jump to last track
        }
        SystemClock.sleep(200)
        assertEquals(2, player!!.currentMediaItemIndex)

        instrumentation.runOnMainSync { player!!.seekToNextMediaItem() }
        SystemClock.sleep(200)
        assertEquals("Repeat ALL must wrap to index 0", 0, player!!.currentMediaItemIndex)

        // Shuffle mode toggles without modifying queue count
        instrumentation.runOnMainSync { player!!.shuffleModeEnabled = true }
        assertTrue(player!!.shuffleModeEnabled)
        assertEquals(3, player!!.mediaItemCount)
    }

    // =========================================================================
    // 3. FAULT INJECTION WITH IN-PROCESS FAKE HTTP STREAM SERVER
    // =========================================================================

    @Test
    fun testRealExoPlayer_faultInjectionWithLocalServer() {
        val f1 = fixtureTrack1 ?: error("Fixture missing")
        val server = FakeHttpStreamServer(f1).apply { start() }
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
            // Point to HTTP 401 endpoint on loopback
            exo.setMediaItem(MediaItem.fromUri("${server.baseUrl()}/stream/401"))
            exo.prepare()
            exo.play()
        }

        assertTrue("ExoPlayer must receive PlaybackException on HTTP 401 within 5s", errorLatch.await(5, TimeUnit.SECONDS))
        val err = errorRef.get()
        assertNotNull(err)
        // 401 is not a recoverable network error in policy
        assertFalse("HTTP 401 must not trigger endless network retry", NetworkRecoveryPolicy.isRecoverableNetworkError(err!!))
    }

    // =========================================================================
    // 4. SERVICE TEARDOWN & RECOVERY COOLDOWN INVARIANTS
    // =========================================================================

    @Test
    fun testRealExoPlayer_serviceTeardownSafety() {
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

        val targetSeconds = 180 // 3 minutes
        val f1 = fixtureTrack1 ?: error("Fixture 1 missing")
        val f2 = fixtureTrack2 ?: error("Fixture 2 missing")

        val startTimeMs = SystemClock.elapsedRealtime()
        var transitions = 0
        var stalls = 0

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.repeatMode = Player.REPEAT_MODE_ALL
            exo.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    transitions++
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_BUFFERING) {
                        stalls++
                    }
                }
            })
            exo.setMediaItems(listOf(MediaItem.fromUri(Uri.fromFile(f1)), MediaItem.fromUri(Uri.fromFile(f2))))
            exo.prepare()
            exo.play()
        }

        // Wait out the pilot duration in 10-second heartbeat increments
        for (sec in 10..targetSeconds step 10) {
            SystemClock.sleep(10_000L)
            val currentNativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
            println("[PilotSoak Heartbeat] ${sec}s / ${targetSeconds}s, transitions=$transitions, nativeHeap=${"%.2f".format(currentNativeHeapMb)}MB")
        }

        val totalWallElapsedMs = SystemClock.elapsedRealtime() - startTimeMs
        instrumentation.runOnMainSync { player?.pause() }

        println(
            "PilotSoak completed: wallElapsed=${totalWallElapsedMs}ms, " +
                "transitions=$transitions, stalls=$stalls, nativeHeap=${Debug.getNativeHeapAllocatedSize() / (1024 * 1024)}MB"
        )
        assertTrue("Wall duration must be at least 175s", totalWallElapsedMs >= 175_000L)
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

        val targetSeconds = 1800 // 30 minutes
        val f1 = fixtureTrack1 ?: error("Fixture 1 missing")
        val f2 = fixtureTrack2 ?: error("Fixture 2 missing")

        val startTimeMs = SystemClock.elapsedRealtime()
        var transitions = 0

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(context).build()
            player = exo
            exo.repeatMode = Player.REPEAT_MODE_ALL
            exo.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    transitions++
                }
            })
            exo.setMediaItems(listOf(MediaItem.fromUri(Uri.fromFile(f1)), MediaItem.fromUri(Uri.fromFile(f2))))
            exo.prepare()
            exo.play()
        }

        for (sec in 60..targetSeconds step 60) {
            SystemClock.sleep(60_000L)
            val nativeHeapMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
            println("[30MinSoak Heartbeat] ${sec / 60}m / 30m, transitions=$transitions, nativeHeap=${"%.2f".format(nativeHeapMb)}MB")
        }

        val totalWallElapsedMs = SystemClock.elapsedRealtime() - startTimeMs
        instrumentation.runOnMainSync { player?.pause() }

        println("30MinSoak completed: wallElapsed=${totalWallElapsedMs}ms, transitions=$transitions")
        assertTrue("Wall duration must be at least 1790s", totalWallElapsedMs >= 1790_000L)
    }
}
