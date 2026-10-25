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
 *  - TDLib cleans the text it is given (a TAB becomes a space), so what comes back is compared with [TdlibText.normalize] of
 *    what was sent; a text that cleaning would change is never sent.
 *  - No update in time, a lost answer, an abort, a payload that is not ours: [SendResult.Unknown], with a sanitised stage. The
 *    operation ids stay unchanged; the coordinator never sends them again and only finds them by reading the history.
 *  - An error answer to sendMessage, or `updateMessageSendFailed` with a 4xx code, is a definite refusal ([SendResult.Failed]);
 *    429 / FLOOD_WAIT is passed on with the server's own number of seconds. A failure with any other code is doubt.
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
        if (channelId != this.channelId) return SendResult.Failed(SyncErrorKind.CHANNEL_MISSING, stage = SyncStage.SEND_PRECHECK_IDENTITY)
        if (!sendAllowed()) return SendResult.Failed(SyncErrorKind.UNAUTHORIZED, stage = SyncStage.SEND_PRECHECK_CONSENT)
        // A text TDLib would change can never be told apart from a foreign one afterwards: do not hand it over at all.
        if (TdlibText.normalize(text) != text) return SendResult.Failed(SyncErrorKind.REJECTED, stage = SyncStage.SEND_PRECHECK_TEXT)
        verifyIdentity()?.let { return SendResult.Failed(it, stage = SyncStage.SEND_PRECHECK_IDENTITY) }
        // nothing was handed over
        if (!hub.connectionIsUsable()) return SendResult.Failed(SyncErrorKind.NETWORK, stage = SyncStage.SEND_PRECHECK_CONNECTION)
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
                // TDLib answered the request with an error. Only a definite 4xx (flood wait included) is a refusal. A 5xx, a
                // timeout-like code or any other number can come from a request that Telegram DID process (REWORK-02):
                // that is doubt, so it is never reported as "not stored".
                is TdlibSyncHub.Reply.Err -> {
                    hub.metrics.count(SyncMetrics.Event.SEND_FAILED)
                    return if (reply.code in 400..499) failed(reply.code, reply.message, SyncStage.SEND_DISPATCH_ERROR)
                    else unknown(SyncStage.SEND_DISPATCH_ERROR, ErrorBucket.of(reply.code))
                }
                // Handed over, no answer (or the identity changed meanwhile): it may or may not be stored.
                TdlibSyncHub.Reply.Timeout -> return unknown(SyncStage.SEND_DISPATCH_TIMEOUT)
                TdlibSyncHub.Reply.Aborted -> return unknown(SyncStage.SEND_DISPATCH_ABORTED)
                is TdlibSyncHub.Reply.Ok -> reply.json
            }
            // TDLib cleans the text on the way in, so what comes back is compared with the CLEANED text.
            val expected = TdlibText.normalize(text)
            val oldId = provisional.optLong("id", 0L)
            val provisionalStage = when {
                provisional.optString("@type") != "message" || oldId == 0L -> SyncStage.SEND_PROVISIONAL_MISMATCH_TYPE
                provisional.optLong("chat_id") != channelId -> SyncStage.SEND_PROVISIONAL_MISMATCH_CHAT
                !sameText(provisional, expected) -> SyncStage.SEND_PROVISIONAL_MISMATCH_TEXT
                else -> null
            }
            if (provisionalStage != null) {
                hub.metrics.count(SyncMetrics.Event.SEND_PAYLOAD_MISMATCH)
                return unknown(provisionalStage)
            }
            val outcome = watch.await(oldId, timeouts.sendAckMs) ?: return unknown(SyncStage.SEND_ACK_TIMEOUT)
            // Callback-time gate: nothing is acknowledged for an identity that is gone.
            if (!hub.identityHolds(account, epoch)) return unknown(SyncStage.SEND_IDENTITY_LOST)
            return when (outcome) {
                is SendOutcome.Failed -> {
                    hub.metrics.count(SyncMetrics.Event.SEND_FAILED)
                    // Only a definite client-side refusal (4xx, flood wait included) means "not stored". Anything else
                    // (5xx, odd codes) may have been processed by the server before it failed: that is doubt, not failure.
                    if (outcome.code in 400..499) failed(outcome.code, outcome.message, SyncStage.SEND_ACK_FAILED)
                    else unknown(SyncStage.SEND_ACK_FAILED, ErrorBucket.of(outcome.code))
                }
                is SendOutcome.Succeeded -> {
                    val finalMessage = outcome.message
                    val finalId = finalMessage.optLong("id", 0L)
                    val stillPending = finalMessage.optJSONObject("sending_state") != null
                    if (finalId <= 0L || finalId == oldId || stillPending || finalMessage.optLong("chat_id") != channelId || !sameText(finalMessage, expected)) {
                        hub.metrics.count(SyncMetrics.Event.SEND_PAYLOAD_MISMATCH)
                        unknown(SyncStage.SEND_ACK_MISMATCH)
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

    private fun unknown(stage: SyncStage, bucket: ErrorBucket = ErrorBucket.NONE): SendResult {
        hub.metrics.count(SyncMetrics.Event.SEND_UNKNOWN)
        return SendResult.Unknown(stage, bucket)
    }

    private fun failed(code: Int, message: String, stage: SyncStage): SendResult =
        TdlibSyncErrors.kindOf(code, message).let { SendResult.Failed(it.first, it.second, stage, ErrorBucket.of(code)) }

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
