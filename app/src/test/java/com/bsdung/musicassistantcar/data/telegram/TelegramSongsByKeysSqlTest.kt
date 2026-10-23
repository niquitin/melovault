package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * E96 P0: runs the very statement behind [TelegramLibraryDao.getSongsByKeys] on a real in-memory
 * SQLite database with an index-sized library: it must be a plain SELECT, answer the Playlist from
 * the database (not from the 200 songs kept in memory), and stay fast for 300 saved keys.
 */
class TelegramSongsByKeysSqlTest {
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

    private fun songsByKeys(account: Long, keys: List<Long>): List<Long> {
        val out = ArrayList<Long>()
        connection.prepareStatement(TelegramLibraryDao.songsByKeysSql(keys.size)).use { stmt ->
            // Same binding as the Android implementation: every argument as a string.
            stmt.setString(1, account.toString())
            keys.forEachIndexed { i, key -> stmt.setString(i + 2, key.toString()) }
            stmt.executeQuery().use { rs -> while (rs.next()) out.add(rs.getLong(TelegramLibrarySql.COL_SONG_KEY)) }
        }
        return out
    }

    @Test
    fun findsLiveSongsOfOneAccountByKey() {
        insertSong(100, -1, 1)
        insertSong(100, -1, 2, deleted = 1)
        insertSong(100, -2, 3)
        insertSong(200, -1, 4)
        assertEquals(listOf(1L, 3L), songsByKeys(100, listOf(1L, 2L, 3L, 4L, 5L)).sorted())
        assertEquals(listOf(4L), songsByKeys(200, listOf(1L, 4L)))
    }

    @Test
    fun aSavedSongBeyondTheNewest200IsFoundAmong25000() {
        connection.autoCommit = false
        for (i in 1L..25_000L) insertSong(100, -1000L - (i % 29), i)
        connection.commit()
        connection.autoCommit = true
        val saved = (1L..300L).map { it * 80L } // 80 .. 24_000, none of them among the 200 newest
        val started = System.nanoTime()
        val found = songsByKeys(100, saved)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(saved.sorted(), found.sorted())
        println("getSongsByKeys: 300 keys over 25000 songs took $elapsedMs ms (JVM SQLite)")
        assertTrue("took $elapsedMs ms", elapsedMs < 2_000)
    }

    @Test
    fun itIsASingleReadOnlySelectAndKeyCountIsBounded() {
        assertTrue(TelegramLibraryDao.songsByKeysSql(3).startsWith("SELECT "))
        assertEquals(3, TelegramLibraryDao.songsByKeysSql(3).count { it == '?' } - 1)
        // 1 account + 400 keys stays well under SQLite's 999 variable limit.
        assertTrue(1 + TelegramLibraryDao.SONGS_BY_KEYS_CHUNK < 999)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroKeysIsRejectedByTheStatementBuilder() {
        TelegramLibraryDao.songsByKeysSql(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun moreKeysThanOneChunkIsRejectedByTheStatementBuilder() {
        TelegramLibraryDao.songsByKeysSql(TelegramLibraryDao.SONGS_BY_KEYS_CHUNK + 1)
    }
}
