package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The living places, built to their stage, and the province that sizes to their folk. */
class SettlementGenTest {

    private val world = WorldGenerator.generate(424242L)

    private val settlements = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }

    /** A village-rank place (village or holdfast): open ground, no walls of its own. */
    private val openStead = settlements.first {
        baseStageOf(it.kind) == SettlementStage.VILLAGE
    }

    private fun build(site: com.rork.hollowmarch.world.Site, folk: Int = site.population): GameMap =
        SiteGen.map(world, site, 0, null, null, 1, folk = folk)

    private fun countWalls(map: GameMap, tex: Int, lx: Int, ly: Int, reach: Int): Int {
        var n = 0
        for (dy in -reach..reach) {
            for (dx in -reach..reach) {
                val x = lx + dx
                val y = ly + dy
                if (x !in 1 until map.width - 1 || y !in 1 until map.height - 1) continue
                if (map.walls[y * map.width + x] == tex) n++
            }
        }
        return n
    }

    // ---------------------------------------------------------- surfaces by stage

    @Test
    fun eachStageBuildsItsOwnKindOfPlace() {
        val site = openStead
        val village = build(site, folk = 100)
        val town = build(site, folk = 500)
        val city = build(site, folk = 3000)
        val capital = build(site, folk = 8000)

        // the ladder spreads: a village's ground, a capital's sprawl
        assertEquals(SettlementStage.VILLAGE.interiorSpan, village.width)
        assertEquals(SettlementStage.TOWN.interiorSpan, town.width)
        assertEquals(SettlementStage.CITY.interiorSpan, city.width)
        assertEquals(SettlementStage.CAPITAL.interiorSpan, capital.width)

        // the village: timber houses, tilled earth, no stone, no ring
        assertTrue(village.walls.count { it == Textures.WALL_TIMBER } > 0)
        assertEquals(0, village.walls.count { it == Textures.WALL_STONE })
        assertEquals(0, village.floorTex.count { it == Textures.FLOOR_FLAG })
        assertTrue("the village keeps a well", village.entities.any { it.name == "the well" })

        // the town: a palisade, a market square of flagstone
        val townTimber = town.walls.count { it == Textures.WALL_TIMBER }
        assertTrue("a town raises a palisade: $townTimber", townTimber > 100)
        assertEquals(0, town.walls.count { it == Textures.WALL_STONE })
        val townFlag = town.floorTex.count { it == Textures.FLOOR_FLAG }
        assertTrue("a town keeps a market square: $townFlag", townFlag > 30)
        assertTrue(town.entities.any { it.container && it.name == "market stall" })

        // the city: stone walls and a keep
        val cityStone = city.walls.count { it == Textures.WALL_STONE }
        assertTrue("a city raises stone: $cityStone", cityStone > 100)
        assertTrue(city.buildings.any { it.kind == "keep" })

        // the capital: a citadel, the grand temple, and more stone than the city
        val capitalStone = capital.walls.count { it == Textures.WALL_STONE }
        assertTrue("a capital raises greater stone: $capitalStone", capitalStone > cityStone)
        assertTrue(capital.buildings.any { it.kind == "citadel" })
        assertTrue(capital.buildings.any { it.name.contains("temple") })
        val capitalFlag = capital.floorTex.count { it == Textures.FLOOR_FLAG }
        assertTrue("a capital keeps grand flagstone: $capitalFlag", capitalFlag > townFlag)

        // every stage's place is entered by open ground, and its doors stand open
        listOf(village, town, city, capital).forEach { map ->
            assertTrue(map.outdoor)
            assertFalse("you wake on open ground", map.isWall(map.spawnX, map.spawnY))
            map.portals.forEach { portal ->
                assertFalse("the ${portal.label} stands open", map.isWall(portal.x, portal.y))
            }
            map.buildings.forEach { b ->
                assertEquals(
                    "the door of ${b.name} stands open",
                    0,
                    map.walls[b.doorY * map.width + b.doorX]
                )
            }
        }
    }

    @Test
    fun everyBuildingKeepsItsFootprint() {
        val site = openStead
        listOf(100, 500, 3000, 8000).forEach { folk ->
            val map = build(site, folk = folk)
            assertTrue("a living place keeps buildings", map.buildings.isNotEmpty())
            map.buildings.forEach { b ->
                assertTrue(b.w >= 3 && b.h >= 3)
                for (y in b.y until b.y + b.h) {
                    for (x in b.x until b.x + b.w) {
                        val perimeter = x == b.x || x == b.x + b.w - 1 || y == b.y || y == b.y + b.h - 1
                        val isDoor = x == b.doorX && y == b.doorY
                        val wall = map.walls[y * map.width + x]
                        if (perimeter && !isDoor) {
                            assertFalse("the walls of ${b.name} hold", wall == 0)
                        } else {
                            assertEquals("the inside of ${b.name} stands open", 0, wall)
                        }
                    }
                }
            }
            // no two buildings stand on the same ground
            map.buildings.forEachIndexed { i, a ->
                map.buildings.forEachIndexed { j, c ->
                    if (i < j) {
                        assertFalse(
                            "${a.name} and ${c.name} crowd the same ground",
                            a.x < c.x + c.w && a.x + a.w > c.x && a.y < c.y + c.h && a.y + a.h > c.y
                        )
                    }
                }
            }
        }
    }

    @Test
    fun sameSeedBuildsTheSameTown() {
        val site = openStead
        listOf(100, 500, 8000).forEach { folk ->
            val a = build(site, folk = folk)
            val b = build(site, folk = folk)
            assertTrue(a.walls.contentEquals(b.walls))
            assertTrue(a.floorTex.contentEquals(b.floorTex))
            assertEquals(a.buildings, b.buildings)
            assertEquals(a.portals, b.portals)
        }
    }

    @Test
    fun theSurfaceSizesToTheLivingFolk() {
        val site = openStead
        val small = build(site, folk = 100)
        val big = build(site, folk = 8000)
        assertEquals(SettlementStage.VILLAGE.interiorSpan, small.width)
        assertEquals(SettlementStage.CAPITAL.interiorSpan, big.width)
        // the census speaks when no folk is given
        val census = build(site)
        assertEquals(stageOf(site, site.population).interiorSpan, census.width)
    }

    // ---------------------------------------------------------- the open province

    @Test
    fun theOpenProvinceSizesToTheFolk() {
        val map = OverlandGen.build(world)
        settlements.forEach { site ->
            val stage = stageOf(site, site.population)
            val lx = OverlandGen.landmarkX(site)
            val ly = OverlandGen.landmarkY(site)
            val reach = (stage.wallRadius + 2).toInt().coerceAtLeast(stage.yardRadius + 1)
            val timber = countWalls(map, Textures.WALL_TIMBER, lx, ly, reach)
            val stone = countWalls(map, Textures.WALL_STONE, lx, ly, reach)
            when (stage) {
                SettlementStage.VILLAGE -> {
                    assertTrue("a village keeps only its low huts, no palisade: $timber", timber < 20)
                    assertTrue("no stone ring is mistaken for it: $stone", stone < 20)
                }
                SettlementStage.TOWN ->
                    assertTrue("a town's palisade stands on the province: $timber", timber > 25)
                else ->
                    assertTrue("a ${stage.label} stands walled in stone: $stone", stone > 30)
            }
        }
        // one door to a place, always
        settlements.forEach { site ->
            assertEquals(
                "${site.name} keeps one gate",
                1,
                map.portals.count { it.targetSiteId == site.id }
            )
        }
    }

    @Test
    fun aGrownPlaceIsRedrawnInPlace() {
        val map = OverlandGen.build(world)
        val site = openStead
        val lx = OverlandGen.landmarkX(site)
        val ly = OverlandGen.landmarkY(site)
        assertTrue(
            "a village keeps no palisade before its rise: ${countWalls(map, Textures.WALL_TIMBER, lx, ly, 8)}",
            countWalls(map, Textures.WALL_TIMBER, lx, ly, 8) < 20
        )
        assertEquals(1, map.portals.count { it.targetSiteId == site.id })

        OverlandGen.redrawLandmark(map, world, site, 500)
        val timber = countWalls(map, Textures.WALL_TIMBER, lx, ly, 8)
        assertTrue("the palisade stands where the folk raised it: $timber", timber > 25)
        assertEquals("one door to a place, always", 1, map.portals.count { it.targetSiteId == site.id })
        map.portals.filter { it.targetSiteId == site.id }.forEach { portal ->
            assertFalse("the new gate stands open", map.isWall(portal.x, portal.y))
        }
        val entry = map.entrySpots.getValue(site.id)
        assertTrue(
            "you stand outside the new walls",
            entry.second - ly > SettlementStage.TOWN.wallRadius
        )
        // same folk, same walls: the redraw is the seed's, never luck's
        val walls = map.walls.copyOf()
        OverlandGen.redrawLandmark(map, world, site, 500)
        assertTrue("the redraw is deterministic", walls.contentEquals(map.walls))
    }

    @Test
    fun aYearsTurnRedrawsAGrownPlace() {
        val engine = GameEngine(world, null, null)
        // touch the province so a stage change redraws it in place
        engine.overland
        engine.minutes += 300f * 365f * 1440f
        engine.stepFolkYears()
        val rose = settlements.filter {
            engine.stageAt(it).ordinal > baseStageOf(it.kind).ordinal
        }
        assertTrue("some stead rose within three hundred years: ${rose.size}", rose.isNotEmpty())
        rose.forEach { site ->
            val stage = engine.stageAt(site)
            val lx = OverlandGen.landmarkX(site)
            val ly = OverlandGen.landmarkY(site)
            val reach = stage.wallRadius.toInt() + 2
            val timber = countWalls(engine.overland, Textures.WALL_TIMBER, lx, ly, reach)
            val stone = countWalls(engine.overland, Textures.WALL_STONE, lx, ly, reach)
            when (stage) {
                SettlementStage.TOWN -> assertTrue(timber > 25)
                SettlementStage.CITY, SettlementStage.CAPITAL -> assertTrue(stone > 30)
                else -> {}
            }
            assertTrue(
                "the chronicle carries the growth",
                engine.chronicle.any { it.kind == com.rork.hollowmarch.world.EventKind.GROWTH && it.text.contains(site.name) }
            )
        }
    }

    @Test
    fun warbandCampsKeepTheirOldYard() {
        val camp = world.sites.firstOrNull { it.kind == SiteKind.CAMP && !it.ruined } ?: return
        val map = OverlandGen.build(world)
        val lx = OverlandGen.landmarkX(camp)
        val ly = OverlandGen.landmarkY(camp)
        assertEquals("a camp stands unwalled", 0, countWalls(map, Textures.WALL_TIMBER, lx, ly, 8))
        assertTrue(map.portals.any { it.targetSiteId == camp.id && it.label == "the watchfire" })
    }
}
