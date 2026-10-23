package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P2: the wire format of one playlist-sync message. Independent of TDLib: it is only text.
 *
 * ```
 * #mvsync v1
 * scope <16 hex>            fingerprint of the Telegram account (never the account number, phone or session)
 * sender <deviceId>
 * batch <batchId>
 * op <opId> <ADD|REMOVE> <tg:chat:msg> <seq> <dot,dot,...>      1..20 lines
 * sum <sha256 of every line above, as written>
 * ```
 * `sum` is a CHECKSUM against damaged text. It is NOT a signature and proves nothing about who wrote
 * the message; anyone who can post in the channel can write a valid one. Real authenticity is a matter
 * for the transport (P3), which only ever reads the account's own private channel.
 *
 * Decoding is strict. A message that starts with the marker but is not a good message of this version is
 * `Invalid` and must stop the reader (it may hide operations); only text that does not start with the
 * marker at all is `Foreign` and may be passed over. Encoding is deterministic, so sending the same
 * operations again produces the very same text.
 */
internal object PlaylistSyncProtocol {
    const val VERSION = 1
    const val MARKER = "#mvsync"
    private const val HEADER = "$MARKER v$VERSION"

    /** Encoder target. Telegram text messages stop at 4096 characters. */
    const val MAX_CHARS = 3500
    /** Decoder hard limit. */
    const val MAX_TEXT_CHARS = 4096
    const val MAX_OPS = 20
    const val MAX_DOTS = 32

    enum class Error {
        FUTURE_VERSION, BAD_FORMAT, BAD_CHECKSUM, WRONG_ACCOUNT, TOO_LARGE, TOO_MANY_OPS, TOO_MANY_DOTS, DUPLICATE_OP
    }

    data class Envelope(val scope: String, val sender: String, val batchId: String, val ops: List<PlaylistOp>)

    sealed class Decoded {
        data class Ok(val envelope: Envelope) : Decoded()
        /** Not a sync message at all (does not start with the marker). Harmless to skip. */
        object Foreign : Decoded()
        data class Invalid(val error: Error) : Decoded()
    }

    /** A fingerprint of the account that scopes messages to it. Not secret, not reversible to the account number in practice. */
    fun scopeOf(accountId: Long): String =
        TelegramPlaylistCodec.sha256Hex("melovault:playlist-sync:scope:v1:$accountId").take(16)

    fun encode(envelope: Envelope): String {
        require(envelope.ops.size in 1..MAX_OPS) { "1..$MAX_OPS operations per message" }
        val body = StringBuilder()
        body.append(HEADER).append('\n')
        body.append("scope\t").append(envelope.scope).append('\n')
        body.append("sender\t").append(envelope.sender).append('\n')
        body.append("batch\t").append(envelope.batchId).append('\n')
        for (op in envelope.ops) {
            require(op.dots.size <= MAX_DOTS) { "too many tags in one operation" }
            body.append("op\t").append(op.opId).append('\t').append(op.type.name).append('\t')
                .append(op.item.key).append('\t').append(op.seq).append('\t')
                .append(op.dots.sorted().joinToString(",") { it.key }).append('\n')
        }
        val text = body.toString()
        return text + "sum\t" + TelegramPlaylistCodec.sha256Hex(text)
    }

    /**
     * Splits [ops] (kept in the given order) into messages that each fit [MAX_CHARS] and [MAX_OPS].
     * Returns null when a single operation cannot fit by itself (it can never be sent).
     * The batch id is taken from the first operation, so equal input gives equal messages.
     */
    fun planFirstEnvelope(scope: String, sender: String, ops: List<PlaylistOp>): Envelope? {
        if (ops.isEmpty()) return null
        val taken = ArrayList<PlaylistOp>()
        for (op in ops.take(MAX_OPS)) {
            if (op.dots.size > MAX_DOTS) break
            taken.add(op)
            val text = encode(Envelope(scope, sender, batchIdOf(taken.first()), taken))
            if (text.length > MAX_CHARS) {
                taken.removeAt(taken.size - 1)
                break
            }
        }
        if (taken.isEmpty()) return null
        return Envelope(scope, sender, batchIdOf(taken.first()), taken.toList())
    }

    private fun batchIdOf(first: PlaylistOp): String = first.opId.replace('#', '-')

    fun decode(text: String, expectedScope: String): Decoded {
        if (!text.startsWith(MARKER)) return Decoded.Foreign
        if (text.length > MAX_TEXT_CHARS) return Decoded.Invalid(Error.TOO_LARGE)
        val firstLine = text.substringBefore('\n')
        if (firstLine != HEADER) {
            val version = Regex("^$MARKER v(\\d+)$").matchEntire(firstLine)?.groupValues?.get(1)?.toLongOrNull()
            return if (version != null && version > VERSION) Decoded.Invalid(Error.FUTURE_VERSION)
            else Decoded.Invalid(Error.BAD_FORMAT)
        }
        val sumAt = text.lastIndexOf("\nsum\t")
        if (sumAt < 0) return Decoded.Invalid(Error.BAD_FORMAT)
        val body = text.substring(0, sumAt + 1)
        val claimed = text.substring(sumAt + 5)
        if (claimed != TelegramPlaylistCodec.sha256Hex(body)) return Decoded.Invalid(Error.BAD_CHECKSUM)
        return try {
            parse(body.split('\n').dropLast(1), expectedScope)
        } catch (_: IllegalArgumentException) {
            Decoded.Invalid(Error.BAD_FORMAT)
        }
    }

    private fun parse(lines: List<String>, expectedScope: String): Decoded {
        fun bad() = Decoded.Invalid(Error.BAD_FORMAT)
        if (lines.size < 5) return bad()
        val scope = lines[1].takeIf { it.startsWith("scope\t") }?.substringAfter('\t') ?: return bad()
        val sender = lines[2].takeIf { it.startsWith("sender\t") }?.substringAfter('\t') ?: return bad()
        val batch = lines[3].takeIf { it.startsWith("batch\t") }?.substringAfter('\t') ?: return bad()
        if (!Regex("^[0-9a-f]{16}$").matches(scope) || !PlaylistIds.isDeviceId(sender) || !PlaylistIds.isDeviceId(batch)) return bad()
        val opLines = lines.drop(4)
        if (opLines.size > MAX_OPS) return Decoded.Invalid(Error.TOO_MANY_OPS)
        val ops = ArrayList<PlaylistOp>()
        val seen = HashSet<String>()
        for (line in opLines) {
            val f = line.split('\t')
            if (f.size != 6 || f[0] != "op") return bad()
            val item = PlaylistItemId.parse(f[3]) as? PlaylistItemId.Telegram ?: return bad()
            val dotKeys = f[5].split(',')
            if (dotKeys.size > MAX_DOTS) return Decoded.Invalid(Error.TOO_MANY_DOTS)
            val dots = dotKeys.map { Dot.parse(it) ?: return bad() }
            if (dots.toSet().size != dots.size) return bad()
            val op = PlaylistOp(f[1], OpType.valueOf(f[2]), item, dots.toSet(), f[4].toLong())
            // The operation id and any new tag belong to the device that wrote the message.
            if (Dot.parse(op.opId)?.device != sender) return bad()
            if (op.type == OpType.ADD && op.dots.single().device != sender) return bad()
            if (!seen.add(op.opId)) return Decoded.Invalid(Error.DUPLICATE_OP)
            ops.add(op)
        }
        if (ops.isEmpty()) return bad()
        // Checked last on purpose: a damaged message must not be mistaken for another account's.
        if (scope != expectedScope) return Decoded.Invalid(Error.WRONG_ACCOUNT)
        return Decoded.Ok(Envelope(scope, sender, batch, ops))
    }
}
