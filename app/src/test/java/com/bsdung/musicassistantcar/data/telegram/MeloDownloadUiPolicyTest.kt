package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test

class MeloDownloadUiPolicyTest {
    private val good = MeloDownloadUiPolicy.FileFacts(true, true, true, 100L, true)
    private fun evidence(complete: Boolean = true, path: String? = "/owner/a", size: Long = 100L) =
        MeloDownloadUiPolicy.Evidence(1L, 10L, path, complete, size)
    private fun state(e: MeloDownloadUiPolicy.Evidence, f: MeloDownloadUiPolicy.FileFacts? = good) =
        MeloDownloadUiPolicy.evaluate(1L, e, f).state

    @Test fun completeRequiresOwnerAndReadableNonemptyExactSize() {
        assertEquals(MeloDownloadUiPolicy.State.READY, state(evidence()))
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, state(evidence().copy(ownerId = 2)))
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, state(evidence(), good.copy(owned = false)))
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, state(evidence(), good.copy(file = false)))
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, state(evidence(), good.copy(exists = false)))
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, state(evidence(), good.copy(readable = false)))
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, state(evidence(), good.copy(bytes = 0)))
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, state(evidence(size = 101)))
    }
    @Test fun partialHundredAndPathAloneNeverReady() {
        assertEquals(MeloDownloadUiPolicy.State.DOWNLOADING, state(evidence(false).copy(downloading = true, percent = 100)))
        assertEquals(MeloDownloadUiPolicy.State.UNKNOWN, state(evidence(false)))
        assertEquals(MeloDownloadUiPolicy.State.UNKNOWN, state(evidence(), null))
        assertEquals(MeloDownloadUiPolicy.State.REMOTE, state(evidence(false, null).copy(remoteKnown = true), null))
    }
    @Test fun unknownExpectedSizeStillNeedsCompletionAndNonempty() {
        assertEquals(MeloDownloadUiPolicy.State.READY, state(evidence(size = -1)))
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, state(evidence(path = null), null))
    }
    @Test fun playbackAndStorageRemainIndependent() {
        val partial = MeloDownloadUiPolicy.Presentation(MeloDownloadUiPolicy.State.DOWNLOADING, 100)
        assertEquals("Đang phát · Đang tải 100%", MeloDownloadUiPolicy.rowText(partial, true, true, false, false, quality = "FLAC"))
        val ready = MeloDownloadUiPolicy.evaluate(1, evidence(), good)
        assertEquals("Tạm dừng · Đã tải · FLAC", MeloDownloadUiPolicy.rowText(ready, true, false, true, false, quality = "FLAC"))
        assertEquals("Trên máy · FLAC", MeloDownloadUiPolicy.rowText(partial, false, false, false, false, local = true, quality = "FLAC"))
    }
    private class Harness {
        val workers = mutableListOf<Runnable>()
        val posts = mutableListOf<Runnable>()
        var facts = MeloDownloadUiPolicy.FileFacts(true, true, true, 100L, true)
        var stats = 0
        val controller = MeloDownloadUiPolicy.Controller({ stats++; facts }, { workers.add(it) }, { posts.add(it) }, {})
        init { controller.setOwner(1) }
        fun finish() { while (workers.isNotEmpty()) workers.removeAt(0).run(); while (posts.isNotEmpty()) posts.removeAt(0).run() }
    }
    @Test fun bindsAreMemoryOnlyAndDuplicateProofsCoalesce() {
        val h = Harness(); repeat(3) { h.controller.observe(evidence()) }
        repeat(100) { assertEquals(MeloDownloadUiPolicy.State.UNKNOWN, h.controller.presentation(10).state) }
        assertEquals(0, h.stats); assertEquals(1, h.workers.size)
        h.finish(); assertEquals(1, h.stats)
        repeat(10) { h.controller.observe(evidence()); h.controller.presentation(10) }
        assertEquals(1, h.stats); assertTrue(h.workers.isEmpty())
    }
    @Test fun oldOwnerCannotPublishOrInvokeCallback() {
        val h = Harness(); var calls = 0
        h.controller.observe(evidence()) { calls++ }; h.controller.setOwner(2); h.finish()
        assertEquals(0, calls); assertEquals(MeloDownloadUiPolicy.State.UNKNOWN, h.controller.presentation(10).state)
        h.controller.observe(evidence()); assertTrue(h.workers.isEmpty())
    }
    @Test fun oldEpochIsRejectedAndForegroundRechecks() {
        val h = Harness(); h.controller.observe(evidence()); h.workers.removeAt(0).run()
        h.controller.invalidate(); h.controller.ensure(10); h.posts.removeAt(0).run()
        assertEquals(MeloDownloadUiPolicy.State.UNKNOWN, h.controller.presentation(10).state)
        h.facts = h.facts.copy(exists = false); h.finish()
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, h.controller.presentation(10).state)
        assertEquals(evidence(), h.controller.evidence(10))
    }
    @Test fun replacementRevisionWinsOverSlowOldPath() {
        val h = Harness(); h.controller.observe(evidence()); h.workers.removeAt(0).run()
        h.controller.observe(evidence().copy(path = "/owner/b")); h.finish()
        assertEquals("/owner/b", h.controller.presentation(10).readyPath)
    }
    @Test fun checkedCallbackOnlyRunsAfterBackgroundCheck() {
        val h = Harness(); var ready: String? = null
        h.controller.observe(evidence()) { ready = it.readyPath }; assertNull(ready)
        h.workers.removeAt(0).run(); assertNull(ready); h.posts.removeAt(0).run()
        assertEquals("/owner/a", ready)
    }
    @Test fun foregroundDoesNotLoseAnInFlightPlayCallback() {
        val h = Harness(); var calls = 0
        h.controller.observe(evidence()) { calls++ }; h.controller.recheck(10); h.finish()
        assertEquals(1, calls); assertEquals(1, h.stats)
        h.facts = good.copy(readable = false); h.controller.recheck(10); h.finish()
        assertEquals(MeloDownloadUiPolicy.State.UNAVAILABLE, h.controller.presentation(10).state)
    }
    @Test fun missingCopyDoesNotRemoveIndexAndAlternateIsSelected() {
        val h = Harness(); h.facts = good.copy(exists = false); h.controller.observe(evidence()); h.finish()
        h.facts = good; h.controller.observe(evidence().copy(songKey = 11, path = "/owner/b")); h.finish()
        fun song(key: Long) = IndexedTelegramSong(1, 20, key, key, key.toInt(), "Mix", "$key.flac")
        val group = TelegramOfflineLibrary.Group(listOf(TelegramOfflineLibrary.Entry(song(10), "/owner/a"),
            TelegramOfflineLibrary.Entry(song(11), "/owner/b")))
        val picked = TelegramOfflinePlayPolicy.defaultEntry(group, 1, 10) {
            h.controller.presentation(it.song.songKey).state == MeloDownloadUiPolicy.State.READY
        }
        assertEquals(11L, picked!!.song.songKey); assertEquals(2, group.entries.size)
        assertNotNull(h.controller.evidence(10))
    }
    @Test fun playingOrPercentDoesNotInventCompletion() {
        val h = Harness()
        assertEquals(MeloDownloadUiPolicy.State.DOWNLOADING, h.controller.presentation(10, true, 100).state)
        assertEquals(MeloDownloadUiPolicy.State.UNKNOWN, h.controller.presentation(10).state)
        h.controller.observe(evidence()); h.finish()
        assertEquals(MeloDownloadUiPolicy.State.READY, h.controller.presentation(10, true, 100).state)
    }
}
