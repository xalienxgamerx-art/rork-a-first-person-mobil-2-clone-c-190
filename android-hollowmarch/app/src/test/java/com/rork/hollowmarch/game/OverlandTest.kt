package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Biome
import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlandTest {

    private val world = WorldGenerator.generate(424242L)

    private fun build(): GameMap = OverlandGen.build(world)

    @Test
    fun sameSeedBuildsTheSameCountry() {
        val a = build()
        val b = build()
        assertTrue(a.walls.contentEquals(b.walls))
        assertTrue(a.floorTex.contentEquals(b.floorTex))
        assertEquals(a.portals, b.portals)
        assertEquals(a.entrySpots, b.entrySpots)
        assertEquals(a.entities.size, b.entities.size)
        assertEquals(a.entities.map { it.x }, b.entities.map { it.x })
        assertEquals(a.entities.map { it.spriteId }, b.entities.map { it.spriteId })
    }

    @Test
    fun everyLandmarkIsReachableOnFootFromTheVault() {
        val map = build()
        val vaultEntry = map.entrySpots.getValue(world.vaultSiteId)
        val seen = flood(map, vaultEntry)
        map.entrySpots.forEach { (siteId, spot) ->
            assertTrue(
                "${world.site(siteId).name} is reachable on foot from the vault",
                seen[spot.second.toInt() * map.width + spot.first.toInt()]
            )
        }
        map.portals.forEach { portal ->
            assertTrue("the ${portal.label} stands open", !map.isWall(portal.x, portal.y))
        }
    }

    private fun flood(map: GameMap, start: Pair<Float, Float>): BooleanArray {
        val seen = BooleanArray(map.width * map.height)
        val queue = ArrayDeque<Pair<Int, Int>>()
        fun push(x: Int, y: Int) {
            if (x !in 0 until map.width || y !in 0 until map.height) return
            val idx = y * map.width + x
            if (seen[idx] || map.walls[idx] != 0) return
            seen[idx] = true
            queue += Pair(x, y)
        }
        push(start.first.toInt(), start.second.toInt())
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            push(x + 1, y)
            push(x - 1, y)
            push(x, y + 1)
            push(x, y - 1)
        }
        return seen
    }

    @Test
    fun theRiversKeepFords() {
        val map = build()
        val fords = map.floorTex.count { it == Textures.FLOOR_FORD }
        assertTrue(
            "the rivers keep fords to wade: $fords",
            fords >= world.terrain.rivers.size
        )
    }

    @Test
    fun theMarshIsSlowestAndCostliest() {
        val (marshSpeed, marshCost) = OverlandGen.pacing(Biome.MARSH, false)
        val (downsSpeed, downsCost) = OverlandGen.pacing(Biome.DOWNS, false)
        assertTrue("marsh mud slows the walker", marshSpeed < downsSpeed)
        assertTrue("marsh mud costs breath", marshCost > downsCost)
        val (fordSpeed, fordCost) = OverlandGen.pacing(Biome.DOWNS, true)
        assertTrue("a ford is slower still", fordSpeed < downsSpeed)
        assertTrue("a ford costs breath", fordCost > downsCost)
    }

    @Test
    fun theCompassKnowsEightWinds() {
        assertEquals("N", OverlandGen.windOf(0f, -1f))
        assertEquals("E", OverlandGen.windOf(1f, 0f))
        assertEquals("S", OverlandGen.windOf(0f, 1f))
        assertEquals("W", OverlandGen.windOf(-1f, 0f))
        assertEquals("NE", OverlandGen.windOf(1f, -1f))
        assertEquals("SE", OverlandGen.windOf(1f, 1f))
        assertEquals("SW", OverlandGen.windOf(-1f, 1f))
        assertEquals("NW", OverlandGen.windOf(-1f, -1f))
    }

    @Test
    fun expeditionsWakeAtTheBottomOfTheVault() {
        val engine = GameEngine(world, null, null)
        // the first waking is the deep waking: the lowest floor of the sealed vault
        assertFalse(engine.onOverland)
        assertFalse(engine.outdoor)
        assertEquals(SiteGen.floorCount(world, world.site(world.vaultSiteId)), engine.depth)
        assertTrue(engine.map.wallHeights != null)

        // the climb out ends on the open ground before the vault, facing its door
        engine.climbToOpenGround()
        assertTrue(engine.onOverland)
        assertTrue(engine.outdoor)
        assertEquals(0, engine.depth)
        assertEquals(engine.overland.title, engine.map.title)
        val entry = engine.overland.entrySpots.getValue(world.vaultSiteId)
        assertEquals(entry.first, engine.camera.x, 0.001f)
        assertEquals(entry.second, engine.camera.y, 0.001f)
        // facing the sealed door: due north
        assertEquals(-1.5708f, engine.camera.angle, 0.01f)
    }

    @Test
    fun aLandmarkLeadsIntoThePlaceAndBackOut() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val vault = world.site(world.vaultSiteId)
        val door = engine.overland.portals.first { it.targetSiteId == vault.id }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
        assertFalse("the landmark leaves the open ground", engine.onOverland)
        assertTrue(engine.outdoor)
        assertEquals(0, engine.depth)
        assertEquals(vault.name, engine.map.title)
        assertTrue("the visit is remembered", vault.id in engine.visitedSites)

        val out = engine.map.portals.first { it.targetFloor == OverlandGen.OVERLAND_FLOOR }
        engine.camera.x = out.x
        engine.camera.y = out.y
        engine.usePortal()
        assertTrue("walking out returns you to the open ground", engine.onOverland)
        assertEquals(engine.overland.title, engine.map.title)
        val entry = engine.overland.entrySpots.getValue(vault.id)
        assertEquals(entry.first, engine.camera.x, 0.001f)
        assertEquals(entry.second, engine.camera.y, 0.001f)
    }

    @Test
    fun theOpenRoadRidesTheSave() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val settlement = world.sites.first { it.isSettlement }
        val door = engine.overland.portals.first { it.targetSiteId == settlement.id }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
        val out = engine.map.portals.first { it.targetFloor == OverlandGen.OVERLAND_FLOOR }
        engine.camera.x = out.x
        engine.camera.y = out.y
        engine.usePortal()
        assertTrue(engine.onOverland)

        val saved = engine.toSaveSlot()
        assertTrue(saved.onOverland)
        val again = GameEngine(world, saved, null)
        assertTrue("the open road wakes you where you stood", again.onOverland)
        assertEquals(engine.camera.x, again.camera.x, 0.01f)
        assertEquals(engine.camera.y, again.camera.y, 0.01f)
        assertTrue(settlement.id in again.visitedSites)
    }

    @Test
    fun pinsNameRealPlaces() {
        val engine = GameEngine(world, null, null)
        engine.rumors.filter { it.siteId >= 0 }.forEach { rumor ->
            assertTrue("a pin names a real place", world.sites.any { it.id == rumor.siteId })
        }
        val bearings = engine.bearings()
        assertTrue("the sheet keeps something to find", bearings.isNotEmpty())
        bearings.forEach { bearing ->
            assertTrue(bearing.compass in setOf("N", "NE", "E", "SE", "S", "SW", "W", "NW"))
            assertTrue(bearing.leagues >= 0f)
            assertTrue(bearing.hours >= 0f)
        }
        // every living settlement is common knowledge; an unentered ruin is not
        val settlements = world.sites.filter { it.isSettlement && !it.ruined }.map { it.id }.toSet()
        assertTrue(
            "the roads are known",
            bearings.any { it.site.id in settlements && !it.visited && it.source == "the roads" }
        )
        val unvisitedRuin = world.sites.first { it.kind == SiteKind.RUIN }
        assertTrue(
            "an unentered hostile site is unconfirmed",
            bearings.none { it.site.id == unvisitedRuin.id && it.visited }
        )
    }

    @Test
    fun anOldSaveWakesWhereItStood() {
        val slot = SaveSlot(
            seed = world.seed,
            day = 41,
            minutes = 40 * 1440f + 8 * 60f,
            outdoor = false,
            depth = 1,
            x = 5f,
            y = 5f,
            angle = 0f,
            vitality = 50,
            fatigue = 30,
            magicka = 20,
            torch = 0.8f,
            kills = 0,
            brass = 10,
            siteId = world.vaultSiteId,
            siteName = "",
            deeds = emptyList()
        )
        val engine = GameEngine(world, slot, null)
        assertFalse("an old save wakes in its place, not on the road", engine.onOverland)
        assertEquals(world.site(world.vaultSiteId).name, engine.map.title)
    }
}
