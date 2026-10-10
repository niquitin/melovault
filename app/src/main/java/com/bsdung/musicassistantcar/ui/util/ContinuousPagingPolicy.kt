package com.bsdung.musicassistantcar.ui.util

/**
 * Deterministic state machine governing continuous vertical infinite scrolling.
 *
 * Guarantees that:
 * 1. Scrolling never triggers duplicate in-flight requests (atomic State.LOADING gating).
 * 2. On network/backend error, auto-fetching is strictly paused (State.ERROR prevents scroll storms).
 * 3. Retry can only be initiated via explicit user interaction (isRetry=true).
 * 4. Stale callbacks from obsolete generations are rejected without corrupting list or state.
 * 5. Reaching the end of the collection permanently pauses further paging requests.
 */
class ContinuousPagingPolicy(
    val pageSize: Int = 30,
) {
    enum class State {
        IDLE,
        LOADING,
        ERROR,
        END_REACHED,
    }

    var state: State = State.IDLE
        private set

    var currentLoadedPages: Int = 0
        private set

    var generation: Long = 0L
        private set

    var errorMessage: String? = null
        private set

    val isLoading: Boolean get() = state == State.LOADING
    val isError: Boolean get() = state == State.ERROR
    val isEndReached: Boolean get() = state == State.END_REACHED

    /**
     * Resets paging for a new folder, search query, or sort order.
     */
    fun reset(): Long {
        generation++
        state = State.IDLE
        currentLoadedPages = 0
        errorMessage = null
        return generation
    }

    /**
     * Evaluates whether a vertical scroll event should trigger fetching the next page.
     * Only returns true if state is strictly IDLE, list is non-empty, and user is near bottom.
     */
    fun shouldFetchOnScroll(
        dy: Int,
        lastVisiblePosition: Int,
        totalItemCount: Int,
        threshold: Int = 4,
    ): Boolean {
        if (dy <= 0) return false
        if (state != State.IDLE) return false
        if (totalItemCount <= 0) return false
        return lastVisiblePosition >= totalItemCount - threshold
    }

    /**
     * Initiates loading next page. Returns false if already loading, at end, or in error (unless isRetry=true).
     */
    fun startLoading(expectedGeneration: Long, isRetry: Boolean = false): Boolean {
        if (expectedGeneration != generation) return false
        if (state == State.LOADING || state == State.END_REACHED) return false
        if (state == State.ERROR && !isRetry) return false

        state = State.LOADING
        errorMessage = null
        return true
    }

    /**
     * Completes page load on success.
     * Returns true if applied, false if generation was stale.
     */
    fun onPageSuccess(
        callbackGeneration: Long,
        newItemsCount: Int,
        totalInCollection: Int = -1,
        loadedItemsCount: Int = -1,
    ): Boolean {
        if (callbackGeneration != generation) return false
        currentLoadedPages++
        val reachedEnd = newItemsCount < pageSize || (totalInCollection in 0..loadedItemsCount)
        state = if (reachedEnd) State.END_REACHED else State.IDLE
        errorMessage = null
        return true
    }

    /**
     * Transitions to ERROR state on network/backend failure.
     * Auto-fetching is paused until user triggers retry.
     */
    fun onPageFailure(
        callbackGeneration: Long,
        message: String? = null,
    ): Boolean {
        if (callbackGeneration != generation) return false
        state = State.ERROR
        errorMessage = message ?: "Không tải được thêm"
        return true
    }
}
