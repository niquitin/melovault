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
     * Retrieves songs for an account, with optional chat filtering, substring title search,
     * pagination, ordered by message date descending.
     */
    fun getSongs(
        accountId: Long,
        chatId: Long? = null,
        searchQuery: String? = null,
        offset: Int = 0,
        limit: Int = 100
    ): List<IndexedTelegramSong>

    /**
     * Counts active (non-deleted) songs matching filter criteria.
     */
    fun getSongCount(
        accountId: Long,
        chatId: Long? = null,
        searchQuery: String? = null
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
}
