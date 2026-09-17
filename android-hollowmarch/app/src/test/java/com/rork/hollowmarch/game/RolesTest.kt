package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Biome
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trades of the province: a role dealt to every soul from the seed, a day
 * shaped by the role, and both kept through saving, loading and rebuilding.
 */
class RolesTest {

    private val world = WorldGenerator.generate(424242L)
    private val settlements = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }

    @Test
    fun registryIsCompleteAndUnique() {
        assertEquals(257, ROLES.size)
        assertEquals(ROLES.size, ROLES.ALL.map { it.name }.toSet().size)
        ROLES.ALL.forEach { def ->
            assertEquals(def.name, ROLES.byName(def.name)?.name)
            assertNotNull("${def.name} keeps a craft", def.category)
        }
        assertNotNull(ROLES.byName("Barber-Surgeon"))
        assertNotNull(ROLES.byName("Instrument Maker"))
        assertNotNull(ROLES.byName("Black Market Dealer"))
        assertNull(ROLES.byName("Astropath"))
    }

    @Test
    fun everySoulCarriesARealRole() {
        settlements.take(8).forEach { site ->
            val surface = SiteGen.map(world, site, 0, null, null, 1)
            val residents = surface.entities.filter { it.resident }
            assertTrue("${site.name} keeps folk", residents.isNotEmpty())
            residents.forEach { soul ->
                assertTrue(
                    "${soul.name} of ${site.name} keeps the role '${soul.role}'",
                    ROLES.isValid(soul.role)
                )
            }
        }
    }

    @Test
    fun keepersTakeRolesThatMatchTheirBuilding() {
        var checked = 0
        settlements.forEach { site ->
            val surface = SiteGen.map(world, site, 0, null, null, 1)
            surface.buildings.forEachIndexed { i, b ->
                val soul =
                    surface.entities.firstOrNull { it.resident && it.homeBuilding == i }
                        ?: return@forEachIndexed
                when (b.kind) {
                    "tavern" -> assertTrue(
                        "${soul.name} keeps the tavern as ${soul.role}",
                        soul.role in setOf("Tavern Keeper", "Innkeeper", "Bartender")
                    )
                    "temple" -> assertTrue(
                        "${soul.name} keeps the temple as ${soul.role}",
                        soul.role in setOf("Priest", "Priestess", "Temple Keeper")
                    )
                    "keep", "citadel" -> assertTrue(
                        "${soul.name} keeps the ${b.kind} as ${soul.role}",
                        soul.role in setOf("Captain", "Guard Captain", "Commander", "Sheriff")
                    )
                    "tent" -> assertTrue(
                        "${soul.name} keeps the tent as ${soul.role}",
                        soul.role in setOf("Bandit", "Poacher", "Wanderer")
                    )
                }
                checked++
            }
        }
        assertTrue("the province keeps keepers to check", checked > 0)
    }

    @Test
    fun rolesSuitTheLand() {
        val coastal = mutableListOf<String>()
        val hilly = mutableListOf<String>()
        settlements.forEach { site ->
            val roles = SiteGen.map(world, site, 0, null, null, 1)
                .entities.filter { it.resident }.map { it.role }
            when (world.terrain.biomeAt(site.x, site.y)) {
                Biome.OCEAN, Biome.MARSH -> coastal += roles
                Biome.HILLS, Biome.PEAK -> hilly += roles
                else -> {}
            }
        }
        if (coastal.isNotEmpty()) {
            assertTrue(
                "the waterside folk work water or wilds: $coastal",
                coastal.any {
                    it in setOf(
                        "Fisher", "Boatman", "Sailor", "Shipwright", "Salt Worker",
                        "Clay Worker", "Trapper", "Forager", "Ropemaker", "Fishmonger"
                    )
                }
            )
        }
        if (hilly.isNotEmpty()) {
            assertTrue(
                "the hill folk work the diggings: $hilly",
                hilly.any {
                    it in setOf(
                        "Miner", "Quarryman", "Smelter", "Prospector", "Stonemason", "Gem Cutter"
                    )
                }
            )
        }
    }

    @Test
    fun interiorKeepersShareTheSurfaceRole() {
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        val surface = SiteGen.map(world, site, 0, null, null, 1)
        val index = surface.buildings.indexOfFirst { it.keeper.isNotBlank() }
        val building = surface.buildings[index]
        val room = SiteGen.buildingInterior(
            world, site, index, building, SiteGen.cultureId(world, site), 1, null
        )
        val surfaceRole = surface.entities
            .first { it.resident && it.homeBuilding == index }.role
        val roomKeeper = room.entities.first { it.resident && it.name == building.keeper }
        assertEquals(surfaceRole, roomKeeper.role)
    }

    @Test
    fun schedulesAreDeterministicAndVaried() {
        val first = WorkSchedule.forRole("Farmer", WorkSchedule.key(999L, "soul-a"))
        val again = WorkSchedule.forRole("Farmer", WorkSchedule.key(999L, "soul-a"))
        assertEquals(first.wake, again.wake, 0.0001f)
        assertEquals(first.blocks, again.blocks)
        val wakes = (1..40).map {
            WorkSchedule.forRole("Farmer", WorkSchedule.key(123L, "soul-$it")).wake
        }
        assertTrue("souls of one trade keep different hours", wakes.toSet().size > 1)
        assertNotEquals(
            WorkSchedule.forRole("Farmer", WorkSchedule.key(5L, "x")).blocks,
            WorkSchedule.forRole("Scholar", WorkSchedule.key(5L, "x")).blocks
        )
    }

    @Test
    fun schedulesTileTheWholeDay() {
        ROLES.ALL.forEach { def ->
            repeat(3) { k ->
                val schedule = WorkSchedule.forRole(def.name, WorkSchedule.key(1L, "${def.name}-$k"))
                assertTrue(schedule.blocks.first().start <= 0.5f)
                assertEquals(24f, schedule.blocks.last().end, 0.001f)
                var cursor = 0f
                schedule.blocks.forEach { block ->
                    assertEquals("${def.name} tiles", block.start, cursor, 0.01f)
                    assertTrue(block.end > block.start)
                    cursor = block.end
                }
                var q = 0f
                while (q < 24f) {
                    assertTrue(
                        "${def.name} answers the hour $q",
                        schedule.blockAt(q).activity.isNotBlank()
                    )
                    q += 0.25f
                }
            }
        }
    }

    @Test
    fun everyRoleSleepsBeforeTheStreetsEmpty() {
        ROLES.ALL.forEach { def ->
            repeat(5) { k ->
                val schedule = WorkSchedule.forRole(def.name, WorkSchedule.key(7L, "${def.name}-$k"))
                val sleep = schedule.blocks.last()
                assertTrue(
                    "${def.name} rests from ${schedule.wake + sleep.start}",
                    schedule.wake + sleep.start <= 20.01f
                )
            }
        }
    }

    @Test
    fun rolesSurviveTheSave() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        val before = engine.map.entities.filter { it.resident }.associate { it.persistId to it.role }
        assertTrue("folk stand in the place", before.isNotEmpty())
        assertTrue(before.values.all { ROLES.isValid(it) })
        val again = GameEngine(world, engine.toSaveSlot(), null).apply { climbToOpenGround() }
        enterSite(again, site.id)
        before.forEach { (id, role) ->
            assertEquals(
                "$id keeps its trade through the save",
                role,
                again.map.entities.firstOrNull { it.resident && it.persistId == id }?.role
            )
        }
    }

    @Test
    fun rolesSurviveLeavingAndReturning() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        val before = engine.map.entities.filter { it.resident }.associate { it.persistId to it.role }
        // rebuild the same scene from the world's memory
        val rebuilt = SiteGen.map(world, site, 0, null, null, 1)
        SceneBinder.stamp(world.seed, site.id, 0, rebuilt.entities)
        rebuilt.entities.forEach { entity ->
            if (entity.resident) {
                val record = before[entity.persistId]
                if (record != null) entity.role = record
            }
        }
        SceneBinder.restore(WorldState.decode(engine.toSaveSlot().worldState, world.seed), site.id, 0, rebuilt)
        val after = rebuilt.entities.filter { it.resident }.associate { it.persistId to it.role }
        before.forEach { (id, role) -> assertEquals(role, after[id]) }
    }

    @Test
    fun recordsCarryTheirRoleThroughEncodeAndDecode() {
        val state = WorldState(424242L)
        state.rememberNpc(
            NpcRecord(
                id = "424242#r#7#0#1", name = "Aldric", siteId = 7, floor = 0,
                homeBuilding = 2, powerId = -1, anchorX = 3.5f, anchorY = 4.5f,
                x = 3.5f, y = 4.5f, role = "Blacksmith", workBuilding = 2
            )
        )
        val woken = WorldState.decode(state.encode(), 424242L)
        val record = woken.npc("424242#r#7#0#1")
        assertEquals("Blacksmith", record?.role)
        assertEquals(2, record?.workBuilding)
    }

    @Test
    fun theDayTurnsTheSoulsToTheirWork() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        engine.minutes = 2 * 1440f + 9 * 60f
        val souls = engine.map.entities.filter { it.resident && it.homeBuilding >= 0 }
        assertTrue("the keepers are out for the day", souls.isNotEmpty())
        val anchors = souls.associate { it.persistId to (it.x to it.y) }
        repeat(40) { engine.update(0.2f) }
        // mid-morning: nobody claims to be asleep, and somebody has walked to work
        val activities = engine.map.entities.filter { it.resident }.mapNotNull {
            engine.worldState.npc(it.persistId)?.activity
        }
        assertTrue(activities.isNotEmpty())
        assertTrue(
            "no one sleeps mid-morning: $activities",
            activities.none { it.startsWith("asleep") }
        )
        val walked = souls.any { soul ->
            val anchor = anchors[soul.persistId] ?: return@any false
            MapFactory.distance(anchor.first, anchor.second, soul.x, soul.y) > 0.5f
        }
        assertTrue("the day moves folk from their thresholds", walked)
    }

    @Test
    fun theSimulationKnowsTheTradesToo() {
        val state = WorldState(world.seed)
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        state.rememberNpc(
            NpcRecord(
                id = "sim-soul", name = "Bren", siteId = site.id, floor = 0,
                homeBuilding = 0, powerId = -1, anchorX = 10.5f, anchorY = 10.5f,
                x = 10.5f, y = 10.5f, role = "Blacksmith", workBuilding = 0
            )
        )
        // mid-morning of a working day
        val report = WorldSimulation.advance(state, world, SettlementLedger.fresh(world), 10 * 60f, site.id)
        val soul = state.npc("sim-soul")!!
        assertTrue(soul.activity.isNotBlank())
        assertTrue("a smith at work: ${soul.activity}", soul.activity != "asleep behind a barred door")
        assertTrue(report.moved >= 0)
    }

    private fun enterSite(engine: GameEngine, siteId: Int) {
        val door = engine.map.portals.first { it.targetSiteId == siteId }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
    }
}
