package com.rork.hollowmarch.world

/** A generated people: language, homeland, values, taboos and the craft they are known for. */
data class Culture(
    val id: Int,
    val name: String,
    val adjective: String,
    val epithet: String,
    val craft: String,
    val homeland: String,
    val values: List<String> = emptyList(),
    val taboo: String = ""
)

enum class PowerKind(val label: String) {
    ORDER("order"),
    CULT("cult"),
    HOLDFAST("holdfast"),
    GUILD("guild"),
    WARBAND("warband")
}

/** A faction that survived worldgen and still acts in the province. */
data class Power(
    val id: Int,
    val name: String,
    val kind: PowerKind,
    val cultureId: Int,
    val foundedYear: Int,
    val splinterFromId: Int?,
    val creed: String,
    val hostileByNature: Boolean,
    val capitalSiteId: Int? = null,
    val deityId: Int? = null,
    val population: Int = 0,
    val strength: Int = 0,
    val wealth: Int = 0,
    val relations: Map<Int, Int> = emptyMap(),
    val goals: List<String> = emptyList(),
    val extinct: Boolean = false
)

enum class SiteKind(val label: String) {
    CAPITAL("capital"),
    CITY("city"),
    TOWN("town"),
    VILLAGE("village"),
    HOLDFAST("holdfast"),
    CAMP("camp"),
    RUIN("ruin"),
    BARROW("barrow"),
    VAULT("vault"),
    SHRINE("shrine")
}

data class Site(
    val id: Int,
    val name: String,
    val kind: SiteKind,
    val x: Float,
    val y: Float,
    val holderPowerId: Int?,
    val note: String,
    val foundedYear: Int = 0,
    val population: Int = 0,
    val sackedCount: Int = 0,
    val ruined: Boolean = false,
    val structures: List<Structure> = emptyList(),
    val stability: Int = 100,
    val loyalty: Int = 100,
    val garrison: Int = 0,
    val influences: List<FactionInfluence> = emptyList()
)

enum class StructureKind(val label: String) {
    TEMPLE("temple"),
    TAVERN("tavern"),
    MARKET("market"),
    GUILD("guild hall"),
    CATACOMB("catacomb")
}

/** A named building inside a settlement, with the person history remembers keeping it. */
data class Structure(
    val id: Int,
    val siteId: Int,
    val kind: StructureKind,
    val name: String,
    val foundedYear: Int,
    val keeperFigureId: Int? = null,
    val ruined: Boolean = false
)

/** A place people actually live, as opposed to dungeons and standing stones. */
val Site.isSettlement: Boolean
    get() = kind in setOf(
        SiteKind.CAPITAL, SiteKind.CITY, SiteKind.TOWN,
        SiteKind.VILLAGE, SiteKind.HOLDFAST
    )

enum class EventKind {
    FOUNDING, GROWTH, WAR, BATTLE, TREATY, SCHISM, PLAGUE, FAMINE, FLOOD, SEALING,
    PROPHECY, DEATH, SUCCESSION, MIGRATION, RUIN, DESTRUCTION, ARTIFACT, BEAST, PACT,
    TAVERN, CHARTER, GUILDHALL, CONSECRATION, CATACOMB,
    CLAIM, MARRIAGE, REBELLION, PLOT,
    COMET, ECLIPSE, MOONWONDER
}

data class ChronicleEvent(
    val year: Int,
    val kind: EventKind,
    val text: String,
    /** The event's named subject — a comet's coined name, when it has one. */
    val subject: String = ""
)

data class Age(val name: String, val startYear: Int, val endYear: Int)

/** A god of one people's pantheon. */
data class Deity(
    val id: Int,
    val name: String,
    val cultureId: Int,
    val domain: String,
    val epithet: String
)

/** A historical figure: lineage, titles, and the deeds the chroniclers kept. */
data class Figure(
    val name: String,
    val cultureId: Int,
    val bornYear: Int,
    val diedYear: Int?,
    val title: String,
    val powerId: Int? = null,
    val parentIds: List<Int> = emptyList(),
    val feats: List<String> = emptyList(),
    val houseId: Int? = null,
    val spouseId: Int? = null,
    val friends: List<Int> = emptyList(),
    val rivals: List<Int> = emptyList(),
    val deathCause: String? = null
) {
    val lifespan: Int? get() = diedYear?.let { it - bornYear }
}

/** A noble house: a named bloodline whose members press claims and contest seats. */
data class House(
    val id: Int,
    val name: String,
    val cultureId: Int,
    val foundedYear: Int,
    val headFigureId: Int?,
    val patronPowerId: Int? = null,
    val extinct: Boolean = false
)

/** A pressed right to a settlement: who claims it, how strongly, and why. */
data class Claim(
    val id: Int,
    val siteId: Int,
    val claimantPowerId: Int?,
    val claimantFigureId: Int?,
    val strength: Int,
    val origin: String,
    val madeYear: Int
)

/** One faction's slice of a settlement's politics, in shares of a hundred. */
data class FactionInfluence(val label: String, val share: Int)

/** A war between two powers, with its battles and how it ended. */
data class War(
    val id: Int,
    val attackerId: Int,
    val defenderId: Int,
    val cause: String,
    val startYear: Int,
    val endYear: Int?,
    val battles: List<Battle>,
    val outcome: String
) {
    val totalDead: Int get() = battles.sumOf { it.dead }
}

data class Battle(
    val year: Int,
    val siteId: Int,
    val attackerId: Int,
    val defenderId: Int,
    val attackerGeneralId: Int?,
    val defenderGeneralId: Int?,
    val dead: Int,
    val attackerWon: Boolean
)

/** An artifact made by a named hand, and where history lost it. */
data class Artifact(
    val id: Int,
    val name: String,
    val kind: String,
    val makerId: Int,
    val madeYear: Int,
    val keeperSiteId: Int?,
    val whereabouts: String
)

/** A megabeast or night-creature that woke during worldgen. */
data class Beast(
    val id: Int,
    val name: String,
    val kind: String,
    val lairSiteId: Int,
    val wokeYear: Int,
    val slainYear: Int?,
    val slayerId: Int?,
    val raids: Int
) {
    val alive: Boolean get() = slainYear == null
}

data class Rumor(
    val text: String,
    val source: String,
    val daysOld: Int,
    val aboutPlayer: Boolean,
    /** The place the rumor names, if it names one: the seed of a pin on the Bearings sheet. */
    val siteId: Int = -1
)

// ---------------------------------------------------------------------------
// Terrain
// ---------------------------------------------------------------------------

enum class Biome { OCEAN, MARSH, MOOR, DOWNS, FOREST, HILLS, PEAK }

data class RiverPoint(val x: Float, val y: Float)

data class River(val name: String, val points: List<RiverPoint>)

/** The province's land itself: height, wetness, and the rivers cut into it. */
class TerrainMap(
    val size: Int,
    val heights: FloatArray,
    val moisture: FloatArray,
    val rivers: List<River>
) {
    fun heightAt(nx: Float, ny: Float): Float {
        val cx = (nx.coerceIn(0f, 0.999f) * size).toInt()
        val cy = (ny.coerceIn(0f, 0.999f) * size).toInt()
        return heights[cy * size + cx]
    }

    private fun moistureAt(nx: Float, ny: Float): Float {
        val cx = (nx.coerceIn(0f, 0.999f) * size).toInt()
        val cy = (ny.coerceIn(0f, 0.999f) * size).toInt()
        return moisture[cy * size + cx]
    }

    fun biomeAt(nx: Float, ny: Float): Biome {
        val h = heightAt(nx, ny)
        val m = moistureAt(nx, ny)
        return classify(h, m)
    }

    companion object {
        fun classify(h: Float, m: Float): Biome = when {
            h < 0.34f -> Biome.OCEAN
            h < 0.42f -> if (m > 0.55f) Biome.MARSH else Biome.MOOR
            h < 0.60f -> if (m > 0.48f) Biome.FOREST else Biome.DOWNS
            h < 0.76f -> Biome.HILLS
            else -> Biome.PEAK
        }
    }
}

// ---------------------------------------------------------------------------
// The world
// ---------------------------------------------------------------------------

/** Everything worldgen produced for one seed. Deterministic: same seed, same province. */
data class World(
    val seed: Long,
    val seedCode: String,
    val provinceName: String,
    val ages: List<Age>,
    val cultures: List<Culture>,
    val powers: List<Power>,
    val figures: List<Figure>,
    val deities: List<Deity>,
    val events: List<ChronicleEvent>,
    val sites: List<Site>,
    val wars: List<War>,
    val artifacts: List<Artifact>,
    val beasts: List<Beast>,
    val houses: List<House> = emptyList(),
    val claims: List<Claim> = emptyList(),
    val terrain: TerrainMap,
    val rumors: List<Rumor>,
    val currentYear: Int,
    val vaultSiteId: Int,
    val barrowSiteId: Int,
    val ruinCount: Int
) {
    val currentAge: Age get() = ages.last()

    val livingBeasts: List<Beast> get() = beasts.filter { it.alive }
    val lostArtifacts: List<Artifact> get() = artifacts.filter { it.keeperSiteId == null }

    fun power(id: Int?): Power? = powers.firstOrNull { it.id == id }

    fun site(id: Int): Site = sites.first { it.id == id }

    fun culture(id: Int): Culture = cultures.first { it.id == id }

    fun figure(id: Int?): Figure? = id?.let { figures.getOrNull(it) }

    fun deity(id: Int?): Deity? = deities.firstOrNull { it.id == id }

    fun beast(id: Int?): Beast? = beasts.firstOrNull { it.id == id }

    fun house(id: Int?): House? = id?.let { hid -> houses.firstOrNull { it.id == hid } }

    /** Every claim pressed on a settlement, strongest first. */
    fun claimsOn(siteId: Int): List<Claim> =
        claims.filter { it.siteId == siteId }.sortedByDescending { it.strength }

    fun warCount(): Int = wars.size

    /** A short line about who this power resents or trusts most, for the journal. */
    fun relationLine(power: Power): String? {
        if (power.relations.isEmpty()) return null
        val (otherId, value) = power.relations.entries.minByOrNull { kotlin.math.abs(it.value) } ?: return null
        val other = power(otherId) ?: return null
        return when {
            value <= -55 -> "sworn enemy of ${other.name}"
            value <= -20 -> "bitter rivals of ${other.name}"
            value >= 55 -> "sworn ally of ${other.name}"
            value >= 20 -> "bound by pact to ${other.name}"
            else -> null
        }
    }

    /** Three most recent weighty events — the generation log shown on the title plate. */
    fun highlightEvents(count: Int): List<ChronicleEvent> {
        val weighty = events.filter {
            it.kind == EventKind.SCHISM || it.kind == EventKind.FLOOD ||
                it.kind == EventKind.SEALING || it.kind == EventKind.FOUNDING ||
                it.kind == EventKind.WAR || it.kind == EventKind.BEAST ||
                it.kind == EventKind.ARTIFACT || it.kind == EventKind.DESTRUCTION ||
                it.kind == EventKind.COMET || it.kind == EventKind.ECLIPSE ||
                it.kind == EventKind.MOONWONDER
        }
        val pool = if (weighty.size >= count) weighty else events
        return pool.takeLast(count)
    }

    fun eventsInAge(age: Age): List<ChronicleEvent> =
        events.filter { it.year in age.startYear..age.endYear }
}

/** Player standing with a power, shown as a tick-marked bar. */
fun standingLabel(value: Int): String = when {
    value <= -60 -> "Blood-owed"
    value <= -25 -> "Hostile"
    value < 10 -> "Watchful"
    value < 45 -> "Known"
    value < 75 -> "Welcomed"
    else -> "Sworn"
}
