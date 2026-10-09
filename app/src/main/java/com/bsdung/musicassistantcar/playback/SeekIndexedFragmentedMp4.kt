package com.bsdung.musicassistantcar.playback

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Some offline FLAC-in-fragmented-MP4 downloads contain moof/mdat pairs but no sidx.
 * Media3 can play those files sequentially, but their timeline is not seekable.
 *
 * Build a lossless, seek-indexed COPY in app cache. The pinned original is never
 * opened for writing, changed or deleted. Unsupported/inconsistent input falls
 * back to the original file without changing playback behavior.
 */
internal object SeekIndexedFragmentedMp4 {
    private data class Box(val start: Long, val size: Long, val header: Long, val type: String) {
        val end: Long get() = start + size
    }

    private data class Fragment(val decodeTime: Long, val duration: Long, val bytes: Long)

    private data class Plan(val insertionOffset: Long, val index: ByteArray)

    @Synchronized
    fun prepare(original: File, cacheRoot: File): File {
        if (!original.isFile || !original.extension.equals("m4a", ignoreCase = true)) return original
        val planned = runCatching {
            RandomAccessFile(original, "r").use { parse(it) }
        }.getOrNull() ?: return original

        val key = runCatching {
            val bytes = (original.absolutePath + ":" + original.length() + ":" + original.lastModified())
                .toByteArray(Charsets.UTF_8)
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }.getOrNull() ?: return original

        val directory = File(cacheRoot, "seek-index")
        if (!directory.isDirectory && !directory.mkdirs()) return original
        val output = File(directory, "$key.m4a")
        val expectedLength = original.length() + planned.index.size
        if (output.isFile && output.length() == expectedLength) return output

        val temp = File(directory, "$key.part")
        return try {
            FileInputStream(original).use { input ->
                FileOutputStream(temp).use { out ->
                    copyBytes(input, out, planned.insertionOffset)
                    out.write(planned.index)
                    copyBytes(input, out, original.length() - planned.insertionOffset)
                    out.fd.sync()
                }
            }
            if (temp.length() != expectedLength ||
                !original.isFile ||
                original.length() + planned.index.size != expectedLength
            ) {
                original
            } else {
                if (output.exists()) output.delete()
                if (temp.renameTo(output) && output.length() == expectedLength) output else original
            }
        } catch (_: Exception) {
            original
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun copyBytes(input: FileInputStream, output: FileOutputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(256 * 1024)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
            if (n <= 0) throw IllegalStateException("Truncated fragmented MP4")
            output.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun parse(raf: RandomAccessFile): Plan? {
        val top = boxes(raf, 0, raf.length()) ?: return null
        if (top.size < 4 || top[0].type != "ftyp" ||
            top[1].type != "moov" || top.any { it.type == "sidx" }
        ) return null
        val moov = top[1]
        val track = children(raf, moov)?.singleOrNull { it.type == "trak" } ?: return null
        val mdia = child(raf, track, "mdia") ?: return null
        val mdhd = child(raf, mdia, "mdhd") ?: return null
        val minf = child(raf, mdia, "minf") ?: return null
        val stbl = child(raf, minf, "stbl") ?: return null
        val stsd = child(raf, stbl, "stsd") ?: return null

        // Restrict this repair to FLAC-in-MP4. Do not touch AAC/ALAC/DRM variants.
        if (stsd.size < 24) return null
        raf.seek(stsd.start + 12)
        if (raf.readInt() != 1) return null
        raf.seek(stsd.start + 20)
        if (fourCC(raf) != "fLaC") return null

        raf.seek(mdhd.start + 8)
        val mdhdVersion = raf.readUnsignedByte()
        val timescaleOffset = if (mdhdVersion == 0) 20L else if (mdhdVersion == 1) 28L else return null
        if (mdhd.size < timescaleOffset + 4) return null
        raf.seek(mdhd.start + timescaleOffset)
        val timescale = uint32(raf.readInt())
        if (timescale == 0L || timescale > Int.MAX_VALUE) return null

        val firstFragmentIndex = top.indexOfFirst { it.type == "moof" }
        if (firstFragmentIndex < 2 || (top.size - firstFragmentIndex) % 2 != 0) return null
        if (top[firstFragmentIndex].start != moov.end) return null

        val fragments = ArrayList<Fragment>()
        var trackId = -1L
        for (i in firstFragmentIndex until top.size step 2) {
            val moof = top[i]
            val mdat = top[i + 1]
            if (moof.type != "moof" || mdat.type != "mdat" || mdat.start != moof.end) return null
            val traf = children(raf, moof)?.singleOrNull { it.type == "traf" } ?: return null
            val tfhd = child(raf, traf, "tfhd") ?: return null
            val tfdt = child(raf, traf, "tfdt") ?: return null
            val trun = child(raf, traf, "trun") ?: return null
            if (tfhd.size < 16 || tfdt.size < 16 || trun.size < 16) return null

            raf.seek(tfhd.start + 8)
            val flags = raf.readInt() and 0x00FFFFFF
            val id = uint32(raf.readInt())
            if (id == 0L || (trackId >= 0 && trackId != id)) return null
            trackId = id
            var offset = tfhd.start + 16L
            if ((flags and 0x1) != 0) offset += 8 // base_data_offset
            if ((flags and 0x2) != 0) offset += 4 // sample_description_index
            if ((flags and 0x8) == 0 || offset + 4 > tfhd.end) return null
            raf.seek(offset)
            val defaultDuration = uint32(raf.readInt())
            if (defaultDuration == 0L) return null

            raf.seek(tfdt.start + 8)
            val version = raf.readUnsignedByte()
            val decodeTime = when (version) {
                0 -> {
                    raf.seek(tfdt.start + 12)
                    uint32(raf.readInt())
                }
                1 -> {
                    if (tfdt.size < 20) return null
                    raf.seek(tfdt.start + 12)
                    raf.readLong()
                }
                else -> return null
            }

            raf.seek(trun.start + 8)
            val runFlags = raf.readInt() and 0x00FFFFFF
            val samples = uint32(raf.readInt())
            if (samples == 0L || samples > 100_000L ||
                (runFlags and 0x100) != 0 // individual durations need a different parser
            ) return null
            val fragmentDuration = samples * defaultDuration
            val referenceBytes = moof.size + mdat.size
            if (decodeTime < 0L || fragmentDuration <= 0L ||
                fragmentDuration > 0xFFFFFFFFL || referenceBytes > 0x7FFFFFFFL
            ) return null
            fragments.add(Fragment(decodeTime, fragmentDuration, referenceBytes))
        }
        if (fragments.isEmpty() || fragments.size > 65535 || trackId > Int.MAX_VALUE) return null
        if (fragments.zipWithNext().any { (a, b) -> a.decodeTime + a.duration != b.decodeTime }) return null
        if (fragments.first().decodeTime > 0xFFFFFFFFL) return null

        val buffer = ByteArrayOutputStream(32 + fragments.size * 12)
        DataOutputStream(buffer).use { data ->
            data.writeInt(32 + fragments.size * 12)
            data.writeBytes("sidx")
            data.writeInt(0) // fullbox version 0; earliest time and first_offset are 32-bit
            data.writeInt(trackId.toInt())
            data.writeInt(timescale.toInt())
            data.writeInt(fragments.first().decodeTime.toInt())
            data.writeInt(0) // first moof immediately follows this sidx
            data.writeShort(0)
            data.writeShort(fragments.size)
            fragments.forEach {
                data.writeInt(it.bytes.toInt()) // reference_type=0, referenced_size
                data.writeInt(it.duration.toInt())
                data.writeInt(0x90000000L.toInt()) // starts_with_SAP=1, SAP_type=1
            }
        }
        return Plan(top[firstFragmentIndex].start, buffer.toByteArray())
    }

    private fun child(raf: RandomAccessFile, box: Box, type: String): Box? =
        children(raf, box)?.firstOrNull { it.type == type }

    private fun children(raf: RandomAccessFile, box: Box): List<Box>? =
        boxes(raf, box.start + box.header, box.end)

    private fun boxes(raf: RandomAccessFile, begin: Long, end: Long): List<Box>? {
        if (begin < 0 || end > raf.length() || begin > end) return null
        val result = ArrayList<Box>()
        var p = begin
        while (p < end) {
            if (end - p < 8 || result.size > 2000) return null
            raf.seek(p)
            val size32 = uint32(raf.readInt())
            val type = fourCC(raf)
            val header: Long
            val size: Long
            if (size32 == 1L) {
                if (end - p < 16) return null
                size = raf.readLong()
                header = 16
            } else {
                size = if (size32 == 0L) end - p else size32
                header = 8
            }
            if (size < header || size > end - p) return null
            result.add(Box(p, size, header, type))
            p += size
        }
        return if (p == end) result else null
    }

    private fun fourCC(raf: RandomAccessFile): String {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun uint32(value: Int): Long = value.toLong() and 0xFFFFFFFFL
}
