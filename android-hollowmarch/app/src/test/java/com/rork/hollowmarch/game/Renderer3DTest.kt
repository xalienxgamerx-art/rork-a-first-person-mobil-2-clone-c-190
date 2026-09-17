package com.rork.hollowmarch.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * The polygon renderer must be exactly itself: the same seed, the same pixels,
 * forever. These frames are tiny halls with a known wall, sampled at known
 * depths, and the expected colors are computed from the same shared shading
 * math the renderer itself uses.
 */
class Renderer3DTest {

    private val w = 96
    private val h = 120
    private val scale = vScale(w)
    private val horizon = h / 2

    /** A closed hall, one dais of stone across its middle, grass underfoot. */
    private fun hallMap(): GameMap {
        val map = GameMap(16, 16, outdoor = false, title = "test hall")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        for (x in 4..12) map.walls[6 * 16 + x] = Textures.WALL_STONE
        return map
    }

    /** Standing south of the dais, facing north, half an eye above the floor. */
    private fun scene(map: GameMap): RenderScene = RenderScene(
        map = map,
        camera = Camera(8.5078125f, 10.5f, -(PI.toFloat() / 2f)),
        torch = 1f, outdoorLight = 1f, townBearing = 0f, townDistance = 0f,
        hurtFlash = 0f, strikeArc = 0f, swingPhase = 0f
    )

    private val fogTable = buildFogTable(outdoor = false, fogBoost = 0f)
    private val indoorFog = 0x0A0908

    @Test
    fun wallFaceRendersItsGoldenPixel() {
        val r = Renderer3D(w, h)
        r.render(scene(hallMap()))
        val dirY = sin(-(PI.toFloat() / 2f))
        val v = -3.5f * dirY                                  // depth of the dais' south face
        val tu = (8.5078125f * 64f).toInt() and Textures.MASK  // half a texel past the seam
        val wz = 0.5f - 12f * v / scale                        // world height twelve rows down
        val tv = ((1f - wz) * 64f).toInt() and Textures.MASK
        val bright = maxOf(torchBrightness(v, 1f), 0f) * 0.72f // y-facing stone takes the side shade
        val expected = fogged(
            ditherColor(Textures.sampleWall(Textures.WALL_STONE, tu, tv), bright, 48, 72),
            indoorFog, fogAt(fogTable, v)
        )
        assertEquals("wall face pixel", expected, r.pixels[72 * w + 48])
    }

    @Test
    fun ceilingAndFloorRenderTheirGoldenPixels() {
        val r = Renderer3D(w, h)
        r.render(scene(hallMap()))
        val tu = (8.5078125f * 64f).toInt() and Textures.MASK
        val v = 0.5f * scale / 30f                             // thirty rows off the horizon
        val tv = ((10.5f - v) * 64f).toInt() and Textures.MASK
        val ceiling = fogged(
            ditherColor(
                Textures.sampleFloor(Textures.FLOOR_CEILING, tu, tv),
                torchBrightness(v, 1f) * 0.75f, 48, 30
            ),
            indoorFog, fogAt(fogTable, v)
        )
        assertEquals("ceiling pixel", ceiling, r.pixels[30 * w + 48])
        val floor = fogged(
            ditherColor(
                Textures.sampleFloor(Textures.FLOOR_GRASS, tu, tv),
                torchBrightness(v, 1f), 48, 90
            ),
            indoorFog, fogAt(fogTable, v)
        )
        assertEquals("floor pixel", floor, r.pixels[90 * w + 48])
    }

    @Test
    fun aSoulBehindTheWallIsHidden() {
        val map = hallMap()
        map.entities += Entity(8.5078125f, 4.5f, Sprites.PILGRIM, EntityKind.ENEMY, 1f)
        val r = Renderer3D(w, h)
        r.render(scene(map))
        val withSoul = r.pixels.copyOf()
        map.entities.clear()
        r.render(scene(map))
        assertTrue("the wall must hide what stands behind it", withSoul.contentEquals(r.pixels))
    }

    @Test
    fun aSoulBeforeTheWallIsSeen() {
        val map = hallMap()
        val r = Renderer3D(w, h)
        r.render(scene(map))
        val empty = r.pixels.copyOf()
        map.entities += Entity(8.5078125f, 9.0f, Sprites.PILGRIM, EntityKind.ENEMY, 1f)
        r.render(scene(map))
        assertFalse("a soul in the open must be drawn", empty.contentEquals(r.pixels))
    }

    @Test
    fun sameSceneSamePixels() {
        val map = hallMap()
        map.entities += Entity(8.5078125f, 9.0f, Sprites.PILGRIM, EntityKind.ENEMY, 1f)
        val r = Renderer3D(w, h)
        r.render(scene(map))
        val first = r.pixels.copyOf()
        r.render(scene(map))
        assertTrue("rendering is deterministic", first.contentEquals(r.pixels))
    }
}
