package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributesTest {

    private val world = WorldGenerator.generate(424242L)

    // ------------------------------------------------------------- attributes

    @Test
    fun theEightAttributesCarryTheirGovernance() {
        assertEquals(8, Attr.entries.size)
        assertEquals("Might", Attr.MIGHT.label)
        assertEquals("social influence, personality, command", Attr.PRESENCE.governs)
    }

    @Test
    fun statBlocksStartBalancedAndStayClamped() {
        val stats = StatBlock.balanced()
        Attr.entries.forEach { attr -> assertEquals(StatBlock.BASE, stats[attr]) }
        stats.set(Attr.MIGHT, 100)
        assertEquals(StatBlock.MAX, stats[Attr.MIGHT])
        stats.set(Attr.VIGOR, -50)
        assertEquals(1, stats[Attr.VIGOR])
        stats.adjust(Attr.SWIFTNESS, 3)
        assertEquals(StatBlock.BASE + 3, stats[Attr.SWIFTNESS])
        // Might hit the cap (+14 over base), Vigor fell to 1 (-5), Swiftness rose 3.
        assertEquals(StatBlock.BASE * 8 + 14 - 5 + 3, stats.total())
    }

    @Test
    fun statBlocksRoundTripThroughTheirCompactForm() {
        val stats = StatBlock.balanced()
        stats.adjust(Attr.MIGHT, 4)
        stats.adjust(Attr.FORTUNE, -2)
        stats.set(Attr.PRESENCE, 17)
        val restored = StatBlock.fromEncoded(stats.encode())
        assertTrue(restored != null)
        Attr.entries.forEach { attr -> assertEquals(stats[attr], restored!![attr]) }
        assertNull(StatBlock.fromEncoded(null))
        assertNull(StatBlock.fromEncoded(""))
        assertNull(StatBlock.fromEncoded("nonsense\u001E\u001D"))
        // A partly mangled string keeps what it can and clamps the rest.
        val partial = StatBlock.fromEncoded("MIGHT=12\u001EGARBAGE")
        assertEquals(12, partial!![Attr.MIGHT])
        assertEquals(StatBlock.BASE, partial[Attr.VIGOR])
    }

    // ---------------------------------------------------------------- classes

    @Test
    fun theSameSeedNamesTheSameRoster() {
        val a = ClassRoster(world).classes
        val b = ClassRoster(world).classes
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsNameClassesDifferently() {
        val other = WorldGenerator.generate(999L)
        val rosterA = ClassRoster(world).classes.map { it.name }
        val rosterB = ClassRoster(other).classes.map { it.name }
        assertNotEquals(rosterA, rosterB)
    }

    @Test
    fun theRosterHoldsEightDistinctNamedArchetypes() {
        val roster = ClassRoster(world)
        assertEquals(8, roster.classes.size)
        assertEquals(8, roster.classes.map { it.name }.distinct().size)
        assertEquals(8, roster.classes.map { it.key }.distinct().size)
        assertEquals("Reaver-shaped names fit their weights", "warden", roster.byKey("warden")?.key)
        assertTrue(roster.byKey("warden")!!.weights.getValue(Attr.MIGHT) >= 3)
        assertNull(roster.byKey("no-such-class"))
    }

    // -------------------------------------------------------------- npc rolls

    @Test
    fun npcStatRollsAreDeterministicAndInBounds() {
        val weights = ClassRoster(world).byKey("warden")!!.weights
        val a = rollNpcStats(3, weights, Random(4242L))
        val b = rollNpcStats(3, weights, Random(4242L))
        Attr.entries.forEach { attr -> assertEquals(a[attr], b[attr]) }
        Attr.entries.forEach { attr -> assertTrue(a[attr] in 1..18) }
    }

    @Test
    fun higherLevelCreaturesRollStrongerOnAverage() {
        val weights = ClassRoster(world).byKey("warden")!!.weights
        val low = averageStat(1, weights, Attr.MIGHT)
        val high = averageStat(6, weights, Attr.MIGHT)
        assertTrue("level 6 rolls Might $high vs level 1's $low", high > low)
    }

    @Test
    fun classWeightsBendTheRolls() {
        val warden = ClassRoster(world).byKey("warden")!!.weights
        val might = averageStat(4, warden, Attr.MIGHT)
        val fortune = averageStat(4, warden, Attr.FORTUNE)
        assertTrue("a warden rolls Might $might over Fortune $fortune", might > fortune)
    }
}
