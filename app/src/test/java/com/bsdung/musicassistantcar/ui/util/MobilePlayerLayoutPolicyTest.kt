package com.bsdung.musicassistantcar.ui.util

import org.junit.Assert.*
import org.junit.Test

class MobilePlayerLayoutPolicyTest {

    @Test
    fun isPortraitPhone_360x780_returnsTrue() {
        assertTrue(MobilePlayerLayoutPolicy.isPortraitPhone(360, 780))
    }

    @Test
    fun isPortraitPhone_393x851_returnsTrue() {
        assertTrue(MobilePlayerLayoutPolicy.isPortraitPhone(393, 851))
    }

    @Test
    fun isPortraitPhone_320x520_returnsTrue() {
        assertTrue(MobilePlayerLayoutPolicy.isPortraitPhone(320, 520))
    }

    @Test
    fun isPortraitPhone_617x1097_returnsTrue() {
        assertTrue(MobilePlayerLayoutPolicy.isPortraitPhone(617, 1097))
    }

    @Test
    fun isPortraitPhone_landscapeAndNegative_returnsFalse() {
        assertFalse(MobilePlayerLayoutPolicy.isPortraitPhone(400, 300))
        assertFalse(MobilePlayerLayoutPolicy.isPortraitPhone(700, 480))
        assertFalse(MobilePlayerLayoutPolicy.isPortraitPhone(-360, 780))
        assertFalse(MobilePlayerLayoutPolicy.isPortraitPhone(360, -780))
        assertFalse(MobilePlayerLayoutPolicy.isPortraitPhone(-1, -1))
    }

    @Test
    fun artworkSizeDp_360x780_isGreaterThan200AndWithinWidthLimit() {
        val size = MobilePlayerLayoutPolicy.artworkSizeDp(360, 780)
        assertTrue("Artwork size should be > 200", size > 200)
        assertTrue("Artwork size should be <= width - 32", size <= 360 - 32)
    }

    @Test
    fun artworkSizeDp_320x520Bounded_andZeroForInvalidDimensions() {
        val size = MobilePlayerLayoutPolicy.artworkSizeDp(320, 520)
        assertTrue("Artwork size should be bounded (> 0)", size > 0)
        assertTrue("Artwork size should be bounded (<= width - 32)", size <= 320 - 32)
        assertEquals(0, MobilePlayerLayoutPolicy.artworkSizeDp(0, 0))
        assertEquals(0, MobilePlayerLayoutPolicy.artworkSizeDp(-320, 520))
        assertEquals(0, MobilePlayerLayoutPolicy.artworkSizeDp(320, -520))
    }

    @Test
    fun shortExpandedArtwork_710x377_staysFullyVisibleInCarPanel() {
        assertEquals(46, MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(710, 377))
        assertEquals(46, MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(912, 381))
    }

    @Test
    fun shortExpandedArtwork_preservesPortraitAndTallerScreens() {
        assertNull(MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(411, 881))
        assertNull(MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(710, 600))
        assertNull(MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(699, 377))
        assertNull(MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(710, 0))
        assertNull(MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(-1, 377))
    }

    @Test
    fun playerVisibility_portraitBrowsing_showsMiniAndReversesWhenFullOpened() {
        assertTrue(
            MobilePlayerLayoutPolicy.shouldShowMiniPlayer(
                isPortraitPhone = true,
                isBrowsing = true,
                userOpenedFullPlayer = false
            )
        )
        assertFalse(
            MobilePlayerLayoutPolicy.shouldShowFullPlayer(
                isPortraitPhone = true,
                isBrowsing = true,
                userOpenedFullPlayer = false
            )
        )

        assertFalse(
            MobilePlayerLayoutPolicy.shouldShowMiniPlayer(
                isPortraitPhone = true,
                isBrowsing = true,
                userOpenedFullPlayer = true
            )
        )
        assertTrue(
            MobilePlayerLayoutPolicy.shouldShowFullPlayer(
                isPortraitPhone = true,
                isBrowsing = true,
                userOpenedFullPlayer = true
            )
        )
    }

    @Test
    fun queuePanel_portraitKeepsMiniEvenAfterFullPlayerOpened() {
        assertTrue(MobilePlayerLayoutPolicy.shouldShowMiniPlayerForPanel(
            isPortraitPhone = true, isBrowsing = false, userOpenedFullPlayer = true,
            isQueueVisible = true, isLyricsVisible = false,
        ))
        assertTrue(MobilePlayerLayoutPolicy.shouldShowMiniPlayerForPanel(
            isPortraitPhone = true, isBrowsing = false, userOpenedFullPlayer = true,
            isQueueVisible = false, isLyricsVisible = true,
        ))
        assertFalse(MobilePlayerLayoutPolicy.shouldShowMiniPlayerForPanel(
            isPortraitPhone = true, isBrowsing = false, userOpenedFullPlayer = true,
            isQueueVisible = false, isLyricsVisible = false,
        ))
    }

    @Test
    fun carQueuePanel_keepsFullPlayerWithoutForcingMini() {
        assertFalse(MobilePlayerLayoutPolicy.shouldShowMiniPlayerForPanel(
            isPortraitPhone = false, isBrowsing = true, userOpenedFullPlayer = false,
            isQueueVisible = true, isLyricsVisible = false,
        ))
    }

    @Test
    fun playerVisibility_carLandscape_showsFullPlayer() {
        assertTrue(
            MobilePlayerLayoutPolicy.shouldShowFullPlayer(
                isPortraitPhone = false,
                isBrowsing = true,
                userOpenedFullPlayer = false
            )
        )
        assertTrue(
            MobilePlayerLayoutPolicy.shouldShowFullPlayer(
                isPortraitPhone = false,
                isBrowsing = false,
                userOpenedFullPlayer = false
            )
        )
        assertFalse(
            MobilePlayerLayoutPolicy.shouldShowMiniPlayer(
                isPortraitPhone = false,
                isBrowsing = true,
                userOpenedFullPlayer = false
            )
        )
    }
}
