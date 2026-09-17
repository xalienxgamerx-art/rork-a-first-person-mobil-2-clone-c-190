package com.rork.hollowmarch.game

/** The four families of craft the province knows. */
enum class SkillGroup(val label: String) {
    PHYSICAL("Physical"),
    PRACTICAL("Survival & Practical"),
    KNOWLEDGE("Knowledge"),
    SOCIAL("Social")
}

/**
 * The twenty-seven skills of the province, each bound to the attribute it
 * exercises. Deeds feed skills; skills harden their governing attribute.
 * Fortune alone has no skills — fate is the one thing that cannot be taught.
 */
enum class Skill(val label: String, val group: SkillGroup, val attr: Attr) {
    // Physical
    ATHLETICS("Athletics", SkillGroup.PHYSICAL, Attr.MIGHT),
    MELEE("Melee", SkillGroup.PHYSICAL, Attr.MIGHT),
    MARKSMANSHIP("Marksmanship", SkillGroup.PHYSICAL, Attr.FINESSE),
    DEFENSE("Defense", SkillGroup.PHYSICAL, Attr.VIGOR),
    ENDURANCE("Endurance", SkillGroup.PHYSICAL, Attr.VIGOR),
    STEALTH("Stealth", SkillGroup.PHYSICAL, Attr.SWIFTNESS),
    SLEIGHT_OF_HAND("Sleight of Hand", SkillGroup.PHYSICAL, Attr.FINESSE),

    // Survival & Practical
    SURVIVAL("Survival", SkillGroup.PRACTICAL, Attr.WISDOM),
    AWARENESS("Awareness", SkillGroup.PRACTICAL, Attr.WISDOM),
    MEDICINE("Medicine", SkillGroup.PRACTICAL, Attr.INTELLECT),
    ANIMAL_HANDLING("Animal Handling", SkillGroup.PRACTICAL, Attr.PRESENCE),
    AGRICULTURE("Agriculture", SkillGroup.PRACTICAL, Attr.MIGHT),
    CRAFTSMANSHIP("Craftsmanship", SkillGroup.PRACTICAL, Attr.FINESSE),
    LOCKPICKING("Lockpicking", SkillGroup.PRACTICAL, Attr.FINESSE),

    // Knowledge
    LORE("Lore", SkillGroup.KNOWLEDGE, Attr.INTELLECT),
    SCHOLARSHIP("Scholarship", SkillGroup.KNOWLEDGE, Attr.INTELLECT),
    INVESTIGATION("Investigation", SkillGroup.KNOWLEDGE, Attr.WISDOM),
    ENGINEERING("Engineering", SkillGroup.KNOWLEDGE, Attr.INTELLECT),
    ALCHEMY("Alchemy", SkillGroup.KNOWLEDGE, Attr.INTELLECT),
    NAVIGATION("Navigation", SkillGroup.KNOWLEDGE, Attr.WISDOM),

    // Social
    PERSUASION("Persuasion", SkillGroup.SOCIAL, Attr.PRESENCE),
    DECEPTION("Deception", SkillGroup.SOCIAL, Attr.PRESENCE),
    INTIMIDATION("Intimidation", SkillGroup.SOCIAL, Attr.MIGHT),
    LEADERSHIP("Leadership", SkillGroup.SOCIAL, Attr.PRESENCE),
    EMPATHY("Empathy", SkillGroup.SOCIAL, Attr.WISDOM),
    ETIQUETTE("Etiquette", SkillGroup.SOCIAL, Attr.PRESENCE),
    TRADE("Trade", SkillGroup.SOCIAL, Attr.INTELLECT);

    companion object {
        const val MIN = 1
        const val MAX = 20
    }
}
