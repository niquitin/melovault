package com.bsdung.musicassistantcar.data.telegram

/**
 * Data Access Object contract for MeloVault Telegram Library Index.
 */
interface TelegramLibraryDao {

    /**
     * Inserts or updates a collection of indexed Telegram songs in a single transaction.
     * Returns count of songs successfully inserted/updated.
     */
    fun upsertSongs(songs: Collection<IndexedTelegramSong>): Int

    /**
     * Inserts or updates songs AND updates sync state checkpoint atomically
     * in a single transaction. Returns count of songs committed.
     */
    fun commitPageAndCheckpoint(
        accountId: Long,
        phase: String,
        cursor: String,
        items: Collection<IndexedTelegramSong>,
        checkpoint: TelegramSyncState
    ): Int

    /**
     * Retrieves songs for an account, with optional chat filtering, accent-insensitive search over title/performer/filename,
     * pagination, ordered by message date descending (NEWEST) or search key (NAME).
     * [fromKey] keeps only songs whose search key is >= it (A–Z jump).
     */
    fun getSongs(
        accountId: Long,
        chatId: Long? = null,
        searchQuery: String? = null,
        offset: Int = 0,
        limit: Int = 100,
        sort: SongSort = SongSort.NEWEST,
        fromKey: String? = null,
        forumTopicId: Int? = null
    ): List<IndexedTelegramSong>

    /**
     * Counts active (non-deleted) songs matching filter criteria.
     */
    fun getSongCount(
        accountId: Long,
        chatId: Long? = null,
        searchQuery: String? = null,
        fromKey: String? = null,
        forumTopicId: Int? = null
    ): Int

    /**
     * Retrieves a song by its 64-bit domain-separated songKey.
     */
    fun getSongByKey(accountId: Long, songKey: Long): IndexedTelegramSong?

    /**
     * READ-ONLY (E96 P0): the live songs of [songKeys] for one account, in one pass instead of one
     * query per key. Keys that are not indexed (or whose song is deleted) are simply absent from the
     * result; the caller decides what to show for them. At most one song per key.
     */
    fun getSongsByKeys(accountId: Long, songKeys: Collection<Long>): List<IndexedTelegramSong>

    /**
     * Updates the session-scoped file_id for a specific indexed song.
     */
    fun updateSongFileId(accountId: Long, chatId: Long, messageId: Long, fileId: Int)

    /**
     * Retrieves all sources (channels/chats) for an account, ordered by track count descending.
     */
    fun getSources(accountId: Long): List<IndexedTelegramSource>

    /**
     * READ-ONLY (E95 F05): the real number of live (not deleted) songs per chat, straight from the
     * songs table, for chats that have at least one. Unlike the stored `track_count` of
     * [getSources], which is only recalculated when a full sync completes, this is always true.
     * Never writes; chat 0 and chats without songs are not in the map.
     */
    fun getLiveSourceCounts(accountId: Long): Map<Long, Int>

    /**
     * Inserts or updates sources.
     */
    fun upsertSources(sources: Collection<IndexedTelegramSource>): Int

    /**
     * Updates the human-readable title of a source.
     */
    fun updateSourceTitle(accountId: Long, chatId: Long, title: String)

    /**
     * Recalculates track counts for all sources belonging to an account.
     */
    fun recomputeSourceTrackCounts(accountId: Long)

    /**
     * Retrieves the sync state checkpoint for an account and scope.
     */
    fun getSyncState(
        accountId: Long,
        scope: String = TelegramSyncState.SCOPE_GLOBAL
    ): TelegramSyncState?

    /**
     * Updates or creates the sync state checkpoint for an account and scope.
     */
    fun updateSyncState(state: TelegramSyncState)

    fun getForumTopics(accountId: Long, chatId: Long, query: String? = null, offset: Int = 0, limit: Int = 50): List<IndexedTelegramForumTopic>
    fun getForumTopic(accountId: Long, chatId: Long, topicId: Int): IndexedTelegramForumTopic?
    fun getVisibleGeneralTopic(accountId: Long, chatId: Long): IndexedTelegramForumTopic?
    fun getForumHistoryStates(accountId: Long, chatId: Long): Map<Int, TelegramForumHistoryState>
    fun getForumTopicCounts(accountId: Long, chatId: Long): Map<Int, Int>
    /** Additive metadata refresh: missing rows in a partial page never delete cached topics. */
    fun commitForumTopics(
        accountId: Long, chatId: Long, topics: Collection<IndexedTelegramForumTopic>,
        checkpoint: TelegramSyncState? = null, isCurrent: () -> Boolean = { true },
    ): Boolean
    fun getForumHistoryState(accountId: Long, chatId: Long, forumTopicId: Int): TelegramForumHistoryState?
    /** Songs and BOTH history cursors commit together, or not at all (including cancellation). */
    fun commitForumHistoryPage(
        state: TelegramForumHistoryState, songs: Collection<IndexedTelegramSong>, isCurrent: () -> Boolean = { true },
    ): Boolean

    /**
     * Gets the newest message timestamp (seconds) for incremental delta calculation.
     */
    fun getLatestSongDate(accountId: Long, mediaType: String? = null): Long

    /**
     * Marks songs as deleted/unavailable (tombstoned) without purging physical cache.
     */
    fun markSongsUnavailable(accountId: Long, compositeKeys: Collection<Pair<Long, Long>>)

    /**
     * Deletes all index data (songs, sources, sync state) for an account.
     */
    fun deleteAccountData(accountId: Long)

    companion object {
        fun forumCatalogueScope(chatId: Long) = "forum_topics:$chatId"
        /** SQLite allows 999 bound variables; one is the account, the rest are keys. */
        const val SONGS_BY_KEYS_CHUNK = 400

        /** The statement behind [getSongsByKeys] for [count] keys. Kept here so the SQLite test runs the very same text. */
        fun songsByKeysSql(count: Int): String {
            require(count in 1..SONGS_BY_KEYS_CHUNK)
            return "SELECT * FROM ${TelegramLibrarySql.TABLE_SONGS} " +
                "WHERE ${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0 " +
                "AND ${TelegramLibrarySql.COL_SONG_KEY} IN (${List(count) { "?" }.joinToString(",")})"
        }

        /**
         * The one statement behind [getLiveSourceCounts]: a SELECT over the existing index
         * `idx_tg_songs_chat (account_id, chat_id, is_deleted)`, which covers it entirely (no table
         * read, no new index). Kept here so the SQLite test runs the very same text.
         */
        const val LIVE_SOURCE_COUNTS_SQL =
            "SELECT ${TelegramLibrarySql.COL_CHAT_ID}, COUNT(*) FROM ${TelegramLibrarySql.TABLE_SONGS} " +
                "WHERE ${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0 " +
                "GROUP BY ${TelegramLibrarySql.COL_CHAT_ID}"

        const val FORUM_TOPIC_COUNTS_SQL =
            "SELECT forum_topic_id, COUNT(*) FROM telegram_songs " +
                "WHERE account_id = ? AND chat_id = ? AND is_deleted = 0 AND forum_topic_id IS NOT NULL GROUP BY forum_topic_id"
    }
}
