package com.rork.hollowmarch.game

/**
 * The eight families of close combat the province knows, and the five crafts of
 * the mark. Every melee arm belongs to exactly one; the bow belongs to none of
 * the eight — a bow swung in desperation counts as bare hands, and its true
 * craft lives in Marksmanship, beside the crossbow, the powder arm, the sling
 * and the thrown steel.
 */
enum class WeaponCategory(val label: String) {
    SWORDS("Swords"),
    AXES("Axes"),
    MACES("Maces"),
    HAMMERS("Hammers"),
    POLEARMS("Polearms"),
    STAVES("Staves"),
    DAGGERS("Daggers"),
    UNARMED("Unarmed"),
    ARCHERY("Archery"),
    CROSSBOWS("Crossbows"),
    FIREARMS("Firearms"),
    SLINGS("Slings"),
    THROWN("Thrown Arms");

    companion object {
        /** The family a shape belongs to, or null for arms that are no one's melee craft. */
        fun forWeapon(archetype: ItemArchetype): WeaponCategory? = when (archetype) {
            // Swords — edges held short, in one hand or a long arm of steel.
            ItemArchetype.BLADE, ItemArchetype.SHORTSWORD, ItemArchetype.FALCHION,
            ItemArchetype.MESSER, ItemArchetype.SABRE, ItemArchetype.SCIMITAR,
            ItemArchetype.ARMING_SWORD, ItemArchetype.ULFBERHT, ItemArchetype.KATANA,
            ItemArchetype.RAPIER, ItemArchetype.ESTOC -> SWORDS

            // Axes — cleaving edges on a haft.
            ItemArchetype.AXE, ItemArchetype.BATTLE_AXE, ItemArchetype.DANE_AXE -> AXES

            // Maces — weighted striking heads, from the hedgerow club up.
            ItemArchetype.MACE, ItemArchetype.CLUB, ItemArchetype.BLUDGEON,
            ItemArchetype.FLAIL, ItemArchetype.FLANGED_MACE, ItemArchetype.MORNING_STAR,
            ItemArchetype.TORCH -> MACES

            // Hammers — the crushing trades with a face and a spike.
            ItemArchetype.WAR_HAMMER, ItemArchetype.SHESTOPYOR,
            ItemArchetype.HORSEMANS_PICK, ItemArchetype.BEC_DE_CORBIN -> HAMMERS

            // Polearms — every head at the end of a long ash shaft.
            ItemArchetype.SPEAR, ItemArchetype.BILL, ItemArchetype.GLAIVE,
            ItemArchetype.GUANDAO, ItemArchetype.PUDAO, ItemArchetype.SOVNYA,
            ItemArchetype.NAGINATA, ItemArchetype.BARDICHE, ItemArchetype.WAR_SCYTHE,
            ItemArchetype.PIKE, ItemArchetype.POLEAXE, ItemArchetype.HALBERD,
            ItemArchetype.HARPOON, ItemArchetype.TRIDENT, ItemArchetype.LANCE,
            ItemArchetype.PLANCON_A_PICOT -> POLEARMS

            // Staves — the pilgrim's six feet of ash.
            ItemArchetype.QUARTERSTAFF -> STAVES

            // Daggers — the point that rides the belt.
            ItemArchetype.DAGGER, ItemArchetype.KNIFE -> DAGGERS

            // The marksmanship crafts — fed by Marksmanship, not Melee.
            ItemArchetype.BOW -> ARCHERY
            ItemArchetype.CROSSBOW -> CROSSBOWS
            ItemArchetype.ARQUEBUS, ItemArchetype.HAND_CANNON, ItemArchetype.THREE_EYE_CANNON -> FIREARMS
            ItemArchetype.SLING -> SLINGS
            ItemArchetype.THROWING_KNIFE, ItemArchetype.FRANCISCA,
            ItemArchetype.CHAKRAM, ItemArchetype.SHURIKEN -> THROWN

            else -> null
        }
    }
}

/**
 * A body's familiarity with each family of arm — a second ledger beside the
 * Melee skill, on the same scale and fed the same way. Melee answers "how well
 * do you fight"; proficiency answers "how well do you fight with this kind of
 * weapon". Grows only through use. Encoded whole for the save.
 */
class Proficiencies private constructor(
    private val values: MutableMap<WeaponCategory, Int>,
    private val bars: MutableMap<WeaponCategory, Float>
) {

    /** Current standing with a family; the untried sit at the floor. */
    fun value(category: WeaponCategory): Int = values[category] ?: MIN

    /**
     * Feed [category]'s bar by [amount] of practice; the next level costs more
     * the higher the standing, exactly as the skills do. Returns the rise, if any.
     */
    fun feed(category: WeaponCategory, amount: Float): Int? {
        if (amount <= 0f) return null
        val current = value(category)
        if (current >= MAX) return null
        val now = (bars[category] ?: 0f) + amount
        return if (now >= threshold(current)) {
            bars[category] = now - threshold(current)
            values[category] = current + 1
            current + 1
        } else {
            bars[category] = now
            null
        }
    }

    /** How much practice the next level of standing costs. */
    fun threshold(current: Int): Float = 8f + current * 2f

    fun progressFraction(category: WeaponCategory): Float =
        ((bars[category] ?: 0f) / threshold(value(category))).coerceIn(0f, 0.999f)

    /** Compact form for the save. */
    fun encode(): String = WeaponCategory.entries.joinToString(ENTRY) {
        "${it.name}$FIELD${value(it)}$FIELD${bars[it] ?: 0f}"
    }

    companion object {
        const val MIN = 1
        const val MAX = 20

        private const val ENTRY = "\u001E"
        private const val FIELD = "="

        /** The family each calling's hands know first. */
        private val CLASS_FAVOR = mapOf(
            "warden" to WeaponCategory.SWORDS,
            "skulk" to WeaponCategory.DAGGERS,
            "caller" to WeaponCategory.STAVES,
            "cantor" to WeaponCategory.MACES,
            "wayfarer" to WeaponCategory.POLEARMS,
            "bulwark" to WeaponCategory.HAMMERS,
            "hedgewitch" to WeaponCategory.DAGGERS,
            "ascetic" to WeaponCategory.UNARMED
        )

        fun favoredOf(klass: ActorClass?): WeaponCategory? =
            klass?.let { CLASS_FAVOR[it.key] }

        /** A fresh ledger: every family at the floor, the calling's own favored a touch ahead. */
        fun forClass(klass: ActorClass?): Proficiencies {
            val values = mutableMapOf<WeaponCategory, Int>()
            WeaponCategory.entries.forEach { values[it] = MIN }
            favoredOf(klass)?.let { values[it] = MIN + 2 }
            return Proficiencies(values, mutableMapOf())
        }

        /**
         * A creature of the province: the arm it carries decides where its years
         * of use went — a guard's hand knows its blade, a civilian's knows little.
         */
        fun forNpc(level: Int, weapon: Item?, klass: ActorClass?): Proficiencies {
            val carried = weapon?.let { WeaponCategory.forWeapon(it.archetype) }
            val favored = carried ?: favoredOf(klass) ?: WeaponCategory.UNARMED
            val second = favoredOf(klass)?.takeIf { it != favored }
            val proficiencies = forClass(null)
            proficiencies.values[favored] = (MIN + level).coerceAtMost(12)
            second?.let { proficiencies.values[it] = (MIN + level / 2).coerceAtMost(8) }
            return proficiencies
        }

        /** Restore from [encode]; a mangled ledger wakes fresh, never broken. */
        fun fromEncoded(raw: String?, klass: ActorClass? = null): Proficiencies {
            val proficiencies = forClass(klass)
            if (raw.isNullOrBlank()) return proficiencies
            raw.split(ENTRY).forEach { piece ->
                val parts = piece.split(FIELD)
                if (parts.size != 3) return@forEach
                val category = WeaponCategory.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
                val value = parts[1].toIntOrNull() ?: return@forEach
                val bar = parts[2].toFloatOrNull() ?: 0f
                if (value in MIN..MAX && bar >= 0f) {
                    proficiencies.values[category] = value
                    proficiencies.bars[category] = bar
                }
            }
            return proficiencies
        }
    }
}

/**
 * Familiarity with one particular weapon, held per piece: the same ledger keyed
 * by each item's instance id, so two blades cut from one steel grow apart. A new
 * weapon in hand knows nothing; a lifetime beside one weapon learns it wholly.
 * Encoded whole for the save.
 */
class Masteries private constructor(
    private val values: MutableMap<Int, Int>,
    private val bars: MutableMap<Int, Float>
) {

    /** How well this body knows the piece with [uid]; the stranger knows nothing. */
    fun value(uid: Int): Int = values[uid] ?: MIN

    /**
     * Feed the familiarity with the piece [uid]; the bar fills slower than a
     * skill's — high mastery is a long companionship, not an afternoon.
     */
    fun feed(uid: Int, amount: Float): Int? {
        if (uid <= 0 || amount <= 0f) return null
        val current = value(uid)
        if (current >= MAX) return null
        val now = (bars[uid] ?: 0f) + amount
        return if (now >= threshold(current)) {
            bars[uid] = now - threshold(current)
            values[uid] = current + 1
            current + 1
        } else {
            bars[uid] = now
            null
        }
    }

    /** How much companionship the next degree of familiarity costs. */
    fun threshold(current: Int): Float = 10f + current * 3f

    fun progressFraction(uid: Int): Float =
        ((bars[uid] ?: 0f) / threshold(value(uid))).coerceIn(0f, 0.999f)

    /** The pieces this body knows beyond a stranger's first grip. */
    fun knownWeapons(): Set<Int> = values.keys.filter { value(it) > MIN }.toSet()

    /** Compact form for the save. */
    fun encode(): String = values.keys.sorted().joinToString(ENTRY) { uid ->
        "$uid$FIELD${value(uid)}$FIELD${bars[uid] ?: 0f}"
    }

    companion object {
        const val MIN = 0
        const val MAX = 20

        private const val ENTRY = "\u001E"
        private const val FIELD = "="

        fun empty(): Masteries = Masteries(mutableMapOf(), mutableMapOf())

        /** Restore from [encode]; a mangled ledger wakes fresh, never broken. */
        fun fromEncoded(raw: String?): Masteries {
            val masteries = empty()
            if (raw.isNullOrBlank()) return masteries
            raw.split(ENTRY).forEach { piece ->
                val parts = piece.split(FIELD)
                if (parts.size != 3) return@forEach
                val uid = parts[0].toIntOrNull() ?: return@forEach
                val value = parts[1].toIntOrNull() ?: return@forEach
                val bar = parts[2].toFloatOrNull() ?: 0f
                if (uid > 0 && value in MIN..MAX && bar >= 0f) {
                    masteries.values[uid] = value
                    masteries.bars[uid] = bar
                }
            }
            return masteries
        }
    }
}

/**
 * The smith's ledger of made pieces: every weapon that leaves a forge, a grave
 * or a satchel carries its own mark, so mastery can tell one blade from its twin.
 * Restored from the save so a returned-to piece keeps its history.
 */
object ItemUids {
    private var next = 1

    /** The next free mark, and note that it is taken. */
    fun take(): Int = next++

    /** The mark the next piece would bear. */
    fun current(): Int = next

    /** Continue the ledger where a save left off; never hand a mark out twice. */
    fun restore(value: Int) {
        if (value > next) next = value
    }

    /** A test's fresh ledger. */
    fun reset(value: Int = 1) {
        next = value
    }
}
