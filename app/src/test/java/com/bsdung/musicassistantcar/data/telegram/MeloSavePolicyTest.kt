package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloSavePolicy.Availability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E96 P4-01: the one-tap Lưu / Bỏ lưu button (what it may do, how it reads) and the guard that folds quick repeated taps. */
class MeloSavePolicyTest {
    private val self = 1001L
    private val chat = -1_002_000_000_001L
    private val raw = 4_194_304L * 7
    private val key = TelegramSongKey.forMessage(chat, raw, self)

    private fun availability(
        playing: Boolean = true, local: Boolean = false, songKey: Long? = key, known: Boolean = true,
        chatId: Long = chat, rawId: Long = raw, unreadable: Boolean = false, loaded: Boolean = true,
    ) = MeloSavePolicy.availability(playing, local, songKey, known, chatId, rawId, self, unreadable, loaded)

    // ---- availability ---------------------------------------------------------------------------------------

    @Test fun p01_aTelegramSongWhoseKeyAgreesWithItsChatAndMessageIsReady() {
        assertEquals(Availability.READY, availability())
    }

    @Test fun p02_aLocalSongIsNeverSavedAndNoTelegramIdentityIsInvented() {
        assertEquals(Availability.LOCAL_ONLY, availability(local = true))
        // Even when stray chat/message numbers are around, a local song stays local.
        assertEquals(Availability.LOCAL_ONLY, availability(local = true, chatId = chat, rawId = raw))
        val state = MeloSavePolicy.buttonState(Availability.LOCAL_ONLY, saved = false, busy = false)
        assertFalse(state.actionable)
        assertEquals("Chỉ lưu được bài Telegram", state.hint)
    }

    @Test fun p03_aRemoteSongWithoutChatOrMessageIsSavedOnThisDeviceOnlyAndWarned() {
        val noIds = availability(chatId = 0L, rawId = 0L)
        assertEquals(Availability.LEGACY_ONLY, noIds)
        val state = MeloSavePolicy.buttonState(noIds, saved = false, busy = false)
        assertTrue("legacy save is still a local save", state.actionable)
        assertEquals(MeloSavePolicy.HINT_LEGACY, state.hint)
    }

    @Test fun p04_aKeyThatDoesNotMatchItsClaimedChatAndMessageIsNotTrusted() {
        assertEquals(Availability.LEGACY_ONLY, availability(songKey = key + 1))
        assertFalse(MeloSavePolicy.isResolved(key, chat, raw, accountId = 0L))
        assertFalse(MeloSavePolicy.isResolved(key, chat, 0L, self))
        assertFalse(MeloSavePolicy.isResolved(key, 0L, raw, self))
        assertTrue(MeloSavePolicy.isResolved(key, chat, raw, self))
    }

    @Test fun p05_aSongInSavedMessagesKeepsItsMessageIdAsKeyAndIsReady() {
        // For the account's own Saved Messages chat the key IS the message id (older playlists).
        val saved = TelegramSongKey.forMessage(self, raw, self)
        assertEquals(raw, saved)
        assertEquals(Availability.READY, availability(songKey = saved, chatId = self, rawId = raw))
    }

    @Test fun p06_nothingPlayingUnknownSongAndPlaylistProblemsDisableTheButtonWithAnExactReason() {
        assertEquals(Availability.NOTHING_PLAYING, availability(playing = false))
        assertEquals(Availability.NOT_IDENTIFIED, availability(songKey = null))
        assertEquals(Availability.NOT_IDENTIFIED, availability(known = false))
        assertEquals(Availability.PLAYLIST_UNREADABLE, availability(unreadable = true))
        assertEquals(Availability.PLAYLIST_NOT_LOADED, availability(loaded = false))
        for (a in listOf(Availability.NOTHING_PLAYING, Availability.NOT_IDENTIFIED, Availability.PLAYLIST_UNREADABLE, Availability.PLAYLIST_NOT_LOADED)) {
            val s = MeloSavePolicy.buttonState(a, saved = false, busy = false)
            assertFalse(a.name, s.actionable)
            assertTrue(a.name, !s.hint.isNullOrBlank())
        }
    }

    @Test fun p07_anUnreadablePlaylistWinsOverAResolvedSong() {
        // Never offer a write the session will refuse.
        assertEquals(Availability.PLAYLIST_UNREADABLE, availability(unreadable = true, loaded = true))
    }

    // ---- the button -------------------------------------------------------------------------------------------

    @Test fun p08_theLabelFollowsWhatIsCommittedNotWhatWasTapped() {
        val unsaved = MeloSavePolicy.buttonState(Availability.READY, saved = false, busy = false)
        val saved = MeloSavePolicy.buttonState(Availability.READY, saved = true, busy = false)
        assertEquals("☆ Lưu", unsaved.label)
        assertEquals("★ Bỏ lưu", saved.label)
        // While a write runs the label is still the committed truth (no optimistic flip).
        val writing = MeloSavePolicy.buttonState(Availability.READY, saved = false, busy = true)
        assertEquals("☆ Lưu", writing.label)
        assertTrue(writing.busy)
        assertTrue(writing.contentDescription.contains("đang ghi"))
    }

    @Test fun p09_accessibilityTextNamesTheActionAndTheState() {
        val unsaved = MeloSavePolicy.buttonState(Availability.READY, saved = false, busy = false)
        val saved = MeloSavePolicy.buttonState(Availability.READY, saved = true, busy = false)
        assertTrue(unsaved.contentDescription.startsWith("Lưu bài đang phát"))
        assertTrue(saved.contentDescription.startsWith("Bỏ lưu bài đang phát"))
        assertEquals("Chưa lưu", unsaved.stateDescription)
        assertEquals("Đã lưu trên máy", saved.stateDescription)
        // "Đã lưu trên máy" never claims the cloud.
        assertFalse(saved.stateDescription.contains("đồng bộ", ignoreCase = true))
    }

    @Test fun p10_aLocalOrIdleSongNeverShowsAsSaved() {
        assertFalse(MeloSavePolicy.buttonState(Availability.LOCAL_ONLY, saved = true, busy = false).saved)
        assertFalse(MeloSavePolicy.buttonState(Availability.NOTHING_PLAYING, saved = true, busy = false).saved)
    }

    @Test fun p11_theChipLabelsOfTheOtherScreensUseTheSameWords() {
        assertEquals("☆ Lưu", MeloSavePolicy.chipLabel(false))
        assertEquals("★ Bỏ lưu", MeloSavePolicy.chipLabel(true))
    }

    // ---- quick taps ---------------------------------------------------------------------------------------------

    private fun t(n: Int) = PlaylistTarget(songKey = n.toLong(), chatId = chat, rawMessageId = raw + n)

    @Test fun p12_theFirstTapStartsAWriteAndTapsDuringItOnlyCount() {
        val g = SaveToggleGuard<PlaylistTarget>()
        assertFalse(g.busy)
        assertTrue(g.tap(t(1)))
        assertTrue(g.busy)
        assertFalse("a second tap must not start a second write", g.tap(t(1)))
    }

    @Test fun p13_aSecondTapDuringTheWriteBecomesExactlyOneMoreToggleAfterItNeverTwoAtOnce() {
        val g = SaveToggleGuard<PlaylistTarget>()
        assertTrue(g.tap(t(1)))
        assertFalse(g.tap(t(1)))
        // Save then Unsave was asked: the first write ends, then ONE more toggle restores the person's intent.
        assertEquals("one extra tap = one more toggle", t(1), g.finish(ok = true))
        assertTrue("the follow-up write is itself in flight", g.busy)
        assertNull(g.finish(ok = true))
        assertFalse(g.busy)
    }

    @Test fun p14_extraTapsThatComeInPairsCancelAndOddOnesWriteOnce() {
        val g = SaveToggleGuard<PlaylistTarget>()
        var writes = 0
        if (g.tap(t(1))) writes++
        g.tap(t(1)); g.tap(t(1)) // two extra taps cancel
        var next = g.finish(ok = true)
        assertNull("an even number of extra taps adds no write", next)
        assertEquals(1, writes)
        // Four taps in total (3 extra) = 1 + 1 more write.
        assertTrue(g.tap(t(1)))
        g.tap(t(1)); g.tap(t(1)); g.tap(t(1))
        next = g.finish(ok = true)
        assertEquals(t(1), next)
        assertNull(g.finish(ok = true))
    }

    @Test fun p15_aTapOnAnotherSongWhileWritingIsKeptAndWrittenAfterwardNeverLost() {
        val g = SaveToggleGuard<PlaylistTarget>()
        assertTrue(g.tap(t(1)))
        assertFalse(g.tap(t(2))) // the person skipped to the next track and saved it
        assertEquals(t(2), g.finish(ok = true))
        assertNull(g.finish(ok = true))
    }

    @Test fun p16_aFailedWriteDropsOnlyThatSongsExtraTapsAndKeepsOthers() {
        val g = SaveToggleGuard<PlaylistTarget>()
        assertTrue(g.tap(t(1)))
        g.tap(t(1)) // would have toggled back
        g.tap(t(2))
        assertEquals("song 1 failed: its repeat is not retried; song 2 still gets its write", t(2), g.finish(ok = false))
        assertNull(g.finish(ok = true))
    }

    @Test fun p17_resetForgetsEverythingSoAnotherAccountInheritsNoTaps() {
        val g = SaveToggleGuard<PlaylistTarget>()
        g.tap(t(1)); g.tap(t(2))
        g.reset()
        assertFalse(g.busy)
        assertNull(g.finish(ok = true))
        assertTrue(g.tap(t(3)))
    }

    @Test fun p18_finishWithoutAWriteIsHarmless() {
        val g = SaveToggleGuard<PlaylistTarget>()
        assertNull(g.finish(ok = true))
        assertNull(g.finish(ok = false))
        assertFalse(g.isBusyWith(t(1)))
    }

    @Test fun p19_theWriteTargetIsFixedAtTapTimeNotAtCompletionTime() {
        // The activity builds the PlaylistTarget when the person taps; a later track change cannot redirect the write.
        val g = SaveToggleGuard<PlaylistTarget>()
        val tapped = t(1)
        assertTrue(g.tap(tapped))
        assertTrue(g.isBusyWith(tapped))
        assertFalse(g.isBusyWith(t(2)))
    }
}
