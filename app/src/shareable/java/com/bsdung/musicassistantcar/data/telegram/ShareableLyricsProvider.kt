package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Candidate
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Meta
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One HTTP answer. [retryAfterSec] is the `Retry-After` header when the service sent one. */
internal data class LyricsHttpResponse(val code: Int, val body: String?, val retryAfterSec: Long? = null)

/** The only door to the network. Tests use a fake; production uses [HttpUrlConnectionLyricsTransport]. */
internal interface LyricsTransport {
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String>): LyricsHttpResponse
}

/** `HttpURLConnection` with short timeouts and a body cap; no HTTP library is added to the app (LD8). */
internal class HttpUrlConnectionLyricsTransport : LyricsTransport {
    override fun get(url: String, headers: Map<String, String>): LyricsHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readCapped(it) }
            val retry = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
            return LyricsHttpResponse(code, body, retry)
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_BODY_BYTES) throw IOException("lyrics response too large")
        }
        return out.toString("UTF-8")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 8_000
        const val MAX_BODY_BYTES = 512 * 1024
    }
}

/**
 * LRCLIB adapter (LD1/LD3/LD8): builds the request from the four allowed fields only, spaces
 * requests out, honours `Retry-After`, and turns every outcome into a value. It never logs a title
 * or artist, and it never throws.
 */
internal class ShareableLyricsProvider(
    private val transport: LyricsTransport,
    private val userAgent: String,
    private val log: (String) -> Unit = {},
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val sleepMs: (Long) -> Unit = { Thread.sleep(it) },
    private val jitterMs: () -> Long = { (Math.random() * 1_000).toLong() },
    private val baseUrl: String = BASE_URL
) {

    sealed class GetResult {
        data class Found(val candidate: Candidate) : GetResult()
        object NoMatch : GetResult()
        data class TemporaryError(val retryAfterMs: Long?) : GetResult()
    }

    sealed class SearchResult {
        data class Results(val candidates: List<Candidate>) : SearchResult()
        data class TemporaryError(val retryAfterMs: Long?) : SearchResult()
    }

    private var lastRequestAtMs = Long.MIN_VALUE
    private var blockedUntilMs = 0L
    private val lock = Any()

    /** `/api/get`: the exact song. 404 means no such record - it is not "instrumental" (LD6). */
    fun get(meta: Meta): GetResult {
        val response = execute("get", buildGetUrl(meta))
            ?: return GetResult.TemporaryError(null)
        if (response is Outcome.Blocked) return GetResult.TemporaryError(response.retryAfterMs)
        val http = (response as Outcome.Answer).response
        return when {
            http.code == 200 -> parseRecord(http.body)?.let { GetResult.Found(it) } ?: GetResult.TemporaryError(null)
            http.code == 404 -> GetResult.NoMatch
            http.code == 429 -> GetResult.TemporaryError(rateLimited(http))
            else -> GetResult.TemporaryError(null)
        }
    }

    /** `/api/search` by track and artist (automatic fallback after a 404). */
    fun search(meta: Meta): SearchResult =
        runSearch(buildSearchUrl(track = meta.title, artist = meta.artist, query = null))

    /** `/api/search?q=` typed by the user in the manual picker. */
    fun searchText(query: String): SearchResult =
        runSearch(buildSearchUrl(track = null, artist = null, query = query))

    private fun runSearch(url: String): SearchResult {
        val response = execute("search", url) ?: return SearchResult.TemporaryError(null)
        if (response is Outcome.Blocked) return SearchResult.TemporaryError(response.retryAfterMs)
        val http = (response as Outcome.Answer).response
        return when {
            http.code == 200 -> parseList(http.body)?.let { SearchResult.Results(it) } ?: SearchResult.TemporaryError(null)
            http.code == 404 -> SearchResult.Results(emptyList())
            http.code == 429 -> SearchResult.TemporaryError(rateLimited(http))
            else -> SearchResult.TemporaryError(null)
        }
    }

    private sealed class Outcome {
        class Answer(val response: LyricsHttpResponse) : Outcome()
        class Blocked(val retryAfterMs: Long) : Outcome()
    }

    /** Null = the network failed (no answer at all). */
    private fun execute(kind: String, url: String): Outcome? {
        synchronized(lock) {
            val now = nowMs()
            if (now < blockedUntilMs) {
                log("stage=lyrics_blocked kind=$kind")
                return Outcome.Blocked(blockedUntilMs - now)
            }
            val wait = lastRequestAtMs + MIN_GAP_MS - now
            if (lastRequestAtMs != Long.MIN_VALUE && wait > 0) sleepMs(wait)
            lastRequestAtMs = nowMs()
            log("stage=lyrics_request kind=$kind")
            return try {
                val response = transport.get(url, mapOf("User-Agent" to userAgent, "Accept" to "application/json"))
                log("stage=lyrics_response kind=$kind code=${response.code}")
                Outcome.Answer(response)
            } catch (e: IOException) {
                log("stage=lyrics_response kind=$kind code=io")
                null
            } catch (e: RuntimeException) {
                log("stage=lyrics_response kind=$kind code=err")
                null
            }
        }
    }

    /** 429: wait `Retry-After` plus 0-1 s, and make later requests wait too (LD8). */
    private fun rateLimited(http: LyricsHttpResponse): Long {
        val base = (http.retryAfterSec ?: DEFAULT_RETRY_SEC).coerceIn(1L, MAX_RETRY_SEC) * 1_000L
        val delay = base + jitterMs().coerceIn(0L, 1_000L)
        synchronized(lock) { blockedUntilMs = nowMs() + delay }
        return delay
    }

    internal fun buildGetUrl(meta: Meta): String {
        val params = ArrayList<Pair<String, String>>()
        params += "track_name" to (meta.title ?: "").trim()
        params += "artist_name" to (meta.artist ?: "").trim()
        meta.album?.trim()?.takeIf { it.isNotEmpty() }?.let { params += "album_name" to it }
        meta.durationSec?.takeIf { it > 0 }?.let { params += "duration" to it.toString() }
        return "$baseUrl/api/get?" + encode(params)
    }

    internal fun buildSearchUrl(track: String?, artist: String?, query: String?): String {
        val params = ArrayList<Pair<String, String>>()
        if (query != null) {
            params += "q" to query.trim()
        } else {
            params += "track_name" to (track ?: "").trim()
            artist?.trim()?.takeIf { it.isNotEmpty() }?.let { params += "artist_name" to it }
        }
        return "$baseUrl/api/search?" + encode(params)
    }

    private fun encode(params: List<Pair<String, String>>): String =
        params.joinToString("&") { (name, value) ->
            name + "=" + URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        }

    private fun parseRecord(body: String?): Candidate? = try {
        if (body.isNullOrBlank()) null else toCandidate(JSONObject(body))
    } catch (e: JSONException) {
        null
    }

    private fun parseList(body: String?): List<Candidate>? = try {
        if (body.isNullOrBlank()) {
            null
        } else {
            val array = JSONArray(body)
            (0 until minOf(array.length(), MeloLyricsPolicy.MAX_SEARCH_RESULTS))
                .mapNotNull { array.optJSONObject(it)?.let(::toCandidate) }
        }
    } catch (e: JSONException) {
        null
    }

    private fun toCandidate(json: JSONObject): Candidate = Candidate(
        id = json.optLong("id", 0L),
        trackName = text(json, "trackName") ?: "",
        artistName = text(json, "artistName") ?: "",
        albumName = text(json, "albumName"),
        durationSec = json.optDouble("duration", 0.0).let { if (it.isNaN()) 0.0 else it },
        instrumental = json.optBoolean("instrumental", false),
        plainLyrics = text(json, "plainLyrics"),
        syncedLyrics = text(json, "syncedLyrics")
    )

    /** JSON null and "" both mean "no value"; `optString` alone would turn JSON null into the word "null". */
    private fun text(json: JSONObject, key: String): String? {
        if (!json.has(key) || json.isNull(key)) return null
        return json.optString(key, "").takeIf { it.isNotBlank() }
    }

    companion object {
        const val BASE_URL = "https://lrclib.net"
        const val MIN_GAP_MS = 300L
        const val DEFAULT_RETRY_SEC = 5L
        const val MAX_RETRY_SEC = 600L
    }
}
