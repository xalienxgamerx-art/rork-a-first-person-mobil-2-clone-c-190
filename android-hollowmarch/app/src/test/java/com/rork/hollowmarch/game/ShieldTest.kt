package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * The shield ledger: creation, mass, coverage, the toggle grammar, the
 * geometry of interception, the blow's arithmetic, and what the save keeps.
 */
class ShieldTest {

    private val world = WorldGenerator.generate(424242L)

    @Before
    fun resetUids() = ItemUids.reset()

    // ------------------------------------------------------------- creation

    @Test
    fun `all eleven shield forms exist as shield archetypes`() {
        val shields = ItemArchetype.entries.filter { Shields.isShield(it) }
        assertEquals(11, shields.size)
        assertEquals(ShieldType.entries.size, shields.size)
        shields.forEach { archetype ->
            assertEquals(archetype, Shields.archetypeFor(Shields.typeOf(archetype)!!))
        }
    }

    @Test
    fun `weapon archetypes are no shields`() {
        ItemArchetype.WEAPONS.forEach { archetype ->
            assertFalse(Shields.isShield(archetype))
            assertNull(Shields.typeOf(archetype))
        }
    }

    @Test
    fun `creation carries construction, reinforcement, grip and dimensions`() {
        val item = Shields.create(
            ShieldType.KITE, Material.ASHWOOD, ShieldConstruction.LAYERED_WOOD,
            ShieldReinforcement.METAL_RIM, ShieldGrip.STRAPPED, Quality.HONEST, 3, Random(7)
        )
        assertEquals(ItemArchetype.KITE_SHIELD, item.archetype)
        assertEquals(Material.ASHWOOD, item.material)
        assertEquals(ShieldConstruction.LAYERED_WOOD, item.construction)
        assertEquals(ShieldReinforcement.METAL_RIM, item.reinforcement)
        assertEquals(ShieldGrip.STRAPPED, item.grip)
        val dims = item.dimensions
        assertNotNull(dims)
        assertTrue(dims!!.thickness > 0f)
        assertTrue(dims.width > 0f && dims.height > 0f)
    }

    @Test
    fun `dimensions vary plank to plank but stay near the type's measure`() {
        val rng = Random(9)
        val widths = (0 until 20).map {
            Shields.dimensionsOf(
                Shields.create(ShieldType.ROUND, Material.ASHWOOD,
                    ShieldConstruction.SOLID_WOOD, ShieldReinforcement.NONE,
                    ShieldGrip.CENTRAL, Quality.HONEST, -1, rng)
            ).width
        }
        assertTrue(widths.any { it > ShieldType.ROUND.widthM })
        assertTrue(widths.any { it < ShieldType.ROUND.widthM })
        assertTrue(widths.all { it < ShieldType.ROUND.widthM * 1.2f })
    }

    // ------------------------------------------------------------- mass

    @Test
    fun `a shield's weight is the wright's arithmetic, not the base weight`() {
        val plain = Shields.create(
            ShieldType.KITE, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(11)
        )
        val rimmed = Shields.create(
            ShieldType.KITE, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.METAL_RIM, ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(11)
        )
        val plainMass = Shields.massOf(plain)
        assertTrue(plainMass > 0f)
        assertTrue(Shields.massOf(rimmed) > plainMass + 0.5f)
        assertEquals(plainMass, plain.weight(), 0.02f)
    }

    @Test
    fun `metal plate outweighs wood at the same shape`() {
        val wood = Shields.create(
            ShieldType.RONDACHE, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(13)
        )
        val steel = Shields.create(
            ShieldType.RONDACHE, Material.STEEL, ShieldConstruction.METAL_PLATE,
            ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(13)
        )
        assertTrue(Shields.massOf(steel) > Shields.massOf(wood) * 2f)
    }

    @Test
    fun `a boss adds mass and rigidity the plain board lacks`() {
        val bare = Shields.create(ShieldType.TARGE, Material.ASHWOOD,
            ShieldConstruction.WOOD_LEATHER, ShieldReinforcement.NONE,
            ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(17))
        val bossed = Shields.create(
            ShieldType.TARGE, Material.ASHWOOD,
            ShieldConstruction.WOOD_LEATHER, ShieldReinforcement.BOSS,
            ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(17)
        )
        assertTrue(Shields.massOf(bossed) > Shields.massOf(bare))
        assertTrue(
            Shields.effectiveness(bossed, DamageType.SHARP, 0f, 1) >
                Shields.effectiveness(bare, DamageType.SHARP, 0f, 1)
        )
    }

    @Test
    fun `grip changes handling`() {
        val central = Shields.create(ShieldType.ROUND, Material.ASHWOOD,
            ShieldConstruction.SOLID_WOOD, ShieldReinforcement.NONE,
            ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(19))
        val guige = Shields.create(ShieldType.ROUND, Material.ASHWOOD,
            ShieldConstruction.SOLID_WOOD, ShieldReinforcement.NONE,
            ShieldGrip.GUIGE, Quality.HONEST, -1, Random(19))
        assertTrue(Shields.handling(central) > Shields.handling(guige))
    }

    // ------------------------------------------------------------- equipment

    private fun shieldItem(): Item = Shields.create(
        ShieldType.HEATER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
        ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(23)
    )

    @Test
    fun `a shield takes the left hand`() {
        val equipment = Equipment.empty()
        val shield = shieldItem()
        val result = equipment.equip(shield)
        assertEquals(WearSlot.LEFT_HAND, result!!.first)
        assertTrue(result.second.isEmpty())
        assertEquals(WearSlot.LEFT_HAND, equipment.slotOf(shield))
    }

    @Test
    fun `one-handed weapon and shield coexist`() {
        val equipment = Equipment.empty()
        val sword = Item(ItemArchetype.SHORTSWORD, Material.ASHWOOD)
        val shield = shieldItem()
        equipment.equip(sword)
        equipment.equip(shield)
        assertEquals(WearSlot.RIGHT_HAND, equipment.slotOf(sword))
        assertEquals(WearSlot.LEFT_HAND, equipment.slotOf(shield))
        assertEquals(sword, equipment.weaponIn(Hand.RIGHT))
        assertEquals(sword, equipment.bestWeapon())
    }

    @Test
    fun `the board is no longer counted as an arm`() {
        val equipment = Equipment.empty()
        equipment.equip(shieldItem())
        assertNull(equipment.weaponIn(Hand.LEFT))
        assertNull(equipment.bestWeapon())
    }

    @Test
    fun `a long arm that claimed both hands gives way to the board`() {
        val equipment = Equipment.empty()
        val spear = Item(ItemArchetype.SPEAR, Material.ASHWOOD)
        equipment.equip(spear)
        assertTrue(equipment.leftClaimed)
        val result = equipment.equip(shieldItem())!!
        assertEquals(WearSlot.LEFT_HAND, result.first)
        assertTrue(result.second.contains(spear))
        assertFalse(equipment.leftClaimed)
    }

    @Test
    fun `a long arm displaces the board from the left hand`() {
        val equipment = Equipment.empty()
        val shield = shieldItem()
        equipment.equip(shield)
        val spear = Item(ItemArchetype.SPEAR, Material.ASHWOOD)
        val result = equipment.equip(spear)!!
        assertTrue(result.second.contains(shield))
        assertNull(equipment.worn(WearSlot.LEFT_HAND))
    }

    // ------------------------------------------------------------- save/load

    @Test
    fun `a shield encodes and restores whole`() {
        val item = Shields.create(
            ShieldType.HEATER, Material.IRON, ShieldConstruction.WOOD_LEATHER,
            ShieldReinforcement.METAL_RIM, ShieldGrip.STRAPPED, Quality.FINE, 5, Random(23)
        )
        assertEquals(item, Item.fromEncoded(item.encode()))
    }

    @Test
    fun `an old save's six-field shield line wakes with honest defaults`() {
        val restored = Item.fromEncoded("KITE_SHIELD=ASHWOOD=HONEST=3=1=0")
        assertNotNull(restored)
        assertEquals(ShieldConstruction.SOLID_WOOD, restored!!.construction)
        assertEquals(ShieldReinforcement.NONE, restored.reinforcement)
        assertEquals(ShieldGrip.CENTRAL, restored.grip)
        assertEquals(ShieldType.KITE.widthM, Shields.dimensionsOf(restored).width, 0.0001f)
    }

    @Test
    fun `a mangled shield line wakes bare, never broken`() {
        assertNull(Item.fromEncoded("KITE_SHIELD=ASHWOOD=HONEST"))
        assertNull(Item.fromEncoded("KITE_SHIELD=NOT_A_METAL=HONEST=3=1=0"))
    }

    @Test
    fun `shield equipment rides the save`() {
        val engine = GameEngine(world, null, null)
        val shield = Shields.create(
            ShieldType.PAVISE, Material.ASHWOOD, ShieldConstruction.LAYERED_WOOD,
            ShieldReinforcement.GROUND_STAND, ShieldGrip.GUIGE, Quality.FINE, -1, Random(29)
        )
        engine.equipment.equip(shield)
        val woken = GameEngine(world, engine.toSaveSlot(), null)
        val guard = Shields.worn(woken.equipment)
        assertNotNull(guard)
        assertEquals(ShieldType.PAVISE, Shields.typeOf(guard!!.archetype))
        assertEquals(ShieldConstruction.LAYERED_WOOD, guard.construction)
        assertEquals(ShieldReinforcement.GROUND_STAND, guard.reinforcement)
        assertEquals(ShieldGrip.GUIGE, guard.grip)
        // blocking is transient combat state: a loaded save wakes unblocked
        assertFalse(woken.blocking)
    }

    // ------------------------------------------------------------- toggle

    @Test
    fun `no shield, no guard, no crash`() {
        val engine = GameEngine(world, null, null)
        engine.toggleBlock()
        assertFalse(engine.blocking)
        engine.toggleBlock()
        engine.toggleBlock()
        assertFalse(engine.blocking)
    }

    @Test
    fun `tap once raises, tap again lowers`() {
        val engine = GameEngine(world, null, null)
        engine.equipment.equip(shieldItem())
        engine.toggleBlock()
        assertTrue(engine.blocking)
        engine.toggleBlock()
        assertFalse(engine.blocking)
    }

    @Test
    fun `holding changes nothing, only presses do`() {
        val engine = GameEngine(world, null, null)
        engine.equipment.equip(shieldItem())
        engine.toggleBlock()
        assertTrue(engine.blocking)
        // the hold and the release are not presses: no further calls, no further flips
        assertTrue(engine.blocking)
        engine.toggleBlock()
        assertFalse(engine.blocking)
        // a long hold after the second tap is still just that one transition
        assertFalse(engine.blocking)
    }

    @Test
    fun `a shield taken down while blocking ends the guard`() {
        val engine = GameEngine(world, null, null)
        val shield = shieldItem()
        engine.equipment.equip(shield)
        engine.toggleBlock()
        assertTrue(engine.blocking)
        engine.unequipFromEquipment(WearSlot.LEFT_HAND)
        assertFalse(engine.blocking)
        assertTrue(engine.inventory.all.contains(shield))
    }

    @Test
    fun `no shield at all means the toggle grants nothing`() {
        val engine = GameEngine(world, null, null)
        engine.toggleBlock()
        assertFalse(engine.blocking)
    }

    // ------------------------------------------------------------- geometry

    private val guard = Shields.create(
        ShieldType.HEATER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
        ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(31)
    )

    @Test
    fun `frontal attacks land center, oblique the edge, behind misses`() {
        val half = Shields.coverageHalfAngle(guard)
        assertEquals(BlockZone.CENTER, Shields.zone(0f, half))
        assertEquals(BlockZone.EDGE, Shields.zone(half * 0.8f, half))
        assertEquals(BlockZone.MISS, Shields.zone(half * 1.2f, half))
        assertEquals(BlockZone.MISS, Shields.zone(180f, half))
    }

    @Test
    fun `off-axis reads the defender's facing`() {
        assertEquals(0f, Shields.offAxisDegrees(1f, 0f, 5f, 0f), 0.001f)
        assertEquals(90f, Shields.offAxisDegrees(1f, 0f, 0f, 5f), 0.001f)
        assertEquals(180f, Shields.offAxisDegrees(1f, 0f, -5f, 0f), 0.001f)
    }

    @Test
    fun `a buckler covers less than a pavise`() {
        val buckler = Shields.create(
            ShieldType.BUCKLER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(37)
        )
        val pavise = Shields.create(
            ShieldType.PAVISE, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.GUIGE, Quality.HONEST, -1, Random(41)
        )
        val small = Shields.coverageHalfAngle(buckler)
        val great = Shields.coverageHalfAngle(pavise)
        assertTrue(great > small + 20f)
        // the same blow: the buckler loses it, the pavise still covers
        assertEquals(BlockZone.MISS, Shields.zone(30f, small))
        assertTrue(Shields.zone(30f, great) != BlockZone.MISS)
    }

    @Test
    fun `a rim widens the covered cone`() {
        val bare = Shields.create(
            ShieldType.KITE, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(43)
        )
        val rimmed = Shields.create(
            ShieldType.KITE, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.METAL_RIM, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(43)
        )
        assertEquals(Shields.coverageHalfAngle(bare) + 2f, Shields.coverageHalfAngle(rimmed), 0.001f)
    }

    // ------------------------------------------------------------- the blow

    @Test
    fun `center takes more of the blow than the edge`() {
        val center = Shields.resolve(guard, DamageType.SHARP, 0f, 1, 10)
        val edge = Shields.resolve(guard, DamageType.SHARP, 40f, 1, 10)
        assertEquals(BlockZone.CENTER, center.first)
        assertEquals(BlockZone.EDGE, edge.first)
        assertTrue(center.second > edge.second)
    }

    @Test
    fun `misses grant nothing`() {
        val miss = Shields.resolve(guard, DamageType.SHARP, 180f, 10, 10)
        assertEquals(BlockZone.MISS, miss.first)
        assertEquals(0f, miss.second, 0.0001f)
    }

    @Test
    fun `each temper meets its own harm - metal the point, hide the maul`() {
        val plate = Shields.create(
            ShieldType.RONDACHE, Material.STEEL, ShieldConstruction.METAL_PLATE,
            ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(47)
        )
        val hide = Shields.create(
            ShieldType.HEATER, Material.ASHWOOD, ShieldConstruction.LEATHER,
            ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(47)
        )
        // the same board answers each harm differently, by its make
        assertTrue(
            Shields.effectiveness(plate, DamageType.PIERCE, 0f, 1, 10) >
                Shields.effectiveness(plate, DamageType.BLUNT, 0f, 1, 10)
        )
        assertTrue(
            Shields.effectiveness(hide, DamageType.BLUNT, 0f, 1, 10) >
                Shields.effectiveness(hide, DamageType.PIERCE, 0f, 1, 10)
        )
        // and across boards: metal turns the point far better than hide
        assertTrue(
            Shields.effectiveness(plate, DamageType.PIERCE, 0f, 1, 10) >
                Shields.effectiveness(hide, DamageType.PIERCE, 0f, 1, 10)
        )
    }

    @Test
    fun `defense training helps but never erases the shape`() {
        val buckler = Shields.create(
            ShieldType.BUCKLER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(53)
        )
        val tower = Shields.create(
            ShieldType.TOWER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(53)
        )
        val novice = Shields.effectiveness(buckler, DamageType.SHARP, 0f, 1, 10)
        val veteran = Shields.effectiveness(buckler, DamageType.SHARP, 0f, 20, 10)
        assertTrue(veteran > novice)
        // the master's buckler still covers less than the raw recruit's tower
        assertTrue(Shields.coverageHalfAngle(buckler) < Shields.coverageHalfAngle(tower))
    }

    @Test
    fun `straps hold a maul better than a fist`() {
        val strapped = Shields.create(
            ShieldType.ROUND, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(59)
        )
        val fist = Shields.create(
            ShieldType.ROUND, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(59)
        )
        // identical planks, different grip: only the stability differs
        assertEquals(Shields.massOf(strapped), Shields.massOf(fist), 0.001f)
        assertTrue(
            Shields.effectiveness(strapped, DamageType.BLUNT, 0f, 1, 10) >
                Shields.effectiveness(fist, DamageType.BLUNT, 0f, 1, 10)
        )
    }

    // ------------------------------------------------------------- handling

    @Test
    fun `a buckler moves and turns better than a tower while blocking`() {
        val buckler = Shields.create(
            ShieldType.BUCKLER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(61)
        )
        val tower = Shields.create(
            ShieldType.TOWER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.GUIGE, Quality.HONEST, -1, Random(61)
        )
        assertTrue(Shields.moveFactor(buckler) > Shields.moveFactor(tower) + 0.3f)
        assertTrue(Shields.turnFactor(buckler) > Shields.turnFactor(tower))
        assertTrue(Shields.recoveryFactor(buckler) < Shields.recoveryFactor(tower))
    }

    @Test
    fun `carried mass rides the ordinary load`() {
        val engine = GameEngine(world, null, null)
        val tower = Shields.create(
            ShieldType.TOWER, Material.STEEL, ShieldConstruction.METAL_PLATE,
            ShieldReinforcement.METAL_BANDS, ShieldGrip.GUIGE, Quality.HONEST, -1, Random(67)
        )
        val buckler = Shields.create(
            ShieldType.BUCKLER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(67)
        )
        assertTrue(tower.weight() > buckler.weight() * 3f)
        assertTrue(engine.carryCapacity() > 0f)
        // worn things weigh on the body the same as carried ones
        engine.equipment.equip(tower)
        assertTrue(engine.equipment.weight() >= tower.weight())
        assertTrue(engine.equipment.weight() < tower.weight() + 0.01f ||
            engine.equipment.items().size > 1)
    }

    // ------------------------------------------------------------- NPC

    @Test
    fun `npc shield decision - cover on the approach, drop it to strike`() {
        val buckler = Shields.create(
            ShieldType.BUCKLER, Material.ASHWOOD, ShieldConstruction.SOLID_WOOD,
            ShieldReinforcement.NONE, ShieldGrip.CENTRAL, Quality.HONEST, -1, Random(73)
        )
        val handling = Shields.handling(buckler)
        // far and unaware: no guard
        assertFalse(Shields.shouldBlock(true, false, 4f, 2f, handling))
        // mid range, aware, not about to swing: guard up
        assertTrue(Shields.shouldBlock(true, true, 4f, 1.2f, handling))
        // at striking distance: guard down
        assertFalse(Shields.shouldBlock(true, true, 1.0f, 1.2f, handling))
        // the swing is due: guard down
        assertFalse(Shields.shouldBlock(true, true, 3f, 0.2f, handling))
        // no board, no guard
        assertFalse(Shields.shouldBlock(false, true, 3f, 1.2f, handling))
    }

    @Test
    fun `armLoot arms the shield callings with boards`() {
        val klass = GameEngine(world, null, null).roster.byKey("warden")
        assertNotNull(klass)
        var shields = 0
        var bare = 0
        repeat(60) { index ->
            val entity = Entity(
                x = 5f, y = 5f, spriteId = Sprites.HUSK, kind = EntityKind.ENEMY,
                height = 1f, name = "husk"
            )
            MapFactory.armLoot(entity, klass, 3, -1, Random(index.toLong() * 131 + 5))
            if (Shields.worn(entity.equipment!!) != null) shields++ else bare++
        }
        assertTrue("expected some wardens bearing boards, got $shields", shields > 5)
        assertTrue("expected some wardens without boards, got $bare", bare > 5)
    }

    // ------------------------------------------------------------- rendering

    @Test
    fun `every shield archetype maps to its own sprite`() {
        val ids = ShieldType.entries.map { Sprites.forShield(Shields.archetypeFor(it)) }
        assertEquals(ShieldType.entries.size, ids.toSet().size)
        assertEquals(Sprites.ITEM, Sprites.forShield(ItemArchetype.BLADE))
    }

    @Test
    fun `drops keep the board's own shape and tint`() {
        val item = Shields.create(
            ShieldType.ROTELLA, Material.COPPER, ShieldConstruction.METAL_PLATE,
            ShieldReinforcement.NONE, ShieldGrip.STRAPPED, Quality.HONEST, -1, Random(79)
        )
        assertEquals(
            Sprites.tinted(Sprites.S_ROTELLA, Material.COPPER.tint),
            Sprites.forDrop(item)
        )
        // the untinted sheet still holds the board's own shape
        assertNotNull(Sprites[Sprites.S_ROTELLA])
    }
}
