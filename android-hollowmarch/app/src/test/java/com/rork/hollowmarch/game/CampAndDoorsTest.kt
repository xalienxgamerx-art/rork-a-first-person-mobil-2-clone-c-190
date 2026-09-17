package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doors answer the USE hand, not the shoulder: a doorway bars the walk, the
 * prompt names it from a pace or two off, and camps keep folk of their own.
 */
class CampAndDoorsTest {

    private val world = WorldGenerator.generate(424242L)

    private fun enterSite(engine: GameEngine, siteId: Int) {
        val door = engine.map.portals.first { it.targetSiteId == siteId }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
    }

    /** Walk from a building's standing spot to its threshold, in small honest steps. */
    private fun walkToThreshold(
        yard: GameMap,
        engine: GameEngine,
        spot: Pair<Float, Float>,
        door: Portal
    ): String {
        val dx = if (door.x > spot.first + 0.1f) 1f else if (door.x < spot.first - 0.1f) -1f else 0f
        val dy = if (door.y > spot.second + 0.1f) 1f else if (door.y < spot.second - 0.1f) -1f else 0f
        assertTrue("the door lies a pace from the standing spot", dx != 0f || dy != 0f)
        var px = spot.first
        var py = spot.second
        var prompt = ""
        repeat(12) {
            val (nx, ny) = MapFactory.tryMove(yard, px, py, dx * 0.15f, dy * 0.15f)
            assertFalse("no walking through the doorway", yard.doorwayAt(nx, ny))
            px = nx
            py = ny
            engine.camera.x = px
            engine.camera.y = py
            prompt = engine.usePrompt()
        }
        return prompt
    }

    @Test
    fun campFolkKeepTheTents() {
        val camp = world.sites.first { it.kind == SiteKind.CAMP && !it.ruined }
        val yard = SiteGen.map(world, camp, 0, null, null, 1)
        assertTrue("the warband keeps tents", yard.buildings.isNotEmpty())
        yard.buildings.forEach { b ->
            assertTrue("${b.name} keeps a named soul", b.keeper.isNotBlank())
            assertTrue(
                "${b.keeper} keeps the door of ${b.name}",
                yard.entities.any {
                    it.resident && it.name == b.keeper &&
                        it.homeBuilding == yard.buildings.indexOf(b)
                }
            )
        }
        assertTrue(
            "the homeless keep to the fires",
            yard.entities.any { it.resident && it.homeBuilding == -1 }
        )
        // the war chest stands before the chief's tent, not walled inside its shell
        val chief = yard.buildings.first()
        val chest = yard.entities.first { it.container && it.name == "war chest" }
        val inside = chest.x.toInt() > chief.x && chest.x.toInt() < chief.x + chief.w - 1 &&
            chest.y.toInt() > chief.y && chest.y.toInt() < chief.y + chief.h - 1
        assertFalse("the war chest is reachable in the yard", inside)
    }

    @Test
    fun doorwaysBarTheShoulder() {
        val camp = world.sites.first { it.kind == SiteKind.CAMP && !it.ruined }
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        enterSite(engine, camp.id)
        val yard = engine.map
        val door = yard.portals.first { it.targetFloor >= SiteGen.BUILDING_FLOOR_BASE }
        val spot = yard.arrivalSpots[door.targetFloor - SiteGen.BUILDING_FLOOR_BASE]
        assertTrue("the doorway is marked as a door", yard.doorwayAt(door.x, door.y))
        assertFalse("the standing spot is no door", yard.doorwayAt(spot.first, spot.second))
        walkToThreshold(yard, engine, spot, door)
    }

    @Test
    fun theUseHandOpensTents() {
        val camp = world.sites.first { it.kind == SiteKind.CAMP && !it.ruined }
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        enterSite(engine, camp.id)
        val door = engine.map.portals.first { it.targetFloor >= SiteGen.BUILDING_FLOOR_BASE }
        val idx = door.targetFloor - SiteGen.BUILDING_FLOOR_BASE
        val building = engine.map.buildings[idx]
        val prompt = walkToThreshold(engine.map, engine, engine.map.arrivalSpots[idx], door)
        assertTrue("the door answers at the threshold: \"$prompt\"", prompt.contains("Enter"))

        engine.usePortal()
        assertEquals(SiteGen.BUILDING_FLOOR_BASE + idx, engine.depth)
        assertEquals(building.name, engine.map.title)
        // and the way out answers the same hand
        assertFalse(engine.outdoor)
        assertTrue(engine.usePrompt().isNotBlank())
        engine.usePortal()
        assertEquals(0, engine.depth)
        assertTrue(engine.outdoor)
    }

    @Test
    fun villageDoorsBarTheShoulderToo() {
        val site = world.sites.filter { it.isSettlement && !it.ruined && it.population > 0 }
            .first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        enterSite(engine, site.id)
        val yard = engine.map
        val door = yard.portals.first { it.targetFloor >= SiteGen.BUILDING_FLOOR_BASE }
        val spot = yard.arrivalSpots[door.targetFloor - SiteGen.BUILDING_FLOOR_BASE]
        walkToThreshold(yard, engine, spot, door)
    }
}
