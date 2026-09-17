package com.rork.hollowmarch.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyEventTest {

    private val seed = 424242L

    @Test
    fun showerNightsAreFewAndKept() {
        val nights = (1..400).count { SkyEvents.showerNight(seed, it) }
        assertTrue("showers in 400 days: $nights", nights in 8..60)
        for (day in 1..200) {
            assertEquals(SkyEvents.showerNight(seed, day), SkyEvents.showerNight(seed, day))
        }
    }

    @Test
    fun auroraNightsAreFewAndKept() {
        val nights = (1..400).count { SkyEvents.auroraNight(seed, it) }
        assertTrue("auroras in 400 days: $nights", nights in 10..90)
        for (day in 1..200) {
            assertEquals(SkyEvents.auroraNight(seed, day), SkyEvents.auroraNight(seed, day))
        }
    }

    @Test
    fun theAuroraStaysNorthOfTheFarThird() {
        val day = (1..400).first { SkyEvents.auroraNight(seed, it) }
        assertEquals(0f, SkyEvents.auroraStrength(seed, day, 0.2f, 0f), 0f)
        assertEquals(0f, SkyEvents.auroraStrength(seed, day, 0.5f, 0f), 0f)
        assertTrue(SkyEvents.auroraStrength(seed, day, 0.8f, 0f) > 0f)
        // stronger the further north you stand
        assertTrue(
            SkyEvents.auroraStrength(seed, day, 0.99f, 0f) >
                SkyEvents.auroraStrength(seed, day, 0.75f, 0f)
        )
        // and thick cloud snuffs it out
        assertEquals(0f, SkyEvents.auroraStrength(seed, day, 0.95f, 0.8f), 0f)
    }

    @Test
    fun cloudHidesTheFallingStars() {
        assertTrue(SkyEvents.meteors(seed, 5, 10f, true, 0.9f).isEmpty())
    }

    @Test
    fun noFallingStarsByDay() {
        assertTrue(SkyEvents.meteors(seed, 5, 10f, false, 0f).isEmpty())
    }

    @Test
    fun showersDriveMoreFallingStarsThanLoneNights() {
        val showerDay = (1..400).first { SkyEvents.showerNight(seed, it) }
        val loneDay = (1..400).first { !SkyEvents.showerNight(seed, it) }
        var showerCount = 0
        var loneCount = 0
        for (t in 0 until 200) {
            showerCount += SkyEvents.meteors(seed, showerDay, t * 0.8f, true, 0f).size
            loneCount += SkyEvents.meteors(seed, loneDay, t * 0.8f, true, 0f).size
        }
        assertTrue("shower $showerCount vs lone $loneCount", showerCount > loneCount * 4)
    }

    @Test
    fun meteorsAreHashedNotRandom() {
        assertEquals(
            SkyEvents.meteors(seed, 9, 33.4f, true, 0f),
            SkyEvents.meteors(seed, 9, 33.4f, true, 0f)
        )
    }
}
