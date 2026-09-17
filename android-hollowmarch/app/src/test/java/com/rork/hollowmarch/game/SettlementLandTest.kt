package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Living land on the settlement maps themselves: the yard rolls as the province
 * does, while every wall, street and building pad keeps level for the masonry.
 * Same seed, same land, same pixels, forever.
 */
class SettlementLandTest {

    private val w = 96
    private val h = 120

    private val world = WorldGenerator.generate(424242L)
    private val stead = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }
        .first { baseStageOf(it.kind) == SettlementStage.VILLAGE }

    private fun build(folk: Int = 500): GameMap =
        SiteGen.map(world, stead, 0, null, null, 1, folk = folk)

    private fun scene(map: GameMap): RenderScene = RenderScene(
        map = map,
        camera = Camera(map.width * 0.5f + 0.5078125f, map.height - 6.5f, -(PI.toFloat() / 2f)),
        torch = 1f, outdoorLight = 1f, townBearing = 0f, townDistance = 0f,
        hurtFlash = 0f, strikeArc = 0f, swingPhase = 0f
    )

    @Test
    fun `the settlement's ground rolls and its pads keep level`() {
        val map = build()
        val hts = map.heights
        assertTrue("the town stands on living land", hts != null)
        val vw = map.width + 1
        val pads = setOf(Textures.FLOOR_ROAD, Textures.FLOOR_FLAG, Textures.FLOOR_MUD)
        var padDelta = 0f
        var wildDelta = 0f
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val tex = map.floorTex[y * map.width + x]
                val i = y * vw + x
                if (tex in pads) {
                    padDelta = max(padDelta, abs(hts!![i + 1] - hts[i]))
                    padDelta = max(padDelta, abs(hts[i + vw] - hts[i]))
                } else if (tex == Textures.FLOOR_GRASS) {
                    wildDelta = max(wildDelta, abs(hts!![i + 1] - hts[i]))
                    wildDelta = max(wildDelta, abs(hts[i + vw] - hts[i]))
                    lo = min(lo, hts[i])
                    hi = max(hi, hts[i])
                }
            }
        }
        assertTrue("the open yard rolls: $hi - $lo", hi - lo > 0.08f)
        assertTrue(
            "the streets lie gentler than the yard: $padDelta vs $wildDelta",
            padDelta < wildDelta
        )
        assertTrue("the pads lie near level: $padDelta", padDelta < 0.05f)
    }

    @Test
    fun `the same folk build on the same land twice`() {
        val a = build()
        val b = build()
        assertTrue("the same walls", a.walls.contentEquals(b.walls))
        assertTrue("the same floors", a.floorTex.contentEquals(b.floorTex))
        assertTrue("the same land", a.heights!!.contentEquals(b.heights))
    }

    @Test
    fun `the settlement's frame is exactly itself`() {
        val a = build()
        val b = build()
        val ra = Renderer3D(w, h)
        ra.render(scene(a))
        val rb = Renderer3D(w, h)
        rb.render(scene(b))
        assertTrue("same seed, same pixels", ra.pixels.contentEquals(rb.pixels))
    }
}
