package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.World
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * How a holding people dresses what they bury: the plain wall, the accent walls,
 * the floor mix, how often braziers burn, and the chronicler's line for the dark.
 */
data class Skin(
    val plainWall: Int,
    val accents: List<Int>,
    val floors: List<Int>,
    val brazierEvery: Int,
    val darkLine: String,
    val gateWall: Int
)

/**
 * Every dead place built its own way. Vaults, barrows, ruins, camps and shrines
 * each draw their layout, their doors, their dressing and their boss from the
 * world seed plus the site itself: same seed, same place, forever.
 *
 * A site with buried floors is two faces of one place. The surface holds one or
 * two physical doors; each door is a portal down, and each has a matching way
 * out on the first floor. Deeper floors chain by stairs, and the boss's hall
 * waits at the bottom.
 */
object SiteGen {

    private val SKINS = listOf(
        Skin(
            Textures.WALL_STONE, listOf(Textures.WALL_RUNE, Textures.WALL_MOSS),
            listOf(Textures.FLOOR_FLAG, Textures.FLOOR_FLAG, Textures.FLOOR_MUD),
            2, "Rune-cut piers hold the dark.", Textures.WALL_GATE
        ),
        Skin(
            Textures.WALL_MOSS, listOf(Textures.WALL_STONE),
            listOf(Textures.FLOOR_MUD, Textures.FLOOR_FLAG),
            3, "The wet stone sweats moss.", Textures.WALL_RUIN
        ),
        Skin(
            Textures.WALL_RUIN, listOf(Textures.WALL_STONE, Textures.WALL_TIMBER),
            listOf(Textures.FLOOR_MUD, Textures.FLOOR_MUD, Textures.FLOOR_FLAG),
            4, "Old rubble ribs the dark.", Textures.WALL_RUIN
        ),
        Skin(
            Textures.WALL_TIMBER, listOf(Textures.WALL_STONE),
            listOf(Textures.FLOOR_FLAG, Textures.FLOOR_MUD),
            2, "Cut timber braces the dark.", Textures.WALL_GATE
        )
    )

    /** The skin a people gives their dead places: pure in the culture, so it never wanders. */
    fun skinFor(cultureId: Int): Skin = SKINS[((cultureId % SKINS.size) + SKINS.size) % SKINS.size]

    /** Who a site answers to, in the skin the culture is owed. */
    fun cultureId(world: World, site: Site): Int =
        world.power(site.holderPowerId)?.cultureId ?: -1

    // ------------------------------------------------------------------ bosses

    /** Where a boss stands: the world's own monsters first, fitting fallbacks after. */
    data class BossSpec(val name: String, val line: String?)

    /**
     * A beast that lairs at the site wakes as its boss; the vault's guardian is
     * tied to whatever the age's artifact left in its keeping; otherwise a
     * fitting fallback takes the post.
     */
    fun bossFor(world: World, site: Site, slainBeasts: Set<Int> = emptySet()): BossSpec {
        // A beast slain out in the open does not wake in its lair: the world remembers.
        val beast = world.beasts.firstOrNull {
            it.lairSiteId == site.id && it.alive && it.id !in slainBeasts
        }
        if (beast != null) {
            return BossSpec(
                beast.name,
                "${beast.name} the ${beast.kind} woke beneath ${site.name} in year ${beast.wokeYear}. It is still here."
            )
        }
        if (site.kind == SiteKind.VAULT) {
            world.artifacts.firstOrNull { it.keeperSiteId == site.id }?.let { artifact ->
                return BossSpec(
                    "Warden of ${artifact.name}",
                    "The ${artifact.name} is kept here, and its warden never left."
                )
            }
        }
        return when (site.kind) {
            SiteKind.BARROW -> BossSpec("Barrow-King", null)
            SiteKind.RUIN -> BossSpec("Ruin Warden", null)
            SiteKind.CAMP -> BossSpec("Bandit Chief", null)
            SiteKind.SHRINE -> BossSpec("Custodian", null)
            else -> BossSpec("Vault Warden", null)
        }
    }

    // ------------------------------------------------------------------ floors

    /** How many buried floors a site keeps: vaults are always deep, barrows rarely are. */
    fun floorCount(world: World, site: Site): Int {
        val rng = Random(mapSeed(world, site, 0) + 91)
        return when (site.kind) {
            SiteKind.VAULT -> 2 + rng.nextInt(2)
            SiteKind.BARROW -> if (rng.nextInt(4) == 0) 2 else 1
            SiteKind.RUIN -> 1 + rng.nextInt(2)
            else -> 0
        }
    }

    /** How many doors the surface keeps: every dead place has at least one way in. */
    fun entranceCount(world: World, site: Site): Int {
        val rng = Random(mapSeed(world, site, 0) + 57)
        return when (site.kind) {
            SiteKind.VAULT, SiteKind.RUIN -> if (rng.nextInt(2) == 0) 2 else 1
            SiteKind.BARROW -> if (rng.nextInt(3) == 0) 2 else 1
            else -> 0
        }
    }

    /** How far above the buried floors the building interiors begin. */
    const val BUILDING_FLOOR_BASE = 100

    /** The seed of a site's face: world, site and floor, ground to one number. */
    fun mapSeed(world: World, site: Site, floor: Int): Long =
        world.seed * 1000003L + site.id * 49157L + floor * 127L

    /**
     * The map of one face of a site: floor 0 is the surface, deeper floors are buried.
     * [folk] is the settlement's living count, as the years have made it; a living
     * place is sized to the stage its folk stand at.
     */
    fun map(
        world: World,
        site: Site,
        floor: Int,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        day: Int,
        slainBeasts: Set<Int> = emptySet(),
        folk: Int = -1
    ): GameMap {
        if (floor >= BUILDING_FLOOR_BASE) {
            // a building's inside: the yard is built first, for its footprints
            val yard = surface(world, site, roster, geography, day, slainBeasts, folk)
            val idx = floor - BUILDING_FLOOR_BASE
            val room = yard.buildings.getOrNull(idx) ?: return yard
            return buildingInterior(world, site, idx, room, cultureId(world, site), day, geography)
        }
        val floors = floorCount(world, site)
        return if (floor <= 0 || floors == 0) {
            surface(world, site, roster, geography, day, slainBeasts, folk)
        } else {
            underground(world, site, floor.coerceAtMost(floors), roster, geography, slainBeasts)
        }
    }

    private fun surface(
        world: World,
        site: Site,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        day: Int,
        slainBeasts: Set<Int>,
        folk: Int
    ): GameMap {
        val map = when (site.kind) {
            SiteKind.VAULT -> vaultSurface(world, site, roster, geography, day)
            SiteKind.BARROW -> barrowSurface(world, site, roster, geography, day)
            SiteKind.RUIN -> ruinSurface(world, site, roster, geography, day)
            SiteKind.CAMP -> camp(world, site, roster, geography, day, slainBeasts)
            SiteKind.SHRINE -> shrine(world, site, roster, geography, day, slainBeasts)
            // The living places are built to their stage, as their folk stand.
            SiteKind.CAPITAL, SiteKind.CITY, SiteKind.TOWN, SiteKind.VILLAGE, SiteKind.HOLDFAST ->
                SettlementGen.map(
                    world, site,
                    stageOf(site, if (folk >= 0) folk else site.population),
                    roster, geography, day
                )
            else -> MapFactory.wilds(
                world.seed + site.id, site.name, roster, day, cultureId(world, site), geography
            )
        }
        // Every face of a place keeps one way back out onto the open province:
        // two paces behind the doorstep, on the ground you came in over.
        val outX = map.spawnX - cos(map.spawnAngle) * 2f
        val outY = map.spawnY - sin(map.spawnAngle) * 2f
        for (d in 1..2) {
            val x = (map.spawnX - cos(map.spawnAngle) * d).toInt()
            val y = (map.spawnY - sin(map.spawnAngle) * d).toInt()
            if (x in 1 until map.width - 1 && y in 1 until map.height - 1) {
                map.walls[y * map.width + x] = 0
            }
        }
        map.portals += Portal(
            x = outX,
            y = outY,
            label = "the open road",
            prompt = "Walk out into ${world.provinceName}",
            targetFloor = OverlandGen.OVERLAND_FLOOR,
            arrivalIndex = 0,
            down = false
        )
        return map
    }

    // ------------------------------------------------------------------ surfaces

    private fun vaultSurface(
        world: World,
        site: Site,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        day: Int
    ): GameMap {
        val skin = skinFor(cultureId(world, site))
        val seed = mapSeed(world, site, 0)
        val rng = Random(seed)
        val size = 40
        val map = openGround(seed, size, site.name)
        val count = entranceCount(world, site)
        val doors = openDoors(
            map, site, listOf("the warded gate", "the sunken postern").take(count),
            if (count == 2) listOf(SOUTH, NORTHEAST) else listOf(SOUTH),
            size / 2, size / 2, 9, 8, Textures.WALL_STONE, skin
        )
        scatterProps(
            map, rng, 26, doors,
            listOf(
                Pair(Sprites.DEAD_TREE, 2.6f), Pair(Sprites.STANDING_STONE, 1.6f),
                Pair(Sprites.REEDS, 0.9f), Pair(Sprites.GRAVE, 0.6f)
            )
        )
        repeat(2 + rng.nextInt(2)) { looseEnemy(map, rng, doors, 1 + day / 25, roster, cultureId(world, site), geography) }
        return map
    }

    private fun barrowSurface(
        world: World,
        site: Site,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        day: Int
    ): GameMap {
        val skin = skinFor(cultureId(world, site))
        val seed = mapSeed(world, site, 0)
        val rng = Random(seed)
        val size = 40
        val map = openGround(seed, size, site.name)
        val count = entranceCount(world, site)
        val doors = openDoors(
            map, site, listOf("the barrow-mouth", "the sunk trench").take(count),
            if (count == 2) listOf(SOUTH, NORTHEAST) else listOf(SOUTH),
            size / 2, size / 2, 8, 6, Textures.WALL_RUIN, skin
        )
        scatterProps(
            map, rng, 30, doors,
            listOf(
                Pair(Sprites.REEDS, 0.9f), Pair(Sprites.DEAD_TREE, 2.6f), Pair(Sprites.GRAVE, 0.6f)
            )
        )
        repeat(1 + rng.nextInt(2)) { looseEnemy(map, rng, doors, 1 + day / 25, roster, cultureId(world, site), geography) }
        return map
    }

    private fun ruinSurface(
        world: World,
        site: Site,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        day: Int
    ): GameMap {
        val skin = skinFor(cultureId(world, site))
        val seed = mapSeed(world, site, 0)
        val rng = Random(seed)
        val size = 44
        val map = openGround(seed, size, site.name)
        val count = entranceCount(world, site)
        val doors = openDoors(
            map, site, listOf("the collapsed gate", "the cellar door").take(count),
            if (count == 2) listOf(SOUTH, NORTHEAST) else listOf(SOUTH),
            size / 2, size / 2, 10, 9, Textures.WALL_RUIN, skin
        )
        // broken walls of whatever the place was
        repeat(7) {
            val x = 4 + rng.nextInt(size - 12)
            val y = 4 + rng.nextInt(size - 16)
            val len = 3 + rng.nextInt(6)
            val horizontal = rng.nextBoolean()
            for (i in 0 until len) {
                if (rng.nextInt(4) == 0) continue
                val nx = if (horizontal) x + i else x
                val ny = if (horizontal) y else y + i
                if (nx in 1 until size - 1 && ny in 1 until size - 1 && map.walls[ny * size + nx] == 0) {
                    map.walls[ny * size + nx] = Textures.WALL_RUIN
                }
            }
        }
        scatterProps(
            map, rng, 30, doors,
            listOf(
                Pair(Sprites.STANDING_STONE, 0.9f), Pair(Sprites.GRAVE, 0.6f),
                Pair(Sprites.DEAD_TREE, 2.6f), Pair(Sprites.REEDS, 0.9f)
            )
        )
        repeat(2 + rng.nextInt(2)) { looseEnemy(map, rng, doors, 1 + day / 25, roster, cultureId(world, site), geography) }
        return map
    }

    /** A warband's yard: palisade with two gates, watchfires, tents, and the chief. */
    private fun camp(
        world: World,
        site: Site,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        day: Int,
        slainBeasts: Set<Int>
    ): GameMap {
        val seed = mapSeed(world, site, 0)
        val rng = Random(seed)
        val size = 48
        val map = openGround(seed, size, site.name)
        val cultureId = cultureId(world, site)
        val x0 = 10
        val x1 = size - 11
        val y0 = 10
        val y1 = size - 11
        // the palisade, cut by a south gate and an east gap
        for (x in x0..x1) {
            if (abs(x - (x0 + x1) / 2) > 1) {
                map.walls[y0 * size + x] = Textures.WALL_TIMBER
                map.walls[y1 * size + x] = Textures.WALL_TIMBER
            }
        }
        for (y in y0..y1) {
            if (abs(y - (y0 + y1) / 2) > 1) {
                map.walls[y * size + x0] = Textures.WALL_TIMBER
                map.walls[y * size + x1] = Textures.WALL_TIMBER
            }
        }
        map.spawnX = size / 2 + 0.5f
        map.spawnY = y1 + 2.5f
        map.spawnAngle = -1.5708f

        // watchfires, tents, and the chief's tent at the back
        repeat(3) {
            map.entities += Entity(
                x = x0 + 3 + rng.nextInt(x1 - x0 - 5) + rng.nextFloat(),
                y = y0 + 3 + rng.nextInt(y1 - y0 - 5) + rng.nextFloat(),
                spriteId = Sprites.BRAZIER, kind = EntityKind.PROP, height = 0.85f, name = "watchfire"
            )
        }
        // the chief's tent holds the north, its door toward the heart of the camp
        val placed = mutableListOf<BuildingFootprint>()
        val chief = SettlementGen.placeTentAt(
            map, placed, size / 2, size / 2, size / 2 - 3, y0 + 2, 5, 5, "chief's tent"
        )
        repeat(4 + rng.nextInt(3)) {
            SettlementGen.tryTent(
                map, rng, placed, size / 2, size / 2, size, 3, 3,
                6, (x1 - size / 2 - 3).coerceAtLeast(7)
            )
        }
        SettlementGen.wireDoors(map, site)
        SettlementGen.placeResidents(map, rng, world, site, size / 2, size / 2, force = true)
        val bossLevel = 3 + day / 20
        map.entities += bossEntity(
            rng, bossFor(world, site, slainBeasts), bossLevel, size / 2 + 2.2f, y0 + 3.5f,
            roster, cultureId, geography, beastly = false
        )
        map.entities += hoard(
            rng, chief.doorX - 1.6f, chief.doorY + 1.5f, "war chest", bossLevel, cultureId, geography,
            Sprites.URN, 0.5f
        )
        repeat(2) {
            map.entities += MapFactory.container(
                rng, x0 + 3 + rng.nextInt(x1 - x0 - 6) + rng.nextFloat(),
                y0 + 3 + rng.nextInt(y1 - y0 - 6) + rng.nextFloat(),
                Sprites.URN, 0.35f, "supply cache", 1 + day / 25, chanceOfLoot = 1, cultureId, geography
            )
        }
        repeat(4 + rng.nextInt(3)) { looseEnemy(map, rng, emptyList(), 1 + day / 20, roster, cultureId, geography) }
        // nothing of the warband's is left walled inside a tent shell
        map.entities.removeAll { e ->
            map.buildings.any { b ->
                e.x.toInt() > b.x && e.x.toInt() < b.x + b.w - 1 &&
                    e.y.toInt() > b.y && e.y.toInt() < b.y + b.h - 1
            }
        }
        map.entities.removeAll { map.isWall(it.x, it.y) }
        return map
    }

    /** A small sacred place: a ring of stones, an altar, offering bowls, a custodian. */
    private fun shrine(
        world: World,
        site: Site,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        day: Int,
        slainBeasts: Set<Int>
    ): GameMap {
        val seed = mapSeed(world, site, 0)
        val rng = Random(seed)
        val size = 36
        val map = openGround(seed, size, site.name)
        val cultureId = cultureId(world, site)
        val cx = size / 2
        val cy = size / 2
        // the stone circle, with its opening facing the south approach
        repeat(10) { i ->
            val a = i * 6.28318f / 10
            if (angDist(a, SOUTH) < 0.4f) return@repeat
            map.entities += Entity(
                x = cx + cos(a) * 6f, y = cy + sin(a) * 6f,
                spriteId = Sprites.STANDING_STONE, kind = EntityKind.PROP,
                height = 1.6f, name = "standing stone"
            )
        }
        // the altar: a walled box with a mouth toward the opening
        for (y in cy - 2..cy) {
            for (x in cx - 1..cx + 1) {
                val rim = y == cy - 2 || y == cy || x == cx - 1 || x == cx + 1
                if (rim && !(x == cx && y == cy)) map.walls[y * size + x] = Textures.WALL_GATE
            }
        }
        map.entities += MapFactory.container(
            rng, cx + 0.5f, cy - 1.4f, Sprites.URN, 0.35f, "offering bowl",
            1 + day / 25, chanceOfLoot = 1, cultureId, geography
        )
        repeat(2 + rng.nextInt(2)) {
            map.entities += MapFactory.container(
                rng, cx - 4 + rng.nextInt(9) + rng.nextFloat(), cy - 4 + rng.nextInt(9) + rng.nextFloat(),
                Sprites.URN, 0.35f, "offering bowl", 1 + day / 25, chanceOfLoot = 2, cultureId, geography
            )
        }
        val bossLevel = 2 + day / 25
        map.entities += bossEntity(
            rng, bossFor(world, site, slainBeasts), bossLevel, cx + 2.5f, cy + 2.5f,
            roster, cultureId, geography, beastly = false
        )
        map.entities += hoard(
            rng, cx - 1.8f, cy + 3.2f, "offering hoard", bossLevel, cultureId, geography,
            Sprites.GRAVE, 0.6f
        )
        repeat(1 + rng.nextInt(2)) { looseEnemy(map, rng, emptyList(), 1 + day / 25, roster, cultureId, geography) }
        map.spawnX = cx + 0.5f
        map.spawnY = cy + 9.5f
        map.spawnAngle = -1.5708f
        map.entities.removeAll { map.isWall(it.x, it.y) }
        return map
    }

    // ------------------------------------------------------------------ underground

    private class CarveSpec(
        val size: Int,
        val rooms: Int,
        val minW: Int,
        val maxW: Int,
        val minH: Int,
        val maxH: Int,
        val wide: Boolean
    )

    private fun underground(
        world: World,
        site: Site,
        floor: Int,
        roster: ClassRoster?,
        geography: MaterialGeography?,
        slainBeasts: Set<Int>
    ): GameMap {
        val floors = floorCount(world, site)
        val cultureId = cultureId(world, site)
        val skin = skinFor(cultureId)
        val rng = Random(mapSeed(world, site, floor))
        val entrances = entranceCount(world, site)
        val spec = when (site.kind) {
            SiteKind.BARROW -> CarveSpec(40, 6 + rng.nextInt(3), 3, 5, 3, 5, wide = false)
            SiteKind.RUIN -> CarveSpec(44, 6 + rng.nextInt(3), 5, 9, 4, 7, wide = true)
            else -> CarveSpec(46, 8 + rng.nextInt(3), 5, 8, 5, 8, wide = true)
        }
        val map = GameMap(spec.size, spec.size, outdoor = false, title = floorTitle(site.name, floor))
        map.walls.fill(skin.plainWall)
        map.floorTex.fill(skin.floors.first())
        val rooms = carve(map, rng, skin, spec)

        // the deep places learn their heights: crawlways press one course low, the
        // halls rise two, and the boss's hall three, on its rows of pillars. The
        // corridors are cut again after, so a pillar never dams the ways between.
        val hts = FloatArray(map.width * map.height) { 1f }
        val ceilings = FloatArray(map.width * map.height) { 1f }
        map.wallHeights = hts
        map.ceilingHeights = ceilings
        val bossIdx = if (floor == floors) rooms.size - 1 else -1
        if (bossIdx >= 0) {
            val hall = rooms[bossIdx]
            val keep = setOf(
                hall.cx to hall.cy,
                (hall.cx - 1.2f).toInt() to (hall.cy + 1.2f).toInt(),
                (hall.cx - 1.4f).toInt() to (hall.cy - 1.4f).toInt(),
                (hall.cx + 1.4f).toInt() to (hall.cy - 1.4f).toInt()
            )
            for (y in hall.y + 1 until hall.y + hall.h - 1) {
                for (x in hall.x + 1 until hall.x + hall.w - 1) {
                    if ((x - hall.x) % 2 == 1 && (y - hall.y) % 2 == 1 && (x to y) !in keep) {
                        val idx = y * map.width + x
                        map.walls[idx] = skin.plainWall
                        hts[idx] = 3f
                    }
                }
            }
            for (i in 1 until rooms.size) connect(map, rooms[i - 1], rooms[i], spec.wide)
            if (rooms.size > 3) connect(map, rooms.first(), rooms.last(), spec.wide)
        }
        val start = rooms.first()
        map.spawnX = start.cx + 0.5f
        map.spawnY = start.cy + 0.5f
        map.spawnAngle = rng.nextFloat() * 6.28f

        // Every way in answers a way out; the deep floors chain by stairs.
        if (floor == 1) {
            for (i in 0 until entrances) {
                val px = (start.x + 1 + i * 2).coerceAtMost(start.x + start.w - 2)
                val py = start.y
                map.portals += Portal(
                    px + 0.5f, py + 0.5f, "the way out", "Climb out of ${site.name}", 0, i, down = false
                )
                map.arrivalSpots += Pair(px + 0.5f, py + 1.6f)
                map.entities += Entity(
                    x = px + 1.5f, y = py + 1.2f, spriteId = Sprites.BRAZIER,
                    kind = EntityKind.PROP, height = 0.85f, name = "brazier"
                )
            }
        } else {
            map.portals += Portal(
                start.cx + 0.5f, start.cy + 0.5f, "the stair up", "Climb up", floor - 1,
                if (floor - 1 == 1) entrances else 1, down = false
            )
            map.arrivalSpots += Pair(start.cx + 0.5f, start.cy + 1.6f)
        }
        if (floor < floors) {
            val last = rooms.last()
            map.portals += Portal(
                last.cx + 0.5f, last.cy + 0.5f, "the stair down", "Go deeper", floor + 1, 0, down = true
            )
            map.arrivalSpots += Pair(last.cx + 0.5f, last.cy + 1.6f)
        }

        val levelBase = floor + 1
        rooms.drop(1).forEach { room ->
            when (site.kind) {
                SiteKind.BARROW -> {
                    if (rng.nextInt(2) == 0) {
                        map.entities += MapFactory.container(
                            rng, room.cx + 0.5f, room.cy + 0.5f, Sprites.GRAVE, 0.6f, "grave",
                            levelBase, chanceOfLoot = 1, cultureId, geography
                        )
                    }
                    if (rng.nextInt(2) == 0) {
                        map.entities += MapFactory.container(
                            rng, room.x + 0.7f, room.y + 0.7f, Sprites.URN, 0.35f, "urn",
                            levelBase, chanceOfLoot = 1, cultureId, geography
                        )
                    }
                    if (rng.nextInt(2) == 0) roomEnemy(map, rng, room, levelBase, roster, cultureId, geography)
                }
                SiteKind.RUIN -> {
                    if (rng.nextInt(3) == 0) {
                        map.entities += Entity(
                            x = room.x + 0.8f + rng.nextFloat() * (room.w - 1.6f),
                            y = room.y + 0.8f + rng.nextFloat() * (room.h - 1.6f),
                            spriteId = Sprites.STANDING_STONE, kind = EntityKind.PROP,
                            height = 0.8f, name = "rubble"
                        )
                    }
                    if (rng.nextInt(4) == 0) {
                        map.entities += MapFactory.container(
                            rng, room.cx + 0.5f, room.cy + 0.5f, Sprites.URN, 0.35f, "urn",
                            levelBase, chanceOfLoot = 1, cultureId, geography
                        )
                    }
                    if (rng.nextInt(skin.brazierEvery.coerceAtLeast(1)) == 0) roomBrazier(map, rng, room)
                    repeat(rng.nextInt(3)) { roomEnemy(map, rng, room, levelBase, roster, cultureId, geography) }
                }
                else -> {
                    if (rng.nextInt(skin.brazierEvery.coerceAtLeast(1)) == 0) roomBrazier(map, rng, room)
                    repeat(1 + rng.nextInt(2)) { roomEnemy(map, rng, room, levelBase, roster, cultureId, geography) }
                    if (rng.nextInt(3) == 0) {
                        map.entities += MapFactory.container(
                            rng, room.cx + 0.5f, room.cy + 0.5f, Sprites.URN, 0.35f, "urn",
                            levelBase, chanceOfLoot = 1, cultureId, geography
                        )
                    }
                    if (rng.nextInt(5) == 0) {
                        map.entities += MapFactory.container(
                            rng, room.x + 0.7f, room.y + 0.7f, Sprites.GRAVE, 0.6f, "grave",
                            levelBase, chanceOfLoot = 1, cultureId, geography
                        )
                    }
                }
            }
        }

        if (floor == floors) {
            val boss = bossFor(world, site, slainBeasts)
            val bossLevel = 1 + floors * 2 + floor
            val hall = rooms.last()
            val beastly = world.beasts.any {
                it.lairSiteId == site.id && it.alive && it.id !in slainBeasts
            }
            map.entities += bossEntity(
                rng, boss, bossLevel, hall.cx + 0.5f, hall.cy + 0.5f,
                roster, cultureId, geography, beastly
            )
            val hoardName = when (site.kind) {
                SiteKind.BARROW -> "grave-goods"
                SiteKind.RUIN -> "buried cache"
                else -> "reliquary"
            }
            // the hoard sits beside the boss, kept inside the hall's own floor
            val hoardX = (hall.cx - 1.2f).coerceIn(hall.x + 0.7f, hall.x + hall.w - 0.7f)
            val hoardY = (hall.cy + 1.2f).coerceIn(hall.y + 0.7f, hall.y + hall.h - 0.7f)
            map.entities += hoard(
                rng, hoardX, hoardY, hoardName, bossLevel, cultureId, geography,
                if (site.kind == SiteKind.BARROW) Sprites.GRAVE else Sprites.URN,
                if (site.kind == SiteKind.BARROW) 0.6f else 0.35f
            )
            // gate-posted corners mark the warded hall
            for (gx in listOf(hall.x - 1, hall.x + hall.w)) {
                for (gy in listOf(hall.y - 1, hall.y + hall.h)) {
                    if (gx in 1 until map.width - 1 && gy in 1 until map.height - 1) {
                        val idx = gy * map.width + gx
                        map.walls[idx] = skin.gateWall
                        hts[idx] = 3f
                    }
                }
            }
            map.entities += Entity(
                x = hall.cx - 1.4f, y = hall.cy - 1.4f, spriteId = Sprites.BRAZIER,
                kind = EntityKind.PROP, height = 0.85f, name = "brazier"
            )
            map.entities += Entity(
                x = hall.cx + 1.4f, y = hall.cy - 1.4f, spriteId = Sprites.BRAZIER,
                kind = EntityKind.PROP, height = 0.85f, name = "brazier"
            )
            repeat(2) { roomEnemy(map, rng, hall, levelBase + 1, roster, cultureId, geography) }
        }

        // wall heights by the room each stone borders, and every way on framed in
        // taller stone with its roof lifted: the stair chambers read as alcoves
        val sides = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
        val roomOf = ByteArray(map.width * map.height)
        rooms.forEachIndexed { i, room ->
            for (y in room.y until room.y + room.h) {
                for (x in room.x until room.x + room.w) {
                    roomOf[y * map.width + x] = (i + 1).toByte()
                }
            }
        }
        for (y in 0 until map.height) {
            for (x in 0 until map.width) {
                val idx = y * map.width + x
                if (map.walls[idx] == 0) {
                    val ro = roomOf[idx].toInt()
                    ceilings[idx] = if (ro > 0) (if (ro - 1 == bossIdx) 3f else 2f) else 1f
                } else {
                    var h = hts[idx]
                    for ((dx, dy) in sides) {
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || ny < 0 || nx >= map.width || ny >= map.height) continue
                        val ro = roomOf[ny * map.width + nx].toInt()
                        if (ro > 0) h = maxOf(h, if (ro - 1 == bossIdx) 3f else 2f)
                    }
                    hts[idx] = h
                }
            }
        }
        for (portal in map.portals) {
            val px = portal.x.toInt()
            val py = portal.y.toInt()
            if (px < 0 || py < 0 || px >= map.width || py >= map.height) continue
            ceilings[py * map.width + px] = 3f
            for ((dx, dy) in sides) {
                val nx = px + dx
                val ny = py + dy
                if (nx < 0 || ny < 0 || nx >= map.width || ny >= map.height) continue
                val idx = ny * map.width + nx
                if (map.walls[idx] != 0) hts[idx] = maxOf(hts[idx], 3f)
            }
        }

        map.entities.removeAll { map.isWall(it.x, it.y) }
        return map
    }

    // ---------------------------------------------------------- inside the walls

    /**
     * The inside of one building: exactly the footprint its walls keep outside —
     * same tiles, same door — with a hearth, a bed, and the keeper's things.
     */
    fun buildingInterior(
        world: World,
        site: Site,
        index: Int,
        building: BuildingFootprint,
        cultureId: Int,
        day: Int,
        geography: MaterialGeography?
    ): GameMap {
        val w = building.w
        val h = building.h
        val map = GameMap(w, h, outdoor = false, title = building.name)
        val stone = building.kind in setOf("temple", "keep", "citadel", "guild hall")
        val wallTex = if (stone) Textures.WALL_STONE else Textures.WALL_TIMBER
        val floorTex = if (stone) Textures.FLOOR_FLAG else Textures.FLOOR_MUD
        for (y in 0 until h) {
            for (x in 0 until w) {
                val perimeter = x == 0 || x == w - 1 || y == 0 || y == h - 1
                val idx = y * w + x
                map.walls[idx] = if (perimeter) wallTex else 0
                map.floorTex[idx] = floorTex
            }
        }
        // the door, in the room's own coordinates: the footprint keeps the same shape
        val doorLX = building.doorX - building.x
        val doorLY = building.doorY - building.y
        map.walls[doorLY * w + doorLX] = 0
        map.portals += Portal(
            x = doorLX + 0.5f, y = doorLY + 0.5f,
            label = "the doorway", prompt = "Step out of ${building.name}",
            targetFloor = 0, arrivalIndex = index, down = false, targetSiteId = site.id, door = true
        )
        // where you land: one pace inside the door, facing the room
        val inDx = if (doorLX == 0) 1 else if (doorLX == w - 1) -1 else 0
        val inDy = if (doorLY == 0) 1 else if (doorLY == h - 1) -1 else 0
        val inX = (doorLX + inDx).coerceIn(1, w - 2)
        val inY = (doorLY + inDy).coerceIn(1, h - 2)
        map.arrivalSpots += Pair(inX + 0.5f, inY + 0.5f)
        map.spawnX = inX + 0.5f
        map.spawnY = inY + 0.5f
        map.spawnAngle = when {
            doorLY == 0 -> 1.5708f
            doorLY == h - 1 -> -1.5708f
            doorLX == 0 -> 0f
            else -> 3.14159f
        }

        // the keeper's things, by the kind of house it is
        val rng = Random(mapSeed(world, site, BUILDING_FLOOR_BASE + index))
        val level = 1 + day / 25
        val spots = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                if (x == inX && y == inY) continue
                spots += Pair(x, y)
            }
        }
        fun spot(): Pair<Int, Int>? = if (spots.isEmpty()) null else spots.removeAt(rng.nextInt(spots.size))
        fun bedroll(x: Int, y: Int) = Entity(
            x = x + 0.5f, y = y + 0.5f, spriteId = Sprites.TENT, kind = EntityKind.PROP,
            height = 0.3f, name = "bedroll"
        )
        if (building.kind == "tent") {
            // the tent's keeper is home to whoever knocks
            if (building.keeper.isNotBlank()) {
                spot()?.let { (x, y) ->
                    map.entities += Entity(
                        x = x + 0.5f, y = y + 0.5f, spriteId = Sprites.PILGRIM,
                        kind = EntityKind.ENEMY, height = 1.0f,
                        name = building.keeper, resident = true
                    )
                }
            }
            val bed = spot() ?: Pair(inX, inY)
            map.entities += bedroll(bed.first, bed.second)
            if (rng.nextInt(2) == 0) {
                spot()?.let { (x, y) ->
                    map.entities += MapFactory.container(
                        rng, x + 0.5f, y + 0.5f, Sprites.URN, 0.35f, "bandit's cache",
                        level, chanceOfLoot = 2, cultureId, geography
                    )
                }
            }
        } else {
            // the keeper's chest comes first, so the smallest room still keeps one
            val chest = when (building.kind) {
                "tavern" -> "the till"
                "temple" -> "offering bowl"
                "keep", "citadel", "guild hall" -> "strongbox"
                else -> "keeper's chest"
            }
            // the keeper is home to whoever knocks, before any comfort is placed
            if (building.keeper.isNotBlank()) {
                val role = RoleBook.keeperRole(
                    world.seed, site, world.terrain.biomeAt(site.x, site.y), building.kind, index
                )
                spot()?.let { (x, y) ->
                    map.entities += Entity(
                        x = x + 0.5f, y = y + 0.5f, spriteId = Sprites.PILGRIM,
                        kind = EntityKind.ENEMY, height = 1.0f,
                        name = building.keeper, resident = true, role = role, workBuilding = index,
                        personality = PersonalityBook.deal(
                            PersonalityBook.key(world.seed, "${site.id}:ib$index"), role
                        )
                    )
                }
            }
            spot()?.let { (x, y) ->
                map.entities += MapFactory.container(
                    rng, x + 0.5f, y + 0.5f, Sprites.URN, 0.35f, chest,
                    level, chanceOfLoot = 1, cultureId, geography
                )
            }
            if (w >= 4) {
                spot()?.let { (x, y) ->
                    map.entities += Entity(
                        x = x + 0.5f, y = y + 0.5f, spriteId = Sprites.BRAZIER,
                        kind = EntityKind.PROP, height = 0.8f,
                        name = if (building.kind == "temple") "the god's light" else "the hearth"
                    )
                }
            }
            repeat(if (building.kind == "tavern") 2 else 1) {
                spot()?.let { (x, y) -> map.entities += bedroll(x, y) }
            }
            when (building.kind) {
                "tavern" -> repeat(2) {
                    spot()?.let { (x, y) ->
                        map.entities += Entity(
                            x = x + 0.5f, y = y + 0.5f, spriteId = Sprites.URN,
                            kind = EntityKind.PROP, height = 0.45f, name = "table"
                        )
                    }
                }
                "temple" -> spot()?.let { (x, y) ->
                    map.entities += Entity(
                        x = x + 0.5f, y = y + 0.5f, spriteId = Sprites.BRAZIER,
                        kind = EntityKind.PROP, height = 0.8f, name = "the god's light"
                    )
                }
                "keep", "citadel", "guild hall" -> spot()?.let { (x, y) ->
                    map.entities += Entity(
                        x = x + 0.5f, y = y + 0.5f, spriteId = Sprites.URN,
                        kind = EntityKind.PROP, height = 0.6f, name = "weapon rack"
                    )
                }
                else -> {}
            }
        }
        map.entities.removeAll { map.isWall(it.x, it.y) }
        return map
    }

    // ------------------------------------------------------------------ carving

    private fun carve(map: GameMap, rng: Random, skin: Skin, spec: CarveSpec): List<Room> {
        val rooms = mutableListOf<Room>()
        var attempts = 0
        while (rooms.size < spec.rooms && attempts < 260) {
            attempts++
            val w = spec.minW + rng.nextInt(spec.maxW - spec.minW + 1)
            val h = spec.minH + rng.nextInt(spec.maxH - spec.minH + 1)
            val x = 2 + rng.nextInt(spec.size - w - 4)
            val y = 2 + rng.nextInt(spec.size - h - 4)
            val room = Room(x, y, w, h)
            if (rooms.any { it.overlaps(room) }) continue
            rooms += room
        }
        rooms.forEach { room ->
            val style = skin.accents[rng.nextInt(skin.accents.size)]
            for (y in room.y until room.y + room.h) {
                for (x in room.x until room.x + room.w) {
                    map.walls[y * map.width + x] = 0
                    map.floorTex[y * map.width + x] = skin.floors[rng.nextInt(skin.floors.size)]
                }
            }
            // carve the room's own wall skin into the ring around it
            for (y in room.y - 1..room.y + room.h) {
                for (x in room.x - 1..room.x + room.w) {
                    if (x !in 0 until map.width || y !in 0 until map.height) continue
                    if (map.walls[y * map.width + x] != 0) map.walls[y * map.width + x] = style
                }
            }
        }
        // chain every room to the next, then loop the ends: every chamber connects
        for (i in 1 until rooms.size) {
            connect(map, rooms[i - 1], rooms[i], spec.wide)
        }
        if (rooms.size > 3) connect(map, rooms.first(), rooms.last(), spec.wide)
        return rooms
    }

    private fun connect(map: GameMap, a: Room, b: Room, wide: Boolean) {
        carvePath(map, a.cx, a.cy, b.cx, a.cy, wide)
        carvePath(map, b.cx, a.cy, b.cx, b.cy, wide)
    }

    private fun carvePath(map: GameMap, x0: Int, y0: Int, x1: Int, y1: Int, wide: Boolean) {
        val steps = maxOf(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
        for (i in 0..steps) {
            val x = x0 + (x1 - x0) * i / steps
            val y = y0 + (y1 - y0) * i / steps
            if (wide) {
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 1 until map.width - 1 || ny !in 1 until map.height - 1) continue
                    if (abs(dx) + abs(dy) <= 1) {
                        map.walls[ny * map.width + nx] = 0
                        map.floorTex[ny * map.width + nx] = Textures.FLOOR_FLAG
                    }
                }
            } else {
                if (x in 1 until map.width - 1 && y in 1 until map.height - 1) {
                    map.walls[y * map.width + x] = 0
                    map.floorTex[y * map.width + x] = Textures.FLOOR_FLAG
                }
            }
        }
    }

    // ------------------------------------------------------------------ pieces

    private const val SOUTH = 1.5708f
    private const val NORTHEAST = -0.7854f
    private const val DOOR_ARC = 0.16f

    /** Distance between two angles, wrapped to a half turn. */
    private fun angDist(a: Float, b: Float): Float {
        val d = abs(a - b) % 6.28318f
        return if (d > 3.14159f) 6.28318f - d else d
    }

    private fun openGround(seed: Long, size: Int, name: String): GameMap {
        val map = GameMap(size, size, outdoor = true, title = name)
        map.walls.fill(0)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val n = Textures.valueNoise(x * 0.11f, y * 0.11f, 21)
                map.floorTex[y * size + x] =
                    if (n < 0.34f) Textures.FLOOR_MUD else Textures.FLOOR_GRASS
            }
        }
        for (i in 0 until size) {
            map.walls[i] = Textures.WALL_RUIN
            map.walls[(size - 1) * size + i] = Textures.WALL_RUIN
            map.walls[i * size] = Textures.WALL_RUIN
            map.walls[i * size + size - 1] = Textures.WALL_RUIN
        }
        return map
    }

    /**
     * A ring of wall with doors cut through it. Each door becomes a portal down
     * with an arrival spot just outside: descend here, climb out here, always.
     */
    private fun openDoors(
        map: GameMap,
        site: Site,
        labels: List<String>,
        angles: List<Float>,
        cx: Int,
        cy: Int,
        rx: Int,
        ry: Int,
        ringWall: Int,
        skin: Skin
    ): List<Pair<Float, Float>> {
        for (i in 0 until 360) {
            val a = i * 6.28318f / 360
            if (angles.any { angDist(a, it) < DOOR_ARC }) continue
            for (rr in 0..1) {
                val x = (cx + cos(a) * (rx + rr)).roundToInt()
                val y = (cy + sin(a) * (ry + rr)).roundToInt()
                if (x in 1 until map.width - 1 && y in 1 until map.height - 1) {
                    map.walls[y * map.width + x] = ringWall
                }
            }
        }
        // gate-posts framing each door
        angles.forEach { a ->
            for (s in listOf(-DOOR_ARC - 0.06f, DOOR_ARC + 0.06f)) {
                val x = (cx + cos(a + s) * (rx + 0.5f)).roundToInt()
                val y = (cy + sin(a + s) * (ry + 0.5f)).roundToInt()
                if (x in 1 until map.width - 1 && y in 1 until map.height - 1) {
                    map.walls[y * map.width + x] = skin.gateWall
                }
            }
        }
        angles.forEachIndexed { i, a ->
            val px = cx + cos(a) * (rx + 2.6f)
            val py = cy + sin(a) * (ry + 2.6f)
            map.portals += Portal(
                px, py, labels[i], "Descend into ${site.name}", 1, i, down = true
            )
            map.arrivalSpots += Pair(px, py)
        }
        map.spawnX = cx + cos(angles[0]) * (rx + 2.6f)
        map.spawnY = cy + sin(angles[0]) * (ry + 2.6f)
        map.spawnAngle = angles[0] + 3.14159f
        return angles.map { Pair(cx + cos(it) * (rx + 2.6f), cy + sin(it) * (ry + 2.6f)) }
    }

    private fun floorTitle(name: String, floor: Int): String = when (floor) {
        1 -> name
        2 -> "$name, the second depth"
        3 -> "$name, the third depth"
        else -> "$name, the ${floor}th depth"
    }

    private fun scatterProps(
        map: GameMap,
        rng: Random,
        count: Int,
        avoid: List<Pair<Float, Float>>,
        pool: List<Pair<Int, Float>>
    ) {
        repeat(count) {
            val x = (2 + rng.nextInt(map.width - 4)) + rng.nextFloat()
            val y = (2 + rng.nextInt(map.height - 4)) + rng.nextFloat()
            if (map.isWall(x, y)) return@repeat
            if (avoid.any { MapFactory.distance(x, y, it.first, it.second) < 2.4f }) return@repeat
            val (sprite, height) = pool[rng.nextInt(pool.size)]
            map.entities += Entity(
                x = x, y = y, spriteId = sprite, kind = EntityKind.PROP,
                height = height, name = "scenery"
            )
        }
    }

    /**
     * A beast's heart key, drawn from where it stands — so a heart is dealt
     * without spending a single roll of the generation's own rng, and the
     * map rolls byte-for-byte what it rolled before hearts existed.
     */
    private fun beastHeartKey(x: Float, y: Float): Long =
        (x.toRawBits().toLong() shl 32) or (y.toRawBits().toLong() and 0xffffffffL)

    private fun looseEnemy(
        map: GameMap,
        rng: Random,
        avoid: List<Pair<Float, Float>>,
        levelBase: Int,
        roster: ClassRoster?,
        cultureId: Int,
        geography: MaterialGeography?
    ) {
        repeat(20) {
            val x = (2 + rng.nextInt(map.width - 4)) + rng.nextFloat()
            val y = (2 + rng.nextInt(map.height - 4)) + rng.nextFloat()
            if (map.isWall(x, y)) return@repeat
            if (avoid.any { MapFactory.distance(x, y, it.first, it.second) < 3f }) return@repeat
            map.entities += MapFactory.rollEnemy(
                rng, x, y, levelBase, roster, cultureId, geography,
                heartKey = beastHeartKey(x, y)
            )
            return
        }
    }

    private fun roomEnemy(
        map: GameMap,
        rng: Random,
        room: Room,
        levelBase: Int,
        roster: ClassRoster?,
        cultureId: Int,
        geography: MaterialGeography?
    ) {
        val ex = room.x + 1 + rng.nextFloat() * (room.w - 2)
        val ey = room.y + 1 + rng.nextFloat() * (room.h - 2)
        map.entities += MapFactory.rollEnemy(
            rng, ex, ey, levelBase, roster, cultureId, geography,
            heartKey = beastHeartKey(ex, ey)
        )
    }

    private fun roomBrazier(map: GameMap, rng: Random, room: Room) {
        map.entities += Entity(
            x = room.x + 0.6f + rng.nextFloat() * (room.w - 1.2f),
            y = room.y + 0.6f + rng.nextFloat() * (room.h - 1.2f),
            spriteId = Sprites.BRAZIER, kind = EntityKind.PROP, height = 0.8f, name = "brazier"
        )
    }

    /** The boss: named, bigger, meaner, and marked so the world remembers its death. */
    private fun bossEntity(
        rng: Random,
        spec: BossSpec,
        level: Int,
        x: Float,
        y: Float,
        roster: ClassRoster?,
        cultureId: Int,
        geography: MaterialGeography?,
        beastly: Boolean
    ): Entity {
        val boss = MapFactory.rollEnemy(rng, x, y, level, roster, cultureId, geography)
        boss.name = spec.name
        boss.personality = PersonalityBook.dealBeast(
            spec.name.hashCode().toLong() * 1000003L + level, spec.name
        )
        boss.boss = true
        boss.spriteId = if (beastly) Sprites.HOUND else Sprites.HUSK
        boss.height = if (beastly) 1.35f else 1.5f
        boss.speed = (boss.speed * 0.85f).coerceAtLeast(0.45f)
        boss.maxHp = boss.maxHp * 3
        boss.hp = boss.maxHp
        boss.damage += (boss.damage * 0.4f).roundToInt().coerceAtLeast(1)
        return boss
    }

    /** What the boss guards: a chest worth the whole descent. */
    private fun hoard(
        rng: Random,
        x: Float,
        y: Float,
        name: String,
        level: Int,
        cultureId: Int,
        geography: MaterialGeography?,
        spriteId: Int,
        height: Float
    ): Entity {
        val chest = Entity(
            x = x, y = y, spriteId = spriteId, kind = EntityKind.PROP,
            height = height, name = name, container = true
        )
        repeat(2 + rng.nextInt(2)) {
            val (items, coins) = Loot.containerContents(rng, level + 2, underground = true, cultureId, geography)
            chest.loot += items
            chest.lootBrass += coins * 3 + level * 2
        }
        return chest
    }
}
