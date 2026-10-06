package com.bsdung.musicassistantcar.lyrics

import com.bsdung.musicassistantcar.data.CarApiClient
import java.util.concurrent.atomic.AtomicLong

data class LyricsRequestToken(
    val generation: Long,
    val signature: String,
    val trackKey: String,
)

class LyricsRepository(
    private val cache: LyricsCache,
    private val lookupFetcher: (
        trackKey: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long?,
        forceRefresh: Boolean,
        preferredProvider: String?,
        preferredRecordId: String?,
    ) -> LyricsLookupResult,
) {
    constructor(
        cache: LyricsCache,
        apiClient: CarApiClient,
    ) : this(
        cache = cache,
        lookupFetcher = { trackKey, title, artist, album, durationMs, forceRefresh, preferredProvider, preferredRecordId ->
            apiClient.lookupLyrics(
                trackKey = trackKey,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                forceRefresh = forceRefresh,
                preferredProvider = preferredProvider,
                preferredRecordId = preferredRecordId,
            )
        },
    )

    private val requestGeneration = AtomicLong(0L)

    @Volatile
    private var activeSignature: String? = null

    fun signatureFor(metadata: LyricsTrackMetadata): String =
        LyricsCache.computeMetadataSignature(
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            durationMs = metadata.durationMs,
        )

    @Synchronized
    fun beginTrackRequest(metadata: LyricsTrackMetadata): LyricsRequestToken {
        val sig = signatureFor(metadata)
        val gen = requestGeneration.incrementAndGet()
        activeSignature = sig
        return LyricsRequestToken(
            generation = gen,
            signature = sig,
            trackKey = metadata.trackKey,
        )
    }

    @Synchronized
    fun cancelActiveRequests() {
        requestGeneration.incrementAndGet()
        activeSignature = null
    }

    @Synchronized
    fun isTokenCurrent(token: LyricsRequestToken): Boolean {
        return token.generation == requestGeneration.get() && token.signature == activeSignature
    }

    fun resolveLyrics(
        metadata: LyricsTrackMetadata,
        forceRefresh: Boolean = false,
        preferredProvider: String? = null,
        preferredRecordId: String? = null,
    ): LyricsLookupResult {
        val signature = signatureFor(metadata)

        if (!metadata.hasLookupMetadata() && preferredRecordId.isNullOrBlank()) {
            return LyricsLookupResult(
                status = LyricsStatus.NOT_FOUND,
                diagnostic = "missing_metadata",
            )
        }

        if (forceRefresh) {
            cache.invalidate(signature)
        }

        var stalePositiveBackup: LyricsLookupResult? = null
        var effectiveProvider = preferredProvider
        var effectiveRecordId = preferredRecordId

        if (!forceRefresh && preferredRecordId.isNullOrBlank()) {
            val cached = cache.read(signature)
            if (cached != null) {
                if (!cached.isSoftExpired) {
                    return cached.result
                }
                stalePositiveBackup = cached.result
            }
            val manualOverride = cache.getManualOverride(signature)
            if (manualOverride != null) {
                effectiveProvider = manualOverride.provider
                effectiveRecordId = manualOverride.recordId
            }
        }

        val remoteResult = try {
            lookupFetcher(
                metadata.trackKey,
                metadata.title,
                metadata.artist,
                metadata.album,
                metadata.durationMs,
                forceRefresh,
                effectiveProvider,
                effectiveRecordId,
            )
        } catch (exc: Exception) {
            LyricsLookupResult(
                status = LyricsStatus.TEMPORARY_ERROR,
                diagnostic = exc.message ?: exc.javaClass.simpleName,
            )
        }

        if (remoteResult.status == LyricsStatus.TEMPORARY_ERROR && stalePositiveBackup != null) {
            return stalePositiveBackup
        }

        if (!preferredRecordId.isNullOrBlank() && remoteResult.status in POSITIVE_STATUSES) {
            cache.saveManualOverride(
                signature = signature,
                provider = remoteResult.provider,
                recordId = preferredRecordId,
            )
            cache.write(signature, remoteResult)
        } else if (remoteResult.status in CACHEABLE_STATUSES) {
            cache.write(signature, remoteResult)
        }

        return remoteResult
    }

    companion object {
        private val POSITIVE_STATUSES = setOf(
            LyricsStatus.SYNCED,
            LyricsStatus.PLAIN,
            LyricsStatus.INSTRUMENTAL,
        )

        private val CACHEABLE_STATUSES = setOf(
            LyricsStatus.SYNCED,
            LyricsStatus.PLAIN,
            LyricsStatus.INSTRUMENTAL,
            LyricsStatus.NOT_FOUND,
        )

        fun toUiState(
            result: LyricsLookupResult,
            currentPositionMs: Long = 0L,
            autoFollow: Boolean = true,
        ): LyricsUiState {
            return when (result.status) {
                LyricsStatus.SYNCED -> {
                    val lines = if (result.syncedLines.isNotEmpty()) {
                        result.syncedLines
                    } else {
                        LrcParser.parse(result.syncedLyrics)
                    }
                    if (lines.isEmpty()) {
                        if (!result.plainLyrics.isNullOrBlank()) {
                            LyricsUiState.Plain(
                                text = result.plainLyrics,
                                provider = result.provider,
                                providerRecordId = result.providerRecordId,
                                fromCache = result.fromCache,
                            )
                        } else {
                            LyricsUiState.NotFound(provider = result.provider)
                        }
                    } else {
                        val activeIdx = LrcParser.findActiveLineIndex(lines, currentPositionMs)
                        LyricsUiState.Synced(
                            lines = lines,
                            activeLineIndex = activeIdx,
                            autoFollow = autoFollow,
                            provider = result.provider,
                            providerRecordId = result.providerRecordId,
                            fromCache = result.fromCache,
                        )
                    }
                }
                LyricsStatus.PLAIN -> {
                    val plain = result.plainLyrics?.trim().orEmpty()
                    if (plain.isEmpty()) {
                        LyricsUiState.NotFound(provider = result.provider)
                    } else {
                        LyricsUiState.Plain(
                            text = plain,
                            provider = result.provider,
                            providerRecordId = result.providerRecordId,
                            fromCache = result.fromCache,
                        )
                    }
                }
                LyricsStatus.INSTRUMENTAL -> LyricsUiState.Instrumental(
                    provider = result.provider,
                    fromCache = result.fromCache,
                )
                LyricsStatus.AMBIGUOUS -> {
                    if (result.candidates.isEmpty()) {
                        LyricsUiState.NotFound(provider = result.provider)
                    } else {
                        LyricsUiState.Ambiguous(
                            candidates = result.candidates.take(5),
                            provider = result.provider,
                        )
                    }
                }
                LyricsStatus.NOT_FOUND -> LyricsUiState.NotFound(
                    provider = result.provider,
                )
                LyricsStatus.TEMPORARY_ERROR -> LyricsUiState.TemporaryError(
                    message = "Không thể tải lời bài hát",
                    diagnostic = result.diagnostic,
                )
            }
        }
    }
}

class LyricsSessionController(
    private val repository: LyricsRepository,
    private val onSeekRequested: (Long) -> Unit = {},
) {
    var uiState: LyricsUiState = LyricsUiState.Idle
        private set

    var autoFollow: Boolean = true
        private set

    var activeLineIndex: Int = -1
        private set

    var scrollPositionIndex: Int = 0
        private set

    var isFullscreenVisible: Boolean = false
        private set

    var isActivityInForeground: Boolean = true
        private set

    var currentSignature: String? = null
        private set

    val isKeepScreenOnEnabled: Boolean
        get() = isFullscreenVisible && isActivityInForeground

    fun startLookup(metadata: LyricsTrackMetadata): LyricsRequestToken {
        autoFollow = true
        activeLineIndex = -1
        scrollPositionIndex = 0
        val token = repository.beginTrackRequest(metadata)
        currentSignature = token.signature
        uiState = LyricsUiState.Loading(
            title = metadata.title,
            artist = metadata.artist,
        )
        return token
    }

    fun onTrackChanged(metadata: LyricsTrackMetadata?): LyricsRequestToken? {
        autoFollow = true
        activeLineIndex = -1
        scrollPositionIndex = 0
        if (metadata == null) {
            repository.cancelActiveRequests()
            currentSignature = null
            uiState = LyricsUiState.Idle
            return null
        }
        return startLookup(metadata)
    }

    fun enterFullscreen(
        metadata: LyricsTrackMetadata? = null,
        currentPositionMs: Long = 0L,
    ): LyricsUiState {
        isFullscreenVisible = true
        if (metadata == null) {
            return uiState
        }
        val sig = repository.signatureFor(metadata)
        if (currentSignature == sig && uiState !is LyricsUiState.Idle) {
            if (uiState is LyricsUiState.Synced) {
                onPositionUpdated(currentPositionMs)
            }
            return uiState
        }
        return loadSynchronously(
            metadata = metadata,
            forceRefresh = false,
            currentPositionMs = currentPositionMs,
        )
    }

    fun exitFullscreen() {
        isFullscreenVisible = false
    }

    fun onActivityForegroundChanged(inForeground: Boolean) {
        isActivityInForeground = inForeground
    }

    fun applyResultIfCurrent(
        token: LyricsRequestToken,
        result: LyricsLookupResult,
        currentPositionMs: Long = 0L,
    ): Boolean {
        if (!repository.isTokenCurrent(token)) {
            return false
        }
        currentSignature = token.signature
        val nextState = LyricsRepository.toUiState(
            result = result,
            currentPositionMs = currentPositionMs,
            autoFollow = autoFollow,
        )
        uiState = nextState
        if (nextState is LyricsUiState.Synced) {
            activeLineIndex = nextState.activeLineIndex
            if (autoFollow && activeLineIndex >= 0) {
                scrollPositionIndex = activeLineIndex
            }
        } else {
            activeLineIndex = -1
        }
        return true
    }

    fun loadSynchronously(
        metadata: LyricsTrackMetadata,
        forceRefresh: Boolean = false,
        preferredProvider: String? = null,
        preferredRecordId: String? = null,
        currentPositionMs: Long = 0L,
    ): LyricsUiState {
        val token = startLookup(metadata)
        val result = repository.resolveLyrics(
            metadata = metadata,
            forceRefresh = forceRefresh,
            preferredProvider = preferredProvider,
            preferredRecordId = preferredRecordId,
        )
        applyResultIfCurrent(token, result, currentPositionMs)
        return uiState
    }

    fun retryCurrentTrack(
        metadata: LyricsTrackMetadata,
        currentPositionMs: Long = 0L,
    ): LyricsUiState {
        val forceRefresh = uiState !is LyricsUiState.TemporaryError
        return loadSynchronously(
            metadata = metadata,
            forceRefresh = forceRefresh,
            currentPositionMs = currentPositionMs,
        )
    }

    fun onUserManualScroll() {
        autoFollow = false
        val current = uiState
        if (current is LyricsUiState.Synced) {
            uiState = current.copy(autoFollow = false)
        }
    }

    fun onResumeAutoFollow(currentPositionMs: Long) {
        autoFollow = true
        val current = uiState
        if (current is LyricsUiState.Synced) {
            val idx = LrcParser.findActiveLineIndex(current.lines, currentPositionMs)
            activeLineIndex = idx
            if (idx >= 0) {
                scrollPositionIndex = idx
            }
            uiState = current.copy(activeLineIndex = idx, autoFollow = true)
        }
    }

    fun onPositionUpdated(positionMs: Long): Boolean {
        val current = uiState as? LyricsUiState.Synced ?: return false
        val idx = LrcParser.findActiveLineIndex(current.lines, positionMs)
        val changed = idx != activeLineIndex
        activeLineIndex = idx
        if (autoFollow && idx >= 0) {
            scrollPositionIndex = idx
        }
        uiState = current.copy(activeLineIndex = idx, autoFollow = autoFollow)
        return changed
    }

    fun onLineTapped(line: LyricsLine): Boolean {
        if (uiState !is LyricsUiState.Synced) {
            return false
        }
        onSeekRequested(line.startMs)
        autoFollow = true
        onPositionUpdated(line.startMs)
        return true
    }

    fun onPlainLyricsTapped(): Boolean {
        // Plain unsynced lyrics do not have line timestamps and must never seek.
        return false
    }

    companion object {
        fun computeCenteredScrollOffset(viewportHeightPx: Int, itemHeightPx: Int): Int {
            if (viewportHeightPx <= 0) return 0
            val safeItemHeight = itemHeightPx.coerceAtLeast(0)
            return ((viewportHeightPx - safeItemHeight) / 2).coerceAtLeast(0)
        }
    }
}


