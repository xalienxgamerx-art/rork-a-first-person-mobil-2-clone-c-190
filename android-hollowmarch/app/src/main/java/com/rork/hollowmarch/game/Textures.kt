package com.rork.hollowmarch.game

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Every surface in the province is painted procedurally at startup into small
 * low-resolution tiles, so the render keeps its chunky 1996 texel grain.
 */
object Textures {
    const val SIZE = 64
    const val MASK = SIZE - 1

    const val WALL_STONE = 1
    const val WALL_MOSS = 2
    const val WALL_RUNE = 3
    const val WALL_GATE = 4
    const val WALL_RUIN = 5
    const val WALL_TIMBER = 6
    const val WALL_WATER = 7
    const val WALL_ROOF = 8

    const val FLOOR_FLAG = 0
    const val FLOOR_GRASS = 1
    const val FLOOR_ROAD = 2
    const val FLOOR_MUD = 3
    const val FLOOR_CEILING = 4
    const val FLOOR_HEATH = 5
    const val FLOOR_PINE = 6
    const val FLOOR_SCREE = 7
    const val FLOOR_FORD = 8

    /** wallTextures[id] — index 0 unused so tile ids map straight through. */
    lateinit var walls: Array<IntArray>
        private set
    lateinit var floors: Array<IntArray>
        private set

    private var built = false

    fun ensureBuilt() {
        if (built) return
        val rng = Random(20260830L)
        walls = Array(9) { id ->
            when (id) {
                WALL_STONE -> stone(rng, 0x6A5F4E, 0x2A2620)
                WALL_MOSS -> moss(rng)
                WALL_RUNE -> rune(rng)
                WALL_GATE -> gate(rng)
                WALL_RUIN -> stone(rng, 0x796E5A, 0x2E2A22, cracked = true)
                WALL_TIMBER -> timber(rng)
                WALL_WATER -> water(rng)
                WALL_ROOF -> shingles(rng)
                else -> stone(rng, 0x6A5F4E, 0x2A2620)
            }
        }
        floors = Array(9) { id ->
            when (id) {
                FLOOR_FLAG -> flagstone(rng)
                FLOOR_GRASS -> ground(rng, 0x3B4430, 0x2A331F)
                FLOOR_ROAD -> ground(rng, 0x5A4C38, 0x453A2A)
                FLOOR_MUD -> ground(rng, 0x30301F, 0x22261A)
                FLOOR_HEATH -> heath(rng)
                FLOOR_PINE -> ground(rng, 0x3A3D28, 0x242A1A)
                FLOOR_SCREE -> scree(rng)
                FLOOR_FORD -> ford(rng)
                else -> ground(rng, 0x1C1A16, 0x131210)
            }
        }
        built = true
    }

    private fun shade(color: Int, amount: Float): Int {
        val r = (((color shr 16) and 0xFF) * amount).toInt().coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) * amount).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * amount).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ar + (br - ar) * t).toInt().coerceIn(0, 255)
        val g = (ag + (bg - ag) * t).toInt().coerceIn(0, 255)
        val bl = (ab + (bb - ab) * t).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or bl
    }

    private fun stone(rng: Random, base: Int, mortar: Int, cracked: Boolean = false): IntArray {
        val px = IntArray(SIZE * SIZE)
        val brickH = 16
        val brickW = 32
        for (y in 0 until SIZE) {
            val row = y / brickH
            val offset = if (row % 2 == 0) 0 else brickW / 2
            for (x in 0 until SIZE) {
                val bx = (x + offset) % SIZE
                val inMortar = (y % brickH) < 2 || (bx % brickW) < 2
                val brickSeed = (row * 31 + (bx / brickW) * 17)
                val variance = ((brickSeed * 2654435761L) % 40L).toInt() / 200f - 0.1f
                var c = if (inMortar) mortar else shade(base, 0.85f + variance)
                val grain = rng.nextInt(24) - 12
                c = shade(c, 1f + grain / 180f)
                if (cracked && !inMortar && rng.nextInt(90) == 0) c = shade(c, 0.4f)
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    /** Rows of hand-split shingles, weathered warm brown, each course shadowed. */
    private fun shingles(rng: Random): IntArray {
        val px = IntArray(SIZE * SIZE)
        val courseH = 11
        val shingleW = 9
        for (y in 0 until SIZE) {
            val row = y / courseH
            val offset = if (row % 2 == 0) 0 else shingleW / 2
            for (x in 0 until SIZE) {
                val bx = (x + offset) % SIZE
                val inShadow = (y % courseH) >= courseH - 2 || (bx % shingleW) >= shingleW - 1
                val shingleSeed = (row * 37 + (bx / shingleW) * 19)
                val variance = ((shingleSeed * 2654435761L) % 36L).toInt() / 220f - 0.08f
                var c = if (inShadow) shade(0x3A2E22, 0.8f) else shade(0x6B4E33, 0.9f + variance)
                val grain = rng.nextInt(20) - 10
                c = shade(c, 1f + grain / 160f)
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    private fun moss(rng: Random): IntArray {
        val px = stone(rng, 0x5C5645, 0x24221C)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val n = valueNoise(x * 0.13f, y * 0.13f, 7)
                if (n > 0.55f) {
                    val t = ((n - 0.55f) / 0.45f).coerceIn(0f, 1f) * 0.8f
                    px[y * SIZE + x] = mix(px[y * SIZE + x], 0x33452F, t)
                }
                if (y > 40 && rng.nextInt(6) == 0) {
                    px[y * SIZE + x] = mix(px[y * SIZE + x], 0x22301F, 0.4f)
                }
            }
        }
        return px
    }

    private fun rune(rng: Random): IntArray {
        val px = stone(rng, 0x6E6250, 0x2A2620)
        val marks = listOf(
            intArrayOf(16, 12, 16, 50), intArrayOf(16, 12, 40, 12),
            intArrayOf(40, 12, 40, 32), intArrayOf(24, 30, 40, 30),
            intArrayOf(24, 30, 24, 50), intArrayOf(24, 50, 46, 50)
        )
        marks.forEach { m ->
            drawLine(px, m[0], m[1], m[2], m[3], 0xC8952F, 2)
        }
        for (i in px.indices) {
            if (rng.nextInt(30) == 0) px[i] = shade(px[i], 0.7f)
        }
        return px
    }

    private fun gate(rng: Random): IntArray {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val bar = (x % 10) < 5 && x in 6..57 && y in 4..60
                var c = if (bar) mix(0xC8952F, 0x6B4E18, ((x % 10) / 5f)) else 0x14120E
                if (!bar && rng.nextInt(12) == 0) c = shade(0x2A2620, 1f)
                if (y < 4 || y > 60) c = 0x3A3128
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    private fun timber(rng: Random): IntArray {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val plank = (x / 12) % 2 == 0
                val base = if (plank) 0x4A3A28 else 0x40321F
                var c = shade(base, 0.9f + valueNoise(x * 0.4f, y * 0.05f, 3) * 0.25f)
                if (x % 12 < 1) c = 0x1E1811
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    /** Grey-brown tick grass with pale dead patches: the moor's own carpet. */
    private fun heath(rng: Random): IntArray {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val n = valueNoise(x * 0.14f, y * 0.14f, 31)
                var c = mix(0x2C2E1E, 0x4A4430, n)
                if (valueNoise(x * 0.08f, y * 0.08f, 33) > 0.72f) c = mix(c, 0x5A5238, 0.6f)
                if (rng.nextInt(10) == 0) c = shade(c, 0.82f)
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    /** Loose grey stone and gravel underfoot: the hills and the peaks. */
    private fun scree(rng: Random): IntArray {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val n = valueNoise(x * 0.2f, y * 0.2f, 41)
                var c = mix(0x3A3630, 0x5C5850, n)
                if (rng.nextInt(7) == 0) c = shade(c, 1.25f)
                if (rng.nextInt(7) == 0) c = shade(c, 0.7f)
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    /** Shallow water over gravel: the bright, walkable crossing of a river. */
    private fun ford(rng: Random): IntArray {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val ripple = valueNoise(x * 0.3f, y * 0.45f, 47)
                var c = mix(0x37443E, 0x56685C, ripple)
                if (ripple > 0.66f) c = mix(c, 0x7C8C80, 0.5f)
                if (rng.nextInt(9) == 0) c = mix(c, 0x4A4232, 0.5f)
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    /** Deep water: dark, cold, and it will not let you pass. */
    private fun water(rng: Random): IntArray {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val ripple = valueNoise(x * 0.22f, y * 0.35f, 53)
                var c = mix(0x14201E, 0x243430, ripple)
                if (ripple > 0.78f) c = mix(c, 0x3A4E46, 0.5f)
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    private fun flagstone(rng: Random): IntArray {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val cell = (x / 21) + (y / 21) * 3
                val seam = (x % 21) < 2 || (y % 21) < 2
                val variance = ((cell * 2654435761L) % 30L).toInt() / 220f
                var c = if (seam) 0x1C1913 else shade(0x53483A, 0.85f + variance)
                c = shade(c, 1f + (rng.nextInt(20) - 10) / 200f)
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    private fun ground(rng: Random, base: Int, dark: Int): IntArray {
        val px = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val n = valueNoise(x * 0.18f, y * 0.18f, 3)
                var c = mix(dark, base, n)
                if (rng.nextInt(9) == 0) c = shade(c, 0.85f)
                px[y * SIZE + x] = c
            }
        }
        return px
    }

    private fun drawLine(px: IntArray, x0: Int, y0: Int, x1: Int, y1: Int, color: Int, thickness: Int) {
        val steps = maxOf(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
        for (i in 0..steps) {
            val x = x0 + (x1 - x0) * i / steps
            val y = y0 + (y1 - y0) * i / steps
            for (ty in 0 until thickness) {
                for (tx in 0 until thickness) {
                    val px2 = x + tx
                    val py2 = y + ty
                    if (px2 in 0 until SIZE && py2 in 0 until SIZE) {
                        px[py2 * SIZE + px2] = color
                    }
                }
            }
        }
    }

    /** Cheap deterministic value noise, used for moss, soil and cloth grain. */
    fun valueNoise(x: Float, y: Float, seed: Int): Float {
        val xi = x.toInt()
        val yi = y.toInt()
        val xf = x - xi
        val yf = y - yi
        val v00 = hash(xi, yi, seed)
        val v10 = hash(xi + 1, yi, seed)
        val v01 = hash(xi, yi + 1, seed)
        val v11 = hash(xi + 1, yi + 1, seed)
        val sx = xf * xf * (3 - 2 * xf)
        val sy = yf * yf * (3 - 2 * yf)
        val top = v00 + (v10 - v00) * sx
        val bottom = v01 + (v11 - v01) * sx
        return top + (bottom - top) * sy
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695040888963407L.toInt()
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0xFFFF) / 65535f
    }

    fun sampleWall(id: Int, tx: Int, ty: Int): Int {
        val tex = walls[min(id, walls.size - 1)]
        return tex[(ty and MASK) * SIZE + (tx and MASK)]
    }

    fun sampleFloor(id: Int, tx: Int, ty: Int): Int {
        val tex = floors[min(id, floors.size - 1)]
        return tex[(ty and MASK) * SIZE + (tx and MASK)]
    }
}
