package com.bsdung.musicassistantcar.ui.util

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.disk.DiskCache
import coil.load
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.transform.RoundedCornersTransformation
import com.bsdung.musicassistantcar.R
import com.bsdung.musicassistantcar.data.RemoteCatalog

object ArtworkLoader {
    private val headers = RemoteCatalog().authorizationHeaders()
    private var customImageLoader: ImageLoader? = null

    private fun getImageLoader(context: Context): ImageLoader {
        return customImageLoader ?: synchronized(this) {
            customImageLoader ?: ImageLoader.Builder(context.applicationContext)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.12) // 12% memory cache for FYT automotive stability
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("coil_artwork_cache"))
                        .maxSizeBytes(50L * 1024 * 1024) // 50MB disk cache
                        .build()
                }
                .crossfade(200)
                .respectCacheHeaders(false)
                .build().also { customImageLoader = it }
        }
    }

    fun prefetchArtwork(context: Context, uri: Any?) {
        if (uri == null) return
        val uriStr = uri.toString()
        if (uriStr.isBlank()) return
        val loader = getImageLoader(context)
        val request = ImageRequest.Builder(context.applicationContext)
            .data(uri)
            .apply {
                headers.forEach { (key, value) ->
                    setHeader(key, value)
                }
            }
            .build()
        loader.enqueue(request)
    }

    fun loadArtwork(
        imageView: ImageView,
        uri: String?,
        cornerRadiusDp: Float = 14f,
        placeholderResId: Int = R.drawable.ic_album_placeholder,
    ) {
        if (uri.isNullOrBlank()) {
            imageView.setImageResource(placeholderResId)
            return
        }

        val density = imageView.resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        val loader = getImageLoader(imageView.context)

        imageView.load(uri, loader) {
            crossfade(200)
            placeholder(placeholderResId)
            error(placeholderResId)
            headers.forEach { (key, value) ->
                setHeader(key, value)
            }
            transformations(RoundedCornersTransformation(radiusPx))
        }
    }
}
