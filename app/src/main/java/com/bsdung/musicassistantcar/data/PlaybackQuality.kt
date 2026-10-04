package com.bsdung.musicassistantcar.data

enum class StreamQuality(
    val queryValue: String,
    val label: String,
) {
    ORIGINAL(
        "original",
        "Original",
    ),
    MOBILE_320(
        "mobile",
        "Mobile 320",
    ),
}

object PlaybackQuality {
    @Volatile
    var current: StreamQuality = StreamQuality.ORIGINAL

    @Volatile
    var isQualitySwitching: Boolean = false

    fun toggle(): StreamQuality {
        current = if (current == StreamQuality.ORIGINAL) {
            StreamQuality.MOBILE_320
        } else {
            StreamQuality.ORIGINAL
        }
        return current
    }
}
