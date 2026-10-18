package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloMediaControlPolicyTest {

    @Test
    fun notificationPermissionAskedOnceOnAndroid13PlusAfterLogin() {
        assertTrue(MeloMediaControlPolicy.shouldAskNotificationPermission(sdk = 37, granted = false, alreadyAsked = false, authorized = true))
        assertTrue(MeloMediaControlPolicy.shouldAskNotificationPermission(33, false, false, true))
        assertFalse(MeloMediaControlPolicy.shouldAskNotificationPermission(32, false, false, true)) // Sony, Android 12
        assertFalse(MeloMediaControlPolicy.shouldAskNotificationPermission(37, true, false, true))
        assertFalse(MeloMediaControlPolicy.shouldAskNotificationPermission(37, false, true, true))
        assertFalse(MeloMediaControlPolicy.shouldAskNotificationPermission(37, false, false, false))
    }

    @Test
    fun nextStaysAvailableWhenTheNextSongIsOnlyQueued() {
        assertTrue(MeloMediaControlPolicy.nextAvailable(loadedHasNext = false, queueHasNextNotLoaded = true))
        assertTrue(MeloMediaControlPolicy.nextAvailable(true, false))
        assertFalse(MeloMediaControlPolicy.nextAvailable(false, false))
    }

    @Test
    fun serviceContinuesAfterEndOnlyWhenNoScreenAndNotStopping() {
        assertTrue(MeloMediaControlPolicy.shouldContinueAfterEnd(uiAttached = false, stopAtEndOfTrack = false, queueHasNextNotLoaded = true))
        assertFalse(MeloMediaControlPolicy.shouldContinueAfterEnd(true, false, true))   // the screen continues by itself
        assertFalse(MeloMediaControlPolicy.shouldContinueAfterEnd(false, true, true))   // "Dừng hết bài"
        assertFalse(MeloMediaControlPolicy.shouldContinueAfterEnd(false, false, false)) // end of the queue
    }

    @Test
    fun userPressOverridesPrefetchSettingAndScreenState() {
        assertTrue(MeloMediaControlPolicy.mayDownloadNext(userRequestedThis = true, prefetchEnabled = false, uiAttached = true))
        assertTrue(MeloMediaControlPolicy.mayDownloadNext(false, true, false))
        assertFalse(MeloMediaControlPolicy.mayDownloadNext(false, false, false)) // prefetch switch off
        assertFalse(MeloMediaControlPolicy.mayDownloadNext(false, true, true))   // the screen handles prefetch
    }

    @Test
    fun fallbackArtistIsTheTrimmedChannelNameOrNull() {
        assertEquals("NHÓM NHẠC LAK EMI", MeloMediaControlPolicy.fallbackArtist("  NHÓM NHẠC LAK EMI "))
        assertNull(MeloMediaControlPolicy.fallbackArtist("   "))
        assertNull(MeloMediaControlPolicy.fallbackArtist(null))
    }
}
