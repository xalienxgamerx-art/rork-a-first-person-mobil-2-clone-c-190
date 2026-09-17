package com.rork.hollowmarch.game

import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/** One falling star: where it starts on the dome, where it heads, and how old it is. */
data class Meteor(
    val az: Float,
    val el: Float,
    val dirAz: Float,
    val dirEl: Float,
    /** 0..1 through its brief life. */
    val age: Float,
    val bright: Float
)

/**
 * The rare lights of a world: meteor showers on certain nights, a lone falling
 * star now and then, and the aurora over the far north. Every answer here is a
 * pure function of the seed, the day and the clock — no runtime luck.
 */
object SkyEvents {

    fun eventSeed(worldSeed: Long, day: Int): Long = worldSeed * 7717L + day * 2749L

    /** True on the few scattered nights a month when the sky rains falling stars. */
    fun showerNight(worldSeed: Long, day: Int): Boolean =
        Random(eventSeed(worldSeed, day)).nextFloat() < 0.07f

    /** The aurora nights: rare, and only worth the walk in the far north. */
    fun auroraNight(worldSeed: Long, day: Int): Boolean =
        Random(eventSeed(worldSeed, day) + 101L).nextFloat() < 0.10f

    /**
     * How strong the aurora stands over a spot. Latitude runs 0 at the south
     * edge of the province to 1 at the north rim; nothing shows south of the
     * far third, and thick cloud snuffs the whole show.
     */
    fun auroraStrength(worldSeed: Long, day: Int, latitude: Float, cloud: Float): Float {
        if (!auroraNight(worldSeed, day) || cloud > 0.55f) return 0f
        val north = ((latitude - 0.67f) / 0.33f).coerceIn(0f, 1f)
        if (north <= 0f) return 0f
        val flicker = 0.75f + 0.25f * sin(day * 3.7f + worldSeed % 7)
        return (north * flicker).coerceIn(0f, 1f)
    }

    /**
     * The falling stars of a moment. A shower night drives them thick; on other
     * clear nights a lone one drifts down now and then. Each lives about a
     * second and a half, hashed from the animation clock.
     */
    fun meteors(worldSeed: Long, day: Int, clock: Float, isNight: Boolean, cloud: Float): List<Meteor> {
        if (!isNight || cloud > 0.6f) return emptyList()
        val shower = showerNight(worldSeed, day)
        val bucket = floor(clock / 1.6f).toLong()
        val out = mutableListOf<Meteor>()
        val tries = if (shower) 3 else 1
        for (i in 0 until tries) {
            val rng = Random(bucket * 131L + i * 977L + worldSeed)
            val chance = if (shower) 0.9f else 0.06f
            if (rng.nextFloat() > chance) continue
            out += Meteor(
                az = rng.nextFloat() * 6.28318f,
                el = 0.25f + rng.nextFloat() * 0.9f,
                dirAz = (rng.nextFloat() - 0.5f) * 0.8f,
                dirEl = -(0.3f + rng.nextFloat() * 0.6f),
                age = ((clock - bucket * 1.6f) / 1.6f).coerceIn(0f, 1f),
                bright = 0.7f + rng.nextFloat() * 0.3f
            )
        }
        return out
    }
}
