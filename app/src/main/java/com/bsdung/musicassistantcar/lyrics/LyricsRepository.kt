package com.bsdung.musicassistantcar.lyrics

import com.bsdung.musicassistantcar.data.CarApiClient
import java.io.File
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
    val overrideStore: LyricsOverrideStore = LyricsOverrideStore(
        overrideFile = File(
            cache.cacheDir.parentFile ?: cache.cacheDir,
            LyricsOverrideStore.DEFAULT_FILE_NAME,
        ),
    ),
    private val candidatesFetcher: (
        trackKey: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long?,
    ) -> LyricsCandidatesResult = { _, _, _, _, _ -> LyricsCandidatesResult() },
    private val resolveFetcher: (
        provider: String,
        providerRecordId: String,
        forceRefresh: Boolean,
    ) -> LyricsLookupResult = { provider, recordId, forceRefresh ->
        lookupFetcher("", "", "", "", null, forceRefresh, provider, recordId)
    },
) {
    constructor(
        cache: LyricsCache,
        apiClient: CarApiClient,
        overrideStore: LyricsOverrideStore = LyricsOverrideStore(
            overrideFile = File(
                cache.cacheDir.parentFile ?: cache.cacheDir,
                LyricsOverrideStore.DEFAULT_FILE_NAME,
            ),
        ),
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
        overrideStore = overrideStore,
        candidatesFetcher = { trackKey, title, artist, album, durationMs ->
            apiClient.fetchLyricsCandidates(
                trackKey = trackKey,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
            )
        },
        resolveFetcher = { provider, providerRecordId, forceRefresh ->
            apiClient.resolveLyricsByRecordId(
                provider = provider,
                providerRecordId = providerRecordId,
                forceRefresh = forceRefresh,
            )
        },
    )

    private val requestGeneration = AtomicLong(0L)
    private val candidateListGeneration = AtomicLong(0L)

    @Volatile
    private var activeSignature: String? = null

    @Volatile
    private var activeTrackKey: String? = null

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
        candidateListGeneration.incrementAndGet()
        activeSignature = sig
        activeTrackKey = metadata.trackKey
        return LyricsRequestToken(
            generation = gen,
            signature = sig,
            trackKey = metadata.trackKey,
        )
    }

    @Synchronized
    fun beginCandidateListRequest(metadata: LyricsTrackMetadata): LyricsRequestToken {
        val sig = signatureFor(metadata)
        val gen = candidateListGeneration.incrementAndGet()
        if (activeSignature == null) {
            activeSignature = sig
        }
        if (activeTrackKey == null) {
            activeTrackKey = metadata.trackKey
        }
        return LyricsRequestToken(
            generation = gen,
            signature = sig,
            trackKey = metadata.trackKey,
        )
    }

    @Synchronized
    fun cancelActiveRequests() {
        requestGeneration.incrementAndGet()
        candidateListGeneration.incrementAndGet()
        activeSignature = null
        activeTrackKey = null
    }

    @Synchronized
    fun isTokenCurrent(token: LyricsRequestToken): Boolean {
        return token.generation == requestGeneration.get() &&
            token.signature == activeSignature &&
            token.trackKey == activeTrackKey
    }

    @Synchronized
    fun isCandidateListTokenCurrent(token: LyricsRequestToken): Boolean {
        return token.generation == candidateListGeneration.get() &&
            token.signature == activeSignature &&
            token.trackKey == activeTrackKey
    }

    fun hasValidManualOverride(metadata: LyricsTrackMetadata): Boolean {
        return overrideStore.getValidOverride(metadata) != null
    }

    fun hasManualOverride(metadata: LyricsTrackMetadata): Boolean =
        hasValidManualOverride(metadata)

    fun getValidManualOverride(metadata: LyricsTrackMetadata): LyricsOverrideEntry? {
        return overrideStore.getValidOverride(metadata)
    }

    fun getManualOverride(metadata: LyricsTrackMetadata): LyricsOverrideEntry? =
        getValidManualOverride(metadata)

    fun fetchCandidates(metadata: LyricsTrackMetadata): LyricsCandidatesResult {
        if (!metadata.hasLookupMetadata()) {
            return LyricsCandidatesResult(
                status = "OK",
                candidates = emptyList(),
                diagnostic = "missing_metadata",
            )
        }
        return try {
            val raw = candidatesFetcher(
                metadata.trackKey,
                metadata.title,
                metadata.artist,
                metadata.album,
                metadata.durationMs,
            )
            val deduped = LinkedHashMap<String, LyricsCandidate>()
            for (cand in raw.candidates) {
                val id = cand.recordId.trim()
                if (id.isNotEmpty() && !deduped.containsKey(id)) {
                    deduped[id] = cand
                }
                if (deduped.size >= MAX_DISPLAY_CANDIDATES) break
            }
            raw.copy(candidates = deduped.values.toList())
        } catch (exc: Exception) {
            LyricsCandidatesResult(
                status = "TEMPORARY_ERROR",
                candidates = emptyList(),
                diagnostic = exc.message ?: exc.javaClass.simpleName,
            )
        }
    }

    fun selectManualCandidate(
        metadata: LyricsTrackMetadata,
        candidate: LyricsCandidate,
        forceRefresh: Boolean = false,
    ): LyricsLookupResult {
        val result = selectManualCandidate(
            metadata = metadata,
            provider = candidate.provider,
            providerRecordId = candidate.recordId,
            forceRefresh = forceRefresh,
        )
        if (metadata.trackKey.isNotBlank() && result.status in POSITIVE_STATUSES) {
            overrideStore.saveOverride(
                metadata = metadata,
                provider = candidate.provider.ifBlank { result.provider },
                providerRecordId = candidate.recordId,
                selectedTitle = candidate.title.ifBlank { metadata.title },
                selectedArtist = candidate.artist.ifBlank { metadata.artist },
                selectedAlbum = candidate.album.ifBlank { metadata.album },
                selectedDurationMs = candidate.durationMs ?: metadata.durationMs,
            )
        }
        return result
    }

    fun selectManualCandidate(
        metadata: LyricsTrackMetadata,
        provider: String = "lrclib",
        providerRecordId: String,
        forceRefresh: Boolean = false,
    ): LyricsLookupResult {
        val cleanRecordId = providerRecordId.trim()
        val cleanProvider = provider.trim().ifEmpty { "lrclib" }
        val signature = signatureFor(metadata)

        if (!forceRefresh && cleanRecordId.isNotEmpty()) {
            val cached = cache.read(signature)
            if (
                cached != null &&
                !cached.isSoftExpired &&
                cached.result.providerRecordId == cleanRecordId &&
                cached.result.status in POSITIVE_STATUSES
            ) {
                if (metadata.trackKey.isNotBlank()) {
                    overrideStore.saveOverride(
                        metadata = metadata,
                        provider = cleanProvider,
                        providerRecordId = cleanRecordId,
                    )
                }
                cache.saveManualOverride(
                    signature = signature,
                    provider = cleanProvider,
                    recordId = cleanRecordId,
                )
                return cached.result.copy(
                    fromCache = true,
                    provenance = LyricsProvenance.MANUAL,
                )
            }
        }

        return resolveLyrics(
            metadata = metadata,
            forceRefresh = forceRefresh,
            preferredProvider = cleanProvider,
            preferredRecordId = cleanRecordId,
        )
    }

    fun clearManualOverride(metadata: LyricsTrackMetadata): LyricsLookupResult {
        val signature = signatureFor(metadata)
        if (metadata.trackKey.isNotBlank()) {
            overrideStore.clearOverride(metadata.trackKey)
        }
        cache.invalidate(signature)
        return resolveLyrics(
            metadata = metadata,
            forceRefresh = false,
            preferredProvider = null,
            preferredRecordId = null,
        )
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

        val storedOverride = if (preferredRecordId.isNullOrBlank()) {
            overrideStore.getValidOverride(metadata)
        } else {
            null
        }
        val isManualOverride = !preferredRecordId.isNullOrBlank() || storedOverride != null
        val effectiveProvider = when {
            !preferredProvider.isNullOrBlank() -> preferredProvider.trim()
            storedOverride != null -> storedOverride.provider
            else -> null
        }
        val effectiveRecordId = when {
            !preferredRecordId.isNullOrBlank() -> preferredRecordId.trim()
            storedOverride != null -> storedOverride.providerRecordId
            else -> null
        }
        val expectedProvenance = if (isManualOverride) LyricsProvenance.MANUAL else LyricsProvenance.AUTO

        if (forceRefresh) {
            cache.invalidateEntryOnly(signature)
        }

        var stalePositiveBackup: LyricsLookupResult? = null

        if (!forceRefresh) {
            val cached = cache.read(signature)
            if (cached != null) {
                val matchesRecord = !isManualOverride || cached.result.providerRecordId == effectiveRecordId
                if (matchesRecord) {
                    val taggedCached = cached.result.copy(
                        fromCache = true,
                        provenance = expectedProvenance,
                    )
                    if (!cached.isSoftExpired) {
                        return taggedCached
                    }
                    stalePositiveBackup = taggedCached
                }
            }
        }

        val rawRemoteResult = try {
            if (isManualOverride && !effectiveRecordId.isNullOrBlank()) {
                resolveFetcher(
                    effectiveProvider ?: "lrclib",
                    effectiveRecordId,
                    forceRefresh,
                )
            } else {
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
            }
        } catch (exc: Exception) {
            LyricsLookupResult(
                status = LyricsStatus.TEMPORARY_ERROR,
                diagnostic = exc.message ?: exc.javaClass.simpleName,
            )
        }

        val parsedLines = if (rawRemoteResult.syncedLines.isNotEmpty()) {
            rawRemoteResult.syncedLines
        } else if (!rawRemoteResult.syncedLyrics.isNullOrBlank()) {
            LrcParser.parse(rawRemoteResult.syncedLyrics)
        } else {
            emptyList()
        }
        val remoteResult = rawRemoteResult.copy(
            syncedLines = parsedLines,
            provenance = expectedProvenance,
        )

        if (remoteResult.status == LyricsStatus.TEMPORARY_ERROR && stalePositiveBackup != null) {
            return stalePositiveBackup
        }

        if (isManualOverride && !effectiveRecordId.isNullOrBlank() && remoteResult.status in POSITIVE_STATUSES) {
            if (metadata.trackKey.isNotBlank()) {
                val existingOverride = overrideStore.getValidOverride(metadata)
                overrideStore.saveOverride(
                    metadata = metadata,
                    provider = remoteResult.provider,
                    providerRecordId = effectiveRecordId,
                    selectedTitle = existingOverride?.title ?: remoteResult.matchedTrack?.title ?: metadata.title,
                    selectedArtist = existingOverride?.artist ?: remoteResult.matchedTrack?.artist ?: metadata.artist,
                    selectedAlbum = existingOverride?.album ?: remoteResult.matchedTrack?.album ?: metadata.album,
                    selectedDurationMs = existingOverride?.durationMs ?: remoteResult.matchedTrack?.durationMs ?: metadata.durationMs,
                )
            }
            cache.saveManualOverride(
                signature = signature,
                provider = remoteResult.provider,
                recordId = effectiveRecordId,
            )
            cache.write(signature, remoteResult)
        } else if (!isManualOverride && remoteResult.status in CACHEABLE_STATUSES) {
            cache.write(signature, remoteResult)
        }

        return remoteResult
    }

    companion object {
        const val MAX_DISPLAY_CANDIDATES = 5

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
                                provenance = result.provenance,
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
                            provenance = result.provenance,
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
                            provenance = result.provenance,
                        )
                    }
                }
                LyricsStatus.INSTRUMENTAL -> LyricsUiState.Instrumental(
                    provider = result.provider,
                    fromCache = result.fromCache,
                    provenance = result.provenance,
                )
                LyricsStatus.AMBIGUOUS -> {
                    if (result.candidates.isEmpty()) {
                        LyricsUiState.NotFound(provider = result.provider)
                    } else {
                        LyricsUiState.Ambiguous(
                            candidates = result.candidates.take(MAX_DISPLAY_CANDIDATES),
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

    var candidatePickerState: LyricsCandidatePickerState = LyricsCandidatePickerState.Hidden
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

    var currentTrackKey: String? = null
        private set

    private val candidateRequestGeneration = AtomicLong(0L)

    val isKeepScreenOnEnabled: Boolean
        get() = isFullscreenVisible && isActivityInForeground

    fun startLookup(metadata: LyricsTrackMetadata): LyricsRequestToken {
        autoFollow = true
        activeLineIndex = -1
        scrollPositionIndex = 0
        if (currentTrackKey != null && currentTrackKey != metadata.trackKey) {
            candidateRequestGeneration.incrementAndGet()
            candidatePickerState = LyricsCandidatePickerState.Hidden
        }
        val token = repository.beginTrackRequest(metadata)
        currentSignature = token.signature
        currentTrackKey = token.trackKey
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
        candidateRequestGeneration.incrementAndGet()
        candidatePickerState = LyricsCandidatePickerState.Hidden
        if (metadata == null) {
            repository.cancelActiveRequests()
            currentSignature = null
            currentTrackKey = null
            uiState = LyricsUiState.Idle
            return null
        }
        return startLookup(metadata)
    }

    @Synchronized
    fun beginCandidateRequest(metadata: LyricsTrackMetadata): LyricsRequestToken {
        val sig = repository.signatureFor(metadata)
        val gen = candidateRequestGeneration.incrementAndGet()
        currentSignature = sig
        currentTrackKey = metadata.trackKey
        candidatePickerState = LyricsCandidatePickerState.Loading(
            trackKey = metadata.trackKey,
            title = metadata.title,
            artist = metadata.artist,
        )
        return LyricsRequestToken(
            generation = gen,
            signature = sig,
            trackKey = metadata.trackKey,
        )
    }

    fun startCandidatePicker(metadata: LyricsTrackMetadata): LyricsRequestToken =
        beginCandidateRequest(metadata)

    @Synchronized
    fun isCandidateTokenCurrent(token: LyricsRequestToken): Boolean {
        return token.generation == candidateRequestGeneration.get() &&
            token.signature == currentSignature &&
            token.trackKey == currentTrackKey
    }

    @Synchronized
    fun applyCandidatesIfCurrent(
        token: LyricsRequestToken,
        response: LyricsCandidatesResult,
        selectedRecordId: String? = null,
    ): Boolean {
        if (!isCandidateTokenCurrent(token)) {
            return false
        }
        candidatePickerState = when {
            response.status.equals("TEMPORARY_ERROR", ignoreCase = true) ||
                response.status.equals("ERROR", ignoreCase = true) -> {
                LyricsCandidatePickerState.Error(
                    trackKey = token.trackKey,
                    diagnostic = response.diagnostic,
                )
            }
            response.candidates.isEmpty() -> {
                LyricsCandidatePickerState.Empty(trackKey = token.trackKey)
            }
            else -> {
                LyricsCandidatePickerState.Loaded(
                    trackKey = token.trackKey,
                    candidates = response.candidates.take(LyricsRepository.MAX_DISPLAY_CANDIDATES),
                    selectedRecordId = selectedRecordId,
                )
            }
        }
        return true
    }

    fun openCandidatePickerSynchronously(metadata: LyricsTrackMetadata): LyricsCandidatePickerState {
        val token = beginCandidateRequest(metadata)
        val response = repository.fetchCandidates(metadata)
        val activeOverrideId = repository.getValidManualOverride(metadata)?.providerRecordId
            ?: (uiState as? LyricsUiState.Synced)?.providerRecordId
            ?: (uiState as? LyricsUiState.Plain)?.providerRecordId
        applyCandidatesIfCurrent(token, response, activeOverrideId)
        return candidatePickerState
    }

    @Synchronized
    fun closeCandidatePicker() {
        candidateRequestGeneration.incrementAndGet()
        candidatePickerState = LyricsCandidatePickerState.Hidden
    }

    fun selectCandidateSynchronously(
        metadata: LyricsTrackMetadata,
        candidate: LyricsCandidate,
        currentPositionMs: Long = 0L,
    ): LyricsUiState {
        val token = startLookup(metadata)
        val result = repository.selectManualCandidate(
            metadata = metadata,
            candidate = candidate,
            forceRefresh = false,
        )
        if (applyResultIfCurrent(token, result, currentPositionMs)) {
            closeCandidatePicker()
        }
        return uiState
    }

    fun clearManualOverrideSynchronously(
        metadata: LyricsTrackMetadata,
        currentPositionMs: Long = 0L,
    ): LyricsUiState {
        val token = startLookup(metadata)
        val result = repository.clearManualOverride(metadata)
        if (applyResultIfCurrent(token, result, currentPositionMs)) {
            closeCandidatePicker()
        }
        return uiState
    }

    fun clearOverrideSynchronously(
        metadata: LyricsTrackMetadata,
        currentPositionMs: Long = 0L,
    ): LyricsUiState = clearManualOverrideSynchronously(metadata, currentPositionMs)

    fun enterFullscreen(
        metadata: LyricsTrackMetadata? = null,
        currentPositionMs: Long = 0L,
    ): LyricsUiState {
        isFullscreenVisible = true
        if (metadata == null) {
            return uiState
        }
        val sig = repository.signatureFor(metadata)
        if (currentSignature == sig && currentTrackKey == metadata.trackKey && uiState !is LyricsUiState.Idle) {
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
        closeCandidatePicker()
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
        currentTrackKey = token.trackKey
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
