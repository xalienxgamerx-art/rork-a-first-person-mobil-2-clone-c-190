package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The waking deep's climb: stair by stair, the way out, and the open road.
 *  Shared by every test harness that needs a delver back on the open ground. */
internal fun GameEngine.climbToOpenGround() {
    var guard = 0
    while (!onOverland && guard++ < 16) {
        val portal = when {
            inBuilding != null -> map.portals.firstOrNull { it.targetFloor == 0 }
            outdoor && depth == 0 ->
                map.portals.firstOrNull { it.targetFloor == OverlandGen.OVERLAND_FLOOR }
                    ?: map.portals.firstOrNull { !it.down }
            else -> map.portals.firstOrNull { !it.down }
        } ?: return
        camera.x = portal.x
        camera.y = portal.y
        usePortal()
    }
}

/** The first waking is the deep waking: the bottom of the sealed vault, and the
 *  climb out. Every hostile site after that is entered by its door, and the clock
 *  keeps twenty real minutes of daylight and fifteen of night. */
class DescentAndClockTest {

    private val world = WorldGenerator.generate(424242L)

    @Test
    fun theFirstWakeIsAtTheBottom() {
        val vault = world.site(world.vaultSiteId)
        val floors = SiteGen.floorCount(world, vault)
        assertTrue("the vault is deep", floors >= 2)
        val engine = GameEngine(world, null, null)
        assertEquals("the lowest floor", floors, engine.depth)
        assertFalse("under the earth", engine.outdoor)
        assertFalse(engine.onOverland)
        assertTrue("the deep keeps its heights", engine.map.wallHeights != null)
        assertFalse("no way out but up", engine.map.portals.any { it.label == "the way out" })
        assertTrue("the stair up waits", engine.map.portals.any { !it.down })
        assertTrue("the depths own the telling", engine.log.any { it.text.contains("bottom of") })
    }

    @Test
    fun theClimbOutEndsOnTheOpenGround() {
        val engine = GameEngine(world, null, null)
        engine.climbToOpenGround()
        assertTrue("daylight again", engine.outdoor)
        assertTrue("the open road", engine.onOverland)
        assertTrue(
            "the deed is written",
            engine.deeds.any { it.startsWith("Climbed out of") }
        )
    }

    @Test
    fun everySiteAfterOpensAtTheEntrance() {
        val vault = world.site(world.vaultSiteId)
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        // back in through the vault's own door: the yard, not the deep
        val door = engine.map.portals.first { it.targetSiteId == vault.id }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
        assertTrue(engine.outdoor)
        assertEquals(0, engine.depth)
        assertTrue("the doorway down waits", engine.map.portals.any { it.down })

        // and another hostile site altogether: its doorstep, not its depths
        val ruin = world.sites.first { it.kind == SiteKind.RUIN }
        engine.climbToOpenGround()
        val ruinDoor = engine.map.portals.first { it.targetSiteId == ruin.id }
        engine.camera.x = ruinDoor.x
        engine.camera.y = ruinDoor.y
        engine.usePortal()
        assertTrue(engine.outdoor)
        assertEquals(0, engine.depth)
    }

    @Test
    fun daylightLastsTwentyRealMinutes() {
        val engine = GameEngine(world, null, null)
        engine.minutes = 6f * 60f
        var seconds = 0f
        while (engine.timeOfDay >= 0.25f && engine.timeOfDay < 0.75f && seconds < 100000f) {
            // the clock is under test, not the delver: a spawn-adjacent hound must
            // not kill the simulation before the sun crosses the sky
            engine.vitality = engine.maxVitality
            engine.update(0.05f)
            seconds += 0.05f
        }
        assertEquals("the sun rides twenty real minutes", 1200f, seconds, 1f)
    }

    @Test
    fun nightLastsFifteenRealMinutes() {
        val engine = GameEngine(world, null, null)
        engine.minutes = 18f * 60f
        var seconds = 0f
        while ((engine.timeOfDay >= 0.75f || engine.timeOfDay < 0.25f) && seconds < 100000f) {
            engine.vitality = engine.maxVitality
            engine.update(0.05f)
            seconds += 0.05f
        }
        assertEquals("the night runs fifteen real minutes", 900f, seconds, 1f)
    }
}
