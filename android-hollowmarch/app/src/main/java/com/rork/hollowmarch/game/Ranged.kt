package com.rork.hollowmarch.game

import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The five families of the marksmanship craft: the bow and its bent limbs, the
 * wound crossbow, the powder arms, the whirled sling, and the thrown steel.
 * Every ranged arm belongs to exactly one; its numbers live in a [RangedForm].
 */
enum class RangedKind(val label: String) {
    BOW("bow"),
    CROSSBOW("crossbow"),
    FIREARM("firearm"),
    HAND_CANNON("hand cannon"),
    SLING("sling"),
    THROWN("thrown arm")
}

/**
 * What flies when the string is cut or the powder speaks: its own mass, its own
 * harm, how much the air takes from it, and whether the ground gives it back.
 */
enum class AmmoKind(
    val label: String,
    val archetype: ItemArchetype,
    val damageType: DamageType,
    /** Kilograms, before the material's temper is worked in. */
    val baseMassKg: Float,
    /** How much of its speed the air takes each second, as a fraction. */
    val drag: Float,
    /** True when what lands can be picked back up. Fired shot is spent for good. */
    val recoverable: Boolean,
    /** True for arrows and bolts, which carry a worked head of some other metal. */
    val hasHead: Boolean = false
) {
    ARROW("arrow", ItemArchetype.ARROW, DamageType.PIERCE, 0.028f, 0.05f, true, hasHead = true),
    BOLT("bolt", ItemArchetype.BOLT, DamageType.PIERCE, 0.042f, 0.06f, true, hasHead = true),
    STONE_SHOT("sling stone", ItemArchetype.SLING_STONE, DamageType.BLUNT, 0.055f, 0.02f, true),
    LEAD_SHOT("lead glande", ItemArchetype.SLING_LEAD_SHOT, DamageType.BLUNT, 0.048f, 0.02f, true),
    CLAY_SHOT("clay bullet", ItemArchetype.SLING_CLAY_SHOT, DamageType.BLUNT, 0.030f, 0.03f, false),
    FIREARM_BALL("lead ball", ItemArchetype.LEAD_BALL, DamageType.PIERCE, 0.016f, 0.008f, false),
    IRON_SHOT("iron shot", ItemArchetype.IRON_BALL, DamageType.BLUNT, 0.055f, 0.008f, false),
    STONE_BALL("stone ball", ItemArchetype.STONE_BALL, DamageType.BLUNT, 0.130f, 0.01f, false)
}

/**
 * The arms-wright's own measure of one piece: the pull of the limbs, the length
 * of the draw, the charge of powder, the barrels a piece carries. Only the
 * fields its kind needs are filled; the rest stand empty.
 */
enum class RangedForm(
    val label: String,
    val kind: RangedKind,
    val drawWeightN: Float? = null,
    val drawLengthM: Float? = null,
    val drawSeconds: Float? = null,
    val efficiency: Float = 0.75f,
    val powerStrokeM: Float? = null,
    val cockSeconds: Float? = null,
    val powderGrams: Float? = null,
    val reloadSeconds: Float? = null,
    val releaseFactor: Float? = null,
    val whirlSeconds: Float? = null,
    val barrels: Int = 1
) {
    // The bows: draw weight in newtons, draw in metres, the string's cycle in seconds.
    REED_BOW("reed bow", RangedKind.BOW, drawWeightN = 35f, drawLengthM = 0.50f, drawSeconds = 0.8f, efficiency = 0.65f),
    SHORTBOW("shortbow", RangedKind.BOW, drawWeightN = 55f, drawLengthM = 0.55f, drawSeconds = 0.9f, efficiency = 0.70f),
    HUNTING_BOW("hunting bow", RangedKind.BOW, drawWeightN = 75f, drawLengthM = 0.60f, drawSeconds = 1.0f, efficiency = 0.72f),
    LONGBOW("longbow", RangedKind.BOW, drawWeightN = 110f, drawLengthM = 0.70f, drawSeconds = 1.2f, efficiency = 0.75f),
    WARBOW("warbow", RangedKind.BOW, drawWeightN = 130f, drawLengthM = 0.71f, drawSeconds = 1.35f, efficiency = 0.78f),
    HORN_RECURVE("horn recurve", RangedKind.BOW, drawWeightN = 90f, drawLengthM = 0.62f, drawSeconds = 1.0f, efficiency = 0.80f),

    // The crossbows: steel bent by lever or crank, cocked slow, loosed flat.
    LIGHT_CROSSBOW("latch crossbow", RangedKind.CROSSBOW, drawWeightN = 160f, powerStrokeM = 0.18f, cockSeconds = 2.4f, efficiency = 0.75f),
    HUNTING_CROSSBOW("hunting crossbow", RangedKind.CROSSBOW, drawWeightN = 250f, powerStrokeM = 0.18f, cockSeconds = 3.2f, efficiency = 0.78f),
    ARBALEST("arbalet", RangedKind.CROSSBOW, drawWeightN = 500f, powerStrokeM = 0.22f, cockSeconds = 5.5f, efficiency = 0.80f),

    // The powder arms: a charge of corned powder behind a lead ball.
    PETRONEL("petronel", RangedKind.FIREARM, powderGrams = 2.5f, reloadSeconds = 8f),
    ARQUEBUS("arquebus", RangedKind.FIREARM, powderGrams = 3.5f, reloadSeconds = 10f),
    CALIVER("caliver", RangedKind.FIREARM, powderGrams = 3f, reloadSeconds = 9f),
    HACKBUT("hackbut", RangedKind.FIREARM, powderGrams = 4.5f, reloadSeconds = 12f),

    // The hand cannons: a barrel on a stock, touched off with a slow match.
    HAND_CANNON("hand cannon", RangedKind.HAND_CANNON, powderGrams = 6f, reloadSeconds = 14f),
    SAN_YAN_CHONG("three-eye hand cannon", RangedKind.HAND_CANNON, powderGrams = 4f, reloadSeconds = 11f, barrels = 3),

    // The slings: a cord, a pouch, and the long whirl.
    HERDSMAN_SLING("herdsman's sling", RangedKind.SLING, releaseFactor = 1f, whirlSeconds = 0.9f),
    BALEARIC_SLING("Balearic sling", RangedKind.SLING, releaseFactor = 1.2f, whirlSeconds = 1.1f),
    STAFF_SLING("staff sling", RangedKind.SLING, releaseFactor = 1.35f, whirlSeconds = 1.3f),

    // The thrown arms: no cord, no barrel, only the hand.
    HAND_THROW("bare hand", RangedKind.THROWN)
}

/**
 * One shot in the air: a real thing with mass, position in three dimensions,
 * honest velocity, and a life of its own until wall, ground, or body stops it.
 * Transient by nature — a shot never rides the save.
 */
class Projectile(
    val id: Int,
    /** True when the delver's hand sent it; false when a creature of the province did. */
    val fromPlayer: Boolean,
    val shooterName: String,
    /** The arm that sent it, by name — for the log and the ledger. */
    val weaponName: String,
    /** The arm's family, and the hand's measure of it, carried for the ledger. */
    val category: WeaponCategory,
    val proficiency: Int,
    val mastery: Int,
    val marks: Int,
    /** The cone of doubt the shot was loosed inside, in degrees. */
    val spreadDegrees: Float,
    /** The very piece in the air: an arrow, a bolt, a thrown knife. */
    val ammoItem: Item,
    val label: String,
    val damageType: DamageType,
    val recoverable: Boolean,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    val massKg: Float,
    val drag: Float,
    val quality: Quality,
    val headMaterial: Material?,
    val spriteId: Int,
    var age: Float = 0f,
    var distance: Float = 0f
) {
    /** The speed it flies at right now, in metres per second. */
    val speedMps: Float
        get() = sqrt(vx * vx + vy * vy + vz * vz) / Ranged.MPS_TO_UPS
}

/**
 * The physics and the ledgers of ranged arms: speeds from draw weight and
 * powder, mass from shape and temper, harm from momentum, and the spread a
 * hand's skill allows. One world-unit is 1.8 metres; gravity is the honest
 * 9.81 m/s^2 read in those units.
 */
object Ranged {

    /** Gravity, read in world-units per second squared (1 unit = 1.8 m). */
    const val GRAVITY_UPS = 5.45f

    /** Metres per second to world-units per second. */
    const val MPS_TO_UPS = 1f / 1.8f

    /** Powder's worked energy per gram, times the arm's efficiency. */
    private const val POWDER_JOULES_PER_GRAM = 2600f
    private const val POWDER_EFFICIENCY = 0.40f

    /** No arm of the province drives a shot past this, whatever the arithmetic says. */
    private const val SPEED_CAP_MPS = 280f
    private const val MAX_THROW_SPEED_MPS = 20f
    private const val SLING_BASE_MPS = 34f

    /** Impact harm per unit of momentum (kg·m/s), before temper and make. */
    private const val HARM_PER_MOMENTUM = 2.6f

    /** The family a shape belongs to, or null for arms that are no one's ranged craft. */
    fun kindOf(archetype: ItemArchetype): RangedKind? = when (archetype) {
        ItemArchetype.BOW -> RangedKind.BOW
        ItemArchetype.CROSSBOW -> RangedKind.CROSSBOW
        ItemArchetype.ARQUEBUS -> RangedKind.FIREARM
        ItemArchetype.HAND_CANNON, ItemArchetype.THREE_EYE_CANNON -> RangedKind.HAND_CANNON
        ItemArchetype.SLING -> RangedKind.SLING
        ItemArchetype.THROWING_KNIFE, ItemArchetype.FRANCISCA,
        ItemArchetype.CHAKRAM, ItemArchetype.SHURIKEN -> RangedKind.THROWN
        else -> null
    }

    fun isRanged(archetype: ItemArchetype): Boolean = kindOf(archetype) != null

    fun isAmmo(archetype: ItemArchetype): Boolean = archetype.slot == ItemSlot.AMMUNITION

    /** The family of shot a kind wants. */
    fun ammoOf(kind: RangedKind): List<AmmoKind> = when (kind) {
        RangedKind.BOW -> listOf(AmmoKind.ARROW)
        RangedKind.CROSSBOW -> listOf(AmmoKind.BOLT)
        RangedKind.SLING -> listOf(AmmoKind.LEAD_SHOT, AmmoKind.STONE_SHOT, AmmoKind.CLAY_SHOT)
        RangedKind.FIREARM -> listOf(AmmoKind.FIREARM_BALL)
        RangedKind.HAND_CANNON -> listOf(AmmoKind.IRON_SHOT, AmmoKind.STONE_BALL)
        RangedKind.THROWN -> emptyList()
    }

    /** True when this shot can be fed to this arm. */
    fun accepts(weapon: Item, ammo: Item): Boolean {
        val kind = kindOf(weapon.archetype) ?: return false
        val ammoKind = ammoKindOf(ammo) ?: return false
        return ammoKind in ammoOf(kind)
    }

    /** The shot-family of a bundle, or null when it feeds no arm. */
    fun ammoKindOf(ammo: Item): AmmoKind? =
        AmmoKind.entries.firstOrNull { it.archetype == ammo.archetype }

    /** The form of a piece: its own rolled form, or the shape its kind takes by default. */
    fun formOf(item: Item): RangedForm = item.rangedForm ?: defaultForm(item.archetype)

    /** The plain form an archetype takes when no arms-wright has rolled it. */
    fun defaultForm(archetype: ItemArchetype): RangedForm = when (archetype) {
        ItemArchetype.BOW -> RangedForm.SHORTBOW
        ItemArchetype.CROSSBOW -> RangedForm.HUNTING_CROSSBOW
        ItemArchetype.ARQUEBUS -> RangedForm.ARQUEBUS
        ItemArchetype.HAND_CANNON -> RangedForm.HAND_CANNON
        ItemArchetype.THREE_EYE_CANNON -> RangedForm.SAN_YAN_CHONG
        ItemArchetype.SLING -> RangedForm.HERDSMAN_SLING
        else -> RangedForm.HAND_THROW
    }

    /** The thrown steel's own measure: its mass in kilograms and the speed of the arm. */
    data class ThrowSpec(val massKg: Float, val speedMps: Float)

    private val THROW_SPECS = mapOf(
        ItemArchetype.THROWING_KNIFE to ThrowSpec(0.18f, 14f),
        ItemArchetype.FRANCISCA to ThrowSpec(0.35f, 12f),
        ItemArchetype.CHAKRAM to ThrowSpec(0.30f, 16f),
        ItemArchetype.SHURIKEN to ThrowSpec(0.08f, 18f)
    )

    fun throwSpecOf(archetype: ItemArchetype): ThrowSpec? = THROW_SPECS[archetype]

    /** Mass of one piece of shot or one arrow, worked from shape and temper. */
    fun ammoMassKg(ammo: Item): Float {
        val kind = ammoKindOf(ammo) ?: return 0.03f
        val base = kind.baseMassKg * ammo.material.weightFactor()
        if (!kind.hasHead) return base
        val head = (ammo.headMaterial ?: Material.IRON).weightFactor()
        return base + (if (kind == AmmoKind.ARROW) 0.012f else 0.018f) * head
    }

    /**
     * The speed a shot leaves the arm at, in metres per second: the bow from
     * its stored energy and the shot's mass, the powder arm from its charge,
     * the sling from its whirl, the thrown arm from the hand alone. [marks]
     * puts a little more of the archer into the string.
     */
    fun launchSpeedMps(
        weapon: Item,
        ammoMassKg: Float,
        drawFraction: Float,
        marks: Int
    ): Float {
        val form = formOf(weapon)
        val skillFactor = 1f + (marks - 10) * 0.004f
        val mass = ammoMassKg.coerceAtLeast(0.005f)
        val speed = when (form.kind) {
            RangedKind.BOW -> {
                val energy = (form.drawWeightN ?: 55f) * (form.drawLengthM ?: 0.55f) *
                    form.efficiency * drawFraction.coerceIn(0.25f, 1f)
                sqrt(2f * energy / mass)
            }
            RangedKind.CROSSBOW -> {
                val energy = (form.drawWeightN ?: 200f) * (form.powerStrokeM ?: 0.18f) * form.efficiency
                sqrt(2f * energy / mass)
            }
            RangedKind.FIREARM, RangedKind.HAND_CANNON -> {
                val energy = (form.powderGrams ?: 3f) * POWDER_JOULES_PER_GRAM * POWDER_EFFICIENCY
                sqrt(2f * energy / mass)
            }
            RangedKind.SLING -> {
                val cast = SLING_BASE_MPS * (form.releaseFactor ?: 1f)
                cast * (0.8f + 0.2f * drawFraction.coerceIn(0f, 1f))
            }
            RangedKind.THROWN -> {
                val spec = throwSpecOf(weapon.archetype) ?: ThrowSpec(0.2f, 14f)
                spec.speedMps
            }
        }
        // The hand's own measure is worked in once, and no arm outruns its cap.
        val cap = if (form.kind == RangedKind.THROWN) MAX_THROW_SPEED_MPS else SPEED_CAP_MPS
        return (speed * skillFactor).coerceAtMost(cap)
    }

    /**
     * How far off the mark this arm may send a shot, in degrees: the weapon's
     * own wildness, eased by Marksmanship, the family, and the piece known,
     * worsened by a hasty loose and by loosing on the move.
     */
    fun spreadDegrees(
        weapon: Item,
        drawFraction: Float,
        marks: Int,
        proficiency: Int,
        mastery: Int,
        moving: Boolean
    ): Float {
        val kind = formOf(weapon).kind
        val base = when (kind) {
            RangedKind.BOW -> 1.4f
            RangedKind.CROSSBOW -> 0.9f
            RangedKind.FIREARM, RangedKind.HAND_CANNON -> 2.8f
            RangedKind.SLING -> 2.4f
            RangedKind.THROWN -> 2.0f
        }
        val learned = ((marks - 1) * 0.025f + proficiency * 0.012f + mastery * 0.008f)
            .coerceIn(0f, 0.7f)
        val haste = if (kind == RangedKind.BOW || kind == RangedKind.SLING) {
            1f + (1f - drawFraction.coerceIn(0f, 1f)) * 1.4f
        } else 1f
        val stride = if (moving) 1.8f else 1f
        return base * (1f - learned) * haste * stride
    }

    /** The harm a shot does when it lands, from its momentum, temper and make. */
    fun impactDamage(
        massKg: Float,
        speedMps: Float,
        kind: AmmoKind,
        headMaterial: Material?,
        quality: Quality
    ): Int {
        val momentum = massKg.coerceAtLeast(0.005f) * speedMps.coerceAtLeast(0f)
        val temper = (headMaterial ?: Material.IRON).damageMult(kind.damageType)
        return (momentum * HARM_PER_MOMENTUM * temper * quality.damageMult)
            .roundToInt().coerceAtLeast(1)
    }

    /** The thrown arm's harm, from the same arithmetic. */
    fun impactDamageThrown(archetype: ItemArchetype, speedMps: Float, item: Item): Int {
        val spec = throwSpecOf(archetype) ?: return 1
        val momentum = spec.massKg * speedMps
        val temper = item.material.damageMult(archetype.damageType)
        return (momentum * HARM_PER_MOMENTUM * temper * item.quality.damageMult)
            .roundToInt().coerceAtLeast(1)
    }

    /** Seconds the arm asks before it is ready: draw, whirl, cock, or the charge. */
    fun cycleSeconds(weapon: Item, phase: RangedPhaseKind): Float {
        val form = formOf(weapon)
        return when (phase) {
            RangedPhaseKind.DRAW -> when (form.kind) {
                RangedKind.BOW -> form.drawSeconds ?: 1f
                RangedKind.SLING -> form.whirlSeconds ?: 1f
                else -> 0.4f
            }
            RangedPhaseKind.RELOAD -> when (form.kind) {
                RangedKind.CROSSBOW -> form.cockSeconds ?: 3f
                RangedKind.FIREARM, RangedKind.HAND_CANNON ->
                    (form.reloadSeconds ?: 10f) * (1f + (form.barrels - 1) * 0.5f)
                else -> 1f
            }
            RangedPhaseKind.IDLE -> 0f
        }
    }

    /** Seconds before the arm may be asked again after a loose. */
    fun recoverySeconds(weapon: Item): Float = when (formOf(weapon).kind) {
        RangedKind.BOW -> 0.5f
        RangedKind.CROSSBOW -> 0.8f
        RangedKind.FIREARM -> 1.2f
        RangedKind.HAND_CANNON -> 1.6f
        RangedKind.SLING -> 0.6f
        RangedKind.THROWN -> 0.9f
    }

    /** True for the powder arms, whose flash and thunder cost silence. */
    fun isGunpowder(weapon: Item): Boolean {
        val kind = formOf(weapon).kind
        return kind == RangedKind.FIREARM || kind == RangedKind.HAND_CANNON
    }

    /** How many chambers or barrels the piece holds ready for a fresh charge. */
    fun capacity(weapon: Item): Int = when (formOf(weapon).kind) {
        RangedKind.FIREARM, RangedKind.HAND_CANNON -> formOf(weapon).barrels
        RangedKind.CROSSBOW -> 1
        else -> 0
    }

    /** The arms-wright's roll: which form this shape of weapon takes. */
    fun rollForm(rng: Random, archetype: ItemArchetype): RangedForm {
        val pool = when (archetype) {
            ItemArchetype.BOW -> listOf(
                RangedForm.REED_BOW, RangedForm.SHORTBOW, RangedForm.HUNTING_BOW,
                RangedForm.LONGBOW, RangedForm.WARBOW, RangedForm.HORN_RECURVE
            )
            ItemArchetype.CROSSBOW -> listOf(
                RangedForm.LIGHT_CROSSBOW, RangedForm.HUNTING_CROSSBOW, RangedForm.ARBALEST
            )
            ItemArchetype.ARQUEBUS -> listOf(
                RangedForm.PETRONEL, RangedForm.ARQUEBUS, RangedForm.CALIVER, RangedForm.HACKBUT
            )
            ItemArchetype.HAND_CANNON -> listOf(RangedForm.HAND_CANNON)
            ItemArchetype.THREE_EYE_CANNON -> listOf(RangedForm.SAN_YAN_CHONG)
            ItemArchetype.SLING -> listOf(
                RangedForm.HERDSMAN_SLING, RangedForm.BALEARIC_SLING, RangedForm.STAFF_SLING
            )
            else -> listOf(RangedForm.HAND_THROW)
        }
        return pool[rng.nextInt(pool.size)]
    }

    /** The metals a region's fletchers reach for when they head a shaft. */
    private val HEAD_MATERIALS = listOf(
        Material.BONE, Material.BOG_IRON, Material.COPPER, Material.IRON,
        Material.VERDIGRIS, Material.IRON, Material.STEEL
    )

    /** Roll a bundle of [kind] shot: material by its kind, a headed shaft where it takes one. */
    fun rollAmmo(rng: Random, kind: AmmoKind, cultureId: Int, count: Int): Item {
        val material = when (kind) {
            AmmoKind.ARROW, AmmoKind.BOLT, AmmoKind.CLAY_SHOT -> Material.ASHWOOD
            AmmoKind.LEAD_SHOT, AmmoKind.FIREARM_BALL -> Material.LEAD
            AmmoKind.STONE_SHOT, AmmoKind.STONE_BALL -> Material.GRAVE_SLATE
            AmmoKind.IRON_SHOT -> Material.IRON
        }
        val head = if (kind.hasHead) HEAD_MATERIALS[rng.nextInt(HEAD_MATERIALS.size)] else null
        return Item(
            archetype = kind.archetype,
            material = material,
            quality = Quality.roll(rng),
            cultureId = cultureId,
            count = count.coerceAtLeast(1),
            headMaterial = head
        )
    }

    /** One line of arms-lore: what the piece asks of the hand that holds it. */
    fun summary(weapon: Item): String {
        val form = formOf(weapon)
        return when (form.kind) {
            RangedKind.BOW ->
                "draw ${form.drawWeightN?.roundToInt()} N, ${form.drawLengthM} m, ${form.drawSeconds}s to full draw"
            RangedKind.CROSSBOW ->
                "${form.drawWeightN?.roundToInt()} N steel, ${form.cockSeconds}s to cock"
            RangedKind.FIREARM, RangedKind.HAND_CANNON -> {
                val barrels = if (form.barrels > 1) ", ${form.barrels} barrels" else ""
                "${form.powderGrams} g charge, ${form.reloadSeconds}s to reload$barrels"
            }
            RangedKind.SLING ->
                "${form.releaseFactor}x cast, ${form.whirlSeconds}s whirl"
            RangedKind.THROWN ->
                throwSpecOf(weapon.archetype)
                    ?.let { "${(it.massKg * 1000).roundToInt()} g steel, thrown" }
                    ?: "thrown"
        }
    }

    /** The sprite a shot in flight draws as. */
    fun spriteFor(kind: AmmoKind): Int = when (kind) {
        AmmoKind.ARROW -> Sprites.P_ARROW
        AmmoKind.BOLT -> Sprites.P_BOLT
        AmmoKind.FIREARM_BALL, AmmoKind.IRON_SHOT, AmmoKind.STONE_BALL -> Sprites.P_BALL
        else -> Sprites.P_SHOT
    }
}

/** What the ranged hand is doing between the tap and the loose. */
enum class RangedPhaseKind { IDLE, DRAW, RELOAD }
