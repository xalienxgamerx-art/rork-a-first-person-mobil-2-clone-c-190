package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Biome
import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.World
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The living places, built to their stage: villages of timber around a well,
 * towns behind a palisade with a market square, cities behind stone with a
 * keep, and a capital with its citadel and avenues. Every building keeps a
 * real footprint and a door — same seed, same town, forever.
 */
object SettlementGen {

    private val HOUSE_NAMES = listOf(
        "timber house", "thatched cot", "mud-walled cot", "longhouse", "turf-roofed steading"
    )

    /** The map of a living place at [stage]: outdoor, walkable, and its own shape. */
    fun map(
        world: World,
        site: Site,
        stage: SettlementStage,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        day: Int
    ): GameMap {
        val seed = SiteGen.mapSeed(world, site, 0) + stage.ordinal * 977L
        val rng = Random(seed)
        val size = stage.interiorSpan
        val map = GameMap(size, size, outdoor = true, title = site.name)
        map.walls.fill(0)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val n = Textures.valueNoise(x * 0.11f, y * 0.11f, 21)
                map.floorTex[y * size + x] = if (n < 0.34f) Textures.FLOOR_MUD else Textures.FLOOR_GRASS
            }
        }
        for (i in 0 until size) {
            map.walls[i] = Textures.WALL_RUIN
            map.walls[(size - 1) * size + i] = Textures.WALL_RUIN
            map.walls[i * size] = Textures.WALL_RUIN
            map.walls[i * size + size - 1] = Textures.WALL_RUIN
        }
        val cx = size / 2
        val cy = size / 2
        val cultureId = SiteGen.cultureId(world, site)
        val level = 1 + day / 25

        when (stage) {
            SettlementStage.VILLAGE -> village(map, rng, site, cx, cy, size, cultureId, geography, level)
            SettlementStage.TOWN -> town(map, rng, site, cx, cy, size, cultureId, geography, level)
            SettlementStage.CITY -> walledCity(
                map, rng, site, cx, cy, size, cultureId, geography, level, citadel = false
            )
            SettlementStage.CAPITAL -> walledCity(
                map, rng, site, cx, cy, size, cultureId, geography, level, citadel = true
            )
            SettlementStage.CAMP -> {} // warband camps keep the old yard, in SiteGen.camp
        }

        // the way in: south of everything, on trodden ground, facing the heart
        val spawnY = if (stage == SettlementStage.VILLAGE) {
            (size - 5)
        } else {
            cy + ringOf(stage) + 3
        }
        for (y in spawnY downTo cy) {
            for (dx in -1..1) {
                val idx = y * size + cx + dx
                if (map.walls[idx] == Textures.WALL_RUIN) continue
                map.walls[idx] = 0
                map.floorTex[idx] = Textures.FLOOR_ROAD
            }
        }
        map.spawnX = cx + 0.5f
        map.spawnY = spawnY + 0.5f
        map.spawnAngle = -1.5708f
        wireDoors(map, site)
        placeResidents(map, rng, world, site, cx, cy)
        map.entities.removeAll { map.isWall(it.x, it.y) }

        // the land itself rolls here too, as the province does: hills in the open
        // yard, and every wall, street and building pad kept level for the masonry
        val amp = when (world.terrain.biomeAt(site.x, site.y)) {
            Biome.HILLS, Biome.PEAK -> 1.6f
            Biome.FOREST, Biome.MOOR -> 0.9f
            Biome.DOWNS -> 0.7f
            Biome.MARSH -> 0.4f
            Biome.OCEAN -> 0.25f
        }
        Landform.buildHeights(
            map, seed, { _, _ -> amp },
            padTexes = setOf(
                Textures.FLOOR_ROAD, Textures.FLOOR_FORD,
                Textures.FLOOR_MUD, Textures.FLOOR_FLAG
            )
        )
        return map
    }

    /**
     * The named souls of the place: a keeper to every building, and a homeless
     * few who keep to the heart. They walk by day and go home through their
     * doors at night.
     */
    internal fun placeResidents(
        map: GameMap,
        rng: Random,
        world: World,
        site: Site,
        cx: Int,
        cy: Int,
        force: Boolean = false
    ) {
        if ((!force && site.population <= 0) || map.buildings.isEmpty()) return
        val cultureId = SiteGen.cultureId(world, site)
        val pool = world.figures
            .filter { it.cultureId == cultureId }
            .map { it.name }
            .ifEmpty { world.figures.map { it.name } }
            .shuffled(rng)
        if (pool.isEmpty()) return
        var next = 0
        fun name(): String = pool[next++ % pool.size]
        // the trades are dealt from the seed, the place and the land — never luck
        val biome = world.terrain.biomeAt(site.x, site.y)
        val tavernIdx = map.buildings.indexOfFirst { it.kind == "tavern" }
        val templeIdx = map.buildings.indexOfFirst { it.kind == "temple" }
        val keepIdx = map.buildings.indexOfFirst { it.kind == "keep" || it.kind == "citadel" }
        val guildIdx = map.buildings.indexOfFirst { it.kind == "guild hall" }
        fun workAt(role: String, ownIndex: Int): Int = when (ROLES.byName(role)?.workplace) {
            Workplace.TAVERN -> if (ownIndex == tavernIdx) ownIndex else tavernIdx
            Workplace.TEMPLE -> if (ownIndex == templeIdx) ownIndex else templeIdx
            Workplace.KEEP -> if (ownIndex == keepIdx) ownIndex else keepIdx
            Workplace.GUILD -> if (ownIndex == guildIdx) ownIndex else guildIdx
            Workplace.HOME -> ownIndex
            else -> -1
        }
        // a keeper to every building, a pace back from its own threshold,
        // so the door stays within the use hand
        map.buildings.forEachIndexed { i, b ->
            val keeper = name()
            val role = RoleBook.keeperRole(world.seed, site, biome, b.kind, i)
            map.buildings[i] = b.copy(keeper = keeper)
            val spot = map.arrivalSpots.getOrNull(i) ?: return@forEachIndexed
            val backX = spot.first + (spot.first - (b.doorX + 0.5f))
            val backY = spot.second + (spot.second - (b.doorY + 0.5f))
            val open = !map.isWall(backX, backY)
            map.entities += Entity(
                x = if (open) backX else spot.first,
                y = if (open) backY else spot.second,
                spriteId = Sprites.PILGRIM,
                kind = EntityKind.ENEMY, height = 1.0f,
                name = keeper, resident = true, homeBuilding = i,
                role = role, workBuilding = workAt(role, i),
                personality = PersonalityBook.deal(
                    PersonalityBook.key(world.seed, "${site.id}:k$i"), role
                )
            )
        }
        // the homeless few, about the well and the square
        var homeless = 0
        repeat(1 + rng.nextInt(2)) {
            val ang = rng.nextFloat() * 6.28f
            val r = 1.5f + rng.nextFloat() * 4f
            val bx = cx + 0.5f + cos(ang) * r
            val by = cy + 0.5f + sin(ang) * r
            if (!map.isWall(bx, by)) {
                val hIndex = homeless++
                val hRole = RoleBook.homelessRole(world.seed, site, hIndex)
                map.entities += Entity(
                    x = bx, y = by, spriteId = Sprites.PILGRIM,
                    kind = EntityKind.ENEMY, height = 0.95f,
                    name = name(), resident = true, homeBuilding = -1,
                    role = hRole, workBuilding = -1,
                    personality = PersonalityBook.deal(
                        PersonalityBook.key(world.seed, "${site.id}:h$hIndex"), hRole
                    )
                )
            }
        }
    }

    /**
     * Every building's door becomes a way in: a portal into an interior of exactly
     * this footprint, and a standing spot just outside for the way back out.
     */
    internal fun wireDoors(map: GameMap, site: Site) {
        map.buildings.forEachIndexed { i, b ->
            val dx = b.doorX - (b.x + b.w / 2)
            val dy = b.doorY - (b.y + b.h / 2)
            val outX = (b.doorX + if (dx > 0) 1 else if (dx < 0) -1 else 0)
                .coerceIn(1, map.width - 2)
            val outY = (b.doorY + if (dy > 0) 1 else if (dy < 0) -1 else 0)
                .coerceIn(1, map.height - 2)
            map.walls[outY * map.width + outX] = 0
            map.arrivalSpots += Pair(outX + 0.5f, outY + 0.5f)
            map.portals += Portal(
                x = b.doorX + 0.5f, y = b.doorY + 0.5f,
                label = "the door of ${b.name}",
                prompt = "Enter ${b.name}",
                targetFloor = SiteGen.BUILDING_FLOOR_BASE + i,
                arrivalIndex = 0, down = false, targetSiteId = site.id, door = true
            )
        }
    }

    /** A footprint tent pitched at a fixed spot, its door facing the heart. */
    internal fun placeTentAt(
        map: GameMap,
        placed: MutableList<BuildingFootprint>,
        cx: Int,
        cy: Int,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        name: String
    ): BuildingFootprint {
        val tent = withDoor(x, y, w, h, cx, cy, "tent", name)
        drawBuilding(map, tent, Textures.WALL_TIMBER, Textures.FLOOR_MUD)
        placed += tent
        return tent
    }

    /** Find a clear spot in the yard band and pitch a tent there. */
    internal fun tryTent(
        map: GameMap,
        rng: Random,
        placed: MutableList<BuildingFootprint>,
        cx: Int,
        cy: Int,
        size: Int,
        w: Int,
        h: Int,
        minR: Int,
        maxR: Int,
        name: String = "tent"
    ): BuildingFootprint? = tryPlace(
        map, rng, placed, cx, cy, size,
        minR, maxR.coerceAtLeast(minR + 1),
        w, h, Textures.WALL_TIMBER, Textures.FLOOR_MUD, "tent", name, avoid = emptyList()
    )

    /** How far the settlement's own wall ring stands from its heart, in cells. */
    private fun ringOf(stage: SettlementStage): Int = when (stage) {
        SettlementStage.TOWN -> 12
        SettlementStage.CITY -> 14
        SettlementStage.CAPITAL -> 16
        else -> 0
    }

    // ------------------------------------------------------------------ village

    /** Timber houses ringed around a well, field plots at the edge, no walls. */
    private fun village(
        map: GameMap,
        rng: Random,
        site: Site,
        cx: Int,
        cy: Int,
        size: Int,
        cultureId: Int,
        geography: MaterialGeography?,
        level: Int
    ) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                if (dx * dx + dy * dy <= 5) {
                    map.floorTex[(cy + dy) * size + (cx + dx)] = Textures.FLOOR_ROAD
                }
            }
        }
        map.entities += Entity(
            x = cx + 0.5f, y = cy + 0.5f, spriteId = Sprites.STANDING_STONE,
            kind = EntityKind.PROP, height = 1.1f, name = "the well"
        )
        brazier(map, cx + 1.8f, cy + 1.8f, "the well's fire")
        val placed = mutableListOf<BuildingFootprint>()
        // the south approach stays clear, as in every living place
        val avoid = listOf(intRect(cx - 1, cy, cx + 1, size - 2))
        // the village's own works, whatever the years gave it: a tavern, a shrine-house
        namedBuildings(
            map, rng, placed, site, cx, cy, size,
            avoid = avoid, minR = 5, maxR = 9,
            wallTex = Textures.WALL_TIMBER, floorTex = Textures.FLOOR_MUD,
            sizes = listOf(4 to 3)
        )
        repeat(5 + rng.nextInt(3)) {
            val w = 3 + rng.nextInt(2)
            val h = 3 + rng.nextInt(2)
            tryPlace(
                map, rng, placed, cx, cy, size, 5, 10, w, h,
                Textures.WALL_TIMBER, Textures.FLOOR_MUD, "house",
                HOUSE_NAMES[rng.nextInt(HOUSE_NAMES.size)], avoid
            )?.let { placed += it }
        }
        // field plots: tilled earth at the village's edge
        repeat(3 + rng.nextInt(3)) {
            val fx = 3 + rng.nextInt(size - 12)
            val fy = 3 + rng.nextInt(size - 12)
            val fw = 3 + rng.nextInt(3)
            val fh = 2 + rng.nextInt(2)
            for (y in fy until (fy + fh).coerceAtMost(size - 2)) {
                for (x in fx until (fx + fw).coerceAtMost(size - 2)) {
                    if (map.walls[y * size + x] == 0) {
                        map.floorTex[y * size + x] = Textures.FLOOR_MUD
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ town

    /** A palisade, a cross of streets, a market square, and the town's own works. */
    private fun town(
        map: GameMap,
        rng: Random,
        site: Site,
        cx: Int,
        cy: Int,
        size: Int,
        cultureId: Int,
        geography: MaterialGeography?,
        level: Int
    ) {
        val ring = ringOf(SettlementStage.TOWN)
        wallRing(map, cx, cy, size, ring, Textures.WALL_TIMBER)
        carveStreets(map, cx, cy, size, ring, halfWidth = 1)
        // the market square at the crossing
        for (dy in -3..3) {
            for (dx in -3..3) {
                map.walls[(cy + dy) * size + (cx + dx)] = 0
                map.floorTex[(cy + dy) * size + (cx + dx)] = Textures.FLOOR_FLAG
            }
        }
        marketStalls(map, rng, cx, cy, level, cultureId, geography)
        brazier(map, cx + 0.5f, cy + 0.5f, "the square's fire")
        brazier(map, cx + 0.5f, cy + ring - 1.5f, "gatefire")
        val avoid = streetRects(cx, cy, ring, halfWidth = 1) + plazaRect(cx, cy, 3)
        val placed = mutableListOf<BuildingFootprint>()
        namedBuildings(
            map, rng, placed, site, cx, cy, size, avoid, 5, 9,
            Textures.WALL_TIMBER, Textures.FLOOR_FLAG, listOf(4 to 3, 5 to 4)
        )
        repeat(6 + rng.nextInt(3)) {
            tryPlace(
                map, rng, placed, cx, cy, size, 5, 10, 3 + rng.nextInt(2), 3 + rng.nextInt(2),
                Textures.WALL_TIMBER, Textures.FLOOR_MUD, "house",
                HOUSE_NAMES[rng.nextInt(HOUSE_NAMES.size)], avoid
            )?.let { placed += it }
        }
    }

    // ------------------------------------------------------------------ city & capital

    /** Stone walls, districts, and a keep — or, for a capital, a citadel and avenues. */
    private fun walledCity(
        map: GameMap,
        rng: Random,
        site: Site,
        cx: Int,
        cy: Int,
        size: Int,
        cultureId: Int,
        geography: MaterialGeography?,
        level: Int,
        citadel: Boolean
    ) {
        val stage = if (citadel) SettlementStage.CAPITAL else SettlementStage.CITY
        val ring = ringOf(stage)
        val half = if (citadel) 2 else 1
        wallRing(map, cx, cy, size, ring, Textures.WALL_STONE, height = 2f)
        carveStreets(map, cx, cy, size, ring, halfWidth = half)
        // the grand plaza at the crossing
        val plaza = if (citadel) 4 else 3
        for (dy in -plaza..plaza) {
            for (dx in -plaza..plaza) {
                map.walls[(cy + dy) * size + (cx + dx)] = 0
                map.floorTex[(cy + dy) * size + (cx + dx)] = Textures.FLOOR_FLAG
            }
        }
        marketStalls(map, rng, cx, cy, level, cultureId, geography)
        brazier(map, cx + 0.5f, cy + 0.5f, "the square's fire")
        brazier(map, cx + 0.5f, cy + ring - 1.5f, "gatefire")
        // the keep, or the citadel, holding the north
        val kw = if (citadel) 9 else 7
        val kh = if (citadel) 6 else 5
        val keepName = if (citadel) "the citadel of ${site.name}" else "${site.name} keep"
        val keep = BuildingFootprint(
            x = cx - kw / 2, y = cy - ring + 3, w = kw, h = kh,
            doorX = cx, doorY = cy - ring + 3 + kh - 1,
            kind = if (citadel) "citadel" else "keep", name = keepName
        )
        drawBuilding(map, keep, Textures.WALL_STONE, Textures.FLOOR_FLAG)
        gatePosts(map, keep)
        brazier(map, keep.doorX - 1.6f, keep.doorY + 0.8f)
        brazier(map, keep.doorX + 1.6f, keep.doorY + 0.8f)
        // the great houses stand before any lesser building is set, and keep their ground
        val greatHouses = mutableListOf(keep)
        if (citadel) {
            // the grand temple west of the square, the guild hall east
            val temple = BuildingFootprint(
                x = cx - ring + 3, y = cy - 3, w = 6, h = 5,
                doorX = cx - ring + 8, doorY = cy - 1,
                kind = "temple", name = "the grand temple"
            )
            drawBuilding(map, temple, Textures.WALL_STONE, Textures.FLOOR_FLAG)
            greatHouses += temple
            val guild = BuildingFootprint(
                x = cx + ring - 8, y = cy - 3, w = 6, h = 5,
                doorX = cx + ring - 8, doorY = cy - 1,
                kind = "guild hall", name = "the guild hall"
            )
            drawBuilding(map, guild, Textures.WALL_STONE, Textures.FLOOR_FLAG)
            greatHouses += guild
        }
        val avoid = streetRects(cx, cy, ring, half) + plazaRect(cx, cy, plaza) +
            listOf(intRect(keep.x - 1, keep.y - 1, keep.x + keep.w, keep.y + keep.h))
        val placed = mutableListOf<BuildingFootprint>()
        placed += greatHouses
        namedBuildings(
            map, rng, placed, site, cx, cy, size, avoid, 5, ring - 4,
            Textures.WALL_STONE, Textures.FLOOR_FLAG, listOf(4 to 3, 5 to 4)
        )
        repeat(if (citadel) 10 + rng.nextInt(4) else 7 + rng.nextInt(3)) {
            tryPlace(
                map, rng, placed, cx, cy, size, 5, ring - 3, 3 + rng.nextInt(2), 3 + rng.nextInt(2),
                Textures.WALL_TIMBER, Textures.FLOOR_MUD, "house",
                HOUSE_NAMES[rng.nextInt(HOUSE_NAMES.size)], avoid
            )?.let { placed += it }
        }
    }

    // ------------------------------------------------------------------ pieces

    /** The site's own works — tavern, temple, guild hall — placed where they fit. */
    private fun namedBuildings(
        map: GameMap,
        rng: Random,
        placed: MutableList<BuildingFootprint>,
        site: Site,
        cx: Int,
        cy: Int,
        size: Int,
        avoid: List<IntRect>,
        minR: Int,
        maxR: Int,
        wallTex: Int,
        floorTex: Int,
        sizes: List<Pair<Int, Int>>
    ) {
        site.structures.filter { !it.ruined }.forEachIndexed { i, st ->
            // a tavern is a communal hall: it always takes the largest footprint
            val (w, h) = if (st.kind.label == "tavern") sizes.last() else sizes[i % sizes.size]
            tryPlace(
                map, rng, placed, cx, cy, size, minR, maxR, w, h,
                wallTex, floorTex, st.kind.label, st.name, avoid
            )?.let { placed += it }
        }
    }

    /** Find a spot in the ring band, cut the footprint, open its door toward the heart. */
    private fun tryPlace(
        map: GameMap,
        rng: Random,
        placed: List<BuildingFootprint>,
        cx: Int,
        cy: Int,
        size: Int,
        minR: Int,
        maxR: Int,
        w: Int,
        h: Int,
        wallTex: Int,
        floorTex: Int,
        kind: String,
        name: String,
        avoid: List<IntRect>
    ): BuildingFootprint? {
        repeat(60) {
            val ang = rng.nextFloat() * 6.28318f
            val r = minR + rng.nextInt((maxR - minR + 1).coerceAtLeast(1))
            val bx = cx + (cos(ang) * r).roundToInt()
            val by = cy + (sin(ang) * r).roundToInt()
            val x = (bx - w / 2).coerceIn(2, size - w - 2)
            val y = (by - h / 2).coerceIn(2, size - h - 2)
            val clash = placed.any { p ->
                x - 1 < p.x + p.w && x + w + 1 > p.x && y - 1 < p.y + p.h && y + h + 1 > p.y
            } || avoid.any { rect -> x <= rect.x1 && x + w - 1 >= rect.x0 && y <= rect.y1 && y + h - 1 >= rect.y0 }
            if (clash) return@repeat
            val building = withDoor(x, y, w, h, cx, cy, kind, name)
            drawBuilding(map, building, wallTex, floorTex)
            return building
        }
        return null
    }

    /** The door goes in the side of the building that faces the town's heart. */
    private fun withDoor(x: Int, y: Int, w: Int, h: Int, cx: Int, cy: Int, kind: String, name: String): BuildingFootprint {
        val bx = x + w / 2
        val by = y + h / 2
        val dx = bx - cx
        val dy = by - cy
        return if (abs(dy) >= abs(dx)) {
            val doorY = if (dy > 0) y else y + h - 1
            BuildingFootprint(x, y, w, h, bx.coerceIn(x + 1, x + w - 2), doorY, kind, name)
        } else {
            val doorX = if (dx > 0) x else x + w - 1
            BuildingFootprint(x, y, w, h, doorX, by.coerceIn(y + 1, y + h - 2), kind, name)
        }
    }

    /** Walls on the perimeter, a cleared floor within, and the door standing open.
     *  The great houses rise over their neighbors: keeps and citadels three walls
     *  high, temples and guild halls two, every lesser roof a single course. */
    private fun drawBuilding(map: GameMap, b: BuildingFootprint, wallTex: Int, floorTex: Int) {
        val tall = heightOfKind(b.kind)
        val hts = if (tall > 1f) {
            map.wallHeights ?: FloatArray(map.width * map.height).also { map.wallHeights = it }
        } else null
        for (y in b.y until b.y + b.h) {
            for (x in b.x until b.x + b.w) {
                val perimeter = x == b.x || x == b.x + b.w - 1 || y == b.y || y == b.y + b.h - 1
                val idx = y * map.width + x
                map.walls[idx] = if (perimeter) wallTex else 0
                if (perimeter) hts?.set(idx, tall)
                map.floorTex[idx] = floorTex
            }
        }
        map.walls[b.doorY * map.width + b.doorX] = 0
        map.roofs += Roof(
            b.x, b.y, b.x + b.w, b.y + b.h, tall, tall + 0.5f,
            b.w >= b.h, wallTex, Textures.WALL_ROOF
        )
        map.buildings += b
    }

    /** How high a house's masonry stands, in wall-heights, by what the house is. */
    private fun heightOfKind(kind: String): Float = when (kind) {
        "keep", "citadel" -> 3f
        "temple", "guild hall" -> 2f
        else -> 1f
    }

    /** A closed ring of wall with its south gate cut wide; stone rings stand taller. */
    private fun wallRing(map: GameMap, cx: Int, cy: Int, size: Int, ring: Int, tex: Int, height: Float = 1f) {
        val hts = if (height > 1f) {
            map.wallHeights ?: FloatArray(map.width * map.height).also { map.wallHeights = it }
        } else null
        for (d in -ring..ring) {
            val idxN = (cy - ring) * size + (cx + d)
            val idxS = (cy + ring) * size + (cx + d)
            val idxW = (cy + d) * size + (cx - ring)
            val idxE = (cy + d) * size + (cx + ring)
            map.walls[idxN] = tex
            map.walls[idxS] = tex
            map.walls[idxW] = tex
            map.walls[idxE] = tex
            if (hts != null) {
                hts[idxN] = height
                hts[idxS] = height
                hts[idxW] = height
                hts[idxE] = height
            }
        }
        for (dx in -1..1) {
            map.walls[(cy + ring) * size + (cx + dx)] = 0
            map.floorTex[(cy + ring) * size + (cx + dx)] = Textures.FLOOR_ROAD
        }
    }

    /** The cross of streets, wide for the great places, trodden for the small. */
    private fun carveStreets(map: GameMap, cx: Int, cy: Int, size: Int, ring: Int, halfWidth: Int) {
        for (d in -ring..ring) {
            for (dx in -halfWidth..halfWidth) {
                map.walls[(cy + d) * size + (cx + dx)] = 0
                map.floorTex[(cy + d) * size + (cx + dx)] = Textures.FLOOR_ROAD
                map.walls[(cy + dx) * size + (cx + d)] = 0
                map.floorTex[(cy + dx) * size + (cx + d)] = Textures.FLOOR_ROAD
            }
        }
    }

    private fun marketStalls(
        map: GameMap,
        rng: Random,
        cx: Int,
        cy: Int,
        level: Int,
        cultureId: Int,
        geography: MaterialGeography?
    ) {
        repeat(4) { i ->
            val sx = cx + if (i % 2 == 0) -3 else 3
            val sy = cy + if (i < 2) -3 else 3
            map.entities += MapFactory.container(
                rng, sx + 0.5f, sy + 0.5f, Sprites.URN, 0.35f, "market stall",
                level, chanceOfLoot = 2, cultureId, geography
            )
        }
    }

    /** Warded corner posts mark the great house: gate towers a keep's own height. */
    private fun gatePosts(map: GameMap, b: BuildingFootprint) {
        for (gx in listOf(b.x - 1, b.x + b.w)) {
            for (gy in listOf(b.y - 1, b.y + b.h)) {
                if (gx in 1 until map.width - 1 && gy in 1 until map.height - 1) {
                    val idx = gy * map.width + gx
                    map.walls[idx] = Textures.WALL_GATE
                    val hts = map.wallHeights
                        ?: FloatArray(map.width * map.height).also { map.wallHeights = it }
                    hts[idx] = 3f
                }
            }
        }
    }

    private fun brazier(map: GameMap, x: Float, y: Float, name: String = "brazier") {
        map.entities += Entity(
            x = x, y = y, spriteId = Sprites.BRAZIER, kind = EntityKind.PROP,
            height = 0.85f, name = name
        )
    }

    private fun streetRects(cx: Int, cy: Int, ring: Int, halfWidth: Int): List<IntRect> = listOf(
        intRect(cx - halfWidth, cy - ring, cx + halfWidth, cy + ring),
        intRect(cx - ring, cy - halfWidth, cx + ring, cy + halfWidth)
    )

    private fun plazaRect(cx: Int, cy: Int, r: Int): IntRect =
        intRect(cx - r, cy - r, cx + r, cy + r)

    private fun intRect(x0: Int, y0: Int, x1: Int, y1: Int): IntRect = IntRect(x0, y0, x1, y1)

    /** A kept-clear rectangle of ground, in cells inclusive. */
    data class IntRect(val x0: Int, val y0: Int, val x1: Int, val y1: Int)
}
