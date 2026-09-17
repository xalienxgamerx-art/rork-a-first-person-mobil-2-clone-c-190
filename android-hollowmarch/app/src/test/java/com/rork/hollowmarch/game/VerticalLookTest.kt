package com.rork.hollowmarch.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Vertical look — the first trick no raycaster could do. Drag the eye upward and
 * the world must slide down the screen until the ground gives way to nothing but
 * sky; drag it down and the land climbs to the top. Same seed, same pixels, pitched.
 */
class VerticalLookTest {

    private val w = 96
    private val h = 120

    /** An open field under the whole sky: no walls, no roof, nothing in the way. */
    private fun fieldMap(): GameMap {
        val map = GameMap(16, 16, outdoor = true, title = "look field")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        return map
    }

    private fun scene(map: GameMap, pitch: Float): RenderScene {
        val cam = Camera(8.5078125f, 10.5f, -(PI.toFloat() / 2f))
        cam.pitch = pitch
        return RenderScene(
            map = map, camera = cam,
            torch = 1f, outdoorLight = 1f, townBearing = 0f, townDistance = 0f,
            hurtFlash = 0f, strikeArc = 0f, swingPhase = 0f
        )
    }

    /** A closed hall with a dais of stone across its middle, as the golden frames use. */
    private fun hallMap(): GameMap {
        val map = GameMap(16, 16, outdoor = false, title = "look hall")
        for (i in map.floorTex.indices) map.floorTex[i] = Textures.FLOOR_GRASS
        for (x in 4..12) map.walls[6 * 16 + x] = Textures.WALL_STONE
        return map
    }

    private fun diffs(a: IntArray, b: IntArray): Int {
        var n = 0
        for (i in a.indices) if (a[i] != b[i]) n++
        return n
    }

    @Test
    fun `looking up slides the world down`() {
        val r = Renderer3D(w, h)
        r.render(scene(fieldMap(), 0f))
        val level = r.pixels.copyOf()
        r.render(scene(fieldMap(), 0.9f))
        val up = r.pixels.copyOf()
        // The foot of the screen was ground; pitched up it is nothing but sky.
        val bottom = (h - 3) * w + 48
        assertTrue("the ground gives way to sky", up[bottom] != level[bottom])
        assertTrue("the whole view shears", diffs(level, up) > 400)
    }

    @Test
    fun `looking down raises the land`() {
        val r = Renderer3D(w, h)
        r.render(scene(fieldMap(), 0f))
        val level = r.pixels.copyOf()
        r.render(scene(fieldMap(), -0.9f))
        val down = r.pixels.copyOf()
        // The crown of the screen was sky; pitched down it is the ground's own fog.
        val top = 1 * w + 48
        assertTrue("the sky gives way to land", down[top] != level[top])
        assertTrue("the whole view shears", diffs(level, down) > 400)
    }

    @Test
    fun `the eye also lifts indoors`() {
        val r = Renderer3D(w, h)
        r.render(scene(hallMap(), 0f))
        val level = r.pixels.copyOf()
        r.render(scene(hallMap(), 0.8f))
        val up = r.pixels.copyOf()
        // Under a roof the pitched eye sees the roof where the floor used to be.
        val bottom = (h - 3) * w + 48
        assertTrue("the floor gives way to the vault above", up[bottom] != level[bottom])
        assertTrue("the whole hall shears", diffs(level, up) > 400)
    }

    @Test
    fun `the pitched frame is exactly itself`() {
        val r = Renderer3D(w, h)
        r.render(scene(fieldMap(), 0.6f))
        val first = r.pixels.copyOf()
        r.render(scene(fieldMap(), 0.6f))
        assertTrue("pitched rendering is deterministic", first.contentEquals(r.pixels))
    }

    @Test
    fun `a pitched eye still hides what walls hide`() {
        val map = hallMap()
        map.entities += Entity(8.5078125f, 4.5f, Sprites.PILGRIM, EntityKind.ENEMY, 1f)
        val r = Renderer3D(w, h)
        r.render(scene(map, 0f))
        val level = r.pixels.copyOf()
        map.entities.clear()
        r.render(scene(map, 0f))
        assertEquals(
            "no soul at eye level: none pitched either",
            0, diffs(level, r.pixels)
        )
    }
}
