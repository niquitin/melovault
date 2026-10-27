package com.bsdung.musicassistantcar.data

import com.bsdung.musicassistantcar.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every shareable build must be inert toward the owner's infrastructure. */
class E9DistributionBoundaryTest {
    @Test fun shareableBuildHasNoOwnerEndpointsOrEmbeddedToken() {
        if (BuildConfig.APPLICATION_ID == "app.melovault.player" || BuildConfig.APPLICATION_ID == "app.melovault.player.open") {
            assertFalse(BuildConfig.PERSONAL_BACKEND_ENABLED)
            assertEquals("", BuildConfig.CAR_API_PRIMARY)
            assertEquals("", BuildConfig.CAR_API_SECONDARY)
            assertEquals("", BuildConfig.CAR_API_TOKEN)
        } else {
            assertEquals("com.bsdung.musicassistantcar", BuildConfig.APPLICATION_ID)
            assertTrue(BuildConfig.PERSONAL_BACKEND_ENABLED)
            // E9 must stop shipping an administrator bearer token, even in debug APKs.
            assertEquals("", BuildConfig.CAR_API_TOKEN)
        }
    }
}
