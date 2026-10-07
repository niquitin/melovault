package com.bsdung.musicassistantcar.playback.alac

import android.media.MediaCodecList
import android.util.Log
import androidx.media3.common.MimeTypes

object AlacCapabilities {
    private const val TAG = "AlacCapabilities"

    @JvmStatic
    @JvmOverloads
    fun supportsPlatformDecoder(
        mimeType: String = MimeTypes.AUDIO_ALAC,
        supportedTypesProvider: (() -> List<String>)? = null,
    ): Boolean {
        if (supportedTypesProvider != null) {
            return supportedTypesProvider().any { it.equals(mimeType, ignoreCase = true) }
        }
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals(mimeType, ignoreCase = true)) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking platform decoder for $mimeType", e)
            false
        }
    }

    @JvmStatic
    fun logDeviceCapabilities() {
        val platformSupported = supportsPlatformDecoder()
        Log.i(TAG, "ALAC_PLATFORM_DECODER: ${if (platformSupported) "AVAILABLE" else "UNAVAILABLE"}")
    }

    @JvmStatic
    fun logSelectedRenderer(isPlatform: Boolean, decoderName: String? = null) {
        val nameSuffix = if (!decoderName.isNullOrBlank()) " ($decoderName)" else ""
        if (isPlatform) {
            Log.i(TAG, "ALAC_RENDERER: PLATFORM$nameSuffix")
        } else {
            Log.i(TAG, "ALAC_RENDERER: SOFTWARE_FALLBACK$nameSuffix")
        }
    }
}
