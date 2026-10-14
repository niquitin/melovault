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
    private var customImageLoader: ImageLoader? = null

    private fun allowArtworkResource(uri: Any?): Boolean {
        val resource = uri?.toString().orEmpty()
        if (!resource.startsWith("http://", true) && !resource.startsWith("https://", true)) {
            return true // File/content artwork never needs backend Authorization.
        }
        return com.bsdung.musicassistantcar.data.AuthenticatedTransportGuard
            .isApprovedNow(resource)
    }

    private fun currentHeaders(context: Context, uri: Any?): Map<String, String> {
        val resource = uri?.toString().orEmpty()
        if (!resource.startsWith("http://", true) && !resource.startsWith("https://", true)) {
            return emptyMap()
        }
        if (!allowArtworkResource(uri)) return emptyMap()
        com.bsdung.musicassistantcar.data.SecureTokenStore.init(context)
        return com.bsdung.musicassistantcar.data.CarApiClient.currentAuthorizationHeaders()
    }

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
                        .maxSizeBytes(256L * 1024 * 1024) // E6 256MB disk LRU
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
        if (uriStr.isBlank() || !allowArtworkResource(uri)) return
        val loader = getImageLoader(context)
        val headers = currentHeaders(context, uri)
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
        if (uri.isNullOrBlank() || !allowArtworkResource(uri)) {
            imageView.setImageResource(placeholderResId)
            return
        }

        val density = imageView.resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        val loader = getImageLoader(imageView.context)
        val headers = currentHeaders(imageView.context, uri)

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
