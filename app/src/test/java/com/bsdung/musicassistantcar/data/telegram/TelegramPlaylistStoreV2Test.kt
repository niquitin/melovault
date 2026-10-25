package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

/** E96 P1: the V2 text format and the encrypted V2 file (fake cipher / fake file I/O, no Android). */
class TelegramPlaylistStoreV2Test {
    @get:Rule val folder = TemporaryFolder()

    private val userA = TelegramUserId(1001L)
    private val userB = TelegramUserId(2002L)

    private fun store(cipher: PlaylistCipher = TestAesGcmCipher(2), io: PlaylistFileIo = TestPlainFileIo) =
        TelegramPlaylistStoreV2(folder.root, cipher, io)

    private fun fileOf(user: TelegramUserId) =
        File(File(folder.root, "tg_playlists_v2"), TelegramAccountStoragePolicy.directoryName(user) + ".bin")

    /** A state that uses every kind of line the format has. */
    private fun richState(account: Long = userA.value): PlaylistState {
        var s = PlaylistState.empty(account, "dev-aaa")
        s = s.addTelegram(PlaylistItemId.Telegram(account, 4_194_304L)).state
        s = s.addTelegram(PlaylistItemId.Telegram(-100123L, 77L)).state
        s = s.addTelegram(PlaylistItemId.Telegram(-100124L, 77L)).state
        s = s.removeTelegram(PlaylistItemId.Telegram(-100123L, 77L)).state
        s = s.addLegacy(999L).addLegacy(1000L).removeLegacy(1000L)
        s = s.markRetryRequired("dev-aaa#2").acknowledge("dev-aaa#1")
        return s.copy(
            migration = MigrationRecord(MigrationStatus.COMPLETE, "abc123", 7),
            projection = ProjectionRecord("d1", "d2", ProjectionStatus.DIVERGED_ACK, true, 2, 3, 4),
            checkpoint = SyncCheckpoint(-100777L, 41L),
        )
    }

    // ---- format -------------------------------------------------------------------------------

    @Test fun encodeDecodeRoundTripsEverythingAndIsDeterministic() {
        val s = richState()
        val bytes = TelegramPlaylistCodec.encode(s)
        assertArrayEquals(bytes, TelegramPlaylistCodec.encode(s))
        val decoded = TelegramPlaylistCodec.decode(bytes, userA.value)
        assertTrue(decoded is TelegramPlaylistCodec.Decoded.Ok)
        assertEquals(s, (decoded as TelegramPlaylistCodec.Decoded.Ok).state)
    }

    @Test fun anEmptyStateRoundTrips() {
        val s = PlaylistState.empty(userA.value, "dev-aaa")
        val decoded = TelegramPlaylistCodec.decode(TelegramPlaylistCodec.encode(s), userA.value)
        assertEquals(s, (decoded as TelegramPlaylistCodec.Decoded.Ok).state)
    }

    @Test fun anyChangedByteIsRejectedByTheChecksum() {
        val bytes = TelegramPlaylistCodec.encode(richState())
        for (at in listOf(10, bytes.size / 2, bytes.size - 80)) {
            val broken = bytes.copyOf().also { it[at] = (it[at].toInt() xor 0x01).toByte() }
            val decoded = TelegramPlaylistCodec.decode(broken, userA.value)
            assertEquals("at=$at", TelegramPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.FORMAT), decoded)
        }
    }

    @Test fun truncatedOrEmptyTextIsRejected() {
        val bytes = TelegramPlaylistCodec.encode(richState())
        for (cut in listOf(0, 5, bytes.size / 2, bytes.size - 1)) {
            val decoded = TelegramPlaylistCodec.decode(bytes.copyOf(cut), userA.value)
            assertTrue("cut=$cut", decoded is TelegramPlaylistCodec.Decoded.Invalid)
        }
    }

    private fun sealed(body: String): ByteArray =
        (body + "END\t" + TelegramPlaylistCodec.sha256Hex(body) + "\n").toByteArray()

    private val goodBody: String
        get() = String(TelegramPlaylistCodec.encode(PlaylistState.empty(1001L, "dev-aaa"))).substringBefore("END\t")

    @Test fun anUnknownLineTypeIsRejectedNotSkipped() {
        val decoded = TelegramPlaylistCodec.decode(sealed(goodBody + "X\twhatever\n"), 1001L)
        assertEquals(TelegramPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.FORMAT), decoded)
    }

    @Test fun aRepeatedSingletonLineIsRejected() {
        val decoded = TelegramPlaylistCodec.decode(sealed(goodBody + "lamport\t5\n"), 1001L)
        assertEquals(TelegramPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.FORMAT), decoded)
    }

    @Test fun aNewerSchemaIsUnreadableForUsAndNeverReadAsOurOwn() {
        val newer = goodBody.replace("schema\t2", "schema\t3")
        assertEquals(
            TelegramPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.SCHEMA),
            TelegramPlaylistCodec.decode(sealed(newer), 1001L)
        )
    }

    @Test fun anotherAccountsStateIsRejected() {
        val bytes = TelegramPlaylistCodec.encode(PlaylistState.empty(2002L, "dev-aaa"))
        assertEquals(
            TelegramPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.ACCOUNT),
            TelegramPlaylistCodec.decode(bytes, 1001L)
        )
    }

    @Test fun anOperationKeepsItsIdAcrossSaveRetryAndReload() {
        val s = PlaylistState.empty(userA.value, "dev-aaa").addTelegram(PlaylistItemId.Telegram(userA.value, 5_000_000L)).state
        val retried = s.markRetryRequired("dev-aaa#1")
        val back = (TelegramPlaylistCodec.decode(TelegramPlaylistCodec.encode(retried), userA.value) as TelegramPlaylistCodec.Decoded.Ok).state
        assertEquals("dev-aaa#1", back.outbox.single().op.opId)
        assertEquals(OutboxStatus.RETRY_REQUIRED, back.outbox.single().status)
        assertEquals(s.outbox.single().op, back.outbox.single().op)
    }

    // ---- file -----------------------------------------------------------------------------------

    @Test fun neverSavedIsMissing() {
        assertEquals(V2LoadResult.Missing, store().load(userA))
        assertFalse(fileOf(userA).exists())
    }

    @Test fun saveThenLoadIsValidAndSurvivesARestart() {
        val s = richState()
        store().save(userA, s)
        assertEquals(V2LoadResult.Valid(s), store().load(userA)) // a new store object = a new process
    }

    @Test fun v2LivesInItsOwnDirectoryWithItsOwnKeyAlias() {
        store().save(userA, richState())
        assertTrue(fileOf(userA).isFile)
        assertFalse(File(File(folder.root, "tg_playlists_v1"), fileOf(userA).name).exists())
        assertNotEquals(KeystorePlaylistCipher.V1_ALIAS, KeystorePlaylistCipher.V2_ALIAS)
        assertEquals("melovault_e91_saved_playlist_aes_v1", KeystorePlaylistCipher.V1_ALIAS) // V1's key is never renamed
    }

    @Test fun aDamagedV2FileIsUnreadableAndSaveNeverOverwritesIt() {
        store().save(userA, richState())
        val damaged = fileOf(userA).readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        fileOf(userA).writeBytes(damaged)
        assertEquals(V2LoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), store().load(userA))
        try {
            store().save(userA, PlaylistState.empty(userA.value, "dev-new"))
            fail("must refuse to overwrite")
        } catch (e: PlaylistUnreadableException) {
            assertEquals(PlaylistUnreadableReason.DECRYPT, e.reason)
        }
        assertArrayEquals(damaged, fileOf(userA).readBytes())
    }

    @Test fun aMissingV2KeyNeverCreatesANewOneForAnExistingFile() {
        store().save(userA, richState())
        val before = fileOf(userA).readBytes()
        var encrypts = 0
        val keyGone = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray { encrypts++; return ByteArray(40) }
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray = throw IllegalStateException("Playlist key is not available")
        }
        val s = store(keyGone)
        assertEquals(V2LoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), s.load(userA))
        try {
            s.save(userA, richState())
            fail("must refuse")
        } catch (_: PlaylistUnreadableException) {
        }
        assertEquals("no key may be created (encrypt is where a key would be minted)", 0, encrypts)
        assertArrayEquals(before, fileOf(userA).readBytes())
    }

    @Test fun keystoreFailureWhileEncryptingLeavesTheOldFile() {
        store().save(userA, richState())
        val before = fileOf(userA).readBytes()
        val good = TestAesGcmCipher(2)
        val encryptFails = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray = throw GeneralSecurityException("keystore")
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray = good.decrypt(aad, blob)
        }
        try {
            store(encryptFails).save(userA, PlaylistState.empty(userA.value, "dev-new"))
            fail("must fail")
        } catch (_: GeneralSecurityException) {
        }
        assertArrayEquals(before, fileOf(userA).readBytes())
    }

    @Test fun aWriteThatFailsMidwayLeavesTheOldFileValid() {
        val s1 = richState()
        store().save(userA, s1)
        val failing = object : PlaylistFileIo {
            override fun read(file: File): ByteArray = TestPlainFileIo.read(file)
            override fun write(file: File, bytes: ByteArray) = throw IOException("disk full")
        }
        try {
            store(io = failing).save(userA, s1.addLegacy(5L))
            fail("must fail")
        } catch (_: IOException) {
        }
        assertEquals(V2LoadResult.Valid(s1), store().load(userA))
    }

    @Test fun aBlobThatDoesNotReadBackIsNeverWritten() {
        store().save(userA, richState())
        val before = fileOf(userA).readBytes()
        val good = TestAesGcmCipher(2)
        var decrypts = 0
        val liar = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray = good.encrypt(aad, plain)
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray =
                if (decrypts++ == 0) good.decrypt(aad, blob) else TelegramPlaylistCodec.encode(PlaylistState.empty(userA.value, "dev-lie"))
        }
        try {
            store(liar).save(userA, richState().addLegacy(8L))
            fail("verification must fail")
        } catch (_: IllegalStateException) {
        }
        assertArrayEquals(before, fileOf(userA).readBytes())
    }

    @Test fun aNewerSchemaFileIsNeverOverwritten() {
        val cipher = TestAesGcmCipher(2)
        val aad = ("v2:" + TelegramAccountStoragePolicy.directoryName(userA)).toByteArray()
        val newer = sealed(goodBody.replace("account\t1001", "account\t${userA.value}").replace("schema\t2", "schema\t9"))
        fileOf(userA).parentFile!!.mkdirs()
        fileOf(userA).writeBytes(cipher.encrypt(aad, newer))
        val before = fileOf(userA).readBytes()
        assertEquals(V2LoadResult.Unreadable(PlaylistUnreadableReason.SCHEMA), store(cipher).load(userA))
        try {
            store(cipher).save(userA, PlaylistState.empty(userA.value, "dev-new"))
            fail("must refuse")
        } catch (e: PlaylistUnreadableException) {
            assertEquals(PlaylistUnreadableReason.SCHEMA, e.reason)
        }
        assertArrayEquals(before, fileOf(userA).readBytes())
    }

    @Test fun sizeBoundsAndAnOddFileAreUnreadableNotMissing() {
        store().save(userA, richState())
        fileOf(userA).writeBytes(ByteArray(27) { 1 })
        assertEquals(V2LoadResult.Unreadable(PlaylistUnreadableReason.SIZE), store().load(userA))
        fileOf(userA).delete()
        assertTrue(fileOf(userA).mkdirs())
        assertEquals(V2LoadResult.Unreadable(PlaylistUnreadableReason.IO), store().load(userA))
    }

    @Test fun twoAccountsNeverShareAFileAndASwappedFileIsRejected() {
        val a = richState(userA.value)
        val b = PlaylistState.empty(userB.value, "dev-bbb").addLegacy(42L)
        val s = store()
        s.save(userA, a)
        s.save(userB, b)
        assertEquals(V2LoadResult.Valid(a), s.load(userA))
        assertEquals(V2LoadResult.Valid(b), s.load(userB))
        fileOf(userA).copyTo(fileOf(userB), overwrite = true) // a file moved onto another account
        assertEquals(V2LoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), s.load(userB))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aStateOfAnotherAccountCannotBeSaved() {
        store().save(userA, PlaylistState.empty(userB.value, "dev-bbb"))
    }
}
