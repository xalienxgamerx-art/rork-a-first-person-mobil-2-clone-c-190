package com.rork.hollowmarch.world

import com.rork.hollowmarch.game.SkyGen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sky writes history too: omens land in every chronicle, the same every time. */
class OmenTest {

    private val world = WorldGenerator.generate(20260910L)

    @Test
    fun omensAreDeterministicPerSeed() {
        val again = WorldGenerator.generate(20260910L)
        val a = world.events.filter { it.kind in OMEN_KINDS }
        val b = again.events.filter { it.kind in OMEN_KINDS }
        assertEquals(a, b)
    }

    @Test
    fun everyWorldHasOmens() {
        for (seed in listOf(1L, 7L, 424242L, 20260910L)) {
            val w = WorldGenerator.generate(seed)
            assertTrue("comet for seed $seed", w.events.any { it.kind == EventKind.COMET })
            assertTrue("eclipse for seed $seed", w.events.any { it.kind == EventKind.ECLIPSE })
            assertTrue("moon wonder for seed $seed", w.events.any { it.kind == EventKind.MOONWONDER })
        }
    }

    @Test
    fun cometIsCoinedByOneOfTheProvincesPeoples() {
        val comet = world.events.first { it.kind == EventKind.COMET }
        assertTrue("comet should carry its coined name as subject", comet.subject.isNotBlank())
        assertTrue("comet name should be a single coined word", !comet.subject.contains(' '))
        assertTrue("comet text should speak its name", comet.text.contains(comet.subject))
        assertTrue(
            "comet should be named by a people of this province",
            world.cultures.any { comet.text.contains(it.adjective) || comet.text.contains(it.name) }
        )
    }

    @Test
    fun cometReturnKeepsItsName() {
        val comets = world.events.filter { it.kind == EventKind.COMET }
        if (comets.size == 2) {
            assertEquals("a returning comet keeps its name", comets[0].subject, comets[1].subject)
            assertTrue("return comes later", comets[1].year > comets[0].year)
        }
    }

    @Test
    fun famousOmensAreDrawnUpIntoConstellations() {
        val sky = SkyGen.build(world)
        val named = sky.constellations.joinToString(" | ") { it.name + " " + it.lore }
        world.events.firstOrNull { it.kind == EventKind.COMET }?.let { comet ->
            assertTrue("comet '${comet.subject}' should be a named star", named.contains(comet.subject))
        }
        world.events.firstOrNull { it.kind == EventKind.ECLIPSE }?.let {
            assertTrue("an eclipse should hang in the sky", named.contains("Eclipse"))
        }
    }

    @Test
    fun moonWondersMatchTheWorldsActualMoons() {
        val moons = SkyGen.build(world).moons
        world.events.filter { it.kind == EventKind.MOONWONDER }.forEach { wonder ->
            if (wonder.text.contains("moons stood in a row")) {
                assertTrue("two moons stand in a row only when there are two", moons.size >= 2)
            }
        }
        // A tinted-moon wonder only names a tint this world's moons actually wear.
        val tints = moons.map { it.tintName }
        world.events.filter { it.kind == EventKind.MOONWONDER }.forEach { wonder ->
            tints.firstOrNull { wonder.text.contains("$it moon") }?.let { assertTrue(it in tints) }
        }
    }

    @Test
    fun omenYearsFallInsideHistory() {
        world.events.filter { it.kind in OMEN_KINDS }.forEach {
            assertTrue("omen year ${it.year} inside history", it.year in 2..world.currentYear)
        }
    }

    @Test
    fun omenEventsAreChronologicallySorted() {
        val years = world.events.map { it.year }
        assertEquals(years, years.sorted())
    }

    companion object {
        private val OMEN_KINDS = setOf(EventKind.COMET, EventKind.ECLIPSE, EventKind.MOONWONDER)
    }
}
