package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Nhiều Playlist có tên, S6: the `#mvlist v1` wire format. Pure text, no TDLib. */
class NamedPlaylistSyncProtocolTest {
    private val account = 1001L
    private val scope = PlaylistSyncProtocol.scopeOf(account)
    private val Foreign = NamedPlaylistSyncProtocol.Decoded.Foreign

    private fun tg(n: Long) = PlaylistItemId.Telegram(-1001234L, n)

    private fun invalid(error: PlaylistSyncProtocol.Error) = NamedPlaylistSyncProtocol.Decoded.Invalid(error)

    /** One device's operations of every kind, in the order it made them. */
    private fun everyKindOfOperation(): List<NamedOp> {
        val made = ArrayList<NamedOp>()
        var s = NamedPlaylistState.empty(account, "dev-a")
        fun step(m: NamedMutation): NamedMutation {
            assertNull(m.refusal)
            s = m.state
            made += m.ops
            return m
        }
        val id = checkNotNull(step(s.createList("Nhạc đi xe 🚗")).listId)
        step(s.addTelegram(id, tg(1)))
        step(s.addTelegram(id, tg(2)))
        step(s.moveItem(id, tg(2), 0))
        step(s.removeTelegram(id, tg(1)))
        step(s.renameList(id, "Đổi tên"))
        step(s.deleteList(id))
        assertEquals(NamedOpType.values().toSet(), made.map { it.type }.toSet())
        return made
    }

    private fun envelope(ops: List<NamedOp>, sender: String = "dev-a") =
        NamedPlaylistSyncProtocol.Envelope(scope, sender, ops.first().opId.replace('#', '-'), ops)

    /** A message with a correct checksum around the given body lines (so only the content under test is wrong). */
    private fun sealed(vararg lines: String): String {
        val body = lines.joinToString("\n") + "\n"
        return body + "sum " + TelegramPlaylistCodec.sha256Hex(body)
    }

    private val head = arrayOf("#mvlist v1", "scope $scope", "sender dev-a", "batch dev-a-1")
    private fun message(vararg ops: String) = sealed(*head, *ops)
    private val name = NamedPlaylistCodec.encodeName("Tên")

    // ---- good messages ---------------------------------------------------------------------------

    @Test fun everyKindOfOperationRoundTripsAndEncodingIsDeterministic() {
        val ops = everyKindOfOperation()
        val text = NamedPlaylistSyncProtocol.encode(envelope(ops))
        assertEquals(text, NamedPlaylistSyncProtocol.encode(envelope(ops)))
        assertTrue(text.startsWith("#mvlist v1\nscope $scope\nsender dev-a\nbatch dev-a-1\nop dev-a#1 LCREATE dev-a.1 1 dev-a#1 "))
        val decoded = NamedPlaylistSyncProtocol.decode(text, scope)
        assertEquals(NamedPlaylistSyncProtocol.Decoded.Ok(envelope(ops)), decoded)
        for (op in ops) {
            val one = NamedPlaylistSyncProtocol.decode(NamedPlaylistSyncProtocol.encode(envelope(listOf(op))), scope)
            assertEquals(listOf(op), (one as NamedPlaylistSyncProtocol.Decoded.Ok).envelope.ops)
        }
    }

    @Test fun theTextIsAFixedPointOfTdlibCleaningAndAGoodExampleIsExactlyAsDocumented() {
        val ops = everyKindOfOperation()
        val text = NamedPlaylistSyncProtocol.encode(envelope(ops))
        assertEquals(text, TdlibText.normalize(text))
        assertFalse(text.contains('\t'))
        assertFalse(text.contains('\r'))
        assertFalse("names are not written in clear", text.contains("Nhạc"))
        val lines = text.lines()
        assertEquals("#mvlist v1", lines[0])
        assertTrue(lines.last().startsWith("sum "))
        assertEquals(lines.last().removePrefix("sum "), TelegramPlaylistCodec.sha256Hex(lines.dropLast(1).joinToString("\n") + "\n"))
        val fields = lines.filter { it.startsWith("op ") }.map { it.split(' ') }
        // LCREATE, IADD, IADD, IMOVE, IREMOVE, LRENAME, LDELETE
        assertEquals(listOf(7, 8, 8, 7, 7, 6, 6), fields.map { it.size })
    }

    @Test fun twentyOperationsWithTheLongestNamesNeverMakeAMessageAbove3500Characters() {
        var s = NamedPlaylistState.empty(account, "dev-a")
        val ops = ArrayList<NamedOp>()
        val distinctEmoji = listOf("🎵", "🎶", "🎧", "🎤", "🎸", "🥁", "🎹", "🎺", "🎻", "🪕")
        for (n in 0 until 20) {
            val longName = distinctEmoji[n % 10].repeat(57) + ('a' + n / 10) + n
            val m = s.createList(longName)
            assertNull(m.refusal)
            s = m.state
            ops += m.ops
        }
        assertEquals(20, ops.size)
        assertTrue("one longest operation alone is far below the limit", NamedPlaylistSyncProtocol.encode(envelope(ops.take(1))).length < 1200)
        val taken = ArrayList<NamedOp>()
        var rest: List<NamedOp> = ops
        while (rest.isNotEmpty()) {
            val planned = checkNotNull(NamedPlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", rest))
            val text = NamedPlaylistSyncProtocol.encode(planned)
            assertTrue("length=${text.length}", text.length <= NamedPlaylistSyncProtocol.MAX_CHARS)
            assertTrue(planned.ops.size in 1..NamedPlaylistSyncProtocol.MAX_OPS)
            assertEquals(text, NamedPlaylistSyncProtocol.encode(planned))
            assertEquals(planned, (NamedPlaylistSyncProtocol.decode(text, scope) as NamedPlaylistSyncProtocol.Decoded.Ok).envelope)
            taken += planned.ops
            rest = rest.drop(planned.ops.size)
        }
        assertEquals("the messages together carry every operation in order", ops, taken)
    }

    @Test fun twentyShortOperationsFitIntoOneMessage() {
        var s = NamedPlaylistState.empty(account, "dev-a")
        val id = checkNotNull(s.createList("L").also { s = it.state }.listId)
        val ops = ArrayList<NamedOp>()
        for (n in 1L..19L) {
            val m = s.addTelegram(id, PlaylistItemId.Telegram(-1001234567890L, 1_000_000L + n))
            s = m.state
            ops += m.ops
        }
        val planned = checkNotNull(NamedPlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", s.outbox.map { it.op }))
        assertEquals(20, planned.ops.size)
        assertTrue(NamedPlaylistSyncProtocol.encode(planned).length < 3500)
        assertNull(NamedPlaylistSyncProtocol.planFirstEnvelope(scope, "dev-a", emptyList()))
    }

    @Test fun anEnvelopeNeedsOneToTwentyOperations() {
        val ops = everyKindOfOperation()
        try {
            NamedPlaylistSyncProtocol.encode(NamedPlaylistSyncProtocol.Envelope(scope, "dev-a", "dev-a-1", emptyList()))
            org.junit.Assert.fail()
        } catch (_: IllegalArgumentException) {
        }
        try {
            NamedPlaylistSyncProtocol.encode(NamedPlaylistSyncProtocol.Envelope(scope, "dev-a", "dev-a-1", List(21) { ops[0] }))
            org.junit.Assert.fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    // ---- the two protocols side by side ----------------------------------------------------------

    @Test fun anOlderOrFavouritesDecoderSeesAMvlistTextAsForeignAndSkipsIt() {
        val text = NamedPlaylistSyncProtocol.encode(envelope(everyKindOfOperation()))
        assertEquals(PlaylistSyncProtocol.Decoded.Foreign, PlaylistSyncProtocol.decode(text, scope))
        assertEquals(PlaylistSyncProtocol.Decoded.Foreign, PlaylistSyncProtocol.decode(text, "0000000000000000"))
        assertEquals("a damaged one too: only the marker decides", PlaylistSyncProtocol.Decoded.Foreign, PlaylistSyncProtocol.decode(text.dropLast(5), scope))
    }

    @Test fun theNamedDecoderSeesAMvsyncTextAsForeign() {
        val op = PlaylistState.empty(account, "dev-a").addTelegram(tg(1)).op!!
        val favourite = PlaylistSyncProtocol.encode(PlaylistSyncProtocol.Envelope(scope, "dev-a", "dev-a-1", listOf(op)))
        assertTrue(favourite.startsWith("#mvsync"))
        assertEquals(Foreign, NamedPlaylistSyncProtocol.decode(favourite, scope))
        for (text in listOf("", " ", "hello", "#mvsyn", "#MVLIST v1", " #mvlist v1", "x#mvlist v1", "Tên bài hát.mp3")) {
            assertEquals(text, Foreign, NamedPlaylistSyncProtocol.decode(text, scope))
        }
    }

    @Test fun theTwoMarkersAreDifferent() {
        assertTrue(NamedPlaylistSyncProtocol.MARKER != PlaylistSyncProtocol.MARKER)
        assertFalse(NamedPlaylistSyncProtocol.MARKER.startsWith(PlaylistSyncProtocol.MARKER))
        assertFalse(PlaylistSyncProtocol.MARKER.startsWith(NamedPlaylistSyncProtocol.MARKER))
    }

    // ---- bad messages -----------------------------------------------------------------------------

    @Test fun aMessageWithOtherThanThisAccountsScopeIsWrongAccount() {
        val text = NamedPlaylistSyncProtocol.encode(envelope(everyKindOfOperation()))
        assertEquals(invalid(PlaylistSyncProtocol.Error.WRONG_ACCOUNT), NamedPlaylistSyncProtocol.decode(text, PlaylistSyncProtocol.scopeOf(2002L)))
    }

    @Test fun aNewerVersionStopsTheReaderAndOtherHeadersAreBadFormat() {
        assertEquals(invalid(PlaylistSyncProtocol.Error.FUTURE_VERSION), NamedPlaylistSyncProtocol.decode("#mvlist v2\nwhatever", scope))
        assertEquals(invalid(PlaylistSyncProtocol.Error.FUTURE_VERSION), NamedPlaylistSyncProtocol.decode("#mvlist v10", scope))
        for (header in listOf("#mvlist", "#mvlist v0", "#mvlist v1 ", "#mvlist vx", "#mvlistv1", "#mvlist  v1", "#mvlist1")) {
            assertEquals(header, invalid(PlaylistSyncProtocol.Error.BAD_FORMAT), NamedPlaylistSyncProtocol.decode("$header\nscope $scope", scope))
        }
    }

    @Test fun aTooLargeTextIsRefusedBeforeAnythingElse() {
        val big = "#mvlist v1\n" + "x".repeat(NamedPlaylistSyncProtocol.MAX_TEXT_CHARS)
        assertEquals(invalid(PlaylistSyncProtocol.Error.TOO_LARGE), NamedPlaylistSyncProtocol.decode(big, scope))
    }

    @Test fun aMissingOrWrongChecksumIsRefused() {
        val good = NamedPlaylistSyncProtocol.encode(envelope(everyKindOfOperation()))
        assertEquals(invalid(PlaylistSyncProtocol.Error.BAD_FORMAT), NamedPlaylistSyncProtocol.decode(good.substringBeforeLast("\nsum "), scope))
        val wrong = good.dropLast(1) + (if (good.last() == '0') '1' else '0')
        assertEquals(invalid(PlaylistSyncProtocol.Error.BAD_CHECKSUM), NamedPlaylistSyncProtocol.decode(wrong, scope))
        assertEquals(invalid(PlaylistSyncProtocol.Error.BAD_CHECKSUM), NamedPlaylistSyncProtocol.decode(good + "0", scope))
    }

    @Test fun anyChangedOrCutTextIsNeverAccepted() {
        val good = NamedPlaylistSyncProtocol.encode(envelope(everyKindOfOperation()))
        val random = Random(7)
        repeat(300) {
            val at = random.nextInt(good.length)
            val changed = good.substring(0, at) + (if (good[at] == 'q') 'r' else 'q') + good.substring(at + 1)
            val result = NamedPlaylistSyncProtocol.decode(changed, scope)
            assertTrue("at=$at", result !is NamedPlaylistSyncProtocol.Decoded.Ok)
        }
        for (cut in listOf(10, good.length / 2, good.length - 1)) {
            val result = NamedPlaylistSyncProtocol.decode(good.take(cut), scope)
            assertTrue("cut=$cut", result !is NamedPlaylistSyncProtocol.Decoded.Ok)
        }
    }

    @Test fun malformedHeaderLinesAreBadFormatEvenWithAGoodChecksum() {
        val op = "op dev-a#1 LCREATE dev-a.1 1 dev-a#1 $name"
        assertTrue(NamedPlaylistSyncProtocol.decode(message(op), scope) is NamedPlaylistSyncProtocol.Decoded.Ok)
        fun bad(text: String) = assertEquals(text, invalid(PlaylistSyncProtocol.Error.BAD_FORMAT), NamedPlaylistSyncProtocol.decode(text, scope))
        bad(sealed("#mvlist v1", "scope ABCDEF0123456789", "sender dev-a", "batch dev-a-1", op)) // scope not lowercase hex
        bad(sealed("#mvlist v1", "scope abc", "sender dev-a", "batch dev-a-1", op))
        bad(sealed("#mvlist v1", "scope $scope", "sender dev a", "batch dev-a-1", op))
        bad(sealed("#mvlist v1", "scope $scope", "sender dev-a", "batch dev a", op))
        bad(sealed("#mvlist v1", "scope $scope", "batch dev-a-1", "sender dev-a", op)) // wrong order
        bad(sealed("#mvlist v1", "scope $scope", "sender dev-a", "batch dev-a-1")) // no operation
        bad(sealed("#mvlist v1", "scope $scope", "sender dev-a", op)) // no batch line
        bad("#mvlist v1\nscope $scope\nsender dev-a\nbatch dev-a-1\n$op\nsum\t" + TelegramPlaylistCodec.sha256Hex("x"))
    }

    @Test fun malformedOperationLinesAreBadFormat() {
        fun bad(op: String) =
            assertEquals(op, invalid(PlaylistSyncProtocol.Error.BAD_FORMAT), NamedPlaylistSyncProtocol.decode(message(op), scope))
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#1") // no name
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#1 $name extra")
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#1 ${name}=") // padding is not part of the format
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#1 !!!")
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#1 ${NamedPlaylistCodec.encodeName("")}") // empty name
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#1 ${NamedPlaylistCodec.encodeName("x".repeat(61))}")
        bad("op dev-a#1 LCREATE dev-a.2 1 dev-a#1 $name") // list id must be the create tag
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#2 $name") // tag must be the op id
        bad("op dev-a#1 LCREATE dev-a.1 0 dev-a#1 $name") // seq 0
        bad("op dev-a#1 LCREATE dev-a.1 -3 dev-a#1 $name")
        bad("op dev-a#1 LCREATE dev-a.1 x dev-a#1 $name")
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#1,dev-a#1 $name")
        bad("op dev-a#1 WIPE dev-a.1 1 dev-a#1 $name")
        bad("op dev-a#1 lcreate dev-a.1 1 dev-a#1 $name")
        bad("op dev-a#1 LCREATE nolist 1 dev-a#1 $name")
        bad("op dev-a#1 LRENAME dev-a.1 1")
        bad("op dev-a#1 LRENAME dev-a.1 1 $name $name")
        bad("op dev-a#2 LDELETE dev-a.1 2")
        bad("op dev-a#2 LDELETE dev-a.1 2 notadot")
        bad("op dev-a#2 LDELETE dev-a.1 2 dev-a#1,dev-a#1")
        bad("op dev-a#2 IADD dev-a.1 tg:-1:5 2 dev-a#2")
        bad("op dev-a#2 IADD dev-a.1 tg:-1:5 2 dev-a#3 V") // tag must be the op id
        bad("op dev-a#2 IADD dev-a.1 tg:-1:5 2 dev-a#2 10") // position ends in 0
        bad("op dev-a#2 IADD dev-a.1 tg:-1:5 2 dev-a#2 a-b")
        bad("op dev-a#2 IADD dev-a.1 tg:-1:5 2 dev-a#2,dev-a#9 V")
        bad("op dev-a#2 IADD dev-a.1 local:5 2 dev-a#2 V") // a song of the device is never in a message
        bad("op dev-a#2 IADD dev-a.1 legacy:5 2 dev-a#2 V")
        bad("op dev-a#2 IADD dev-a.1 tg:0:5 2 dev-a#2 V")
        bad("op dev-a#2 IADD dev-a.1 tg:-1:0 2 dev-a#2 V")
        bad("op dev-a#3 IREMOVE dev-a.1 tg:-1:5 3")
        bad("op dev-a#3 IREMOVE dev-a.1 tg:-1:5 3 dev-a#2 V")
        bad("op dev-a#3 IMOVE dev-a.1 tg:-1:5 3")
        bad("op dev-a#3 IMOVE dev-a.1 tg:-1:5 3 dev-a#2")
        bad("op dev-a#3 IMOVE dev-a.1 tg:-1:5 3 V extra")
        bad("op dev-a#3 IMOVE dev-a.1 tg:-1:5 3 ${"1".repeat(FractionalIndex.MAX_LEN + 1)}")
        bad("op")
        bad("op dev-a#1")
        bad("operation dev-a#1 LCREATE dev-a.1 1 dev-a#1 $name")
        bad("op dev-a#1 LCREATE dev-a.1 1 dev-a#1 $name ")
        bad("")
    }

    @Test fun theOperationAndItsTagsMustBelongToTheSender() {
        val other = NamedPlaylistCodec.encodeName("Của máy khác")
        assertEquals(
            invalid(PlaylistSyncProtocol.Error.BAD_FORMAT),
            NamedPlaylistSyncProtocol.decode(message("op dev-b#1 LCREATE dev-b.1 1 dev-b#1 $other"), scope)
        )
        assertEquals(
            invalid(PlaylistSyncProtocol.Error.BAD_FORMAT),
            NamedPlaylistSyncProtocol.decode(message("op dev-a#2 IADD dev-a.1 tg:-1:5 2 dev-a#2 V", "op dev-b#1 IMOVE dev-a.1 tg:-1:5 3 W"), scope)
        )
        // a remove may observe the tags of other devices, only its own op id must be the sender's
        assertTrue(
            NamedPlaylistSyncProtocol.decode(message("op dev-a#3 IREMOVE dev-a.1 tg:-1:5 3 dev-b#2,dev-a#2"), scope) is NamedPlaylistSyncProtocol.Decoded.Ok
        )
        assertTrue(
            NamedPlaylistSyncProtocol.decode(message("op dev-a#3 LDELETE dev-b.1 3 dev-b#1"), scope) is NamedPlaylistSyncProtocol.Decoded.Ok
        )
    }

    @Test fun tooManyOperationsTooManyTagsAndRepeatedOperationsAreNamedErrors() {
        val ops21 = (1..21).map { "op dev-a#$it IMOVE dev-a.1 tg:-1:5 $it V" }
        assertEquals(invalid(PlaylistSyncProtocol.Error.TOO_MANY_OPS), NamedPlaylistSyncProtocol.decode(message(*ops21.toTypedArray()), scope))
        assertTrue(NamedPlaylistSyncProtocol.decode(message(*ops21.take(20).toTypedArray()), scope) is NamedPlaylistSyncProtocol.Decoded.Ok)
        val dots33 = (1..33).joinToString(",") { "dev-b#$it" }
        assertEquals(
            invalid(PlaylistSyncProtocol.Error.TOO_MANY_DOTS),
            NamedPlaylistSyncProtocol.decode(message("op dev-a#3 IREMOVE dev-a.1 tg:-1:5 3 $dots33"), scope)
        )
        val dots32 = (1..32).joinToString(",") { "dev-b#$it" }
        assertTrue(NamedPlaylistSyncProtocol.decode(message("op dev-a#3 IREMOVE dev-a.1 tg:-1:5 3 $dots32"), scope) is NamedPlaylistSyncProtocol.Decoded.Ok)
        assertEquals(
            invalid(PlaylistSyncProtocol.Error.DUPLICATE_OP),
            NamedPlaylistSyncProtocol.decode(message("op dev-a#3 IMOVE dev-a.1 tg:-1:5 3 V", "op dev-a#3 IMOVE dev-a.1 tg:-1:5 4 W"), scope)
        )
    }

    @Test fun aWrongScopeIsNotMistakenForADamagedMessageNorTheOtherWayRound() {
        val op = "op dev-a#1 IMOVE dev-a.1 tg:-1:5 1 V"
        // good checksum, other account's scope: WRONG_ACCOUNT
        val other = sealed("#mvlist v1", "scope ${PlaylistSyncProtocol.scopeOf(9L)}", "sender dev-a", "batch dev-a-1", op)
        assertEquals(invalid(PlaylistSyncProtocol.Error.WRONG_ACCOUNT), NamedPlaylistSyncProtocol.decode(other, scope))
        // other account's scope AND a damaged operation: still reported as damage, never as "another account's"
        val damaged = sealed("#mvlist v1", "scope ${PlaylistSyncProtocol.scopeOf(9L)}", "sender dev-a", "batch dev-a-1", "op dev-a#1 NOPE")
        assertEquals(invalid(PlaylistSyncProtocol.Error.BAD_FORMAT), NamedPlaylistSyncProtocol.decode(damaged, scope))
    }

    @Test fun decodingGivesBackTheVeryOperationsThatWereEncoded() {
        val ops = everyKindOfOperation()
        val decoded = (NamedPlaylistSyncProtocol.decode(NamedPlaylistSyncProtocol.encode(envelope(ops)), scope) as NamedPlaylistSyncProtocol.Decoded.Ok)
        assertEquals(ops, decoded.envelope.ops)
        // a replica that receives them ends with the content the sender made
        var receiver = NamedPlaylistState.empty(account, "dev-b")
        val id = ops.first().listId
        for (op in decoded.envelope.ops) receiver = receiver.applyRemote(op)
        assertNotNull(receiver.lists[id])
        assertFalse("the last operation deletes the playlist", receiver.exists(id))
    }
}
