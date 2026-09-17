package com.rork.hollowmarch.game

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The eleven shield forms of the province, each with its own base geometry:
 * the dims a plank of it starts from, the cone of the body it can cover, and
 * how quick it is in the hand. Grip and construction and reinforcement modify
 * these; the shape never stops being itself.
 */
enum class ShieldType(
    val label: String,
    /** Base width in meters. */
    val widthM: Float,
    /** Base height in meters. */
    val heightM: Float,
    /** Base thickness in meters. */
    val thicknessM: Float,
    /** How much of the plank's face is actually board: circles fill less than rectangles. */
    val fill: Float,
    /** Half the cone a raised shield covers, in degrees off dead ahead. */
    val coverageHalfAngle: Float,
    /** Maneuverability, 0..1: how quickly the shape answers the hand. */
    val maneuver: Float
) {
    BUCKLER("Buckler", 0.28f, 0.30f, 0.012f, 0.785f, 24f, 0.95f),
    ROUND("Round Shield", 0.70f, 0.70f, 0.012f, 0.785f, 40f, 0.75f),
    TARGE("Targe", 0.45f, 0.50f, 0.014f, 0.80f, 32f, 0.85f),
    HEATER("Heater Shield", 0.60f, 0.80f, 0.010f, 0.72f, 46f, 0.70f),
    KITE("Kite Shield", 0.50f, 1.10f, 0.010f, 0.66f, 52f, 0.58f),
    ALMOND("Almond Shield", 0.45f, 1.00f, 0.010f, 0.60f, 48f, 0.62f),
    HUNGARIAN("Hungarian Shield", 0.38f, 1.05f, 0.010f, 0.58f, 44f, 0.60f),
    PAVISE("Pavise", 0.90f, 1.30f, 0.022f, 0.95f, 62f, 0.25f),
    RONDACHE("Rondache", 0.80f, 0.80f, 0.012f, 0.785f, 50f, 0.60f),
    ROTELLA("Rotella", 0.65f, 0.65f, 0.014f, 0.785f, 48f, 0.65f),
    TOWER("Tower Shield", 0.75f, 1.40f, 0.018f, 0.92f, 68f, 0.20f)
}

/**
 * How a shield was built: what its plank is made of and how that changes its
 * weight, thickness, rigidity and the way it meets each harm. Two kites of one
 * wood part at the make.
 */
enum class ShieldConstruction(
    val label: String,
    /** The word for the make in a shield's name, when one is worth saying. */
    val descriptor: String?,
    val massMult: Float,
    val thicknessMult: Float,
    /** Rigidity 0..1: how little the board gives when a blow lands. */
    val rigidity: Float,
    val blockBonus: Float,
    /** The materials this build plausibly wants: woods and bone for boards, metals for plate. */
    val materials: List<Material>
) {
    SOLID_WOOD("solid wood", null, 1.00f, 1.00f, 0.80f, 0.00f,
        listOf(Material.ASHWOOD, Material.ASHWOOD, Material.BONE)),
    LAYERED_WOOD("layered wood", "Layered", 0.95f, 0.90f, 0.90f, 0.02f,
        listOf(Material.ASHWOOD, Material.ASHWOOD)),
    LAMINATED_WOOD("laminated wood", "Laminated", 1.00f, 0.85f, 1.00f, 0.04f,
        listOf(Material.ASHWOOD)),
    WICKER("wicker", "Wicker", 0.75f, 1.10f, 0.50f, -0.04f,
        listOf(Material.ASHWOOD)),
    LEATHER("hardened leather", null, 0.55f, 0.70f, 0.40f, -0.06f,
        listOf(Material.ASHWOOD, Material.BONE)),
    HIDE("raw hide", null, 0.60f, 0.80f, 0.45f, -0.05f,
        listOf(Material.ASHWOOD, Material.BONE)),
    METAL_PLATE("metal plate", "Plated", 1.90f, 0.80f, 1.00f, 0.10f,
        listOf(Material.IRON, Material.BOG_IRON, Material.STEEL, Material.VERDIGRIS, Material.BRASS)),
    METAL_FRAME("metal frame", null, 1.30f, 1.00f, 0.95f, 0.05f,
        listOf(Material.IRON, Material.BOG_IRON, Material.STEEL, Material.VERDIGRIS)),
    COMPOSITE("composite", null, 0.90f, 0.90f, 0.85f, 0.03f,
        listOf(Material.ASHWOOD, Material.BONE, Material.IRON)),
    WOOD_LEATHER("wood core, leather-covered", "Leather-covered", 0.90f, 1.05f, 0.85f, 0.03f,
        listOf(Material.ASHWOOD, Material.ASHWOOD, Material.ASHWOOD, Material.BONE)),
    WOOD_HIDE("wood core, hide-covered", "Hide-covered", 0.95f, 1.10f, 0.80f, 0.02f,
        listOf(Material.ASHWOOD, Material.ASHWOOD, Material.BONE));
}

/**
 * The iron the smith added to the board: every piece its own weight, its own
 * stiffening, and its own say in where a blow ends. None of it is free — the
 * mass rides the arm.
 */
enum class ShieldReinforcement(
    val label: String,
    /** Added mass in kilograms at a mid-sized board; scaled to the shield's face. */
    val addedMass: Float,
    val rigidity: Float,
    val blockBonus: Float,
    /** Degrees the covered cone widens, for rims and edges that reach. */
    val coverageBonus: Float
) {
    NONE("none", 0f, 0f, 0f, 0f),
    BOSS("iron boss", 0.7f, 0.10f, 0.03f, 0f),
    METAL_RIM("metal rim", 1.2f, 0.15f, 0.05f, 2f),
    METAL_EDGE("metal edge", 0.8f, 0.10f, 0.04f, 1f),
    METAL_BANDS("metal bands", 1.6f, 0.20f, 0.06f, 2f),
    RADIAL_BANDS("radial bands", 1.8f, 0.25f, 0.07f, 1f),
    REINFORCED_CENTER("reinforced center", 1.0f, 0.15f, 0.05f, 0f),
    REINFORCED_EDGE("reinforced edge", 0.9f, 0.12f, 0.05f, 2f),
    REINFORCED_POINT("reinforced point", 0.6f, 0.10f, 0.03f, 0f),
    REINFORCED_SPINE("reinforced spine", 0.9f, 0.20f, 0.05f, 0f),
    METAL_STUDS("metal studs", 0.8f, 0.08f, 0.04f, 1f),
    GROUND_SPIKE("ground spike", 1.1f, 0.10f, 0.02f, 0f),
    GROUND_STAND("ground stand", 1.4f, 0.10f, 0.02f, 0f);
}

/**
 * How the shield rides: a fist through a central grip answers the hand; arm
 * straps hold a board steady against the shoulder; a neck strap carries what
 * the arm could not, and lets both hands keep their work.
 */
enum class ShieldGrip(
    val label: String,
    val handlingMult: Float,
    val moveMult: Float,
    /** Stability against impact: strapped boards take a maul better than a fist-grip. */
    val stabilityMult: Float
) {
    CENTRAL("central grip", 1.15f, 1.05f, 0.90f),
    STRAPPED("arm straps", 0.90f, 0.95f, 1.10f),
    GUIGE("neck strap", 0.75f, 0.90f, 1.20f);
}

/** A shield's measured body: the three dimensions coverage and mass are built from. */
data class ShieldDimensions(
    val width: Float,
    val height: Float,
    val thickness: Float
) {
    fun volume(fill: Float): Float = width * height * fill * thickness
    fun faceArea(fill: Float): Float = width * height * fill
}

/**
 * Where an intercepted blow landed on the board: the middle takes the whole
 * weight, the edge turns less of it, and a blow outside the cone never
 * touched the shield at all.
 */
enum class BlockZone { CENTER, EDGE, MISS }

/**
 * The shieldwright's whole craft in one place: rolling plausible shields from
 * the region's materials, measuring them, and answering the combat questions —
 * what does a blow against this board weigh, and how much of it gets through.
 * Pure arithmetic throughout, so the ledger can be verified.
 */
object Shields {

    /** Reference density of a wooden board, in kilograms per cubic meter. */
    private const val WOOD_DENSITY = 700f

    /** The strongest any block may be; nothing turns a blow wholly aside forever. */
    private const val MAX_EFFECT = 0.92f

    fun isShield(archetype: ItemArchetype): Boolean = archetype.slot == ItemSlot.SHIELD
    fun isShield(item: Item): Boolean = isShield(item.archetype)

    /** The shield a body bears in its left hand, if any. */
    fun worn(equipment: Equipment): Item? =
        equipment.worn(WearSlot.LEFT_HAND)?.takeIf { isShield(it) }

    /** The shield form of an archetype, or null for everything that is no board at all. */
    fun typeOf(archetype: ItemArchetype): ShieldType? = when (archetype) {
        ItemArchetype.BUCKLER -> ShieldType.BUCKLER
        ItemArchetype.ROUND_SHIELD -> ShieldType.ROUND
        ItemArchetype.TARGE -> ShieldType.TARGE
        ItemArchetype.HEATER_SHIELD -> ShieldType.HEATER
        ItemArchetype.KITE_SHIELD -> ShieldType.KITE
        ItemArchetype.ALMOND_SHIELD -> ShieldType.ALMOND
        ItemArchetype.HUNGARIAN_SHIELD -> ShieldType.HUNGARIAN
        ItemArchetype.PAVISE -> ShieldType.PAVISE
        ItemArchetype.RONDACHE -> ShieldType.RONDACHE
        ItemArchetype.ROTELLA -> ShieldType.ROTELLA
        ItemArchetype.TOWER_SHIELD -> ShieldType.TOWER
        else -> null
    }

    // ------------------------------------------------------------- measuring

    /** The board's measured dimensions, as the wright cut it. */
    fun dimensionsOf(item: Item): ShieldDimensions {
        item.dimensions?.let { return it }
        val type = typeOf(item.archetype)
        return if (type != null) {
            ShieldDimensions(type.widthM, type.heightM, type.thicknessM)
        } else {
            ShieldDimensions(0.6f, 0.7f, 0.01f)
        }
    }

    /**
     * What the shield weighs: the material's density worked through the board's
     * own volume — face times thickness, by the shape's fill — plus the mass of
     * every band and boss riveted on, scaled to the face it must cover.
     */
    fun massOf(item: Item): Float {
        val type = typeOf(item.archetype) ?: return item.archetype.baseWeight
        val construction = item.construction ?: ShieldConstruction.SOLID_WOOD
        val dims = dimensionsOf(item)
        // The plank was cut to [ShieldDimensions] at the make's thickness already;
        // weighing it measures the cut board, it does not cut it again.
        val boardVolume = dims.volume(type.fill)
        val density = WOOD_DENSITY * item.material.weightFactor()
        val boardMass = boardVolume * density * construction.massMult
        val face = dims.faceArea(type.fill)
        val referenceFace = type.widthM * type.heightM * type.fill
        val reinforcementMass =
            (item.reinforcement ?: ShieldReinforcement.NONE).addedMass * (face / referenceFace)
        return boardMass + reinforcementMass
    }

    /** How big a cone the raised shield covers: the shape's reach, widened by reaching rims. */
    fun coverageHalfAngle(item: Item): Float {
        val type = typeOf(item.archetype) ?: return 40f
        val dims = dimensionsOf(item)
        val face = dims.faceArea(type.fill)
        val referenceFace = type.widthM * type.heightM * type.fill
        val sizeReach = ((face / referenceFace) - 1f) * 8f
        val reinforcement = (item.reinforcement ?: ShieldReinforcement.NONE).coverageBonus
        return (type.coverageHalfAngle + sizeReach + reinforcement).coerceIn(14f, 80f)
    }

    /**
     * How the shield answers the hand, 0..1: the shape's own quickness, times
     * the grip's, dragged down by everything the arm must swing.
     */
    fun handling(item: Item): Float {
        val type = typeOf(item.archetype) ?: return 0.7f
        val grip = item.grip ?: ShieldGrip.CENTRAL
        val mass = massOf(item)
        val massDrag = 1f - (mass / (mass + 6f)) * 0.55f
        return (type.maneuver * grip.handlingMult * massDrag).coerceIn(0.08f, 1f)
    }

    /**
     * What the stride keeps while the shield is raised: a buckler barely slows
     * the walk, a pavise makes it a shuffle. The shield's weight also rides the
     * load in the ordinary encumbrance, so a great board taxes the back even
     * at rest — that is [Item.weight]'s work, not this.
     */
    fun moveFactor(item: Item): Float {
        val grip = item.grip ?: ShieldGrip.CENTRAL
        val factor = handling(item) * grip.moveMult
        return (0.45f + factor * 0.5f).coerceIn(0.4f, 0.95f)
    }

    /** What a raised shield does to the turn: heavy boards swing the body slow. */
    fun turnFactor(item: Item): Float =
        (0.55f + handling(item) * 0.45f).coerceIn(0.5f, 1f)

    /** What a raised shield adds to the swing that must come back over it. */
    fun recoveryFactor(item: Item): Float =
        1f + (1f - handling(item)) * 0.6f

    // ------------------------------------------------------------- geometry

    /**
     * The angle in degrees between a defender's facing and the direction the
     * attack came from: zero when the board stares down the blow, one-eighty
     * when it arrives from behind.
     */
    fun offAxisDegrees(facingX: Float, facingY: Float, toAttackerX: Float, toAttackerY: Float): Float {
        val toLen = kotlin.math.sqrt(toAttackerX * toAttackerX + toAttackerY * toAttackerY)
        if (toLen < 0.0001f) return 0f
        val fLen = kotlin.math.sqrt(facingX * facingX + facingY * facingY)
        if (fLen < 0.0001f) return 180f
        val dot = (facingX * toAttackerX + facingY * toAttackerY) / (fLen * toLen)
        return Math.toDegrees(kotlin.math.acos(dot.toDouble().coerceIn(-1.0, 1.0))).toFloat()
    }

    /** Where the blow landed on the board, from the angle it arrived at. */
    fun zone(offAxisDeg: Float, halfAngle: Float): BlockZone = when {
        offAxisDeg > halfAngle -> BlockZone.MISS
        offAxisDeg <= halfAngle * 0.55f -> BlockZone.CENTER
        else -> BlockZone.EDGE
    }

    // ------------------------------------------------------------- the blow

    /**
     * How much of an intercepted blow the board turns aside, 0..1: the make,
     * the metal, the angle it arrived at, where it landed, the defender's
     * trained Defense, and the force that came in behind it all have their say.
     */
    fun effectiveness(
        item: Item,
        harm: DamageType,
        offAxisDeg: Float,
        defenseSkill: Int,
        attackerMight: Int = 10
    ): Float {
        val construction = item.construction ?: ShieldConstruction.SOLID_WOOD
        val reinforcement = item.reinforcement ?: ShieldReinforcement.NONE
        val grip = item.grip ?: ShieldGrip.CENTRAL

        // The make's own say, before anything strikes it.
        var base = 0.52f + construction.blockBonus + reinforcement.blockBonus +
            (item.quality.armorMult - 1f) * 0.10f + defenseSkill * 0.006f

        // How the board's temper meets each harm. Metal turns the edge and the
        // point, gives under the maul; wood holds against the maul, keeps the
        // point in it; hide spreads the maul and feeds the point through.
        val faceMetal = construction == ShieldConstruction.METAL_PLATE ||
            construction == ShieldConstruction.METAL_FRAME
        val faceHide = construction == ShieldConstruction.LEATHER ||
            construction == ShieldConstruction.HIDE ||
            construction == ShieldConstruction.WOOD_HIDE ||
            construction == ShieldConstruction.WOOD_LEATHER
        base += when (harm) {
            DamageType.SHARP -> if (faceMetal) 0.08f else if (faceHide) -0.03f else 0.0f
            DamageType.PIERCE -> if (faceMetal) 0.10f else if (faceHide) -0.10f else -0.06f
            DamageType.BLUNT -> if (faceMetal) -0.10f else if (faceHide) 0.05f else 0.0f
        }

        // Thickness and rigidity stiffen the answer; mass holds a maul back.
        val dims = dimensionsOf(item)
        base += (dims.thickness - 0.010f) * 4f * construction.thicknessMult
        val mass = massOf(item)
        base += (construction.rigidity + reinforcement.rigidity) * 0.08f * grip.stabilityMult
        if (harm == DamageType.BLUNT) {
            base += (mass / (mass + 5f)) * 0.18f
            base -= (attackerMight - 10) * 0.008f
        }

        // The angle: dead ahead takes the weight, the edge takes less.
        val angleFactor = cos(Math.toRadians(offAxisDeg.toDouble())).toFloat().coerceIn(0f, 1f)
        base *= 0.55f + 0.45f * angleFactor

        return base.coerceIn(0f, MAX_EFFECT)
    }

    /** A full verdict on one intercepted blow: where it landed, and what got through. */
    fun resolve(
        item: Item,
        harm: DamageType,
        offAxisDeg: Float,
        defenseSkill: Int,
        attackerMight: Int = 10
    ): Pair<BlockZone, Float> {
        val halfAngle = coverageHalfAngle(item)
        val zone = zone(offAxisDeg, halfAngle)
        if (zone == BlockZone.MISS) return zone to 0f
        val zoneFactor = if (zone == BlockZone.CENTER) 1f else 0.55f
        return zone to (effectiveness(item, harm, offAxisDeg, defenseSkill, attackerMight) * zoneFactor)
    }

    // ------------------------------------------------------------- the wright

    /**
     * Roll a plausible shield: a shape its bearer could fight with, a build its
     * region could make, iron only where iron belongs, a grip its size can
     * carry. Deterministic under the given rng.
     */
    fun roll(rng: Random, level: Int, cultureId: Int, geography: MaterialGeography?): Item {
        // Common shapes early and often; the great boards are a specialist's kit.
        val type = when (rng.nextInt(20)) {
            0, 1, 2 -> ShieldType.BUCKLER
            3, 4, 5 -> ShieldType.ROUND
            6, 7 -> ShieldType.TARGE
            8, 9 -> ShieldType.HEATER
            10 -> ShieldType.KITE
            11 -> ShieldType.ALMOND
            12 -> ShieldType.HUNGARIAN
            13 -> if (level >= 4) ShieldType.PAVISE else ShieldType.ROUND
            14, 15 -> ShieldType.RONDACHE
            16 -> ShieldType.ROTELLA
            else -> if (level >= 5) ShieldType.TOWER else ShieldType.HEATER
        }

        val construction = when {
            type == ShieldType.BUCKLER ->
                listOf(
                    ShieldConstruction.METAL_PLATE, ShieldConstruction.METAL_PLATE,
                    ShieldConstruction.SOLID_WOOD, ShieldConstruction.LAMINATED_WOOD
                )[rng.nextInt(4)]
            type == ShieldType.PAVISE || type == ShieldType.TOWER ->
                listOf(
                    ShieldConstruction.SOLID_WOOD, ShieldConstruction.SOLID_WOOD,
                    ShieldConstruction.LAYERED_WOOD, ShieldConstruction.WOOD_LEATHER
                )[rng.nextInt(4)]
            type == ShieldType.TARGE ->
                listOf(
                    ShieldConstruction.WOOD_LEATHER, ShieldConstruction.WOOD_HIDE,
                    ShieldConstruction.SOLID_WOOD, ShieldConstruction.LEATHER
                )[rng.nextInt(4)]
            else -> ShieldConstruction.entries[rng.nextInt(ShieldConstruction.entries.size)]
        }

        val material = geography?.roll(rng, cultureId)
            ?.takeIf { it in construction.materials }
            ?: construction.materials[rng.nextInt(construction.materials.size)]

        val reinforcement = when {
            construction == ShieldConstruction.METAL_PLATE ||
                construction == ShieldConstruction.METAL_FRAME ->
                listOf(
                    ShieldReinforcement.NONE, ShieldReinforcement.METAL_RIM,
                    ShieldReinforcement.METAL_EDGE, ShieldReinforcement.REINFORCED_CENTER
                )[rng.nextInt(4)]
            type == ShieldType.PAVISE ->
                listOf(
                    ShieldReinforcement.NONE, ShieldReinforcement.NONE,
                    ShieldReinforcement.METAL_BANDS, ShieldReinforcement.GROUND_SPIKE,
                    ShieldReinforcement.GROUND_STAND
                )[rng.nextInt(5)]
            else -> ShieldReinforcement.entries[rng.nextInt(ShieldReinforcement.entries.size)]
        }

        val grip = when {
            type == ShieldType.BUCKLER -> ShieldGrip.CENTRAL
            type == ShieldType.PAVISE || type == ShieldType.TOWER ->
                if (rng.nextInt(3) == 0) ShieldGrip.STRAPPED else ShieldGrip.GUIGE
            rng.nextInt(3) == 0 -> ShieldGrip.CENTRAL
            else -> ShieldGrip.STRAPPED
        }

        return create(type, material, construction, reinforcement, grip, Quality.roll(rng), cultureId, rng)
    }

    /**
     * Cut a shield to its type's measure with a wright's small variation in the
     * plank, and mark it with the smith's own uid.
     */
    fun create(
        type: ShieldType,
        material: Material,
        construction: ShieldConstruction,
        reinforcement: ShieldReinforcement,
        grip: ShieldGrip,
        quality: Quality,
        cultureId: Int,
        rng: Random
    ): Item {
        val width = type.widthM * (0.92f + rng.nextFloat() * 0.16f)
        val height = type.heightM * (0.92f + rng.nextFloat() * 0.16f)
        val thickness = (type.thicknessM * construction.thicknessMult * (0.9f + rng.nextFloat() * 0.2f))
            .coerceAtLeast(0.004f)
        return Item(
            archetype = archetypeFor(type),
            material = material,
            quality = quality,
            cultureId = cultureId,
            construction = construction,
            reinforcement = reinforcement,
            grip = grip,
            dimensions = ShieldDimensions(width, height, thickness)
        ).marked()
    }

    /** The archetype each type is worked from — the same shapes the ledger knows. */
    fun archetypeFor(type: ShieldType): ItemArchetype = when (type) {
        ShieldType.BUCKLER -> ItemArchetype.BUCKLER
        ShieldType.ROUND -> ItemArchetype.ROUND_SHIELD
        ShieldType.TARGE -> ItemArchetype.TARGE
        ShieldType.HEATER -> ItemArchetype.HEATER_SHIELD
        ShieldType.KITE -> ItemArchetype.KITE_SHIELD
        ShieldType.ALMOND -> ItemArchetype.ALMOND_SHIELD
        ShieldType.HUNGARIAN -> ItemArchetype.HUNGARIAN_SHIELD
        ShieldType.PAVISE -> ItemArchetype.PAVISE
        ShieldType.RONDACHE -> ItemArchetype.RONDACHE
        ShieldType.ROTELLA -> ItemArchetype.ROTELLA
        ShieldType.TOWER -> ItemArchetype.TOWER_SHIELD
    }

    /** The guard line a satchel or a chronicle might read: make, metal, measure. */
    fun summary(item: Item): String {
        val construction = item.construction ?: ShieldConstruction.SOLID_WOOD
        val reinforcement = item.reinforcement ?: ShieldReinforcement.NONE
        val grip = item.grip ?: ShieldGrip.CENTRAL
        val dims = dimensionsOf(item)
        val parts = mutableListOf(construction.label)
        if (reinforcement != ShieldReinforcement.NONE) parts += reinforcement.label
        parts += grip.label
        parts += "${(dims.width * 100).roundToInt()}×${(dims.height * 100).roundToInt()} cm"
        parts += "${coverageHalfAngle(item).roundToInt()}° guard"
        parts += "${(handling(item) * 100).roundToInt()}% handling"
        return parts.joinToString(" · ")
    }

    /**
     * The NPC guard's own arithmetic: when a shield-bearer covers, and how
     * long the stance holds. Quick boards come up readily; great boards are
     * raised deliberately and dropped late.
     */
    fun shouldBlock(
        hasShield: Boolean,
        aware: Boolean,
        distance: Float,
        attackCooldown: Float,
        handling: Float
    ): Boolean = hasShield && aware && distance > 1.15f && distance < 6f &&
        attackCooldown > (0.7f - handling * 0.3f)
}
