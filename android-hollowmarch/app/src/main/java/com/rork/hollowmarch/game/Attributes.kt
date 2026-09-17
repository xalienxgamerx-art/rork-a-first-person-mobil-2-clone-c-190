package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.World
import kotlin.math.roundToInt
import kotlin.random.Random

/** The eight attributes of every living thing in the province, and what each governs. */
enum class Attr(val label: String, val governs: String) {
    MIGHT("Might", "physical power"),
    VIGOR("Vigor", "health, stamina, resilience"),
    FINESSE("Finesse", "coordination, precision"),
    SWIFTNESS("Swiftness", "speed, reflexes, movement"),
    WISDOM("Wisdom", "perception, judgment, experience"),
    INTELLECT("Intellect", "reasoning, knowledge, learning"),
    PRESENCE("Presence", "social influence, personality, command"),
    FORTUNE("Fortune", "luck, chance, fate")
}

/**
 * A creature's eight numbers, clamped 1..[StatBlock.MAX] and encoded whole for
 * the save. Player, delver and every spawned creature holds one.
 */
class StatBlock(values: Map<Attr, Int> = emptyMap()) {

    private val stats = mutableMapOf<Attr, Int>()

    init {
        Attr.entries.forEach { attr ->
            stats[attr] = (values[attr] ?: BASE).coerceIn(1, MAX)
        }
    }

    operator fun get(attr: Attr): Int = stats[attr] ?: BASE

    fun set(attr: Attr, value: Int) {
        stats[attr] = value.coerceIn(1, MAX)
    }

    /** Move a stat by [delta], staying inside the bounds; used by level-ups and point buy. */
    fun adjust(attr: Attr, delta: Int) {
        set(attr, get(attr) + delta)
    }

    fun total(): Int = stats.values.sum()

    fun copy(): StatBlock = StatBlock(stats)

    /** Compact form for the save; keys are stable enum names, values plain ints. */
    fun encode(): String = Attr.entries.joinToString(ENTRY) { "${it.name}$FIELD${get(it)}" }

    companion object {
        const val BASE = 6
        const val MAX = 20

        private const val ENTRY = "\u001E"
        private const val FIELD = "="

        /** Every stat at the base — a blank slate with no story yet. */
        fun balanced(): StatBlock = StatBlock()

        /** Restore from [encode]; malformed strings come back null, never broken. */
        fun fromEncoded(raw: String?): StatBlock? {
            if (raw.isNullOrBlank()) return null
            val values = mutableMapOf<Attr, Int>()
            raw.split(ENTRY).forEach { piece ->
                val parts = piece.split(FIELD, limit = 2)
                if (parts.size != 2) return@forEach
                val attr = Attr.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
                val value = parts[1].toIntOrNull() ?: return@forEach
                values[attr] = value.coerceIn(1, MAX)
            }
            // Nothing survived: the string was not a stat block at all.
            return if (values.isEmpty()) null else StatBlock(values)
        }
    }
}

/** One of the eight ways of life the province knows: a stable key and an attribute weighting. */
data class ActorClass(
    val key: String,
    val name: String,
    val weights: Map<Attr, Int>
)

/**
 * The province's own roster: the eight archetypes every fighting soul draws from,
 * wearing names this seed alone produced. The same world always names them the same.
 */
class ClassRoster(world: World) {

    val classes: List<ActorClass>

    init {
        val rng = Random(world.seed * 104729L + 61)
        val taken = mutableSetOf<String>()
        classes = ARCHETYPES.map { (key, weights, roles) ->
            var name = rollClassName(rng, roles)
            var guard = 24
            while (name in taken && guard-- > 0) name = rollClassName(rng, roles)
            taken += name
            ActorClass(key = key, name = name, weights = weights)
        }
    }

    fun byKey(key: String?): ActorClass? = classes.firstOrNull { it.key == key }

    companion object {
        private val EPITHETS = listOf(
            "Barrow", "Verdigris", "Ashen", "Hollow", "Brass", "Pale",
            "Marsh", "Gloam", "Rust", "Salt", "Wicker", "Grave", "Fen", "Lantern"
        )
        private val SUFFIXES = listOf(
            "-Sworn", "-bound", "-marked", "-taught", "-bred", "-kindled", "-blooded"
        )

        private fun rollClassName(rng: Random, roles: List<String>): String {
            val epithet = EPITHETS[rng.nextInt(EPITHETS.size)]
            val role = roles[rng.nextInt(roles.size)]
            return if (rng.nextInt(3) == 0) {
                "$epithet $role"
            } else {
                "$epithet${SUFFIXES[rng.nextInt(SUFFIXES.size)]} $role"
            }
        }

        /** key, attribute weights, and the roles its procedural names draw from. */
        private val ARCHETYPES = listOf(
            Triple(
                "warden",
                mapOf(Attr.MIGHT to 3, Attr.VIGOR to 3, Attr.FINESSE to 1),
                listOf("Reaver", "Blade", "Breaker", "Bulwark")
            ),
            Triple(
                "skulk",
                mapOf(Attr.FINESSE to 3, Attr.SWIFTNESS to 3, Attr.FORTUNE to 1),
                listOf("Knife", "Skulk", "Cutter", "Pick")
            ),
            Triple(
                "caller",
                mapOf(Attr.INTELLECT to 3, Attr.WISDOM to 2),
                listOf("Caller", "Weaver", "Singer", "Reader")
            ),
            Triple(
                "cantor",
                mapOf(Attr.PRESENCE to 3, Attr.WISDOM to 2),
                listOf("Cantor", "Vicar", "Hymnist", "Pallbearer")
            ),
            Triple(
                "wayfarer",
                mapOf(Attr.SWIFTNESS to 3, Attr.WISDOM to 2),
                listOf("Wayfarer", "Stalker", "Marsh-Ranger", "Tracker")
            ),
            Triple(
                "bulwark",
                mapOf(Attr.VIGOR to 3, Attr.PRESENCE to 2, Attr.MIGHT to 1),
                listOf("Warder", "Sentinel", "Garrison-Knight", "Shield")
            ),
            Triple(
                "hedgewitch",
                mapOf(Attr.INTELLECT to 2, Attr.PRESENCE to 1, Attr.FORTUNE to 2),
                listOf("Warlock", "Hexer", "Hedge-Witch", "Pact-Maker")
            ),
            Triple(
                "ascetic",
                mapOf(Attr.FINESSE to 2, Attr.VIGOR to 2, Attr.SWIFTNESS to 1),
                listOf("Ascetic", "Fist", "Penitent", "Flagellant")
            )
        )
    }
}

/**
 * Roll a creature's stats for its [level] under a class [weights] profile: a base
 * block plus a small budget spread by weight, deterministic under the given [rng].
 */
fun rollNpcStats(level: Int, weights: Map<Attr, Int>, rng: Random): StatBlock {
    val stats = StatBlock.balanced()
    var budget = 4 + level.coerceAtLeast(1) * 2
    val cap = 18
    var guard = budget * 20 + 64
    while (budget > 0 && guard-- > 0) {
        val attr = weightedPick(weights, rng) ?: break
        if (stats[attr] < cap) {
            stats.adjust(attr, 1)
            budget--
        }
    }
    return stats
}

/** Pick an attribute by weight, deterministically under the given rng. */
fun weightedPick(weights: Map<Attr, Int>, rng: Random): Attr? {
    if (weights.isEmpty()) return Attr.entries[rng.nextInt(Attr.entries.size)]
    // Jitter keeps the weights honest without making them mechanical.
    val scored = weights.entries.map { (attr, weight) ->
        attr to weight * (0.6f + rng.nextFloat() * 0.8f)
    }
    val total = scored.sumOf { it.second.toDouble() }
    if (total <= 0.0) return null
    var roll = rng.nextDouble() * total
    for ((attr, score) in scored) {
        roll -= score.toDouble()
        if (roll <= 0.0) return attr
    }
    return scored.last().first
}

/** Average of one attribute across many rolls — a test and tuning aid, not gameplay. */
fun averageStat(level: Int, weights: Map<Attr, Int>, attr: Attr, rolls: Int = 200, seed: Long = 7L): Float {
    var sum = 0
    repeat(rolls) { i ->
        sum += rollNpcStats(level, weights, Random(seed * 977L + i))[attr]
    }
    return sum.toFloat() / rolls
}
