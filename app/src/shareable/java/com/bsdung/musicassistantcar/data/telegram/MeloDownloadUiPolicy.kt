package com.bsdung.musicassistantcar.data.telegram

/** Download presentation facts, separate from playback and from the actual cache/index. */
internal object MeloDownloadUiPolicy {
    enum class State { UNKNOWN, REMOTE, DOWNLOADING, READY, UNAVAILABLE }
    data class Evidence(val ownerId: Long, val songKey: Long, val path: String?, val completed: Boolean,
        val expectedBytes: Long = -1L, val rootPath: String = "", val freshFileId: Int? = null,
        val remoteKnown: Boolean = false, val downloading: Boolean = false, val percent: Int? = null)
    data class FileFacts(val exists: Boolean, val file: Boolean, val readable: Boolean, val bytes: Long, val owned: Boolean)
    data class Presentation(val state: State, val percent: Int? = null, val readyPath: String? = null, val bytes: Long? = null) {
        val text: String get() = when (state) {
            State.UNKNOWN -> "Đang kiểm tra…"
            State.REMOTE -> "Telegram · Chưa tải đủ"
            State.DOWNLOADING -> percent?.let { "Đang tải $it%" } ?: "Đang tải…"
            State.READY -> "Đã tải"
            State.UNAVAILABLE -> "Tệp trên máy không còn dùng được"
        }
    }

    fun evaluate(owner: Long, evidence: Evidence, facts: FileFacts?): Presentation {
        if (owner <= 0L || owner != evidence.ownerId || (facts != null && !facts.owned)) return Presentation(State.UNAVAILABLE)
        val validFile = evidence.path != null && facts != null && facts.exists && facts.file && facts.readable && facts.bytes > 0L
        val sizeMatches = evidence.expectedBytes <= 0L || facts?.bytes == evidence.expectedBytes
        if (evidence.completed && validFile && sizeMatches) return Presentation(State.READY, readyPath = evidence.path, bytes = facts!!.bytes)
        if (evidence.downloading) return Presentation(State.DOWNLOADING, evidence.percent?.coerceIn(0, 100))
        if (evidence.path != null && facts == null) return Presentation(State.UNKNOWN)
        if (evidence.completed || (evidence.path != null && !validFile)) return Presentation(State.UNAVAILABLE)
        return Presentation(if (evidence.remoteKnown) State.REMOTE else State.UNKNOWN)
    }

    fun rowText(download: Presentation, current: Boolean, playing: Boolean, paused: Boolean, buffering: Boolean,
        opening: Boolean = false, local: Boolean = false, quality: String? = null): String {
        val playback = when {
            current && paused -> "Tạm dừng"
            current && buffering -> TelegramPlayFeedbackPolicy.BUFFERING_TEXT
            current && playing -> "Đang phát"
            opening -> TelegramPlayFeedbackPolicy.RESOLVING_TEXT
            current -> "Chờ phát"
            else -> null
        }
        val storage = if (local) "Trên máy" else download.text
        return listOfNotNull(playback, storage.takeUnless { opening && download.state == State.UNKNOWN },
            quality?.takeIf { local || download.state == State.READY }).joinToString(" · ")
    }

    /** Owner/epoch/revision guards are UI-only; the supplied worker performs every file stat. */
    class Controller(private val stat: (Evidence) -> FileFacts,
        private val execute: (Runnable) -> Unit, private val post: (Runnable) -> Unit,
        private val changed: () -> Unit) {
        private data class Entry(val evidence: Evidence, val revision: Long)
        private data class Token(val owner: Long, val epoch: Long, val key: Long, val revision: Long)
        private data class Result(val token: Token, val presentation: Presentation)
        private val entries = HashMap<Long, Entry>()
        private val results = HashMap<Long, Result>()
        private val pending = HashMap<Long, Token>()
        private val callbacks = HashMap<Token, MutableList<(Presentation) -> Unit>>()
        var ownerId: Long = 0L
            private set
        private var epoch = 0L
        private var revision = 0L

        fun setOwner(owner: Long) {
            if (ownerId == owner) return
            ownerId = owner
            entries.clear()
            invalidate()
        }
        fun invalidate() { epoch++; results.clear(); pending.clear(); callbacks.clear(); changed() }
        fun evidence(key: Long): Evidence? = entries[key]?.evidence?.takeIf { it.ownerId == ownerId }

        fun observe(evidence: Evidence, force: Boolean = false, onChecked: ((Presentation) -> Unit)? = null) {
            if (evidence.ownerId != ownerId || ownerId <= 0L) return
            val previous = entries[evidence.songKey]
            val entry = if (previous?.evidence == evidence && (!force || pending[evidence.songKey] != null)) previous else Entry(evidence, ++revision).also {
                entries[evidence.songKey] = it
                results.remove(evidence.songKey)
            }
            val token = Token(ownerId, epoch, evidence.songKey, entry.revision)
            val done = results[evidence.songKey]?.takeIf { it.token == token }
            if (done != null) { onChecked?.invoke(done.presentation); return }
            if (onChecked != null) callbacks.getOrPut(token) { mutableListOf() }.add(onChecked)
            if (pending[evidence.songKey] == token) return
            if (evidence.path == null) {
                publish(token, evaluate(ownerId, evidence, null))
                return
            }
            pending[evidence.songKey] = token
            execute(Runnable {
                val facts = runCatching { stat(evidence) }.getOrElse { FileFacts(false, false, false, 0L, false) }
                val presentation = evaluate(evidence.ownerId, evidence, facts)
                post(Runnable { publish(token, presentation) })
            })
        }

        private fun publish(token: Token, value: Presentation) {
            if (ownerId != token.owner || epoch != token.epoch || entries[token.key]?.revision != token.revision) {
                callbacks.remove(token)
                return
            }
            if (pending[token.key] == token) pending.remove(token.key)
            results[token.key] = Result(token, value)
            val listeners = callbacks.remove(token).orEmpty()
            changed()
            listeners.forEach { it(value) }
        }
        fun ensure(key: Long) { entries[key]?.let { observe(it.evidence) } }
        /** A currently running check is already fresh. Preserve its action listeners on resume. */
        fun recheck(key: Long) {
            entries[key]?.let { observe(it.evidence, force = pending[key] == null) }
        }
        fun presentation(key: Long, downloading: Boolean = false, percent: Int? = null): Presentation {
            val result = results[key]?.takeIf { it.token.owner == ownerId && it.token.epoch == epoch &&
                it.token.revision == entries[key]?.revision }?.presentation
            if (result?.state == State.READY) return result
            if (downloading) return Presentation(State.DOWNLOADING, percent?.coerceIn(0, 100))
            return result ?: Presentation(State.UNKNOWN)
        }
    }
}
