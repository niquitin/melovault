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
        // SQLiteOpenHelper wraps this in one transaction: a killed upgrade leaves version 1 intact.
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3 && newVersion >= 3) TelegramLibrarySql.MIGRATE_2_TO_3.forEach(db::execSQL)
    }

    private fun migrateToV2(db: SQLiteDatabase) {
        val started = System.currentTimeMillis()
        db.execSQL(TelegramLibrarySql.MIGRATE_1_TO_2_ADD_COLUMN)
        var rows = 0
        val stmt = db.compileStatement(TelegramLibrarySql.BACKFILL_SEARCH_KEY_UPDATE)
        try {
            db.query(
                TelegramLibrarySql.TABLE_SONGS,
                arrayOf(
                    TelegramLibrarySql.COL_ACCOUNT_ID, TelegramLibrarySql.COL_CHAT_ID,
                    TelegramLibrarySql.COL_MESSAGE_ID, TelegramLibrarySql.COL_TITLE,
                    TelegramLibrarySql.COL_PERFORMER, TelegramLibrarySql.COL_FILENAME
                ),
                null, null, null, null, null
            ).use { c ->
                while (c.moveToNext()) {
                    val key = TelegramSearchPolicy.songSearchKey(
                        c.getString(3), if (c.isNull(4)) null else c.getString(4), c.getString(5)
                    )
                    stmt.clearBindings()
                    stmt.bindString(1, key)
                    stmt.bindLong(2, c.getLong(0))
                    stmt.bindLong(3, c.getLong(1))
                    stmt.bindLong(4, c.getLong(2))
                    stmt.executeUpdateDelete()
                    rows++
                }
            }
        } finally {
            stmt.close()
        }
        android.util.Log.i("MeloVaultDb", "search_key migration v1->v2: rows=$rows ms=${System.currentTimeMillis() - started}")
    }

    override fun upsertSongs(songs: Collection<IndexedTelegramSong>): Int {
        if (songs.isEmpty()) return 0
        val db = writableDatabase
        var count = 0
        db.beginTransactionNonExclusive()
        try {
            for (song in songs) {
                if (song.accountId != accountId) continue
                db.execSQL(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(song))
                count++
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
        require(accountId == this.accountId && checkpoint.accountId == accountId)
        val db = writableDatabase
        var count = 0
        db.beginTransactionNonExclusive()
        try {
            for (song in items) {
                if (song.accountId != accountId) continue
                db.execSQL(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(song))
                count++
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
        limit: Int,
        sort: SongSort,
        fromKey: String?,
        forumTopicId: Int?
    ): List<IndexedTelegramSong> {
        val db = readableDatabase
        val selectionArgs = mutableListOf<String>()
        val selection = StringBuilder("${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0")
        selectionArgs.add(accountId.toString())

        if (chatId != null) {
            selection.append(" AND ${TelegramLibrarySql.COL_CHAT_ID} = ?")
            selectionArgs.add(chatId.toString())
        }

        require(forumTopicId == null || (chatId != null && forumTopicId > 0))
        if (forumTopicId != null) {
            selection.append(" AND ${TelegramLibrarySql.COL_FORUM_TOPIC_ID} = ?")
            selectionArgs.add(forumTopicId.toString())
        }

        if (!fromKey.isNullOrEmpty()) {
            selection.append(" AND ${TelegramLibrarySql.COL_SEARCH_KEY} >= ?")
            selectionArgs.add(fromKey)
        }

        TelegramSearchPolicy.whereClause(searchQuery)?.let {
            selection.append(" AND ").append(it.sql)
            selectionArgs.addAll(it.args)
        }

        val order = when (sort) {
            SongSort.NEWEST -> "${TelegramLibrarySql.COL_DATE} DESC, ${TelegramLibrarySql.COL_MESSAGE_ID} DESC, ${TelegramLibrarySql.COL_CHAT_ID} DESC"
            SongSort.NAME -> "${TelegramLibrarySql.COL_SEARCH_KEY} ASC, ${TelegramLibrarySql.COL_CHAT_ID} ASC, ${TelegramLibrarySql.COL_MESSAGE_ID} ASC"
        }
        val orderBy = "$order LIMIT $limit OFFSET $offset"
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
        searchQuery: String?,
        fromKey: String?,
        forumTopicId: Int?
    ): Int {
        val db = readableDatabase
        val selectionArgs = mutableListOf<String>()
        val selection = StringBuilder("${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0")
        selectionArgs.add(accountId.toString())

        if (chatId != null) {
            selection.append(" AND ${TelegramLibrarySql.COL_CHAT_ID} = ?")
            selectionArgs.add(chatId.toString())
        }

        require(forumTopicId == null || (chatId != null && forumTopicId > 0))
        if (forumTopicId != null) {
            selection.append(" AND ${TelegramLibrarySql.COL_FORUM_TOPIC_ID} = ?")
            selectionArgs.add(forumTopicId.toString())
        }

        if (!fromKey.isNullOrEmpty()) {
            selection.append(" AND ${TelegramLibrarySql.COL_SEARCH_KEY} >= ?")
            selectionArgs.add(fromKey)
        }

        TelegramSearchPolicy.whereClause(searchQuery)?.let {
            selection.append(" AND ").append(it.sql)
            selectionArgs.addAll(it.args)
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

    override fun getSongsByKeys(accountId: Long, songKeys: Collection<Long>): List<IndexedTelegramSong> {
        if (songKeys.isEmpty()) return emptyList()
        val found = LinkedHashMap<Long, IndexedTelegramSong>()
        val db = readableDatabase
        for (chunk in songKeys.distinct().chunked(TelegramLibraryDao.SONGS_BY_KEYS_CHUNK)) {
            val args = arrayOf(accountId.toString()) + chunk.map { it.toString() }
            db.rawQuery(TelegramLibraryDao.songsByKeysSql(chunk.size), args).use { cursor ->
                while (cursor.moveToNext()) {
                    val song = mapCursorToSong(cursor)
                    found.putIfAbsent(song.songKey, song)
                }
            }
        }
        return found.values.toList()
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

    override fun getLiveSourceCounts(accountId: Long): Map<Long, Int> {
        val counts = LinkedHashMap<Long, Int>()
        readableDatabase.rawQuery(TelegramLibraryDao.LIVE_SOURCE_COUNTS_SQL, arrayOf(accountId.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                val count = cursor.getInt(1)
                if (count > 0) counts[cursor.getLong(0)] = count
            }
        }
        return counts
    }

    override fun upsertSources(sources: Collection<IndexedTelegramSource>): Int {
        if (sources.isEmpty()) return 0
        val db = writableDatabase
        var count = 0
        db.beginTransactionNonExclusive()
        try {
            for (source in sources) {
                if (source.accountId != accountId) continue
                db.execSQL(TelegramLibrarySql.UPSERT_SOURCE_SQL, arrayOf(
                    source.accountId, source.chatId, source.title, source.type, source.trackCount,
                    source.lastUpdatedAtMs, source.isForum?.let { if (it) 1 else 0 }, source.accountId, source.chatId,
                ))
                count++
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
        db.beginTransactionNonExclusive()
        try {
            db.execSQL(TelegramLibrarySql.ENSURE_SOURCE_SQL, arrayOf(accountId, chatId, title, now))
            db.execSQL(TelegramLibrarySql.UPDATE_SOURCE_TITLE_SQL, arrayOf(title, now, accountId, chatId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
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

                    val now = System.currentTimeMillis()
                    db.execSQL(TelegramLibrarySql.ENSURE_SOURCE_SQL, arrayOf(accountId, chatId, sourceTitle, now))
                    db.execSQL(TelegramLibrarySql.UPDATE_SOURCE_COUNT_SQL, arrayOf(count, now, accountId, chatId))
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

    override fun getForumTopics(accountId: Long, chatId: Long, query: String?, offset: Int, limit: Int): List<IndexedTelegramForumTopic> {
        val where = StringBuilder("account_id = ? AND chat_id = ? AND is_hidden = 0")
        val args = mutableListOf(accountId.toString(), chatId.toString())
        TelegramSearchPolicy.whereClause(query)?.let { where.append(" AND ").append(it.sql); args.addAll(it.args) }
        val result = mutableListOf<IndexedTelegramForumTopic>()
        readableDatabase.query(TelegramLibrarySql.TABLE_FORUM_TOPICS, null, where.toString(), args.toTypedArray(), null, null,
            "is_pinned DESC, sort_order DESC, forum_topic_id DESC LIMIT ${limit.coerceAtLeast(1)} OFFSET ${offset.coerceAtLeast(0)}").use { c ->
            while (c.moveToNext()) {
                fun index(name: String) = c.getColumnIndexOrThrow(name)
                result.add(IndexedTelegramForumTopic(accountId, chatId, c.getInt(index("forum_topic_id")), c.getString(index("title")),
                    c.getInt(index("is_general")) != 0, c.getInt(index("is_hidden")) != 0, c.getInt(index("is_closed")) != 0,
                    c.getInt(index("is_pinned")) != 0, c.getLong(index("sort_order")), c.getLong(index("last_updated_at_ms"))))
            }
        }
        return result
    }

    private fun mapForumTopic(c: Cursor): IndexedTelegramForumTopic {
        fun index(name: String) = c.getColumnIndexOrThrow(name)
        return IndexedTelegramForumTopic(c.getLong(index("account_id")), c.getLong(index("chat_id")), c.getInt(index("forum_topic_id")),
            c.getString(index("title")), c.getInt(index("is_general")) != 0, c.getInt(index("is_hidden")) != 0,
            c.getInt(index("is_closed")) != 0, c.getInt(index("is_pinned")) != 0, c.getLong(index("sort_order")), c.getLong(index("last_updated_at_ms")))
    }

    override fun getForumTopic(accountId: Long, chatId: Long, topicId: Int): IndexedTelegramForumTopic? =
        readableDatabase.rawQuery("SELECT * FROM telegram_forum_topics WHERE account_id=? AND chat_id=? AND forum_topic_id=?",
            arrayOf(accountId.toString(), chatId.toString(), topicId.toString())).use { if (it.moveToFirst()) mapForumTopic(it) else null }

    override fun getVisibleGeneralTopic(accountId: Long, chatId: Long): IndexedTelegramForumTopic? =
        readableDatabase.rawQuery("SELECT * FROM telegram_forum_topics WHERE account_id=? AND chat_id=? AND is_general=1 AND is_hidden=0 LIMIT 1",
            arrayOf(accountId.toString(), chatId.toString())).use { if (it.moveToFirst()) mapForumTopic(it) else null }

    override fun getForumHistoryStates(accountId: Long, chatId: Long): Map<Int, TelegramForumHistoryState> = linkedMapOf<Int, TelegramForumHistoryState>().apply {
        readableDatabase.rawQuery("SELECT * FROM telegram_forum_history WHERE account_id=? AND chat_id=?",
            arrayOf(accountId.toString(), chatId.toString())).use { c ->
            while (c.moveToNext()) {
                fun value(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
                val id = value("forum_topic_id").toInt()
                put(id, TelegramForumHistoryState(accountId, chatId, id, value("older_cursor"), value("older_complete") != 0L,
                    value("newest_message_id"), value("refresh_cursor"), value("refresh_until_message_id"), value("refresh_pending") != 0L, value("retry_after_ms")))
            }
        }
    }

    override fun getForumTopicCounts(accountId: Long, chatId: Long): Map<Int, Int> = linkedMapOf<Int, Int>().apply {
        readableDatabase.rawQuery(TelegramLibraryDao.FORUM_TOPIC_COUNTS_SQL, arrayOf(accountId.toString(), chatId.toString())).use { c ->
            while (c.moveToNext()) put(c.getInt(0), c.getInt(1))
        }
    }

    override fun commitForumTopics(accountId: Long, chatId: Long, topics: Collection<IndexedTelegramForumTopic>,
        checkpoint: TelegramSyncState?, isCurrent: () -> Boolean): Boolean {
        require(accountId == this.accountId && topics.all { it.accountId == accountId && it.chatId == chatId })
        require(checkpoint == null || (checkpoint.accountId == accountId && checkpoint.syncScope == TelegramLibraryDao.forumCatalogueScope(chatId)))
        if (!isCurrent()) return false
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            if (!isCurrent()) return false
            topics.forEach { db.execSQL(TelegramLibrarySql.UPSERT_FORUM_TOPIC_SQL, TelegramLibrarySql.forumTopicArgs(it)) }
            checkpoint?.let(::updateSyncState)
            if (!isCurrent()) return false
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
    }

    override fun getForumHistoryState(accountId: Long, chatId: Long, forumTopicId: Int): TelegramForumHistoryState? {
        readableDatabase.rawQuery("SELECT * FROM ${TelegramLibrarySql.TABLE_FORUM_HISTORY} WHERE account_id=? AND chat_id=? AND forum_topic_id=?",
            arrayOf(accountId.toString(), chatId.toString(), forumTopicId.toString())).use { c ->
            if (!c.moveToFirst()) return null
            fun value(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
            return TelegramForumHistoryState(accountId, chatId, forumTopicId, value("older_cursor"), value("older_complete") != 0L,
                value("newest_message_id"), value("refresh_cursor"), value("refresh_until_message_id"), value("refresh_pending") != 0L,
                value("retry_after_ms"))
        }
    }

    override fun commitForumHistoryPage(state: TelegramForumHistoryState, songs: Collection<IndexedTelegramSong>,
        isCurrent: () -> Boolean): Boolean {
        require(state.accountId == accountId && songs.all {
            it.accountId == state.accountId && it.chatId == state.chatId && it.forumTopicId == state.forumTopicId
        })
        if (!isCurrent()) return false
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            if (!isCurrent()) return false
            songs.forEach { db.execSQL(TelegramLibrarySql.UPSERT_SONG_SQL, TelegramLibrarySql.songArgs(it)) }
            db.execSQL(TelegramLibrarySql.UPSERT_FORUM_HISTORY_SQL, TelegramLibrarySql.forumHistoryArgs(state))
            if (!isCurrent()) return false
            db.setTransactionSuccessful()
            return true
        } finally { db.endTransaction() }
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
            db.delete(TelegramLibrarySql.TABLE_FORUM_TOPICS, "account_id = ?", arrayOf(accountId.toString()))
            db.delete(TelegramLibrarySql.TABLE_FORUM_HISTORY, "account_id = ?", arrayOf(accountId.toString()))
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
            isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_IS_DELETED)) != 0,
            forumTopicId = cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_FORUM_TOPIC_ID).let {
                if (cursor.isNull(it)) null else cursor.getInt(it)
            }
        )
    }

    private fun mapCursorToSource(cursor: Cursor): IndexedTelegramSource {
        return IndexedTelegramSource(
            accountId = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_ACCOUNT_ID)),
            chatId = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_CHAT_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_TITLE)),
            type = cursor.getString(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_TYPE)),
            trackCount = cursor.getInt(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_TRACK_COUNT)),
            lastUpdatedAtMs = cursor.getLong(cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_LAST_UPDATED_AT_MS)),
            isForum = cursor.getColumnIndexOrThrow(TelegramLibrarySql.COL_IS_FORUM).let { if (cursor.isNull(it)) null else cursor.getInt(it) != 0 }
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
