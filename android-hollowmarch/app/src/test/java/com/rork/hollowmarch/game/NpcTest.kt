package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcTest {

    private val world = WorldGenerator.generate(424242L)
    private val roster = ClassRoster(world)

    @Test
    fun spawnsAreDeterministicForASeedAndLevel() {
        val a = MapFactory.rollEnemy(Random(99L), 3f, 3f, 3, roster)
        val b = MapFactory.rollEnemy(Random(99L), 3f, 3f, 3, roster)
        assertEquals(a.name, b.name)
        assertEquals(a.level, b.level)
        assertEquals(a.klass?.key, b.klass?.key)
        Attr.entries.forEach { attr -> assertEquals(a.stats!![attr], b.stats!![attr]) }
        assertEquals(a.maxHp, b.maxHp)
        assertEquals(a.damage, b.damage)
        Skill.entries.forEach { skill -> assertEquals(a.skills.value(skill), b.skills.value(skill)) }
        assertEquals(a.loot, b.loot)
    }

    @Test
    fun creaturesCarryProvinceClassesAndDerivedNumbers() {
        repeat(30) { i ->
            val foe = MapFactory.rollEnemy(Random(i.toLong() * 31L), 2f, 2f, 2 + i % 4, roster)
            assertNotNull("creature $i has a class", foe.klass)
            assertNotNull("creature $i has stats", foe.stats)
            assertTrue(roster.classes.any { it.key == foe.klass!!.key })
            assertEquals(Derived.npcMaxHp(foe.stats!!, foe.skills, foe.level), foe.maxHp)
            assertEquals(
                Derived.npcDamage(foe.stats!!, foe.skills, foe.level, foe.equipment?.bestWeapon()),
                foe.damage
            )
            assertTrue(foe.level >= 2)
        }
    }

    @Test
    fun humanoidsWakeDressedAndHoundsStayBare() {
        var hound: Entity? = null
        var humanoid: Entity? = null
        repeat(60) { i ->
            val foe = MapFactory.rollEnemy(Random(1000L + i), 2f, 2f, 3, roster)
            if (foe.spriteId == Sprites.HOUND && hound == null) hound = foe
            if (foe.spriteId != Sprites.HOUND && humanoid == null) humanoid = foe
        }
        assertNotNull(hound)
        assertNotNull(humanoid)
        // A hound's mouth is its only arm.
        assertTrue(hound!!.equipment?.isEmpty() != false)
        assertTrue(hound!!.loot.isEmpty())
        // The dead dress in their kit: a weapon in hand, armor on the bones.
        val dressed = humanoid!!
        val gear = dressed.equipment
        assertNotNull(gear)
        assertNotNull("the dead hold a weapon", gear!!.bestWeapon())
        assertTrue(gear.all().isNotEmpty())
        // What they don't wear waits in their pockets.
        assertTrue(gear.all().none { it.second.archetype.slot == ItemSlot.TRINKET })
    }

    @Test
    fun deeperOrOlderSpawnsAreStronger() {
        val weak = MapFactory.rollEnemy(Random(5L), 2f, 2f, 1, roster)
        val strong = MapFactory.rollEnemy(Random(5L), 2f, 2f, 6, roster)
        assertTrue("${strong.maxHp} vs ${weak.maxHp}", strong.maxHp > weak.maxHp)
        assertTrue(strong.damage >= weak.damage)
    }

    @Test
    fun withoutARosterCreaturesFallBackToTheOldFormulas() {
        val foe = MapFactory.rollEnemy(Random(3L), 2f, 2f, 3, null)
        assertNull(foe.klass)
        assertNull(foe.stats)
        assertEquals(16 + 3 * 4, foe.maxHp)
        // But they still wake with the full grammar of skills, unfavored.
        Skill.entries.forEach { skill -> assertEquals(Skill.MIN, foe.skills.value(skill)) }
    }

    @Test
    fun creatureSpeedsStayWalkable() {
        repeat(20) {
            val foe = MapFactory.rollEnemy(Random(it.toLong() * 7L), 2f, 2f, 1 + it % 5, roster)
            assertTrue("${foe.name} speed ${foe.speed}", foe.speed in 0.4f..2.4f)
        }
    }

    @Test
    fun everyCreatureWakesWithAllTwentySevenSkills() {
        val foe = MapFactory.rollEnemy(Random(7L), 2f, 2f, 2, roster)
        assertEquals(Skill.entries.size, 27)
        Skill.entries.forEach { skill ->
            assertTrue("${skill.name} present", foe.skills.value(skill) >= Skill.MIN)
        }
        // A creature's growth starts its level at the encounter's own level.
        assertEquals(foe.level, foe.skills.level)
    }

    @Test
    fun aClassFavorsItsOwnSkillsAtBirth() {
        val warden = roster.byKey("warden") ?: error("the province keeps a warden")
        val growth = Growth.forClass(warden)
        val mightWeight = warden.weights[Attr.MIGHT] ?: 0
        assertTrue("the warden favors Might", mightWeight > 0)
        Skill.entries.filter { it.attr == Attr.MIGHT }.forEach { skill ->
            assertEquals(Skill.MIN + mightWeight, growth.value(skill))
        }
        val classless = Growth.forClass(null)
        assertTrue(growth.value(Skill.entries.first { it.attr == Attr.MIGHT }) > classless.value(Skill.entries.first { it.attr == Attr.MIGHT }))
    }

    @Test
    fun aCreatureLearnsFromFightingYou() {
        val engine = GameEngine(world, null, null)
        val foe = MapFactory.rollEnemy(Random(7L), 2f, 2f, 2, roster)
        val before = foe.skills.value(Skill.MELEE)

        engine.trainNpc(foe, Skill.MELEE, 400f)

        assertTrue(foe.skills.value(Skill.MELEE) > before)
        assertTrue(foe.risesThisLife > 0)
        // Level every third rise, exactly as the delver levels.
        assertTrue(foe.skills.level > foe.level || foe.level >= 2)
        // Its derived numbers follow its skills, weapon and all.
        assertEquals(
            Derived.npcDamage(foe.stats!!, foe.skills, foe.level, foe.equipment?.bestWeapon()),
            foe.damage
        )
        assertEquals(Derived.npcMaxHp(foe.stats!!, foe.skills, foe.level), foe.maxHp)
    }

    @Test
    fun learningHardensTheBody() {
        val engine = GameEngine(world, null, null)
        val foe = MapFactory.rollEnemy(Random(7L), 2f, 2f, 2, roster)
        val beforeHp = foe.maxHp
        val beforeDamage = foe.damage

        engine.trainNpc(foe, Skill.ENDURANCE, 200f)

        assertTrue("${foe.maxHp} after learning", foe.maxHp > beforeHp)
        assertTrue(foe.damage >= beforeDamage)
    }

    @Test
    fun aClasslessCreatureStillLearns() {
        val engine = GameEngine(world, null, null)
        val foe = MapFactory.rollEnemy(Random(3L), 2f, 2f, 2, null)
        val before = foe.skills.value(Skill.MELEE)

        engine.trainNpc(foe, Skill.MELEE, 100f)

        assertTrue(foe.skills.value(Skill.MELEE) > before)
    }

    @Test
    fun theDeadLearnNothing() {
        val engine = GameEngine(world, null, null)
        val foe = MapFactory.rollEnemy(Random(7L), 2f, 2f, 2, roster)
        foe.alive = false
        val before = foe.skills.value(Skill.MELEE)

        engine.trainNpc(foe, Skill.MELEE, 100f)

        assertEquals(before, foe.skills.value(Skill.MELEE))
        assertEquals(0, foe.risesThisLife)
    }
}
