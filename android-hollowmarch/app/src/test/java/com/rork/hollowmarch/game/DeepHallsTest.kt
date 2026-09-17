package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.tan

/**
 * The hostile depths become true 3D spaces: crawlways press one course low, the
 * halls rise two, the boss's hall three on its rows of pillars, and the indoor
 * ceiling follows the masonry overhead. Same seed, same depths, forever.
 */
class DeepHallsTest {

    private val w = 96
    private val h = 120

    private val world = WorldGenerator.generate(424242L)
    private val vault = world.sites.first { it.kind == SiteKind.VAULT && !it.ruined }

    private fun floor(n: Int): GameMap = SiteGen.map(world, vault, n, null, null, 1)

    @Test
    fun `the boss's hall looms three courses tall`() {
        val floors = SiteGen.floorCount(world, vault)
        val map = floor(floors)
        val hts = map.wallHeights
        assertTrue("the deep places keep their heights", hts != null)
        // a pillar: wall cell standing alone, open on all four sides
        val pillars = hts!!.withIndex().count { (i, hgt) ->
            hgt == 3f && map.walls[i] != 0 && run {
                val x = i % map.width
                val y = i / map.width
                (x == 0 || map.walls[i - 1] == 0) &&
                    (x == map.width - 1 || map.walls[i + 1] == 0) &&
                    (y == 0 || map.walls[i - map.width] == 0) &&
                    (y == map.height - 1 || map.walls[i + map.width] == 0)
            }
        }
        assertTrue("the boss's hall stands on pillars: $pillars", pillars > 0)
    }

    @Test
    fun `crawlways press low and the halls rise`() {
        val map = floor(1)
        val hts = map.wallHeights
        assertTrue("the first depth keeps its heights", hts != null)
        var low = 0
        var tall = 0
        for (i in map.walls.indices) {
            if (map.walls[i] == 0) continue
            when (hts!![i]) {
                1f -> low++
                2f -> tall++
            }
        }
        assertTrue("crawlway stone stands low: $low", low > 0)
        assertTrue("hall stone rises two courses: $tall", tall > 0)
        assertTrue("nothing looms past the boss's three", hts!!.all { it <= 3f })
    }

    @Test
    fun `every way on stands framed in taller stone`() {
        val map = floor(1)
        val ceilings = map.ceilingHeights!!
        assertTrue("the depth has its ways on", map.portals.isNotEmpty())
        val sides = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
        for (portal in map.portals) {
            val px = portal.x.toInt()
            val py = portal.y.toInt()
            assertEquals(
                "the alcove's roof lifts over the way: ${portal.label}",
                3f, ceilings[py * map.width + px], 1e-4f
            )
            for ((dx, dy) in sides) {
                val nx = px + dx
                val ny = py + dy
                if (nx < 0 || ny < 0 || nx >= map.width || ny >= map.height) continue
                val i = ny * map.width + nx
                if (map.walls[i] != 0) {
                    assertTrue(
                        "the stone beside the way stands tall: ${portal.label}",
                        map.wallHeights!![i] >= 3f
                    )
                }
            }
        }
    }

    @Test
    fun `every chamber still connects`() {
        val map = floor(1)
        // every way on is reachable from the spawn: the pillars never dam the ways
        val seen = BooleanArray(map.width * map.height)
        val queue = ArrayDeque<Pair<Int, Int>>()
        val sx = map.spawnX.toInt()
        val sy = map.spawnY.toInt()
        seen[sy * map.width + sx] = true
        queue += sx to sy
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            for ((dx, dy) in listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)) {
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= map.width || ny >= map.height) continue
                val i = ny * map.width + nx
                if (seen[i] || map.walls[i] != 0) continue
                seen[i] = true
                queue += nx to ny
            }
        }
        map.portals.forEach { portal ->
            assertTrue(
                "the way stands reached: ${portal.label}",
                seen[portal.y.toInt() * map.width + portal.x.toInt()]
            )
        }
    }

    @Test
    fun `the depths are the seed's, never luck's`() {
        val a = floor(1)
        val b = floor(1)
        assertTrue("the same walls", a.walls.contentEquals(b.walls))
        assertTrue("the same heights", a.wallHeights!!.contentEquals(b.wallHeights))
        assertTrue("the same roofs of stone", a.ceilingHeights!!.contentEquals(b.ceilingHeights))
        assertEquals("the same ways on", a.portals, b.portals)
    }

    @Test
    fun `the ceiling follows the masonry`() {
        // a stone hall: walls three courses, every inner cell beneath a high roof
        val map = GameMap(16, 16, outdoor = false, title = "hall")
        map.walls.fill(Textures.WALL_STONE)
        map.floorTex.fill(Textures.FLOOR_FLAG)
        for (y in 1 until 15) for (x in 1 until 15) map.walls[y * 16 + x] = 0
        map.wallHeights = FloatArray(16 * 16) { 3f }
        map.ceilingHeights = FloatArray(16 * 16) { 3f }
        val r = Renderer3D(w, h)
        val cam = Camera(8.5078125f, 8.5f, -(PI.toFloat() / 2f))
        cam.pitch = 0.3f
        val scene = RenderScene(
            map = map, camera = cam,
            torch = 1f, outdoorLight = 1f, townBearing = 0f, townDistance = 0f,
            hurtFlash = 0f, strikeArc = 0f, swingPhase = 0f
        )
        r.render(scene)
        val horizon = (60 + tan(0.3f) * vScale(w)).toInt().coerceIn(2, h - 2)
        val row = 30
        val v = 2.5f * vScale(w) / (horizon - row)
        val tvF = ((8.5f - v) * 64f).toInt() and Textures.MASK
        val tuF = (8.5078125f * 64f).toInt() and Textures.MASK
        val expected = fogged(
            ditherColor(
                Textures.sampleFloor(Textures.FLOOR_CEILING, tuF, tvF),
                torchBrightness(v, 1f) * 0.75f, 48, row
            ),
            0x0A0908, fogAt(buildFogTable(false, 0f), v)
        )
        assertEquals("the hall's roof rides high", expected, r.pixels[row * w + 48])
        // the same hall under the old flat roof: the plane hangs one course up
        map.ceilingHeights = null
        map.wallHeights = null
        r.render(scene)
        assertTrue("the flat roof hangs low", r.pixels[row * w + 48] != expected)
    }
}
