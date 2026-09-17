package com.rork.hollowmarch.game

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Screen-space atmosphere and shading shared by the whole game — sky, weather,
 * hands and post effects are drawn the same way whatever geometry stands under them,
 * so the look never changes with the engine underneath.
 */

/** The camera plane's half-width: the family's horizontal field of view. */
internal const val FOV_PLANE = 0.72f

/** Vertical projection distance: a wall of height one spans this many pixels at one unit. */
internal fun vScale(w: Int): Float = w * 1.02f

// ------------------------------------------------------------------ shading

private val bayer = intArrayOf(
    0, 8, 2, 10,
    12, 4, 14, 6,
    3, 11, 1, 9,
    15, 7, 13, 5
)

private const val LEVELS = 7f
private const val TORCH_FALLOFF = 0.10f

/** Quantize brightness through a Bayer matrix so shadows band and dither like 1996. */
internal fun ditherColor(color: Int, brightness: Float, x: Int, y: Int): Int {
    val threshold = bayer[(y and 3) * 4 + (x and 3)] / 16f
    val q = (floor(brightness.coerceIn(0f, 1.2f) * LEVELS + threshold) / LEVELS).coerceIn(0f, 1.2f)
    val r = (((color shr 16) and 0xFF) * q).toInt().coerceIn(0, 255)
    val g = (((color shr 8) and 0xFF) * q).toInt().coerceIn(0, 255)
    val b = ((color and 0xFF) * q).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

internal fun mixColor(a: Int, b: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    val ar = (a shr 16) and 0xFF
    val ag = (a shr 8) and 0xFF
    val ab = a and 0xFF
    val br = (b shr 16) and 0xFF
    val bg = (b shr 8) and 0xFF
    val bb = b and 0xFF
    val r = (ar + (br - ar) * f).toInt().coerceIn(0, 255)
    val g = (ag + (bg - ag) * f).toInt().coerceIn(0, 255)
    val bl = (ab + (bb - ab) * f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

internal fun fogged(color: Int, fogColor: Int, fog: Float): Int =
    if (fog <= 0.01f) color else mixColor(color, fogColor, fog)

internal fun fogFactor(dist: Float, outdoor: Boolean, fogBoost: Float = 0f): Float {
    val base = if (outdoor) 0.055f else 0.075f
    val density = base + if (outdoor) fogBoost * 0.12f else 0f
    return (1f - exp(-dist * density)).coerceIn(0f, 1f)
}

internal fun torchBrightness(dist: Float, torch: Float): Float {
    val strength = 0.25f + torch * 1.15f
    return (strength / (1f + dist * dist * TORCH_FALLOFF)).coerceIn(0f, 1.25f)
}

internal fun duskFog(light: Float, weather: WeatherState): Int = mixColor(
    mixColor(0x100C0A, 0x3A2A22, light),
    0x46494D,
    maxOf(weather.cloud * 0.6f, weather.fogBoost).coerceIn(0f, 1f)
)

/** The night fraction: how far the stars may climb out of the daylight. */
internal fun nightAmt(light: Float): Float {
    val d = (1f - light).coerceIn(0f, 1f)
    return d * d
}

/** Fog looked up by distance: a table built once per frame, read per pixel. */
internal fun buildFogTable(outdoor: Boolean, fogBoost: Float): FloatArray {
    val table = FloatArray(256)
    for (i in table.indices) table[i] = fogFactor(i * 0.25f, outdoor, fogBoost)
    return table
}

/** The frame's fog at a world distance, read from the table built for this frame. */
internal fun fogAt(table: FloatArray, dist: Float): Float =
    table[(dist * 4f + 0.5f).toInt().coerceIn(0, table.size - 1)]

internal fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return dx * dx + dy * dy
}

internal fun normalizeAngle(a: Float): Float {
    var v = a
    while (v > Math.PI) v -= (2 * Math.PI).toFloat()
    while (v < -Math.PI) v += (2 * Math.PI).toFloat()
    return v
}

/**
 * Everything drawn straight onto the buffer without world geometry: the sky and
 * its stars, clouds, rain, the held torch and sword, hurt flash and vignette.
 */
internal class Atmosphere(private val w: Int, private val h: Int, private val pixels: IntArray) {

    private val focal: Float get() = (w / 2f) / FOV_PLANE

    fun drawBackdrop(scene: RenderScene, horizon: Int, fogColor: Int) {
        if (!scene.map.outdoor) {
            drawCeiling(scene, horizon, fogColor)
            return
        }
        val light = scene.outdoorLight
        val sunEl = SkyGen.sunElevation(scene.timeOfDay)
        // warmth where the sun stands at the rim of the world
        val glow = exp(-abs(sunEl) * 5.5f)
        val zenith = mixColor(
            mixColor(0x05070E, 0x2A4C7A, light),
            0x3A2A3E, glow * 0.45f
        )
        val skyLow = mixColor(
            mixColor(0x0D1018, 0x9FB2C0, light),
            0xC2683A, glow
        )
        for (y in 0 until horizon) {
            val v = y.toFloat() / horizon
            val base = mixColor(zenith, skyLow, v * sqrt(v))
            for (x in 0 until w) {
                pixels[y * w + x] = ditherColor(base, 1f, x, y)
            }
        }
        if (scene.sky != null) {
            drawGalaxyBand(scene, horizon)
            drawStars(scene, horizon)
            drawConstellations(scene, horizon)
            drawSun(scene, horizon)
            drawMoons(scene, horizon)
        }
        drawClouds(scene, horizon)
        if (scene.aurora > 0.01f) drawAurora(scene, horizon)
        if (scene.meteors.isNotEmpty()) drawMeteors(scene, horizon)
        drawSkyline(scene, horizon, fogColor)
    }

    // ---------------------------------------------------------------- backdrop

    /** The northern lights: banded curtains of green and teal under a red crown. */
    private fun drawAurora(scene: RenderScene, horizon: Int) {
        val strength = scene.aurora
        val cam = scene.camera
        val north = -1.5708f
        val seed = (scene.sky?.noiseSeed ?: 11) + 5
        val t = scene.clock
        for (x in 0 until w) {
            val az = cam.angle + ((x - w / 2f) / (w / 2f)) * FOV_PLANE
            val delta = normalizeAngle(az - north)
            if (abs(delta) > 1.0f) continue
            val edge = 1f - abs(delta) / 1.0f
            val ripple = Textures.valueNoise(az * 4f, t * 0.35f, seed)
            val baseEl = 0.30f + ripple * 0.35f
            val height = 0.30f + ripple * 0.20f
            for (k in 0..24) {
                val f = k / 24f
                val y = horizon - (tan(baseEl + f * height) * focal).toInt()
                if (y < 0 || y >= horizon) continue
                val band = 1f - abs(f - 0.55f) / 0.55f
                val color = when {
                    f > 0.85f -> 0x8C3B4A // the red crown
                    f > 0.45f -> 0x3FA08A // teal
                    else -> 0x2E8B57      // green
                }
                val shimmer = Textures.valueNoise(az * 7f, t * 0.5f + f * 3f, seed)
                val amount = (strength * edge * band * (0.4f + 0.6f * shimmer)).coerceIn(0f, 1f)
                if (amount < 0.03f) continue
                pixels[y * w + x] = ditherColor(color, amount, x, y)
            }
        }
    }

    /** Falling stars: a bright head and a fading tail, gone in a breath. */
    private fun drawMeteors(scene: RenderScene, horizon: Int) {
        val cam = scene.camera
        val night = nightAmt(scene.outdoorLight)
        scene.meteors.forEach { meteor ->
            val travel = 0.5f * meteor.age
            for (seg in 0 until 10) {
                val t = travel - seg * 0.025f
                val az = meteor.az + meteor.dirAz * t
                val el = (meteor.el + meteor.dirEl * t).coerceAtLeast(0.02f)
                val dx = normalizeAngle(az - cam.angle)
                if (abs(dx) > 1.0f) continue
                val y = horizon - (tan(el) * focal).toInt()
                if (y < 0 || y >= horizon) continue
                val x = (w / 2f + (dx / FOV_PLANE) * (w / 2f)).toInt()
                if (x < 0 || x >= w) continue
                val fade = (1f - seg / 10f) * (1f - meteor.age * 0.6f)
                val brightness = meteor.bright * night * fade
                if (brightness < 0.05f) continue
                pixels[y * w + x] = ditherColor(0xE6E2C8, brightness, x, y)
                // the head burns a touch wider
                if (seg == 0 && x + 1 < w) {
                    pixels[y * w + x + 1] = ditherColor(0xE6E2C8, brightness * 0.7f, x + 1, y)
                }
            }
        }
    }

    /** Cloud in slow banks: grey wool by day, a lid that swallows the stars by night. */
    private fun drawClouds(scene: RenderScene, horizon: Int) {
        val cloud = scene.weather.cloud
        if (cloud < 0.03f) return
        val seed = (scene.sky?.noiseSeed ?: 7) + 31
        val cam = scene.camera
        val lit = (1f - nightAmt(scene.outdoorLight)).coerceIn(0f, 1f)
        val cloudColor = mixColor(0x0A0C10, 0x9AA0A8, lit * (1f - cloud * 0.45f))
        val threshold = 1f - cloud * 0.85f
        for (x in 0 until w) {
            val az = cam.angle + ((x - w / 2f) / (w / 2f)) * FOV_PLANE
            for (y in 0 until horizon) {
                val el = atan((horizon - y) / focal)
                val pattern = Textures.valueNoise(az * 3f + scene.clock * 0.012f, el * 5f, seed)
                if (pattern <= threshold) continue
                val a = ((pattern - threshold) / (1f - threshold)).coerceIn(0f, 1f) * 0.75f
                val idx = y * w + x
                pixels[idx] = mixColor(pixels[idx], cloudColor, a)
            }
        }
    }

    /** Rain: slanted streaks, hashed from the clock so no runtime luck is spent. */
    fun drawRain(scene: RenderScene) {
        val drops = (scene.weather.rain * 110).toInt()
        val tick = kotlin.math.floor(scene.clock * 16f).toInt()
        for (i in 0 until drops) {
            val hz = (i * 2654435761L) xor (tick.toLong() * 40503L)
            val x = ((hz ushr 8) % w).toInt()
            val y = ((hz ushr 24) % h).toInt()
            val len = 5 + i % 4
            for (k in 0 until len) {
                val px = x - k / 2
                val py = y + k * 2
                if (px in 0 until w && py in 0 until h) {
                    pixels[py * w + px] = mixColor(pixels[py * w + px], 0xAEBDC8, 0.5f)
                }
            }
        }
    }

    /** The river of the galaxy: a soft band of faint light, tilted by the world. */
    private fun drawGalaxyBand(scene: RenderScene, horizon: Int) {
        val sky = scene.sky ?: return
        val night = nightAmt(scene.outdoorLight) * (1f - scene.weather.cloud * 0.95f)
        if (night < 0.05f) return
        val cam = scene.camera
        for (x in 0 until w) {
            val az = cam.angle + ((x - w / 2f) / (w / 2f)) * FOV_PLANE
            val centerEl = 0.55f + sky.bandTilt * sin(az - sky.bandBearing)
            val centerRow = horizon - (tan(centerEl) * focal).toInt()
            for (dy in -10..10) {
                val y = centerRow + dy
                if (y < 0 || y >= horizon) continue
                val frac = 1f - abs(dy) / 10f
                val noise = Textures.valueNoise(x * 0.09f, y * 0.09f, sky.noiseSeed)
                val amount = frac * frac * noise * 0.5f
                if (amount < 0.04f) continue
                pixels[y * w + x] = ditherColor(0xA8B4C4, amount * night, x, y)
            }
        }
    }

    /** The fixed stars, climbing as the sun goes down and twinkling faintly. */
    private fun drawStars(scene: RenderScene, horizon: Int) {
        val sky = scene.sky ?: return
        val night = nightAmt(scene.outdoorLight) * (1f - scene.weather.cloud * 0.95f)
        if (night < 0.05f) return
        val cam = scene.camera
        for (star in sky.stars) {
            val dx = normalizeAngle(star.az - cam.angle)
            if (abs(dx) > 1.0f) continue
            val cy = horizon - (tan(star.el) * focal).toInt()
            if (cy < 0 || cy >= horizon) continue
            val x = (w / 2f + (dx / FOV_PLANE) * (w / 2f)).toInt()
            if (x < 0 || x >= w) continue
            val twinkle = 0.78f + 0.22f * sin(scene.clock * 2.1f + star.twinkle)
            pixels[cy * w + x] = ditherColor(star.color, star.bright * night * twinkle, x, cy)
        }
    }

    /** Constellation lines, faint as an old chart, and only deep in the night. */
    private fun drawConstellations(scene: RenderScene, horizon: Int) {
        val sky = scene.sky ?: return
        if ((1f - scene.outdoorLight).coerceIn(0f, 1f) < 0.72f || scene.weather.cloud > 0.65f) return
        val cam = scene.camera
        sky.constellations.forEach { con ->
            val pts = con.stars.mapNotNull { star ->
                val dx = normalizeAngle(star.az - cam.angle)
                if (abs(dx) > 1.0f) return@mapNotNull null
                val cy = horizon - (tan(star.el) * focal).toInt()
                if (cy < 0 || cy >= horizon) return@mapNotNull null
                Pair((w / 2f + (dx / FOV_PLANE) * (w / 2f)).toInt(), cy)
            }
            for (i in 0 until pts.size - 1) {
                drawFaintLine(pts[i], pts[i + 1], horizon)
            }
        }
    }

    private fun drawFaintLine(a: Pair<Int, Int>, b: Pair<Int, Int>, horizon: Int) {
        val steps = maxOf(abs(b.first - a.first), abs(b.second - a.second)).coerceAtLeast(1)
        for (i in 0..steps) {
            val x = a.first + (b.first - a.first) * i / steps
            val y = a.second + (b.second - a.second) * i / steps
            if (x < 0 || x >= w || y < 0 || y >= horizon) continue
            pixels[y * w + x] = mixColor(pixels[y * w + x], 0x9FB4D8, 0.16f)
        }
    }

    /** The sun: blood at the rim of the world, white-gold at its height. */
    private fun drawSun(scene: RenderScene, horizon: Int) {
        val el = SkyGen.sunElevation(scene.timeOfDay)
        if (el < -0.02f) return
        val pos = projectOnDome(scene.camera, SkyGen.sunAzimuth(scene.timeOfDay), el.coerceAtLeast(0.01f), horizon)
            ?: return
        val r = (w * 0.05f).toInt().coerceAtLeast(5)
        val warm = exp(-SkyGen.sunElevation(scene.timeOfDay) * 3f)
        var body = mixColor(0xF2E6C0, 0xC2683A, warm * 0.85f)
        // cloud thins the sun's face
        body = mixColor(body, 0x6E747C, scene.weather.cloud * 0.7f)
        drawDisc(pos.first, pos.second, r, horizon) { _, _, d2 ->
            if (d2 > 1f) null else body
        }
    }

    /** Every moon of this world, each on its own rising hour and its own phase. */
    private fun drawMoons(scene: RenderScene, horizon: Int) {
        val sky = scene.sky ?: return
        if (nightAmt(scene.outdoorLight) < 0.02f) return
        sky.moons.forEachIndexed { i, moon ->
            val mt = (scene.timeOfDay + moon.offset) % 1f
            val el = sin(2f * Math.PI.toFloat() * (mt - 0.25f)) * 0.9f
            if (el < 0.01f) return@forEachIndexed
            val az = Math.PI.toFloat() * ((mt - 0.25f) / 0.5f)
            val pos = projectOnDome(scene.camera, az, el, horizon) ?: return@forEachIndexed
            val r = (w * moon.size).toInt().coerceAtLeast(5)
            // the phase, turning slowly across the days of the world
            val dayFloat = scene.dayNumber + scene.timeOfDay
            val illum = 0.5f + 0.5f * sin(2f * Math.PI.toFloat() * dayFloat / moon.periodDays + moon.phase0)
            val litAmt = illum * 1.6f - 0.3f
            val craterSeed = 9 + i
            val cloudVeil = (scene.weather.cloud * 0.85f).coerceIn(0f, 1f)
            drawDisc(pos.first, pos.second, r, horizon) { dx, dy, d2 ->
                if (d2 > 1f) null else {
                    val side = (litAmt + dx * 0.5f).coerceIn(0f, 1f)
                    var body = mixColor(moon.darkTint, moon.tint, side)
                    val crater = Textures.valueNoise(dx * 1.7f + 3f, dy * 1.7f + 7f, craterSeed)
                    if (crater > 0.62f) body = mixColor(body, moon.darkTint, 0.4f)
                    // cloud takes the shine off the moon
                    mixColor(body, 0x0A0C10, cloudVeil)
                }
            }
        }
    }

    /** Where a point on the dome lands on screen, or null when out of sight. */
    private fun projectOnDome(cam: Camera, az: Float, el: Float, horizon: Int): Pair<Int, Int>? {
        val dx = normalizeAngle(az - cam.angle)
        if (abs(dx) > 1.0f) return null
        val cy = horizon - (tan(el) * focal).toInt()
        if (cy >= horizon) return null
        return Pair((w / 2f + (dx / FOV_PLANE) * (w / 2f)).toInt(), cy)
    }

    private fun drawDisc(
        cx: Int,
        cy: Int,
        r: Int,
        horizon: Int,
        colorAt: (dx: Float, dy: Float, d2: Float) -> Int?
    ) {
        for (y in (cy - r)..(cy + r)) {
            if (y < 0 || y >= horizon) continue
            for (x in (cx - r)..(cx + r)) {
                if (x < 0 || x >= w) continue
                val dx = (x - cx).toFloat() / r
                val dy = (y - cy).toFloat() / r
                val d2 = dx * dx + dy * dy
                if (d2 > 1f) continue
                val color = colorAt(dx, dy, d2) ?: continue
                pixels[y * w + x] = ditherColor(color, 1f - d2 * 0.2f, x, y)
            }
        }
    }

    /** The lit gate of the nearest town, sitting on the horizon where the road points. */
    private fun drawSkyline(scene: RenderScene, horizon: Int, fogColor: Int) {
        if (scene.townDistance <= 0f) return
        val cam = scene.camera
        for (x in 0 until w) {
            val columnAngle = cam.angle + ((x - w / 2f) / (w / 2f)) * FOV_PLANE
            val delta = normalizeAngle(columnAngle - scene.townBearing)
            if (abs(delta) > 0.30f) continue
            val edge = 1f - (abs(delta) / 0.30f)
            val towerSeed = Textures.valueNoise(x * 0.09f, 3.5f, 17)
            val towerHeight = (h * (0.012f + 0.05f * towerSeed * edge)).toInt()
            if (towerHeight < 2) continue
            val top = horizon - towerHeight
            for (y in top until horizon) {
                if (y < 0) continue
                val silhouette = mixColor(0x16130F, fogColor, 0.45f)
                pixels[y * w + x] = ditherColor(silhouette, 1f, x, y)
            }
            // firelight in the windows after dark; smoke standing over the roofs by day
            if (towerHeight > 4) {
                if (scene.outdoorLight < 0.45f) {
                    if ((x * 7 + towerHeight) % 11 == 0) {
                        val wy = top + 1 + (towerHeight / 3)
                        if (wy in 0 until horizon) pixels[wy * w + x] = (0xFF shl 24) or 0xC8952F
                    }
                } else if ((x * 13 + towerHeight) % 17 == 0) {
                    val smokeTop = top - (h * 0.06f * Textures.valueNoise(x * 0.05f, 7.5f, 23)).toInt()
                    var sy = smokeTop
                    while (sy < top) {
                        if (sy >= 0 && (sy - smokeTop) % 3 != 0) {
                            val sx = x + (((sy - smokeTop) / 5) % 3) - 1
                            if (sx in 0 until w) {
                                pixels[sy * w + sx] = ditherColor(mixColor(0x39332A, fogColor, 0.35f), 1f, sx, sy)
                            }
                        }
                        sy++
                    }
                }
            }
        }
    }

    private fun drawCeiling(scene: RenderScene, horizon: Int, fogColor: Int) {
        val cam = scene.camera
        val scale = vScale(w)
        val dirX = cam.dirX
        val dirY = cam.dirY
        val planeX = -dirY * FOV_PLANE
        val planeY = dirX * FOV_PLANE
        for (y in 0 until horizon) {
            val p = (horizon - y).toFloat()
            val rowDist = ((1f - cam.eye) * scale) / p
            if (rowDist > 40f) {
                for (x in 0 until w) pixels[y * w + x] = fogColor
                continue
            }
            val stepX = rowDist * (2 * planeX) / w
            val stepY = rowDist * (2 * planeY) / w
            var fx = cam.x + rowDist * (dirX - planeX)
            var fy = cam.y + rowDist * (dirY - planeY)
            val brightness = torchBrightness(rowDist, scene.torch) * 0.75f
            val fog = fogFactor(rowDist, scene.map.outdoor, scene.weather.fogBoost)
            for (x in 0 until w) {
                val cellX = kotlin.math.floor(fx).toInt()
                val cellY = kotlin.math.floor(fy).toInt()
                val tx = ((fx - cellX) * Textures.SIZE).toInt()
                val ty = ((fy - cellY) * Textures.SIZE).toInt()
                val color = Textures.sampleFloor(Textures.FLOOR_CEILING, tx, ty)
                pixels[y * w + x] = fogged(ditherColor(color, brightness, x, y), fogColor, fog)
                fx += stepX
                fy += stepY
            }
        }
    }

    // ---------------------------------------------------------------- hands

    fun drawHeld(scene: RenderScene) {
        val bob = (sin(scene.camera.bob * 0.35f) * h * 0.006f).toInt()
        val swing = scene.swingPhase
        // The torch shows only when it is actually held; the satchel bundle stays dark.
        if (scene.heldTorch) {
            val torch = Sprites[Sprites.HELD_TORCH]
            val torchH = (h * 0.34f).toInt()
            val torchW = torchH * torch.w / torch.h
            blit(
                torch,
                x0 = (-torchW * 0.15f).toInt(),
                y0 = h - torchH + bob,
                dw = torchW,
                dh = torchH,
                flash = 0f
            )
        }

        val sword = if (scene.heldWeaponSprite >= 0) {
            Sprites.tinted(scene.heldWeaponSprite, scene.heldWeaponTint)
        } else {
            Sprites[Sprites.HELD_SWORD]
        }
        val swordH = (h * (0.42f + swing * 0.08f)).toInt()
        val swordW = swordH * sword.w / sword.h
        val lunge = (swing * h * 0.09f).toInt()
        blit(
            sword,
            x0 = w - swordW + (swing * w * 0.10f).toInt(),
            y0 = h - swordH + bob + (h * 0.05f).toInt() - lunge,
            dw = swordW,
            dh = swordH,
            flash = 0f
        )

        // The board rides the left arm: carried low, raised into the guard.
        if (scene.heldShieldSprite >= 0) {
            val shield = Sprites.tinted(scene.heldShieldSprite, scene.heldShieldTint)
            val raise = scene.shieldRaise.coerceIn(0f, 1f)
            val shieldH = (h * (0.30f + raise * 0.20f)).toInt()
            val shieldW = shieldH * shield.w / shield.h
            blit(
                shield,
                x0 = (w * (0.03f + raise * 0.10f)).toInt(),
                y0 = h - shieldH + bob + (h * 0.07f).toInt() - (raise * h * 0.22f).toInt(),
                dw = shieldW,
                dh = shieldH,
                flash = 0f
            )
        }

        if (scene.strikeArc > 0.01f) drawStrikeArc(scene.strikeArc)
    }

    private fun drawStrikeArc(t: Float) {
        val alpha = (1f - t).coerceIn(0f, 1f)
        val cx = w * 0.5f
        val cy = h * 0.52f
        val radius = w * (0.30f + t * 0.35f)
        val sweepStart = -0.7f
        val sweepEnd = 1.5f
        var a = sweepStart
        while (a < sweepEnd) {
            val px = (cx + cos(a) * radius).toInt()
            val py = (cy + sin(a) * radius * 0.75f).toInt()
            for (dy in -1..1) for (dx in -1..1) {
                val x = px + dx
                val y = py + dy
                if (x in 0 until w && y in 0 until h) {
                    pixels[y * w + x] = mixColor(pixels[y * w + x], 0xE6DAC0, 0.55f * alpha)
                }
            }
            a += 0.03f
        }
    }

    private fun blit(sprite: Sprite, x0: Int, y0: Int, dw: Int, dh: Int, flash: Float) {
        for (sy in 0 until dh) {
            val y = y0 + sy
            if (y < 0 || y >= h) continue
            val texY = sy * sprite.h / dh
            for (sx in 0 until dw) {
                val x = x0 + sx
                if (x < 0 || x >= w) continue
                val texX = sx * sprite.w / dw
                val texel = sprite.px[texY * sprite.w + texX]
                if ((texel ushr 24) == 0) continue
                var color = texel and 0xFFFFFF
                if (flash > 0f) color = mixColor(color, 0xE6DAC0, flash)
                pixels[y * w + x] = (0xFF shl 24) or (color and 0xFFFFFF)
            }
        }
    }

    // ---------------------------------------------------------------- post

    fun applyHurt(strength: Float) {
        val amount = strength.coerceIn(0f, 0.7f)
        for (i in pixels.indices) {
            pixels[i] = mixColor(pixels[i], 0xA33B28, amount * 0.55f)
        }
    }

    fun applyVignette() {
        val cx = w / 2f
        val cy = h / 2f
        val maxD = sqrt(cx * cx + cy * cy)
        for (y in 0 until h step 1) {
            val dy = y - cy
            for (x in 0 until w) {
                val dx = x - cx
                val d = sqrt(dx * dx + dy * dy) / maxD
                if (d < 0.55f) continue
                val amount = ((d - 0.55f) / 0.45f).coerceIn(0f, 1f) * 0.85f
                pixels[y * w + x] = mixColor(pixels[y * w + x], 0x000000, amount)
            }
        }
    }
}
