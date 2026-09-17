package com.rork.hollowmarch.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthTest {

    private val warden = ActorClass(
        "warden",
        "Barrow-Sworn Reaver",
        mapOf(Attr.MIGHT to 3, Attr.VIGOR to 3, Attr.FINESSE to 1)
    )

    @Test
    fun classFavoredSkillsStartHigher() {
        val trained = Growth.forClass(warden)
        assertEquals(4, trained.value(Skill.MELEE))
        assertEquals(4, trained.value(Skill.ENDURANCE))
        assertEquals(2, trained.value(Skill.MARKSMANSHIP))
        assertEquals(1, trained.value(Skill.PERSUASION))
        val plain = Growth.forClass(null)
        assertEquals(1, plain.value(Skill.MELEE))
    }

    @Test
    fun deedsFeedSkillsAndARaisedBarRaisesTheSkill() {
        val growth = Growth.forClass(null)
        val stats = StatBlock.balanced()
        assertNull(growth.feed(Skill.MELEE, stats, growth.skillThreshold(1) - 1f))
        assertEquals(1, growth.value(Skill.MELEE))
        assertNull(growth.feed(Skill.MELEE, stats, 0f))
        val rise = growth.feed(Skill.MELEE, stats, 3f)
        assertEquals(Skill.MELEE, rise?.skill)
        assertEquals(2, growth.value(Skill.MELEE))
        // The governing attribute's bar got a measure of learning, not a rise yet.
        assertNull(rise?.attrRise)
        assertTrue(growth.attrProgressFraction(Attr.MIGHT, stats[Attr.MIGHT]) > 0f)
    }

    @Test
    fun skillRisesHardenTheGoverningAttribute() {
        val growth = Growth.forClass(null)
        val stats = StatBlock.balanced()
        // Twelve Melee rises pour 24 learning into Might, whose bar starts at 24.
        repeat(12) {
            growth.feed(Skill.MELEE, stats, growth.skillThreshold(growth.value(Skill.MELEE)))
        }
        assertEquals(StatBlock.BASE + 1, stats[Attr.MIGHT])
        assertEquals(0f, growth.progressFraction(Skill.MELEE), 0.001f)
    }

    @Test
    fun theBarsHardenAsTheValuesClimb() {
        val growth = Growth.forClass(null)
        assertTrue(growth.skillThreshold(1) < growth.skillThreshold(10))
        assertTrue(growth.skillThreshold(20) > growth.skillThreshold(10))
        assertTrue(growth.attrThreshold(StatBlock.BASE) < growth.attrThreshold(StatBlock.BASE + 6))
        assertTrue(growth.attrThreshold(20) > growth.attrThreshold(10))
    }

    @Test
    fun everyThirdSkillRiseGrantsALevel() {
        val growth = Growth.forClass(null)
        val stats = StatBlock.balanced()
        listOf(Skill.MELEE, Skill.DEFENSE, Skill.ATHLETICS).forEach { skill ->
            growth.feed(skill, stats, growth.skillThreshold(growth.value(skill)))
        }
        // Three rises make a level, and nothing waits to be spent.
        assertEquals(2, growth.level)
        // The fourth rise must wait for the third after it.
        growth.feed(Skill.STEALTH, stats, growth.skillThreshold(growth.value(Skill.STEALTH)))
        assertEquals(2, growth.level)
    }

    @Test
    fun masteredSkillsRiseNoMore() {
        val growth = Growth.fromEncoded("1\u001F0\u001F0\u001FMELEE=20\u001F")
        val stats = StatBlock.balanced()
        assertNull(growth.feed(Skill.MELEE, stats, 999f))
        assertEquals(20, growth.value(Skill.MELEE))
    }

    @Test
    fun growthSurvivesASaveRoundTrip() {
        val growth = Growth.forClass(warden)
        val stats = StatBlock.balanced()
        growth.feed(Skill.MELEE, stats, growth.skillThreshold(4) * 0.8f)
        growth.feed(Skill.DEFENSE, stats, growth.skillThreshold(4))
        growth.feed(Skill.NAVIGATION, stats, growth.skillThreshold(1))

        val restored = Growth.fromEncoded(growth.encode(), warden)
        assertEquals(growth.level, restored.level)
        Skill.entries.forEach { skill ->
            assertEquals(skill.name, growth.value(skill), restored.value(skill))
        }
        Attr.entries.forEach { attr ->
            assertEquals(
                attr.name,
                growth.attrProgressFraction(attr, stats[attr]),
                restored.attrProgressFraction(attr, stats[attr]),
                0.001f
            )
        }
    }

    @Test
    fun aMangledSaveWakesFresh() {
        val fresh = Growth.fromEncoded(null)
        assertEquals(1, fresh.level)
        val blank = Growth.fromEncoded("")
        assertEquals(1, blank.level)
        val garbage = Growth.fromEncoded("nonsense\u001F\u001E\u001D")
        assertEquals(1, garbage.level)
    }

    @Test
    fun oldAttributeSavesKeepTheirLevelAndWakeSkillsFresh() {
        val growth = Growth.fromEncoded("3\u001F1\u001F4\u001FMIGHT=7.5\u001EVIGOR=2", warden)
        assertEquals(3, growth.level)
        // Old attribute bars don't leak into skills; class-favored starts apply.
        assertEquals(4, growth.value(Skill.MELEE))
        assertEquals(1, growth.value(Skill.PERSUASION))
        assertEquals(0f, growth.attrProgressFraction(Attr.MIGHT, StatBlock.BASE), 0.0001f)
    }

    @Test
    fun skillTrainingBendsTheDerivedNumbers() {
        val stats = StatBlock.balanced()
        val trained = Growth.fromEncoded(
            "1\u001F0\u001F0\u001FMELEE=16\u001EENDURANCE=20\u001EDEFENSE=12\u001ETRADE=10\u001ELORE=12\u001F"
        )
        assertEquals(
            Derived.strikeDamage(stats, null, null, Random(7)) + 5,
            Derived.strikeDamage(stats, trained, null, Random(7))
        )
        assertTrue(Derived.maxVitality(stats, trained) > Derived.maxVitality(stats, null))
        assertTrue(Derived.maxFatigue(stats, trained) > Derived.maxFatigue(stats, null))
        assertTrue(Derived.maxMagicka(stats, trained) > Derived.maxMagicka(stats, null))
        assertTrue(Derived.dodgeChance(stats, trained) > Derived.dodgeChance(stats, null))
        assertTrue(Derived.priceFactor(stats, trained) < Derived.priceFactor(stats, null))
        assertTrue(Derived.strikeCooldown(stats, trained) < Derived.strikeCooldown(stats, null))
        assertTrue(Derived.torchDrain(stats, trained) < Derived.torchDrain(stats, null))
        assertTrue(Derived.mendPotency(stats, trained) > Derived.mendPotency(stats, null))
        assertTrue(Derived.moveSpeed(stats, trained) > Derived.moveSpeed(stats, null))
    }
}
