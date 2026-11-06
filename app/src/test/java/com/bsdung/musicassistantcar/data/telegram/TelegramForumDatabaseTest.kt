package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class TelegramForumDatabaseTest {
    private fun database() = DriverManager.getConnection("jdbc:sqlite::memory:")
    private fun Connection.execute(sql: String, args: Array<Any?> = emptyArray()) = prepareStatement(sql).use { statement ->
        args.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
        statement.executeUpdate()
    }
    private fun Connection.scalar(sql: String): Long = createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getLong(1) } }
    private fun Connection.fresh() = TelegramLibrarySql.ALL_CREATE_STATEMENTS.forEach { execute(it) }
    private fun song(account: Long = 11, chat: Long = -1001, message: Long = 100, topic: Int? = 71) = IndexedTelegramSong(
        account, chat, message, 9000 + message, 81, "Cùng tên", "01.wav", fileSize = 4096,
        date = message, forumTopicId = topic,
    )

    /** Independent legacy schema, deliberately not derived by stripping columns from v3. */
    private fun Connection.legacy(version: Int) {
        execute("""CREATE TABLE telegram_songs (
            account_id INTEGER NOT NULL,chat_id INTEGER NOT NULL,message_id INTEGER NOT NULL,
            song_key INTEGER NOT NULL,file_id INTEGER NOT NULL,title TEXT NOT NULL,filename TEXT NOT NULL,
            performer TEXT,duration_seconds INTEGER NOT NULL DEFAULT 0,file_size INTEGER NOT NULL DEFAULT -1,
            mime_type TEXT,media_type TEXT NOT NULL DEFAULT 'audio',date INTEGER NOT NULL DEFAULT 0,
            last_seen_at_ms INTEGER NOT NULL,source_title TEXT,is_deleted INTEGER NOT NULL DEFAULT 0
            ${if (version == 2) ",search_key TEXT NOT NULL DEFAULT ''" else ""},
            PRIMARY KEY(account_id,chat_id,message_id))""")
        execute("""CREATE TABLE telegram_sources (account_id INTEGER NOT NULL,chat_id INTEGER NOT NULL,title TEXT NOT NULL,
            type TEXT NOT NULL DEFAULT 'unknown',track_count INTEGER NOT NULL DEFAULT 0,last_updated_at_ms INTEGER NOT NULL,
            PRIMARY KEY(account_id,chat_id))""")
        execute(TelegramLibrarySql.CREATE_TABLE_SYNC_STATE)
        execute("PRAGMA user_version=$version")
        execute("INSERT INTO telegram_songs(account_id,chat_id,message_id,song_key,file_id,title,filename,last_seen_at_ms) VALUES (11,-1001,100,9100,81,'Nhạc cũ','old.wav',1)")
        execute("INSERT INTO telegram_songs(account_id,chat_id,message_id,song_key,file_id,title,filename,last_seen_at_ms) VALUES (12,-1001,100,9200,82,'Nhạc cũ','other.flac',1)")
        execute("INSERT INTO telegram_sources(account_id,chat_id,title,last_updated_at_ms) VALUES (11,-1001,'Nguồn cũ',1)")
    }
    private fun Connection.upgrade(version: Int) {
        autoCommit = false
        if (version == 1) {
            execute(TelegramLibrarySql.MIGRATE_1_TO_2_ADD_COLUMN)
            createStatement().use { s ->
                s.executeQuery("SELECT account_id,chat_id,message_id,title,performer,filename FROM telegram_songs").use { rows ->
                    while (rows.next()) execute(TelegramLibrarySql.BACKFILL_SEARCH_KEY_UPDATE, arrayOf(
                        TelegramSearchPolicy.songSearchKey(rows.getString(4), rows.getString(5), rows.getString(6)),
                        rows.getLong(1), rows.getLong(2), rows.getLong(3),
                    ))
                }
            }
        }
        TelegramLibrarySql.MIGRATE_2_TO_3.forEach { execute(it) }
        execute("PRAGMA user_version=3")
        commit()
        autoCommit = true
    }

    @Test fun legacyV1AndV2KeepIdentityPlaylistResolutionAndFileMetadata() {
        for (version in 1..2) database().use { db ->
            db.legacy(version)
            val savedPlaylist = listOf(9100L)
            val fileLinks = mapOf(9100L to "old.wav")
            db.upgrade(version)
            assertEquals(3L, db.scalar("PRAGMA user_version"))
            assertEquals(2L, db.scalar("SELECT COUNT(*) FROM telegram_songs"))
            assertEquals(2L, db.scalar("SELECT COUNT(*) FROM telegram_songs WHERE forum_topic_id IS NULL"))
            assertEquals(1L, db.scalar("SELECT COUNT(*) FROM telegram_sources WHERE is_forum IS NULL"))
            db.prepareStatement(TelegramLibraryDao.songsByKeysSql(1)).use { query ->
                query.setLong(1, 11); query.setLong(2, savedPlaylist.first())
                query.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals(fileLinks[savedPlaylist.first()], rows.getString("filename"))
                    assertEquals(81, rows.getInt("file_id"))
                    assertFalse(rows.next())
                }
            }
            if (version == 1) assertEquals(1L, db.scalar("SELECT COUNT(*) FROM telegram_songs WHERE account_id=11 AND search_key LIKE '%nhac cu%'"))
        }
    }

    @Test fun unknownMetadataNeverErasesKnownTopicButExplicitRelationCanMoveTheSameSong() = database().use { db ->
        db.fresh()
        val initial = song()
        db.execute(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(initial))
        // Both global page commits and standalone upserts use exactly this SQL.
        repeat(2) {
            db.execute(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(initial.copy(title = "Đã cập nhật", forumTopicId = null)))
            assertEquals(71L, db.scalar("SELECT forum_topic_id FROM telegram_songs"))
        }
        db.execute(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(initial.copy(forumTopicId = 72)))
        assertEquals(1L, db.scalar("SELECT COUNT(*) FROM telegram_songs"))
        assertEquals(72L, db.scalar("SELECT forum_topic_id FROM telegram_songs"))
        assertEquals(initial.songKey, db.scalar("SELECT song_key FROM telegram_songs"))
    }

    @Test fun sourceMaintenancePreservesForumTypeIdentityAndOtherAccount() = database().use { db ->
        db.fresh()
        db.execute(TelegramLibrarySql.UPSERT_SOURCE_SQL, arrayOf(11L, -1001L, "Known title", "supergroup", 1, 1L, 1, 11L, -1001L))
        db.execute(TelegramLibrarySql.UPSERT_SOURCE_SQL, arrayOf(12L, -1001L, "Other account", "channel", 77, 1L, 0, 12L, -1001L))
        repeat(2) {
            db.execute(TelegramLibrarySql.ENSURE_SOURCE_SQL, arrayOf(11L, -1001L, "Stale song title", 2L))
            db.execute(TelegramLibrarySql.UPDATE_SOURCE_COUNT_SQL, arrayOf(226, 2L, 11L, -1001L))
            assertEquals(1L, db.scalar("SELECT COUNT(*) FROM telegram_sources WHERE account_id=11 AND title='Known title' AND type='supergroup' AND is_forum=1 AND track_count=226"))
        }
        db.execute(TelegramLibrarySql.ENSURE_SOURCE_SQL, arrayOf(11L, -1001L, "Renamed", 3L))
        db.execute(TelegramLibrarySql.UPDATE_SOURCE_TITLE_SQL, arrayOf("Renamed", 3L, 11L, -1001L))
        assertEquals(1L, db.scalar("SELECT COUNT(*) FROM telegram_sources WHERE account_id=11 AND title='Renamed' AND type='supergroup' AND is_forum=1 AND track_count=226"))
        assertEquals(1L, db.scalar("SELECT COUNT(*) FROM telegram_sources WHERE account_id=12 AND title='Other account' AND type='channel' AND is_forum=0 AND track_count=77"))
        db.execute(TelegramLibrarySql.ENSURE_SOURCE_SQL, arrayOf(11L, -1002L, "New source", 4L))
        db.execute(TelegramLibrarySql.UPDATE_SOURCE_COUNT_SQL, arrayOf(5, 4L, 11L, -1002L))
        assertEquals(1L, db.scalar("SELECT COUNT(*) FROM telegram_sources WHERE chat_id=-1002 AND is_forum IS NULL AND track_count=5"))
        val memory = InMemoryTelegramLibraryDao()
        memory.upsertSources(listOf(IndexedTelegramSource(11, -1001, "Known", type = "supergroup", isForum = true)))
        memory.upsertSongs(listOf(song()))
        memory.recomputeSourceTrackCounts(11)
        memory.updateSourceTitle(11, -1001, "Renamed")
        assertEquals(true, memory.getSources(11).single().isForum)
        assertEquals("supergroup", memory.getSources(11).single().type)
        assertEquals(1, memory.getSources(11).single().trackCount)
    }

    @Test fun sourceUnknownStateIsPreservedAndConfirmedFalseCanReplaceTrue() = database().use { db ->
        db.fresh()
        fun write(value: Int?) = db.execute(TelegramLibrarySql.UPSERT_SOURCE_SQL, arrayOf(11, -1001, "Nguồn", "supergroup", 2, 1, value, 11, -1001))
        write(1); write(null)
        assertEquals(1L, db.scalar("SELECT is_forum FROM telegram_sources"))
        write(0)
        assertEquals(0L, db.scalar("SELECT is_forum FROM telegram_sources"))
    }

    @Test fun topicCountsAreAccountChatScopedAndUseTheCoveringIndex() = database().use { db ->
        db.fresh()
        for (account in 11L..12L) for (chat in listOf(-1001L, -1002L)) for (topic in 71..72) {
            for (message in 1L..250L) {
                val row = song(account, chat, topic * 1000L + message, topic)
                db.execute(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(row))
            }
        }
        db.execute(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(song(message = 90001, topic = null)))
        db.execute(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(song(message = 90002).copy(isDeleted = true)))
        db.prepareStatement(TelegramLibraryDao.FORUM_TOPIC_COUNTS_SQL).use { s ->
            s.setLong(1, 11); s.setLong(2, -1001)
            s.executeQuery().use { rows ->
                val counts = mutableMapOf<Int, Int>()
                while (rows.next()) counts[rows.getInt(1)] = rows.getInt(2)
                assertEquals(mapOf(71 to 250, 72 to 250), counts)
            }
        }
        db.prepareStatement("EXPLAIN QUERY PLAN ${TelegramLibraryDao.FORUM_TOPIC_COUNTS_SQL}").use { s ->
            s.setLong(1, 11); s.setLong(2, -1001)
            s.executeQuery().use { rows ->
                val details = buildList { while (rows.next()) add(rows.getString("detail")) }.joinToString()
                assertTrue(details, details.contains("COVERING INDEX idx_tg_songs_topic"))
            }
        }
    }

    @Test fun rollbackKeepsBothSongsAndHistoryCheckpointUntouched() = database().use { db ->
        db.fresh()
        val before = TelegramForumHistoryState(11, -1001, 71, olderCursor = 500)
        db.execute(TelegramLibrarySql.UPSERT_FORUM_HISTORY_SQL, TelegramLibrarySql.forumHistoryArgs(before))
        db.autoCommit = false
        db.execute(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(song()))
        db.execute(TelegramLibrarySql.UPSERT_FORUM_HISTORY_SQL, TelegramLibrarySql.forumHistoryArgs(before.copy(olderCursor = 100)))
        db.rollback(); db.autoCommit = true
        assertEquals(0L, db.scalar("SELECT COUNT(*) FROM telegram_songs"))
        assertEquals(500L, db.scalar("SELECT older_cursor FROM telegram_forum_history"))
    }

    @Test fun inMemoryTopicsKeepIdentitiesAndSongFilteringMatchesCountsSearchAndPaging() {
        val dao = InMemoryTelegramLibraryDao()
        val sameName = "Nhạc thử"
        dao.commitForumTopics(11, -1001, listOf(
            IndexedTelegramForumTopic(11, -1001, 1, "General", isGeneral = true, isHidden = true),
            IndexedTelegramForumTopic(11, -1001, 71, sameName, order = 2),
            IndexedTelegramForumTopic(11, -1001, 72, sameName, isPinned = true, order = 1),
        ))
        dao.commitForumTopics(12, -1001, listOf(IndexedTelegramForumTopic(12, -1001, 71, sameName)))
        dao.commitForumTopics(11, -1002, listOf(IndexedTelegramForumTopic(11, -1002, 71, sameName)))
        assertEquals(listOf(72, 71), dao.getForumTopics(11, -1001, "nhac thu").map { it.forumTopicId })
        dao.upsertSongs(listOf(song(), song(message = 101), song(message = 102, topic = 72), song(message = 103, topic = null), song(account = 12), song(chat = -1002)))
        assertEquals(4, dao.getSongCount(11, -1001))
        assertEquals(2, dao.getSongCount(11, -1001, searchQuery = "cung", forumTopicId = 71))
        assertEquals(listOf(100L), dao.getSongs(11, -1001, searchQuery = "ten", offset = 1, limit = 1, forumTopicId = 71).map { it.messageId })
        assertEquals(mapOf(71 to 2, 72 to 1), dao.getForumTopicCounts(11, -1001))
        dao.upsertSongs(listOf(song(topic = null)))
        dao.commitPageAndCheckpoint(11, "audio", "next", listOf(song(topic = null)), TelegramSyncState(11))
        assertEquals(71, dao.getSongByKey(11, 9100)?.forumTopicId)
        dao.upsertSources(listOf(IndexedTelegramSource(11, -1001, "Nguồn", isForum = true)))
        dao.upsertSources(listOf(IndexedTelegramSource(11, -1001, "Tên mới")))
        assertEquals(true, dao.getSources(11).single().isForum)
    }

    @Test fun cancellationAndForeignItemsCannotAdvanceEitherForumCheckpoint() {
        val dao = InMemoryTelegramLibraryDao()
        val state = TelegramForumHistoryState(11, -1001, 71, olderCursor = 100)
        var checks = 0
        assertFalse(dao.commitForumHistoryPage(state, listOf(song())) { ++checks < 2 })
        assertNull(dao.getForumHistoryState(11, -1001, 71))
        assertEquals(0, dao.getSongCount(11))
        assertThrows(IllegalArgumentException::class.java) { dao.commitForumHistoryPage(state, listOf(song(account = 12))) }
        val checkpoint = TelegramSyncState(11, syncScope = TelegramLibraryDao.forumCatalogueScope(-1001), nextOffset = "page2")
        assertFalse(dao.commitForumTopics(11, -1001, listOf(IndexedTelegramForumTopic(11, -1001, 71, "Nhạc")), checkpoint) { false })
        assertNull(dao.getSyncState(11, checkpoint.syncScope))
        assertTrue(dao.getForumTopics(11, -1001).isEmpty())
        assertTrue(dao.commitForumHistoryPage(state, listOf(song())))
        assertEquals(100L, dao.getForumHistoryState(11, -1001, 71)?.olderCursor)
        assertNull(dao.getSyncState(11))
    }
}
