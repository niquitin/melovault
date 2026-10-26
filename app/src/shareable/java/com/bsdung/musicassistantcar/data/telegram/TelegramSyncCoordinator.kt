package com.bsdung.musicassistantcar.data.telegram

import java.util.concurrent.atomic.AtomicLong

/**
 * Modes of synchronization for Telegram Library.
 */
enum class TelegramSyncMode {
    /**
     * Resumes from stored checkpoint if available, or scans all reachable audio and documents
     * across all pages until cursors are exhausted.
     */
    FULL_DISCOVERY,

    /**
     * Scans newest messages down to the existing high-water mark (with a safety overlap window).
     */
    INCREMENTAL
}

/**
 * Action or instruction returned by [TelegramSyncCoordinator] to the caller (Activity or Worker).
 */
sealed class TelegramSyncAction {
    data class RequestPage(
        val phase: String,
        val offset: String,
        val limit: Int,
        val generation: Long,
        val recommendedDelayMs: Long = 0L
    ) : TelegramSyncAction()

    data class ProgressUpdate(
        val state: TelegramSyncState,
        val newTracksInCycle: Int
    ) : TelegramSyncAction()

    data class Completed(
        val totalTracksIndexed: Int,
        val isIncremental: Boolean
    ) : TelegramSyncAction()

    data class Paused(
        val state: TelegramSyncState
    ) : TelegramSyncAction()

    data class Failed(
        val errorCode: Int,
        val errorMessage: String,
        val canResume: Boolean
    ) : TelegramSyncAction()

    data class BackoffScheduled(
        val retryAfterSeconds: Int,
        val attempt: Int,
        val nextOffset: String,
        val phase: String,
        val generation: Long
    ) : TelegramSyncAction()

    object DiscardedStale : TelegramSyncAction()
}

/**
 * Manages the state machine and policy for resumable paged and incremental synchronization
 * of Telegram audio and audio-documents.
 */
class TelegramSyncCoordinator(
    private val dao: TelegramLibraryDao
) {
    private val generationCounter = AtomicLong(1L)
    @Volatile private var activeAccountId: Long = 0L
    @Volatile private var activeGeneration: Long = 0L
    @Volatile private var activeMode: TelegramSyncMode = TelegramSyncMode.FULL_DISCOVERY
    @Volatile private var isSyncing: Boolean = false
    @Volatile private var isPaused: Boolean = false

    private var currentPhase: String = TelegramSyncState.PHASE_IDLE
    private var currentOffset: String = ""
    private var pagesInPhase: Int = 0
    private var totalItemsInCycle: Int = 0
    private var highWaterDate: Long = 0L
    private var overlapEncounteredCount: Int = 0
    private var retryAttemptCount: Int = 0

    companion object {
        const val PAGE_LIMIT = 100
        const val INCREMENTAL_OVERLAP_TOLERANCE = 10
        const val COOPERATIVE_PACE_DELAY_MS = 50L
        const val RATE_LIMIT_DEFAULT_BACKOFF_MS = 3000L
        const val MAX_BACKOFF_RETRIES = 3
    }

    @Synchronized
    fun isBusy(): Boolean = isSyncing

    /** True after [pauseSync] until the next [startSync] or [cancelSync]; read-only, for the ⋮ menu card. */
    @Synchronized
    fun isPaused(): Boolean = isPaused

    @Synchronized
    fun getActiveGeneration(): Long = activeGeneration

    @Synchronized
    fun getActiveAccountId(): Long = activeAccountId

    /**
     * Initiates or resumes synchronization for the specified account.
     */
    @Synchronized
    fun startSync(
        accountId: Long,
        mode: TelegramSyncMode,
        forceFullRestart: Boolean = false
    ): TelegramSyncAction {
        require(accountId > 0L) { "Invalid Telegram account ID: $accountId" }

        // If switching account, cancel old work immediately
        if (activeAccountId != accountId) {
            cancelSync()
            activeAccountId = accountId
        }

        if (isSyncing && !isPaused && !forceFullRestart) {
            val existing = dao.getSyncState(accountId) ?: TelegramSyncState(accountId = accountId)
            return TelegramSyncAction.ProgressUpdate(existing, totalItemsInCycle)
        }

        activeGeneration = generationCounter.incrementAndGet()
        activeMode = mode
        isSyncing = true
        isPaused = false
        totalItemsInCycle = 0
        overlapEncounteredCount = 0
        retryAttemptCount = 0

        val existingState = dao.getSyncState(accountId)
        val canResumeExisting = mode == TelegramSyncMode.FULL_DISCOVERY && !forceFullRestart &&
            existingState != null &&
            existingState.status in listOf(
                TelegramSyncState.STATUS_PARTIAL,
                TelegramSyncState.STATUS_PAUSED,
                TelegramSyncState.STATUS_SYNCING
            ) &&
            existingState.phase in listOf(TelegramSyncState.PHASE_AUDIO, TelegramSyncState.PHASE_DOCUMENT) &&
            (existingState.nextOffset.isNotEmpty() || existingState.phase == TelegramSyncState.PHASE_DOCUMENT)

        if (canResumeExisting) {
            // Resume from stored cursor (even if process died mid-sync in STATUS_SYNCING)
            currentPhase = existingState!!.phase
            currentOffset = existingState.nextOffset
            pagesInPhase = existingState.pagesSynced
            totalItemsInCycle = existingState.totalItemsSynced
        } else {
            // Fresh start
            currentPhase = TelegramSyncState.PHASE_AUDIO
            currentOffset = ""
            pagesInPhase = 0
            totalItemsInCycle = 0
        }

        // Phase-specific high-water mark to prevent document termination based on audio dates
        highWaterDate = dao.getLatestSongDate(
            accountId,
            if (currentPhase == TelegramSyncState.PHASE_DOCUMENT) "document" else "audio"
        )

        val updatedState = TelegramSyncState(
            accountId = accountId,
            syncScope = TelegramSyncState.SCOPE_GLOBAL,
            phase = currentPhase,
            nextOffset = currentOffset,
            pagesSynced = pagesInPhase,
            totalItemsSynced = totalItemsInCycle,
            status = TelegramSyncState.STATUS_SYNCING,
            highWaterDate = highWaterDate
        )
        dao.updateSyncState(updatedState)

        return TelegramSyncAction.RequestPage(
            phase = currentPhase,
            offset = currentOffset,
            limit = PAGE_LIMIT,
            generation = activeGeneration,
            recommendedDelayMs = 0L
        )
    }

    /**
     * Pauses an active synchronization gracefully.
     */
    @Synchronized
    fun pauseSync(): TelegramSyncAction? {
        if (!isSyncing || isPaused) return null
        isPaused = true
        isSyncing = false
        val state = dao.getSyncState(activeAccountId)?.copy(
            status = TelegramSyncState.STATUS_PAUSED,
            nextOffset = currentOffset,
            phase = currentPhase,
            pagesSynced = pagesInPhase,
            totalItemsSynced = totalItemsInCycle
        ) ?: TelegramSyncState(
            accountId = activeAccountId,
            status = TelegramSyncState.STATUS_PAUSED,
            nextOffset = currentOffset,
            phase = currentPhase
        )
        dao.updateSyncState(state)
        return TelegramSyncAction.Paused(state)
    }

    /**
     * Cancels any active synchronization and invalidates pending async callbacks.
     */
    @Synchronized
    fun cancelSync() {
        generationCounter.incrementAndGet()
        isSyncing = false
        isPaused = false
        retryAttemptCount = 0
        if (activeAccountId > 0L) {
            val state = dao.getSyncState(activeAccountId)?.copy(status = TelegramSyncState.STATUS_PARTIAL)
            if (state != null) dao.updateSyncState(state)
        }
    }

    /**
     * Processes a page of incoming messages from TDLib.
     */
    @Synchronized
    fun onPageReceived(
        accountId: Long,
        generation: Long,
        phase: String,
        songs: List<IndexedTelegramSong>,
        nextOffset: String
    ): TelegramSyncAction {
        if (generation != activeGeneration || accountId != activeAccountId || !isSyncing || phase != currentPhase) {
            return TelegramSyncAction.DiscardedStale
        }

        // Stalled cursor check: repeated identical non-empty offset is a stall, NOT completion!
        val isCursorStalled = nextOffset.isNotEmpty() && nextOffset == currentOffset
        if (isCursorStalled) {
            isSyncing = false
            val errorMsg = "Cursor stalled at offset: $nextOffset"
            val state = TelegramSyncState(
                accountId = accountId,
                syncScope = TelegramSyncState.SCOPE_GLOBAL,
                phase = currentPhase,
                nextOffset = currentOffset,
                pagesSynced = pagesInPhase,
                totalItemsSynced = totalItemsInCycle,
                status = TelegramSyncState.STATUS_PARTIAL,
                highWaterDate = highWaterDate,
                lastErrorMessage = errorMsg
            )
            dao.updateSyncState(state)
            return TelegramSyncAction.Failed(
                errorCode = -2,
                errorMessage = errorMsg,
                canResume = true
            )
        }

        retryAttemptCount = 0
        pagesInPhase++

        // Check incremental sync high-water boundary for current phase
        var incrementalDone = false
        if (activeMode == TelegramSyncMode.INCREMENTAL && highWaterDate > 0L) {
            for (song in songs) {
                if (song.date <= highWaterDate) {
                    overlapEncounteredCount++
                }
            }
            if (overlapEncounteredCount >= INCREMENTAL_OVERLAP_TOLERANCE) {
                incrementalDone = true
            }
        }

        // Cursor is exhausted if nextOffset is empty, or incremental threshold reached
        val isPhaseExhausted = nextOffset.isEmpty() || incrementalDone

        if (!isPhaseExhausted) {
            // Continue in current phase with atomic page and checkpoint commit
            currentOffset = nextOffset
            val state = TelegramSyncState(
                accountId = accountId,
                syncScope = TelegramSyncState.SCOPE_GLOBAL,
                phase = currentPhase,
                nextOffset = currentOffset,
                pagesSynced = pagesInPhase,
                totalItemsSynced = totalItemsInCycle + songs.size,
                status = TelegramSyncState.STATUS_SYNCING,
                highWaterDate = highWaterDate
            )
            dao.commitPageAndCheckpoint(accountId, currentPhase, currentOffset, songs, state)
            totalItemsInCycle += songs.size

            return TelegramSyncAction.RequestPage(
                phase = currentPhase,
                offset = currentOffset,
                limit = PAGE_LIMIT,
                generation = activeGeneration,
                recommendedDelayMs = COOPERATIVE_PACE_DELAY_MS
            )
        } else {
            // Phase is finished; commit any remaining songs on this boundary page
            if (songs.isNotEmpty()) {
                totalItemsInCycle += songs.size
                val interimState = TelegramSyncState(
                    accountId = accountId,
                    syncScope = TelegramSyncState.SCOPE_GLOBAL,
                    phase = currentPhase,
                    nextOffset = nextOffset,
                    pagesSynced = pagesInPhase,
                    totalItemsSynced = totalItemsInCycle,
                    status = TelegramSyncState.STATUS_SYNCING,
                    highWaterDate = highWaterDate
                )
                dao.commitPageAndCheckpoint(accountId, currentPhase, nextOffset, songs, interimState)
            }
            return advanceToNextPhaseOrComplete(accountId)
        }
    }

    @Synchronized
    private fun advanceToNextPhaseOrComplete(accountId: Long): TelegramSyncAction {
        if (currentPhase == TelegramSyncState.PHASE_AUDIO) {
            // Advance from audio to document phase
            currentPhase = TelegramSyncState.PHASE_DOCUMENT
            currentOffset = ""
            pagesInPhase = 0
            overlapEncounteredCount = 0

            // Phase-specific high-water mark for document phase
            highWaterDate = dao.getLatestSongDate(accountId, "document")

            val state = TelegramSyncState(
                accountId = accountId,
                syncScope = TelegramSyncState.SCOPE_GLOBAL,
                phase = currentPhase,
                nextOffset = currentOffset,
                pagesSynced = pagesInPhase,
                totalItemsSynced = totalItemsInCycle,
                status = TelegramSyncState.STATUS_SYNCING,
                highWaterDate = highWaterDate
            )
            dao.updateSyncState(state)

            return TelegramSyncAction.RequestPage(
                phase = currentPhase,
                offset = currentOffset,
                limit = PAGE_LIMIT,
                generation = activeGeneration,
                recommendedDelayMs = COOPERATIVE_PACE_DELAY_MS
            )
        } else {
            // Both audio and document phases complete
            isSyncing = false
            currentPhase = TelegramSyncState.PHASE_IDLE
            currentOffset = ""

            // Recalculate source track counts
            dao.recomputeSourceTrackCounts(accountId)

            val newHighWater = dao.getLatestSongDate(accountId)
            val completeState = TelegramSyncState(
                accountId = accountId,
                syncScope = TelegramSyncState.SCOPE_GLOBAL,
                phase = TelegramSyncState.PHASE_IDLE,
                nextOffset = "",
                pagesSynced = pagesInPhase,
                totalItemsSynced = totalItemsInCycle,
                status = TelegramSyncState.STATUS_COMPLETE,
                highWaterDate = newHighWater,
                lastSyncCompletedAtMs = System.currentTimeMillis()
            )
            dao.updateSyncState(completeState)

            val totalIndexed = dao.getSongCount(accountId)
            return TelegramSyncAction.Completed(
                totalTracksIndexed = totalIndexed,
                isIncremental = activeMode == TelegramSyncMode.INCREMENTAL
            )
        }
    }

    /**
     * Handles an error returned by TDLib.
     */
    @Synchronized
    fun onError(
        accountId: Long,
        generation: Long,
        errorCode: Int,
        errorMessage: String
    ): TelegramSyncAction {
        if (generation != activeGeneration || accountId != activeAccountId || !isSyncing) {
            return TelegramSyncAction.DiscardedStale
        }

        val isRateLimit = errorCode == 429 || errorMessage.contains("FLOOD_WAIT", ignoreCase = true)
        if (isRateLimit && retryAttemptCount < MAX_BACKOFF_RETRIES) {
            retryAttemptCount++
            val match = Regex("FLOOD_WAIT_(\\d+)", RegexOption.IGNORE_CASE).find(errorMessage)
            val waitSec = match?.groupValues?.get(1)?.toIntOrNull()
                ?: (2 * (1 shl (retryAttemptCount - 1).coerceAtMost(5))).coerceAtMost(60)

            val state = dao.getSyncState(accountId)?.copy(
                status = TelegramSyncState.STATUS_PARTIAL,
                lastErrorMessage = "FLOOD_WAIT: retrying in ${waitSec}s (attempt $retryAttemptCount/$MAX_BACKOFF_RETRIES)"
            ) ?: TelegramSyncState(
                accountId = accountId,
                status = TelegramSyncState.STATUS_PARTIAL,
                lastErrorMessage = "FLOOD_WAIT: retrying in ${waitSec}s (attempt $retryAttemptCount/$MAX_BACKOFF_RETRIES)"
            )
            dao.updateSyncState(state)

            return TelegramSyncAction.BackoffScheduled(
                retryAfterSeconds = waitSec,
                attempt = retryAttemptCount,
                nextOffset = currentOffset,
                phase = currentPhase,
                generation = activeGeneration
            )
        }

        isSyncing = false
        val canResume = isRateLimit || currentOffset.isNotEmpty()

        val state = dao.getSyncState(accountId)?.copy(
            status = if (canResume) TelegramSyncState.STATUS_PARTIAL else TelegramSyncState.STATUS_ERROR,
            lastErrorMessage = "$errorCode: $errorMessage"
        ) ?: TelegramSyncState(
            accountId = accountId,
            status = if (canResume) TelegramSyncState.STATUS_PARTIAL else TelegramSyncState.STATUS_ERROR,
            lastErrorMessage = "$errorCode: $errorMessage"
        )
        dao.updateSyncState(state)

        return TelegramSyncAction.Failed(
            errorCode = errorCode,
            errorMessage = errorMessage,
            canResume = canResume
        )
    }
}
