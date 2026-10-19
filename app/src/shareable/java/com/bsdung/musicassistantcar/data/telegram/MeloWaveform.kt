package com.bsdung.musicassistantcar.data.telegram

import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.io.File

/**
 * Container-header reads for the Now Playing screens. The waveform scrubber and its decoding
 * analysis were removed in E95 (F09); what stays is the bit depth, which only the downloaded
 * file's header states. Runs on a background thread; never touches TDLib.
 */
internal object MeloWaveform {

    /**
     * Bit depth from the container header only (no decoding), for screens that have no waveform
     * analysis. Null when the file is unreadable or does not state it. Runs on a background thread.
     */
    fun probeBitDepth(file: File): Int? {
        if (!file.isFile) return null
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            sourceBitDepth(extractor.getTrackFormat(track))
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** Source bit depth when the container states it (often FLAC/WAV); null otherwise. */
    private fun sourceBitDepth(format: MediaFormat): Int? {
        if (format.containsKey("bits-per-sample")) {
            return runCatching { format.getInteger("bits-per-sample") }.getOrNull()?.takeIf { it > 0 }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            return when (format.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                AudioFormat.ENCODING_PCM_8BIT -> 8
                AudioFormat.ENCODING_PCM_16BIT -> 16
                AudioFormat.ENCODING_PCM_FLOAT -> 32
                else -> null
            }
        }
        return null
    }
}
