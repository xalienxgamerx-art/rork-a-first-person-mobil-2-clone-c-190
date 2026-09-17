package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Rumor
import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.World
import com.rork.hollowmarch.world.isSettlement
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * World Simulation v1 — the province goes on without you.
 *
 * Granularity follows distance from the player, so nothing is simulated every
 * frame: the souls of the ground you stand on are stirred by the hour, souls
 * elsewhere by the day, and factions and settlements by the day. The frame loop
 * still owns whatever is loaded and awake; this owns everything that is not.
 *
 * Every roll here is seeded from the world seed and the calendar, never from
 * runtime luck: the same absence produces the same province on your return.
 */
object WorldSimulation {

    /** The most hours the near simulation will walk through in one advance. */
    private const val MAX_NEAR_HOURS = 72

    /** The most days the distant simulation will walk through in one advance. */
    private const val MAX_FAR_DAYS = 120

    /** What an absence did to the province, for the log and the chronicle. */
    data class Report(
        val hours: Int,
        val days: Int,
        val moved: Int,
        val notes: List<String>,
        val rumors: List<Rumor>
    ) {
        val quiet: Boolean get() = moved == 0 && notes.isEmpty() && rumors.isEmpty()

        /**
         * Two advances of one journey read as one: the road's hours and the
         * gate's quarter hour are the same walk, and its reckoning is the sum.
         */
        operator fun plus(other: Report): Report = Report(
            hours = hours + other.hours,
            days = days + other.days,
            moved = moved + other.moved,
            notes = notes + other.notes,
            rumors = rumors + other.rumors
        )

        companion object {
            val NONE = Report(0, 0, 0, emptyList(), emptyList())
        }
    }

    /** What a soul is doing at a given hour: the shape of an ordinary day. */
    fun activityAt(hour: Int, homeless: Boolean): String = when {
        hour in 0..5 -> if (homeless) "huddled out of the wind" else "asleep behind a barred door"
        hour in 6..8 -> "drawing water at the well"
        hour in 9..12 -> "at work in the daylight"
        hour in 13..16 -> "trading what it has"
        hour in 17..19 -> "back along the street toward its door"
        else -> if (homeless) "keeping to the heart of the place" else "home for the night"
    }

    /** True when the hour sends a soul with a door of its own home. */
    fun homeAt(hour: Int): Boolean = hour >= 20 || hour < 6

    /**
     * Carry the world forward from [state]'s last simulated moment to [minutes].
     * Called when meaningful time passes — travel, rest, the descent of hours —
     * never per frame.
     */
    fun advance(
        state: WorldState,
        world: World,
        settlements: SettlementLedger,
        minutes: Float,
        playerSiteId: Int
    ): Report {
        val from = state.lastSimMinutes
        if (minutes <= from) {
            state.lastSimMinutes = maxOf(from, minutes)
            return Report.NONE
        }
        val fromHour = (from / 60f).toInt()
        val toHour = (minutes / 60f).toInt()
        val hours = toHour - fromHour
        val fromDay = (from / 1440f).toInt()
        val toDay = (minutes / 1440f).toInt()
        val days = toDay - fromDay
        val notes = mutableListOf<String>()
        val rumors = mutableListOf<Rumor>()

        val moved = stirSouls(state, world, from, minutes, playerSiteId, notes)
        if (days > 0) {
            turnFactions(state, world, fromDay, toDay, notes, rumors)
            turnSettlements(state, world, settlements, fromDay, toDay, notes, rumors)
            markWeather(state, world, fromDay, toDay, minutes, notes)
        }
        state.lastSimMinutes = minutes
        return Report(hours.coerceAtLeast(0), days.coerceAtLeast(0), moved, notes, rumors)
    }

    // ------------------------------------------------------------------ souls

    /**
     * Souls go about their day. The ground the player stands on is stirred hour
     * by hour; everywhere else moves at the day's pace, which is all anyone can
     * see of a place they are not standing in.
     */
    private fun stirSouls(
        state: WorldState,
        world: World,
        from: Float,
        to: Float,
        playerSiteId: Int,
        notes: MutableList<String>
    ): Int {
        val living = state.livingNpcs()
        if (living.isEmpty()) return 0
        val endHour = ((to % 1440f) / 60f).toInt()
        val nearHours = ((to - from) / 60f).toInt().coerceIn(0, MAX_NEAR_HOURS)
        val farDays = ((to - from) / 1440f).toInt().coerceIn(0, MAX_FAR_DAYS)
        var moved = 0
        living.forEach { npc ->
            val near = npc.siteId == playerSiteId
            // Nearby souls answer the hour; distant ones only the day.
            val steps = if (near) nearHours else farDays
            if (steps <= 0) return@forEach
            val before = npc.x to npc.y
            val wasDoing = npc.activity
            // A soul with a trade keeps its trade's own day; the common round otherwise.
            val schedule = if (npc.role.isNotBlank() && ROLES.isValid(npc.role)) {
                WorkSchedule.forRole(npc.role, WorkSchedule.key(world.seed, npc.id))
            } else {
                null
            }
            npc.activity = schedule?.activityAt(endHour.toFloat(), npc.homeBuilding < 0)
                ?: activityAt(endHour, npc.homeBuilding < 0)
            // Where the hour puts it: home draws it in, the day sends it out.
            val rng = Random(
                world.seed * 15485863L + npc.id.hashCode() * 31L + (to / 60f).toLong() * 7919L
            )
            val homeNow = npc.homeBuilding >= 0 &&
                (homeAt(endHour) || schedule?.isHomeAt(endHour.toFloat()) == true)
            if (homeNow) {
                // Home is its anchor: generation set it down at its own threshold.
                npc.x = npc.anchorX
                npc.y = npc.anchorY
            } else {
                val angle = rng.nextFloat() * 6.28318f
                val reach = 1f + rng.nextFloat() * 3.4f
                npc.x = npc.anchorX + cos(angle) * reach
                npc.y = npc.anchorY + sin(angle) * reach
            }
            if (before.first != npc.x || before.second != npc.y) moved++
            if (near && wasDoing != npc.activity) {
                state.record(
                    WorldChange(to, WorldChangeKind.MOVE, npc.id, "${npc.name} is ${npc.activity}")
                )
            }
        }
        if (moved > 0 && playerSiteId >= 0) {
            world.sites.firstOrNull { it.id == playerSiteId }?.takeIf { it.isSettlement }?.let {
                notes += "The folk of ${it.name} have gone about their day without you."
            }
        }
        return moved
    }

    // --------------------------------------------------------------- factions

    /**
     * Powers press their business at the day's pace. Pressure accrues from the
     * seed and the calendar; when a power has pressed long enough, the roads
     * carry word of it.
     */
    private fun turnFactions(
        state: WorldState,
        world: World,
        fromDay: Int,
        toDay: Int,
        notes: MutableList<String>,
        rumors: MutableList<Rumor>
    ) {
        val powers = world.powers.filter { !it.extinct }
        if (powers.isEmpty()) return
        val days = (toDay - fromDay).coerceAtMost(MAX_FAR_DAYS)
        powers.forEach { power ->
            var pressure = state.factionPressure[power.id] ?: 0
            for (d in 1..days) {
                val day = fromDay + d
                val rng = Random(world.seed * 32452843L + power.id * 100003L + day * 7907L)
                pressure += when {
                    power.hostileByNature -> 2 + rng.nextInt(3)
                    power.strength > 60 -> 1 + rng.nextInt(3)
                    else -> rng.nextInt(2)
                }
                if (pressure >= 40) {
                    pressure = 0
                    val seat = power.capitalSiteId
                        ?: world.sites.firstOrNull { it.holderPowerId == power.id }?.id
                        ?: -1
                    val goal = power.goals.firstOrNull() ?: power.creed
                    val text = "${power.name} presses its business: $goal"
                    state.record(
                        WorldChange(day * 1440f, WorldChangeKind.FACTION, "${power.id}", text)
                    )
                    notes += text
                    rumors += Rumor(
                        text = "\"${power.name} has been busy while you were on the road.\"",
                        source = "the brass road",
                        daysOld = 0,
                        aboutPlayer = false,
                        siteId = seat
                    )
                }
            }
            state.factionPressure[power.id] = pressure
        }
    }

    // ------------------------------------------------------------ settlements

    /**
     * Settlements stir at the day's pace: their graves are dug, their markets
     * keep their days. The years themselves are still the ledger's business.
     */
    private fun turnSettlements(
        state: WorldState,
        world: World,
        settlements: SettlementLedger,
        fromDay: Int,
        toDay: Int,
        notes: MutableList<String>,
        rumors: MutableList<Rumor>
    ) {
        val steads = world.sites.filter { it.isSettlement && !it.ruined }
        if (steads.isEmpty()) return
        val day = toDay
        val rng = Random(world.seed * 49979687L + day * 104729L)
        val stead = steads[rng.nextInt(steads.size)]
        val folk = settlements.folkOf(stead)
        val text = when (rng.nextInt(3)) {
            0 -> "${stead.name} held its market day; ${folk} souls keep it still."
            1 -> "${stead.name} buried its dead and got on with the season."
            else -> "${stead.name} mended its walls against the season."
        }
        state.record(WorldChange(day * 1440f, WorldChangeKind.SETTLEMENT, "${stead.id}", text))
        notes += text
        if ((toDay - fromDay) >= 2) {
            rumors += Rumor(
                text = "\"Word from ${stead.name}: $text\"",
                source = "the road to ${stead.name}",
                daysOld = 0,
                aboutPlayer = false,
                siteId = stead.id
            )
        }
    }

    // --------------------------------------------------------------- weather

    /** The front that ruled the day you arrive is noted when it is not the one you left. */
    private fun markWeather(
        state: WorldState,
        world: World,
        fromDay: Int,
        toDay: Int,
        minutes: Float,
        notes: MutableList<String>
    ) {
        val before = Weather.forDay(world.seed, fromDay + 1)
        val after = Weather.forDay(world.seed, toDay + 1)
        if (before == after) return
        val text = "The sky turned from ${before.label} to ${after.label} while you walked."
        state.record(WorldChange(minutes, WorldChangeKind.WEATHER, "${toDay + 1}", text))
        notes += text
    }

    /** How long a walk of so many leagues asks, at a walker's pace in this weather. */
    fun travelHours(leagues: Float, rain: Float): Float {
        val pace = rainPacing(rain).first
        return leagues / (2.1f * pace)
    }

    /** What a walk of so many hours costs a walker in weariness. */
    fun travelFatigue(hours: Float): Int = (hours * 2.4f).roundToInt().coerceAtLeast(1)

    /** The country a walk crosses, for the encounter roll: leagues to chances. */
    fun encounterRolls(leagues: Float): Int = (leagues / 6f).toInt().coerceIn(0, 6)

    /** A site's seat on the open province, in the cells the overland map uses. */
    fun overlandSpot(site: Site, map: GameMap): Pair<Float, Float> =
        (site.x * (map.width - 1) + 0.5f) to (site.y * (map.height - 1) + 0.5f)
}
