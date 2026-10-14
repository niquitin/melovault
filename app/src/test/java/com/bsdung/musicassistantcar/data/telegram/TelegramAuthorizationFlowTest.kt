package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAuthorizationFlowTest {
    private val a = TelegramUserId(434345L)
    private val b = TelegramUserId(987654L)

    @Test fun appCredentialsAndPhoneAreNotProofOfAccountAuthorization() {
        val library = TelegramAccountLibrary()
        val flow = TelegramAuthorizationFlow(library)
        val epoch = flow.startClient()
        assertEquals(TelegramAuthStep.WAIT_PARAMETERS, flow.step)
        assertFalse(flow.isReady())
        assertFalse(flow.onGetMe(epoch, a))
        assertFalse(flow.onNativeState(epoch, TelegramAuthStep.AUTHORIZED))
        assertTrue(flow.onNativeState(epoch, TelegramAuthStep.WAIT_PHONE_NUMBER))
        assertTrue(flow.onNativeState(epoch, TelegramAuthStep.WAIT_CODE))
        assertTrue(flow.onNativeState(epoch, TelegramAuthStep.WAIT_PASSWORD))
        assertFalse(flow.isReady())
        assertTrue(library.currentItems().isEmpty())
        assertEquals(null, library.authorizedUser())
    }

    @Test fun readyStateRequiresAuthorizedGetMeForCurrentEpoch() {
        val library = TelegramAccountLibrary()
        val flow = TelegramAuthorizationFlow(library)
        val epoch = flow.startClient()
        assertTrue(flow.onNativeState(epoch, TelegramAuthStep.AUTHORIZED_AWAITING_GET_ME))
        assertFalse(flow.isReady())
        assertTrue(flow.onGetMe(epoch, a))
        assertTrue(flow.isReady())
        assertEquals(a, library.authorizedUser())
        assertFalse(flow.onNativeState(epoch, TelegramAuthStep.WAIT_PHONE_NUMBER))
        flow.logout()
        assertFalse(flow.isReady())
        assertEquals(null, library.authorizedUser())
    }

    @Test fun previousUserLoginCannotContaminateNewUserAfterAccountSwitch() {
        val library = TelegramAccountLibrary()
        val flow = TelegramAuthorizationFlow(library)
        val epochA = flow.startClient()
        flow.onNativeState(epochA, TelegramAuthStep.AUTHORIZED_AWAITING_GET_ME)
        assertTrue(flow.onGetMe(epochA, a))
        library.accept(TelegramAudioEntry(a, -100344L, 13L, "A only", "flac"))

        val epochB = flow.startClient()
        assertFalse(flow.isReady())
        assertEquals(null, library.authorizedUser())
        assertTrue(library.currentItems().isEmpty())
        assertFalse(flow.onNativeState(epochA, TelegramAuthStep.AUTHORIZED_AWAITING_GET_ME))
        assertFalse(flow.onGetMe(epochA, a))
        assertTrue(flow.onNativeState(epochB, TelegramAuthStep.AUTHORIZED_AWAITING_GET_ME))
        assertTrue(flow.onGetMe(epochB, b))
        assertTrue(flow.isReady())
        assertTrue(library.currentItems().isEmpty())
        library.accept(TelegramAudioEntry(b, -100344L, 13L, "B only", "flac"))
        assertEquals("B only", library.currentItems().single().title)
    }

    @Test fun closedNativeClientNeverBecomesReadyFromDelayedEvents() {
        val library = TelegramAccountLibrary()
        val flow = TelegramAuthorizationFlow(library)
        val epoch = flow.startClient()
        assertTrue(flow.onNativeState(epoch, TelegramAuthStep.WAIT_EMAIL_ADDRESS))
        assertTrue(flow.onNativeState(epoch, TelegramAuthStep.WAIT_EMAIL_CODE))
        assertTrue(flow.onNativeState(epoch, TelegramAuthStep.CLOSED))
        assertFalse(flow.onNativeState(epoch, TelegramAuthStep.AUTHORIZED_AWAITING_GET_ME))
        assertFalse(flow.onGetMe(epoch, a))
        assertFalse(flow.isReady())
    }

    @Test fun nativeFailureRevokesPreviousSessionAndRequiresNewClient() {
        val lib = TelegramAccountLibrary()
        val flow = TelegramAuthorizationFlow(lib)
        val epoch = flow.startClient()
        flow.onNativeState(epoch, TelegramAuthStep.AUTHORIZED_AWAITING_GET_ME)
        assertTrue(flow.onGetMe(epoch, a))
        assertTrue(flow.onNativeState(epoch, TelegramAuthStep.ERROR))
        assertEquals(null, lib.authorizedUser())
        assertFalse(flow.isReady())
        assertFalse(flow.onGetMe(epoch, a))
        assertTrue(flow.startClient() > epoch)
        assertEquals(TelegramAuthStep.WAIT_PARAMETERS, flow.step)
    }
}
