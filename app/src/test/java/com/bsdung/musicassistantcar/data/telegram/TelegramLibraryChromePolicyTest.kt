package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramLibraryChromePolicyTest {
    @Test fun swipeUpHidesNavigationToExpandSongList() {
        assertTrue(TelegramLibraryChromePolicy.collapsedAfter(
            false, -42f, 36f, true, 500))
    }
    @Test fun pullDownRevealsNavigationFromAnyScrollPosition() {
        assertFalse(TelegramLibraryChromePolicy.collapsedAfter(
            true, 50f, 36f, true, 500))
    }
    @Test fun smallScrollDoesNotJitter() {
        assertTrue(TelegramLibraryChromePolicy.collapsedAfter(
            true, -6f, 36f, true, 200))
        assertFalse(TelegramLibraryChromePolicy.collapsedAfter(
            false, 6f, 36f, true, 200))
    }
    @Test fun authenticationMustKeepNavigationVisible() {
        assertFalse(TelegramLibraryChromePolicy.collapsedAfter(
            true, -500f, 36f, false, 400))
    }
    @Test fun fewItemsCannotHideMenu() {
        assertFalse(TelegramLibraryChromePolicy.collapsedAfter(
            true, -500f, 36f, true, 1))
    }
}
