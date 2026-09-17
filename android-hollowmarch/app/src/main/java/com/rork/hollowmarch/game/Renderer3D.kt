package com.rork.hollowmarch.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.tan

/** Everything that can draw a frame from a scene into a pixel buffer. */
internal interface FrameRenderer {
    val pixels: IntArray
    fun render(scene: RenderScene)
}

/**
 * A true 3D polygon renderer in the Daggerfall manner: the map's grid becomes real
 * geometry — every wall cell a textured box, the ground and roof real planes — drawn
 * by a perspective-correct software rasterizer into the same low-resolution buffer,
 * with a per-pixel depth buffer. The folk stay camera-facing billboards, depth-tested
 * against the world, and the sky, weather, hands and post effects come from the
 * shared atmosphere code, so the 1996 dither look never changes.
 */
class Renderer3D(val w: Int, val h: Int) : FrameRenderer {

    override val pixels = IntArray(w * h)
    private val depth = FloatArray(w * h)
    private val scale = vScale(w)
    private val focalH = w / (2f * FOV_PLANE)
    private val fx = Atmosphere(w, h, pixels)

    // per-frame state
    private var horizonY = 0
    private var camX = 0f
    private var camY = 0f
    private var dirX = 1f
    private var dirY = 0f
    private var eyeZ = 0.5f
    private var torch = 1f
    private var fogColor = 0
    private var fogTable = FloatArray(256)

    // current draw call
    private var mode = MODE_TILE
    private var texId = 0
    private var shade = 1f
    private var ambient = 0f
    private var sprite: Sprite? = null
    private var flash = 0f

    // quad assembly: four vertices of [lateral, forward, height, texU, texV]
    private val q = FloatArray(20)
    private var qi = 0

    // clipping and projection scratch
    private val clipBuf = FloatArray(40)
    private val px = FloatArray(9)
    private val py = FloatArray(9)
    private val piz = FloatArray(9)
    private val puiz = FloatArray(9)
    private val pviz = FloatArray(9)

    /** One thing to draw as a camera-facing billboard. A flying shot rides its own foot height. */
    private class Board(
        val sprite: Sprite,
        val x: Float,
        val y: Float,
        val height: Float,
        val flash: Float,
        val footZ: Float = -1f,
        val gear: List<GearDraw> = emptyList()
    )

    /** A carried piece riding its bearer's board, painted just over the body. */
    private class GearDraw(
        val sprite: Sprite,
        val sideFrac: Float,
        val bottomFrac: Float,
        val heightFrac: Float
    )

    private companion object {
        const val NEAR = 0.05f
        const val MODE_TILE = 0
        const val MODE_FLOOR = 1
        const val MODE_SPRITE = 2
        const val TEX = 64f
    }

    override fun render(scene: RenderScene) {
        Textures.ensureBuilt()
        Sprites.ensureBuilt()
        depth.fill(Float.MAX_VALUE)
        val cam = scene.camera
        // Vertical look, sheared Doom-fashion: pitch slides the horizon and every
        // wall, hill and star moves with it. Clamped so the sky painters stay in frame.
        horizonY = (h * 0.5f + cam.bob + tan(cam.pitch) * scale).toInt().coerceIn(2, h - 2)
        fogColor = if (scene.map.outdoor) duskFog(scene.outdoorLight, scene.weather) else 0x0A0908
        fogTable = buildFogTable(scene.map.outdoor, scene.weather.fogBoost)
        torch = scene.torch
        camX = cam.x
        camY = cam.y
        dirX = cam.dirX
        dirY = cam.dirY
        eyeZ = cam.eye + scene.map.heightAt(cam.x, cam.y)

        fx.drawBackdrop(scene, horizonY, fogColor)
        drawWorld(scene)
        // What no geometry reached is the fog itself, as the far country fades.
        if (scene.map.outdoor) fillDistantWithFog()
        drawSprites(scene)
        if (scene.map.outdoor && scene.weather.rain > 0.01f) fx.drawRain(scene)
        fx.drawHeld(scene)
        if (scene.hurtFlash > 0.01f) fx.applyHurt(scene.hurtFlash)
        fx.applyVignette()
    }

    // ---------------------------------------------------------------- geometry

    private fun drawWorld(scene: RenderScene) {
        val map = scene.map
        val outdoor = map.outdoor
        val light = scene.outdoorLight

        // The ground: the flat world merges runs of one floor stone along each row;
        // the rolling world walks its lattice cell by cell, level runs merged.
        if (map.heights == null) {
            for (y in 0 until map.height) {
                val row = y * map.width
                var x = 0
                while (x < map.width) {
                    if (map.walls[row + x] != 0) {
                        x++
                        continue
                    }
                    val tex = map.floorTex[row + x]
                    var x1 = x + 1
                    while (x1 < map.width && map.walls[row + x1] == 0 && map.floorTex[row + x1] == tex) x1++
                    floorQuad(x.toFloat(), y.toFloat(), x1.toFloat(), y + 1f, tex, if (outdoor) light * 0.85f else 0f)
                    x = x1
                }
            }
        } else {
            drawTerrainFloor(map, light)
        }
        // The world's edge: flagstone reaching out to the fog.
        drawApron(map, outdoor, light)

        // The roof of the underworld, indoors only: one flat plane where the deep
        // places keep their old single course, and cell by cell where they rise.
        if (!outdoor) {
            if (map.ceilingHeights != null) {
                drawCeilings(map)
            } else {
                setDraw(MODE_FLOOR, Textures.FLOOR_CEILING, 0.75f, 0f)
                vWorld(0f, 0f, 1f, 0f, 0f)
                vWorld(map.width.toFloat(), 0f, 1f, map.width * TEX, 0f)
                vWorld(map.width.toFloat(), map.height.toFloat(), 1f, map.width * TEX, map.height * TEX)
                vWorld(0f, map.height.toFloat(), 1f, 0f, map.height * TEX)
                flush()
            }
        }

        // The walls: one textured face per open side, the shade falling on y-facing stone.
        // Only what the fog could not yet claim is worth its fill.
        val r = 64
        val y0 = maxOf(0, (camY - r).toInt())
        val y1 = minOf(map.height - 1, (camY + r + 1).toInt())
        val x0 = maxOf(0, (camX - r).toInt())
        val x1 = minOf(map.width - 1, (camX + r + 1).toInt())
        for (y in y0..y1) {
            for (x in x0..x1) {
                val t = map.walls[y * map.width + x]
                if (t == 0) continue
                val xf = x.toFloat()
                val yf = y.toFloat()
                val hHere = map.wallHeight(x, y)
                wallSide(map, x, y - 1, hHere, xf, yf, xf + 1f, yf, xf * TEX, (xf + 1f) * TEX, t, 0.72f)
                wallSide(map, x, y + 1, hHere, xf, yf + 1f, xf + 1f, yf + 1f, xf * TEX, (xf + 1f) * TEX, t, 0.72f)
                wallSide(map, x - 1, y, hHere, xf, yf, xf, yf + 1f, yf * TEX, (yf + 1f) * TEX, t, 1f)
                wallSide(map, x + 1, y, hHere, xf + 1f, yf, xf + 1f, yf + 1f, (xf + 1f) * TEX, (yf + 1f) * TEX, t, 1f)
            }
        }

        // The roofs: a pitched plane over every raised house, its gables of stone.
        drawRoofs(map)
    }

    /** The rolling ground: one quad per cell of the lattice, level runs merged. */
    private fun drawTerrainFloor(map: GameMap, light: Float) {
        val amb = light * 0.85f
        val r = 44
        val y0 = maxOf(0, (camY - r).toInt())
        val y1 = minOf(map.height - 1, (camY + r + 1).toInt())
        val x0 = maxOf(0, (camX - r).toInt())
        val x1 = minOf(map.width - 1, (camX + r + 1).toInt())
        for (y in y0..y1) {
            val row = y * map.width
            var x = x0
            while (x <= x1) {
                if (map.walls[row + x] != 0) {
                    x++
                    continue
                }
                val tex = map.floorTex[row + x]
                var xe = x
                while (xe + 1 <= x1 && map.walls[row + xe + 1] == 0 &&
                    map.floorTex[row + xe + 1] == tex && runContinuesFlat(map, x, xe + 1, y)
                ) {
                    xe++
                }
                terrainQuad(map, x.toFloat(), y.toFloat(), (xe + 1).toFloat(), (y + 1).toFloat(), tex, amb)
                x = xe + 1
            }
        }
    }

    /** True when the cell at xa keeps the run's west corners: the whole run lies flat. */
    private fun runContinuesFlat(map: GameMap, runX: Int, xa: Int, y: Int): Boolean {
        if (!Landform.flatCell(map, xa, y)) return false
        val vw = map.width + 1
        val hts = map.heights ?: return false
        return abs(hts[y * vw + xa] - hts[y * vw + runX]) < Landform.FLAT_EPS &&
            abs(hts[(y + 1) * vw + xa] - hts[(y + 1) * vw + runX]) < Landform.FLAT_EPS
    }

    private fun drawApron(map: GameMap, outdoor: Boolean, light: Float) {
        setDraw(MODE_FLOOR, Textures.FLOOR_FLAG, 1f, if (outdoor) light * 0.85f else 0f)
        val lo = -256f
        val hiX = map.width + 256f
        val hiY = map.height + 256f
        apronQuad(map, lo, -256f, hiX, 0f)
        apronQuad(map, lo, map.height.toFloat(), hiX, hiY)
        apronQuad(map, -256f, 0f, 0f, map.height.toFloat())
        apronQuad(map, map.width.toFloat(), 0f, hiX, map.height.toFloat())
    }

    private fun apronQuad(map: GameMap, x0: Float, y0: Float, x1: Float, y1: Float) {
        vWorld(x0, y1, map.heightAt(x0, y1), x0 * TEX, y1 * TEX)
        vWorld(x1, y1, map.heightAt(x1, y1), x1 * TEX, y1 * TEX)
        vWorld(x1, y0, map.heightAt(x1, y0), x1 * TEX, y0 * TEX)
        vWorld(x0, y0, map.heightAt(x0, y0), x0 * TEX, y0 * TEX)
        flush()
    }

    private fun floorQuad(x0: Float, y0: Float, x1: Float, y1: Float, tex: Int, amb: Float) {
        setDraw(MODE_FLOOR, tex, 1f, amb)
        vWorld(x0, y1, 0f, x0 * TEX, y1 * TEX)
        vWorld(x1, y1, 0f, x1 * TEX, y1 * TEX)
        vWorld(x1, y0, 0f, x1 * TEX, y0 * TEX)
        vWorld(x0, y0, 0f, x0 * TEX, y0 * TEX)
        flush()
    }

    private fun wallFace(
        map: GameMap, ax: Float, ay: Float, bx: Float, by: Float,
        u0: Float, u1: Float, tex: Int, sh: Float, z0: Float, z1: Float
    ) {
        // A wall rides the land: its foot at the ground's height, its head where the
        // masonry stops — one wall high, or three for a keep. The courses of stone
        // repeat with every height, as the old castles were laid.
        val hA = map.heightAt(ax, ay)
        val hB = map.heightAt(bx, by)
        setDraw(MODE_TILE, tex, sh, 0f)
        vWorld(ax, ay, hA + z1, u0, (1f - z1) * TEX)
        vWorld(bx, by, hB + z1, u1, (1f - z1) * TEX)
        vWorld(bx, by, hB + z0, u1, (1f - z0) * TEX)
        vWorld(ax, ay, hA + z0, u0, (1f - z0) * TEX)
        flush()
    }

    /**
     * One side of a wall cell: its whole face where the neighbor is open, the band
     * above a shorter neighbor — the steps of keeps and the crenels of battlements —
     * and nothing where the neighbor stands as tall or taller.
     */
    private fun wallSide(
        map: GameMap, nx: Int, ny: Int, hHere: Float,
        ax: Float, ay: Float, bx: Float, by: Float,
        u0: Float, u1: Float, tex: Int, sh: Float
    ) {
        if (nx < 0 || ny < 0 || nx >= map.width || ny >= map.height) return
        val hn = map.wallHeight(nx, ny)
        if (hHere > hn + 1e-4f) wallFace(map, ax, ay, bx, by, u0, u1, tex, sh, hn, hHere)
    }

    // ---------------------------------------------------------------- roofs

    /** A pitched roof over every raised house on the map, culled like the ground. */
    private fun drawRoofs(map: GameMap) {
        for (roof in map.roofs) {
            val cx = (roof.x0 + roof.x1) * 0.5f - camX
            val cy = (roof.y0 + roof.y1) * 0.5f - camY
            val fwd = dirX * cx + dirY * cy
            if (fwd < -3f || fwd > 46f) continue
            val lat = dirX * cy - dirY * cx
            val span = (roof.x1 - roof.x0 + roof.y1 - roof.y0) * 0.5f
            if (abs(lat) > fwd * FOV_PLANE + span + 3f) continue
            drawRoof(map, roof)
        }
    }

    /**
     * One roof: two slopes from eave to ridge — shingle courses riding the pitch,
     * shaded as the walls are — and the gable ends closed with wall stone.
     */
    private fun drawRoof(map: GameMap, roof: Roof) {
        val x0 = roof.x0.toFloat()
        val y0 = roof.y0.toFloat()
        val x1 = roof.x1.toFloat()
        val y1 = roof.y1.toFloat()
        val e = roof.eave
        val r = roof.ridge
        if (roof.alongX) {
            val ry = (y0 + y1) * 0.5f
            setDraw(MODE_TILE, roof.roofTex, 0.72f, 0f)
            vWorld(x0, y1, map.heightAt(x0, y1) + e, x0 * TEX, (1f - e) * TEX)
            vWorld(x1, y1, map.heightAt(x1, y1) + e, x1 * TEX, (1f - e) * TEX)
            vWorld(x1, ry, map.heightAt(x1, ry) + r, x1 * TEX, (1f - r) * TEX)
            vWorld(x0, ry, map.heightAt(x0, ry) + r, x0 * TEX, (1f - r) * TEX)
            flush()
            setDraw(MODE_TILE, roof.roofTex, 1f, 0f)
            vWorld(x0, ry, map.heightAt(x0, ry) + r, x0 * TEX, (1f - r) * TEX)
            vWorld(x1, ry, map.heightAt(x1, ry) + r, x1 * TEX, (1f - r) * TEX)
            vWorld(x1, y0, map.heightAt(x1, y0) + e, x1 * TEX, (1f - e) * TEX)
            vWorld(x0, y0, map.heightAt(x0, y0) + e, x0 * TEX, (1f - e) * TEX)
            flush()
            gable(map, roof, x0, y0, y1, ry, e, r, alongX = true)
            gable(map, roof, x1, y0, y1, ry, e, r, alongX = true)
        } else {
            val rx = (x0 + x1) * 0.5f
            setDraw(MODE_TILE, roof.roofTex, 1f, 0f)
            vWorld(x0, y0, map.heightAt(x0, y0) + e, y0 * TEX, (1f - e) * TEX)
            vWorld(x0, y1, map.heightAt(x0, y1) + e, y1 * TEX, (1f - e) * TEX)
            vWorld(rx, y1, map.heightAt(rx, y1) + r, y1 * TEX, (1f - r) * TEX)
            vWorld(rx, y0, map.heightAt(rx, y0) + r, y0 * TEX, (1f - r) * TEX)
            flush()
            setDraw(MODE_TILE, roof.roofTex, 0.72f, 0f)
            vWorld(rx, y0, map.heightAt(rx, y0) + r, y0 * TEX, (1f - r) * TEX)
            vWorld(rx, y1, map.heightAt(rx, y1) + r, y1 * TEX, (1f - r) * TEX)
            vWorld(x1, y1, map.heightAt(x1, y1) + e, y1 * TEX, (1f - e) * TEX)
            vWorld(x1, y0, map.heightAt(x1, y0) + e, y0 * TEX, (1f - e) * TEX)
            flush()
            gable(map, roof, y0, x0, x1, rx, e, r, alongX = false)
            gable(map, roof, y1, x0, x1, rx, e, r, alongX = false)
        }
    }

    /** One gable end, closed with the house's own stone: a triangle under the peak. */
    private fun gable(
        map: GameMap, roof: Roof, plane: Float, a0: Float, a1: Float,
        mid: Float, e: Float, r: Float, alongX: Boolean
    ) {
        setDraw(MODE_TILE, roof.wallTex, 1f, 0f)
        fun vert(a: Float, z: Float) {
            val g = if (alongX) map.heightAt(plane, a) else map.heightAt(a, plane)
            if (alongX) vWorld(plane, a, g + z, a * TEX, (1f - z) * TEX)
            else vWorld(a, plane, g + z, a * TEX, (1f - z) * TEX)
        }
        vert(a0, e)
        vert(a1, e)
        vert(mid, r)
        vert(mid, r)
        flush()
    }

    /** The deep roof, cell by cell: crawlways press close, the halls read as caverns. */
    private fun drawCeilings(map: GameMap) {
        val ceilings = map.ceilingHeights ?: return
        val r = 44
        val y0 = maxOf(0, (camY - r).toInt())
        val y1 = minOf(map.height - 1, (camY + r + 1).toInt())
        val x0 = maxOf(0, (camX - r).toInt())
        val x1 = minOf(map.width - 1, (camX + r + 1).toInt())
        for (y in y0..y1) {
            for (x in x0..x1) {
                setDraw(MODE_FLOOR, Textures.FLOOR_CEILING, 0.75f, 0f)
                val ch = ceilings[y * map.width + x]
                vWorld(x.toFloat(), (y + 1).toFloat(), ch, x * TEX, (y + 1) * TEX)
                vWorld((x + 1).toFloat(), (y + 1).toFloat(), ch, (x + 1) * TEX, (y + 1) * TEX)
                vWorld((x + 1).toFloat(), y.toFloat(), ch, (x + 1) * TEX, y * TEX)
                vWorld(x.toFloat(), y.toFloat(), ch, x * TEX, y * TEX)
                flush()
            }
        }
    }

    /** One stretch of rolling ground, its corners read from the land's lattice. */
    private fun terrainQuad(map: GameMap, x0: Float, y0: Float, x1: Float, y1: Float, tex: Int, amb: Float) {
        // refuse what stands behind or far beyond the fog before it is assembled
        val cx = (x0 + x1) * 0.5f - camX
        val cy = (y0 + y1) * 0.5f - camY
        val fwd = dirX * cx + dirY * cy
        if (fwd < -2f || fwd > 46f) return
        val lat = dirX * cy - dirY * cx
        if (abs(lat) > fwd * FOV_PLANE + 3f) return
        val h00 = map.heightAt(x0, y0)
        val h10 = map.heightAt(x1, y0)
        val h11 = map.heightAt(x1, y1)
        val h01 = map.heightAt(x0, y1)
        setDraw(MODE_FLOOR, tex, 1f, amb)
        vWorld(x0, y1, h01, x0 * TEX, y1 * TEX)
        vWorld(x1, y1, h11, x1 * TEX, y1 * TEX)
        vWorld(x1, y0, h10, x1 * TEX, y0 * TEX)
        vWorld(x0, y0, h00, x0 * TEX, y0 * TEX)
        flush()
    }

    /** The fog itself, wherever no ground reached: the far country beyond the mesh. */
    private fun fillDistantWithFog() {
        val fc = fogColor
        for (y in horizonY until h) {
            val base = y * w
            for (x in 0 until w) {
                if (depth[base + x] == Float.MAX_VALUE) pixels[base + x] = fc
            }
        }
    }

    // ---------------------------------------------------------------- billboards

    private fun drawSprites(scene: RenderScene) {
        val ambient = if (scene.map.outdoor) scene.outdoorLight * 0.85f else 0f
        val cull = 32f * 32f
        val viewAngle = atan2(dirY, dirX)
        val boards = ArrayList<Board>(64)
        // A whole province of trees must not be projected: only what stands near.
        scene.map.entities.forEach { entity ->
            if (dist2(entity.x, entity.y, camX, camY) > cull) return@forEach
            // The dead all lie as one shape: the same sprawled corpse whatever they were.
            val dead = entity.kind == EntityKind.ENEMY && !entity.alive
            // What they carry rides their board: the weapon hand on the side it faces.
            val gear = if (dead || entity.equipment == null) emptyList<GearDraw>() else {
                val rightSign = cos(entity.facingAngle - viewAngle)
                GearVisualizer.pieces(entity, rightSign, entity.guardRaise).map { piece ->
                    GearDraw(Sprites.tinted(piece.spriteId, piece.tint), piece.side, piece.bottom, piece.scale)
                }
            }
            boards += Board(
                Sprites[if (dead) Sprites.CORPSE else entity.spriteId],
                entity.x, entity.y,
                if (dead) entity.height * 0.32f else entity.height,
                entity.hurtFlash,
                gear = gear
            )
        }
        // Whatever you set down waits on the floor, one humble bundle among all.
        scene.groundItems.forEach { drop ->
            if (dist2(drop.x, drop.y, camX, camY) <= cull) {
                boards += Board(Sprites.forDrop(drop.item), drop.x, drop.y, 0.22f, 0f)
            }
        }
        // What is in the air: honest shot, riding its own height above the land.
        scene.projectiles.forEach { p ->
            if (dist2(p.x, p.y, camX, camY) > cull) return@forEach
            val height = when (p.spriteId) {
                Sprites.P_ARROW -> 0.34f
                Sprites.P_BOLT -> 0.30f
                Sprites.P_BALL -> 0.16f
                Sprites.P_SHOT -> 0.12f
                else -> 0.40f
            }
            val tint = (p.ammoItem.headMaterial ?: p.ammoItem.material).tint
            boards += Board(Sprites.tinted(p.spriteId, tint), p.x, p.y, height, 0f, p.z)
        }
        boards.sortByDescending { dist2(it.x, it.y, camX, camY) }

        boards.forEach { board ->
            val relX = board.x - camX
            val relY = board.y - camY
            val v = dirX * relX + dirY * relY
            if (v < 0.20f) return@forEach
            val u = dirX * relY - dirY * relX
            val sp = board.sprite
            // The vertical stretch: billboards ride the vertical scale, not the horizontal.
            val halfW = board.height * (sp.w.toFloat() / sp.h) * (scale / focalH) * 0.5f
            // Every soul stands on the land; a flying shot rides its own height.
            val base = if (board.footZ >= 0f) board.footZ else scene.map.heightAt(board.x, board.y)
            setDraw(MODE_SPRITE, 0, 1f, ambient)
            sprite = sp
            flash = board.flash
            vCam(u - halfW, v, base + board.height - eyeZ, 0f, 0f)
            vCam(u + halfW, v, base + board.height - eyeZ, sp.w.toFloat(), 0f)
            vCam(u + halfW, v, base - eyeZ, sp.w.toFloat(), sp.h.toFloat())
            vCam(u - halfW, v, base - eyeZ, 0f, sp.h.toFloat())
            flush()
            // The bearer's own gear rides the same board, painted over the body:
            // sprites never write depth, so near-to-far painter's order keeps every
            // piece honest — over its bearer, under whatever stands nearer.
            for (g in board.gear) {
                val gs = g.sprite
                val gHalfW = g.heightFrac * board.height * (gs.w.toFloat() / gs.h) * (scale / focalH) * 0.5f
                val gBottom = base + g.bottomFrac * board.height
                val gTop = gBottom + g.heightFrac * board.height
                val gU = u + g.sideFrac * halfW
                setDraw(MODE_SPRITE, 0, 1f, ambient)
                sprite = gs
                flash = board.flash
                vCam(gU - gHalfW, v, gTop - eyeZ, 0f, 0f)
                vCam(gU + gHalfW, v, gTop - eyeZ, gs.w.toFloat(), 0f)
                vCam(gU + gHalfW, v, gBottom - eyeZ, gs.w.toFloat(), gs.h.toFloat())
                vCam(gU - gHalfW, v, gBottom - eyeZ, 0f, gs.h.toFloat())
                flush()
            }
        }
    }

    // ---------------------------------------------------------------- rasterizer

    private fun setDraw(m: Int, tex: Int, sh: Float, amb: Float) {
        mode = m
        texId = tex
        shade = sh
        ambient = amb
        sprite = null
        flash = 0f
        qi = 0
    }

    /** A vertex given in world coordinates, textured in texel units. */
    private fun vWorld(wx: Float, wy: Float, wz: Float, tu: Float, tv: Float) {
        val relX = wx - camX
        val relY = wy - camY
        q[qi] = dirX * relY - dirY * relX
        q[qi + 1] = dirX * relX + dirY * relY
        q[qi + 2] = wz - eyeZ
        q[qi + 3] = tu
        q[qi + 4] = tv
        qi += 5
    }

    /** A vertex already in camera coordinates: lateral, forward depth, height. */
    private fun vCam(u: Float, v: Float, z: Float, tu: Float, tv: Float) {
        q[qi] = u
        q[qi + 1] = v
        q[qi + 2] = z
        q[qi + 3] = tu
        q[qi + 4] = tv
        qi += 5
    }

    /** Clip the assembled quad to the near plane, project it, and fill it. */
    private fun flush() {
        var n = 0
        for (i in 0 until 4) {
            val a = i * 5
            val b = ((i + 1) % 4) * 5
            val va = q[a + 1]
            val vb = q[b + 1]
            val aIn = va >= NEAR
            if (aIn) {
                System.arraycopy(q, a, clipBuf, n * 5, 5)
                n++
            }
            if (aIn != (vb >= NEAR)) {
                val t = (NEAR - va) / (vb - va)
                for (k in 0 until 5) clipBuf[n * 5 + k] = q[a + k] + (q[b + k] - q[a + k]) * t
                n++
            }
        }
        qi = 0
        if (n < 3) return
        for (i in 0 until n) {
            val o = i * 5
            val v = clipBuf[o + 1]
            val iz = 1f / v
            px[i] = w / 2f + (clipBuf[o] / v) * focalH
            py[i] = horizonY - (clipBuf[o + 2] / v) * scale
            piz[i] = iz
            puiz[i] = clipBuf[o + 3] * iz
            pviz[i] = clipBuf[o + 4] * iz
        }
        for (i in 1 until n - 1) rasterTri(0, i, i + 1)
    }

    private fun rasterTri(a: Int, b: Int, c: Int) {
        val xa = px[a]; val ya = py[a]
        val xb = px[b]; val yb = py[b]
        val xc = px[c]; val yc = py[c]
        val area = (xb - xa) * (yc - ya) - (xc - xa) * (yb - ya)
        if (abs(area) < 1e-7f) return
        val invArea = 1f / area

        val minX = maxOf(0, ceil(minOf(xa, xb, xc)).toInt())
        val maxX = minOf(w - 1, floor(maxOf(xa, xb, xc)).toInt())
        val minY = maxOf(0, ceil(minOf(ya, yb, yc)).toInt())
        val maxY = minOf(h - 1, floor(maxOf(ya, yb, yc)).toInt())
        if (minX > maxX || minY > maxY) return

        val iz0 = piz[a]; val iz1 = piz[b]; val iz2 = piz[c]
        val u0 = puiz[a]; val u1 = puiz[b]; val u2 = puiz[c]
        val v0 = pviz[a]; val v1 = pviz[b]; val v2 = pviz[c]
        val tex = texId
        val sp = sprite
        val fl = flash
        val sh = shade
        val amb = ambient
        val torch = this.torch
        val fogCol = fogColor
        val fogT = fogTable
        val m = mode

        val waSx = (yb - yc) * invArea; val waSy = (xc - xb) * invArea
        val wbSx = (yc - ya) * invArea; val wbSy = (xa - xc) * invArea
        val wcSx = (ya - yb) * invArea; val wcSy = (xb - xa) * invArea

        val fx0 = minX.toFloat()
        val fy0 = minY.toFloat()
        var wa = ((xc - xb) * (fy0 - yb) - (yc - yb) * (fx0 - xb)) * invArea
        var wb = ((xa - xc) * (fy0 - yc) - (ya - yc) * (fx0 - xc)) * invArea
        var wc = ((xb - xa) * (fy0 - ya) - (yb - ya) * (fx0 - xa)) * invArea

        for (y in minY..maxY) {
            var waR = wa
            var wbR = wb
            var wcR = wc
            var idx = y * w + minX
            for (x in minX..maxX) {
                if (waR >= 0f && wbR >= 0f && wcR >= 0f) {
                    val iz = waR * iz0 + wbR * iz1 + wcR * iz2
                    val dist = 1f / iz
                    if (dist < depth[idx] - 1e-4f) {
                        if (m == MODE_SPRITE) {
                            val spx = sp!!
                            val tu = ((waR * u0 + wbR * u1 + wcR * u2) * dist).toInt().coerceIn(0, spx.w - 1)
                            val tv = ((waR * v0 + wbR * v1 + wcR * v2) * dist).toInt().coerceIn(0, spx.h - 1)
                            val texel = spx.px[tv * spx.w + tu]
                            if ((texel ushr 24) != 0) {
                                var color = texel and 0xFFFFFF
                                if (fl > 0.01f) color = mixColor(color, 0xA33B28, fl.coerceIn(0f, 0.8f))
                                val bright = maxOf(torchBrightness(dist, torch), amb)
                                pixels[idx] = fogged(ditherColor(color, bright, x, y), fogCol, fogAt(fogT, dist))
                            }
                        } else {
                            depth[idx] = dist
                            val tu = ((waR * u0 + wbR * u1 + wcR * u2) * dist).toInt()
                            val tv = ((waR * v0 + wbR * v1 + wcR * v2) * dist).toInt()
                            val color = if (m == MODE_FLOOR) Textures.sampleFloor(tex, tu, tv)
                            else Textures.sampleWall(tex, tu, tv)
                            val bright = maxOf(torchBrightness(dist, torch), amb) * sh
                            pixels[idx] = fogged(ditherColor(color, bright, x, y), fogCol, fogAt(fogT, dist))
                        }
                    }
                }
                waR += waSx
                wbR += wbSx
                wcR += wcSx
                idx++
            }
            wa += waSy
            wb += wbSy
            wc += wcSy
        }
    }
}
