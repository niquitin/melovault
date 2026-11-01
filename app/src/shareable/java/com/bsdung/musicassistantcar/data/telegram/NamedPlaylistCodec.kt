package com.bsdung.musicassistantcar.data.telegram

import java.nio.ByteBuffer
import java.util.Base64

/**
 * Nhiều Playlist có tên, S3: text serialization of a [NamedPlaylistState] (the plaintext of the encrypted file).
 *
 * Same discipline as [TelegramPlaylistCodec]: deterministic (same state → same bytes), strict (anything unexpected
 * is rejected, never skipped) and self-checking (a SHA-256 line over the body on top of the AES-GCM tag). Names are
 * stored as base64url of their UTF-8 bytes so that no character of a name can break the line format.
 */
internal object NamedPlaylistCodec {
    const val SCHEMA_VERSION = 1
    private const val MAGIC = "MVNL1"
    private const val TAB = '\t'
    private const val NONE = "-"

    sealed class Decoded {
        data class Ok(val state: NamedPlaylistState) : Decoded()
        data class Invalid(val reason: PlaylistUnreadableReason) : Decoded()
    }

    fun encodeName(name: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(name.toByteArray(Charsets.UTF_8))

    /** The name behind [text], or null unless [text] is exactly what [encodeName] makes of a valid UTF-8 name. */
    fun decodeName(text: String): String? = try {
        val bytes = Base64.getUrlDecoder().decode(text)
        if (Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) != text) {
            null
        } else {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        }
    } catch (_: Exception) {
        null
    }

    fun encode(state: NamedPlaylistState): ByteArray {
        val body = StringBuilder()
        fun line(vararg fields: Any) = body.append(fields.joinToString(TAB.toString())).append('\n')
        line(MAGIC)
        line("schema", SCHEMA_VERSION)
        line("account", state.accountId)
        line("device", state.deviceId)
        line("lamport", state.lamport)
        line("opcounter", state.opCounter)
        val cp = state.checkpoint
        if (cp.replayPosition == SyncCheckpoint.NO_REPLAY) line("checkpoint", cp.channelId, cp.lastAppliedServerId)
        else line("checkpoint", cp.channelId, cp.lastAppliedServerId, cp.replayPosition)
        for ((id, rec) in state.lists.entries.sortedBy { it.key }) {
            for ((dot, seq) in rec.created.entries.sortedBy { it.key }) line("LC", id.key, dot.key, seq)
            for (dot in rec.deleted.sorted()) line("LD", id.key, dot.key)
            rec.name?.let { line("LN", id.key, it.seq, it.op.key, encodeName(it.value)) }
            for ((item, r) in rec.items.entries.sortedBy { it.key.key }) {
                for (dot in r.adds.sorted()) line("IA", id.key, item.key, dot.key)
                for (dot in r.removed.sorted()) line("IR", id.key, item.key, dot.key)
                r.pos?.let { line("IP", id.key, item.key, it.seq, it.op.key, it.value) }
            }
        }
        for ((id, entries) in state.localItems.entries.sortedBy { it.key }) {
            for (e in entries.canonical()) line("LO", id.key, e.localId, e.pos, e.fingerprint)
        }
        for (entry in state.outbox) {
            val op = entry.op
            line(
                "O", op.opId, op.type, op.listId.key, op.item?.key ?: NONE, op.seq,
                if (op.dots.isEmpty()) NONE else op.dots.sorted().joinToString(",") { it.key },
                op.name?.let { encodeName(it) } ?: NONE, op.pos ?: NONE, entry.status, entry.attempts
            )
        }
        val text = body.toString()
        return (text + "END" + TAB + TelegramPlaylistCodec.sha256Hex(text) + "\n").toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray, expectedAccount: Long): Decoded {
        val text = try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            return Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        }
        val endAt = text.lastIndexOf("END$TAB")
        if (endAt <= 0 || !text.endsWith("\n")) return Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        val body = text.substring(0, endAt)
        val check = text.substring(endAt + 4).trimEnd('\n')
        if (check != TelegramPlaylistCodec.sha256Hex(body)) return Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        return try {
            parse(body.split('\n').dropLast(1), expectedAccount)
        } catch (_: ParseFailure) {
            Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        } catch (_: IllegalArgumentException) {
            Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        }
    }

    private class ParseFailure : RuntimeException()

    private fun fail(): Nothing = throw ParseFailure()

    private class ListBuilder {
        val created = HashMap<Dot, Long>()
        val deleted = HashSet<Dot>()
        var name: Reg<String>? = null
        val adds = HashMap<PlaylistItemId.Telegram, MutableSet<Dot>>()
        val removed = HashMap<PlaylistItemId.Telegram, MutableSet<Dot>>()
        val pos = HashMap<PlaylistItemId.Telegram, Reg<String>>()

        fun build(): NamedListRecord {
            val items = (adds.keys + removed.keys + pos.keys).associateWith { item ->
                NamedItemRecord(adds[item].orEmpty().toSet(), removed[item].orEmpty().toSet(), pos[item])
            }
            return NamedListRecord(created.toMap(), deleted.toSet(), name, items)
        }
    }

    private fun parse(lines: List<String>, expectedAccount: Long): Decoded {
        if (lines.firstOrNull() != MAGIC) return Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        var schema: Int? = null
        var account: Long? = null
        var device: String? = null
        var lamport: Long? = null
        var opCounter: Long? = null
        var checkpoint: SyncCheckpoint? = null
        val lists = HashMap<ListId, ListBuilder>()
        val local = HashMap<ListId, MutableList<LocalEntry>>()
        val outbox = ArrayList<NamedOutboxEntry>()
        val seenOps = HashSet<String>()

        fun once(current: Any?): Unit = if (current != null) fail() else Unit
        fun listId(key: String) = ListId.parse(key) ?: fail()
        fun builder(key: String) = lists.getOrPut(listId(key)) { ListBuilder() }
        fun telegram(key: String) = PlaylistItemId.parse(key) as? PlaylistItemId.Telegram ?: fail()
        fun dot(key: String) = Dot.parse(key) ?: fail()
        fun name(text: String) = decodeName(text) ?: fail()
        fun optional(text: String) = if (text == NONE) null else text

        for (raw in lines.drop(1)) {
            val f = raw.split(TAB)
            when (f[0]) {
                "schema" -> { once(schema); schema = f.getOrNull(1)?.toIntOrNull() ?: fail() }
                "account" -> { once(account); account = f.getOrNull(1)?.toLongOrNull() ?: fail() }
                "device" -> { once(device); device = f.getOrNull(1) ?: fail() }
                "lamport" -> { once(lamport); lamport = f.getOrNull(1)?.toLongOrNull() ?: fail() }
                "opcounter" -> { once(opCounter); opCounter = f.getOrNull(1)?.toLongOrNull() ?: fail() }
                "checkpoint" -> {
                    once(checkpoint)
                    // The replay position is present only while a FULL_REPLAY is in progress.
                    if (f.size != 3 && f.size != 4) fail()
                    val replay = if (f.size == 4) f[3].toLong().also { if (it < 0L) fail() } else SyncCheckpoint.NO_REPLAY
                    checkpoint = SyncCheckpoint(f[1].toLong(), f[2].toLong(), replay)
                }
                "LC" -> {
                    if (f.size != 4) fail()
                    if (builder(f[1]).created.put(dot(f[2]), f[3].toLong()) != null) fail()
                }
                "LD" -> {
                    if (f.size != 3) fail()
                    if (!builder(f[1]).deleted.add(dot(f[2]))) fail()
                }
                "LN" -> {
                    if (f.size != 5) fail()
                    val b = builder(f[1])
                    once(b.name)
                    val value = name(f[4])
                    if (!NamedPlaylistNames.isStored(value)) fail()
                    b.name = Reg(value, f[2].toLong(), dot(f[3]))
                }
                "IA" -> {
                    if (f.size != 4) fail()
                    if (!builder(f[1]).adds.getOrPut(telegram(f[2])) { HashSet() }.add(dot(f[3]))) fail()
                }
                "IR" -> {
                    if (f.size != 4) fail()
                    if (!builder(f[1]).removed.getOrPut(telegram(f[2])) { HashSet() }.add(dot(f[3]))) fail()
                }
                "IP" -> {
                    if (f.size != 6) fail()
                    if (!FractionalIndex.isValid(f[5])) fail()
                    if (builder(f[1]).pos.put(telegram(f[2]), Reg(f[5], f[3].toLong(), dot(f[4]))) != null) fail()
                }
                "LO" -> {
                    if (f.size != 5) fail()
                    val entry = LocalEntry(f[2].toLong(), f[3], f[4])
                    val mine = local.getOrPut(listId(f[1])) { ArrayList() }
                    if (mine.any { it.localId == entry.localId }) fail()
                    mine.add(entry)
                }
                "O" -> {
                    if (f.size != 11) fail()
                    if (!seenOps.add(f[1])) fail()
                    val dots = optional(f[6])?.split(',')?.map { dot(it) }?.toSet().orEmpty()
                    val op = NamedOp(
                        opId = f[1],
                        type = NamedOpType.valueOf(f[2]),
                        listId = listId(f[3]),
                        item = optional(f[4])?.let { telegram(it) },
                        seq = f[5].toLong(),
                        dots = dots,
                        name = optional(f[7])?.let { name(it) },
                        pos = optional(f[8]),
                    )
                    outbox.add(NamedOutboxEntry(op, OutboxStatus.valueOf(f[9]), f[10].toInt().also { if (it < 0) fail() }))
                }
                else -> fail()
            }
        }
        // A newer schema is never read as if it were ours, and so can never be overwritten by us.
        val readSchema = schema ?: fail()
        if (readSchema > SCHEMA_VERSION) return Decoded.Invalid(PlaylistUnreadableReason.SCHEMA)
        if (readSchema != SCHEMA_VERSION) fail()
        if (account != expectedAccount) return Decoded.Invalid(PlaylistUnreadableReason.ACCOUNT)
        val records = lists.mapValues { it.value.build() }
        // Songs of the device only belong to a playlist that exists.
        if (local.keys.any { records[it]?.isAlive != true }) fail()
        val state = NamedPlaylistState(
            accountId = account ?: fail(),
            deviceId = device ?: fail(),
            lamport = lamport ?: fail(),
            opCounter = opCounter ?: fail(),
            lists = records,
            localItems = local.mapValues { it.value.canonical() },
            outbox = outbox,
            checkpoint = checkpoint ?: fail(),
        )
        return Decoded.Ok(state)
    }
}
