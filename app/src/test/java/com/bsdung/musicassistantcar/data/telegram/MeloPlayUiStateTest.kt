package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test

class MeloPlayUiStateTest {
    @Test fun wholeFloodWaitTakesPrecedenceOverGeneralErrorCode() {
        assertEquals(1L, TelegramPlayFeedbackPolicy.failure("FLOOD_WAIT_1", 400).waitSeconds)
        assertEquals(60L, TelegramPlayFeedbackPolicy.failure("FLOOD_WAIT_60", 420).waitSeconds)
        assertEquals("Telegram yêu cầu chờ 60 giây", TelegramPlayFeedbackPolicy.errorText("FLOOD_WAIT_60", 429))
        assertEquals(TelegramPlayFeedbackPolicy.RATE_LIMIT_MESSAGE, TelegramPlayFeedbackPolicy.failure("too many requests", 429).text)
        assertNull(TelegramPlayFeedbackPolicy.failure("too many requests", 429).waitSeconds)
    }

    @Test fun malformedOverflowAndPrivateRawTextNeverProduceCountdownOrEcho() {
        val raw = listOf("FLOOD_WAIT_-1", "FLOOD_WAIT_", "FLOOD_WAIT_999999999999999999999999", "FLOOD_WAIT_9223372036854775807",
            "FLOOD_WAIT_60 PRIVATE_FILE.mp3", "FLOOD_WAIT_1\n", " FLOOD_WAIT_1", "flood_wait_1")
        for (message in raw) {
            val failure = TelegramPlayFeedbackPolicy.failure(message, 429)
            assertNull(message, failure.waitSeconds)
            assertEquals(TelegramPlayFeedbackPolicy.RATE_LIMIT_MESSAGE, failure.text)
            assertFalse(failure.text.contains("PRIVATE_FILE"))
        }
    }

    @Test fun deadlineRejectsMultiplicationAndAdditionOverflow() {
        assertEquals(61_000L, MeloPlayUiState.deadline(1_000, 60))
        assertNull(MeloPlayUiState.deadline(1_000, Long.MAX_VALUE))
        assertNull(MeloPlayUiState.deadline(Long.MAX_VALUE - 500, 1))
        assertNull(MeloPlayUiState.deadline(-1, 1))
        assertNull(MeloPlayUiState.deadline(1, -1))
        val state = MeloPlayUiState { Long.MAX_VALUE - 500 }
        val attempt = state.begin(1, 10)!!
        state.fail(attempt, TelegramPlayFeedbackPolicy.failure("FLOOD_WAIT_1", 420))
        assertEquals(TelegramPlayFeedbackPolicy.RATE_LIMIT_MESSAGE, state.presentation(1)?.text)
        assertNull(state.presentation(1)?.remainingSeconds)
    }

    @Test fun retryIsBlockedBeforeAndEnabledAtDeadlineWithoutAutomaticAttempt() {
        var now = 1_000L
        val state = MeloPlayUiState { now }
        val attempt = state.begin(1, 10)!!
        state.fail(attempt, TelegramPlayFeedbackPolicy.failure("FLOOD_WAIT_1", 429))
        assertFalse(state.canRetry(attempt, 1))
        assertNull(state.begin(1, 10))
        now = 1_999
        assertEquals(1L, state.presentation(1)?.remainingSeconds)
        assertFalse(state.canRetry(attempt, 1))
        now = 2_000
        assertTrue(state.canRetry(attempt, 1))
        assertEquals(attempt, state.current)
        now = 2_001
        assertTrue(state.canRetry(attempt, 1))
        assertTrue(state.begin(1, 10)!!.generation > attempt.generation)
    }

    @Test fun switchingAtoBtoAAndLocalDetourCannotEvadeCooldown() {
        var now = 1_000L
        val state = MeloPlayUiState { now }
        val a = state.begin(1, 10)!!
        state.fail(a, TelegramPlayFeedbackPolicy.failure("FLOOD_WAIT_60", 420))
        val b = state.begin(1, 20)!!
        assertNull(state.begin(1, 10))
        assertEquals(b, state.current)
        assertEquals(a, state.presentation(1)?.attempt)
        state.cancel()
        assertNull(state.begin(1, 10))
        now = 61_000
        assertNotNull(state.begin(1, 10))
    }

    @Test fun staleAAfterBAndNewAIsRejectedEvenWithTheSameSongKey() {
        val state = MeloPlayUiState { 1_000L }
        val oldA = state.begin(1, 10)!!
        val b = state.begin(1, 20)!!
        val newA = state.begin(1, 10)!!
        assertFalse(state.accepts(oldA, 1, true))
        assertFalse(state.accepts(b, 1, true))
        assertTrue(state.accepts(newA, 1, true))
        state.fail(oldA, TelegramPlayFeedbackPolicy.failure("FLOOD_WAIT_60", 429))
        assertEquals(newA, state.presentation(1)?.attempt)
        assertEquals(MeloPlayUiState.Phase.RESOLVING, state.presentation(1)?.phase)
    }

    @Test fun repeatedResolveAndBufferTapsDoNotStartAnotherAttempt() {
        val state = MeloPlayUiState { 1_000L }
        val attempt = state.begin(1, 10)!!
        assertNull(state.begin(1, 10))
        state.buffering(attempt)
        assertNull(state.begin(1, 10))
        assertEquals(attempt, state.current)
    }

    @Test fun bufferingIsNotPausedAndCurrentItemAloneDoesNotClearIt() {
        val state = MeloPlayUiState { 1_000L }
        val attempt = state.begin(1, 10)!!
        state.buffering(attempt)
        state.observe(1, 10, true, false, true, true)
        assertEquals(MeloPlayUiState.Phase.BUFFERING, state.presentation(1)?.phase)
        assertEquals(TelegramPlayFeedbackPolicy.BUFFERING_TEXT, state.presentation(1)?.text)
        state.observe(1, 10, true, false, false, true)
        assertEquals(MeloPlayUiState.Phase.PAUSED, state.presentation(1)?.phase)
        state.observe(1, 10, true, true, true, false)
        assertNull(state.presentation(1))
    }

    @Test fun playingOldAAndChangingBrowseSourceDoesNotClearErrorForB() {
        val state = MeloPlayUiState { 1_000L }
        val b = state.begin(1, 20)!!
        state.fail(b, TelegramPlayFeedbackPolicy.failure(TelegramPlayFeedbackPolicy.TIMEOUT_MESSAGE, null))
        state.observe(1, 10, true, true, true, false)
        assertEquals(b, state.presentation(1)?.attempt)
        assertEquals(MeloPlayUiState.Phase.ERROR, state.presentation(1)?.phase)
        assertTrue(state.presentation(1)?.retryEnabled == true)
    }

    @Test fun oldPlayingItemWithSameKeyDoesNotEraseANewResolve() {
        val state = MeloPlayUiState { 1_000L }
        state.begin(1, 10)
        state.observe(1, 10, true, true, true, false)
        assertEquals(MeloPlayUiState.Phase.RESOLVING, state.presentation(1)?.phase)
    }

    @Test fun permissionsAndMissingMessageDoNotOfferRetryButTemporaryErrorsDo() {
        for (code in listOf(400, 401, 403, 404)) assertFalse(TelegramPlayFeedbackPolicy.failure("private", code).retryable)
        assertTrue(TelegramPlayFeedbackPolicy.failure("private", 500).retryable)
        assertTrue(TelegramPlayFeedbackPolicy.failure(TelegramPlayFeedbackPolicy.TIMEOUT_MESSAGE, null).retryable)
        assertTrue(TelegramPlayFeedbackPolicy.failure(TelegramPlayFeedbackPolicy.SEND_REQUEST_MESSAGE, null).retryable)
        assertFalse(TelegramPlayFeedbackPolicy.errorText(TelegramPlayFeedbackPolicy.SEND_REQUEST_MESSAGE, null).contains("PRIVATE"))
        assertFalse(TelegramPlayFeedbackPolicy.failure("not an audio file PRIVATE", null).retryable)
    }

    @Test fun staleOwnerAndEndedLifecycleCannotPublishOrRetry() {
        val state = MeloPlayUiState { 1_000L }
        val attempt = state.begin(1, 10)!!
        assertFalse(state.accepts(attempt, 2, true))
        assertFalse(state.accepts(attempt, 1, false))
        assertNull(state.presentation(2))
        state.clear()
        assertFalse(state.accepts(attempt, 1, true))
        assertNotEquals(attempt, state.begin(1, 10))
    }

    @Test fun rendererRecreationDoesNotResetMonotonicDeadline() {
        var now = 1_000L
        val state = MeloPlayUiState { now }
        val attempt = state.begin(1, 10)!!
        state.fail(attempt, TelegramPlayFeedbackPolicy.failure("FLOOD_WAIT_60", 429))
        repeat(3) { now += 5_000; assertEquals(60L - (it + 1) * 5L, state.presentation(1)?.remainingSeconds) }
        assertEquals(attempt, state.current)
    }

    @Test fun tickerHasOnePendingRefreshAndDetachOrHiddenWindowCancelsIt() {
        val pending = mutableSetOf<Runnable>()
        var refreshes = 0
        val ticker = MeloPlayUiState.CountdownTicker({ job, delay -> assertEquals(1000L, delay); pending.add(job) },
            { pending.remove(it) }, { refreshes++ })
        ticker.update(true, true, true, 60)
        ticker.update(true, true, true, 59)
        assertEquals(1, pending.size)
        pending.single().run(); assertEquals(1, refreshes)
        ticker.stop(); assertTrue(pending.isEmpty())
        ticker.update(false, true, true, 50); assertTrue(pending.isEmpty())
        ticker.update(true, false, true, 50); assertTrue(pending.isEmpty())
        ticker.update(true, true, false, 50); assertTrue(pending.isEmpty())
        ticker.update(true, true, true, 0); assertTrue(pending.isEmpty())
    }

    @Test fun stallFeedbackCanClearWhenActualPlaybackRecovers() {
        val state = MeloPlayUiState { 1_000L }
        val attempt = state.begin(1, 10)!!
        state.buffering(attempt)
        state.fail(attempt, TelegramPlayFeedbackPolicy.failure(TelegramPlayFeedbackPolicy.STALL_TEXT, null))
        state.observe(1, 10, true, true, true, false)
        assertNull(state.presentation(1))
    }
}
