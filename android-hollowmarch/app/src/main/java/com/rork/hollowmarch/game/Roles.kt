package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Biome
import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.StructureKind
import kotlin.random.Random

/**
 * NPC roles and jobs — what the named souls of a living place do for a living,
 * and how their day is shaped around it.
 *
 * Three parts:
 *
 *  - [ROLES], the registry: every role of the province, what broad craft it
 *    belongs to, which schedule archetype shapes its day, and where it works.
 *  - [RoleBook], the assignment: a role is dealt to each soul once, from the
 *    seed, the place and the soul's own station — never from runtime luck.
 *  - [WorkSchedule], the day: blocks of duty and rest laid out from the role's
 *    archetype, with each soul's own waking hour and tempos drawn from a seed
 *    that never changes. Two souls of one trade keep different hours; the same
 *    soul keeps the same hours every day of the world's life.
 *
 * The role and its workplace are written into the persistent record, so a
 * soul keeps its trade through saving, loading and every rebuild of the scene.
 */

/** The broad craft a role belongs to — used for flavor, counts and pools. */
enum class RoleCategory {
    AGRICULTURE, FOOD, CRAFT, EXTRACTION, COMMERCE, HOSPITALITY, SERVICE,
    CONSTRUCTION, MEDICINE, LORE, ARCANE, FAITH, GOVERNANCE, MILITARY,
    SECURITY, ARTS, DOMESTIC, WILDERNESS, UNDERWORLD, COMMON, NOBILITY
}

/** Where a working day is spent: what the map must answer with a place. */
enum class Workplace {
    /** The soul's own dwelling doubles as the shop, clinic or office. */
    HOME,
    /** The tavern's hall — its keepers and its servers. */
    TAVERN,
    /** The temple or shrine-house. */
    TEMPLE,
    /** The keep, citadel or the great house in service. */
    KEEP,
    /** The guild hall — the learned and the lettered. */
    GUILD,
    /** The market square and its stalls. */
    MARKET,
    /** The tilled plots at the settlement's edge. */
    FIELDS,
    /** The water's edge — nets, boats, mills. */
    WATER,
    /** Out the gate: the woods, the diggings, the road. */
    GATE,
    /** The well and the square — the lot of those with no trade. */
    WELL
}

/** One stop on a soul's daily round. */
enum class Duty { HOME, WORK, WELL, MARKET, TAVERN, TEMPLE, GATE, ROAD, FIELDS, WATER }

/** The shape of a day, before any soul's own hours are drawn. */
enum class Archetype { RURAL, CRAFT, BAKER, MINER, MERCHANT, TAVERNKEEP, GUARD, PRIEST, SCHOLAR, NOBLE, HEALER, LABORER, PERFORMER, UNDERWORLD, LOAFER, SERVICE }

/** One role of the province. */
data class RoleDef(
    val name: String,
    val category: RoleCategory,
    val archetype: Archetype,
    val workplace: Workplace
)

/**
 * The registry itself — every calling the province knows, grouped by craft.
 * Names are persisted verbatim, so the list must never reorder or rename.
 */
object ROLES {

    private fun r(name: String, archetype: Archetype, workplace: Workplace) =
        RoleDef(name, RoleCategory.AGRICULTURE, archetype, workplace)

    private fun f(name: String, archetype: Archetype, workplace: Workplace) =
        RoleDef(name, RoleCategory.FOOD, archetype, workplace)

    private fun c(name: String, archetype: Archetype = Archetype.CRAFT, workplace: Workplace = Workplace.HOME) =
        RoleDef(name, RoleCategory.CRAFT, archetype, workplace)

    private fun e(name: String) = RoleDef(name, RoleCategory.EXTRACTION, Archetype.MINER, Workplace.GATE)

    private fun m(name: String, workplace: Workplace = Workplace.MARKET) =
        RoleDef(name, RoleCategory.COMMERCE, Archetype.MERCHANT, workplace)

    private fun h(name: String, archetype: Archetype = Archetype.TAVERNKEEP, workplace: Workplace = Workplace.TAVERN) =
        RoleDef(name, RoleCategory.HOSPITALITY, archetype, workplace)

    private fun s(name: String, archetype: Archetype, workplace: Workplace) =
        RoleDef(name, RoleCategory.SERVICE, archetype, workplace)

    private fun k(name: String) = RoleDef(name, RoleCategory.CONSTRUCTION, Archetype.LABORER, Workplace.GATE)

    private fun doc(name: String) = RoleDef(name, RoleCategory.MEDICINE, Archetype.HEALER, Workplace.HOME)

    private fun l(name: String) = RoleDef(name, RoleCategory.LORE, Archetype.SCHOLAR, Workplace.GUILD)

    private fun a(name: String) = RoleDef(name, RoleCategory.ARCANE, Archetype.SCHOLAR, Workplace.GUILD)

    private fun p(name: String) = RoleDef(name, RoleCategory.FAITH, Archetype.PRIEST, Workplace.TEMPLE)

    private fun g(name: String) = RoleDef(name, RoleCategory.GOVERNANCE, Archetype.NOBLE, Workplace.KEEP)

    private fun mil(name: String) = RoleDef(name, RoleCategory.MILITARY, Archetype.GUARD, Workplace.KEEP)

    private fun sec(name: String) = RoleDef(name, RoleCategory.SECURITY, Archetype.GUARD, Workplace.GATE)

    private fun art(name: String) = RoleDef(name, RoleCategory.ARTS, Archetype.PERFORMER, Workplace.MARKET)

    private fun dom(name: String, workplace: Workplace = Workplace.KEEP) =
        RoleDef(name, RoleCategory.DOMESTIC, Archetype.SERVICE, workplace)

    private fun w(name: String) = RoleDef(name, RoleCategory.WILDERNESS, Archetype.GUARD, Workplace.GATE)

    private fun u(name: String) = RoleDef(name, RoleCategory.UNDERWORLD, Archetype.UNDERWORLD, Workplace.TAVERN)

    private fun common(name: String) = RoleDef(name, RoleCategory.COMMON, Archetype.LOAFER, Workplace.WELL)

    private fun n(name: String) = RoleDef(name, RoleCategory.NOBILITY, Archetype.NOBLE, Workplace.KEEP)

    val ALL: List<RoleDef> = listOf(
        // agriculture
        r("Farmer", Archetype.RURAL, Workplace.FIELDS),
        r("Rancher", Archetype.RURAL, Workplace.FIELDS),
        r("Shepherd", Archetype.RURAL, Workplace.FIELDS),
        r("Herder", Archetype.RURAL, Workplace.FIELDS),
        r("Fisher", Archetype.RURAL, Workplace.WATER),
        r("Hunter", Archetype.RURAL, Workplace.GATE),
        r("Trapper", Archetype.RURAL, Workplace.GATE),
        r("Forager", Archetype.RURAL, Workplace.GATE),
        r("Gardener", Archetype.RURAL, Workplace.FIELDS),
        r("Beekeeper", Archetype.RURAL, Workplace.FIELDS),
        // food
        f("Miller", Archetype.RURAL, Workplace.WATER),
        f("Baker", Archetype.BAKER, Workplace.HOME),
        f("Butcher", Archetype.MERCHANT, Workplace.MARKET),
        f("Cook", Archetype.TAVERNKEEP, Workplace.TAVERN),
        f("Brewer", Archetype.CRAFT, Workplace.HOME),
        f("Vintner", Archetype.RURAL, Workplace.FIELDS),
        f("Cheesemaker", Archetype.CRAFT, Workplace.HOME),
        f("Fishmonger", Archetype.MERCHANT, Workplace.MARKET),
        f("Preserver", Archetype.CRAFT, Workplace.HOME),
        // craft
        c("Blacksmith"),
        c("Weaponsmith"),
        c("Armorer"),
        c("Bladesmith"),
        c("Toolsmith"),
        c("Carpenter"),
        c("Fletcher"),
        c("Bowyer"),
        c("Tanner"),
        c("Leatherworker"),
        c("Tailor"),
        c("Weaver"),
        c("Dyer"),
        c("Cobbler"),
        c("Potter"),
        c("Glassblower"),
        c("Mason"),
        c("Stonemason"),
        c("Brickmaker"),
        c("Cooper"),
        c("Ropemaker"),
        c("Candlemaker"),
        c("Soapmaker"),
        c("Wheelwright"),
        c("Cartwright"),
        c("Shipwright", workplace = Workplace.WATER),
        c("Jeweler"),
        c("Goldsmith"),
        c("Silversmith"),
        c("Engraver"),
        c("Sculptor"),
        c("Locksmith"),
        c("Instrument Maker"),
        // extraction
        e("Miner"),
        e("Quarryman"),
        e("Prospector"),
        e("Smelter"),
        e("Charcoal Burner"),
        e("Lumberjack"),
        e("Forester"),
        e("Salt Worker"),
        e("Clay Worker"),
        e("Gem Cutter"),
        // commerce
        m("Merchant"),
        m("Shopkeeper"),
        m("Grocer"),
        m("Market Vendor"),
        m("Peddler", Workplace.GATE),
        m("Caravan Merchant", Workplace.GATE),
        m("Broker"),
        m("Pawnbroker"),
        m("Moneychanger"),
        m("Livestock Trader"),
        m("Produce Merchant"),
        m("Arms Merchant"),
        m("Armor Merchant"),
        m("Cloth Merchant"),
        // hospitality
        h("Tavern Keeper"),
        h("Innkeeper"),
        h("Bartender"),
        h("Server"),
        h("Tavern Cook"),
        h("Kitchen Hand"),
        h("Stablemaster", Archetype.RURAL, Workplace.GATE),
        h("Stable Hand", Archetype.RURAL, Workplace.GATE),
        h("Bathhouse Keeper", Archetype.SERVICE, Workplace.WELL),
        h("Bathhouse Attendant", Archetype.SERVICE, Workplace.WELL),
        // handlers and household service
        s("Housekeeper", Archetype.SERVICE, Workplace.KEEP),
        s("Butler", Archetype.SERVICE, Workplace.KEEP),
        s("Host", Archetype.TAVERNKEEP, Workplace.TAVERN),
        s("Bouncer", Archetype.TAVERNKEEP, Workplace.TAVERN),
        s("Teamster", Archetype.RURAL, Workplace.GATE),
        s("Coachman", Archetype.RURAL, Workplace.GATE),
        s("Carriage Driver", Archetype.RURAL, Workplace.GATE),
        s("Ferryman", Archetype.RURAL, Workplace.WATER),
        s("Boatman", Archetype.RURAL, Workplace.WATER),
        s("Sailor", Archetype.RURAL, Workplace.WATER),
        s("Ship Captain", Archetype.RURAL, Workplace.WATER),
        s("Caravaner", Archetype.RURAL, Workplace.GATE),
        s("Courier", Archetype.RURAL, Workplace.GATE),
        s("Messenger", Archetype.RURAL, Workplace.GATE),
        s("Porter", Archetype.LABORER, Workplace.GATE),
        s("Guide", Archetype.RURAL, Workplace.GATE),
        s("Drover", Archetype.RURAL, Workplace.FIELDS),
        // construction
        k("Builder"),
        k("Architect"),
        k("Roofer"),
        k("Thatcher"),
        k("Plasterer"),
        k("Bricklayer"),
        k("Well Digger"),
        k("Road Worker"),
        k("Bridge Builder"),
        k("Engineer"),
        k("Surveyor"),
        k("Sewer Worker"),
        k("Construction Laborer"),
        // medicine
        doc("Physician"),
        doc("Surgeon"),
        doc("Healer"),
        doc("Apothecary"),
        doc("Herbalist"),
        doc("Midwife"),
        doc("Barber-Surgeon"),
        doc("Bonesetter"),
        doc("Nurse"),
        // knowledge
        l("Scholar"),
        l("Scribe"),
        l("Librarian"),
        l("Teacher"),
        l("Tutor"),
        l("Historian"),
        l("Cartographer"),
        l("Naturalist"),
        l("Astronomer"),
        l("Astrologer"),
        l("Mathematician"),
        l("Translator"),
        l("Archivist"),
        l("Researcher"),
        l("Philosopher"),
        l("Chronicler"),
        // arcane
        a("Alchemist"),
        a("Arcanist"),
        a("Mage"),
        a("Wizard"),
        a("Enchanter"),
        a("Occultist"),
        // faith
        p("Priest"),
        p("Priestess"),
        p("Acolyte"),
        p("Monk"),
        p("Nun"),
        p("Friar"),
        p("Preacher"),
        p("Missionary"),
        p("Shrine Keeper"),
        p("Temple Keeper"),
        p("Oracle"),
        p("Sacristan"),
        // funerary
        RoleDef("Cemetery Keeper", RoleCategory.FAITH, Archetype.PRIEST, Workplace.TEMPLE),
        RoleDef("Undertaker", RoleCategory.FAITH, Archetype.CRAFT, Workplace.TEMPLE),
        // governance
        g("Mayor"),
        g("Magistrate"),
        g("Judge"),
        g("Bailiff"),
        g("Sheriff"),
        g("Tax Collector"),
        g("Clerk"),
        g("Administrator"),
        g("Councilor"),
        g("Governor"),
        g("Steward"),
        g("Census Keeper"),
        g("Diplomat"),
        g("Ambassador"),
        g("Herald"),
        g("Notary"),
        // military
        mil("Guard"),
        mil("Watchman"),
        mil("Soldier"),
        mil("Militia Member"),
        mil("Patrolman"),
        mil("Constable"),
        mil("Guard Captain"),
        mil("Sergeant"),
        mil("Lieutenant"),
        mil("Captain"),
        mil("Commander"),
        mil("Jailor"),
        mil("Prison Warden"),
        mil("Investigator"),
        // security
        sec("Scout"),
        sec("Outrider"),
        sec("Bounty Hunter"),
        sec("Caravan Guard"),
        RoleDef("Gatekeeper", RoleCategory.SECURITY, Archetype.GUARD, Workplace.KEEP),
        // arts
        art("Bard"),
        art("Musician"),
        art("Singer"),
        art("Dancer"),
        art("Actor"),
        art("Performer"),
        art("Storyteller"),
        art("Jester"),
        art("Poet"),
        art("Painter"),
        art("Artist"),
        art("Street Performer"),
        // domestic service
        dom("Maid"),
        dom("Nanny"),
        dom("Servant"),
        dom("Personal Attendant"),
        dom("Caregiver"),
        dom("Groundskeeper", Workplace.FIELDS),
        // wilderness
        w("Ranger"),
        w("Explorer"),
        w("Treasure Hunter"),
        w("Monster Hunter"),
        w("Wilderness Guide"),
        // underworld
        u("Thief"),
        u("Pickpocket"),
        u("Burglar"),
        u("Cutpurse"),
        u("Smuggler"),
        u("Fence"),
        u("Poacher"),
        u("Bandit"),
        u("Robber"),
        u("Assassin"),
        u("Spy"),
        u("Informant"),
        u("Black Market Dealer"),
        u("Contraband Runner"),
        u("Gang Member"),
        u("Crime Boss"),
        // the common lot
        common("Beggar"),
        common("Unemployed"),
        common("Retired"),
        common("Student"),
        common("Homemaker"),
        common("Wanderer"),
        common("Pilgrim"),
        common("Refugee"),
        common("Prisoner"),
        // nobility
        n("Lord"),
        n("Lady"),
        n("Baron"),
        n("Baroness"),
        n("Count"),
        n("Countess"),
        n("Duke"),
        n("Duchess"),
        n("Estate Owner"),
        n("Landowner"),
        n("Courtier"),
        n("Chamberlain"),
        n("Estate Steward")
    )

    private val byNameMap: Map<String, RoleDef> = ALL.associateBy { it.name }

    val size: Int get() = ALL.size

    fun byName(name: String): RoleDef? = byNameMap[name]

    fun isValid(name: String): Boolean = byNameMap.containsKey(name)

    /** What a soul of this role is "doing at work", in the chronicler's words. */
    fun workText(role: String): String? = WORK_TEXT[role]

    private val WORK_TEXT = mapOf(
        "Miller" to "grinding grain at the mill",
        "Fisher" to "out on the water with the nets",
        "Hunter" to "out on the hunt",
        "Trapper" to "walking the traplines",
        "Forager" to "gathering in the wilds",
        "Miner" to "at the diggings",
        "Quarryman" to "splitting stone at the quarry",
        "Prospector" to "poking at the hillsides",
        "Smelter" to "tending the smelting fires",
        "Charcoal Burner" to "minding the charcoal clamp",
        "Lumberjack" to "felling timber in the woods",
        "Forester" to "walking the woods",
        "Salt Worker" to "boiling brine at the pans",
        "Clay Worker" to "digging and working the clay",
        "Gem Cutter" to "cutting and setting stones",
        "Sailor" to "rigging and repairing by the water",
        "Boatman" to "ferrying folk along the shore",
        "Ferryman" to "holding the ferry crossing",
        "Ship Captain" to "overseeing the boats",
        "Coachman" to "minding coach and horses",
        "Courier" to "carrying the post",
        "Messenger" to "running the day's messages",
        "Peddler" to "walking the roads with a pack",
        "Caravan Merchant" to "readying the caravan"
    )
}

/**
 * A soul's day, laid out in blocks of duty. Blocks are kept relative to the
 * soul's own waking hour, so every schedule tiles its full day exactly — and
 * the same role, dealt to two souls, gives each its own hours forever.
 */
class WorkSchedule private constructor(val wake: Float, val blocks: List<Block>) {

    data class Block(
        /** Hours after [wake] the block begins. */
        val start: Float,
        val end: Float,
        val duty: Duty,
        val activity: String,
        /** True for the night's rest, which the homeless spend out of the wind. */
        val isSleep: Boolean
    )

    /** The block ruling the given hour of the day, any hour of any day. */
    fun blockAt(hour: Float): Block {
        val q = ((hour - wake) % 24f + 24f) % 24f
        return blocks.firstOrNull { q >= it.start && q < it.end }
            ?: blocks.last()
    }

    fun isHomeAt(hour: Float): Boolean = blockAt(hour).duty == Duty.HOME

    fun activityAt(hour: Float, homeless: Boolean): String {
        val block = blockAt(hour)
        return if (block.isSleep && homeless) "huddled out of the wind" else block.activity
    }

    companion object {

        /** A soul's stable schedule key: the world's seed and the soul's own id. */
        fun key(worldSeed: Long, id: String): Long = worldSeed * 6151L + id.hashCode().toLong() * 31L

        /**
         * Deal a day to a soul: the role's archetype shapes the blocks, the
         * seed key (world seed and the soul's own stable id) draws its hours.
         * Blank or unknown roles keep a common loafer's day.
         */
        fun forRole(role: String, seedKey: Long, personality: Personality? = null): WorkSchedule {
            val def = ROLES.byName(role)
            val rng = Random(seedKey * 92821L + (def?.archetype?.ordinal ?: 0) * 127L)
            val archetype = def?.archetype ?: Archetype.LOAFER
            val workplace = def?.workplace ?: Workplace.WELL
            return build(archetype, workplace, role, rng, personality)
        }

        private fun build(
            archetype: Archetype,
            workplace: Workplace,
            role: String,
            rng: Random,
            personality: Personality? = null
        ): WorkSchedule {
            var wake = WAKE[archetype]!!(rng)
            if (personality != null) wake = (wake + personality.wakeAdjust()).coerceIn(3.2f, 10.5f)
            val work = ROLES.workText(role) ?: when (workplace) {
                Workplace.FIELDS -> "working the fields"
                Workplace.WATER -> "out on the water"
                Workplace.GATE -> "out beyond the gate"
                Workplace.TAVERN -> "serving in the hall"
                Workplace.TEMPLE -> "keeping the god's house"
                Workplace.KEEP -> "about the great house"
                Workplace.GUILD -> "at the guild hall"
                Workplace.MARKET -> "minding the stall"
                Workplace.HOME -> "at the workbench"
                Workplace.WELL -> "about the square"
            }
            val raw = mutableListOf<Triple<Duty, Float, String>>()
            when (archetype) {
                Archetype.RURAL -> {
                    raw += home(rng, 0.5f, 0.4f, "the morning meal")
                    raw += Triple(workDuty(workplace), 5f + rng.nextFloat() * 1.5f, work)
                    raw += Triple(Duty.WELL, 0.7f + rng.nextFloat() * 0.6f, "resting at the well")
                    raw += Triple(workDuty(workplace), 3.5f + rng.nextFloat() * 1.2f, "back to the ${workNoun(workplace)}")
                    raw += home(rng, 0.7f, 0.2f, "home for supper")
                }
                Archetype.CRAFT -> {
                    raw += home(rng, 0.6f, 0.3f, "the morning meal")
                    raw += Triple(workDuty(workplace), 4.2f + rng.nextFloat() * 1.4f, work)
                    raw += Triple(Duty.WELL, 0.7f + rng.nextFloat() * 0.5f, "a bite at the well")
                    raw += Triple(workDuty(workplace), 2.6f + rng.nextFloat() * 0.9f, work)
                    raw += Triple(Duty.MARKET, 0.9f + rng.nextFloat() * 0.6f, "offering the day's wares")
                }
                Archetype.BAKER -> {
                    raw += home(rng, 0.4f, 0.2f, "a bite before the ovens")
                    raw += Triple(workDuty(workplace), 3f + rng.nextFloat() * 0.8f, "kneading and firing the ovens")
                    raw += Triple(Duty.MARKET, 2.4f + rng.nextFloat() * 0.9f, "selling bread at the market")
                    raw += Triple(workDuty(workplace), 1.4f, "cleaning the ovens")
                    raw += home(rng, 0.6f, 0.2f, "the midday meal")
                    raw += Triple(workDuty(workplace), 1.6f, "preparing tomorrow's dough")
                }
                Archetype.MINER -> {
                    raw += home(rng, 0.4f, 0.2f, "a bite before dawn")
                    raw += Triple(Duty.GATE, 0.3f, "out with the first light")
                    raw += Triple(workDuty(workplace), 4.8f + rng.nextFloat() * 1.2f, work)
                    raw += Triple(Duty.WELL, 0.8f + rng.nextFloat() * 0.4f, "a meal at the well")
                    raw += Triple(workDuty(workplace), 3.8f + rng.nextFloat() * 1.1f, work)
                    raw += home(rng, 0.8f, 0.2f, "home for supper")
                }
                Archetype.MERCHANT -> {
                    raw += home(rng, 0.6f, 0.3f, "the morning meal")
                    raw += Triple(Duty.MARKET, 4.4f + rng.nextFloat() * 1.2f, work)
                    raw += Triple(Duty.WELL, 0.7f + rng.nextFloat() * 0.5f, "a bite at the well")
                    raw += Triple(Duty.MARKET, 3.4f + rng.nextFloat() * 1.1f, work)
                    raw += home(rng, 0.7f, 0.2f, "home for supper")
                }
                Archetype.TAVERNKEEP -> {
                    raw += home(rng, 0.4f, 0.2f, "the morning meal")
                    raw += Triple(Duty.TAVERN, 2.6f + rng.nextFloat() * 0.4f, "readying the hall")
                    raw += Triple(Duty.TAVERN, 3.2f + rng.nextFloat() * 0.5f, "serving the midday crowd")
                    raw += Triple(Duty.WELL, 0.6f, "a step out for air")
                    raw += Triple(Duty.TAVERN, 4f + rng.nextFloat() * 0.4f, "pouring and serving")
                    raw += Triple(Duty.TAVERN, 1f, "closing and securing the hall")
                }
                Archetype.GUARD -> {
                    raw += home(rng, 0.5f, 0.2f, "the morning meal")
                    raw += Triple(workDuty(workplace), 3.6f + rng.nextFloat() * 0.8f, "standing the watch")
                    raw += Triple(Duty.ROAD, 1.8f + rng.nextFloat() * 0.7f, "walking the patrol")
                    raw += Triple(Duty.WELL, 0.7f, "off duty at the well")
                    raw += Triple(workDuty(workplace), 3.8f + rng.nextFloat() * 0.8f, "back on the watch")
                }
                Archetype.PRIEST -> {
                    raw += home(rng, 0.5f, 0.2f, "the morning meal")
                    raw += Triple(Duty.TEMPLE, 2.8f + rng.nextFloat() * 0.6f, "at morning rites")
                    raw += Triple(Duty.WELL, 0.7f, "a walk among the folk")
                    raw += Triple(Duty.TEMPLE, 3.4f + rng.nextFloat() * 0.8f, "counsel and blessings")
                    raw += Triple(Duty.TEMPLE, 1.4f, "evening prayers")
                    raw += home(rng, 0.6f, 0.2f, "home for supper")
                }
                Archetype.SCHOLAR -> {
                    raw += home(rng, 0.6f, 0.3f, "the morning meal")
                    raw += Triple(workDuty(workplace), 3.8f + rng.nextFloat() * 1f, "among the shelves and ledgers")
                    raw += Triple(Duty.WELL, 0.8f, "a walk to stretch the mind")
                    raw += Triple(workDuty(workplace), 3.6f + rng.nextFloat() * 0.9f, "at the desk")
                    raw += home(rng, 0.5f, 0.2f, "home for the evening")
                }
                Archetype.NOBLE -> {
                    raw += home(rng, 0.8f, 0.2f, "breaking fast")
                    raw += Triple(workDuty(workplace), 3.2f + rng.nextFloat() * 0.8f, "holding court")
                    raw += home(rng, 1.2f, 0.2f, "the midday meal")
                    raw += Triple(workDuty(workplace), 2.8f + rng.nextFloat() * 0.7f, "receiving petitioners")
                    raw += Triple(Duty.WELL, 0.6f, "an airing in the square")
                }
                Archetype.HEALER -> {
                    raw += home(rng, 0.5f, 0.2f, "the morning meal")
                    raw += Triple(workDuty(workplace), 3.6f + rng.nextFloat() * 0.8f, "receiving the sick")
                    raw += Triple(Duty.WELL, 0.7f, "a walk among the folk")
                    raw += Triple(workDuty(workplace), 3.4f + rng.nextFloat() * 0.9f, "at the sickbeds")
                    raw += Triple(Duty.MARKET, 0.8f, "gathering herbs and dressings")
                }
                Archetype.LABORER -> {
                    raw += home(rng, 0.5f, 0.2f, "the morning meal")
                    raw += Triple(workDuty(workplace), 4.6f + rng.nextFloat() * 1.3f, work)
                    raw += Triple(Duty.WELL, 0.8f, "a meal at the well")
                    raw += Triple(workDuty(workplace), 3.4f + rng.nextFloat() * 1.1f, work)
                    raw += home(rng, 0.7f, 0.2f, "home for supper")
                }
                Archetype.PERFORMER -> {
                    raw += home(rng, 0.7f, 0.2f, "a late breakfast")
                    raw += Triple(Duty.WELL, 1.2f, "practicing by the well")
                    raw += Triple(Duty.MARKET, 3.4f + rng.nextFloat() * 0.9f, "playing for the crowd")
                    raw += Triple(Duty.WELL, 0.6f, "counting the day's coin")
                    raw += Triple(Duty.MARKET, 2.2f, "the evening set")
                    raw += Triple(Duty.TAVERN, 1f, "a tune in the tavern")
                }
                Archetype.UNDERWORLD -> {
                    raw += home(rng, 0.7f, 0.2f, "rising late")
                    raw += Triple(Duty.WELL, 1.8f + rng.nextFloat() * 0.6f, "lounging by the well")
                    raw += Triple(Duty.MARKET, 1.6f, "quiet trade in the shadows")
                    raw += Triple(Duty.TAVERN, 2.6f + rng.nextFloat() * 0.7f, "drinks and whispers")
                    raw += Triple(Duty.WELL, 0.7f, "watching the street")
                }
                Archetype.LOAFER -> {
                    raw += home(rng, 0.7f, 0.2f, "a slow start")
                    raw += Triple(Duty.WELL, 2.2f + rng.nextFloat() * 0.8f, "about the well and square")
                    raw += Triple(Duty.MARKET, 1.6f, "browsing the stalls")
                    raw += Triple(Duty.TAVERN, 2.4f + rng.nextFloat() * 0.7f, "a long drink")
                    raw += Triple(Duty.WELL, 0.7f, "back to the well")
                }
                Archetype.SERVICE -> {
                    raw += home(rng, 0.5f, 0.2f, "the morning meal")
                    raw += Triple(workDuty(workplace), 4.4f + rng.nextFloat() * 1f, "about the great house")
                    raw += Triple(Duty.WELL, 0.7f, "a step out for air")
                    raw += Triple(workDuty(workplace), 3.8f + rng.nextFloat() * 0.9f, "serving at table")
                    raw += home(rng, 0.6f, 0.2f, "home for supper")
                }
            }
            // the heart bends the hours: the diligent stretch the work, the idle
            // stretch the well and the meal — never the trade itself
            if (personality != null) {
                val core = workDuty(workplace)
                val workMult = personality.workMultiplier()
                val breakMult = personality.breakMultiplier()
                for (i in raw.indices) {
                    val (duty, hours, text) = raw[i]
                    when {
                        duty == core -> raw[i] = Triple(duty, (hours * workMult).coerceAtLeast(0.4f), text)
                        duty == Duty.WELL -> raw[i] = Triple(duty, (hours * breakMult).coerceAtLeast(0.3f), text)
                    }
                }
            }
            // close the day at the clock's own deadline: home by 20:00 wherever
            // the work got to, then sleep until each soul's own waking hour
            val cap = 20f - wake
            var t = raw.sumOf { it.second.toDouble() }.toFloat()
            if (t > cap) {
                // the work ran long: pull the day back inside the deadline
                var excess = t - cap
                for (i in raw.indices.reversed()) {
                    if (excess <= 0f) break
                    val (duty, hours, text) = raw[i]
                    if (duty == Duty.HOME) continue
                    val shave = minOf(excess, hours - 0.5f)
                    if (shave > 0f) {
                        raw[i] = Triple(duty, hours - shave, text)
                        excess -= shave
                    }
                }
                t = raw.sumOf { it.second.toDouble() }.toFloat()
            }
            val evening = minOf((18.2f + rng.nextFloat() * 0.8f).coerceAtLeast(t), cap)
            if (evening > t) {
                // the leisure hour goes where the heart pulls it
                val leisure = personality?.eveningDuty(rng) ?: Duty.HOME
                raw += Triple(leisure, evening - t, personality?.freeTimeActivity(leisure, role) ?: "at leisure at home")
            }
            val sleepStart = minOf(evening + 0.4f + rng.nextFloat() * 0.5f, cap)
            if (sleepStart > evening) raw += Triple(Duty.HOME, sleepStart - evening, "home for the evening")
            t = raw.sumOf { it.second.toDouble() }.toFloat()
            val blocks = mutableListOf<Block>()
            var cursor = 0f
            raw.forEach { (duty, hours, activity) ->
                blocks += Block(cursor, cursor + hours, duty, activity, isSleep = false)
                cursor += hours
            }
            blocks += Block(cursor, 24f, Duty.HOME, "asleep behind a barred door", isSleep = true)
            return WorkSchedule(wake, blocks)
        }

        private fun home(rng: Random, base: Float, jitter: Float, activity: String): Triple<Duty, Float, String> =
            Triple(Duty.HOME, base + rng.nextFloat() * jitter, activity)

        private fun workNoun(workplace: Workplace): String = when (workplace) {
            Workplace.FIELDS -> "fields"
            Workplace.WATER -> "water"
            Workplace.GATE -> "gate and the wilds beyond"
            else -> "work"
        }

        /** When each kind of day begins, with room for each soul's own temper. */
        private val WAKE: Map<Archetype, (Random) -> Float> = mapOf(
            Archetype.RURAL to { it.nextFloat() * 1.6f + 4.8f },
            Archetype.CRAFT to { it.nextFloat() * 1.1f + 5.9f },
            Archetype.BAKER to { it.nextFloat() * 0.7f + 3.3f },
            Archetype.MINER to { it.nextFloat() * 0.7f + 4.4f },
            Archetype.MERCHANT to { it.nextFloat() * 1.1f + 6.2f },
            Archetype.TAVERNKEEP to { it.nextFloat() * 0.8f + 6.5f },
            Archetype.GUARD to { it.nextFloat() * 0.9f + 5.8f },
            Archetype.PRIEST to { it.nextFloat() * 0.9f + 5.4f },
            Archetype.SCHOLAR to { it.nextFloat() * 1.1f + 6.4f },
            Archetype.NOBLE to { it.nextFloat() * 1.4f + 7.8f },
            Archetype.HEALER to { it.nextFloat() * 1f + 5.8f },
            Archetype.LABORER to { it.nextFloat() * 1.1f + 5f },
            Archetype.PERFORMER to { it.nextFloat() * 1.4f + 7.6f },
            Archetype.UNDERWORLD to { it.nextFloat() * 1.6f + 8.4f },
            Archetype.LOAFER to { it.nextFloat() * 2f + 6.8f },
            Archetype.SERVICE to { it.nextFloat() * 0.9f + 5.4f }
        )
    }
}

/** The duty a working block keeps, from where the role's day is spent. */
internal fun workDuty(workplace: Workplace): Duty = when (workplace) {
    Workplace.FIELDS -> Duty.FIELDS
    Workplace.WATER -> Duty.WATER
    Workplace.GATE -> Duty.GATE
    Workplace.MARKET -> Duty.MARKET
    Workplace.TAVERN -> Duty.TAVERN
    Workplace.TEMPLE -> Duty.TEMPLE
    else -> Duty.WORK
}

/**
 * The dealing of roles: from the world's seed, the place and the soul's own
 * station, once and forever. Deterministic — the same call names the same trade.
 */
object RoleBook {

    /** The role of the soul who keeps a building. */
    fun keeperRole(worldSeed: Long, site: Site, biome: Biome, buildingKind: String, buildingIndex: Int): String {
        val rng = Random(worldSeed * 77L + site.id * 8191L + buildingIndex * 97L)
        return when (buildingKind) {
            "tavern" -> pick(rng, listOf("Tavern Keeper", "Innkeeper", "Bartender"))
            "temple" -> pick(rng, listOf("Priest", "Priestess", "Temple Keeper"))
            "keep", "citadel" -> pick(rng, listOf("Captain", "Guard Captain", "Commander", "Sheriff"))
            "guild hall" -> pick(rng, listOf("Merchant", "Scholar", "Broker"))
            "market" -> pick(rng, listOf("Merchant", "Market Vendor", "Grocer"))
            "tent" -> pick(rng, listOf("Bandit", "Poacher", "Wanderer"))
            else -> civilian(worldSeed, site, biome, buildingIndex)
        }
    }

    /** The role of one of the homeless who keep to the heart of the place. */
    fun homelessRole(worldSeed: Long, site: Site, index: Int): String {
        val rng = Random(worldSeed * 77L + site.id * 8191L + 90077L + index * 31L)
        return pick(
            rng,
            listOf("Beggar", "Wanderer", "Peddler", "Bard", "Pilgrim", "Unemployed", "Storyteller", "Refugee", "Retired")
        )
    }

    /**
     * A civilian calling for a house's keeper, drawn from what the land, the
     * place's works and the place's size would actually sustain.
     */
    private fun civilian(worldSeed: Long, site: Site, biome: Biome, index: Int): String {
        val rng = Random(worldSeed * 131L + site.id * 7907L + index * 53L)
        val stage = site.kind
        val pool = mutableListOf<String>()

        // the land's own work comes first
        when (biome) {
            Biome.OCEAN -> pool += listOf("Fisher", "Fisher", "Boatman", "Sailor", "Fishmonger", "Shipwright", "Ropemaker", "Salt Worker")
            Biome.MARSH -> pool += listOf("Fisher", "Trapper", "Forager", "Clay Worker", "Salt Worker", "Ropemaker")
            Biome.FOREST -> pool += listOf("Lumberjack", "Forester", "Hunter", "Trapper", "Carpenter", "Fletcher", "Bowyer", "Charcoal Burner")
            Biome.HILLS, Biome.PEAK -> pool += listOf("Miner", "Quarryman", "Smelter", "Prospector", "Stonemason", "Gem Cutter", "Shepherd")
            Biome.DOWNS, Biome.MOOR -> pool += listOf("Shepherd", "Herder", "Weaver", "Dyer", "Farmer", "Rancher")
        }

        // what the place's own works would sustain
        val kinds = site.structures.filter { !it.ruined }.map { it.kind }.toSet()
        if (StructureKind.TAVERN in kinds) pool += listOf("Brewer", "Cook", "Server", "Kitchen Hand")
        if (StructureKind.MARKET in kinds) pool += listOf("Merchant", "Market Vendor", "Grocer", "Butcher", "Baker")
        if (StructureKind.TEMPLE in kinds) pool += listOf("Acolyte", "Cemetery Keeper", "Candlemaker", "Sacristan")
        if (StructureKind.GUILD in kinds) pool += listOf("Scribe", "Clerk", "Translator", "Notary")
        if (StructureKind.CATACOMB in kinds) pool += listOf("Undertaker", "Cemetery Keeper")

        // the weight of the place itself
        when {
            stage == SiteKind.CAPITAL -> pool += listOf(
                "Tailor", "Jeweler", "Goldsmith", "Physician", "Scholar", "Bard", "Painter",
                "Moneychanger", "Armor Merchant", "Arms Merchant", "Courtier", "Administrator", "Herald"
            )
            stage == SiteKind.CITY -> pool += listOf(
                "Tailor", "Potter", "Physician", "Apothecary", "Teacher", "Bard", "Moneychanger",
                "Merchant", "Locksmith", "Engineer", "Guard", "Watchman"
            )
            stage == SiteKind.TOWN -> pool += listOf(
                "Blacksmith", "Carpenter", "Tailor", "Weaver", "Baker", "Cobbler", "Cooper",
                "Merchant", "Guard", "Watchman", "Midwife"
            )
            else -> pool += listOf(
                "Farmer", "Farmer", "Blacksmith", "Carpenter", "Weaver", "Midwife", "Miller", "Baker", "Beekeeper"
            )
        }
        return pick(rng, pool)
    }

    internal fun pick(rng: Random, pool: List<String>): String {
        val known = pool.filter { ROLES.isValid(it) }
        if (known.isEmpty()) return "Farmer"
        return known[rng.nextInt(known.size)]
    }
}
