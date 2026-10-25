package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncProtocol.Decoded
import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncProtocol.Error
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * E96 P5B3 REWORK-01 (b): the sync text must survive what TDLib does to it (tabs become spaces), and the first TAB
 * format - including the very message the Sony may have put in its channel after TDLib cleaned it - is read back only
 * when its ORIGINAL checksum matches. Pure text, no channel.
 */
class TelegramPlaylistSyncProtocolTdlibTest {
    private val account = 987_654_321_012L
    private val scope = PlaylistSyncProtocol.scopeOf(account)
    private val tab = '\t'

    private fun ops(n: Int, device: String = "dev-a"): List<PlaylistOp> =
        (1..n).map { addOp(device, it.toLong(), -100123L, it * 4_194_304L) }

    private fun envelope(opList: List<PlaylistOp>, sender: String = "dev-a", sc: String = scope) =
        PlaylistSyncProtocol.Envelope(sc, sender, opList.first().opId.replace('#', '-'), opList)

    /** What the first version of the app sent: TAB separated, checksum over the TAB text. */
    private fun legacyTabText(env: PlaylistSyncProtocol.Envelope) = PlaylistSyncProtocol.encodeWith(env, tab)

    /** What Telegram kept of it after TDLib cleaned the text. */
    private fun asTdlibKeepsIt(text: String) = FakeTdlibClientText.clean(text)

    private fun invalidOf(text: String): Error = (PlaylistSyncProtocol.decode(text, scope) as Decoded.Invalid).error

    @Test fun t02_theEncodedTextIsAFixedPointOfTdlibCleaning_forRandomEnvelopes() {
        val random = Random(2026_10_24L)
        repeat(500) { round ->
            val count = 1 + random.nextInt(20)
            val list = (1..count).map { n ->
                val dots = (0..random.nextInt(3)).map { Dot("dev-${'a' + random.nextInt(3)}", 1L + random.nextInt(900)) }.toSet()
                val chat = if (random.nextBoolean()) -(1_000_000_000_000L + random.nextInt(900_000)) else 1_000L + random.nextInt(9_000)
                val msg = (1L + random.nextInt(5_000)) * 1_048_576L
                if (random.nextBoolean()) addOp("dev-a", n.toLong() + round * 25L, chat, msg, seq = random.nextInt(100_000).toLong())
                else removeOp("dev-a", n.toLong() + round * 25L, chat, msg, dots, seq = random.nextInt(100_000).toLong())
            }
            val env = envelope(list)
            val text = PlaylistSyncProtocol.encode(env)
            assertEquals("round $round", text, FakeTdlibClientText.clean(text))
            assertEquals("round $round", text, TdlibText.normalize(text))
            assertFalse(text.contains(tab) || text.contains('\r'))
            assertEquals(list, (PlaylistSyncProtocol.decode(text, scope) as Decoded.Ok).envelope.ops)
        }
    }

    @Test fun theProductionNormaliserAgreesWithTheIndependentTdlibCopyOnHostileText() {
        val samples = listOf(
            "a\tb", "\ta\t", "  x  ", "\n\nx\n\n", "x\r\ny", "a b‮c", "tab\there\tand\u0000nul", "plain", "", " ", "\t\t",
            "#mvsync v1\nscope\tabc\n", "ünïcödé\tvăn bản", "emoji 🎵\ttab",
        )
        for (s in samples) assertEquals("sample <${s.replace("\t", "\\t")}>", FakeTdlibClientText.clean(s), TdlibText.normalize(s))
        val random = Random(7L)
        repeat(300) {
            val s = String(CharArray(random.nextInt(40)) { listOf(' ', '\t', '\n', '\r', 'a', 'Z', '0', ' ', '‮', '\u0001', 'é')[random.nextInt(11)] })
            assertEquals(FakeTdlibClientText.clean(s), TdlibText.normalize(s))
        }
    }

    @Test fun encodingRefusesAFieldThatTdlibWouldChange() {
        // A field holding a TAB (or CR, or a line separator) can never be sent: encode() checks the fixed point first.
        for (bad in listOf("dev-a#1\t", "dev-a#1\r", "dev-a#1 ")) {
            val refused = try {
                val op = PlaylistOp(bad, OpType.ADD, PlaylistItemId.Telegram(-100123L, 5L), setOf(Dot("dev-a", 1)), 1L)
                PlaylistSyncProtocol.encode(envelope(listOf(op)))
                false
            } catch (_: IllegalStateException) {
                true
            } catch (_: IllegalArgumentException) {
                true
            }
            assertTrue("a field with a character TDLib rewrites must not be encodable", refused)
        }
    }

    @Test fun t03_theFirstTabFormatIsStillReadWhenItsOwnChecksumFits() {
        val env = envelope(ops(5))
        val legacy = legacyTabText(env)
        assertTrue(legacy.contains(tab))
        val decoded = PlaylistSyncProtocol.decode(legacy, scope) as Decoded.Ok
        assertEquals(env.ops, decoded.envelope.ops)
        assertEquals(env.sender, decoded.envelope.sender)
    }

    @Test fun t03_theTabMessageAfterTdlibCleanedItIsReadBackWithItsOriginalChecksum() {
        val env = envelope(ops(5))
        val kept = asTdlibKeepsIt(legacyTabText(env))
        assertFalse("TDLib left no tab", kept.contains(tab))
        assertNotEquals("and it is not the current format either", PlaylistSyncProtocol.encode(env), kept)
        val decoded = PlaylistSyncProtocol.decode(kept, scope) as Decoded.Ok
        assertEquals(env.ops, decoded.envelope.ops)
        assertEquals(listOf("dev-a#1", "dev-a#2", "dev-a#3", "dev-a#4", "dev-a#5"), decoded.envelope.ops.map { it.opId })
    }

    @Test fun theCurrentAndTheCleanedLegacyFormsOfTheSameOperationsAreDifferentTextsButEqualMeaning() {
        val env = envelope(ops(3))
        val current = PlaylistSyncProtocol.decode(PlaylistSyncProtocol.encode(env), scope) as Decoded.Ok
        val legacy = PlaylistSyncProtocol.decode(asTdlibKeepsIt(legacyTabText(env)), scope) as Decoded.Ok
        assertEquals(current.envelope, legacy.envelope)
    }

    @Test fun aLegacyTextWithAnAlteredFieldIsNeverAccepted() {
        val env = envelope(ops(3))
        val kept = asTdlibKeepsIt(legacyTabText(env))
        // change one digit inside an operation line: the ORIGINAL checksum no longer fits any reconstruction
        val at = kept.indexOf("op dev-a#2")
        val altered = kept.substring(0, at) + kept.substring(at).replaceFirst("tg:-100123:8388608", "tg:-100123:8388609")
        assertNotEquals(kept, altered)
        assertEquals(Error.BAD_CHECKSUM, invalidOf(altered))
    }

    @Test fun aLegacyTextWithAnotherChecksumOrNoChecksumIsNeverAccepted() {
        val kept = asTdlibKeepsIt(legacyTabText(envelope(ops(2))))
        val hash = kept.substringAfterLast("sum ")
        val flipped = kept.dropLast(hash.length) + (if (hash.first() == 'a') 'b' else 'a') + hash.drop(1)
        assertEquals(Error.BAD_CHECKSUM, invalidOf(flipped))
        assertEquals(Error.BAD_FORMAT, invalidOf(kept.substringBeforeLast("\nsum ")))
        assertTrue(invalidOf(kept.dropLast(3)) in setOf(Error.BAD_CHECKSUM, Error.BAD_FORMAT))
    }

    @Test fun aMixedTextWithSomeTabsLeftIsNeverGuessed() {
        val env = envelope(ops(2))
        val kept = asTdlibKeepsIt(legacyTabText(env))
        // one separator turned back into a TAB: the text now holds a TAB, so it is read as the TAB format only - and fails
        val mixed = kept.replaceFirst("scope ", "scope\t")
        assertTrue(PlaylistSyncProtocol.decode(mixed, scope) is Decoded.Invalid)
    }

    @Test fun doubleSpacesEmptyFieldsAndExtraFieldsAreInvalidInEveryForm() {
        val env = envelope(ops(2))
        val current = PlaylistSyncProtocol.encode(env)
        fun resealCurrent(body: String) = body + "sum " + TelegramPlaylistCodec.sha256Hex(body)
        val body = current.substringBeforeLast("sum ")
        assertTrue(PlaylistSyncProtocol.decode(resealCurrent(body.replaceFirst("scope ", "scope  ")), scope) is Decoded.Invalid)
        assertTrue(PlaylistSyncProtocol.decode(resealCurrent(body.replaceFirst("batch dev-a-1", "batch ")), scope) is Decoded.Invalid)
        assertTrue(PlaylistSyncProtocol.decode(resealCurrent(body.replaceFirst("op dev-a#1 ADD", "op dev-a#1  ADD")), scope) is Decoded.Invalid)
        assertTrue(PlaylistSyncProtocol.decode(resealCurrent(body.replaceFirst("\nop ", "\nop extra ")), scope) is Decoded.Invalid)
        // and the same damage inside a legacy text that was re-sealed by hand with the TAB checksum
        val legacyBody = legacyTabText(env).substringBeforeLast("sum\t")
        val doubled = legacyBody.replaceFirst("sender\t", "sender\t\t")
        val sealed = doubled + "sum\t" + TelegramPlaylistCodec.sha256Hex(doubled)
        assertTrue(PlaylistSyncProtocol.decode(asTdlibKeepsIt(sealed), scope) is Decoded.Invalid)
    }

    @Test fun aLegacyMessageOfAnotherAccountOrASenderMismatchOrARepeatedOperationIsRefusedNotAcknowledged() {
        val otherScope = PlaylistSyncProtocol.scopeOf(111L)
        val foreign = asTdlibKeepsIt(legacyTabText(envelope(ops(2), sc = otherScope)))
        assertEquals(Error.WRONG_ACCOUNT, invalidOf(foreign))
        // an operation id that does not belong to the sender
        val liar = envelope(listOf(addOp("dev-z", 1, -100123L, 4_194_304L)), sender = "dev-a")
        assertEquals(Error.BAD_FORMAT, invalidOf(asTdlibKeepsIt(legacyTabText(liar))))
        // the same operation twice in one message
        val twice = envelope(listOf(addOp("dev-a", 1, -100123L, 4_194_304L), addOp("dev-a", 1, -100123L, 8_388_608L)))
        assertEquals(Error.DUPLICATE_OP, invalidOf(asTdlibKeepsIt(legacyTabText(twice))))
    }

    @Test fun aFutureVersionAndOversizedTextAreStillRefusedInTheCleanedForm() {
        val kept = asTdlibKeepsIt(legacyTabText(envelope(ops(1))))
        assertEquals(Error.FUTURE_VERSION, invalidOf(kept.replaceFirst("#mvsync v1", "#mvsync v2")))
        assertEquals(Error.TOO_LARGE, invalidOf("#mvsync v1\n" + "x".repeat(PlaylistSyncProtocol.MAX_TEXT_CHARS)))
    }
}
