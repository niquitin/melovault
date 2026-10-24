package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P1: the pure data model of the Playlist V2 (no Android, no Telegram, no clock, no I/O).
 *
 * The saved list is an Observed-Remove Set. Every ADD carries a unique tag (a [Dot]); a REMOVE only
 * removes the tags its device had *observed*. Whether a song is present never depends on a device
 * clock: it is "at least one add tag that no remove has covered". Display order uses a logical
 * (Lamport-style) counter, never wall time. Merging two replicas is a plain union, so it is
 * commutative, associative and idempotent.
 */

internal object PlaylistIds {
    private val DEVICE = Regex("^[A-Za-z0-9-]{1,64}$")
    fun isDeviceId(value: String): Boolean = DEVICE.matches(value)
}

/** A unique add tag: the n-th operation of one device. */
internal data class Dot(val device: String, val counter: Long) : Comparable<Dot> {
    init {
        require(PlaylistIds.isDeviceId(device)) { "Bad device id" }
        require(counter > 0L) { "Dot counter must be positive" }
    }

    val key: String get() = "$device#$counter"

    override fun compareTo(other: Dot): Int {
        val byDevice = device.compareTo(other.device)
        return if (byDevice != 0) byDevice else counter.compareTo(other.counter)
    }

    companion object {
        fun parse(text: String): Dot? {
            val cut = text.lastIndexOf('#')
            if (cut <= 0) return null
            val counter = text.substring(cut + 1).toLongOrNull() ?: return null
            val device = text.substring(0, cut)
            if (counter <= 0L || !PlaylistIds.isDeviceId(device)) return null
            return Dot(device, counter)
        }
    }
}

/** What a playlist entry identifies. Account scope lives on the state, not here. */
internal sealed class PlaylistItemId {
    abstract val key: String

    /** A song of a Telegram chat: (chatId, rawMessageId). Together with the account it is the canonical identity. */
    data class Telegram(val chatId: Long, val messageId: Long) : PlaylistItemId() {
        init {
            require(chatId != 0L) { "chatId must not be 0" }
            require(messageId > 0L) { "messageId must be positive" }
        }

        override val key: String get() = "tg:$chatId:$messageId"

        /** The UI/SQLite/V1 key of this song for an account whose Saved Messages chat is [selfChatId]. */
        fun songKey(selfChatId: Long): Long = TelegramSongKey.forMessage(chatId, messageId, selfChatId)
    }

    /** A V1 key whose chat and message could not be verified. Kept as is, never guessed, never synced. */
    data class Legacy(val songKey: Long) : PlaylistItemId() {
        init { require(songKey > 0L) { "songKey must be positive" } }
        override val key: String get() = "legacy:$songKey"
    }

    /** Music from the device itself. It has no cross-device identity: never stored in a playlist, never synced. */
    data class Local(val localId: Long) : PlaylistItemId() {
        override val key: String get() = "local:$localId"
    }

    val isSyncable: Boolean get() = this is Telegram

    companion object {
        fun parse(text: String): PlaylistItemId? {
            val parts = text.split(':')
            return try {
                when (parts.firstOrNull()) {
                    "tg" -> if (parts.size == 3) Telegram(parts[1].toLong(), parts[2].toLong()) else null
                    "legacy" -> if (parts.size == 2) Legacy(parts[1].toLong()) else null
                    "local" -> if (parts.size == 2) Local(parts[1].toLong()) else null
                    else -> null
                }
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

internal enum class OpType { ADD, REMOVE }

/**
 * One replicated change. [dots] is the single new tag of an ADD, or the tags a REMOVE observed.
 * [opId] is unique and never changes, also when the operation is sent again.
 */
internal data class PlaylistOp(
    val opId: String,
    val type: OpType,
    val item: PlaylistItemId.Telegram,
    val dots: Set<Dot>,
    val seq: Long,
) {
    init {
        require(Dot.parse(opId) != null) { "Bad op id" }
        require(dots.isNotEmpty()) { "An operation needs at least one tag" }
        require(type != OpType.ADD || dots.size == 1) { "An ADD carries exactly one tag" }
        require(seq > 0L) { "seq must be positive" }
    }
}

/** The observed-remove set of Telegram songs. Immutable; every change returns a new value. */
internal class PlaylistOrSet private constructor(
    private val adds: Map<PlaylistItemId.Telegram, Map<Dot, Long>>,
    private val removed: Map<PlaylistItemId.Telegram, Set<Dot>>,
) {
    fun apply(op: PlaylistOp): PlaylistOrSet = when (op.type) {
        OpType.ADD -> {
            val dot = op.dots.single()
            val current = adds[op.item].orEmpty()
            val seq = current[dot]?.let { minOf(it, op.seq) } ?: op.seq
            PlaylistOrSet(adds + (op.item to (current + (dot to seq))), removed)
        }
        OpType.REMOVE ->
            PlaylistOrSet(adds, removed + (op.item to (removed[op.item].orEmpty() + op.dots)))
    }

    fun merge(other: PlaylistOrSet): PlaylistOrSet {
        val mergedAdds = HashMap<PlaylistItemId.Telegram, Map<Dot, Long>>(adds)
        for ((item, dots) in other.adds) {
            val mine = mergedAdds[item].orEmpty()
            val union = HashMap<Dot, Long>(mine)
            for ((dot, seq) in dots) union[dot] = union[dot]?.let { minOf(it, seq) } ?: seq
            mergedAdds[item] = union
        }
        val mergedRemoved = HashMap<PlaylistItemId.Telegram, Set<Dot>>(removed)
        for ((item, dots) in other.removed) mergedRemoved[item] = mergedRemoved[item].orEmpty() + dots
        return PlaylistOrSet(mergedAdds, mergedRemoved)
    }

    /** Add tags of [item] that no remove has covered, with their logical position. */
    fun liveDots(item: PlaylistItemId.Telegram): Map<Dot, Long> {
        val covered = removed[item].orEmpty()
        return adds[item].orEmpty().filterKeys { it !in covered }
    }

    fun contains(item: PlaylistItemId.Telegram): Boolean = liveDots(item).isNotEmpty()

    /** Present songs in display order: earliest live logical position first, ties by tag. No clock involved. */
    fun ordered(): List<Pair<PlaylistItemId.Telegram, Long>> =
        adds.keys.mapNotNull { item ->
            val live = liveDots(item)
            if (live.isEmpty()) null
            else Triple(item, live.values.min(), live.filterValues { it == live.values.min() }.keys.min())
        }.sortedWith(compareBy<Triple<PlaylistItemId.Telegram, Long, Dot>>({ it.second }, { it.third }, { it.first.key }))
            .map { it.first to it.second }

    /** Every song that was ever added here, present or removed. */
    fun knownItems(): Set<PlaylistItemId.Telegram> = adds.keys

    fun wasRemoved(item: PlaylistItemId.Telegram): Boolean = removed[item].orEmpty().isNotEmpty()

    fun allAdds(): Map<PlaylistItemId.Telegram, Map<Dot, Long>> = adds
    fun allRemoved(): Map<PlaylistItemId.Telegram, Set<Dot>> = removed

    override fun equals(other: Any?): Boolean =
        other is PlaylistOrSet && adds == other.adds && normalizedRemoved() == other.normalizedRemoved()

    override fun hashCode(): Int = 31 * adds.hashCode() + normalizedRemoved().hashCode()

    private fun normalizedRemoved() = removed.filterValues { it.isNotEmpty() }

    companion object {
        val EMPTY = PlaylistOrSet(emptyMap(), emptyMap())

        fun of(
            adds: Map<PlaylistItemId.Telegram, Map<Dot, Long>>,
            removed: Map<PlaylistItemId.Telegram, Set<Dot>>,
        ) = PlaylistOrSet(adds, removed)
    }
}

internal enum class OutboxStatus { PENDING, ACKNOWLEDGED, RETRY_REQUIRED }

/** A change waiting to be told to other devices. It leaves the outbox only once acknowledged and pruned. */
internal data class OutboxEntry(val op: PlaylistOp, val status: OutboxStatus, val attempts: Int)

internal enum class MigrationStatus { NOT_STARTED, IN_PROGRESS, COMPLETE }

internal data class MigrationRecord(val status: MigrationStatus, val v1Digest: String, val v1Count: Int)

internal enum class ProjectionStatus { IN_SYNC, STALE, DIVERGED_ACK }

/**
 * Bookkeeping for the V1 file kept for older APKs. [pendingDigest] is the content about to be (or just)
 * written to V1; [lastDigest] the content last known to be there. Both are digests, never the list.
 */
internal data class ProjectionRecord(
    val lastDigest: String,
    val pendingDigest: String,
    val status: ProjectionStatus,
    val truncated: Boolean,
    val conflictImported: Int,
    val conflictSkipped: Int,
    val conflictIgnoredRemovals: Int,
)

/** Reserved for the sync phases (P2/P3). Nothing in P1 reads or writes it. */
/**
 * [lastAppliedServerId]: every message up to it has been applied (or deliberately skipped); only ever grows.
 * [replayPosition]: where a FULL_REPLAY in progress stands, or [NO_REPLAY]. A replay longer than one run
 * resumes from it, also after a restart. In the file it is written ONLY while a replay is in progress, so
 * a state without one is byte-for-byte what the previous build wrote.
 */
internal data class SyncCheckpoint(
    val channelId: Long,
    val lastAppliedServerId: Long,
    val replayPosition: Long = NO_REPLAY,
) {
    companion object {
        const val NO_REPLAY = -1L
    }
}

/** An unverified V1 key kept in saved order. Local to this device: no operation, no outbox entry. */
internal data class LegacyEntry(val songKey: Long, val seq: Long) {
    init {
        require(songKey > 0L)
        require(seq > 0L)
    }
}

internal class OutboxFullException : RuntimeException("Playlist outbox is full")

internal data class Mutation(val state: PlaylistState, val op: PlaylistOp?)

/** One device's complete Playlist V2 replica. Immutable. */
internal data class PlaylistState(
    val accountId: Long,
    val deviceId: String,
    val lamport: Long,
    val opCounter: Long,
    val orSet: PlaylistOrSet,
    val legacy: List<LegacyEntry>,
    val legacyRemoved: Set<Long>,
    val outbox: List<OutboxEntry>,
    val migration: MigrationRecord,
    val projection: ProjectionRecord,
    val checkpoint: SyncCheckpoint,
) {
    init {
        require(accountId > 0L)
        require(PlaylistIds.isDeviceId(deviceId))
    }

    /** Adds [item]. Already present → nothing changes and no operation is made (no duplicate). */
    fun addTelegram(item: PlaylistItemId.Telegram, seqOverride: Long? = null): Mutation {
        if (orSet.contains(item)) return Mutation(this, null)
        if (outbox.size >= MAX_OUTBOX) throw OutboxFullException()
        val counter = opCounter + 1
        val seq = seqOverride ?: (lamport + 1)
        val dot = Dot(deviceId, counter)
        val op = PlaylistOp(dot.key, OpType.ADD, item, setOf(dot), seq)
        return Mutation(
            copy(
                opCounter = counter,
                lamport = maxOf(lamport, seq),
                orSet = orSet.apply(op),
                outbox = outbox + OutboxEntry(op, OutboxStatus.PENDING, 0),
            ),
            op
        )
    }

    /** Removes only the add tags this replica has observed. Not present → nothing happens. */
    fun removeTelegram(item: PlaylistItemId.Telegram): Mutation {
        val observed = orSet.liveDots(item).keys
        if (observed.isEmpty()) return Mutation(this, null)
        if (outbox.size >= MAX_OUTBOX) throw OutboxFullException()
        val counter = opCounter + 1
        val seq = lamport + 1
        val op = PlaylistOp(Dot(deviceId, counter).key, OpType.REMOVE, item, observed, seq)
        return Mutation(
            copy(
                opCounter = counter,
                lamport = seq,
                orSet = orSet.apply(op),
                outbox = outbox + OutboxEntry(op, OutboxStatus.PENDING, 0),
            ),
            op
        )
    }

    fun addLegacy(songKey: Long, seqOverride: Long? = null): PlaylistState {
        if (legacy.any { it.songKey == songKey }) return this
        val seq = seqOverride ?: (lamport + 1)
        return copy(lamport = maxOf(lamport, seq), legacy = legacy + LegacyEntry(songKey, seq), legacyRemoved = legacyRemoved - songKey)
    }

    fun removeLegacy(songKey: Long): PlaylistState {
        if (legacy.none { it.songKey == songKey }) return this
        return copy(legacy = legacy.filterNot { it.songKey == songKey }, legacyRemoved = legacyRemoved + songKey)
    }

    /** Replaces a verified legacy key by the real song at the same position. Not a user removal: no tombstone. */
    fun upgradeLegacy(entry: LegacyEntry, item: PlaylistItemId.Telegram): Mutation {
        val without = copy(legacy = legacy.filterNot { it.songKey == entry.songKey })
        return without.addTelegram(item, seqOverride = entry.seq)
    }

    /** A change received from another device (P2/P3). Idempotent; never touches this device's outbox. */
    fun applyRemote(op: PlaylistOp): PlaylistState =
        copy(orSet = orSet.apply(op), lamport = maxOf(lamport, op.seq))

    fun acknowledge(opId: String): PlaylistState = mapOutbox(opId) { it.copy(status = OutboxStatus.ACKNOWLEDGED) }

    fun markRetryRequired(opId: String): PlaylistState =
        mapOutbox(opId) { if (it.status == OutboxStatus.ACKNOWLEDGED) it else it.copy(status = OutboxStatus.RETRY_REQUIRED, attempts = it.attempts + 1) }

    /**
     * Undoes the write-ahead flag of [markRetryRequired] for an operation whose send was DEFINITELY refused (nothing was
     * stored, or nothing was even handed over): it goes back to exactly what it was before (PENDING, one attempt less).
     * Only the sync coordinator calls this, only for operations it flagged itself a moment earlier in the same step.
     */
    fun revertRefusedDispatch(opId: String): PlaylistState =
        mapOutbox(opId) {
            if (it.status == OutboxStatus.RETRY_REQUIRED) it.copy(status = OutboxStatus.PENDING, attempts = maxOf(0, it.attempts - 1)) else it
        }

    /** Drops only acknowledged entries. Pending and retry entries are never dropped. */
    fun pruneAcknowledged(): PlaylistState = copy(outbox = outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED })

    private fun mapOutbox(opId: String, change: (OutboxEntry) -> OutboxEntry) =
        copy(outbox = outbox.map { if (it.op.opId == opId) change(it) else it })

    /** Saved songs in display order as the UI/V1 key space (songKey). Telegram items and legacy keys interleave by position. */
    fun presentSongKeys(selfChatId: Long): List<Long> {
        data class Slot(val seq: Long, val rank: Int, val tie: String, val songKey: Long)
        val slots = ArrayList<Slot>()
        for ((item, seq) in orSet.ordered()) {
            val tie = orSet.liveDots(item).filterValues { it == seq }.keys.min().key
            slots.add(Slot(seq, 0, tie, item.songKey(selfChatId)))
        }
        for (entry in legacy) slots.add(Slot(entry.seq, 1, entry.songKey.toString(), entry.songKey))
        return slots.sortedWith(compareBy({ it.seq }, { it.rank }, { it.tie })).map { it.songKey }.distinct()
    }

    /** The live Telegram item whose UI key is [songKey], if any. */
    fun telegramItemFor(songKey: Long, selfChatId: Long): PlaylistItemId.Telegram? =
        orSet.ordered().map { it.first }.firstOrNull { it.songKey(selfChatId) == songKey }

    fun isLegacy(songKey: Long): Boolean = legacy.any { it.songKey == songKey }

    companion object {
        /** Operations kept waiting. Reaching it refuses new changes loudly; nothing waiting is ever dropped. */
        const val MAX_OUTBOX = 5000

        val NO_MIGRATION = MigrationRecord(MigrationStatus.NOT_STARTED, "-", 0)
        val NO_PROJECTION = ProjectionRecord("-", "-", ProjectionStatus.IN_SYNC, false, 0, 0, 0)

        fun empty(accountId: Long, deviceId: String) = PlaylistState(
            accountId = accountId,
            deviceId = deviceId,
            lamport = 0L,
            opCounter = 0L,
            orSet = PlaylistOrSet.EMPTY,
            legacy = emptyList(),
            legacyRemoved = emptySet(),
            outbox = emptyList(),
            migration = NO_MIGRATION,
            projection = NO_PROJECTION,
            checkpoint = SyncCheckpoint(0L, 0L),
        )
    }
}
