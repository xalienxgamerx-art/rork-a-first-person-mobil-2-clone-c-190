package com.rork.hollowmarch.game

/** How one blow ended: into the mark, into the air, or turned aside by a body moving faster. */
enum class StrikeOutcome { HIT, MISSED, DODGED, BLOCKED }

/**
 * One resolved melee exchange, kept as the verification ledger behind the
 * combat log's poetry: every number the hit and dodge rolls drew on, and what
 * the armor finally made of the blow. Nothing here is shown to the player;
 * tests and debugging read it. A blow that missed or was dodged carries no
 * damage and no armor reduction, for no armor was reached.
 */
data class CombatResolution(
    val attacker: String,
    val defender: String,
    val finesse: Int,
    val melee: Int,
    val weapon: String,
    val category: WeaponCategory,
    val proficiency: Int,
    val mastery: Int,
    val hitChance: Int,
    val hitRoll: Int,
    val outcome: StrikeOutcome,
    val defenderSwiftness: Int,
    val armorTemper: String,
    val armorMaterial: String,
    val armorDodgePenalty: Int,
    val dodgeChance: Int,
    val dodgeRoll: Int,
    val damage: Int,
    val armorReduction: Int,
    /** When a raised shield took the blow: the board's name, and where it landed. */
    val shieldLine: String = "",
    val blockZone: String = "",
    /** Ranged resolutions only: the struck arrow or ball's arrival speed, in m/s. */
    val impactSpeedMps: Float = 0f,
    /** Ranged resolutions only: the projectile's mass, in kg. */
    val projectileMassKg: Float = 0f,
    /** Ranged resolutions only: the shooter's marksmanship skill at the moment of the shot. */
    val marksmanship: Int = 0,
    /** Ranged resolutions only: how far the shot flew before striking, in world units. */
    val flightDistance: Float = 0f
)
