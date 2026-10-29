package com.bsdung.musicassistantcar.data.telegram

/** Attempt identity, feedback and explicit retry gates. No player, network, storage or automatic retry. */
internal class MeloPlayUiState(private val clock: () -> Long) {
    data class Attempt(val ownerId: Long, val generation: Long, val songKey: Long)
    enum class Phase { RESOLVING, BUFFERING, PLAYING, PAUSED, ERROR }
    data class Feedback(val attempt: Attempt, val phase: Phase, val text: String,
        val retryable: Boolean = false, val deadlineMs: Long? = null, val recoverWhenPlaying: Boolean = false)
    data class Presentation(val attempt: Attempt, val phase: Phase, val text: String,
        val showRetry: Boolean, val retryEnabled: Boolean, val remainingSeconds: Long?)
    private data class Identity(val owner: Long, val songKey: Long)
    private var generation = 0L
    private val waits = HashMap<Identity, Feedback>()
    @Volatile var current: Attempt? = null
        private set
    private var phase: Phase? = null
    private var feedback: Feedback? = null

    fun begin(owner: Long, songKey: Long): Attempt? {
        if (owner <= 0L) return null
        val now = clock()
        waits.entries.removeAll { (it.value.deadlineMs ?: 0L) <= now }
        waits[Identity(owner, songKey)]?.let { feedback = it; return null }
        val previous = current
        if (previous?.ownerId == owner && previous.songKey == songKey && phase in listOf(Phase.RESOLVING, Phase.BUFFERING)) {
            feedback = Feedback(previous, phase!!, if (phase == Phase.RESOLVING) TelegramPlayFeedbackPolicy.RESOLVING_TEXT else TelegramPlayFeedbackPolicy.BUFFERING_TEXT)
            return null
        }
        return Attempt(owner, ++generation, songKey).also {
            current = it; phase = Phase.RESOLVING
            feedback = Feedback(it, Phase.RESOLVING, TelegramPlayFeedbackPolicy.RESOLVING_TEXT)
        }
    }

    fun accepts(attempt: Attempt?, owner: Long, active: Boolean): Boolean =
        active && owner > 0L && attempt != null && attempt == current && attempt.ownerId == owner

    fun buffering(attempt: Attempt) {
        if (attempt != current) return
        phase = Phase.BUFFERING
        feedback = Feedback(attempt, Phase.BUFFERING, TelegramPlayFeedbackPolicy.BUFFERING_TEXT)
    }

    fun fail(attempt: Attempt, failure: TelegramPlayFeedbackPolicy.Failure) {
        if (attempt != current) return
        val deadline = failure.waitSeconds?.let { deadline(clock(), it) }
        val text = if (failure.waitSeconds != null && deadline == null) TelegramPlayFeedbackPolicy.RATE_LIMIT_MESSAGE else failure.text
        phase = Phase.ERROR
        feedback = Feedback(attempt, Phase.ERROR, text, failure.retryable, deadline, failure.recoverWhenPlaying)
        if (deadline != null) waits[Identity(attempt.ownerId, attempt.songKey)] = feedback!!
    }

    /** A still-playing A never clears the feedback for an attempt to open B. */
    fun observe(owner: Long, playerKey: Long?, hasCurrent: Boolean, playing: Boolean, playWhenReady: Boolean, buffering: Boolean) {
        val attempt = current ?: return
        if (attempt.ownerId != owner || attempt.songKey != playerKey || !hasCurrent || phase == Phase.RESOLVING) return
        if (phase == Phase.ERROR) {
            if (playing && feedback?.recoverWhenPlaying == true) { phase = Phase.PLAYING; feedback = null }
            return
        }
        when {
            playing -> { phase = Phase.PLAYING; if (feedback?.attempt == attempt) feedback = null }
            !playWhenReady -> { phase = Phase.PAUSED; feedback = Feedback(attempt, Phase.PAUSED, "Tạm dừng") }
            buffering -> this.buffering(attempt)
        }
    }

    fun presentation(owner: Long): Presentation? {
        val value = feedback?.takeIf { it.attempt.ownerId == owner } ?: return null
        val remaining = value.deadlineMs?.let { (it - clock()).coerceAtLeast(0L) }
        val seconds = remaining?.let { if (it == 0L) 0L else (it - 1L) / 1000L + 1L }
        val text = if (seconds != null) {
            if (seconds > 0L) "Telegram yêu cầu chờ $seconds giây" else "Đã hết thời gian chờ. Có thể thử lại."
        } else value.text
        return Presentation(value.attempt, value.phase, text, value.phase == Phase.ERROR && value.retryable,
            value.phase == Phase.ERROR && value.retryable && (remaining == null || remaining == 0L), seconds)
    }
    fun canRetry(attempt: Attempt, owner: Long): Boolean = presentation(owner)?.let {
        it.attempt == attempt && it.retryEnabled
    } == true

    /** Local play invalidates callbacks, while cooldowns survive a detour through local music. */
    fun cancel() { generation++; current = null; phase = null; feedback = null }
    fun clear() { cancel(); waits.clear() }

    class CountdownTicker(private val post: (Runnable, Long) -> Unit,
        private val remove: (Runnable) -> Unit, onTick: () -> Unit) {
        private val tick = Runnable { onTick() }
        fun update(attached: Boolean, visible: Boolean, focused: Boolean, remainingSeconds: Long) {
            remove(tick)
            if (attached && visible && focused && remainingSeconds > 0L) post(tick, 1000L)
        }
        fun stop() { remove(tick) }
    }

    companion object {
        fun deadline(nowMs: Long, seconds: Long): Long? =
            if (nowMs >= 0L && seconds >= 0L && seconds <= (Long.MAX_VALUE - nowMs) / 1000L) nowMs + seconds * 1000L else null
    }
}
