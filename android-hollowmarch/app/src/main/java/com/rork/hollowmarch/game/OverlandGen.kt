package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Biome
import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.TerrainMap
import com.rork.hollowmarch.world.World
import com.rork.hollowmarch.world.isSettlement
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The province itself: one huge outdoor map built from the same seed and terrain
 * that made its sites. Same seed, same country, forever. Every site stands here
 * as a landmark with a door; the ways between are open ground, rivers, and fords.
 */
object OverlandGen {

    /** Cells across the province. One cell is a few hundred paces. */
    const val SIZE = 320

    /** The province spans 42 leagues corner to corner, as the chronicle counts. */
    const val LEAGUES_PER_CELL = 42f / (SIZE - 1)

    /** The floor number that means the open ground itself. */
    const val OVERLAND_FLOOR = -1

    fun mapSeed(world: World): Long = world.seed * 7919L + 104729L

    /** Where a site's landmark stands on the province map, in cells. */
    fun landmarkX(site: Site): Int = (site.x * (SIZE - 1)).roundToInt().coerceIn(10, SIZE - 11)
    fun landmarkY(site: Site): Int = (site.y * (SIZE - 1)).roundToInt().coerceIn(10, SIZE - 11)

    /** How the land treats a walker: how fast you go, and the breath it costs. */
    fun pacing(biome: Biome, ford: Boolean): Pair<Float, Float> = when {
        ford -> 0.60f to 2.2f
        biome == Biome.MARSH -> 0.62f to 2.0f
        biome == Biome.HILLS -> 0.80f to 1.6f
        biome == Biome.FOREST -> 0.88f to 1.2f
        biome == Biome.MOOR -> 0.95f to 1.1f
        else -> 1f to 1f
    }

    /** The chronicler's name for a stretch of country. */
    fun biomeLabel(biome: Biome): String = when (biome) {
        Biome.OCEAN -> "the sea"
        Biome.MARSH -> "the marsh"
        Biome.MOOR -> "the moor"
        Biome.DOWNS -> "the downs"
        Biome.FOREST -> "the forest"
        Biome.HILLS -> "the hills"
        Biome.PEAK -> "the peaks"
    }

    /** Eight winds from a bearing, matching the HUD compass: north is up the map. */
    fun windOf(dx: Float, dy: Float): String {
        val deg = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 450.0) % 360.0).toFloat()
        return when {
            deg < 22.5f || deg >= 337.5f -> "N"
            deg < 67.5f -> "NE"
            deg < 112.5f -> "E"
            deg < 157.5f -> "SE"
            deg < 202.5f -> "S"
            deg < 247.5f -> "SW"
            deg < 292.5f -> "W"
            else -> "NW"
        }
    }

    /** The whole province, walkable, landmarked, and forded. */
    fun build(world: World): GameMap {
        val rng = Random(mapSeed(world))
        val size = SIZE
        val terrain = world.terrain
        val map = GameMap(size, size, outdoor = true, title = world.provinceName)

        // The land itself: ground by biome, sea and peak impassable.
        for (y in 0 until size) {
            for (x in 0 until size) {
                val idx = y * size + x
                when (terrain.biomeAt(x / size.toFloat(), y / size.toFloat())) {
                    Biome.OCEAN -> {
                        map.walls[idx] = Textures.WALL_WATER
                        map.floorTex[idx] = Textures.FLOOR_HEATH
                    }
                    Biome.PEAK -> {
                        map.walls[idx] = Textures.WALL_STONE
                        map.floorTex[idx] = Textures.FLOOR_SCREE
                    }
                    Biome.MARSH -> map.floorTex[idx] = Textures.FLOOR_MUD
                    Biome.MOOR -> map.floorTex[idx] = Textures.FLOOR_HEATH
                    Biome.FOREST -> map.floorTex[idx] = Textures.FLOOR_PINE
                    Biome.HILLS -> map.floorTex[idx] = Textures.FLOOR_SCREE
                    Biome.DOWNS -> map.floorTex[idx] = Textures.FLOOR_GRASS
                }
            }
        }

        // Rivers cut the country; every so often a shallow stretch becomes the ford.
        terrain.rivers.forEach { river ->
            river.points.forEachIndexed { i, p ->
                val cx = (p.x * size).toInt().coerceIn(1, size - 2)
                val cy = (p.y * size).toInt().coerceIn(1, size - 2)
                if (i % 6 == 3) {
                    for (dy in -1..1) for (dx in -1..1) {
                        val idx = (cy + dy) * size + (cx + dx)
                        if (map.walls[idx] == Textures.WALL_STONE) continue
                        map.walls[idx] = 0
                        map.floorTex[idx] = Textures.FLOOR_FORD
                    }
                } else {
                    val idx = cy * size + cx
                    if (map.walls[idx] == 0) map.walls[idx] = Textures.WALL_WATER
                }
            }
        }

        // Trees, stones and reeds by the land's own logic — thick in forest, sparse on the downs.
        for (y in 2 until size - 2) {
            for (x in 2 until size - 2) {
                val idx = y * size + x
                if (map.walls[idx] != 0) continue
                val biome = terrain.biomeAt(x / size.toFloat(), y / size.toFloat())
                val density = when (biome) {
                    Biome.FOREST -> 0.05f
                    Biome.MARSH -> 0.035f
                    Biome.HILLS -> 0.02f
                    Biome.MOOR -> 0.012f
                    Biome.DOWNS -> 0.005f
                    else -> 0f
                }
                if (density <= 0f || rng.nextFloat() > density) continue
                val piece = when (biome) {
                    Biome.FOREST ->
                        if (rng.nextInt(8) == 0) Sprites.DEAD_TREE to 2.6f else Sprites.PINE to 2.8f
                    Biome.MARSH -> Sprites.REEDS to 0.9f
                    Biome.MOOR -> when (rng.nextInt(4)) {
                        0 -> Sprites.DEAD_TREE to 2.4f
                        1 -> Sprites.STANDING_STONE to 1.2f
                        else -> Sprites.REEDS to 0.85f
                    }
                    Biome.HILLS ->
                        if (rng.nextInt(3) == 0) Sprites.DEAD_TREE to 2.2f else Sprites.STANDING_STONE to 1.1f
                    else ->
                        if (rng.nextInt(5) == 0) Sprites.GRAVE to 0.6f else Sprites.REEDS to 0.8f
                }
                map.entities += Entity(
                    x = x + rng.nextFloat(),
                    y = y + rng.nextFloat(),
                    spriteId = piece.first,
                    kind = EntityKind.PROP,
                    height = piece.second,
                    name = "scenery"
                )
            }
        }

        // Every place stands on the land as a landmark with its door and its yard.
        world.sites.forEach { site ->
            // The living places are drawn to their stage: yard, walls and gate at scale.
            if (site.isSettlement) {
                drawLandmark(map, site, stageOf(site, site.population))
                return@forEach
            }
            val lx = landmarkX(site)
            val ly = landmarkY(site)
            val cx = lx + 0.5f
            val cy = ly + 0.5f
            // the yard: open ground and a trodden heart
            for (dy in -5..5) for (dx in -5..5) {
                val x = lx + dx
                val y = ly + dy
                if (x !in 1 until size - 1 || y !in 1 until size - 1) continue
                val d2 = dx * dx + dy * dy
                if (d2 <= 25) {
                    val idx = y * size + x
                    map.walls[idx] = 0
                    if (d2 <= 8) map.floorTex[idx] = Textures.FLOOR_ROAD
                }
            }
            when (site.kind) {
                SiteKind.VAULT -> {
                    map.entities += Entity(
                        x = cx, y = cy, spriteId = Sprites.HILLDOOR,
                        kind = EntityKind.PROP, height = 1.7f, name = "the sealed door"
                    )
                    map.entities += Entity(
                        x = cx - 2.2f, y = cy + 1.4f, spriteId = Sprites.STANDING_STONE,
                        kind = EntityKind.PROP, height = 1.5f, name = "ward-stone"
                    )
                    map.entities += Entity(
                        x = cx + 2.2f, y = cy + 1.4f, spriteId = Sprites.STANDING_STONE,
                        kind = EntityKind.PROP, height = 1.5f, name = "ward-stone"
                    )
                    map.portals += Portal(
                        x = cx, y = cy, label = "the sealed door",
                        prompt = "Enter ${site.name}", targetFloor = 0,
                        arrivalIndex = 0, down = true, targetSiteId = site.id
                    )
                }
                SiteKind.BARROW -> {
                    map.entities += Entity(
                        x = cx, y = cy, spriteId = Sprites.CAIRN,
                        kind = EntityKind.PROP, height = 1.4f, name = "cairn"
                    )
                    map.entities += Entity(
                        x = cx - 2.0f, y = cy + 1.8f, spriteId = Sprites.GRAVE,
                        kind = EntityKind.PROP, height = 0.6f, name = "grave"
                    )
                    map.entities += Entity(
                        x = cx + 2.0f, y = cy + 1.2f, spriteId = Sprites.GRAVE,
                        kind = EntityKind.PROP, height = 0.6f, name = "grave"
                    )
                    map.portals += Portal(
                        x = cx, y = cy, label = "the barrow-mouth",
                        prompt = "Enter ${site.name}", targetFloor = 0,
                        arrivalIndex = 0, down = true, targetSiteId = site.id
                    )
                }
                SiteKind.RUIN -> {
                    // broken ribs of whatever the place was
                    for (i in 0 until 5) {
                        val ang = i * 1.9f
                        val px = (lx + cos(ang) * 3f).roundToInt().coerceIn(1, size - 2)
                        val py = (ly + sin(ang) * 3f).roundToInt().coerceIn(1, size - 2)
                        map.walls[py * size + px] = Textures.WALL_RUIN
                    }
                    map.entities += Entity(
                        x = cx, y = cy, spriteId = Sprites.STANDING_STONE,
                        kind = EntityKind.PROP, height = 1.4f, name = "fallen keeper"
                    )
                    map.portals += Portal(
                        x = cx, y = cy + 0.5f, label = "the fallen walls",
                        prompt = "Enter ${site.name}", targetFloor = 0,
                        arrivalIndex = 0, down = true, targetSiteId = site.id
                    )
                }
                SiteKind.CAMP -> {
                    map.entities += Entity(
                        x = cx, y = cy - 1.2f, spriteId = Sprites.WATCHFIRE,
                        kind = EntityKind.PROP, height = 1.0f, name = "watchfire"
                    )
                    map.entities += Entity(
                        x = cx - 2.2f, y = cy + 1.2f, spriteId = Sprites.TENT,
                        kind = EntityKind.PROP, height = 1.2f, name = "tent"
                    )
                    map.entities += Entity(
                        x = cx + 2.2f, y = cy + 1.2f, spriteId = Sprites.TENT,
                        kind = EntityKind.PROP, height = 1.2f, name = "tent"
                    )
                    map.portals += Portal(
                        x = cx, y = cy + 0.5f, label = "the watchfire",
                        prompt = "Enter ${site.name}", targetFloor = 0,
                        arrivalIndex = 0, down = false, targetSiteId = site.id
                    )
                }
                else -> {
                    // the shrine: a ring of stones around its door
                    repeat(8) { i ->
                        val ang = i * 6.28318f / 8
                        map.entities += Entity(
                            x = cx + cos(ang) * 3.2f, y = cy + sin(ang) * 3.2f,
                            spriteId = Sprites.STANDING_STONE,
                            kind = EntityKind.PROP, height = 1.5f, name = "standing stone"
                        )
                    }
                    map.portals += Portal(
                        x = cx, y = cy, label = "the stone circle",
                        prompt = "Enter ${site.name}", targetFloor = 0,
                        arrivalIndex = 0, down = false, targetSiteId = site.id
                    )
                }
            }

            // where walking out puts you: open ground south of the landmark
            var spot: Pair<Float, Float>? = null
            for (dy in 7..16) {
                val y = ly + dy
                if (y >= size - 2) break
                if (map.walls[y * size + lx] == 0 && map.walls[(y + 1) * size + lx] == 0) {
                    spot = Pair(cx, y + 0.5f)
                    break
                }
            }
            val entry = spot ?: Pair(cx, (ly + 8).toFloat())
            // a trodden path from the yard to the standing spot
            val entryCell = entry.second.toInt() - ly
            for (dy in 5..entryCell) {
                val y = ly + dy
                if (y !in 1 until size - 1) continue
                for (dx in -1..1) {
                    val idx = y * size + lx + dx
                    if (map.walls[idx] == Textures.WALL_STONE) continue
                    map.walls[idx] = 0
                }
            }
            map.walls[entry.second.toInt() * size + lx] = 0
            map.entrySpots[site.id] = entry
        }

        // every door stands open, whatever was built beside it
        map.portals.forEach { portal ->
            val px = portal.x.toInt()
            val py = portal.y.toInt()
            for (dy in -1..1) for (dx in -1..1) {
                val x = px + dx
                val y = py + dy
                if (x in 1 until size - 1 && y in 1 until size - 1) map.walls[y * size + x] = 0
            }
        }

        // No place stands across an uncrossable cut: roads are beaten where the land refuses.
        val vault = world.site(world.vaultSiteId)
        val vaultEntry = map.entrySpots.getValue(vault.id)
        var guard = 0
        while (guard++ < 4) {
            val reached = floodFrom(map, vaultEntry.first.toInt(), vaultEntry.second.toInt())
            val stranded = map.entrySpots.filterValues { spot ->
                !reached[spot.second.toInt() * size + spot.first.toInt()]
            }
            if (stranded.isEmpty()) break
            stranded.values.forEach { spot -> carveWalk(map, spot, vaultEntry) }
        }

        // nothing stands in a doorway or on a standing spot
        val keepClear = map.portals.map { Pair(it.x, it.y) } + map.entrySpots.values
        map.entities.removeAll { prop ->
            keepClear.any { MapFactory.distance(prop.x, prop.y, it.first, it.second) < 1.8f }
        }

        // The land itself rises: hills and hollows from the world's own seed, with
        // level pads kept under every road, river and living place.
        Landform.buildHeights(map, mapSeed(world), ampAt = { x, y ->
            when (terrain.biomeAt(x / size.toFloat(), y / size.toFloat())) {
                Biome.HILLS, Biome.PEAK -> 1.6f
                Biome.FOREST, Biome.MOOR -> 0.9f
                Biome.DOWNS -> 0.7f
                Biome.MARSH -> 0.4f
                Biome.OCEAN -> 0.25f
            }
        })

        map.spawnX = vaultEntry.first
        map.spawnY = vaultEntry.second
        map.spawnAngle = -1.5708f
        return map
    }

    /** Every cell reachable on foot from a standing spot. */
    private fun floodFrom(map: GameMap, sx: Int, sy: Int): BooleanArray {
        val seen = BooleanArray(map.width * map.height)
        val queue = ArrayDeque<Pair<Int, Int>>()
        fun push(x: Int, y: Int) {
            if (x !in 0 until map.width || y !in 0 until map.height) return
            val idx = y * map.width + x
            if (seen[idx] || map.walls[idx] != 0) return
            seen[idx] = true
            queue += Pair(x, y)
        }
        push(sx, sy)
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            push(x + 1, y)
            push(x - 1, y)
            push(x, y + 1)
            push(x, y - 1)
        }
        return seen
    }

    /** An old road beaten straight across whatever refused the walker. */
    private fun carveWalk(map: GameMap, from: Pair<Float, Float>, to: Pair<Float, Float>) {
        var x = from.first.toInt()
        val y0 = from.second.toInt()
        val xEnd = to.first.toInt()
        val yEnd = to.second.toInt()
        while (x != xEnd) {
            clearWalk(map, x, y0)
            x += if (xEnd > x) 1 else -1
        }
        var y = y0
        while (y != yEnd) {
            clearWalk(map, xEnd, y)
            y += if (yEnd > y) 1 else -1
        }
        clearWalk(map, xEnd, yEnd)
    }

    private fun clearWalk(map: GameMap, x: Int, y: Int) {
        if (x in 1 until map.width - 1 && y in 1 until map.height - 1) {
            map.walls[y * map.width + x] = 0
            map.floorTex[y * map.width + x] = Textures.FLOOR_ROAD
        }
    }

    /**
     * The landmark of a living settlement, sized to its stage: the yard's reach,
     * the wall ring, the south gate, and the door you enter by.
     */
    fun drawLandmark(
        map: GameMap, site: Site, stage: SettlementStage,
        atX: Int = landmarkX(site), atY: Int = landmarkY(site)
    ) {
        val size = map.width
        val lx = atX
        val ly = atY
        val cx = lx + 0.5f
        val cy = ly + 0.5f
        // the yard: open ground and a trodden heart
        val r = stage.yardRadius
        for (dy in -r..r) for (dx in -r..r) {
            val x = lx + dx
            val y = ly + dy
            if (x !in 1 until size - 1 || y !in 1 until size - 1) continue
            val d2 = dx * dx + dy * dy
            if (d2 <= r * r) {
                map.walls[y * size + x] = 0
                if (d2 * 2 <= r * r) map.floorTex[y * size + x] = Textures.FLOOR_ROAD
            }
        }
        val walled = stage.wallRadius > 0f
        val edgeDy = if (walled) stage.wallRadius.roundToInt() else r
        if (walled) {
            // stone rings stand two courses high; timber palisades keep low
            val stoneRing = stage.ordinal >= SettlementStage.CITY.ordinal
            val wallTex = if (stoneRing) Textures.WALL_STONE else Textures.WALL_TIMBER
            val hts = if (stoneRing) ensureHeights(map) else null
            val wr = stage.wallRadius
            for (a in 0 until 360 step 2) {
                val ang = a * 6.28318f / 360f
                if (angDist(ang, SOUTH) < 0.32f) continue
                val px = (lx + cos(ang) * wr).roundToInt()
                val py = (ly + sin(ang) * wr).roundToInt()
                if (px in 1 until size - 1 && py in 1 until size - 1) {
                    val idx = py * size + px
                    map.walls[idx] = wallTex
                    hts?.set(idx, 2f)
                }
            }
            // the gate cut wide, facing the south approach
            val gy = ly + edgeDy
            if (gy in 1 until size - 1) {
                for (dx in -1..1) {
                    map.walls[gy * size + lx + dx] = 0
                    map.floorTex[gy * size + lx + dx] = Textures.FLOOR_ROAD
                }
                // the wardens' towers flank the gate, a keep's own height
                for (dx in intArrayOf(-2, 2)) {
                    val tx = lx + dx
                    if (tx in 1 until size - 1) {
                        val idx = gy * size + tx
                        map.walls[idx] = Textures.WALL_GATE
                        ensureHeights(map)[idx] = 3f
                    }
                }
            }
        }
        // smoke and shelter, by the stage's hand
        val rng = Random(site.id * 1000003L + stage.ordinal * 977L + 7L)
        when (stage) {
            SettlementStage.VILLAGE -> {
                map.entities += Entity(
                    x = cx, y = cy, spriteId = Sprites.STANDING_STONE,
                    kind = EntityKind.PROP, height = 1.1f, name = "the well"
                )
                map.entities += Entity(
                    x = cx - 1.8f, y = cy + 0.8f, spriteId = Sprites.TENT,
                    kind = EntityKind.PROP, height = 1.15f, name = "cot"
                )
                map.entities += Entity(
                    x = cx + 1.9f, y = cy + 0.9f, spriteId = Sprites.TENT,
                    kind = EntityKind.PROP, height = 1.2f, name = "longhouse"
                )
                map.entities += Entity(
                    x = cx - 0.4f, y = cy - 1.9f, spriteId = Sprites.TENT,
                    kind = EntityKind.PROP, height = 1.1f, name = "cot"
                )
                map.entities += Entity(
                    x = cx + 0.3f, y = cy + 2.2f, spriteId = Sprites.BRAZIER,
                    kind = EntityKind.PROP, height = 0.85f, name = "wellfire"
                )
                // a hut or two of honest timber at the yard's edge
                drawLandmarkBuildings(
                    map, site, stage, lx, ly, edgeDy, rng,
                    hall = null, houses = 1 + rng.nextInt(2)
                )
            }
            SettlementStage.TOWN -> {
                drawLandmarkBuildings(
                    map, site, stage, lx, ly, edgeDy, rng,
                    hall = "hall", houses = 2 + rng.nextInt(2)
                )
                map.entities += Entity(
                    x = cx + 0.2f, y = cy + 2.2f, spriteId = Sprites.BRAZIER,
                    kind = EntityKind.PROP, height = 0.85f, name = "gatefire"
                )
            }
            SettlementStage.CAMP -> {
                map.entities += Entity(
                    x = cx - 1.4f, y = cy + 0.6f, spriteId = Sprites.TENT,
                    kind = EntityKind.PROP, height = 1.25f, name = "hall"
                )
                map.entities += Entity(
                    x = cx + 1.6f, y = cy - 1.0f, spriteId = Sprites.TENT,
                    kind = EntityKind.PROP, height = 1.1f, name = "longhouse"
                )
                map.entities += Entity(
                    x = cx + 2.3f, y = cy + 1.7f, spriteId = Sprites.TENT,
                    kind = EntityKind.PROP, height = 1.2f, name = "rowhouse"
                )
                map.entities += Entity(
                    x = cx + 0.2f, y = cy + 2.2f, spriteId = Sprites.BRAZIER,
                    kind = EntityKind.PROP, height = 0.85f, name = "gatefire"
                )
                map.entities += Entity(
                    x = cx - 2.5f, y = cy - 0.7f, spriteId = Sprites.BRAZIER,
                    kind = EntityKind.PROP, height = 0.85f, name = "streetfire"
                )
            }
            else -> {
                // the walled great places: keep, shrine, and houses of proper height
                drawLandmarkBuildings(
                    map, site, stage, lx, ly, edgeDy, rng,
                    hall = "keep", houses = 3 + rng.nextInt(3), temple = true
                )
                map.entities += Entity(
                    x = cx - 2.5f, y = cy - 0.7f, spriteId = Sprites.BRAZIER,
                    kind = EntityKind.PROP, height = 0.85f, name = "streetfire"
                )
                map.entities += Entity(
                    x = cx + 0.2f, y = cy + 2.2f, spriteId = Sprites.BRAZIER,
                    kind = EntityKind.PROP, height = 0.85f, name = "gatefire"
                )
            }
        }
        // the door: the gate for the walled, the yard's edge for the open steads
        val portal = Portal(
            x = cx, y = ly + edgeDy + 0.6f,
            label = if (walled) "the gates" else "the gate",
            prompt = "Enter ${site.name}", targetFloor = 0,
            arrivalIndex = 0, down = false, targetSiteId = site.id
        )
        map.portals += portal
        map.entities.removeAll { prop ->
            prop.kind == EntityKind.PROP &&
                MapFactory.distance(prop.x, prop.y, portal.x, portal.y) < 1.8f
        }
        // where walking out puts you: open ground south of the walls
        var spot: Pair<Float, Float>? = null
        val startDy = edgeDy + 2
        for (dy in startDy..(startDy + 10)) {
            val y = ly + dy
            if (y >= size - 2) break
            if (map.walls[y * size + lx] == 0 && map.walls[(y + 1) * size + lx] == 0) {
                spot = Pair(cx, y + 0.5f)
                break
            }
        }
        val entry = spot ?: Pair(cx, (ly + startDy).coerceAtMost(size - 3).toFloat())
        val entryCell = entry.second.toInt() - ly
        for (dy in edgeDy..entryCell) {
            val y = ly + dy
            if (y !in 1 until size - 1) continue
            for (dx in -1..1) {
                val idx = y * size + lx + dx
                if (map.walls[idx] == Textures.WALL_STONE) continue
                map.walls[idx] = 0
            }
        }
        val entryRow = entry.second.toInt()
        if (entryRow in 1 until size - 1) map.walls[entryRow * size + lx] = 0
        map.entrySpots[site.id] = entry
        map.entities.removeAll { prop ->
            prop.kind == EntityKind.PROP &&
                MapFactory.distance(prop.x, prop.y, entry.first, entry.second) < 1.8f
        }
    }

    /** The masonry's heights, present and honest: walls unwritten stand one course. */
    private fun ensureHeights(map: GameMap): FloatArray =
        map.wallHeights ?: FloatArray(map.width * map.height).also {
            it.fill(1f)
            map.wallHeights = it
        }

    /**
     * Real masonry on the province: the landmark's own houses drawn as footprints —
     * walls of proper height, a door toward the heart, trodden floors the land keeps
     * level. The great places raise a keep and a shrine; the village makes do with
     * a hut or two.
     */
    private fun drawLandmarkBuildings(
        map: GameMap, site: Site, stage: SettlementStage,
        lx: Int, ly: Int, edgeDy: Int, rng: Random,
        hall: String?, houses: Int, temple: Boolean = false
    ) {
        val placed = mutableListOf<BuildingFootprint>()
        val wallTex = if (stage.ordinal >= SettlementStage.CITY.ordinal) {
            Textures.WALL_STONE
        } else {
            Textures.WALL_TIMBER
        }
        if (hall != null) {
            val (w, h) = if (hall == "keep") 4 to 3 else 3 to 3
            placeLandmarkBuilding(
                map, rng, placed, lx, ly, edgeDy, w, h, wallTex, hall,
                "the ${site.name} ${if (hall == "keep") "keep" else "hall"}", north = true
            )
        }
        if (temple) {
            placeLandmarkBuilding(
                map, rng, placed, lx, ly, edgeDy, 3, 2, wallTex,
                "temple", "the shrine of ${site.name}"
            )
        }
        repeat(houses) {
            placeLandmarkBuilding(
                map, rng, placed, lx, ly, edgeDy, 2 + rng.nextInt(2), 2, wallTex,
                "house", "cot"
            )
        }
        // whatever stood where the masonry now stands is swept away
        map.entities.removeAll { prop ->
            prop.kind == EntityKind.PROP && placed.any { b ->
                val px = prop.x.toInt()
                val py = prop.y.toInt()
                px >= b.x && px < b.x + b.w && py >= b.y && py < b.y + b.h
            }
        }
    }

    /**
     * Cut one house into the yard: walls of its kind's height, a door facing the
     * heart, and nothing builts over the heart, the gate lane, or standing stone.
     */
    private fun placeLandmarkBuilding(
        map: GameMap, rng: Random, placed: MutableList<BuildingFootprint>,
        lx: Int, ly: Int, edgeDy: Int, w: Int, h: Int,
        wallTex: Int, kind: String, name: String, north: Boolean = false
    ): BuildingFootprint? {
        val size = map.width
        repeat(60) { attempt ->
            val x: Int
            val y: Int
            if (north && attempt == 0) {
                // the great house stands fixed north of the heart
                x = (lx - w / 2).coerceIn(1, size - w - 1)
                y = (ly - h - 1).coerceIn(1, size - h - 1)
            } else {
                val ang = rng.nextFloat() * 6.28318f
                val rad = 2.6f + rng.nextFloat() * ((edgeDy - 1).coerceAtLeast(2)).toFloat()
                val bx = lx + (cos(ang) * rad).roundToInt()
                val by = ly + (sin(ang) * rad).roundToInt()
                x = (bx - w / 2).coerceIn(1, size - w - 1)
                y = (by - h / 2).coerceIn(1, size - h - 1)
            }
            val clash = placed.any { p ->
                x - 1 < p.x + p.w && x + w + 1 > p.x && y - 1 < p.y + p.h && y + h + 1 > p.y
            }
            if (clash) return@repeat
            var ok = true
            for (yy in y until y + h) {
                for (xx in x until x + w) {
                    val dx = xx - lx
                    val dy = yy - ly
                    if (!north && dx * dx + dy * dy <= 4) { ok = false; break }  // the heart stays clear
                    if (abs(dx) <= 1 && dy >= 0) { ok = false; break }           // the lane from gate to heart
                    if (abs(dx) <= 2 && dy >= edgeDy - 1) { ok = false; break }  // the apron before the towers
                    if (map.walls[yy * size + xx] != 0) { ok = false; break }    // nothing builts on stone or water
                }
                if (!ok) break
            }
            if (!ok) return@repeat
            // the door faces the heart
            val faceX = lx - (x + w / 2)
            val faceY = ly - (y + h / 2)
            val doorX: Int
            val doorY: Int
            if (abs(faceX) > abs(faceY)) {
                doorX = if (faceX > 0) x + w - 1 else x
                doorY = y + h / 2
            } else {
                doorY = if (faceY > 0) y + h - 1 else y
                doorX = x + w / 2
            }
            val building = BuildingFootprint(x, y, w, h, doorX, doorY, kind, name)
            drawLandmarkBuilding(map, building, wallTex)
            placed += building
            return building
        }
        return null
    }

    /** How high a landmark house's masonry stands, in wall-heights, by what it is. */
    private fun tallOf(kind: String): Float = when (kind) {
        "keep", "citadel" -> 3f
        "temple", "hall" -> 2f
        else -> 1f
    }

    /** The masonry itself: walls of the kind's height, a trodden floor, the door open. */
    private fun drawLandmarkBuilding(map: GameMap, b: BuildingFootprint, wallTex: Int) {
        val tall = tallOf(b.kind)
        val hts = if (tall > 1f) ensureHeights(map) else null
        for (yy in b.y until b.y + b.h) {
            for (xx in b.x until b.x + b.w) {
                val perimeter = xx == b.x || xx == b.x + b.w - 1 || yy == b.y || yy == b.y + b.h - 1
                val idx = yy * map.width + xx
                map.walls[idx] = if (perimeter) wallTex else 0
                if (perimeter) hts?.set(idx, tall)
                map.floorTex[idx] = Textures.FLOOR_ROAD
            }
        }
        map.walls[b.doorY * map.width + b.doorX] = 0
        map.roofs += Roof(
            b.x, b.y, b.x + b.w, b.y + b.h, tall, tall + 0.5f,
            b.w >= b.h, wallTex, Textures.WALL_ROOF
        )
        map.buildings += b
    }

    /**
     * A place its folk raised to a new stage: the old landmark is swept from the
     * province and redrawn at its new size, in place, from the same seed.
     */
    fun redrawLandmark(map: GameMap, world: World, site: Site, folk: Int) {
        val size = map.width
        val lx = landmarkX(site)
        val ly = landmarkY(site)
        val heartX = lx + 0.5f
        val heartY = ly + 0.5f
        val reach = 13
        map.entities.removeAll { prop ->
            prop.kind == EntityKind.PROP &&
                MapFactory.distance(prop.x, prop.y, heartX, heartY) <= reach
        }
        for (dy in -reach..reach) for (dx in -reach..reach) {
            val x = lx + dx
            val y = ly + dy
            if (x in 1 until size - 1 && y in 1 until size - 1) {
                paintNatural(map, world.terrain, x, y)
            }
        }
        // old entry paths ran farther south: sweep the lane too
        for (dy in reach + 1..reach + 8) {
            val y = ly + dy
            if (y !in 1 until size - 1) continue
            for (dx in -1..1) paintNatural(map, world.terrain, lx + dx, y)
        }
        map.buildings.removeAll { b ->
            MapFactory.distance(b.x + b.w * 0.5f, b.y + b.h * 0.5f, heartX, heartY) <= reach + 2
        }
        map.roofs.removeAll { rf ->
            MapFactory.distance(
                (rf.x0 + rf.x1) * 0.5f, (rf.y0 + rf.y1) * 0.5f, heartX, heartY
            ) <= reach + 2
        }
        map.portals.removeAll { it.targetSiteId == site.id }
        map.entrySpots.remove(site.id)
        drawLandmark(map, site, stageOf(site, folk))
    }

    /** The natural cell, as the country itself made it. */
    private fun paintNatural(map: GameMap, terrain: TerrainMap, x: Int, y: Int) {
        val size = map.width
        val idx = y * size + x
        when (terrain.biomeAt(x / size.toFloat(), y / size.toFloat())) {
            Biome.OCEAN -> {
                map.walls[idx] = Textures.WALL_WATER
                map.floorTex[idx] = Textures.FLOOR_HEATH
            }
            Biome.PEAK -> {
                map.walls[idx] = Textures.WALL_STONE
                map.floorTex[idx] = Textures.FLOOR_SCREE
            }
            Biome.MARSH -> {
                map.walls[idx] = 0
                map.floorTex[idx] = Textures.FLOOR_MUD
            }
            Biome.MOOR -> {
                map.walls[idx] = 0
                map.floorTex[idx] = Textures.FLOOR_HEATH
            }
            Biome.FOREST -> {
                map.walls[idx] = 0
                map.floorTex[idx] = Textures.FLOOR_PINE
            }
            Biome.HILLS -> {
                map.walls[idx] = 0
                map.floorTex[idx] = Textures.FLOOR_SCREE
            }
            Biome.DOWNS -> {
                map.walls[idx] = 0
                map.floorTex[idx] = Textures.FLOOR_GRASS
            }
        }
    }

    private const val SOUTH = 1.5708f

    /** Distance between two angles, wrapped to a half turn. */
    private fun angDist(a: Float, b: Float): Float {
        val d = abs(a - b) % 6.28318f
        return if (d > 3.14159f) 6.28318f - d else d
    }
}
