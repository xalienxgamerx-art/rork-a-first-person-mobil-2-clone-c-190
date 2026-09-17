package com.rork.hollowmarch.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The rolling land must keep the family's oldest promise: the same seed, the
 * same country, forever — and now the same hills under the same pixels.
 */
class TerrainTest {

    private val w = 96
    private val h = 120

    private fun downsMap(): GameMap {
        val map = GameMap(32, 32, outdoor = true, title = "downs")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        return map
    }

    private fun fieldMap(): GameMap {
        val map = GameMap(16, 16, outdoor = true, title = "field")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        return map
    }

    /** Standing in open grass, facing north, half an eye above the ground. */
    private fun scene(map: GameMap): RenderScene = RenderScene(
        map = map,
        camera = Camera(8.5078125f, 10.5f, -(PI.toFloat() / 2f)),
        torch = 1f, outdoorLight = 1f, townBearing = 0f, townDistance = 0f,
        hurtFlash = 0f, strikeArc = 0f, swingPhase = 0f
    )

    /** A plateau half again the height of a wall, filling the north of the field. */
    private fun plateau(): FloatArray {
        val hts = FloatArray(17 * 17)
        for (vy in 0..8) for (vx in 0..16) hts[vy * 17 + vx] = 0.9f
        return hts
    }

    private fun firstDiffRow(a: IntArray, b: IntArray, col: Int): Int {
        for (y in 0 until h) if (a[y * w + col] != b[y * w + col]) return y
        return -1
    }

    @Test
    fun `the land is born the same from the same seed`() {
        val a = downsMap()
        Landform.buildHeights(a, 77L, { _, _ -> 0.8f })
        val b = downsMap()
        Landform.buildHeights(b, 77L, { _, _ -> 0.8f })
        assertTrue(a.heights!!.contentEquals(b.heights))
        val c = downsMap()
        Landform.buildHeights(c, 78L, { _, _ -> 0.8f })
        assertFalse("another seed raises another country", a.heights!!.contentEquals(c.heights))
    }

    @Test
    fun `heightAt reads the lattice true`() {
        val map = downsMap()
        val hts = FloatArray(33 * 33) { i -> (i % 7) * 0.1f }
        map.heights = hts
        assertEquals(hts[3 * 33 + 2], map.heightAt(2f, 3f), 1e-6f)
        // halfway between four corners, their average
        val mid = (hts[3 * 33 + 2] + hts[3 * 33 + 3] + hts[4 * 33 + 2] + hts[4 * 33 + 3]) * 0.25f
        assertEquals(mid, map.heightAt(2.5f, 3.5f), 1e-4f)
        // beyond the edge, the land holds its rim
        assertEquals(hts[3 * 33], map.heightAt(-5f, 3f), 1e-6f)
        // no field at all: the flat world reads zero
        map.heights = null
        assertEquals(0f, map.heightAt(2.5f, 3.5f), 1e-6f)
    }

    @Test
    fun `the road lies level while the land rolls`() {
        val map = downsMap()
        for (x in 0 until 32) map.floorTex[16 * 32 + x] = Textures.FLOOR_ROAD
        Landform.buildHeights(map, 9L, { _, _ -> 1.2f })
        val hts = map.heights!!
        val vw = 33
        var padDelta = 0f
        for (x in 0 until 32) {
            val d = abs(hts[16 * vw + x + 1] - hts[16 * vw + x])
            if (d > padDelta) padDelta = d
        }
        var wildDelta = 0f
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (y in 2..12) {
            for (x in 0..32) {
                val hgt = hts[y * vw + x]
                if (hgt < lo) lo = hgt
                if (hgt > hi) hi = hgt
                if (x < 32) {
                    val d = abs(hts[y * vw + x + 1] - hts[y * vw + x])
                    if (d > wildDelta) wildDelta = d
                }
            }
        }
        assertTrue("the wild land rolls", hi - lo > 0.15f)
        assertTrue("the road lies level upon it", padDelta < wildDelta)
    }

    @Test
    fun `a hill breaks the horizon`() {
        val hill = fieldMap()
        hill.heights = plateau()
        val flat = fieldMap()
        flat.heights = FloatArray(17 * 17)
        val r = Renderer3D(w, h)
        r.render(scene(hill))
        val hillFrame = r.pixels.copyOf()
        r.render(scene(flat))
        val flatFrame = r.pixels.copyOf()
        assertTrue(
            "the high ground stands against the sky",
            hillFrame[48 * w + 48] != flatFrame[48 * w + 48]
        )
        assertEquals(
            "the low ground underfoot is unchanged",
            flatFrame[110 * w + 48], hillFrame[110 * w + 48]
        )
    }

    @Test
    fun `a soul stands on the high ground`() {
        val hill = fieldMap()
        hill.heights = plateau()
        val flat = fieldMap()
        flat.heights = FloatArray(17 * 17)
        val r = Renderer3D(w, h)
        hill.entities += Entity(8.5078125f, 5.0f, Sprites.PILGRIM, EntityKind.ENEMY, 1f)
        r.render(scene(hill))
        val hillWith = r.pixels.copyOf()
        hill.entities.clear()
        r.render(scene(hill))
        val hillWithout = r.pixels.copyOf()
        flat.entities += Entity(8.5078125f, 5.0f, Sprites.PILGRIM, EntityKind.ENEMY, 1f)
        r.render(scene(flat))
        val flatWith = r.pixels.copyOf()
        flat.entities.clear()
        r.render(scene(flat))
        val flatWithout = r.pixels.copyOf()
        val hillRow = firstDiffRow(hillWith, hillWithout, 48)
        val flatRow = firstDiffRow(flatWith, flatWithout, 48)
        assertTrue("the soul is drawn on the hill", hillRow >= 0)
        assertTrue("the soul is drawn on the flat", flatRow >= 0)
        assertTrue("the high ground lifts the soul", hillRow < flatRow)
    }

    @Test
    fun `the flat world still renders its old ground`() {
        val r = Renderer3D(w, h)
        r.render(scene(fieldMap()))
        val scale = vScale(w)
        val v = 0.5f * scale / 30f                 // thirty rows off the horizon
        val tu = (8.5078125f * 64f).toInt() and Textures.MASK
        val tv = ((10.5f - v) * 64f).toInt() and Textures.MASK
        val bright = maxOf(torchBrightness(v, 1f), 1f * 0.85f)
        val expected = fogged(
            ditherColor(Textures.sampleFloor(Textures.FLOOR_GRASS, tu, tv), bright, 48, 90),
            duskFog(1f, WeatherState(WeatherKind.CLEAR, 0f, 0f, 0f)),
            fogAt(buildFogTable(true, 0f), v)
        )
        assertEquals("outdoor ground pixel", expected, r.pixels[90 * w + 48])
    }
}
