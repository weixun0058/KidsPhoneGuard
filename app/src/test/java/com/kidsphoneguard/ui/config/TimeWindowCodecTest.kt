package com.kidsphoneguard.ui.config

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeWindowCodecTest {
    @Test
    fun `parses the first valid range and formats it consistently`() {
        assertEquals(480 to 540, TimeWindowCodec.parseRange("08:00-09:00, 22:00-07:00"))
        assertEquals("08:00-09:00", TimeWindowCodec.formatRange(480, 540))
    }

    @Test
    fun `uses defaults for malformed ranges and normalizes values`() {
        assertEquals(1320 to 420, TimeWindowCodec.parseRange("invalid"))
        assertEquals("23:59", TimeWindowCodec.format(-1))
        assertEquals("00:00", TimeWindowCodec.format(24 * 60))
    }
}
