package com.virtualrun.app.model

import org.junit.Assert.*
import org.junit.Test

class RunPaceTest {
    @Test fun freeInputParsesMinutesAndSecondsWithoutTheOldPresetLimits() {
        for ((input, seconds) in listOf("6:00" to 360, " 5:45 " to 345, "8：10" to 490,
            "6" to 360, "0:30" to 30, "0:01" to 1, "40:00" to 2400, "9999:59" to 599999)) {
            assertEquals(input, seconds, RunPace.parse(input))
            assertEquals(seconds, RunPace.parse(RunPace.format(seconds)))
        }
    }
    @Test fun invalidOrOverflowingInputsAreRejected() {
        for (input in listOf("", ":", "0:00", "0", "-1:00", "6:60", "6:-1", "6.5", "6:",
            "6:00:00", "NaN", "Infinity", "9999999999999999999999999999:00")) {
            assertNull(input, RunPace.parse(input))
        }
        assertThrows(IllegalArgumentException::class.java) { RunPace(0, 360) }
        assertThrows(IllegalArgumentException::class.java) { RunPace(390, 360) }
    }
    @Test fun fixedAndRangeModesHaveCorrectUnitsAndDoNotOverflow() {
        val fixed = RunPace(345, 345)
        assertTrue(fixed.isFixed)
        assertEquals(5.75f, fixed.referenceMinutesPerKm, 1e-6f)
        assertEquals("固定配速 5:45 /公里", fixed.displayText)
        val range = RunPace(300, 390)
        assertFalse(range.isFixed)
        assertEquals(5.75f, range.referenceMinutesPerKm, 1e-6f)
        assertEquals("配速范围 5:00–6:30 /公里", range.displayText)
        assertTrue(RunPace(Int.MAX_VALUE, Int.MAX_VALUE).referenceMinutesPerKm > 0)
        assertEquals("配速范围 7:03–7:26 /公里", RunPace().displayText)
    }
}
