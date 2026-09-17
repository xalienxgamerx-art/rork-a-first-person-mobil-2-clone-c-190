package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelverTest {

    private val world = WorldGenerator.generate(424242L)

    @Test
    fun balancedStartsMatchTheOldProvince() {
        val stats = StatBlock.balanced()
        assertEquals(58, Derived.maxVitality(stats))
        assertEquals(42, Derived.maxFatigue(stats))
        assertEquals(30, Derived.maxMagicka(stats))
    }

    @Test
    fun attributesBendTheirOwnNumbers() {
        val strong = StatBlock.balanced().also {
            it.set(Attr.VIGOR, 12)
            it.set(Attr.INTELLECT, 12)
            it.set(Attr.MIGHT, 12)
        }
        val plain = StatBlock.balanced()
        assertTrue(Derived.maxVitality(strong) > Derived.maxVitality(plain))
        assertTrue(Derived.maxMagicka(strong) > Derived.maxMagicka(plain))
        // Might swings harder on average.
        val strongHits = (1..50).map { Derived.strikeDamage(strong, null, null, Random(it.toLong())) }.average()
        val plainHits = (1..50).map { Derived.strikeDamage(plain, null, null, Random(it.toLong())) }.average()
        assertTrue("Might 12 averaged $strongHits vs base $plainHits", strongHits > plainHits)
    }

    @Test
    fun aWeaponStrikesHarderThanBareKnuckles() {
        val stats = StatBlock.balanced()
        val blade = Item(ItemArchetype.BLADE, Material.VERDIGRIS, Quality.HONEST)
        val armed = (1..50).map { Derived.strikeDamage(stats, null, blade, Random(it.toLong())) }.average()
        val bare = (1..50).map { Derived.strikeDamage(stats, null, null, Random(it.toLong())) }.average()
        assertTrue("armed $armed vs bare $bare", armed > bare)
        // The temper has its say: grave-slate crushes past bone.
        val slate = Item(ItemArchetype.MACE, Material.GRAVE_SLATE, Quality.HONEST)
        val bone = Item(ItemArchetype.MACE, Material.BONE, Quality.HONEST)
        assertTrue(slate.damage() > bone.damage())
    }

    @Test
    fun theWeaponSetsTheRhythmOfTheSwing() {
        val stats = StatBlock.balanced()
        val dagger = Item(ItemArchetype.DAGGER, Material.BONE, Quality.HONEST)
        val mace = Item(ItemArchetype.MACE, Material.GRAVE_SLATE, Quality.HONEST)
        assertTrue(
            "dagger ${Derived.strikeCooldown(stats, null, dagger)} vs mace ${Derived.strikeCooldown(stats, null, mace)}",
            Derived.strikeCooldown(stats, null, dagger) < Derived.strikeCooldown(stats, null, mace)
        )
    }

    @Test
    fun chancesStayWithinHundred() {
        val stats = StatBlock.balanced()
        assertTrue(Derived.critChance(stats) in 0..100)
        assertTrue(Derived.dodgeChance(stats) in 0..100)
        val swift = StatBlock.balanced().also { it.set(Attr.SWIFTNESS, 20) }
        assertTrue(Derived.dodgeChance(swift) <= 20)
        val clumsy = StatBlock.balanced().also { it.set(Attr.SWIFTNESS, 1) }
        assertEquals(0, Derived.dodgeChance(clumsy))
    }

    @Test
    fun pricesFallWithPresence() {
        val plain = Derived.priceFactor(StatBlock.balanced())
        val commanding = StatBlock.balanced().also { it.set(Attr.PRESENCE, 15) }
        assertTrue("Presence ${commanding[Attr.PRESENCE]} paid ${Derived.priceFactor(commanding)} vs $plain", Derived.priceFactor(commanding) < plain)
        assertTrue(Derived.priceFactor(commanding) >= 0.6f)
    }

    @Test
    fun pointBuyMathAddsUp() {
        val allocated = mapOf(Attr.MIGHT to 12, Attr.VIGOR to 10)
        assertEquals(10, CharacterForge.spent(allocated))
        assertEquals(CharacterForge.POOL - 10, CharacterForge.remaining(allocated))
        assertTrue(CharacterForge.canSpend(allocated, Attr.FINESSE))
        assertTrue(!CharacterForge.canRefund(allocated, Attr.FINESSE))
        assertTrue(CharacterForge.canRefund(allocated, Attr.MIGHT))
        val maxed = mapOf(Attr.MIGHT to CharacterForge.CREATION_CAP)
        assertTrue(!CharacterForge.canSpend(maxed, Attr.MIGHT))
    }

    @Test
    fun theDelverSurvivesASaveRoundTrip() {
        val creation = DelverCreation(
            stats = mapOf(Attr.MIGHT to 12, Attr.VIGOR to 10, Attr.FORTUNE to 9),
            classKey = "warden"
        )
        val engine = GameEngine(world, null, creation)
        assertEquals(12, engine.stats[Attr.MIGHT])
        assertEquals("warden", engine.klass?.key)

        val slot = engine.toSaveSlot()
        assertTrue(slot.stats.isNotBlank())
        val reloaded = GameEngine(world, slot)
        Attr.entries.forEach { attr -> assertEquals(engine.stats[attr], reloaded.stats[attr]) }
        assertEquals(engine.klass?.key, reloaded.klass?.key)
        assertEquals(Derived.maxVitality(engine.stats, engine.growth), reloaded.maxVitality)
    }

    @Test
    fun oldSavesWakeBalancedAndClassless() {
        val engine = GameEngine(world, null)
        assertEquals(StatBlock.BASE, engine.stats[Attr.MIGHT])
        assertNull(engine.klass)
        assertEquals(58, engine.maxVitality)
    }
}
