package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTest {

    @Test
    fun theLoadIsTheSumOfItsPieces() {
        val inventory = Inventory.fromEncoded(null)
        assertTrue(inventory.isEmpty())
        assertEquals(0f, inventory.weight(), 0.001f)

        val blade = Item(ItemArchetype.BLADE, Material.GRAVE_SLATE, Quality.HONEST, -1)
        val remedies = Item(ItemArchetype.REMEDY, Material.VERDIGRIS, count = 3)
        inventory.add(blade)
        inventory.add(remedies)
        assertEquals(blade.weight() + remedies.weight(), inventory.weight(), 0.01f)
        assertEquals(2, inventory.all.size)
        assertEquals(3, inventory.countOf(ItemArchetype.REMEDY))
    }

    @Test
    fun stackablesJoinTheirKinAndLeaveOneByOne() {
        val inventory = Inventory.fromEncoded(null)
        inventory.add(Item(ItemArchetype.TORCH, Material.ASHWOOD, count = 2))
        inventory.add(Item(ItemArchetype.TORCH, Material.ASHWOOD, count = 2))
        assertEquals(1, inventory.all.size)
        assertEquals(4, inventory.countOf(ItemArchetype.TORCH))

        assertTrue(inventory.useTorch())
        assertTrue(inventory.useTorch())
        assertTrue(inventory.useTorch())
        assertTrue(inventory.useTorch())
        assertFalse(inventory.useTorch())
        assertTrue(inventory.isEmpty())

        // Removing more than you hold does nothing; removing less leaves the rest.
        inventory.add(Item(ItemArchetype.REMEDY, Material.VERDIGRIS, count = 3))
        assertFalse(inventory.remove(Item(ItemArchetype.REMEDY, Material.VERDIGRIS), 4))
        assertTrue(inventory.remove(Item(ItemArchetype.REMEDY, Material.VERDIGRIS), 2))
        assertEquals(1, inventory.countOf(ItemArchetype.REMEDY))

        // A weapon stands alone and leaves whole.
        inventory.add(Item(ItemArchetype.BLADE, Material.BONE))
        inventory.add(Item(ItemArchetype.BLADE, Material.BRASS))
        assertTrue(inventory.remove(Item(ItemArchetype.BLADE, Material.BONE)))
        assertFalse(inventory.remove(Item(ItemArchetype.BLADE, Material.BONE)))
        assertEquals(2, inventory.all.size)
    }

    @Test
    fun theSatchelSurvivesASaveRoundTrip() {
        val inventory = Inventory.fromEncoded(null)
        inventory.add(Item(ItemArchetype.BLADE, Material.SALT_GLASS, Quality.FINE, 3))
        inventory.add(Item(ItemArchetype.HAUBERK, Material.BOG_IRON, Quality.WORN, 1))
        inventory.add(Item(ItemArchetype.TORCH, Material.ASHWOOD, count = 5))

        val restored = Inventory.fromEncoded(inventory.encode())
        assertEquals(inventory.all, restored.all)
        assertEquals(inventory.weight(), restored.weight(), 0.001f)

        // Old saves predating items wake empty, never broken.
        assertTrue(Inventory.fromEncoded("").isEmpty())
        assertTrue(Inventory.fromEncoded("garbage\u001E\u001E\u001E").isEmpty())
    }

    @Test
    fun theBackBearsWhatItBears() {
        val strong = StatBlock(mapOf(Attr.MIGHT to 14, Attr.VIGOR to 8))
        val weak = StatBlock(mapOf(Attr.MIGHT to 4, Attr.VIGOR to 4))
        assertTrue(Derived.carryCapacity(strong) > Derived.carryCapacity(weak))

        val capacity = Derived.carryCapacity(weak)
        // Easy up to seven tenths full.
        assertEquals(1f, Derived.encumbranceFactor(capacity * 0.5f, capacity), 0.001f)
        // Leaning forward near the limit.
        val near = Derived.encumbranceFactor(capacity * 0.95f, capacity)
        assertTrue(near < 1f && near > 0.7f)
        // A trudge past it.
        assertEquals(0.58f, Derived.encumbranceFactor(capacity * 1.4f, capacity), 0.001f)
        // A trained Endurance carries more.
        val trained = Growth.fromEncoded("1\u001F0\u001F0\u001FENDURANCE=10\u001F")
        assertTrue(Derived.carryCapacity(strong, trained) > Derived.carryCapacity(strong))
    }

    @Test
    fun aFreshDelverWakesWithAStarterKit() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)

        // The waking blade sits in the right hand, not in the satchel.
        assertNotNull(engine.equipment.bestWeapon())
        assertTrue(engine.equipment.worn(WearSlot.RIGHT_HAND)?.archetype == ItemArchetype.BLADE)
        assertTrue(engine.inventory.countOf(ItemArchetype.REMEDY) > 0)
        assertTrue(engine.inventory.countOf(ItemArchetype.TORCH) > 0)
        assertTrue(engine.inventory.weight() <= engine.carryCapacity())
    }

    @Test
    fun aRemedyDrunkIsARemedyGoneAndWoundsClosed() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)
        engine.vitality = (engine.maxVitality * 0.4f).toInt()
        val wounded = engine.vitality
        val before = engine.inventory.countOf(ItemArchetype.REMEDY)

        assertTrue(engine.useRemedy())
        assertTrue(engine.vitality > wounded)
        assertEquals(before - 1, engine.inventory.countOf(ItemArchetype.REMEDY))

        // An empty satchel refuses politely.
        repeat(before) { engine.inventory.useRemedy() }
        assertFalse(engine.useRemedy())
    }

    @Test
    fun aTorchTakenUpLeavesTheRestOfTheBundleShouldered() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)
        val bundle = engine.inventory.all.first { it.archetype == ItemArchetype.TORCH }
        assertEquals(2, bundle.count)

        // One torch leaves the bundle for the free hand; the rest stays shouldered.
        engine.equipFromSatchel(bundle)
        val inHand = engine.equipment.worn(WearSlot.LEFT_HAND)!!
        assertEquals(ItemArchetype.TORCH, inHand.archetype)
        assertEquals(1, inHand.count)
        assertEquals(1, engine.inventory.countOf(ItemArchetype.TORCH))
        // The hand counts a burning torch as an arm.
        assertEquals(inHand, engine.equipment.weaponIn(Hand.LEFT))

        // Given up, it rejoins what remains of the bundle.
        engine.unequipFromEquipment(WearSlot.LEFT_HAND)
        assertEquals(2, engine.inventory.countOf(ItemArchetype.TORCH))
    }

    @Test
    fun theDarkHasNoCircleWithoutATorchInHand() {
        val world = WorldGenerator.generate(9L)
        val engine = GameEngine(world, null, null)
        // Blade only: no torch in hand, no circle of light, no pitch spent.
        assertFalse(engine.holdsTorch)
        assertEquals(0f, engine.litTorch, 0.001f)
        val before = engine.torch
        engine.update(0.5f)
        assertEquals(before, engine.torch, 0.001f)

        // Take up the torch: the circle returns and the burn begins.
        engine.equipFromSatchel(engine.inventory.all.first { it.archetype == ItemArchetype.TORCH })
        assertTrue(engine.holdsTorch)
        assertEquals(before, engine.litTorch, 0.001f)
        engine.update(0.5f)
        assertTrue(engine.litTorch < before)
    }

    @Test
    fun theHandIsAskedOnlyWhenBothStandFree() {
        val world = WorldGenerator.generate(9L)
        val engine = GameEngine(world, null, null)
        val torch = engine.inventory.all.first { it.archetype == ItemArchetype.TORCH }
        // The waking blade holds the right hand: no question, the torch takes the left.
        assertFalse(engine.canChooseHand(torch))
        engine.equipFromSatchel(torch)
        assertEquals(ItemArchetype.TORCH, engine.equipment.worn(WearSlot.LEFT_HAND)?.archetype)

        // Both hands free again: the question returns.
        engine.unequipFromEquipment(WearSlot.RIGHT_HAND)
        engine.unequipFromEquipment(WearSlot.LEFT_HAND)
        assertTrue(engine.canChooseHand(torch))
    }

    @Test
    fun theWieldedTorchFightsRelightsAndSurvivesTheSave() {
        val world = WorldGenerator.generate(9L)
        val engine = GameEngine(world, null, null)
        val bundle = engine.inventory.all.first { it.archetype == ItemArchetype.TORCH }

        // Blade in the right hand; the torch is asked for and takes the left.
        engine.equipFromSatchel(bundle, Hand.LEFT)
        assertEquals(ItemArchetype.TORCH, engine.equipment.weaponIn(Hand.LEFT)?.archetype)
        assertTrue(engine.holdsTorch)

        // It fights: swing after swing with fire in the fist.
        repeat(8) { engine.strike() }

        // It relights while wielded.
        engine.torch = 0.2f
        engine.relightTorch()
        assertEquals(1f, engine.torch, 0.001f)

        // It rides the save and keeps fighting on waking.
        val woken = GameEngine(world, engine.toSaveSlot(), null)
        assertEquals(ItemArchetype.TORCH, woken.equipment.weaponIn(Hand.LEFT)?.archetype)
        repeat(8) { woken.strike() }
        // The relight spent the last of the bundle; the lit one rides in hand.
        assertEquals(0, woken.inventory.countOf(ItemArchetype.TORCH))
    }

    @Test
    fun aTorchFromTheBundleBurnsBeforeTheBrass() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)
        engine.torch = 0.2f
        engine.brass = 50
        val held = engine.inventory.countOf(ItemArchetype.TORCH)

        engine.relightTorch()
        assertEquals(1f, engine.torch, 0.001f)
        assertEquals(held - 1, engine.inventory.countOf(ItemArchetype.TORCH))
        assertEquals(50, engine.brass)

        // With no torches left, pitch is bought as ever.
        repeat(held) { engine.inventory.useTorch() }
        engine.torch = 0.2f
        engine.relightTorch()
        assertEquals(47, engine.brass)
        assertEquals(1f, engine.torch, 0.001f)
    }

    @Test
    fun aThingLaidDownWaitsForTheUseHand() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)
        // The waking blade is in hand; it comes off before it can be set down.
        engine.unequipFromEquipment(WearSlot.RIGHT_HAND)
        val blade = engine.inventory.all.first { it.archetype == ItemArchetype.BLADE }
        val before = engine.inventory.all.size

        engine.dropItem(blade)
        assertEquals(before - 1, engine.inventory.all.size)
        assertEquals(1, engine.groundItems.size)
        // Nothing grabs it back for you: the ground keeps it until you use your hands.
        engine.update(0.05f)
        assertEquals(1, engine.groundItems.size)
        // The USE hand takes it up where you stand.
        assertEquals(Interact.PICKED, engine.interact())
        assertEquals(0, engine.groundItems.size)
        assertEquals(before, engine.inventory.all.size)
    }

    @Test
    fun aStackSetDownIsTheWholeStack() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)
        // The starter kit wakes with a remedy stack of two.
        val stack = engine.inventory.all.first { it.archetype == ItemArchetype.REMEDY }
        assertEquals(2, stack.count)

        engine.dropItem(stack)
        assertEquals(0, engine.inventory.countOf(ItemArchetype.REMEDY))
        assertEquals(2, engine.groundItems.single().item.count)

        assertEquals(Interact.PICKED, engine.interact())
        assertEquals(2, engine.inventory.countOf(ItemArchetype.REMEDY))
    }

    @Test
    fun removalTakesTheVeryPieceYouMean() {
        val inventory = Inventory.fromEncoded(null)
        val bone = Item(ItemArchetype.BLADE, Material.BONE)
        val brass = Item(ItemArchetype.BLADE, Material.BRASS)
        inventory.add(bone)
        inventory.add(brass)

        assertTrue(inventory.remove(brass))
        assertEquals(1, inventory.all.size)
        assertEquals(bone, inventory.all.single())
        assertFalse(inventory.remove(brass))
    }

    @Test
    fun theUseHandChoosesTheNearestWork() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)
        // The delver wakes at the vault's door; step clear of it so nothing is within reach.
        engine.camera.x = 3f
        engine.camera.y = 3f
        assertEquals(Interact.NONE, engine.interact())

        // A grave with spoils, close at hand.
        val grave = Entity(
            x = engine.camera.x + 1f, y = engine.camera.y, spriteId = Sprites.GRAVE,
            kind = EntityKind.PROP, height = 0.5f, name = "grave", container = true,
            loot = mutableListOf(Item(ItemArchetype.CHARM, Material.BONE)), lootBrass = 5
        )
        engine.map.entities += grave
        assertEquals(Interact.LOOT, engine.interact())

        engine.takeAll(grave)
        assertEquals(Interact.NONE, engine.interact())
    }

    @Test
    fun anExpeditionCarriesItsSatchelThroughTheSave() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)
        val remedy = Item(ItemArchetype.REMEDY, Material.VERDIGRIS, count = 2)
        engine.inventory.add(remedy)
        val spare = Item(ItemArchetype.BLADE, Material.BRASS, Quality.HONEST)
        engine.inventory.add(spare)
        engine.dropItem(spare)

        val restored = GameEngine(world, engine.toSaveSlot(), null)
        assertEquals(engine.inventory.all, restored.inventory.all)
        assertEquals(1, restored.groundItems.size)
        assertEquals(engine.groundItems.first().item, restored.groundItems.first().item)
        // The dropped blade is not in both hands and on the floor.
        assertFalse(restored.inventory.all.any { it.archetype == ItemArchetype.BLADE })
        // The saved drop survives the walk over it.
        restored.update(0.05f)
        assertEquals(1, restored.groundItems.size)
    }

    @Test
    fun anOldSaveWakesWithTheStarterKit() {
        val world = WorldGenerator.generate(4242L)
        val slot = SaveSlot(
            seed = world.seed,
            day = 41,
            minutes = 40 * 1440f,
            outdoor = false,
            depth = 3,
            x = 4f,
            y = 4f,
            angle = 0f,
            vitality = 40,
            fatigue = 30,
            magicka = 20,
            torch = 0.8f,
            kills = 3,
            brass = 12,
            siteId = -1,
            siteName = "",
            deeds = emptyList()
        )
        val engine = GameEngine(world, slot, null)
        // An outfit that never rode the save takes up its best blade once.
        assertNotNull(engine.equipment.bestWeapon())
        assertTrue(engine.inventory.countOf(ItemArchetype.TORCH) > 0)
    }

    @Test
    fun anExpeditionCarriesItsOutfitThroughTheSave() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null)
        val helm = Item(ItemArchetype.HELM, Material.BOG_IRON, Quality.FINE)
        engine.inventory.add(helm)
        engine.equipFromSatchel(helm)
        assertEquals(helm, engine.equipment.worn(WearSlot.HEAD))
        // Wearing it lifted it off the back.
        assertFalse(engine.inventory.all.contains(helm))

        val restored = GameEngine(world, engine.toSaveSlot(), null)
        assertEquals(engine.equipment.all(), restored.equipment.all())
        assertEquals(helm, restored.equipment.worn(WearSlot.HEAD))
        assertFalse(restored.inventory.all.contains(helm))
    }
}
