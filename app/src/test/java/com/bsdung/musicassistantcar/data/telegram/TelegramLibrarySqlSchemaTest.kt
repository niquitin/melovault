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

    private val v1SongsDdl = """
        CREATE TABLE telegram_songs (
            account_id INTEGER NOT NULL, chat_id INTEGER NOT NULL, message_id INTEGER NOT NULL,
            song_key INTEGER NOT NULL, file_id INTEGER NOT NULL, title TEXT NOT NULL, filename TEXT NOT NULL,
            performer TEXT, duration_seconds INTEGER NOT NULL DEFAULT 0, file_size INTEGER NOT NULL DEFAULT -1,
            mime_type TEXT, media_type TEXT NOT NULL DEFAULT 'audio', date INTEGER NOT NULL DEFAULT 0,
            last_seen_at_ms INTEGER NOT NULL, source_title TEXT, is_deleted INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY (account_id, chat_id, message_id)
        )
    """.trimIndent()

    private fun insertSong(
        c: Connection, chat: Long, msg: Long, title: String, performer: String?,
        deleted: Int = 0, key: String? = null
    ) {
        val cols = if (key != null) ", search_key" else ""
        val vals = if (key != null) ", ?" else ""
        c.prepareStatement(
            "INSERT INTO telegram_songs (account_id,chat_id,message_id,song_key,file_id,title,filename,performer,date,last_seen_at_ms,is_deleted$cols) " +
                "VALUES (1,?,?,?,5,?,?,?,?,99,?$vals)"
        ).use { ps ->
            ps.setLong(1, chat); ps.setLong(2, msg); ps.setLong(3, msg * 7)
            ps.setString(4, title); ps.setString(5, "$title.mp3"); ps.setString(6, performer)
            ps.setLong(7, msg); ps.setInt(8, deleted)
            if (key != null) ps.setString(9, key)
            ps.executeUpdate()
        }
    }

    private fun countMatches(c: Connection, query: String, chat: Long? = null): Int {
        val where = TelegramSearchPolicy.whereClause(query)!!
        val chatSql = if (chat != null) " AND chat_id = $chat" else ""
        return c.prepareStatement(
            "SELECT COUNT(*) FROM telegram_songs WHERE account_id=1 AND is_deleted=0$chatSql AND ${where.sql}"
        ).use { ps ->
            where.args.forEachIndexed { i, a -> ps.setString(i + 1, a) }
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    private fun dump(c: Connection, sql: String, n: Int): List<List<Any?>> =
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val out = mutableListOf<List<Any?>>()
                while (rs.next()) out.add((1..n).map { rs.getObject(it) })
                out
            }
        }

    @Test
    fun newTableHasSearchKeyColumn() {
        val cols = dump(connection, "PRAGMA table_info(telegram_songs)", 2).map { it[1] }
        assertTrue(cols.contains("search_key"))
        assertTrue(cols.contains("forum_topic_id"))
        assertEquals(3, TelegramLibrarySql.DATABASE_VERSION)
    }

    @Test
    fun migrationV1ToV2KeepsRowsAndFillsSearchKey() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use { it.execute(v1SongsDdl) }
            insertSong(c, 10, 1, "Sử Thanh Hóa", "Đàm Vĩnh Hưng")
            insertSong(c, 10, 2, "Plain Song", null)
            insertSong(c, 11, 3, "Đường Về", "Ca sĩ")
            val oldCols = "account_id,chat_id,message_id,song_key,file_id,title,filename,performer,date,is_deleted"
            val before = dump(c, "SELECT $oldCols FROM telegram_songs ORDER BY message_id", 10)

            c.createStatement().use { it.execute(TelegramLibrarySql.MIGRATE_1_TO_2_ADD_COLUMN) }
            val rows = dump(c, "SELECT account_id,chat_id,message_id,title,performer,filename FROM telegram_songs", 6)
            c.prepareStatement(TelegramLibrarySql.BACKFILL_SEARCH_KEY_UPDATE).use { ps ->
                for (r in rows) {
                    ps.setString(1, TelegramSearchPolicy.songSearchKey(r[3] as String, r[4] as String?, r[5] as String))
                    ps.setLong(2, (r[0] as Number).toLong())
                    ps.setLong(3, (r[1] as Number).toLong())
                    ps.setLong(4, (r[2] as Number).toLong())
                    assertEquals(1, ps.executeUpdate())
                }
            }

            assertEquals(before, dump(c, "SELECT $oldCols FROM telegram_songs ORDER BY message_id", 10))
            val keys = dump(c, "SELECT search_key FROM telegram_songs ORDER BY message_id", 1).map { it[0] }
            assertEquals("su thanh hoa dam vinh hung su thanh hoa.mp3", keys[0])
            assertEquals("plain song plain song.mp3", keys[1])
            assertEquals(1, countMatches(c, "su thanh hoa"))
            assertEquals(1, countMatches(c, "ca si"))
        }
    }

    @Test
    fun realSearchQueryCountsFiltersAndFallback() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use { it.execute(v1SongsDdl) }
            c.createStatement().use { it.execute(TelegramLibrarySql.MIGRATE_1_TO_2_ADD_COLUMN) }
            fun key(t: String, p: String?) = TelegramSearchPolicy.songSearchKey(t, p, "$t.mp3")
            insertSong(c, 10, 1, "Sử Thanh Hóa", "A", key = key("Sử Thanh Hóa", "A"))
            insertSong(c, 11, 2, "Sử Thanh Hóa Remix", "B", key = key("Sử Thanh Hóa Remix", "B"))
            insertSong(c, 10, 3, "Sử Thanh Hóa Deleted", "C", deleted = 1, key = key("Sử Thanh Hóa Deleted", "C"))
            insertSong(c, 10, 4, "Other", "D", key = key("Other", "D"))
            insertSong(c, 10, 5, "Hoa Su legacy", "E", key = "")
            insertSong(c, 10, 6, "100% Hit", null, key = key("100% Hit", null))
            insertSong(c, 10, 7, "1000 Hit", null, key = key("1000 Hit", null))

            assertEquals(2, countMatches(c, "su thanh hoa"))
            assertEquals(1, countMatches(c, "su thanh hoa", chat = 11))
            assertEquals(1, countMatches(c, "100%"))
            assertEquals(2, countMatches(c, "hit"))
            assertEquals(1, countMatches(c, "hoa su legacy"))
            assertEquals(3, countMatches(c, "hoa"))
        }
    }
}
