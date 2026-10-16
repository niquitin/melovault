package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Validates TelegramLibrarySql DDL, index statements, composite keys,
 * and SQL syntax against a real in-memory SQLite database via JDBC.
 */
class TelegramLibrarySqlSchemaTest {

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
        if (!connection.isClosed) {
            connection.close()
        }
    }

    @Test
    fun testTablesAndIndexesCreatedSuccessfully() {
        val tables = mutableSetOf<String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rs ->
                while (rs.next()) {
                    tables.add(rs.getString("name"))
                }
            }
        }
        assertTrue(tables.contains(TelegramLibrarySql.TABLE_SONGS))
        assertTrue(tables.contains(TelegramLibrarySql.TABLE_SOURCES))
        assertTrue(tables.contains(TelegramLibrarySql.TABLE_SYNC_STATE))

        val indexes = mutableSetOf<String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index'").use { rs ->
                while (rs.next()) {
                    indexes.add(rs.getString("name"))
                }
            }
        }
        assertTrue(indexes.contains("idx_tg_songs_chat"))
        assertTrue(indexes.contains("idx_tg_songs_date"))
        assertTrue(indexes.contains("idx_tg_songs_title"))
        assertTrue(indexes.contains("idx_tg_songs_key"))
    }

    @Test
    fun testCompositePrimaryKeyEnforcesUniqueness() {
        val insertSql = """
            INSERT OR REPLACE INTO ${TelegramLibrarySql.TABLE_SONGS} (
                ${TelegramLibrarySql.COL_ACCOUNT_ID}, ${TelegramLibrarySql.COL_CHAT_ID}, ${TelegramLibrarySql.COL_MESSAGE_ID},
                ${TelegramLibrarySql.COL_SONG_KEY}, ${TelegramLibrarySql.COL_FILE_ID}, ${TelegramLibrarySql.COL_TITLE},
                ${TelegramLibrarySql.COL_FILENAME}, ${TelegramLibrarySql.COL_DURATION_SECONDS}, ${TelegramLibrarySql.COL_FILE_SIZE},
                ${TelegramLibrarySql.COL_LAST_SEEN_AT_MS}
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        // Insert first track: account 100, chat 200, message 500
        connection.prepareStatement(insertSql).use { ps ->
            ps.setLong(1, 100L)
            ps.setLong(2, 200L)
            ps.setLong(3, 500L)
            ps.setLong(4, 12345L)
            ps.setInt(5, 99)
            ps.setString(6, "Initial Title")
            ps.setString(7, "track.mp3")
            ps.setInt(8, 180)
            ps.setLong(9, 1024L)
            ps.setLong(10, 1000L)
            ps.executeUpdate()
        }

        // Cross-chat: same messageId (500), but different chatId (300) -> MUST SUCCEED (distinct row)
        connection.prepareStatement(insertSql).use { ps ->
            ps.setLong(1, 100L)
            ps.setLong(2, 300L)
            ps.setLong(3, 500L)
            ps.setLong(4, 54321L)
            ps.setInt(5, 100)
            ps.setString(6, "Different Chat Track")
            ps.setString(7, "other.mp3")
            ps.setInt(8, 200)
            ps.setLong(9, 2048L)
            ps.setLong(10, 1000L)
            ps.executeUpdate()
        }

        // Account isolation: same chatId (200) & messageId (500), but different account (101) -> MUST SUCCEED
        connection.prepareStatement(insertSql).use { ps ->
            ps.setLong(1, 101L)
            ps.setLong(2, 200L)
            ps.setLong(3, 500L)
            ps.setLong(4, 99999L)
            ps.setInt(5, 101)
            ps.setString(6, "Account 101 Track")
            ps.setString(7, "acc101.mp3")
            ps.setInt(8, 220)
            ps.setLong(9, 4096L)
            ps.setLong(10, 1000L)
            ps.executeUpdate()
        }

        // Exact match replacement (INSERT OR REPLACE) -> replaces initial title
        connection.prepareStatement(insertSql).use { ps ->
            ps.setLong(1, 100L)
            ps.setLong(2, 200L)
            ps.setLong(3, 500L)
            ps.setLong(4, 12345L)
            ps.setInt(5, 99)
            ps.setString(6, "Updated Title")
            ps.setString(7, "track.mp3")
            ps.setInt(8, 180)
            ps.setLong(9, 1024L)
            ps.setLong(10, 2000L)
            ps.executeUpdate()
        }

        // Verify total row count is exactly 3
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT COUNT(*) FROM ${TelegramLibrarySql.TABLE_SONGS}").use { rs ->
                assertTrue(rs.next())
                assertEquals(3, rs.getInt(1))
            }
        }

        // Verify title of (100, 200, 500) was updated to "Updated Title"
        val querySql = "SELECT ${TelegramLibrarySql.COL_TITLE} FROM ${TelegramLibrarySql.TABLE_SONGS} WHERE account_id=100 AND chat_id=200 AND message_id=500"
        connection.createStatement().use { stmt ->
            stmt.executeQuery(querySql).use { rs ->
                assertTrue(rs.next())
                assertEquals("Updated Title", rs.getString(1))
            }
        }
    }

    @Test
    fun testSyncStateCompositePrimaryKey() {
        val insertSql = """
            INSERT OR REPLACE INTO ${TelegramLibrarySql.TABLE_SYNC_STATE} (
                ${TelegramLibrarySql.COL_ACCOUNT_ID}, ${TelegramLibrarySql.COL_SYNC_SCOPE},
                ${TelegramLibrarySql.COL_PHASE}, ${TelegramLibrarySql.COL_NEXT_OFFSET},
                ${TelegramLibrarySql.COL_PAGES_SYNCED}, ${TelegramLibrarySql.COL_TOTAL_ITEMS_SYNCED},
                ${TelegramLibrarySql.COL_STATUS}, ${TelegramLibrarySql.COL_LAST_SYNC_COMPLETED_AT_MS}
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(insertSql).use { ps ->
            ps.setLong(1, 100L)
            ps.setString(2, "global")
            ps.setString(3, "audio")
            ps.setString(4, "offset_1")
            ps.setInt(5, 1)
            ps.setInt(6, 100)
            ps.setString(7, "syncing")
            ps.setLong(8, 0L)
            ps.executeUpdate()
        }

        // Update with new offset and pages
        connection.prepareStatement(insertSql).use { ps ->
            ps.setLong(1, 100L)
            ps.setString(2, "global")
            ps.setString(3, "audio")
            ps.setString(4, "offset_2")
            ps.setInt(5, 2)
            ps.setInt(6, 200)
            ps.setString(7, "syncing")
            ps.setLong(8, 0L)
            ps.executeUpdate()
        }

        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT ${TelegramLibrarySql.COL_NEXT_OFFSET}, ${TelegramLibrarySql.COL_PAGES_SYNCED} FROM ${TelegramLibrarySql.TABLE_SYNC_STATE} WHERE account_id=100 AND sync_scope='global'").use { rs ->
                assertTrue(rs.next())
                assertEquals("offset_2", rs.getString(1))
                assertEquals(2, rs.getInt(2))
            }
        }
    }
}
