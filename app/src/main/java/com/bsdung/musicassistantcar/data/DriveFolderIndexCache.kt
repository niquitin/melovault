package com.bsdung.musicassistantcar.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

data class CompactIndexTrack(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Double? = null,
    val driveFileId: Int? = null,
) {
    fun toCarTrack(): CarTrack = CarTrack(
        id = id,
        title = title,
        artist = artist,
        album = album,
        status = "ready",
        duration = duration,
    )

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("title", title)
        obj.put("artist", artist)
        obj.put("album", album)
        if (duration != null) obj.put("duration", duration)
        if (driveFileId != null) obj.put("driveFileId", driveFileId)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): CompactIndexTrack {
            return CompactIndexTrack(
                id = obj.optInt("id"),
                title = obj.optString("title", ""),
                artist = obj.optString("artist", ""),
                album = obj.optString("album", ""),
                duration = if (obj.has("duration") && !obj.isNull("duration")) obj.optDouble("duration") else null,
                driveFileId = if (obj.has("driveFileId") && !obj.isNull("driveFileId")) obj.optInt("driveFileId") else null,
            )
        }
    }
}

object DriveFolderIndexCache {
    private const val TAG = "DriveFolderIndexCache"
    private const val CACHE_DIR_NAME = "drive_index_cache"

    fun cacheKey(root: Int, path: String): String {
        val cleanPath = path.trim().replace('\\', '/').trim('/')
        val safePath = if (cleanPath.isEmpty()) "" else cleanPath.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "root_${root}_$safePath"
    }

    fun getCacheFile(context: Context, root: Int, path: String): File {
        val dir = File(context.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${cacheKey(root, path)}.json")
    }

    /**
     * Loads index for the given root and path.
     * 1. Checks disk cache (filesDir/drive_index_cache/root_{root}_{path}.json)
     * 2. Checks bundled assets (drive_index_cache/root_{root}_{path}.json)
     * Returns the list of CompactIndexTrack if found, or null on cache miss.
     */
    fun loadIndex(context: Context, root: Int, path: String): List<CompactIndexTrack>? {
        val diskFile = getCacheFile(context, root, path)
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                FileInputStream(diskFile).use { input ->
                    val tracks = parseIndexStream(input)
                    if (tracks.isNotEmpty()) {
                        Log.i(TAG, "Cache HIT (disk): root=$root, path='$path', items=${tracks.size}")
                        return tracks
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read disk cache for root=$root, path='$path': ${e.message}")
            }
        }

        // Try bundled assets
        val assetPath = "$CACHE_DIR_NAME/${cacheKey(root, path)}.json"
        try {
            context.assets.open(assetPath).use { input ->
                val tracks = parseIndexStream(input)
                if (tracks.isNotEmpty()) {
                    Log.i(TAG, "Cache HIT (asset): root=$root, path='$path', items=${tracks.size}")
                    // Asynchronously save to disk cache
                    saveIndexAsync(context, root, path, tracks)
                    return tracks
                }
            }
        } catch (ignored: Exception) {
            // Not found in assets
        }

        Log.i(TAG, "Cache MISS: root=$root, path='$path'")
        return null
    }

    fun saveIndex(context: Context, root: Int, path: String, tracks: List<CompactIndexTrack>) {
        try {
            val file = getCacheFile(context, root, path)
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(tempFile).use { fos ->
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write("[\n")
                    tracks.forEachIndexed { index, track ->
                        writer.write(track.toJson().toString())
                        if (index < tracks.size - 1) {
                            writer.write(",\n")
                        } else {
                            writer.write("\n")
                        }
                    }
                    writer.write("]")
                }
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            Log.i(TAG, "Saved index to disk: root=$root, path='$path', items=${tracks.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save index to disk for root=$root, path='$path': ${e.message}", e)
        }
    }

    fun saveIndexAsync(context: Context, root: Int, path: String, tracks: List<CompactIndexTrack>) {
        Thread {
            saveIndex(context, root, path, tracks)
        }.start()
    }

    fun parseIndexStream(input: InputStream): List<CompactIndexTrack> {
        val text = input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        val array = JSONArray(text)
        val result = ArrayList<CompactIndexTrack>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(CompactIndexTrack.fromJson(obj))
        }
        return result
    }

    /**
     * Crawls a drive folder tree recursively via [CarApiClient.driveBrowseWithMetadata],
     * invoking [onFirstBatchReady] as soon as the first tracks are discovered so audio can start immediately.
     */
    fun crawlAndIndex(
        api: CarApiClient,
        root: Int,
        startPath: String,
        onFirstBatchReady: ((List<CompactIndexTrack>) -> Unit)? = null,
    ): List<CompactIndexTrack> {
        val discoveredTracks = mutableListOf<CompactIndexTrack>()
        val foldersToVisit = ArrayDeque<String>()
        foldersToVisit.add(startPath)
        var firstBatchReported = false

        while (!foldersToVisit.isEmpty()) {
            val currentFolder = foldersToVisit.removeFirst()
            try {
                var offset = 0
                val limit = 100
                while (true) {
                    val browseRes = api.driveBrowseWithMetadata(
                        root = root,
                        path = currentFolder,
                        limit = limit,
                        offset = offset,
                    )
                    if (browseRes.items.isEmpty()) break

                    for (item in browseRes.items) {
                        if (item.kind == "folder") {
                            foldersToVisit.add(item.path)
                        } else if (item.kind == "track") {
                            val trackId = item.trackId ?: item.driveFileId ?: 0
                            if (trackId > 0) {
                                discoveredTracks.add(
                                    CompactIndexTrack(
                                        id = trackId,
                                        title = item.title.ifBlank { item.name },
                                        artist = item.artist,
                                        album = item.album,
                                        driveFileId = item.driveFileId,
                                    )
                                )
                            }
                        }
                    }

                    if (!firstBatchReported && discoveredTracks.size >= 25) {
                        firstBatchReported = true
                        onFirstBatchReady?.invoke(discoveredTracks.toList())
                    }

                    offset += browseRes.items.size
                    if (browseRes.total > 0 && offset >= browseRes.total) break
                    if (browseRes.items.size < limit) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error crawling folder '$currentFolder' in root $root: ${e.message}")
            }
        }

        if (!firstBatchReported && discoveredTracks.isNotEmpty()) {
            onFirstBatchReady?.invoke(discoveredTracks.toList())
        }

        return discoveredTracks
    }
}
