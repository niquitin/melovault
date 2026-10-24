package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * E96 P5B3 REWORK-04 (blocker A): two sends at the same moment each get their own provisional id, their own success update and
 * their own server id - and NEVER a second message. The flaky `s13` failed once in a full forced run with `Unknown`. The cause
 * was the TEST fake (its provisional-id counter and its scripted-mode queue were not thread-safe, so two simultaneous requests
 * could be handed the same temporary id), not the product: the transport rightly answered Unknown when the update it got did not
 * belong to its message. This test forces the overlap with a barrier and repeats it, so the race cannot hide behind timing.
 * The ack wait is long on purpose here: it takes timing out of the question and leaves only correlation.
 */
class TdlibConcurrentSendStressTest {
    @get:Rule val folder = TemporaryFolder()
    private val user = 1001L
    private val world = FakeTdlibWorld()
    private val pool = Executors.newFixedThreadPool(2)
    private lateinit var rig: TdlibRig
    private var chat = 0L
    private lateinit var hub: TdlibSyncHub

    @Before fun setUp() {
        rig = TdlibRig(world, user, folder.newFolder()).start()
        chat = world.createSyncChannel(user).id
        hub = TdlibSyncHub(rig.core).also { check(it.open()) }
    }

    @After fun tearDown() {
        hub.close()
        pool.shutdownNow()
        rig.stop()
    }

    private fun transport(): TdlibPlaylistSyncTransport {
        val epoch = rig.core.authEpoch()
        val reader = TdlibHistoryReader(hub, user, epoch, chat, HistoryLimits(requestTimeoutMs = 5_000L))
        return TdlibPlaylistSyncTransport(hub, user, epoch, chat, reader, TdlibTransportTimeouts(5_000L, 5_000L))
    }

    private fun text(n: Int) = envelopeText(user, "dev-a", addOp("dev-a", n.toLong(), user, songId(n)))
    private fun syncMessages() = world.texts(chat).filter { it.startsWith(PlaylistSyncProtocol.MARKER) }

    @Test fun r4_theFakeNeverHandsTwoSimultaneousRequestsTheSameTemporaryId() {
        // the barrier makes both threads reach the id counter together, every round
        val barrier = CyclicBarrier(2)
        rig.gateway.sendHook = { barrier.await(5, TimeUnit.SECONDS) }
        val rounds = 300
        repeat(rounds) { i ->
            val a = pool.submit(Callable { transport().send(chat, text(2 * i + 1)) })
            val b = pool.submit(Callable { transport().send(chat, text(2 * i + 2)) })
            val ra = a.get(10, TimeUnit.SECONDS)
            val rb = b.get(10, TimeUnit.SECONDS)
            assertTrue("round $i: $ra", ra is SendResult.Sent)
            assertTrue("round $i: $rb", rb is SendResult.Sent)
            assertNotEquals("round $i", (ra as SendResult.Sent).serverMessageId, (rb as SendResult.Sent).serverMessageId)
        }
        // exactly one stored message per send: nothing was sent twice, nothing was lost
        assertEquals(2 * rounds, syncMessages().size)
        assertEquals(2 * rounds, syncMessages().toSet().size)
        assertEquals(2 * rounds, rig.gateway.count("sendMessage"))
    }

    @Test fun r4_aSendWhoseUpdateBelongsToAnotherMessageIsStillUnknown_neverAnAck() {
        // the product rule stays: an update that is not ours is never an acknowledgement (REWORK-01..03 semantics are unchanged)
        rig.gateway.sendModes.add(SendMode.WRONG_TEXT)
        val result = transport().send(chat, text(1))
        assertEquals(SendResult.Unknown(), result)
    }
}
