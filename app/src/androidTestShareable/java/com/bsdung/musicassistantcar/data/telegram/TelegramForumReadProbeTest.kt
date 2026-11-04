package com.bsdung.musicassistantcar.data.telegram

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit S1 read-only probe. Can run against the installed baseline: uses only pre-existing owner/hub APIs.
 * No login, new client, writes to Telegram, DB access, media downloads, payload/identity logging or session access.
 */
@RunWith(AndroidJUnit4::class)
class TelegramForumReadProbeTest {
    @Test fun readExistingForumAndRegularSource() {
        assumeTrue("Explicit read-only device probe; skipped by ordinary connected tests",
            InstrumentationRegistry.getArguments().getString("forumReadProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, TelegramPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val hub = TdlibSyncHub(ShareableTdlibOwner)
        var requests = 0
        fun report(value: String) {
            Log.i("MeloForumProbe", value)
            instrumentation.sendStatus(0, Bundle().apply { putString("forum_probe", value) })
        }
        try {
            val deadline = System.currentTimeMillis() + 50_000
            while (ShareableTdlibOwner.authorizedAccount() == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            val account = ShareableTdlibOwner.authorizedAccount()
            assertTrue("BLOCKED: existing account not ready; no login attempted", account != null)
            val epoch = ShareableTdlibOwner.authEpoch()
            assertTrue("BLOCKED: owner sink busy; no existing binding replaced", hub.open())
            fun call(type: String, configure: (JSONObject) -> Unit = {}): JSONObject {
                requests++
                return when (val reply = hub.call(requireNotNull(account), epoch, type, 45_000, configure)) {
                    is TdlibSyncHub.Reply.Ok -> reply.json
                    is TdlibSyncHub.Reply.Err -> error("BLOCKED: $type code=${reply.code}")
                    TdlibSyncHub.Reply.Timeout -> error("BLOCKED: $type timeout identity=${hub.identityHolds(account, epoch)} connection=${hub.connectionIsUsable()}")
                    TdlibSyncHub.Reply.Aborted -> error("BLOCKED: $type aborted identity=${hub.identityHolds(account, epoch)}")
                }
            }
            // Loading the existing main list is read-only. A 404 means TDLib has already loaded it all.
            requests++
            val loaded = hub.call(requireNotNull(account), epoch, "loadChats", 20_000) {
                it.put("chat_list", JSONObject().put("@type", "chatListMain")).put("limit", 100)
            }
            assertTrue("BLOCKED: chat list unavailable", loaded is TdlibSyncHub.Reply.Ok ||
                (loaded is TdlibSyncHub.Reply.Err && loaded.code == 404))
            val chats = call("getChats") {
                it.put("chat_list", JSONObject().put("@type", "chatListMain")).put("limit", 100)
            }.getJSONArray("chat_ids")
            var regular = false
            var regularChat = 0L
            var forumChat = 0L
            var forumTopics: List<JSONObject> = emptyList()
            var generalSeen = false
            var catalogueEnd = false
            var cataloguePages = 0
            var cursorRepeated = false
            for (i in 0 until minOf(chats.length(), 100)) {
                if (regular && forumChat != 0L) break
                val chat = call("getChat") { it.put("chat_id", chats.getLong(i)) }
                val chatId = chat.getLong("id")
                val type = chat.getJSONObject("type")
                if (type.optString("@type") != "chatTypeSupergroup") { regular = true; regularChat = chatId; continue }
                val group = call("getSupergroup") { it.put("supergroup_id", type.getLong("supergroup_id")) }
                if (!group.getBoolean("is_forum")) { regular = true; regularChat = chatId; continue }
                if (forumChat != 0L) continue
                val topics = LinkedHashMap<Int, JSONObject>()
                var date = 0
                var message = 0L
                var topic = 0
                val cursors = HashSet<Triple<Int, Long, Int>>()
                for (pageIndex in 0 until 4) {
                    val page = call("getForumTopics") {
                        it.put("chat_id", chatId).put("query", "").put("offset_date", date)
                            .put("offset_message_id", message).put("offset_forum_topic_id", topic).put("limit", 50)
                    }
                    assertTrue("Schema mismatch: forumTopics", page.optString("@type") == "forumTopics")
                    cataloguePages++
                    val rows = page.getJSONArray("topics")
                    for (j in 0 until rows.length()) {
                        val row = rows.getJSONObject(j)
                        val info = row.getJSONObject("info")
                        assertTrue("Schema mismatch: forumTopicInfo", info.getString("@type") == "forumTopicInfo" && info.getLong("chat_id") == chatId)
                        info.getString("name") // validate only; never log
                        row.getBoolean("is_pinned")
                        info.getBoolean("is_closed")
                        if (info.getBoolean("is_general")) generalSeen = true
                        if (!info.getBoolean("is_hidden")) topics[info.getInt("forum_topic_id")] = row
                    }
                    date = page.getInt("next_offset_date")
                    message = page.getLong("next_offset_message_id")
                    topic = page.getInt("next_offset_forum_topic_id")
                    if (date == 0 && message == 0L && topic == 0) { catalogueEnd = true; break }
                    if (!cursors.add(Triple(date, message, topic))) { cursorRepeated = true; break }
                }
                if (topics.size >= 2) { forumChat = chatId; forumTopics = topics.values.toList() }
            }
            report("catalogue pages=$cataloguePages visible=${forumTopics.size} general=$generalSeen end=$catalogueEnd repeated=$cursorRepeated regular=$regular")
            assertTrue("BLOCKED: no existing forum with two visible topics in bounded discovery", forumChat != 0L)
            assertTrue("BLOCKED: no regular source in bounded discovery", regular)
            val regularHistory = call("getChatHistory") {
                it.put("chat_id", regularChat).put("from_message_id", 0).put("offset", 0).put("limit", 1).put("only_local", false)
            }
            assertTrue("Regular source history schema mismatch", regularHistory.getString("@type") == "messages")
            report("regular_history read=true size=${regularHistory.getJSONArray("messages").length()}")
            var finished = 0
            var singletonProbes = 0
            var terminalSingletons = 0
            var matched = 0
            var missing = 0
            var historyPages = 0
            val samples = forumTopics.filterNot { it.getJSONObject("info").getBoolean("is_general") }.take(6)
            for (row in samples) {
                if (finished == 2) break
                val topicId = row.getJSONObject("info").getInt("forum_topic_id")
                // Probe-only seed: c15d3f5 ForumTopicManager.cpp uses the creation service's server id;
                // MessageId.h encodes a server id with shift 20. Confirm the returned service message first,
                // then use only its ORIGINAL raw id for history. Product pagination never needs this conversion.
                requests++
                val seed = hub.call(requireNotNull(account), epoch, "getMessage", 45_000) {
                    it.put("chat_id", forumChat).put("message_id", topicId.toLong() shl 20)
                }
                if (seed is TdlibSyncHub.Reply.Err && seed.code in setOf(400, 404)) {
                    report("boundary_seed unavailable=true code=${seed.code}")
                    continue
                }
                if (seed is TdlibSyncHub.Reply.Err) report("boundary_seed rejected=true code=${seed.code}")
                assertTrue("BLOCKED: creation service not available for bounded terminal probe", seed is TdlibSyncHub.Reply.Ok)
                val creation = (seed as TdlibSyncHub.Reply.Ok).json
                assertTrue("Creation seed returned foreign chat", creation.getLong("chat_id") == forumChat)
                if (creation.getJSONObject("content").getString("@type") != "messageForumTopicCreated") {
                    report("boundary_seed creation=false"); continue
                }
                // TDLib may omit the creation service from topic history. Read the first actual message
                // immediately after it, using the documented negative offset (bounded to ONE message).
                val earliest = call("getForumTopicHistory") {
                    it.put("chat_id", forumChat).put("forum_topic_id", topicId)
                        .put("from_message_id", creation.getLong("id")).put("offset", -1).put("limit", 1)
                }.getJSONArray("messages")
                report("boundary_seed_page size=${earliest.length()}")
                if (earliest.length() != 1) continue
                val first = earliest.getJSONObject(0)
                val firstTopic = first.getJSONObject("topic_id")
                assertTrue("Earliest message scope mismatch", first.getLong("chat_id") == forumChat &&
                    firstTopic.getString("@type") == "messageTopicForum" && firstTopic.getInt("forum_topic_id") == topicId)
                var from = first.getLong("id")
                var lastBoundarySize = 1
                val seen = HashSet<Long>()
                for (pageIndex in 0 until 4) {
                    val page = call("getForumTopicHistory") {
                        it.put("chat_id", forumChat).put("forum_topic_id", topicId).put("from_message_id", from)
                            .put("offset", 0).put("limit", 50)
                    }
                    assertTrue("Schema mismatch: messages", page.getString("@type") == "messages")
                    historyPages++
                    val messages = page.getJSONArray("messages")
                    report("boundary_page size=${messages.length()} index=$pageIndex")
                    if (messages.length() == 0) {
                        finished++
                        if (lastBoundarySize == 1) terminalSingletons++
                        break
                    }
                    lastBoundarySize = messages.length()
                    var oldest = Long.MAX_VALUE
                    for (j in 0 until messages.length()) {
                        val message = messages.getJSONObject(j)
                        assertTrue("History returned foreign chat", message.getLong("chat_id") == forumChat)
                        val id = message.getLong("id")
                        assertTrue("History moved forward", from == 0L || id <= from)
                        oldest = minOf(oldest, id)
                        seen.add(id)
                        val topicInfo = message.optJSONObject("topic_id")
                        if (topicInfo == null) missing++ else {
                            assertTrue("History returned conflicting topic", topicInfo.getString("@type") == "messageTopicForum" && topicInfo.getInt("forum_topic_id") == topicId)
                            matched++
                        }
                    }
                    if (oldest == from) {
                        assertTrue("Boundary cannot be probed below id 1", from > 1L)
                        singletonProbes++
                        from--
                    } else from = oldest
                }
                report("history unique=${seen.size}")
                // Also prove ordinary newest-to-older paging on the same topic, without exhaustively reading it.
                var headFrom = 0L
                for (pageIndex in 0 until 2) {
                    val head = call("getForumTopicHistory") {
                        it.put("chat_id", forumChat).put("forum_topic_id", topicId).put("from_message_id", headFrom)
                            .put("offset", 0).put("limit", 50)
                    }.getJSONArray("messages")
                    var oldest = Long.MAX_VALUE
                    for (j in 0 until head.length()) {
                        val message = head.getJSONObject(j)
                        val relation = message.getJSONObject("topic_id")
                        assertTrue("Head page scope mismatch", message.getLong("chat_id") == forumChat &&
                            relation.getString("@type") == "messageTopicForum" && relation.getInt("forum_topic_id") == topicId)
                        val raw = message.getLong("id")
                        assertTrue("Head cursor moved forward", headFrom == 0L || raw <= headFrom)
                        oldest = minOf(oldest, raw)
                        matched++
                    }
                    report("head_page size=${head.length()} index=$pageIndex")
                    if (head.length() == 0 || oldest == headFrom) break
                    headFrom = oldest
                }
            }
            report("result topics=2 history_pages=$historyPages terminal=$finished terminal_singletons=$terminalSingletons inclusive_probes=$singletonProbes matched=$matched missing=$missing requests=$requests")
            assertTrue("BLOCKED: terminal history not proven within budget", finished == 2)
            assertTrue("BLOCKED: singleton terminal page not observed", terminalSingletons == 2)
        } finally {
            hub.close()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
