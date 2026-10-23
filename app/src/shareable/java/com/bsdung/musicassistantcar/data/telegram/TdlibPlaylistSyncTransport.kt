package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject

internal data class TdlibTransportTimeouts(
    val requestMs: Long = 20_000L,
    /** How long to wait for updateMessageSendSucceeded after the provisional answer. Silence is [SendResult.Unknown], never an ACK. */
    val sendAckMs: Long = 60_000L,
)

/**
 * E96 P3: [PlaylistSyncTransport] over the single TDLib owner. One instance is made for ONE (account, auth epoch,
 * channel) and is useless as soon as any of them changes.
 *
 * Sending:
 *  - Only append: one `sendMessage` with `inputMessageText`. Never editMessageText, media, audio, a path or a credential.
 *  - TDLib's first answer to sendMessage is a PROVISIONAL message (temporary id, pending state). That is not proof the
 *    server stored it. [SendResult.Sent] is returned only on the correlated `updateMessageSendSucceeded`: same chat, its
 *    `old_message_id` equal to the provisional id, a final server id different from it, and the very same text. The
 *    update may arrive before or after the provisional answer; both orders are handled.
 *  - No update in time, a lost answer, an abort, a payload that is not ours: [SendResult.Unknown]. The operation ids stay
 *    unchanged and the coordinator finds the message again by reading the history.
 *  - `updateMessageSendFailed`, or an error answer, is a clear failure; 429 / FLOOD_WAIT is passed on with the server's
 *    own number of seconds.
 *
 * Every send and fetch is gated on the live identity (account + epoch) before dispatch and again when the answer arrives.
 * It must run on a worker thread: it blocks (bounded) and refuses to run on the TDLib dispatch thread.
 */
internal class TdlibPlaylistSyncTransport(
    private val hub: TdlibSyncHub,
    private val account: Long,
    private val epoch: Long,
    private val channelId: Long,
    private val reader: TdlibHistoryReader,
    private val timeouts: TdlibTransportTimeouts = TdlibTransportTimeouts(),
    /** Hard stop for anything that would write to the channel (consent to transmit). Checked on every send. */
    private val sendAllowed: () -> Boolean = { true },
) : PlaylistSyncTransport {
    private var identityVerified = false

    /** getMe must say the same account the sync was started for. Once per instance (the epoch guard covers the rest). */
    private fun verifyIdentity(): SyncErrorKind? {
        if (identityVerified) return null
        if (!hub.identityHolds(account, epoch)) return hub.staleKind(account)
        return when (val me = hub.call(account, epoch, "getMe", timeouts.requestMs)) {
            is TdlibSyncHub.Reply.Ok -> if (me.json.optLong("id") == account) {
                identityVerified = true
                null
            } else SyncErrorKind.UNAUTHORIZED
            is TdlibSyncHub.Reply.Err -> TdlibSyncErrors.kindOf(me.code, me.message).first
            TdlibSyncHub.Reply.Timeout -> SyncErrorKind.TIMEOUT
            TdlibSyncHub.Reply.Aborted -> hub.staleKind(account)
        }
    }

    override fun send(channelId: Long, text: String): SendResult {
        require(text.startsWith(PlaylistSyncProtocol.MARKER)) { "only sync messages are ever sent" }
        require(text.length <= PlaylistSyncProtocol.MAX_TEXT_CHARS)
        if (channelId != this.channelId) return SendResult.Failed(SyncErrorKind.CHANNEL_MISSING)
        if (!sendAllowed()) return SendResult.Failed(SyncErrorKind.UNAUTHORIZED)
        verifyIdentity()?.let { return SendResult.Failed(it) }
        if (!hub.connectionIsUsable()) return SendResult.Failed(SyncErrorKind.NETWORK) // nothing was handed over
        val watch = hub.openSendWatch(channelId)
        try {
            val reply = hub.call(account, epoch, "sendMessage", timeouts.requestMs) { request ->
                request.put("chat_id", channelId)
                request.put("options", JSONObject().put("@type", "messageSendOptions").put("disable_notification", true))
                request.put(
                    "input_message_content",
                    JSONObject().put("@type", "inputMessageText")
                        .put("text", JSONObject().put("@type", "formattedText").put("text", text))
                        .put("clear_draft", true)
                )
            }
            val provisional = when (reply) {
                is TdlibSyncHub.Reply.Err -> {
                    hub.metrics.count(SyncMetrics.Event.SEND_FAILED)
                    return TdlibSyncErrors.kindOf(reply.code, reply.message).let { SendResult.Failed(it.first, it.second) }
                }
                // Handed over, no answer (or the identity changed meanwhile): it may or may not be stored.
                TdlibSyncHub.Reply.Timeout, TdlibSyncHub.Reply.Aborted -> return unknown()
                is TdlibSyncHub.Reply.Ok -> reply.json
            }
            val oldId = provisional.optLong("id", 0L)
            if (provisional.optString("@type") != "message" || oldId == 0L || provisional.optLong("chat_id") != channelId || !sameText(provisional, text)) {
                hub.metrics.count(SyncMetrics.Event.SEND_PAYLOAD_MISMATCH)
                return unknown()
            }
            val outcome = watch.await(oldId, timeouts.sendAckMs) ?: return unknown()
            // Callback-time gate: nothing is acknowledged for an identity that is gone.
            if (!hub.identityHolds(account, epoch)) return unknown()
            return when (outcome) {
                is SendOutcome.Failed -> {
                    hub.metrics.count(SyncMetrics.Event.SEND_FAILED)
                    TdlibSyncErrors.kindOf(outcome.code, outcome.message).let { SendResult.Failed(it.first, it.second) }
                }
                is SendOutcome.Succeeded -> {
                    val finalMessage = outcome.message
                    val finalId = finalMessage.optLong("id", 0L)
                    val stillPending = finalMessage.optJSONObject("sending_state") != null
                    if (finalId <= 0L || finalId == oldId || stillPending || finalMessage.optLong("chat_id") != channelId || !sameText(finalMessage, text)) {
                        hub.metrics.count(SyncMetrics.Event.SEND_PAYLOAD_MISMATCH)
                        unknown()
                    } else {
                        hub.metrics.count(SyncMetrics.Event.SEND_ACKED)
                        SendResult.Sent(finalId)
                    }
                }
            }
        } finally {
            watch.close()
        }
    }

    private fun unknown(): SendResult {
        hub.metrics.count(SyncMetrics.Event.SEND_UNKNOWN)
        return SendResult.Unknown
    }

    private fun sameText(message: JSONObject, expected: String): Boolean {
        val content = message.optJSONObject("content") ?: return false
        return content.optString("@type") == "messageText" && content.optJSONObject("text")?.optString("text") == expected
    }

    override fun fetchAfter(channelId: Long, afterServerId: Long, limit: Int): FetchResult {
        if (channelId != this.channelId) return FetchResult.Failed(SyncErrorKind.CHANNEL_MISSING)
        verifyIdentity()?.let { return FetchResult.Failed(it) }
        return reader.fetchAfter(channelId, afterServerId, limit)
    }
}
