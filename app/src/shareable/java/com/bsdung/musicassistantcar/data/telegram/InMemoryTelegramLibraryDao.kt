package com.bsdung.musicassistantcar.data.telegram

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory thread-safe implementation of [TelegramLibraryDao]
 * for deterministic unit tests, verification, and fast testing scenarios.
 */
class InMemoryTelegramLibraryDao : TelegramLibraryDao {

    // Key: Triple(accountId, chatId, messageId) -> IndexedTelegramSong
    private val songs = ConcurrentHashMap<Triple<Long, Long, Long>, IndexedTelegramSong>()

    // Key: Pair(accountId, chatId) -> IndexedTelegramSource
    private val sources = ConcurrentHashMap<Pair<Long, Long>, IndexedTelegramSource>()

    // Key: Pair(accountId, syncScope) -> TelegramSyncState
    private val syncStates = ConcurrentHashMap<Pair<Long, String>, TelegramSyncState>()
    private val topics = ConcurrentHashMap<Triple<Long, Long, Int>, IndexedTelegramForumTopic>()
    private val forumHistory = ConcurrentHashMap<Triple<Long, Long, Int>, TelegramForumHistoryState>()

    @Synchronized
    override fun upsertSongs(songs: Collection<IndexedTelegramSong>): Int {
        var count = 0
        for (song in songs) {
            val key = Triple(song.accountId, song.chatId, song.messageId)
            this.songs.compute(key) { _, old -> song.copy(forumTopicId = song.forumTopicId ?: old?.forumTopicId) }
            count++
        }
        return count
    }

    @Synchronized
    override fun commitPageAndCheckpoint(
        accountId: Long,
        phase: String,
        cursor: String,
        items: Collection<IndexedTelegramSong>,
        checkpoint: TelegramSyncState
    ): Int {
        require(accountId > 0L && checkpoint.accountId == accountId)
        synchronized(this) {
            var count = 0
            for (song in items) {
                if (song.accountId != accountId) continue
                val key = Triple(song.accountId, song.chatId, song.messageId)
                this.songs.compute(key) { _, old -> song.copy(forumTopicId = song.forumTopicId ?: old?.forumTopicId) }
                count++
            }
            syncStates[Pair(checkpoint.accountId, checkpoint.syncScope)] = checkpoint
            return count
        }
    }

    @Synchronized
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
        require(forumTopicId == null || (chatId != null && forumTopicId > 0))
        return songs.values
            .asSequence()
            .filter { it.accountId == accountId && !it.isDeleted }
            .filter { chatId == null || it.chatId == chatId }
            .filter { forumTopicId == null || (chatId != null && it.forumTopicId == forumTopicId) }
            .filter { TelegramSearchPolicy.matches(searchQuery, it) }
            .filter { fromKey.isNullOrEmpty() || MeloAlphabetPolicy.keyOf(it) >= fromKey }
            .sortedWith(
                if (sort == SongSort.NAME) MeloAlphabetPolicy.nameComparator()
                else compareByDescending<IndexedTelegramSong> { it.date }.thenByDescending { it.messageId }.thenByDescending { it.chatId }
            )
            .drop(offset)
            .take(limit)
            .toList()
    }

    @Synchronized
    override fun getSongCount(
        accountId: Long,
        chatId: Long?,
        searchQuery: String?,
        fromKey: String?,
        forumTopicId: Int?
    ): Int {
        require(forumTopicId == null || (chatId != null && forumTopicId > 0))
        return songs.values
            .asSequence()
            .filter { it.accountId == accountId && !it.isDeleted }
            .filter { chatId == null || it.chatId == chatId }
            .filter { forumTopicId == null || (chatId != null && it.forumTopicId == forumTopicId) }
            .filter { TelegramSearchPolicy.matches(searchQuery, it) }
            .filter { fromKey.isNullOrEmpty() || MeloAlphabetPolicy.keyOf(it) >= fromKey }
            .count()
    }

    @Synchronized
    override fun getSongByKey(accountId: Long, songKey: Long): IndexedTelegramSong? {
        return songs.values.firstOrNull { it.accountId == accountId && it.songKey == songKey && !it.isDeleted }
    }

    @Synchronized
    override fun getSongsByKeys(accountId: Long, songKeys: Collection<Long>): List<IndexedTelegramSong> {
        if (songKeys.isEmpty()) return emptyList()
        val wanted = songKeys.toHashSet()
        return songs.values
            .filter { it.accountId == accountId && !it.isDeleted && it.songKey in wanted }
            .distinctBy { it.songKey }
    }

    @Synchronized
    override fun updateSongFileId(accountId: Long, chatId: Long, messageId: Long, fileId: Int) {
        val key = Triple(accountId, chatId, messageId)
        val existing = songs[key]
        if (existing != null) {
            songs[key] = existing.copy(fileId = fileId)
        }
    }

    @Synchronized
    override fun getSources(accountId: Long): List<IndexedTelegramSource> {
        return sources.values
            .asSequence()
            .filter { it.accountId == accountId }
            .sortedByDescending { it.trackCount }
            .toList()
    }

    @Synchronized
    override fun getLiveSourceCounts(accountId: Long): Map<Long, Int> =
        songs.values
            .asSequence()
            .filter { it.accountId == accountId && !it.isDeleted }
            .groupingBy { it.chatId }
            .eachCount()

    @Synchronized
    override fun upsertSources(sources: Collection<IndexedTelegramSource>): Int {
        var count = 0
        for (src in sources) {
            this.sources.compute(Pair(src.accountId, src.chatId)) { _, old -> src.copy(isForum = src.isForum ?: old?.isForum) }
            count++
        }
        return count
    }

    @Synchronized
    override fun updateSourceTitle(accountId: Long, chatId: Long, title: String) {
        val key = Pair(accountId, chatId)
        val existing = sources[key]
        if (existing != null) {
            sources[key] = existing.copy(title = title, lastUpdatedAtMs = System.currentTimeMillis())
        } else {
            sources[key] = IndexedTelegramSource(
                accountId = accountId,
                chatId = chatId,
                title = title,
                trackCount = 0
            )
        }
    }

    @Synchronized
    override fun recomputeSourceTrackCounts(accountId: Long) {
        val activeSongs = songs.values.filter { it.accountId == accountId && !it.isDeleted }
        val countsByChat = activeSongs.groupingBy { it.chatId }.eachCount()

        for ((chatId, count) in countsByChat) {
            val key = Pair(accountId, chatId)
            val existing = sources[key]
            if (existing != null) {
                sources[key] = existing.copy(trackCount = count)
            } else {
                val sampleTitle = activeSongs.firstOrNull { it.chatId == chatId }?.sourceTitle ?: "Chat $chatId"
                sources[key] = IndexedTelegramSource(
                    accountId = accountId,
                    chatId = chatId,
                    title = sampleTitle,
                    trackCount = count
                )
            }
        }
    }

    @Synchronized
    override fun getSyncState(accountId: Long, scope: String): TelegramSyncState? {
        return syncStates[Pair(accountId, scope)]
    }

    @Synchronized
    override fun updateSyncState(state: TelegramSyncState) {
        syncStates[Pair(state.accountId, state.syncScope)] = state
    }

    @Synchronized
    override fun getForumTopics(accountId: Long, chatId: Long, query: String?, offset: Int, limit: Int): List<IndexedTelegramForumTopic> =
        topics.values.filter { topic -> topic.accountId == accountId && topic.chatId == chatId && !topic.isHidden &&
            TelegramSearchPolicy.queryTerms(query).all { MeloComponentPolicy.searchKey(topic.title).contains(it) } }
            .sortedWith(compareByDescending<IndexedTelegramForumTopic> { it.isPinned }.thenByDescending { it.order }.thenByDescending { it.forumTopicId })
            .drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(1))

    @Synchronized
    override fun getForumTopicCounts(accountId: Long, chatId: Long): Map<Int, Int> = songs.values.asSequence()
        .filter { it.accountId == accountId && it.chatId == chatId && !it.isDeleted && it.forumTopicId != null }
        .groupingBy { it.forumTopicId!! }.eachCount()

    @Synchronized
    override fun getForumTopic(accountId: Long, chatId: Long, topicId: Int) = topics[Triple(accountId, chatId, topicId)]

    @Synchronized
    override fun getVisibleGeneralTopic(accountId: Long, chatId: Long) = topics.values.firstOrNull {
        it.accountId == accountId && it.chatId == chatId && it.isGeneral && !it.isHidden
    }

    @Synchronized
    override fun getForumHistoryStates(accountId: Long, chatId: Long) = forumHistory.values
        .filter { it.accountId == accountId && it.chatId == chatId }.associateBy { it.forumTopicId }

    @Synchronized
    override fun commitForumTopics(accountId: Long, chatId: Long, topics: Collection<IndexedTelegramForumTopic>,
        checkpoint: TelegramSyncState?, isCurrent: () -> Boolean): Boolean {
        require(topics.all { it.accountId == accountId && it.chatId == chatId })
        require(checkpoint == null || (checkpoint.accountId == accountId && checkpoint.syncScope == TelegramLibraryDao.forumCatalogueScope(chatId)))
        if (!isCurrent()) return false
        val updates = topics.associateBy { Triple(it.accountId, it.chatId, it.forumTopicId) }
        if (!isCurrent()) return false
        this.topics.putAll(updates)
        checkpoint?.let { syncStates[it.accountId to it.syncScope] = it }
        return true
    }

    @Synchronized
    override fun getForumHistoryState(accountId: Long, chatId: Long, forumTopicId: Int): TelegramForumHistoryState? =
        forumHistory[Triple(accountId, chatId, forumTopicId)]

    @Synchronized
    override fun commitForumHistoryPage(state: TelegramForumHistoryState, songs: Collection<IndexedTelegramSong>,
        isCurrent: () -> Boolean): Boolean {
        require(songs.all { it.accountId == state.accountId && it.chatId == state.chatId && it.forumTopicId == state.forumTopicId })
        if (!isCurrent()) return false
        val updates = songs.associate { song ->
            val key = Triple(song.accountId, song.chatId, song.messageId)
            key to song.copy(forumTopicId = song.forumTopicId ?: this.songs[key]?.forumTopicId)
        }
        if (!isCurrent()) return false
        this.songs.putAll(updates)
        forumHistory[Triple(state.accountId, state.chatId, state.forumTopicId)] = state
        return true
    }

    @Synchronized
    override fun getLatestSongDate(accountId: Long, mediaType: String?): Long {
        return songs.values
            .asSequence()
            .filter { it.accountId == accountId && !it.isDeleted }
            .filter { mediaType == null || it.mediaType == mediaType }
            .maxOfOrNull { it.date } ?: 0L
    }

    @Synchronized
    override fun markSongsUnavailable(
        accountId: Long,
        compositeKeys: Collection<Pair<Long, Long>>
    ) {
        for ((chatId, messageId) in compositeKeys) {
            val key = Triple(accountId, chatId, messageId)
            val existing = songs[key]
            if (existing != null) {
                songs[key] = existing.copy(isDeleted = true)
            }
        }
    }

    @Synchronized
    override fun deleteAccountData(accountId: Long) {
        songs.keys.removeIf { it.first == accountId }
        sources.keys.removeIf { it.first == accountId }
        syncStates.keys.removeIf { it.first == accountId }
        topics.keys.removeIf { it.first == accountId }
        forumHistory.keys.removeIf { it.first == accountId }
    }
}
