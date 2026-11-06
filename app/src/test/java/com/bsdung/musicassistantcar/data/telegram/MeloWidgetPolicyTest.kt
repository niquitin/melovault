package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloWidgetPolicy.Action
import com.bsdung.musicassistantcar.data.telegram.MeloWidgetPolicy.Center
import com.bsdung.musicassistantcar.data.telegram.MeloWidgetPolicy.Input
import com.bsdung.musicassistantcar.data.telegram.MeloWidgetPolicy.Playback
import org.junit.Assert.*
import org.junit.Test

class MeloWidgetPolicyTest {
    private val ready = Input(hasItem = true, title = "Bài A", performer = "Ca sĩ A",
        playback = Playback.READY, canPlayPause = true, canSeekStart = true,
        hasPrevious = true, hasNext = true, canPrevious = true, canNext = true)

    @Test fun unavailableStatesErasePreviousMetadataAndControls() {
        for (input in listOf(Input(), ready.copy(hasItem = false), ready.copy(ownerValid = false),
                ready.copy(hasError = true), ready.copy(playback = Playback.IDLE))) {
            val view = MeloWidgetPolicy.view(input)
            assertEquals("MeloVault", view.title)
            assertEquals("Mở ứng dụng để chọn bài", view.performer)
            assertEquals(Center.NONE, view.center)
            assertFalse(view.previous)
            assertFalse(view.next)
            assertEquals(MeloWidgetPolicy.Open.LIBRARY, view.open)
            Action.entries.forEach { assertFalse(MeloWidgetPolicy.allows(it, input)) }
        }
    }

    @Test fun readyAndBufferingFollowIntentToPlay() {
        for (playback in listOf(Playback.READY, Playback.BUFFERING)) {
            for (playing in listOf(true, false)) {
                val input = ready.copy(playback = playback, playWhenReady = playing)
                val view = MeloWidgetPolicy.view(input)
                assertEquals(if (playing) Center.PAUSE else Center.PLAY, view.center)
                assertEquals("Bài A", view.title)
                assertEquals("Ca sĩ A", view.performer)
                assertEquals(MeloWidgetPolicy.Open.PLAYER, view.open)
            }
        }
    }

    @Test fun endedOnlyReplaysWhenSeekingAndPlayAreAvailable() {
        val ended = ready.copy(playback = Playback.ENDED, playWhenReady = true)
        assertEquals(Center.REPLAY, MeloWidgetPolicy.view(ended).center)
        assertTrue(MeloWidgetPolicy.allows(Action.PLAY, ended))
        for (blocked in listOf(ended.copy(canSeekStart = false), ended.copy(canPlayPause = false))) {
            assertEquals(Center.NONE, MeloWidgetPolicy.view(blocked).center)
            assertFalse(MeloWidgetPolicy.allows(Action.PLAY, blocked))
        }
    }

    @Test fun edgeButtonsNeedBothExistenceAndWrapperCommand() {
        for (has in listOf(true, false)) for (command in listOf(true, false)) {
            val view = MeloWidgetPolicy.view(ready.copy(hasPrevious = has, hasNext = has,
                canPrevious = command, canNext = command))
            assertEquals(has && command, view.previous)
            assertEquals(has && command, view.next)
        }
        // NextAwarePlayer can offer Next while ExoPlayer has no following timeline item.
        assertTrue(MeloWidgetPolicy.view(ready.copy(hasPrevious = false)).next)
    }

    @Test fun metadataUsesOnlyExplicitTrustedPerformer() {
        for (raw in listOf(null, "", "   ", "<unknown>", "UNKNOWN", "Nghệ sĩ không rõ")) {
            assertEquals("Chưa rõ ca sĩ", MeloWidgetPolicy.view(ready.copy(performer = raw)).performer)
        }
        assertEquals("Ca sĩ B", MeloWidgetPolicy.view(ready.copy(performer = " Ca sĩ B ")).performer)
        val longTitle = "Nhạc ".repeat(100)
        assertEquals(longTitle.trim(), MeloWidgetPolicy.view(ready.copy(title = longTitle)).title)
        assertEquals("Bài hát chưa có tên", MeloWidgetPolicy.view(ready.copy(title = " ")).title)
    }

    @Test fun delayedPauseAndPlayAreIdempotent() {
        assertTrue(MeloWidgetPolicy.allows(Action.PAUSE, ready.copy(playWhenReady = true)))
        assertFalse(MeloWidgetPolicy.allows(Action.PAUSE, ready))
        assertTrue(MeloWidgetPolicy.allows(Action.PLAY, ready))
        assertFalse(MeloWidgetPolicy.allows(Action.PLAY, ready.copy(playWhenReady = true)))
    }

    @Test fun nextFenceBlocksRepeatedClicksAndExpires() {
        val generation = MeloWidgetPolicy.Generation()
        val token = generation.token
        assertTrue(generation.takeNext(token, 0))
        assertFalse(generation.takeNext(token, 1))
        assertFalse(generation.takeNext(token, MeloWidgetPolicy.NEXT_FENCE_MS - 1))
        assertTrue(generation.takeNext(token, MeloWidgetPolicy.NEXT_FENCE_MS))
        generation.clearNext() // playback error / resolved request
        assertTrue(generation.takeNext(token, MeloWidgetPolicy.NEXT_FENCE_MS + 1))
    }

    @Test fun sameSongMetadataUpdateKeepsNextFenceAndRejectsOldToken() {
        val generation = MeloWidgetPolicy.Generation()
        generation.update("A/title1", "A")
        val first = generation.token
        assertTrue(generation.takeNext(first, 0))
        assertFalse(generation.takeNext(first, 20))

        // Title or cover of A arrives while B is still resolving: the widget redraws with a new token.
        assertTrue(generation.update("A/title2", "A"))
        val redrawn = generation.token
        assertNotEquals(first, redrawn)
        assertFalse(generation.takeNext(first, 100))
        assertFalse("a redraw must not allow a second Next inside the window", generation.takeNext(redrawn, 100))
        assertFalse(generation.takeNext(redrawn, MeloWidgetPolicy.NEXT_FENCE_MS - 1))
        assertTrue(generation.takeNext(redrawn, MeloWidgetPolicy.NEXT_FENCE_MS))
    }

    @Test fun nextFenceReopensForAnotherSongOwnerOrEmptyState() {
        for ((changedKey, changedFence) in listOf("B/title" to "B", "A/owner2" to "A/owner2", null to null)) {
            val generation = MeloWidgetPolicy.Generation()
            generation.update("A/title", "A")
            assertTrue(generation.takeNext(generation.token, 0))
            assertTrue(generation.update(changedKey, changedFence))
            assertTrue(generation.takeNext(generation.token, 1))
        }
    }

    @Test fun playerErrorStillClearsFenceAfterMetadataUpdate() {
        val generation = MeloWidgetPolicy.Generation()
        generation.update("A/title1", "A")
        assertTrue(generation.takeNext(generation.token, 0))
        generation.update("A/title2", "A")
        generation.clearNext()
        assertTrue(generation.takeNext(generation.token, 1))
    }

    @Test fun pendingTelegramSongStartsOnlyForItsOwnerWithSongInServiceQueue() {
        assertTrue(MeloWidgetPolicy.mayStartPendingTelegram(1L, 1L, listOf(7L, 8L), 8L))
        // The service refused or lost the queue: playing now would leave the widget without an owner.
        assertFalse(MeloWidgetPolicy.mayStartPendingTelegram(1L, 1L, emptyList(), 8L))
        assertFalse(MeloWidgetPolicy.mayStartPendingTelegram(1L, 1L, listOf(7L), 8L))
        // Signed out or switched account while the controller was connecting.
        assertFalse(MeloWidgetPolicy.mayStartPendingTelegram(1L, null, listOf(8L), 8L))
        assertFalse(MeloWidgetPolicy.mayStartPendingTelegram(1L, 2L, listOf(8L), 8L))
    }

    @Test fun changedItemRejectsLateArtworkAndTransport() {
        val generation = MeloWidgetPolicy.Generation()
        generation.update("A")
        val old = generation.token
        assertFalse(generation.update("A"))
        assertTrue(generation.update("B"))
        assertFalse(generation.accepts(old))
        assertFalse(generation.takeNext(old, 10))
        assertTrue(generation.takeNext(generation.token, 10))
    }

    @Test fun revocationAndTeardownInvalidateCallbacks() {
        val generation = MeloWidgetPolicy.Generation()
        generation.update("owner A / song A")
        val old = generation.token
        generation.update(null)
        assertFalse(generation.accepts(old))
        val empty = generation.token
        generation.invalidate() // close
        assertFalse(generation.accepts(empty))
    }

    @Test fun twoWidgetsAndActionsHaveDifferentPendingIntentIdentities() {
        val identities = (1..2).flatMap { id -> Action.entries.map {
            MeloWidgetPolicy.intentIdentity(id, it, "opaque-token")
        } }
        assertEquals(8, identities.toSet().size)
        assertNotEquals(identities.first(), MeloWidgetPolicy.intentIdentity(1, Action.PLAY, "new-token"))
    }

    @Test fun resizeAndNewInstanceForceFullRenderWithoutPolling() {
        val gate = MeloWidgetPolicy.RenderGate()
        val value = MeloWidgetPolicy.view(ready)
        assertTrue(gate.needsRender(value, false))
        assertFalse(gate.needsRender(value, false))
        assertTrue(gate.needsRender(value, true))
        assertTrue(gate.needsRender(value, true))
        assertTrue(gate.needsRender(MeloWidgetPolicy.view(Input()), false))
    }

    @Test fun localFolderFallbackNeverBecomesPerformerButSameRealTagIsKept() {
        for (raw in listOf(null, " ", "<unknown>", "Nghệ sĩ không rõ")) {
            val song = MeloLocalSourcePolicy.songOf(1, "A", raw, null, "Music/Test", 1, 1, null)!!
            assertEquals("Test", song.artist)
            assertNull(song.trustedPerformer)
        }
        val tagged = MeloLocalSourcePolicy.songOf(2, "A", " Test ", null, "Music/Test", 1, 1, null)!!
        assertEquals("Test", tagged.trustedPerformer)
        // Older manually constructed songs remain source-compatible and fail closed.
        assertNull(MeloLocalSourcePolicy.LocalSong(3, "A", "Test", null, "Test", 1, 1, null, "", "").trustedPerformer)
    }
}
