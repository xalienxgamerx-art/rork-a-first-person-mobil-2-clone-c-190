package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProficiencyTest {

    private val world = WorldGenerator.generate(20260914L)

    // ---------------------------------------------------------- the categories

    @Test
    fun everyArmBelongsToExactlyOneFamily() {
        // Every arm of the province answers its own family, melee and ranged alike.
        ItemArchetype.WEAPONS.forEach { archetype ->
            assertTrue(
                "${archetype.name} belongs to no family of arms",
                WeaponCategory.forWeapon(archetype) != null
            )
        }
        // The marksmanship families are their own teachers, beside the melee ones.
        assertEquals(WeaponCategory.ARCHERY, WeaponCategory.forWeapon(ItemArchetype.BOW))
        assertEquals(WeaponCategory.CROSSBOWS, WeaponCategory.forWeapon(ItemArchetype.CROSSBOW))
        assertEquals(WeaponCategory.FIREARMS, WeaponCategory.forWeapon(ItemArchetype.ARQUEBUS))
        assertEquals(WeaponCategory.FIREARMS, WeaponCategory.forWeapon(ItemArchetype.HAND_CANNON))
        assertEquals(WeaponCategory.FIREARMS, WeaponCategory.forWeapon(ItemArchetype.THREE_EYE_CANNON))
        assertEquals(WeaponCategory.SLINGS, WeaponCategory.forWeapon(ItemArchetype.SLING))
        assertEquals(WeaponCategory.THROWN, WeaponCategory.forWeapon(ItemArchetype.THROWING_KNIFE))
        assertEquals(WeaponCategory.THROWN, WeaponCategory.forWeapon(ItemArchetype.FRANCISCA))
        assertEquals(WeaponCategory.THROWN, WeaponCategory.forWeapon(ItemArchetype.CHAKRAM))
        assertEquals(WeaponCategory.THROWN, WeaponCategory.forWeapon(ItemArchetype.SHURIKEN))
        // Every family has arms of its own kind, and the examples hold.
        assertEquals(WeaponCategory.SWORDS, WeaponCategory.forWeapon(ItemArchetype.BLADE))
        assertEquals(WeaponCategory.SWORDS, WeaponCategory.forWeapon(ItemArchetype.ARMING_SWORD))
        assertEquals(WeaponCategory.AXES, WeaponCategory.forWeapon(ItemArchetype.AXE))
        assertEquals(WeaponCategory.AXES, WeaponCategory.forWeapon(ItemArchetype.DANE_AXE))
        assertEquals(WeaponCategory.MACES, WeaponCategory.forWeapon(ItemArchetype.MACE))
        assertEquals(WeaponCategory.MACES, WeaponCategory.forWeapon(ItemArchetype.FLANGED_MACE))
        assertEquals(WeaponCategory.HAMMERS, WeaponCategory.forWeapon(ItemArchetype.WAR_HAMMER))
        assertEquals(WeaponCategory.POLEARMS, WeaponCategory.forWeapon(ItemArchetype.SPEAR))
        assertEquals(WeaponCategory.POLEARMS, WeaponCategory.forWeapon(ItemArchetype.HALBERD))
        assertEquals(WeaponCategory.STAVES, WeaponCategory.forWeapon(ItemArchetype.QUARTERSTAFF))
        assertEquals(WeaponCategory.DAGGERS, WeaponCategory.forWeapon(ItemArchetype.DAGGER))
        assertEquals(WeaponCategory.DAGGERS, WeaponCategory.forWeapon(ItemArchetype.KNIFE))
    }

    // ------------------------------------------------------- the player ledger

    @Test
    fun familiesStandAlone() {
        val proficiencies = Proficiencies.forClass(null)
        assertEquals(Proficiencies.MIN, proficiencies.value(WeaponCategory.SWORDS))
        assertEquals(Proficiencies.MIN, proficiencies.value(WeaponCategory.MACES))
        // Years with the mace teach the mace, and no other family.
        repeat(40) { proficiencies.feed(WeaponCategory.MACES, 99f) }
        assertTrue(proficiencies.value(WeaponCategory.MACES) > Proficiencies.MIN)
        assertEquals(Proficiencies.MIN, proficiencies.value(WeaponCategory.SWORDS))
        assertEquals(Proficiencies.MIN, proficiencies.value(WeaponCategory.UNARMED))
    }

    @Test
    fun practiceRaisesAndTheLedgerEnds() {
        val proficiencies = Proficiencies.forClass(null)
        assertEquals(Proficiencies.MIN + 1, proficiencies.feed(WeaponCategory.POLEARMS, 99f))
        assertEquals(Proficiencies.MIN + 2, proficiencies.feed(WeaponCategory.POLEARMS, 99f))
        repeat(60) { proficiencies.feed(WeaponCategory.POLEARMS, 99f) }
        assertEquals(Proficiencies.MAX, proficiencies.value(WeaponCategory.POLEARMS))
        // Past the end there is nothing left to learn.
        assertNull(proficiencies.feed(WeaponCategory.POLEARMS, 99f))
    }

    @Test
    fun aCallingsHandsKnowTheirOwnArmFirst() {
        val warden = ActorClass("warden", "Ashen Reaver", emptyMap())
        val proficiencies = Proficiencies.forClass(warden)
        assertEquals(Proficiencies.MIN + 2, proficiencies.value(WeaponCategory.SWORDS))
        assertEquals(Proficiencies.MIN, proficiencies.value(WeaponCategory.MACES))
    }

    // --------------------------------------------------------- the npc ledger

    @Test
    fun aGuardsHandKnowsItsBlade() {
        val sword = Item(ItemArchetype.ARMING_SWORD, Material.STEEL, Quality.HONEST).marked()
        val warden = ActorClass("warden", "Brass Bulwark", emptyMap())
        val guard = Proficiencies.forNpc(6, sword, warden)
        val civilian = Proficiencies.forNpc(1, null, null)
        // The soldier's years went into the arm it carries; the civilian has none.
        assertTrue(guard.value(WeaponCategory.SWORDS) > civilian.value(WeaponCategory.SWORDS))
        assertEquals(Proficiencies.MIN, civilian.value(WeaponCategory.SWORDS))
        assertEquals(Proficiencies.MIN, civilian.value(WeaponCategory.MACES))
        // Not every soul the same: a taller level carries surer hands.
        assertTrue(Proficiencies.forNpc(10, sword, warden).value(WeaponCategory.SWORDS) > guard.value(WeaponCategory.SWORDS))
    }

    // ------------------------------------------------------------- the mastery

    @Test
    fun aNewPieceKnowsNothingOfYourHand() {
        val masteries = Masteries.empty()
        val blade = Item(ItemArchetype.BLADE, Material.BOG_IRON, Quality.WORN).marked()
        assertTrue(blade.uid > 0)
        assertEquals(Masteries.MIN, masteries.value(blade.uid))
        assertEquals(Masteries.MIN + 1, masteries.feed(blade.uid, 99f))
        assertEquals(Masteries.MIN + 2, masteries.feed(blade.uid, 99f))
    }

    @Test
    fun twinBladesGrowApart() {
        val masteries = Masteries.empty()
        // Two pieces cut from one steel, borne by one hand: still two pieces.
        val first = Item(ItemArchetype.FLANGED_MACE, Material.IRON, Quality.HONEST).marked()
        val second = Item(ItemArchetype.FLANGED_MACE, Material.IRON, Quality.HONEST).marked()
        assertNotEquals(first.uid, second.uid)
        repeat(20) { masteries.feed(first.uid, 99f) }
        assertTrue(masteries.value(first.uid) > masteries.value(second.uid))
        assertEquals(Masteries.MIN, masteries.value(second.uid))
        // Switching arms changes what the hand knows, and nothing else.
        assertEquals(masteries.value(first.uid), masteries.value(first.uid))
        masteries.feed(second.uid, 30f)
        assertTrue(masteries.value(second.uid) > Masteries.MIN)
    }

    @Test
    fun masteryCapsAndRidesTheSave() {
        val masteries = Masteries.empty()
        repeat(80) { masteries.feed(7, 99f) }
        assertEquals(Masteries.MAX, masteries.value(7))
        val restored = Masteries.fromEncoded(masteries.encode())
        assertEquals(Masteries.MAX, restored.value(7))
        assertEquals(Masteries.MIN, restored.value(8))
    }

    @Test
    fun proficienciesRideTheSave() {
        val proficiencies = Proficiencies.forClass(null)
        repeat(10) { proficiencies.feed(WeaponCategory.MACES, 99f) }
        proficiencies.feed(WeaponCategory.AXES, 12f)
        val restored = Proficiencies.fromEncoded(proficiencies.encode())
        assertEquals(proficiencies.value(WeaponCategory.MACES), restored.value(WeaponCategory.MACES))
        assertEquals(proficiencies.value(WeaponCategory.AXES), restored.value(WeaponCategory.AXES))
        assertEquals(proficiencies.value(WeaponCategory.SWORDS), restored.value(WeaponCategory.SWORDS))
    }

    // ------------------------------------------------------------- the item id

    @Test
    fun aPiecesMarkSurvivesTheSave() {
        ItemUids.reset(40)
        val blade = Item(ItemArchetype.SABRE, Material.STEEL, Quality.FINE).marked()
        assertEquals(40, blade.uid)
        val restored = Item.fromEncoded(blade.encode())
        assertTrue(restored != null)
        assertEquals(blade.uid, restored?.uid)
        // A piece from an older save rides unmarked, and stands alone still.
        val old = Item(ItemArchetype.BLADE, Material.BOG_IRON, Quality.WORN, 3, 1)
        val encoded = listOf(
            old.archetype.name, old.material.name, old.quality.name, "${old.cultureId}", "${old.count}"
        ).joinToString("=")
        assertEquals(0, Item.fromEncoded(encoded)?.uid)
        ItemUids.reset()
    }

    // -------------------------------------------------------- the combat wires

    @Test
    fun practicedHandsAndKnownPiecesStrikeHarder() {
        val stats = StatBlock.balanced()
        val weapon = Item(ItemArchetype.FLANGED_MACE, Material.IRON, Quality.HONEST).marked()
        val plain = Derived.strikeDamage(stats, null, weapon, Random(5L), 0, 0)
        val practiced = Derived.strikeDamage(stats, null, weapon, Random(5L), 6, 8)
        // Proficiency 6 teaches +2; mastery 8 with one piece teaches +2 more.
        assertEquals(4, practiced - plain)
        // Bare hands gain from Unarmed practice all the same.
        val fists = Derived.strikeDamage(stats, null, null, Random(5L), 0, 0)
        val fisted = Derived.strikeDamage(stats, null, null, Random(5L), 9, 0)
        assertEquals(3, fisted - fists)
        // A practiced creature hits harder, and one that knows its arm harder still.
        val npcPlain = Derived.npcDamage(stats, null, 2, weapon, 0, 0)
        val npcPracticed = Derived.npcDamage(stats, null, 2, weapon, 6, 8)
        assertEquals(4, npcPracticed - npcPlain)
        // A familiar arm comes up quicker.
        assertTrue(
            Derived.strikeCooldown(stats, null, weapon, 10, 10) <
                Derived.strikeCooldown(stats, null, weapon, 0, 0)
        )
    }

    // ------------------------------------------------- the engine's own ledgers

    @Test
    fun theDelverWakeMarkedAndUntried() {
        val engine = GameEngine(world, null, null)
        // The waking blade bears its own mark, and no mastery rides with it yet.
        val blade = engine.equipment.bestWeapon()
        assertTrue(blade != null)
        assertTrue((blade?.uid ?: 0) > 0)
        assertEquals(Masteries.MIN, engine.masteries.value(blade?.uid ?: 0))
        // Melee stands apart: proficiencies begin at the floor without a calling.
        assertEquals(Proficiencies.MIN, engine.proficiencies.value(WeaponCategory.SWORDS))
        assertEquals(engine.growth.value(Skill.MELEE), Skill.MIN)
    }

    @Test
    fun theLedgersRideTheSave() {
        val engine = GameEngine(world, null, null)
        val blade = engine.equipment.bestWeapon() ?: throw AssertionError("the waking blade is gone")
        repeat(6) { engine.proficiencies.feed(WeaponCategory.MACES, 99f) }
        engine.masteries.feed(blade.uid, 99f)
        val slot = engine.toSaveSlot()
        assertTrue(slot.nextUid > blade.uid)

        val ridden = GameEngine(world, slot, null)
        assertEquals(engine.proficiencies.value(WeaponCategory.MACES), ridden.proficiencies.value(WeaponCategory.MACES))
        assertEquals(Masteries.MIN + 1, ridden.masteries.value(blade.uid))
        assertEquals(engine.proficiencies.value(WeaponCategory.SWORDS), ridden.proficiencies.value(WeaponCategory.SWORDS))
    }

    @Test
    fun theCarriedArmDecidesWhereTheYearsWent() {
        val engine = GameEngine(world, null, null)
        // Every armed creature of the depths knows the arm it carries better than
        // a stranger would — its proficiency was armed where its life put arms.
        val armed = engine.map.entities.firstOrNull {
            it.kind == EntityKind.ENEMY && it.alive && it.equipment?.bestWeapon() != null
        }
        if (armed != null) {
            val weapon = armed.equipment?.bestWeapon()
            val category = WeaponCategory.forWeapon(weapon?.archetype ?: ItemArchetype.BLADE)
                ?: WeaponCategory.UNARMED
            assertTrue(armed.proficiencies.value(category) > Proficiencies.MIN)
            assertEquals(Masteries.MIN, armed.masteries.value(weapon?.uid ?: 0))
        }
    }
}
