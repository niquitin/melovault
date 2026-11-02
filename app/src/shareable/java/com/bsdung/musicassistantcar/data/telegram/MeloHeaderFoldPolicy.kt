package com.bsdung.musicassistantcar.data.telegram

/**
 * The owner's title row folds away while the song list scrolls down and shows again on the way back up.
 *
 * Folding it while a finger is still dragging moves the list under the finger, which reads as a drag the
 * other way and folds it back at once, so the row blinked for as long as the finger stayed down. This keeps
 * what the scroll wants apart from what is applied: a change waits while the finger drags and goes through
 * once the list flings or stops. Pure JVM; the pane feeds it the scroll deltas and the scroll state.
 */
internal class HeaderFold(private val thresholdPx: Int) {

    enum class ScrollPhase { IDLE, TOUCH, FLING }

    /** The state the screen shows now. */
    var applied = false
        private set
    var phase = ScrollPhase.IDLE
        private set

    private var wanted = false
    private var accumulated = 0
    private var rebaseline = false

    /**
     * [delta] is positive when the list scrolled down (content moved up). It adds up while the direction
     * holds and starts over when it turns, so only a real run of scrolling crosses the threshold, not one
     * noisy frame. [atTop]: the first row is on screen with its top edge showing.
     */
    fun onDelta(delta: Int, atTop: Boolean) {
        if (delta != 0) {
            val limit = MAX_RUN_THRESHOLDS * thresholdPx
            accumulated = if (accumulated == 0 || (delta > 0) == (accumulated > 0)) {
                (accumulated + delta).coerceIn(-limit, limit)
            } else delta
        }
        wanted = when {
            atTop -> false
            accumulated > thresholdPx -> true
            accumulated < -thresholdPx -> false
            else -> wanted
        }
    }

    fun onPhase(next: ScrollPhase) {
        phase = next
    }

    /** The state to switch to now, or null: nothing to change, or a finger is still dragging. */
    fun pendingChange(): Boolean? = wanted.takeIf { it != applied && phase != ScrollPhase.TOUCH }

    /** The screen now shows [value]; the next scroll callback measures from where the list is after the change. */
    fun markApplied(value: Boolean) {
        applied = value
        wanted = value
        accumulated = 0
        rebaseline = true
    }

    /** True once after [markApplied]: that callback only takes a new reference point, it is not scrolling. */
    fun consumeRebaseline(): Boolean = rebaseline.also { rebaseline = false }

    fun reset() {
        applied = false
        wanted = false
        accumulated = 0
        rebaseline = false
    }

    private companion object {
        const val MAX_RUN_THRESHOLDS = 4
    }
}
