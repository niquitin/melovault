package com.bsdung.musicassistantcar.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * E6 R7: Real Audio Playback & Buffering Diagnostic Tracker.
 *
 * Distinguishes normal ExoPlayer state transitions (initial track preparation, intra-track seeks,
 * paused buffering) from genuine mid-track audible rebuffer stalls.
 *
 * Strictly token-free: all URIs and error messages are sanitized before logging or exporting.
 * Never infers playback stability from backend /health ONLINE status alone.
 */
object PlaybackDiagnosticTracker {
    private const val TAG = "PlaybackDiagnostics"
    private const val MAX_EVENTS = 100
    private const val SEEK_GRACE_WINDOW_MS = 1_500L

    enum class BufferReason {
        NONE,
        INITIAL_PREPARE,
        SEEK_BUFFER,
        PAUSED_BUFFER,
        MID_TRACK_REBUFFER_STALL,
    }

    enum class PlaybackStabilityVerdict(val description: String) {
        NOT_EVALUATED("NOT_EVALUATED (No active playback session evaluated yet)"),
        STABLE_AUDIBLE_STREAM("STABLE (Zero mid-track rebuffer stalls, zero HTTP stream errors)"),
        DEGRADED_MINOR_REBUFFER("DEGRADED_MINOR (1 brief mid-track rebuffer < 2s)"),
        UNSTABLE_AUDIBLE_STALLS("UNSTABLE_REBUFFERING (Audible mid-track stalls detected; backend ONLINE alone is NOT sufficient)"),
        PLAYBACK_ERROR("PLAYBACK_ERROR (HTTP/Stream error encountered)"),
    }

    data class PlaybackEvent(
        val timestampIso: String,
        val type: String,
        val mediaId: String,
        val playbackState: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val bufferedPositionMs: Long,
        val bufferedHeadroomMs: Long,
        val stallDurationMs: Long? = null,
        val detail: String,
    )

    data class Snapshot(
        val mediaId: String?,
        val sanitizedUri: String?,
        val playbackStateName: String,
        val playWhenReady: Boolean,
        val isPlaying: Boolean,
        val positionMs: Long,
        val bufferedPositionMs: Long,
        val bufferedHeadroomMs: Long,
        val activeBufferReason: BufferReason,
        val activeStallDurationMs: Long,
        val initialPrepareCount: Int,
        val lastInitialPrepareMs: Long,
        val seekBufferCount: Int,
        val lastSeekBufferMs: Long,
        val realRebufferCount: Int,
        val totalRealRebufferMs: Long,
        val maxRealRebufferMs: Long,
        val lastRealRebufferMs: Long,
        val httpErrorCount: Int,
        val lastHttpStatusCode: Int?,
        val lastSanitizedError: String?,
        val networkTransitionCount: Int,
        val cachedBytesRead: Long,
        val upstreamBytesRead: Long,
        val cacheHitRatioPercent: Double,
        val activeCacheTarget: String,
        val activeCacheCapBytes: Long,
        val currentCacheSpaceBytes: Long,
        val lastCacheFallbackReason: String,
        val stabilityVerdict: PlaybackStabilityVerdict,
    )

    @Volatile var timeProvider: () -> Long = { System.currentTimeMillis() }

    @Volatile private var currentMediaId: String? = null
    @Volatile private var currentSanitizedUri: String? = null
    @Volatile private var currentPlaybackState: Int = Player.STATE_IDLE
    @Volatile private var currentPlayWhenReady: Boolean = false
    @Volatile private var currentIsPlaying: Boolean = false
    @Volatile private var currentPositionMs: Long = 0L
    @Volatile private var currentBufferedPositionMs: Long = 0L

    @Volatile private var hasReachedReadyForCurrentTrack: Boolean = false
    @Volatile private var wasPlayingInCurrentTrack: Boolean = false
    @Volatile private var lastSeekTimestampMs: Long = -10_000L
    @Volatile private var activeBufferReason: BufferReason = BufferReason.NONE
    @Volatile private var bufferEnteredAtMs: Long = 0L
    @Volatile private var hasEvaluatedPlayback: Boolean = false

    private val initialPrepareCount = AtomicInteger(0)
    private val lastInitialPrepareMs = AtomicLong(0L)
    private val seekBufferCount = AtomicInteger(0)
    private val lastSeekBufferMs = AtomicLong(0L)
    private val realRebufferCount = AtomicInteger(0)
    private val totalRealRebufferMs = AtomicLong(0L)
    private val maxRealRebufferMs = AtomicLong(0L)
    private val lastRealRebufferMs = AtomicLong(0L)
    private val httpErrorCount = AtomicInteger(0)
    @Volatile private var lastHttpStatusCode: Int? = null
    @Volatile private var lastSanitizedError: String? = null
    private val networkTransitionCount = AtomicInteger(0)

    private val events = ConcurrentLinkedQueue<PlaybackEvent>()

    private val sensitiveParamRegex = Regex(
        "(?i)(token|access_token|refresh_token|auth|authorization|jwt|secret|password|passwd|api_key|apikey|key|sig|signature|cookie)=([^&\\s#]+)"
    )
    private val bearerRegex = Regex("(?i)Bearer\\s+[A-Za-z0-9._\\-~+/]+=*")

    /**
     * Strips any authentication tokens, secrets, or sensitive query parameters from a string or URI.
     */
    fun sanitizeForDiagnostics(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(sensitiveParamRegex) { match -> "${match.groupValues[1]}=[REDACTED]" }
            .replace(bearerRegex, "Bearer [REDACTED]")
    }

    fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

    @Synchronized
    fun onTrackTransition(
        mediaId: String?,
        uriString: String?,
        reasonName: String,
        positionMs: Long,
        bufferedPositionMs: Long,
    ) {
        val now = timeProvider()
        closeActiveBufferIfAny(now, transitionedTrack = true)
        currentMediaId = mediaId
        currentSanitizedUri = sanitizeForDiagnostics(uriString)
        currentPositionMs = positionMs.coerceAtLeast(0L)
        currentBufferedPositionMs = bufferedPositionMs.coerceAtLeast(currentPositionMs)
        hasReachedReadyForCurrentTrack = false
        wasPlayingInCurrentTrack = false
        hasEvaluatedPlayback = true

        recordEvent(
            type = "TRACK_TRANSITION",
            stallDurationMs = null,
            detail = "reason=$reasonName uri=${currentSanitizedUri ?: "none"}",
        )
    }

    @Synchronized
    fun onSeekDiscontinuity(
        mediaId: String?,
        oldPositionMs: Long,
        newPositionMs: Long,
        bufferedPositionMs: Long,
    ) {
        val now = timeProvider()
        lastSeekTimestampMs = now
        if (mediaId != null) currentMediaId = mediaId
        currentPositionMs = newPositionMs.coerceAtLeast(0L)
        currentBufferedPositionMs = bufferedPositionMs.coerceAtLeast(currentPositionMs)
        if (activeBufferReason == BufferReason.MID_TRACK_REBUFFER_STALL) {
            activeBufferReason = BufferReason.SEEK_BUFFER
        }
        recordEvent(
            type = "SEEK",
            stallDurationMs = null,
            detail = "from=${oldPositionMs}ms to=${newPositionMs}ms",
        )
    }

    @Synchronized
    fun onPlayerStateUpdate(
        mediaId: String?,
        uriString: String?,
        playbackState: Int,
        playWhenReady: Boolean,
        isPlaying: Boolean,
        positionMs: Long,
        bufferedPositionMs: Long,
    ) {
        val now = timeProvider()
        if (mediaId != null && mediaId != currentMediaId) {
            currentMediaId = mediaId
            currentSanitizedUri = sanitizeForDiagnostics(uriString)
            hasReachedReadyForCurrentTrack = false
            wasPlayingInCurrentTrack = false
        } else if (uriString != null && currentSanitizedUri == null) {
            currentSanitizedUri = sanitizeForDiagnostics(uriString)
        }

        val prevState = currentPlaybackState
        val prevPlaying = currentIsPlaying
        val prevPlayWhenReady = currentPlayWhenReady

        currentPlaybackState = playbackState
        currentPlayWhenReady = playWhenReady
        currentIsPlaying = isPlaying
        currentPositionMs = positionMs.coerceAtLeast(0L)
        currentBufferedPositionMs = if (bufferedPositionMs == C.TIME_UNSET || bufferedPositionMs < 0L) {
            currentPositionMs
        } else {
            bufferedPositionMs.coerceAtLeast(currentPositionMs)
        }

        if (playbackState == Player.STATE_BUFFERING) {
            hasEvaluatedPlayback = true
            if (prevState != Player.STATE_BUFFERING || activeBufferReason == BufferReason.NONE) {
                bufferEnteredAtMs = now
                val sinceSeekMs = now - lastSeekTimestampMs
                activeBufferReason = when {
                    !playWhenReady -> BufferReason.PAUSED_BUFFER
                    !hasReachedReadyForCurrentTrack -> BufferReason.INITIAL_PREPARE
                    sinceSeekMs in 0..SEEK_GRACE_WINDOW_MS -> BufferReason.SEEK_BUFFER
                    wasPlayingInCurrentTrack || prevPlaying -> BufferReason.MID_TRACK_REBUFFER_STALL
                    else -> BufferReason.INITIAL_PREPARE
                }
                val headroom = (currentBufferedPositionMs - currentPositionMs).coerceAtLeast(0L)
                recordEvent(
                    type = "BUFFER_START",
                    stallDurationMs = null,
                    detail = "reason=$activeBufferReason playWhenReady=$playWhenReady headroom=${headroom}ms",
                )
            }
        } else if (playbackState == Player.STATE_READY) {
            hasEvaluatedPlayback = true
            val completedStallMs = closeActiveBufferIfAny(now, transitionedTrack = false)
            hasReachedReadyForCurrentTrack = true
            if (isPlaying) {
                wasPlayingInCurrentTrack = true
            }
            if (prevState != Player.STATE_READY || prevPlaying != isPlaying || prevPlayWhenReady != playWhenReady) {
                val headroom = (currentBufferedPositionMs - currentPositionMs).coerceAtLeast(0L)
                recordEvent(
                    type = "STATE_READY",
                    stallDurationMs = completedStallMs,
                    detail = "isPlaying=$isPlaying playWhenReady=$playWhenReady headroom=${headroom}ms",
                )
            }
        } else if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
            closeActiveBufferIfAny(now, transitionedTrack = false)
            if (prevState != playbackState) {
                recordEvent(
                    type = "STATE_${stateName(playbackState)}",
                    stallDurationMs = null,
                    detail = "playWhenReady=$playWhenReady",
                )
            }
        }
    }

    @Synchronized
    fun onPlaybackError(
        mediaId: String?,
        error: Throwable,
        positionMs: Long = currentPositionMs,
        bufferedPositionMs: Long = currentBufferedPositionMs,
    ) {
        hasEvaluatedPlayback = true
        if (mediaId != null) currentMediaId = mediaId
        currentPositionMs = positionMs.coerceAtLeast(0L)
        currentBufferedPositionMs = bufferedPositionMs.coerceAtLeast(currentPositionMs)

        val httpCode = extractHttpStatusCode(error)
        if (httpCode != null) {
            lastHttpStatusCode = httpCode
        }
        httpErrorCount.incrementAndGet()
        val sanitizedMsg = sanitizeForDiagnostics("${error.javaClass.simpleName}: ${error.message ?: "unknown"}")
        lastSanitizedError = sanitizedMsg

        recordEvent(
            type = "HTTP_OR_STREAM_ERROR",
            stallDurationMs = null,
            detail = "httpCode=${httpCode ?: "N/A"} error=$sanitizedMsg",
        )
    }

    fun onNetworkTransition(transportName: String, connected: Boolean) {
        networkTransitionCount.incrementAndGet()
        recordEvent(
            type = "NETWORK_TRANSITION",
            stallDurationMs = null,
            detail = "transport=$transportName connected=$connected",
        )
    }

    private fun extractHttpStatusCode(error: Throwable?): Int? {
        var cur = error
        while (cur != null) {
            if (cur is HttpDataSource.InvalidResponseCodeException) {
                return cur.responseCode
            }
            if (cur is PlaybackException) {
                val msg = cur.message.orEmpty()
                val match = Regex("Response code:\\s*(\\d{3})").find(msg)
                if (match != null) {
                    return match.groupValues[1].toIntOrNull()
                }
            }
            cur = cur.cause
        }
        return null
    }

    private fun closeActiveBufferIfAny(nowMs: Long, transitionedTrack: Boolean): Long? {
        val reason = activeBufferReason
        if (reason == BufferReason.NONE) return null
        val durationMs = (nowMs - bufferEnteredAtMs).coerceAtLeast(0L)
        activeBufferReason = BufferReason.NONE
        bufferEnteredAtMs = 0L

        when (reason) {
            BufferReason.INITIAL_PREPARE -> {
                initialPrepareCount.incrementAndGet()
                lastInitialPrepareMs.set(durationMs)
                recordEvent(
                    type = "INITIAL_PREPARE_DONE",
                    stallDurationMs = durationMs,
                    detail = "startupLatency=${durationMs}ms",
                )
            }
            BufferReason.SEEK_BUFFER -> {
                seekBufferCount.incrementAndGet()
                lastSeekBufferMs.set(durationMs)
                recordEvent(
                    type = "SEEK_BUFFER_DONE",
                    stallDurationMs = durationMs,
                    detail = "seekBufferLatency=${durationMs}ms",
                )
            }
            BufferReason.PAUSED_BUFFER -> {
                recordEvent(
                    type = "PAUSED_BUFFER_DONE",
                    stallDurationMs = durationMs,
                    detail = "pausedBuffer=${durationMs}ms",
                )
            }
            BufferReason.MID_TRACK_REBUFFER_STALL -> {
                if (!transitionedTrack || durationMs >= 250L) {
                    realRebufferCount.incrementAndGet()
                    totalRealRebufferMs.addAndGet(durationMs)
                    lastRealRebufferMs.set(durationMs)
                    var prevMax = maxRealRebufferMs.get()
                    while (durationMs > prevMax && !maxRealRebufferMs.compareAndSet(prevMax, durationMs)) {
                        prevMax = maxRealRebufferMs.get()
                    }
                    recordEvent(
                        type = "REAL_REBUFFER_STALL_RESOLVED",
                        stallDurationMs = durationMs,
                        detail = "audibleStall=${durationMs}ms totalStalls=${realRebufferCount.get()} totalStallMs=${totalRealRebufferMs.get()}",
                    )
                }
            }
            BufferReason.NONE -> {}
        }
        return durationMs
    }

    private fun recordEvent(type: String, stallDurationMs: Long?, detail: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
        val sanitizedDetail = sanitizeForDiagnostics(detail)
        val headroom = (currentBufferedPositionMs - currentPositionMs).coerceAtLeast(0L)
        val evt = PlaybackEvent(
            timestampIso = sdf.format(Date(timeProvider())),
            type = type,
            mediaId = currentMediaId ?: "none",
            playbackState = stateName(currentPlaybackState),
            isPlaying = currentIsPlaying,
            positionMs = currentPositionMs,
            bufferedPositionMs = currentBufferedPositionMs,
            bufferedHeadroomMs = headroom,
            stallDurationMs = stallDurationMs,
            detail = sanitizedDetail,
        )
        events.add(evt)
        while (events.size > MAX_EVENTS) {
            events.poll()
        }
        runCatching {
            val stallPart = stallDurationMs?.let { " stall=${it}ms" } ?: ""
            Log.i(
                TAG,
                "[$type] mediaId=${evt.mediaId} state=${evt.playbackState} isPlaying=${evt.isPlaying} " +
                    "pos=${evt.positionMs}ms buf=${evt.bufferedPositionMs}ms headroom=${evt.bufferedHeadroomMs}ms" +
                    "$stallPart $sanitizedDetail"
            )
        }
    }

    fun snapshot(): Snapshot {
        val now = timeProvider()
        val activeStall = if (activeBufferReason != BufferReason.NONE && bufferEnteredAtMs > 0L) {
            (now - bufferEnteredAtMs).coerceAtLeast(0L)
        } else 0L
        val rebuffers = realRebufferCount.get()
        val totalStall = totalRealRebufferMs.get() +
            (if (activeBufferReason == BufferReason.MID_TRACK_REBUFFER_STALL) activeStall else 0L)
        val maxStall = maxOf(
            maxRealRebufferMs.get(),
            if (activeBufferReason == BufferReason.MID_TRACK_REBUFFER_STALL) activeStall else 0L
        )
        val errors = httpErrorCount.get()

        val verdict = when {
            !hasEvaluatedPlayback -> PlaybackStabilityVerdict.NOT_EVALUATED
            errors > 0 && currentPlaybackState == Player.STATE_IDLE -> PlaybackStabilityVerdict.PLAYBACK_ERROR
            rebuffers >= 2 || totalStall >= 2_000L ||
                (activeBufferReason == BufferReason.MID_TRACK_REBUFFER_STALL && activeStall >= 2_000L) ->
                PlaybackStabilityVerdict.UNSTABLE_AUDIBLE_STALLS
            rebuffers == 1 || errors > 0 -> PlaybackStabilityVerdict.DEGRADED_MINOR_REBUFFER
            else -> PlaybackStabilityVerdict.STABLE_AUDIBLE_STREAM
        }

        val cachedBytes = StreamCacheManager.totalCachedBytesRead()
        val upstreamBytes = StreamCacheManager.totalUpstreamBytesRead()
        val totalBytes = cachedBytes + upstreamBytes
        val hitRatio = if (totalBytes > 0L) (cachedBytes.toDouble() * 100.0) / totalBytes.toDouble() else 0.0

        return Snapshot(
            mediaId = currentMediaId,
            sanitizedUri = currentSanitizedUri,
            playbackStateName = stateName(currentPlaybackState),
            playWhenReady = currentPlayWhenReady,
            isPlaying = currentIsPlaying,
            positionMs = currentPositionMs,
            bufferedPositionMs = currentBufferedPositionMs,
            bufferedHeadroomMs = (currentBufferedPositionMs - currentPositionMs).coerceAtLeast(0L),
            activeBufferReason = activeBufferReason,
            activeStallDurationMs = activeStall,
            initialPrepareCount = initialPrepareCount.get(),
            lastInitialPrepareMs = lastInitialPrepareMs.get(),
            seekBufferCount = seekBufferCount.get(),
            lastSeekBufferMs = lastSeekBufferMs.get(),
            realRebufferCount = rebuffers,
            totalRealRebufferMs = totalStall,
            maxRealRebufferMs = maxStall,
            lastRealRebufferMs = lastRealRebufferMs.get(),
            httpErrorCount = errors,
            lastHttpStatusCode = lastHttpStatusCode,
            lastSanitizedError = lastSanitizedError,
            networkTransitionCount = networkTransitionCount.get(),
            cachedBytesRead = cachedBytes,
            upstreamBytesRead = upstreamBytes,
            cacheHitRatioPercent = hitRatio,
            activeCacheTarget = StreamCacheManager.activeTargetName(),
            activeCacheCapBytes = StreamCacheManager.activeCapBytes(),
            currentCacheSpaceBytes = StreamCacheManager.activeCacheUsedBytes(),
            lastCacheFallbackReason = StreamCacheManager.lastFallbackReason().name,
            stabilityVerdict = verdict,
        )
    }

    fun recentEvents(): List<PlaybackEvent> = events.toList()

    fun formatDiagnosticsSection(): String {
        val s = snapshot()
        val sb = StringBuilder()
        sb.appendLine("2. DATA PLANE & REAL PLAYBACK STABILITY DIAGNOSTICS (TOKEN-FREE)")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("Playback Verdict:       ${s.stabilityVerdict.description}")
        sb.appendLine("Active Media ID:        ${s.mediaId ?: "None"}")
        sb.appendLine("Sanitized Stream URI:   ${s.sanitizedUri ?: "None"}")
        sb.appendLine("Player State:           ${s.playbackStateName} (playWhenReady=${s.playWhenReady}, isPlaying=${s.isPlaying})")
        sb.appendLine("Playback Position:      ${s.positionMs} ms")
        sb.appendLine("Buffered Position:      ${s.bufferedPositionMs} ms (Headroom: ${s.bufferedHeadroomMs} ms)")
        sb.appendLine("Active Buffer Reason:   ${s.activeBufferReason.name} (activeStall=${s.activeStallDurationMs} ms)")
        sb.appendLine("Normal Startup Buffers: ${s.initialPrepareCount} (last=${s.lastInitialPrepareMs} ms)")
        sb.appendLine("Normal Seek Buffers:    ${s.seekBufferCount} (last=${s.lastSeekBufferMs} ms)")
        sb.appendLine("Real Rebuffer Stalls:   ${s.realRebufferCount} (total=${s.totalRealRebufferMs} ms, max=${s.maxRealRebufferMs} ms, last=${s.lastRealRebufferMs} ms)")
        sb.appendLine("HTTP / Stream Errors:   ${s.httpErrorCount} (lastCode=${s.lastHttpStatusCode ?: "None"}, lastErr=${s.lastSanitizedError ?: "None"})")
        sb.appendLine("Network Transitions:    ${s.networkTransitionCount}")
        sb.appendLine("Stream Cache Target:    ${s.activeCacheTarget} (fallback=${s.lastCacheFallbackReason})")
        sb.appendLine("Stream Cache Cap/Used:  ${s.activeCacheCapBytes / (1024 * 1024)} MiB cap / ${s.currentCacheSpaceBytes / (1024 * 1024)} MiB used")
        sb.appendLine(
            String.format(
                Locale.US,
                "Cache-Hit Telemetry:    %.1f%% hit (%d B cached read vs %d B upstream network read)",
                s.cacheHitRatioPercent,
                s.cachedBytesRead,
                s.upstreamBytesRead,
            )
        )
        if (events.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Recent Playback & Buffer Events (Last ${events.size}):")
            for (e in events) {
                val stallStr = e.stallDurationMs?.let { " [stall=${it}ms]" } ?: ""
                sb.appendLine(
                    "[${e.timestampIso}] [${e.type}] media=${e.mediaId} state=${e.playbackState} " +
                        "playing=${e.isPlaying} pos=${e.positionMs}ms buf=${e.bufferedPositionMs}ms " +
                        "headroom=${e.bufferedHeadroomMs}ms$stallStr ${e.detail}"
                )
            }
        }
        return sb.toString()
    }

    @Synchronized
    fun resetForTesting() {
        timeProvider = { System.currentTimeMillis() }
        currentMediaId = null
        currentSanitizedUri = null
        currentPlaybackState = Player.STATE_IDLE
        currentPlayWhenReady = false
        currentIsPlaying = false
        currentPositionMs = 0L
        currentBufferedPositionMs = 0L
        hasReachedReadyForCurrentTrack = false
        wasPlayingInCurrentTrack = false
        lastSeekTimestampMs = -10_000L
        activeBufferReason = BufferReason.NONE
        bufferEnteredAtMs = 0L
        hasEvaluatedPlayback = false
        initialPrepareCount.set(0)
        lastInitialPrepareMs.set(0L)
        seekBufferCount.set(0)
        lastSeekBufferMs.set(0L)
        realRebufferCount.set(0)
        totalRealRebufferMs.set(0L)
        maxRealRebufferMs.set(0L)
        lastRealRebufferMs.set(0L)
        httpErrorCount.set(0)
        lastHttpStatusCode = null
        lastSanitizedError = null
        networkTransitionCount.set(0)
        events.clear()
    }
}
