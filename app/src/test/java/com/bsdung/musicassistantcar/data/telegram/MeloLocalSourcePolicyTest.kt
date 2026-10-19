package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloLocalSourcePolicy.PermissionStep
import com.bsdung.musicassistantcar.data.telegram.MeloSourceCardPolicy.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MeloLocalSourcePolicyTest {

    private fun song(id: Long, title: String, artist: String? = null, album: String? = null, path: String = "Music/Test") =
        MeloLocalSourcePolicy.songOf(id, title, artist, album, path, 1000L, 2000L, "audio/flac")!!

    // 1. Local key is negative, below -2^62, reversible and unique.
    @Test
    fun localKeyIsNegativeAndReversible() {
        for (id in listOf(1L, 2L, 42L, 3_557L, 1_000_000L, (1L shl 40), (1L shl 62) - 1)) {
            val key = MeloLocalSourcePolicy.keyOf(id)
            assertTrue("key=$key", key <= -(1L shl 62))
            assertTrue(MeloLocalSourcePolicy.isLocalKey(key))
            assertEquals(id, MeloLocalSourcePolicy.idOfKey(key))
        }
        assertNotEquals(MeloLocalSourcePolicy.keyOf(41), MeloLocalSourcePolicy.keyOf(42))
        for (bad in listOf(0L, -1L, Long.MIN_VALUE, 1L shl 62)) {
            try {
                MeloLocalSourcePolicy.keyOf(bad)
                fail("keyOf($bad) must throw")
            } catch (expected: IllegalArgumentException) {
            }
        }
    }

    // 2. Never collides with a Telegram key (>= 0) and fails every "key > 0" guard.
    @Test
    fun neverCollidesWithTelegramKeys() {
        val saved = 777L
        val telegram = buildList {
            add(TelegramSongKey.forMessage(saved, 1L, saved))
            add(TelegramSongKey.forMessage(saved, Int.MAX_VALUE.toLong(), saved))
            for (chat in listOf(-1001234567890L, -1009876543210L, 42L, 5555555555L)) {
                for (message in listOf(1L, 2L, 99L, 123_456_789L)) add(TelegramSongKey.forMessage(chat, message, saved))
            }
        }
        for (key in telegram) {
            assertTrue(key >= 0)
            assertFalse(MeloLocalSourcePolicy.isLocalKey(key))
            assertNull(MeloLocalSourcePolicy.idOfKey(key))
        }
        val local = (1L..500L).map { MeloLocalSourcePolicy.keyOf(it) }
        assertTrue(local.none { it > 0 || it == 0L || it == -1L })
        assertTrue(local.toSet().intersect(telegram.toSet()).isEmpty())
        assertNull(MeloLocalSourcePolicy.idOfKey(-1L))
        assertNull(MeloLocalSourcePolicy.idOfKey(0L))
    }

    // 3. mediaId only for local:<positive>, and the Telegram parsers cannot read it.
    @Test
    fun mediaIdRoundTripAndRejection() {
        assertEquals("local:42", MeloLocalSourcePolicy.mediaIdOf(42))
        assertEquals(42L, MeloLocalSourcePolicy.idOfMediaId("local:42"))
        for (bad in listOf("telegram:42", "device:external_primary:42", "picked:audio", "local:", "local:-1", "local:abc",
            "local:0", "local:4 2", "LOCAL:42", "xlocal:42", "local:99999999999999999999", "", null)) {
            assertNull("bad=$bad", MeloLocalSourcePolicy.idOfMediaId(bad))
        }
        assertNull("local:42".removePrefix("telegram:").toLongOrNull())
        try {
            MeloLocalSourcePolicy.mediaIdOf(0)
            fail("mediaIdOf(0) must throw")
        } catch (expected: IllegalArgumentException) {
        }
    }

    // 4. Artist shown: real artist, else last folder, else null.
    @Test
    fun displayArtistFallsBackToFolder() {
        for (raw in listOf("<unknown>", "Nghệ sĩ không rõ", "nghệ sĩ không rõ", "", "   ", null)) {
            assertEquals("Vietnam", MeloLocalSourcePolicy.displayArtist(raw, "Music/Vietnam"))
        }
        assertEquals("Vietnam", MeloLocalSourcePolicy.displayArtist(null, "Music/Vietnam/"))
        assertNull(MeloLocalSourcePolicy.displayArtist(null, ""))
        assertNull(MeloLocalSourcePolicy.displayArtist("<unknown>", null))
        assertEquals("Sơn Tùng", MeloLocalSourcePolicy.displayArtist(" Sơn Tùng ", "Music/Vietnam"))
        assertEquals("", MeloLocalSourcePolicy.folderOf(""))
    }

    // 5. Sort NEWEST and NAME (accent-insensitive, tie by id).
    @Test
    fun sortNewestAndName() {
        val songs = listOf(song(5, "Đêm"), song(9, "Ánh"), song(2, "Zebra"), song(7, "Bình"), song(3, "Ánh"))
        assertEquals(listOf(9L, 7L, 5L, 3L, 2L), MeloLocalSourcePolicy.sorted(songs, SongSort.NEWEST).map { it.mediaStoreId })
        val byName = MeloLocalSourcePolicy.sorted(songs, SongSort.NAME)
        assertEquals(listOf("Ánh", "Ánh", "Bình", "Đêm", "Zebra"), byName.map { it.title })
        assertEquals(listOf(3L, 9L), byName.take(2).map { it.mediaStoreId })
        for (s in byName) assertEquals(MeloAlphabetPolicy.keyOf(s.title, s.artist, null), s.alphaKey)
        assertEquals(songs.size, byName.size)
    }

    // 6. Search: no accents, AND of words, title/artist/album/folder, empty = unchanged.
    @Test
    fun filterIsAccentInsensitiveAnd() {
        val a = song(1, "Sử Thanh Hóa", artist = "Ca sĩ Một", album = "Album Alpha", path = "Music/Quê Hương")
        val b = song(2, "Biển nhớ", artist = "Ai đó", path = "Music/Other")
        val songs = listOf(a, b)
        assertEquals(listOf(1L), MeloLocalSourcePolicy.filtered(songs, "su thanh hoa").map { it.mediaStoreId })
        assertEquals(listOf(1L), MeloLocalSourcePolicy.filtered(songs, "thanh  SU").map { it.mediaStoreId })
        assertEquals(listOf(1L), MeloLocalSourcePolicy.filtered(songs, "ca si mot").map { it.mediaStoreId })
        assertEquals(listOf(1L), MeloLocalSourcePolicy.filtered(songs, "alpha").map { it.mediaStoreId })
        assertEquals(listOf(1L), MeloLocalSourcePolicy.filtered(songs, "que huong").map { it.mediaStoreId })
        assertEquals(listOf(2L), MeloLocalSourcePolicy.filtered(songs, "bien other").map { it.mediaStoreId })
        assertEquals(songs, MeloLocalSourcePolicy.filtered(songs, ""))
        assertEquals(songs, MeloLocalSourcePolicy.filtered(songs, null))
        assertEquals(songs, MeloLocalSourcePolicy.filtered(songs, "   "))
        assertTrue(MeloLocalSourcePolicy.filtered(songs, "khong co").isEmpty())
        assertTrue(MeloLocalSourcePolicy.filtered(songs, "su bien").isEmpty())
    }

    // 7. Two files with the same name stay two songs with two keys.
    @Test
    fun duplicateTitlesStayDistinct() {
        val first = song(10, "Cùng tên", path = "Music/A")
        val second = song(11, "Cùng tên", path = "Music/B")
        assertNotEquals(first.key, second.key)
        assertNotEquals(MeloLocalSourcePolicy.mediaIdOf(10), MeloLocalSourcePolicy.mediaIdOf(11))
        val songs = listOf(first, second)
        assertEquals(2, MeloLocalSourcePolicy.sorted(songs, SongSort.NAME).size)
        assertEquals(2, MeloLocalSourcePolicy.sorted(songs, SongSort.NEWEST).size)
        assertEquals(2, MeloLocalSourcePolicy.filtered(songs, "cung ten").size)
    }

    // 8. Queue window: at most 500, 100 before, always holds the tapped index.
    @Test
    fun queueWindowBounds() {
        assertTrue(MeloLocalSourcePolicy.queueWindow(0, 0).isEmpty())
        assertEquals(0..9, MeloLocalSourcePolicy.queueWindow(10, 3))
        assertEquals(0..499, MeloLocalSourcePolicy.queueWindow(2000, 0))
        assertEquals(1400..1899, MeloLocalSourcePolicy.queueWindow(2000, 1500))
        assertEquals(1500..1999, MeloLocalSourcePolicy.queueWindow(2000, 1999))
        assertEquals(0..499, MeloLocalSourcePolicy.queueWindow(2000, -5))
        assertEquals(1500..1999, MeloLocalSourcePolicy.queueWindow(2000, 99_999))
        for (index in listOf(0, 1, 99, 100, 101, 499, 500, 1000, 1899, 1900, 1999)) {
            val range = MeloLocalSourcePolicy.queueWindow(2000, index)
            assertTrue("index=$index range=$range", index in range)
            assertEquals(500, range.last - range.first + 1)
        }
        assertEquals(0..499, MeloLocalSourcePolicy.queueWindow(500, 250))
    }

    // 9. Permission name by SDK.
    @Test
    fun permissionNameBySdk() {
        for (sdk in listOf(26, 29, 30, 31, 32)) {
            assertEquals("android.permission.READ_EXTERNAL_STORAGE", MeloLocalSourcePolicy.permissionFor(sdk))
        }
        for (sdk in listOf(33, 34, 36)) {
            assertEquals("android.permission.READ_MEDIA_AUDIO", MeloLocalSourcePolicy.permissionFor(sdk))
        }
    }

    // 10. Permission steps.
    @Test
    fun permissionSteps() {
        assertEquals(PermissionStep.OPEN, MeloLocalSourcePolicy.permissionStep(true, false, false))
        assertEquals(PermissionStep.OPEN, MeloLocalSourcePolicy.permissionStep(true, true, true))
        assertEquals(PermissionStep.EXPLAIN_THEN_ASK, MeloLocalSourcePolicy.permissionStep(false, false, false))
        assertEquals(PermissionStep.EXPLAIN_THEN_ASK, MeloLocalSourcePolicy.permissionStep(false, true, true))
        assertEquals(PermissionStep.OPEN_SETTINGS, MeloLocalSourcePolicy.permissionStep(false, true, false))
    }

    // 11. Card only with permission and songs; sits after Telegram without "đã tải".
    @Test
    fun cardSourceRules() {
        assertNull(MeloLocalSourcePolicy.cardSource(false, 3))
        assertNull(MeloLocalSourcePolicy.cardSource(true, null))
        assertNull(MeloLocalSourcePolicy.cardSource(true, 0))
        assertNull(MeloLocalSourcePolicy.cardSource(true, -2))
        val local = MeloLocalSourcePolicy.cardSource(true, 3)
        assertEquals(MeloSourceCardPolicy.Source(Kind.LOCAL, 3, null), local)
        val cards = MeloSourceCardPolicy.cards(
            listOf(local!!, MeloSourceCardPolicy.Source(Kind.TELEGRAM, 25_105, 67)),
            Kind.LOCAL
        )
        assertEquals(listOf(Kind.TELEGRAM, Kind.LOCAL), cards.map { it.kind })
        assertEquals("3 bài", cards[1].detail)
        assertFalse(cards[1].detail.contains("đã tải"))
        assertTrue(cards[1].selected)
        assertEquals("Nhạc trên máy", cards[1].title)
    }

    // 12. Note text.
    @Test
    fun noteTexts() {
        assertEquals("Đang đọc nhạc trên máy…", MeloLocalSourcePolicy.note(true, 0, 0, null))
        assertEquals("Không tìm thấy bài nhạc nào trên máy.", MeloLocalSourcePolicy.note(false, 0, 0, null))
        assertEquals("Không có bài khớp.", MeloLocalSourcePolicy.note(false, 120, 0, "xyz"))
        assertNull(MeloLocalSourcePolicy.note(false, 120, 120, null))
        assertNull(MeloLocalSourcePolicy.note(false, 120, 3, "abc"))
        assertNull(MeloLocalSourcePolicy.note(true, 120, 120, null))
    }

    // 13. Queue-next only inside one source.
    @Test
    fun queueNextStaysInsideOneSource() {
        assertTrue(MeloLocalSourcePolicy.canQueueNext(currentIsLocal = true, songIsLocal = true))
        assertTrue(MeloLocalSourcePolicy.canQueueNext(currentIsLocal = false, songIsLocal = false))
        assertFalse(MeloLocalSourcePolicy.canQueueNext(currentIsLocal = true, songIsLocal = false))
        assertFalse(MeloLocalSourcePolicy.canQueueNext(currentIsLocal = false, songIsLocal = true))
    }

    // 14. songOf validation and field handling.
    @Test
    fun songOfValidatesAndKeepsFields() {
        assertNull(MeloLocalSourcePolicy.songOf(0, "x", null, null, "", 0, 0, null))
        assertNull(MeloLocalSourcePolicy.songOf(-4, "x", null, null, "", 0, 0, null))
        val zero = MeloLocalSourcePolicy.songOf(5, "Tên", null, null, "", 0, 0, null)!!
        assertEquals(0L, zero.durationMs)
        assertNull(zero.mimeType)
        assertNull(zero.artist)
        assertNull(zero.album)
        assertEquals("", zero.folder)
        val full = MeloLocalSourcePolicy.songOf(6, "  Tên  ", "Ai", "Album", "Music/Pop", 1234, 5678, "audio/mpeg")!!
        assertEquals("Tên", full.title)
        assertEquals(1234L, full.durationMs)
        assertEquals(5678L, full.sizeBytes)
        assertEquals("audio/mpeg", full.mimeType)
        assertEquals("Pop", full.folder)
        assertEquals(MeloLocalSourcePolicy.keyOf(6), full.key)
        assertEquals("Nhạc thiết bị #7", MeloLocalSourcePolicy.songOf(7, "  ", null, null, "", 0, 0, null)!!.title)
        assertNull(MeloLocalSourcePolicy.songOf(8, "a", null, null, "", 0, 0, "   ")!!.mimeType)
    }

    // 15. Log format has MIME types and counts only.
    @Test
    fun formatCountsHasOnlyMimeAndNumbers() {
        assertEquals("", MeloLocalSourcePolicy.formatCounts(emptyList()))
        assertEquals(
            "audio/flac=3 audio/mpeg=2 other=1",
            MeloLocalSourcePolicy.formatCounts(
                listOf("audio/flac", "audio/mpeg", "audio/flac", null, "AUDIO/MPEG", "audio/flac")
            )
        )
        val hostile = MeloLocalSourcePolicy.formatCounts(listOf("/storage/emulated/0/Music/Secret Song.mp3", "ten bai hat"))
        assertEquals("other=2", hostile)
    }

    // 16. Search key text and alpha key are built from displayed fields.
    @Test
    fun searchTextAndAlphaKeyUseDisplayedFields() {
        val s = MeloLocalSourcePolicy.songOf(3, "Đường Về", "<unknown>", "Álbum Mới", "Music/Quê Nhà", 0, 0, null)!!
        assertEquals("quê nhà", s.artist!!.lowercase())
        assertTrue("duong ve" in s.searchText)
        assertTrue("album moi" in s.searchText)
        assertTrue("que nha" in s.searchText)
        assertFalse("khong ro" in s.searchText)
        assertTrue(s.alphaKey.startsWith("duong"))
        assertEquals('D', MeloAlphabetPolicy.letterOf(s.alphaKey))
    }

    // 17. Ids that are not real MediaStore ids never become songs.
    @Test
    fun hugeIdIsRejected() {
        assertNull(MeloLocalSourcePolicy.songOf(1L shl 62, "x", null, null, "", 0, 0, null))
        assertNotNull(MeloLocalSourcePolicy.songOf((1L shl 62) - 1, "x", null, null, "", 0, 0, null))
    }

    // 18. Negative sizes and durations are clamped.
    @Test
    fun negativeNumbersAreClamped() {
        val s = MeloLocalSourcePolicy.songOf(9, "x", null, null, "", -5, -9, "audio/flac")!!
        assertEquals(0L, s.durationMs)
        assertEquals(0L, s.sizeBytes)
    }

    // 19. Constants the Activity relies on.
    @Test
    fun constantsMatchPlan() {
        assertEquals("local:", MeloLocalSourcePolicy.MEDIA_ID_PREFIX)
        assertEquals("Nhạc trên máy", MeloLocalSourcePolicy.SOURCE_TITLE)
        assertEquals(200, MeloLocalSourcePolicy.PAGE_SIZE)
        assertEquals(500, MeloLocalSourcePolicy.QUEUE_MAX)
        assertEquals(100, MeloLocalSourcePolicy.QUEUE_BEFORE)
        assertEquals("melovault_local_music", MeloLocalSourcePolicy.PREFS_NAME)
        assertEquals("read_audio_asked", MeloLocalSourcePolicy.KEY_ASKED)
        assertEquals(5 * 60_000L, MeloLocalSourcePolicy.RELOAD_AFTER_MS)
    }

    // 21. Row status text: "Trên máy" for local songs, "Đã tải" unchanged for Telegram.
    @Test
    fun presentStatusSaysTrenMayForLocalSongs() {
        val downloaded = MeloComponentPolicy.Status.DOWNLOADED
        assertEquals("Trên máy · FLAC", MeloComponentPolicy.presentStatus(downloaded, "FLAC", local = true).label)
        assertEquals("Trên máy", MeloComponentPolicy.presentStatus(downloaded, null, local = true).label)
        assertEquals("Trên máy", MeloComponentPolicy.presentStatus(downloaded, " ", local = true).label)
        // Default and explicit false keep today's wording.
        assertEquals("Đã tải · FLAC", MeloComponentPolicy.presentStatus(downloaded, "FLAC").label)
        assertEquals("Đã tải · FLAC", MeloComponentPolicy.presentStatus(downloaded, "FLAC", local = false).label)
        // Other states ignore the flag.
        assertEquals("Đang phát", MeloComponentPolicy.presentStatus(MeloComponentPolicy.Status.PLAYING, "FLAC", local = true).label)
        assertEquals("Tạm dừng", MeloComponentPolicy.presentStatus(MeloComponentPolicy.Status.PAUSED, null, local = true).label)
        assertEquals("Telegram · Chưa tải", MeloComponentPolicy.presentStatus(MeloComponentPolicy.Status.TELEGRAM, null, local = true).label)
    }

    // 20. The queue window applied to a real list keeps order and uniqueness.
    @Test
    fun queueWindowOverSortedListHasUniqueSongs() {
        val songs = (1L..1200L).map { song(it, "Bài %04d".format(it)) }
        val sorted = MeloLocalSourcePolicy.sorted(songs, SongSort.NAME)
        val range = MeloLocalSourcePolicy.queueWindow(sorted.size, 700)
        val slice = sorted.slice(range)
        assertEquals(500, slice.size)
        assertEquals(slice.size, slice.map { it.mediaStoreId }.toSet().size)
        assertTrue(sorted[700] in slice)
        assertEquals(slice, slice.sortedWith(compareBy({ it.alphaKey }, { it.mediaStoreId })))
    }
}
