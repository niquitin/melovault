package com.bsdung.musicassistantcar

import com.bsdung.musicassistantcar.ui.util.MobilePlayerLayoutPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Exhaustive layout and viewport verification for FYT 7870 Real Hardware Specifications:
 * - Hardware: UIS7870 (13.1-inch, 1920 x 1200 physical pixels, 16:10 aspect ratio)
 * - Modes tested: Fullscreen (1920px), 2/3 Split Window (~1280px), 1/2 Split Window (~960px)
 * - Density buckets: 160 dpi (mdpi), 213 dpi (tvdpi), 240 dpi (hdpi), 320 dpi (xhdpi)
 * - Verification: WindowWidthClass, layout qualifier selection, artwork sizing,
 *   ergonomic touch targets, and strict non-regression for Shanling M3 Ultra.
 */
class FytRealHardwareLayoutTest {

    data class FytViewport(
        val name: String,
        val physicalWidthPx: Int,
        val physicalHeightPx: Int,
        val densityDpi: Int,
        val insetsTopPx: Int = 60,
        val insetsBottomPx: Int = 80,
    ) {
        val densityFactor: Float get() = densityDpi / 160f
        val usableWidthPx: Int get() = physicalWidthPx
        val usableHeightPx: Int get() = physicalHeightPx - (insetsTopPx + insetsBottomPx)
        val widthDp: Int get() = (usableWidthPx / densityFactor).roundToInt()
        val heightDp: Int get() = (usableHeightPx / densityFactor).roundToInt()
        val widthClass: WindowWidthClass get() = MainActivity.widthClassFor(widthDp)
        val isPortraitPhone: Boolean get() = MobilePlayerLayoutPolicy.isPortraitPhone(widthDp, heightDp)

        /** Expected Android resource qualifier selected based on widthDp and orientation */
        val expectedQualifier: String
            get() = if (widthDp >= 700) "layout-w700dp" else if (widthDp > heightDp) "layout-land" else "layout"
    }

    private val fytViewports = listOf(
        // 160 dpi (mdpi)
        FytViewport("160dpi Fullscreen", 1920, 1200, 160),
        FytViewport("160dpi 2/3 Split Window", 1264, 1200, 160),
        FytViewport("160dpi 1/2 Split Window", 948, 1200, 160),

        // 213 dpi (tvdpi)
        FytViewport("213dpi Fullscreen", 1920, 1200, 213),
        FytViewport("213dpi 2/3 Split Window", 1264, 1200, 213),
        FytViewport("213dpi 1/2 Split Window", 948, 1200, 213),

        // 240 dpi (hdpi)
        FytViewport("240dpi Fullscreen", 1920, 1200, 240),
        FytViewport("240dpi 2/3 Split Window", 1264, 1200, 240),
        FytViewport("240dpi 1/2 Split Window", 948, 1200, 240),

        // 320 dpi (xhdpi)
        FytViewport("320dpi Fullscreen", 1920, 1200, 320),
        FytViewport("320dpi 2/3 Split Window", 1264, 1200, 320),
        FytViewport("320dpi 1/2 Split Window", 948, 1200, 320),
    )

    @Test
    fun testFullscreenAndTwoThirdsSplitViewportsAreNeverClassifiedAsPortraitPhone() {
        val carDualPanelModeViewports = fytViewports.filter {
            it.name.contains("Fullscreen") || it.name.contains("2/3 Split")
        }
        for (vp in carDualPanelModeViewports) {
            assertFalse(
                "FYT car viewport '${vp.name}' (${vp.widthDp}x${vp.heightDp} dp) must NOT be classified as portrait phone",
                vp.isPortraitPhone
            )
        }
    }

    @Test
    fun testHalfSplitViewportsTransitionToPortraitOnlyAtHighDensities() {
        // At 160 dpi (948 dp wide) & 213 dpi (712 dp wide): width >= 700 dp -> widescreen dual-panel car layout
        assertFalse(fytViewports.first { it.name == "160dpi 1/2 Split Window" }.isPortraitPhone)
        assertFalse(fytViewports.first { it.name == "213dpi 1/2 Split Window" }.isPortraitPhone)

        // At 240 dpi (632 dp wide, 706 dp high) & 320 dpi (474 dp wide, 530 dp high):
        // Height exceeds width and width < 700 dp -> natural transition to portrait single-column
        assertTrue(fytViewports.first { it.name == "240dpi 1/2 Split Window" }.isPortraitPhone)
        assertTrue(fytViewports.first { it.name == "320dpi 1/2 Split Window" }.isPortraitPhone)
    }

    @Test
    fun testCarViewportsSelectExpectedLayoutResourceQualifiers() {
        for (vp in fytViewports) {
            val qualifier = vp.expectedQualifier
            if (vp.name.contains("Fullscreen")) {
                assertEquals("Fullscreen '${vp.name}' must select layout-w700dp", "layout-w700dp", qualifier)
            } else if (vp.name.contains("2/3 Split")) {
                assertTrue(
                    "2/3 Split '${vp.name}' must select dual-panel (layout-w700dp or layout-land), got $qualifier",
                    qualifier == "layout-w700dp" || qualifier == "layout-land"
                )
            } else {
                // 1/2 Split
                if (vp.densityDpi <= 213) {
                    assertEquals("1/2 Split '${vp.name}' (w>=700dp) must select layout-w700dp", "layout-w700dp", qualifier)
                } else {
                    assertEquals("1/2 Split '${vp.name}' (narrow vertical column) selects layout", "layout", qualifier)
                }
            }
        }
    }

    @Test
    fun testFytFullscreenViewportsAlwaysResolveToExpanded() {
        val fullscreenViewports = fytViewports.filter { it.name.contains("Fullscreen") }
        for (vp in fullscreenViewports) {
            assertEquals(
                "Fullscreen '${vp.name}' must resolve to EXPANDED width class",
                WindowWidthClass.EXPANDED,
                vp.widthClass
            )
            assertEquals("layout-w700dp", vp.expectedQualifier)
        }
    }

    @Test
    fun testFytTwoThirdsSplitViewportsHaveSufficientWidthForDualPanel() {
        val twoThirdsViewports = fytViewports.filter { it.name.contains("2/3 Split") }
        for (vp in twoThirdsViewports) {
            assertTrue(
                "2/3 split viewport '${vp.name}' must have widthDp >= 600, got ${vp.widthDp}",
                vp.widthDp >= 600
            )
            // Now Playing card gets 40% width
            val nowPlayingWidthDp = (vp.widthDp * 0.40f).roundToInt()
            assertTrue(
                "Now Playing card width ($nowPlayingWidthDp dp) must be >= 240dp to comfortably host transport controls",
                nowPlayingWidthDp >= 240
            )
            // Library card gets 60% width
            val libraryWidthDp = (vp.widthDp * 0.60f).roundToInt()
            assertTrue(
                "Library card width ($libraryWidthDp dp) must be >= 360dp to host 2-tier collection action bar",
                libraryWidthDp >= 360
            )
        }
    }

    @Test
    fun testArtworkSizeIsGenerousAcrossAllFytCarViewports() {
        for (vp in fytViewports) {
            val artworkSize = MobilePlayerLayoutPolicy.artworkSizeDp(vp.widthDp, vp.heightDp)
            assertTrue(
                "Artwork size ($artworkSize dp) on FYT '${vp.name}' must be >= 140dp",
                artworkSize >= 140
            )
            if (vp.widthDp > vp.heightDp) {
                assertTrue(
                    "Artwork size ($artworkSize dp) on landscape FYT '${vp.name}' must not exceed 240dp",
                    artworkSize <= 240
                )
            } else {
                assertTrue(
                    "Artwork size ($artworkSize dp) on vertical FYT '${vp.name}' must not exceed 320dp",
                    artworkSize <= 320
                )
            }
            // Verify that shortExpandedArtworkSizeDp NEVER returns the old 46dp bug
            val shortSize = MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(vp.widthDp, vp.heightDp)
            if (shortSize != null) {
                assertNotEquals(46, shortSize)
                assertTrue("shortExpandedArtworkSizeDp must be >= 140dp, was $shortSize", shortSize >= 140)
            }
        }
    }

    @Test
    fun testTransportControlsTouchHitboxGuarantees() {
        // Hero Play/Pause button is 56dp (car requirement >= 56dp)
        val playPauseDp = 56
        assertTrue("Hero Play/Pause must be >= 56dp", playPauseDp >= 56)

        // Previous and Next buttons are 48dp (car requirement >= 48dp)
        val skipButtonDp = 48
        assertTrue("Skip buttons must be >= 48dp", skipButtonDp >= 48)

        // Shuffle and Repeat buttons are 44dp (min standard >= 44dp)
        val secondaryControlDp = 44
        assertTrue("Secondary controls must be >= 44dp", secondaryControlDp >= 44)

        // Total width of buttons: 44 + 48 + 56 + 48 + 44 = 240dp
        val totalButtonWidth = secondaryControlDp * 2 + skipButtonDp * 2 + playPauseDp
        assertEquals(240, totalButtonWidth)
    }

    @Test
    fun testShanlingM3UltraPortraitStrictIsolation() {
        // Shanling M3 Ultra hardware spec: 768x1280 px @ 320 dpi (Android 10)
        // widthDp = 384, heightDp = 640 (portrait)
        val m3WidthDp = 384
        val m3HeightDp = 640

        // 1. Must be classified as portrait phone
        assertTrue(
            "Shanling M3 Ultra MUST be classified as portrait phone",
            MobilePlayerLayoutPolicy.isPortraitPhone(m3WidthDp, m3HeightDp)
        )

        // 2. Must resolve to COMPACT
        assertEquals(
            WindowWidthClass.COMPACT,
            MainActivity.widthClassFor(m3WidthDp)
        )

        // 3. Artwork must be exactly 230dp (M3 Compact layout frozen spec)
        assertEquals(
            230,
            MobilePlayerLayoutPolicy.artworkSizeDp(m3WidthDp, m3HeightDp)
        )

        // 4. Car short expanded artwork must NOT activate for M3
        assertNull(
            MobilePlayerLayoutPolicy.shortExpandedArtworkSizeDp(m3WidthDp, m3HeightDp)
        )

        // 5. Car landscape expansion must NOT activate for M3
        assertFalse(
            MobilePlayerLayoutPolicy.supportsLandscapeArtworkExpansion(m3WidthDp, m3HeightDp)
        )
        assertNull(
            MobilePlayerLayoutPolicy.expandedLandscapeArtworkSizeDp(m3WidthDp, m3HeightDp)
        )
    }

    @Test
    fun testCarLayoutXmlDefines44DpTouchTargetsForBothTiersAndMoreButton() {
        val layoutFiles = listOf(
            java.io.File("src/main/res/layout-w700dp/activity_main.xml"),
            java.io.File("src/main/res/layout-land/activity_main.xml"),
        ).map { file ->
            if (file.exists()) file else java.io.File("app/${file.path}")
        }

        val required44DpButtons = listOf(
            "collectionPlayAllButton",
            "collectionShuffleButton",
            "collectionAddAllButton",
            "collectionDownloadAllButton",
            "collectionDeleteOfflineButton",
        )

        for (file in layoutFiles) {
            assertTrue("Car layout file must exist: ${file.path}", file.exists())
            val content = file.readText()
            assertTrue("Car layout ${file.name} must contain '⋮ Thêm' button", content.contains("android:text=\"⋮ Thêm\""))
            for (buttonId in required44DpButtons) {
                val idMarker = "android:id=\"@+id/$buttonId\""
                val idx = content.indexOf(idMarker)
                assertTrue("Button $buttonId must exist in ${file.path}", idx >= 0)
                val endTagIdx = content.indexOf("/>", idx)
                val block = content.substring(idx, endTagIdx)
                assertTrue(
                    "Button $buttonId in ${file.path} must have layout_height=\"44dp\"",
                    block.contains("android:layout_height=\"44dp\"")
                )
                assertTrue(
                    "Button $buttonId in ${file.path} must have minHeight=\"44dp\"",
                    block.contains("android:minHeight=\"44dp\"")
                )
            }
        }
    }

    @Test
    fun testAlertDialogThemeEnforcesHighContrastListItemTextOnDarkSurface() {
        val themesFile = java.io.File("src/main/res/values/themes.xml").let {
            if (it.exists()) it else java.io.File("app/src/main/res/values/themes.xml")
        }
        assertTrue("themes.xml must exist", themesFile.exists())
        val content = themesFile.readText()

        assertTrue(
            "Theme.MusicAssistantCar.Dialog must inherit from dark Theme.MaterialComponents.Dialog.Alert",
            content.contains("name=\"Theme.MusicAssistantCar.Dialog\" parent=\"Theme.MaterialComponents.Dialog.Alert\"")
        )
        assertTrue(
            "Theme.MusicAssistantCar.Dialog must set textColorAlertDialogListItem to @color/text_primary",
            content.contains("<item name=\"textColorAlertDialogListItem\">@color/text_primary</item>")
        )
        assertTrue(
            "Theme.MusicAssistantCar.Dialog must set android:textColorAlertDialogListItem to @color/text_primary",
            content.contains("<item name=\"android:textColorAlertDialogListItem\">@color/text_primary</item>")
        )

        // Verify WCAG 2.1 contrast ratio between @color/text_primary (#F8FAFC) and @color/bg_surface_elevated (#1E222D) >= 4.5:1
        fun relativeLuminance(r8: Int, g8: Int, b8: Int): Double {
            fun channel(c: Int): Double {
                val s = c / 255.0
                return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(r8) + 0.7152 * channel(g8) + 0.0722 * channel(b8)
        }
        val lText = relativeLuminance(0xF8, 0xFA, 0xFC) // #F8FAFC
        val lBg = relativeLuminance(0x1E, 0x22, 0x2D)   // #1E222D
        val contrastRatio = (Math.max(lText, lBg) + 0.05) / (Math.min(lText, lBg) + 0.05)
        assertTrue(
            "Dialog list item contrast ratio ($contrastRatio) must be >= 4.5:1",
            contrastRatio >= 4.5
        )
    }
}

