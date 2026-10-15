package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBackgroundPrefetchPolicyTest {
    @Test fun retriesOnlyOnceForSameAccountAndNeighbor() {
        assertTrue(TelegramBackgroundPrefetchPolicy.canRetry(1, 7, 7, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.canRetry(2, 7, 7, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.canRetry(0, 7, 7, 12, 12))
    }

    @Test fun neverRetriesAfterAccountChangeOrLogout() {
        assertFalse(TelegramBackgroundPrefetchPolicy.canRetry(1, 7, 8, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.canRetry(1, 7, null, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.canRetry(1, null, 7, 12, 12))
    }

    @Test fun neverRetriesOnStaleNextTrack() {
        assertFalse(TelegramBackgroundPrefetchPolicy.canRetry(1, 7, 7, 12, 13))
        assertFalse(TelegramBackgroundPrefetchPolicy.canRetry(1, 7, 7, 12, null))
    }

    @Test fun acceptsOnlyCurrentVerifiedFileAndTrack() {
        assertTrue(TelegramBackgroundPrefetchPolicy.isCurrentResult(
            22, 22, 7, 7, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.isCurrentResult(
            22, 23, 7, 7, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.isCurrentResult(
            22, 22, 7, 8, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.isCurrentResult(
            22, 22, 7, 7, 12, 13))
    }

    @Test fun deniesMissingIdentityAndInvalidIds() {
        assertFalse(TelegramBackgroundPrefetchPolicy.isCurrentResult(
            null, 22, 7, 7, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.isCurrentResult(
            22, 22, null, 7, 12, 12))
        assertFalse(TelegramBackgroundPrefetchPolicy.isCurrentResult(
            22, 22, 7, 7, null, 12))
    }
}
