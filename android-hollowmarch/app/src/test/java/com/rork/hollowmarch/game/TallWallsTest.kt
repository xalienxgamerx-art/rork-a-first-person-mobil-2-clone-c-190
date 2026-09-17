package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Tall buildings: keeps and citadels three walls high, temples two, gate towers
 * over the great doors, and the band of a tall wall rising above its shorter
 * neighbor — the battlement you cannot see over. Same seed, same stones, same
 * pixels, forever.
 */
class TallWallsTest {

    private val w = 96
    private val h = 120

    /** An open field under the sky, with given wall cells and their heights. */
    private fun field(vararg walls: Pair<Int, Float>): GameMap {
        val map = GameMap(16, 16, outdoor = true, title = "keep yard")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        if (walls.isNotEmpty()) map.wallHeights = FloatArray(16 * 16)
        for ((idx, height) in walls) {
            map.walls[idx] = Textures.WALL_STONE
            map.wallHeights!![idx] = height
        }
        return map
    }

    private fun scene(map: GameMap): RenderScene {
        val cam = Camera(8.5078125f, 10.5f, -(PI.toFloat() / 2f))
        return RenderScene(
            map = map, camera = cam,
            torch = 1f, outdoorLight = 1f, townBearing = 0f, townDistance = 0f,
            hurtFlash = 0f, strikeArc = 0f, swingPhase = 0f
        )
    }

    private val fogTable = buildFogTable(true, 0f)
    private val fog = duskFog(1f, WeatherState(WeatherKind.CLEAR, 0f, 0f, 0f))
    private val tu = (8.5078125f * 64f).toInt() and Textures.MASK

    @Test
    fun `the stone's height reads true`() {
        val map = field()
        assertEquals(0f, map.wallHeight(8, 6), 1e-6f)
        assertEquals(0f, map.wallHeight(-1, 6), 1e-6f)
        map.walls[6 * 16 + 8] = Textures.WALL_STONE
        assertEquals(1f, map.wallHeight(8, 6), 1e-6f)
        map.wallHeights = FloatArray(16 * 16)
        map.wallHeights!![6 * 16 + 8] = 3f
        assertEquals(3f, map.wallHeight(8, 6), 1e-6f)
    }

    @Test
    fun `a keep rises three walls high`() {
        val r = Renderer3D(w, h)
        r.render(scene(field(6 * 16 + 8 to 3f)))
        // Twenty rows above where a one-wall head used to end: solid stone now.
        val v = 3.5f
        val wz = 0.5f + (60 - 20) * v / vScale(w)
        val tv = ((1f - wz) * 64f).toInt() and Textures.MASK
        val bright = maxOf(torchBrightness(v, 1f), 0f) * 0.72f
        val expected = fogged(
            ditherColor(Textures.sampleWall(Textures.WALL_STONE, tu, tv), bright, 48, 20),
            fog, fogAt(fogTable, v)
        )
        assertEquals("the tall face fills the old sky", expected, r.pixels[20 * w + 48])
    }

    @Test
    fun `a tall wall shows its band over a shorter one`() {
        val r = Renderer3D(w, h)
        r.render(scene(field(8 * 16 + 8 to 1f, 7 * 16 + 8 to 3f)))
        // Above the short wall's head, the tall wall's own band, at the near depth.
        val v = 2.5f
        val wz = 0.5f + (60 - 20) * v / vScale(w)
        val tv = ((1f - wz) * 64f).toInt() and Textures.MASK
        val bright = maxOf(torchBrightness(v, 1f), 0f) * 0.72f
        val expected = fogged(
            ditherColor(Textures.sampleWall(Textures.WALL_STONE, tu, tv), bright, 48, 20),
            fog, fogAt(fogTable, v)
        )
        assertEquals("the band rides above its neighbor", expected, r.pixels[20 * w + 48])
    }

    @Test
    fun `one-wall heights draw exactly the flat world`() {
        val flat = field(8 * 16 + 8 to 1f, 7 * 16 + 8 to 3f)
        flat.wallHeights = null
        val r = Renderer3D(w, h)
        r.render(scene(flat))
        val nullFrame = r.pixels.copyOf()
        val ones = field(8 * 16 + 8 to 1f, 7 * 16 + 8 to 3f)
        ones.wallHeights = FloatArray(16 * 16) { 1f }
        r.render(scene(ones))
        assertTrue(
            "raising nothing changes nothing",
            nullFrame.contentEquals(r.pixels)
        )
    }

    @Test
    fun `the tall frame is exactly itself`() {
        val map = field(6 * 16 + 8 to 3f)
        val r = Renderer3D(w, h)
        r.render(scene(map))
        val first = r.pixels.copyOf()
        r.render(scene(map))
        assertTrue("tall rendering is deterministic", first.contentEquals(r.pixels))
    }

    // ------------------------------------------------------------ settlements

    private val world = WorldGenerator.generate(424242L)
    private val stead = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }
        .first { baseStageOf(it.kind) == SettlementStage.VILLAGE }

    private fun build(folk: Int): GameMap = SiteGen.map(world, stead, 0, null, null, 1, folk = folk)

    @Test
    fun `the village keeps a low roofline`() {
        val village = build(folk = 100)
        val hts = village.wallHeights
        // Whatever the years gave it — a tavern, a shrine-house — nothing looms:
        // no keep, no stone ring, no gate towers, nothing past two walls high.
        if (hts != null) {
            assertTrue(
                "no village stone reaches past two walls",
                hts.all { it <= 2f }
            )
        }
        assertEquals(0, village.walls.count { it == Textures.WALL_GATE })
    }

    @Test
    fun `the city's keep and ring stand tall`() {
        val city = build(folk = 3000)
        val hts = city.wallHeights
        assertTrue("the city raises stone", hts != null)
        val keep = city.buildings.first { it.kind == "keep" || it.kind == "citadel" }
        for (y in keep.y until keep.y + keep.h) {
            for (x in keep.x until keep.x + keep.w) {
                val perimeter = x == keep.x || x == keep.x + keep.w - 1 ||
                    y == keep.y || y == keep.y + keep.h - 1
                val idx = y * city.width + x
                if (perimeter && city.walls[idx] != 0) {
                    assertEquals("the keep stands three walls high", 3f, hts!![idx], 1e-4f)
                }
            }
        }
        for (i in city.walls.indices) {
            if (city.walls[i] == Textures.WALL_GATE) {
                assertEquals("the gate tower stands tall", 3f, hts!![i], 1e-4f)
            }
        }
        assertTrue(
            "the stone ring itself stands taller",
            hts!!.withIndex().count { it.value > 1f && city.walls[it.index] != 0 } > 40
        )
        // and the same folk raise the same stones twice over
        val again = build(folk = 3000)
        assertTrue("settlement heights are deterministic", hts.contentEquals(again.wallHeights))
    }
}
