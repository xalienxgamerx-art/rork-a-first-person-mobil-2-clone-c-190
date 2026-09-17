package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.ChronicleEvent
import com.rork.hollowmarch.world.EventKind
import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.World
import com.rork.hollowmarch.world.isSettlement
import kotlin.random.Random

/**
 * The folk of the province's living steads: how many they are, how the years
 * raise and thin them, and what a place becomes when its folk outgrow it.
 * All arithmetic is pure and seeded — same seed, same year, same folk forever.
 */

/** The ladder of settlement stages, and the size each one builds at. */
enum class SettlementStage(
    val label: String,
    val siteKind: SiteKind,
    val minFolk: Int,
    /** How wide the yard stands on the open province, in cells from the heart. */
    val yardRadius: Int,
    /** The radius of the wall ring on the open province; 0f means unwalled. */
    val wallRadius: Float,
    /** The span of the settlement's own surface, in cells across. */
    val interiorSpan: Int
) {
    CAMP("camp", SiteKind.CAMP, 0, 3, 0f, 24),
    VILLAGE("village", SiteKind.VILLAGE, 40, 5, 0f, 32),
    TOWN("town", SiteKind.TOWN, 320, 6, 5.4f, 40),
    CITY("city", SiteKind.CITY, 2400, 8, 7.4f, 48),
    CAPITAL("capital", SiteKind.CAPITAL, 6000, 10, 9.4f, 56);

    val next: SettlementStage? get() = entries.getOrNull(ordinal + 1)
}

/** The stage a count of folk supports, from the count alone. */
fun stageFromFolk(folk: Int): SettlementStage =
    SettlementStage.entries.last { it.minFolk <= folk.coerceAtLeast(0) }

/** The rank a place was founded at; it never falls below this, however thin its folk. */
fun baseStageOf(kind: SiteKind): SettlementStage = when (kind) {
    SiteKind.CAPITAL -> SettlementStage.CAPITAL
    SiteKind.CITY -> SettlementStage.CITY
    SiteKind.TOWN -> SettlementStage.TOWN
    SiteKind.VILLAGE, SiteKind.HOLDFAST -> SettlementStage.VILLAGE
    else -> SettlementStage.CAMP
}

/** The stage a settlement stands at: the higher of its founding rank and its living folk. */
fun stageOf(site: Site, folk: Int): SettlementStage =
    maxOf(baseStageOf(site.kind), stageFromFolk(folk))

/** The kinds of year a settlement's folk can live through. */
enum class FolkYearKind { GOOD, LEAN, FAMINE, PLAGUE }

/** What one year's turn did to a settlement's folk. */
data class FolkYear(
    val folk: Int,
    val born: Int,
    /** Those the year's hardship took — souls slain by hand are counted beside them. */
    val died: Int,
    val kind: FolkYearKind,
    /** The stage the place rose into this year, if its folk carried it over a threshold. */
    val risenTo: SettlementStage?
)

/**
 * One settlement's year, rolled from the seed, the place, and the year itself —
 * never from luck. [slainThisYear] hands in the souls killed by hand since the
 * last turn; they lower the count exactly as plague does.
 */
fun folkYearStep(
    seed: Long,
    siteId: Int,
    year: Int,
    folk: Int,
    foundingStage: SettlementStage,
    slainThisYear: Int
): FolkYear {
    val rng = Random(seed * 31L + siteId * 100003L + year * 7L)
    // kind years outnumber the hard ones, so the folk slow-grows and the ladder climbs
    val kind = when (rng.nextInt(100)) {
        in 0..3 -> FolkYearKind.PLAGUE
        in 4..8 -> FolkYearKind.FAMINE
        in 9..18 -> FolkYearKind.LEAN
        else -> FolkYearKind.GOOD
    }
    val lost = when (kind) {
        FolkYearKind.PLAGUE -> folk * (10 + rng.nextInt(11)) / 100
        FolkYearKind.FAMINE -> folk * (8 + rng.nextInt(11)) / 100
        FolkYearKind.LEAN -> folk * (3 + rng.nextInt(5)) / 100
        FolkYearKind.GOOD -> 0
    }
    val born = when (kind) {
        FolkYearKind.GOOD -> (folk * (3 + rng.nextInt(5)) / 100).coerceAtLeast(1)
        FolkYearKind.LEAN -> if (folk > 0) (folk / 100).coerceAtLeast(1) else 0
        FolkYearKind.FAMINE, FolkYearKind.PLAGUE -> 0
    }
    val afterHardship = (folk + born - lost).coerceAtLeast(0)
    val afterSlain = (afterHardship - slainThisYear).coerceAtLeast(0)
    val before = maxOf(foundingStage, stageFromFolk(folk))
    val after = maxOf(foundingStage, stageFromFolk(afterSlain))
    return FolkYear(
        folk = afterSlain,
        born = born,
        died = lost,
        kind = kind,
        risenTo = if (after.ordinal > before.ordinal) after else null
    )
}

/** The chronicler's founding-style line for a place its folk raised to a new stage. */
fun stageEventText(site: Site, from: SettlementStage, to: SettlementStage, folk: Int): String =
    "${site.name} has grown — its folk, ${folk} strong, raised it from a ${from.label} to a ${to.label}."

/** What a year's turn wrote about one settlement, for the engine's chronicle and log. */
data class YearTurn(
    val site: Site,
    val events: List<ChronicleEvent>,
    val roseTo: SettlementStage?
)

/**
 * The living count of every settlement, turned year by year. Kept whole in the
 * save as appended fields; an old save without them wakes from the world's own
 * census and is deterministic from there.
 */
class SettlementLedger private constructor(
    private val state: MutableMap<Int, FolkState>,
    private val seed: Long,
    startYear: Int
) {
    data class FolkState(var folk: Int, var lastYear: Int, var graves: Int = 0)

    /** The year the ledger has been stepped to. */
    var currentYear: Int = startYear
        private set

    /** The living folk of a settlement, or the census count if the ledger has none. */
    fun folkOf(site: Site): Int = state[site.id]?.folk ?: site.population.coerceAtLeast(0)

    /** The stage a settlement stands at right now. */
    fun stageAt(site: Site): SettlementStage = stageOf(site, folkOf(site))

    /** One soul struck down on the settlement's ground this year. */
    fun recordDeath(siteId: Int) {
        state[siteId]?.graves = state[siteId]?.graves?.plus(1) ?: 0
    }

    /** Souls slain by hand since the last turn, waiting to be counted. */
    fun gravesWaiting(siteId: Int): Int = state[siteId]?.graves ?: 0

    /**
     * Turn the years forward to [year] for every living settlement, writing the
     * chronicle as it goes. Pure: the same seed and years give the same turns.
     */
    fun stepYears(year: Int, sites: List<Site>): List<YearTurn> {
        if (year <= currentYear) return emptyList()
        val turns = mutableListOf<YearTurn>()
        sites.filter { it.isSettlement && !it.ruined }.forEach { site ->
            val st = state[site.id] ?: return@forEach
            val founding = baseStageOf(site.kind)
            val events = mutableListOf<ChronicleEvent>()
            var rose: SettlementStage? = null
            while (st.lastYear < year) {
                val turnYear = st.lastYear
                st.lastYear++
                val slain = st.graves
                st.graves = 0
                val step = folkYearStep(seed, site.id, turnYear, st.folk, founding, slain)
                st.folk = step.folk
                when (step.kind) {
                    FolkYearKind.PLAGUE -> events += ChronicleEvent(
                        turnYear, EventKind.PLAGUE,
                        "Plague walked ${site.name}; ${step.died} were buried."
                    )
                    FolkYearKind.FAMINE -> events += ChronicleEvent(
                        turnYear, EventKind.FAMINE,
                        "Famine gripped ${site.name}; ${step.died} starved."
                    )
                    else -> {}
                }
                if (slain > 0) {
                    events += ChronicleEvent(
                        turnYear, EventKind.DEATH,
                        "$slain of ${site.name}'s folk were slain and buried this year."
                    )
                }
                step.risenTo?.let { risen ->
                    rose = risen
                    events += ChronicleEvent(
                        turnYear, EventKind.GROWTH,
                        stageEventText(site, maxOf(founding, stageFromFolk(step.folk - 1)), risen, step.folk)
                    )
                }
            }
            if (events.isNotEmpty()) turns += YearTurn(site, events, rose)
        }
        currentYear = year
        return turns
    }

    /** Whole ledger, packed for the save: id, folk, year, graves — in that order. */
    fun encode(): String = state.entries.joinToString("\u001E") { (id, st) ->
        "$id=${st.folk}=${st.lastYear}=${st.graves}"
    }

    companion object {
        /** A fresh ledger from the world's own census, at the world's present year. */
        fun fresh(world: World): SettlementLedger {
            val state = world.sites
                .filter { it.isSettlement && !it.ruined && it.population > 0 }
                .associate { it.id to FolkState(it.population, world.currentYear) }
            return SettlementLedger(state.toMutableMap(), world.seed, world.currentYear)
        }

        /**
         * Restore from [encode]; a blank or mangled string wakes from the world's
         * census, so old saves keep working and the years turn from where they stand.
         */
        fun fromEncoded(raw: String?, world: World): SettlementLedger {
            val ledger = fresh(world)
            if (raw.isNullOrBlank()) return ledger
            raw.split('\u001E').forEach { piece ->
                val parts = piece.split("=")
                if (parts.size < 3) return@forEach
                val id = parts[0].toIntOrNull() ?: return@forEach
                val st = ledger.state[id] ?: return@forEach
                st.folk = (parts[1].toIntOrNull() ?: st.folk).coerceAtLeast(0)
                st.lastYear = parts[2].toIntOrNull() ?: st.lastYear
                if (parts.size > 3) st.graves = (parts[3].toIntOrNull() ?: 0).coerceAtLeast(0)
            }
            return ledger
        }
    }
}
