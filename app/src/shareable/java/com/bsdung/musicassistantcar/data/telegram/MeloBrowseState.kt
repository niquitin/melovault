package com.bsdung.musicassistantcar.data.telegram

/** Bounded, activity-local browse memory. Contains no player, file, bitmap or View. */
internal class MeloBrowseState(private val capacity: Int = 16) {
    data class Key(val ownerId: Long, val scope: MeloBrowseUiPolicy.Scope) {
        init { require(ownerId > 0L) }
    }
    data class Context(
        val query: String = "", val sort: SongSort = SongSort.NEWEST, val letterFloor: String? = null,
        val anchorSongKey: Long? = null, val offsetPx: Int = 0, val sourceScrollY: Int = 0,
        val sourceAnchorChatId: Long? = null, val sourceOffsetPx: Int = 0,
        val topicAnchorId: Int? = null, val topicOffsetPx: Int = 0
    )
    enum class DeckTab { QUEUE }
    data class Deck(val tab: DeckTab = DeckTab.QUEUE, val songKey: Long? = null,
        val offsetPx: Int = 0, val bodyScrollY: Int = 0)
    data class Saved(val active: Key, val sourcesReturn: Key?, val localReturn: Key?,
        val contexts: List<Pair<Key, Context>>, val deck: Deck = Deck(), val topicsReturn: Key? = null)
    data class Pending(val key: Key, val generation: Long, val context: Context)
    class QueryDraft(initial: String = "") {
        var committed: String = initial
            private set
        private var draft: String = initial
        fun edit(value: String) { draft = value }
        fun sync(value: String) { committed = value; draft = value }
        fun take(): String? = draft.takeIf { it != committed }?.also { committed = it }
    }
    sealed class Anchor {
        data class Found(val index: Int, val offsetPx: Int) : Anchor()
        object Wait : Anchor()
        object Drop : Anchor()
    }

    /**
     * A found anchor is a request until a layout shows it: a row refresh before that layout re-syncs a
     * ListView to its old first row, so the selection is re-applied after each refresh and positions are
     * not saved meanwhile. A touch, a letter jump or a new context ends the request.
     */
    class SongRestore {
        data class Selection(val index: Int, val offsetPx: Int)
        var pending: Selection? = null
            private set
        fun start(index: Int, offsetPx: Int): Selection = Selection(index.coerceAtLeast(0), offsetPx).also { pending = it }
        fun afterRefresh(): Selection? = pending
        /** True once the anchor is the first row, or is visible with the list clamped at its end. */
        fun onScrolled(first: Int, visible: Int, total: Int): Boolean {
            val wanted = pending ?: return true
            val atEnd = visible > 0 && first + visible >= total && wanted.index in first until total
            if (first != wanted.index && !atEnd) return false
            pending = null
            return true
        }
        fun cancel() { pending = null }
    }

    private val contexts = LinkedHashMap<Key, Context>(16, 0.75f, true)
    var active: Key? = null
        private set
    var sourcesReturn: Key? = null
    var localReturn: Key? = null
    var topicsReturn: Key? = null
    var deck: Deck = Deck()

    init { require(capacity > 0) }
    val size: Int get() = contexts.size

    fun activate(key: Key, defaultSort: SongSort): Context {
        active = key
        return contexts[key] ?: Context(sort = defaultSort).also { put(key, it) }
    }
    fun get(key: Key): Context? = contexts[key]
    fun put(key: Key, context: Context) {
        contexts[key] = context
        while (contexts.size > capacity) contexts.remove(contexts.keys.first())
    }
    fun query(key: Key, query: String) {
        val old = contexts[key] ?: Context()
        if (query != old.query) put(key, old.copy(query = query, letterFloor = null,
            anchorSongKey = null, offsetPx = 0, sourceScrollY = 0, sourceAnchorChatId = null, sourceOffsetPx = 0,
            topicAnchorId = null, topicOffsetPx = 0))
    }
    fun sort(key: Key, sort: SongSort, floor: String? = null) {
        val old = contexts[key] ?: Context()
        if (old.sort != sort || old.letterFloor != floor) {
            put(key, old.copy(sort = sort, letterFloor = floor, anchorSongKey = null, offsetPx = 0))
        }
    }
    fun position(key: Key, songKey: Long?, offsetPx: Int, sourceScrollY: Int) {
        val old = contexts[key] ?: return
        put(key, old.copy(anchorSongKey = songKey ?: old.anchorSongKey,
            offsetPx = if (songKey == null) old.offsetPx else offsetPx,
            sourceScrollY = sourceScrollY.coerceAtLeast(0)))
    }
    /**
     * Saves the place of the rows on screen. [generationOk]: the rows come from the newest query; rows of an
     * older query never save. The key on screen can still change without a new query (right after app open the
     * sync start turns on all-chats mode once the first list is drawn), so that key is activated here with the
     * list's own query/sort ([shown]) instead of every save being dropped until the next refresh (D2).
     */
    fun savePosition(key: Key, generationOk: Boolean, defaultSort: SongSort, shown: (Context) -> Context,
        songKey: Long?, offsetPx: Int, sourceScrollY: Int): Boolean {
        if (!generationOk) return false
        if (active != key) put(key, shown(activate(key, defaultSort)))
        position(key, songKey, offsetPx, sourceScrollY)
        return true
    }
    fun pending(key: Key, generation: Long): Pending? = contexts[key]?.takeIf { it.anchorSongKey != null }
        ?.let { Pending(key, generation, it) }
    fun sourcePosition(key: Key, chatId: Long?, offsetPx: Int) {
        contexts[key]?.let { put(key, it.copy(sourceAnchorChatId = chatId, sourceOffsetPx = offsetPx)) }
    }
    fun topicPosition(key: Key, topicId: Int?, offsetPx: Int) {
        contexts[key]?.let { put(key, it.copy(topicAnchorId = topicId, topicOffsetPx = offsetPx)) }
    }

    fun save(): Saved? {
        val key = active ?: return null
        val forumParents = if (key.scope.kind in setOf(MeloBrowseUiPolicy.Kind.FORUM_TOPIC, MeloBrowseUiPolicy.Kind.FORUM_TOPICS) ||
            (topicsReturn != null && topicsReturn?.scope?.chatId == key.scope.chatId)) listOf(Key(key.ownerId, MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.SOURCES))) else emptyList()
        val counterpart = if (key.scope.kind == MeloBrowseUiPolicy.Kind.FORUM_TOPIC) key.copy(scope = key.scope.copy(downloadedOnly = !key.scope.downloadedOnly)) else null
        val keys = (listOfNotNull(key, sourcesReturn, localReturn, topicsReturn, counterpart) + forumParents).distinct().filter { it.ownerId == key.ownerId }
        return Saved(key, sourcesReturn, localReturn, keys.mapNotNull { k ->
            contexts[k]?.let { k to it.copy(query = it.query.take(512), letterFloor = it.letterFloor?.take(512)) }
        }, deck, topicsReturn)
    }
    fun restore(saved: Saved, verifiedOwner: Long): Boolean {
        if (verifiedOwner <= 0L || saved.active.ownerId != verifiedOwner ||
            saved.contexts.size > 8 || saved.contexts.map { it.first }.distinct().size != saved.contexts.size ||
            saved.contexts.any { it.first.ownerId != verifiedOwner } ||
            saved.contexts.none { it.first == saved.active }) return false
        clear()
        saved.contexts.forEach { (key, context) ->
            put(key, context.copy(query = context.query.take(512), letterFloor = context.letterFloor?.take(512),
                sourceScrollY = context.sourceScrollY.coerceAtLeast(0)))
        }
        active = saved.active
        sourcesReturn = saved.sourcesReturn?.takeIf { it.ownerId == verifiedOwner && contexts.containsKey(it) }
        localReturn = saved.localReturn?.takeIf { it.ownerId == verifiedOwner && contexts.containsKey(it) }
        topicsReturn = saved.topicsReturn?.takeIf { it.ownerId == verifiedOwner && contexts.containsKey(it) }
        deck = saved.deck.copy(bodyScrollY = saved.deck.bodyScrollY.coerceAtLeast(0))
        return true
    }
    fun clear() { contexts.clear(); active = null; sourcesReturn = null; localReturn = null; topicsReturn = null; deck = Deck() }

    companion object {
        fun decodeKey(ownerId: Long, kind: String?, chatId: Long?, downloadedOnly: Boolean, forumTopicId: Int? = null): Key? = runCatching {
            val decoded = MeloBrowseUiPolicy.Kind.valueOf(requireNotNull(kind))
            val safeKind = if (decoded == MeloBrowseUiPolicy.Kind.FORUM_TOPIC && forumTopicId == null) MeloBrowseUiPolicy.Kind.CHANNEL else decoded
            Key(ownerId, MeloBrowseUiPolicy.Scope(safeKind, chatId,
                downloadedOnly && !(decoded == MeloBrowseUiPolicy.Kind.FORUM_TOPIC && forumTopicId == null),
                forumTopicId.takeIf { safeKind == MeloBrowseUiPolicy.Kind.FORUM_TOPIC }))
        }.getOrNull()

        /** Missing in an early page is pending, not an instruction to jump to row zero. */
        fun locate(pending: Pending, key: Key, generation: Long, query: String, sort: SongSort,
            floor: String?, loadedKeys: List<Long>, complete: Boolean): Anchor {
            val wanted = pending.context
            if (pending.key != key || pending.generation != generation || wanted.query != query ||
                wanted.sort != sort || wanted.letterFloor != floor) return Anchor.Drop
            val index = loadedKeys.indexOf(wanted.anchorSongKey)
            return when {
                index >= 0 -> Anchor.Found(index, wanted.offsetPx)
                complete -> Anchor.Drop
                else -> Anchor.Wait
            }
        }
    }
}
