package com.bsdung.musicassistantcar.data.telegram

/**
 * Nhiều Playlist có tên, S6: the wire format of one named-playlist sync message (`#mvlist v1`). Only text, like
 * [PlaylistSyncProtocol], and it reuses that protocol's checksum, scope, size limits and error list.
 *
 * ```
 * #mvlist v1
 * scope <16 hex>              same fingerprint as #mvsync: PlaylistSyncProtocol.scopeOf(account)
 * sender <deviceId of the named-playlist store>
 * batch <batchId>
 * op <opId> LCREATE <listId> <seq> <dot> <nameB64>
 * op <opId> LRENAME <listId> <seq> <nameB64>
 * op <opId> LDELETE <listId> <seq> <dot,dot,...>
 * op <opId> IADD    <listId> <tg:chat:msg> <seq> <dot> <pos>
 * op <opId> IREMOVE <listId> <tg:chat:msg> <seq> <dot,dot,...>
 * op <opId> IMOVE   <listId> <tg:chat:msg> <seq> <pos>
 * sum <sha256 of every line above, as written>
 * ```
 * One space between fields, no field holds a space, so the text is a fixed point of [TdlibText.normalize]. A name
 * is base64url without padding of its UTF-8 bytes. The format never carries a song of the device itself.
 *
 * Compatibility: [PlaylistSyncProtocol.decode] sees a `#mvlist` text as [PlaylistSyncProtocol.Decoded.Foreign] (it does not
 * start with its marker) and so does every older app; this decoder sees `#mvsync` as [Decoded.Foreign] in turn. A text that
 * starts with `#mvlist` but is not a good message of this version is [Decoded.Invalid] and must stop the reader.
 * `sum` is a checksum against damage, not a signature. Encoding is deterministic.
 */
internal object NamedPlaylistSyncProtocol {
    const val VERSION = 1
    const val MARKER = "#mvlist"
    private const val HEADER = "$MARKER v$VERSION"

    const val MAX_CHARS = PlaylistSyncProtocol.MAX_CHARS
    const val MAX_TEXT_CHARS = PlaylistSyncProtocol.MAX_TEXT_CHARS
    const val MAX_OPS = PlaylistSyncProtocol.MAX_OPS
    const val MAX_DOTS = PlaylistSyncProtocol.MAX_DOTS

    private const val SPACE = ' '

    data class Envelope(val scope: String, val sender: String, val batchId: String, val ops: List<NamedOp>)

    sealed class Decoded {
        data class Ok(val envelope: Envelope) : Decoded()

        /** Not a named-playlist message at all (does not start with the marker). Harmless to skip. */
        object Foreign : Decoded()
        data class Invalid(val error: PlaylistSyncProtocol.Error) : Decoded()
    }

    fun encode(envelope: Envelope): String {
        require(envelope.ops.size in 1..MAX_OPS) { "1..$MAX_OPS operations per message" }
        val body = StringBuilder()
        body.append(HEADER).append('\n')
        body.append("scope").append(SPACE).append(envelope.scope).append('\n')
        body.append("sender").append(SPACE).append(envelope.sender).append('\n')
        body.append("batch").append(SPACE).append(envelope.batchId).append('\n')
        for (op in envelope.ops) {
            require(op.dots.size <= MAX_DOTS) { "too many tags in one operation" }
            body.append("op ").append(op.opId).append(SPACE).append(op.type.name).append(SPACE).append(op.listId.key)
            val dots = op.dots.sorted().joinToString(",") { it.key }
            when (op.type) {
                NamedOpType.LCREATE -> body.append(SPACE).append(op.seq).append(SPACE).append(dots)
                    .append(SPACE).append(NamedPlaylistCodec.encodeName(checkNotNull(op.name)))
                NamedOpType.LRENAME -> body.append(SPACE).append(op.seq)
                    .append(SPACE).append(NamedPlaylistCodec.encodeName(checkNotNull(op.name)))
                NamedOpType.LDELETE -> body.append(SPACE).append(op.seq).append(SPACE).append(dots)
                NamedOpType.IADD -> body.append(SPACE).append(checkNotNull(op.item).key).append(SPACE).append(op.seq)
                    .append(SPACE).append(dots).append(SPACE).append(checkNotNull(op.pos))
                NamedOpType.IREMOVE -> body.append(SPACE).append(checkNotNull(op.item).key).append(SPACE).append(op.seq)
                    .append(SPACE).append(dots)
                NamedOpType.IMOVE -> body.append(SPACE).append(checkNotNull(op.item).key).append(SPACE).append(op.seq)
                    .append(SPACE).append(checkNotNull(op.pos))
            }
            body.append('\n')
        }
        val text = body.toString()
        val full = text + "sum" + SPACE + TelegramPlaylistCodec.sha256Hex(text)
        // Fail closed: a text TDLib would change can never be sent, because its checksum would no longer match.
        check(TdlibText.normalize(full) == full) { "sync text must survive TDLib's cleaning unchanged" }
        return full
    }

    /**
     * Takes the longest prefix of [ops] (kept in the given order) that fits [MAX_CHARS] and [MAX_OPS] into one message.
     * Returns null when a single operation cannot fit by itself. The batch id comes from the first operation, so equal
     * input gives equal messages.
     */
    fun planFirstEnvelope(scope: String, sender: String, ops: List<NamedOp>): Envelope? {
        if (ops.isEmpty()) return null
        val taken = ArrayList<NamedOp>()
        for (op in ops.take(MAX_OPS)) {
            if (op.dots.size > MAX_DOTS) break
            taken.add(op)
            if (encode(Envelope(scope, sender, batchIdOf(taken.first()), taken)).length > MAX_CHARS) {
                taken.removeAt(taken.size - 1)
                break
            }
        }
        if (taken.isEmpty()) return null
        return Envelope(scope, sender, batchIdOf(taken.first()), taken.toList())
    }

    private fun batchIdOf(first: NamedOp): String = first.opId.replace('#', '-')

    fun decode(text: String, expectedScope: String): Decoded {
        if (!text.startsWith(MARKER)) return Decoded.Foreign
        if (text.length > MAX_TEXT_CHARS) return Decoded.Invalid(PlaylistSyncProtocol.Error.TOO_LARGE)
        val firstLine = text.substringBefore('\n')
        if (firstLine != HEADER) {
            val version = Regex("^$MARKER v(\\d+)$").matchEntire(firstLine)?.groupValues?.get(1)?.toLongOrNull()
            return if (version != null && version > VERSION) Decoded.Invalid(PlaylistSyncProtocol.Error.FUTURE_VERSION)
            else Decoded.Invalid(PlaylistSyncProtocol.Error.BAD_FORMAT)
        }
        val marker = "\nsum$SPACE"
        val sumAt = text.lastIndexOf(marker)
        if (sumAt < 0) return Decoded.Invalid(PlaylistSyncProtocol.Error.BAD_FORMAT)
        val body = text.substring(0, sumAt + 1)
        val claimed = text.substring(sumAt + marker.length)
        if (claimed != TelegramPlaylistCodec.sha256Hex(body)) return Decoded.Invalid(PlaylistSyncProtocol.Error.BAD_CHECKSUM)
        return try {
            parse(body.split('\n').dropLast(1), expectedScope)
        } catch (_: IllegalArgumentException) {
            Decoded.Invalid(PlaylistSyncProtocol.Error.BAD_FORMAT)
        }
    }

    private class Bad : RuntimeException()

    private fun parse(lines: List<String>, expectedScope: String): Decoded {
        fun bad() = Decoded.Invalid(PlaylistSyncProtocol.Error.BAD_FORMAT)
        if (lines.size < 5) return bad()
        val scope = lines[1].takeIf { it.startsWith("scope$SPACE") }?.substringAfter(SPACE) ?: return bad()
        val sender = lines[2].takeIf { it.startsWith("sender$SPACE") }?.substringAfter(SPACE) ?: return bad()
        val batch = lines[3].takeIf { it.startsWith("batch$SPACE") }?.substringAfter(SPACE) ?: return bad()
        if (!Regex("^[0-9a-f]{16}$").matches(scope) || !PlaylistIds.isDeviceId(sender) || !PlaylistIds.isDeviceId(batch)) return bad()
        val opLines = lines.drop(4)
        if (opLines.size > MAX_OPS) return Decoded.Invalid(PlaylistSyncProtocol.Error.TOO_MANY_OPS)
        val ops = ArrayList<NamedOp>()
        val seen = HashSet<String>()
        for (line in opLines) {
            val op = try {
                parseOp(line)
            } catch (e: TooManyDots) {
                return Decoded.Invalid(PlaylistSyncProtocol.Error.TOO_MANY_DOTS)
            } catch (_: Bad) {
                return bad()
            }
            // The operation id and any new tag belong to the device that wrote the message.
            if (op.opDot.device != sender) return bad()
            if ((op.type == NamedOpType.LCREATE || op.type == NamedOpType.IADD) && op.dots.single().device != sender) return bad()
            if (!seen.add(op.opId)) return Decoded.Invalid(PlaylistSyncProtocol.Error.DUPLICATE_OP)
            ops.add(op)
        }
        if (ops.isEmpty()) return bad()
        // Checked last on purpose: a damaged message must not be mistaken for another account's.
        if (scope != expectedScope) return Decoded.Invalid(PlaylistSyncProtocol.Error.WRONG_ACCOUNT)
        return Decoded.Ok(Envelope(scope, sender, batch, ops))
    }

    private class TooManyDots : RuntimeException()

    private fun parseOp(line: String): NamedOp {
        val f = line.split(SPACE)
        if (f.size < 6 || f[0] != "op") throw Bad()
        val type = NamedOpType.values().firstOrNull { it.name == f[2] } ?: throw Bad()
        val listId = ListId.parse(f[3]) ?: throw Bad()
        fun seq(text: String) = text.toLongOrNull() ?: throw Bad()
        fun dots(text: String): Set<Dot> {
            val keys = text.split(',')
            if (keys.size > MAX_DOTS) throw TooManyDots()
            val parsed = keys.map { Dot.parse(it) ?: throw Bad() }
            if (parsed.toSet().size != parsed.size) throw Bad()
            return parsed.toSet()
        }
        fun item(text: String) = PlaylistItemId.parse(text) as? PlaylistItemId.Telegram ?: throw Bad()
        fun name(text: String) = NamedPlaylistCodec.decodeName(text) ?: throw Bad()
        fun need(count: Int) { if (f.size != count) throw Bad() }
        return when (type) {
            NamedOpType.LCREATE -> { need(7); NamedOp(f[1], type, listId, null, seq(f[4]), dots(f[5]), name(f[6]), null) }
            NamedOpType.LRENAME -> { need(6); NamedOp(f[1], type, listId, null, seq(f[4]), emptySet(), name(f[5]), null) }
            NamedOpType.LDELETE -> { need(6); NamedOp(f[1], type, listId, null, seq(f[4]), dots(f[5]), null, null) }
            NamedOpType.IADD -> { need(8); NamedOp(f[1], type, listId, item(f[4]), seq(f[5]), dots(f[6]), null, f[7]) }
            NamedOpType.IREMOVE -> { need(7); NamedOp(f[1], type, listId, item(f[4]), seq(f[5]), dots(f[6]), null, null) }
            NamedOpType.IMOVE -> { need(7); NamedOp(f[1], type, listId, item(f[4]), seq(f[5]), emptySet(), null, f[6]) }
        }
    }
}
