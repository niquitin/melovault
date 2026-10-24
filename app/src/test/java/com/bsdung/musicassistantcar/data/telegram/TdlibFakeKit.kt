package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Random
import java.util.TreeMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One Telegram message of the fake cloud. [content] is the TDLib content type; only "messageText" has text. */
internal class FakeMsg(val id: Long, val chatId: Long, var text: String, val content: String = "messageText", var editDate: Int = 0)

internal class FakeChatData(val id: Long, val supergroupId: Long, var title: String, var description: String) {
    var kind = "chatTypeSupergroup"
    var isChannel = true
    var username = ""
    var status = "chatMemberStatusCreator"
    var members = 1
    var autoDelete = 0
    var linkedChat = false
    var archived = false
    var serverSeq = 0L
    val messages = TreeMap<Long, FakeMsg>()
}

/** The Telegram cloud of ONE account. Several devices (gateways) of the same account share one world. Server ids are (seq shl 20) with gaps. */
internal class FakeTdlibWorld(seed: Long = 11L) {
    private val random = Random(seed)
    val chats = LinkedHashMap<Long, FakeChatData>()
    private var nextSupergroup = 1_000L

    @Synchronized
    fun createChannel(
        title: String = SyncChannelSpec.TITLE, description: String = "", withCreateMessage: Boolean = true,
    ): FakeChatData {
        nextSupergroup++
        val chat = FakeChatData(-1_000_000_000_000L - nextSupergroup, nextSupergroup, title, description)
        chats[chat.id] = chat
        if (withCreateMessage) serviceMessage(chat.id)
        return chat
    }

    /** A channel exactly as the app would make it for [account]. */
    @Synchronized
    fun createSyncChannel(account: Long): FakeChatData = createChannel(description = SyncChannelSpec.descriptionOf(account))

    @Synchronized
    private fun nextId(chat: FakeChatData, gap: Boolean): Long {
        chat.serverSeq += if (gap) 1 + random.nextInt(3) else 1
        return chat.serverSeq shl 20
    }

    @Synchronized
    fun serviceMessage(chatId: Long): Long {
        val chat = chats.getValue(chatId)
        val m = FakeMsg(nextId(chat, false), chatId, "", "messageChannelCreate")
        chat.messages[m.id] = m
        return m.id
    }

    @Synchronized
    fun post(chatId: Long, text: String, gap: Boolean = true): Long {
        val chat = chats.getValue(chatId)
        val m = FakeMsg(nextId(chat, gap), chatId, text)
        chat.messages[m.id] = m
        return m.id
    }

    @Synchronized
    fun postMany(chatId: Long, count: Int, prefix: String = "note"): List<Long> = (1..count).map { post(chatId, "$prefix $it") }

    @Synchronized
    fun delete(chatId: Long, id: Long) {
        chats.getValue(chatId).messages.remove(id)
    }

    @Synchronized
    fun edit(chatId: Long, id: Long, newText: String) {
        chats.getValue(chatId).messages.getValue(id).apply {
            text = newText
            editDate = 1_700_000_000
        }
    }

    @Synchronized
    fun ids(chatId: Long): List<Long> = chats.getValue(chatId).messages.keys.toList()

    @Synchronized
    fun texts(chatId: Long): List<String> = chats.getValue(chatId).messages.values.filter { it.content == "messageText" }.map { it.text }
}

internal enum class SendMode {
    OK_UPDATE_AFTER, OK_UPDATE_BEFORE, NO_UPDATE_STORED, NO_UPDATE_NOT_STORED, NO_REPLY_STORED, NO_REPLY_NOT_STORED,
    FAIL_UPDATE, ERROR_REPLY, DUPLICATE_SUCCESS, FAIL_AFTER_SUCCESS, WRONG_TEXT, WRONG_CHAT, SAME_ID,
    /** The provisional answer itself is wrong: another type, another chat, another text. */
    WRONG_PROVISIONAL_TYPE, WRONG_PROVISIONAL_CHAT, WRONG_PROVISIONAL_TEXT,
}

/**
 * A scripted native TDLib JSON client: answers are queued for the receive loop exactly as the real library would
 * deliver them, with @client_id and @extra. It records every request. Nothing in it is a real Telegram.
 */
internal class FakeTdlibJsonGateway(val world: FakeTdlibWorld, var userId: Long) : TdlibJsonPort {
    val incoming = LinkedBlockingQueue<String>()
    val requests = CopyOnWriteArrayList<JSONObject>()
    val sendModes = java.util.ArrayDeque<SendMode>()
    var defaultSendMode = SendMode.OK_UPDATE_AFTER
    var sendError: Pair<Int, String> = 429 to "Too Many Requests: retry after 35"
    var held = false
    private val heldOut = CopyOnWriteArrayList<String>()
    /** TDLib "chooses" how many messages it returns: at most this many per getChatHistory. */
    var pageCap = Int.MAX_VALUE
    var beforeHistory: ((JSONObject) -> Unit)? = null
    var historyFilter: ((JSONObject, MutableList<Any>) -> Unit)? = null
    var sendHook: (() -> Unit)? = null
    var onCreate: (() -> Unit)? = null
    var clientsCreated = 0
    var closeRequests = 0
    var nonTextSends = 0
    /** loadChats loads this many unloaded chats per call; "all loaded" is answered with TDLib's 404. */
    var loadStep = Int.MAX_VALUE
    var getChatsClamp = 1000
    var neverFinishLoad = false
    private val unloaded = HashSet<Long>()
    enum class ForwardFault { ERROR_REPLY, MALFORMED, SILENT }
    /** Faults applied, in order, to the next getChatHistory requests that read FORWARD (negative offset). */
    val forwardFaults = java.util.ArrayDeque<ForwardFault>()

    /** Every chat that exists now is unknown to this fresh client until loadChats brings it in (new chats arrive by update). */
    fun startUnloaded() {
        unloaded.addAll(world.chats.keys)
    }
    private val failures = HashMap<String, java.util.ArrayDeque<Pair<Int, String>>>()
    private val silent = HashSet<String>()
    private var clientId = 0
    private var provisional = 0L

    fun failNext(type: String, code: Int, message: String) {
        failures.getOrPut(type) { java.util.ArrayDeque() }.add(code to message)
    }

    fun staySilent(type: String) = silent.add(type)

    fun count(type: String): Int = requests.count { it.optString("@type") == type }
    fun typesSeen(): List<String> = requests.map { it.optString("@type") }

    override fun newClientId(): Int {
        clientsCreated++
        clientId = 77
        return clientId
    }

    override fun requestClose(clientId: Int) {
        closeRequests++
    }

    override fun receiveOne(timeoutSeconds: Double): String? = incoming.poll((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS)

    override fun send(clientId: Int, jsonRequest: String) {
        val request = JSONObject(jsonRequest)
        requests.add(request)
        handle(request)
    }

    /** Pushes an update (no @extra) to the receive loop. */
    fun emit(update: JSONObject) = push(update.put("@client_id", clientId))

    fun connectionState(name: String) =
        emit(JSONObject().put("@type", "updateConnectionState").put("state", JSONObject().put("@type", name)))

    fun release() {
        heldOut.forEach { incoming.add(it) }
        heldOut.clear()
    }

    fun dropHeld() = heldOut.clear()

    fun heldCount(): Int = heldOut.size

    fun releaseReversed() {
        heldOut.reversed().forEach { incoming.add(it) }
        heldOut.clear()
    }

    private fun push(json: JSONObject) {
        if (held) heldOut.add(json.toString()) else incoming.add(json.toString())
    }

    private fun reply(request: JSONObject, body: JSONObject) {
        body.put("@client_id", clientId)
        request.optString("@extra").takeIf { it.isNotEmpty() }?.let { body.put("@extra", it) }
        push(body)
    }

    private fun error(request: JSONObject, code: Int, message: String) =
        reply(request, JSONObject().put("@type", "error").put("code", code).put("message", message))

    fun msgJson(m: FakeMsg): JSONObject {
        val content = JSONObject().put("@type", m.content)
        if (m.content == "messageText") content.put("text", JSONObject().put("@type", "formattedText").put("text", m.text).put("entities", JSONArray()))
        return JSONObject().put("@type", "message").put("id", m.id).put("chat_id", m.chatId).put("is_outgoing", true)
            .put("edit_date", m.editDate).put("date", 1_700_000_000).put("content", content)
            .put("sender_id", JSONObject().put("@type", "messageSenderChat").put("chat_id", m.chatId))
    }

    private fun handle(r: JSONObject) {
        val type = r.optString("@type")
        failures[type]?.poll()?.let { error(r, it.first, it.second); return }
        if (type in silent) return
        when (type) {
            "getMe" -> reply(r, JSONObject().put("@type", "user").put("id", userId))
            "getChat" -> {
                val c = world.chats[r.optLong("chat_id")] ?: return error(r, 400, "Chat not found")
                reply(r, JSONObject().put("@type", "chat").put("id", c.id).put("title", c.title).put("message_auto_delete_time", c.autoDelete)
                    .put("type", JSONObject().put("@type", c.kind).put("supergroup_id", c.supergroupId).put("is_channel", c.isChannel)))
            }
            "loadChats" -> {
                val archive = r.optJSONObject("chat_list")?.optString("@type") == "chatListArchive"
                val next = unloaded.filter { world.chats[it]?.archived == archive }.take(minOf(loadStep, r.optInt("limit", 100)).coerceAtLeast(1))
                if (neverFinishLoad) return reply(r, JSONObject().put("@type", "ok"))
                if (next.isEmpty()) return error(r, 404, "Not Found") // TDLib: all chats of the list are loaded
                unloaded.removeAll(next.toSet())
                reply(r, JSONObject().put("@type", "ok"))
            }
            "getChats" -> {
                val archive = r.optJSONObject("chat_list")?.optString("@type") == "chatListArchive"
                val limit = minOf(r.optInt("limit"), getChatsClamp)
                val ids = JSONArray()
                world.chats.values.filter { it.id !in unloaded && it.archived == archive }.take(limit).forEach { ids.put(it.id) }
                reply(r, JSONObject().put("@type", "chats").put("total_count", ids.length()).put("chat_ids", ids))
            }
            "searchChats" -> {
                // Offline title search among chats this client already has loaded, at most `limit` of them.
                val q = r.optString("query").lowercase()
                val ids = JSONArray()
                world.chats.values.filter { it.id !in unloaded && it.title.lowercase().contains(q) }.take(r.optInt("limit", 50)).forEach { ids.put(it.id) }
                reply(r, JSONObject().put("@type", "chats").put("total_count", ids.length()).put("chat_ids", ids))
            }
            "getSupergroup" -> {
                val c = world.chats.values.firstOrNull { it.supergroupId == r.optLong("supergroup_id") } ?: return error(r, 400, "Supergroup not found")
                val names = JSONArray().also { if (c.username.isNotEmpty()) it.put(c.username) }
                reply(r, JSONObject().put("@type", "supergroup").put("id", c.supergroupId).put("is_channel", c.isChannel)
                    .put("usernames", JSONObject().put("active_usernames", names)).put("status", JSONObject().put("@type", c.status))
                    .put("member_count", c.members).put("has_linked_chat", c.linkedChat))
            }
            "getSupergroupFullInfo" -> {
                val c = world.chats.values.firstOrNull { it.supergroupId == r.optLong("supergroup_id") } ?: return error(r, 400, "Supergroup not found")
                reply(r, JSONObject().put("@type", "supergroupFullInfo").put("description", c.description).put("member_count", c.members))
            }
            "createNewSupergroupChat" -> {
                onCreate?.invoke()
                val c = world.createChannel(r.optString("title"), r.optString("description"))
                c.isChannel = r.optBoolean("is_channel")
                c.autoDelete = r.optInt("message_auto_delete_time")
                reply(r, JSONObject().put("@type", "chat").put("id", c.id).put("title", c.title)
                    .put("type", JSONObject().put("@type", c.kind).put("supergroup_id", c.supergroupId).put("is_channel", c.isChannel)))
            }
            "getChatHistory" -> history(r)
            "sendMessage" -> sendMessage(r)
            else -> reply(r, JSONObject().put("@type", "ok"))
        }
    }

    private fun history(r: JSONObject) {
        beforeHistory?.invoke(r)
        if (r.optInt("offset") < 0) {
            when (forwardFaults.poll()) {
                ForwardFault.ERROR_REPLY -> return error(r, 500, "Internal error")
                ForwardFault.MALFORMED -> return reply(r, JSONObject().put("@type", "messages").put("total_count", 3))
                ForwardFault.SILENT -> return
                null -> Unit
            }
        }
        val chat = world.chats[r.optLong("chat_id")] ?: return error(r, 400, "Chat not found")
        val from = r.optLong("from_message_id")
        val offset = r.optInt("offset")
        val limit = minOf(r.optInt("limit"), 100, pageCap)
        val desc: List<FakeMsg> = synchronized(world) { chat.messages.values.sortedByDescending { it.id } }
        val picked: List<FakeMsg> = if (offset == 0) {
            desc.filter { from == 0L || it.id <= from }.take(limit)
        } else {
            val newer = desc.filter { it.id > from }.sortedBy { it.id }.take(-offset).sortedByDescending { it.id }
            newer + desc.filter { it.id <= from }.take(maxOf(0, limit - newer.size))
        }
        val list = picked.map<FakeMsg, Any> { msgJson(it) }.toMutableList()
        historyFilter?.invoke(r, list)
        val array = JSONArray()
        list.forEach { array.put(it) }
        reply(r, JSONObject().put("@type", "messages").put("total_count", list.size).put("messages", array))
    }

    private fun sendMessage(r: JSONObject) {
        sendHook?.invoke()
        val content = r.optJSONObject("input_message_content")
        if (content?.optString("@type") != "inputMessageText") nonTextSends++
        // Real TDLib cleans the text on the way in (clean_input_string + trim): tabs become spaces. The fake must too,
        // otherwise a protocol that only works with raw text looks fine here and fails against Telegram.
        val text = FakeTdlibClientText.clean(content?.optJSONObject("text")?.optString("text").orEmpty())
        val chatId = r.optLong("chat_id")
        if (!world.chats.containsKey(chatId)) return error(r, 400, "Chat not found")
        val mode = sendModes.poll() ?: defaultSendMode
        val old = ++provisional + 1_000L
        fun provisionalMessage() = msgJson(FakeMsg(old, chatId, text)).put("sending_state", JSONObject().put("@type", "messageSendingStatePending"))
        fun success(finalMessage: JSONObject) =
            JSONObject().put("@type", "updateMessageSendSucceeded").put("message", finalMessage).put("old_message_id", old)
        fun store(): FakeMsg = world.post(chatId, text).let { world.chats.getValue(chatId).messages.getValue(it) }
        when (mode) {
            SendMode.OK_UPDATE_AFTER -> {
                val m = store()
                reply(r, provisionalMessage())
                emit(success(msgJson(m)))
            }
            SendMode.OK_UPDATE_BEFORE -> {
                val m = store()
                emit(success(msgJson(m)))
                reply(r, provisionalMessage())
            }
            SendMode.DUPLICATE_SUCCESS -> {
                val m = store()
                reply(r, provisionalMessage())
                emit(success(msgJson(m)))
                emit(success(msgJson(m)))
            }
            SendMode.FAIL_AFTER_SUCCESS -> {
                val m = store()
                reply(r, provisionalMessage())
                emit(success(msgJson(m)))
                emit(JSONObject().put("@type", "updateMessageSendFailed").put("message", provisionalMessage()).put("old_message_id", old)
                    .put("error", JSONObject().put("code", 500).put("message", "late failure")))
            }
            SendMode.NO_UPDATE_STORED -> { store(); reply(r, provisionalMessage()) }
            SendMode.NO_UPDATE_NOT_STORED -> reply(r, provisionalMessage())
            SendMode.NO_REPLY_STORED -> store()
            SendMode.NO_REPLY_NOT_STORED -> Unit
            SendMode.FAIL_UPDATE -> {
                reply(r, provisionalMessage())
                emit(JSONObject().put("@type", "updateMessageSendFailed").put("message", provisionalMessage()).put("old_message_id", old)
                    .put("error", JSONObject().put("code", sendError.first).put("message", sendError.second)))
            }
            SendMode.ERROR_REPLY -> error(r, sendError.first, sendError.second)
            SendMode.WRONG_TEXT -> {
                val m = store()
                reply(r, provisionalMessage())
                emit(success(msgJson(m).also { it.getJSONObject("content").getJSONObject("text").put("text", text + " tampered") }))
            }
            SendMode.WRONG_CHAT -> {
                val m = store()
                reply(r, provisionalMessage())
                emit(success(msgJson(m).put("chat_id", chatId + 1)))
            }
            SendMode.WRONG_PROVISIONAL_TYPE -> { store(); reply(r, JSONObject().put("@type", "ok")) }
            SendMode.WRONG_PROVISIONAL_CHAT -> { store(); reply(r, provisionalMessage().put("chat_id", chatId + 1)) }
            SendMode.WRONG_PROVISIONAL_TEXT -> {
                store()
                reply(r, provisionalMessage().also { it.getJSONObject("content").getJSONObject("text").put("text", text + " x") })
            }
            SendMode.SAME_ID -> {
                store()
                reply(r, provisionalMessage())
                emit(success(provisionalMessage().also { it.remove("sending_state") }))
            }
        }
    }
}

/**
 * An INDEPENDENT copy of what TDLib (pinned c15d3f5) does to an outgoing text: td/telegram/misc.cpp clean_input_string()
 * - every byte 0..32 except '\n' becomes ' ' (so TAB becomes a space), '\r' is dropped, U+2028..U+202E are dropped -
 * then fix_formatted_text() trims spaces and newlines at both ends. Kept apart from production code on purpose.
 */
internal object FakeTdlibClientText {
    fun clean(text: String): String {
        val out = StringBuilder(text.length)
        for (ch in text) {
            val c = ch.code
            when {
                ch == '\n' -> out.append(ch)
                ch == '\r' -> Unit
                c <= 32 -> out.append(' ')
                c in 0x2028..0x202E -> Unit
                else -> out.append(ch)
            }
        }
        return out.toString().trim { it == ' ' || it == '\n' }
    }
}

internal fun waitUntil(timeoutMs: Long = 3_000L, condition: () -> Boolean): Boolean {
    val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    while (System.nanoTime() < end) {
        if (condition()) return true
        Thread.sleep(5)
    }
    return condition()
}

/** The real [TdlibOwnerCore] with its receive loop, on top of a fake native client. */
internal class TdlibRig(val world: FakeTdlibWorld, val user: Long, val dir: File) {
    val gateway = FakeTdlibJsonGateway(world, user)
    val core = TdlibOwnerCore(gateway)
    val uiEvents = CopyOnWriteArrayList<JSONObject>()
    val playbackEvents = CopyOnWriteArrayList<JSONObject>()
    val ui: (JSONObject) -> Unit = { uiEvents.add(it) }
    val playback: (JSONObject) -> Unit = { playbackEvents.add(it) }

    fun start(verified: Boolean = true, withPlayback: Boolean = false): TdlibRig {
        core.attachUi(dir, ui)
        if (withPlayback) core.bindPlayback(playback)
        if (verified) core.markGetMeVerified(user)
        return this
    }

    fun stop() {
        core.stopReceiverForTests()
    }

    /** What a logout and a new login of [account] look like to the owner. */
    fun relogin(account: Long) {
        core.revokeAuthorization()
        core.markGetMeVerified(account)
    }
}

/** A recording sink for owner tests. */
internal class RecordingSink(val block: ((JSONObject) -> Unit)? = null) : SyncEventSink {
    val events = CopyOnWriteArrayList<JSONObject>()
    @Volatile var overflow = 0
    @Volatile var closed = 0
    override fun onEvent(event: JSONObject) {
        block?.invoke(event)
        events.add(event)
    }
    override fun onOverflow() { overflow++ }
    override fun onClientClosed() { closed++ }
}

/** One device of the account: real owner core on a fake native client, real V2 session, real service. Consent starts all granted when [consent] is true. */
internal class SyncRig(
    val name: String,
    root: File,
    val world: FakeTdlibWorld,
    val account: TelegramUserId,
    channelId: Long,
    seed: Int,
    val config: SyncConfig = SyncConfig(),
    buildEnabled: Boolean = true,
    consent: Boolean = true,
    private val timeouts: TdlibTransportTimeouts = TdlibTransportTimeouts(requestMs = 2_000L, sendAckMs = 400L),
    private val limits: HistoryLimits = HistoryLimits(requestTimeoutMs = 2_000L),
    private val discoveryLimits: DiscoveryLimits = DiscoveryLimits(),
    /** A fake TDLib may be driven past the production cap of 3 operations; tests of the cap itself pass the real one. */
    private val sendBudgetCap: Int = TEST_SEND_BUDGET,
    private val createUnlocked: Boolean = true,
) {
    val tdlib = TdlibRig(world, account.value, File(root, "tdlib-slot").apply { mkdirs() }).start()
    val device = FakeSyncDevice(
        name, File(root, "device").apply { mkdirs() }, account, FakeTelegramChannel(channelId = channelId), seed, config,
        bindOnFirstBoot = channelId != 0L,
    )
    val consentStore = InMemorySyncConsentStore()
    val actions = SyncConsentActions(consentStore)
    private val build = buildEnabled
    var service = newService()

    init {
        if (consent) {
            check(actions.enable(account, true))
            check(actions.allowChannelCreation(account))
            check(actions.allowInitialTransmit(account))
        }
    }

    private fun newService() = TdlibPlaylistSyncService(
        tdlib.core, device.session, SyncGate(build, consentStore), { device.nowMs }, config, timeouts, limits, discoveryLimits,
        sendBudgetCap = sendBudgetCap, channelCreationUnlocked = createUnlocked,
    )

    fun restart() {
        device.restart(bindChannel = false)
        service = newService()
    }

    fun run(mode: SyncMode = SyncMode.INCREMENTAL, budget: Int = TEST_SEND_BUDGET): SyncReport =
        (service.runSync(account, mode, budget) as Gated.Done).value

    fun stop() = tdlib.stop()
}

internal fun songId(n: Int): Long = n * 4_194_304L
