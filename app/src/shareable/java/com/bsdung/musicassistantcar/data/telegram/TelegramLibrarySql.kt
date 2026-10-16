package com.bsdung.musicassistantcar.data.telegram

/**
 * SQL DDL, index statements, column constants, and migration helpers
 * for MeloVault Telegram Library Index.
 */
object TelegramLibrarySql {
    const val DATABASE_VERSION = 1

    const val TABLE_SONGS = "telegram_songs"
    const val TABLE_SOURCES = "telegram_sources"
    const val TABLE_SYNC_STATE = "telegram_sync_state"

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

    val ALL_CREATE_STATEMENTS = listOf(
        CREATE_TABLE_SONGS,
        CREATE_INDEX_SONGS_CHAT,
        CREATE_INDEX_SONGS_DATE,
        CREATE_INDEX_SONGS_TITLE,
        CREATE_INDEX_SONGS_KEY,
        CREATE_TABLE_SOURCES,
        CREATE_TABLE_SYNC_STATE
    )
}
