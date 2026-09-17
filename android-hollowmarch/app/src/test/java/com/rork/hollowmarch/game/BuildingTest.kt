package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.StructureKind
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Doors lead in, rooms match their footprints, beds sleep dry, and theft is remembered. */
class BuildingTest {

    private val world = WorldGenerator.generate(424242L)
    private val settlements = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }

    private fun enterSite(engine: GameEngine, siteId: Int) {
        val door = engine.map.portals.first { it.targetSiteId == siteId }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
    }

    private fun stepThrough(engine: GameEngine, portal: Portal) {
        engine.camera.x = portal.x
        engine.camera.y = portal.y
        engine.usePortal()
    }

    @Test
    fun everyDoorLeadsInsideAndBackOut() {
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        val surface = SiteGen.map(world, site, 0, null, null, 1)
        assertTrue(surface.buildings.isNotEmpty())
        assertEquals(surface.buildings.size, surface.arrivalSpots.size)
        surface.buildings.forEachIndexed { i, b ->
            val door = surface.portals.first { it.targetFloor == SiteGen.BUILDING_FLOOR_BASE + i }
            assertEquals("the door of ${b.name}", door.label)
            assertEquals(
                "the doorway of ${b.name} stands open",
                0,
                surface.walls[b.doorY * surface.width + b.doorX]
            )
            val out = surface.arrivalSpots[i]
            assertFalse("you stand outside ${b.name}", surface.isWall(out.first, out.second))
            val inside = out.first.toInt() >= b.x && out.first.toInt() < b.x + b.w &&
                out.second.toInt() >= b.y && out.second.toInt() < b.y + b.h
            assertFalse("the standing spot is not inside ${b.name}", inside)
        }
    }

    @Test
    fun aDoorPutsYouInExactlyThatFootprint() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        val yard = engine.map
        val building = yard.buildings.first()
        stepThrough(engine, yard.portals.first { it.targetFloor >= SiteGen.BUILDING_FLOOR_BASE })

        assertEquals(SiteGen.BUILDING_FLOOR_BASE, engine.depth)
        assertFalse(engine.outdoor)
        assertEquals(building.name, engine.map.title)
        assertEquals("the room is the footprint", building.w, engine.map.width)
        assertEquals(building.h, engine.map.height)
        for (y in 0 until building.h) {
            for (x in 0 until building.w) {
                val perimeter = x == 0 || x == building.w - 1 || y == 0 || y == building.h - 1
                val isDoor = x == building.doorX - building.x && y == building.doorY - building.y
                val wall = engine.map.walls[y * building.w + x]
                if (perimeter && !isDoor) assertTrue(wall != 0) else assertEquals(0, wall)
            }
        }
        // and the way out lands you by the door you came in, in the same yard
        val out = engine.map.portals.first { !it.down && it.targetFloor == 0 }
        stepThrough(engine, out)
        assertEquals(0, engine.depth)
        assertTrue(engine.outdoor)
        assertTrue("the same yard you left", engine.map === yard)
        assertEquals(yard.arrivalSpots[0].first, engine.camera.x, 0.001f)
        assertEquals(yard.arrivalSpots[0].second, engine.camera.y, 0.001f)
    }

    @Test
    fun interiorsHoldTheirKeepersThings() {
        val site = world.sites.first {
            it.structures.any { s -> s.kind == StructureKind.TAVERN && !s.ruined }
        }
        val surface = SiteGen.map(world, site, 0, null, null, 1)
        val tavern = surface.buildings.first { it.kind == "tavern" }
        val room = SiteGen.buildingInterior(
            world, site, surface.buildings.indexOf(tavern), tavern,
            SiteGen.cultureId(world, site), 1, null
        )
        assertTrue("the tavern keeps a till", room.entities.any { it.container })
        assertTrue("the tavern keeps a hearth", room.entities.any { it.name == "the hearth" })
        assertTrue("the doorway leads out", room.portals.any { it.targetFloor == 0 })
        // a tent keeps a bedroll and nothing grander
        val tent = BuildingFootprint(2, 2, 3, 3, 3, 4, "tent", "tent")
        val tentRoom = SiteGen.buildingInterior(world, site, 90, tent, -1, 1, null)
        assertTrue(tentRoom.entities.any { it.name == "bedroll" })
        assertTrue(tentRoom.entities.none { it.container })
    }

    @Test
    fun restIndoorsIsDryAndAmbushFree() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        engine.brass = 50
        stepThrough(engine, engine.map.portals.first { it.targetFloor >= SiteGen.BUILDING_FLOOR_BASE })
        assertNotNull(engine.inBuilding)
        engine.fatigue = 10
        engine.vitality = 10
        val enemiesBefore = engine.map.entities.count { it.kind == EntityKind.ENEMY }
        engine.rest(8)
        assertEquals(
            "no ambush indoors",
            enemiesBefore,
            engine.map.entities.count { it.kind == EntityKind.ENEMY }
        )
        assertTrue("dry rest pays the full rate: ${engine.fatigue}", engine.fatigue > 10)
        assertTrue(engine.vitality > 10)
    }

    @Test
    fun innsTakeBrass() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = world.sites.first {
            it.structures.any { s -> s.kind == StructureKind.TAVERN && !s.ruined }
        }
        enterSite(engine, site.id)
        val tavernIdx = engine.map.buildings.indexOfFirst { it.kind == "tavern" }
        assertTrue(tavernIdx >= 0)
        stepThrough(
            engine,
            engine.map.portals.first { it.targetFloor == SiteGen.BUILDING_FLOOR_BASE + tavernIdx }
        )
        assertEquals("tavern", engine.inBuilding?.kind)

        engine.brass = 0
        engine.fatigue = 5
        engine.rest(8)
        assertEquals("no brass, no bed", 0, engine.brass)
        assertEquals(5, engine.fatigue)

        engine.brass = 12
        engine.rest(8)
        assertEquals(7, engine.brass)
        assertTrue(engine.fatigue > 5)
    }

    @Test
    fun stealingFromASettlementCostsRegard() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first {
            baseStageOf(it.kind).ordinal >= SettlementStage.TOWN.ordinal
        }
        enterSite(engine, site.id)
        val stall = engine.map.entities.firstOrNull { it.container }
        assertNotNull("the market keeps stalls", stall)
        if (stall!!.loot.isEmpty() && stall.lootBrass == 0) {
            stall.loot += Item(ItemArchetype.REMEDY, Material.VERDIGRIS)
        }
        val before = engine.reputation.regardFor(site.id)
        engine.takeAll(stall)
        assertTrue("theft is remembered", engine.reputation.regardFor(site.id) < before)
        // once per chest: taking again costs nothing more
        val after = engine.reputation.regardFor(site.id)
        engine.takeAll(stall)
        assertEquals(after, engine.reputation.regardFor(site.id))
    }

    @Test
    fun campTentsAreEnterable() {
        val camp = world.sites.first { it.kind == SiteKind.CAMP && !it.ruined }
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        enterSite(engine, camp.id)
        assertTrue("the warband keeps tents", engine.map.buildings.isNotEmpty())
        val tent = engine.map.buildings.first()
        val idx = engine.map.buildings.indexOf(tent)
        stepThrough(
            engine,
            engine.map.portals.first { it.targetFloor == SiteGen.BUILDING_FLOOR_BASE + idx }
        )
        assertEquals(tent.w, engine.map.width)
        assertEquals(tent.name, engine.map.title)
        assertTrue(engine.map.entities.any { it.name == "bedroll" })
    }
}
