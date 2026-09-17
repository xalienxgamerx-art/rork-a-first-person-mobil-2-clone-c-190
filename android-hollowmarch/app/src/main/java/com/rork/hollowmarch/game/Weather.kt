package com.rork.hollowmarch.game

import kotlin.random.Random

enum class WeatherKind(val label: String) {
    CLEAR("clear skies"),
    HAZE("a high haze"),
    OVERCAST("overcast"),
    RAIN("rain")
}

/** One sampled moment of weather: what the sky is doing right now. */
data class WeatherState(
    val kind: WeatherKind,
    /** 0 (bare sky) to 1 (thick cloud). */
    val cloud: Float,
    /** 0 (dry) to 1 (pouring). */
    val rain: Float,
    /** Extra ground fog, thick in the marsh and under rain. */
    val fogBoost: Float
)

/**
 * The weather of a world: fronts rolled per in-game day from the seed and the
 * calendar, easing one into the next across the small hours. Nothing here is
 * left to runtime luck — same day, same sky.
 */
object Weather {

    fun daySeed(worldSeed: Long, day: Int): Long = worldSeed * 1000003L + day * 7919L

    /** The front that rules a given day. */
    fun forDay(worldSeed: Long, day: Int): WeatherKind {
        val roll = Random(daySeed(worldSeed, day)).nextFloat()
        return when {
            roll < 0.42f -> WeatherKind.CLEAR
            roll < 0.68f -> WeatherKind.HAZE
            roll < 0.88f -> WeatherKind.OVERCAST
            else -> WeatherKind.RAIN
        }
    }

    private val CLOUD = floatArrayOf(0f, 0.35f, 0.75f, 0.95f)
    private val RAIN = floatArrayOf(0f, 0f, 0f, 0.85f)

    /**
     * The weather at a moment: yesterday's front eases into today's across the
     * small hours (roughly two to six), so nothing flips at dawn. In the marsh
     * the air itself thickens; rain gathers only once the cloud has.
     */
    fun at(worldSeed: Long, day: Int, timeOfDay: Float, inMarsh: Boolean): WeatherState {
        val today = forDay(worldSeed, day)
        val yesterday = forDay(worldSeed, day - 1)
        val change = ((timeOfDay - 0.08f) / 0.17f).coerceIn(0f, 1f)
        val cloud = CLOUD[yesterday.ordinal] + (CLOUD[today.ordinal] - CLOUD[yesterday.ordinal]) * change
        val rain = RAIN[yesterday.ordinal] + (RAIN[today.ordinal] - RAIN[yesterday.ordinal]) * change
        val rainAmt = rain * ((cloud - 0.5f) / 0.45f).coerceIn(0f, 1f)
        val fogBoost = (if (inMarsh) 0.15f + 0.30f * cloud else 0f) + rainAmt * 0.25f
        val kind = when {
            rainAmt > 0.25f -> WeatherKind.RAIN
            cloud > 0.55f -> WeatherKind.OVERCAST
            cloud > 0.2f -> WeatherKind.HAZE
            else -> WeatherKind.CLEAR
        }
        return WeatherState(kind, cloud, rainAmt, fogBoost.coerceIn(0f, 0.8f))
    }
}
