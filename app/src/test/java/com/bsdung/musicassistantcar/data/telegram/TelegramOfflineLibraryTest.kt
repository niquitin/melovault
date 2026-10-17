package com.bsdung.musicassistantcar.data.telegram

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TelegramOfflineLibraryTest {
    @get:Rule val temp = TemporaryFolder()
    private fun song(id: Long, title: String = "曲 · Mix", chat: Long = 10, account: Long = 1) =
        IndexedTelegramSong(account, chat, id, chat * 10000 + id, id.toInt(), title, "$id.flac")

    @Test fun discoversBeyondFirstPage() {
        val root = temp.newFolder()
        val file = File(root, "audio").apply { writeText("audio") }
        val rows = (1L..650).map { song(it) }
        val offsets = mutableListOf<Int>()
        var maxBatch = 0
        val result = TelegramOfflineLibrary.scan(1, root,
            page = { offset, limit -> offsets.add(offset); rows.drop(offset).take(limit) },
            resolve = { batch ->
                maxBatch = maxOf(maxBatch, batch.size)
                batch.map { if (it.messageId == 601L) TelegramOfflineLibrary.Local.Complete(file.path, 5)
                    else TelegramOfflineLibrary.Local.NotDownloaded }
            }, cancelled = { false }, progress = {})!!
        assertEquals(listOf(0, 200, 400, 600, 650), offsets)
        assertTrue(maxBatch <= 32)
        assertEquals(650, result.checked)
        assertEquals(601L, result.entries.single().song.messageId)
        assertTrue(result.finished)
    }

    @Test fun daoSearchAndSourceFiltersSurvivePaginationWithEqualDatesAndMessageIds() {
        val db = InMemoryTelegramLibraryDao()
        db.upsertSongs((1L..450).flatMap { listOf(song(it, "Mix", chat = 10), song(it, "Mix", chat = 20)) })
        db.upsertSongs(listOf(song(999, "Other", chat = 10), song(1000, "Mix", account = 2)))
        val root = temp.newFolder()
        val file = File(root, "audio").apply { writeText("abc") }
        val result = TelegramOfflineLibrary.scan(1, root,
            page = { offset, limit -> db.getSongs(1, chatId = 20, searchQuery = "mix", offset = offset, limit = limit) },
            resolve = { rows -> rows.map { TelegramOfflineLibrary.Local.Complete(file.path, 3) } },
            cancelled = { false }, progress = {})!!
        assertEquals(450, result.entries.size)
        assertTrue(result.entries.all { it.song.chatId == 20L && it.song.title == "Mix" })
        val all = (0..900 step 200).flatMap { db.getSongs(1, offset = it, limit = 200) }
        assertEquals(901, all.map { it.chatId to it.messageId }.distinct().size)
    }

    @Test fun rejectsOffAccountMissingOutsidePartialAndUnknownFiles() {
        val root = temp.newFolder()
        val inside = File(root, "partial").apply { writeText("abc") }
        val outside = temp.newFile().apply { writeText("abc") }
        val inputs = listOf(
            TelegramOfflineLibrary.Local.Complete(inside.path, 4),
            TelegramOfflineLibrary.Local.Complete(outside.path, 3),
            TelegramOfflineLibrary.Local.Complete(File(root, "missing").path, 3),
            TelegramOfflineLibrary.Local.Complete(inside.path, -1),
            TelegramOfflineLibrary.Local.Unknown
        )
        val rows = (1L..5).map { song(it) } + song(6, account = 2)
        val result = TelegramOfflineLibrary.scan(1, root,
            page = { offset, limit -> rows.drop(offset).take(limit) },
            resolve = { batch -> batch.map { inputs[(it.messageId - 1).toInt()] } },
            cancelled = { false }, progress = {})!!
        assertEquals(5, result.checked)
        assertEquals(5, result.unknown)
        assertTrue(result.entries.isEmpty())
    }

    @Test fun deletedFileIsRevalidatedOnNextScan() {
        val root = temp.newFolder()
        val file = File(root, "audio").apply { writeText("abc") }
        val local = TelegramOfflineLibrary.Local.Complete(file.path, 3)
        assertNotNull(TelegramOfflineLibrary.verifiedPath(root, local))
        assertTrue(file.delete())
        assertNull(TelegramOfflineLibrary.verifiedPath(root, local))
    }

    @Test fun cancellationStopsBeforeNextBatchWithoutPublishingFinalCount() {
        var cancel = false
        var calls = 0
        val result = TelegramOfflineLibrary.scan(1, temp.newFolder(),
            page = { _, _ -> (1L..200).map { song(it) } },
            resolve = { batch -> calls++; cancel = true; batch.map { TelegramOfflineLibrary.Local.Unknown } },
            cancelled = { cancel }, progress = { fail("Cancelled scan published results") })
        assertNull(result)
        assertEquals(1, calls)
    }

    @Test fun fourSourcesOfSameFileRemainIndividuallySelectable() {
        val entries = (1L..4).map { TelegramOfflineLibrary.Entry(song(it, chat = it), "/same/audio") }
        val group = TelegramOfflineLibrary.groups(entries).single()
        assertTrue(group.sameFile)
        assertEquals(4, group.entries.size)
        assertEquals(entries[2], group.selected(1, 3, 3))
        assertNull(group.selected(2, 3, 3))
        assertNull(group.selected(1, 4, 3))
    }

    @Test fun sameTitleDifferentContentIsExplicitlyAmbiguousAndAllVersionsSurvive() {
        val entries = listOf(
            TelegramOfflineLibrary.Entry(song(1, "長い曲 🎵"), "/one"),
            TelegramOfflineLibrary.Entry(song(2, "長い曲 🎵"), "/two"))
        val group = TelegramOfflineLibrary.groups(entries).single()
        assertFalse(group.sameFile)
        assertTrue(group.label.startsWith("Các bản cùng tên"))
        assertEquals(entries, group.entries)
    }

    @Test fun sameCanonicalFileWithDifferentTitleStillGroupsWithoutLosingOtherSongs() {
        val entries = listOf(
            TelegramOfflineLibrary.Entry(song(1, "A"), "/one"),
            TelegramOfflineLibrary.Entry(song(2, "B"), "/one"),
            TelegramOfflineLibrary.Entry(song(3, "C"), "/two"))
        val groups = TelegramOfflineLibrary.groups(entries)
        assertEquals(2, groups.size)
        assertTrue(groups.first().sameFile)
        assertEquals(3, groups.sumOf { it.entries.size })
    }

    @Test fun groupingNeverCombinesAccountsEvenWithMatchingPathAndTitle() {
        val groups = TelegramOfflineLibrary.groups(listOf(
            TelegramOfflineLibrary.Entry(song(1, account = 1), "/same"),
            TelegramOfflineLibrary.Entry(song(1, account = 2), "/same")))
        assertEquals(2, groups.size)
    }
}
