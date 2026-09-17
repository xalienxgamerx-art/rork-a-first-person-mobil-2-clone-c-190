package com.rork.hollowmarch.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The seventeen places, and what each will and will not hold. */
class EquipmentTest {

    private fun blade(quality: Quality = Quality.HONEST) =
        Item(ItemArchetype.BLADE, Material.VERDIGRIS, quality)

    private fun dagger() = Item(ItemArchetype.DAGGER, Material.BONE, Quality.HONEST)

    private fun helm(quality: Quality = Quality.HONEST) =
        Item(ItemArchetype.HELM, Material.BOG_IRON, quality)

    private fun hauberk() = Item(ItemArchetype.HAUBERK, Material.BOG_IRON, Quality.WORN)

    private fun signet(quality: Quality = Quality.HONEST) =
        Item(ItemArchetype.SIGNET, Material.BRASS, quality)

    private fun band() = Item(ItemArchetype.BAND, Material.BONE, Quality.HONEST)

    private fun charm() = Item(ItemArchetype.CHARM, Material.BONE, Quality.HONEST)

    @Test
    fun aWeaponTakesTheRightHandThenTheLeftThenDisplacesTheRight() {
        val equipment = Equipment.empty()
        val first = blade()
        val second = dagger()
        val third = blade(Quality.FINE)

        val worn = equipment.equip(first)
        assertEquals(WearSlot.RIGHT_HAND, worn?.first)
        assertTrue(worn?.second.isNullOrEmpty())

        assertEquals(WearSlot.LEFT_HAND, equipment.equip(second)?.first)

        val thirdResult = equipment.equip(third)
        assertEquals(WearSlot.RIGHT_HAND, thirdResult?.first)
        assertEquals(listOf(first), thirdResult?.second)
        // The left hand still holds the second blade; only the right gave way.
        assertEquals(second, equipment.worn(WearSlot.LEFT_HAND))
        assertEquals(third, equipment.worn(WearSlot.RIGHT_HAND))
    }

    @Test
    fun aChosenHandTakesThePieceWhenBothStandFree() {
        val equipment = Equipment.empty()
        val ash = Item(ItemArchetype.TORCH, Material.ASHWOOD, Quality.HONEST)
        // The bearer's choice outranks the right-first fill order.
        assertEquals(WearSlot.LEFT_HAND, equipment.equip(ash, Hand.LEFT)?.first)

        // A choice for an occupied hand displaces what stands there.
        val pitch = Item(ItemArchetype.TORCH, Material.VERDIGRIS, Quality.HONEST)
        val result = equipment.equip(pitch, Hand.LEFT)!!
        assertEquals(WearSlot.LEFT_HAND, result.first)
        assertEquals(listOf(ash), result.second)
        assertEquals(pitch, equipment.worn(WearSlot.LEFT_HAND))
    }

    @Test
    fun aLongArmClaimsBothHandsAndForbidsTheLeft() {
        val equipment = Equipment.empty()
        equipment.equip(blade())
        val spear = Item(ItemArchetype.SPEAR, Material.ASHWOOD, Quality.HONEST)

        val result = equipment.equip(spear)
        assertEquals(WearSlot.RIGHT_HAND, result?.first)
        // Everything the hands held gives way to the long arm.
        assertEquals(listOf(blade()), result!!.second)
        assertTrue(equipment.leftClaimed)
        assertNull(equipment.worn(WearSlot.LEFT_HAND))
        assertEquals(spear, equipment.worn(WearSlot.RIGHT_HAND))

        // A short arm cannot share the body with a long one: the long arm yields.
        val shortArm = dagger()
        val displaced = equipment.equip(shortArm)
        assertFalse(equipment.leftClaimed)
        assertEquals(shortArm, equipment.worn(WearSlot.RIGHT_HAND))
        assertEquals(1, displaced?.second?.size)
        assertEquals(spear.archetype, displaced!!.second.first().archetype)
    }

    @Test
    fun ringsFillTheLeftRingBeforeTheRight() {
        val equipment = Equipment.empty()
        assertEquals(WearSlot.LEFT_RING, equipment.equip(signet())?.first)
        assertEquals(WearSlot.RIGHT_RING, equipment.equip(band())?.first)

        // A third ring displaces the first.
        val third = signet(Quality.FINE)
        val result = equipment.equip(third)
        assertEquals(WearSlot.LEFT_RING, result?.first)
        assertEquals(third, equipment.worn(WearSlot.LEFT_RING))
        assertEquals(band(), equipment.worn(WearSlot.RIGHT_RING))
    }

    @Test
    fun accessoriesFillTheirPlacesInOrder() {
        val equipment = Equipment.empty()
        WearSlot.entries.filter { it.isAccessory }.forEachIndexed { index, slot ->
            assertEquals(slot, equipment.equip(charm())?.first)
            assertEquals(index + 1, WearSlot.entries.count { it.isAccessory && equipment.worn(it) != null })
        }
        // The fifth charm turns the first out.
        val fifth = charm()
        val result = equipment.equip(fifth)
        assertEquals(WearSlot.ACCESSORY_0, result?.first)
        assertEquals(fifth, equipment.worn(WearSlot.ACCESSORY_0))
    }

    @Test
    fun armorDisplacesWhatItReplaces() {
        val equipment = Equipment.empty()
        equipment.equip(helm())
        val betterHelm = helm(Quality.LEGENDARY)

        val result = equipment.equip(betterHelm)
        assertEquals(WearSlot.HEAD, result?.first)
        assertEquals(betterHelm, equipment.worn(WearSlot.HEAD))
        assertEquals(1, result?.second?.size)
        assertEquals(Quality.HONEST, result!!.second.first().quality)

        // A chest piece does not disturb the head.
        val chest = hauberk()
        assertEquals(WearSlot.CHEST, equipment.equip(chest)?.first)
        assertEquals(betterHelm, equipment.worn(WearSlot.HEAD))
    }

    @Test
    fun onlyWearableThingsCanBeWorn() {
        val equipment = Equipment.empty()
        assertNull(equipment.equip(Item(ItemArchetype.REMEDY, Material.VERDIGRIS)))
        assertTrue(equipment.isEmpty())
        // The torch is held like an arm: it takes the free hand, then the other.
        assertEquals(WearSlot.RIGHT_HAND, equipment.equip(Item(ItemArchetype.TORCH, Material.ASHWOOD))?.first)
        assertEquals(WearSlot.LEFT_HAND, equipment.equip(Item(ItemArchetype.TORCH, Material.VERDIGRIS))?.first)
        // And the hand counts a burning torch as an arm.
        assertNotNull(equipment.weaponIn(Hand.RIGHT))
    }

    @Test
    fun anOutfitSurvivesASaveRoundTrip() {
        val equipment = Equipment.empty()
        equipment.equip(blade(Quality.FINE))
        equipment.equip(dagger())
        equipment.equip(helm())
        equipment.equip(signet())
        equipment.equip(charm())

        val restored = Equipment.fromEncoded(equipment.encode())
        assertEquals(equipment.all(), restored.all())
        assertFalse(restored.leftClaimed)

        // A long arm rides the save with both hands claimed.
        val longArms = Equipment.empty()
        val spear = Item(ItemArchetype.SPEAR, Material.ASHWOOD, Quality.HONEST)
        longArms.equip(spear)
        val restoredLong = Equipment.fromEncoded(longArms.encode())
        assertTrue(restoredLong.leftClaimed)
        assertEquals(spear, restoredLong.worn(WearSlot.RIGHT_HAND))
        assertNull(restoredLong.worn(WearSlot.LEFT_HAND))

        // Blank and broken strings wake bare, never broken.
        assertTrue(Equipment.fromEncoded(null).isEmpty())
        assertTrue(Equipment.fromEncoded("").isEmpty())
        assertTrue(Equipment.fromEncoded("garbage\u001E\u001Enonsense").isEmpty())
    }

    @Test
    fun strippingFreesBothHandsOfALongArm() {
        val equipment = Equipment.empty()
        val spear = Item(ItemArchetype.SPEAR, Material.ASHWOOD, Quality.HONEST)
        equipment.equip(spear)
        assertTrue(equipment.leftClaimed)

        assertEquals(spear, equipment.unequip(WearSlot.RIGHT_HAND))
        assertFalse(equipment.leftClaimed)
        assertNull(equipment.unequip(WearSlot.LEFT_HAND))
        assertTrue(equipment.isEmpty())
    }

    @Test
    fun theBodyBearsNoMoreForWearing() {
        val equipment = Equipment.empty()
        equipment.equip(helm())
        equipment.equip(hauberk())
        assertEquals(
            equipment.items().sumOf { it.weight().toDouble() }.toFloat(),
            equipment.weight(),
            0.001f
        )
        // The ledger reads in wearing order: head before hands before rings.
        val order = equipment.all().map { it.first }
        assertEquals(listOf(WearSlot.HEAD, WearSlot.CHEST), order)
    }
}
