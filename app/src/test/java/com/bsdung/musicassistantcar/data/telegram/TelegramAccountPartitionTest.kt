package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TelegramAccountPartitionTest {
    @get:Rule val temp = TemporaryFolder()
    private val userA = TelegramUserId(34120001L)
    private val userB = TelegramUserId(34120002L)

    private fun entry(owner: TelegramUserId, title: String) =
        TelegramAudioEntry(owner, chatId = -1002323L, messageId = 55L,
            title = title, format = "audio/flac")

    @Test fun unauthenticatedLibraryCannotAccessOrAcceptTracks() {
        val lib = TelegramAccountLibrary()
        assertTrue(lib.currentItems().isEmpty())
        assertFalse(lib.mayOpenLocalFile(File(temp.root, "track.flac"), temp.root))
        assertThrows(IllegalStateException::class.java) { lib.accept(entry(userA, "private")) }
    }

    @Test fun twoAccountsWithIdenticalChatAndMessageIdsRemainSeparated() {
        val lib = TelegramAccountLibrary()
        lib.onAuthenticatedUser(userA)
        lib.accept(entry(userA, "A only"))
        assertEquals(listOf("A only"), lib.currentItems().map { it.title })
        assertThrows(IllegalArgumentException::class.java) {
            lib.accept(entry(userB, "intrusion"))
        }
        lib.onAuthenticatedUser(userB)
        assertTrue(lib.currentItems().isEmpty())
        lib.accept(entry(userB, "B only"))
        assertEquals(listOf("B only"), lib.currentItems().map { it.title })
        lib.onAuthenticatedUser(userA)
        assertEquals(listOf("A only"), lib.currentItems().map { it.title })
        lib.logout()
        assertTrue(lib.currentItems().isEmpty())
        assertEquals(null, lib.authorizedUser())
    }

    @Test fun accountDirectoriesAreDistinctAndNeverUseNamesOrPhoneNumbers() {
        val a = TelegramAccountStoragePolicy.directoryName(userA)
        val b = TelegramAccountStoragePolicy.directoryName(userB)
        assertNotEquals(a, b)
        assertEquals(67, a.length)
        assertTrue(a.matches(Regex("tg-[0-9a-f]{64}")))
        val root = temp.root
        val sessionA = TelegramAccountStoragePolicy.sessionDirectory(root, userA)
        val downloadA = TelegramAccountStoragePolicy.downloadDirectory(root, userA)
        assertNotEquals(sessionA, downloadA)
        assertNotEquals(
            TelegramAccountStoragePolicy.streamCacheDirectory(root, userA),
            TelegramAccountStoragePolicy.streamCacheDirectory(root, userB)
        )
        assertFalse(sessionA.path.contains("phone"))
        assertEquals(
            TelegramAccountStoragePolicy.sessionDirectory(root, userA),
            TelegramAccountStoragePolicy.sessionDirectory(root, userA)
        )
    }

    @Test fun downloadedFileMustBelongToAuthenticatedUserOnly() {
        val appFiles = temp.newFolder("private_app_files")
        val aRoot = TelegramAccountStoragePolicy.downloadDirectory(appFiles, userA)
        val bRoot = TelegramAccountStoragePolicy.downloadDirectory(appFiles, userB)
        assertTrue(aRoot.mkdirs())
        assertTrue(bRoot.mkdirs())
        val aFile = File(aRoot, "song.flac").apply { writeText("fake") }
        val bFile = File(bRoot, "song.flac").apply { writeText("fake") }
        val lib = TelegramAccountLibrary()
        lib.onAuthenticatedUser(userA)
        assertTrue(lib.mayOpenLocalFile(aFile, appFiles))
        assertFalse(lib.mayOpenLocalFile(bFile, appFiles))
        assertFalse(lib.mayOpenLocalFile(File(aRoot, "../outside.flac"), appFiles))
        assertFalse(lib.mayOpenLocalFile(aRoot, appFiles))
        lib.onAuthenticatedUser(userB)
        assertFalse(lib.mayOpenLocalFile(aFile, appFiles))
        assertTrue(lib.mayOpenLocalFile(bFile, appFiles))
        lib.logout()
        assertFalse(lib.mayOpenLocalFile(bFile, appFiles))
    }

    @Test fun rejectInvalidTelegramIdentityOrMessageIds() {
        assertThrows(IllegalArgumentException::class.java) { TelegramUserId(0L) }
        assertThrows(IllegalArgumentException::class.java) { TelegramUserId(-1L) }
        assertThrows(IllegalArgumentException::class.java) {
            TelegramAudioEntry(userA, chatId = 0L, messageId = 1L,
                title = "bad", format = "audio/flac")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TelegramAudioEntry(userA, chatId = 1L, messageId = 0L,
                title = "bad", format = "audio/flac")
        }
    }
}
