package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloBrowseUiPolicy.Kind
import com.bsdung.musicassistantcar.data.telegram.MeloBrowseUiPolicy.Scope
import org.junit.Assert.*
import org.junit.Test

class MeloBrowseStateTest {
    private fun key(id: Long, owner: Long = 1) = MeloBrowseState.Key(owner, Scope(Kind.CHANNEL, id))

    @Test fun sourceAtoBtoARetainsQuerySortAndIdentityAnchor() {
        val memory = MeloBrowseState()
        val a = key(-1); val b = key(-2)
        memory.activate(a, SongSort.NEWEST)
        memory.query(a, "sử thanh hóa")
        memory.sort(a, SongSort.NAME, "s")
        memory.position(a, 500, -17, 0)
        assertEquals("", memory.activate(b, SongSort.NEWEST).query)
        memory.query(b, "khác")
        val restored = memory.activate(a, SongSort.NEWEST)
        assertEquals("sử thanh hóa", restored.query)
        assertEquals(SongSort.NAME, restored.sort)
        assertEquals("s", restored.letterFloor)
        assertEquals(500L, restored.anchorSongKey)
        assertEquals(-17, restored.offsetPx)
    }

    @Test fun localChannelAndDownloadedChannelNeverShareContext() {
        val memory = MeloBrowseState()
        val keys = listOf(key(-1), MeloBrowseState.Key(1, Scope(Kind.DOWNLOADED, -1)),
            MeloBrowseState.Key(1, Scope(Kind.LOCAL)))
        keys.forEachIndexed { i, id -> memory.activate(id, SongSort.NEWEST); memory.query(id, "q$i") }
        keys.forEachIndexed { i, id -> assertEquals("q$i", memory.get(id)?.query) }
    }

    @Test fun seventeenthContextEvictsLeastRecentlyUsedOnly() {
        val memory = MeloBrowseState()
        (1L..16L).forEach { memory.activate(key(-it), SongSort.NEWEST) }
        memory.get(key(-1))
        memory.activate(key(-17), SongSort.NEWEST)
        assertEquals(16, memory.size)
        assertNull(memory.get(key(-2)))
        assertNotNull(memory.get(key(-1)))
    }

    @Test fun queryAndSortChangesCancelAnchorsButRepeatedRefreshDoesNot() {
        val memory = MeloBrowseState(); val key = key(-1)
        memory.activate(key, SongSort.NEWEST); memory.query(key, "a"); memory.position(key, 7, 2, 0)
        memory.query(key, "a"); assertEquals(7L, memory.get(key)?.anchorSongKey)
        memory.query(key, ""); assertNull(memory.get(key)?.anchorSongKey)
        memory.position(key, 8, 2, 0); memory.sort(key, SongSort.NAME)
        assertNull(memory.get(key)?.anchorSongKey)
    }

    @Test fun earlyPageKeepsPendingAndLaterPageRestoresExactOffset() {
        val key = key(-1)
        val pending = MeloBrowseState.Pending(key, 8, MeloBrowseState.Context(anchorSongKey = 500, offsetPx = -23))
        assertEquals(MeloBrowseState.Anchor.Wait, MeloBrowseState.locate(pending, key, 8, "", SongSort.NEWEST,
            null, listOf(1, 2), false))
        assertEquals(MeloBrowseState.Anchor.Found(1, -23), MeloBrowseState.locate(pending, key, 8, "", SongSort.NEWEST,
            null, listOf(1, 500), false))
        assertEquals(MeloBrowseState.Anchor.Drop, MeloBrowseState.locate(pending, key, 8, "", SongSort.NEWEST,
            null, listOf(1, 2), true))
    }

    @Test fun staleOwnerGenerationOrQueryCannotRestoreScroll() {
        val key = key(-1)
        val pending = MeloBrowseState.Pending(key, 8, MeloBrowseState.Context(query = "a", anchorSongKey = 500))
        assertEquals(MeloBrowseState.Anchor.Drop, MeloBrowseState.locate(pending, key, 9, "a", SongSort.NEWEST, null, listOf(500), false))
        assertEquals(MeloBrowseState.Anchor.Drop, MeloBrowseState.locate(pending, key(-1, 2), 8, "a", SongSort.NEWEST, null, listOf(500), false))
        assertEquals(MeloBrowseState.Anchor.Drop, MeloBrowseState.locate(pending, key, 8, "b", SongSort.NEWEST, null, listOf(500), false))
    }

    @Test fun savedStateContainsOnlyActiveAndReturnContextsWithBoundedQueries() {
        val memory = MeloBrowseState()
        val a = key(-1); val sources = MeloBrowseState.Key(1, Scope(Kind.SOURCES))
        memory.activate(a, SongSort.NAME); memory.query(a, "x".repeat(600)); memory.position(a, 80, -4, 0)
        memory.activate(key(-2), SongSort.NEWEST)
        memory.activate(sources, SongSort.NEWEST); memory.position(sources, null, 0, 430)
        memory.sourcesReturn = a
        val saved = memory.save()!!
        assertEquals(2, saved.contexts.size)
        assertEquals(512, saved.contexts.first { it.first == a }.second.query.length)
        val restored = MeloBrowseState()
        assertTrue(restored.restore(saved, 1))
        assertEquals(430, restored.get(sources)?.sourceScrollY)
        assertEquals(80L, restored.get(a)?.anchorSongKey)
        assertFalse(MeloBrowseState().restore(saved, 2))
        assertFalse(MeloBrowseState().restore(saved, 0))
    }

    @Test fun corruptOrCrossOwnerSavedContextIsRejected() {
        val key = key(-1)
        assertFalse(MeloBrowseState().restore(MeloBrowseState.Saved(key, null, null, emptyList()), 1))
        assertFalse(MeloBrowseState().restore(MeloBrowseState.Saved(key, null, null,
            listOf(key to MeloBrowseState.Context(), key(-2, 2) to MeloBrowseState.Context())), 1))
    }

    @Test fun damagedScopePayloadDoesNotRestoreForeignOrInvalidSource() {
        assertNull(MeloBrowseState.decodeKey(0, "CHANNEL", -1, false))
        assertNull(MeloBrowseState.decodeKey(1, "future_unknown_kind", null, false))
        assertNull(MeloBrowseState.decodeKey(1, "CHANNEL", null, false))
        assertNull(MeloBrowseState.decodeKey(1, "LOCAL", -1, false))
        assertEquals(key(-1), MeloBrowseState.decodeKey(1, "CHANNEL", -1, false))
        val memory = MeloBrowseState(); val sources = MeloBrowseState.Key(1, Scope(Kind.SOURCES))
        memory.activate(sources, SongSort.NEWEST); memory.position(sources, null, 0, 800)
        memory.query(sources, "new search")
        assertEquals(0, memory.get(sources)?.sourceScrollY)
    }

    @Test fun draftFlushesOnceAndRetainsAnExplicitEmptyQuery() {
        val draft = MeloBrowseState.QueryDraft("old")
        draft.edit("new")
        assertEquals("new", draft.take()); assertNull(draft.take())
        draft.edit(""); assertEquals("", draft.take()); assertNull(draft.take())
        draft.sync("restored"); assertNull(draft.take())
    }

    @Test fun repeatedRendererChangesDoNotConsumeContextOrPaginationTarget() {
        val memory = MeloBrowseState(); val key = key(-1)
        memory.activate(key, SongSort.NAME); memory.position(key, 1800, -11, 0)
        repeat(3) {
            val saved = memory.get(key)!!
            assertEquals(1800L, memory.activate(key, SongSort.NEWEST).anchorSongKey)
            assertEquals(saved, memory.get(key))
        }
    }

    /** The part of ListView that matters here: a data change before layout re-syncs to the old first row. */
    private class FakeList(var first: Int = 0, val total: Int = 200, val visible: Int = 8) {
        private var wanted: Int? = null
        fun setSelectionFromTop(index: Int) { wanted = index }
        fun notifyDataSetChanged() { wanted = null }
        fun layout() { wanted?.let { first = minOf(it, total - visible) }; wanted = null }
    }

    /** capture → detach → attach → refresh → layout, as on one Sony rotation; returns the saved anchor. */
    private fun rotate(memory: MeloBrowseState, key: MeloBrowseState.Key, rows: List<Long>, reapply: Boolean): Long? {
        val decision = MeloBrowseState.locate(memory.pending(key, 1)!!, key, 1, "q", SongSort.NEWEST, null, rows, false)
        val found = decision as MeloBrowseState.Anchor.Found
        val list = FakeList()
        val restore = MeloBrowseState.SongRestore()
        val selection = restore.start(found.index, found.offsetPx)
        list.setSelectionFromTop(selection.index)
        list.notifyDataSetChanged()
        if (reapply) restore.afterRefresh()?.let { list.setSelectionFromTop(it.index) }
        list.layout()
        val restored = reapply && restore.onScrolled(list.first, list.visible, list.total)
        if (!reapply || restored) memory.position(key, rows[list.first], found.offsetPx, 0)
        return memory.get(key)!!.anchorSongKey
    }

    @Test fun rowRefreshBeforeLayoutLosesAnchorUnlessTheSelectionIsReapplied() {
        val rows = (1000L until 1200L).toList()
        val memory = MeloBrowseState(); val key = key(-1)
        memory.activate(key, SongSort.NEWEST); memory.query(key, "q"); memory.position(key, 1040, -12, 0)
        assertEquals(1000L, rotate(memory, key, rows, reapply = false))
        memory.position(key, 1040, -12, 0)
        repeat(3) { assertEquals(1040L, rotate(memory, key, rows, reapply = true)) }
        assertEquals("q", memory.get(key)!!.query)
        assertEquals(-12, memory.get(key)!!.offsetPx)
    }

    @Test fun songRestoreWaitsForTheAnchorRowAndAcceptsAClampedEnd() {
        val restore = MeloBrowseState.SongRestore()
        assertTrue(restore.onScrolled(0, 8, 200))
        restore.start(40, -12)
        assertFalse(restore.onScrolled(0, 8, 200))
        assertFalse(restore.onScrolled(39, 8, 200))
        assertEquals(MeloBrowseState.SongRestore.Selection(40, -12), restore.afterRefresh())
        assertTrue(restore.onScrolled(40, 8, 200))
        assertNull(restore.pending)
        restore.start(198, 0)
        assertFalse(restore.onScrolled(0, 8, 200))
        assertTrue(restore.onScrolled(192, 8, 200))
        restore.start(5, 0); restore.cancel()
        assertNull(restore.afterRefresh())
        assertEquals(0, restore.start(-3, 0).index)
    }

    @Test fun clearForgetsEveryOwnerContextAndDeck() {
        val memory = MeloBrowseState(); val key = key(-1)
        memory.activate(key, SongSort.NEWEST)
        memory.deck = MeloBrowseState.Deck(songKey = 5, bodyScrollY = 100)
        memory.clear()
        assertNull(memory.active); assertEquals(0, memory.size); assertNull(memory.save())
        assertEquals(MeloBrowseState.Deck(), memory.deck)
    }
}
