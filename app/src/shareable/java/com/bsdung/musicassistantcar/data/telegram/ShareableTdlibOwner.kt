package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Requests of the playlist sync carry this tag prefix. Their answers go to the sync sink ONLY, never to the UI or playback listeners. */
internal const val SYNC_TAG_PREFIX = "mvsync:"

/** The native JSON surface the owner needs. The real one wraps JNI ([NativeTdlibJsonPort]); tests put a fake behind it. */
internal interface TdlibJsonPort {
    fun newClientId(): Int
    fun send(clientId: Int, jsonRequest: String)
    fun receiveOne(timeoutSeconds: Double): String?
    fun requestClose(clientId: Int)
}

internal object NativeTdlibJsonPort : TdlibJsonPort {
    override fun newClientId(): Int = TdlibNativeJsonGateway.newClientId()
    override fun send(clientId: Int, jsonRequest: String) = TdlibNativeJsonGateway.send(clientId, jsonRequest)
    override fun receiveOne(timeoutSeconds: Double): String? = TdlibNativeJsonGateway.receiveOne(timeoutSeconds)
    override fun requestClose(clientId: Int) = TdlibNativeJsonGateway.requestClose(clientId)
}

/**
 * E96 P3: what the playlist sync needs from the single TDLib owner and nothing more. It never creates a client,
 * never receives on its own and never keeps the client alive: it only rides on the client the UI or the playback
 * service already own.
 */
internal interface SyncOwnerPort {
    fun authorizedAccount(): Long?

    /** Changes every time an authorization is (re)established or lost, or the client is replaced or closed (also for the same account). */
    fun authEpoch(): Long

    /** Never blocks. Throws [IllegalStateException] when no client is active. */
    fun send(type: String, tag: String? = null, configure: ((JSONObject) -> Unit)? = null)

    /** One sink at a time; returns false (and changes nothing) if another one is bound. */
    fun bindSync(sink: SyncEventSink): Boolean
    fun unbindSync(sink: SyncEventSink)
}

/** Called from ONE dispatch thread, in order. Must never block. */
internal interface SyncEventSink {
    fun onEvent(event: JSONObject)

    /** The bounded queue was full: events were dropped, so nothing in flight may be trusted any more. */
    fun onOverflow()

    /** The client was closed (all owners left). */
    fun onClientClosed()
}

/** Which untagged updates the sync wants to see (keeps the bounded queue free of everything else, e.g. file progress). */
internal object SyncEventFilter {
    private val wanted = setOf(
        "updateMessageSendSucceeded", "updateMessageSendFailed", "updateNewMessage", "updateDeleteMessages",
        "updateMessageEdited", "updateConnectionState", "updateAuthorizationState",
    )

    fun wants(type: String): Boolean = type in wanted
}

/**
 * Hands events to a sink on its own thread through a bounded queue, so the native receive thread never waits for the sync.
 * The thread lives only while the sink is bound.
 */
internal class SyncPump(private val sink: SyncEventSink, capacity: Int = 512) {
    private val queue = ArrayBlockingQueue<JSONObject>(capacity)
    @Volatile private var closed = false
    private val thread = Thread({
        while (!closed) {
            val event = try { queue.poll(500, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { break } ?: continue
            runCatching { sink.onEvent(event) }
        }
    }, "MeloVault-Sync-Dispatch").apply { isDaemon = true }

    fun start() = thread.start()

    /** Never blocks. A full queue drops everything queued, tells the sink, and refuses this event. */
    fun offer(event: JSONObject): Boolean {
        if (closed) return false
        if (queue.offer(event)) return true
        queue.clear()
        runCatching { sink.onOverflow() }
        return false
    }

    fun close() {
        closed = true
        queue.clear()
        thread.interrupt()
    }
}

/**
 * Exactly one native TDLib receive loop within the Shareable app process.
 *
 * The UI may detach and be recreated without closing the in-flight native
 * client. MediaSessionService may continue bounded downloads while the UI is
 * gone. No Activity reference, password, token or account data is persisted.
 *
 * E96 P3: an optional third listener, the playlist sync sink, rides on the same client. It does not own it: when
 * the UI and the playback service are gone the client closes whether or not the sync is bound. Requests tagged
 * [SYNC_TAG_PREFIX] are answered to the sync sink only; the other listeners never see them.
 */
internal class TdlibOwnerCore(private val port: TdlibJsonPort) : SyncOwnerPort {
    private val lock = Any()
    @Volatile private var id = 0
    @Volatile private var active = false
    private var sessionDir: String? = null
    private var uiListener: ((JSONObject) -> Unit)? = null
    private var backgroundListener: ((JSONObject) -> Unit)? = null
    private var serviceBound = false
    @Volatile private var verifiedUserId: Long? = null
    private var startedReceiver = false
    @Volatile private var stopReceiver = false
    private val epoch = AtomicLong(0L)
    private var syncSink: SyncEventSink? = null
    private var syncPump: SyncPump? = null

    fun attachUi(directory: File, callback: (JSONObject) -> Unit): Int =
        synchronized(lock) {
            val path = directory.canonicalPath
            if (active) {
                check(path == sessionDir) { "TDLib account slot changed while active" }
            } else {
                id = port.newClientId()
                sessionDir = path
                active = true
                epoch.incrementAndGet()
                if (!startedReceiver) {
                    startedReceiver = true
                    Thread({
                        while (!stopReceiver) {
                            if (!active) {
                                try { Thread.sleep(100) } catch (_: InterruptedException) {}
                                continue
                            }
                            try {
                                val response = port.receiveOne(0.4) ?: continue
                                dispatch(JSONObject(response))
                            } catch (_: Exception) {
                                // Never log payloads and never busy-loop on JNI failure.
                                try { Thread.sleep(250) } catch (_: InterruptedException) {}
                            }
                        }
                    }, "MeloVault-TDLib-Owned-Receiver").apply { isDaemon = true }.start()
                }
            }
            uiListener = callback
            id
        }

    /** Receive-thread routing. Internal so tests can drive it. */
    internal fun dispatch(event: JSONObject) {
        if (event.optInt("@client_id") != id) return
        val tag = event.optString("@extra")
        val (ui, background, pump) = synchronized(lock) { Triple(uiListener, backgroundListener, syncPump) }
        if (tag.startsWith(SYNC_TAG_PREFIX)) {
            pump?.offer(event)
            return
        }
        // Never emit raw TDLib JSON or credentials in logs.
        runCatching { background?.invoke(event) }
        runCatching { ui?.invoke(event) }
        if (pump != null && SyncEventFilter.wants(event.optString("@type"))) pump.offer(event)
    }

    fun detachUi(callback: (JSONObject) -> Unit) = synchronized(lock) {
        if (uiListener === callback) uiListener = null
        closeIfUnowned()
    }

    fun bindPlayback(callback: (JSONObject) -> Unit) = synchronized(lock) {
        serviceBound = true
        backgroundListener = callback
    }

    fun unbindPlayback() = synchronized(lock) {
        backgroundListener = null
        serviceBound = false
        closeIfUnowned()
    }

    fun uiIsAttached(): Boolean = synchronized(lock) { uiListener != null }

    fun markGetMeVerified(userId: Long) {
        require(userId > 0L)
        synchronized(lock) {
            verifiedUserId = userId
            epoch.incrementAndGet()
        }
    }

    override fun authorizedAccount(): Long? = verifiedUserId

    override fun authEpoch(): Long = epoch.get()

    fun revokeAuthorization() = synchronized(lock) {
        verifiedUserId = null
        epoch.incrementAndGet()
    }

    override fun bindSync(sink: SyncEventSink): Boolean = synchronized(lock) {
        if (syncSink != null) return false
        val pump = SyncPump(sink)
        syncSink = sink
        syncPump = pump
        pump.start()
        true
    }

    override fun unbindSync(sink: SyncEventSink) = synchronized(lock) {
        if (syncSink !== sink) return@synchronized
        syncPump?.close()
        syncPump = null
        syncSink = null
    }

    fun syncIsBound(): Boolean = synchronized(lock) { syncSink != null }

    fun clientIsActive(): Boolean = active

    override fun send(type: String, tag: String?, configure: ((JSONObject) -> Unit)?) {
        val current = id
        check(active && current > 0) { "No active TDLib client" }
        val request = JSONObject().put("@type", type)
        if (tag != null) request.put("@extra", tag)
        configure?.invoke(request)
        port.send(current, request.toString())
    }

    /** Tests only: ends the receive loop of this core. The app never calls it. */
    internal fun stopReceiverForTests() {
        stopReceiver = true
    }

    private fun closeIfUnowned() {
        if (uiListener != null || serviceBound || !active) return
        val previous = id
        active = false
        id = 0
        sessionDir = null
        verifiedUserId = null
        epoch.incrementAndGet()
        val sink = syncSink
        syncPump?.close()
        syncPump = null
        syncSink = null
        sink?.let { runCatching { it.onClientClosed() } }
        if (previous > 0) runCatching { port.requestClose(previous) }
    }
}

internal object ShareableTdlibOwner : SyncOwnerPort {
    private val core = TdlibOwnerCore(NativeTdlibJsonPort)

    fun attachUi(directory: File, callback: (JSONObject) -> Unit): Int = core.attachUi(directory, callback)
    fun detachUi(callback: (JSONObject) -> Unit) = core.detachUi(callback)
    fun bindPlayback(callback: (JSONObject) -> Unit) = core.bindPlayback(callback)
    fun unbindPlayback() = core.unbindPlayback()
    fun uiIsAttached(): Boolean = core.uiIsAttached()
    fun markGetMeVerified(userId: Long) = core.markGetMeVerified(userId)
    fun revokeAuthorization() = core.revokeAuthorization()

    override fun authorizedAccount(): Long? = core.authorizedAccount()
    override fun authEpoch(): Long = core.authEpoch()
    override fun bindSync(sink: SyncEventSink): Boolean = core.bindSync(sink)
    override fun unbindSync(sink: SyncEventSink) = core.unbindSync(sink)
    override fun send(type: String, tag: String?, configure: ((JSONObject) -> Unit)?) = core.send(type, tag, configure)
}
