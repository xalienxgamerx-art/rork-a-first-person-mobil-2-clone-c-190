package com.rork.hollowmarch.world

import com.rork.hollowmarch.game.SkyGen
import com.rork.hollowmarch.game.SkyMoon
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Dwarf-Fortress-flavoured world generation. A seed produces peoples with their own
 * phonetics, powers that splinter from one another, centuries of simulated history and
 * the ruins that history left behind. Same seed always rebuilds the same province.
 */
object WorldGenerator {

    private val ONSETS = listOf(
        "k", "b", "d", "g", "h", "m", "n", "r", "s", "t", "v", "th", "br", "dr",
        "gr", "kr", "sk", "st", "tr", "vr", "fl", "gl", "sh", "ch", "orr", "w"
    )
    private val NUCLEI = listOf("a", "e", "i", "o", "u", "ae", "ei", "au", "y", "ea", "ou")
    private val CODAS = listOf(
        "l", "n", "r", "s", "th", "ll", "rn", "st", "ck", "sk", "m", "g",
        "d", "nd", "rk", "lm", "tt", "ff", "sh"
    )

    private val TERRAINS = listOf(
        "the fens", "the salt flats", "the ash hills", "the barrow downs", "the drowned quarter",
        "the reed lakes", "the brass road", "the cold moors", "the black pine woods", "the chalk cliffs"
    )
    private val EPITHETS = listOf(
        "fen freeholders", "barrow-keepers", "salt-burners", "road-wardens", "reed-cutters",
        "ash-tenders", "grave-tithers", "moor-drovers", "bell-ringers", "ferry-folk", "exiled"
    )
    private val CRAFTS = listOf(
        "reed-boats", "bone lacquer", "brass casting", "salt glass", "black iron",
        "ash pottery", "rope and pitch", "carved antler", "peat vellum", "tin bells"
    )
    private val ADJECTIVES = listOf(
        "Ashen", "Verdigris", "Drowned", "Gilded", "Hollow", "Rust", "Pale", "Iron",
        "Salt", "Black", "Cinder", "Bone", "Brass", "Grey", "Silent", "Weeping"
    )
    private val NOUNS = listOf(
        "Hand", "Key", "Bell", "Lantern", "Chain", "Vigil", "Crown", "Nail",
        "Wound", "Coin", "Thorn", "Gate", "Ledger", "Mask", "Reed", "Ledger"
    )
    private val AGE_WORDS = listOf(
        "Reeds", "Brass", "Cinders", "Salt", "Wolves", "Vigils", "Ash", "Chains",
        "Bells", "the Quiet", "Floods", "Rust", "Lanterns", "the Long Thaw"
    )
    private val SITE_SUFFIX = listOf(
        "gate", "mill", "ford", "hollow", "reach", "fell", "mere", "wick", "stead", "moor"
    )
    private val CREEDS = listOf(
        "that debts outlive the dead", "that the roads are unclean", "that fire cleans what water spoils",
        "that every grave is owed a name", "that brass remembers what men forget",
        "that the drowned should not be counted", "that a sworn word binds three generations",
        "that no vault should be opened twice"
    )
    private val VALUES = listOf(
        "endurance", "honest debts", "silence", "hospitality", "blood-price",
        "patience", "sworn oaths", "thrift", "vengeance", "tidiness of graves"
    )
    private val TABOOS = listOf(
        "naming the drowned", "eating river eels", "whistling after dark",
        "burying the dead face-up", "selling iron to strangers",
        "counting the dead aloud", "crossing the ford at dusk", "burning driftwood"
    )
    private val DOMAINS = listOf(
        "the tide", "graves", "the road", "brass", "famine", "the pale moon",
        "sworn debts", "rust", "the gate", "salt", "the hunt", "quiet",
        "smoke", "the ford"
    )
    private val DEITY_TITLES = listOf(
        "Mother of", "Father of", "Keeper of", "Warden of", "Lady of", "Lord of"
    )
    private val DEATHS = listOf(
        "a fever", "a fall in the dark", "gout", "a bad winter",
        "poison no one confessed to", "a wound that reopened", "old debts"
    )
    private val WAR_CAUSES = listOf(
        "an unpaid tithe", "a desecrated shrine", "the brass road tolls", "an insult at a wedding",
        "a stolen relic", "old claims on the downs", "the grain barges", "a broken betrothal"
    )
    private val ARTIFACT_KINDS = listOf(
        "blade", "chalice", "crown", "bell", "key", "mask", "nail", "lantern"
    )
    private val BEAST_KINDS = listOf(
        "tar-wyrm", "marsh-lurker", "bone-tyrant", "grave-tithe", "fen terror", "hollow beast"
    )
    private val RUMOR_SOURCES = listOf(
        "drover", "smith", "reed-cutter", "tithe-taker", "ferryman", "bell-ringer",
        "salt-burner", "grave-digger", "pedlar", "watchman"
    )
    private val AGENDAS = listOf(
        "expand the borders", "protect the capital", "suppress unrest",
        "avenge old grudges", "grow rich", "press old claims", "end the rival line"
    )

    /**
     * Forge a province. [historyYears] sets how long the simulation runs, [maxEvents]
     * caps the chronicle — once full, history keeps living but stops being written;
     * pass 0 or less for an uncapped chronicle — and [cultureCount] fixes the number
     * of peoples (0 rolls a random three to five). No setting has a hard ceiling:
     * the forge builds whatever the caller dares to ask for. The same seed with the
     * same settings always produces the same world.
     */
    fun generate(
        seed: Long,
        historyYears: Int = 400,
        maxEvents: Int = 220,
        cultureCount: Int = 0
    ): World {
        val rng = Random(seed)
        val cultures = generateCultures(rng, cultureCount)
        val phonetics = cultures.associate { it.id to Phonetics.forCulture(rng, it.id) }

        // The land itself, from a rng derived only from the seed, so the history
        // simulation below can be re-ordered without changing the province.
        val riverNames = (0 until 5).map {
            "the ${phonetics.getValue(cultures[rng.nextInt(cultures.size)].id).word(rng, 1)}"
        }
        val terrain = generateTerrain(Random(seed * 31 + 7L), riverNames)

        // Every people keeps its own small pantheon.
        val deities = mutableListOf<Deity>()
        var nextDeityId = 0
        cultures.forEach { culture ->
            repeat(2 + rng.nextInt(3)) {
                val domain = DOMAINS.random(rng)
                deities += Deity(
                    id = nextDeityId++,
                    name = phonetics.getValue(culture.id).word(rng, 2),
                    cultureId = culture.id,
                    domain = domain,
                    epithet = "${DEITY_TITLES.random(rng)} $domain"
                )
            }
        }
        fun deity(id: Int?): Deity? = deities.firstOrNull { it.id == id }
        val patronDeity = cultures.associate { culture ->
            culture.id to deities.filter { it.cultureId == culture.id }.random(rng).id
        }

        val province = "Marches of ${cultures[0].name}"

        val totalYears = historyYears.coerceAtLeast(60)
        val ages = generateAges(rng, totalYears)

        val sites = mutableListOf<Site>()
        val powers = mutableListOf<Power>()
        val figures = mutableListOf<Figure>()
        val events = EventLedger(if (maxEvents > 0) maxEvents else Int.MAX_VALUE)
        val ruinedSiteIds = mutableSetOf<Int>()
        var nextSiteId = 0
        var nextPowerId = 0

        // --- Living politics: houses, claims, agendas and the mood of each settlement. ---
        val houses = mutableListOf<House>()
        var nextHouseId = 0
        val claims = mutableListOf<Claim>()
        var nextClaimId = 0
        val loyaltyNow = mutableMapOf<Int, Int>()
        val stabilityNow = mutableMapOf<Int, Int>()
        val garrisonNow = mutableMapOf<Int, Int>()
        val agendasNow = mutableMapOf<Int, List<String>>()
        val childOf = mutableMapOf<Int, MutableList<Int>>()
        // Successions whose passed-over blood still has its claim to press.
        val pendingContests = mutableListOf<Triple<Int, Int, Int>>()

        /** Finds open, livable ground: no sea, no peaks, no crowding the neighbours. */
        fun placeFor(rng: Random, spacing: Float): Pair<Float, Float> {
            var bestX = 0.5f
            var bestY = 0.5f
            var best = -Float.MAX_VALUE
            var found = false
            repeat(14) {
                val x = 0.10f + rng.nextFloat() * 0.80f
                val y = 0.10f + rng.nextFloat() * 0.80f
                val h = terrain.heightAt(x, y)
                if (h < 0.42f || h > 0.74f) return@repeat
                found = true
                var nearest = Float.MAX_VALUE
                for (s in sites) {
                    val dx = x - s.x
                    val dy = y - s.y
                    nearest = minOf(nearest, sqrt(dx * dx + dy * dy))
                }
                var riverSq = Float.MAX_VALUE
                for (river in terrain.rivers) {
                    for (p in river.points) {
                        val dx = x - p.x
                        val dy = y - p.y
                        riverSq = minOf(riverSq, dx * dx + dy * dy)
                    }
                }
                val score = minOf(nearest / spacing, 1f) * 10f +
                    (if (riverSq < 0.0036f) 1.5f else 0f) +
                    rng.nextFloat()
                if (score > best) {
                    best = score
                    bestX = x
                    bestY = y
                }
            }
            if (!found) {
                return 0.10f + rng.nextFloat() * 0.80f to 0.10f + rng.nextFloat() * 0.80f
            }
            return bestX to bestY
        }

        fun coinSite(
            kind: SiteKind,
            cultureId: Int,
            holder: Int?,
            note: String,
            foundedYear: Int = 0,
            forcedName: String? = null
        ): Site {
            val ph = phonetics.getValue(cultureId)
            val name = forcedName ?: when (kind) {
                SiteKind.BARROW -> "${ADJECTIVES.random(rng)} Barrow"
                SiteKind.SHRINE -> "Shrine of ${ph.word(rng, 2)}"
                SiteKind.CAMP -> "${ph.word(rng, 1)}${SITE_SUFFIX.random(rng)} Camp"
                else -> "${ph.word(rng, 1)}${SITE_SUFFIX.random(rng)}"
            }
            val (px, py) = placeFor(
                rng,
                when (kind) {
                    SiteKind.CAPITAL, SiteKind.CITY -> 0.16f
                    SiteKind.TOWN -> 0.12f
                    SiteKind.VILLAGE -> 0.085f
                    else -> 0.07f
                }
            )
            val site = Site(
                id = nextSiteId++,
                name = name,
                kind = kind,
                x = px,
                y = py,
                holderPowerId = holder,
                note = note,
                foundedYear = foundedYear,
                population = when (kind) {
                    SiteKind.CAPITAL -> 6000 + rng.nextInt(10000)
                    SiteKind.CITY -> 3000 + rng.nextInt(6000)
                    SiteKind.TOWN -> 500 + rng.nextInt(2600)
                    SiteKind.VILLAGE -> 30 + rng.nextInt(370)
                    SiteKind.HOLDFAST -> 150 + rng.nextInt(650)
                    SiteKind.CAMP -> 20 + rng.nextInt(80)
                    else -> 0
                }
            )
            sites += site
            loyaltyNow[site.id] = 62 + rng.nextInt(24)
            stabilityNow[site.id] = 60 + rng.nextInt(28)
            garrisonNow[site.id] =
                if (kind == SiteKind.CAPITAL || kind == SiteKind.CITY) 18 + rng.nextInt(20) else rng.nextInt(12)
            return site
        }

        // Lineage state: a figure's id is its index in the list.
        val rulers = mutableMapOf<Int, Int>()
        val rulerDeathAge = mutableMapOf<Int, Int>()
        val strengthNow = mutableMapOf<Int, Int>()
        val battlesWon = mutableMapOf<Int, Int>()
        val wealthNow = mutableMapOf<Int, Int>()
        val structures = mutableListOf<Structure>()
        var nextStructureId = 0
        val wars = mutableListOf<War>()
        val grudge = mutableMapOf<Pair<Int, Int>, Int>()
        val extinctPowers = mutableSetOf<Int>()
        val artifacts = mutableListOf<Artifact>()
        val beasts = mutableListOf<Beast>()
        var nextArtifactId = 0
        var nextBeastId = 0

        fun coinFigure(
            cultureId: Int,
            bornYear: Int,
            title: String,
            powerId: Int? = null,
            parentIdx: Int? = null,
            houseId: Int? = null
        ): Int {
            figures += Figure(
                name = phonetics.getValue(cultureId).word(rng, 2),
                cultureId = cultureId,
                bornYear = bornYear,
                diedYear = null,
                title = title,
                powerId = powerId,
                parentIds = parentIdx?.let { listOf(it) } ?: emptyList(),
                houseId = houseId
            )
            return figures.lastIndex
        }

        /** Two figures remember each other: friends and rivals, in small ledgers. */
        fun seedBond(a: Int, b: Int, rival: Boolean) {
            if (a == b || a < 0 || b < 0 || a >= figures.size || b >= figures.size) return
            if (rival) {
                figures[a] = figures[a].copy(rivals = (figures[a].rivals + b).distinct().takeLast(4))
                figures[b] = figures[b].copy(rivals = (figures[b].rivals + a).distinct().takeLast(4))
            } else {
                figures[a] = figures[a].copy(friends = (figures[a].friends + b).distinct().takeLast(4))
                figures[b] = figures[b].copy(friends = (figures[b].friends + a).distinct().takeLast(4))
            }
        }

        // --- Living structures: temples, taverns, markets, guild halls, catacombs. ---
        fun structuresAt(siteId: Int, kind: StructureKind? = null): List<Structure> =
            structures.filter { it.siteId == siteId && (kind == null || it.kind == kind) && !it.ruined }

        fun livingKeeper(structure: Structure): Boolean =
            structure.keeperFigureId?.let { figures[it].diedYear == null } ?: false

        fun coinKeeper(site: Site, kind: StructureKind, year: Int, cultureId: Int): Int {
            val title = when (kind) {
                StructureKind.TEMPLE -> "priest of ${site.name}"
                StructureKind.TAVERN -> "innkeeper of ${site.name}"
                StructureKind.MARKET -> "merchant of ${site.name}"
                StructureKind.GUILD -> "guildmaster of ${site.name}"
                StructureKind.CATACOMB -> "gravedigger of ${site.name}"
            }
            return coinFigure(cultureId, year - (19 + rng.nextInt(24)), title)
        }

        fun coinStructure(site: Site, kind: StructureKind, year: Int, cultureId: Int): Structure {
            val ph = phonetics.getValue(cultureId)
            val name = when (kind) {
                StructureKind.TEMPLE -> "Temple of ${deity(patronDeity[cultureId])?.name ?: ph.word(rng, 2)}"
                StructureKind.TAVERN -> "The ${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}"
                StructureKind.MARKET -> "${site.name} Market"
                StructureKind.GUILD -> "The ${ADJECTIVES.random(rng)} Hall"
                StructureKind.CATACOMB -> "The ${ADJECTIVES.random(rng)} Catacombs"
            }
            val keeperIdx = coinKeeper(site, kind, year, cultureId)
            val structure = Structure(
                id = nextStructureId++,
                siteId = site.id,
                kind = kind,
                name = name,
                foundedYear = year,
                keeperFigureId = keeperIdx
            )
            structures += structure
            val keeper = figures[keeperIdx]
            events += ChronicleEvent(
                year,
                when (kind) {
                    StructureKind.TEMPLE -> EventKind.CONSECRATION
                    StructureKind.TAVERN -> EventKind.TAVERN
                    StructureKind.MARKET -> EventKind.CHARTER
                    StructureKind.GUILD -> EventKind.GUILDHALL
                    StructureKind.CATACOMB -> EventKind.CATACOMB
                },
                when (kind) {
                    StructureKind.TEMPLE ->
                        "${keeper.name} consecrates $name at ${site.name}; the god keeps the hearth."
                    StructureKind.TAVERN ->
                        "$name opens its doors at ${site.name}; ${keeper.name} pours the first cup."
                    StructureKind.MARKET ->
                        "${site.name} is granted a market charter; ${keeper.name} keeps the scales."
                    StructureKind.GUILD ->
                        "$name is raised at ${site.name}; ${keeper.name} keeps the rolls and the grudges."
                    StructureKind.CATACOMB ->
                        "The dead of ${site.name} go under the hill; ${keeper.name} seals $name."
                }
            )
            return structure
        }

        fun coinRuler(power: Power, year: Int, houseId: Int? = null): Int {
            val idx = coinFigure(
                power.cultureId, year - (22 + rng.nextInt(26)),
                rulerTitle(power.kind), power.id, houseId = houseId
            )
            rulers[power.id] = idx
            rulerDeathAge[idx] = 46 + rng.nextInt(36)
            if (houseId != null) {
                val hIdx = houses.indexOfFirst { it.id == houseId }
                if (hIdx >= 0) houses[hIdx] = houses[hIdx].copy(headFigureId = idx)
            }
            return idx
        }

        /** A noble house takes its name; its blood will press its rights in time. */
        fun coinHouse(cultureId: Int, year: Int, patron: Int?): House {
            val house = House(
                id = nextHouseId++,
                name = "House ${phonetics.getValue(cultureId).word(rng, 1)}",
                cultureId = cultureId,
                foundedYear = year,
                headFigureId = null,
                patronPowerId = patron
            )
            houses += house
            return house
        }

        /** A ruler dies; an heir — often of their blood — takes the seat. */
        fun succeed(powerId: Int, year: Int, cause: String) {
            val oldIdx = rulers[powerId] ?: return
            val old = figures[oldIdx]
            if (old.diedYear == null) {
                figures[oldIdx] = old.copy(diedYear = year, deathCause = cause)
            }
            val power = powers.firstOrNull { it.id == powerId } ?: return
            // Temple legitimacy calms a succession; a rich guild can buy one instead.
            val seat = power.capitalSiteId
                ?.let { sid -> sites.firstOrNull { it.id == sid && !it.ruined } }
            val temple = seat?.let { s ->
                structuresAt(s.id, StructureKind.TEMPLE).firstOrNull { livingKeeper(it) }
            }
            val guildBacker = if (temple == null) {
                powers.firstOrNull {
                    it.kind == PowerKind.GUILD && it.id !in extinctPowers &&
                        it.cultureId == power.cultureId && (wealthNow[it.id] ?: 0) >= 130
                }
            } else {
                null
            }
            val usurped = guildBacker != null && rng.nextInt(4) == 0
            if (usurped && guildBacker != null) {
                wealthNow[guildBacker.id] = (wealthNow[guildBacker.id] ?: 0) - 110
                // Bought seats are bitter seats.
                seat?.let { loyaltyNow[it.id] = ((loyaltyNow[it.id] ?: 70) - (8 + rng.nextInt(8))).coerceIn(0, 100) }
            }
            // Adult children of the dead ruler can contest the seat itself.
            val children = (childOf[oldIdx] ?: emptyList())
                .filter { figures[it].diedYear == null && year - figures[it].bornYear >= 15 }
            val contested = !usurped && children.size >= 2 && rng.nextInt(10) < 6
            val heirIdx = when {
                contested -> {
                    val heir = children.first()
                    figures[heir] = figures[heir].copy(title = rulerTitle(power.kind), powerId = powerId)
                    pendingContests += Triple(powerId, children[1], 40 + rng.nextInt(45))
                    heir
                }
                !usurped && rng.nextInt(10) < 6 ->
                    coinFigure(power.cultureId, year - (16 + rng.nextInt(24)), rulerTitle(power.kind), powerId, oldIdx, old.houseId)
                else ->
                    coinFigure(power.cultureId, year - (24 + rng.nextInt(18)), rulerTitle(power.kind), powerId, houseId = old.houseId)
            }
            val asHeir = contested || oldIdx in figures[heirIdx].parentIds
            rulers[powerId] = heirIdx
            rulerDeathAge[heirIdx] = 46 + rng.nextInt(36)
            figures[heirIdx].houseId?.let { hid ->
                val hIdx = houses.indexOfFirst { it.id == hid }
                if (hIdx >= 0) houses[hIdx] = houses[hIdx].copy(headFigureId = heirIdx)
            }
            events += ChronicleEvent(
                year,
                EventKind.SUCCESSION,
                "${figures[heirIdx].name} takes the ${rulerTitle(power.kind)}'s seat of ${power.name}" +
                    when {
                        usurped && guildBacker != null ->
                            ", bought with ${guildBacker.name} gold; the old blood seethes."
                        temple != null ->
                            if (asHeir) ", heir of ${old.name}, anointed at the ${temple.name}."
                            else ", anointed at the ${temple.name}."
                        asHeir -> ", heir of ${old.name}." + if (contested) " The younger blood grumbles." else ""
                        else -> ", raised from among the sworn."
                    }
            )
        }

        /** Rulers die when their years run out; so do the rest, eventually. */
        fun mortalityStep(year: Int) {
            for (power in powers.toList()) {
                val rIdx = rulers[power.id] ?: continue
                val fig = figures[rIdx]
                if (fig.diedYear != null) continue
                if (year - fig.bornYear >= (rulerDeathAge[rIdx] ?: 60)) {
                    succeed(power.id, year, DEATHS.random(rng))
                }
            }
            val old = figures.withIndex().filter { it.value.diedYear == null && year - it.value.bornYear > 58 }
            if (old.size > 2 && rng.nextInt(3) == 0) {
                val (idx, fig) = old.random(rng)
                if (!rulers.containsValue(idx)) {
                    figures[idx] = fig.copy(diedYear = year, deathCause = DEATHS.random(rng))
                    events += ChronicleEvent(
                        year,
                        EventKind.DEATH,
                        "${fig.name} the ${fig.title} dies at ${sites.random(rng).name}; the debts pass to kin."
                    )
                    // Grief with a ledger behind it: the rivals do not mourn.
                    val watchers = fig.rivals.filter { figures.getOrNull(it)?.diedYear == null }
                    if (watchers.isNotEmpty() && rng.nextInt(2) == 0) {
                        val watcher = figures[watchers.random(rng)]
                        events += ChronicleEvent(
                            year,
                            EventKind.PLOT,
                            "${fig.name} the ${fig.title} is dead; ${watcher.name} the ${watcher.title} " +
                                "does not mourn, and counts the inheritance."
                        )
                    }
                }
            }
        }

        fun coinPower(kind: PowerKind, cultureId: Int, year: Int, parent: Int?, deityId: Int? = null): Power {
            val culture = cultures[cultureId]
            val name = when (kind) {
                PowerKind.ORDER -> "Order of the ${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}"
                PowerKind.CULT -> if (rng.nextBoolean()) {
                    "The ${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}"
                } else {
                    "${ADJECTIVES.random(rng)}bound of ${culture.name}"
                }
                PowerKind.HOLDFAST -> "${culture.name} Freeholders"
                PowerKind.GUILD -> "The ${ADJECTIVES.random(rng)} Guild"
                PowerKind.WARBAND -> "${phonetics.getValue(cultureId).word(rng, 2)}'s Riders"
            }
            val power = Power(
                id = nextPowerId++,
                name = name,
                kind = kind,
                cultureId = cultureId,
                foundedYear = year,
                splinterFromId = parent,
                creed = CREEDS.random(rng),
                hostileByNature = kind == PowerKind.CULT || kind == PowerKind.WARBAND,
                deityId = deityId
            )
            strengthNow[power.id] = 30 + rng.nextInt(50)
            battlesWon[power.id] = 0
            wealthNow[power.id] = (strengthNow[power.id] ?: 30) / 2
            agendasNow[power.id] = AGENDAS.shuffled(rng).take(1 + rng.nextInt(3))
            powers += power
            return power
        }

        fun holderCultureOf(site: Site): Int =
            site.holderPowerId?.let { hid -> powers.firstOrNull { it.id == hid }?.cultureId } ?: 0

        /** Markets and guild halls earn their keep each turn; dead keepers are replaced. */
        fun structureStep(year: Int) {
            for (st in structures.toList()) {
                if (st.ruined) continue
                val site = sites.firstOrNull { it.id == st.siteId } ?: continue
                if (site.ruined) continue
                val holder = site.holderPowerId
                if (holder != null && holder !in extinctPowers) {
                    val tithe = when (st.kind) {
                        StructureKind.MARKET -> 3
                        StructureKind.GUILD -> 5
                        StructureKind.TAVERN, StructureKind.TEMPLE -> 1
                        StructureKind.CATACOMB -> 0
                    }
                    if (tithe > 0) wealthNow[holder] = (wealthNow[holder] ?: 0) + tithe
                }
                val kIdx = st.keeperFigureId
                if (kIdx != null && figures[kIdx].diedYear == null &&
                    year - figures[kIdx].bornYear > 55 && rng.nextInt(4) == 0
                ) {
                    figures[kIdx] = figures[kIdx].copy(diedYear = year, deathCause = DEATHS.random(rng))
                    // Some debts are settled with a cutthroat.
                    val cutthroats = figures[kIdx].rivals.filter { figures.getOrNull(it)?.diedYear == null }
                    if (cutthroats.isNotEmpty() && rng.nextInt(3) == 0) {
                        val rival = figures[cutthroats.random(rng)]
                        events += ChronicleEvent(
                            year,
                            EventKind.PLOT,
                            "${figures[kIdx].name} the ${figures[kIdx].title} of ${site.name} dies; " +
                                "${rival.name} the ${rival.title} paid the cutthroat, it is whispered."
                        )
                    }
                }
                if (kIdx != null && figures[kIdx].diedYear != null) {
                    structures[structures.indexOfFirst { it.id == st.id }] =
                        st.copy(keeperFigureId = coinKeeper(site, st.kind, year, holderCultureOf(site)))
                }
            }
            // Swelling towns pour and trade; the dead pile up under the hill.
            val living = sites.filter { !it.ruined && it.isSettlement && it.population > 0 }
            living.firstOrNull {
                it.population >= 500 && structuresAt(it.id, StructureKind.TAVERN).isEmpty() && rng.nextInt(6) == 0
            }?.let { site ->
                coinStructure(site, StructureKind.TAVERN, year, holderCultureOf(site))
            }
            living.firstOrNull {
                it.population >= 80 && structuresAt(it.id, StructureKind.CATACOMB).isEmpty() && rng.nextInt(8) == 0
            }?.let { site ->
                coinStructure(site, StructureKind.CATACOMB, year, holderCultureOf(site))
            }
        }

        // --- Year 1: the founding peoples take their ground.
        cultures.forEachIndexed { index, culture ->
            val holdfast = coinPower(
                if (index == 0) PowerKind.HOLDFAST else PowerKind.values()[rng.nextInt(4)],
                culture.id,
                1 + rng.nextInt(20),
                null,
                patronDeity[culture.id]
            )
            val seat = coinSite(
                when (index) {
                    0 -> SiteKind.CAPITAL
                    // Each people keeps at least one market town; the rest are holds.
                    1 -> SiteKind.TOWN
                    else -> if (rng.nextInt(3) == 0) SiteKind.TOWN else SiteKind.HOLDFAST
                },
                culture.id,
                holdfast.id,
                "seat of the ${culture.epithet}"
            )
            powers[powers.indexOfFirst { it.id == holdfast.id }] =
                holdfast.copy(capitalSiteId = seat.id)
            coinRuler(holdfast, holdfast.foundedYear)
            val house = coinHouse(culture.id, holdfast.foundedYear, holdfast.id)
            val founderIdx = rulers[holdfast.id]
            if (founderIdx != null) {
                figures[founderIdx] = figures[founderIdx].copy(houseId = house.id)
                houses[houses.indexOfFirst { it.id == house.id }] = house.copy(headFigureId = founderIdx)
            }
            events += ChronicleEvent(
                house.foundedYear,
                EventKind.FOUNDING,
                "${house.name} takes its name in ${culture.homeland}; its blood will press its rights."
            )
            val god = deity(holdfast.deityId)
            events += ChronicleEvent(
                holdfast.foundedYear,
                EventKind.FOUNDING,
                "${holdfast.name} raise ${seat.name}" +
                    (if (index == 0) ", the great seat of the province" else "") +
                    " in ${culture.homeland}" +
                    (god?.let { ", and ${it.name} ${it.epithet} keeps the hearth." } ?: ".")
            )
            // Each people spreads into its homeland: the villages come first.
            val hamlets = (0 until 1 + rng.nextInt(2)).map {
                coinSite(
                    SiteKind.VILLAGE, culture.id, holdfast.id,
                    "tilled since the first years", 1 + rng.nextInt(30)
                )
            }
            if (hamlets.isNotEmpty()) {
                events += ChronicleEvent(
                    holdfast.foundedYear,
                    EventKind.FOUNDING,
                    "The ${culture.epithet} spread from ${seat.name} into ${hamlets.joinToString(" and ") { it.name }}."
                )
            }
            // The great seat keeps a temple, a market and a pour house from early on.
            if (index == 0) {
                coinStructure(seat, StructureKind.TEMPLE, holdfast.foundedYear, culture.id)
                coinStructure(seat, StructureKind.MARKET, holdfast.foundedYear, culture.id)
                coinStructure(seat, StructureKind.TAVERN, holdfast.foundedYear, culture.id)
            } else if (seat.kind == SiteKind.TOWN) {
                coinStructure(seat, StructureKind.TAVERN, holdfast.foundedYear, culture.id)
                coinStructure(seat, StructureKind.MARKET, holdfast.foundedYear, culture.id)
            }
        }

        // Old ruins no living people claims; beasts den in them later.
        repeat(2 + rng.nextInt(2)) {
            val c = cultures.random(rng)
            val ruin = coinSite(SiteKind.RUIN, c.id, null, "older than the tithe rolls", 1)
            events += ChronicleEvent(1, EventKind.RUIN, "${ruin.name} stands empty; no living people claims its stones.")
        }

        // --- The drowned figure whose vault the player will wake inside.
        val vaultCulture = cultures[rng.nextInt(cultures.size)]
        val floodYear = (totalYears * 0.55f).toInt() + rng.nextInt(30)
        val drownedIdx = coinFigure(vaultCulture.id, floodYear - 40 - rng.nextInt(20), "delver")
        val keeperCulture = cultures[(vaultCulture.id + 1) % cultures.size]
        val keepers = coinPower(PowerKind.CULT, keeperCulture.id, floodYear - 60, null, patronDeity[keeperCulture.id])
        coinRuler(keepers, floodYear - 60)
        val barrow = coinSite(SiteKind.BARROW, keeperCulture.id, keepers.id, "sealed after the drowning")
        val vault = coinSite(
            SiteKind.VAULT,
            vaultCulture.id,
            keepers.id,
            "beneath ${barrow.name}",
            forcedName = "Third Vault of ${figures[drownedIdx].name}"
        )

        // --- Wars: grudges turn to marches, marches to battles, battles to treaties.
        class WarDraft(
            val id: Int,
            val attackerId: Int,
            val defenderId: Int,
            val cause: String,
            val startYear: Int,
            val targetSiteId: Int
        ) {
            val battles = mutableListOf<Battle>()
            var attackerWins = 0
            var endYear: Int? = null
            var outcome: String = ""
        }
        val warDrafts = mutableListOf<WarDraft>()
        var nextWarId = 0

        fun adjustGrudge(a: Int, b: Int, delta: Int) {
            val key = if (a < b) a to b else b to a
            grudge[key] = (grudge[key] ?: 0) + delta
        }

        fun generalFor(power: Power, year: Int): Int {
            val rIdx = rulers[power.id]
            if (rIdx != null && figures[rIdx].diedYear == null) return rIdx
            return coinFigure(power.cultureId, year - (26 + rng.nextInt(20)), "champion", power.id)
        }

        /** A dispossessed line presses its right to a settlement; the rolls remember. */
        fun pressClaim(siteId: Int, powerId: Int?, figureId: Int?, strength: Int, origin: String, year: Int) {
            val pressable = strength.coerceIn(10, 95)
            if (claims.any { it.siteId == siteId && it.claimantPowerId == powerId }) return
            claims += Claim(
                id = nextClaimId++,
                siteId = siteId,
                claimantPowerId = powerId,
                claimantFigureId = figureId,
                strength = pressable,
                origin = origin,
                madeYear = year
            )
            val claimant = powers.firstOrNull { it.id == powerId }
            val site = sites.firstOrNull { it.id == siteId }
            events += ChronicleEvent(
                year,
                EventKind.CLAIM,
                "${claimant?.name ?: "An old line"} press a claim on ${site?.name ?: "lost ground"} — " +
                    "$origin. The rolls give it strength $pressable."
            )
        }

        fun declareWar(year: Int): Boolean {
            val alive = powers.filter { it.id !in extinctPowers }
            if (alive.size < 2) return false
            // Agendas choose the blade: expansionists march, the vengeful follow.
            val ambitious = alive.filter {
                (agendasNow[it.id] ?: emptyList()).any { g -> g == "expand the borders" || g == "press old claims" }
            }
            val vengeful = alive.filter { "avenge old grudges" in (agendasNow[it.id] ?: emptyList()) }
            val attacker = when {
                ambitious.isNotEmpty() && rng.nextInt(10) < 6 -> ambitious.random(rng)
                vengeful.isNotEmpty() && rng.nextInt(10) < 4 -> vengeful.random(rng)
                else -> alive.random(rng)
            }
            val enemyPool = alive.filter { it.id != attacker.id }
            // A pressed claim decides both foe and field when one can be warred over.
            val claimPair = claims
                .filter { it.claimantPowerId == attacker.id }
                .mapNotNull { c ->
                    val targetSite = sites.firstOrNull {
                        it.id == c.siteId && !it.ruined && it.kind != SiteKind.VAULT &&
                            it.holderPowerId != null && it.holderPowerId != attacker.id
                    }
                    if (targetSite == null) null else c to targetSite
                }
                .maxByOrNull { (c, _) -> c.strength }
            val claimantPower = claimPair?.second?.holderPowerId
                ?.let { hid -> alive.firstOrNull { it.id == hid } }
            val defender = when {
                claimantPower != null && rng.nextInt(10) < 7 -> claimantPower
                // Grudges draw the blade: the bitterest rival is attacked first.
                rng.nextInt(10) < 6 -> enemyPool.minByOrNull {
                    grudge[if (it.id < attacker.id) it.id to attacker.id else attacker.id to it.id] ?: 0
                }!!
                else -> enemyPool.random(rng)
            }
            val claimWar = claimPair != null && claimantPower?.id == defender.id
            val cause = when {
                claimWar ->
                    "their claim on ${claimPair!!.second.name} — ${claimPair.first.origin}, strength ${claimPair.first.strength}"
                attacker.creed != defender.creed && rng.nextBoolean() ->
                    "the doctrine that ${defender.creed}"
                else -> WAR_CAUSES.random(rng)
            }
            val target = if (claimWar) {
                claimPair!!.second
            } else {
                sites
                    .filter { it.holderPowerId == defender.id && !it.ruined && it.kind != SiteKind.VAULT }
                    .ifEmpty { sites.filter { it.kind != SiteKind.VAULT } }
                    .random(rng)
            }
            warDrafts += WarDraft(nextWarId++, attacker.id, defender.id, cause, year, target.id)
            adjustGrudge(attacker.id, defender.id, -70)
            events += ChronicleEvent(
                year,
                EventKind.WAR,
                "${attacker.name} march on ${defender.name} over $cause."
            )
            return true
        }

        fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = ax - bx
            val dy = ay - by
            return sqrt(dx * dx + dy * dy)
        }

        /** Whatever was kept at a burned site is lost to history. */
        fun scatterArtifacts(site: Site, year: Int) {
            for (i in artifacts.indices) {
                val a = artifacts[i]
                if (a.keeperSiteId == site.id) {
                    artifacts[i] = a.copy(keeperSiteId = null, whereabouts = "lost when ${site.name} burned in yr $year")
                }
            }
        }

        /** A sacked site's relics are carried off to the victor's seat — or not at all. */
        fun moveArtifacts(site: Site, newHolder: Power, year: Int) {
            val newSeat = sites.firstOrNull { it.holderPowerId == newHolder.id && !it.ruined }
            for (i in artifacts.indices) {
                val a = artifacts[i]
                if (a.keeperSiteId == site.id) {
                    artifacts[i] = if (newSeat != null) {
                        a.copy(keeperSiteId = newSeat.id, whereabouts = "carried off to ${newSeat.name} in yr $year")
                    } else {
                        a.copy(keeperSiteId = null, whereabouts = "lost on the ${site.name} road in yr $year")
                    }
                }
            }
        }

        fun captureSite(site: Site, attacker: Power, defender: Power, year: Int) {
            val idx = sites.indexOfFirst { it.id == site.id }
            if (idx < 0) return
            val old = sites[idx]
            val burned = rng.nextInt(4) == 0
            sites[idx] = old.copy(
                holderPowerId = attacker.id,
                sackedCount = old.sackedCount + 1,
                population = if (burned) 0 else (old.population * 2 / 3).coerceAtLeast(10),
                ruined = burned
            )
            // The conquered keep their own counsel: loyalty collapses under new banners.
            loyaltyNow[old.id] = 15 + rng.nextInt(25)
            stabilityNow[old.id] = (stabilityNow[old.id] ?: 70) / 2
            if (burned) {
                ruinedSiteIds += old.id
                var burnedHalls = 0
                for (i in structures.indices) {
                    val st = structures[i]
                    if (st.siteId == old.id && !st.ruined) {
                        structures[i] = st.copy(ruined = true)
                        burnedHalls++
                    }
                }
                scatterArtifacts(old, year + 1)
                events += if (old.isSettlement) {
                    ChronicleEvent(
                        year + 1,
                        EventKind.DESTRUCTION,
                        "${old.name} burns. ${format(max(old.population, 30))} souls die or scatter; " +
                            if (burnedHalls > 0) "the taverns burn first; ${attacker.name} march on."
                            else "${attacker.name} march on."
                    )
                } else {
                    ChronicleEvent(
                        year + 1,
                        EventKind.RUIN,
                        "${old.name} is left to the crows; its wells are fouled."
                    )
                }
            } else {
                moveArtifacts(old, attacker, year)
                events += ChronicleEvent(
                    year,
                    EventKind.WAR,
                    "${old.name} passes to ${attacker.name}; ${defender.name} fall back."
                )
                // The dispossessed remember: the beaten line presses its right.
                pressClaim(
                    old.id,
                    defender.id,
                    rulers[defender.id]?.takeIf { figures[it].diedYear == null },
                    55 + rng.nextInt(40),
                    "held by ${defender.name} until year $year",
                    year
                )
            }
            if (!sites.any { it.holderPowerId == defender.id && !it.ruined } && defender.id !in extinctPowers) {
                extinctPowers += defender.id
                events += ChronicleEvent(
                    year + 1,
                    EventKind.DEATH,
                    "${defender.name} is broken. Its banners burn; its debts pass to strangers."
                )
            }
        }

        fun endWar(draft: WarDraft, year: Int, outcome: String) {
            if (draft.endYear != null) return
            draft.endYear = year
            draft.outcome = outcome
            wars += War(
                id = draft.id,
                attackerId = draft.attackerId,
                defenderId = draft.defenderId,
                cause = draft.cause,
                startYear = draft.startYear,
                endYear = year,
                battles = draft.battles.toList(),
                outcome = outcome
            )
            val attacker = powers.firstOrNull { it.id == draft.attackerId }
            val defender = powers.firstOrNull { it.id == draft.defenderId }
            if (attacker != null && defender != null) {
                adjustGrudge(attacker.id, defender.id, 15)
                events += ChronicleEvent(
                    year,
                    EventKind.TREATY,
                    if (outcome.contains("took")) {
                        "${defender.name} swear peace and pay ${attacker.name} a tithe of grain and brass."
                    } else {
                        "${attacker.name} and ${defender.name} swear peace; ${outcome}."
                    }
                )
            }
        }

        fun warStep(year: Int) {
            // Wars that outlive two generations are recorded as they fade.
            warDrafts
                .filter { it.endYear == null && it.battles.isNotEmpty() && year - it.startYear > 60 }
                .forEach { endWar(it, year, "the war peters out; both sides keep their anger") }
            val active = warDrafts.filter { it.endYear == null }
            if (active.isNotEmpty() && rng.nextInt(3) > 0) {
                // The oldest grudge is fought first.
                val draft = active.minByOrNull { it.startYear } ?: return
                val attacker = powers.firstOrNull { it.id == draft.attackerId } ?: return
                val defender = powers.firstOrNull { it.id == draft.defenderId } ?: return
                if (attacker.id in extinctPowers || defender.id in extinctPowers) {
                    endWar(draft, year, "both halls had other griefs")
                    return
                }
                val site = sites.firstOrNull { it.id == draft.targetSiteId } ?: sites.first()
                val atkGen = generalFor(attacker, year)
                val defGen = generalFor(defender, year)
                val atkStr = strengthNow[attacker.id] ?: 40
                val defStr = strengthNow[defender.id] ?: 40
                // A rightful claim stiffens the attacker's spine.
                val claimStrength = claims
                    .firstOrNull { it.claimantPowerId == attacker.id && it.siteId == site.id }?.strength ?: 0
                val attackerWon = rng.nextInt(atkStr + defStr) < atkStr + claimStrength / 3
                val dead = 90 + rng.nextInt(1500)
                draft.battles += Battle(
                    year = year,
                    siteId = site.id,
                    attackerId = attacker.id,
                    defenderId = defender.id,
                    attackerGeneralId = atkGen,
                    defenderGeneralId = defGen,
                    dead = dead,
                    attackerWon = attackerWon
                )
                events += ChronicleEvent(
                    year,
                    EventKind.BATTLE,
                    if (attackerWon) {
                        "At ${site.name}, ${attacker.name} break the line of ${defender.name}. ${format(dead)} dead."
                    } else {
                        "At ${site.name}, ${defender.name} hold the field against ${attacker.name}. ${format(dead)} dead."
                    }
                )
                // War sours the ground it stands on.
                loyaltyNow[site.id] = ((loyaltyNow[site.id] ?: 70) - (4 + rng.nextInt(8))).coerceIn(0, 100)
                // Generals who bleed each other remember it.
                if (atkGen != defGen && rng.nextInt(3) == 0) seedBond(atkGen, defGen, rival = true)
                // Generals sometimes fall; rulers are succeeded in the field.
                listOf(attacker to atkGen, defender to defGen).forEach { (power, gen) ->
                    if (rng.nextInt(8) == 0 && figures[gen].diedYear == null) {
                        if (rulers[power.id] == gen) {
                            succeed(power.id, year, "fell at ${site.name}")
                        } else {
                            val fig = figures[gen]
                            figures[gen] = fig.copy(diedYear = year, deathCause = "fell at ${site.name}")
                            events += ChronicleEvent(
                                year,
                                EventKind.DEATH,
                                "${fig.name} the ${fig.title} falls at ${site.name}."
                            )
                        }
                    }
                }
                if (attackerWon) {
                    draft.attackerWins++
                    strengthNow[attacker.id] = (strengthNow[attacker.id] ?: 40) + 6
                    battlesWon[attacker.id] = (battlesWon[attacker.id] ?: 0) + 1
                    rulers[attacker.id]?.let { rIdx ->
                        val r = figures[rIdx]
                        if (r.diedYear == null) {
                            figures[rIdx] = r.copy(feats = r.feats + "bled ${defender.name} at ${site.name} in yr $year")
                        }
                    }
                } else {
                    strengthNow[defender.id] = (strengthNow[defender.id] ?: 40) + 6
                    battlesWon[defender.id] = (battlesWon[defender.id] ?: 0) + 1
                }
                if (draft.attackerWins >= 2) {
                    captureSite(site, attacker, defender, year)
                    // A satisfied claim is struck from the rolls; the rightful lords are welcome.
                    if (claims.removeAll { it.claimantPowerId == attacker.id && it.siteId == site.id }) {
                        loyaltyNow[site.id] = ((loyaltyNow[site.id] ?: 70) + 15).coerceIn(0, 100)
                    }
                    endWar(draft, year, "${attacker.name} took ${site.name}")
                } else if (draft.battles.size >= 2 + draft.id % 2) {
                    if (draft.attackerWins >= 1) {
                        // A war's worth of victories tells: the field changes hands.
                        captureSite(site, attacker, defender, year)
                        if (claims.removeAll { it.claimantPowerId == attacker.id && it.siteId == site.id }) {
                            loyaltyNow[site.id] = ((loyaltyNow[site.id] ?: 70) + 15).coerceIn(0, 100)
                        }
                        endWar(draft, year, "${attacker.name} took ${site.name}")
                    } else {
                        endWar(draft, year, "white peace; both sides bury their dead and keep their anger")
                    }
                }
            } else {
                declareWar(year)
            }
        }

        fun schismStep(year: Int) {
            val parent = powers.filter { it.id !in extinctPowers }.randomOrNull(rng) ?: return
            val child = coinPower(
                if (rng.nextBoolean()) PowerKind.CULT else PowerKind.ORDER,
                parent.cultureId,
                year,
                parent.id,
                parent.deityId
            )
            coinRuler(child, year)
            events += ChronicleEvent(
                year,
                EventKind.SCHISM,
                "${child.name} splinters from ${parent.name} over the doctrine ${child.creed}."
            )
        }

        fun hardshipStep(year: Int) {
            val target = sites.filter { !it.ruined && it.population > 40 }.randomOrNull(rng) ?: return
            // Misery deepens where the people have lost faith in their lords.
            val restless = (loyaltyNow[target.id] ?: 70) < 35
            val lost = (target.population * (30 + rng.nextInt(30)) / 100 * (if (restless) 5 else 4) / 4)
                .coerceAtLeast(20)
            val idx = sites.indexOfFirst { it.id == target.id }
            val remaining = target.population - lost
            if (target.isSettlement && remaining < 25) {
                // The rot wins: the last families walk away and the place dies for good.
                // Only the catacombs keep; the dead stay when the living will not.
                for (i in structures.indices) {
                    val st = structures[i]
                    if (st.siteId == target.id && !st.ruined && st.kind != StructureKind.CATACOMB) {
                        structures[i] = st.copy(ruined = true)
                    }
                }
                sites[idx] = target.copy(population = 0, ruined = true)
                ruinedSiteIds += target.id
                events += ChronicleEvent(
                    year,
                    EventKind.DESTRUCTION,
                    "The last families walk out of ${target.name}; " +
                        if (rng.nextBoolean()) "the grey rot has won." else "the empty granaries have won."
                )
            } else {
                sites[idx] = target.copy(population = remaining)
                loyaltyNow[target.id] = ((loyaltyNow[target.id] ?: 70) - (10 + rng.nextInt(16))).coerceIn(0, 100)
                stabilityNow[target.id] = ((stabilityNow[target.id] ?: 70) - (8 + rng.nextInt(12))).coerceIn(0, 100)
                val catacombs = structuresAt(target.id, StructureKind.CATACOMB).isNotEmpty()
                val blame = if (restless) " The people blame their lords." else ""
                events += if (rng.nextBoolean()) {
                    ChronicleEvent(
                        year, EventKind.PLAGUE,
                        "Grey rot takes ${target.name}. ${format(lost)} dead; the gates stay shut nine months." +
                            (if (catacombs) " The catacombs fill." else "") + blame
                    )
                } else {
                    ChronicleEvent(
                        year, EventKind.FAMINE,
                        "The granaries at ${target.name} fail. ${format(lost)} dead before the river barges come." +
                            (if (catacombs) " The catacombs fill." else "") + blame
                    )
                }
            }
        }

        fun foundingStep(year: Int) {
            val culture = cultures.random(rng)
            val holder = powers.filter { it.cultureId == culture.id && it.id !in extinctPowers }.randomOrNull(rng)
            val kind = when (rng.nextInt(10)) {
                in 0..5 -> SiteKind.VILLAGE
                6 -> SiteKind.TOWN
                in 7..8 -> SiteKind.CAMP
                else -> SiteKind.HOLDFAST
            }
            val newSite = coinSite(kind, culture.id, holder?.id, "founded in year $year", year)
            if (kind == SiteKind.TOWN) {
                coinStructure(newSite, StructureKind.TAVERN, year, culture.id)
                coinStructure(newSite, StructureKind.MARKET, year, culture.id)
            }
            events += ChronicleEvent(
                year,
                EventKind.FOUNDING,
                "${holder?.name ?: "The ${culture.epithet}"} found ${newSite.name}, a ${kind.label}, in ${culture.homeland}."
            )
        }

        /** Villages swell into towns, towns into cities; the tithe rolls remember. */
        fun growthStep(year: Int) {
            val living = sites.filter { !it.ruined && it.population > 0 && it.isSettlement }
            val target = living.randomOrNull(rng) ?: return
            var grown = (target.population * (6 + rng.nextInt(15)) / 100).coerceAtLeast(1)
            // Restless towns grow half as fast: unrest strangles the market carts.
            val restless = (loyaltyNow[target.id] ?: 70) < 35
            if (restless) grown /= 2
            val newPop = target.population + grown
            val newKind = when {
                target.kind == SiteKind.VILLAGE && newPop >= 320 -> SiteKind.TOWN
                target.kind == SiteKind.TOWN && newPop >= 2400 -> SiteKind.CITY
                else -> target.kind
            }
            val updated = target.copy(population = newPop, kind = newKind)
            sites[sites.indexOfFirst { it.id == target.id }] = updated
            val holderCulture = holderCultureOf(target)
            if (newKind != target.kind) {
                if (newKind == SiteKind.TOWN) {
                    if (structuresAt(target.id, StructureKind.MARKET).isEmpty()) {
                        coinStructure(updated, StructureKind.MARKET, year, holderCulture)
                    }
                    if (structuresAt(target.id, StructureKind.TEMPLE).isEmpty()) {
                        coinStructure(updated, StructureKind.TEMPLE, year, holderCulture)
                    }
                } else if (newKind == SiteKind.CITY && structuresAt(target.id, StructureKind.GUILD).isEmpty()) {
                    coinStructure(updated, StructureKind.GUILD, year, holderCulture)
                }
            } else if (
                target.kind == SiteKind.VILLAGE && newPop >= 60 &&
                structuresAt(target.id, StructureKind.TAVERN).isEmpty() && rng.nextInt(5) == 0
            ) {
                coinStructure(updated, StructureKind.TAVERN, year, holderCulture)
            }
            events += ChronicleEvent(
                year,
                EventKind.GROWTH,
                (when (newKind) {
                    target.kind -> "${target.name} grows; ${format(grown)} more souls pay the tithe."
                    SiteKind.TOWN -> "${target.name} outgrows its palisade and is counted a town."
                    else -> "${target.name} is chartered a city; its tolls are reckoned in ingots now."
                }) + if (restless) " Unrest strangles the market." else ""
            )
        }

        /** A power names its largest living settlement its high seat. */
        fun capitalStep(year: Int) {
            val power = powers.filter { it.id !in extinctPowers }.randomOrNull(rng) ?: return
            val seat = sites
                .filter { it.holderPowerId == power.id && !it.ruined && it.isSettlement }
                .maxByOrNull { it.population } ?: return
            if (seat.kind == SiteKind.CAPITAL) return
            val exalted = seat.copy(kind = SiteKind.CAPITAL)
            sites[sites.indexOfFirst { it.id == seat.id }] = exalted
            powers[powers.indexOfFirst { it.id == power.id }] =
                power.copy(capitalSiteId = seat.id)
            if (structuresAt(seat.id, StructureKind.TEMPLE).isEmpty()) {
                coinStructure(exalted, StructureKind.TEMPLE, year, power.cultureId)
            }
            events += ChronicleEvent(
                year,
                EventKind.GROWTH,
                "${seat.name} is named the high seat of ${power.name}; the road tolls come here now."
            )
        }

        fun cultureStep(year: Int) {
            if (rng.nextBoolean()) {
                val culture = cultures.random(rng)
                events += ChronicleEvent(
                    year,
                    EventKind.MIGRATION,
                    "The ${culture.epithet} abandon ${culture.homeland} and carry their ${culture.craft} east."
                )
            } else {
                val culture = cultures.random(rng)
                val prophetIdx = coinFigure(culture.id, year - 30 - rng.nextInt(20), "prophet")
                val power = powers.random(rng)
                events += ChronicleEvent(
                    year,
                    EventKind.PROPHECY,
                    "${figures[prophetIdx].name} of ${culture.name} declares ${power.creed}; ${1 + rng.nextInt(4)} shrines burned."
                )
            }
        }

        fun minorStep(year: Int) {
            val culture = cultures.random(rng)
            val holder = powers.filter { it.id !in extinctPowers }.randomOrNull(rng)
            val shrine = coinSite(SiteKind.SHRINE, culture.id, holder?.id, "raised in year $year", year)
            events += ChronicleEvent(
                year,
                EventKind.FOUNDING,
                "${shrine.name} is raised over a ${CRAFTS.random(rng)} kiln."
            )
        }

        fun artifactStep(year: Int) {
            val power = powers.filter { it.id !in extinctPowers }.randomOrNull(rng) ?: return
            val seat = sites.firstOrNull { it.holderPowerId == power.id && !it.ruined } ?: return
            val culture = cultures[power.cultureId]
            val makerIdx = coinFigure(power.cultureId, year - (30 + rng.nextInt(25)), "smith of ${culture.craft}")
            val name = "The ${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}"
            artifacts += Artifact(
                id = nextArtifactId++,
                name = name,
                kind = ARTIFACT_KINDS.random(rng),
                makerId = makerIdx,
                madeYear = year,
                keeperSiteId = seat.id,
                whereabouts = "kept at ${seat.name}"
            )
            events += ChronicleEvent(
                year,
                EventKind.ARTIFACT,
                "${figures[makerIdx].name} forges $name for ${power.name}; it is kept at ${seat.name}."
            )
        }

        fun beastStep(year: Int) {
            val living = beasts.filter { it.alive }
            if (living.isEmpty() || (rng.nextInt(4) == 0 && beasts.size < 5)) {
                val lairs = sites.filter { it.kind == SiteKind.RUIN || it.kind == SiteKind.BARROW }
                val lair = lairs.randomOrNull(rng) ?: return
                val name = phonetics.getValue(cultures.random(rng).id).word(rng, 2)
                val kind = BEAST_KINDS.random(rng)
                beasts += Beast(
                    id = nextBeastId++,
                    name = name,
                    kind = kind,
                    lairSiteId = lair.id,
                    wokeYear = year,
                    slainYear = null,
                    slayerId = null,
                    raids = 0
                )
                events += ChronicleEvent(
                    year,
                    EventKind.BEAST,
                    "$name the $kind wakes beneath ${lair.name}. The first tithe-taker to see it does not come back."
                )
            } else {
                val beast = living.random(rng)
                val lair = sites.firstOrNull { it.id == beast.lairSiteId } ?: return
                val near = sites.filter { it.id != lair.id && it.population > 0 }
                    .minByOrNull { dist(lair.x, lair.y, it.x, it.y) } ?: return
                val taken = 3 + rng.nextInt(30)
                val idx = beasts.indexOfFirst { it.id == beast.id }
                beasts[idx] = beast.copy(raids = beast.raids + 1)
                events += ChronicleEvent(
                    year,
                    EventKind.BEAST,
                    "${beast.name} carries off $taken souls from ${near.name}. ${beast.raids + 1} raids in all."
                )
                // Fear walks with the beast.
                loyaltyNow[near.id] = ((loyaltyNow[near.id] ?: 70) - (3 + rng.nextInt(7))).coerceIn(0, 100)
                if (beast.raids + 1 >= 2 && rng.nextInt(10) < 4) {
                    val culture = cultures.random(rng)
                    val heroIdx = coinFigure(culture.id, year - (24 + rng.nextInt(16)), "monster-slayer")
                    beasts[idx] = beasts[idx].copy(slainYear = year, slayerId = heroIdx)
                    figures[heroIdx] = figures[heroIdx].copy(feats = listOf("slew ${beast.name} in ${lair.name} in yr $year"))
                    events += ChronicleEvent(
                        year + 1,
                        EventKind.BEAST,
                        "${figures[heroIdx].name} the monster-slayer cuts ${beast.name} down above ${lair.name}. The province breathes."
                    )
                }
            }
        }

        /** The slow currents of mood: courts wed, garrisons steady, banners rise. */
        fun politicsStep(year: Int) {
            // The ledgers of the covetous never quite close.
            for (power in powers) {
                if (power.id in extinctPowers) continue
                if ("grow rich" in (agendasNow[power.id] ?: emptyList())) {
                    wealthNow[power.id] = (wealthNow[power.id] ?: 0) + 2
                }
            }
            // Courts wed and beget: dynasties grow their rivals at home.
            for (power in powers.toList()) {
                if (power.id in extinctPowers) continue
                val rIdx = rulers[power.id] ?: continue
                val ruler = figures[rIdx]
                if (ruler.diedYear != null) continue
                val age = year - ruler.bornYear
                if (ruler.spouseId == null && age in 20..55 && rng.nextInt(12) == 0) {
                    val spouseIdx = coinFigure(power.cultureId, year - (18 + rng.nextInt(10)), "consort", power.id)
                    figures[spouseIdx] = figures[spouseIdx].copy(spouseId = rIdx)
                    figures[rIdx] = ruler.copy(spouseId = spouseIdx)
                    seedBond(rIdx, spouseIdx, rival = false)
                    // A wedding promises a bloodline; the first child follows within the year.
                    val kidIdx = coinFigure(
                        power.cultureId, year, "child of ${power.name}", power.id, rIdx, ruler.houseId
                    )
                    childOf.getOrPut(rIdx) { mutableListOf() }.add(kidIdx)
                    events += ChronicleEvent(
                        year,
                        EventKind.MARRIAGE,
                        "${ruler.name} the ${ruler.title} weds ${figures[spouseIdx].name}; " +
                            "the halls exchange gifts and hostages."
                    )
                }
                val wed = figures[rIdx].spouseId?.let { sid -> figures.getOrNull(sid)?.diedYear == null } == true
                if (wed && (childOf[rIdx]?.size ?: 0) < 4 && age < 55 && rng.nextInt(8) == 0) {
                    val kidIdx = coinFigure(
                        power.cultureId, year, "child of ${power.name}", power.id, rIdx, ruler.houseId
                    )
                    childOf.getOrPut(rIdx) { mutableListOf() }.add(kidIdx)
                }
            }
            // When loyalty and order collapse, a town raises its own banner.
            for (site in sites.toList()) {
                if (site.ruined || !site.isSettlement) continue
                val holderId = site.holderPowerId ?: continue
                if (holderId in extinctPowers) continue
                if ((loyaltyNow[site.id] ?: 100) >= 20 || (stabilityNow[site.id] ?: 100) >= 35) continue
                if (rng.nextInt(6) != 0) continue
                val holder = powers.firstOrNull { it.id == holderId } ?: continue
                val rebels = coinPower(
                    if (rng.nextBoolean()) PowerKind.HOLDFAST else PowerKind.WARBAND,
                    holderCultureOf(site), year, holderId, holder.deityId
                )
                val leaderIdx = coinFigure(rebels.cultureId, year - (30 + rng.nextInt(20)), "free-lord", rebels.id)
                rulers[rebels.id] = leaderIdx
                rulerDeathAge[leaderIdx] = 46 + rng.nextInt(36)
                val banner = rebels.copy(capitalSiteId = site.id)
                powers[powers.indexOfFirst { it.id == rebels.id }] = banner
                sites[sites.indexOfFirst { it.id == site.id }] =
                    site.copy(holderPowerId = banner.id, sackedCount = site.sackedCount + 1)
                loyaltyNow[site.id] = 55 + rng.nextInt(20)
                garrisonNow[site.id] = 10
                events += ChronicleEvent(
                    year,
                    EventKind.REBELLION,
                    "${site.name} rises against ${holder.name}; " +
                        "${figures[leaderIdx].name} the free-lord keeps its gates now."
                )
                // Neighbours of the same people may follow the banner.
                for (near in sites.toList()) {
                    if (near.id == site.id || near.ruined || !near.isSettlement) continue
                    if (near.holderPowerId != holderId || holderCultureOf(near) != banner.cultureId) continue
                    if (rng.nextInt(3) != 0) continue
                    sites[sites.indexOfFirst { it.id == near.id }] = near.copy(holderPowerId = banner.id)
                    loyaltyNow[near.id] = 55 + rng.nextInt(20)
                    events += ChronicleEvent(
                        year,
                        EventKind.REBELLION,
                        "${near.name} declares for the rising; ${holder.name}'s wardens walk out by night."
                    )
                }
                // The broken holder remembers the theft.
                pressClaim(
                    site.id, holderId, rulers[holderId]?.takeIf { figures[it].diedYear == null },
                    50 + rng.nextInt(40), "seized by rebels in year $year", year
                )
                break
            }
            // Settled successions leave grudges: the passed-over press their rights.
            while (pendingContests.isNotEmpty()) {
                val (contestedPower, rival, strength) = pendingContests.removeAt(0)
                if (figures[rival].diedYear != null) continue
                val power = powers.firstOrNull { it.id == contestedPower } ?: continue
                val seatId = power.capitalSiteId
                    ?: sites.firstOrNull { it.holderPowerId == contestedPower && !it.ruined }?.id
                    ?: continue
                var splinter: Power? = null
                if (strength >= 60 && rng.nextInt(3) == 0) {
                    val defected = sites
                        .filter {
                            it.holderPowerId == contestedPower && !it.ruined &&
                                it.id != seatId && it.isSettlement
                        }
                        .maxByOrNull { it.population }
                    if (defected != null) {
                        val rebelBanner = coinPower(
                            if (rng.nextBoolean()) PowerKind.HOLDFAST else PowerKind.ORDER,
                            power.cultureId, year, contestedPower, power.deityId
                        )
                        sites[sites.indexOfFirst { it.id == defected.id }] =
                            defected.copy(holderPowerId = rebelBanner.id)
                        val rebelSeat = rebelBanner.copy(capitalSiteId = defected.id)
                        powers[powers.indexOfFirst { it.id == rebelBanner.id }] = rebelSeat
                        figures[rival] = figures[rival].copy(title = rulerTitle(rebelSeat.kind), powerId = rebelSeat.id)
                        rulers[rebelSeat.id] = rival
                        rulerDeathAge[rival] = 46 + rng.nextInt(36)
                        warDrafts += WarDraft(
                            nextWarId++, rebelSeat.id, contestedPower,
                            "the succession of ${power.name}", year, seatId
                        )
                        adjustGrudge(rebelSeat.id, contestedPower, -70)
                        val blood = figures[rival].houseId
                            ?.let { hid -> houses.firstOrNull { it.id == hid }?.name }
                        events += ChronicleEvent(
                            year,
                            EventKind.WAR,
                            "${figures[rival].name}${blood?.let { " of $it" } ?: ""} raises ${rebelSeat.name} " +
                                "against ${power.name} over the succession; ${defected.name} declares for the rival blood."
                        )
                        splinter = rebelSeat
                    }
                }
                pressClaim(seatId, splinter?.id, rival, strength, "passed over in the succession", year)
            }
            // Agendas are paid for: protective powers garrison their most restive stead.
            for (power in powers) {
                if (power.id in extinctPowers) continue
                val goals = agendasNow[power.id] ?: continue
                if ("suppress unrest" !in goals && "protect the capital" !in goals) continue
                val restive = sites
                    .filter { it.holderPowerId == power.id && !it.ruined && it.isSettlement }
                    .minByOrNull { loyaltyNow[it.id] ?: 100 } ?: continue
                if ((wealthNow[power.id] ?: 0) >= 120) {
                    wealthNow[power.id] = (wealthNow[power.id] ?: 0) - 30
                    garrisonNow[restive.id] = ((garrisonNow[restive.id] ?: 0) + 6).coerceAtMost(60)
                    loyaltyNow[restive.id] = ((loyaltyNow[restive.id] ?: 70) + 3).coerceIn(0, 100)
                }
            }
            // Garrisons steady the streets; stability drifts toward loyalty.
            for (site in sites) {
                if (site.ruined) continue
                if ((garrisonNow[site.id] ?: 0) >= 15) {
                    loyaltyNow[site.id] = ((loyaltyNow[site.id] ?: 70) + 1).coerceIn(0, 100)
                }
                val l = loyaltyNow[site.id] ?: 70
                val s = stabilityNow[site.id] ?: 70
                stabilityNow[site.id] = if (s < l) s + 1 else if (s > l) s - 1 else s
            }
            // Agendas are re-read when the wind changes.
            if (rng.nextInt(10) == 0) {
                val power = powers.filter { it.id !in extinctPowers }.randomOrNull(rng) ?: return
                agendasNow[power.id] = AGENDAS.shuffled(rng).take(1 + rng.nextInt(3))
            }
        }

        // --- Simulate the centuries.
        var year = 20
        while (year < totalYears) {
            year += 4 + rng.nextInt(14)
            if (year >= totalYears) break

            if (year in (floodYear - 2)..(floodYear + 2)) continue

            structureStep(year)
            politicsStep(year)

            when (rng.nextInt(100)) {
                in 0..21 -> warStep(year)
                in 22..25 -> mortalityStep(year)
                in 26..31 -> foundingStep(year)
                in 32..36 -> schismStep(year)
                in 37..40 -> hardshipStep(year)
                in 41..44 -> artifactStep(year)
                in 45..49 -> beastStep(year)
                in 50..53 -> mortalityStep(year)
                in 54..55 -> cultureStep(year)
                in 56..63 -> growthStep(year)
                in 64..66 -> capitalStep(year)
                else -> minorStep(year)
            }
        }

        // --- The drowning: the event that sealed the vault.
        val floodDead = 900 + rng.nextInt(2600)
        // The drowning takes a real settlement — the low-lying one, always.
        val floodVictim = sites
            .filter { it.isSettlement && !it.ruined && it.foundedYear < floodYear }
            .minByOrNull { terrain.heightAt(it.x, it.y) }
        if (floodVictim != null) {
            val victimDead = floodVictim.population
            // The flood takes the halls; the catacombs keep, drowned and sealed.
            for (i in structures.indices) {
                val st = structures[i]
                if (st.siteId == floodVictim.id && !st.ruined && st.kind != StructureKind.CATACOMB) {
                    structures[i] = st.copy(ruined = true)
                }
            }
            sites[sites.indexOfFirst { it.id == floodVictim.id }] =
                floodVictim.copy(population = 0, ruined = true, note = "gone under in the drowning")
            ruinedSiteIds += floodVictim.id
            events.list += ChronicleEvent(
                floodYear,
                EventKind.DESTRUCTION,
                "${floodVictim.name} goes under the flood. ${format(max(victimDead, 50))} souls; the bell tolls beneath the reeds."
            )
        }
        // The founding wound is written no matter how full the chronicle is.
        events.list += ChronicleEvent(
            floodYear,
            EventKind.FLOOD,
            "The Drowning of ${figures[drownedIdx].name}. ${format(floodDead)} dead. ${keepers.name} seal the ${vault.name}."
        )
        events.list += ChronicleEvent(
            floodYear + 1,
            EventKind.SEALING,
            "${barrow.name} is closed with brass nails and left unnamed on the tithe rolls."
        )

        // The relic the player can actually find, and one the flood took for good.
        val smithIdx = coinFigure(keepers.cultureId, floodYear - 58, "keeper-smith", keepers.id)
        val drownedName = figures[drownedIdx].name
        artifacts += Artifact(
            id = nextArtifactId++,
            name = "The Grave-Nail of $drownedName",
            kind = "sealed relic",
            makerId = smithIdx,
            madeYear = floodYear - 28,
            keeperSiteId = vault.id,
            whereabouts = "sealed in the ${vault.name}"
        )
        events.list += ChronicleEvent(
            floodYear - 28,
            EventKind.ARTIFACT,
            "${figures[smithIdx].name} forges The Grave-Nail of $drownedName; ${keepers.name} swear it stays with the dead."
        )
        val founderIdx = coinFigure(cultures[0].id, floodYear - 80, "bell-founder")
        artifacts += Artifact(
            id = nextArtifactId++,
            name = "The ${ADJECTIVES.random(rng)} Bell",
            kind = "bell",
            makerId = founderIdx,
            madeYear = floodYear - 60,
            keeperSiteId = null,
            whereabouts = "sank with the drowned quarter in yr $floodYear"
        )

        figures[drownedIdx] = figures[drownedIdx].copy(diedYear = floodYear, deathCause = "the drowning")
        events.list.sortBy { it.year }

        // Every province keeps one great living seat; if history burned them all,
        // the last great settlement is named the capital.
        if (sites.none { it.kind == SiteKind.CAPITAL && !it.ruined && it.population > 0 }) {
            sites.filter { !it.ruined && it.isSettlement && it.population > 0 }
                .maxByOrNull { it.population }
                ?.let { seat ->
                    sites[sites.indexOfFirst { it.id == seat.id }] =
                        seat.copy(kind = SiteKind.CAPITAL)
                }
        }
        // And at least one market town: if every town burned, rotted or drowned,
        // the largest living stead is counted a town, with its market and pour house.
        fun ensureMarketTown() {
            if (sites.any { it.kind == SiteKind.TOWN && !it.ruined && it.population > 0 }) return
            sites.filter {
                !it.ruined && it.isSettlement && it.population > 0 &&
                    it.kind != SiteKind.CAPITAL && it.kind != SiteKind.CITY
            }.maxByOrNull { it.population }?.let { stead ->
                val promoted = stead.copy(kind = SiteKind.TOWN)
                sites[sites.indexOfFirst { it.id == stead.id }] = promoted
                if (structuresAt(stead.id, StructureKind.MARKET).isEmpty()) {
                    structures += Structure(
                        id = nextStructureId++,
                        siteId = promoted.id,
                        kind = StructureKind.MARKET,
                        name = "${promoted.name} Market",
                        foundedYear = totalYears,
                        keeperFigureId = coinKeeper(
                            promoted, StructureKind.MARKET, totalYears, holderCultureOf(promoted)
                        )
                    )
                }
                if (structuresAt(stead.id, StructureKind.TAVERN).isEmpty()) {
                    structures += Structure(
                        id = nextStructureId++,
                        siteId = promoted.id,
                        kind = StructureKind.TAVERN,
                        name = "The ${ADJECTIVES.random(rng)} ${NOUNS.random(rng)}",
                        foundedYear = totalYears,
                        keeperFigureId = coinKeeper(
                            promoted, StructureKind.TAVERN, totalYears, holderCultureOf(promoted)
                        )
                    )
                }
            }
        }
        ensureMarketTown()

        // And one chartered city: if growth never chartered one, the largest
        // town that grew past the city rolls is counted a city at the end,
        // with a guild hall and a guildmaster to keep the rolls.
        if (sites.none { it.kind == SiteKind.CITY && !it.ruined && it.population > 0 }) {
            val candidates = sites.filter { !it.ruined && it.kind == SiteKind.TOWN && it.population > 0 }
            (candidates.filter { it.population >= 2000 }.ifEmpty { candidates })
                .maxByOrNull { it.population }
                ?.let { town ->
                    val chartered = town.copy(kind = SiteKind.CITY)
                    sites[sites.indexOfFirst { it.id == town.id }] = chartered
                    if (structuresAt(town.id, StructureKind.GUILD).isEmpty()) {
                        structures += Structure(
                            id = nextStructureId++,
                            siteId = chartered.id,
                            kind = StructureKind.GUILD,
                            name = "The ${ADJECTIVES.random(rng)} Hall",
                            foundedYear = totalYears,
                            keeperFigureId = coinKeeper(
                                chartered, StructureKind.GUILD, totalYears, holderCultureOf(chartered)
                            )
                        )
                    }
                }
        }
        ensureMarketTown()

        /** Who really holds sway in a settlement, in shares of a hundred. */
        fun influencesFor(site: Site): List<FactionInfluence> {
            val entries = mutableListOf<Pair<String, Int>>()
            val holder = powers.firstOrNull { it.id == site.holderPowerId }
            if (holder != null && holder.id !in extinctPowers) {
                entries += holder.name to 30 + (garrisonNow[site.id] ?: 0) / 3
            }
            structures.firstOrNull { it.siteId == site.id && it.kind == StructureKind.GUILD && !it.ruined }
                ?.let { hall ->
                    val guild = powers.firstOrNull {
                        it.kind == PowerKind.GUILD && it.id !in extinctPowers &&
                            it.cultureId == holderCultureOf(site)
                    }
                    entries += (guild?.name ?: hall.name) to 16 + (guild?.let { (wealthNow[it.id] ?: 0) / 30 } ?: 0)
                }
            structures.firstOrNull { it.siteId == site.id && it.kind == StructureKind.TEMPLE && !it.ruined }
                ?.let { temple ->
                    entries += temple.name to 12 + (if (livingKeeper(temple)) 8 else 0)
                }
            houses.firstOrNull { it.patronPowerId == site.holderPowerId && it.headFigureId != null }
                ?.let { entries += it.name to 10 }
            val garrison = garrisonNow[site.id] ?: 0
            if (garrison > 10) entries += "the garrison" to garrison / 4
            entries += "the townsfolk" to 10 + (100 - (loyaltyNow[site.id] ?: 70)) / 5
            val total = entries.sumOf { it.second }.coerceAtLeast(1)
            return entries.map { FactionInfluence(it.first, it.second * 100 / total) }
                .sortedByDescending { it.share }
        }

        // Final standings: what each power ended history holding, and whom it hates.
        val finalPowers = powers.map { p ->
            val held = sites.filter { it.holderPowerId == p.id && !it.ruined }
            val capital = held.filter { it.isSettlement }.maxByOrNull { it.population } ?: held.firstOrNull()
            val relations = grudge.entries
                .filter { it.key.first == p.id || it.key.second == p.id }
                .associate { if (it.key.first == p.id) it.key.second to it.value else it.key.first to it.value }
            p.copy(
                capitalSiteId = capital?.id,
                population = held.sumOf { it.population },
                strength = (strengthNow[p.id] ?: 40) + (battlesWon[p.id] ?: 0) * 8,
                wealth = (wealthNow[p.id] ?: 0) + held.sumOf { it.population } / 3,
                relations = relations,
                goals = agendasNow[p.id] ?: emptyList(),
                extinct = held.isEmpty() || p.id in extinctPowers
            )
        }

        val ruinCount = ruinedSiteIds.size + 18 + rng.nextInt(60)

        // The sky writes history too: omens from the world's own heavens, on their own
        // rng so this pass can never disturb the rest of the province's rolls.
        val omens = generateOmens(
            rng = Random(seed * 6971L + 100003L),
            moons = SkyGen.moonsFor(seed),
            phonetics = phonetics.values.toList(),
            cultures = cultures,
            totalYears = totalYears
        )
        val allEvents = (events.list + omens).sortedBy { it.year }
        val rumors = generateRumors(rng, allEvents, sites, finalPowers, vault, beasts, artifacts, wars)
        val structureBySite = structures.groupBy { it.siteId }

        return World(
            seed = seed,
            seedCode = seedCode(seed, cultures[0].name),
            provinceName = province,
            ages = ages,
            cultures = cultures,
            powers = finalPowers,
            figures = figures,
            deities = deities,
            events = allEvents,
            sites = sites.map {
                it.copy(
                    structures = structureBySite[it.id].orEmpty(),
                    stability = stabilityNow[it.id] ?: 70,
                    loyalty = loyaltyNow[it.id] ?: 70,
                    garrison = garrisonNow[it.id] ?: 0,
                    influences = influencesFor(it)
                )
            },
            wars = wars,
            artifacts = artifacts,
            beasts = beasts,
            houses = houses.toList(),
            claims = claims.toList(),
            terrain = terrain,
            rumors = rumors,
            currentYear = totalYears,
            vaultSiteId = vault.id,
            barrowSiteId = barrow.id,
            ruinCount = ruinCount
        )
    }

    /**
     * The province's land: fBm height shaped so the borders drown, a second field
     * for wetness, and rivers that walk downhill from the high ground to the sea,
     * wetting everything they pass. Deterministic from its own rng.
     */
    private fun generateTerrain(rng: Random, riverNames: List<String>): TerrainMap {
        val size = 96
        val heights = FloatArray(size * size)
        val moisture = FloatArray(size * size)
        val hs = rng.nextInt(1 shl 30)
        val ms = rng.nextInt(1 shl 30)

        for (y in 0 until size) {
            for (x in 0 until size) {
                val nx = x / size.toFloat()
                val ny = y / size.toFloat()
                val base = fbm(nx * 3.4f, ny * 3.4f, hs)
                val ridge = 1f - abs(fbm(nx * 6.3f, ny * 6.3f, hs + 7) - 0.5f) * 2f
                var h = base * 0.8f + ridge * 0.2f
                val ex = (nx - 0.5f) * 2f
                val ey = (ny - 0.5f) * 2f
                val edge = max(abs(ex), abs(ey))
                h += 0.08f - edge * edge * 0.42f
                heights[y * size + x] = h.coerceIn(0f, 1f)
                moisture[y * size + x] = fbm(nx * 2.6f, ny * 2.6f, ms).coerceIn(0f, 1f)
            }
        }

        val rivers = mutableListOf<River>()
        repeat(3 + rng.nextInt(3)) {
            var sx = -1
            var sy = -1
            var attempts = 0
            while (sx < 0 && attempts < 80) {
                attempts++
                val x = 8 + rng.nextInt(size - 16)
                val y = 8 + rng.nextInt(size - 16)
                if (heights[y * size + x] > 0.60f) {
                    sx = x
                    sy = y
                }
            }
            if (sx < 0) return@repeat

            val pts = mutableListOf<RiverPoint>()
            val visited = mutableSetOf<Int>()
            var cx = sx
            var cy = sy
            while (pts.size < 160) {
                visited += cy * size + cx
                pts += RiverPoint((cx + 0.5f) / size, (cy + 0.5f) / size)
                if (heights[cy * size + cx] < 0.35f) break
                var bx = -1
                var by = -1
                var bh = Float.MAX_VALUE
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx2 = cx + dx
                        val ny2 = cy + dy
                        if (nx2 < 0 || ny2 < 0 || nx2 >= size || ny2 >= size) continue
                        val key = ny2 * size + nx2
                        if (key in visited) continue
                        val nh = heights[key]
                        if (nh < bh) {
                            bh = nh
                            bx = nx2
                            by = ny2
                        }
                    }
                }
                if (bx < 0) break
                cx = bx
                cy = by
            }
            if (pts.size > 10 && riverNames.isNotEmpty()) {
                rivers += River(riverNames[rivers.size % riverNames.size], pts)
            }
        }

        // Rivers wet the land around them, which turns their banks to marsh and forest.
        for (river in rivers) {
            for (p in river.points) {
                val cx = (p.x * size).toInt()
                val cy = (p.y * size).toInt()
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        val nx2 = cx + dx
                        val ny2 = cy + dy
                        if (nx2 !in 0 until size || ny2 !in 0 until size) continue
                        val d = abs(dx) + abs(dy)
                        val idx = ny2 * size + nx2
                        moisture[idx] = (moisture[idx] + (0.16f - d * 0.035f)).coerceIn(0f, 1f)
                    }
                }
            }
        }
        return TerrainMap(size, heights, moisture, rivers)
    }

    private fun fbm(x: Float, y: Float, seed: Int): Float {
        var amp = 0.5f
        var freq = 1f
        var sum = 0f
        var norm = 0f
        for (octave in 0 until 4) {
            sum += valueNoise(x * freq, y * freq, seed + octave * 101) * amp
            norm += amp
            amp *= 0.5f
            freq *= 2.1f
        }
        return sum / norm
    }

    /** Cheap deterministic value noise, independent of the renderer's copy. */
    private fun valueNoise(x: Float, y: Float, seed: Int): Float {
        val xi = x.toInt()
        val yi = y.toInt()
        val xf = x - xi
        val yf = y - yi
        val v00 = hash(xi, yi, seed)
        val v10 = hash(xi + 1, yi, seed)
        val v01 = hash(xi, yi + 1, seed)
        val v11 = hash(xi + 1, yi + 1, seed)
        val sx = xf * xf * (3 - 2 * xf)
        val sy = yf * yf * (3 - 2 * yf)
        val top = v00 + (v10 - v00) * sx
        val bottom = v01 + (v11 - v01) * sx
        return top + (bottom - top) * sy
    }

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695040888963407L.toInt()
        h = (h xor (h shr 13)) * 1274126177
        return ((h xor (h shr 16)) and 0xFFFF) / 65535f
    }

    /** What each kind of power calls its head. */
    private fun rulerTitle(kind: PowerKind): String = when (kind) {
        PowerKind.HOLDFAST -> "thegn"
        PowerKind.ORDER -> "grandmaster"
        PowerKind.CULT -> "hierophant"
        PowerKind.GUILD -> "guildmaster"
        PowerKind.WARBAND -> "war-chief"
    }

    /**
     * Sky omens: one named comet (and its return), eclipses, and strange moon years.
     * Moon text draws on the world's actual moons, so the chronicle matches the sky you sleep under.
     */
    private fun generateOmens(
        rng: Random,
        moons: List<SkyMoon>,
        phonetics: List<Phonetics>,
        cultures: List<Culture>,
        totalYears: Int
    ): List<ChronicleEvent> {
        val out = mutableListOf<ChronicleEvent>()

        // One comet per world, named in the province's own tongue — and it comes back.
        val cometName = phonetics.random(rng).word(rng, 2)
        val cometCulture = cultures.random(rng)
        val firstYear = 2 + rng.nextInt((totalYears - 4).coerceAtLeast(1))
        out += ChronicleEvent(
            year = firstYear,
            kind = EventKind.COMET,
            subject = cometName,
            text = "In $firstYear, the comet $cometName crossed the night for ${3 + rng.nextInt(9)} nights. " +
                "The ${cometCulture.adjective} folk called it a lamp swung before the world, and would not plant while it burned."
        )
        val returnYear = firstYear + 62 + rng.nextInt(30)
        if (returnYear <= totalYears) {
            out += ChronicleEvent(
                year = returnYear,
                kind = EventKind.COMET,
                subject = cometName,
                text = "The comet $cometName returned, exactly as the old tables promised. Pilgrim roads were crowded that season."
            )
        }

        // Eclipses: the sun went out at noon.
        repeat(1 + rng.nextInt(2)) {
            val year = 2 + rng.nextInt((totalYears - 3).coerceAtLeast(1))
            out += ChronicleEvent(
                year = year,
                kind = EventKind.ECLIPSE,
                text = "In $year, the sun went black at noon. The birds went to bed, the wells went still, and the priests lit every candle they had."
            )
        }

        // Moon wonders, read straight from the world's own moons.
        repeat(1 + rng.nextInt(3)) {
            val year = 2 + rng.nextInt((totalYears - 3).coerceAtLeast(1))
            val text = if (moons.size >= 2 && rng.nextInt(2) == 0) {
                "In $year, both moons stood in a row at dusk and held there past the third bell. Pilots steered by it all winter."
            } else {
                val moon = moons.random(rng)
                if (rng.nextInt(2) == 0) {
                    "In $year, the ${moon.tintName} moon rose full twice in one night. The chroniclers wrote it down three times to be sure."
                } else {
                    "In $year, the ${moon.tintName} moon dimmed at its setting, as if a hand had passed over it. The old folk called it a warning and got on with the harvest."
                }
            }
            out += ChronicleEvent(year = year, kind = EventKind.MOONWONDER, text = text)
        }
        return out
    }

    /** A chronicle with finite pages: once full, the world lives on unwritten. */
    private class EventLedger(val cap: Int) {
        val list = mutableListOf<ChronicleEvent>()

        operator fun plusAssign(event: ChronicleEvent) {
            if (list.size < cap) list += event
        }
    }

    private fun generateCultures(rng: Random, requested: Int): List<Culture> {
        val count = if (requested >= 1) requested else 3 + rng.nextInt(3)
        val usedTerrain = mutableSetOf<String>()
        val usedEpithet = mutableSetOf<String>()
        val usedCraft = mutableSetOf<String>()
        return (0 until count).map { id ->
            val ph = Phonetics.forCulture(rng, id)
            val name = ph.word(rng, 2)
            val homeland = pickUnused(rng, TERRAINS, usedTerrain)
            val epithet = pickUnused(rng, EPITHETS, usedEpithet)
            val craft = pickUnused(rng, CRAFTS, usedCraft)
            Culture(
                id = id,
                name = name,
                adjective = name + "ish",
                epithet = epithet,
                craft = craft,
                homeland = homeland,
                values = VALUES.shuffled(rng).take(2),
                taboo = TABOOS.random(rng)
            )
        }
    }

    private fun generateAges(rng: Random, totalYears: Int): List<Age> {
        val count = 3 + rng.nextInt(2)
        val used = mutableSetOf<String>()
        val cuts = mutableListOf(1)
        for (i in 1 until count) {
            cuts += (totalYears * i / count) + rng.nextInt(20) - 10
        }
        cuts += totalYears
        return (0 until count).map { i ->
            Age(
                name = "Age of ${pickUnused(rng, AGE_WORDS, used)}",
                startYear = cuts[i].coerceAtLeast(1),
                endYear = cuts[i + 1]
            )
        }
    }

    /** Road-talk drawn from what actually happened: wars, beasts, lost relics, the vault. */
    private fun generateRumors(
        rng: Random,
        events: List<ChronicleEvent>,
        sites: List<Site>,
        powers: List<Power>,
        vault: Site,
        beasts: List<Beast>,
        artifacts: List<Artifact>,
        wars: List<War>
    ): List<Rumor> {
        val out = mutableListOf<Rumor>()
        val town = sites.firstOrNull { it.isSettlement && !it.ruined } ?: sites.first()

        out += Rumor(
            text = "\"A vault below the barrow was opened. Nobody paid the keepers.\"",
            source = "${RUMOR_SOURCES.random(rng)} at ${town.name}",
            daysOld = 1 + rng.nextInt(3),
            aboutPlayer = true
        )

        events.takeLast(3).forEach { event ->
            out += Rumor(
                text = "\"" + shortRumor(event, rng) + "\"",
                source = "${RUMOR_SOURCES.random(rng)} at ${sites.random(rng).name}",
                daysOld = 1 + rng.nextInt(30),
                aboutPlayer = false
            )
        }

        beasts.filter { it.alive }.take(2).forEach { beast ->
            val lair = sites.firstOrNull { it.id == beast.lairSiteId }
            out += Rumor(
                text = "\"${beast.name} still walks. ${beast.raids} steadings gone this year. Keep clear of ${lair?.name ?: "the fens"}.\"",
                source = "${RUMOR_SOURCES.random(rng)} near ${lair?.name ?: town.name}",
                daysOld = 2 + rng.nextInt(10),
                aboutPlayer = false,
                siteId = lair?.id ?: -1
            )
        }

        artifacts.filter { it.keeperSiteId == null }.take(1).forEach { artifact ->
            out += Rumor(
                text = "\"They still hunt ${artifact.name} — ${artifact.whereabouts}, they say.\"",
                source = "${RUMOR_SOURCES.random(rng)} on the road",
                daysOld = 3 + rng.nextInt(20),
                aboutPlayer = false
            )
        }

        wars.lastOrNull()?.let { war ->
            val a = powers.firstOrNull { it.id == war.attackerId }
            val b = powers.firstOrNull { it.id == war.defenderId }
            if (a != null && b != null) {
                out += Rumor(
                    text = "\"${a.name} have not forgotten what ${b.name} took from them. There will be another war.\"",
                    source = "${RUMOR_SOURCES.random(rng)} at ${town.name}",
                    daysOld = 2 + rng.nextInt(14),
                    aboutPlayer = false
                )
            }
        }

        val buyer = powers.filter { !it.extinct }.randomOrNull(rng) ?: powers.first()
        out += Rumor(
            text = "\"${buyer.name} pay well for anything brass out of ${vault.name}.\"",
            source = "${RUMOR_SOURCES.random(rng)} on the road",
            daysOld = 2 + rng.nextInt(12),
            aboutPlayer = false,
            siteId = vault.id
        )
        return out
    }

    private fun shortRumor(event: ChronicleEvent, rng: Random): String {
        val trimmed = event.text.substringBefore('.').trim()
        return when (event.kind) {
            EventKind.WAR, EventKind.BATTLE -> "$trimmed. They are still burying them."
            EventKind.PLAGUE, EventKind.FAMINE -> "$trimmed. Do not drink the well water."
            EventKind.SCHISM -> "$trimmed. Two creeds, one knife."
            EventKind.BEAST -> "$trimmed. Keep a blade by the door."
            EventKind.TREATY -> "$trimmed. Paper peace, iron anger."
            EventKind.SUCCESSION -> "$trimmed. New seat, same debts."
            EventKind.COMET -> "$trimmed. They still name children after it."
            EventKind.ECLIPSE -> "$trimmed. No one lights a lamp that day, even now."
            EventKind.MOONWONDER -> "$trimmed. The old folk still point at the sky when they tell it."
            else -> "$trimmed, they say."
        } + if (rng.nextInt(4) == 0) " I had it from my cousin." else ""
    }

    private fun <T> pickUnused(rng: Random, pool: List<T>, used: MutableSet<T>): T {
        val free = pool.filterNot { used.contains(it) }
        val chosen = if (free.isEmpty()) pool.random(rng) else free.random(rng)
        used += chosen
        return chosen
    }

    private fun format(n: Int): String =
        n.toString().reversed().chunked(3).joinToString(",").reversed()

    private fun seedCode(seed: Long, cultureName: String): String {
        val digits = (abs(seed) % 10000L).toString().padStart(4, '0')
        return "$digits-${cultureName.uppercase()}"
    }

    /** Per-culture sound: each people draws from its own small set of syllables. */
    private class Phonetics(
        val onsets: List<String>,
        val nuclei: List<String>,
        val codas: List<String>
    ) {
        fun word(rng: Random, syllables: Int): String {
            val sb = StringBuilder()
            repeat(syllables) { i ->
                sb.append(onsets.random(rng))
                sb.append(nuclei.random(rng))
                if (i == syllables - 1 || rng.nextInt(3) == 0) sb.append(codas.random(rng))
            }
            return sb.toString().replaceFirstChar { it.uppercase() }
        }

        companion object {
            fun forCulture(rng: Random, cultureId: Int): Phonetics {
                val local = Random(rng.nextLong() * 31 + cultureId * 7919L)
                return Phonetics(
                    onsets = ONSETS.shuffled(local).take(5),
                    nuclei = NUCLEI.shuffled(local).take(3),
                    codas = CODAS.shuffled(local).take(4)
                )
            }
        }
    }
}
