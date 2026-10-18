package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.DriverManager

class MeloAlphabetPolicyTest {
    private fun key(t: String) = TelegramSearchPolicy.songSearchKey(t, null, null)

    @Test
    fun lettersFollowAccentInsensitiveKey() {
        assertEquals('A', MeloAlphabetPolicy.letterOf(key("Ánh trăng")))
        assertEquals('D', MeloAlphabetPolicy.letterOf(key("Đường xa")))
        assertEquals('O', MeloAlphabetPolicy.letterOf(key("ồ")))
        assertEquals('#', MeloAlphabetPolicy.letterOf(key("01 - Intro")))
        assertEquals('#', MeloAlphabetPolicy.letterOf(key("[FLAC] x")))
        assertEquals('#', MeloAlphabetPolicy.letterOf(""))
        assertEquals('#', MeloAlphabetPolicy.letterOf(key("ベース")))
    }

    @Test
    fun lowerBound() {
        assertEquals("m", MeloAlphabetPolicy.lowerBound('M'))
        assertNull(MeloAlphabetPolicy.lowerBound('#'))
        assertEquals(27, MeloAlphabetPolicy.ALL_LETTERS.size)
    }

    @Test
    fun firstIndexAtOrAfter() {
        val keys = listOf("1 a", "an", "binh", "duong", "duong xa", "zing")
        assertEquals(0, MeloAlphabetPolicy.firstIndexAtOrAfter(keys, null))
        assertEquals(2, MeloAlphabetPolicy.firstIndexAtOrAfter(keys, "b"))
        assertEquals(3, MeloAlphabetPolicy.firstIndexAtOrAfter(keys, "d"))
        assertEquals(5, MeloAlphabetPolicy.firstIndexAtOrAfter(keys, "m")) // next letter with songs
        assertEquals(6, MeloAlphabetPolicy.firstIndexAtOrAfter(keys, "zz"))
        assertEquals(0, MeloAlphabetPolicy.firstIndexAtOrAfter(emptyList(), "a"))
    }

    @Test
    fun visibleLetters() {
        assertEquals(27, MeloAlphabetPolicy.visibleLetters(27 * 24, 24).size)
        val squeezed = MeloAlphabetPolicy.visibleLetters(300, 24)
        assertTrue(squeezed.size < 27)
        assertEquals('#', squeezed.first())
        assertEquals('Z', squeezed.last())
        assertTrue(300 / squeezed.size >= 24)
    }

    @Test
    fun letterAt() {
        assertEquals('#', MeloAlphabetPolicy.letterAt(-10f, 270))
        assertEquals('#', MeloAlphabetPolicy.letterAt(0f, 270))
        assertEquals('A', MeloAlphabetPolicy.letterAt(15f, 270))
        assertEquals('Z', MeloAlphabetPolicy.letterAt(269f, 270))
        assertEquals('Z', MeloAlphabetPolicy.letterAt(9999f, 270))
    }

    @Test
    fun barVisibility() {
        assertTrue(MeloAlphabetPolicy.barVisible(SongSort.NAME, false, 50))
        assertFalse(MeloAlphabetPolicy.barVisible(SongSort.NEWEST, false, 5000))
        assertFalse(MeloAlphabetPolicy.barVisible(SongSort.NAME, true, 5000))
        assertFalse(MeloAlphabetPolicy.barVisible(SongSort.NAME, false, 49))
        assertTrue(MeloAlphabetPolicy.barVisible(SongSort.NAME, false, 10, hasLowerBound = true))
    }

    @Test
    fun parseSortDefaultsToNewest() {
        assertEquals(SongSort.NEWEST, MeloAlphabetPolicy.parseSort(null))
        assertEquals(SongSort.NEWEST, MeloAlphabetPolicy.parseSort("zzz"))
        assertEquals(SongSort.NAME, MeloAlphabetPolicy.parseSort("NAME"))
    }

    // ---- DAO ordering: InMemory and the SQL used by AndroidTelegramLibraryDatabase agree ----

    private fun song(id: Long, title: String, chat: Long = 2L, date: Long = id, deleted: Boolean = false) =
        IndexedTelegramSong(
            accountId = 1, chatId = chat, messageId = id, songKey = id, fileId = 1,
            title = title, filename = "$title.mp3", date = date, isDeleted = deleted
        )

    private val titles = listOf("Zing", "Đường xa", "Bình", "Ánh trăng", "01 Intro", "Mây", "Đường xa", "Gone")

    private fun seeded(): InMemoryTelegramLibraryDao = InMemoryTelegramLibraryDao().also { dao ->
        dao.upsertSongs(titles.mapIndexed { i, t -> song(i + 1L, t, deleted = t == "Gone") })
    }

    @Test
    fun nameSortOrdersByUnaccentedTitle() {
        val got = seeded().getSongs(1, sort = SongSort.NAME).map { it.title }
        assertEquals(listOf("01 Intro", "Ánh trăng", "Bình", "Đường xa", "Đường xa", "Mây", "Zing"), got)
    }

    @Test
    fun equalNamesAreStableByChatThenMessage() {
        val dao = seeded()
        dao.upsertSongs(listOf(song(50, "Đường xa", chat = 1)))
        val got = dao.getSongs(1, sort = SongSort.NAME).filter { it.title == "Đường xa" }.map { it.chatId to it.messageId }
        assertEquals(listOf(1L to 50L, 2L to 2L, 2L to 7L), got)
    }

    @Test
    fun newestStaysTheDefault() {
        val got = seeded().getSongs(1).map { it.messageId }
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L, 2L, 1L), got)
    }

    @Test
    fun fromKeyCutsAndCountMatches() {
        val dao = seeded()
        val rows = dao.getSongs(1, sort = SongSort.NAME, fromKey = "m")
        assertEquals(listOf("Mây", "Zing"), rows.map { it.title })
        assertEquals(rows.size, dao.getSongCount(1, fromKey = "m"))
        assertEquals(7, dao.getSongCount(1))
        assertEquals(listOf("Zing"), dao.getSongs(1, sort = SongSort.NAME, fromKey = "n").map { it.title })
        assertEquals(listOf("Đường xa"), dao.getSongs(1, sort = SongSort.NAME, searchQuery = "duong", fromKey = "d", offset = 1, limit = 1).map { it.title })
    }

    @Test
    fun sqlOrderMatchesInMemoryOrder() {
        val dao = seeded()
        val expected = dao.getSongs(1, sort = SongSort.NAME, fromKey = "b").map { it.messageId }
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use {
                it.execute(
                    "CREATE TABLE telegram_songs (account_id INTEGER, chat_id INTEGER, message_id INTEGER, " +
                        "search_key TEXT NOT NULL DEFAULT '', is_deleted INTEGER NOT NULL DEFAULT 0)"
                )
            }
            c.prepareStatement("INSERT INTO telegram_songs VALUES (1,?,?,?,?)").use { ps ->
                dao.getSongs(1, limit = 100).forEach { s -> ps.setLong(1, s.chatId); ps.setLong(2, s.messageId); ps.setString(3, MeloAlphabetPolicy.keyOf(s)); ps.setInt(4, 0); ps.addBatch() }
                ps.executeBatch()
            }
            val got = mutableListOf<Long>()
            c.createStatement().use { st ->
                st.executeQuery(
                    "SELECT message_id FROM telegram_songs WHERE account_id=1 AND is_deleted=0 AND search_key >= 'b' " +
                        "ORDER BY search_key ASC, chat_id ASC, message_id ASC"
                ).use { rs -> while (rs.next()) got.add(rs.getLong(1)) }
            }
            assertEquals(expected, got)
        }
    }
}
