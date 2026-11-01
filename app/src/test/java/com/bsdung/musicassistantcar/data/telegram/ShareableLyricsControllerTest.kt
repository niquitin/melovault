package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Meta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.Executor

class ShareableLyricsControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 10_000_000_000L
    private var consent = LyricsConsent.GRANTED

    /** Background tasks wait here until the test runs them, so the order of answers can be scrambled. */
    private val queued = ArrayDeque<Runnable>()
    private val executor = Executor { queued.addLast(it) }
    private val published = mutableListOf<LyricsState>()
    private val logs = mutableListOf<String>()

    private fun controller(transport: LyricsTransport): ShareableLyricsController {
        val provider = ShareableLyricsProvider(
            transport = transport,
            userAgent = "MeloVault/test",
            nowMs = { now },
            sleepMs = { now += it },
            jitterMs = { 0L }
        )
        val cache = ShareableLyricsCache(File(tmp.root, "melo_lyrics_v1"), nowMs = { now })
        return ShareableLyricsController(cache, provider, executor, post = { it.run() }, consent = { consent }, nowMs = { now }, log = { logs += it })
    }

    private fun runAll() {
        while (queued.isNotEmpty()) queued.removeFirst().run()
    }

    private fun publisher(): (LyricsState) -> Unit = { published += it }

    private val meta = Meta("Lạc Trôi", "Sơn Tùng M-TP", null, 233)

    private fun record(id: Int, duration: Int, synced: String? = "[00:01.00]xin chào", plain: String? = "xin chào", instrumental: Boolean = false): String {
        fun q(s: String?) = if (s == null) "null" else "\"" + s.replace("\n", "\\n") + "\""
        return """{"id":$id,"trackName":"t","artistName":"a","duration":$duration,"instrumental":$instrumental,"plainLyrics":${q(plain)},"syncedLyrics":${q(synced)}}"""
    }

    // --- consent ----------------------------------------------------------------------------

    @Test
    fun undecidedConsentAsksAndSendsNothing() {
        consent = LyricsConsent.UNDECIDED
        val transport = FakeLyricsTransport()
        controller(transport).request("u", meta, publisher())
        runAll()
        assertEquals(listOf<LyricsState>(LyricsState.NeedConsent), published)
        assertTrue(transport.urls.isEmpty())
    }

    @Test
    fun deniedConsentIsOffAndSendsNothing() {
        consent = LyricsConsent.DENIED
        val transport = FakeLyricsTransport()
        controller(transport).request("u", meta, publisher())
        runAll()
        assertEquals(listOf<LyricsState>(LyricsState.Off), published)
        assertTrue(transport.urls.isEmpty())
        assertTrue(queued.isEmpty())
    }

    // --- cache first, then network ----------------------------------------------------------

    @Test
    fun networkAnswerIsShownThenServedFromCacheWithoutAnotherRequest() {
        val transport = FakeLyricsTransport().answer(200, record(1, 233))
        val c = controller(transport)
        c.request("u", meta, publisher())
        assertEquals(listOf<LyricsState>(LyricsState.Loading), published)
        runAll()
        val first = published.last() as LyricsState.Synced
        assertFalse(first.fromCache)
        assertEquals("xin chào", first.lines.single().text)

        published.clear()
        c.request("u", meta, publisher())
        runAll()
        val second = published.last() as LyricsState.Synced
        assertTrue(second.fromCache)
        assertEquals(1, transport.urls.size)
    }

    @Test
    fun cacheOfSongAIsNotUsedForSameNameOtherLength() {
        val transport = FakeLyricsTransport().answer(200, record(1, 233)).answer(200, record(2, 301, synced = null, plain = "bản live"))
        val c = controller(transport)
        c.request("u", meta, publisher()); runAll()
        published.clear()
        c.request("u", meta.copy(durationSec = 301), publisher()); runAll()
        val state = published.last()
        assertTrue(state is LyricsState.Plain)
        assertFalse((state as LyricsState.Plain).fromCache)
        assertEquals(2, transport.urls.size)
    }

    @Test
    fun plainOnlyRecordIsPlainAndRecordWithoutTextIsNoMatch() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233, synced = null, plain = "chỉ có lời thường")).answer(200, record(2, 250, synced = null, plain = null)))
        c.request("u", meta, publisher()); runAll()
        assertEquals("chỉ có lời thường", (published.last() as LyricsState.Plain).text)
        c.request("u", meta.copy(durationSec = 250), publisher()); runAll()
        assertEquals(LyricsState.NoMatch(canSearch = true), published.last())
    }

    @Test
    fun instrumentalOnlyWhenTheRecordSaysSoAndNeverFrom404() {
        val instrumental = controller(FakeLyricsTransport().answer(200, record(3, 233, synced = null, plain = null, instrumental = true)))
        instrumental.request("u", meta, publisher()); runAll()
        assertEquals(LyricsState.Instrumental, published.last())

        published.clear()
        val missing = controller(FakeLyricsTransport().answer(404).answer(200, "[]"))
        missing.request("u2", meta, publisher()); runAll()
        assertEquals(LyricsState.NoMatch(canSearch = true), published.last())
    }

    // --- fallback search (LD5) --------------------------------------------------------------

    @Test
    fun notFoundThenExactlyOneCloseSearchResultIsTaken() {
        val list = "[" + record(5, 400) + "," + record(6, 234) + "]"
        val c = controller(FakeLyricsTransport().answer(404).answer(200, list))
        c.request("u", meta, publisher()); runAll()
        assertTrue(published.last() is LyricsState.Synced)
    }

    @Test
    fun notFoundThenTwoCloseResultsAsksTheUserAndCachesNothing() {
        val list = "[" + record(5, 232) + "," + record(6, 234) + "]"
        val transport = FakeLyricsTransport().answer(404).answer(200, list).answer(404).answer(200, list)
        val c = controller(transport)
        c.request("u", meta, publisher()); runAll()
        val ambiguous = published.last() as LyricsState.Ambiguous
        assertEquals(2, ambiguous.candidates.size)

        c.request("u", meta, publisher()); runAll()
        assertEquals(4, transport.urls.size)
    }

    @Test
    fun definiteNothingIsRememberedForThreeDaysOnly() {
        val transport = FakeLyricsTransport().answer(404).answer(200, "[]").answer(404).answer(200, "[]")
        val c = controller(transport)
        c.request("u", meta, publisher()); runAll()
        assertEquals(LyricsState.NoMatch(canSearch = true), published.last())
        c.request("u", meta, publisher()); runAll()
        assertEquals(2, transport.urls.size)

        now += 3L * 24 * 60 * 60 * 1000 + 1
        c.request("u", meta, publisher()); runAll()
        assertEquals(4, transport.urls.size)
    }

    // --- no lookup cases --------------------------------------------------------------------

    @Test
    fun missingMetadataAndMixsetNeverTouchTheNetwork() {
        val transport = FakeLyricsTransport()
        val c = controller(transport)
        c.request("u", Meta("Tên bài", null, null, 200), publisher()); runAll()
        assertEquals(LyricsState.MissingMetadata, published.last())
        c.request("u", Meta("Mixset", "DJ", null, 3_600), publisher()); runAll()
        assertEquals(LyricsState.NoMatch(canSearch = true), published.last())
        assertTrue(transport.urls.isEmpty())
    }

    // --- errors -----------------------------------------------------------------------------

    @Test
    fun temporaryFailureIsShownAndNotRemembered() {
        val transport = FakeLyricsTransport().answer(500).answer(200, record(1, 233))
        val c = controller(transport)
        c.request("u", meta, publisher()); runAll()
        assertEquals(LyricsState.TemporaryError(null), published.last())
        c.request("u", meta, publisher()); runAll()
        assertTrue(published.last() is LyricsState.Synced)
        assertEquals(2, transport.urls.size)
    }

    @Test
    fun rateLimitCarriesRetryDelayAndFurtherRequestsWaitWithoutNetwork() {
        val transport = FakeLyricsTransport().answer(429, null, retryAfterSec = 9)
        val c = controller(transport)
        c.request("u", meta, publisher()); runAll()
        assertEquals(LyricsState.TemporaryError(9_000L), published.last())
        c.request("u", meta.copy(durationSec = 100), publisher()); runAll()
        assertTrue(published.last() is LyricsState.TemporaryError)
        assertEquals(1, transport.urls.size)
    }

    @Test
    fun anyFailureBecomesAStateNeverAnException() {
        val transport = FakeLyricsTransport().fail(IOException("down"))
        controller(transport).request("u", meta, publisher())
        runAll()
        assertEquals(LyricsState.TemporaryError(null), published.last())
        val rogue = FakeLyricsTransport().fail(IllegalStateException("bug"))
        controller(rogue).request("u", meta.copy(durationSec = 1), publisher())
        runAll()
        assertTrue(published.last() is LyricsState.TemporaryError)
    }

    // --- generations (section 4 of the plan) -------------------------------------------------

    @Test
    fun answersArrivingOutOfOrderOnlyShowTheLatestSong() {
        val a = meta
        val b = Meta("Bài B", "Nghệ sĩ B", null, 200)
        val transport = FakeLyricsTransport().answer(200, record(2, 233, synced = "[00:01.00]bài A lần hai"))
            .answer(200, record(1, 200, synced = "[00:01.00]bài B"))
            .answer(200, record(3, 233, synced = "[00:01.00]bài A lần một"))
        val c = controller(transport)
        c.request("u", a, publisher())
        c.request("u", b, publisher())
        c.request("u", a, publisher())
        published.clear()
        // run the newest request first, then the older ones
        val tasks = queued.toList().reversed()
        queued.clear()
        tasks.forEach { it.run() }
        val results = published.filterIsInstance<LyricsState.Synced>()
        assertEquals(1, results.size)
        assertEquals("bài A lần hai", results.single().lines.single().text)
    }

    @Test
    fun cancelDropsTheAnswerOfAClosedTab() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233)))
        c.request("u", meta, publisher())
        c.cancel()
        published.clear()
        runAll()
        assertTrue(published.isEmpty())
        assertTrue(logs.contains("stage=lyrics_drop"))
    }

    // --- screen rotation: the new pane asks before the old one closes (B1) ---------------------

    @Test
    fun aClosingPaneDoesNotCancelTheRequestOfThePaneThatReplacedIt() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233)).answer(200, record(1, 233)))
        val oldPane = Any()
        val newPane = Any()
        val oldShown = mutableListOf<LyricsState>()
        val newShown = mutableListOf<LyricsState>()
        c.request("u", meta, { oldShown += it }, oldPane)
        c.request("u", meta, { newShown += it }, newPane)
        c.cancel(oldPane)
        runAll()
        assertTrue("the new pane gets the lyrics: $newShown", newShown.last() is LyricsState.Synced)
        assertEquals("the old pane gets nothing after Loading", listOf<LyricsState>(LyricsState.Loading), oldShown)
    }

    @Test
    fun aPaneStillCancelsItsOwnRequest() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233)))
        val pane = Any()
        val shown = mutableListOf<LyricsState>()
        c.request("u", meta, { shown += it }, pane)
        c.cancel(pane)
        runAll()
        assertEquals(listOf<LyricsState>(LyricsState.Loading), shown)
    }

    @Test
    fun cancelOfAPaneThatNeverAskedChangesNothing() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233)))
        val asking = Any()
        val shown = mutableListOf<LyricsState>()
        c.request("u", meta, { shown += it }, asking)
        c.cancel(Any())
        runAll()
        assertTrue(shown.last() is LyricsState.Synced)
    }

    @Test
    fun cancelWithoutAClientDropsEveryRequest() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233)))
        val shown = mutableListOf<LyricsState>()
        c.request("u", meta, { shown += it }, Any())
        c.cancel()
        runAll()
        assertEquals(listOf<LyricsState>(LyricsState.Loading), shown)
    }

    @Test
    fun anAnswerThatNeverComesCanBeAskedForAgain() {
        val transport = FakeLyricsTransport().answer(200, record(1, 233))
        val c = controller(transport)
        val pane = Any()
        val shown = mutableListOf<LyricsState>()
        c.request("u", meta, { shown += it }, pane)
        queued.clear() // the answer is lost
        assertEquals(listOf<LyricsState>(LyricsState.Loading), shown)
        c.request("u", meta, { shown += it }, pane) // the pane asks again
        runAll()
        assertTrue(shown.last() is LyricsState.Synced)
    }

    @Test
    fun answerForAnotherAccountIsDropped() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233)).answer(200, record(2, 233)))
        c.request("alice", meta, publisher())
        c.request("bob", meta, publisher())
        published.clear()
        runAll()
        assertEquals(1, published.filterIsInstance<LyricsState.Synced>().size)
    }

    // --- manual choice (LD10) ---------------------------------------------------------------

    @Test
    fun manualChoiceIsRememberedEvenForSongWithoutArtist() {
        val noArtist = Meta("Bài lạ", null, null, 200)
        val transport = FakeLyricsTransport()
        val c = controller(transport)
        c.request("u", noArtist, publisher()); runAll()
        assertEquals(LyricsState.MissingMetadata, published.last())

        val picked = MeloLyricsPolicy.Candidate(8, "Bài lạ", "Ai đó", null, 200.0, false, "lời", "[00:02.00]đã chọn")
        val shown = c.choose("u", noArtist, picked) as LyricsState.Synced
        assertTrue(shown.manual)

        c.request("u", noArtist, publisher()); runAll()
        val again = published.last() as LyricsState.Synced
        assertTrue(again.fromCache && again.manual)
        assertTrue(transport.urls.isEmpty())

        c.clearChoice("u", noArtist)
        c.request("u", noArtist, publisher()); runAll()
        assertEquals(LyricsState.MissingMetadata, published.last())
    }

    @Test
    fun manualSearchPublishesResultsAndIsDroppedWhenTheSongChanged() {
        val list = "[" + record(5, 232) + "," + record(6, 500) + "]"
        val transport = FakeLyricsTransport().answer(404).answer(200, "[]").answer(200, list)
            .answer(200, list).answer(404).answer(200, "[]")
        val c = controller(transport)
        c.request("u", meta, publisher()); runAll()

        val searches = mutableListOf<LyricsManualSearch>()
        c.searchManual("u", meta, "lạc trôi") { searches += it }
        runAll()
        assertEquals(2, (searches.single() as LyricsManualSearch.Results).candidates.size)
        assertEquals("lạc trôi", java.net.URLDecoder.decode(transport.urls.last().substringAfter("q="), "UTF-8"))

        searches.clear()
        c.searchManual("u", meta, "lạc trôi") { searches += it }
        c.request("u", Meta("Bài khác", "Ai đó", null, 100), publisher())
        runAll()
        assertTrue(searches.isEmpty())
    }

    @Test
    fun manualSearchNeedsConsentAndAnActiveSong() {
        val transport = FakeLyricsTransport()
        val c = controller(transport)
        val searches = mutableListOf<LyricsManualSearch>()
        c.searchManual("u", meta, "x") { searches += it }
        runAll()
        assertTrue(searches.isEmpty() && transport.urls.isEmpty())
    }

    // --- accounts ---------------------------------------------------------------------------

    @Test
    fun logoutOfOneAccountClearsItsCacheOnly() {
        val transport = FakeLyricsTransport().answer(200, record(1, 233)).answer(200, record(2, 233)).answer(200, record(3, 233))
        val c = controller(transport)
        c.request("alice", meta, publisher()); runAll()
        c.request("bob", meta, publisher()); runAll()
        assertEquals(2, transport.urls.size)

        c.clearOwner("alice")
        c.request("bob", meta, publisher()); runAll()
        assertTrue((published.last() as LyricsState.Synced).fromCache)
        c.request("alice", meta, publisher()); runAll()
        assertFalse((published.last() as LyricsState.Synced).fromCache)
        assertEquals(3, transport.urls.size)
        assertNotNull(File(tmp.root, "melo_lyrics_v1"))
    }

    @Test
    fun loadingIsPublishedSynchronouslyAndResultComesFromTheExecutor() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233)))
        c.request("u", meta, publisher())
        assertEquals(1, published.size)
        assertEquals(1, queued.size)
    }

    @Test
    fun logsCarryNoSongNames() {
        val c = controller(FakeLyricsTransport().answer(200, record(1, 233)))
        c.request("u", Meta("Tên Riêng Tư", "Người Hát Riêng", null, 233), publisher())
        runAll()
        assertTrue(logs.none { it.contains("Riêng") })
    }
}
