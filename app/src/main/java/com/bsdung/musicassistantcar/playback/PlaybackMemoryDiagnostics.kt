package com.bsdung.musicassistantcar.playback

import android.os.Debug
import android.util.Log

/**
 * Observe process heap/PSS categories without forcing collection or altering audio playback.
 *
 * PSS alone is not proof of a leak: comparing native/Java/graphics components across
 * a long-running session helps separate codec buffers and bitmap/cache growth.
 * Contains no song, account, URL or credential information.
 */
internal object PlaybackMemoryDiagnostics {
    private const val TAG = "PlaybackMemory"

    fun snapshot(reason: String) {
        runCatching {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            val runtime = Runtime.getRuntime()
            val heapUsedKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L
            val heapCommittedKb = runtime.totalMemory() / 1024L
            val nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024L
            val graphicsKb = info.getMemoryStat("summary.graphics")?.toLongOrNull() ?: -1L
            Log.i(
                TAG,
                "MEMORY_SAMPLE reason=$reason totalPssKb=${info.totalPss} " +
                    "javaPssKb=${info.dalvikPss} nativePssKb=${info.nativePss} " +
                    "otherPssKb=${info.otherPss} graphicsKb=$graphicsKb " +
                    "javaHeapUsedKb=$heapUsedKb javaHeapCommittedKb=$heapCommittedKb " +
                    "nativeHeapAllocatedKb=$nativeHeapKb",
            )
        }.onFailure {
            Log.w(TAG, "MEMORY_SAMPLE failed: ${it.javaClass.simpleName}")
        }
    }
}
