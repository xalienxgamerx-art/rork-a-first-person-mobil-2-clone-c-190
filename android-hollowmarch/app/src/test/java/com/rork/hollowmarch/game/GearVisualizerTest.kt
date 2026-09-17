package com.rork.hollowmarch.game

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a body shows of what it holds: sides, centers, and the raised guard. */
class GearVisualizerTest {

    private fun armed(vararg items: Item): Entity {
        val entity = Entity(
            x = 5f, y = 5f, spriteId = Sprites.HUSK, kind = EntityKind.ENEMY,
            height = 1f, name = "husk"
        )
        val equipment = Equipment.empty()
        items.forEach { equipment.equip(it) }
        entity.equipment = equipment
        return entity
    }

    @Test
    fun theWeaponRidesTheSideItsBearerFaces() {
        val entity = armed(Item(ItemArchetype.BLADE, Material.IRON))
        val pieces = GearVisualizer.pieces(entity, 1f, 0f)
        assertEquals("one arm, one piece", 1, pieces.size)
        assertEquals(Sprites.W_BLADE, pieces[0].spriteId)
        assertEquals("the arm wears its metal", Material.IRON.tint, pieces[0].tint)
        assertTrue("seen from behind, the arm rides right", pieces[0].side > 0f)
        val faceToFace = GearVisualizer.pieces(entity, -1f, 0f)
        assertTrue("face to face, the arm rides left", faceToFace[0].side < 0f)
        assertEquals("the sides mirror", abs(pieces[0].side), abs(faceToFace[0].side), 1e-5f)
    }

    @Test
    fun aGreatArmRidesCentered() {
        val pieces = GearVisualizer.pieces(armed(Item(ItemArchetype.BOW, Material.ASHWOOD)), 1f, 0f)
        assertEquals(1, pieces.size)
        assertEquals(Sprites.W_BOW, pieces[0].spriteId)
        assertEquals("the long arm takes the middle", 0f, pieces[0].side, 1e-5f)
        val sword = GearVisualizer.pieces(armed(Item(ItemArchetype.BLADE, Material.IRON)), 1f, 0f)[0]
        assertTrue("carried big, not like a short blade", pieces[0].scale > sword.scale)
        assertTrue("carried low, in both hands", pieces[0].bottom < sword.bottom)
    }

    @Test
    fun theBoardRidesTheOffArmAndRisesIntoTheGuard() {
        val entity = armed(
            Item(ItemArchetype.BLADE, Material.IRON),
            Item(ItemArchetype.ROUND_SHIELD, Material.IRON)
        )
        val rest = GearVisualizer.pieces(entity, 1f, 0f)
        assertEquals("the arm and the board", 2, rest.size)
        val board = rest.first { it.spriteId == Sprites.S_ROUND }
        assertTrue("the board rides the off side", board.side < 0f)

        val guarded = GearVisualizer.pieces(entity, 1f, 1f)
        val raised = guarded.first { it.spriteId == Sprites.S_ROUND }
        assertTrue("the guard slides toward the center", abs(raised.side) < abs(board.side))
        assertTrue("the guard lifts", raised.bottom > board.bottom)
        assertTrue("the guard squares up", raised.scale > board.scale)
    }

    @Test
    fun aTorchBurnsInWhateverHandHoldsIt() {
        val bearer = armed(Item(ItemArchetype.TORCH, Material.ASHWOOD))
        // the torch fills the right hand first, like any short arm
        assertEquals(Sprites.HELD_TORCH, GearVisualizer.pieces(bearer, 1f, 0f)[0].spriteId)

        val paired = Entity(
            x = 5f, y = 5f, spriteId = Sprites.HUSK, kind = EntityKind.ENEMY,
            height = 1f, name = "husk"
        )
        val equipment = Equipment.empty()
        equipment.equip(Item(ItemArchetype.BLADE, Material.IRON))
        equipment.equip(Item(ItemArchetype.TORCH, Material.ASHWOOD), Hand.LEFT)
        paired.equipment = equipment
        val pieces = GearVisualizer.pieces(paired, 1f, 0f)
        assertEquals(2, pieces.size)
        val torch = pieces.first { it.spriteId == Sprites.HELD_TORCH }
        assertTrue("the torch rides the off side", torch.side < 0f)
    }

    @Test
    fun theBareAndTheDeadShowNothing() {
        val hound = Entity(
            x = 1f, y = 1f, spriteId = Sprites.HOUND, kind = EntityKind.ENEMY,
            height = 0.62f, name = "barrow hound"
        )
        assertTrue("a hound carries no arms", GearVisualizer.pieces(hound, 1f, 0f).isEmpty())

        val emptyHanded = armed()
        assertTrue("bare hands show bare", GearVisualizer.pieces(emptyHanded, 1f, 0f).isEmpty())

        val corpse = armed(Item(ItemArchetype.BLADE, Material.IRON))
        corpse.alive = false
        assertTrue("the dead lie bare", GearVisualizer.pieces(corpse, 1f, 0f).isEmpty())
    }
}
