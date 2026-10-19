package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloCompactPolicy.Mode
import com.bsdung.musicassistantcar.data.telegram.MeloLibraryPolicy.NewLayer
import com.bsdung.musicassistantcar.data.telegram.MeloSourceCardPolicy.Kind
import com.bsdung.musicassistantcar.data.telegram.MeloSourceCardPolicy.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloSourceCardPolicyTest {

    @Test
    fun telegramWithCountsShowsOneCard() {
        val cards = MeloSourceCardPolicy.cards(listOf(Source(Kind.TELEGRAM, 25_104, 57)), null)
        assertEquals(1, cards.size)
        assertEquals("Telegram", cards[0].title)
        assertEquals("25.104 bài · đã tải 57", cards[0].detail)
    }

    @Test
    fun unknownDownloadedOmitsThatPart() {
        val card = MeloSourceCardPolicy.cards(listOf(Source(Kind.TELEGRAM, 25_104, null)), null).single()
        assertEquals("25.104 bài", card.detail)
        assertFalse(card.detail.contains("đã tải"))
        assertFalse(card.detail.contains("?"))
    }

    @Test
    fun zeroDownloadedIsShownWhenKnown() {
        val card = MeloSourceCardPolicy.cards(listOf(Source(Kind.TELEGRAM, 25_104, 0)), null).single()
        assertEquals("25.104 bài · đã tải 0", card.detail)
    }

    @Test
    fun notConnectedOrZeroTotalHidesCard() {
        assertTrue(MeloSourceCardPolicy.cards(listOf(Source(Kind.TELEGRAM, null, null)), null).isEmpty())
        assertTrue(MeloSourceCardPolicy.cards(listOf(Source(Kind.TELEGRAM, 0, 0)), null).isEmpty())
        assertTrue(MeloSourceCardPolicy.cards(listOf(Source(Kind.TELEGRAM, -5, 3)), null).isEmpty())
        assertTrue(MeloSourceCardPolicy.cards(emptyList(), Kind.TELEGRAM).isEmpty())
    }

    @Test
    fun localAndDriveOnlyWhenTheyHaveSongs() {
        val onlyTelegram = MeloSourceCardPolicy.cards(
            listOf(Source(Kind.LOCAL, null, null), Source(Kind.DRIVE, 0, null), Source(Kind.TELEGRAM, 10, null)),
            null
        )
        assertEquals(listOf(Kind.TELEGRAM), onlyTelegram.map { it.kind })

        val two = MeloSourceCardPolicy.cards(
            listOf(Source(Kind.LOCAL, 120, null), Source(Kind.TELEGRAM, 10, null)),
            null
        )
        assertEquals(listOf(Kind.TELEGRAM, Kind.LOCAL), two.map { it.kind })
        assertEquals(listOf("Telegram", "Nhạc trên máy"), two.map { it.title })
    }

    @Test
    fun duplicateKindKeepsFirst() {
        val cards = MeloSourceCardPolicy.cards(
            listOf(Source(Kind.TELEGRAM, 10, 1), Source(Kind.TELEGRAM, 99, 9)),
            null
        )
        assertEquals(1, cards.size)
        assertEquals("10 bài · đã tải 1", cards[0].detail)
    }

    @Test
    fun selectionAndContentDescription() {
        val sources = listOf(Source(Kind.TELEGRAM, 25_104, 57))
        val chosen = MeloSourceCardPolicy.cards(sources, Kind.TELEGRAM).single()
        assertTrue(chosen.selected)
        assertEquals("Nguồn Telegram, 25.104 bài, đã tải 57, đang chọn", chosen.contentDescription)
        val idle = MeloSourceCardPolicy.cards(sources, null).single()
        assertFalse(idle.selected)
        assertEquals("Nguồn Telegram, 25.104 bài, đã tải 57", idle.contentDescription)
    }

    @Test
    fun selectedKindFollowsChannel() {
        assertEquals(Kind.TELEGRAM, MeloSourceCardPolicy.selectedKind(null, false))
        assertNull(MeloSourceCardPolicy.selectedKind(123L, false))
        assertNull(MeloSourceCardPolicy.selectedKind(null, true))
    }

    @Test
    fun selectedKindIsLocalWhileLocalListIsOpen() {
        assertEquals(Kind.LOCAL, MeloSourceCardPolicy.selectedKind(null, false, localOpen = true))
        assertEquals(Kind.LOCAL, MeloSourceCardPolicy.selectedKind(5L, false, localOpen = true))
        assertEquals(Kind.LOCAL, MeloSourceCardPolicy.selectedKind(null, true, localOpen = true))
        // Default (localOpen = false) keeps the Telegram rules.
        assertEquals(Kind.TELEGRAM, MeloSourceCardPolicy.selectedKind(null, false, localOpen = false))
        assertNull(MeloSourceCardPolicy.selectedKind(5L, false, localOpen = false))
    }

    @Test
    fun twoCardsOnNarrowPhoneShowHalfOfTheSecond() {
        val width = MeloSourceCardPolicy.cardWidthPx(328, 2, 200, 8)
        assertEquals(213, width)
        val peek = 328 - width - 8
        assertTrue("peek=$peek", kotlin.math.abs(peek - width / 2) <= 2)
    }

    @Test
    fun rowVisibleOnlyOnPhoneTabletLibrary() {
        for (layer in listOf(NewLayer.PHONE_PORTRAIT, NewLayer.TABLET_PORTRAIT)) {
            assertTrue(MeloSourceCardPolicy.rowVisible(layer, false, Mode.ALL, 800, 1))
            assertTrue(MeloSourceCardPolicy.rowVisible(layer, false, Mode.DOWNLOADED, 800, 1))
            assertFalse(MeloSourceCardPolicy.rowVisible(layer, false, Mode.PLAYLIST, 800, 1))
            assertFalse(MeloSourceCardPolicy.rowVisible(layer, true, Mode.ALL, 800, 1))
            assertFalse(MeloSourceCardPolicy.rowVisible(layer, false, Mode.ALL, 800, 0))
        }
    }

    @Test
    fun phoneRowHidesInLowWindowsButTabletRowStays() {
        // FYT 1/3 and 1/2 are 642dp tall: no 88dp row there, the list gets the room.
        assertFalse(MeloSourceCardPolicy.rowVisible(NewLayer.PHONE_PORTRAIT, false, Mode.ALL, 642, 1))
        assertFalse(MeloSourceCardPolicy.rowVisible(NewLayer.PHONE_PORTRAIT, false, Mode.ALL, 699, 1))
        assertTrue(MeloSourceCardPolicy.rowVisible(NewLayer.PHONE_PORTRAIT, false, Mode.ALL, 700, 1))
        // Sony portrait (~840dp) and S25 (832dp) keep the cards.
        assertTrue(MeloSourceCardPolicy.rowVisible(NewLayer.PHONE_PORTRAIT, false, Mode.ALL, 840, 1))
        assertTrue(MeloSourceCardPolicy.rowVisible(NewLayer.PHONE_PORTRAIT, false, Mode.ALL, 832, 1))
        // FYT 2/3 (782 × 642dp, list + bottom bar) keeps the Telegram card for "Về tất cả".
        assertTrue(MeloSourceCardPolicy.rowVisible(NewLayer.TABLET_PORTRAIT, false, Mode.ALL, 642, 1))
    }

    @Test
    fun twoPaneNeedsTallWindow() {
        assertFalse(MeloSourceCardPolicy.rowVisible(NewLayer.TWO_PANE, false, Mode.ALL, 360, 1))
        assertFalse(MeloSourceCardPolicy.rowVisible(NewLayer.TWO_PANE, false, Mode.ALL, 384, 1))
        assertTrue(MeloSourceCardPolicy.rowVisible(NewLayer.TWO_PANE, false, Mode.ALL, 560, 1))
        assertTrue(MeloSourceCardPolicy.rowVisible(NewLayer.TWO_PANE, false, Mode.DOWNLOADED, 800, 1))
        assertFalse(MeloSourceCardPolicy.rowVisible(NewLayer.TWO_PANE, true, Mode.ALL, 800, 1))
    }

    @Test
    fun cardWidthFillsOrShowsHalfOfLast() {
        assertEquals(1080, MeloSourceCardPolicy.cardWidthPx(1080, 1, 525, 30))
        assertEquals(525, MeloSourceCardPolicy.cardWidthPx(1080, 2, 525, 30))
        val three = MeloSourceCardPolicy.cardWidthPx(1080, 3, 600, 30)
        assertTrue(three >= 600)
        val peek = 1080 - three - 30
        assertTrue("peek=$peek width=$three", kotlin.math.abs(peek - three / 2) <= 2)
        assertEquals(0, MeloSourceCardPolicy.cardWidthPx(1080, 0, 600, 30))
    }
}
