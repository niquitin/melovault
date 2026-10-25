package com.bsdung.musicassistantcar.data.telegram

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * E96 P5B3 REWORK-01 (a): the live failure of the first "Đồng bộ ngay" on the Sony.
 *
 * Real TDLib turns every TAB of an outgoing text into a space (clean_input_string). The P2 wire format used TAB as its
 * separator and the transport compared the provisional answer with the raw text, so a healthy send looked like a
 * "payload mismatch": [SendResult.Unknown], every operation of the envelope marked RETRY_REQUIRED and a 5 second backoff.
 * The old fake stored the text untouched, which is why nothing caught it. [FakeTdlibClientText] now does what TDLib does.
 */
class TdlibTextNormalizationRegressionTest {
    @get:Rule val folder = TemporaryFolder()
    private val acct = TelegramUserId(1001L)
    private val world = FakeTdlibWorld()
    private val chat by lazy { world.createSyncChannel(acct.value).id }
    private val rigs = ArrayList<SyncRig>()

    private fun rig(name: String) = SyncRig(name, folder.newFolder(), world, acct, chat, seed = 41).also { rigs.add(it) }

    @After fun tearDown() = rigs.forEach { it.stop() }

    @Test fun t1_theFakeCleansTextLikeTdlib_soTabsBecomeSpaces() {
        assertEquals("a b", FakeTdlibClientText.clean("a\tb"))
        assertEquals("a b", FakeTdlibClientText.clean("\n a\tb \n"))
        assertEquals("x\ny", FakeTdlibClientText.clean("x\r\ny"))
        assertEquals("ab", FakeTdlibClientText.clean("a b"))
    }

    @Test fun t1_fiveWaitingOperationsOfOneSongAreAcknowledgedByOneNormalisedEnvelope() {
        val a = rig("dev-a")
        // The Sony's situation: one song, several add/remove operations queued (5 here), one envelope.
        repeat(5) { a.device.toggle(acct.value, songId(1)) }
        val queued = a.device.pendingIds()
        assertEquals(5, queued.size)

        val report = a.run()

        assertEquals("a healthy send must not look like a mismatch", SyncStatus.OK, report.status)
        assertEquals(0L, report.retryAfterMs)
        assertEquals(1, a.tdlib.gateway.count("sendMessage"))
        assertEquals(emptyList<String>(), a.device.pendingIds())
        assertEquals(0, a.device.stored().outbox.count { it.status == OutboxStatus.RETRY_REQUIRED })
        // What the channel holds is exactly what TDLib would keep: no tab is left in it.
        val sync = world.texts(chat).filter { it.startsWith(PlaylistSyncProtocol.MARKER) }
        assertEquals(1, sync.size)
        assertTrue("TDLib keeps no tab", '\t' !in sync.single())
    }
}
