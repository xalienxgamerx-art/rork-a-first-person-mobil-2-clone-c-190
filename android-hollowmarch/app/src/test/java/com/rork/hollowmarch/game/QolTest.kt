package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.ChronicleEvent
import com.rork.hollowmarch.world.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Small honest arithmetic: the satchel's order, the wait till dawn, the loose ear. */
class QolTest {

    @Test
    fun tillDawnArithmetic() {
        assertEquals("11pm waits seven hours", 7, hoursTillDawn(23, 0))
        assertEquals("half past five waits one", 1, hoursTillDawn(5, 30))
        assertEquals("dawn itself waits the whole day", 24, hoursTillDawn(6, 0))
        assertEquals("seven in the morning waits twenty-three", 23, hoursTillDawn(7, 0))
        assertEquals("noon waits eighteen", 18, hoursTillDawn(12, 0))
        assertEquals("midnight waits six", 6, hoursTillDawn(0, 0))
        assertEquals(4, hoursTillDawn(2, 15))
    }

    @Test
    fun theSatchelSorts() {
        val honestBlade = Item(ItemArchetype.BLADE, Material.BOG_IRON, Quality.HONEST)
        val remedies = Item(ItemArchetype.REMEDY, Material.VERDIGRIS, count = 3)
        val legendaryBlade = Item(ItemArchetype.BLADE, Material.BOG_IRON, Quality.LEGENDARY)
        val items = listOf(honestBlade, remedies, legendaryBlade)

        assertEquals("as carried keeps the pack's own order", items, sortSatchel(items, SatchelSort.CARRIED))

        val light = sortSatchel(items, SatchelSort.LIGHT)
        assertTrue("lightest first", light.first().weight() <= light.last().weight())

        val worth = sortSatchel(items, SatchelSort.WORTH)
        assertTrue("worth most first", worth.first().value() >= worth.last().value())

        val fine = sortSatchel(items, SatchelSort.FINE)
        assertEquals("finest first", Quality.LEGENDARY, fine.first().quality)
        assertEquals("shoddiest last", Quality.HONEST, fine.last().quality)
    }

    @Test
    fun theSearchForgives() {
        assertTrue("a blank ear hears everything", searchMatches("anything at all", ""))
        assertTrue("loose case", searchMatches("The comet Korthan crossed", "korthan"))
        assertTrue("trimmed", searchMatches("wells went still", "  wells  "))
        assertFalse(searchMatches("The sun went black", "moon"))
    }

    @Test
    fun chronicleKindsOfYear() {
        val comet = ChronicleEvent(50, EventKind.COMET, "The comet came.", subject = "Korthan")
        val war = ChronicleEvent(80, EventKind.WAR, "Two powers went to war.")

        assertTrue(chronicleMatches(comet, "", ChronicleFilter.ALL))
        assertTrue(chronicleMatches(comet, "", ChronicleFilter.OMENS))
        assertFalse(chronicleMatches(comet, "", ChronicleFilter.BLOOD))
        assertTrue(chronicleMatches(war, "", ChronicleFilter.BLOOD))
        assertTrue("the subject answers the search too", chronicleMatches(comet, "korthan", ChronicleFilter.OMENS))
        assertTrue(chronicleMatches(comet, "comet", ChronicleFilter.ALL))

        // every kind the chronicle keeps belongs to some page
        EventKind.entries.forEach { kind ->
            assertTrue(
                "$kind has a page of its kind",
                ChronicleFilter.entries.any { it.kinds.contains(kind) }
            )
        }
    }

    @Test
    fun walkedAgoMarks() {
        assertEquals("walked today", walkedAgoLabel(0))
        assertEquals("walked yesterday", walkedAgoLabel(1))
        assertEquals("walked 9 days since", walkedAgoLabel(9))
    }
}
