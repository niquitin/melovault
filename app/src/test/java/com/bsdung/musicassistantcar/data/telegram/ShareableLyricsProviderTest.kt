package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Meta
import com.bsdung.musicassistantcar.data.telegram.ShareableLyricsProvider.GetResult
import com.bsdung.musicassistantcar.data.telegram.ShareableLyricsProvider.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

/** Scripted transport: one answer per call, in order, recording every URL and header set it was given. */
internal class FakeLyricsTransport(private val script: MutableList<() -> LyricsHttpResponse> = mutableListOf()) : LyricsTransport {
    val urls = mutableListOf<String>()
    val headers = mutableListOf<Map<String, String>>()

    fun answer(code: Int, body: String? = null, retryAfterSec: Long? = null): FakeLyricsTransport {
        script += { LyricsHttpResponse(code, body, retryAfterSec) }
        return this
    }

    fun fail(error: Exception): FakeLyricsTransport {
        script += { throw error }
        return this
    }

    override fun get(url: String, headers: Map<String, String>): LyricsHttpResponse {
        urls += url
        this.headers += headers
        check(script.isNotEmpty()) { "unexpected request: no scripted answer left" }
        return script.removeAt(0).invoke()
    }
}

class ShareableLyricsProviderTest {

    private var now = 1_000_000L
    private val slept = mutableListOf<Long>()
    private val logs = mutableListOf<String>()

    private fun provider(transport: LyricsTransport, jitter: Long = 0L) = ShareableLyricsProvider(
        transport = transport,
        userAgent = "MeloVault/test",
        log = { logs += it },
        nowMs = { now },
        sleepMs = { slept += it; now += it },
        jitterMs = { jitter }
    )

    private val meta = Meta("Lạc Trôi", "Sơn Tùng M-TP", "Album X", 233)

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split('&').associate {
            val (k, v) = it.split('=', limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    private val syncedRecord = """{"id":42,"trackName":"Lạc Trôi","artistName":"Sơn Tùng M-TP","albumName":"Album X",
        |"duration":233.4,"instrumental":false,"plainLyrics":"a\nb","syncedLyrics":"[00:01.00]a\n[00:02.00]b"}""".trimMargin()

    // --- request shape (LD3) ----------------------------------------------------------------

    @Test
    fun getUrlCarriesOnlyTheFourAllowedFields() {
        val transport = FakeLyricsTransport().answer(404)
        provider(transport).get(meta)
        val url = transport.urls.single()
        assertTrue(url.startsWith("https://lrclib.net/api/get?"))
        assertEquals(
            mapOf("track_name" to "Lạc Trôi", "artist_name" to "Sơn Tùng M-TP", "album_name" to "Album X", "duration" to "233"),
            query(url)
        )
    }

    @Test
    fun noTelegramIdentifierPathOrSongKeyIsInTheRequest() {
        val transport = FakeLyricsTransport().answer(404).answer(200, "[]")
        val p = provider(transport)
        p.get(meta)
        p.search(meta)
        val everything = (transport.urls + transport.headers.flatMap { it.entries.map { e -> e.key + ":" + e.value } }).joinToString("\n")
        listOf("chat", "message", "telegram", "songkey", "account", "owner", "session", "/storage", "file").forEach {
            assertFalse("request must not contain '$it'", everything.lowercase().contains(it))
        }
        assertTrue(query(transport.urls[0]).keys.all { it in setOf("track_name", "artist_name", "album_name", "duration") })
        assertTrue(query(transport.urls[1]).keys.all { it in setOf("track_name", "artist_name") })
    }

    @Test
    fun onlyUserAgentAndAcceptHeadersAreSent() {
        val transport = FakeLyricsTransport().answer(404)
        provider(transport).get(meta)
        assertEquals(mapOf("User-Agent" to "MeloVault/test", "Accept" to "application/json"), transport.headers.single())
    }

    @Test
    fun albumAndDurationAreLeftOutWhenUnknown() {
        val transport = FakeLyricsTransport().answer(404).answer(404)
        val p = provider(transport)
        p.get(Meta("T", "A", null, null))
        p.get(Meta("T", "A", "  ", 0))
        assertEquals(setOf("track_name", "artist_name"), query(transport.urls[0]).keys)
        assertEquals(setOf("track_name", "artist_name"), query(transport.urls[1]).keys)
    }

    @Test
    fun parametersAreEncodedNotConcatenated() {
        val transport = FakeLyricsTransport().answer(404)
        provider(transport).get(Meta("A&B=C#D e", "x?y", null, 10))
        val url = transport.urls.single()
        assertFalse(url.contains(' ') || url.contains('#'))
        assertEquals("A&B=C#D e", query(url)["track_name"])
        assertEquals("x?y", query(url)["artist_name"])
    }

    @Test
    fun manualSearchUsesQueryParameterOnly() {
        val transport = FakeLyricsTransport().answer(200, "[]")
        provider(transport).searchText("  lạc trôi sơn tùng ")
        assertEquals(mapOf("q" to "lạc trôi sơn tùng"), query(transport.urls.single()))
    }

    // --- outcomes ---------------------------------------------------------------------------

    @Test
    fun okWithSyncedAndPlainLyrics() {
        val found = provider(FakeLyricsTransport().answer(200, syncedRecord)).get(meta) as GetResult.Found
        assertEquals(42L, found.candidate.id)
        assertEquals("[00:01.00]a\n[00:02.00]b", found.candidate.syncedLyrics)
        assertEquals("a\nb", found.candidate.plainLyrics)
        assertTrue(found.candidate.hasSynced)
        assertEquals(233.4, found.candidate.durationSec, 0.0001)
    }

    @Test
    fun jsonNullSyncedLyricsIsNullNotTheWordNull() {
        val body = """{"id":1,"trackName":"t","artistName":"a","duration":10,"instrumental":false,"plainLyrics":"hi","syncedLyrics":null}"""
        val c = (provider(FakeLyricsTransport().answer(200, body)).get(meta) as GetResult.Found).candidate
        assertNull(c.syncedLyrics)
        assertFalse(c.hasSynced)
        assertEquals("hi", c.plainLyrics)
    }

    @Test
    fun instrumentalFlagIsReadFromTheRecord() {
        val body = """{"id":2,"trackName":"t","artistName":"a","duration":10,"instrumental":true,"plainLyrics":null,"syncedLyrics":null}"""
        val c = (provider(FakeLyricsTransport().answer(200, body)).get(meta) as GetResult.Found).candidate
        assertTrue(c.instrumental)
        assertNull(c.plainLyrics)
    }

    @Test
    fun notFoundIsNoMatchAndNeverInstrumental() {
        val result = provider(FakeLyricsTransport().answer(404, """{"code":404,"name":"TrackNotFound"}""")).get(meta)
        assertEquals(GetResult.NoMatch, result)
    }

    @Test
    fun rateLimitedWaitsRetryAfterPlusJitterAndBlocksLaterRequests() {
        val transport = FakeLyricsTransport().answer(429, null, retryAfterSec = 7).answer(404)
        val p = provider(transport, jitter = 400)
        val first = p.get(meta) as GetResult.TemporaryError
        assertEquals(7_400L, first.retryAfterMs)

        now += 3_000
        val blocked = p.get(meta) as GetResult.TemporaryError
        assertEquals(1, transport.urls.size)
        assertEquals(4_400L, blocked.retryAfterMs)

        now += 4_400
        assertEquals(GetResult.NoMatch, p.get(meta))
        assertEquals(2, transport.urls.size)
    }

    @Test
    fun rateLimitedWithoutHeaderUsesFiveSeconds() {
        val result = provider(FakeLyricsTransport().answer(429), jitter = 0).get(meta) as GetResult.TemporaryError
        assertEquals(5_000L, result.retryAfterMs)
    }

    @Test
    fun serverErrorsAndOddCodesAreTemporary() {
        listOf(500, 502, 503, 400, 403, 302).forEach { code ->
            val result = provider(FakeLyricsTransport().answer(code, "x")).get(meta)
            assertEquals("code $code", GetResult.TemporaryError(null), result)
        }
    }

    @Test
    fun networkFailuresAreTemporaryNotExceptions() {
        assertEquals(GetResult.TemporaryError(null), provider(FakeLyricsTransport().fail(IOException("timeout"))).get(meta))
        assertEquals(GetResult.TemporaryError(null), provider(FakeLyricsTransport().fail(IllegalStateException("boom"))).get(meta))
    }

    @Test
    fun brokenOrEmptyJsonIsTemporary() {
        assertEquals(GetResult.TemporaryError(null), provider(FakeLyricsTransport().answer(200, "{not json")).get(meta))
        assertEquals(GetResult.TemporaryError(null), provider(FakeLyricsTransport().answer(200, "")).get(meta))
        assertEquals(GetResult.TemporaryError(null), provider(FakeLyricsTransport().answer(200, null)).get(meta))
    }

    // --- search -----------------------------------------------------------------------------

    @Test
    fun searchParsesListAndCapsAtTwenty() {
        val items = (1..25).joinToString(",", "[", "]") {
            """{"id":$it,"trackName":"t$it","artistName":"a","duration":${100 + it},"instrumental":false,"plainLyrics":"p","syncedLyrics":null}"""
        }
        val results = (provider(FakeLyricsTransport().answer(200, items)).search(meta) as SearchResult.Results).candidates
        assertEquals(20, results.size)
        assertEquals(1L, results.first().id)
    }

    @Test
    fun searchEmptyArrayAnd404MeanNoResults() {
        assertEquals(SearchResult.Results(emptyList()), provider(FakeLyricsTransport().answer(200, "[]")).search(meta))
        assertEquals(SearchResult.Results(emptyList()), provider(FakeLyricsTransport().answer(404)).search(meta))
    }

    @Test
    fun searchRejectsNonArrayAndErrors() {
        assertEquals(SearchResult.TemporaryError(null), provider(FakeLyricsTransport().answer(200, """{"a":1}""")).search(meta))
        assertEquals(SearchResult.TemporaryError(null), provider(FakeLyricsTransport().answer(500)).search(meta))
        assertEquals(SearchResult.TemporaryError(5_000L), provider(FakeLyricsTransport().answer(429)).search(meta))
    }

    // --- spacing and privacy of logs --------------------------------------------------------

    @Test
    fun requestsAreSpacedAtLeast300ms() {
        val transport = FakeLyricsTransport().answer(404).answer(404).answer(404)
        val p = provider(transport)
        p.get(meta)
        p.get(meta)
        now += 1_000
        p.get(meta)
        assertEquals(listOf(300L), slept)
    }

    @Test
    fun logsHoldStagesAndCodesButNoTitleOrArtist() {
        val transport = FakeLyricsTransport().answer(404).answer(200, "[]").fail(IOException("x"))
        val p = provider(transport)
        val secret = Meta("Bí Mật Tên Bài", "Nghệ Sĩ Riêng", "Album Riêng", 200)
        p.get(secret)
        p.search(secret)
        p.get(secret)
        assertTrue(logs.isNotEmpty())
        logs.forEach {
            assertTrue(it, it.startsWith("stage=lyrics_"))
            assertFalse(it, it.contains("Bí Mật") || it.contains("Nghệ Sĩ") || it.contains("Album"))
        }
        assertTrue(logs.any { it.contains("code=404") })
    }
}
