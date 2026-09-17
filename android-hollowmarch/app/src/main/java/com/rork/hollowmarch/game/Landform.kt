package com.rork.hollowmarch.game

import kotlin.math.abs

/**
 * The rolling land: a deterministic heightfield over an outdoor map's vertex
 * lattice, born from the world's seed — no runtime luck, the same country
 * forever. The open ground lifts into hills and hollows, while roads, fords,
 * rivers and every site's yard sit on flattened, fitted pads, so villages and
 * landmarks keep their footprint-honest layouts. Interiors never call this:
 * terrain is outdoor only, and a map without a heightfield draws flat.
 */
object Landform {

    /** How many cells the ground takes to forget a pad and find its hills again. */
    private const val SKIRT = 3

    /** The pad-softening blur's radius, run twice so the fitted pads lie gentle. */
    private const val BLUR = 5

    /**
     * Raise the map's open ground. ampAt gives, per cell, how far the land may
     * stand above or below its middle there, in wall-heights: the hills reach
     * higher than the downs, the marsh barely dares to roll. [padTexes] names the
     * floor stones that count as pads — trodden ground the land must keep level;
     * walls always pad. A settlement passes its mud and flagstone too, so the
     * masonry sits true while the yard rolls.
     */
    fun buildHeights(
        map: GameMap,
        seed: Long,
        ampAt: (Int, Int) -> Float,
        padTexes: Set<Int> = setOf(Textures.FLOOR_ROAD, Textures.FLOOR_FORD)
    ) {
        val w = map.width
        val h = map.height
        val vw = w + 1
        val s = seed.toInt() xor (seed ushr 32).toInt()

        // The raw land: three hands of value noise, broad hills over broken ground.
        val raw = FloatArray(vw * (h + 1))
        for (vy in 0..h) {
            for (vx in 0..w) {
                val x = vx.toFloat()
                val y = vy.toFloat()
                raw[vy * vw + vx] =
                    0.55f * Textures.valueNoise(x * 0.020f, y * 0.020f, s) +
                        0.30f * Textures.valueNoise(x * 0.055f, y * 0.055f, s + 101) +
                        0.15f * Textures.valueNoise(x * 0.130f, y * 0.130f, s + 202)
            }
        }

        // Pad mask: every cell where the ground must keep its manners.
        val masked = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                masked[idx] = map.walls[idx] != 0 || map.floorTex[idx] in padTexes
            }
        }
        map.portals.forEach { p -> markDisc(masked, w, h, p.x.toInt(), p.y.toInt(), 6) }
        map.entrySpots.values.forEach { spot ->
            markDisc(masked, w, h, spot.first.toInt(), spot.second.toInt(), 6)
        }

        // The land's noise at each cell's heart, then gentled for the pads.
        var cellNoise = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * vw + x
                cellNoise[y * w + x] = (raw[i] + raw[i + 1] + raw[i + vw] + raw[i + vw + 1]) * 0.25f
            }
        }
        repeat(2) { cellNoise = blur(cellNoise, w, h, BLUR) }

        // A flood from every pad: each cell learns how far it lies from flat ground.
        val dist = IntArray(w * h)
        dist.fill(Int.MAX_VALUE)
        val queue = ArrayDeque<Int>()
        for (i in masked.indices) {
            if (masked[i]) {
                dist[i] = 0
                queue += i
            }
        }
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val d = dist[i] + 1
            val x = i % w
            val y = i / w
            if (x > 0 && dist[i - 1] > d) {
                dist[i - 1] = d
                queue += i - 1
            }
            if (x < w - 1 && dist[i + 1] > d) {
                dist[i + 1] = d
                queue += i + 1
            }
            if (y > 0 && dist[i - w] > d) {
                dist[i - w] = d
                queue += i - w
            }
            if (y < h - 1 && dist[i + w] > d) {
                dist[i + w] = d
                queue += i + w
            }
        }

        // The lattice: pads held near the gentled land, hills taking over past the skirt.
        val heights = FloatArray(vw * (h + 1))
        for (vy in 0..h) {
            for (vx in 0..w) {
                var best = Int.MAX_VALUE
                var pad = 0f
                for (cy in vy - 1..vy) {
                    if (cy < 0 || cy >= h) continue
                    for (cx in vx - 1..vx) {
                        if (cx < 0 || cx >= w) continue
                        val i = cy * w + cx
                        if (dist[i] < best) {
                            best = dist[i]
                            pad = cellNoise[i]
                        }
                    }
                }
                val d = if (best == Int.MAX_VALUE) SKIRT + 1 else best
                val t = (d.toFloat() / SKIRT).coerceIn(0f, 1f)
                val blend = t * t * (3f - 2f * t)
                val n = pad + (raw[vy * vw + vx] - pad) * blend
                val amp = ampAt(minOf(vx, w - 1), minOf(vy, h - 1))
                heights[vy * vw + vx] = (n - 0.5f) * amp
            }
        }
        map.heights = heights
    }

    /** A flat run's test: a cell whose four corners stand as one. */
    internal fun flatCell(map: GameMap, x: Int, y: Int): Boolean {
        val hts = map.heights ?: return false
        val vw = map.width + 1
        val h00 = hts[y * vw + x]
        return abs(hts[y * vw + x + 1] - h00) < FLAT_EPS &&
            abs(hts[(y + 1) * vw + x] - h00) < FLAT_EPS &&
            abs(hts[(y + 1) * vw + x + 1] - h00) < FLAT_EPS
    }

    internal const val FLAT_EPS = 1e-4f

    private fun markDisc(masked: BooleanArray, w: Int, h: Int, cx: Int, cy: Int, r: Int) {
        for (dy in -r..r) {
            for (dx in -r..r) {
                if (dx * dx + dy * dy > r * r) continue
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until w && y in 0 until h) masked[y * w + x] = true
            }
        }
    }

    /** Two passes of a box blur, sliding-window, edges clamped: the land gentled. */
    private fun blur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val span = 2 * r + 1
        val mid = FloatArray(w * h)
        for (y in 0 until h) {
            var sum = 0f
            for (dx in -r..r) sum += src[y * w + dx.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                mid[y * w + x] = sum / span
                sum += src[y * w + (x + r + 1).coerceIn(0, w - 1)] -
                    src[y * w + (x - r).coerceIn(0, w - 1)]
            }
        }
        val out = FloatArray(w * h)
        for (x in 0 until w) {
            var sum = 0f
            for (dy in -r..r) sum += mid[dy.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / span
                sum += mid[(y + r + 1).coerceIn(0, h - 1) * w + x] -
                    mid[(y - r).coerceIn(0, h - 1) * w + x]
            }
        }
        return out
    }
}
