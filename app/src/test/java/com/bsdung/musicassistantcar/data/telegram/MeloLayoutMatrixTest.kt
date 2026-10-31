package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloDesignTokens.Profile
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.DisplaySpec
import com.bsdung.musicassistantcar.data.telegram.MeloScreenPolicy.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the layout the current rules pick for the device-size matrix of
 * docs/plans/2026-10-31-giao-dien-da-thiet-bi.md section 4 (M1-M9). Expected values are the
 * CURRENT behaviour; a changed threshold must change this table on purpose (GD1).
 */
class MeloLayoutMatrixTest {

    private class Row(
        val name: String,
        widthPx: Int,
        heightPx: Int,
        densityDpi: Int,
        val portraitLayout: Layout?,
        val portraitProfile: Profile?,
        val landscapeLayout: Layout?,
        val landscapeProfile: Profile?,
        val expectPanelCompact: Boolean,
        val landscapeOffersCar: Boolean
    ) {
        // Portrait spec; landscape is the same panel turned sideways.
        val portrait = DisplaySpec(widthPx, heightPx, densityDpi, densityDpi.toFloat(), densityDpi.toFloat())
        val landscape = DisplaySpec(heightPx, widthPx, densityDpi, densityDpi.toFloat(), densityDpi.toFloat())
    }

    private val rows = listOf(
        Row("M1 phone 720x1280/320", 720, 1280, 320, Layout.COMPACT, Profile.COMPACT, Layout.TWO_PANE, Profile.PHONE, true, false),
        Row("M2 phone 1080x2340/480", 1080, 2340, 480, Layout.PHONE_PORTRAIT, Profile.PHONE, Layout.TWO_PANE, Profile.PHONE, false, false),
        Row("M3 Sony 1080x2520/420", 1080, 2520, 420, Layout.PHONE_PORTRAIT, Profile.PHONE, Layout.TWO_PANE, Profile.PHONE, false, false),
        Row("M4 phone 1290x2796/480", 1290, 2796, 480, Layout.PHONE_PORTRAIT, Profile.PHONE, Layout.TWO_PANE, Profile.PHONE, false, false),
        Row("M5 foldable 1812x2176/420", 1812, 2176, 420, Layout.TABLET_PORTRAIT, Profile.TABLET, Layout.TABLET_PORTRAIT, Profile.TABLET, false, false),
        Row("M6 tablet 1200x1920/320", 1200, 1920, 320, Layout.TABLET_PORTRAIT, Profile.TABLET, Layout.TWO_PANE, Profile.TABLET, false, false),
        Row("M7 tablet 1600x2560/320", 1600, 2560, 320, Layout.TABLET_PORTRAIT, Profile.TABLET, Layout.TWO_PANE, Profile.TABLET, false, true),
        // M8 is a landscape-only 3840x2160 panel: its "portrait" spec is not asserted (null).
        Row("M8 large 2160x3840/320", 2160, 3840, 320, null, null, Layout.TWO_PANE, Profile.TABLET, false, true)
    )

    private fun panelCompact(s: DisplaySpec, multiWindow: Boolean = false): Boolean =
        MeloScreenPolicy.panelCompact(minOf(s.widthDp, s.heightDp), maxOf(s.widthDp, s.heightDp), multiWindow)

    private fun layoutOf(s: DisplaySpec): Layout =
        MeloScreenPolicy.chooseLayout(s.widthDp, s.heightDp, false, panelCompact(s))

    @Test
    fun dpSizesOfTheMatrixMatchThePlan() {
        fun dp(r: Row) = r.portrait.widthDp to r.portrait.heightDp
        assertEquals(360 to 640, dp(rows[0]))
        assertEquals(360 to 780, dp(rows[1]))
        assertEquals(411 to 960, dp(rows[2]))
        assertEquals(430 to 932, dp(rows[3]))
        assertEquals(690 to 828, dp(rows[4]))
        assertEquals(600 to 960, dp(rows[5]))
        assertEquals(800 to 1280, dp(rows[6]))
        assertEquals(1080 to 1920, dp(rows[7]))
        // M8 as specified is the sideways 1920 x 1080 dp panel.
        assertEquals(1920 to 1080, rows[7].landscape.widthDp to rows[7].landscape.heightDp)
    }

    @Test
    fun portraitLayoutAndProfileFollowTheMatrix() {
        for (r in rows) {
            val expected = r.portraitLayout ?: continue
            val layout = layoutOf(r.portrait)
            assertEquals(r.name + " portrait layout", expected, layout)
            assertEquals(
                r.name + " portrait profile",
                r.portraitProfile,
                MeloScreenPolicy.profileFor(layout, r.portrait.smallestWidthDp)
            )
        }
    }

    @Test
    fun landscapeLayoutAndProfileFollowTheMatrix() {
        for (r in rows) {
            val expected = r.landscapeLayout ?: continue
            val layout = layoutOf(r.landscape)
            assertEquals(r.name + " landscape layout", expected, layout)
            assertEquals(
                r.name + " landscape profile",
                r.landscapeProfile,
                MeloScreenPolicy.profileFor(layout, r.landscape.smallestWidthDp)
            )
        }
    }

    @Test
    fun onlyTheSmallPanelCountsAsCompactPanel() {
        for (r in rows) {
            assertEquals(r.name + " panelCompact", r.expectPanelCompact, panelCompact(r.portrait))
            // The same panel turned sideways is still the same panel.
            assertEquals(r.name + " panelCompact landscape", r.expectPanelCompact, panelCompact(r.landscape))
        }
    }

    @Test
    fun carModeIsOfferedOnlyOnWideLandscape() {
        for (r in rows) {
            val l = r.landscape
            assertEquals(
                r.name + " offer landscape",
                r.landscapeOffersCar,
                MeloScreenPolicy.shouldOfferCarMode(l.widthDp, l.heightDp, carMode = false, alreadyAsked = false)
            )
            val p = r.portrait
            assertFalse(
                r.name + " no offer in portrait",
                MeloScreenPolicy.shouldOfferCarMode(p.widthDp, p.heightDp, carMode = false, alreadyAsked = false)
            )
        }
        assertFalse(MeloScreenPolicy.shouldOfferCarMode(1280, 800, carMode = true, alreadyAsked = false))
        assertFalse(MeloScreenPolicy.shouldOfferCarMode(1280, 800, carMode = false, alreadyAsked = true))
        assertFalse(MeloScreenPolicy.shouldOfferCarMode(1199, 800, carMode = false, alreadyAsked = false))
        assertTrue(MeloScreenPolicy.shouldOfferCarMode(1200, 800, carMode = false, alreadyAsked = false))
    }

    @Test
    fun m9SonySplitScreenWindowIsCompactOnlyWhenLowEnough() {
        val sony = rows[2].portrait
        // In a split window the panel is not "compact" even on a small panel (multi-window net).
        assertFalse(panelCompact(sony, multiWindow = true))
        assertFalse(MeloScreenPolicy.panelCompact(360, 640, inMultiWindow = true))
        // Upper half of the Sony: about 411 x 470 dp -> Gọn through the split rule (height < 480).
        assertEquals(Layout.COMPACT, MeloScreenPolicy.chooseLayout(411, 470, false, panelCompact = false))
        assertEquals(Layout.COMPACT, MeloScreenPolicy.chooseLayout(411, 479, false, panelCompact = false))
        assertEquals(Layout.PHONE_PORTRAIT, MeloScreenPolicy.chooseLayout(411, 480, false, panelCompact = false))
        // Lower half is a bit taller (bottom bar area) and typically stays on the phone list.
        assertEquals(Layout.PHONE_PORTRAIT, MeloScreenPolicy.chooseLayout(411, 520, false, panelCompact = false))
    }

    @Test
    fun boundariesBetweenTheRowsStayWhereTheyAre() {
        // 599 dp wide is a phone; 600 is a tablet column unless landscape and low.
        assertEquals(Layout.PHONE_PORTRAIT, MeloScreenPolicy.chooseLayout(599, 960, false, false))
        assertEquals(Layout.TABLET_PORTRAIT, MeloScreenPolicy.chooseLayout(600, 960, false, false))
        // Landscape: 559 dp tall already gets two panes, 560 keeps the tablet list.
        assertEquals(Layout.TWO_PANE, MeloScreenPolicy.chooseLayout(800, 559, false, false))
        assertEquals(Layout.TABLET_PORTRAIT, MeloScreenPolicy.chooseLayout(800, 560, false, false))
        // 960 dp wide is always two panes.
        assertEquals(Layout.TABLET_PORTRAIT, MeloScreenPolicy.chooseLayout(959, 1200, false, false))
        assertEquals(Layout.TWO_PANE, MeloScreenPolicy.chooseLayout(960, 1200, false, false))
        // Car Mode wins over every size.
        for (r in rows) {
            assertEquals(Layout.CAR, MeloScreenPolicy.chooseLayout(r.portrait.widthDp, r.portrait.heightDp, true, panelCompact(r.portrait)))
            assertEquals(Profile.CAR, MeloScreenPolicy.profileFor(Layout.CAR, r.portrait.smallestWidthDp))
        }
    }
}
