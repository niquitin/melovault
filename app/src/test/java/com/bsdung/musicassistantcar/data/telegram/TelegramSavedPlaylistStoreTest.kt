package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Real AES-256-GCM with the app's blob layout (12-byte IV, ciphertext, 16-byte tag), minus the Android Keystore. */
internal class TestAesGcmCipher(seed: Int = 7) : PlaylistCipher {
    private val key = SecretKeySpec(ByteArray(32) { (it + seed).toByte() }, "AES")
    private val random = SecureRandom()

    override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
        cipher.updateAAD(aad)
        return cipher.doFinal(blob, 12, blob.size - 12)
    }
}

/** Write-to-temp-then-rename, like the platform AtomicFile, but on plain java.io for JVM tests. */
internal object TestPlainFileIo : PlaylistFileIo {
    override fun read(file: File): ByteArray = file.readBytes()

    override fun write(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".new")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}

class TelegramSavedPlaylistStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val userA = TelegramUserId(1001L)
    private val userB = TelegramUserId(2002L)

    private fun store(
        cipher: PlaylistCipher = TestAesGcmCipher(),
        io: PlaylistFileIo = TestPlainFileIo,
    ) = TelegramSavedPlaylistStore(folder.root, cipher, io)

    private fun fileOf(user: TelegramUserId) =
        File(File(folder.root, "tg_playlists_v1"), TelegramAccountStoragePolicy.directoryName(user) + ".bin")

    private fun ids(n: Int): List<Long> = (1..n).map { it.toLong() }

    // ---- P0-01: the empty playlist ---------------------------------------------------------

    @Test fun emptyPlaylistIsExactly28BytesAndReadsBackAsOkEmpty() {
        val s = store()
        s.save(userA, emptyList())
        assertEquals(28, fileOf(userA).length())
        assertEquals(PlaylistLoadResult.Ok(emptyList()), s.load(userA))
    }

    @Test fun savingEmptyAfterSomethingStillReadsBack() {
        val s = store()
        s.save(userA, listOf(5L, 6L))
        s.save(userA, emptyList())
        assertEquals(PlaylistLoadResult.Ok(emptyList()), s.load(userA))
    }

    // ---- P0-02: missing vs unreadable ------------------------------------------------------

    @Test fun neverSavedIsMissingNotAnEmptyList() {
        assertEquals(PlaylistLoadResult.Missing, store().load(userA))
        assertFalse(fileOf(userA).exists())
    }

    @Test fun orderSurvivesSaveAndLoad() {
        val s = store()
        s.save(userA, listOf(30L, 10L, 20L))
        assertEquals(PlaylistLoadResult.Ok(listOf(30L, 10L, 20L)), s.load(userA))
    }

    @Test fun restartKeepsThePlaylist() {
        store().save(userA, listOf(4L, 3L, 2L, 1L))
        // A brand-new store object (a fresh process) with the same key reads the same file.
        assertEquals(PlaylistLoadResult.Ok(listOf(4L, 3L, 2L, 1L)), store(TestAesGcmCipher()).load(userA))
    }

    @Test fun flippedTagByteIsUnreadableAndSaveNeverTouchesTheFile() {
        val s = store()
        s.save(userA, listOf(1L, 2L, 3L))
        val file = fileOf(userA)
        val damaged = file.readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }
        file.writeBytes(damaged)

        assertEquals(PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), s.load(userA))
        try {
            s.save(userA, listOf(99L))
            fail("save must refuse to overwrite an unreadable file")
        } catch (e: PlaylistUnreadableException) {
            assertEquals(PlaylistUnreadableReason.DECRYPT, e.reason)
        }
        assertArrayEquals("damaged file must stay byte-for-byte", damaged, file.readBytes())
        assertFalse("no temp file may be left behind", File(file.parentFile, file.name + ".new").exists())
    }

    @Test fun wrongKeyIsUnreadableNotEmpty() {
        store(TestAesGcmCipher(seed = 1)).save(userA, listOf(1L, 2L))
        val reader = store(TestAesGcmCipher(seed = 2))
        assertEquals(PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), reader.load(userA))
    }

    @Test fun keystoreFailureOnDecryptIsUnreadableAndProtectsTheFile() {
        store().save(userA, listOf(1L, 2L, 3L))
        val before = fileOf(userA).readBytes()
        val broken = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray = throw GeneralSecurityException("keystore down")
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray = throw GeneralSecurityException("keystore down")
        }
        val s = store(broken)
        assertEquals(PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), s.load(userA))
        try {
            s.save(userA, listOf(7L))
            fail("save must refuse while the file cannot be read")
        } catch (_: PlaylistUnreadableException) {
        }
        assertArrayEquals(before, fileOf(userA).readBytes())
    }

    @Test fun keystoreFailureOnEncryptLeavesTheOldFileUntouched() {
        store().save(userA, listOf(1L, 2L, 3L))
        val before = fileOf(userA).readBytes()
        val good = TestAesGcmCipher()
        val encryptBroken = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray = throw IllegalStateException("no key")
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray = good.decrypt(aad, blob)
        }
        try {
            store(encryptBroken).save(userA, listOf(1L, 2L, 3L, 4L))
            fail("save must fail loudly")
        } catch (e: IllegalStateException) {
            // expected: a plain failure, not "unreadable"
        }
        assertArrayEquals(before, fileOf(userA).readBytes())
        assertEquals(PlaylistLoadResult.Ok(listOf(1L, 2L, 3L)), store().load(userA))
    }

    @Test fun aBlobThatWouldNotReadBackIsNeverWritten() {
        store().save(userA, listOf(1L))
        val before = fileOf(userA).readBytes()
        val good = TestAesGcmCipher()
        val liar = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray = good.encrypt(aad, plain)
            // The first decrypt (the pre-write check of the existing file) is honest; the read-back is not.
            private var calls = 0
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray =
                if (calls++ == 0) good.decrypt(aad, blob) else "999".toByteArray()
        }
        try {
            store(liar).save(userA, listOf(1L, 2L))
            fail("verification must fail")
        } catch (_: IllegalStateException) {
        }
        assertArrayEquals(before, fileOf(userA).readBytes())
    }

    @Test fun truncatedBlobIsUnreadableBySize() {
        val s = store()
        s.save(userA, listOf(1L))
        fileOf(userA).writeBytes(ByteArray(27) { 1 })
        assertEquals(PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.SIZE), s.load(userA))
    }

    @Test fun zeroByteFileIsUnreadableNotEmpty() {
        val s = store()
        s.save(userA, listOf(1L))
        fileOf(userA).writeBytes(ByteArray(0))
        assertEquals(PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.SIZE), s.load(userA))
    }

    @Test fun hugeBlobIsUnreadableBySize() {
        val s = store()
        s.save(userA, listOf(1L))
        fileOf(userA).writeBytes(ByteArray(TelegramSavedPlaylistStore.MAX_BLOB_BYTES + 1) { 1 })
        assertEquals(PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.SIZE), s.load(userA))
    }

    @Test fun readIoErrorIsUnreadableNotEmpty() {
        val s = store()
        s.save(userA, listOf(1L))
        val failing = object : PlaylistFileIo {
            override fun read(file: File): ByteArray = throw IOException("disk")
            override fun write(file: File, bytes: ByteArray) = TestPlainFileIo.write(file, bytes)
        }
        assertEquals(
            PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.IO),
            store(io = failing).load(userA)
        )
    }

    @Test fun aDirectoryInPlaceOfTheFileIsUnreadable() {
        val file = fileOf(userA)
        file.parentFile!!.mkdirs()
        assertTrue(file.mkdir())
        assertEquals(PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.IO), store().load(userA))
    }

    @Test fun authenticatedButInvalidContentIsUnreadableByFormat() {
        val cipher = TestAesGcmCipher()
        val aad = TelegramAccountStoragePolicy.directoryName(userA).toByteArray()
        for (bad in listOf("12\nabc", "0", "-5", "1\n2.5")) {
            fileOf(userA).parentFile!!.mkdirs()
            fileOf(userA).writeBytes(cipher.encrypt(aad, bad.toByteArray()))
            assertEquals(
                "content: $bad",
                PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.FORMAT),
                store(cipher).load(userA)
            )
        }
    }

    @Test fun duplicatesAndBlankLinesInAValidFileAreTolerated() {
        val cipher = TestAesGcmCipher()
        val aad = TelegramAccountStoragePolicy.directoryName(userA).toByteArray()
        fileOf(userA).parentFile!!.mkdirs()
        fileOf(userA).writeBytes(cipher.encrypt(aad, "5\n\n6\n5\n7\n".toByteArray()))
        assertEquals(PlaylistLoadResult.Ok(listOf(5L, 6L, 7L)), store(cipher).load(userA))
    }

    // ---- P0-03: nothing is cut on the way to or from disk ------------------------------------

    @Test fun exactly300SongsRoundTripWithoutLoss() {
        val s = store()
        s.save(userA, ids(300))
        assertEquals(PlaylistLoadResult.Ok(ids(300)), s.load(userA))
    }

    @Test fun savingMoreThan300IsNotCutAndLoadsBackWhole() {
        val s = store()
        s.save(userA, ids(301))
        assertEquals(PlaylistLoadResult.Ok(ids(301)), s.load(userA))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aRunawayListIsRejectedNotTruncated() {
        store().save(userA, ids(TelegramSavedPlaylistStore.MAX_STORED_IDS + 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveIdsAreRejectedNotSilentlyFiltered() {
        store().save(userA, listOf(1L, 0L))
    }

    // ---- account isolation ------------------------------------------------------------------

    @Test fun twoAccountsHaveSeparateFiles() {
        val s = store()
        s.save(userA, listOf(1L, 2L))
        s.save(userB, listOf(9L))
        assertEquals(PlaylistLoadResult.Ok(listOf(1L, 2L)), s.load(userA))
        assertEquals(PlaylistLoadResult.Ok(listOf(9L)), s.load(userB))
    }

    @Test fun anotherAccountsFileCopiedOverIsRejected() {
        val s = store()
        s.save(userA, listOf(1L, 2L))
        // The account is bound into the encrypted data, so a swapped file never reads as the other account's list.
        fileOf(userB).parentFile!!.mkdirs()
        fileOf(userA).copyTo(fileOf(userB))
        assertEquals(PlaylistLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), s.load(userB))
        assertNotNull(fileOf(userB))
    }
}
