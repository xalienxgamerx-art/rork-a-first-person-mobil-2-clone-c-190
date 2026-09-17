package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** The systems talk to each other: weather bends the road, the road reads your name. */
class WiringTest {

    private val world = WorldGenerator.generate(424242L)

    // ------------------------------------------------------------ weather & the road

    @Test
    fun rainBendsThePace() {
        val (speed, cost) = rainPacing(0.85f)
        assertTrue("rain slows the walk", speed < 1f)
        assertTrue("rain costs fatigue", cost > 1f)
        // a dry or merely grey sky leaves the road as it stands
        assertEquals(1f to 1f, rainPacing(0f))
        assertEquals(1f to 1f, rainPacing(0.25f))
    }

    @Test
    fun theBearingsHoursSwellWhenItRains() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        assertTrue("the climb out wakes under the open sky", engine.outdoor)
        val clearHours = engine.bearings().maxOf { it.hours }
        // push the clock into a rain day and the same walks ask more hours
        val rainyDay = (1..400).first { Weather.at(world.seed, it, 0.5f, false).rain > 0.3f }
        engine.minutes = (rainyDay - 1) * 1440f + 720f
        assertTrue(engine.weather.rain > 0.25f)
        val wetHours = engine.bearings().maxOf { it.hours }
        assertTrue("rain makes every walk longer: $wetHours vs $clearHours", wetHours > clearHours)
        assertTrue(engine.weatherWalkNote().contains("Rain slows the road"))
    }

    @Test
    fun restPaysLessInTheRain() {
        val (dryVitality, dryFatigue) = restRecovery(false)
        val (wetVitality, wetFatigue) = restRecovery(true)
        assertTrue(wetVitality < dryVitality)
        assertTrue(wetFatigue < dryFatigue)
    }

    @Test
    fun theEngineRestsThinUnderARainySky() {
        val rainyDay = (1..400).first { Weather.at(world.seed, it, 0.5f, false).rain > 0.3f }
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        engine.minutes = (rainyDay - 1) * 1440f + 720f
        engine.fatigue = 5
        engine.vitality = 5
        engine.rest(8)
        // the thin wet rate pays 5 fatigue an hour; an ambush halves the hours; the cap holds
        val fullHours = minOf(45, engine.maxFatigue)
        val halfHours = minOf(25, engine.maxFatigue)
        assertTrue(
            "rest under rain pays the thin rate, got ${engine.fatigue}",
            engine.fatigue == fullHours || engine.fatigue == halfHours
        )
    }

    // ------------------------------------------------------------ sky & the HUD

    @Test
    fun theMoonsNameThemselves() {
        assertEquals("new", moonPhaseWord(0.05f, true))
        assertEquals("full", moonPhaseWord(0.95f, false))
        assertEquals("waxing crescent", moonPhaseWord(0.30f, true))
        assertEquals("waning gibbous", moonPhaseWord(0.75f, false))

        // and the engine names the moon actually standing in the world's sky
        val engine = GameEngine(world, null, null)
        val moon = SkyGen.moonsFor(world.seed).first()
        val t = ((0.5f - moon.offset) % 1f + 1f) % 1f
        engine.minutes = t * 1440f
        val phase = 2f * Math.PI.toFloat() * (1f + t) / moon.periodDays + moon.phase0
        val illum = 0.5f + 0.5f * sin(phase)
        val expected = "${moonPhaseWord(illum, cos(phase) > 0f)} ${moon.tintName} moon"
        assertEquals(expected, engine.moonWord())
    }

    @Test
    fun theStatusLineReadsTheSky() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        engine.minutes = 720f
        assertTrue(engine.outdoor)
        assertEquals(engine.weather.kind.name.lowercase(), engine.skyWord().substringBefore(" · "))
    }

    // ------------------------------------------------------------ reputation & the road's news

    @Test
    fun travelersGradeTheirNewsByYourName() {
        val site = world.sites.first { it.isSettlement && !it.ruined }

        // blood-owed: the road clams up, stale and grudging, and gives no gift
        val owed = GameEngine(world, null, null).apply { climbToOpenGround() }
        owed.reputation.adjust(Layer.SETTLEMENT, site.id, -80, "test")
        owed.currentSiteId = site.id
        val remediesBefore = owed.inventory.all.count { it.archetype == ItemArchetype.REMEDY }
        spawnAndMeet(owed)
        val grudge = owed.rumors.first()
        assertTrue("grudging news is stale", grudge.daysOld >= 4)
        assertTrue(grudge.text.contains("no talk"))
        assertTrue("no gift for the unwelcome", owed.brass == 0)
        assertEquals(
            "no remedy pressed on the unwelcome",
            remediesBefore,
            owed.inventory.all.count { it.archetype == ItemArchetype.REMEDY }
        )

        // fondly regarded: richer, fresher news, and something for the walk
        val fond = GameEngine(world, null, null).apply { climbToOpenGround() }
        fond.reputation.adjust(Layer.SETTLEMENT, site.id, 80, "test")
        fond.currentSiteId = site.id
        spawnAndMeet(fond)
        assertEquals("fresh news for the fondly regarded", 0, fond.rumors.first().daysOld)
        assertTrue(
            "a gift rides with good standing",
            fond.brass > 0 || fond.inventory.all.any { it.archetype == ItemArchetype.REMEDY }
        )
    }

    private fun spawnAndMeet(engine: GameEngine) {
        // stand far from every place, where the road itself answers
        val far = farthestOpenSpot(engine)
        engine.camera.x = far.first
        engine.camera.y = far.second
        engine.rollEncounter(force = 95)
        val traveler = engine.map.entities.firstOrNull { it.traveler }
        assertNotNull("the empty road keeps travelers", traveler)
        engine.camera.x = traveler!!.x - 1.0f
        engine.camera.y = traveler.y
        assertEquals(Interact.TRAVELER, engine.interact())
    }

    private fun farthestOpenSpot(engine: GameEngine): Pair<Float, Float> {
        var best = 1f to 1f
        var bestDist = -1f
        for (x in 6 until 90 step 7) {
            for (y in 6 until 90 step 7) {
                val fx = x.toFloat()
                val fy = y.toFloat()
                if (engine.map.isWall(fx, fy)) continue
                val d = world.sites.minOf {
                    MapFactory.distance(it.x * 95f, it.y * 95f, fx, fy)
                }
                if (d > bestDist) {
                    bestDist = d
                    best = fx to fy
                }
            }
        }
        return best
    }

    // ------------------------------------------------------------ offerings & regard

    @Test
    fun anOfferingAtTheShrineRaisesTheLocals() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val shrine = world.sites.first { it.kind == SiteKind.SHRINE && !it.ruined }
        engine.currentSiteId = shrine.id
        engine.brass = 20
        val before = engine.reputation.regardFor(shrine.id)
        assertTrue(engine.canOfferAtShrine())

        engine.offerAtShrine()
        assertEquals(15, engine.brass)
        assertTrue("the locals soften", engine.reputation.regardFor(shrine.id) > before)
        assertTrue(engine.reputation.deedLog.any { it.text.contains(shrine.name) })

        // no coin, no offering
        engine.brass = 3
        assertFalse(engine.canOfferAtShrine())
    }
}
