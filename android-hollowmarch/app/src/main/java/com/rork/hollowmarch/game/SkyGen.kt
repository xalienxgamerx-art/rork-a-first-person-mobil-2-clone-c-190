package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.World
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sin
import kotlin.random.Random

/**
 * The heavens of one world: its moons, its fixed stars, the river of the galaxy,
 * and the lore constellations the chronicle claims. Same seed, same sky, forever.
 */
data class Sky(
    val moons: List<SkyMoon>,
    val stars: List<SkyStar>,
    val bandTilt: Float,
    val bandBearing: Float,
    val constellations: List<Constellation>,
    val noiseSeed: Int
)

/** One moon: its size and tint, its rising hour, and the pace of its phase. */
data class SkyMoon(
    val size: Float,
    val tint: Int,
    val darkTint: Int,
    /** Rise-time offset as a fraction of the day: when this moon climbs. */
    val offset: Float,
    /** Days for a full turn of phases. */
    val periodDays: Float,
    val phase0: Float,
    /** What the chroniclers call this moon's color: bone, rust, ash, jade, blood, old gold. */
    val tintName: String
)

/** A fixed star on the dome: where it hangs, how bright, what color. */
data class SkyStar(
    val az: Float,
    val el: Float,
    val bright: Float,
    val color: Int,
    val twinkle: Float
)

/** A lore constellation: a shape of stars, named from the world's own record. */
data class Constellation(
    val name: String,
    val lore: String,
    val stars: List<SkyStar>
)

object SkyGen {

    fun skySeed(world: World): Long = world.seed * 32452843L + 15485863L

    /** The moons of a seed, readable before the World exists — worldgen writes omen text from these. */
    fun moonsFor(seed: Long): List<SkyMoon> = buildMoons(Random(seed * 32452843L + 15485863L))

    // ---------------------------------------------------------------- sun path

    /** The sun's elevation in radians at a fraction of the day (0.25 = dawn, 0.5 = noon). */
    fun sunElevation(t: Float): Float = sin(2f * PI.toFloat() * (t - 0.25f)) * 1.05f

    /** The sun's bearing in radians: 0 (east) at dawn through π (west) at dusk. */
    fun sunAzimuth(t: Float): Float = PI.toFloat() * ((t - 0.25f) / 0.5f)

    // ---------------------------------------------------------------- build

    fun build(world: World): Sky {
        val rng = Random(skySeed(world))
        return Sky(
            moons = buildMoons(rng),
            stars = buildStars(rng),
            bandTilt = (rng.nextFloat() - 0.5f) * 1.4f,
            bandBearing = rng.nextFloat() * 6.28318f,
            constellations = buildConstellations(world, rng),
            noiseSeed = 40 + rng.nextInt(200)
        )
    }

    private val MOON_TINTS = listOf(
        Triple(0xD8CBB0, 0x6E6552, "bone"),
        Triple(0xB06A3E, 0x5A3220, "rust"),
        Triple(0x9A9A94, 0x4A4A46, "ash"),
        Triple(0x7FA98F, 0x3C5A48, "jade"),
        Triple(0xA84438, 0x521F18, "blood"),
        Triple(0xC9A24B, 0x6B5423, "old gold")
    )

    private fun buildMoons(rng: Random): List<SkyMoon> {
        val count = 1 + rng.nextInt(3)
        val tints = MOON_TINTS.shuffled(rng)
        return (0 until count).map { i ->
            val tint = tints[i % tints.size]
            SkyMoon(
                size = 0.045f + rng.nextFloat() * 0.05f,
                tint = tint.first,
                darkTint = tint.second,
                tintName = tint.third,
                offset = rng.nextFloat(),
                periodDays = 12f + rng.nextFloat() * 28f,
                phase0 = rng.nextFloat() * 6.28318f
            )
        }
    }

    private val STAR_COLORS = buildList {
        repeat(14) { add(0xCDD3DC) } // pale white
        repeat(3) { add(0xD8C48A) }  // gold
        repeat(2) { add(0x9FB4D8) }  // blue
        add(0xC97B5A)                // red giant
    }

    private fun buildStars(rng: Random): List<SkyStar> {
        val colors = STAR_COLORS
        return List(260) {
            val bright = 0.25f + rng.nextFloat() * rng.nextFloat() * rng.nextFloat() * 3f
            SkyStar(
                az = rng.nextFloat() * 6.28318f,
                // uniform-ish on the dome: low stars far outnumber the high ones
                el = asin(rng.nextFloat() * 0.985f),
                bright = bright.coerceIn(0.25f, 1f),
                color = colors[rng.nextInt(colors.size)],
                twinkle = rng.nextFloat() * 6.28318f
            )
        }
    }

    private val DEITY_NAMES = arrayOf("%s's Crown", "%s's Loom", "the %s Gate", "%s's Torch")
    private val FIGURE_NAMES = arrayOf("the %s Chase", "%s's Wain", "the %s Bear", "the %s Road")
    private val EVENT_NAMES = arrayOf("the %s Star", "the %s Sign")

    private val LORE_LINES = arrayOf(
        "Steer by %s when the night is kind.",
        "%s stands highest at the shearing moon.",
        "The old pilots swear %s fell the night the world turned.",
        "%s is the first up and the last down.",
        "Sailors read weather in %s."
    )

    /** Constellations named from the world's gods, its great dead, and its weighty years. */
    private fun buildConstellations(world: World, rng: Random): List<Constellation> {
        data class Naming(val label: String, val patterns: Array<String>)

        // A famous comet or eclipse is drawn up into the stars ahead of everything else.
        val omenPool = world.events
            .filter { it.kind == com.rork.hollowmarch.world.EventKind.COMET ||
                it.kind == com.rork.hollowmarch.world.EventKind.ECLIPSE }
            .shuffled(rng)
            .take(2)
            .map { omen ->
                Naming(
                    omen.subject.ifBlank { omen.kind.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                    EVENT_NAMES
                )
            }

        val pool = omenPool + buildList {
            world.deities.forEach { deity ->
                add(Naming(deity.name, DEITY_NAMES))
            }
            world.figures.filter { it.diedYear != null }.forEach { figure ->
                add(Naming(figure.name, FIGURE_NAMES))
            }
            world.events
                .filter { it.kind in WEIGHTY_SKY_KINDS }
                .forEach { event ->
                    add(Naming(event.kind.name.lowercase().replaceFirstChar { it.uppercase() }, EVENT_NAMES))
                }
        }.shuffled(rng)

        val count = (4 + rng.nextInt(4)).coerceAtMost(pool.size)
        return (0 until count).map { i ->
            val naming = pool[i]
            Constellation(
                name = naming.patterns[rng.nextInt(naming.patterns.size)].format(naming.label),
                lore = LORE_LINES[rng.nextInt(LORE_LINES.size)].format(naming.label),
                stars = constellationShape(rng)
            )
        }
    }

    private val WEIGHTY_SKY_KINDS = setOf(
        com.rork.hollowmarch.world.EventKind.WAR,
        com.rork.hollowmarch.world.EventKind.PLAGUE,
        com.rork.hollowmarch.world.EventKind.SEALING,
        com.rork.hollowmarch.world.EventKind.DESTRUCTION,
        com.rork.hollowmarch.world.EventKind.PROPHECY,
        com.rork.hollowmarch.world.EventKind.FOUNDING,
        com.rork.hollowmarch.world.EventKind.COMET,
        com.rork.hollowmarch.world.EventKind.ECLIPSE
    )

    /** A small rough ring or zigzag of bright stars somewhere on the dome. */
    private fun constellationShape(rng: Random): List<SkyStar> {
        val centerAz = rng.nextFloat() * 6.28318f
        val centerEl = 0.30f + rng.nextFloat() * 0.9f
        val count = 5 + rng.nextInt(4)
        var az = centerAz
        var el = centerEl
        return List(count) { i ->
            az += (rng.nextFloat() - 0.5f) * 0.16f
            el = (el + (rng.nextFloat() - 0.5f) * 0.14f).coerceIn(0.06f, 1.45f)
            SkyStar(
                az = (az + 6.28318f) % 6.28318f,
                el = el,
                bright = 0.8f + (if (i == 0) 0.2f else 0f),
                color = 0xCDD3DC,
                twinkle = rng.nextFloat() * 6.28318f
            )
        }
    }
}
