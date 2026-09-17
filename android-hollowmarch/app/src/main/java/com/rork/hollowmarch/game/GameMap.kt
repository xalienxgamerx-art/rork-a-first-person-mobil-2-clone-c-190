package com.rork.hollowmarch.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class EntityKind { ENEMY, PROP }

/** Whether a creature has made you: blind to you, searching, or on you. */
enum class Detection { UNAWARE, SEARCHING, AWARE }

/** A stair, a door, a way between floors: where you stand to pass, and where you land beyond. */
data class Portal(
    val x: Float,
    val y: Float,
    val label: String,
    val prompt: String,
    val targetFloor: Int,
    val arrivalIndex: Int,
    val down: Boolean,
    /** A way into another place entirely — the landmark of a site on the open ground. */
    val targetSiteId: Int = -1,
    /** A building's door: it bars the walk, and answers the USE hand from a pace or two. */
    val door: Boolean = false
)

/** A billboard in the world: a husk that wants you dead, or a reed clump that does not. */
class Entity(
    var x: Float,
    var y: Float,
    var spriteId: Int,
    var kind: EntityKind,
    var height: Float,
    var name: String = "",
    var hp: Int = 0,
    var maxHp: Int = 0,
    var damage: Int = 0,
    var speed: Float = 0f,
    var alive: Boolean = true,
    var hurtFlash: Float = 0f,
    var attackCooldown: Float = 0f,
    var wanderPhase: Float = 0f,
    var level: Int = 1,
    val klass: ActorClass? = null,
    val stats: StatBlock? = null,
    var xp: Float = 0f,
    var loot: MutableList<Item> = mutableListOf(),
    var lootBrass: Int = 0,
    val container: Boolean = false,
    var boss: Boolean = false,
    val skills: Growth = Growth.forClass(null),
    /** How the creature's hand knows each family of arm; armed where its life put arms in it. */
    var proficiencies: Proficiencies = Proficiencies.forClass(null),
    /** How familiar the creature is with the particular pieces it carries, by their marks. */
    var masteries: Masteries = Masteries.empty(),
    var risesThisLife: Int = 0,
    var detection: Detection = Detection.UNAWARE,
    var equipment: Equipment? = null,
    /** A great beast of the chronicle, met out in the open: its world id, when slain. */
    var beastId: Int = -1,
    /** A harmless soul met on the road: word for the hearing, a small kindness. */
    var traveler: Boolean = false,
    /** A named soul of a living place: streets by day, home by night, never the first to fight. */
    var resident: Boolean = false,
    /** The building this soul calls home, or -1 for the homeless. */
    var homeBuilding: Int = -1,
    /** The trade this soul was dealt: a role from the registry, blank for the untraded. */
    var role: String = "",
    /** The building this soul works at, or -1 when the work has no door of its own. */
    var workBuilding: Int = -1,
    /** The heart under the trade: dealt once at generation, kept through every save. */
    var personality: Personality? = null,
    /** The soul's idle stroll target on the streets. */
    var wanderX: Float = 0f,
    var wanderY: Float = 0f,
    /**
     * The world's own name for this thing: a stable, deterministic id, assigned
     * by [SceneBinder.stamp] when the scene is built. Blank for scenery and for
     * souls met in passing, which the world does not remember. Never a given
     * name — two souls called the same thing are two different souls.
     */
    var persistId: String = "",
    /** A shield-bearer's raised guard: transient combat state, never saved. */
    var blocking: Boolean = false,
    /** How far the board has eased up into the guard, 0..1: transient, never saved. */
    var guardRaise: Float = 0f,
    /** Where the creature faces, in radians — the board's say in where blocks land. */
    var facingAngle: Float = 0f,
    /** What shot this archer still carries, honest and finite; 0 sends it closing for the melee. */
    var ammoCount: Int = 0
)

/** A real building on the ground: walls, a floor, a door — and exactly this footprint. */
data class BuildingFootprint(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val doorX: Int,
    val doorY: Int,
    /** What the place is: a house, a tavern, a temple, a keep. */
    val kind: String,
    val name: String,
    /** The named soul who keeps this building, when one is remembered. */
    val keeper: String = ""
)

/** A pitched roof over a building: two slopes from eave to ridge, gables of wall stone. */
data class Roof(
    val x0: Int,
    val y0: Int,
    /** The footprint's far edges, exclusive. */
    val x1: Int,
    val y1: Int,
    /** The eave's height in wall-heights — where the masonry stops. */
    val eave: Float,
    /** The ridge's height — half a wall above the eave. */
    val ridge: Float,
    /** True when the ridge runs east-west along the long wall. */
    val alongX: Boolean,
    val wallTex: Int,
    val roofTex: Int
)

class GameMap(
    val width: Int,
    val height: Int,
    val outdoor: Boolean,
    val title: String
) {
    val walls = IntArray(width * height)
    val floorTex = IntArray(width * height)

    /** How tall the masonry stands, per cell, in wall-heights. Null: every wall is one. */
    var wallHeights: FloatArray? = null

    /**
     * The roof's height over every cell, in wall-heights — the deep places'
     * cavernous halls and low crawlways. Null draws the old flat roof, one course up.
     */
    var ceilingHeights: FloatArray? = null

    /**
     * The land's height at every lattice corner — a (width+1) x (height+1) field
     * of wall-heights — when the open ground rolls. Null keeps the world flat:
     * every interior reads zero and draws exactly as before.
     */
    var heights: FloatArray? = null
    val explored = BooleanArray(width * height)
    private var exploredCount = 0
    private var openCount = -1
    val entities = mutableListOf<Entity>()

    /** The buildings that stand here, each with the footprint its walls keep. */
    val buildings = mutableListOf<BuildingFootprint>()
    /** The pitched roofs over those buildings, drawn by the polygon renderer. */
    val roofs = mutableListOf<Roof>()

    var spawnX = 2f
    var spawnY = 2f
    var spawnAngle = 0f

    /** Every way through: doors down, stairs up, one per seam between floors. */
    val portals = mutableListOf<Portal>()

    /**
     * Where you land when a door sends you here — arrivalSpots[i] answers the
     * door whose arrivalIndex is i. Same seed, same landings, forever.
     */
    val arrivalSpots = mutableListOf<Pair<Float, Float>>()

    /** Where you stand when you walk out of a site into open country: site id to spot. */
    val entrySpots = mutableMapOf<Int, Pair<Float, Float>>()

    fun tileAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return Textures.WALL_STONE
        return walls[y * width + x]
    }

    /** The stone's height at a cell: 0 in the open, 1 unless the builders raised it. */
    fun wallHeight(x: Int, y: Int): Float {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0f
        if (walls[y * width + x] == 0) return 0f
        return wallHeights?.get(y * width + x) ?: 1f
    }

    fun isWall(x: Float, y: Float): Boolean = tileAt(x.toInt(), y.toInt()) != 0

    /** A building's doorway bars the shoulder: doors open to the USE hand, not the walk. */
    fun doorwayAt(x: Float, y: Float): Boolean =
        portals.any { it.door && it.x.toInt() == x.toInt() && it.y.toInt() == y.toInt() }

    fun floorAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return Textures.FLOOR_FLAG
        return floorTex[y * width + x]
    }

    /** The land's height under a point: the lattice, bilinearly woven; flat ground reads zero. */
    fun heightAt(x: Float, y: Float): Float {
        val hts = heights ?: return 0f
        val vw = width + 1
        val fx = x.coerceIn(0f, width.toFloat())
        val fy = y.coerceIn(0f, height.toFloat())
        val x0 = fx.toInt().coerceAtMost(width - 1)
        val y0 = fy.toInt().coerceAtMost(height - 1)
        val tx = fx - x0
        val ty = fy - y0
        val i = y0 * vw + x0
        val top = hts[i] + (hts[i + 1] - hts[i]) * tx
        val bottom = hts[i + vw] + (hts[i + vw + 1] - hts[i + vw]) * tx
        return top + (bottom - top) * ty
    }

    fun markExplored(x: Int, y: Int) {
        for (dy in -2..2) for (dx in -2..2) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until width && ny in 0 until height) {
                val idx = ny * width + nx
                if (!explored[idx]) {
                    explored[idx] = true
                    exploredCount++
                }
            }
        }
    }

    fun exploredFraction(): Float {
        // counted incrementally: a whole province must not be swept every frame
        if (openCount < 0) {
            var open = 0
            for (w in walls) if (w == 0) open++
            openCount = open
        }
        return if (openCount == 0) 0f else exploredCount.toFloat() / openCount
    }
}

internal class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
    val cx: Int get() = x + w / 2
    val cy: Int get() = y + h / 2
    fun overlaps(other: Room): Boolean =
        x - 1 < other.x + other.w && x + w + 1 > other.x &&
            y - 1 < other.y + other.h && y + h + 1 > other.y
}

object MapFactory {

    /** A grave or urn that may hold what the living left with the dead. */
    internal fun container(
        rng: Random,
        x: Float,
        y: Float,
        spriteId: Int,
        height: Float,
        name: String,
        depth: Int,
        chanceOfLoot: Int,
        cultureId: Int,
        geography: MaterialGeography? = null
    ): Entity {
        val entity = Entity(
            x = x, y = y, spriteId = spriteId, kind = EntityKind.PROP,
            height = height, name = name, container = true
        )
        if (rng.nextInt(chanceOfLoot) == 0) {
            val (items, coins) = Loot.containerContents(rng, depth, underground = true, cultureId, geography)
            entity.loot += items
            entity.lootBrass = coins
        }
        return entity
    }

    /**
     * Roll a creature of the province: a random class weighted by what it is,
     * stats rolled for its level, and every number derived from those stats.
     * Without a roster it falls back to the old hardcoded formulas.
     */
    fun rollEnemy(
        rng: Random,
        x: Float,
        y: Float,
        levelBase: Int,
        roster: ClassRoster? = null,
        cultureId: Int = -1,
        geography: MaterialGeography? = null,
        heartKey: Long? = null
    ): Entity {
        val roll = rng.nextInt(10)
        val name = when {
            roll < 5 -> "husk"
            roll < 8 -> "barrow hound"
            else -> "grave-wraith"
        }
        val spriteId = when (name) {
            "husk" -> Sprites.HUSK
            "barrow hound" -> Sprites.HOUND
            else -> Sprites.WRAITH
        }
        val height = when (name) {
            "husk" -> 1.0f
            "barrow hound" -> 0.62f
            else -> 1.15f
        }
        val baseSpeed = when (name) {
            "husk" -> 0.85f
            "barrow hound" -> 1.5f
            else -> 0.7f
        }
        val level = levelBase.coerceAtLeast(1) + rng.nextInt(2)
        val weights = CREATURE_WEIGHTS[name] ?: DEFAULT_WEIGHTS
        val klass = roster?.byKey(weightedStringPick(weights, rng) ?: "")
        val stats = klass?.let { rollNpcStats(level, it.weights, rng) }
        val skills = Growth.forClass(klass).also { it.level = level }
        if (klass == null || stats == null) {
            val entity = when (name) {
                "husk" -> Entity(
                    x = x, y = y, spriteId = spriteId, kind = EntityKind.ENEMY, height = height,
                    name = name, hp = 16 + levelBase * 4, maxHp = 16 + levelBase * 4,
                    damage = 4 + levelBase, speed = baseSpeed, level = level, skills = skills
                )
                "barrow hound" -> Entity(
                    x = x, y = y, spriteId = spriteId, kind = EntityKind.ENEMY, height = height,
                    name = name, hp = 11 + levelBase * 3, maxHp = 11 + levelBase * 3,
                    damage = 3 + levelBase, speed = baseSpeed, level = level, skills = skills
                )
                else -> Entity(
                    x = x, y = y, spriteId = spriteId, kind = EntityKind.ENEMY, height = height,
                    name = name, hp = 22 + levelBase * 5, maxHp = 22 + levelBase * 5,
                    damage = 7 + levelBase, speed = baseSpeed, level = level, skills = skills
                )
            }
            heartKey?.let { entity.personality = PersonalityBook.dealBeast(it, name) }
            armLoot(entity, null, level, cultureId, rng, geography)
            return entity
        }
        val entity = Entity(
            x = x, y = y, spriteId = spriteId, kind = EntityKind.ENEMY, height = height,
            name = name,
            hp = Derived.npcMaxHp(stats, skills, level), maxHp = Derived.npcMaxHp(stats, skills, level),
            damage = Derived.npcDamage(stats, skills, level), speed = Derived.npcSpeed(stats, skills, baseSpeed),
            level = level, klass = klass, stats = stats, skills = skills
        )
        heartKey?.let { entity.personality = PersonalityBook.dealBeast(it, name) }
        armLoot(entity, klass, level, cultureId, rng, geography)
        return entity
    }

    /**
     * Roll what the creature carries, once, at spawn — the same seed, the same
     * spoils. Humanoids dress in their kit: weapon in hand, armor on the bones,
     * and what they don't wear left in their pockets. Hounds carry no arms;
     * what they have is what they ate.
     */
    fun armLoot(
        entity: Entity,
        klass: ActorClass?,
        level: Int,
        cultureId: Int,
        rng: Random,
        geography: MaterialGeography? = null
    ) {
        if (entity.spriteId == Sprites.HOUND) {
            entity.lootBrass = Loot.brass(rng, level) / 2
            // Hounds fight with what they were born carrying.
            entity.proficiencies = Proficiencies.forNpc(entity.level, null, klass)
            return
        }
        val equipment = Equipment.empty()
        val carried = mutableListOf<Item>()
        Loot.kit(rng, klass, level, cultureId, geography).forEach { item ->
            // Ammunition becomes the quiver it shoots from: honest, finite, spent by the loosing.
            if (item.archetype.slot == ItemSlot.AMMUNITION) {
                entity.ammoCount += item.count
                return@forEach
            }
            // Trinkets are pockets, not protection; they ride the loot.
            val result = if (item.archetype.slot == ItemSlot.TRINKET) null else equipment.equip(item)
            if (result != null) result.second.forEach { carried += it } else carried += item
        }
        entity.equipment = equipment
        entity.loot += carried
        entity.lootBrass = Loot.brass(rng, level)
        // The arm it carries decides where its years of practice went.
        entity.proficiencies = Proficiencies.forNpc(entity.level, equipment.bestWeapon(), klass)
        val stats = entity.stats
        if (stats != null) {
            entity.damage = Derived.npcDamage(stats, entity.skills, entity.level, equipment.bestWeapon())
        }
    }

    /** Which of the province's callings each kind of creature tends toward. */
    val CREATURE_WEIGHTS = mapOf(
        "husk" to mapOf("warden" to 3, "bulwark" to 2, "ascetic" to 2, "skulk" to 1),
        "barrow hound" to mapOf("wayfarer" to 3, "skulk" to 2),
        "grave-wraith" to mapOf("hedgewitch" to 3, "caller" to 2, "cantor" to 1)
    )

    private val DEFAULT_WEIGHTS = Attr.entries.associate { it.name.lowercase() to 1 }

    /** Pick a key by weight, deterministically under the given rng. */
    fun weightedStringPick(weights: Map<String, Int>, rng: Random): String? {
        val total = weights.values.sum()
        if (total <= 0) return null
        var roll = rng.nextInt(total)
        for ((key, weight) in weights) {
            roll -= weight
            if (roll < 0) return key
        }
        return weights.keys.first()
    }

    /** Marsh, reeds, a ruined arch, roadside graves and the old brass road running toward town. */
    fun wilds(
        seed: Long,
        name: String,
        roster: ClassRoster? = null,
        day: Int = 1,
        cultureId: Int = -1,
        geography: MaterialGeography? = null
    ): GameMap {
        val rng = Random(seed * 977 + 13)
        val size = 64
        val map = GameMap(size, size, outdoor = true, title = name)
        map.walls.fill(0)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val n = Textures.valueNoise(x * 0.11f, y * 0.11f, 21)
                map.floorTex[y * size + x] = when {
                    n < 0.34f -> Textures.FLOOR_MUD
                    else -> Textures.FLOOR_GRASS
                }
            }
        }

        // impassable border of standing rock so the province has edges
        for (i in 0 until size) {
            map.walls[i] = Textures.WALL_RUIN
            map.walls[(size - 1) * size + i] = Textures.WALL_RUIN
            map.walls[i * size] = Textures.WALL_RUIN
            map.walls[i * size + size - 1] = Textures.WALL_RUIN
        }

        val arcX = size / 2
        val arcY = size - 8
        // the barrow arch you climb out of
        for (dx in -3..3) {
            if (abs(dx) >= 2) {
                map.walls[arcY * size + (arcX + dx)] = Textures.WALL_RUIN
                map.walls[(arcY + 1) * size + (arcX + dx)] = Textures.WALL_RUIN
            }
        }
        map.walls[(arcY + 2) * size + arcX] = 0
        map.spawnX = arcX + 0.5f
        map.spawnY = arcY + 2.5f
        map.spawnAngle = -1.5708f

        // the old brass road: a winding track toward the far gate
        var roadX = arcX.toFloat()
        for (y in arcY downTo 2) {
            roadX += (rng.nextFloat() - 0.5f) * 1.6f
            roadX = roadX.coerceIn(6f, (size - 7).toFloat())
            for (dx in -1..1) {
                val rx = (roadX + dx).toInt()
                if (rx in 1 until size - 1) map.floorTex[y * size + rx] = Textures.FLOOR_ROAD
            }
        }

        // ruins, stones, trees and reeds scattered by the land's own logic
        repeat(70) {
            val x = 2 + rng.nextInt(size - 4)
            val y = 2 + rng.nextInt(size - 4)
            if (map.floorTex[y * size + x] == Textures.FLOOR_ROAD) return@repeat
            if (abs(x - arcX) < 4 && abs(y - arcY) < 5) return@repeat
            val sprite = when (rng.nextInt(10)) {
                in 0..4 -> Sprites.REEDS
                in 5..6 -> Sprites.DEAD_TREE
                7, 8 -> Sprites.STANDING_STONE
                else -> Sprites.GRAVE
            }
            val height = when (sprite) {
                Sprites.DEAD_TREE -> 2.6f
                Sprites.STANDING_STONE -> 1.6f
                Sprites.REEDS -> 0.9f
                else -> 0.6f
            }
            if (sprite == Sprites.GRAVE && rng.nextInt(4) == 0) {
                map.entities += container(
                    rng, x + rng.nextFloat(), y + rng.nextFloat(), Sprites.GRAVE, height,
                    "grave", 1, chanceOfLoot = 2, cultureId, geography
                )
            } else {
                map.entities += Entity(
                    x = x + rng.nextFloat(),
                    y = y + rng.nextFloat(),
                    spriteId = sprite,
                    kind = EntityKind.PROP,
                    height = height,
                    name = "scenery"
                )
            }
        }

        // broken walls of an older holdfast
        repeat(6) {
            val x = 4 + rng.nextInt(size - 12)
            val y = 4 + rng.nextInt(size - 16)
            val len = 3 + rng.nextInt(6)
            val horizontal = rng.nextBoolean()
            for (i in 0 until len) {
                if (rng.nextInt(4) == 0) continue
                val nx = if (horizontal) x + i else x
                val ny = if (horizontal) y else y + i
                if (nx in 1 until size - 1 && ny in 1 until size - 1) {
                    map.walls[ny * size + nx] = Textures.WALL_RUIN
                }
            }
        }

        repeat(5) {
            var ex: Int
            var ey: Int
            do {
                ex = 4 + rng.nextInt(size - 8)
                ey = 4 + rng.nextInt(size - 20)
            } while (map.walls[ey * size + ex] != 0)
            map.entities += rollEnemy(
                rng, ex + 0.5f, ey + 0.5f, 1 + day / 25, roster, cultureId, geography,
                heartKey = seed * 8191L + 400L + it
            )
        }

        // the wild land rolls too, born from the same seed as everything else
        Landform.buildHeights(map, seed, { _, _ -> 0.8f })

        // clear anything sitting inside a wall
        map.entities.removeAll { map.isWall(it.x, it.y) }
        return map
    }

    /** Slide along walls instead of sticking to them. */
    fun tryMove(map: GameMap, x: Float, y: Float, dx: Float, dy: Float): Pair<Float, Float> {
        val pad = 0.22f
        var nx = x
        var ny = y
        val probeX = x + dx + sign(dx) * pad
        val probeY = y + dy + sign(dy) * pad
        if (!map.isWall(probeX, y) && !map.doorwayAt(probeX, y)) nx = x + dx
        if (!map.isWall(x, probeY) && !map.doorwayAt(x, probeY)) ny = y + dy
        return Pair(
            nx.coerceIn(1.2f, map.width - 1.2f),
            ny.coerceIn(1.2f, map.height - 1.2f)
        )
    }

    private fun sign(v: Float): Float = if (v >= 0f) 1f else -1f

    fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun clampIndex(v: Int, size: Int): Int = min(max(v, 0), size - 1)
}
