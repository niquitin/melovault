package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramSessionEpochTest {
    @Test fun staleCallbackFromOldClientCannotSwitchIdentity() {
        val library = TelegramAccountLibrary()
        val coordinator = TelegramSessionEpoch(library)
        val old = coordinator.beginNewClient()
        assertTrue(coordinator.onAuthenticatedGetMe(old, TelegramUserId(123L)))
        val newer = coordinator.beginNewClient()
        assertFalse(coordinator.isReady(newer))
        assertFalse(coordinator.onAuthenticatedGetMe(old, TelegramUserId(123L)))
        assertFalse(coordinator.closeClient(old))
        assertEquals(null, library.authorizedUser())
        assertTrue(coordinator.onAuthenticatedGetMe(newer, TelegramUserId(456L)))
        assertTrue(coordinator.isReady(newer))
        assertEquals(TelegramUserId(456L), library.authorizedUser())
    }

    @Test fun logoutRevokesActiveAccountAndRepeatedOldCallbacks() {
        val lib = TelegramAccountLibrary()
        val epoch = TelegramSessionEpoch(lib)
        val started = epoch.beginNewClient()
        assertTrue(epoch.onAuthenticatedGetMe(started, TelegramUserId(100L)))
        assertTrue(epoch.closeClient(started))
        assertFalse(epoch.isReady(started))
        assertFalse(epoch.closeClient(started))
        assertFalse(epoch.onAuthenticatedGetMe(started, TelegramUserId(100L)))
        assertTrue(lib.currentItems().isEmpty())
        assertEquals(null, lib.authorizedUser())
    }
}
