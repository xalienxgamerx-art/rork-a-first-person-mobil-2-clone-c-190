package com.rork.hollowmarch.game

/**
 * One piece of carried gear painted onto a body's billboard: its picture, its
 * metal, and where it hangs — lateral offset in body half-widths (+ is the
 * camera's right), bottom edge and height in bearer-heights.
 */
data class GearPiece(
    val spriteId: Int,
    val tint: Int,
    val side: Float,
    val bottom: Float,
    val scale: Float
)

/**
 * What a creature shows of what it wears and holds. Pure geometry — no sheet,
 * no pixels — so the renderer stays dumb and the tests stay cheap.
 */
object GearVisualizer {

    /** A one-handed arm hangs at the hand's side. */
    private const val HAND_SIDE = 0.58f
    private const val HAND_BOTTOM = 0.24f
    private const val HAND_SCALE = 0.66f

    /** A two-hander rides the chest: lower, bigger, dead center. */
    private const val GREAT_SIDE = 0f
    private const val GREAT_BOTTOM = 0.10f
    private const val GREAT_SCALE = 0.85f

    private const val SHIELD_SIDE = 0.62f
    private const val SHIELD_BOTTOM = 0.30f
    private const val SHIELD_SCALE = 0.52f

    private const val TORCH_SIDE = 0.55f
    private const val TORCH_BOTTOM = 0.30f
    private const val TORCH_SCALE = 0.62f

    /** How far the raised board slides toward the center and lifts, at full guard. */
    private const val RAISE_SLIDE = 0.72f
    private const val RAISE_LIFT = 0.26f

    /**
     * The pieces a body shows, given [rightSign] — the cosine of the angle
     * between its facing and the camera's view: +1 seen from behind, -1 face
     * to face — and [guardRaise], how far a shield has eased up into the
     * guard, 0..1.
     */
    fun pieces(entity: Entity, rightSign: Float, guardRaise: Float): List<GearPiece> {
        val equipment = entity.equipment ?: return emptyList()
        if (!entity.alive) return emptyList()
        val right = equipment.worn(WearSlot.RIGHT_HAND)
        val left = equipment.worn(WearSlot.LEFT_HAND)
        val great = right != null && equipment.leftClaimed
        val pieces = mutableListOf<GearPiece>()
        if (right != null) {
            pieces += if (great) {
                GearPiece(
                    Sprites.forWeapon(right.archetype), right.material.tint,
                    GREAT_SIDE, GREAT_BOTTOM, GREAT_SCALE
                )
            } else {
                handPiece(right, rightSign * HAND_SIDE)
            }
        }
        if (left != null && !great) pieces += offPiece(left, -rightSign, guardRaise)
        return pieces
    }

    private fun handPiece(item: Item, side: Float): GearPiece = when {
        item.archetype == ItemArchetype.TORCH ->
            GearPiece(Sprites.HELD_TORCH, 0xFFFFFF, side, TORCH_BOTTOM, TORCH_SCALE)
        else ->
            GearPiece(Sprites.forWeapon(item.archetype), item.material.tint, side, HAND_BOTTOM, HAND_SCALE)
    }

    /** The off arm: a board that rises into the guard, or whatever else it holds. */
    private fun offPiece(item: Item, side: Float, guardRaise: Float): GearPiece = when {
        item.archetype.slot == ItemSlot.SHIELD -> {
            val raise = guardRaise.coerceIn(0f, 1f)
            GearPiece(
                Sprites.forShield(item.archetype),
                item.material.tint,
                side * (1f - RAISE_SLIDE * raise),
                SHIELD_BOTTOM + RAISE_LIFT * raise,
                SHIELD_SCALE + 0.08f * raise
            )
        }
        item.archetype == ItemArchetype.TORCH ->
            GearPiece(Sprites.HELD_TORCH, 0xFFFFFF, side, TORCH_BOTTOM, TORCH_SCALE)
        else ->
            GearPiece(Sprites.forWeapon(item.archetype), item.material.tint, side, HAND_BOTTOM, HAND_SCALE)
    }
}
