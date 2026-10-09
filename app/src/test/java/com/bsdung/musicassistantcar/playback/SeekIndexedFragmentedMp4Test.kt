package com.bsdung.musicassistantcar.playback

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files

class SeekIndexedFragmentedMp4Test {
    private fun bytes(builder: DataOutputStream.() -> Unit): ByteArray {
        val stream = ByteArrayOutputStream()
        DataOutputStream(stream).use { it.builder() }
        return stream.toByteArray()
    }

    private fun atom(type: String, content: ByteArray): ByteArray = bytes {
        writeInt(content.size + 8)
        writeBytes(type)
        write(content)
    }

    private fun container(type: String, vararg children: ByteArray): ByteArray =
        atom(type, children.fold(ByteArray(0)) { a, b -> a + b })

    private fun sampleTrack(timescale: Int = 48000): ByteArray {
        val stsd = atom("stsd", bytes {
            writeInt(0) // version and flags
            writeInt(1) // sample description count
            writeInt(8)
            writeBytes("fLaC")
        })
        val mdhd = atom("mdhd", bytes {
            writeInt(0)
            writeInt(0)
            writeInt(0)
            writeInt(timescale)
            writeInt(0)
        })
        return container("trak", container("mdia", mdhd, container("minf", container("stbl", stsd))))
    }

    private fun fragment(decodeTime: Int, samples: Int, duration: Int = 1024): ByteArray {
        val tfhd = atom("tfhd", bytes {
            writeInt(8) // default_sample_duration present
            writeInt(1) // track id
            writeInt(duration)
        })
        val tfdt = atom("tfdt", bytes {
            writeInt(0) // version 0
            writeInt(decodeTime)
        })
        val trun = atom("trun", bytes {
            writeInt(0)
            writeInt(samples)
        })
        return container("moof", container("traf", tfhd, tfdt, trun)) +
            atom("mdat", ByteArray(samples * 4) { 0x3A })
    }

    private fun mockFragmentedM4a(): ByteArray {
        val ftyp = atom("ftyp", bytes { writeBytes("iso8"); writeInt(0) })
        val moov = container("moov", sampleTrack())
        return ftyp + moov + fragment(0, 3) + fragment(3072, 2)
    }

    private fun intAt(b: ByteArray, offset: Int): Long =
        ((b[offset].toLong() and 255) shl 24) or
            ((b[offset + 1].toLong() and 255) shl 16) or
            ((b[offset + 2].toLong() and 255) shl 8) or
            (b[offset + 3].toLong() and 255)

    @Test
    fun insertsSidxAheadOfFragmentsWithoutModifyingPinnedOriginal() {
        val tmp = Files.createTempDirectory("seek-index-test").toFile()
        try {
            val original = File(tmp, "pinned.m4a")
            val before = mockFragmentedM4a()
            original.writeBytes(before)
            val indexed = SeekIndexedFragmentedMp4.prepare(original, File(tmp, "cache"))
            assertNotEquals(original.absolutePath, indexed.absolutePath)
            assertArrayEquals(before, original.readBytes())
            val b = indexed.readBytes()
            val start = 16 + mockMoovSize()
            assertEquals("sidx", String(b, start + 4, 4))
            assertEquals(56L, intAt(b, start))
            assertEquals(1L, intAt(b, start + 12)) // track id
            assertEquals(48000L, intAt(b, start + 16))
            assertEquals(2, ((b[start + 30].toInt() and 255) shl 8) + (b[start + 31].toInt() and 255))
            assertEquals(3072L, intAt(b, start + 36))
            assertEquals(2048L, intAt(b, start + 48))
            assertEquals(before.size + 56, b.size)
            assertEquals("moof", String(b, start + 56 + 4, 4))
            assertArrayEquals(before, original.readBytes())
            assertEquals(indexed.absolutePath, SeekIndexedFragmentedMp4.prepare(original, File(tmp, "cache")).absolutePath)
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun mockMoovSize(): Int = container("moov", sampleTrack()).size

    @Test
    fun unsupportedAudioReturnsOriginalAndDoesNotMakeCache() {
        val tmp = Files.createTempDirectory("nonfragmented-test").toFile()
        try {
            val source = File(tmp, "normal.m4a")
            source.writeBytes(atom("ftyp", bytes { writeBytes("isom") }) +
                container("moov", sampleTrack()))
            assertEquals(source, SeekIndexedFragmentedMp4.prepare(source, File(tmp, "cache")))
            assertTrue(source.isFile)
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun malformedDiscontinuousFragmentFallsBackSafely() {
        val tmp = Files.createTempDirectory("bad-fragments-test").toFile()
        try {
            val source = File(tmp, "bad.m4a")
            source.writeBytes(atom("ftyp", bytes { writeBytes("iso8") }) +
                container("moov", sampleTrack()) +
                fragment(0, 3) + fragment(4096, 2))
            val before = source.readBytes()
            assertEquals(source, SeekIndexedFragmentedMp4.prepare(source, File(tmp, "cache")))
            assertArrayEquals(before, source.readBytes())
        } finally {
            tmp.deleteRecursively()
        }
    }
}
