package app.ucon.measure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProbesStatsTest {
    @Test
    fun constant_values_give_zero_jitter() {
        val s = listOf(10.0, 10.0, 10.0, 10.0).toLatencyStats()
        assertEquals(10.0, s.min)
        assertEquals(10.0, s.max)
        assertEquals(10.0, s.avg)
        assertEquals(0.0, s.jitter)
        assertEquals(4, s.samples)
    }

    @Test
    fun jitter_is_mean_absolute_deviation() {
        val s = listOf(10.0, 20.0, 30.0, 40.0).toLatencyStats()
        assertEquals(10.0, s.min)
        assertEquals(40.0, s.max)
        assertEquals(25.0, s.avg)
        // MAD = (15 + 5 + 5 + 15) / 4 = 10
        assertEquals(10.0, s.jitter)
    }

    @Test
    fun single_sample_has_zero_jitter() {
        val s = listOf(42.0).toLatencyStats()
        assertEquals(42.0, s.avg)
        assertEquals(0.0, s.jitter)
        assertTrue(s.samples == 1)
    }
}
