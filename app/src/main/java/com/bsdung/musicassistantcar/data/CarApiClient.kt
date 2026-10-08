package com.bsdung.musicassistantcar.data

import android.net.Uri
import com.bsdung.musicassistantcar.BuildConfig
import com.bsdung.musicassistantcar.lyrics.LrcParser
import com.bsdung.musicassistantcar.lyrics.LyricsCandidate
import com.bsdung.musicassistantcar.lyrics.LyricsCandidatesResult
import com.bsdung.musicassistantcar.lyrics.LyricsLookupResult
import com.bsdung.musicassistantcar.lyrics.LyricsMatchedTrack
import com.bsdung.musicassistantcar.lyrics.LyricsStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class CarPlaylist(
    val id: Int,
    val name: String,
    val description: String,
    val trackCount: Int,
)

data class BatchResult(
    val added: Int,
    val skipped: Int,
    val removed: Int,
    val total: Int,
)

data class CollectionActionResult(
    val matched: Int,
    val added: Int,
    val skipped: Int,
    val total: Int,
)

data class CollectionPlaybackResult(
    val items: List<CarTrack>,
    val total: Int,
    val playable: Int,
    val skipped: Int,
)

data class CarTrack(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val status: String,
    val duration: Double?,
    val sourceFormat: String? = null,
    val sourceSizeBytes: Long? = null,
    val sourceCodec: String? = null,
    val sourceBitrate: Int? = null,
    val sourceSampleRate: Int? = null,
    val sourceChannels: Int? = null,
    val sourceType: String? = null,
    val loudnessLufs: Double? = null,
    val truePeakDbtp: Double? = null,
    val loudnessRangeLu: Double? = null,
    val loudnessAnalysisVersion: Int? = null,
)

data class DriveRoot(
    val index: Int,
    val name: String,
)

data class DriveBrowseItem(
    val kind: String,
    val name: String,
    val root: Int,
    val path: String,
    val driveFileId: Int?,
    val trackId: Int?,
    val title: String,
    val artist: String,
    val album: String,
    val format: String? = null,
    val sizeBytes: Long? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
)

data class DriveBrowseResult(
    val items: List<DriveBrowseItem>,
    val total: Int,
    val trackCount: Int? = null,
    val folderCount: Int? = null,
)

data class MediaTechnicalInfo(
    val mediaId: String,
    val format: String? = null,
    val codec: String? = null,
    val sampleRate: Int? = null,
    val bitrate: Int? = null,
    val channels: Int? = null,
    val bitsPerSample: Int? = null,
    val sizeBytes: Long? = null,
)

data class MediaTechnicalSummary(
    val format: String? = null,
    val codec: String? = null,
    val sampleRate: Int? = null,
    val bitrate: Int? = null,
    val channels: Int? = null,
    val bitsPerSample: Int? = null,
    val sizeBytes: Long? = null,
)

data class SignalPathInfo(
    val mediaId: String,
    val sourceType: String? = null,
    val sourceLabel: String? = null,
    val sourceFormat: String? = null,
    val sourceCodec: String? = null,
    val sourceSampleRate: Int? = null,
    val sourceBitrate: Int? = null,
    val sourceChannels: Int? = null,
    val sourceSizeBytes: Long? = null,
    val quality: String? = null,
    val serverAction: String? = null,
    val serverActionLabel: String? = null,
    val deliveryFormat: String? = null,
    val deliveryCodec: String? = null,
    val deliverySampleRate: Int? = null,
    val deliveryBitrate: Int? = null,
    val losslessSource: Boolean? = null,
    val losslessDelivery: Boolean? = null,
    val compatibilityConversion: Boolean = false,
    val compatibilityFrom: String? = null,
    val compatibilityTo: String? = null,
)

open class CarApiClient {
    private fun cleanString(
        item: JSONObject,
        key: String,
        fallback: String = "",
    ): String {
        if (!item.has(key) || item.isNull(key)) {
            return fallback
        }
        val value = item.optString(key, fallback).trim()
        return if (value.equals("null", ignoreCase = true)) {
            fallback
        } else {
            value
        }
    }

    private val endpoints = listOf(
        BuildConfig.CAR_API_PRIMARY.trimEnd('/'),
        BuildConfig.CAR_API_SECONDARY.trimEnd('/'),
    ).filter { it.isNotBlank() }.distinct()

    @Volatile
    private var activeBaseUrl: String? = null

    private fun orderedEndpoints(): List<String> = buildList {
        activeBaseUrl?.let(::add)
        endpoints.forEach { endpoint ->
            if (endpoint != activeBaseUrl) {
                add(endpoint)
            }
        }
    }

    open fun hasValidConfiguration(): Boolean =
        endpoints.isNotEmpty()

    fun baseUrl(): String =
        activeBaseUrl ?: probe()

    fun activeEndpoint(): String? =
        activeBaseUrl

    fun probe(): String {
        var lastError: Exception? = null
        for (endpoint in orderedEndpoints()) {
            try {
                val connection = open(
                    "$endpoint/health",
                    authenticated = false,
                    connectTimeoutMs = 1800,
                    readTimeoutMs = 1800,
                )
                connection.useResponse { code, body ->
                    if (code in 200..299) {
                        activeBaseUrl = endpoint
                        return endpoint
                    }
                    lastError = IOException(
                        "Health $endpoint returned $code: $body"
                    )
                }
            } catch (exc: Exception) {
                lastError = exc
            }
        }
        activeBaseUrl = null
        throw IOException(
            "Không kết nối được MeloVault backend",
            lastError,
        )
    }

    open fun playlists(): List<CarPlaylist> {
        val json = getJson("/v1/playlists")
        val items = json.getJSONArray("items")
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    CarPlaylist(
                        id = item.getInt("id"),
                        name = item.optString("name"),
                        description = nullableString(
                            item,
                            "description",
                        ),
                        trackCount = item.optInt(
                            "track_count",
                            0,
                        ),
                    )
                )
            }
        }
    }

    fun recent(
        limit: Int,
        offset: Int,
    ): List<CarTrack> =
        tracksFrom(
            "/v1/recent?limit=$limit&offset=$offset"
        )

    fun sourceTracks(
        source: String,
        limit: Int,
        offset: Int,
    ): List<CarTrack> =
        tracksFrom(
            "/v1/source/$source?limit=$limit&offset=$offset"
        )

    fun driveRoots(): List<DriveRoot> {
        val items = getJson("/v1/drive/roots")
            .getJSONArray("items")
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    DriveRoot(
                        index = item.getInt("index"),
                        name = item.optString("name"),
                    )
                )
            }
        }
    }

    fun driveBrowse(
        root: Int,
        path: String,
        limit: Int,
        offset: Int,
        sort: String = "",
    ): List<DriveBrowseItem> = driveBrowseWithTotal(root, path, limit, offset, sort).first

    fun driveBrowseWithMetadata(
        root: Int,
        path: String,
        limit: Int,
        offset: Int,
        sort: String = "",
    ): DriveBrowseResult {
        val encodedPath = Uri.encode(path)
        val sortParam = if (sort.isNotBlank()) "&sort=${Uri.encode(sort)}" else ""
        val json = getJson(
            "/v1/drive/browse" +
                "?root=$root" +
                "&path=$encodedPath" +
                "&limit=$limit" +
                "&offset=$offset" +
                sortParam
        )
        val items = json.getJSONArray("items")
        val total = json.optInt("total", items.length())
        val trackCount = if (json.has("track_count") && !json.isNull("track_count")) json.getInt("track_count") else null
        val folderCount = if (json.has("folder_count") && !json.isNull("folder_count")) json.getInt("folder_count") else null
        val itemsList = buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val driveFileId = if (
                    item.has("drive_file_id") &&
                    !item.isNull("drive_file_id")
                ) {
                    item.getInt("drive_file_id")
                } else {
                    null
                }
                val trackId = if (
                    item.has("track_id") &&
                    !item.isNull("track_id")
                ) {
                    item.getInt("track_id")
                } else {
                    null
                }
                val format = cleanString(item, "format").takeIf { it.isNotBlank() }
                val sizeBytes = if (item.has("size_bytes") && !item.isNull("size_bytes")) {
                    item.optLong("size_bytes")
                } else {
                    null
                }
                val codec = cleanString(item, "codec").takeIf { it.isNotBlank() }
                val bitrate = if (item.has("bitrate") && !item.isNull("bitrate")) {
                    item.optInt("bitrate")
                } else {
                    null
                }
                val sampleRate = if (item.has("sample_rate") && !item.isNull("sample_rate")) {
                    item.optInt("sample_rate")
                } else {
                    null
                }
                val channels = if (item.has("channels") && !item.isNull("channels")) {
                    item.optInt("channels")
                } else {
                    null
                }
                add(
                    DriveBrowseItem(
                        kind = item.optString("kind"),
                        name = item.optString("name"),
                        root = item.optInt("root", root),
                        path = item.optString("path"),
                        driveFileId = driveFileId,
                        trackId = trackId,
                        title = cleanString(item, "title"),
                        artist = cleanString(item, "artist"),
                        album = cleanString(item, "album"),
                        format = format,
                        sizeBytes = sizeBytes,
                        codec = codec,
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        channels = channels,
                    )
                )
            }
        }
        return DriveBrowseResult(itemsList, total, trackCount, folderCount)
    }

    fun driveBrowseWithTotal(
        root: Int,
        path: String,
        limit: Int,
        offset: Int,
        sort: String = "",
    ): Pair<List<DriveBrowseItem>, Int> {
        val res = driveBrowseWithMetadata(root, path, limit, offset, sort)
        return Pair(res.items, res.total)
    }

    fun driveFileStreamUri(driveFileId: Int): Uri =
        Uri.parse(
            "${baseUrl()}/v1/drive-files/$driveFileId/stream" +
                "?quality=${PlaybackQuality.current.queryValue}"
        )

    fun driveFileCoverUri(driveFileId: Int): Uri =
        Uri.parse(
            "${baseUrl()}/v1/drive-files/$driveFileId/cover"
        )

    fun driveFile(driveFileId: Int): DriveBrowseItem {
        val item = getJson("/v1/drive-files/$driveFileId")
        val trackId = if (item.has("track_id") && !item.isNull("track_id")) {
            item.getInt("track_id")
        } else {
            null
        }
        val format = cleanString(item, "format").takeIf { it.isNotBlank() }
        val sizeBytes = if (item.has("size_bytes") && !item.isNull("size_bytes")) {
            item.optLong("size_bytes")
        } else {
            null
        }
        val codec = cleanString(item, "codec").takeIf { it.isNotBlank() }
        val bitrate = if (item.has("bitrate") && !item.isNull("bitrate")) {
            item.optInt("bitrate")
        } else {
            null
        }
        val sampleRate = if (item.has("sample_rate") && !item.isNull("sample_rate")) {
            item.optInt("sample_rate")
        } else {
            null
        }
        val channels = if (item.has("channels") && !item.isNull("channels")) {
            item.optInt("channels")
        } else {
            null
        }
        return DriveBrowseItem(
            kind = "track",
            name = item.optString("name"),
            root = 0,
            path = item.optString("drive_path"),
            driveFileId = driveFileId,
            trackId = trackId,
            title = cleanString(item, "title"),
            artist = cleanString(item, "artist"),
            album = cleanString(item, "album"),
            format = format,
            sizeBytes = sizeBytes,
            codec = codec,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
        )
    }

    fun playlistTracks(
        playlistId: Int,
        limit: Int,
        offset: Int,
    ): List<CarTrack> = playlistTracksWithTotal(playlistId, limit, offset).first

    open fun playlistTracksWithTotal(
        playlistId: Int,
        limit: Int,
        offset: Int,
    ): Pair<List<CarTrack>, Int> {
        val json = getJson(
            "/v1/playlists/$playlistId/tracks" +
                "?limit=$limit&offset=$offset"
        )
        val arr = json.optJSONArray("items") ?: org.json.JSONArray()
        val plObj = json.optJSONObject("playlist")
        val total = plObj?.optInt("track_count") ?: arr.length()
        return Pair(parseTracks(arr), total)
    }

    fun artistTracks(
        artist: String,
        limit: Int = 100,
        offset: Int = 0,
        sort: String = "",
    ): List<CarTrack> = artistTracksWithTotal(artist, limit, offset, sort).first

    fun artistTracksWithTotal(
        artist: String,
        limit: Int = 100,
        offset: Int = 0,
        sort: String = "",
    ): Pair<List<CarTrack>, Int> {
        val encoded = Uri.encode(artist)
        val sortParam = if (sort.isNotBlank()) "&sort=${Uri.encode(sort)}" else ""
        val json = getJson("/v1/collection/tracks?kind=artist&artist=$encoded&limit=$limit&offset=$offset$sortParam")
        val arr = json.optJSONArray("items") ?: org.json.JSONArray()
        val total = json.optInt("total", arr.length())
        return Pair(parseTracks(arr), total)
    }

    fun albumTracks(
        album: String,
        artist: String = "",
        limit: Int = 100,
        offset: Int = 0,
        sort: String = "",
    ): List<CarTrack> = albumTracksWithTotal(album, artist, limit, offset, sort).first

    fun albumTracksWithTotal(
        album: String,
        artist: String = "",
        limit: Int = 100,
        offset: Int = 0,
        sort: String = "",
    ): Pair<List<CarTrack>, Int> {
        val encAlbum = Uri.encode(album)
        val encArtist = Uri.encode(artist)
        val sortParam = if (sort.isNotBlank()) "&sort=${Uri.encode(sort)}" else ""
        val json = getJson("/v1/collection/tracks?kind=album&album=$encAlbum&artist=$encArtist&limit=$limit&offset=$offset$sortParam")
        val arr = json.optJSONArray("items") ?: org.json.JSONArray()
        val total = json.optInt("total", arr.length())
        return Pair(parseTracks(arr), total)
    }

    fun search(
        query: String,
        limit: Int,
        offset: Int,
    ): List<CarTrack> {
        val encoded = Uri.encode(query)
        return tracksFrom(
            "/v1/search?q=$encoded&limit=$limit&offset=$offset"
        )
    }

    open fun track(trackId: Int): CarTrack {
        val item = getJson("/v1/tracks/$trackId")
        return parseTrack(item)
    }

    open fun streamUri(trackId: Int): Uri =
        Uri.parse(
            "${baseUrl()}/v1/tracks/$trackId/stream" +
                "?quality=${PlaybackQuality.current.queryValue}"
        )

    fun trackCoverUri(trackId: Int): Uri =
        Uri.parse(
            "${baseUrl()}/v1/tracks/$trackId/cover"
        )

    fun authorizationHeaders(): Map<String, String> =
        mapOf(
            "Authorization" to
                "Bearer ${BuildConfig.CAR_API_TOKEN}"
        )

    fun prewarm(
        mediaId: String,
        quality: StreamQuality = PlaybackQuality.current,
    ): Boolean {
        val body = JSONObject()
            .put("media_id", mediaId)
            .put("quality", quality.queryValue)
        val json = requestJson(
            "POST",
            "/v1/prewarm",
            body,
            readTimeoutMs = 60_000,
        )
        return json.optString("status") == "ok"
    }

    fun getFavoriteStatus(mediaId: String): Boolean {
        val encoded = Uri.encode(mediaId)
        val json = getJson("/v1/favorites/status?media_id=$encoded")
        return json.optBoolean("favorite", false)
    }

    fun getMediaInfo(mediaId: String): MediaTechnicalInfo? {
        val encoded = Uri.encode(mediaId)
        val json = try {
            getJson("/v1/media-info?media_id=$encoded")
        } catch (_: Exception) {
            return null
        }
        val format = cleanString(json, "format").takeIf { it.isNotBlank() }
        val codec = cleanString(json, "codec").takeIf { it.isNotBlank() }
        val sampleRate = if (json.has("sample_rate") && !json.isNull("sample_rate")) {
            json.optInt("sample_rate").takeIf { it > 0 }
        } else null
        val bitrate = if (json.has("bitrate") && !json.isNull("bitrate")) {
            json.optInt("bitrate").takeIf { it > 0 }
        } else null
        val channels = if (json.has("channels") && !json.isNull("channels")) {
            json.optInt("channels").takeIf { it > 0 }
        } else null
        val bitsPerSample = if (json.has("bits_per_sample") && !json.isNull("bits_per_sample")) {
            json.optInt("bits_per_sample").takeIf { it > 0 }
        } else null
        val sizeBytes = if (json.has("size_bytes") && !json.isNull("size_bytes")) {
            json.optLong("size_bytes").takeIf { it > 0 }
        } else null

        return MediaTechnicalInfo(
            mediaId = json.optString("media_id", mediaId),
            format = format,
            codec = codec,
            sampleRate = sampleRate,
            bitrate = bitrate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            sizeBytes = sizeBytes,
        )
    }

    fun getSignalPath(mediaId: String, quality: String = "original"): SignalPathInfo? {
        val encodedMediaId = Uri.encode(mediaId)
        val encodedQuality = Uri.encode(quality)
        val json = try {
            getJson("/v1/signal-path?media_id=$encodedMediaId&quality=$encodedQuality")
        } catch (_: Exception) {
            return null
        }

        fun optIntOrNull(key: String): Int? {
            return if (json.has(key) && !json.isNull(key)) {
                json.optInt(key).takeIf { it > 0 }
            } else null
        }

        fun optLongOrNull(key: String): Long? {
            return if (json.has(key) && !json.isNull(key)) {
                json.optLong(key).takeIf { it > 0 }
            } else null
        }

        fun optBoolOrNull(key: String): Boolean? {
            return if (json.has(key) && !json.isNull(key)) {
                json.optBoolean(key)
            } else null
        }

        return SignalPathInfo(
            mediaId = json.optString("media_id", mediaId),
            sourceType = cleanString(json, "source_type").takeIf { it.isNotBlank() },
            sourceLabel = cleanString(json, "source_label").takeIf { it.isNotBlank() },
            sourceFormat = cleanString(json, "source_format").takeIf { it.isNotBlank() },
            sourceCodec = cleanString(json, "source_codec").takeIf { it.isNotBlank() },
            sourceSampleRate = optIntOrNull("source_sample_rate"),
            sourceBitrate = optIntOrNull("source_bitrate"),
            sourceChannels = optIntOrNull("source_channels"),
            sourceSizeBytes = optLongOrNull("source_size_bytes"),
            quality = cleanString(json, "quality").takeIf { it.isNotBlank() },
            serverAction = cleanString(json, "server_action").takeIf { it.isNotBlank() },
            serverActionLabel = cleanString(json, "server_action_label").takeIf { it.isNotBlank() },
            deliveryFormat = cleanString(json, "delivery_format").takeIf { it.isNotBlank() },
            deliveryCodec = cleanString(json, "delivery_codec").takeIf { it.isNotBlank() },
            deliverySampleRate = optIntOrNull("delivery_sample_rate"),
            deliveryBitrate = optIntOrNull("delivery_bitrate"),
            losslessSource = optBoolOrNull("lossless_source"),
            losslessDelivery = optBoolOrNull("lossless_delivery"),
            compatibilityConversion = json.optBoolean("compatibility_conversion", false),
            compatibilityFrom = cleanString(json, "compatibility_from").takeIf { it.isNotBlank() },
            compatibilityTo = cleanString(json, "compatibility_to").takeIf { it.isNotBlank() },
        )
    }

    fun getMediaInfoBatch(mediaIds: List<String>): Map<String, MediaTechnicalSummary> {
        if (mediaIds.isEmpty()) return emptyMap()
        val boundedIds = mediaIds.distinct().take(50)
        val array = JSONArray()
        boundedIds.forEach { array.put(it) }
        val body = JSONObject().put("media_ids", array)
        val json = requestJson("POST", "/v1/media-info/batch", body)
        val itemsObj = json.optJSONObject("items") ?: return emptyMap()

        val result = mutableMapOf<String, MediaTechnicalSummary>()
        val keys = itemsObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val itemJson = itemsObj.optJSONObject(key) ?: continue
            result[key] = MediaTechnicalSummary(
                format = itemJson.optString("format").takeIf { it.isNotBlank() && it != "null" },
                codec = itemJson.optString("codec").takeIf { it.isNotBlank() && it != "null" },
                sampleRate = if (itemJson.has("sample_rate") && !itemJson.isNull("sample_rate")) itemJson.optInt("sample_rate").takeIf { it > 0 } else null,
                bitrate = if (itemJson.has("bitrate") && !itemJson.isNull("bitrate")) itemJson.optInt("bitrate").takeIf { it > 0 } else null,
                channels = if (itemJson.has("channels") && !itemJson.isNull("channels")) itemJson.optInt("channels").takeIf { it > 0 } else null,
                bitsPerSample = if (itemJson.has("bits_per_sample") && !itemJson.isNull("bits_per_sample")) itemJson.optInt("bits_per_sample").takeIf { it > 0 } else null,
                sizeBytes = if (itemJson.has("size_bytes") && !itemJson.isNull("size_bytes")) itemJson.optLong("size_bytes").takeIf { it > 0L } else null,
            )
        }
        return result
    }

    fun toggleFavorite(mediaId: String): Boolean {
        val body = JSONObject().put("media_id", mediaId)
        val json = requestJson("POST", "/v1/favorites/toggle", body)
        return json.optBoolean("favorite", false)
    }

    fun setFavorite(mediaId: String, favorite: Boolean): Boolean {
        val body = JSONObject().put("media_id", mediaId).put("favorite", favorite)
        val json = requestJson("POST", "/v1/favorites", body)
        return json.optBoolean("favorite", false)
    }

    fun createPlaylist(name: String, description: String = ""): CarPlaylist {
        val body = JSONObject().put("name", name).put("description", description)
        val json = requestJson("POST", "/v1/playlists", body)
        return CarPlaylist(
            id = json.getInt("id"),
            name = json.optString("name", name),
            description = json.optString("description", description),
            trackCount = json.optInt("track_count", 0),
        )
    }

    fun addToPlaylist(playlistId: Int, mediaId: String): Boolean {
        val body = JSONObject().put("media_id", mediaId)
        val json = requestJson("POST", "/v1/playlists/$playlistId/tracks", body)
        return json.optBoolean("added", true)
    }

    fun reorderPlaylist(playlistId: Int, mediaIds: List<String>, offset: Int? = null): Boolean {
        val array = JSONArray()
        mediaIds.forEach { array.put(it) }
        val body = JSONObject().put("media_ids", array)
        if (offset != null) {
            body.put("offset", offset)
        }
        val json = requestJson("PUT", "/v1/playlists/$playlistId/tracks/order", body)
        return json.optString("status") == "ok"
    }

    fun batchAddToPlaylist(playlistId: Int, mediaIds: List<String>): BatchResult {
        val array = JSONArray()
        mediaIds.forEach { array.put(it) }
        val body = JSONObject().put("media_ids", array)
        val json = requestJson("POST", "/v1/playlists/$playlistId/tracks/batch", body)
        return BatchResult(
            added = json.optInt("added", 0),
            skipped = json.optInt("skipped", 0),
            removed = json.optInt("removed", 0),
            total = json.optInt("total", 0),
        )
    }

    fun batchRemoveFromPlaylist(playlistId: Int, mediaIds: List<String>): BatchResult {
        val array = JSONArray()
        mediaIds.forEach { array.put(it) }
        val body = JSONObject().put("media_ids", array)
        val json = requestJson("POST", "/v1/playlists/$playlistId/tracks/remove-batch", body)
        return BatchResult(
            added = json.optInt("added", 0),
            skipped = json.optInt("skipped", 0),
            removed = json.optInt("removed", 0),
            total = json.optInt("total", 0),
        )
    }

    fun addCollectionToPlaylist(
        playlistId: Int,
        kind: String,
        artist: String? = null,
        album: String? = null,
        root: Int? = null,
        path: String? = null,
    ): CollectionActionResult {
        val body = JSONObject().put("kind", kind)
        if (artist != null) body.put("artist", artist)
        if (album != null) body.put("album", album)
        if (root != null) body.put("root", root)
        if (path != null) body.put("path", path)
        val json = requestJson("POST", "/v1/playlists/$playlistId/add-collection", body)
        return CollectionActionResult(
            matched = json.optInt("matched", 0),
            added = json.optInt("added", 0),
            skipped = json.optInt("skipped", 0),
            total = json.optInt("playlist_total", 0),
        )
    }

    fun renamePlaylist(playlistId: Int, newName: String): CarPlaylist {
        val body = JSONObject().put("name", newName)
        val json = requestJson("PUT", "/v1/playlists/$playlistId", body)
        return CarPlaylist(
            id = json.optInt("id", playlistId),
            name = json.optString("name", newName),
            description = json.optString("description", ""),
            trackCount = json.optInt("track_count", 0),
        )
    }

    fun removeFromPlaylist(playlistId: Int, mediaId: String): Boolean {
        val encoded = Uri.encode(mediaId)
        val json = requestJson("DELETE", "/v1/playlists/$playlistId/tracks?media_id=$encoded")
        return json.optBoolean("removed", false)
    }

    fun reportPlay(mediaId: String): Int {
        val body = JSONObject().put("media_id", mediaId)
        val json = requestJson("POST", "/v1/history/play", body)
        return json.optInt("track_id", 0)
    }


    /**
     * Fetches ALL playable tracks for a collection (album / artist / drive_folder / playlist).
     * This single call is pagination-safe — the backend returns the complete set, no page cap.
     * Used by: Play All, Shuffle, Add All to Queue, Play From Here.
     *
     * Use a longer read timeout (60 s) since large playlists can take a moment.
     */
    open fun fetchCollectionPlayback(
        kind: String,
        artist: String? = null,
        album: String? = null,
        root: Int? = null,
        path: String? = null,
        playlistId: Int? = null,
        sort: String = "",
        recursive: Boolean? = null,
    ): CollectionPlaybackResult {
        val body = JSONObject().put("kind", kind)
        if (artist != null) body.put("artist", artist)
        if (album != null) body.put("album", album)
        if (root != null) body.put("root", root)
        if (path != null) body.put("path", path)
        if (playlistId != null) body.put("playlist_id", playlistId)
        if (sort.isNotBlank()) body.put("sort", sort)
        if (recursive != null) body.put("recursive", recursive)
        val json = requestJson("POST", "/v1/collection/playback", body, readTimeoutMs = 60_000)
        val arr = json.optJSONArray("items") ?: org.json.JSONArray()
        val tracks = parseTracks(arr)
        return CollectionPlaybackResult(
            items = tracks,
            total = json.optInt("total", tracks.size),
            playable = json.optInt("playable", tracks.size),
            skipped = json.optInt("skipped", 0),
        )
    }

    open fun lookupLyrics(
        trackKey: String,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long? = null,
        forceRefresh: Boolean = false,
        preferredProvider: String? = null,
        preferredRecordId: String? = null,
    ): LyricsLookupResult {
        val body = JSONObject().apply {
            put("track_key", trackKey)
            put("title", title)
            put("artist", artist)
            put("album", album)
            if (durationMs != null && durationMs > 0L) {
                put("duration_ms", durationMs)
            } else {
                put("duration_ms", JSONObject.NULL)
            }
            put("force_refresh", forceRefresh)
            if (!preferredProvider.isNullOrBlank()) {
                put("preferred_provider", preferredProvider)
            } else {
                put("preferred_provider", JSONObject.NULL)
            }
            if (!preferredRecordId.isNullOrBlank()) {
                put("preferred_record_id", preferredRecordId)
            } else {
                put("preferred_record_id", JSONObject.NULL)
            }
        }
        val json = requestJson(
            method = "POST",
            path = "/car/lyrics/lookup",
            bodyJson = body,
            connectTimeoutMs = 5_000,
            readTimeoutMs = 15_000,
        )
        return parseLyricsLookupResponse(json)
    }

    fun fetchLyricsCandidates(
        trackKey: String,
        title: String,
        artist: String,
        album: String = "",
        durationMs: Long? = null,
    ): LyricsCandidatesResult {
        val body = JSONObject().apply {
            put("track_id", trackKey)
            put("track_key", trackKey)
            put("title", title)
            put("artist", artist)
            put("album", album)
            if (durationMs != null && durationMs > 0L) {
                put("duration_ms", durationMs)
            } else {
                put("duration_ms", JSONObject.NULL)
            }
        }
        val json = requestJson(
            method = "POST",
            path = "/car/lyrics/candidates",
            bodyJson = body,
            connectTimeoutMs = 5_000,
            readTimeoutMs = 15_000,
        )
        return parseLyricsCandidatesResponse(json)
    }

    fun resolveLyricsByRecordId(
        provider: String = "LRCLIB",
        providerRecordId: String,
        forceRefresh: Boolean = false,
    ): LyricsLookupResult {
        val body = JSONObject().apply {
            put("provider", provider.trim().ifBlank { "LRCLIB" })
            put("provider_record_id", providerRecordId.trim())
            put("force_refresh", forceRefresh)
        }
        val json = requestJson(
            method = "POST",
            path = "/car/lyrics/resolve",
            bodyJson = body,
            connectTimeoutMs = 5_000,
            readTimeoutMs = 15_000,
        )
        return parseLyricsLookupResponse(json)
    }

    internal fun parseLyricsCandidate(c: JSONObject, defaultProvider: String = "lrclib"): LyricsCandidate? {
        val recId = cleanString(c, "provider_record_id").ifBlank { cleanString(c, "record_id") }
        if (recId.isBlank()) return null
        val hasSynced = if (c.has("has_synced_lyrics")) {
            c.optBoolean("has_synced_lyrics", false)
        } else {
            c.optBoolean("has_synced", false)
        }
        val hasPlain = if (c.has("has_plain_lyrics")) {
            c.optBoolean("has_plain_lyrics", true)
        } else {
            true
        }
        val confidence = c.optDouble("confidence", 0.0)
        val matchScore = if (c.has("match_score") && !c.isNull("match_score")) {
            c.optDouble("match_score", confidence * 100.0)
        } else {
            confidence * 100.0
        }
        return LyricsCandidate(
            provider = cleanString(c, "provider", defaultProvider).ifBlank { defaultProvider },
            recordId = recId,
            title = cleanString(c, "title"),
            artist = cleanString(c, "artist"),
            album = cleanString(c, "album"),
            durationMs = if (c.has("duration_ms") && !c.isNull("duration_ms")) c.optLong("duration_ms") else null,
            hasSynced = hasSynced,
            hasPlain = hasPlain,
            instrumental = c.optBoolean("instrumental", false),
            confidence = confidence,
            matchScore = matchScore,
            variantConflict = c.optBoolean("variant_conflict", false),
            durationConflict = c.optBoolean("duration_conflict", false),
        )
    }

    internal fun parseLyricsCandidatesResponse(json: JSONObject): LyricsCandidatesResult {
        val status = cleanString(json, "status", "OK").ifBlank { "OK" }.uppercase()
        val provider = cleanString(json, "provider", "lrclib").ifBlank { "lrclib" }
        val diagnostic = cleanString(json, "diagnostic").takeIf { it.isNotBlank() }
        val candidatesArr = json.optJSONArray("candidates") ?: JSONArray()
        val candidates = buildList {
            val count = minOf(candidatesArr.length(), 5)
            for (i in 0 until count) {
                val c = candidatesArr.optJSONObject(i) ?: continue
                val parsed = parseLyricsCandidate(c, provider) ?: continue
                add(parsed)
            }
        }
        return LyricsCandidatesResult(
            status = status,
            provider = provider,
            candidates = candidates,
            diagnostic = diagnostic,
        )
    }

    internal fun parseLyricsLookupResponse(json: JSONObject): LyricsLookupResult {
        val status = LyricsStatus.fromWire(cleanString(json, "status", "TEMPORARY_ERROR"))
        val provider = cleanString(json, "provider", "lrclib").ifBlank { "lrclib" }
        val providerRecordId = cleanString(json, "provider_record_id").takeIf { it.isNotBlank() }
        val confidence = json.optDouble("confidence", 0.0)
        val matchedObj = json.optJSONObject("matched_track")
        val matchedTrack = matchedObj?.let {
            LyricsMatchedTrack(
                title = cleanString(it, "title"),
                artist = cleanString(it, "artist"),
                album = cleanString(it, "album"),
                durationMs = if (it.has("duration_ms") && !it.isNull("duration_ms")) it.optLong("duration_ms") else null,
            )
        }
        val plainLyrics = cleanString(json, "plain_lyrics").takeIf { it.isNotBlank() }
        val syncedLyrics = cleanString(json, "synced_lyrics").takeIf { it.isNotBlank() }
        val instrumental = json.optBoolean("instrumental", status == LyricsStatus.INSTRUMENTAL)
        val diagnostic = cleanString(json, "diagnostic").takeIf { it.isNotBlank() }

        val candidatesArr = json.optJSONArray("candidates") ?: JSONArray()
        val candidates = buildList {
            val count = minOf(candidatesArr.length(), 5)
            for (i in 0 until count) {
                val c = candidatesArr.optJSONObject(i) ?: continue
                val parsed = parseLyricsCandidate(c, provider) ?: continue
                add(parsed)
            }
        }

        val syncedLines = if (status == LyricsStatus.SYNCED && !syncedLyrics.isNullOrBlank()) {
            LrcParser.parse(syncedLyrics)
        } else {
            emptyList()
        }

        val resolvedStatus = if (status == LyricsStatus.SYNCED && syncedLines.isEmpty()) {
            if (!plainLyrics.isNullOrBlank()) LyricsStatus.PLAIN else LyricsStatus.NOT_FOUND
        } else {
            status
        }

        return LyricsLookupResult(
            status = resolvedStatus,
            provider = provider,
            providerRecordId = providerRecordId,
            confidence = confidence,
            matchedTrack = matchedTrack,
            plainLyrics = plainLyrics,
            syncedLyrics = syncedLyrics,
            syncedLines = syncedLines,
            instrumental = instrumental,
            candidates = candidates,
            diagnostic = diagnostic,
            fromCache = false,
        )
    }

    fun getMusicAccounts(): List<AccountStatusDto> {
        val json = requestJson("GET", "/car/integrations/accounts")
        val array = json.optJSONArray("accounts") ?: org.json.JSONArray()
        val list = mutableListOf<AccountStatusDto>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(AccountStatusDto.fromJson(obj))
        }
        return list
    }

    fun getProviderStatus(provider: String): AccountStatusDto {
        val json = requestJson("GET", "/car/integrations/${provider.lowercase()}/status")
        return AccountStatusDto.fromJson(json)
    }

    fun disconnectAccount(provider: String): AccountStatusDto {
        val json = requestJson("DELETE", "/car/integrations/${provider.lowercase()}/connection")
        val accObj = json.optJSONObject("account") ?: json
        return AccountStatusDto.fromJson(accObj)
    }

    private fun requestJson(
        method: String,
        path: String,
        bodyJson: JSONObject? = null,
        connectTimeoutMs: Int = 4_000,
        readTimeoutMs: Int = 15_000,
    ): JSONObject {
        var lastError: Exception? = null
        val bodyBytes = bodyJson?.toString()?.toByteArray(Charsets.UTF_8)
        for (base in orderedEndpoints()) {
            try {
                val connection = URL("$base$path").openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.CAR_API_TOKEN}")
                if (bodyBytes != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(bodyBytes) }
                }
                val result = connection.useResponse { code, body ->
                    if (code !in 200..299) {
                        throw IOException("Backend $code at $base: $body")
                    }
                    if (body.isBlank()) JSONObject() else JSONObject(body)
                }
                activeBaseUrl = base
                return result
            } catch (exc: Exception) {
                lastError = exc
                if (activeBaseUrl == base) {
                    activeBaseUrl = null
                }
            }
        }
        throw IOException(
            "Không kết nối được MeloVault backend cho $method $path",
            lastError,
        )
    }

    private fun tracksFrom(path: String): List<CarTrack> {
        val array = getJson(path).getJSONArray("items")
        return parseTracks(array)
    }

    private fun parseTracks(array: JSONArray): List<CarTrack> =
        buildList {
            for (index in 0 until array.length()) {
                add(parseTrack(array.getJSONObject(index)))
            }
        }

    internal fun parseTrack(item: JSONObject): CarTrack {
        val sourceFormat = cleanString(item, "source_format").takeIf { it.isNotBlank() }
        val sourceSizeBytes = if (item.has("source_size_bytes") && !item.isNull("source_size_bytes")) {
            item.optLong("source_size_bytes")
        } else {
            null
        }
        val sourceCodec = cleanString(item, "source_codec").takeIf { it.isNotBlank() }
        val sourceBitrate = if (item.has("source_bitrate") && !item.isNull("source_bitrate")) {
            item.optInt("source_bitrate")
        } else {
            null
        }
        val sourceSampleRate = if (item.has("source_sample_rate") && !item.isNull("source_sample_rate")) {
            item.optInt("source_sample_rate")
        } else {
            null
        }
        val sourceChannels = if (item.has("source_channels") && !item.isNull("source_channels")) {
            item.optInt("source_channels")
        } else {
            null
        }
        val sourceType = cleanString(item, "source_type").takeIf { it.isNotBlank() }
        val loudnessLufs = if (item.has("loudness_lufs") && !item.isNull("loudness_lufs")) {
            runCatching { item.getDouble("loudness_lufs") }.getOrNull()
        } else {
            null
        }
        val truePeakDbtp = if (item.has("true_peak_dbtp") && !item.isNull("true_peak_dbtp")) {
            runCatching { item.getDouble("true_peak_dbtp") }.getOrNull()
        } else {
            null
        }
        val loudnessRangeLu = if (item.has("loudness_range_lu") && !item.isNull("loudness_range_lu")) {
            runCatching { item.getDouble("loudness_range_lu") }.getOrNull()
        } else {
            null
        }
        val loudnessAnalysisVersion = if (item.has("loudness_analysis_version") && !item.isNull("loudness_analysis_version")) {
            runCatching { item.getInt("loudness_analysis_version") }.getOrNull()
        } else {
            null
        }

        return CarTrack(
            id = item.getInt("id"),
            title = cleanString(
                item,
                "title",
                "Chưa xác định",
            ),
            artist = cleanString(item, "artist"),
            album = cleanString(item, "album"),
            status = item.optString("status", ""),
            duration = if (
                item.has("duration") &&
                !item.isNull("duration")
            ) {
                item.optDouble("duration")
            } else {
                null
            },
            sourceFormat = sourceFormat,
            sourceSizeBytes = sourceSizeBytes,
            sourceCodec = sourceCodec,
            sourceBitrate = sourceBitrate,
            sourceSampleRate = sourceSampleRate,
            sourceChannels = sourceChannels,
            sourceType = sourceType,
            loudnessLufs = loudnessLufs,
            truePeakDbtp = truePeakDbtp,
            loudnessRangeLu = loudnessRangeLu,
            loudnessAnalysisVersion = loudnessAnalysisVersion,
        )
    }

    internal fun parseTrack(map: Map<String, Any?>): CarTrack {
        fun cleanStr(key: String, fallback: String = ""): String {
            val v = map[key]?.toString()?.trim() ?: fallback
            return if (v.equals("null", ignoreCase = true)) fallback else v
        }

        val sourceFormat = cleanStr("source_format").takeIf { it.isNotBlank() }
        val sourceSizeBytes = (map["source_size_bytes"] as? Number)?.toLong()?.takeIf { it > 0 }
        val sourceCodec = cleanStr("source_codec").takeIf { it.isNotBlank() }
        val sourceBitrate = (map["source_bitrate"] as? Number)?.toInt()?.takeIf { it > 0 }
        val sourceSampleRate = (map["source_sample_rate"] as? Number)?.toInt()?.takeIf { it > 0 }
        val sourceChannels = (map["source_channels"] as? Number)?.toInt()?.takeIf { it > 0 }
        val sourceType = cleanStr("source_type").takeIf { it.isNotBlank() }
        val loudnessLufs = (map["loudness_lufs"] as? Number)?.toDouble()
        val truePeakDbtp = (map["true_peak_dbtp"] as? Number)?.toDouble()
        val loudnessRangeLu = (map["loudness_range_lu"] as? Number)?.toDouble()
        val loudnessAnalysisVersion = (map["loudness_analysis_version"] as? Number)?.toInt()

        return CarTrack(
            id = (map["id"] as? Number)?.toInt() ?: 0,
            title = cleanStr("title", "Chưa xác định"),
            artist = cleanStr("artist"),
            album = cleanStr("album"),
            status = cleanStr("status"),
            duration = (map["duration"] as? Number)?.toDouble(),
            sourceFormat = sourceFormat,
            sourceSizeBytes = sourceSizeBytes,
            sourceCodec = sourceCodec,
            sourceBitrate = sourceBitrate,
            sourceSampleRate = sourceSampleRate,
            sourceChannels = sourceChannels,
            sourceType = sourceType,
            loudnessLufs = loudnessLufs,
            truePeakDbtp = truePeakDbtp,
            loudnessRangeLu = loudnessRangeLu,
            loudnessAnalysisVersion = loudnessAnalysisVersion,
        )
    }

    internal fun parseTrackJson(json: String): CarTrack {
        val pattern = Regex(""""([^"]+)"\s*:\s*(?:"([^"]*)"|(null)|(true|false)|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?))""")
        val result = mutableMapOf<String, Any?>()
        pattern.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val strVal = match.groups[2]?.value
            val nullVal = match.groups[3]?.value
            val boolVal = match.groups[4]?.value
            val numVal = match.groups[5]?.value
            when {
                strVal != null -> result[key] = strVal
                nullVal != null -> result[key] = null
                boolVal != null -> result[key] = boolVal.toBoolean()
                numVal != null -> {
                    result[key] = if (numVal.contains(".") || numVal.contains("e") || numVal.contains("E")) {
                        numVal.toDoubleOrNull()
                    } else {
                        numVal.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
                    }
                }
            }
        }
        return parseTrack(result)
    }

    private fun getJson(path: String): JSONObject {
        var lastError: Exception? = null
        for (base in orderedEndpoints()) {
            try {
                val connection = open(
                    "$base$path",
                    authenticated = true,
                    connectTimeoutMs = 4000,
                    readTimeoutMs = 15000,
                )
                val result = connection.useResponse { code, body ->
                    if (code !in 200..299) {
                        throw IOException(
                            "Backend $code at $base: $body"
                        )
                    }
                    JSONObject(body)
                }
                activeBaseUrl = base
                return result
            } catch (exc: Exception) {
                lastError = exc
                if (activeBaseUrl == base) {
                    activeBaseUrl = null
                }
            }
        }
        throw IOException(
            "Không kết nối được MeloVault backend cho $path",
            lastError,
        )
    }

    private fun open(
        url: String,
        authenticated: Boolean,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): HttpURLConnection {
        val connection =
            URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty(
            "Accept",
            "application/json",
        )
        if (authenticated) {
            connection.setRequestProperty(
                "Authorization",
                "Bearer ${BuildConfig.CAR_API_TOKEN}",
            )
        }
        return connection
    }

    private inline fun <T> HttpURLConnection.useResponse(
        block: (Int, String) -> T,
    ): T {
        try {
            val code = responseCode
            val stream = if (code >= 400) {
                errorStream
            } else {
                inputStream
            }
            val body = stream?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            return block(code, body)
        } finally {
            disconnect()
        }
    }

    private fun nullableString(
        item: JSONObject,
        key: String,
    ): String =
        if (item.has(key) && !item.isNull(key)) {
            item.optString(key)
        } else {
            ""
        }
}
