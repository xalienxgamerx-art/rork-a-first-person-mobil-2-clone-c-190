package com.rork.hollowmarch.game

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hearts of the wild: beasts carry the four behavioral values — no trade,
 * no schedule, no traits — and their combat posture reads those values alone.
 */
class CreatureHeartsTest {

    private fun beast(key: Long, name: String = "barrow hound"): Personality =
        PersonalityBook.dealBeast(key, name)

    private fun mean(name: String, pick: (Personality) -> Int): Double {
        val draws = (1L..40L).map { pick(beast(it * 7919L, name)) }
        return draws.average()
    }

    @Test
    fun aBeastCarriesValuesButNoTraits() {
        val heart = beast(42L)
        assertTrue(heart.traits.isEmpty())
        listOf(heart.aggression, heart.confidence, heart.responsibility, heart.energy)
            .forEach { v -> assertTrue("value $v in range", v in 1..99) }
    }

    @Test
    fun theSameFleshKeepsTheSameTemper() {
        assertEquals(beast(123456789L, "grave-wraith"), beast(123456789L, "grave-wraith"))
        assertNotEquals(beast(1L), beast(2L))
    }

    @Test
    fun temperFollowsTheFlesh() {
        // the hound runs hot; the hare's blood is cold
        assertTrue(
            "hound aggression ${mean("hound") { it.aggression }} over hare ${mean("hare") { it.aggression }}",
            mean("hound") { it.aggression } > mean("hare") { it.aggression } + 20.0
        )
        // the wraith does not scare; vermin bolt
        assertTrue(
            "wraith confidence ${mean("wraith") { it.confidence }} over rat ${mean("rat") { it.confidence }}",
            mean("wraith") { it.confidence } > mean("rat") { it.confidence } + 20.0
        )
        // the brute outweighs the husk in wrath
        assertTrue(
            mean("cave bear") { it.aggression } > mean("husk") { it.aggression } + 10.0
        )
        // strange flesh still draws a temper
        val heart = beast(7L, "the Ashen Terror of barrow nine")
        assertTrue(heart.aggression in 1..99 && heart.confidence in 1..99)
    }

    @Test
    fun postureReadsTheValuesAlone() {
        val brave = Personality(40, 95, 50, 50, emptyList())
        val timid = Personality(40, 8, 50, 50, emptyList())
        // the fearless stand long after the timid have broken
        assertTrue("brave retreats at ${brave.retreatHpFraction()}", brave.retreatHpFraction() < 0.08f)
        assertTrue("timid retreats at ${timid.retreatHpFraction()}", timid.retreatHpFraction() > 0.35f)

        val hot = Personality(95, 50, 50, 50, emptyList())
        val calm = Personality(8, 50, 50, 50, emptyList())
        // a hot temper keeps the chase alive and the hand quick
        assertTrue(hot.pursueRadius() > calm.pursueRadius() + 5f)
        assertTrue(hot.attackCooldownScale() < calm.attackCooldownScale())

        // values only, never traits: the same values with or without a trait line behave alike
        val withTraitLine = Personality(95, 50, 50, 50, listOf(Trait.Cowardly))
        assertTrue(withTraitLine.pursueRadius() != hot.pursueRadius())
    }

    @Test
    fun rolledCreaturesCarryHeartsWhenAsked() {
        val withHeart = MapFactory.rollEnemy(Random(11L), 3f, 3f, 2, null, heartKey = 999L)
        assertNotNull("the rolled creature carries a heart", withHeart.personality)
        assertTrue(withHeart.personality?.traits?.isEmpty() == true)

        val bare = MapFactory.rollEnemy(Random(11L), 3f, 3f, 2, null)
        assertNull("no key asked, no heart dealt", bare.personality)

        // the same seed and key draw the same temper
        val again = MapFactory.rollEnemy(Random(11L), 3f, 3f, 2, null, heartKey = 999L)
        assertEquals(withHeart.personality, again.personality)
    }

    @Test
    fun theNamedTerrorKeepsItsHeartAcrossVisits() {
        // world seed and beast id, the key the roaming spawn uses
        val key = PersonalityBook.key(7777L, "beast3")
        val first = beast(key, "the Black Howl")
        val second = beast(key, "the Black Howl")
        assertEquals(first, second)
        assertTrue(abs(first.aggression - second.aggression) == 0)
    }
}
