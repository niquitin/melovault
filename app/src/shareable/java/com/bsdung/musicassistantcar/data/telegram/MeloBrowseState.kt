package com.bsdung.musicassistantcar.data.telegram

/** Bounded, activity-local browse memory. Contains no player, file, bitmap or View. */
internal class MeloBrowseState(private val capacity: Int = 16) {
    data class Key(val ownerId: Long, val scope: MeloBrowseUiPolicy.Scope) {
        init { require(ownerId > 0L) }
    }
    data class Context(
        val query: String = "", val sort: SongSort = SongSort.NEWEST, val letterFloor: String? = null,
        val anchorSongKey: Long? = null, val offsetPx: Int = 0, val sourceScrollY: Int = 0,
        val sourceAnchorChatId: Long? = null, val sourceOffsetPx: Int = 0
    )
    enum class DeckTab { QUEUE }
    data class Deck(val tab: DeckTab = DeckTab.QUEUE, val songKey: Long? = null,
        val offsetPx: Int = 0, val bodyScrollY: Int = 0)
    data class Saved(val active: Key, val sourcesReturn: Key?, val localReturn: Key?,
        val contexts: List<Pair<Key, Context>>, val deck: Deck = Deck())
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

    private val contexts = LinkedHashMap<Key, Context>(16, 0.75f, true)
    var active: Key? = null
        private set
    var sourcesReturn: Key? = null
    var localReturn: Key? = null
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
            anchorSongKey = null, offsetPx = 0, sourceScrollY = 0, sourceAnchorChatId = null, sourceOffsetPx = 0))
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
    fun pending(key: Key, generation: Long): Pending? = contexts[key]?.takeIf { it.anchorSongKey != null }
        ?.let { Pending(key, generation, it) }
    fun sourcePosition(key: Key, chatId: Long?, offsetPx: Int) {
        contexts[key]?.let { put(key, it.copy(sourceAnchorChatId = chatId, sourceOffsetPx = offsetPx)) }
    }

    fun save(): Saved? {
        val key = active ?: return null
        val keys = listOfNotNull(key, sourcesReturn, localReturn).distinct().filter { it.ownerId == key.ownerId }
        return Saved(key, sourcesReturn, localReturn, keys.mapNotNull { k ->
            contexts[k]?.let { k to it.copy(query = it.query.take(512), letterFloor = it.letterFloor?.take(512)) }
        }, deck)
    }
    fun restore(saved: Saved, verifiedOwner: Long): Boolean {
        if (verifiedOwner <= 0L || saved.active.ownerId != verifiedOwner ||
            saved.contexts.size > 3 || saved.contexts.any { it.first.ownerId != verifiedOwner } ||
            saved.contexts.none { it.first == saved.active }) return false
        clear()
        saved.contexts.forEach { (key, context) ->
            put(key, context.copy(query = context.query.take(512), letterFloor = context.letterFloor?.take(512),
                sourceScrollY = context.sourceScrollY.coerceAtLeast(0)))
        }
        active = saved.active
        sourcesReturn = saved.sourcesReturn?.takeIf { it.ownerId == verifiedOwner && contexts.containsKey(it) }
        localReturn = saved.localReturn?.takeIf { it.ownerId == verifiedOwner && contexts.containsKey(it) }
        deck = saved.deck.copy(bodyScrollY = saved.deck.bodyScrollY.coerceAtLeast(0))
        return true
    }
    fun clear() { contexts.clear(); active = null; sourcesReturn = null; localReturn = null; deck = Deck() }

    companion object {
        fun decodeKey(ownerId: Long, kind: String?, chatId: Long?, downloadedOnly: Boolean): Key? = runCatching {
            Key(ownerId, MeloBrowseUiPolicy.Scope(MeloBrowseUiPolicy.Kind.valueOf(requireNotNull(kind)), chatId, downloadedOnly))
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
