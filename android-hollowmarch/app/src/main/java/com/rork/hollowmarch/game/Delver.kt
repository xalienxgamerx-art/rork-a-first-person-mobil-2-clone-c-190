package com.rork.hollowmarch.game

import kotlin.math.roundToInt
import kotlin.random.Random

/** What the player locks in on the forge sheet: an attribute spread and a class key. */
data class DelverCreation(
    val stats: Map<Attr, Int> = emptyMap(),
    val classKey: String = ""
)

/** Point-buy arithmetic for the forge sheet. */
object CharacterForge {
    /** Points to distribute beyond the balanced base. */
    const val POOL = 20

    /** No attribute may begin above this. */
    const val CREATION_CAP = 15

    fun spent(allocated: Map<Attr, Int>): Int =
        allocated.entries.sumOf { (attr, value) -> (value - StatBlock.BASE).coerceAtLeast(0) }

    fun remaining(allocated: Map<Attr, Int>): Int = POOL - spent(allocated)

    fun canSpend(allocated: Map<Attr, Int>, attr: Attr): Boolean =
        remaining(allocated) > 0 && (allocated[attr] ?: StatBlock.BASE) < CREATION_CAP

    fun canRefund(allocated: Map<Attr, Int>, attr: Attr): Boolean =
        (allocated[attr] ?: StatBlock.BASE) > StatBlock.BASE
}

/** Every number the engine reads, derived from the eight attributes and the skills beneath them. */
object Derived {

    private fun lv(skills: Growth?, skill: Skill): Int = skills?.value(skill) ?: 0

    fun maxVitality(stats: StatBlock, skills: Growth? = null): Int =
        40 + stats[Attr.VIGOR] * 3 + lv(skills, Skill.ENDURANCE) / 2

    fun maxFatigue(stats: StatBlock, skills: Growth? = null): Int =
        24 + stats[Attr.VIGOR] * 2 + stats[Attr.SWIFTNESS] + lv(skills, Skill.ENDURANCE)

    fun maxMagicka(stats: StatBlock, skills: Growth? = null): Int =
        12 + stats[Attr.INTELLECT] * 3 + lv(skills, Skill.LORE) / 4

    /** Swifter hands swing again sooner, a practiced blade sooner still, and a familiar one sooner yet; the weapon's quickness has its say. */
    fun strikeCooldown(
        stats: StatBlock,
        skills: Growth? = null,
        weapon: Item? = null,
        proficiency: Int = 0,
        mastery: Int = 0
    ): Float =
        ((0.58f - stats[Attr.FINESSE] * 0.016f - lv(skills, Skill.MELEE) * 0.004f -
            proficiency * 0.003f - mastery * 0.002f) /
            (weapon?.speedFactor() ?: 1f)).coerceIn(0.22f, 0.7f)

    fun moveSpeed(stats: StatBlock, skills: Growth? = null): Float =
        1f + (stats[Attr.SWIFTNESS] - StatBlock.BASE) * 0.012f + lv(skills, Skill.ATHLETICS) * 0.004f

    /** Wise eyes need less torch to see the same dark, and hard travel teaches what to watch. */
    fun torchDrain(stats: StatBlock, skills: Growth? = null): Float =
        1f / (1f + (stats[Attr.WISDOM] - StatBlock.BASE) * 0.03f) /
            (1f + lv(skills, Skill.SURVIVAL) * 0.01f)

    /** Critical hit chance, in percent. */
    fun critChance(stats: StatBlock): Int = 3 + stats[Attr.FORTUNE]

    /**
     * Melee accuracy, in percent: Finesse guides the hand, Melee drills it, the
     * weapon's family is known by practice, and the piece itself by companionship.
     * Even the surest blade in the province finds air now and then.
     */
    fun meleeAccuracy(
        stats: StatBlock,
        skills: Growth? = null,
        proficiency: Int = 0,
        mastery: Int = 0
    ): Int {
        val teachers = stats[Attr.FINESSE] + lv(skills, Skill.MELEE) + proficiency + mastery
        return (55f + (teachers - 20) * 1.2f).roundToInt().coerceIn(15, 95)
    }

    /**
     * What worn armor steals from the dodge — never from Swiftness itself. Each
     * piece is judged by its temper (cloth bends, plate does not), its bulk, and
     * the weight of its material; the sum is capped, so even a full harness of
     * plate leaves a sliver of a chance to move.
     */
    fun armorDodgePenalty(worn: List<Item>): Int =
        worn.filter { it.archetype.slot == ItemSlot.BODY }
            .sumOf { item ->
                val temper = when (item.archetype.armorTemper) {
                    ArmorTemper.CLOTH -> 1f
                    ArmorTemper.EVEN -> 1.5f
                    ArmorTemper.LEATHER -> 4f
                    ArmorTemper.MAIL -> 8f
                    ArmorTemper.SCALE -> 9f
                    ArmorTemper.PLATE -> 12f
                }
                val bulk = 0.5f + item.archetype.baseArmor * 0.5f
                val material = 0.6f + item.material.weightFactor() * 0.45f
                (temper * bulk * material).toDouble()
            }
            .roundToInt()
            .coerceAtMost(MAX_ARMOR_PENALTY)

    private const val MAX_ARMOR_PENALTY = 20

    /**
     * Dodge chance, in percent: Swiftness and trained Defense, dragged down by
     * what is worn. Swiftness itself is the body's own and never changes with
     * the wardrobe.
     */
    fun dodgeChance(stats: StatBlock, skills: Growth? = null, worn: List<Item> = emptyList()): Int =
        (((stats[Attr.SWIFTNESS] - StatBlock.BASE) * 2).coerceIn(0, 20) +
            lv(skills, Skill.DEFENSE) / 4 - armorDodgePenalty(worn))
            .coerceIn(0, 25)

    fun mendPotency(stats: StatBlock, skills: Growth? = null): Float =
        1f + (stats[Attr.INTELLECT] - StatBlock.BASE) * 0.06f + lv(skills, Skill.MEDICINE) * 0.02f

    fun lootFactor(stats: StatBlock): Float =
        1f + (stats[Attr.FORTUNE] - StatBlock.BASE) * 0.08f

    /** A commanding presence makes good deeds weigh more in the province's memory. */
    fun rewardFactor(stats: StatBlock): Float =
        1f + (stats[Attr.PRESENCE] - StatBlock.BASE) * 0.08f

    /** Innkeepers judge the face before the bill, and a trader knows what a room is worth. */
    fun priceFactor(stats: StatBlock, skills: Growth? = null): Float =
        (1f - (stats[Attr.PRESENCE] - StatBlock.BASE) * 0.04f - lv(skills, Skill.TRADE) * 0.008f)
            .coerceIn(0.5f, 1.25f)

    /**
     * Your arm's harm: the weapon's bite — shape, temper and make — with your Might
     * behind it, your trained Melee to guide it, your practiced hand with its kind
     * of arm, and the familiarity earned beside this one particular piece. Bare
     * knuckles do what they can.
     */
    fun strikeDamage(
        stats: StatBlock,
        skills: Growth? = null,
        weapon: Item? = null,
        rng: Random,
        proficiency: Int = 0,
        mastery: Int = 0
    ): Int {
        val arm = lv(skills, Skill.MELEE) / 3 + proficiency / 3 + mastery / 4
        return if (weapon != null) {
            weapon.damage() + stats[Attr.MIGHT] / 2 + arm + rng.nextInt(3)
        } else {
            1 + stats[Attr.MIGHT] / 3 + arm + rng.nextInt(2)
        }
    }

    /** What worn armor turns of a given harm: every piece's temper and make, summed. */
    fun armorSoak(worn: List<Item>, type: DamageType): Int =
        worn.sumOf { it.armorValue(type).toDouble() }.roundToInt()

    /** What your back will bear: Might carries, trained Endurance carries more. */
    fun carryCapacity(stats: StatBlock, skills: Growth? = null): Float =
        24f + stats[Attr.MIGHT] * 2.5f + lv(skills, Skill.ENDURANCE) * 0.6f

    /**
     * How the load slows the stride: easy up to seven tenths full, a lean forward
     * near the limit, and a trudge when the seams strain past it.
     */
    fun encumbranceFactor(load: Float, capacity: Float): Float {
        if (capacity <= 0f) return 1f
        val fraction = load / capacity
        return when {
            fraction <= 0.7f -> 1f
            fraction <= 1f -> 1f - (fraction - 0.7f) * 0.6f
            else -> 0.58f
        }
    }

    // ------------------------------------------------------------ creatures

    /** A creature's health: its Vigor, the Endurance it has trained, its encounter level. */
    fun npcMaxHp(stats: StatBlock, skills: Growth? = null, level: Int): Int =
        8 + stats[Attr.VIGOR] * 2 + lv(skills, Skill.ENDURANCE) / 2 + level * 2

    /** A creature's harm: Might and Melee and its level — more, when it swings a practiced arm it knows. */
    fun npcDamage(
        stats: StatBlock,
        skills: Growth? = null,
        level: Int,
        weapon: Item? = null,
        proficiency: Int = 0,
        mastery: Int = 0
    ): Int {
        val arm = stats[Attr.MIGHT] / 2 + lv(skills, Skill.MELEE) / 3 + level / 2 +
            proficiency / 3 + mastery / 4
        return if (weapon != null) {
            (weapon.damage() * 0.8f).roundToInt() + arm
        } else {
            1 + arm
        }
    }

    /** A creature's stride: its Swiftness and the Athletics it has earned on the chase. */
    fun npcSpeed(stats: StatBlock, skills: Growth? = null, base: Float): Float =
        (base + (stats[Attr.SWIFTNESS] - StatBlock.BASE) * 0.03f + lv(skills, Skill.ATHLETICS) * 0.01f)
            .coerceIn(0.4f, 2.4f)

    // ------------------------------------------------------------ sneak

    /**
     * How hard you are to notice: trained Stealth and a light stride, doubled
     * when you keep your weight on your heels.
     */
    fun stealthScore(stats: StatBlock, skills: Growth? = null, crouched: Boolean = false): Float =
        lv(skills, Skill.STEALTH) * 2f +
            (stats[Attr.SWIFTNESS] - StatBlock.BASE) * 1.5f +
            lv(skills, Skill.SLEIGHT_OF_HAND) * 0.5f +
            (if (crouched) 30f else 0f)

    /** How well a creature notices: its trained Awareness and the Wisdom beneath it. */
    fun detectionScore(stats: StatBlock, skills: Growth? = null): Float =
        6f + lv(skills, Skill.AWARENESS) * 2f + (stats[Attr.WISDOM] - StatBlock.BASE) * 2f

    /**
     * The distance in tiles at which the contest is lost: a measure of sight
     * against a measure of quiet. In the deep dark a crouched delver is all but gone.
     */
    fun detectionRadius(sight: Float, stealth: Float): Float =
        (10f + (sight - stealth) * 0.25f).coerceIn(0.8f, 14f)

    /** A blow from where it isn't looking: far worse, and worst from a dagger or a knife. */
    fun sneakAttackMultiplier(archetype: ItemArchetype? = null): Float =
        if (archetype == ItemArchetype.DAGGER || archetype == ItemArchetype.KNIFE) 2.5f else 2f

    /** Sleight against Awareness, in percent; the trained fingers and the dull guard. */
    fun pickpocketChance(sleight: Int, awareness: Int): Int =
        (55 + sleight * 2 - awareness * 2).coerceIn(10, 90)
}
