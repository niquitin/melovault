package com.bsdung.musicassistantcar.data.telegram

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Real waveform for the Studio Deck scrubber. It samples the downloaded file at
 * [MeloNowPlayingPolicy.WAVEFORM_BUCKETS] evenly spaced positions, decodes a short
 * slice at each one and keeps the peak amplitude, so even a 90-minute mixset is
 * analysed in a few seconds. Runs on a background thread; never touches TDLib.
 */
internal object MeloWaveform {

    class Result(val peaks: FloatArray, val bitDepth: Int?)

    private const val OUTPUT_BUFFERS_PER_SLICE = 3
    private const val MAX_STEPS_PER_SLICE = 60
    private const val DEQUEUE_TIMEOUT_US = 10_000L

    fun cacheFile(cacheDir: File, name: String): File = File(File(cacheDir, "melo_waveform"), name)

    fun loadCached(file: File): FloatArray? = runCatching {
        if (!file.isFile || file.length() <= 0L) return null
        val bytes = file.readBytes()
        FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
    }.getOrNull()

    fun saveCached(file: File, peaks: FloatArray) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(ByteArray(peaks.size) { (peaks[it].coerceIn(0f, 1f) * 255f).toInt().toByte() })
        }
    }

    /** Null when the file cannot be decoded or the analysis was cancelled. */
    fun analyze(file: File, buckets: Int, cancelled: () -> Boolean): Result? {
        if (!file.isFile || buckets <= 0) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs <= 0L) return null
            val bitDepth = sourceBitDepth(format)
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val raw = FloatArray(buckets)
            for (i in 0 until buckets) {
                if (cancelled()) return null
                val positionUs = durationUs * (2L * i + 1L) / (2L * buckets)
                extractor.seekTo(positionUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                decoder.flush()
                raw[i] = slicePeak(extractor, decoder, cancelled)
            }
            return Result(MeloNowPlayingPolicy.bucketPeaks(raw, buckets), bitDepth)
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun slicePeak(extractor: MediaExtractor, decoder: MediaCodec, cancelled: () -> Boolean): Float {
        val info = MediaCodec.BufferInfo()
        var peak = 0f
        var outputs = 0
        var inputDone = false
        var steps = 0
        while (outputs < OUTPUT_BUFFERS_PER_SLICE && steps++ < MAX_STEPS_PER_SLICE && !cancelled()) {
            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                    val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (outIndex >= 0) {
                val out = decoder.getOutputBuffer(outIndex)
                if (out != null && info.size > 0) {
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    peak = maxOf(peak, bufferPeak(out, isFloatOutput(decoder)))
                    outputs++
                }
                decoder.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        return peak
    }

    private fun isFloatOutput(decoder: MediaCodec): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val out = runCatching { decoder.outputFormat }.getOrNull() ?: return false
        return out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
    }

    private fun bufferPeak(buffer: java.nio.ByteBuffer, float: Boolean): Float {
        val ordered = buffer.order(ByteOrder.nativeOrder())
        var peak = 0f
        if (float) {
            val f = ordered.asFloatBuffer()
            while (f.hasRemaining()) peak = maxOf(peak, abs(f.get()))
        } else {
            val s = ordered.asShortBuffer()
            while (s.hasRemaining()) peak = maxOf(peak, abs(s.get().toInt()) / 32768f)
        }
        return peak.coerceIn(0f, 1f)
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
