package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The named souls: keepers at home, beggars at the well, the watch, and the remembered slain. */
class ResidentTest {

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
    fun everyBuildingKeepsANamedKeeper() {
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        val surface = SiteGen.map(world, site, 0, null, null, 1)
        assertTrue("the place keeps buildings", surface.buildings.isNotEmpty())
        surface.buildings.forEachIndexed { i, b ->
            assertTrue("${b.name} keeps a named soul", b.keeper.isNotBlank())
            assertTrue(
                "${b.keeper} stands by the door of ${b.name}",
                surface.entities.any { it.resident && it.homeBuilding == i && it.name == b.keeper }
            )
        }
        assertTrue(
            "the homeless keep to the heart",
            surface.entities.any { it.resident && it.homeBuilding == -1 }
        )
        // same seed, same souls
        val again = SiteGen.map(world, site, 0, null, null, 1)
        assertEquals(
            surface.entities.filter { it.resident }.map { it.name },
            again.entities.filter { it.resident }.map { it.name }
        )
    }

    @Test
    fun theKeeperIsHomeWhenYouEnter() {
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        val surface = SiteGen.map(world, site, 0, null, null, 1)
        val building = surface.buildings.first { it.keeper.isNotBlank() }
        val room = SiteGen.buildingInterior(
            world, site, surface.buildings.indexOf(building), building,
            SiteGen.cultureId(world, site), 1, null
        )
        assertTrue(
            "${building.keeper} is home",
            room.entities.any { it.resident && it.name == building.keeper }
        )
    }

    @Test
    fun soulsWalkHomeAtNight() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        // dusk of the fifth day: the streets empty, the homeless stay out
        engine.minutes = 4 * 1440f + 21 * 60f
        val before = engine.map.entities.count { it.resident && it.homeBuilding >= 0 }
        assertTrue("the keepers are out at dusk", before > 0)
        engine.update(0.2f)
        assertEquals(
            "the keepers have gone in through their doors",
            0,
            engine.map.entities.count { it.resident && it.homeBuilding >= 0 }
        )
        assertTrue(
            "the homeless keep to the streets",
            engine.map.entities.any { it.resident && it.homeBuilding == -1 }
        )
    }

    @Test
    fun slayingASoulCostsThePlace() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        val soul = engine.map.entities.first { it.resident && it.homeBuilding >= 0 }
        val regardBefore = engine.reputation.regardFor(site.id)
        soul.hp = 1
        engine.camera.x = soul.x - 1.0f
        engine.camera.y = soul.y
        engine.camera.angle = 0f

        // the blow may find air first
        var guard = 0
        while (soul.alive && guard++ < 40) {
            engine.strikeCooldown = 0f
            engine.fatigue = engine.maxFatigue
            engine.strike()
        }

        assertFalse("${soul.name} falls", soul.alive)
        assertEquals(
            "the death is counted against the year",
            1,
            engine.settlements.gravesWaiting(site.id)
        )
        assertTrue(
            "the place turns on you",
            engine.reputation.regardFor(site.id) < regardBefore
        )
        assertTrue(
            "the watch comes running",
            engine.map.entities.any { it.name == "${site.name} watchman" }
        )
        assertTrue(
            "the deed is remembered",
            engine.deeds.any { it == "Slew ${soul.name} at ${site.name}" }
        )
        // The world remembers this one soul by its own stable id, never by its name.
        val id = soul.persistId
        assertTrue("the soul carries a stable id", id.isNotBlank())
        assertTrue("the world remembers it slain", engine.worldState.isDead(id))
        val saved = engine.toSaveSlot()
        assertTrue("the world's memory rides the save", saved.worldState.contains(id))
        assertTrue(
            "the death survives the save",
            GameEngine(world, saved, null).worldState.isDead(id)
        )
    }

    @Test
    fun slainSoulsDoNotReturn() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        val soul = engine.map.entities.first { it.resident && it.homeBuilding >= 0 }
        soul.hp = 1
        engine.camera.x = soul.x - 1.0f
        engine.camera.y = soul.y
        engine.camera.angle = 0f
        // the blow may find air first
        var guard = 0
        while (soul.alive && guard++ < 40) {
            engine.strikeCooldown = 0f
            engine.fatigue = engine.maxFatigue
            engine.strike()
        }

        // out onto the road, and back in through the gate
        stepThrough(engine, engine.map.portals.first { it.targetFloor == OverlandGen.OVERLAND_FLOOR })
        assertTrue(engine.onOverland)
        enterSite(engine, site.id)
        assertFalse(
            "${soul.name} does not stand in the yard again",
            engine.map.entities.any { it.resident && it.name == soul.name }
        )
        // but the place goes on: other souls keep their doors
        assertTrue(engine.map.buildings.isNotEmpty())
    }

    @Test
    fun soulsTalk() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        // a homeless soul, away from every door
        val beggar = engine.map.entities.first { it.resident && it.homeBuilding == -1 }
        engine.camera.x = beggar.x - 1.0f
        engine.camera.y = beggar.y
        engine.camera.angle = 0f
        assertTrue(engine.usePrompt().contains("Speak with"))
        val rumorsBefore = engine.rumors.size
        assertEquals(Interact.TRAVELER, engine.interact())
        assertTrue("a rumor for the hearing", engine.rumors.size > rumorsBefore)
        assertTrue("the soul stays in the yard", engine.map.entities.contains(beggar))
    }

    @Test
    fun theYearsRememberTheGraves() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val site = settlements.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }
        enterSite(engine, site.id)
        val soul = engine.map.entities.first { it.resident && it.homeBuilding >= 0 }
        soul.hp = 1
        engine.camera.x = soul.x - 1.0f
        engine.camera.y = soul.y
        engine.camera.angle = 0f
        // the blow may find air first
        var guard = 0
        while (soul.alive && guard++ < 40) {
            engine.strikeCooldown = 0f
            engine.fatigue = engine.maxFatigue
            engine.strike()
        }
        engine.minutes += 400f * 1440f
        engine.stepFolkYears()
        assertTrue(
            "the grave is written in the chronicle",
            engine.chronicle.any {
                it.kind == com.rork.hollowmarch.world.EventKind.DEATH && it.text.contains(site.name)
            }
        )
        assertNotNull(engine.folkOf(site.id))
    }
}
