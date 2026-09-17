package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Pitched roofs: two slopes from eave to ridge over every raised house, the
 * gable ends closed with wall stone, and no soul bleeding through the shingles.
 * The golden pixels hold: same seed, same roofs, same pixels, forever.
 */
class RoofTest {

    private val w = 96
    private val h = 120

    /** A timber house 3 wide and 2 deep, its ridge running east-west. */
    private fun house(): GameMap {
        val map = GameMap(16, 16, outdoor = true, title = "house")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        map.wallHeights = FloatArray(16 * 16) { 1f }
        for (y in 5..6) {
            for (x in 7..9) {
                val perimeter = y == 5 || y == 6 || x == 7 || x == 9
                if (perimeter) map.walls[y * 16 + x] = Textures.WALL_TIMBER
            }
        }
        map.roofs += Roof(7, 5, 10, 7, 1f, 1.5f, alongX = true, Textures.WALL_TIMBER, Textures.WALL_ROOF)
        return map
    }

    private fun scene(map: GameMap, cam: Camera): RenderScene = RenderScene(
        map = map, camera = cam,
        torch = 1f, outdoorLight = 1f, townBearing = 0f, townDistance = 0f,
        hurtFlash = 0f, strikeArc = 0f, swingPhase = 0f
    )

    private val fogTable = buildFogTable(true, 0f)
    private val fog = duskFog(1f, WeatherState(WeatherKind.CLEAR, 0f, 0f, 0f))
    private val tu = (8.5078125f * 64f).toInt() and Textures.MASK

    /** The south slope's pixel: where the ray crosses the plane from eave to ridge. */
    private fun slopePixel(row: Int): Int {
        val s = (60f - row) / vScale(w)
        val z = (0.5f + 1.5f * s) / (1f - 2f * s)
        val t = 1.5f + 2f * z
        val tv = ((1f - z) * 64f).toInt() and Textures.MASK
        val bright = maxOf(torchBrightness(t, 1f), 0f) * 0.72f
        return fogged(
            ditherColor(Textures.sampleWall(Textures.WALL_ROOF, tu, tv), bright, 48, row),
            fog, fogAt(fogTable, t)
        )
    }

    @Test
    fun `the south slope rides from eave to ridge`() {
        val map = house()
        val r = Renderer3D(w, h)
        r.render(scene(map, Camera(8.5078125f, 10.5f, -(PI.toFloat() / 2f))))
        assertEquals(
            "the eave line above the wall top",
            slopePixel(44), r.pixels[44 * w + 48]
        )
        assertEquals(
            "the ridge line where the slopes meet",
            slopePixel(39), r.pixels[39 * w + 48]
        )
    }

    @Test
    fun `the gable ends close in wall stone`() {
        val map = house()
        val r = Renderer3D(w, h)
        r.render(scene(map, Camera(13.5f, 6.3f, PI.toFloat())))
        // the east gable: the eye 3.5 paces from its plane, the ray inside the triangle
        val z = 0.5f + 23f * 3.5f / vScale(w)
        val tv = ((1f - z) * 64f).toInt() and Textures.MASK
        val tuG = (6.3f * 64f).toInt() and Textures.MASK
        val bright = maxOf(torchBrightness(3.5f, 1f), 0f)
        val expected = fogged(
            ditherColor(Textures.sampleWall(Textures.WALL_TIMBER, tuG, tv), bright, 48, 37),
            fog, fogAt(fogTable, 3.5f)
        )
        assertEquals("the gable closes the roof's end", expected, r.pixels[37 * w + 48])
    }

    @Test
    fun `no soul bleeds through the shingles`() {
        val map = house()
        map.entities += Entity(8.5078125f, 4.0f, Sprites.PILGRIM, EntityKind.ENEMY, 1.8f)
        val r = Renderer3D(w, h)
        val cam = Camera(8.5078125f, 10.5f, -(PI.toFloat() / 2f))
        r.render(scene(map, cam))
        val roofPixel = slopePixel(44)
        assertEquals("the roof stands before the soul", roofPixel, r.pixels[44 * w + 48])
        // without its roof, the tall soul's head shows above the wall
        map.roofs.clear()
        r.render(scene(map, cam))
        assertTrue("bare walls reveal the soul", r.pixels[44 * w + 48] != roofPixel)
    }

    // ------------------------------------------------------------ registration

    private val world = WorldGenerator.generate(424242L)
    private val stead = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }
        .first { baseStageOf(it.kind) == SettlementStage.VILLAGE }

    private fun build(folk: Int): GameMap = SiteGen.map(world, stead, 0, null, null, 1, folk = folk)

    @Test
    fun `every raised house wears its roof`() {
        val town = build(500)
        assertTrue("the town has houses", town.buildings.isNotEmpty())
        assertEquals("every house wears a roof", town.buildings.size, town.roofs.size)
        for (roof in town.roofs) {
            assertEquals(
                "the ridge stands half a wall above the eave",
                roof.eave + 0.5f, roof.ridge, 1e-4f
            )
            assertTrue(
                "the roof spans its own footprint",
                town.buildings.any {
                    it.x == roof.x0 && it.y == roof.y0 &&
                        it.x + it.w == roof.x1 && it.y + it.h == roof.y1
                }
            )
        }
        // the province's landmarks too
        val map = GameMap(48, 48, outdoor = true, title = "province")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        val city = world.sites
            .filter { it.isSettlement && !it.ruined && it.population > 0 }
            .first { baseStageOf(it.kind) == SettlementStage.CITY }
        OverlandGen.drawLandmark(map, city, SettlementStage.CITY, 24, 24)
        assertTrue("the landmark has houses", map.buildings.isNotEmpty())
        assertEquals("every landmark house wears a roof", map.buildings.size, map.roofs.size)
    }

    @Test
    fun `the roofs are the seed's, never luck's`() {
        val a = build(500)
        val b = build(500)
        assertEquals("the same roofs twice", a.roofs, b.roofs)
    }
}
