package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * E95 F05: runs the very statement behind [TelegramLibraryDao.getLiveSourceCounts] on a real
 * in-memory SQLite database. It must be a plain SELECT (nothing is written), give the true
 * number of live songs per chat, and be answered from the existing index `idx_tg_songs_chat`
 * alone (no new index, no table scan).
 */
class TelegramLiveSourceCountsSqlTest {

    private lateinit var connection: Connection

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        for (sql in TelegramLibrarySql.ALL_CREATE_STATEMENTS) {
            connection.createStatement().use { it.execute(sql) }
        }
    }

    @After
    fun tearDown() {
        if (!connection.isClosed) connection.close()
    }

    private fun insertSong(account: Long, chat: Long, message: Long, deleted: Int = 0) {
        connection.prepareStatement(
            "INSERT INTO ${TelegramLibrarySql.TABLE_SONGS} (account_id, chat_id, message_id, song_key, file_id, title, filename, " +
                "last_seen_at_ms, is_deleted) VALUES (?, ?, ?, ?, 1, 't', 'f.mp3', 0, ?)"
        ).use {
            it.setLong(1, account); it.setLong(2, chat); it.setLong(3, message); it.setLong(4, message); it.setInt(5, deleted)
            it.executeUpdate()
        }
    }

    private fun liveCounts(account: Long): Map<Long, Int> {
        val out = linkedMapOf<Long, Int>()
        connection.prepareStatement(TelegramLibraryDao.LIVE_SOURCE_COUNTS_SQL).use { stmt ->
            stmt.setString(1, account.toString())
            stmt.executeQuery().use { rs -> while (rs.next()) out[rs.getLong(1)] = rs.getInt(2) }
        }
        return out
    }

    @Test
    fun countsLiveSongsPerChatForOneAccountOnly() {
        // The EMI channel of the owner's FYT photos: 77 songs while its stored count read 0.
        for (i in 1..77) insertSong(100, -1001L, i.toLong())
        for (i in 1..3) insertSong(100, -1002L, 1000L + i)
        insertSong(100, -1002L, 2000L, deleted = 1)
        insertSong(100, -1003L, 3000L, deleted = 1)
        insertSong(200, -1001L, 1L) // another account
        val counts = liveCounts(100)
        assertEquals(mapOf(-1001L to 77, -1002L to 3), counts)
        assertFalse(counts.containsKey(-1003L))
        assertEquals(mapOf(-1001L to 1), liveCounts(200))
        assertTrue(liveCounts(300).isEmpty())
    }

    @Test
    fun statementIsAReadOnlySelect() {
        val sql = TelegramLibraryDao.LIVE_SOURCE_COUNTS_SQL.uppercase()
        assertTrue(sql.startsWith("SELECT "))
        for (verb in listOf("INSERT", "UPDATE", "DELETE", "REPLACE", "ALTER", "DROP", "CREATE")) {
            // Whole words only: the column is_deleted must not trip the DELETE check.
            assertFalse("must not contain $verb", Regex("\\b$verb\\b").containsMatchIn(sql))
        }
        // Running it changes nothing: the sources table stays empty, the song rows stay as they are.
        insertSong(100, -1001L, 1L)
        liveCounts(100)
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM ${TelegramLibrarySql.TABLE_SOURCES}").use { rs ->
                assertTrue(rs.next()); assertEquals(0, rs.getInt(1))
            }
            stmt.executeQuery("SELECT COUNT(*) FROM ${TelegramLibrarySql.TABLE_SONGS}").use { rs ->
                assertTrue(rs.next()); assertEquals(1, rs.getInt(1))
            }
        }
    }

    @Test
    fun planUsesTheExistingChatIndexAndNeedsNoTableRead() {
        val plan = StringBuilder()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("EXPLAIN QUERY PLAN " + TelegramLibraryDao.LIVE_SOURCE_COUNTS_SQL.replace("?", "100")).use { rs ->
                while (rs.next()) plan.append(rs.getString("detail")).append('\n')
            }
        }
        val text = plan.toString()
        assertTrue("plan: $text", text.contains("idx_tg_songs_chat"))
        assertTrue("covering index expected, plan: $text", text.contains("COVERING INDEX idx_tg_songs_chat"))
        assertFalse("no full table scan, plan: $text", text.contains("SCAN telegram_songs\n") || text.contains("SCAN TABLE telegram_songs"))
        // No index was added for it: the schema keeps exactly the four song indexes of before E95.
        val indexes = mutableSetOf<String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='${TelegramLibrarySql.TABLE_SONGS}' AND name LIKE 'idx_%'").use { rs ->
                while (rs.next()) indexes.add(rs.getString(1))
            }
        }
        assertEquals(setOf("idx_tg_songs_chat", "idx_tg_songs_date", "idx_tg_songs_title", "idx_tg_songs_key"), indexes)
    }
}
