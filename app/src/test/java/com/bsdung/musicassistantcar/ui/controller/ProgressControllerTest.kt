package com.bsdung.musicassistantcar.ui.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressControllerTest {

    @Test
    fun formatsZeroAndNegativeAsZeros() {
        assertEquals("00:00", ProgressController.formatTimeMs(0L))
        assertEquals("00:00", ProgressController.formatTimeMs(-100L))
    }

    @Test
    fun formatsMinutesAndSecondsCorrectly() {
        assertEquals("00:05", ProgressController.formatTimeMs(5000L))
        assertEquals("01:30", ProgressController.formatTimeMs(90000L))
        assertEquals("04:15", ProgressController.formatTimeMs(255000L))
        assertEquals("59:59", ProgressController.formatTimeMs(3599000L))
    }

    @Test
    fun formatsHoursWhenOverOneHour() {
        assertEquals("1:00:00", ProgressController.formatTimeMs(3600000L))
        assertEquals("1:23:45", ProgressController.formatTimeMs(5025000L))
    }
}
