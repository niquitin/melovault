package com.bsdung.musicassistantcar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCatalogTest {
    @Test
    fun demoIdsAreStableAndUnique() {
        val ids = DemoCatalog.mediaIds()

        assertEquals(3, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("demo_") })
        assertEquals("root", DemoCatalog.ROOT_ID)
    }
}
