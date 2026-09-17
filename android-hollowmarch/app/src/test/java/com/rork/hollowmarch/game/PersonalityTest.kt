package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The hearts of the province: values and traits dealt once from the seed,
 * bending free hours, days and fights without ever unmaking the trade —
 * and riding the save so a soul is the same soul after loading.
 */
class PersonalityTest {

    private val world = WorldGenerator.generate(424242L)
    private val settlements = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }

    // ------------------------------------------------------------- the catalog

    @Test
    fun catalogIsCompleteAndConsistent() {
        assertTrue("the chronicle knows over a hundred marks", Trait.entries.size >= 100)
        val names = Trait.entries.map { it.name }.toSet()
        assertEquals("no mark is listed twice", names.size, Trait.entries.size)
        // one of each family
        listOf(
            "Friendly", "Hardworking", "Cheerful", "Alcoholic", "Honest",
            "Bookish", "Vindictive", "Romantic", "HotTempered", "FamilyOriented"
        ).forEach { assertTrue("$it is known", it in names) }
        Trait.entries.forEach { t ->
            assertTrue("${t.name} has a weight", t.weight > 0f)
            t.antonyms.forEach { a ->
                assertNotNull("antonym '$a' of ${t.name} resolves", Trait.byName(a))
            }
        }
        // contradictions read both ways
        assertTrue(Trait.Chaste.conflictsWith(Trait.Promiscuous))
        assertTrue(Trait.Promiscuous.conflictsWith(Trait.Chaste))
        assertFalse(Trait.Greedy.conflictsWith(Trait.Friendly))
    }

    // ------------------------------------------------------------ the dealing

    @Test
    fun soulsAreDealtDistinctHearts() {
        var differing = 0
        repeat(60) { i ->
            val key = PersonalityBook.key(7L, "soul-$i")
            val soul = PersonalityBook.deal(key, "Farmer")
            assertEquals("the same key deals the same heart", soul, PersonalityBook.deal(key, "Farmer"))
            assertTrue(soul.aggression in 0..100)
            assertTrue(soul.confidence in 0..100)
            assertTrue(soul.responsibility in 0..100)
            assertTrue(soul.energy in 0..100)
            assertTrue("2-5 traits, got ${soul.traits.size}", soul.traits.size in 2..5)
            assertEquals("no trait repeats", soul.traits.toSet().size, soul.traits.size)
            soul.traits.forEach { a ->
                soul.traits.forEach { b ->
                    if (a != b) assertFalse("'$a' and '$b' contradict", a.conflictsWith(b))
                }
            }
            if (soul != PersonalityBook.deal(PersonalityBook.key(7L, "soul-${i}x"), "Farmer")) differing++
        }
        assertTrue("souls differ from one another ($differing/60)", differing > 45)
    }

    @Test
    fun callingsShapeCharacter() {
        fun mean(role: String, pick: (Personality) -> Int): Float {
            var sum = 0
            repeat(40) { sum += pick(PersonalityBook.deal(PersonalityBook.key(99L, "$role-$it"), role)) }
            return sum / 40f
        }
        assertTrue(
            "guards run hotter than priests (${mean("Guard") { it.aggression }} vs ${mean("Priest") { it.aggression }})",
            mean("Guard") { it.aggression } > mean("Priest") { it.aggression } + 10f
        )
        assertTrue(
            "priests hold duty above thieves",
            mean("Priest") { it.responsibility } > mean("Thief") { it.responsibility } + 15f
        )
        assertTrue(
            "farmers outlast nobles",
            mean("Farmer") { it.energy } > mean("Noble") { it.energy } + 5f
        )
    }

    @Test
    fun theFourAxesStayDistinct() {
        val base = Personality(50, 50, 50, 50)
        // each lever answers only its own axis
        assertEquals(base.pursueRadius(), Personality(50, 90, 50, 50).pursueRadius(), 0.001f)
        assertEquals(base.retreatHpFraction(), Personality(90, 50, 50, 50).retreatHpFraction(), 0.001f)
        assertEquals(base.workMultiplier(), Personality(50, 90, 50, 50).workMultiplier(), 0.001f)
        assertEquals(base.breakMultiplier(), Personality(50, 50, 90, 50).breakMultiplier(), 0.001f)
        // and each axis truly moves its lever
        assertTrue(Personality(90, 50, 50, 50).pursueRadius() > base.pursueRadius())
        assertTrue(Personality(50, 10, 50, 50).retreatHpFraction() > base.retreatHpFraction())
        assertTrue(Personality(50, 50, 50, 90).workMultiplier() > base.workMultiplier())
        assertTrue(Personality(50, 50, 50, 20).breakMultiplier() > base.breakMultiplier())
        // the two faces of a fight: refuses to start, refuses to flee — and its mirror
        val still = Personality(10, 90, 50, 50)
        val raging = Personality(90, 10, 50, 50)
        assertTrue(still.retreatHpFraction() < 0.05f)
        assertTrue(still.retreatHpFraction() < raging.retreatHpFraction())
        assertTrue(still.pursueRadius() < raging.pursueRadius())
    }

    // ------------------------------------------------------------ persistence

    @Test
    fun heartsRideTheSave() {
        val heart = Personality(31, 62, 77, 44, listOf(Trait.Hardworking, Trait.Greedy, Trait.HotTempered))
        assertEquals(heart, Personality.decode(heart.encode()))
        assertNull(Personality.decode(""))

        val state = WorldState(9L)
        val record = NpcRecord(
            id = "9#r#1#0#1", name = "Mera", siteId = 1, floor = 0, homeBuilding = 2,
            powerId = -1, anchorX = 1f, anchorY = 2f, x = 1f, y = 2f,
            role = "Baker", workBuilding = 2, personality = heart
        )
        state.rememberNpc(record)
        val decoded = WorldState.decode(state.encode(), 9L)
        assertEquals(heart, decoded.npc("9#r#1#0#1")?.personality)

        // a record from before souls had hearts still opens, and keeps no heart
        val fields = listOf(
            "9#r#1#0#0", "Old Soul", "1", "0", "2", "-1", "3.5", "4.5", "3.5", "4.5",
            "true", "about its day", "Baker", "-1"
        )
        val old = WorldState.decode("N\u001B" + fields.joinToString("\u001B"), 9L)
        val soul = old.npc("9#r#1#0#0")
        assertNotNull(soul)
        assertEquals("Baker", soul?.role)
        assertNull(soul?.personality)
    }

    @Test
    fun theWorldAdoptsAndKeepsAHeart() {
        val state = WorldState(9L)
        val bare = NpcRecord(
            id = "x", name = "A", siteId = 1, floor = 0, homeBuilding = -1,
            powerId = -1, anchorX = 0f, anchorY = 0f, x = 0f, y = 0f
        )
        state.rememberNpc(bare)
        val heart = Personality(50, 50, 50, 50, listOf(Trait.Bold, Trait.Loyal))
        state.rememberNpc(bare.copy(personality = heart))
        assertEquals("an older record adopts the dealt heart", heart, state.npc("x")?.personality)
    }

    @Test
    fun heartsSurviveLeavingAndReturning() {
        val site = settlements.first()
        val surface = SiteGen.map(world, site, 0, null, null, 1)
        SceneBinder.stamp(world.seed, site.id, 0, surface.entities)
        val state = WorldState(world.seed)
        SceneBinder.remember(state, world, site.id, 0, surface)
        val soul = surface.entities.first { it.resident && it.persistId.isNotBlank() }
        // the world's memory overrules generation on the rebuild
        val custom = Personality(10, 20, 30, 40, listOf(Trait.Bookish, Trait.Stoic))
        state.npc(soul.persistId)?.personality = custom

        val rebuilt = SiteGen.map(world, site, 0, null, null, 1)
        SceneBinder.stamp(world.seed, site.id, 0, rebuilt.entities)
        SceneBinder.restore(state, site.id, 0, rebuilt)
        val again = rebuilt.entities.first { it.resident && it.persistId == soul.persistId }
        assertEquals(custom, again.personality)
    }

    // --------------------------------------------------------- the bent day

    @Test
    fun traitsBendTheDayWithoutBreakingTheTrade() {
        val hard = Personality(50, 50, 80, 80, listOf(Trait.Hardworking, Trait.Workaholic))
        val idle = Personality(50, 50, 20, 30, listOf(Trait.Lazy))
        val key = WorkSchedule.key(7L, "baker")
        val sHard = WorkSchedule.forRole("Baker", key, hard)
        val sIdle = WorkSchedule.forRole("Baker", key, idle)

        fun workHours(s: WorkSchedule) =
            s.blocks.filter { it.duty == Duty.WORK }.sumOf { (it.end - it.start).toDouble() }
        assertTrue("the diligent bake longer", workHours(sHard) > workHours(sIdle) + 1.0)
        assertTrue("the diligent rise first", sHard.wake < sIdle.wake)

        // the trade stands in both days
        listOf(sHard, sIdle).forEach { s ->
            assertTrue("a baker still bakes", s.blocks.any { it.duty == Duty.WORK })
            assertTrue("the night ends in sleep", s.blocks.last().isSleep)
            assertEquals("the day tiles from dawn", 0f, s.blocks.first().start, 0.001f)
            assertEquals("the day tiles to the next dawn", 24f, s.blocks.last().end, 0.01f)
            assertTrue("home before the streets empty", s.wake + s.blocks.last().start <= 20.05f)
            s.blocks.zipWithNext().forEach { (a, b) ->
                assertEquals("blocks never overlap or gap", b.start, a.end, 0.001f)
            }
        }
        // and no heart dealt means the old day, exactly
        val plain = WorkSchedule.forRole("Baker", key)
        val nullHeart = WorkSchedule.forRole("Baker", key, null)
        assertEquals(plain.wake, nullHeart.wake, 0.0001f)
        assertEquals(plain.blocks, nullHeart.blocks)
    }

    @Test
    fun theSameTradeKeepsDifferentLives() {
        val days = (0 until 20).map { i ->
            WorkSchedule.forRole(
                "Farmer", WorkSchedule.key(11L, "farmer-$i"),
                PersonalityBook.deal(PersonalityBook.key(11L, "farmer-$i"), "Farmer")
            )
        }
        assertTrue("farmers rise at different hours", days.map { it.wake }.toSet().size > 1)
        assertTrue(
            "farmers keep different days",
            days.distinctBy { s -> s.blocks.map { it.duty to (it.end - it.start) } }.size > 1
        )
        days.forEach { s ->
            assertTrue("every farmer still farms", s.blocks.any { it.duty == Duty.FIELDS })
            assertTrue("every farmer still sleeps the night", s.blocks.last().isSleep)
        }
    }

    // ------------------------------------------------------------- decisions

    @Test
    fun freeTimeFollowsTheHeart() {
        val drunk = Personality(50, 50, 50, 50, listOf(Trait.Alcoholic))
        val homebody = Personality(50, 50, 50, 30, listOf(Trait.Introverted, Trait.FamilyOriented))
        assertEquals("the drinker takes the evening at the tavern",
            Duty.TAVERN, drunk.freeTimeDecision(18.5f, Duty.HOME, Duty.WORK, Random(3L)))
        assertEquals("the homebody keeps to the household",
            Duty.HOME, homebody.freeTimeDecision(18.5f, Duty.HOME, Duty.WORK, Random(3L)))

        val hard = Personality(50, 50, 80, 85, listOf(Trait.Workaholic))
        assertEquals("the diligent go back to work",
            Duty.WORK, hard.freeTimeDecision(11f, Duty.WELL, Duty.WORK, Random(4L)))

        // the trade's own hours are never second-guessed
        listOf(drunk, homebody, hard).forEach { p ->
            assertEquals(Duty.WORK, p.freeTimeDecision(10f, Duty.WORK, Duty.WORK, Random(5L)))
            assertEquals(Duty.FIELDS, p.freeTimeDecision(10f, Duty.FIELDS, null, Random(6L)))
            assertEquals(Duty.WATER, p.freeTimeDecision(10f, Duty.WATER, null, Random(7L)))
        }

        // combinations speak: greedy and lawful keeps its nose clean,
        // greedy and lawless walks the shadow side
        val lawful = Personality(50, 50, 80, 60, listOf(Trait.Greedy, Trait.LawAbiding))
        val lawless = Personality(50, 50, 15, 60, listOf(Trait.Greedy, Trait.Lawless))
        val lawfulDay = (0 until 14).map { h -> lawful.freeTimeDecision(6f + h, Duty.HOME, Duty.WORK, Random(100L + h)) }
        val lawlessDay = (0 until 14).map { h -> lawless.freeTimeDecision(6f + h, Duty.HOME, Duty.WORK, Random(100L + h)) }
        assertFalse("greed alone is the same; greed with lawless company is not", lawfulDay == lawlessDay)
    }

    @Test
    fun everySoulCarriesAHeart() {
        settlements.take(8).forEach { site ->
            val surface = SiteGen.map(world, site, 0, null, null, 1)
            val residents = surface.entities.filter { it.resident }
            assertTrue("${site.name} keeps folk", residents.isNotEmpty())
            residents.forEach { soul ->
                val p = soul.personality
                assertNotNull("${soul.name} of ${site.name} has no heart", p)
                p?.let {
                    assertTrue("${soul.name} keeps 2-5 traits", it.traits.size in 2..5)
                    assertTrue(it.aggression in 0..100)
                    assertTrue(it.confidence in 0..100)
                    assertTrue(it.responsibility in 0..100)
                    assertTrue(it.energy in 0..100)
                }
            }
        }
    }

    // ----------------------------------------------------------------- combat

    @Test
    fun combatPostureFollowsTheHeart() {
        val coward = Personality(50, 10, 50, 50, listOf(Trait.Cowardly))
        val brave = Personality(50, 95, 50, 50, listOf(Trait.Bold))
        assertTrue("the coward breaks early", coward.retreatHpFraction() > 0.3f)
        assertEquals("the brave stand to the last", 0f, brave.retreatHpFraction(), 0.001f)

        val pacifist = Personality(5, 90, 50, 50, emptyList())
        val brute = Personality(95, 50, 50, 50, listOf(Trait.Vengeful))
        assertTrue("the brute pursues far beyond the pacifist", brute.pursueRadius() > pacifist.pursueRadius() + 3f)
        assertTrue("the brute's hand comes again sooner", brute.attackCooldownScale() < pacifist.attackCooldownScale())
    }
}
