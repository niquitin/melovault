package com.bsdung.musicassistantcar.data.telegram

import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Candidate
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.FetchDecision
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Meta
import com.bsdung.musicassistantcar.data.telegram.MeloLyricsPolicy.Pick
import com.bsdung.musicassistantcar.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloLyricsPolicyTest {

    private fun candidate(id: Long, durationSec: Double) = Candidate(
        id = id, trackName = "t", artistName = "a", albumName = null, durationSec = durationSec,
        instrumental = false, plainLyrics = "x", syncedLyrics = null
    )

    // --- normalize / signature -------------------------------------------------------------

    @Test
    fun decomposedAndComposedVietnameseAreTheSameKey() {
        val composed = "Sơn Tùng"
        val decomposed = "Sơn Tùng"
        assertNotEquals(composed, decomposed)
        assertEquals(MeloLyricsPolicy.normalize(composed), MeloLyricsPolicy.normalize(decomposed))
    }

    @Test
    fun normalizeLowercasesVietnameseAndCollapsesSpaces() {
        assertEquals("đức phúc", MeloLyricsPolicy.normalize("  ĐỨC   Phúc \t"))
        assertEquals("", MeloLyricsPolicy.normalize(null))
    }

    @Test
    fun featuredGuestDoesNotChangeArtistKey() {
        assertEquals("sơn tùng m-tp", MeloLyricsPolicy.normalizeArtist("Sơn Tùng M-TP feat. Snoop Dogg"))
        assertEquals("sơn tùng m-tp", MeloLyricsPolicy.normalizeArtist("Sơn Tùng M-TP (ft. Snoop Dogg)"))
        assertEquals("sơn tùng m-tp", MeloLyricsPolicy.normalizeArtist("Sơn Tùng M-TP [Featuring Snoop Dogg]"))
    }

    @Test
    fun artistsThatMerelyContainFtAreKept() {
        assertEquals("soft cell", MeloLyricsPolicy.normalizeArtist("Soft Cell"))
        assertEquals("left banke", MeloLyricsPolicy.normalizeArtist("Left Banke"))
    }

    @Test
    fun artistThatIsOnlyAFeatureIsNotEmptied() {
        assertEquals("feat. someone", MeloLyricsPolicy.normalizeArtist("feat. Someone"))
    }

    @Test
    fun signatureIgnoresCaseSpacingAndFeaturedGuest() {
        val a = Meta("Lạc Trôi", "Sơn Tùng M-TP", null, 233)
        val b = Meta("  lạc   trôi ", "SƠN TÙNG M-TP ft. Someone", null, 233)
        assertEquals(MeloLyricsPolicy.signature(a), MeloLyricsPolicy.signature(b))
    }

    @Test
    fun signatureChangesWithDurationAndAlbum() {
        val base = Meta("Lạc Trôi", "Sơn Tùng M-TP", null, 233)
        assertNotEquals(MeloLyricsPolicy.signature(base), MeloLyricsPolicy.signature(base.copy(durationSec = 234)))
        assertNotEquals(MeloLyricsPolicy.signature(base), MeloLyricsPolicy.signature(base.copy(album = "m-tp M-TP")))
    }

    @Test
    fun signatureIsLowerCaseSha256Hex() {
        val sig = MeloLyricsPolicy.signature(Meta("a", "b", null, 1))
        assertEquals(64, sig.length)
        assertTrue(sig.all { it in '0'..'9' || it in 'a'..'f' })
    }

    // --- shouldAutoFetch --------------------------------------------------------------------

    @Test
    fun missingTitleOrArtistIsNotLookedUp() {
        assertEquals(FetchDecision.MISSING_METADATA, MeloLyricsPolicy.shouldAutoFetch(Meta("", "Artist", null, 200)))
        assertEquals(FetchDecision.MISSING_METADATA, MeloLyricsPolicy.shouldAutoFetch(Meta("Title", null, null, 200)))
        assertEquals(FetchDecision.MISSING_METADATA, MeloLyricsPolicy.shouldAutoFetch(Meta("Title", "   ", null, 200)))
    }

    @Test
    fun placeholderNamesCountAsMissing() {
        assertEquals(FetchDecision.MISSING_METADATA, MeloLyricsPolicy.shouldAutoFetch(Meta("Title", "Unknown", null, 200)))
        assertEquals(FetchDecision.MISSING_METADATA, MeloLyricsPolicy.shouldAutoFetch(Meta("<unknown>", "Artist", null, 200)))
        assertEquals(FetchDecision.MISSING_METADATA, MeloLyricsPolicy.shouldAutoFetch(Meta("Chưa xác định", "Artist", null, 200)))
    }

    @Test
    fun mixsetThresholdIsFifteenMinutes() {
        assertEquals(FetchDecision.FETCH, MeloLyricsPolicy.shouldAutoFetch(Meta("T", "A", null, 900)))
        assertEquals(FetchDecision.TOO_LONG, MeloLyricsPolicy.shouldAutoFetch(Meta("T", "A", null, 901)))
    }

    @Test
    fun unknownLengthIsStillLookedUp() {
        assertEquals(FetchDecision.FETCH, MeloLyricsPolicy.shouldAutoFetch(Meta("T", "A", null, null)))
    }

    // --- pickCandidate ----------------------------------------------------------------------

    @Test
    fun noResultsPicksNothing() {
        assertSame(Pick.None, MeloLyricsPolicy.pickCandidate(emptyList(), 200))
    }

    @Test
    fun oneResultExactlyTwoSecondsOffIsTaken() {
        val pick = MeloLyricsPolicy.pickCandidate(listOf(candidate(1, 202.0)), 200)
        assertTrue(pick is Pick.Chosen)
        assertEquals(1L, (pick as Pick.Chosen).candidate.id)
    }

    @Test
    fun oneResultThreeSecondsOffIsNotTaken() {
        val pick = MeloLyricsPolicy.pickCandidate(listOf(candidate(1, 203.0)), 200)
        assertTrue(pick is Pick.Ambiguous)
    }

    @Test
    fun twoCloseResultsAreAmbiguousWithCloseOnesFirst() {
        val pick = MeloLyricsPolicy.pickCandidate(listOf(candidate(1, 260.0), candidate(2, 199.0), candidate(3, 201.0)), 200)
        assertTrue(pick is Pick.Ambiguous)
        assertEquals(listOf(2L, 3L, 1L), (pick as Pick.Ambiguous).candidates.map { it.id })
    }

    @Test
    fun onePlusFarOnesTakesTheOneCloseResult() {
        val pick = MeloLyricsPolicy.pickCandidate(listOf(candidate(1, 260.0), candidate(2, 200.5), candidate(3, 90.0)), 200)
        assertEquals(2L, (pick as Pick.Chosen).candidate.id)
    }

    @Test
    fun unknownLengthNeverAutoPicks() {
        assertTrue(MeloLyricsPolicy.pickCandidate(listOf(candidate(1, 200.0)), null) is Pick.Ambiguous)
        assertTrue(MeloLyricsPolicy.pickCandidate(listOf(candidate(1, 200.0)), 0) is Pick.Ambiguous)
    }

    @Test
    fun resultListIsCappedAtTwenty() {
        val many = (1L..30L).map { candidate(it, 100.0 + it) }
        val pick = MeloLyricsPolicy.pickCandidate(many, 500) as Pick.Ambiguous
        assertEquals(20, pick.candidates.size)
    }

    // --- currentLineIndex -------------------------------------------------------------------

    @Test
    fun currentLineBeforeFirstMiddleAndAfterLast() {
        val lines = LrcParser.parse("[00:10.00]one\n[00:20.00]two\n[00:30.00]three")
        assertEquals(-1, MeloLyricsPolicy.currentLineIndex(lines, 9_999))
        assertEquals(0, MeloLyricsPolicy.currentLineIndex(lines, 10_000))
        assertEquals(1, MeloLyricsPolicy.currentLineIndex(lines, 25_000))
        assertEquals(2, MeloLyricsPolicy.currentLineIndex(lines, 3_600_000))
        assertEquals(-1, MeloLyricsPolicy.currentLineIndex(emptyList(), 5_000))
    }

    @Test
    fun oneLineWithTwoTimestampsBecomesTwoLines() {
        val lines = LrcParser.parse("[00:05.00][00:15.00]chorus\n[00:10.00]verse")
        assertEquals(listOf(5_000L, 10_000L, 15_000L), lines.map { it.startMs })
        assertEquals(2, MeloLyricsPolicy.currentLineIndex(lines, 16_000))
    }

    // --- userAgent --------------------------------------------------------------------------

    @Test
    fun userAgentWithoutContactIsJustNameAndVersion() {
        assertEquals("MeloVault/0.6.96-beta", MeloLyricsPolicy.userAgent("0.6.96-beta", null))
        assertEquals("MeloVault/0.6.96-beta", MeloLyricsPolicy.userAgent("0.6.96-beta", "  "))
    }

    @Test
    fun userAgentWithContactAndNoHeaderInjection() {
        assertEquals("MeloVault/1.0 (https://example.org)", MeloLyricsPolicy.userAgent("1.0", "https://example.org"))
        val forged = MeloLyricsPolicy.userAgent("1.0\r\nX-Evil: 1", "a@b.c\nX: y")
        assertFalse(forged.contains('\r') || forged.contains('\n'))
    }
}
