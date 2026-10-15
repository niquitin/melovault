package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import java.io.File

/**
 * Exactly one native TDLib receive loop within the Shareable app process.
 *
 * The UI may detach and be recreated without closing the in-flight native
 * client. MediaSessionService may continue bounded downloads while the UI is
 * gone. No Activity reference, password, token or account data is persisted.
 */
internal object ShareableTdlibOwner {
    private val lock = Any()
    @Volatile private var id = 0
    @Volatile private var active = false
    private var sessionDir: String? = null
    private var uiListener: ((JSONObject) -> Unit)? = null
    private var backgroundListener: ((JSONObject) -> Unit)? = null
    private var serviceBound = false
    @Volatile private var verifiedUserId: Long? = null
    private var startedReceiver = false

    fun attachUi(directory: File, callback: (JSONObject) -> Unit): Int =
        synchronized(lock) {
            val path = directory.canonicalPath
            if (active) {
                check(path == sessionDir) { "TDLib account slot changed while active" }
            } else {
                id = TdlibNativeJsonGateway.newClientId()
                sessionDir = path
                active = true
                if (!startedReceiver) {
                    startedReceiver = true
                    Thread({
                        while (true) {
                            if (!active) {
                                try { Thread.sleep(100) } catch (_: InterruptedException) {}
                                continue
                            }
                            try {
                                val response = TdlibNativeJsonGateway.receiveOne(0.4) ?: continue
                                val event = JSONObject(response)
                                if (event.optInt("@client_id") != id) continue
                                val (ui, background) = synchronized(lock) {
                                    uiListener to backgroundListener
                                }
                                // Never emit raw TDLib JSON or credentials in logs.
                                runCatching { background?.invoke(event) }
                                runCatching { ui?.invoke(event) }
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
        synchronized(lock) { verifiedUserId = userId }
    }

    fun authorizedAccount(): Long? = verifiedUserId

    fun revokeAuthorization() = synchronized(lock) {
        verifiedUserId = null
    }

    fun send(type: String, tag: String? = null, configure: ((JSONObject) -> Unit)? = null) {
        val current = id
        check(active && current > 0) { "No active TDLib client" }
        val request = JSONObject().put("@type", type)
        if (tag != null) request.put("@extra", tag)
        configure?.invoke(request)
        TdlibNativeJsonGateway.send(current, request.toString())
    }

    private fun closeIfUnowned() {
        if (uiListener != null || serviceBound || !active) return
        val previous = id
        active = false
        id = 0
        sessionDir = null
        verifiedUserId = null
        if (previous > 0) runCatching { TdlibNativeJsonGateway.requestClose(previous) }
    }
}
