package com.bsdung.musicassistantcar.data.telegram

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/**
 * Nhiều Playlist có tên, S2: the pure data model of the named playlists (no Android, no Telegram, no clock, no I/O).
 *
 * It is a CRDT like Playlist V2 and does not touch it. Every register is decided by logical counters
 * and device ids, never by wall time:
 *  - a playlist exists while at least one of its create tags is not covered by a delete (observed-remove);
 *  - its name and the position of a song are last-writer-wins registers ordered by (seq, tag);
 *  - a song is in a playlist while at least one of its add tags is not covered by a remove.
 * Merging replicas is a union, so it is commutative, associative and idempotent. Songs of the device itself
 * live only in [NamedPlaylistState.localItems]: never an operation, never in the outbox, never synced.
 */

/** Id of a named playlist: `<deviceId>.<counter>`, made when it is created and never changed. */
internal data class ListId(val device: String, val counter: Long) : Comparable<ListId> {
    init {
        require(PlaylistIds.isDeviceId(device)) { "Bad device id" }
        require(counter > 0L) { "List counter must be positive" }
    }

    val key: String get() = "$device.$counter"

    override fun compareTo(other: ListId): Int {
        val byDevice = device.compareTo(other.device)
        return if (byDevice != 0) byDevice else counter.compareTo(other.counter)
    }

    companion object {
        fun parse(text: String): ListId? {
            val cut = text.lastIndexOf('.')
            if (cut <= 0) return null
            val counter = text.substring(cut + 1).toLongOrNull() ?: return null
            val device = text.substring(0, cut)
            if (counter <= 0L || !PlaylistIds.isDeviceId(device)) return null
            return ListId(device, counter)
        }
    }
}

internal object NamedPlaylistNames {
    const val MAX_CHARS = 60

    /** The name as it is stored (trimmed), or null when it is empty, too long or holds control or unpaired characters. */
    fun clean(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty() || name.codePointCount(0, name.length) > MAX_CHARS) return null
        if (name.any { Character.isISOControl(it) }) return null
        if (String(name.toByteArray(Charsets.UTF_8), Charsets.UTF_8) != name) return null
        return name
    }

    fun isStored(name: String): Boolean = clean(name) == name

    /** Two names with the same key count as the same name: no accents, no case, one space between words. */
    fun comparisonKey(name: String): String {
        val plain = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return plain.lowercase(Locale.ROOT).replace('đ', 'd').replace(Regex("\\s+"), " ")
    }
}

/** What identifies a song of the device itself without its path or URI: a short digest of facts the user can see. */
internal object LocalFingerprint {
    fun of(displayName: String, durationMs: Long, sizeBytes: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$displayName\u0000$durationMs\u0000$sizeBytes".toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private val SHAPE = Regex("^[0-9a-f]{8,64}$")
    fun isValid(value: String): Boolean = SHAPE.matches(value)
}

internal enum class NamedOpType { LCREATE, LRENAME, LDELETE, IADD, IREMOVE, IMOVE }

/**
 * One replicated change. [dots] is the single new tag of LCREATE/IADD (equal to [opId]) or the tags a delete/remove
 * observed; empty for LRENAME/IMOVE. [opId] is unique and never changes, also when the operation is sent again.
 */
internal data class NamedOp(
    val opId: String,
    val type: NamedOpType,
    val listId: ListId,
    val item: PlaylistItemId.Telegram?,
    val seq: Long,
    val dots: Set<Dot>,
    val name: String?,
    val pos: String?,
) {
    val opDot: Dot = Dot.parse(opId) ?: throw IllegalArgumentException("Bad op id")

    init {
        require(seq > 0L) { "seq must be positive" }
        require(dots.size <= MAX_DOTS) { "Too many tags" }
        val ownTag = setOf(opDot)
        when (type) {
            NamedOpType.LCREATE -> {
                require(item == null && pos == null && name != null && NamedPlaylistNames.isStored(name)) { "Bad LCREATE" }
                require(dots == ownTag && listId == ListId(opDot.device, opDot.counter)) { "LCREATE tag must be its own list id" }
            }
            NamedOpType.LRENAME -> require(item == null && pos == null && dots.isEmpty() && name != null && NamedPlaylistNames.isStored(name)) { "Bad LRENAME" }
            NamedOpType.LDELETE -> require(item == null && pos == null && name == null && dots.isNotEmpty()) { "Bad LDELETE" }
            NamedOpType.IADD -> require(item != null && name == null && dots == ownTag && pos != null && FractionalIndex.isValid(pos)) { "Bad IADD" }
            NamedOpType.IREMOVE -> require(item != null && name == null && pos == null && dots.isNotEmpty()) { "Bad IREMOVE" }
            NamedOpType.IMOVE -> require(item != null && name == null && dots.isEmpty() && pos != null && FractionalIndex.isValid(pos)) { "Bad IMOVE" }
        }
    }

    companion object {
        const val MAX_DOTS = 32
    }
}

/** A last-writer-wins value: the one with the larger ([seq], [op]) wins, on every replica alike. */
internal data class Reg<T>(val value: T, val seq: Long, val op: Dot) {
    fun beats(other: Reg<T>): Boolean = seq > other.seq || (seq == other.seq && op > other.op)
}

private fun <T> Reg<T>?.winner(candidate: Reg<T>): Reg<T> = if (this == null || candidate.beats(this)) candidate else this

internal data class NamedItemRecord(
    val adds: Set<Dot>,
    val removed: Set<Dot>,
    val pos: Reg<String>?,
) {
    val liveDots: Set<Dot> get() = adds - removed
    val isLive: Boolean get() = adds.any { it !in removed }
}

internal data class NamedListRecord(
    val created: Map<Dot, Long>,
    val deleted: Set<Dot>,
    val name: Reg<String>?,
    val items: Map<PlaylistItemId.Telegram, NamedItemRecord>,
) {
    val liveCreateDots: Set<Dot> get() = created.keys - deleted
    val isAlive: Boolean get() = created.keys.any { it !in deleted }

    /** When the playlist was made, for the order of the index: the earliest live create tag's logical time. */
    val createdAtSeq: Long get() = created.filterKeys { it !in deleted }.values.minOrNull() ?: Long.MAX_VALUE

    fun apply(op: NamedOp): NamedListRecord {
        return when (op.type) {
            NamedOpType.LCREATE -> {
                val dot = op.dots.single()
                copy(
                    created = created + (dot to (created[dot]?.let { minOf(it, op.seq) } ?: op.seq)),
                    name = name.winner(Reg(checkNotNull(op.name), op.seq, op.opDot)),
                )
            }
            NamedOpType.LRENAME -> copy(name = name.winner(Reg(checkNotNull(op.name), op.seq, op.opDot)))
            NamedOpType.LDELETE -> copy(deleted = deleted + op.dots)
            NamedOpType.IADD -> withItem(op) { it.copy(adds = it.adds + op.dots.single(), pos = it.pos.winner(Reg(checkNotNull(op.pos), op.seq, op.opDot))) }
            NamedOpType.IREMOVE -> withItem(op) { it.copy(removed = it.removed + op.dots) }
            NamedOpType.IMOVE -> withItem(op) { it.copy(pos = it.pos.winner(Reg(checkNotNull(op.pos), op.seq, op.opDot))) }
        }
    }

    private fun withItem(op: NamedOp, change: (NamedItemRecord) -> NamedItemRecord): NamedListRecord {
        val item = checkNotNull(op.item)
        val current = items[item] ?: NamedItemRecord(emptySet(), emptySet(), null)
        return copy(items = items + (item to change(current)))
    }

    companion object {
        val EMPTY = NamedListRecord(emptyMap(), emptySet(), null, emptyMap())
    }
}

/** A song of the device itself inside a playlist. [fingerprint] is a [LocalFingerprint]; there is no path or URI. */
internal data class LocalEntry(val localId: Long, val pos: String, val fingerprint: String) {
    init {
        require(FractionalIndex.isValid(pos)) { "Bad position" }
        require(LocalFingerprint.isValid(fingerprint)) { "Bad fingerprint" }
    }
}

internal enum class NamedRefusal {
    /** 100 playlists are alive. */
    LIST_LIMIT,

    /** The playlist already holds 1000 songs. */
    ITEM_LIMIT,
    NO_SUCH_LIST,
    NO_SUCH_ITEM,
    BAD_NAME,
    DUPLICATE_NAME,
}

internal data class NamedOutboxEntry(val op: NamedOp, val status: OutboxStatus, val attempts: Int)

/** The result of one change: the new state, the operations it made (none = nothing to tell other devices) or why it was refused. */
internal data class NamedMutation(
    val state: NamedPlaylistState,
    val ops: List<NamedOp> = emptyList(),
    val refusal: NamedRefusal? = null,
    val listId: ListId? = null,
)

internal data class NamedListView(val id: ListId, val name: String, val count: Int)

/** A song of a playlist in display order. [fingerprint] is set for songs of the device itself only. */
internal data class NamedEntry(val item: PlaylistItemId, val pos: String, val fingerprint: String?)

/** One device's complete named-playlist replica. Immutable. */
internal data class NamedPlaylistState(
    val accountId: Long,
    val deviceId: String,
    val lamport: Long,
    val opCounter: Long,
    val lists: Map<ListId, NamedListRecord>,
    val localItems: Map<ListId, List<LocalEntry>>,
    val outbox: List<NamedOutboxEntry>,
    val checkpoint: SyncCheckpoint,
) {
    init {
        require(accountId > 0L)
        require(PlaylistIds.isDeviceId(deviceId))
    }

    // ---- views ---------------------------------------------------------------------------------

    private fun aliveRecord(id: ListId): NamedListRecord? = lists[id]?.takeIf { it.isAlive }

    /** Live playlists in the order they were made (logical time, then id). */
    fun lists(): List<NamedListView> =
        lists.filterValues { it.isAlive }.entries
            .sortedWith(compareBy({ it.value.createdAtSeq }, { it.key }))
            .map { (id, rec) -> NamedListView(id, rec.name?.value.orEmpty(), itemCount(id)) }

    fun listCount(): Int = lists.values.count { it.isAlive }

    fun exists(id: ListId): Boolean = aliveRecord(id) != null

    fun nameOf(id: ListId): String? = aliveRecord(id)?.let { it.name?.value.orEmpty() }

    fun itemCount(id: ListId): Int {
        val rec = aliveRecord(id) ?: return 0
        return rec.items.values.count { it.isLive } + localItems[id].orEmpty().size
    }

    /** Songs of a live playlist in display order: by position, ties by song key. Empty for an unknown or deleted playlist. */
    fun entries(id: ListId): List<NamedEntry> {
        val rec = aliveRecord(id) ?: return emptyList()
        val out = ArrayList<NamedEntry>()
        for ((item, r) in rec.items) if (r.isLive) out.add(NamedEntry(item, r.pos?.value.orEmpty(), null))
        for (local in localItems[id].orEmpty()) out.add(NamedEntry(PlaylistItemId.Local(local.localId), local.pos, local.fingerprint))
        return out.sortedWith(compareBy({ it.pos }, { it.item.key }))
    }

    fun contains(id: ListId, item: PlaylistItemId): Boolean = entries(id).any { it.item == item }

    /** Live playlists that hold [item], for the check marks of "add to playlist". */
    fun listsContaining(item: PlaylistItemId): Set<ListId> =
        lists().map { it.id }.filter { contains(it, item) }.toSet()

    private fun nameTaken(name: String, except: ListId?): Boolean {
        val key = NamedPlaylistNames.comparisonKey(name)
        return lists.any { (id, rec) -> id != except && rec.isAlive && rec.name?.let { NamedPlaylistNames.comparisonKey(it.value) } == key }
    }

    // ---- local changes -------------------------------------------------------------------------

    private fun refuse(why: NamedRefusal) = NamedMutation(this, refusal = why)

    /** Makes the next operation, applies it and queues it. Throws [OutboxFullException] and changes nothing when the queue is full. */
    private fun issue(make: (opId: String, dot: Dot, seq: Long) -> NamedOp): Pair<NamedPlaylistState, NamedOp> {
        if (outbox.size >= MAX_OUTBOX) throw OutboxFullException()
        val counter = opCounter + 1
        val seq = lamport + 1
        val dot = Dot(deviceId, counter)
        val op = make(dot.key, dot, seq)
        val next = copy(
            opCounter = counter,
            lamport = seq,
            lists = lists.applying(op),
            outbox = outbox + NamedOutboxEntry(op, OutboxStatus.PENDING, 0),
        )
        return next to op
    }

    fun createList(rawName: String): NamedMutation {
        val name = NamedPlaylistNames.clean(rawName) ?: return refuse(NamedRefusal.BAD_NAME)
        if (listCount() >= MAX_LISTS) return refuse(NamedRefusal.LIST_LIMIT)
        if (nameTaken(name, null)) return refuse(NamedRefusal.DUPLICATE_NAME)
        val (next, op) = issue { opId, dot, seq ->
            NamedOp(opId, NamedOpType.LCREATE, ListId(deviceId, dot.counter), null, seq, setOf(dot), name, null)
        }
        return NamedMutation(next, listOf(op), listId = op.listId)
    }

    fun renameList(id: ListId, rawName: String): NamedMutation {
        val rec = aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        val name = NamedPlaylistNames.clean(rawName) ?: return refuse(NamedRefusal.BAD_NAME)
        if (rec.name?.value == name) return NamedMutation(this, listId = id)
        if (nameTaken(name, id)) return refuse(NamedRefusal.DUPLICATE_NAME)
        val (next, op) = issue { opId, _, seq -> NamedOp(opId, NamedOpType.LRENAME, id, null, seq, emptySet(), name, null) }
        return NamedMutation(next, listOf(op), listId = id)
    }

    /** Deletes only the create tags this replica has observed; a playlist made again later is a new one. */
    fun deleteList(id: ListId): NamedMutation {
        val rec = aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        val (next, op) = issue { opId, _, seq -> NamedOp(opId, NamedOpType.LDELETE, id, null, seq, rec.liveCreateDots, null, null) }
        return NamedMutation(next.copy(localItems = next.localItems - id), listOf(op), listId = id)
    }

    /** Adds [item] at the end. Already in the playlist → nothing changes and no operation is made. */
    fun addTelegram(id: ListId, item: PlaylistItemId.Telegram): NamedMutation {
        aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        if (contains(id, item)) return NamedMutation(this, listId = id)
        if (itemCount(id) >= MAX_ITEMS) return refuse(NamedRefusal.ITEM_LIMIT)
        return place(id, item, itemCount(id), isNew = true, newLocalFingerprint = null)
    }

    /** Removes only the add tags this replica has observed. Not in the playlist → nothing happens. */
    fun removeTelegram(id: ListId, item: PlaylistItemId.Telegram): NamedMutation {
        val rec = aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        val observed = rec.items[item]?.liveDots.orEmpty()
        if (observed.isEmpty()) return NamedMutation(this, listId = id)
        val (next, op) = issue { opId, _, seq -> NamedOp(opId, NamedOpType.IREMOVE, id, item, seq, observed, null, null) }
        return NamedMutation(next, listOf(op), listId = id)
    }

    /** Moves [item] so that it ends up at [index] (0 = first, clamped to the last place) in the display order. */
    fun moveItem(id: ListId, item: PlaylistItemId.Telegram, index: Int): NamedMutation {
        aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        if (!contains(id, item)) return refuse(NamedRefusal.NO_SUCH_ITEM)
        return place(id, item, index, isNew = false, newLocalFingerprint = null)
    }

    /** A song of the device itself: stored on this device only, in no operation. */
    fun addLocal(id: ListId, localId: Long, fingerprint: String): NamedMutation {
        aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        require(LocalFingerprint.isValid(fingerprint)) { "Bad fingerprint" }
        val item = PlaylistItemId.Local(localId)
        if (contains(id, item)) return NamedMutation(this, listId = id)
        if (itemCount(id) >= MAX_ITEMS) return refuse(NamedRefusal.ITEM_LIMIT)
        return place(id, item, itemCount(id), isNew = true, newLocalFingerprint = fingerprint)
    }

    fun removeLocal(id: ListId, localId: Long): NamedMutation {
        aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        val mine = localItems[id].orEmpty()
        if (mine.none { it.localId == localId }) return NamedMutation(this, listId = id)
        val left = mine.filterNot { it.localId == localId }
        return NamedMutation(copy(localItems = if (left.isEmpty()) localItems - id else localItems + (id to left)), listId = id)
    }

    fun moveLocal(id: ListId, localId: Long, index: Int): NamedMutation {
        aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        val item = PlaylistItemId.Local(localId)
        if (!contains(id, item)) return refuse(NamedRefusal.NO_SUCH_ITEM)
        return place(id, item, index, isNew = false, newLocalFingerprint = null)
    }

    /** Spreads the positions of every song of the playlist evenly, keeping the display order. Rarely needed. */
    fun rebalance(id: ListId): NamedMutation {
        aliveRecord(id) ?: return refuse(NamedRefusal.NO_SUCH_LIST)
        val order = entries(id)
        if (order.isEmpty()) return NamedMutation(this, listId = id)
        val keys = FractionalIndex.evenly(order.size)
        val placements = order.mapIndexedNotNull { i, e -> if (e.pos != keys[i]) Placement(e.item, keys[i]) else null }
        return write(id, placements, newItem = null, newLocalFingerprint = null)
    }

    private class Placement(val item: PlaylistItemId, val pos: String)

    /**
     * Puts [target] at [index] of the display order. One new position between its two neighbours is enough
     * unless the neighbours share a position or the key would get too long: then the whole playlist is respaced.
     */
    private fun place(id: ListId, target: PlaylistItemId, index: Int, isNew: Boolean, newLocalFingerprint: String?): NamedMutation {
        val all = entries(id)
        val others = all.filter { it.item != target }
        val at = index.coerceIn(0, others.size)
        if (!isNew && all.indexOfFirst { it.item == target } == at) return NamedMutation(this, listId = id)
        val before = others.getOrNull(at - 1)?.pos
        val after = others.getOrNull(at)?.pos
        val single = if (before == null || after == null || before < after) {
            FractionalIndex.between(before, after).takeIf { FractionalIndex.isValid(it) }
        } else {
            null
        }
        val newItem = if (isNew) target else null
        if (single != null) return write(id, listOf(Placement(target, single)), newItem, newLocalFingerprint)
        val order = others.map { it.item }.toMutableList().also { it.add(at, target) }
        val keys = FractionalIndex.evenly(order.size)
        val currentPos = all.associate { it.item to it.pos }
        val placements = order.mapIndexedNotNull { i, item ->
            if (item == target || currentPos[item] != keys[i]) Placement(item, keys[i]) else null
        }
        return write(id, placements, newItem, newLocalFingerprint)
    }

    private fun write(id: ListId, placements: List<Placement>, newItem: PlaylistItemId?, newLocalFingerprint: String?): NamedMutation {
        var s = this
        val ops = ArrayList<NamedOp>()
        for (p in placements) {
            when (val item = p.item) {
                is PlaylistItemId.Telegram -> {
                    val (next, op) = s.issue { opId, dot, seq ->
                        if (item == newItem) NamedOp(opId, NamedOpType.IADD, id, item, seq, setOf(dot), null, p.pos)
                        else NamedOp(opId, NamedOpType.IMOVE, id, item, seq, emptySet(), null, p.pos)
                    }
                    s = next
                    ops.add(op)
                }
                is PlaylistItemId.Local -> s = s.withLocalPosition(id, item.localId, p.pos, if (item == newItem) newLocalFingerprint else null)
                is PlaylistItemId.Legacy -> throw IllegalArgumentException("A legacy key can not be in a named playlist")
            }
        }
        return NamedMutation(s, ops, listId = id)
    }

    private fun withLocalPosition(id: ListId, localId: Long, pos: String, newFingerprint: String?): NamedPlaylistState {
        val mine = localItems[id].orEmpty()
        val old = mine.firstOrNull { it.localId == localId }
        val fingerprint = newFingerprint ?: checkNotNull(old?.fingerprint) { "Unknown local song" }
        val updated = (mine.filterNot { it.localId == localId } + LocalEntry(localId, pos, fingerprint)).canonical()
        return copy(localItems = localItems + (id to updated))
    }

    // ---- changes from elsewhere and bookkeeping -------------------------------------------------

    /** A change received from another device. Idempotent; never touches this device's outbox. Removes local songs of a deleted playlist. */
    fun applyRemote(op: NamedOp): NamedPlaylistState {
        val merged = lists.applying(op)
        val keepLocal = localItems.filterKeys { merged[it]?.isAlive == true }
        return copy(lists = merged, lamport = maxOf(lamport, op.seq), localItems = keepLocal)
    }

    fun acknowledge(opId: String): NamedPlaylistState = mapOutbox(opId) { it.copy(status = OutboxStatus.ACKNOWLEDGED) }

    fun markRetryRequired(opId: String): NamedPlaylistState =
        mapOutbox(opId) { if (it.status == OutboxStatus.ACKNOWLEDGED) it else it.copy(status = OutboxStatus.RETRY_REQUIRED, attempts = it.attempts + 1) }

    /** Undoes [markRetryRequired] for an operation whose send was DEFINITELY refused; same contract as Playlist V2. */
    fun revertRefusedDispatch(opId: String): NamedPlaylistState =
        mapOutbox(opId) {
            if (it.status == OutboxStatus.RETRY_REQUIRED) it.copy(status = OutboxStatus.PENDING, attempts = maxOf(0, it.attempts - 1)) else it
        }

    /** Drops only acknowledged entries. Pending and retry entries are never dropped. */
    fun pruneAcknowledged(): NamedPlaylistState = copy(outbox = outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED })

    private fun mapOutbox(opId: String, change: (NamedOutboxEntry) -> NamedOutboxEntry) =
        copy(outbox = outbox.map { if (it.op.opId == opId) change(it) else it })

    companion object {
        const val MAX_LISTS = 100
        const val MAX_ITEMS = 1000

        /** Operations kept waiting. Reaching it refuses new changes loudly; nothing waiting is ever dropped. */
        const val MAX_OUTBOX = 5000

        fun empty(accountId: Long, deviceId: String) = NamedPlaylistState(
            accountId = accountId,
            deviceId = deviceId,
            lamport = 0L,
            opCounter = 0L,
            lists = emptyMap(),
            localItems = emptyMap(),
            outbox = emptyList(),
            checkpoint = SyncCheckpoint(0L, 0L),
        )
    }
}

/** The one canonical order of the local songs of a playlist, so that equal states are equal values. */
internal fun List<LocalEntry>.canonical(): List<LocalEntry> = sortedWith(compareBy({ it.pos }, { it.localId }))

internal fun Map<ListId, NamedListRecord>.applying(op: NamedOp): Map<ListId, NamedListRecord> =
    this + (op.listId to (this[op.listId] ?: NamedListRecord.EMPTY).apply(op))
