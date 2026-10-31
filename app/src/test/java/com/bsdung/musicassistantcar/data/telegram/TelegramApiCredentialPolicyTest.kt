package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.*
import org.junit.Test

class TelegramApiCredentialPolicyTest {
    private val hash = "a1".repeat(16) // Synthetic fixture, never a real developer credential.
    private val user = TelegramApiCredentialPolicy.Credentials(42, hash, TelegramApiCredentialPolicy.Source.USER)
    @Test fun emptyId() { assertNull(TelegramApiCredentialPolicy.parseId("")) }
    @Test fun zeroId() { assertNull(TelegramApiCredentialPolicy.parseId("0")) }
    @Test fun negativeId() { assertNull(TelegramApiCredentialPolicy.parseId("-1")) }
    @Test fun textId() { assertNull(TelegramApiCredentialPolicy.parseId("abc")) }
    @Test fun elevenDigits() { assertNull(TelegramApiCredentialPolicy.parseId("12345678901")) }
    @Test fun trimmedId() { assertEquals(123, TelegramApiCredentialPolicy.parseId(" 123 ")) }
    @Test fun maxId() { assertEquals(Int.MAX_VALUE, TelegramApiCredentialPolicy.parseId("2147483647")) }
    @Test fun overflowId() { assertNull(TelegramApiCredentialPolicy.parseId("2147483648")) }
    @Test fun nonAsciiDigits() { assertNull(TelegramApiCredentialPolicy.parseId("１２３")) }
    @Test fun plusId() { assertNull(TelegramApiCredentialPolicy.parseId("+123")) }
    @Test fun shortHash() { assertNull(TelegramApiCredentialPolicy.parseHash("a".repeat(31))) }
    @Test fun longHash() { assertNull(TelegramApiCredentialPolicy.parseHash("a".repeat(33))) }
    @Test fun nonHexHash() { assertNull(TelegramApiCredentialPolicy.parseHash("g".repeat(32))) }
    @Test fun normalizedHash() { assertEquals(hash, TelegramApiCredentialPolicy.parseHash("  ${hash.uppercase()} \n")) }
    @Test fun embeddedWins() {
        assertEquals(TelegramApiCredentialPolicy.Credentials(123, hash, TelegramApiCredentialPolicy.Source.EMBEDDED),
            TelegramApiCredentialPolicy.resolve(123, hash, user))
    }
    @Test fun storedFallback() { assertEquals(user, TelegramApiCredentialPolicy.resolve(0, "", user)) }
    @Test fun bothMissing() { assertNull(TelegramApiCredentialPolicy.resolve(0, "", null)) }
    @Test fun malformedEmbeddedFallback() { assertEquals(user, TelegramApiCredentialPolicy.resolve(123, "a".repeat(31), user)) }
    @Test fun invalidStoredRejected() { assertNull(TelegramApiCredentialPolicy.resolve(0, "", user.copy(apiId = 0))) }
    @Test fun invalidEmbeddedIdRejected() { assertNull(TelegramApiCredentialPolicy.resolve(-1, hash, null)) }
    @Test fun validationReportsBothEmptyFields() {
        val result = TelegramApiCredentialPolicy.validate(" ", "")
        assertFalse(result.ok)
        assertEquals(TelegramApiCredentialPolicy.Error.ID_EMPTY, result.idError)
        assertEquals(TelegramApiCredentialPolicy.Error.HASH_EMPTY, result.hashError)
    }
    @Test fun validationReportsBothInvalidFields() {
        val result = TelegramApiCredentialPolicy.validate("abc", "12345")
        assertEquals(TelegramApiCredentialPolicy.Error.ID_INVALID, result.idError)
        assertEquals(TelegramApiCredentialPolicy.Error.HASH_INVALID, result.hashError)
    }
    @Test fun validInputReturnsNormalizedUserCredentials() {
        val result = TelegramApiCredentialPolicy.validate(" 42 ", hash.uppercase())
        assertTrue(result.ok)
        assertEquals(user, result.credentials)
        assertNull(result.idError)
        assertNull(result.hashError)
    }
    @Test fun emptySecretNeverInsertsReplacementBetweenCharacters() {
        assertEquals("abc", TelegramApiCredentialPolicy.scrub("abc", listOf("")))
    }
    @Test fun blankSecretsLeaveWhitespaceUntouched() {
        assertEquals("a b\nc", TelegramApiCredentialPolicy.scrub("a b\nc", listOf(" ", "\n", "\t")))
    }
    @Test fun multipleSecretsAndRepeatedOccurrences() {
        assertEquals("[redacted] [redacted] [redacted]", TelegramApiCredentialPolicy.scrub("secret other secret", listOf("", "secret", "other")))
    }
    @Test fun uppercaseSecretAlsoRedacted() {
        assertEquals("[redacted]", TelegramApiCredentialPolicy.scrub(hash.uppercase(), listOf(hash)))
    }
    @Test fun longerOverlappingSecretRedactedFirst() {
        assertEquals("[redacted]", TelegramApiCredentialPolicy.scrub("abcdef", listOf("abc", "abcdef")))
    }
    @Test fun maskedIdOnlyShowsLastTwoDigits() { assertEquals("•••• 34", TelegramApiCredentialPolicy.maskId(1234)) }
    @Test fun invalidIdError() { assertNotNull(TelegramApiCredentialPolicy.loginErrorFor("400 API_ID_INVALID")) }
    @Test fun invalidHashError() { assertNotNull(TelegramApiCredentialPolicy.loginErrorFor("API_HASH_INVALID")) }
    @Test fun publishedIdError() { assertNotNull(TelegramApiCredentialPolicy.loginErrorFor("API_ID_PUBLISHED_FLOOD")) }
    @Test fun otherLoginErrorsRemainWithExistingPolicy() {
        assertNull(TelegramApiCredentialPolicy.loginErrorFor(null))
        assertNull(TelegramApiCredentialPolicy.loginErrorFor("PHONE_NUMBER_INVALID"))
    }
    @Test fun credentialToStringDoesNotExposeValues() {
        assertFalse(user.toString().contains(hash))
        assertFalse(user.toString().contains("42"))
    }

    // 2026-10-30 D6: resolve(..., preferStored) for the open build's "saved code wins" rule.
    @Test fun preferStoredWinsOverEmbedded() {
        assertEquals(user, TelegramApiCredentialPolicy.resolve(123, hash, user, preferStored = true))
    }
    @Test fun preferStoredFallsBackToEmbeddedWhenNoStored() {
        assertEquals(
            TelegramApiCredentialPolicy.Credentials(123, hash, TelegramApiCredentialPolicy.Source.EMBEDDED),
            TelegramApiCredentialPolicy.resolve(123, hash, null, preferStored = true)
        )
    }
    @Test fun preferStoredFallsBackToEmbeddedWhenStoredInvalid() {
        assertEquals(
            TelegramApiCredentialPolicy.Credentials(123, hash, TelegramApiCredentialPolicy.Source.EMBEDDED),
            TelegramApiCredentialPolicy.resolve(123, hash, user.copy(apiId = 0), preferStored = true)
        )
    }
    @Test fun preferStoredNullWhenNothingValid() {
        assertNull(TelegramApiCredentialPolicy.resolve(0, "", null, preferStored = true))
    }
    @Test fun preferStoredFalseByDefaultKeepsEmbeddedWins() {
        assertEquals(
            TelegramApiCredentialPolicy.Credentials(123, hash, TelegramApiCredentialPolicy.Source.EMBEDDED),
            TelegramApiCredentialPolicy.resolve(123, hash, user)
        )
    }

    // 2026-10-30 D7/D2: rejectionAction's three branches, each against the three known error codes.
    private val knownApiErrors = listOf("400 API_ID_INVALID", "API_HASH_INVALID", "API_ID_PUBLISHED_FLOOD")

    @Test fun rejectionActionUserSourceAlwaysReenterUser() {
        for (message in knownApiErrors) {
            assertEquals(
                message, TelegramApiCredentialPolicy.RejectAction.REENTER_USER,
                TelegramApiCredentialPolicy.rejectionAction(TelegramApiCredentialPolicy.Source.USER, openBuild = true, message)
            )
            assertEquals(
                message, TelegramApiCredentialPolicy.RejectAction.REENTER_USER,
                TelegramApiCredentialPolicy.rejectionAction(TelegramApiCredentialPolicy.Source.USER, openBuild = false, message)
            )
        }
    }
    @Test fun rejectionActionEmbeddedOpenBuildOffersOwnApi() {
        for (message in knownApiErrors) {
            assertEquals(
                message, TelegramApiCredentialPolicy.RejectAction.OFFER_OWN_API,
                TelegramApiCredentialPolicy.rejectionAction(TelegramApiCredentialPolicy.Source.EMBEDDED, openBuild = true, message)
            )
        }
    }
    @Test fun rejectionActionEmbeddedPrivateBuildReturnsNone() {
        for (message in knownApiErrors) {
            assertEquals(
                message, TelegramApiCredentialPolicy.RejectAction.NONE,
                TelegramApiCredentialPolicy.rejectionAction(TelegramApiCredentialPolicy.Source.EMBEDDED, openBuild = false, message)
            )
        }
    }
    @Test fun rejectionActionUnmatchedMessageReturnsNone() {
        assertEquals(
            TelegramApiCredentialPolicy.RejectAction.NONE,
            TelegramApiCredentialPolicy.rejectionAction(TelegramApiCredentialPolicy.Source.USER, openBuild = true, "PHONE_NUMBER_INVALID")
        )
    }
    @Test fun rejectionActionNullSourceAndMessageReturnsNone() {
        assertEquals(
            TelegramApiCredentialPolicy.RejectAction.NONE,
            TelegramApiCredentialPolicy.rejectionAction(null, openBuild = true, null)
        )
    }
}
