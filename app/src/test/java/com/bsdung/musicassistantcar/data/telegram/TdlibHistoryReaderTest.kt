package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Random

/**
 * E96 P3-02: the history adapter turns TDLib's newest-first, possibly short pages into the oldest COMPLETE prefix
 * the P2 contract demands - or fails closed. The cursor must never be able to jump past a message that was not read.
 */
class TdlibHistoryReaderTest {
    @get:Rule val folder = TemporaryFolder()
    private val user = 1001L
    private val world = FakeTdlibWorld()
    private lateinit var rig: TdlibRig
    private lateinit var hub: TdlibSyncHub
    private var chat = 0L

    @Before fun setUp() {
        rig = TdlibRig(world, user, folder.newFolder()).start()
        chat = world.createChannel().id
        hub = TdlibSyncHub(rig.core).also { check(it.open()) }
        hub.watchChannel(chat)
    }

    @After fun tearDown() {
        hub.close()
        rig.stop()
    }

    private fun reader(limits: HistoryLimits = HistoryLimits(requestTimeoutMs = 1_500L)) =
        TdlibHistoryReader(hub, user, rig.core.authEpoch(), chat, limits)

    private fun history() = rig.gateway.requests.filter { it.optString("@type") == "getChatHistory" }
    private fun reverseRequests() = history().filter { it.optInt("offset") == 0 }
    private fun forwardRequests() = history().filter { it.optInt("offset") < 0 }

    /** Reads the whole channel after [start] page by page, asserting the contract on every page. Returns the ids read. */
    private fun readAll(r: TdlibHistoryReader, start: Long = 0L, limit: Int = 100): List<Long> {
        val read = ArrayList<Long>()
        var cursor = start
        var guard = 0
        while (guard++ < 2_000) {
            val page = r.fetchAfter(chat, cursor, limit) as FetchResult.Page
            assertTrue("proof present", page.completeThroughId != FetchResult.Page.UNPROVEN)
            assertTrue("proof never below the cursor", page.completeThroughId >= cursor)
            assertTrue("at most the limit", page.messages.size <= limit)
            val ids = page.messages.map { it.id }
            assertEquals("ascending", ids.sorted(), ids)
            assertTrue("nothing at or below the cursor", ids.all { it > cursor })
            // The completeness promise, checked against the truth: EVERY message in (cursor, proof] is in the page.
            val truth = world.ids(chat).filter { it > cursor && it <= page.completeThroughId }
            assertEquals("complete prefix", truth, ids.filter { it <= page.completeThroughId })
            read.addAll(ids)
            cursor = maxOf(cursor, page.completeThroughId)
            if (!page.hasMore) return read
        }
        throw AssertionError("did not finish")
    }

    // ---- the contract on a newest-first source -----------------------------------------------------

    @Test fun r01_theFirstPageIsTheOLDESTHundredNotTheNewest() {
        world.postMany(chat, 250)
        val page = reader().fetchAfter(chat, 0L, 100) as FetchResult.Page
        val truth = world.ids(chat)
        assertEquals(truth.take(100), page.messages.map { it.id })
        assertTrue(page.hasMore)
        assertEquals(truth[99], page.completeThroughId)
    }

    @Test fun r02_aWholeHistoryIsReadInOrderWithoutALoss() {
        world.postMany(chat, 250)
        assertEquals(world.ids(chat), readAll(reader()))
    }

    @Test fun r03_shortPagesAreNotTheEndOfTheHistory() {
        world.postMany(chat, 300)
        rig.gateway.pageCap = 7
        assertEquals(world.ids(chat), readAll(reader()))
        assertTrue("it really asked many times", reverseRequests().size >= 300 / 7)
    }

    @Test fun r04_overTenThousandMessagesCostAboutOneRequestPerPageNotPerRead() {
        world.postMany(chat, 10_500)
        assertEquals(world.ids(chat), readAll(reader()))
        assertTrue("reverse scan ~105 requests, got ${reverseRequests().size}", reverseRequests().size in 105..125)
        assertTrue("one cross-check per page, not a scan per page", history().size < 400)
    }

    @Test fun r05_sparseIdsAndMessagesDeletedInTheMiddleAreNoProblem() {
        val ids = world.postMany(chat, 200)
        ids.filterIndexed { i, _ -> i % 3 == 0 }.forEach { world.delete(chat, it) }
        assertEquals(world.ids(chat), readAll(reader()))
    }

    @Test fun r06_overlapDuplicatesAndShuffledOrderInAnAnswerAreTolerated() {
        world.postMany(chat, 230)
        val random = Random(5)
        rig.gateway.historyFilter = { _, list ->
            if (list.isNotEmpty()) list.add(list.first())
            list.shuffle(random)
        }
        assertEquals(world.ids(chat), readAll(reader()))
    }

    @Test fun r07_anIncrementalReadStopsAtTheCursorWithFewRequests() {
        world.postMany(chat, 300)
        val cursor = world.ids(chat)[294]
        val r = reader()
        val read = readAll(r, cursor)
        assertEquals(world.ids(chat).filter { it > cursor }, read)
        assertEquals("one scan answer reaches the cursor", 1, reverseRequests().size)
        assertEquals(1, rig.gateway.count("getChat"))
    }

    @Test fun r08_aMessageThatArrivesDuringTheScanIsNeitherClaimedNorLost() {
        world.postMany(chat, 400)
        var late = 0L
        rig.gateway.beforeHistory = { req ->
            if (late == 0L && req.optInt("offset") == 0 && req.optLong("from_message_id") != 0L) late = world.post(chat, "arrived during the scan")
        }
        val r = reader()
        val first = readAll(r)
        assertTrue("the late message was not part of that read", late !in first)
        assertEquals(world.ids(chat).filter { it < late }, first)
        // The next pass (cursor = everything proven) picks it up.
        val cursor = first.last()
        val next = readAll(reader(), cursor)
        assertEquals(listOf(late), next)
    }

    @Test fun r09_anEmptyAnswerIsNeverTakenForTheBeginningOfTheHistory() {
        world.postMany(chat, 50)
        rig.gateway.historyFilter = { req, list -> if (req.optInt("offset") == 0) list.clear() }
        val result = reader().fetchAfter(chat, 0L, 100)
        assertEquals(FetchResult.Failed(SyncErrorKind.NETWORK), result)
        assertEquals("gave up after the configured number of tries", 3, reverseRequests().size)
        assertEquals("nothing claimed, so the cursor cannot move", 0, forwardRequests().size)
    }

    @Test fun r10_anAnswerThatOnlyRepeatsTheStartMessageIsRetriedUntilProgress() {
        world.postMany(chat, 300)
        var stalled = 0
        rig.gateway.historyFilter = { req, list ->
            if (req.optInt("offset") == 0 && req.optLong("from_message_id") != 0L && stalled < 2 && list.isNotEmpty()) {
                stalled++
                while (list.size > 1) list.removeAt(list.size - 1)
            }
        }
        assertEquals(world.ids(chat), readAll(reader()))
        assertEquals(2, stalled)
    }

    @Test fun r11_aSourceThatNeverGoesFurtherDownFailsClosedAfterTheLimit() {
        world.postMany(chat, 300)
        rig.gateway.historyFilter = { req, list ->
            if (req.optInt("offset") == 0 && req.optLong("from_message_id") != 0L) while (list.size > 1) list.removeAt(list.size - 1)
        }
        assertEquals(FetchResult.Failed(SyncErrorKind.NETWORK), reader().fetchAfter(chat, 0L, 100))
    }

    @Test fun r12_aWrongChatAMalformedAnswerOrANullEntryIsUnproven() {
        world.postMany(chat, 30)
        val broken = listOf<(JSONObject, MutableList<Any>) -> Unit>(
            { _, l -> l[3] = (l[3] as JSONObject).put("chat_id", chat - 1) },
            { _, l -> l[3] = JSONObject.NULL },
            { _, l -> l[3] = (l[3] as JSONObject).put("id", 0) },
            { _, l -> l.clear(); l.add("garbage") },
        )
        for (mutation in broken) {
            rig.gateway.historyFilter = mutation
            val page = reader().fetchAfter(chat, 0L, 100) as FetchResult.Page
            assertEquals(FetchResult.Page.UNPROVEN, page.completeThroughId)
            assertTrue(page.messages.isEmpty())
        }
        assertTrue(hub.metrics.get(SyncMetrics.Event.SCAN_FAILED_CLOSED) >= 4)
    }

    @Test fun r13_aHoleInTheScanIsCaughtByTheIndependentForwardQuery() {
        world.postMany(chat, 150)
        val hole = world.ids(chat)[40]
        rig.gateway.historyFilter = { req, list ->
            if (req.optInt("offset") == 0) list.removeAll { it is JSONObject && it.optLong("id") == hole }
        }
        val page = reader().fetchAfter(chat, 0L, 100) as FetchResult.Page
        assertEquals("refused, so the coordinator will not move the cursor", FetchResult.Page.UNPROVEN, page.completeThroughId)
        assertEquals(1, hub.metrics.get(SyncMetrics.Event.CROSS_CHECK_CONFLICT))
    }

    @Test fun r14_anEditedSyncMessageRaisesAnAlertThatBlocksReadingUntilAcknowledged() {
        world.postMany(chat, 5)
        val sync = world.post(chat, envelopeText(user, "dev-a", addOp("dev-a", 1, user, songId(1))))
        world.edit(chat, sync, world.chats.getValue(chat).messages.getValue(sync).text)
        val r = reader()
        assertEquals(FetchResult.Page.UNPROVEN, (r.fetchAfter(chat, 0L, 100) as FetchResult.Page).completeThroughId)
        assertEquals(ContinuityAlert.MESSAGE_EDITED, hub.alerts.pending())
        val before = history().size
        assertEquals("while the alert stands nothing is even asked", FetchResult.Page.UNPROVEN, (r.fetchAfter(chat, 0L, 100) as FetchResult.Page).completeThroughId)
        assertEquals(before, history().size)
        hub.alerts.acknowledge()
        world.edit(chat, sync, "") // the evidence is gone in this fake
        world.chats.getValue(chat).messages.getValue(sync).editDate = 0
        assertEquals(world.ids(chat), readAll(reader()))
    }

    @Test fun r15_onlyAPermanentDeletionInTheBoundChannelIsContinuityEvidence() {
        fun deleted(chatId: Long, permanent: Boolean, cache: Boolean) = JSONObject().put("@type", "updateDeleteMessages")
            .put("chat_id", chatId).put("message_ids", org.json.JSONArray().put(1)).put("is_permanent", permanent).put("from_cache", cache)
        rig.gateway.emit(deleted(chat + 7, true, false))
        rig.gateway.emit(deleted(chat, false, false))
        rig.gateway.emit(deleted(chat, true, true))
        Thread.sleep(80)
        assertEquals(null, hub.alerts.pending())
        rig.gateway.emit(deleted(chat, true, false))
        assertTrue(waitUntil { hub.alerts.pending() == ContinuityAlert.MESSAGES_DELETED })
        world.postMany(chat, 10)
        assertEquals(FetchResult.Page.UNPROVEN, (reader().fetchAfter(chat, 0L, 100) as FetchResult.Page).completeThroughId)
        hub.alerts.acknowledge()
        assertEquals(world.ids(chat), readAll(reader()))
    }

    @Test fun r16_aDeletedCursorMessageDoesNotStopTheScanFromFindingTheBoundary() {
        val ids = world.postMany(chat, 300)
        val cursor = ids[100]
        world.delete(chat, cursor)
        val read = readAll(reader(), cursor)
        assertEquals(world.ids(chat).filter { it > cursor }, read)
    }

    @Test fun r17_moreUnreadThanTheMemoryBudgetFailsClosedInsteadOfGuessing() {
        world.postMany(chat, 160)
        val page = reader(HistoryLimits(maxMessages = 50, requestTimeoutMs = 1_500L)).fetchAfter(chat, 0L, 100) as FetchResult.Page
        assertEquals(FetchResult.Page.UNPROVEN, page.completeThroughId)
    }

    @Test fun r18_aScanThatNeedsTooManyRequestsBacksOffAndResumesWhereItStood() {
        world.postMany(chat, 100)
        rig.gateway.pageCap = 10
        val r = reader(HistoryLimits(maxRequestsPerCall = 3, requestTimeoutMs = 1_500L))
        var result: FetchResult = r.fetchAfter(chat, 0L, 100)
        assertEquals(FetchResult.Failed(SyncErrorKind.TIMEOUT), result)
        var calls = 1
        while (result is FetchResult.Failed && calls < 30) {
            result = r.fetchAfter(chat, 0L, 100)
            calls++
        }
        val page = result as FetchResult.Page
        assertEquals(world.ids(chat).take(100), page.messages.map { it.id })
        assertEquals("the walk was never restarted from the newest message", 1, reverseRequests().count { it.optLong("from_message_id") == 0L })
    }

    @Test fun r19_aFinishedSnapshotIsNotReusedSoNewMessagesAreSeenNextTime() {
        world.postMany(chat, 40)
        val r = reader()
        val first = readAll(r)
        val newOne = world.post(chat, "later")
        val cursor = first.last()
        val again = r.fetchAfter(chat, cursor, 100) as FetchResult.Page
        assertEquals(listOf(newOne), again.messages.map { it.id })
        assertEquals(newOne, again.completeThroughId)
        assertFalse(again.hasMore)
    }

    @Test fun r20_theSecondLookIsAnIndependentForwardQueryAndAgrees() {
        world.postMany(chat, 60)
        val r = reader()
        val page = r.fetchAfter(chat, 0L, 100) as FetchResult.Page
        val forwardBefore = forwardRequests().size
        val probe = r.fetchAfter(chat, 0L, 1) as FetchResult.Page
        assertEquals(page.messages.first().id, probe.messages.first().id)
        assertEquals(forwardBefore + 1, forwardRequests().size)
        assertEquals(0, reverseRequests().count { it.optLong("from_message_id") == 0L } - 1)
    }

    @Test fun r21_channelAndNetworkErrorsAreReportedInTheTermsOfTheSyncCore() {
        world.postMany(chat, 10)
        rig.gateway.failNext("getChat", 400, "Chat not found")
        assertEquals(FetchResult.Failed(SyncErrorKind.CHANNEL_MISSING), reader().fetchAfter(chat, 0L, 100))
        rig.gateway.failNext("getChatHistory", 429, "Too Many Requests: retry after 44")
        assertEquals(FetchResult.Failed(SyncErrorKind.FLOOD_WAIT, 44), reader().fetchAfter(chat, 0L, 100))
        rig.gateway.staySilent("getChatHistory")
        assertEquals(FetchResult.Failed(SyncErrorKind.TIMEOUT),
            reader(HistoryLimits(requestTimeoutMs = 100L)).fetchAfter(chat, 0L, 100))
    }

    @Test fun r22_whateverTheSourceDoesTheReadIsNeverShorterOrLongerThanTheTruth() {
        for (seed in 1..15) {
            val random = Random(seed.toLong())
            val localWorld = FakeTdlibWorld(seed.toLong())
            val localChat = localWorld.createChannel().id
            val ids = localWorld.postMany(localChat, 120 + random.nextInt(400))
            ids.filter { random.nextInt(5) == 0 }.forEach { localWorld.delete(localChat, it) }
            val localRig = TdlibRig(localWorld, user, folder.newFolder()).start()
            val localHub = TdlibSyncHub(localRig.core).also { check(it.open()) }
            localRig.gateway.pageCap = 1 + random.nextInt(100)
            val r = TdlibHistoryReader(localHub, user, localRig.core.authEpoch(), localChat, HistoryLimits(requestTimeoutMs = 1_500L))
            var cursor = 0L
            val read = ArrayList<Long>()
            while (true) {
                val result = r.fetchAfter(localChat, cursor, 1 + random.nextInt(100))
                val page = result as FetchResult.Page
                read.addAll(page.messages.map { it.id })
                // The cursor only ever moves to a point up to which EVERYTHING was handed over.
                val unread = localWorld.ids(localChat).filter { it > cursor && it <= page.completeThroughId && it !in page.messages.map { m -> m.id } }
                assertEquals("seed $seed: nothing skipped below the proof", emptyList<Long>(), unread)
                assertTrue(page.completeThroughId >= cursor)
                cursor = page.completeThroughId
                if (!page.hasMore) break
            }
            assertEquals("seed $seed", localWorld.ids(localChat), read)
            localHub.close()
            localRig.stop()
        }
    }

    @Test fun r23_aReaderOfAnotherChannelIsRefused() {
        assertEquals(FetchResult.Failed(SyncErrorKind.CHANNEL_MISSING), reader().fetchAfter(chat + 5, 0L, 10))
        assertNotNull(reader())
    }
}
