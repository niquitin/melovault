package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncProtocol.Decoded
import com.bsdung.musicassistantcar.data.telegram.PlaylistSyncProtocol.Error
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E96 P2-01: the message format on its own - no channel, no device. */
class TelegramPlaylistSyncProtocolTest {
    private val account = 987_654_321_012L
    private val scope = PlaylistSyncProtocol.scopeOf(account)

    private fun add(n: Long, chat: Long = -100123L, message: Long = n * 1000) = addOp("dev-a", n, chat, message)

    private fun encode(vararg ops: PlaylistOp, sender: String = "dev-a", scope: String = this.scope) =
        PlaylistSyncProtocol.encode(PlaylistSyncProtocol.Envelope(scope, sender, "dev-a-${ops.first().opId.substringAfter('#')}", ops.toList()))

    /** Re-seals a hand-edited body so only the thing under test is wrong, not the checksum. */
    private fun seal(body: String) = body + "sum\t" + TelegramPlaylistCodec.sha256Hex(body)

    private fun body(text: String) = text.substring(0, text.lastIndexOf("sum\t"))

    private fun invalid(text: String, expectedScope: String = scope): Error =
        (PlaylistSyncProtocol.decode(text, expectedScope) as Decoded.Invalid).error

    @Test fun roundTripKeepsEveryField() {
        val removeObserved = removeOp("dev-a", 3, -100123L, 5000, setOf(Dot("dev-a", 1), Dot("dev-b", 7)), seq = 9)
        val text = encode(add(1), add(2, chat = 1001L, message = 8_388_608L), removeObserved)
        val decoded = PlaylistSyncProtocol.decode(text, scope) as Decoded.Ok
        assertEquals(listOf(add(1), add(2, chat = 1001L, message = 8_388_608L), removeObserved), decoded.envelope.ops)
        assertEquals("dev-a", decoded.envelope.sender)
        assertEquals(scope, decoded.envelope.scope)
    }

    @Test fun encodingIsDeterministicSoARetryIsTheIdenticalMessage() {
        val ops = listOf(add(1), add(2))
        val env = PlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", ops)!!
        assertEquals(PlaylistSyncProtocol.encode(env), PlaylistSyncProtocol.encode(PlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", ops)!!))
        assertEquals("dev-a-1", env.batchId)
    }

    @Test fun theMessageCarriesNoAccountNumberPhoneTokenPathOrSongName() {
        val text = encode(add(1))
        assertFalse(text.contains(account.toString()))
        assertFalse(text.lowercase().let { it.contains("phone") || it.contains("token") || it.contains("hash") || it.contains(".mp3") || it.contains("/") })
        assertTrue(text.startsWith("#mvsync v1\n"))
    }

    @Test fun textThatIsNotOursIsForeignAndHarmless() {
        assertEquals(Decoded.Foreign, PlaylistSyncProtocol.decode("hello", scope))
        assertEquals(Decoded.Foreign, PlaylistSyncProtocol.decode("", scope))
        assertEquals(Decoded.Foreign, PlaylistSyncProtocol.decode(" #mvsync v1", scope))
    }

    @Test fun anyTamperedCharacterIsRejectedByTheChecksum() {
        val text = encode(add(1), add(2))
        for (at in listOf(15, 40, 80, text.indexOf("op\t") + 5, text.length - 70)) {
            val broken = text.substring(0, at) + (if (text[at] == 'a') 'b' else 'a') + text.substring(at + 1)
            val error = invalid(broken)
            assertTrue("at=$at error=$error", error == Error.BAD_CHECKSUM || error == Error.BAD_FORMAT)
        }
        assertEquals(Error.BAD_CHECKSUM, invalid(text.dropLast(1) + (if (text.last() == 'a') 'b' else 'a')))
    }

    @Test fun aMissingOrTruncatedChecksumIsRejected() {
        val text = encode(add(1))
        assertEquals(Error.BAD_FORMAT, invalid(body(text)))
        assertTrue(invalid(text.dropLast(5)) in setOf(Error.BAD_CHECKSUM, Error.BAD_FORMAT))
        assertEquals(Error.BAD_FORMAT, invalid("#mvsync v1"))
    }

    @Test fun aFutureVersionIsNeverReadAsOurOwn() {
        val future = encode(add(1)).replaceFirst("#mvsync v1", "#mvsync v2")
        assertEquals(Error.FUTURE_VERSION, invalid(future)) // even though its checksum no longer fits
        assertEquals(Error.FUTURE_VERSION, invalid("#mvsync v99\nwhatever"))
        assertEquals(Error.BAD_FORMAT, invalid("#mvsync v0\nx"))
        assertEquals(Error.BAD_FORMAT, invalid("#mvsyncx v1\nx"))
    }

    @Test fun anotherAccountsMessageIsRejectedAfterTheChecksumIsFine() {
        val other = PlaylistSyncProtocol.scopeOf(111L)
        assertEquals(Error.WRONG_ACCOUNT, invalid(encode(add(1), scope = other)))
        assertTrue(other != scope)
    }

    @Test fun oversizedTextIsRejected() {
        val huge = "#mvsync v1\n" + "x".repeat(PlaylistSyncProtocol.MAX_TEXT_CHARS)
        assertEquals(Error.TOO_LARGE, invalid(huge))
    }

    @Test fun malformedBodiesAreRejected() {
        val good = encode(add(1))
        val goodBody = body(good)
        val cases = mapOf(
            "no ops" to goodBody.substringBefore("op\t"),
            "bad scope" to goodBody.replace("scope\t$scope", "scope\tNOTHEX"),
            "bad sender" to goodBody.replace("sender\tdev-a", "sender\tdev a"),
            "extra field" to goodBody.replace("\n", "\textra\n").replaceFirst("\textra\n", "\n"),
            "op line short" to goodBody.replace(Regex("op\t[^\n]*"), "op\tdev-a#1\tADD"),
            "bad type" to goodBody.replace("\tADD\t", "\tMOVE\t"),
            "bad dot" to goodBody.replace("dev-a#1\n", "dev-a-1\n").let { it },
            "item is legacy" to goodBody.replace("tg:-100123:1000", "legacy:1000"),
            "item is local" to goodBody.replace("tg:-100123:1000", "local:1000"),
            "item is not a song" to goodBody.replace("tg:-100123:1000", "tg:0:1000"),
            "negative seq" to goodBody.replace("\t1\tdev-a#1", "\t-1\tdev-a#1"),
        )
        for ((name, text) in cases) {
            val result = PlaylistSyncProtocol.decode(seal(text), scope)
            assertTrue("$name -> $result", result is Decoded.Invalid)
        }
    }

    @Test fun anOperationNotWrittenByTheSenderIsRejected() {
        val foreignOpId = body(encode(add(1))).replace("op\tdev-a#1", "op\tdev-z#1")
        assertEquals(Error.BAD_FORMAT, invalid(seal(foreignOpId)))
        val foreignTag = body(encode(add(1))).replace("\tdev-a#1\n", "\tdev-z#1\n")
        assertEquals(Error.BAD_FORMAT, invalid(seal(foreignTag)))
    }

    @Test fun aRepeatedOperationIdInOneMessageIsRejected() {
        val text = body(encode(add(1), add(2)))
        val dup = text.replace("op\tdev-a#2", "op\tdev-a#1")
        assertEquals(Error.DUPLICATE_OP, invalid(seal(dup)))
    }

    @Test fun tooManyOperationsOrTagsAreRejected() {
        val many = (1L..21L).joinToString("") { n -> "op\tdev-a#$n\tADD\ttg:-100123:${n * 10}\t$n\tdev-a#$n\n" }
        val header = "#mvsync v1\nscope\t$scope\nsender\tdev-a\nbatch\tdev-a-1\n"
        assertEquals(Error.TOO_MANY_OPS, invalid(seal(header + many)))
        val dots = (1L..33L).joinToString(",") { "dev-b#$it" }
        val tags = header + "op\tdev-a#1\tREMOVE\ttg:-100123:10\t5\t$dots\n"
        assertEquals(Error.TOO_MANY_DOTS, invalid(seal(tags)))
    }

    @Test fun encodingRefusesWhatCouldNeverBeDecoded() {
        val env = PlaylistSyncProtocol.Envelope(scope, "dev-a", "dev-a-1", emptyList())
        try {
            PlaylistSyncProtocol.encode(env)
            org.junit.Assert.fail("empty")
        } catch (_: IllegalArgumentException) {
        }
        val manyDots = removeOp("dev-a", 1, -100123L, 5, (1L..33L).map { Dot("dev-b", it) }.toSet())
        try {
            PlaylistSyncProtocol.encode(PlaylistSyncProtocol.Envelope(scope, "dev-a", "dev-a-1", listOf(manyDots)))
            org.junit.Assert.fail("33 tags")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun batchingKeepsEveryMessageWithinTheLimitsAndTheOrder() {
        val ops = (1L..95L).map { addOp("dev-a", it, -100123L, it * 7_777L) }
        var rest = ops
        val sent = ArrayList<PlaylistOp>()
        var messages = 0
        while (rest.isNotEmpty()) {
            val env = PlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", rest)!!
            val text = PlaylistSyncProtocol.encode(env)
            assertTrue(text.length <= PlaylistSyncProtocol.MAX_CHARS)
            assertTrue(env.ops.size <= PlaylistSyncProtocol.MAX_OPS)
            assertEquals(env.ops, (PlaylistSyncProtocol.decode(text, scope) as Decoded.Ok).envelope.ops)
            sent.addAll(env.ops)
            rest = rest.drop(env.ops.size)
            messages++
        }
        assertEquals(ops, sent)
        assertEquals(5, messages) // 20 + 20 + 20 + 20 + 15
    }

    @Test fun aSingleOperationThatCannotFitIsReportedNotSplit() {
        val tooManyTags = removeOp("dev-a", 1, -100123L, 5, (1L..40L).map { Dot("dev-b", it) }.toSet())
        assertNull(PlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", listOf(tooManyTags)))
        assertNull(PlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", emptyList()))
        // a good operation behind an unsendable one is not skipped over
        assertNull(PlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", listOf(tooManyTags, add(2))))
    }

    @Test fun thirtyTwoTagsStillFitInOneMessage() {
        val op = removeOp("dev-a", 1, -100123L, 5, (1L..32L).map { Dot("dev-b${it}", it) }.toSet())
        val env = PlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", listOf(op))
        assertNotNull(env)
        assertTrue(PlaylistSyncProtocol.encode(env!!).length <= PlaylistSyncProtocol.MAX_CHARS)
    }

    @Test fun theChecksumIsNotASignatureAnyoneCanWriteAValidMessage() {
        // Documented limit: integrity against damage only. A message made by hand with the right sum is accepted.
        val handMade = seal("#mvsync v1\nscope\t$scope\nsender\tdev-evil\nbatch\tdev-evil-1\nop\tdev-evil#1\tADD\ttg:-100123:1\t1\tdev-evil#1\n")
        assertTrue(PlaylistSyncProtocol.decode(handMade, scope) is Decoded.Ok)
    }
}
