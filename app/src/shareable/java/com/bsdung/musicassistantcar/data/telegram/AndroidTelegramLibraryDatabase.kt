package com.bsdung.musicassistantcar.data.telegram

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Android SQLite-backed durable implementation of [TelegramLibraryDao].
 * Stored in app-private storage: melovault_tg_index_{accountId}.db.
 */
class AndroidTelegramLibraryDatabase(
    context: Context,
    val accountId: Long
) : SQLiteOpenHelper(
    context,
    "melovault_tg_index_${accountId}.db",
    null,
    TelegramLibrarySql.DATABASE_VERSION
), TelegramLibraryDao {

    init {
        require(accountId > 0L) { "Invalid Telegram account ID: $accountId" }
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        for (sql in TelegramLibrarySql.ALL_CREATE_STATEMENTS) {
            db.execSQL(sql)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is baseline; future schema migrations go here with explicit upgrade steps.
    }

    override fun upsertSongs(songs: Collection<IndexedTelegramSong>): Int {
        if (songs.isEmpty()) return 0
        val db = writableDatabase
        var count = 0
        db.beginTransactionNonExclusive()
        try {
            for (song in songs) {
                if (song.accountId != accountId) continue
                val values = ContentValues().apply {
                    put(TelegramLibrarySql.COL_ACCOUNT_ID, song.accountId)
                    put(TelegramLibrarySql.COL_CHAT_ID, song.chatId)
                    put(TelegramLibrarySql.COL_MESSAGE_ID, song.messageId)
                    put(TelegramLibrarySql.COL_SONG_KEY, song.songKey)
                    put(TelegramLibrarySql.COL_FILE_ID, song.fileId)
                    put(TelegramLibrarySql.COL_TITLE, song.title)
                    put(TelegramLibrarySql.COL_FILENAME, song.filename)
                    put(TelegramLibrarySql.COL_PERFORMER, song.performer)
                    put(TelegramLibrarySql.COL_DURATION_SECONDS, song.durationSeconds)
                    put(TelegramLibrarySql.COL_FILE_SIZE, song.fileSize)
                    put(TelegramLibrarySql.COL_MIME_TYPE, song.mimeType)
                    put(TelegramLibrarySql.COL_MEDIA_TYPE, song.mediaType)
                    put(TelegramLibrarySql.COL_DATE, song.date)
                    put(TelegramLibrarySql.COL_LAST_SEEN_AT_MS, song.lastSeenAtMs)
                    put(TelegramLibrarySql.COL_SOURCE_TITLE, song.sourceTitle)
                    put(TelegramLibrarySql.COL_IS_DELETED, if (song.isDeleted) 1 else 0)
                }
                val rowId = db.insertWithOnConflict(
                    TelegramLibrarySql.TABLE_SONGS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (rowId != -1L) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    override fun commitPageAndCheckpoint(
        accountId: Long,
        phase: String,
        cursor: String,
        items: Collection<IndexedTelegramSong>,
        checkpoint: TelegramSyncState
    ): Int {
        val db = writableDatabase
        var count = 0
        db.beginTransactionNonExclusive()
        try {
            for (song in items) {
                if (song.accountId != accountId) continue
                val values = ContentValues().apply {
                    put(TelegramLibrarySql.COL_ACCOUNT_ID, song.accountId)
                    put(TelegramLibrarySql.COL_CHAT_ID, song.chatId)
                    put(TelegramLibrarySql.COL_MESSAGE_ID, song.messageId)
                    put(TelegramLibrarySql.COL_SONG_KEY, song.songKey)
                    put(TelegramLibrarySql.COL_FILE_ID, song.fileId)
                    put(TelegramLibrarySql.COL_TITLE, song.title)
                    put(TelegramLibrarySql.COL_FILENAME, song.filename)
                    put(TelegramLibrarySql.COL_PERFORMER, song.performer)
                    put(TelegramLibrarySql.COL_DURATION_SECONDS, song.durationSeconds)
                    put(TelegramLibrarySql.COL_FILE_SIZE, song.fileSize)
                    put(TelegramLibrarySql.COL_MIME_TYPE, song.mimeType)
                    put(TelegramLibrarySql.COL_MEDIA_TYPE, song.mediaType)
                    put(TelegramLibrarySql.COL_DATE, song.date)
                    put(TelegramLibrarySql.COL_LAST_SEEN_AT_MS, song.lastSeenAtMs)
                    put(TelegramLibrarySql.COL_SOURCE_TITLE, song.sourceTitle)
                    put(TelegramLibrarySql.COL_IS_DELETED, if (song.isDeleted) 1 else 0)
                }
                val rowId = db.insertWithOnConflict(
                    TelegramLibrarySql.TABLE_SONGS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (rowId != -1L) count++
            }

            val stateValues = ContentValues().apply {
                put(TelegramLibrarySql.COL_ACCOUNT_ID, checkpoint.accountId)
                put(TelegramLibrarySql.COL_SYNC_SCOPE, checkpoint.syncScope)
                put(TelegramLibrarySql.COL_PHASE, checkpoint.phase)
                put(TelegramLibrarySql.COL_NEXT_OFFSET, checkpoint.nextOffset)
                put(TelegramLibrarySql.COL_PAGES_SYNCED, checkpoint.pagesSynced)
                put(TelegramLibrarySql.COL_TOTAL_ITEMS_SYNCED, checkpoint.totalItemsSynced)
                put(TelegramLibrarySql.COL_STATUS, checkpoint.status)
                put(TelegramLibrarySql.COL_HIGH_WATER_DATE, checkpoint.highWaterDate)
                put(TelegramLibrarySql.COL_LAST_SYNC_COMPLETED_AT_MS, checkpoint.lastSyncCompletedAtMs)
                put(TelegramLibrarySql.COL_LAST_ERROR_MESSAGE, checkpoint.lastErrorMessage)
            }
            db.insertWithOnConflict(
                TelegramLibrarySql.TABLE_SYNC_STATE,
                null,
                stateValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    override fun getSongs(
        accountId: Long,
        chatId: Long?,
        searchQuery: String?,
        offset: Int,
        limit: Int
    ): List<IndexedTelegramSong> {
        val db = readableDatabase
        val selectionArgs = mutableListOf<String>()
        val selection = StringBuilder("${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0")
        selectionArgs.add(accountId.toString())

        if (chatId != null) {
            selection.append(" AND ${TelegramLibrarySql.COL_CHAT_ID} = ?")
            selectionArgs.add(chatId.toString())
        }

        val cleanQuery = searchQuery?.trim()
        if (!cleanQuery.isNullOrEmpty()) {
            selection.append(" AND ${TelegramLibrarySql.COL_TITLE} LIKE ?")
            selectionArgs.add("%$cleanQuery%")
        }

        val orderBy = "${TelegramLibrarySql.COL_DATE} DESC, ${TelegramLibrarySql.COL_MESSAGE_ID} DESC LIMIT $limit OFFSET $offset"
        val results = mutableListOf<IndexedTelegramSong>()

        db.query(
            TelegramLibrarySql.TABLE_SONGS,
            null,
            selection.toString(),
            selectionArgs.toTypedArray(),
            null,
            null,
            orderBy
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(mapCursorToSong(cursor))
            }
        }
        return results
    }

    override fun getSongCount(
        accountId: Long,
        chatId: Long?,
        searchQuery: String?
    ): Int {
        val db = readableDatabase
        val selectionArgs = mutableListOf<String>()
        val selection = StringBuilder("${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0")
        selectionArgs.add(accountId.toString())

        if (chatId != null) {
            selection.append(" AND ${TelegramLibrarySql.COL_CHAT_ID} = ?")
            selectionArgs.add(chatId.toString())
        }

        val cleanQuery = searchQuery?.trim()
        if (!cleanQuery.isNullOrEmpty()) {
            selection.append(" AND ${TelegramLibrarySql.COL_TITLE} LIKE ?")
            selectionArgs.add("%$cleanQuery%")
        }

        db.rawQuery(
            "SELECT COUNT(*) FROM ${TelegramLibrarySql.TABLE_SONGS} WHERE $selection",
            selectionArgs.toTypedArray()
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    override fun getSongByKey(accountId: Long, songKey: Long): IndexedTelegramSong? {
        val db = readableDatabase
        val selection = "${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_SONG_KEY} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0"
        val selectionArgs = arrayOf(accountId.toString(), songKey.toString())

        db.query(
            TelegramLibrarySql.TABLE_SONGS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) mapCursorToSong(cursor) else null
        }
    }

    override fun updateSongFileId(accountId: Long, chatId: Long, messageId: Long, fileId: Int) {
        if (accountId <= 0L || chatId == 0L || messageId <= 0L || fileId <= 0) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put(TelegramLibrarySql.COL_FILE_ID, fileId)
            }
            db.update(
                TelegramLibrarySql.TABLE_SONGS,
                cv,
                "${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_CHAT_ID} = ? AND ${TelegramLibrarySql.COL_MESSAGE_ID} = ?",
                arrayOf(accountId.toString(), chatId.toString(), messageId.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun getSources(accountId: Long): List<IndexedTelegramSource> {
        val db = readableDatabase
        val selection = "${TelegramLibrarySql.COL_ACCOUNT_ID} = ?"
        val selectionArgs = arrayOf(accountId.toString())
        val orderBy = "${TelegramLibrarySql.COL_TRACK_COUNT} DESC"
        val results = mutableListOf<IndexedTelegramSource>()

        db.query(
            TelegramLibrarySql.TABLE_SOURCES,
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy
        ).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(mapCursorToSource(cursor))
            }
        }
        return results
    }

    override fun upsertSources(sources: Collection<IndexedTelegramSource>): Int {
        if (sources.isEmpty()) return 0
        val db = writableDatabase
        var count = 0
        db.beginTransactionNonExclusive()
        try {
            for (source in sources) {
                if (source.accountId != accountId) continue
                val values = ContentValues().apply {
                    put(TelegramLibrarySql.COL_ACCOUNT_ID, source.accountId)
                    put(TelegramLibrarySql.COL_CHAT_ID, source.chatId)
                    put(TelegramLibrarySql.COL_TITLE, source.title)
                    put(TelegramLibrarySql.COL_TYPE, source.type)
                    put(TelegramLibrarySql.COL_TRACK_COUNT, source.trackCount)
                    put(TelegramLibrarySql.COL_LAST_UPDATED_AT_MS, source.lastUpdatedAtMs)
                }
                val rowId = db.insertWithOnConflict(
                    TelegramLibrarySql.TABLE_SOURCES,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
                if (rowId != -1L) count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    override fun updateSourceTitle(accountId: Long, chatId: Long, title: String) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(TelegramLibrarySql.COL_ACCOUNT_ID, accountId)
            put(TelegramLibrarySql.COL_CHAT_ID, chatId)
            put(TelegramLibrarySql.COL_TITLE, title)
            put(TelegramLibrarySql.COL_LAST_UPDATED_AT_MS, now)
        }
        // Try update first
        val rows = db.update(
            TelegramLibrarySql.TABLE_SOURCES,
            values,
            "${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_CHAT_ID} = ?",
            arrayOf(accountId.toString(), chatId.toString())
        )
        if (rows == 0) {
            values.put(TelegramLibrarySql.COL_TRACK_COUNT, 0)
            values.put(TelegramLibrarySql.COL_TYPE, "unknown")
            db.insertWithOnConflict(
                TelegramLibrarySql.TABLE_SOURCES,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    override fun recomputeSourceTrackCounts(accountId: Long) {
        val db = writableDatabase
        val query = """
            SELECT ${TelegramLibrarySql.COL_CHAT_ID}, COUNT(*) as cnt, MAX(${TelegramLibrarySql.COL_SOURCE_TITLE}) as stitle
            FROM ${TelegramLibrarySql.TABLE_SONGS}
            WHERE ${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0
            GROUP BY ${TelegramLibrarySql.COL_CHAT_ID}
        """.trimIndent()

        db.beginTransactionNonExclusive()
        try {
            db.rawQuery(query, arrayOf(accountId.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    val chatId = cursor.getLong(0)
                    val count = cursor.getInt(1)
                    val sourceTitle = cursor.getString(2) ?: "Chat $chatId"

                    val values = ContentValues().apply {
                        put(TelegramLibrarySql.COL_ACCOUNT_ID, accountId)
                        put(TelegramLibrarySql.COL_CHAT_ID, chatId)
                        put(TelegramLibrarySql.COL_TITLE, sourceTitle)
                        put(TelegramLibrarySql.COL_TRACK_COUNT, count)
                        put(TelegramLibrarySql.COL_LAST_UPDATED_AT_MS, System.currentTimeMillis())
                    }
                    db.insertWithOnConflict(
                        TelegramLibrarySql.TABLE_SOURCES,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun getSyncState(accountId: Long, scope: String): TelegramSyncState? {
        val db = readableDatabase
        val selection = "${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_SYNC_SCOPE} = ?"
        val selectionArgs = arrayOf(accountId.toString(), scope)

        db.query(
            TelegramLibrarySql.TABLE_SYNC_STATE,
            null,
            selection,
            selectionArgs,
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) mapCursorToSyncState(cursor) else null
        }
    }

    override fun updateSyncState(state: TelegramSyncState) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(TelegramLibrarySql.COL_ACCOUNT_ID, state.accountId)
            put(TelegramLibrarySql.COL_SYNC_SCOPE, state.syncScope)
            put(TelegramLibrarySql.COL_PHASE, state.phase)
            put(TelegramLibrarySql.COL_NEXT_OFFSET, state.nextOffset)
            put(TelegramLibrarySql.COL_PAGES_SYNCED, state.pagesSynced)
            put(TelegramLibrarySql.COL_TOTAL_ITEMS_SYNCED, state.totalItemsSynced)
            put(TelegramLibrarySql.COL_STATUS, state.status)
            put(TelegramLibrarySql.COL_HIGH_WATER_DATE, state.highWaterDate)
            put(TelegramLibrarySql.COL_LAST_SYNC_COMPLETED_AT_MS, state.lastSyncCompletedAtMs)
            put(TelegramLibrarySql.COL_LAST_ERROR_MESSAGE, state.lastErrorMessage)
        }
        db.insertWithOnConflict(
            TelegramLibrarySql.TABLE_SYNC_STATE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    override fun getLatestSongDate(accountId: Long, mediaType: String?): Long {
        val db = readableDatabase
        val selectionArgs = mutableListOf<String>()
        val selection = StringBuilder("${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0")
        selectionArgs.add(accountId.toString())

        if (mediaType != null) {
            selection.append(" AND ${TelegramLibrarySql.COL_MEDIA_TYPE} = ?")
            selectionArgs.add(mediaType)
        }

        val sql = "SELECT MAX(${TelegramLibrarySql.COL_DATE}) FROM ${TelegramLibrarySql.TABLE_SONGS} WHERE $selection"
        db.rawQuery(sql, selectionArgs.toTypedArray()).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    override fun markSongsUnavailable(
        accountId: Long,
        compositeKeys: Collection<Pair<Long, Long>>
    ) {
        if (compositeKeys.isEmpty()) return
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val values = ContentValues().apply {
                put(TelegramLibrarySql.COL_IS_DELETED, 1)
            }
            for ((chatId, messageId) in compositeKeys) {
                db.update(
                    TelegramLibrarySql.TABLE_SONGS,
                    values,
                    "${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_CHAT_ID} = ? AND ${TelegramLibrarySql.COL_MESSAGE_ID} = ?",
                    arrayOf(accountId.toString(), chatId.toString(), messageId.toString())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun deleteAccountData(accountId: Long) {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            db.delete(
                TelegramLibrarySql.TABLE_SONGS,
                "${TelegramLibrarySql.COL_ACCOUNT_ID} = ?",
                arrayOf(accountId.toString())
            )
            db.delete(
                TelegramLibrarySql.TABLE_SOURCES,
                "${TelegramLibrarySql.COL_ACCOUNT_ID} = ?",
                arrayOf(accountId.toString())
            )
            db.delete(
                TelegramLibrarySql.TABLE_SYNC_STATE,
                "${TelegramLibrarySql.COL_ACCOUNT_ID} = ?",
                arrayOf(accountId.toString())
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun mapCursorToSong(cursor: Cursor): IndexedTelegramSong {
        return IndexedTelegramSong(
            accountId = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_ACCOUNT_ID)),
            chatId = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_CHAT_ID)),
            messageId = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_MESSAGE_ID)),
            songKey = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_SONG_KEY)),
            fileId = cursor.getInt(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_FILE_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_TITLE)),
            filename = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_FILENAME)),
            performer = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_PERFORMER)),
            durationSeconds = cursor.getInt(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_DURATION_SECONDS)),
            fileSize = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_FILE_SIZE)),
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_MIME_TYPE)),
            mediaType = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_MEDIA_TYPE)),
            date = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_DATE)),
            lastSeenAtMs = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_LAST_SEEN_AT_MS)),
            sourceTitle = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_SOURCE_TITLE)),
            isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_IS_DELETED)) != 0
        )
    }

    private fun mapCursorToSource(cursor: Cursor): IndexedTelegramSource {
        return IndexedTelegramSource(
            accountId = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_ACCOUNT_ID)),
            chatId = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_CHAT_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_TITLE)),
            type = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_TYPE)),
            trackCount = cursor.getInt(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_TRACK_COUNT)),
            lastUpdatedAtMs = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_LAST_UPDATED_AT_MS))
        )
    }

    private fun mapCursorToSyncState(cursor: Cursor): TelegramSyncState {
        return TelegramSyncState(
            accountId = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_ACCOUNT_ID)),
            syncScope = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_SYNC_SCOPE)),
            phase = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_PHASE)),
            nextOffset = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_NEXT_OFFSET)),
            pagesSynced = cursor.getInt(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_PAGES_SYNCED)),
            totalItemsSynced = cursor.getInt(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_TOTAL_ITEMS_SYNCED)),
            status = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_STATUS)),
            highWaterDate = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_HIGH_WATER_DATE)),
            lastSyncCompletedAtMs = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_LAST_SYNC_COMPLETED_AT_MS)),
            lastErrorMessage = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_LAST_ERROR_MESSAGE))
        )
    }
}
