package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteGenTest {

    private val world = WorldGenerator.generate(424242L)
    private val roster = ClassRoster(world)

    private fun siteOf(kind: SiteKind): Site? = world.sites.firstOrNull { it.kind == kind }

    private val dungeonSites = listOf(SiteKind.VAULT, SiteKind.BARROW, SiteKind.RUIN)
        .mapNotNull { siteOf(it) }

    private fun build(site: Site, floor: Int): GameMap =
        SiteGen.map(world, site, floor, roster, null, 1)

    private fun assertWaysStandOpen(map: GameMap) {
        map.portals.forEach { portal ->
            assertTrue("the ${portal.label} stands open", !map.isWall(portal.x, portal.y))
        }
        map.arrivalSpots.forEachIndexed { i, spot ->
            assertTrue("landing $i stands open", !map.isWall(spot.first, spot.second))
        }
    }

    @Test
    fun everyDoorOnTheSurfaceAnswersAWayOut() {
        dungeonSites.forEach { site ->
            val surface = build(site, 0)
            val entrances = surface.portals.filter { it.down }
            assertTrue("${site.name} keeps a door", entrances.isNotEmpty())
            assertTrue("${site.name} keeps at most two doors", entrances.size <= 2)
            assertTrue("every door leads down", entrances.all { it.targetFloor == 1 })
            // and one way back out onto the open province
            val openRoads = surface.portals.filter { !it.down }
            assertEquals("${site.name} keeps one way out", 1, openRoads.size)
            assertEquals(OverlandGen.OVERLAND_FLOOR, openRoads.first().targetFloor)
            assertWaysStandOpen(surface)

            val first = build(site, 1)
            assertEquals(
                "${site.name}: one way out per door",
                entrances.size,
                first.portals.count { !it.down && it.targetFloor == 0 }
            )
            entrances.forEachIndexed { i, _ ->
                val wayOut = first.portals.first { !it.down && it.arrivalIndex == i }
                assertEquals(0, wayOut.targetFloor)
            }
            val extra = if (SiteGen.floorCount(world, site) > 1) 1 else 0
            assertEquals(
                "${site.name}: every landing answers a door",
                entrances.size + extra,
                first.arrivalSpots.size
            )
            assertWaysStandOpen(first)
        }
    }

    @Test
    fun theDeepFloorsChainByStairs() {
        dungeonSites.forEach { site ->
            val floors = SiteGen.floorCount(world, site)
            assertTrue("${site.name} keeps at least one buried floor", floors >= 1)
            for (f in 1..floors) {
                val map = build(site, f)
                assertWaysStandOpen(map)
                // the first floor answers every door with a way out; deeper floors keep one stair
                val expectedUp = if (f == 1) SiteGen.entranceCount(world, site) else 1
                assertEquals("floor $f keeps its ways up", expectedUp, map.portals.count { !it.down })
                if (f < floors) {
                    assertEquals("floor $f keeps one way down", 1, map.portals.count { it.down })
                    assertTrue(map.portals.any { it.down && it.targetFloor == f + 1 })
                } else {
                    assertEquals("the deepest floor has no way down", 0, map.portals.count { it.down })
                }
                assertEquals(
                    "only the deepest floor of ${site.name} keeps its boss",
                    f == floors,
                    map.entities.any { it.boss }
                )
            }
        }
    }

    @Test
    fun everyChamberConnects() {
        dungeonSites.forEach { site ->
            for (f in 0..SiteGen.floorCount(world, site)) {
                val map = build(site, f)
                val seen = BooleanArray(map.width * map.height)
                val queue = ArrayDeque<Pair<Int, Int>>()
                fun push(x: Int, y: Int) {
                    if (x !in 0 until map.width || y !in 0 until map.height) return
                    val idx = y * map.width + x
                    if (seen[idx] || map.walls[idx] != 0) return
                    seen[idx] = true
                    queue += Pair(x, y)
                }
                push(map.spawnX.toInt(), map.spawnY.toInt())
                while (queue.isNotEmpty()) {
                    val (x, y) = queue.removeFirst()
                    push(x + 1, y)
                    push(x - 1, y)
                    push(x, y + 1)
                    push(x, y - 1)
                }
                var open = 0
                var reached = 0
                for (i in map.walls.indices) {
                    if (map.walls[i] == 0) {
                        open++
                        if (seen[i]) reached++
                    }
                }
                if (f == 0) {
                    // the open sky may keep scenery pockets, but nearly all is walked
                    assertTrue(
                        "${site.name}'s yard is walkable: $reached of $open",
                        reached >= open * 0.98f
                    )
                } else {
                    assertEquals(
                        "${site.name} floor $f: every chamber connects",
                        open,
                        reached
                    )
                }
                map.portals.forEach { portal ->
                    assertTrue(
                        "the ${portal.label} lies in reached ground",
                        seen[portal.y.toInt() * map.width + portal.x.toInt()]
                    )
                }
            }
        }
    }

    @Test
    fun eachDeadPlaceKeepsExactlyOneBoss() {
        dungeonSites.forEach { site ->
            val deepest = build(site, SiteGen.floorCount(world, site))
            val bosses = deepest.entities.filter { it.boss }
            assertEquals("${site.name} keeps one boss", 1, bosses.size)
            val boss = bosses.first()
            assertEquals(boss.name, SiteGen.bossFor(world, site).name)
            assertTrue("the boss stands at full strength", boss.hp == boss.maxHp)
            assertTrue("the boss is bigger than the common dead", boss.height >= 1.3f)
            val hoardName = when (site.kind) {
                SiteKind.BARROW -> "grave-goods"
                SiteKind.RUIN -> "buried cache"
                else -> "reliquary"
            }
            val hoards = deepest.entities.filter { it.container && it.name == hoardName }
            assertEquals("${site.name} keeps its hoard", 1, hoards.size)
            assertTrue(
                "the hoard is worth the descent",
                hoards.first().lootBrass > 0 && hoards.first().loot.isNotEmpty()
            )
        }
        listOf(SiteKind.CAMP, SiteKind.SHRINE).mapNotNull { siteOf(it) }.forEach { site ->
            val yard = build(site, 0)
            assertEquals("${site.name} keeps one boss", 1, yard.entities.count { it.boss })
            val hoardName = if (site.kind == SiteKind.CAMP) "war chest" else "offering hoard"
            assertTrue(yard.entities.any { it.container && it.name == hoardName })
        }
    }

    @Test
    fun loreBossesBindToTheirPlaces() {
        world.beasts.filter { it.alive }.forEach { beast ->
            val lair = world.site(beast.lairSiteId)
            assertEquals("the beast keeps its lair", beast.name, SiteGen.bossFor(world, lair).name)
        }
        val vault = world.site(world.vaultSiteId)
        world.artifacts.firstOrNull { it.keeperSiteId == vault.id }?.let { artifact ->
            assertEquals(
                "the vault's guardian answers for the age's artifact",
                "Warden of ${artifact.name}",
                SiteGen.bossFor(world, vault).name
            )
        }
        dungeonSites.forEach { site ->
            val hasLore = world.beasts.any { it.lairSiteId == site.id && it.alive } ||
                (site.kind == SiteKind.VAULT && world.artifacts.any { it.keeperSiteId == site.id })
            if (!hasLore) {
                val expected = when (site.kind) {
                    SiteKind.BARROW -> "Barrow-King"
                    SiteKind.RUIN -> "Ruin Warden"
                    else -> "Vault Warden"
                }
                assertEquals(expected, SiteGen.bossFor(world, site).name)
            }
        }
    }

    /** Walk in through a landmark on the open ground. */
    private fun enterSite(engine: GameEngine, siteId: Int) {
        val door = engine.map.portals.first { it.targetSiteId == siteId }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
    }

    @Test
    fun theWayDownAnswersTheWayUp() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val vault = world.site(world.vaultSiteId)
        enterSite(engine, vault.id)
        assertEquals(0, engine.depth)
        assertTrue(engine.outdoor)
        val floors = SiteGen.floorCount(world, vault)

        var guard = 0
        while (engine.depth < floors && guard++ < 8) {
            val down = engine.map.portals.first { it.down }
            engine.camera.x = down.x
            engine.camera.y = down.y
            engine.usePortal()
            assertTrue(engine.depth in 1..floors)
        }
        assertEquals(floors, engine.depth)
        assertFalse(engine.outdoor)

        guard = 0
        while (engine.depth > 0 && guard++ < 8) {
            val up = engine.map.portals.first { !it.down }
            engine.camera.x = up.x
            engine.camera.y = up.y
            engine.usePortal()
        }
        assertEquals(0, engine.depth)
        assertTrue(engine.outdoor)
    }

    @Test
    fun bossDefeatRidesTheSave() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val vault = world.site(world.vaultSiteId)
        enterSite(engine, vault.id)
        var guard = 0
        while (engine.depth < SiteGen.floorCount(world, vault) && guard++ < 8) {
            val down = engine.map.portals.first { it.down }
            engine.camera.x = down.x
            engine.camera.y = down.y
            engine.usePortal()
        }
        val boss = engine.map.entities.first { it.boss }
        // clear the hall of everything but the boss
        engine.map.entities
            .filter { it.kind == EntityKind.ENEMY && it !== boss }
            .forEach { it.x = 1.5f; it.y = 1.5f }
        engine.camera.x = boss.x - 1.3f
        engine.camera.y = boss.y
        engine.camera.angle = 0f
        boss.hp = 1

        // the blow may find air first
        var strikeGuard = 0
        while (boss.alive && strikeGuard++ < 40) {
            engine.strikeCooldown = 0f
            engine.fatigue = engine.maxFatigue
            engine.strike()
        }

        assertFalse("the boss falls", boss.alive)
        assertTrue(engine.deeds.any { it.startsWith("Slew ${boss.name}") })
        val bossId = boss.persistId
        assertTrue("the boss carries a stable id", bossId.isNotBlank())
        assertTrue("the world remembers it slain", engine.worldState.isDead(bossId))
        val saved = engine.toSaveSlot()
        assertTrue("the deed rides the save", saved.worldState.contains(bossId))
        val again = GameEngine(world, saved, null)
        assertFalse("a slain boss does not rise", again.map.entities.any { it.boss })
    }

    @Test
    fun sameSeedBuildsTheSamePlace() {
        val vault = world.site(world.vaultSiteId)
        for (f in 0..SiteGen.floorCount(world, vault)) {
            val a = build(vault, f)
            val b = build(vault, f)
            assertTrue(a.walls.contentEquals(b.walls))
            assertTrue(a.floorTex.contentEquals(b.floorTex))
            assertEquals(a.portals, b.portals)
            assertEquals(a.arrivalSpots, b.arrivalSpots)
            assertEquals(a.entities.map { it.name }, b.entities.map { it.name })
            assertEquals(a.entities.map { it.maxHp }, b.entities.map { it.maxHp })
        }
    }

    @Test
    fun theSkinFollowsThePeople() {
        assertEquals(SiteGen.skinFor(0), SiteGen.skinFor(4))
        assertEquals(SiteGen.skinFor(3), SiteGen.skinFor(-1))
        assertEquals(SiteGen.skinFor(1), SiteGen.skinFor(9))
        val vault = world.site(world.vaultSiteId)
        val skin = SiteGen.skinFor(SiteGen.cultureId(world, vault))
        val floor = build(vault, 1)
        assertEquals("the plain wall dresses the whole dark", skin.plainWall, floor.walls[0])
        assertEquals(skin.plainWall, floor.walls[floor.walls.size - 1])
    }

    @Test
    fun theLandmarkLeadsToTheDoorstep() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val ruin = world.sites.first { it.kind == SiteKind.RUIN }
        enterSite(engine, ruin.id)
        assertTrue(engine.outdoor)
        assertEquals(0, engine.depth)
        assertFalse("the landmark leaves the open ground", engine.onOverland)
        assertTrue("the ruin keeps its doors", engine.map.portals.any { it.down })
        assertEquals(ruin.name, engine.map.title)
        assertTrue("the visit is remembered", ruin.id in engine.visitedSites)
    }

    @Test
    fun theLivingKeepTheirOldSky() {
        val town = siteOf(SiteKind.TOWN)
        assertNotNull("the province keeps a town", town)
        val yard = build(town!!, 0)
        assertTrue(yard.outdoor)
        assertEquals(0, SiteGen.floorCount(world, town))
    }
}
