package com.bsdung.musicassistantcar.data.telegram

import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.Random

/** Stands for "the process was killed here". An Error on purpose: the coordinator catches Exceptions only, as a real crash would not be caught. */
internal class SimulatedCrash(message: String = "power lost") : Error(message)

/** A file layer that can lose power: after [writesAllowed] writes to files whose path contains [only], writes throw. */
internal class SyncFaultyIo(private val only: String) : PlaylistFileIo {
    var writesAllowed = Int.MAX_VALUE
    var writes = 0
    override fun read(file: File): ByteArray = TestPlainFileIo.read(file)
    override fun write(file: File, bytes: ByteArray) {
        if (file.path.contains(only)) {
            if (writes >= writesAllowed) throw IOException("power lost")
            writes++
        }
        TestPlainFileIo.write(file, bytes)
    }

    fun failNextWrite() {
        writesAllowed = writes
    }

    fun heal() {
        writesAllowed = Int.MAX_VALUE
    }
}

internal class TransportHooks {
    var beforeFetch: (() -> Unit)? = null
    var beforeSend: (() -> Unit)? = null
    /** Runs when the server has stored a message but has not answered yet. */
    var afterStoreBeforeReply: (() -> Unit)? = null
}

/**
 * The shared in-memory "server" of the private channel. The SERVER gives message ids (increasing, with
 * gaps). It can lose answers, fail, shuffle, repeat, fragment, hide a message for a while, and be fed damaged text.
 */
internal class FakeTelegramChannel(seed: Long = 7L, val channelId: Long = -1_001_234_567_890L) {
    private class Stored(val id: Long, val text: String, var hiddenFetches: Int)

    enum class SendFault { NETWORK, LOST_ACK, TIMEOUT_NOT_STORED, FLOOD_35, UNAUTHORIZED }

    private val random = Random(seed)
    private val stored = ArrayList<Stored>()
    private var nextId = 5_000L

    var deleted = false
    var unauthorized = false
    var revoked = false
    var shufflePages = false
    var duplicateInPage = false
    var maxPageSize = Int.MAX_VALUE
    var sendCalls = 0
    /** A message stored by a send whose answer was lost stays invisible to this many fetches (so a retry cannot see it yet). */
    var lostAckHiddenFetches = 0
    var fetchCalls = 0
    var lastAfterId = -1L
    val sendFaults = ArrayDeque<SendFault>()
    val fetchFaults = ArrayDeque<FetchResult.Failed>()

    private fun allocateId(): Long {
        nextId += 1 + random.nextInt(4)
        return nextId
    }

    fun texts(): List<String> = stored.sortedBy { it.id }.map { it.text }
    fun count(): Int = stored.size
    fun maxId(): Long = stored.maxOfOrNull { it.id } ?: 0L

    /** Puts any text in the channel as if somebody posted it (damaged data, other accounts, free text). */
    fun inject(text: String, hiddenFetches: Int = 0): Long {
        val id = allocateId()
        stored.add(Stored(id, text, hiddenFetches))
        return id
    }

    /** Makes a hidden message visible to the next fetch. */
    fun reveal(id: Long) {
        stored.first { it.id == id }.hiddenFetches = 0
    }

    fun send(text: String, afterStoreBeforeReply: () -> Unit): SendResult {
        sendCalls++
        if (deleted) return SendResult.Failed(SyncErrorKind.CHANNEL_MISSING)
        if (revoked) return SendResult.Failed(SyncErrorKind.SESSION_REVOKED)
        if (unauthorized) return SendResult.Failed(SyncErrorKind.UNAUTHORIZED)
        when (sendFaults.poll()) {
            null -> Unit
            SendFault.NETWORK -> return SendResult.Failed(SyncErrorKind.NETWORK)
            SendFault.FLOOD_35 -> return SendResult.Failed(SyncErrorKind.FLOOD_WAIT, 35)
            SendFault.UNAUTHORIZED -> return SendResult.Failed(SyncErrorKind.UNAUTHORIZED)
            SendFault.TIMEOUT_NOT_STORED -> return SendResult.Unknown
            SendFault.LOST_ACK -> {
                inject(text, lostAckHiddenFetches)
                afterStoreBeforeReply()
                return SendResult.Unknown
            }
        }
        val id = inject(text)
        afterStoreBeforeReply()
        return SendResult.Sent(id)
    }

    fun fetchAfter(afterId: Long, limit: Int): FetchResult {
        fetchCalls++
        lastAfterId = afterId
        fetchFaults.poll()?.let { return it }
        if (deleted) return FetchResult.Failed(SyncErrorKind.CHANNEL_MISSING)
        if (revoked) return FetchResult.Failed(SyncErrorKind.SESSION_REVOKED)
        if (unauthorized) return FetchResult.Failed(SyncErrorKind.UNAUTHORIZED)
        stored.forEach { if (it.hiddenFetches > 0) it.hiddenFetches-- }
        val visible = stored.filter { it.id > afterId && it.hiddenFetches == 0 }.sortedBy { it.id }
        val cap = minOf(limit, maxPageSize)
        var page = visible.take(cap).map { ServerMessage(it.id, it.text) }
        val hasMore = visible.size > cap
        if (duplicateInPage && page.isNotEmpty()) page = page + page.first()
        if (shufflePages) page = page.shuffled(random)
        return FetchResult.Page(page, hasMore)
    }

    fun transport(hooks: TransportHooks = TransportHooks()): PlaylistSyncTransport = object : PlaylistSyncTransport {
        override fun send(channelId: Long, text: String): SendResult {
            hooks.beforeSend?.invoke()
            return this@FakeTelegramChannel.send(text) { hooks.afterStoreBeforeReply?.invoke() }
        }

        override fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int): FetchResult {
            hooks.beforeFetch?.invoke()
            return this@FakeTelegramChannel.fetchAfter(afterServerId, limit)
        }
    }
}

/**
 * One independent device: its own folder, Telegram session (account), V1+V2 stores with their own keys, outbox,
 * counters and clock. [restart] throws away every object and reopens the same files, i.e. the process was killed.
 */
internal class FakeSyncDevice(
    val name: String,
    val root: File,
    val account: TelegramUserId,
    val channel: FakeTelegramChannel,
    private val seed: Int,
    private val config: SyncConfig = SyncConfig(),
    private val index: PlaylistIndex? = null,
    bindOnFirstBoot: Boolean = true,
) {
    val v1Io = SyncFaultyIo("tg_playlists_v1")
    val v2Io = SyncFaultyIo("tg_playlists_v2")
    val hooks = TransportHooks()
    var nowMs = 0L

    lateinit var session: TelegramPlaylistV2Session
        private set
    lateinit var coordinator: PlaylistSyncCoordinator
        private set
    private val v2Store get() = TelegramPlaylistStoreV2(root, TestAesGcmCipher(seed + 100), v2Io)

    init {
        boot(bindOnFirstBoot)
    }

    fun boot(bindChannel: Boolean = true) {
        val v1 = TelegramSavedPlaylistStore(root, TestAesGcmCipher(seed), v1Io)
        session = TelegramPlaylistV2Session(v1, TelegramPlaylistStoreV2(root, TestAesGcmCipher(seed + 100), v2Io)) { name }
        index?.let { session.attachIndex(it) }
        check(session.open(account) is TelegramPlaylistSession.OpenResult.Ready) { "$name could not open" }
        if (bindChannel) bindChannel(channel.channelId)
        coordinator = PlaylistSyncCoordinator(session, channel.transport(hooks), { nowMs }, config)
    }

    /** Test stand-in for what P3's channel discovery will do. */
    fun bindChannel(channelId: Long) {
        val view = session.view(account)!!
        if (view.state.checkpoint.channelId == 0L) {
            check(session.update(account, view.epoch) { it.copy(checkpoint = it.checkpoint.copy(channelId = channelId)) } is UpdateResult.Committed)
        }
    }

    fun restart(bindChannel: Boolean = true) = boot(bindChannel)

    /** Removes the V1 copy, so a restored older V2 file is not mixed with it. */
    fun dropV1Copy() {
        File(root, "tg_playlists_v1").deleteRecursively()
    }

    private fun target(chat: Long, message: Long) =
        PlaylistTarget(TelegramSongKey.forMessage(chat, message, account.value), chat, message)

    /** Save or unsave (it is a toggle, as in the app). */
    fun toggle(chat: Long, message: Long): TelegramPlaylistSession.ToggleOutcome = session.toggle(account, target(chat, message))

    fun sync(mode: SyncMode = SyncMode.INCREMENTAL): SyncReport = coordinator.runOnce(account, mode)

    fun present(): List<Long> = session.snapshot()

    /** What is stored on disk right now. */
    fun stored(): PlaylistState = (v2Store.load(account) as V2LoadResult.Valid).state

    fun storedFile(): File = File(File(root, "tg_playlists_v2"), TelegramAccountStoragePolicy.directoryName(account) + ".bin")

    fun pendingIds(): List<String> = stored().outbox.filter { it.status != OutboxStatus.ACKNOWLEDGED }.map { it.op.opId }

    /** The part of the state that must be identical on every device that has seen the same log. */
    fun crdt(): Pair<Map<PlaylistItemId.Telegram, Map<Dot, Long>>, Map<PlaylistItemId.Telegram, Set<Dot>>> =
        stored().orSet.let { it.allAdds() to it.allRemoved().filterValues { dots -> dots.isNotEmpty() } }
}

/** Builds the text of a message from operations written by [sender], for tests that feed the channel by hand. */
internal fun envelopeText(accountId: Long, sender: String, vararg ops: PlaylistOp): String =
    PlaylistSyncProtocol.encode(
        PlaylistSyncProtocol.Envelope(PlaylistSyncProtocol.scopeOf(accountId), sender, ops.first().opId.replace('#', '-'), ops.toList())
    )

internal fun addOp(device: String, counter: Long, chat: Long, message: Long, seq: Long = counter): PlaylistOp {
    val dot = Dot(device, counter)
    return PlaylistOp(dot.key, OpType.ADD, PlaylistItemId.Telegram(chat, message), setOf(dot), seq)
}

internal fun removeOp(device: String, counter: Long, chat: Long, message: Long, observed: Set<Dot>, seq: Long = counter): PlaylistOp =
    PlaylistOp(Dot(device, counter).key, OpType.REMOVE, PlaylistItemId.Telegram(chat, message), observed, seq)
