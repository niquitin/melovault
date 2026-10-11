package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * MeloVault Connectivity & Anti-Flapping Test Suite.
 *
 * Verifies the Shanling M3 Ultra DAP online flickering resolution:
 * 1. Initial State & State Snapshot completeness.
 * 2. Multi-dimensional State: Transport, Backend, Auth, Playback.
 * 3. Anti-Flapping Damping & Hysteresis: Transient timeouts do NOT flicker UI to OFFLINE.
 * 4. Grace Period Enforcement: Only transitions to OFFLINE after 3 consecutive failures AND >= 10s elapsed.
 * 5. Instant Recovery: Successful probe immediately restores ONLINE status and resets failure counts.
 * 6. Auth Error Specialization: HTTP 401 routes immediately to AUTH_ERROR.
 * 7. Client Error Filtering: HTTP 404/400 (missing lyrics/covers) do not degrade server health.
 * 8. Screen Wake-up Stability: Fresh onResume checks do not flash CONNECTING.
 * 9. Playback Transport Coordination: Tracks buffer, cache hit, network stream, and error states.
 * 10. Sanitized Diagnostics: Reports contains full audit metrics with 0 credential leakage.
 */
class ConnectivityIntegrationTest {

    private var virtualTimeMs: Long = 1_000_000L
    private lateinit var fakeApiClient: FakeCarApiClient
    private lateinit var stateManager: ConnectionStateManager
    private val recordedStatuses = CopyOnWriteArrayList<ConnectionStateManager.UiConnectionStatus>()

    class FakeCarApiClient : CarApiClient() {
        var probeResult: String = "http://100.82.19.36:8790"
        var shouldThrow: Boolean = false
        var probeCount: Int = 0

        override fun probe(): String {
            probeCount++
            if (shouldThrow) {
                throw IOException("Connection timed out (tailscale handshake failed)")
            }
            return probeResult
        }
    }

    @Before
    fun setUp() {
        virtualTimeMs = 1_000_000L
        fakeApiClient = FakeCarApiClient()
        recordedStatuses.clear()

        // Create manager with synchronous/direct background executor for deterministic testing
        val directExecutor = Executors.newSingleThreadScheduledExecutor()
        stateManager = ConnectionStateManager.createForTesting(
            context = null,
            apiClientSupplier = { fakeApiClient },
            mainHandler = null, // Direct execution without Android Looper
            executor = directExecutor,
            timeProvider = { virtualTimeMs }
        )

        stateManager.addListener { status, _ ->
            recordedStatuses.add(status)
        }
    }

    @Test
    fun testInitialState() {
        val snap = stateManager.snapshot()
        assertEquals(ConnectionStateManager.UiConnectionStatus.CONNECTING, snap.uiStatus)
        assertEquals(ConnectionStateManager.BackendHealth.CHECKING, snap.backendHealth)
        assertEquals(ConnectionStateManager.AuthStatus.UNKNOWN, snap.authStatus)
        assertEquals(ConnectionStateManager.PlaybackTransport.IDLE, snap.playbackTransport)
        assertEquals(0, snap.consecutiveFailures)
    }

    @Test
    fun testSuccessfulProbeTransitionsToOnline() {
        fakeApiClient.probeResult = "http://100.82.19.36:8790"
        stateManager.probeBackend(quiet = false)

        Thread.sleep(50) // wait for executor task
        val snap = stateManager.snapshot()

        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, snap.uiStatus)
        assertEquals(ConnectionStateManager.BackendHealth.REACHABLE, snap.backendHealth)
        assertEquals(ConnectionStateManager.AuthStatus.AUTHENTICATED, snap.authStatus)
        assertEquals("http://100.82.19.36:8790", snap.activeEndpoint)
        assertEquals(0, snap.consecutiveFailures)
        assertTrue(recordedStatuses.contains(ConnectionStateManager.UiConnectionStatus.ONLINE))
    }

    @Test
    fun testDampingTransientErrorsKeepOnline() {
        // Step 1: Establish ONLINE state
        stateManager.reportApiCall("/health", "http://100.82.19.36:8790", success = true, latencyMs = 85L)
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)

        recordedStatuses.clear()

        // Step 2: First transient failure (e.g. DAP screen just locked, tailscale re-handshake)
        virtualTimeMs += 1_000L
        stateManager.reportApiCall(
            "/api/tracks",
            "http://100.82.19.36:8790",
            success = false,
            error = IOException("SocketTimeoutException: Read timed out")
        )

        var snap = stateManager.snapshot()
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, snap.uiStatus) // Damped! Still ONLINE
        assertEquals(ConnectionStateManager.BackendHealth.DEGRADED, snap.backendHealth)
        assertEquals(1, snap.consecutiveFailures)
        assertFalse(recordedStatuses.contains(ConnectionStateManager.UiConnectionStatus.OFFLINE))

        // Step 3: Second transient failure at t = +3s (< 10s grace period)
        virtualTimeMs += 2_000L
        stateManager.reportApiCall(
            "/api/lyrics",
            "http://100.82.19.36:8790",
            success = false,
            error = IOException("ConnectException: Failed to connect")
        )

        snap = stateManager.snapshot()
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, snap.uiStatus) // Damped! Still ONLINE
        assertEquals(ConnectionStateManager.BackendHealth.DEGRADED, snap.backendHealth)
        assertEquals(2, snap.consecutiveFailures)
        assertFalse(recordedStatuses.contains(ConnectionStateManager.UiConnectionStatus.OFFLINE))
    }

    @Test
    fun testHysteresisTriggersOfflineOnlyAfterThresholdAndGracePeriod() {
        // Step 1: Establish ONLINE state
        stateManager.reportApiCall("/health", "http://100.82.19.36:8790", success = true)

        // Fail 1 at t = 0
        stateManager.reportApiCall("/api/ping", null, success = false, error = IOException("timeout"))
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)

        // Fail 2 at t = 4s
        virtualTimeMs += 4_000L
        stateManager.reportApiCall("/api/ping", null, success = false, error = IOException("timeout"))
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)

        // Fail 3 at t = 8s (< 10s GRACE_PERIOD_MS): Fail count is 3, but grace period not elapsed!
        virtualTimeMs += 4_000L // total elapsed = 8s
        stateManager.reportApiCall("/api/ping", null, success = false, error = IOException("timeout"))
        assertEquals(3, stateManager.snapshot().consecutiveFailures)
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus) // Still damped!

        // Fail 4 at t = 12s (>= 10s GRACE_PERIOD_MS and failures >= 3): Now transitions to OFFLINE!
        virtualTimeMs += 4_000L // total elapsed = 12s
        stateManager.reportApiCall("/api/ping", null, success = false, error = IOException("timeout"))

        val snap = stateManager.snapshot()
        assertEquals(ConnectionStateManager.UiConnectionStatus.OFFLINE, snap.uiStatus)
        assertEquals(ConnectionStateManager.BackendHealth.UNREACHABLE, snap.backendHealth)
        assertEquals(4, snap.consecutiveFailures)
    }

    @Test
    fun testInstantRecoveryResetsFailures() {
        // Put in OFFLINE state
        stateManager.reportApiCall("/api/ping", null, success = false, error = IOException("down"))
        virtualTimeMs += 15_000L
        stateManager.reportApiCall("/api/ping", null, success = false, error = IOException("down"))
        stateManager.reportApiCall("/api/ping", null, success = false, error = IOException("down"))
        assertEquals(ConnectionStateManager.UiConnectionStatus.OFFLINE, stateManager.snapshot().uiStatus)

        // Backend recovers
        stateManager.reportApiCall("/health", "http://100.82.19.36:8790", success = true, latencyMs = 42L)

        val snap = stateManager.snapshot()
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, snap.uiStatus)
        assertEquals(ConnectionStateManager.BackendHealth.REACHABLE, snap.backendHealth)
        assertEquals(0, snap.consecutiveFailures)
        assertEquals(42L, snap.lastProbeLatencyMs)
    }

    @Test
    fun testAuth401TransitionsImmediatelyToAuthError() {
        stateManager.reportApiCall("/health", "http://100.82.19.36:8790", success = true)
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)

        // 401 Unauthorized occurs
        stateManager.reportApiCall("/api/playlists", "http://100.82.19.36:8790", success = false, httpCode = 401)

        val snap = stateManager.snapshot()
        assertEquals(ConnectionStateManager.UiConnectionStatus.AUTH_ERROR, snap.uiStatus)
        assertEquals(ConnectionStateManager.AuthStatus.INVALID_OR_EXPIRED, snap.authStatus)
    }

    @Test
    fun testClient404DoesNotDegradeServerHealth() {
        stateManager.reportApiCall("/health", "http://100.82.19.36:8790", success = true)

        // 404 Not Found on lyrics
        stateManager.reportApiCall("/api/lyrics/nonexistent", "http://100.82.19.36:8790", success = false, httpCode = 404)

        val snap = stateManager.snapshot()
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, snap.uiStatus)
        assertEquals(ConnectionStateManager.BackendHealth.REACHABLE, snap.backendHealth)
        assertEquals(0, snap.consecutiveFailures)
    }

    @Test
    fun testCheckOnResumePreservesFreshOnline() {
        // Establish ONLINE
        stateManager.reportApiCall("/health", "http://100.82.19.36:8790", success = true)
        fakeApiClient.probeCount = 0

        // Resume at t = +10s (freshness window is 30s)
        virtualTimeMs += 10_000L
        stateManager.checkOnResume()

        // Should NOT trigger probe or change UI
        assertEquals(0, fakeApiClient.probeCount)
        assertEquals(ConnectionStateManager.UiConnectionStatus.ONLINE, stateManager.snapshot().uiStatus)
    }

    @Test
    fun testPlaybackTransportStateTracking() {
        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.BUFFERING)
        assertEquals(ConnectionStateManager.PlaybackTransport.BUFFERING, stateManager.snapshot().playbackTransport)

        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.CACHE_OR_OFFLINE)
        assertEquals(ConnectionStateManager.PlaybackTransport.CACHE_OR_OFFLINE, stateManager.snapshot().playbackTransport)

        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.NETWORK_STREAM)
        assertEquals(ConnectionStateManager.PlaybackTransport.NETWORK_STREAM, stateManager.snapshot().playbackTransport)

        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.ERROR)
        assertEquals(ConnectionStateManager.PlaybackTransport.ERROR, stateManager.snapshot().playbackTransport)
    }

    @Test
    fun testDiagnosticsReportSanitizationAndMetrics() {
        stateManager.reportApiCall("/health", "http://100.82.19.36:8790", success = true, latencyMs = 75L)
        stateManager.onPlaybackTransportChanged(ConnectionStateManager.PlaybackTransport.CACHE_OR_OFFLINE)

        val report = stateManager.exportDiagnosticsReport()

        assertTrue(report.contains("MELOVAULT CONNECTIVITY DIAGNOSTICS REPORT"))
        assertTrue(report.contains("UI Status:              ONLINE"))
        assertTrue(report.contains("Active Endpoint:        http://100.82.19.36:8790"))
        assertTrue(report.contains("Playback Transport:     CACHE_OR_OFFLINE"))
        assertTrue(report.contains("Probe Timeout Config:   5000 ms"))
        assertTrue(report.contains("Grace Period:           10000 ms"))
        assertTrue(report.contains("Failure Threshold:      3 consecutive fails"))
        assertTrue(report.contains("Damping / Hysteresis:   ACTIVE"))

        // Verification: Absolutely NO sensitive authentication tokens or passwords
        assertFalse(report.contains("CAR_API_TOKEN"))
        assertFalse(report.contains("token="))
        assertFalse(report.contains("Bearer"))
        assertFalse(report.contains("password"))
    }
}
