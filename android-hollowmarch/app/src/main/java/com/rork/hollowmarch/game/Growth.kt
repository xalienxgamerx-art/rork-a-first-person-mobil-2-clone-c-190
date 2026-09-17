package com.rork.hollowmarch.game

/**
 * Learn by doing, in the old style: deeds feed skills, a filled skill bar raises
 * the skill, every skill that rises pushes its governing attribute toward its own
 * rise, and every third skill rise grants a character level. Nothing is spent —
 * skills grow only through use. Encoded whole into the save.
 */
class Growth private constructor(
    private val values: MutableMap<Skill, Int>,
    private val bars: MutableMap<Skill, Float>,
    private val attrProgress: MutableMap<Attr, Float>,
    var level: Int,
    private var rises: Int
) {

    /** Current skill level; anything unlearned sits at the floor. */
    fun value(skill: Skill): Int = values[skill] ?: Skill.MIN

    /**
     * Fill [skill]'s bar by [amount]; its difficulty scales with the skill's
     * current value. When the bar fills the skill rises, its governing attribute
     * gains learning, and the rise may grant a level. Returns the rise, if any.
     */
    fun feed(skill: Skill, stats: StatBlock, amount: Float): SkillRise? {
        if (amount <= 0f) return null
        val current = value(skill)
        if (current >= Skill.MAX) return null
        val now = (bars[skill] ?: 0f) + amount
        return if (now >= skillThreshold(current)) {
            bars[skill] = now - skillThreshold(current)
            values[skill] = current + 1
            rises++
            val attrRise = riseAttr(skill.attr, stats)
            if (rises % RISES_PER_LEVEL == 0) level++
            SkillRise(skill, attrRise)
        } else {
            bars[skill] = now
            null
        }
    }

    /** How much learning the next skill level costs, growing as the skill does. */
    fun skillThreshold(skillValue: Int): Float = 8f + skillValue * 2f

    fun progressFraction(skill: Skill): Float =
        ((bars[skill] ?: 0f) / skillThreshold(value(skill))).coerceIn(0f, 0.999f)

    /** How far the attribute is from its next rise; fed by the skills beneath it. */
    fun attrThreshold(statValue: Int): Float = 12f + statValue * 2f

    fun attrProgressFraction(attr: Attr, statValue: Int): Float =
        ((attrProgress[attr] ?: 0f) / attrThreshold(statValue)).coerceIn(0f, 0.999f)

    /** A skill that rises pours a measure of learning into its governing attribute. */
    private fun riseAttr(attr: Attr, stats: StatBlock): Attr? {
        val now = (attrProgress[attr] ?: 0f) + SKILL_ATTR_FEED
        return if (now >= attrThreshold(stats[attr])) {
            attrProgress[attr] = now - attrThreshold(stats[attr])
            stats.adjust(attr, 1)
            attr
        } else {
            attrProgress[attr] = now
            null
        }
    }

    /**
     * Compact form for the save: level, a legacy slot kept so old saves keep their
     * section positions, rises, skill values, attribute bars.
     */
    fun encode(): String = listOf(
        "$level",
        "0",
        "$rises",
        Skill.entries.joinToString(ENTRY) { "${it.name}$FIELD${value(it)}" },
        attrProgress.entries.joinToString(ENTRY) { "${it.key.name}$FIELD${it.value}" }
    ).joinToString(SECTION)

    companion object {
        private const val RISES_PER_LEVEL = 3

        /** Learning each skill rise pours into its governing attribute's bar. */
        private const val SKILL_ATTR_FEED = 2f

        private const val SECTION = "\u001F"
        private const val ENTRY = "\u001E"
        private const val FIELD = "="

        /** A fresh life: every skill at its class-favored start, nothing learned yet. */
        fun forClass(klass: ActorClass?): Growth {
            val values = mutableMapOf<Skill, Int>()
            Skill.entries.forEach { skill ->
                // A class weights attributes; its weight decides where its skills begin.
                values[skill] = Skill.MIN + (klass?.weights?.get(skill.attr) ?: 0)
            }
            return Growth(values, mutableMapOf(), mutableMapOf(), 1, 0)
        }

        /**
         * Restore from [encode]; a blank or mangled string wakes fresh with the
         * class's favored skills. Old attribute-shaped saves keep their level and
         * points but wake their skills new — the old bars do not leak into skills.
         */
        fun fromEncoded(raw: String?, klass: ActorClass? = null): Growth {
            val growth = forClass(klass)
            if (raw.isNullOrBlank()) return growth
            val sections = raw.split(SECTION)
            growth.level = sections.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(1) ?: growth.level
            growth.rises = sections.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            if (sections.size > 3) {
                sections[3].split(ENTRY).forEach { piece ->
                    val parts = piece.split(FIELD, limit = 2)
                    if (parts.size != 2) return@forEach
                    val skill = Skill.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
                    val value = parts[1].toIntOrNull() ?: return@forEach
                    growth.values[skill] = value.coerceIn(Skill.MIN, Skill.MAX)
                }
            }
            if (sections.size > 4) {
                sections[4].split(ENTRY).forEach { piece ->
                    val parts = piece.split(FIELD, limit = 2)
                    if (parts.size != 2) return@forEach
                    val attr = Attr.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
                    val value = parts[1].toFloatOrNull() ?: return@forEach
                    if (value >= 0f) growth.attrProgress[attr] = value
                }
            }
            return growth
        }
    }
}

/** A skill that rose, and the attribute (if any) its rise pushed over its own bar. */
data class SkillRise(val skill: Skill, val attrRise: Attr?)
