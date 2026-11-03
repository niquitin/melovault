package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TelegramForumContractTest {
    private val contract = TelegramForumContract
    private fun fixture(name: String) = JSONObject(requireNotNull(javaClass.getResource("/telegram-forum/$name.json")).readText())
    private fun message(id: Long = 9000000001L, chat: Long = -10001L, topic: String? = null): JSONObject =
        JSONObject().put("@type", "message").put("id", id).put("chat_id", chat)
            .put("content", JSONObject().put("@type", "messageText"))
            .also { if (topic != null) it.put("topic_id", JSONObject(topic)) }
    private fun history(vararg messages: JSONObject) = JSONObject().put("@type", "messages")
        .put("messages", org.json.JSONArray(messages.toList()))

    @Test fun threeWayForumDetectionRequiresConfirmedSupergroup() {
        assertEquals(TelegramForumContract.ForumState.FORUM, contract.forumState(JSONObject("""{"@type":"supergroup","is_forum":true}""")))
        assertEquals(TelegramForumContract.ForumState.REGULAR, contract.forumState(JSONObject("""{"@type":"supergroup","is_forum":false}""")))
        assertThrows(IllegalArgumentException::class.java) { contract.forumState(JSONObject("""{"@type":"supergroup"}""")) }
    }

    @Test fun catalogueReadsInfoFlagsAndExactThreePartCursorWithoutNarrowingMessageIds() {
        val page = contract.topicPage(fixture("topics-first"), -10001L)
        assertEquals(listOf(1, 71), page.topics.map { it.id })
        assertTrue(page.topics.first().isGeneral)
        assertTrue(page.topics.first().isHidden)
        assertTrue(page.topics.last().isPinned)
        assertTrue(page.topics.last().isClosed)
        assertEquals(TelegramForumContract.ListCursor(1700000000, 9000000001L, 71), page.next)
        assertFalse(page.complete)
        val request = contract.topicsRequest(-10001L, page.next)
        assertEquals(9000000001L, request.getLong("offset_message_id"))
        assertEquals(71, request.getInt("offset_forum_topic_id"))
        assertEquals(1700000000, request.getInt("offset_date"))
    }

    @Test fun emptyShortOrRepeatedCatalogueDoesNotInventCompletion() {
        val json = fixture("topics-first")
        val cursor = contract.topicPage(json, -10001L).next
        assertTrue(contract.topicPage(json, -10001L, cursor).stalled)
        json.put("topics", org.json.JSONArray())
        assertFalse(contract.topicPage(json, -10001L).complete)
        json.remove("next_offset_date")
        assertThrows(org.json.JSONException::class.java) { contract.topicPage(json, -10001L) }
        assertTrue(contract.topicPage(fixture("topics-end"), -10001L).complete)
    }

    @Test fun duplicateIdsAreDeduplicatedWithinPageAndRemainScopedToRequestedChat() {
        val json = fixture("topics-first")
        json.getJSONArray("topics").put(json.getJSONArray("topics").getJSONObject(1))
        assertEquals(2, contract.topicPage(json, -10001L).topics.size)
        assertThrows(IllegalArgumentException::class.java) { contract.topicPage(json, -10002L) }
    }

    @Test fun onlyTypedInt32ForumMetadataClassifiesGlobalMessages() {
        assertEquals(TelegramForumContract.Relation.Known(71), contract.relation(message(topic = """{"@type":"messageTopicForum","forum_topic_id":71}"""), -10001L))
        for (type in listOf("messageTopicThread", "messageTopicSavedMessages", "messageTopicDirectMessages")) {
            assertEquals(TelegramForumContract.Relation.Unknown, contract.relation(message(topic = """{"@type":"$type","message_thread_id":71}"""), -10001L))
        }
        for (value in listOf("0", "-1", "2147483648", "1.5")) {
            assertEquals(TelegramForumContract.Relation.Rejected, contract.relation(message(topic = """{"@type":"messageTopicForum","forum_topic_id":$value}"""), -10001L))
        }
    }

    @Test fun requestContextFillsOnlyMissingMetadataAndRejectsExplicitConflicts() {
        assertEquals(TelegramForumContract.Relation.Known(71), contract.relation(message(), -10001L, 71))
        assertEquals(TelegramForumContract.Relation.Rejected, contract.relation(message(chat = -10002L), -10001L, 71))
        assertEquals(TelegramForumContract.Relation.Rejected, contract.relation(message(topic = """{"@type":"messageTopicForum","forum_topic_id":72}"""), -10001L, 71))
        assertEquals(TelegramForumContract.Relation.Rejected, contract.relation(message(topic = """{"@type":"messageTopicThread","message_thread_id":71}"""), -10001L, 71))
    }

    @Test fun textOnlyShortPageAdvancesRawCursorAndDoesNotComplete() {
        val page = contract.historyPage(history(message(9000000002L), message(9000000001L)), -10001L, 71, 0)
        assertEquals(9000000001L, page.nextMessageId)
        assertEquals(2, page.messages.size)
        assertFalse(page.complete)
    }

    @Test fun inclusiveBoundaryIsDeduplicatedAndSingletonNeedsIndependentEmptyProbe() {
        val first = contract.historyPage(history(message(9000000001L)), -10001L, 71, 0)
        assertFalse(first.complete)
        val boundary = contract.historyPage(history(message(9000000001L)), -10001L, 71, first.nextMessageId)
        assertTrue(boundary.boundaryProbe)
        assertTrue(boundary.messages.isEmpty())
        assertEquals(9000000000L, boundary.nextMessageId)
        val end = contract.historyPage(history(), -10001L, 71, boundary.nextMessageId)
        assertTrue(end.complete)
    }

    @Test fun foreignMalformedOrForwardPageCannotAdvanceCheckpoint() {
        val bad = contract.historyPage(history(message(9000000001L), message(9000000000L, -10002L)), -10001L, 71, 9000000002L)
        assertEquals(1, bad.rejected)
        assertTrue(bad.messages.isEmpty())
        assertEquals(9000000002L, bad.nextMessageId)
        assertFalse(bad.complete)
        assertEquals(1, contract.historyPage(history(message(9000000003L)), -10001L, 71, 9000000002L).rejected)
    }

    @Test fun smallestPossibleBoundaryNeverRestartsFromNewest() {
        val page = contract.historyPage(history(message(1)), -10001L, 71, 1)
        assertTrue(page.stalled)
        assertFalse(page.complete)
        assertEquals(1L, page.nextMessageId)
    }
}
