package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyTest {

    private val world = WorldGenerator.generate(424242L)

    @Test
    fun sameSeedRollsTheSameHeavens() {
        val a = SkyGen.build(world)
        val b = SkyGen.build(world)
        assertEquals(a.moons, b.moons)
        assertEquals(a.stars, b.stars)
        assertEquals(a.constellations, b.constellations)
        assertEquals(a.bandTilt, b.bandTilt, 1e-6f)
        assertEquals(a.bandBearing, b.bandBearing, 1e-6f)
    }

    @Test
    fun differentWorldsWalkDifferentSkies() {
        val a = SkyGen.build(world)
        val b = SkyGen.build(WorldGenerator.generate(99L))
        assertTrue(a.moons != b.moons || a.stars != b.stars)
    }

    @Test
    fun everyWorldHasOneToThreeMoons() {
        for (seed in 1L..25L) {
            val sky = SkyGen.build(WorldGenerator.generate(seed * 31L))
            assertTrue("moon count for seed ${seed * 31L}", sky.moons.size in 1..3)
            sky.moons.forEach { moon ->
                assertTrue(moon.size in 0.04f..0.10f)
                assertTrue(moon.periodDays >= 12f)
            }
        }
    }

    @Test
    fun moonPhasesTurnAcrossTheDays() {
        val sky = SkyGen.build(world)
        val moon = sky.moons.first()
        val illum: (Float) -> Float = { day ->
            0.5f + 0.5f * kotlin.math.sin(2f * Math.PI.toFloat() * day / moon.periodDays + moon.phase0)
        }
        val early = illum(1f)
        val mid = illum(moon.periodDays / 4f)
        val later = illum(moon.periodDays / 2f)
        assertTrue(
            "the phase moves across the days",
            kotlin.math.abs(mid - early) > 0.1f || kotlin.math.abs(later - early) > 0.1f
        )
        // and the cycle returns a full period later
        assertEquals(early, illum(1f + moon.periodDays), 0.001f)
    }

    @Test
    fun theSunClimbsToNoonAndSinksPastDusk() {
        assertTrue(SkyGen.sunElevation(0.5f) > 0.9f)
        assertTrue(SkyGen.sunElevation(0.25f) in -0.05f..0.05f)
        assertTrue(SkyGen.sunElevation(0.75f) in -0.05f..0.05f)
        assertTrue(SkyGen.sunElevation(0.0f) < 0f)
        assertTrue(SkyGen.sunElevation(0.5f) > SkyGen.sunElevation(0.30f))
    }

    @Test
    fun constellationsAreNamedFromTheChronicle() {
        val sky = SkyGen.build(world)
        assertTrue(sky.constellations.isNotEmpty())
        sky.constellations.forEach { con ->
            assertTrue(con.name.isNotBlank())
            assertTrue(con.lore.isNotBlank())
            assertTrue("star count of ${con.name}", con.stars.size in 4..9)
        }
        // every name borrows a word of the world's own record — a kind, or a named omen's coinage
        sky.constellations.forEach { con ->
            val borrowed = world.deities.any { con.name.contains(it.name) } ||
                world.figures.any { con.name.contains(it.name) } ||
                world.events.any {
                    con.name.contains(it.kind.name, ignoreCase = true) ||
                        (it.subject.isNotBlank() && con.name.contains(it.subject))
                }
            assertTrue("constellation ${con.name} borrowed from the chronicle", borrowed)
        }
    }
}
