package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.EventKind
import com.rork.hollowmarch.world.SiteKind
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folk of the steads: years raise and thin them, and the ladder renames the grown. */
class SettlementTest {

    private val world = WorldGenerator.generate(424242L)

    // ------------------------------------------------------------ the ladder

    @Test
    fun theLadderClimbsWithTheFolk() {
        assertEquals(SettlementStage.CAMP, stageFromFolk(0))
        assertEquals(SettlementStage.CAMP, stageFromFolk(39))
        assertEquals(SettlementStage.VILLAGE, stageFromFolk(40))
        assertEquals(SettlementStage.TOWN, stageFromFolk(320))
        assertEquals(SettlementStage.CITY, stageFromFolk(2400))
        assertEquals(SettlementStage.CAPITAL, stageFromFolk(12000))
    }

    @Test
    fun aFoundingRankNeverFalls() {
        val city = world.sites.first { it.kind == SiteKind.CITY && !it.ruined }
        assertEquals(SettlementStage.CITY, stageOf(city, 12))
        val capital = world.sites.first { it.kind == SiteKind.CAPITAL && !it.ruined }
        assertEquals(SettlementStage.CAPITAL, stageOf(capital, 3))
        // the folk can still carry a place up the ladder
        val village = world.sites.first { it.kind == SiteKind.VILLAGE && !it.ruined }
        assertEquals(SettlementStage.TOWN, stageOf(village, 400))
    }

    @Test
    fun eachStageBuildsBiggerThanTheLast() {
        SettlementStage.entries.zipWithNext().forEach { (small, big) ->
            assertTrue("${small.name} builds smaller than ${big.name}", small.yardRadius < big.yardRadius)
            assertTrue(small.interiorSpan < big.interiorSpan)
            assertTrue(small.wallRadius <= big.wallRadius)
        }
        assertEquals("villages stand unwalled", 0f, SettlementStage.VILLAGE.wallRadius, 0f)
        assertTrue("towns raise a palisade", SettlementStage.TOWN.wallRadius > 0f)
    }

    // ------------------------------------------------------------ the years

    @Test
    fun theYearsAreDeterministic() {
        val a = folkYearStep(world.seed, 7, 120, 300, SettlementStage.VILLAGE, 0)
        val b = folkYearStep(world.seed, 7, 120, 300, SettlementStage.VILLAGE, 0)
        assertEquals("same seed, same year, same folk", a, b)
    }

    @Test
    fun goodYearsRaiseFolkAndHardYearsThinThem() {
        val site = world.sites.first { it.isSettlement && !it.ruined && it.population > 0 }
        var goods = 0
        var hards = 0
        for (year in 1..160) {
            val step = folkYearStep(world.seed, site.id, year, site.population, baseStageOf(site.kind), 0)
            when (step.kind) {
                FolkYearKind.GOOD -> {
                    goods++
                    assertTrue("a good year is never childless", step.born >= 1)
                }
                else -> {
                    hards++
                    assertTrue("a hard year takes its due", step.died > 0 || site.population == 0)
                }
            }
        }
        assertTrue("most years are kind: $goods good, $hards hard", goods > hards)
        assertTrue("hard years come to every place", hards > 0)
    }

    @Test
    fun slainFolkLowerTheCountExactlyAsPlagueDoes() {
        val site = world.sites.first { it.isSettlement && !it.ruined && it.population > 1000 }
        val clean = folkYearStep(world.seed, site.id, 99, site.population, baseStageOf(site.kind), 0)
        val bloody = folkYearStep(world.seed, site.id, 99, site.population, baseStageOf(site.kind), 13)
        assertEquals("thirteen slain, thirteen fewer", clean.folk - 13, bloody.folk)
    }

    @Test
    fun crossingTheThresholdRenamesThePlace() {
        val villages = world.sites.filter { it.kind == SiteKind.VILLAGE && !it.ruined }
        var rose = 0
        villages.forEach { site ->
            var folk = site.population
            for (year in world.currentYear until world.currentYear + 300) {
                val step = folkYearStep(world.seed, site.id, year, folk, SettlementStage.VILLAGE, 0)
                folk = step.folk
                val risen = step.risenTo
                if (risen != null) {
                    rose++
                    assertTrue(risen.ordinal > SettlementStage.VILLAGE.ordinal)
                    assertTrue(
                        "the chronicle names the new stage",
                        stageEventText(site, SettlementStage.VILLAGE, risen, folk).contains(risen.label)
                    )
                    break
                }
            }
        }
        assertTrue("some stead grows within three hundred years: $rose", rose > 0)
    }

    // ------------------------------------------------------------ the ledger

    @Test
    fun theLedgerStartsFromTheWorldsCensus() {
        val ledger = SettlementLedger.fresh(world)
        world.sites.filter { it.isSettlement && !it.ruined && it.population > 0 }.forEach { site ->
            assertEquals(site.population, ledger.folkOf(site))
            assertEquals(stageOf(site, site.population), ledger.stageAt(site))
        }
    }

    @Test
    fun theLedgerRidesTheSave() {
        val ledger = SettlementLedger.fresh(world)
        val site = world.sites.first { it.isSettlement && !it.ruined && it.population > 0 }
        ledger.recordDeath(site.id)
        ledger.recordDeath(site.id)
        ledger.stepYears(world.currentYear + 4, world.sites)
        val restored = SettlementLedger.fromEncoded(ledger.encode(), world)
        assertEquals(ledger.folkOf(site), restored.folkOf(site))
        assertEquals(ledger.stageAt(site), restored.stageAt(site))
        assertEquals("graves are counted at the turn, not hoarded", 0, restored.gravesWaiting(site.id))
        // an old save without the field wakes from the census
        val fresh = SettlementLedger.fromEncoded("", world)
        assertEquals(site.population, fresh.folkOf(site))
    }

    @Test
    fun theYearsTurnAndTheChronicleRemembers() {
        val ledger = SettlementLedger.fresh(world)
        val site = world.sites.first { it.isSettlement && !it.ruined && it.population > 0 }
        ledger.recordDeath(site.id)
        val turns = ledger.stepYears(world.currentYear + 6, world.sites)
        assertTrue("a year with graves in it is written down", turns.isNotEmpty())
        assertEquals(world.currentYear + 6, ledger.currentYear)
        val events = turns.flatMap { it.events }
        assertTrue(
            "the slain are named in the chronicle",
            events.any { it.kind == EventKind.DEATH && it.text.contains(site.name) }
        )
        // turning the same year again writes nothing new
        assertTrue(ledger.stepYears(world.currentYear + 6, world.sites).isEmpty())
    }

    // ------------------------------------------------------------ the engine

    @Test
    fun theEngineCountsLivingFolkAndShowsTheStage() {
        val engine = GameEngine(world, null, null)
        val site = world.sites.first { it.isSettlement && !it.ruined && it.population > 0 }
        assertEquals(site.population, engine.folkOf(site.id))
        assertEquals(stageOf(site, site.population).label, engine.stageAt(site).label)
    }

    @Test
    fun aSoulSlainAtAGateIsCountedAgainstTheYear() {
        val engine = GameEngine(world, null, null)
        val site = world.sites.first { it.isSettlement && !it.ruined && it.population > 0 }
        engine.currentSiteId = site.id
        engine.recordSettlementDeath(site)
        engine.recordSettlementDeath(site)
        assertEquals(2, engine.settlements.gravesWaiting(site.id))
        // the years turn, the graves are written down, and the folk are thinner for them
        engine.minutes = 400f * 1440f
        engine.stepFolkYears()
        assertEquals(0, engine.settlements.gravesWaiting(site.id))
        // the ledger's arithmetic is the pure step's arithmetic, graves handed in
        val step = folkYearStep(world.seed, site.id, world.currentYear, site.population, baseStageOf(site.kind), 2)
        assertEquals(step.folk, engine.folkOf(site.id))
        assertTrue(
            "the journal carries the graves",
            engine.chronicle.any { it.kind == EventKind.DEATH && it.text.contains(site.name) }
        )
    }
}
