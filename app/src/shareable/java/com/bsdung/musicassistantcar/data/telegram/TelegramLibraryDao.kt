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
        fromKey: String? = null
    ): List<IndexedTelegramSong>

    /**
     * Counts active (non-deleted) songs matching filter criteria.
     */
    fun getSongCount(
        accountId: Long,
        chatId: Long? = null,
        searchQuery: String? = null,
        fromKey: String? = null
    ): Int

    /**
     * Retrieves a song by its 64-bit domain-separated songKey.
     */
    fun getSongByKey(accountId: Long, songKey: Long): IndexedTelegramSong?

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
        /**
         * The one statement behind [getLiveSourceCounts]: a SELECT over the existing index
         * `idx_tg_songs_chat (account_id, chat_id, is_deleted)`, which covers it entirely (no table
         * read, no new index). Kept here so the SQLite test runs the very same text.
         */
        const val LIVE_SOURCE_COUNTS_SQL =
            "SELECT ${TelegramLibrarySql.COL_CHAT_ID}, COUNT(*) FROM ${TelegramLibrarySql.TABLE_SONGS} " +
                "WHERE ${TelegramLibrarySql.COL_ACCOUNT_ID} = ? AND ${TelegramLibrarySql.COL_IS_DELETED} = 0 " +
                "GROUP BY ${TelegramLibrarySql.COL_CHAT_ID}"
    }
}
