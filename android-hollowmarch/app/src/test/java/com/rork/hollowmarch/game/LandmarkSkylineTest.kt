package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * The province's own skyline: landmarks of real masonry — stone rings two courses
 * high, gate towers three, keeps and halls over their neighbors, the village's low
 * huts — and the golden pixel that proves a tower looms from the south approach.
 * Same seed, same stones, same pixels, forever.
 */
class LandmarkSkylineTest {

    private val world = WorldGenerator.generate(424242L)

    private fun siteOf(stage: SettlementStage) = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }
        .first { baseStageOf(it.kind) == stage }

    /** A quiet grass field, big enough for a landmark drawn at its heart. */
    private fun field(): GameMap {
        val map = GameMap(48, 48, outdoor = true, title = "province")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        return map
    }

    private fun draw(stage: SettlementStage): GameMap {
        val map = field()
        OverlandGen.drawLandmark(map, siteOf(stage), stage, 24, 24)
        return map
    }

    private fun footprintCells(b: BuildingFootprint): List<Pair<Int, Int>> =
        (b.y until b.y + b.h).flatMap { y ->
            (b.x until b.x + b.w).map { x -> x to y }
        }.filter { (x, y) ->
            x == b.x || x == b.x + b.w - 1 || y == b.y || y == b.y + b.h - 1
        }

    @Test
    fun `the city's ring and towers stand tall on the province`() {
        val map = draw(SettlementStage.CITY)
        val hts = map.wallHeights
        assertTrue("the city raises its masonry", hts != null)
        val built = map.buildings
        fun inBuilding(x: Int, y: Int) = built.any { b ->
            x >= b.x && x < b.x + b.w && y >= b.y && y < b.y + b.h
        }
        var ring = 0
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val i = y * map.width + x
                when (map.walls[i]) {
                    Textures.WALL_STONE -> if (!inBuilding(x, y)) {
                        assertEquals("the stone ring stands two courses", 2f, hts!![i], 1e-4f)
                        ring++
                    }
                    Textures.WALL_GATE ->
                        assertEquals("the gate tower stands a keep's height", 3f, hts!![i], 1e-4f)
                }
            }
        }
        assertTrue("a stone ring stands around the city: $ring", ring > 30)
        val keep = map.buildings.first { it.kind == "keep" }
        for ((x, y) in footprintCells(keep)) {
            val i = y * map.width + x
            if (map.walls[i] != 0) {
                assertEquals("the keep stands three walls high", 3f, hts!![i], 1e-4f)
            }
        }
    }

    @Test
    fun `the town raises its hall and its gate towers`() {
        val map = draw(SettlementStage.TOWN)
        val hts = map.wallHeights
        assertTrue("the town's towers are recorded", hts != null)
        val hall = map.buildings.first { it.kind == "hall" }
        val hallCells = footprintCells(hall).toSet()
        for (i in map.walls.indices) {
            when (map.walls[i]) {
                Textures.WALL_TIMBER -> {
                    val x = i % map.width
                    val y = i / map.width
                    val expected = if ((x to y) in hallCells) 2f else 1f
                    assertEquals("masonry stands by its kind at $x,$y", expected, hts!![i], 1e-4f)
                }
                Textures.WALL_GATE ->
                    assertEquals("the gate tower stands tall", 3f, hts!![i], 1e-4f)
            }
        }
    }

    @Test
    fun `the village keeps its low roofline on the province`() {
        val map = draw(SettlementStage.VILLAGE)
        assertNull("nothing looms in a village", map.wallHeights)
        assertTrue(
            "a hut or two of honest timber stands: ${map.buildings}",
            map.buildings.count { it.kind == "house" } >= 1
        )
        assertEquals(0, map.walls.count { it == Textures.WALL_GATE })
    }

    @Test
    fun `the skyline is the seed's, never luck's`() {
        val first = draw(SettlementStage.CITY)
        val second = draw(SettlementStage.CITY)
        assertTrue("the same stones twice", first.walls.contentEquals(second.walls))
        assertTrue("the same trodden floors", first.floorTex.contentEquals(second.floorTex))
        assertTrue("the same heights", first.wallHeights!!.contentEquals(second.wallHeights))
        assertEquals("the same houses", first.buildings, second.buildings)
    }

    @Test
    fun `the gate tower looms from the south approach`() {
        val map = draw(SettlementStage.CITY)
        val w = 96
        val h = 120
        // the west tower flanks the gate at the ring's south; the eye stands in the road before it
        val gy = 24 + SettlementStage.CITY.wallRadius.roundToInt()
        val cam = Camera(22.5078125f, gy + 3.5f, -(PI.toFloat() / 2f))
        val r = Renderer3D(w, h)
        r.render(
            RenderScene(
                map = map, camera = cam,
                torch = 1f, outdoorLight = 1f, townBearing = 0f, townDistance = 0f,
                hurtFlash = 0f, strikeArc = 0f, swingPhase = 0f
            )
        )
        val v = 2.5f
        val wz = 0.5f + (60 - 20) * v / vScale(w)
        val tv = ((1f - wz) * 64f).toInt() and Textures.MASK
        val tu = (22.5078125f * 64f).toInt() and Textures.MASK
        val bright = maxOf(torchBrightness(v, 1f), 0f) * 0.72f
        val fog = duskFog(1f, WeatherState(WeatherKind.CLEAR, 0f, 0f, 0f))
        val expected = fogged(
            ditherColor(Textures.sampleWall(Textures.WALL_GATE, tu, tv), bright, 48, 20),
            fog, fogAt(buildFogTable(true, 0f), v)
        )
        assertEquals("the tower fills the old sky", expected, r.pixels[20 * w + 48])
    }
}
