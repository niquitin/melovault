package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloSavePolicy.Availability
import org.junit.Assert.*
import org.junit.Test

class MeloRowSavePolicyTest {
    private val owner = 123L
    private val chat = -456L
    private val raw = 88L
    private val key = TelegramSongKey.forMessage(chat, raw, owner)

    @Test fun unplayedRowUsesItsGenuineIdentityRatherThanCurrentTrackOrPosition() {
        val availability = MeloSavePolicy.rowAvailability(false, key, true, chat, raw, owner, false, true)
        assertEquals(Availability.READY, availability)
        assertEquals(PlaylistTarget(key, chat, raw), MeloSavePolicy.rowTarget(owner, owner, key, chat, raw, availability))
        assertNull(MeloSavePolicy.rowTarget(owner, owner + 1, key, chat, raw, availability))
        assertNull(MeloSavePolicy.rowTarget(owner, owner, key, chat, raw + 1, availability))
    }

    @Test fun offlineGroupSaveNamesOnlyTheDisplayedRepresentative() {
        val otherRaw = raw + 1
        val other = TelegramSongKey.forMessage(chat, otherRaw, owner)
        val copies = listOf(key, other)
        val target = MeloSavePolicy.rowTarget(owner, owner, other, chat, otherRaw, Availability.READY)!!
        assertEquals(other, target.songKey)
        assertEquals(otherRaw, target.rawMessageId)
        assertEquals(listOf(key, other), copies)
    }

    @Test fun localPlaceholderAndUnresolvedRowsDoNotInventSaveTargets() {
        assertEquals(Availability.LOCAL_ONLY, MeloSavePolicy.rowAvailability(true, key, true, chat, raw, owner, false, true))
        assertEquals(Availability.NOT_IDENTIFIED, MeloSavePolicy.rowAvailability(false, key, false, chat, raw, owner, false, true))
        assertEquals(Availability.NOT_IDENTIFIED, MeloSavePolicy.rowAvailability(false, key, true, 0, 0, owner, false, true))
        assertNull(MeloSavePolicy.rowTarget(owner, owner, key, chat, raw, Availability.LOCAL_ONLY))
    }

    @Test fun rowWordsNameTheRowAndNeverClaimItIsPlaying() {
        val state = MeloSavePolicy.rowButtonState("Bài A", Availability.READY, false, false)
        assertEquals("Lưu Bài A vào Yêu thích", state.contentDescription)
        assertEquals("Bỏ lưu Bài A khỏi Yêu thích", MeloSavePolicy.rowButtonState("Bài A", Availability.READY, true, false).contentDescription)
        assertFalse(MeloSavePolicy.rowButtonState("A", Availability.NOT_IDENTIFIED, false, false).contentDescription.contains("đang phát"))
        assertTrue(MeloSavePolicy.buttonState(Availability.READY, false, false).contentDescription.contains("đang phát"))
    }

    @Test fun busyAndFailureDoNotOptimisticallyChangeCommittedStar() {
        assertFalse(MeloSavePolicy.rowButtonState("A", Availability.READY, false, true).saved)
        assertFalse(MeloSavePolicy.rowButtonState("A", Availability.PLAYLIST_UNREADABLE, false, false).actionable)
        assertFalse(MeloSavePolicy.rowButtonState("A", Availability.PLAYLIST_NOT_LOADED, false, false).saved)
        assertTrue(MeloSavePolicy.rowButtonState("A", Availability.READY, true, false).saved)
    }

    @Test fun queuedOtherTargetIsBusyWithoutStartingASecondWriter() {
        val a = PlaylistTarget(key, chat, raw)
        val b = PlaylistTarget(TelegramSongKey.forMessage(chat, raw + 1, owner), chat, raw + 1)
        val guard = SaveToggleGuard<PlaylistTarget>()
        assertTrue(guard.tap(a)); assertFalse(guard.tap(b))
        assertTrue(guard.isPendingWith(a)); assertTrue(guard.isPendingWith(b))
        assertFalse(guard.isBusyWith(b))
        assertEquals(b, guard.finish(false))
        assertFalse(guard.isPendingWith(a)); assertTrue(guard.isBusyWith(b))
    }

    @Test fun cancelledQueuedParityIsNotShownAsPending() {
        val guard = SaveToggleGuard<Long>()
        guard.tap(1); guard.tap(2); guard.tap(2)
        assertFalse(guard.isPendingWith(2))
        assertNull(guard.finish(true))
    }

    @Test fun staleGuardOwnerOrLifecycleCannotPublishCommittedState() {
        assertTrue(MeloSavePolicy.acceptsCallback(owner, owner, true, true))
        assertFalse(MeloSavePolicy.acceptsCallback(owner, owner, false, true))
        assertFalse(MeloSavePolicy.acceptsCallback(owner, owner + 1, true, true))
        assertFalse(MeloSavePolicy.acceptsCallback(owner, owner, true, false))
    }

    @Test fun directActionKeeps160DpBodyAndTwo48DpTargetsAtBoundary() {
        // 48 art + 24 gaps + 32 measured duration + 96 actions + 160 body = 360.
        assertTrue(MeloSavePolicy.directRowSave(360, 48, 24, 32, 48, 1f, 1.3f))
        assertFalse(MeloSavePolicy.directRowSave(359, 48, 24, 32, 48, 1f, 1.3f))
        assertFalse(MeloSavePolicy.directRowSave(360, 48, 24, 32, 48, 1f, 1.31f))
        assertFalse(MeloSavePolicy.directRowSave(360, 48, 24, 80, 48, 1f, 1f))
        assertFalse(MeloSavePolicy.directRowSave(0, 48, 24, 32, 48, 1f, 1f))
        assertFalse(MeloSavePolicy.directRowSave(720, 96, 48, 64, 48, 2f, 1f))
        assertTrue(MeloSavePolicy.directRowSave(720, 96, 48, 64, 96, 2f, 1f))
    }
}
