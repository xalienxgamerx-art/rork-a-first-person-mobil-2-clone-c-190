package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatTest {

    private val world = WorldGenerator.generate(424242L)

    // ------------------------------------------------------------ hit chance

    @Test
    fun accuracyRisesWithEveryTeacher() {
        val base = StatBlock(mapOf(Attr.FINESSE to 8))
        val skills = Growth.forClass(null)
        val reference = Derived.meleeAccuracy(base, skills, 4, 2)
        // Finesse always governs.
        assertTrue(
            Derived.meleeAccuracy(StatBlock(mapOf(Attr.FINESSE to 9)), skills, 4, 2) > reference
        )
        // Melee drills the hand.
        val trained = Growth.forClass(null)
        repeat(19) { trained.feed(Skill.MELEE, base, 100f) }
        assertTrue(Derived.meleeAccuracy(base, trained, 4, 2) > reference)
        // The family is known by practice, the piece by companionship.
        assertTrue(Derived.meleeAccuracy(base, skills, 6, 2) > reference)
        assertTrue(Derived.meleeAccuracy(base, skills, 4, 5) > reference)
    }

    @Test
    fun evenTheSurestBladeCanMissAndTheClumsiestCanLand() {
        val master = StatBlock(mapOf(Attr.FINESSE to StatBlock.MAX))
        val masterSkills = Growth.forClass(null)
        repeat(19) { masterSkills.feed(Skill.MELEE, master, 100f) }
        assertTrue(
            "a master can still find air",
            Derived.meleeAccuracy(master, masterSkills, Proficiencies.MAX, Masteries.MAX) < 100
        )
        assertTrue(
            "a novice can still land a blow",
            Derived.meleeAccuracy(
                StatBlock(mapOf(Attr.FINESSE to 1)), Growth.forClass(null),
                Proficiencies.MIN, Masteries.MIN
            ) >= 15
        )
    }

    // ------------------------------------------------------------ dodge

    @Test
    fun dodgeRisesWithSwiftness() {
        val skills = Growth.forClass(null)
        val slow = StatBlock(mapOf(Attr.SWIFTNESS to 6))
        val quick = StatBlock(mapOf(Attr.SWIFTNESS to 16))
        assertTrue(Derived.dodgeChance(quick, skills) > Derived.dodgeChance(slow, skills))
    }

    @Test
    fun armorOrdersItsPenaltiesByTemperAndBulk() {
        fun penalty(archetype: ItemArchetype, material: Material): Int =
            Derived.armorDodgePenalty(listOf(Item(archetype, material, Quality.HONEST, 5)))
        val wrap = penalty(ItemArchetype.WRAP, Material.BONE)
        val gambeson = penalty(ItemArchetype.GAMBESON, Material.BONE)
        val jerkin = penalty(ItemArchetype.JERKIN, Material.BONE)
        val hauberk = penalty(ItemArchetype.HAUBERK, Material.BONE)
        val plate = penalty(ItemArchetype.CUIRASS, Material.BONE)
        assertTrue(wrap < gambeson)
        assertTrue(gambeson < jerkin)
        assertTrue(jerkin < hauberk)
        assertTrue(hauberk < plate)
    }

    @Test
    fun theSameShapeInHeavierStuffDragsMore() {
        fun penalty(material: Material): Int =
            Derived.armorDodgePenalty(listOf(Item(ItemArchetype.HAUBERK, material, Quality.HONEST, 5)))
        assertTrue("lead drags", penalty(Material.LEAD) > penalty(Material.BONE))
    }

    @Test
    fun theSameBodyDodgesDifferentlyDressed() {
        val stats = StatBlock(mapOf(Attr.SWIFTNESS to StatBlock.MAX))
        val skills = Growth.forClass(null)
        repeat(19) { skills.feed(Skill.DEFENSE, stats, 100f) }
        val gambeson = listOf(Item(ItemArchetype.GAMBESON, Material.IRON, Quality.HONEST, 5))
        val plate = listOf(Item(ItemArchetype.CUIRASS, Material.IRON, Quality.SUPERB, 5))
        val light = Derived.dodgeChance(stats, skills, gambeson)
        val heavy = Derived.dodgeChance(stats, skills, plate)
        assertTrue("the padded gambeson leaves the stride quick", light >= 20)
        assertTrue("plate drags the same body near to a crawl", heavy <= 5)
        assertTrue("the tradeoff is real", light - heavy >= 15)
        // even full plate leaves a sliver of a chance to move
        assertTrue("armor never makes a body a statue", heavy > 0)
    }

    @Test
    fun armorNeverTouchesSwiftnessItself() {
        val stats = StatBlock(mapOf(Attr.SWIFTNESS to 14))
        val before = stats[Attr.SWIFTNESS]
        Derived.dodgeChance(
            stats, Growth.forClass(null),
            listOf(Item(ItemArchetype.CUIRASS, Material.IRON, Quality.HONEST, 5))
        )
        assertEquals(before, stats[Attr.SWIFTNESS])
        // and dressing the delver does not rewrite the body either
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val engineBefore = engine.stats[Attr.SWIFTNESS]
        engine.equipment.equip(Item(ItemArchetype.CUIRASS, Material.IRON, Quality.HONEST, 5).marked())
        assertEquals(engineBefore, engine.stats[Attr.SWIFTNESS])
    }

    @Test
    fun armorHasNoSayInWhetherABlowLands() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val accuracy = Derived.meleeAccuracy(engine.stats, engine.growth, 4, 2)
        engine.equipment.equip(Item(ItemArchetype.CUIRASS, Material.IRON, Quality.HONEST, 5).marked())
        assertEquals(accuracy, Derived.meleeAccuracy(engine.stats, engine.growth, 4, 2))
    }

    // ------------------------------------------------------------ proficiency wiring

    @Test
    fun everyFamilyAnswersItsOwnLedger() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val cases = listOf(
            ItemArchetype.ARMING_SWORD to WeaponCategory.SWORDS,
            ItemArchetype.BATTLE_AXE to WeaponCategory.AXES,
            ItemArchetype.FLANGED_MACE to WeaponCategory.MACES,
            ItemArchetype.WAR_HAMMER to WeaponCategory.HAMMERS,
            ItemArchetype.SPEAR to WeaponCategory.POLEARMS,
            ItemArchetype.QUARTERSTAFF to WeaponCategory.STAVES,
            ItemArchetype.DAGGER to WeaponCategory.DAGGERS
        )
        val foe = placeFoe(engine)
        cases.forEach { (archetype, category) ->
            engine.equipment.equip(Item(archetype, Material.IRON, Quality.HONEST, 5).marked(), prefer = Hand.RIGHT)
            strikeOnce(engine)
            val record = engine.combatTrail.last()
            assertEquals("$archetype answers $category", category, record.category)
            assertEquals(engine.proficiencies.value(category), record.proficiency)
        }
        // and bare hands answer the Unarmed ledger
        engine.equipment.unequip(WearSlot.RIGHT_HAND)
        strikeOnce(engine)
        val record = engine.combatTrail.last()
        assertEquals(WeaponCategory.UNARMED, record.category)
        assertEquals(engine.proficiencies.value(WeaponCategory.UNARMED), record.proficiency)
    }

    // ------------------------------------------------------------ engine combat

    @Test
    fun aSwingCanFindAirAndCanFindMark() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val blade = engine.equipment.bestWeapon()
        val foe = placeFoe(engine)
        strikeTimes(engine, 20, foe)
        val trail = engine.combatTrail.toList()
        assertEquals(20, trail.size)
        assertTrue("some blows land", trail.any { it.outcome == StrikeOutcome.HIT })
        assertTrue("some blows find air", trail.any { it.outcome == StrikeOutcome.MISSED })
        // a missed blow deals nothing and reaches no armor
        trail.filter { it.outcome != StrikeOutcome.HIT }.forEach {
            assertEquals(0, it.damage)
            assertEquals(0, it.armorReduction)
        }
        // the flesh keeps the ledger's sum: a landed blow costs what the ledger
        // says, and the defender's own training may add a breath, never take one
        val dealt = trail.filter { it.outcome == StrikeOutcome.HIT }.sumOf { it.damage }
        assertTrue("blows land", dealt > 0)
        assertTrue("no blow cost more than its ledger line", foe.hp >= 9999 - dealt)
        assertTrue("the flesh remembers", foe.hp < 9999)
        // the family and the piece both grew, even off the misses
        assertTrue(engine.proficiencies.value(WeaponCategory.SWORDS) > Proficiencies.MIN)
        assertTrue(engine.masteries.value(blade?.uid ?: 0) > Masteries.MIN)
    }

    @Test
    fun aBodyCanMoveFasterThanTheEdge() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        // a dancer of the deep: swift, schooled in Defense, wearing next to nothing
        val foe = placeFoe(engine, mapOf(Attr.SWIFTNESS to StatBlock.MAX))
        repeat(19) { engine.trainNpc(foe, Skill.DEFENSE, 200f) }
        strikeTimes(engine, 40, foe)
        val dodged = engine.combatTrail.filter { it.outcome == StrikeOutcome.DODGED }
        assertTrue("some blows are slipped aside", dodged.isNotEmpty())
        dodged.forEach { resolution ->
            assertEquals(0, resolution.damage)
            assertEquals(0, resolution.armorReduction)
            assertTrue(resolution.dodgeRoll < resolution.dodgeChance)
            assertTrue(resolution.defenderSwiftness == StatBlock.MAX)
        }
        // a dodging body learns its footwork
        assertTrue(foe.skills.value(Skill.DEFENSE) > Skill.MIN)
    }

    @Test
    fun aCreaturesBlowsAskItsOwnLedgers() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        engine.vitality = 9999
        val mace = Item(ItemArchetype.FLANGED_MACE, Material.IRON, Quality.HONEST, 5).marked()
        val foe = placeFoe(engine, mapOf(Attr.FINESSE to 18))
        foe.equipment = Equipment.empty().also { it.equip(mace) }
        foe.proficiencies = Proficiencies.forClass(null)
        foe.masteries = Masteries.empty()
        foe.proficiencies.feed(WeaponCategory.MACES, 500f)
        val foeStats = foe.stats ?: StatBlock.balanced()
        repeat(10) { foe.skills.feed(Skill.MELEE, foeStats, 100f) }
        repeat(50) {
            foe.detection = Detection.AWARE
            foe.attackCooldown = 0f
            engine.update(0.05f)
        }
        val mine = engine.combatTrail.filter { it.attacker == foe.name }
        assertTrue("the creature's blows are recorded", mine.isNotEmpty())
        assertTrue(mine.all { it.category == WeaponCategory.MACES })
        assertTrue(mine.all { it.proficiency > Proficiencies.MIN })
        assertTrue("some land", mine.any { it.outcome == StrikeOutcome.HIT })
        assertTrue("some find air", mine.any { it.outcome == StrikeOutcome.MISSED })
        // its ledgers grew through its own swings
        assertTrue(foe.proficiencies.value(WeaponCategory.MACES) > Proficiencies.MIN)
        assertTrue("the piece grows familiar", foe.masteries.value(mace.uid) > Masteries.MIN)
    }

    @Test
    fun armorTurnsOnlyBlowsThatArrive() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val foe = placeFoe(engine)
        val plate = Item(ItemArchetype.CUIRASS, Material.IRON, Quality.SUPERB, 5).marked()
        foe.equipment = Equipment.empty().also { it.equip(plate) }
        val soak = Derived.armorSoak(listOf(plate), DamageType.SHARP)
        assertTrue(soak > 0)
        strikeTimes(engine, 40, foe)
        assertTrue(engine.combatTrail.isNotEmpty())
        engine.combatTrail.forEach { resolution ->
            when (resolution.outcome) {
                StrikeOutcome.HIT -> {
                    assertTrue(resolution.damage >= 1)
                    assertEquals(soak, resolution.armorReduction)
                }
                else -> {
                    assertEquals(0, resolution.damage)
                    assertEquals(0, resolution.armorReduction)
                }
            }
            // armor shaped the dodge ledger, never the hit ledger
            assertTrue(resolution.armorDodgePenalty > 0)
        }
    }

    @Test
    fun theLedgerStaysTrimmed() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val foe = placeFoe(engine)
        strikeTimes(engine, 60, foe)
        assertEquals(24, engine.combatTrail.size)
    }

    // ------------------------------------------------------------ helpers

    /** A standing target in the delver's grasp, close enough for either hand to ask. */
    private fun placeFoe(engine: GameEngine, stats: Map<Attr, Int> = emptyMap()): Entity {
        val foe = Entity(
            x = engine.camera.x + 1.0f, y = engine.camera.y, spriteId = 0,
            kind = EntityKind.ENEMY, height = 1.7f, name = "test-foe",
            hp = 9999, maxHp = 9999, damage = 0, speed = 1f, level = 3,
            stats = StatBlock(stats)
        )
        engine.map.entities += foe
        engine.camera.angle = 0f
        return foe
    }

    private fun strikeOnce(engine: GameEngine) {
        engine.strikeCooldown = 0f
        engine.fatigue = engine.maxFatigue
        engine.strike()
    }

    private fun strikeTimes(engine: GameEngine, times: Int, foe: Entity) {
        repeat(times) {
            strikeOnce(engine)
        }
    }
}
