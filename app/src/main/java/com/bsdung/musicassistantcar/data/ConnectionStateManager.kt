package com.bsdung.musicassistantcar.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.random.Random

/**
 * Enterprise-grade Connectivity & Health State Coordinator for MeloVault.
 *
 * Solves the Shanling M3 Ultra DAP online flickering issue:
 * 1. Multi-dimensional State: Differentiates physical network, backend reachability,
 *    authentication status, and active audio playback transport.
 * 2. Anti-Flapping Damping & Hysteresis: Eliminates rapid flipping between
 *    Connected/Connecting/Disconnected by enforcing a grace period and failure threshold
 *    before transitioning to OFFLINE. Background probes never flash CONNECTING.
 * 3. Safe Reconnect with Exponential Backoff + Jitter: Prevents stampedes and battery drain
 *    during Android Doze / Wi-Fi standby on specialized audio DAPs.
 * 4. Diagnostics & Auditing: Records sanitized connectivity metrics for troubleshooting
 *    without exposing tokens or secrets.
 */
class ConnectionStateManager(
    private val context: Context?,
    private val apiClientSupplier: () -> CarApiClient,
    private val mainHandler: Handler? = null,
    private val backgroundExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "MeloVault-ConnectivityCoordinator").apply { isDaemon = true }
    },
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        private const val TAG = "ConnectionStateMgr"

        // Hysteresis & Damping Parameters
        const val GRACE_PERIOD_MS = 10_000L // 10 seconds grace period before going OFFLINE
        const val FAILURE_THRESHOLD = 3     // Require 3 consecutive probe failures
        const val BACKGROUND_PROBE_INTERVAL_MS = 60_000L // Periodic health check when idle
        const val PROBE_TIMEOUT_MS = 5_000  // 5s connect/read timeout for DAP + VPN
        const val RESUME_FRESHNESS_MS = 30_000L // Don't re-probe on resume if checked recently

        // Exponential Backoff Parameters
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L

        @Volatile
        private var instance: ConnectionStateManager? = null

        fun getInstance(context: Context, apiClientSupplier: () -> CarApiClient): ConnectionStateManager {
            return instance ?: synchronized(this) {
                instance ?: ConnectionStateManager(
                    context.applicationContext,
                    apiClientSupplier,
                    runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
                ).also {
                    instance = it
                }
            }
        }

        /** Expose for testing to provide custom mocks/clocks. */
        fun createForTesting(
            context: Context? = null,
            apiClientSupplier: () -> CarApiClient,
            mainHandler: Handler? = null,
            executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
            timeProvider: () -> Long = { System.currentTimeMillis() }
        ): ConnectionStateManager {
            return ConnectionStateManager(context, apiClientSupplier, mainHandler, executor, timeProvider)
        }
    }

    enum class NetworkTransport {
        WIFI,
        CELLULAR,
        ETHERNET,
        VPN,
        NONE,
        UNKNOWN
    }

    enum class BackendHealth {
        REACHABLE,
        DEGRADED,
        UNREACHABLE,
        CHECKING
    }

    enum class AuthStatus {
        AUTHENTICATED,
        INVALID_OR_EXPIRED,
        UNKNOWN
    }

    enum class PlaybackTransport {
        NETWORK_STREAM,
        CACHE_OR_OFFLINE,
        BUFFERING,
        IDLE,
        ERROR
    }

    /**
     * Damped UI State rendered in the top-right pill.
     * Guaranteed not to flutter under transient network jitter.
     */
    enum class UiConnectionStatus(val label: String) {
        ONLINE("Online"),
        CONNECTING("Connecting"),
        OFFLINE("Offline"),
        AUTH_ERROR("Lỗi xác thực")
    }

    data class StateSnapshot(
        val uiStatus: UiConnectionStatus,
        val networkTransport: NetworkTransport,
        val isNetworkConnected: Boolean,
        val backendHealth: BackendHealth,
        val authStatus: AuthStatus,
        val playbackTransport: PlaybackTransport,
        val activeEndpoint: String?,
        val consecutiveFailures: Int,
        val lastSuccessElapsedMs: Long,
        val lastProbeLatencyMs: Long,
        val lastErrorMessage: String?
    )

    data class DiagnosticEvent(
        val timestampIso: String,
        val category: String,
        val message: String,
        val latencyMs: Long? = null,
        val httpCode: Int? = null
    )

    private val listeners = CopyOnWriteArrayList<(UiConnectionStatus, StateSnapshot) -> Unit>()
    private val diagnosticHistory = ConcurrentLinkedQueue<DiagnosticEvent>()
    private val maxHistorySize = 100

    @Volatile private var currentUiStatus = UiConnectionStatus.CONNECTING
    @Volatile private var currentTransport = NetworkTransport.UNKNOWN
    @Volatile private var isNetworkPhysicallyAvailable = true
    @Volatile private var currentBackendHealth = BackendHealth.CHECKING
    @Volatile private var currentAuthStatus = AuthStatus.UNKNOWN
    @Volatile private var currentPlaybackTransport = PlaybackTransport.IDLE
    @Volatile private var lastActiveEndpoint: String? = null
    @Volatile private var consecutiveProbeFailures = AtomicInteger(0)
    @Volatile private var firstFailureTimestampRealtime = 0L
    @Volatile private var lastSuccessTimestampRealtime = 0L
    @Volatile private var lastProbeLatencyMs = -1L
    @Volatile private var lastErrorMessage: String? = null
    @Volatile private var isProbeInProgress = AtomicBoolean(false)

    private var retryCount = AtomicInteger(0)
    private var scheduledProbeTask: ScheduledFuture<*>? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        SecureTokenStore.init(context)
        registerNetworkCallback()
    }

    fun addListener(listener: (UiConnectionStatus, StateSnapshot) -> Unit) {
        listeners.add(listener)
        // Immediately notify current state
        val snap = snapshot()
        listener(snap.uiStatus, snap)
    }

    fun removeListener(listener: (UiConnectionStatus, StateSnapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun snapshot(): StateSnapshot = StateSnapshot(
        uiStatus = currentUiStatus,
        networkTransport = currentTransport,
        isNetworkConnected = isNetworkPhysicallyAvailable,
        backendHealth = currentBackendHealth,
        authStatus = currentAuthStatus,
        playbackTransport = currentPlaybackTransport,
        activeEndpoint = lastActiveEndpoint ?: apiClientSupplier().activeEndpoint(),
        consecutiveFailures = consecutiveProbeFailures.get(),
        lastSuccessElapsedMs = lastSuccessTimestampRealtime,
        lastProbeLatencyMs = lastProbeLatencyMs,
        lastErrorMessage = lastErrorMessage
    )

    fun onPlaybackTransportChanged(transport: PlaybackTransport) {
        if (currentPlaybackTransport == transport) return
        val prev = currentPlaybackTransport
        currentPlaybackTransport = transport
        logDiagnostic("PLAYBACK", "Playback transport changed: $prev -> $transport")
        notifyStateChanged()
    }

    /**
     * Called by activity onResume. Checks if re-probing is necessary.
     * Prevents UI flickering on screen wake-up on Shanling DAP.
     */
    fun checkOnResume() {
        val now = timeProvider()
        val timeSinceLastSuccess = now - lastSuccessTimestampRealtime
        if (currentUiStatus == UiConnectionStatus.ONLINE && timeSinceLastSuccess < RESUME_FRESHNESS_MS) {
            // Already connected and checked recently: DO NOT probe or show CONNECTING
            logDiagnostic("LIFECYCLE", "onResume: Connection fresh (${timeSinceLastSuccess}ms ago), keeping ONLINE")
            return
        }

        // Stale or non-online: run quiet background probe without flickering UI to CONNECTING
        logDiagnostic("LIFECYCLE", "onResume: Scheduling quiet verification probe")
        probeBackend(quiet = (currentUiStatus == UiConnectionStatus.ONLINE))
    }

    /**
     * Explicit user action to retry connection (e.g. clicking Connection status pill).
     */
    fun retryConnectionExplicitly() {
        logDiagnostic("USER", "Manual reconnect requested by user")
        consecutiveProbeFailures.set(0)
        firstFailureTimestampRealtime = 0L
        updateUiStatus(UiConnectionStatus.CONNECTING)
        probeBackend(quiet = false)
    }

    /**
     * Notified by CarApiClient on any request completion to feed the coordinator.
     */
    fun reportApiCall(path: String, endpoint: String?, success: Boolean, httpCode: Int? = null, latencyMs: Long = 0L, error: Throwable? = null) {
        if (success) {
            onApiSuccess(endpoint, latencyMs)
        } else {
            onApiError(path, endpoint, httpCode, error)
        }
    }

    private fun onApiSuccess(endpoint: String?, latencyMs: Long) {
        lastSuccessTimestampRealtime = timeProvider()
        consecutiveProbeFailures.set(0)
        firstFailureTimestampRealtime = 0L
        retryCount.set(0)
        lastActiveEndpoint = endpoint
        currentBackendHealth = BackendHealth.REACHABLE
        currentAuthStatus = AuthStatus.AUTHENTICATED
        lastProbeLatencyMs = latencyMs
        lastErrorMessage = null

        updateUiStatus(UiConnectionStatus.ONLINE)
    }

    private fun onApiError(path: String, endpoint: String?, httpCode: Int?, error: Throwable?) {
        val now = timeProvider()
        val errMsg = error?.message ?: "HTTP $httpCode"
        lastErrorMessage = errMsg

        logDiagnostic("API_ERROR", "Error on $path (endpoint=$endpoint): $errMsg", httpCode = httpCode)

        // 401 Unauthorized is an auth failure, not an endpoint connectivity flap
        if (httpCode == 401) {
            currentAuthStatus = AuthStatus.INVALID_OR_EXPIRED
            updateUiStatus(UiConnectionStatus.AUTH_ERROR)
            return
        }

        // 404 / 400 are client / resource missing errors (e.g. no lyrics, no cover), NOT server offline!
        if (httpCode in 400..499 && httpCode != 408 && httpCode != 429) {
            logDiagnostic("CLIENT_ERROR", "Path $path returned $httpCode, server is healthy")
            return
        }

        // Network / 5xx / Timeout error:
        if (firstFailureTimestampRealtime == 0L) {
            firstFailureTimestampRealtime = now
        }
        val failures = consecutiveProbeFailures.incrementAndGet()

        // Hysteresis: only mark DEGRADED or OFFLINE if persistent
        val elapsedSinceFirstFail = now - firstFailureTimestampRealtime
        if (!isNetworkPhysicallyAvailable || (failures >= FAILURE_THRESHOLD && elapsedSinceFirstFail >= GRACE_PERIOD_MS)) {
            currentBackendHealth = BackendHealth.UNREACHABLE
            updateUiStatus(UiConnectionStatus.OFFLINE)
            scheduleBackoffRetry()
        } else {
            currentBackendHealth = BackendHealth.DEGRADED
            logDiagnostic("DAMPING", "Transient error damped: failCount=$failures, elapsed=${elapsedSinceFirstFail}ms (keeping $currentUiStatus)")
        }
    }

    /**
     * Executes backend health probe.
     * @param quiet If true, does NOT change current UI status to CONNECTING.
     */
    fun probeBackend(quiet: Boolean = false) {
        if (isProbeInProgress.getAndSet(true)) {
            logDiagnostic("PROBE", "Probe already in progress; skipped")
            return
        }

        if (!quiet && currentUiStatus != UiConnectionStatus.ONLINE) {
            updateUiStatus(UiConnectionStatus.CONNECTING)
        }

        backgroundExecutor.execute {
            val t0 = timeProvider()
            try {
                logDiagnostic("PROBE", "Probing backend /health endpoints...")
                val api = apiClientSupplier()
                val endpoint = api.probe() // Now has 5s timeout
                val latency = timeProvider() - t0
                logDiagnostic("PROBE", "Backend probe success: $endpoint in ${latency}ms", latencyMs = latency)
                onApiSuccess(endpoint, latency)
            } catch (e: Exception) {
                val latency = timeProvider() - t0
                logDiagnostic("PROBE", "Backend probe failed in ${latency}ms: ${e.message}", latencyMs = latency)
                onApiError("/health", null, null, e)
            } finally {
                isProbeInProgress.set(false)
            }
        }
    }

    private fun scheduleBackoffRetry() {
        scheduledProbeTask?.cancel(false)
        val currentRetries = retryCount.getAndIncrement()
        val expDelay = min(MAX_BACKOFF_MS, INITIAL_BACKOFF_MS * (1L shl min(currentRetries, 5)))
        val jitter = Random.nextLong(0, 1000)
        val delay = expDelay + jitter

        logDiagnostic("RECONNECT", "Scheduling backoff retry #$currentRetries in ${delay}ms")
        scheduledProbeTask = backgroundExecutor.schedule({
            if (currentUiStatus != UiConnectionStatus.ONLINE) {
                probeBackend(quiet = true)
            }
        }, delay, TimeUnit.MILLISECONDS)
    }

    private fun updateUiStatus(newStatus: UiConnectionStatus) {
        if (currentUiStatus == newStatus) return
        val prev = currentUiStatus
        currentUiStatus = newStatus
        logDiagnostic("STATE_CHANGE", "UiStatus transitioned: $prev -> $newStatus")
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        val snap = snapshot()
        val notifyAction = Runnable {
            for (listener in listeners) {
                listener(snap.uiStatus, snap)
            }
        }
        if (mainHandler != null) {
            try {
                mainHandler.post(notifyAction)
            } catch (_: Exception) {
                notifyAction.run()
            }
        } else {
            notifyAction.run()
        }
    }

    private fun registerNetworkCallback() {
        val ctx = context ?: return
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = cm.getNetworkCapabilities(network)
                    isNetworkPhysicallyAvailable = true
                    currentTransport = determineTransport(caps)
                    com.bsdung.musicassistantcar.playback.PlaybackDiagnosticTracker.onNetworkTransition(
                        currentTransport.name,
                        true,
                    )
                    logDiagnostic("NETWORK", "Network onAvailable: transport=$currentTransport")

                    // When network returns after being lost, verify backend with a short debounce
                    if (currentUiStatus == UiConnectionStatus.OFFLINE) {
                        if (mainHandler != null) {
                            try {
                                mainHandler.postDelayed({ probeBackend(quiet = false) }, 1000L)
                            } catch (_: Exception) {
                                probeBackend(quiet = false)
                            }
                        } else {
                            probeBackend(quiet = false)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    val activeNet = cm.activeNetwork
                    if (activeNet == null || activeNet == network) {
                        isNetworkPhysicallyAvailable = false
                        currentTransport = NetworkTransport.NONE
                        com.bsdung.musicassistantcar.playback.PlaybackDiagnosticTracker.onNetworkTransition(
                            currentTransport.name,
                            false,
                        )
                        logDiagnostic("NETWORK", "Network onLost: All networks disconnected")
                        currentBackendHealth = BackendHealth.UNREACHABLE
                        updateUiStatus(UiConnectionStatus.OFFLINE)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val transport = determineTransport(networkCapabilities)
                    if (transport != currentTransport) {
                        currentTransport = transport
                        com.bsdung.musicassistantcar.playback.PlaybackDiagnosticTracker.onNetworkTransition(
                            currentTransport.name,
                            isNetworkPhysicallyAvailable,
                        )
                        logDiagnostic("NETWORK", "Transport updated to $currentTransport")
                        notifyStateChanged()
                    }
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            runCatching { Log.w(TAG, "Failed to register NetworkCallback: ${e.message}") }
        }
    }

    private fun determineTransport(caps: NetworkCapabilities?): NetworkTransport {
        if (caps == null) return NetworkTransport.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkTransport.VPN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            else -> NetworkTransport.UNKNOWN
        }
    }

    private fun logDiagnostic(category: String, message: String, latencyMs: Long? = null, httpCode: Int? = null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        val sanitized = com.bsdung.musicassistantcar.playback.PlaybackDiagnosticTracker.sanitizeForDiagnostics(message)
        val event = DiagnosticEvent(
            timestampIso = sdf.format(Date()),
            category = category,
            message = sanitized,
            latencyMs = latencyMs,
            httpCode = httpCode
        )
        diagnosticHistory.add(event)
        while (diagnosticHistory.size > maxHistorySize) {
            diagnosticHistory.poll()
        }
        runCatching { Log.d(TAG, "[$category] $sanitized") }
    }

    /**
     * Generates a comprehensive, sanitized Connectivity & Real Playback Diagnostics report.
     * Contains NO authentication tokens, passwords, or personal credentials.
     * Distinguishes Control Plane (backend /health) from Data Plane (real audio stream stability).
     */
    fun exportDiagnosticsReport(): String {
        val snap = snapshot()
        val sb = StringBuilder()
        sb.appendLine("================================================================================")
        sb.appendLine("MELOVAULT CONNECTIVITY DIAGNOSTICS REPORT")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        sb.appendLine("================================================================================")
        sb.appendLine("1. CURRENT STATE SUMMARY")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("UI Status:              ${snap.uiStatus.name} (${snap.uiStatus.label})")
        sb.appendLine("Control Plane Note:     ONLINE only indicates backend reachability; see Section 2 for real playback stability.")
        sb.appendLine("Physical Network:       ${if (snap.isNetworkConnected) "CONNECTED" else "DISCONNECTED"}")
        sb.appendLine("Active Transport:       ${snap.networkTransport.name}")
        sb.appendLine("Backend Health:         ${snap.backendHealth.name}")
        sb.appendLine("Authentication:         ${snap.authStatus.name}")
        sb.appendLine("Playback Transport:     ${snap.playbackTransport.name}")
        sb.appendLine("Active Endpoint:        ${snap.activeEndpoint ?: "None"}")
        sb.appendLine("Consecutive Failures:   ${snap.consecutiveFailures}")
        sb.appendLine("Last Probe Latency:     ${if (snap.lastProbeLatencyMs >= 0) "${snap.lastProbeLatencyMs} ms" else "N/A"}")
        sb.appendLine("Last Error:             ${com.bsdung.musicassistantcar.playback.PlaybackDiagnosticTracker.sanitizeForDiagnostics(snap.lastErrorMessage ?: "None")}")
        sb.appendLine()
        sb.append(com.bsdung.musicassistantcar.playback.PlaybackDiagnosticTracker.formatDiagnosticsSection())
        sb.appendLine()
        sb.appendLine("3. SHANLING M3 ULTRA / TAILSCALE AUDIT METRICS")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("Probe Timeout Config:   ${PROBE_TIMEOUT_MS} ms")
        sb.appendLine("Grace Period:           ${GRACE_PERIOD_MS} ms")
        sb.appendLine("Failure Threshold:      ${FAILURE_THRESHOLD} consecutive fails")
        sb.appendLine("Resume Freshness:       ${RESUME_FRESHNESS_MS} ms")
        sb.appendLine("Damping / Hysteresis:   ACTIVE")
        sb.appendLine()
        sb.appendLine("4. RECENT CONNECTIVITY EVENTS (Last ${diagnosticHistory.size})")
        sb.appendLine("--------------------------------------------------------------------------------")
        for (evt in diagnosticHistory) {
            val latencyStr = evt.latencyMs?.let { " (${it}ms)" } ?: ""
            val codeStr = evt.httpCode?.let { " [HTTP $it]" } ?: ""
            sb.appendLine("[${evt.timestampIso}] [${evt.category}]${codeStr} ${evt.message}${latencyStr}")
        }
        sb.appendLine("================================================================================")
        return sb.toString()
    }

    fun release() {
        try {
            val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        scheduledProbeTask?.cancel(true)
        listeners.clear()
    }
}
