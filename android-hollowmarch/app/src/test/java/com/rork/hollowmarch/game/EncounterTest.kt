package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncounterTest {

    private val world = WorldGenerator.generate(424242L)

    @Test
    fun theSameWalkRollsTheSameTrouble() {
        val a = GameEngine(world, null, null).apply { climbToOpenGround() }
        val b = GameEngine(world, null, null).apply { climbToOpenGround() }
        repeat(6) { i ->
            a.rollEncounter(force = i * 17)
            b.rollEncounter(force = i * 17)
        }
        assertEquals(a.map.entities.map { it.name }, b.map.entities.map { it.name })
        assertEquals(a.map.entities.map { it.x }, b.map.entities.map { it.x })
    }

    @Test
    fun theRightTroubleComesFromTheRightGround() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        // a living beast's territory: roll low and its terror finds you
        val beast = world.beasts.firstOrNull { it.alive }
        if (beast != null) {
            val lairSpot = engine.overland.entrySpots.getValue(beast.lairSiteId)
            engine.camera.x = lairSpot.first
            engine.camera.y = lairSpot.second
            engine.rollEncounter(force = 10)
            assertTrue(
                "the beast roams its own country",
                engine.map.entities.any { it.beastId == beast.id }
            )
        }
        // a warband's country: roll mid and reavers step onto the road
        val camp = world.sites.first { it.kind == com.rork.hollowmarch.world.SiteKind.CAMP }
        val campSpot = engine.overland.entrySpots.getValue(camp.id)
        engine.camera.x = campSpot.first
        engine.camera.y = campSpot.second
        engine.rollEncounter(force = 50)
        assertTrue(
            "the warband's country breeds ambushes",
            engine.map.entities.any { it.name.contains("reaver") || it.name == "brigand" }
        )
    }

    @Test
    fun aTravelersWordBecomesAPin() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        // stand far from every place, so the road itself answers
        val far = farthestOpenSpot(engine)
        engine.camera.x = far.first
        engine.camera.y = far.second
        engine.rollEncounter(force = 95)
        val traveler = engine.map.entities.firstOrNull { it.traveler }
        assertNotNull("the empty road keeps travelers", traveler)
        val before = engine.rumors.count { it.siteId >= 0 }

        // walk up and speak with them
        engine.camera.x = traveler!!.x - 1.0f
        engine.camera.y = traveler.y
        assertEquals(Interact.TRAVELER, engine.interact())

        assertTrue(
            "the traveler's word names a place",
            engine.rumors.any { it.siteId >= 0 } && engine.rumors.size > 0
        )
        assertTrue(
            "the word becomes a pin on the Bearings sheet",
            engine.bearings().any { it.source == "rumor" } &&
                engine.rumors.count { it.siteId >= 0 } >= before
        )
    }

    private fun farthestOpenSpot(engine: GameEngine): Pair<Float, Float> {
        var best = Pair(160f, 160f)
        var bestDistance = -1f
        for (y in 20 until 300 step 10) {
            for (x in 20 until 300 step 10) {
                if (engine.overland.isWall(x.toFloat(), y.toFloat())) continue
                val d = world.sites.minOf { site ->
                    MapFactory.distance(site.x * 319f, site.y * 319f, x.toFloat(), y.toFloat())
                }
                if (d > bestDistance) {
                    bestDistance = d
                    best = Pair(x.toFloat(), y.toFloat())
                }
            }
        }
        return best
    }

    @Test
    fun aSlainBeastEmptiesItsLair() {
        val beast = world.beasts.firstOrNull { it.alive } ?: return
        val lair = world.site(beast.lairSiteId)
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val lairSpot = engine.overland.entrySpots.getValue(lair.id)
        engine.camera.x = lairSpot.first
        engine.camera.y = lairSpot.second
        engine.rollEncounter(force = 10)
        val foe = engine.map.entities.first { it.beastId == beast.id }

        // put it in front of the blade and cut it down
        engine.camera.x = foe.x - 1.3f
        engine.camera.y = foe.y
        engine.camera.angle = 0f
        foe.hp = 1
        // the blow may find air first
        var strikeGuard = 0
        while (foe.alive && strikeGuard++ < 40) {
            engine.strikeCooldown = 0f
            engine.fatigue = engine.maxFatigue
            engine.strike()
        }
        assertFalse("the beast falls in the open", foe.alive)
        assertTrue(engine.deeds.any { it.startsWith("Slew ${beast.name}") })

        assertTrue(
            "the world counts the beast slain",
            beast.id in engine.worldState.slainBeastIds()
        )
        val saved = engine.toSaveSlot()
        assertTrue(
            "the kill rides the save",
            beast.id in GameEngine(world, saved, null).worldState.slainBeastIds()
        )

        // the lair keeps no chronicle beast now: a fallback takes the empty post
        val again = GameEngine(world, saved, null)
        val door = again.overland.portals.first { it.targetSiteId == lair.id }
        again.camera.x = door.x
        again.camera.y = door.y
        again.usePortal()
        var guard = 0
        while (again.depth < SiteGen.floorCount(again.world, lair) && guard++ < 8) {
            val down = again.map.portals.first { it.down }
            again.camera.x = down.x
            again.camera.y = down.y
            again.usePortal()
        }
        val boss = again.map.entities.first { it.boss }
        assertNotEquals("the lair stands empty of its terror", beast.name, boss.name)
    }

    @Test
    fun learnedWordRidesTheSave() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val far = farthestOpenSpot(engine)
        engine.camera.x = far.first
        engine.camera.y = far.second
        engine.rollEncounter(force = 95)
        val traveler = engine.map.entities.firstOrNull { it.traveler } ?: return
        engine.camera.x = traveler.x - 1.0f
        engine.camera.y = traveler.y
        engine.interact()

        val saved = engine.toSaveSlot()
        assertTrue("word rides the save", saved.rumors.isNotBlank())
        val again = GameEngine(world, saved, null)
        assertTrue(again.rumors.any { it.siteId >= 0 })
        assertTrue(
            "the pin stands on the sheet after waking",
            again.bearings().any { it.source == "rumor" }
        )
    }
}
