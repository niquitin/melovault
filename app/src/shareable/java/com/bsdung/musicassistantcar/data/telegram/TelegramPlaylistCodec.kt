package com.bsdung.musicassistantcar.data.telegram

import java.security.MessageDigest

/**
 * E96 P1: text serialization of a [PlaylistState] (the plaintext of the encrypted V2 file).
 *
 * Deterministic (same state → same bytes), strict (anything unexpected is rejected, never skipped) and
 * self-checking (a SHA-256 line over the body, on top of the AES-GCM tag that already covers the file).
 * Plain text without a JSON library so that JVM tests run on the same code as the device.
 */
internal object TelegramPlaylistCodec {
    const val SCHEMA_VERSION = 2
    private const val MAGIC = "MVPL2"
    private const val TAB = '\t'

    sealed class Decoded {
        data class Ok(val state: PlaylistState) : Decoded()
        data class Invalid(val reason: PlaylistUnreadableReason) : Decoded()
    }

    fun encode(state: PlaylistState): ByteArray {
        val body = StringBuilder()
        fun line(vararg fields: Any) = body.append(fields.joinToString(TAB.toString())).append('\n')
        line(MAGIC)
        line("schema", SCHEMA_VERSION)
        line("account", state.accountId)
        line("device", state.deviceId)
        line("lamport", state.lamport)
        line("opcounter", state.opCounter)
        line("migration", state.migration.status, state.migration.v1Digest, state.migration.v1Count)
        val p = state.projection
        line("projection", p.status, p.lastDigest, p.pendingDigest, if (p.truncated) 1 else 0,
            p.conflictImported, p.conflictSkipped, p.conflictIgnoredRemovals)
        val cp = state.checkpoint
        if (cp.replayPosition == SyncCheckpoint.NO_REPLAY) line("checkpoint", cp.channelId, cp.lastAppliedServerId)
        else line("checkpoint", cp.channelId, cp.lastAppliedServerId, cp.replayPosition)
        for ((item, dots) in state.orSet.allAdds().entries.sortedBy { it.key.key }) {
            for ((dot, seq) in dots.entries.sortedBy { it.key }) line("A", item.key, dot.key, seq)
        }
        for ((item, dots) in state.orSet.allRemoved().entries.sortedBy { it.key.key }) {
            for (dot in dots.sorted()) line("R", item.key, dot.key)
        }
        for (entry in state.legacy) line("L", entry.songKey, entry.seq)
        for (key in state.legacyRemoved.sorted()) line("LR", key)
        for (entry in state.outbox) {
            val op = entry.op
            line("O", op.opId, op.type, op.item.key, op.seq, op.dots.sorted().joinToString(",") { it.key },
                entry.status, entry.attempts)
        }
        val text = body.toString()
        return (text + "END" + TAB + sha256Hex(text) + "\n").toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray, expectedAccount: Long): Decoded {
        val text = try {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            return Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        }
        val endAt = text.lastIndexOf("END$TAB")
        if (endAt <= 0 || !text.endsWith("\n")) return Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        val body = text.substring(0, endAt)
        val check = text.substring(endAt + 4).trimEnd('\n')
        if (check != sha256Hex(body)) return Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        return try {
            parse(body.split('\n').dropLast(1), expectedAccount)
        } catch (_: ParseFailure) {
            Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        } catch (_: IllegalArgumentException) {
            Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        } catch (_: NumberFormatException) {
            Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        }
    }

    private class ParseFailure(val reason: PlaylistUnreadableReason) : RuntimeException()

    private fun fail(): Nothing = throw ParseFailure(PlaylistUnreadableReason.FORMAT)

    private fun parse(lines: List<String>, expectedAccount: Long): Decoded {
        if (lines.firstOrNull() != MAGIC) return Decoded.Invalid(PlaylistUnreadableReason.FORMAT)
        var schema: Int? = null
        var account: Long? = null
        var device: String? = null
        var lamport: Long? = null
        var opCounter: Long? = null
        var migration: MigrationRecord? = null
        var projection: ProjectionRecord? = null
        var checkpoint: SyncCheckpoint? = null
        val adds = HashMap<PlaylistItemId.Telegram, MutableMap<Dot, Long>>()
        val removed = HashMap<PlaylistItemId.Telegram, MutableSet<Dot>>()
        val legacy = ArrayList<LegacyEntry>()
        val legacyRemoved = HashSet<Long>()
        val outbox = ArrayList<OutboxEntry>()
        val seenOps = HashSet<String>()

        fun once(current: Any?): Unit = if (current != null) fail() else Unit
        fun telegram(key: String) = PlaylistItemId.parse(key) as? PlaylistItemId.Telegram ?: fail()
        fun dot(key: String) = Dot.parse(key) ?: fail()

        for (raw in lines.drop(1)) {
            val f = raw.split(TAB)
            when (f[0]) {
                "schema" -> { once(schema); schema = f.getOrNull(1)?.toIntOrNull() ?: fail() }
                "account" -> { once(account); account = f.getOrNull(1)?.toLongOrNull() ?: fail() }
                "device" -> { once(device); device = f.getOrNull(1) ?: fail() }
                "lamport" -> { once(lamport); lamport = f.getOrNull(1)?.toLongOrNull() ?: fail() }
                "opcounter" -> { once(opCounter); opCounter = f.getOrNull(1)?.toLongOrNull() ?: fail() }
                "migration" -> {
                    once(migration)
                    if (f.size != 4) fail()
                    migration = MigrationRecord(MigrationStatus.valueOf(f[1]), f[2], f[3].toInt())
                }
                "projection" -> {
                    once(projection)
                    if (f.size != 8) fail()
                    projection = ProjectionRecord(
                        lastDigest = f[2], pendingDigest = f[3], status = ProjectionStatus.valueOf(f[1]),
                        truncated = f[4] == "1", conflictImported = f[5].toInt(),
                        conflictSkipped = f[6].toInt(), conflictIgnoredRemovals = f[7].toInt()
                    )
                }
                "checkpoint" -> {
                    once(checkpoint)
                    // The replay position is present only while a FULL_REPLAY is in progress.
                    if (f.size != 3 && f.size != 4) fail()
                    val replay = if (f.size == 4) f[3].toLong().also { if (it < 0L) fail() } else SyncCheckpoint.NO_REPLAY
                    checkpoint = SyncCheckpoint(f[1].toLong(), f[2].toLong(), replay)
                }
                "A" -> {
                    if (f.size != 4) fail()
                    val seq = f[3].toLong()
                    if (adds.getOrPut(telegram(f[1])) { HashMap() }.put(dot(f[2]), seq) != null) fail()
                }
                "R" -> {
                    if (f.size != 3) fail()
                    if (!removed.getOrPut(telegram(f[1])) { HashSet() }.add(dot(f[2]))) fail()
                }
                "L" -> {
                    if (f.size != 3) fail()
                    legacy.add(LegacyEntry(f[1].toLong(), f[2].toLong()))
                }
                "LR" -> {
                    if (f.size != 2) fail()
                    legacyRemoved.add(f[1].toLong())
                }
                "O" -> {
                    if (f.size != 8) fail()
                    if (!seenOps.add(f[1])) fail()
                    val dots = f[5].split(',').map { dot(it) }.toSet()
                    val op = PlaylistOp(f[1], OpType.valueOf(f[2]), telegram(f[3]), dots, f[4].toLong())
                    outbox.add(OutboxEntry(op, OutboxStatus.valueOf(f[6]), f[7].toInt().also { if (it < 0) fail() }))
                }
                else -> fail()
            }
        }
        // A newer schema is never read as if it were ours, and so can never be overwritten by us.
        val readSchema = schema ?: fail()
        if (readSchema > SCHEMA_VERSION) return Decoded.Invalid(PlaylistUnreadableReason.SCHEMA)
        if (readSchema != SCHEMA_VERSION) fail()
        if (account != expectedAccount) return Decoded.Invalid(PlaylistUnreadableReason.ACCOUNT)
        if (legacy.map { it.songKey }.toSet().size != legacy.size) fail()
        val state = PlaylistState(
            accountId = account ?: fail(),
            deviceId = device ?: fail(),
            lamport = lamport ?: fail(),
            opCounter = opCounter ?: fail(),
            orSet = PlaylistOrSet.of(adds.mapValues { it.value.toMap() }, removed.mapValues { it.value.toSet() }),
            legacy = legacy,
            legacyRemoved = legacyRemoved,
            outbox = outbox,
            migration = migration ?: fail(),
            projection = projection ?: fail(),
            checkpoint = checkpoint ?: fail(),
        )
        return Decoded.Ok(state)
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    /** Digest of a V1 list as the V1 file would hold it; "-" never collides with a real digest. */
    fun listDigest(ids: List<Long>): String = sha256Hex(ids.joinToString("\n"))
}
