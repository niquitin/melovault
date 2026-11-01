package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

/** Nhiều Playlist có tên, S3: the MVNL1 text format and the encrypted file (fake cipher / fake file I/O, no Android). */
class NamedPlaylistStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val userA = TelegramUserId(1001L)
    private val userB = TelegramUserId(2002L)

    private fun store(cipher: PlaylistCipher = TestAesGcmCipher(3), io: PlaylistFileIo = TestPlainFileIo) =
        NamedPlaylistStore(folder.root, cipher, io)

    private fun fileOf(user: TelegramUserId) =
        File(File(folder.root, "tg_named_playlists_v1"), TelegramAccountStoragePolicy.directoryName(user) + ".bin")

    private fun tg(chat: Long, message: Long) = PlaylistItemId.Telegram(chat, message)

    /** A state that uses every kind of line the format has. */
    private fun richState(account: Long = userA.value): NamedPlaylistState {
        var s = NamedPlaylistState.empty(account, "dev-aaa")
        val one = checkNotNull(s.createList("Nhạc đi xe 🚗").also { s = it.state }.listId)
        val two = checkNotNull(s.createList("Tập gym").also { s = it.state }.listId)
        val gone = checkNotNull(s.createList("Sẽ xóa").also { s = it.state }.listId)
        s = s.addTelegram(one, tg(account, 4_194_304L)).state
        s = s.addTelegram(one, tg(-100123L, 77L)).state
        s = s.addTelegram(one, tg(-100124L, 77L)).state
        s = s.removeTelegram(one, tg(-100123L, 77L)).state
        s = s.addTelegram(two, tg(-100123L, 77L)).state
        s = s.moveItem(one, tg(-100124L, 77L), 0).state
        s = s.addLocal(one, 900L, LocalFingerprint.of("Bài máy.mp3", 1000L, 2000L)).state
        s = s.addLocal(two, 901L, LocalFingerprint.of("Bài máy 2.mp3", 1001L, 2001L)).state
        s = s.renameList(two, "Tập gym ✔").state
        s = s.addTelegram(gone, tg(-100125L, 5L)).state
        s = s.deleteList(gone).state
        val ops = s.outbox.map { it.op.opId }
        s = s.acknowledge(ops[0]).markRetryRequired(ops[1]).markRetryRequired(ops[1])
        return s.copy(checkpoint = SyncCheckpoint(-100777L, 41L))
    }

    // ---- format -------------------------------------------------------------------------------

    @Test fun encodeDecodeRoundTripsEverythingAndIsDeterministic() {
        val s = richState()
        val bytes = NamedPlaylistCodec.encode(s)
        assertArrayEquals(bytes, NamedPlaylistCodec.encode(s))
        val decoded = NamedPlaylistCodec.decode(bytes, userA.value)
        assertTrue(decoded is NamedPlaylistCodec.Decoded.Ok)
        assertEquals(s, (decoded as NamedPlaylistCodec.Decoded.Ok).state)
        assertTrue("the file holds the local song", String(bytes).contains("\nLO\t"))
        assertFalse("a local song is never an operation", String(bytes).lines().any { it.startsWith("O\t") && it.contains("local:") })
    }

    @Test fun aReplayInProgressSurvivesTheRoundTrip() {
        val s = richState().let { it.copy(checkpoint = SyncCheckpoint(-100777L, 41L, 17L)) }
        val back = (NamedPlaylistCodec.decode(NamedPlaylistCodec.encode(s), userA.value) as NamedPlaylistCodec.Decoded.Ok).state
        assertEquals(17L, back.checkpoint.replayPosition)
        assertEquals(s, back)
    }

    @Test fun anEmptyStateRoundTrips() {
        val s = NamedPlaylistState.empty(userA.value, "dev-aaa")
        val decoded = NamedPlaylistCodec.decode(NamedPlaylistCodec.encode(s), userA.value)
        assertEquals(s, (decoded as NamedPlaylistCodec.Decoded.Ok).state)
    }

    @Test fun vietnameseAndEmojiNamesRoundTripAndCannotBreakTheLineFormat() {
        var s = NamedPlaylistState.empty(userA.value, "dev-aaa")
        val wanted = listOf("Nhạc Xưa — Bolero", "Đêm 🎶 mưa", "Thử nbsp", "日本語 プレイリスト", "a-b_c.d e")
        for (name in wanted) s = s.createList(name).also { assertNull(name, it.refusal) }.state
        val back = (NamedPlaylistCodec.decode(NamedPlaylistCodec.encode(s), userA.value) as NamedPlaylistCodec.Decoded.Ok).state
        assertEquals(wanted.map { NamedPlaylistNames.clean(it) }, back.lists().map { it.name })
        assertEquals(s, back)
        val text = String(NamedPlaylistCodec.encode(s))
        assertFalse("names are not written in clear", text.contains("Nhạc"))
    }

    @Test fun nameCodingIsStrict() {
        assertEquals("Đêm", NamedPlaylistCodec.decodeName(NamedPlaylistCodec.encodeName("Đêm")))
        assertNull("padding is not part of the format", NamedPlaylistCodec.decodeName(NamedPlaylistCodec.encodeName("Đêm") + "="))
        assertNull("not base64url", NamedPlaylistCodec.decodeName("ab+/"))
        assertNull("not valid UTF-8", NamedPlaylistCodec.decodeName(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(0xC3.toByte(), 0x28))))
        assertNull(NamedPlaylistCodec.decodeName("a"))
    }

    @Test fun anyChangedByteIsRejectedByTheChecksum() {
        val bytes = NamedPlaylistCodec.encode(richState())
        for (at in listOf(10, bytes.size / 2, bytes.size - 80)) {
            val broken = bytes.copyOf().also { it[at] = (it[at].toInt() xor 0x01).toByte() }
            val decoded = NamedPlaylistCodec.decode(broken, userA.value)
            assertEquals("at=$at", NamedPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.FORMAT), decoded)
        }
    }

    @Test fun truncatedOrEmptyTextIsRejected() {
        val bytes = NamedPlaylistCodec.encode(richState())
        for (cut in listOf(0, 5, bytes.size / 2, bytes.size - 1)) {
            val decoded = NamedPlaylistCodec.decode(bytes.copyOf(cut), userA.value)
            assertTrue("cut=$cut", decoded is NamedPlaylistCodec.Decoded.Invalid)
        }
    }

    private fun sealed(body: String): ByteArray =
        (body + "END\t" + TelegramPlaylistCodec.sha256Hex(body) + "\n").toByteArray()

    private val goodBody: String
        get() = String(NamedPlaylistCodec.encode(NamedPlaylistState.empty(1001L, "dev-aaa"))).substringBefore("END\t")

    private fun invalid(body: String, reason: PlaylistUnreadableReason = PlaylistUnreadableReason.FORMAT) =
        assertEquals(NamedPlaylistCodec.Decoded.Invalid(reason), NamedPlaylistCodec.decode(sealed(body), 1001L))

    @Test fun anUnknownLineTypeIsRejectedNotSkipped() = invalid(goodBody + "X\twhatever\n")

    @Test fun aRepeatedSingletonLineIsRejected() = invalid(goodBody + "lamport\t5\n")

    @Test fun aV2FileIsNotReadAsNamedPlaylists() {
        val v2 = TelegramPlaylistCodec.encode(PlaylistState.empty(1001L, "dev-aaa"))
        assertEquals(NamedPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.FORMAT), NamedPlaylistCodec.decode(v2, 1001L))
    }

    @Test fun aNewerSchemaIsUnreadableForUsAndNeverReadAsOurOwn() =
        invalid(goodBody.replace("schema\t1", "schema\t2"), PlaylistUnreadableReason.SCHEMA)

    @Test fun anotherAccountsStateIsRejected() {
        val bytes = NamedPlaylistCodec.encode(NamedPlaylistState.empty(2002L, "dev-aaa"))
        assertEquals(
            NamedPlaylistCodec.Decoded.Invalid(PlaylistUnreadableReason.ACCOUNT),
            NamedPlaylistCodec.decode(bytes, 1001L)
        )
    }

    @Test fun badOrRepeatedRecordsAreRejected() {
        val dot = "dev-aaa#1"
        val id = "dev-aaa.1"
        val name = NamedPlaylistCodec.encodeName("Tên")
        val create = "LC\t$id\t$dot\t1\nLN\t$id\t1\t$dot\t$name\n"
        val ok = goodBody.replace("lamport\t0", "lamport\t1").replace("opcounter\t0", "opcounter\t1") + create
        assertTrue(NamedPlaylistCodec.decode(sealed(ok), 1001L) is NamedPlaylistCodec.Decoded.Ok)
        invalid(ok + "LC\t$id\t$dot\t1\n") // the same create tag twice
        invalid(ok + "LN\t$id\t2\tdev-aaa#2\t$name\n") // a second name register
        invalid(ok + "LD\t$id\t$dot\nLD\t$id\t$dot\n")
        invalid(ok + "IP\t$id\ttg:-1:5\t1\t$dot\t10\n") // position key ends in 0
        invalid(ok + "IP\t$id\ttg:-1:5\t1\t$dot\tV\nIP\t$id\ttg:-1:5\t2\tdev-aaa#2\tW\n")
        invalid(ok + "IA\t$id\tlocal:5\t$dot\n") // only Telegram songs have tags
        invalid(ok + "IA\tnot-a-list\ttg:-1:5\t$dot\n")
        invalid(ok + "IA\t$id\ttg:-1:5\tdev-aaa-2\n")
        invalid(ok.replace(name, "!!!!"))
        invalid(ok + "LO\tdev-aaa.9\t5\tV\t0123456789abcdef\n") // a local song of a list that does not exist
        invalid(ok + "LO\t$id\t5\tV\t/storage/x.mp3\n") // a path instead of a fingerprint
        invalid(ok + "LO\t$id\t5\tV\t0123456789abcdef\nLO\t$id\t5\tW\t0123456789abcdef\n")
        invalid(ok + "O\tdev-aaa#1\tLCREATE\t$id\t-\t1\t$dot\t$name\t-\tPENDING\t0\nO\tdev-aaa#1\tLCREATE\t$id\t-\t1\t$dot\t$name\t-\tPENDING\t0\n")
        invalid(ok + "O\tdev-aaa#2\tLRENAME\t$id\t-\t2\t-\t-\t-\tPENDING\t0\n") // a rename without a name
        invalid(ok + "O\tdev-aaa#2\tIMOVE\t$id\tlocal:5\t2\t-\t-\tV\tPENDING\t0\n") // a local song in an operation
        invalid(ok + "O\tdev-aaa#2\tIMOVE\t$id\ttg:-1:5\t2\t-\t-\tV\tSOMETIMES\t0\n")
        invalid(ok + "O\tdev-aaa#2\tIMOVE\t$id\ttg:-1:5\t2\t-\t-\tV\tPENDING\t-1\n")
    }

    @Test fun anOperationKeepsItsIdAcrossSaveRetryAndReload() {
        var s = NamedPlaylistState.empty(userA.value, "dev-aaa")
        val id = checkNotNull(s.createList("Gym").also { s = it.state }.listId)
        s = s.addTelegram(id, tg(userA.value, 5_000_000L)).state
        val retried = s.markRetryRequired("dev-aaa#2")
        val back = (NamedPlaylistCodec.decode(NamedPlaylistCodec.encode(retried), userA.value) as NamedPlaylistCodec.Decoded.Ok).state
        assertEquals(listOf("dev-aaa#1", "dev-aaa#2"), back.outbox.map { it.op.opId })
        assertEquals(OutboxStatus.RETRY_REQUIRED, back.outbox[1].status)
        assertEquals(s.outbox.map { it.op }, back.outbox.map { it.op })
    }

    // ---- file -----------------------------------------------------------------------------------

    @Test fun neverSavedIsMissing() {
        assertEquals(NamedLoadResult.Missing, store().load(userA))
        assertFalse(fileOf(userA).exists())
    }

    @Test fun saveThenLoadIsValidAndSurvivesARestart() {
        val s = richState()
        store().save(userA, s)
        assertEquals(NamedLoadResult.Valid(s), store().load(userA)) // a new store object = a new process
    }

    @Test fun theNamedStoreLivesInItsOwnDirectoryWithItsOwnKeyAliasAndAssociatedData() {
        store().save(userA, richState())
        assertTrue(fileOf(userA).isFile)
        assertFalse(File(File(folder.root, "tg_playlists_v2"), fileOf(userA).name).exists())
        assertFalse(File(File(folder.root, "tg_playlists_v1"), fileOf(userA).name).exists())
        assertEquals("tg_named_playlists_v1", NamedPlaylistStore.DIRECTORY)
        assertNotEquals(KeystorePlaylistCipher.V1_ALIAS, KeystorePlaylistCipher.NAMED_V1_ALIAS)
        assertNotEquals(KeystorePlaylistCipher.V2_ALIAS, KeystorePlaylistCipher.NAMED_V1_ALIAS)
        assertEquals("melovault_e91_saved_playlist_aes_v1", KeystorePlaylistCipher.V1_ALIAS) // the old keys are never renamed
        assertEquals("melovault_e96_playlist_v2_aes_v1", KeystorePlaylistCipher.V2_ALIAS)
        // the associated data is "nl1:" + directory: the blob does not open with the V2 one
        val cipher = TestAesGcmCipher(3)
        val blob = fileOf(userA).readBytes()
        val dir = TelegramAccountStoragePolicy.directoryName(userA)
        assertTrue(cipher.decrypt(("nl1:$dir").toByteArray(), blob).isNotEmpty())
        try {
            cipher.decrypt(("v2:$dir").toByteArray(), blob)
            fail("must not open with another store's associated data")
        } catch (_: Exception) {
        }
    }

    @Test fun aDamagedFileIsUnreadableAndSaveNeverOverwritesIt() {
        store().save(userA, richState())
        val damaged = fileOf(userA).readBytes().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        fileOf(userA).writeBytes(damaged)
        assertEquals(NamedLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), store().load(userA))
        try {
            store().save(userA, NamedPlaylistState.empty(userA.value, "dev-new"))
            fail("must refuse to overwrite")
        } catch (e: PlaylistUnreadableException) {
            assertEquals(PlaylistUnreadableReason.DECRYPT, e.reason)
        }
        assertArrayEquals(damaged, fileOf(userA).readBytes())
    }

    @Test fun aMissingKeyNeverCreatesANewOneForAnExistingFile() {
        store().save(userA, richState())
        val before = fileOf(userA).readBytes()
        var encrypts = 0
        val keyGone = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray { encrypts++; return ByteArray(40) }
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray = throw IllegalStateException("Playlist key is not available")
        }
        val s = store(keyGone)
        assertEquals(NamedLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), s.load(userA))
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
        val good = TestAesGcmCipher(3)
        val encryptFails = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray = throw GeneralSecurityException("keystore")
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray = good.decrypt(aad, blob)
        }
        try {
            store(encryptFails).save(userA, NamedPlaylistState.empty(userA.value, "dev-new"))
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
        val id = s1.lists().first().id
        try {
            store(io = failing).save(userA, s1.addTelegram(id, tg(-100999L, 1L)).state)
            fail("must fail")
        } catch (_: IOException) {
        }
        assertEquals(NamedLoadResult.Valid(s1), store().load(userA))
    }

    @Test fun aBlobThatDoesNotReadBackIsNeverWritten() {
        store().save(userA, richState())
        val before = fileOf(userA).readBytes()
        val good = TestAesGcmCipher(3)
        var decrypts = 0
        val liar = object : PlaylistCipher {
            override fun encrypt(aad: ByteArray, plain: ByteArray): ByteArray = good.encrypt(aad, plain)
            override fun decrypt(aad: ByteArray, blob: ByteArray): ByteArray =
                if (decrypts++ == 0) good.decrypt(aad, blob) else NamedPlaylistCodec.encode(NamedPlaylistState.empty(userA.value, "dev-lie"))
        }
        try {
            store(liar).save(userA, richState().let { it.copy(lamport = it.lamport + 1) })
            fail("verification must fail")
        } catch (_: IllegalStateException) {
        }
        assertArrayEquals(before, fileOf(userA).readBytes())
    }

    @Test fun aNewerSchemaFileIsNeverOverwritten() {
        val cipher = TestAesGcmCipher(3)
        val aad = ("nl1:" + TelegramAccountStoragePolicy.directoryName(userA)).toByteArray()
        val newer = sealed(goodBody.replace("account\t1001", "account\t${userA.value}").replace("schema\t1", "schema\t9"))
        fileOf(userA).parentFile!!.mkdirs()
        fileOf(userA).writeBytes(cipher.encrypt(aad, newer))
        val before = fileOf(userA).readBytes()
        assertEquals(NamedLoadResult.Unreadable(PlaylistUnreadableReason.SCHEMA), store(cipher).load(userA))
        try {
            store(cipher).save(userA, NamedPlaylistState.empty(userA.value, "dev-new"))
            fail("must refuse")
        } catch (e: PlaylistUnreadableException) {
            assertEquals(PlaylistUnreadableReason.SCHEMA, e.reason)
        }
        assertArrayEquals(before, fileOf(userA).readBytes())
    }

    @Test fun sizeBoundsAndAnOddFileAreUnreadableNotMissing() {
        store().save(userA, richState())
        fileOf(userA).writeBytes(ByteArray(27) { 1 })
        assertEquals(NamedLoadResult.Unreadable(PlaylistUnreadableReason.SIZE), store().load(userA))
        fileOf(userA).delete()
        assertTrue(fileOf(userA).mkdirs())
        assertEquals(NamedLoadResult.Unreadable(PlaylistUnreadableReason.IO), store().load(userA))
    }

    @Test fun twoAccountsNeverShareAFileAndASwappedFileIsRejected() {
        val a = richState(userA.value)
        val b = NamedPlaylistState.empty(userB.value, "dev-bbb").createList("Của B").state
        val s = store()
        s.save(userA, a)
        s.save(userB, b)
        assertEquals(NamedLoadResult.Valid(a), s.load(userA))
        assertEquals(NamedLoadResult.Valid(b), s.load(userB))
        fileOf(userA).copyTo(fileOf(userB), overwrite = true) // a file moved onto another account
        assertEquals(NamedLoadResult.Unreadable(PlaylistUnreadableReason.DECRYPT), s.load(userB))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aStateOfAnotherAccountCannotBeSaved() {
        store().save(userA, NamedPlaylistState.empty(userB.value, "dev-bbb"))
    }

    @Test fun theLargestAllowedStateStillFitsTheFileLimit() {
        var s = NamedPlaylistState.empty(userA.value, "dev-aaa")
        val id = checkNotNull(s.createList("Lớn").also { s = it.state }.listId)
        for (n in 1L..300L) s = s.addTelegram(id, tg(-1001234567890L, 1_000_000L + n)).state
        val bytes = NamedPlaylistCodec.encode(s)
        assertTrue("300 songs is far below 8 MB, got ${bytes.size}", bytes.size < NamedPlaylistStore.MAX_BLOB_BYTES / 20)
        store().save(userA, s)
        assertEquals(NamedLoadResult.Valid(s), store().load(userA))
    }
}
