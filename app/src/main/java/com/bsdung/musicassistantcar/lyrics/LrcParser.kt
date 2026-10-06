package com.bsdung.musicassistantcar.lyrics

object LrcParser {
    private val OFFSET_TAG_REGEX = Regex(
        """^\s*\[\s*offset\s*:\s*([+-]?\d+)\s*\]\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val METADATA_TAG_REGEX = Regex(
        """^\s*\[\s*[a-zA-Z_]+\s*:[^\]]*\]\s*$""",
    )

    private val LEADING_TIMESTAMP_REGEX = Regex(
        """^\[\s*(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\s*\]""",
    )

    /**
     * Deterministically parses raw LRC text into sorted, deduplicated [LyricsLine] items.
     */
    fun parse(rawLrc: String?): List<LyricsLine> {
        if (rawLrc.isNullOrBlank()) return emptyList()

        val rawLines = rawLrc.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var offsetMs = 0L

        // First pass: detect [offset:+/-N] tag
        for (line in rawLines) {
            val offsetMatch = OFFSET_TAG_REGEX.matchEntire(line)
            if (offsetMatch != null) {
                offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
                break
            }
        }

        val parsedEntries = ArrayList<LyricsLine>()

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            if (OFFSET_TAG_REGEX.matches(trimmed)) continue

            var cursor = trimmed
            val timestampsMs = ArrayList<Long>(2)
            var malformedTimestampOnLine = false

            while (cursor.startsWith("[")) {
                val match = LEADING_TIMESTAMP_REGEX.find(cursor)
                if (match != null) {
                    val minutes = match.groupValues[1].toLongOrNull()
                    val seconds = match.groupValues[2].toLongOrNull()
                    val fractionStr = match.groupValues[3]
                    if (minutes == null || seconds == null || seconds !in 0..59) {
                        malformedTimestampOnLine = true
                        break
                    }
                    val fractionMs = when (fractionStr.length) {
                        0 -> 0L
                        1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                        else -> fractionStr.take(3).toLongOrNull() ?: 0L
                    }
                    val baseMs = minutes * 60_000L + seconds * 1_000L + fractionMs
                    val adjustedMs = (baseMs + offsetMs).coerceAtLeast(0L)
                    timestampsMs.add(adjustedMs)
                    cursor = cursor.substring(match.range.last + 1)
                } else {
                    break
                }
            }

            if (malformedTimestampOnLine || timestampsMs.isEmpty()) {
                // Either a metadata tag like [ar:Artist] or a malformed line without valid timestamps
                continue
            }

            // If the remaining text itself is just another metadata tag or blank, skip
            val lyricText = cursor.trim()
            if (lyricText.isEmpty()) continue
            if (METADATA_TAG_REGEX.matches(lyricText)) continue

            for (ts in timestampsMs) {
                parsedEntries.add(LyricsLine(startMs = ts, text = lyricText))
            }
        }

        if (parsedEntries.isEmpty()) return emptyList()

        // Stable sort by startMs ascending, then deduplicate identical (startMs, text)
        return parsedEntries
            .sortedBy { it.startMs }
            .distinct()
    }

    /**
     * Binary search lookup for the active synced line at [positionMs].
     *
     * Returns:
     * - `-1` if [lines] is empty or [positionMs] is before `lines.first().startMs`
     * - greatest index `i` where `lines[i].startMs <= positionMs`
     */
    fun findActiveLineIndex(
        lines: List<LyricsLine>,
        positionMs: Long,
    ): Int {
        if (lines.isEmpty()) return -1
        if (positionMs < lines[0].startMs) return -1

        var low = 0
        var high = lines.lastIndex
        var best = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midStart = lines[mid].startMs
            if (midStart <= positionMs) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }
}
