package com.bsdung.musicassistantcar.data.telegram

import org.drinkless.tdlib.JsonClient

/**
 * Thin, Shareable-only adapter for TDLib's upstream JSONJava JNI binding.
 *
 * Never logs request or response payloads: they may contain phone numbers,
 * OTPs, 2FA material, Telegram account data, or downloaded file paths.
 * Native binaries are supplied only after an audited TDLib build.
 */
internal object TdlibNativeJsonGateway {
    /** Throws rather than claiming the account is connected when JNI is absent. */
    fun newClientId(): Int = try {
        JsonClient.createClientId().also { require(it > 0) }
    } catch (e: UnsatisfiedLinkError) {
        throw IllegalStateException("TDLib Android native library not available", e)
    }

    fun send(clientId: Int, jsonRequest: String) {
        require(clientId > 0)
        require(jsonRequest.isNotBlank())
        try {
            JsonClient.send(clientId, jsonRequest)
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException("TDLib Android native library not available", e)
        }
    }

    /**
     * TDLib receive() is global across all clients and MUST have exactly one
     * consumer thread. Caller must dispatch by @client_id and @extra.
     */
    fun receiveOne(timeoutSeconds: Double): String? {
        require(timeoutSeconds in 0.0..5.0)
        return try {
            JsonClient.receive(timeoutSeconds)
        } catch (e: UnsatisfiedLinkError) {
            throw IllegalStateException("TDLib Android native library not available", e)
        }
    }

    /** Close request is asynchronous; caller invalidates old session epoch. */
    fun requestClose(clientId: Int) {
        send(clientId, """{"@type":"close"}""")
    }
}
