package com.bsdung.musicassistantcar.data.telegram

/**
 * SQL DDL, index statements, column constants, and migration helpers
 * for MeloVault Telegram Library Index.
 */
object TelegramLibrarySql {
    const val DATABASE_VERSION = 3

    const val TABLE_SONGS = "telegram_songs"
    const val TABLE_SOURCES = "telegram_sources"
    const val TABLE_SYNC_STATE = "telegram_sync_state"
    const val TABLE_FORUM_TOPICS = "telegram_forum_topics"
    const val TABLE_FORUM_HISTORY = "telegram_forum_history"
    const val COL_FORUM_TOPIC_ID = "forum_topic_id"
    const val COL_IS_FORUM = "is_forum"

    // Songs column names
    const val COL_ACCOUNT_ID = "account_id"
    const val COL_CHAT_ID = "chat_id"
    const val COL_MESSAGE_ID = "message_id"
    const val COL_SONG_KEY = "song_key"
    const val COL_FILE_ID = "file_id"
    const val COL_TITLE = "title"
    const val COL_FILENAME = "filename"
    const val COL_PERFORMER = "performer"
    const val COL_DURATION_SECONDS = "duration_seconds"
    const val COL_FILE_SIZE = "file_size"
    const val COL_MIME_TYPE = "mime_type"
    const val COL_MEDIA_TYPE = "media_type"
    const val COL_DATE = "date"
    const val COL_LAST_SEEN_AT_MS = "last_seen_at_ms"
    const val COL_SOURCE_TITLE = "source_title"
    const val COL_IS_DELETED = "is_deleted"
    const val COL_SEARCH_KEY = "search_key"

    // Sources column names
    const val COL_TYPE = "type"
    const val COL_TRACK_COUNT = "track_count"
    const val COL_LAST_UPDATED_AT_MS = "last_updated_at_ms"

    // Sync state column names
    const val COL_SYNC_SCOPE = "sync_scope"
    const val COL_PHASE = "phase"
    const val COL_NEXT_OFFSET = "next_offset"
    const val COL_PAGES_SYNCED = "pages_synced"
    const val COL_TOTAL_ITEMS_SYNCED = "total_items_synced"
    const val COL_STATUS = "status"
    const val COL_HIGH_WATER_DATE = "high_water_date"
    const val COL_LAST_SYNC_COMPLETED_AT_MS = "last_sync_completed_at_ms"
    const val COL_LAST_ERROR_MESSAGE = "last_error_message"

    val CREATE_TABLE_SONGS = """
        CREATE TABLE IF NOT EXISTS $TABLE_SONGS (
            $COL_ACCOUNT_ID INTEGER NOT NULL,
            $COL_CHAT_ID INTEGER NOT NULL,
            $COL_MESSAGE_ID INTEGER NOT NULL,
            $COL_SONG_KEY INTEGER NOT NULL,
            $COL_FILE_ID INTEGER NOT NULL,
            $COL_TITLE TEXT NOT NULL,
            $COL_FILENAME TEXT NOT NULL,
            $COL_PERFORMER TEXT,
            $COL_DURATION_SECONDS INTEGER NOT NULL DEFAULT 0,
            $COL_FILE_SIZE INTEGER NOT NULL DEFAULT -1,
            $COL_MIME_TYPE TEXT,
            $COL_MEDIA_TYPE TEXT NOT NULL DEFAULT 'audio',
            $COL_DATE INTEGER NOT NULL DEFAULT 0,
            $COL_LAST_SEEN_AT_MS INTEGER NOT NULL,
            $COL_SOURCE_TITLE TEXT,
            $COL_IS_DELETED INTEGER NOT NULL DEFAULT 0,
            $COL_SEARCH_KEY TEXT NOT NULL DEFAULT '',
            $COL_FORUM_TOPIC_ID INTEGER,
            PRIMARY KEY ($COL_ACCOUNT_ID, $COL_CHAT_ID, $COL_MESSAGE_ID)
        );
    """.trimIndent()

    val CREATE_INDEX_SONGS_CHAT = """
        CREATE INDEX IF NOT EXISTS idx_tg_songs_chat ON $TABLE_SONGS($COL_ACCOUNT_ID, $COL_CHAT_ID, $COL_IS_DELETED);
    """.trimIndent()

    val CREATE_INDEX_SONGS_DATE = """
        CREATE INDEX IF NOT EXISTS idx_tg_songs_date ON $TABLE_SONGS($COL_ACCOUNT_ID, $COL_IS_DELETED, $COL_DATE DESC);
    """.trimIndent()

    val CREATE_INDEX_SONGS_TITLE = """
        CREATE INDEX IF NOT EXISTS idx_tg_songs_title ON $TABLE_SONGS($COL_ACCOUNT_ID, $COL_IS_DELETED, $COL_TITLE);
    """.trimIndent()

    val CREATE_INDEX_SONGS_KEY = """
        CREATE INDEX IF NOT EXISTS idx_tg_songs_key ON $TABLE_SONGS($COL_ACCOUNT_ID, $COL_SONG_KEY);
    """.trimIndent()

    val CREATE_TABLE_SOURCES = """
        CREATE TABLE IF NOT EXISTS $TABLE_SOURCES (
            $COL_ACCOUNT_ID INTEGER NOT NULL,
            $COL_CHAT_ID INTEGER NOT NULL,
            $COL_TITLE TEXT NOT NULL,
            $COL_TYPE TEXT NOT NULL DEFAULT 'unknown',
            $COL_TRACK_COUNT INTEGER NOT NULL DEFAULT 0,
            $COL_LAST_UPDATED_AT_MS INTEGER NOT NULL,
            $COL_IS_FORUM INTEGER,
            PRIMARY KEY ($COL_ACCOUNT_ID, $COL_CHAT_ID)
        );
    """.trimIndent()

    val CREATE_TABLE_SYNC_STATE = """
        CREATE TABLE IF NOT EXISTS $TABLE_SYNC_STATE (
            $COL_ACCOUNT_ID INTEGER NOT NULL,
            $COL_SYNC_SCOPE TEXT NOT NULL,
            $COL_PHASE TEXT NOT NULL DEFAULT 'idle',
            $COL_NEXT_OFFSET TEXT NOT NULL DEFAULT '',
            $COL_PAGES_SYNCED INTEGER NOT NULL DEFAULT 0,
            $COL_TOTAL_ITEMS_SYNCED INTEGER NOT NULL DEFAULT 0,
            $COL_STATUS TEXT NOT NULL DEFAULT 'idle',
            $COL_HIGH_WATER_DATE INTEGER NOT NULL DEFAULT 0,
            $COL_LAST_SYNC_COMPLETED_AT_MS INTEGER NOT NULL DEFAULT 0,
            $COL_LAST_ERROR_MESSAGE TEXT,
            PRIMARY KEY ($COL_ACCOUNT_ID, $COL_SYNC_SCOPE)
        );
    """.trimIndent()

    /** v1 -> v2: add the accent-insensitive search column; rows are backfilled by the caller. */
    val MIGRATE_1_TO_2_ADD_COLUMN =
        "ALTER TABLE $TABLE_SONGS ADD COLUMN $COL_SEARCH_KEY TEXT NOT NULL DEFAULT ''"

    val BACKFILL_SEARCH_KEY_UPDATE = """
        UPDATE $TABLE_SONGS SET $COL_SEARCH_KEY = ?
        WHERE $COL_ACCOUNT_ID = ? AND $COL_CHAT_ID = ? AND $COL_MESSAGE_ID = ?
    """.trimIndent()

    val CREATE_INDEX_SONGS_TOPIC = "CREATE INDEX IF NOT EXISTS idx_tg_songs_topic ON $TABLE_SONGS(account_id, chat_id, forum_topic_id, is_deleted, date DESC, message_id DESC)"
    val CREATE_TABLE_FORUM_TOPICS = """
        CREATE TABLE IF NOT EXISTS $TABLE_FORUM_TOPICS (
            account_id INTEGER NOT NULL, chat_id INTEGER NOT NULL, forum_topic_id INTEGER NOT NULL,
            title TEXT NOT NULL, search_key TEXT NOT NULL DEFAULT '', is_general INTEGER NOT NULL DEFAULT 0,
            is_hidden INTEGER NOT NULL DEFAULT 0, is_closed INTEGER NOT NULL DEFAULT 0, is_pinned INTEGER NOT NULL DEFAULT 0,
            sort_order INTEGER NOT NULL DEFAULT 0, last_updated_at_ms INTEGER NOT NULL,
            PRIMARY KEY(account_id, chat_id, forum_topic_id)
        )
    """.trimIndent()
    val CREATE_TABLE_FORUM_HISTORY = """
        CREATE TABLE IF NOT EXISTS $TABLE_FORUM_HISTORY (
            account_id INTEGER NOT NULL, chat_id INTEGER NOT NULL, forum_topic_id INTEGER NOT NULL,
            older_cursor INTEGER NOT NULL DEFAULT 0, older_complete INTEGER NOT NULL DEFAULT 0,
            newest_message_id INTEGER NOT NULL DEFAULT 0, refresh_cursor INTEGER NOT NULL DEFAULT 0,
            refresh_until_message_id INTEGER NOT NULL DEFAULT 0, refresh_pending INTEGER NOT NULL DEFAULT 0,
            retry_after_ms INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(account_id, chat_id, forum_topic_id)
        )
    """.trimIndent()
    val MIGRATE_2_TO_3 = listOf(
        "ALTER TABLE $TABLE_SONGS ADD COLUMN $COL_FORUM_TOPIC_ID INTEGER",
        "ALTER TABLE $TABLE_SOURCES ADD COLUMN $COL_IS_FORUM INTEGER",
        CREATE_INDEX_SONGS_TOPIC, CREATE_TABLE_FORUM_TOPICS, CREATE_TABLE_FORUM_HISTORY,
    )

    /** Compatible with Android 8 SQLite (no modern UPSERT dependency). Preserve only unknown metadata. */
    val UPSERT_SONG_SQL = """
        INSERT OR REPLACE INTO $TABLE_SONGS
        (account_id,chat_id,message_id,song_key,file_id,title,filename,performer,duration_seconds,file_size,
         mime_type,media_type,date,last_seen_at_ms,source_title,is_deleted,search_key,forum_topic_id)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,COALESCE(?,
            (SELECT forum_topic_id FROM $TABLE_SONGS WHERE account_id=? AND chat_id=? AND message_id=?)))
    """.trimIndent()

    fun songArgs(song: IndexedTelegramSong): Array<Any?> = arrayOf(
        song.accountId, song.chatId, song.messageId, song.songKey, song.fileId, song.title, song.filename,
        song.performer, song.durationSeconds, song.fileSize, song.mimeType, song.mediaType, song.date,
        song.lastSeenAtMs, song.sourceTitle, if (song.isDeleted) 1 else 0,
        TelegramSearchPolicy.songSearchKey(song.title, song.performer, song.filename), song.forumTopicId,
        song.accountId, song.chatId, song.messageId,
    )

    val UPSERT_SOURCE_SQL = """
        INSERT OR REPLACE INTO $TABLE_SOURCES (account_id,chat_id,title,type,track_count,last_updated_at_ms,is_forum)
        VALUES (?,?,?,?,?,?,COALESCE(?,(SELECT is_forum FROM $TABLE_SOURCES WHERE account_id=? AND chat_id=?)))
    """.trimIndent()

    // Source maintenance must update its fields in place: REPLACE would erase forum/type metadata.
    val ENSURE_SOURCE_SQL = """
        INSERT OR IGNORE INTO $TABLE_SOURCES (account_id,chat_id,title,last_updated_at_ms) VALUES (?,?,?,?)
    """.trimIndent()
    val UPDATE_SOURCE_TITLE_SQL = """
        UPDATE $TABLE_SOURCES SET title=?,last_updated_at_ms=? WHERE account_id=? AND chat_id=?
    """.trimIndent()
    val UPDATE_SOURCE_COUNT_SQL = """
        UPDATE $TABLE_SOURCES SET track_count=?,last_updated_at_ms=? WHERE account_id=? AND chat_id=?
    """.trimIndent()

    val UPSERT_FORUM_HISTORY_SQL = """
        INSERT OR REPLACE INTO $TABLE_FORUM_HISTORY
        (account_id,chat_id,forum_topic_id,older_cursor,older_complete,newest_message_id,refresh_cursor,
         refresh_until_message_id,refresh_pending,retry_after_ms) VALUES (?,?,?,?,?,?,?,?,?,?)
    """.trimIndent()

    val UPSERT_FORUM_TOPIC_SQL = """
        INSERT OR REPLACE INTO $TABLE_FORUM_TOPICS
        (account_id,chat_id,forum_topic_id,title,search_key,is_general,is_hidden,is_closed,is_pinned,sort_order,last_updated_at_ms)
        VALUES (?,?,?,?,?,?,?,?,?,?,?)
    """.trimIndent()

    fun forumTopicArgs(topic: IndexedTelegramForumTopic): Array<Any?> = arrayOf(
        topic.accountId, topic.chatId, topic.forumTopicId, topic.title, MeloComponentPolicy.searchKey(topic.title),
        if (topic.isGeneral) 1 else 0, if (topic.isHidden) 1 else 0, if (topic.isClosed) 1 else 0,
        if (topic.isPinned) 1 else 0, topic.order, topic.lastUpdatedAtMs,
    )

    fun forumHistoryArgs(state: TelegramForumHistoryState): Array<Any?> = arrayOf(
        state.accountId, state.chatId, state.forumTopicId, state.olderCursor, if (state.olderComplete) 1 else 0,
        state.newestMessageId, state.refreshCursor, state.refreshUntilMessageId, if (state.refreshPending) 1 else 0,
        state.retryAfterMs,
    )

    val ALL_CREATE_STATEMENTS = listOf(
        CREATE_TABLE_SONGS,
        CREATE_INDEX_SONGS_CHAT,
        CREATE_INDEX_SONGS_DATE,
        CREATE_INDEX_SONGS_TITLE,
        CREATE_INDEX_SONGS_KEY,
        CREATE_TABLE_SOURCES,
        CREATE_TABLE_SYNC_STATE,
        CREATE_INDEX_SONGS_TOPIC,
        CREATE_TABLE_FORUM_TOPICS,
        CREATE_TABLE_FORUM_HISTORY
    )
}
