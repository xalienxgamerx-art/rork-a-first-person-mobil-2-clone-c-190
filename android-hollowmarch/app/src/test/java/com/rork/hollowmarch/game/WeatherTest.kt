package com.rork.hollowmarch.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {

    private val seed = 424242L

    @Test
    fun frontsAreRolledByTheCalendarNotByLuck() {
        for (day in 1..60) {
            assertEquals(Weather.forDay(seed, day), Weather.forDay(seed, day))
        }
        // and the weather differs across seeds
        assertTrue((1..40).any { Weather.forDay(seed, it) != Weather.forDay(seed + 1L, it) })
    }

    @Test
    fun everyKindOfSkyComesAround() {
        val seen = mutableSetOf<WeatherKind>()
        for (day in 1..400) seen += Weather.forDay(seed, day)
        assertTrue(seen.containsAll(WeatherKind.values().toSet()))
    }

    @Test
    fun frontsEaseInRatherThanFlip() {
        // across the small hours the cloud moves gradually
        val before = Weather.at(seed, 10, 0.079f, false).cloud
        val after = Weather.at(seed, 10, 0.26f, false).cloud
        assertTrue(kotlin.math.abs(after - before) < 0.6f)
        // and mid-morning the day's front holds steady
        val noon = Weather.at(seed, 10, 0.5f, false)
        val evening = Weather.at(seed, 10, 0.9f, false)
        assertEquals(noon.cloud, evening.cloud, 0.001f)
    }

    @Test
    fun rainWaitsForCloud() {
        for (day in 1..200) {
            val w = Weather.at(seed, day, 0.5f, false)
            if (w.kind == WeatherKind.CLEAR || w.kind == WeatherKind.HAZE) {
                assertTrue("dry under ${w.kind.label}", w.rain < 0.01f)
            }
            if (w.kind == WeatherKind.RAIN) assertTrue(w.rain > 0.1f)
        }
    }

    @Test
    fun theMarshThickensTheAir() {
        for (day in 1..100) {
            val open = Weather.at(seed, day, 0.5f, false)
            val marsh = Weather.at(seed, day, 0.5f, true)
            assertTrue("day $day marsh fog", marsh.fogBoost > open.fogBoost)
        }
    }

    @Test
    fun cloudSwallowsTheStars() {
        // the spec the renderer draws by: night visibility under cloud
        fun visible(light: Float, cloud: Float): Float {
            val d = (1f - light).coerceIn(0f, 1f)
            return d * d * (1f - cloud * 0.95f)
        }
        assertTrue(visible(0.08f, 0f) > 0.7f)
        assertTrue(visible(0.08f, 1f) < 0.05f)
    }
}
