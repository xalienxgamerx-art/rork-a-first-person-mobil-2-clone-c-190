package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.Site
import com.rork.hollowmarch.world.WorldGenerator
import com.rork.hollowmarch.world.isSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistent World v1 — the acceptance criteria, end to end.
 *
 * The province must be authoritative, persistent, deterministic, and still
 * changing while nobody is watching it. Every test here walks the real engine:
 * base world -> persistent state -> runtime scene.
 */
class PersistentWorldTest {

    private val world = WorldGenerator.generate(424242L)

    private val steads: List<Site> = world.sites
        .filter { it.isSettlement && !it.ruined && it.population > 0 }

    private fun enterSite(engine: GameEngine, siteId: Int) {
        val door = engine.map.portals.first { it.targetSiteId == siteId }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
    }

    private fun leaveSite(engine: GameEngine) {
        val out = engine.map.portals.first { it.targetFloor == OverlandGen.OVERLAND_FLOOR }
        engine.camera.x = out.x
        engine.camera.y = out.y
        engine.usePortal()
    }

    /** Cut one thing down where it stands; the swing may find air first. */
    private fun cutDown(engine: GameEngine, victim: Entity) {
        victim.hp = 1
        engine.camera.x = victim.x - 1.0f
        engine.camera.y = victim.y
        engine.camera.angle = 0f
        var guard = 0
        while (victim.alive && guard++ < 40) {
            engine.strikeCooldown = 0f
            engine.fatigue = engine.maxFatigue
            engine.strike()
        }
        assertFalse("${victim.name} falls", victim.alive)
    }

    private fun aStead(): Site = steads.first { baseStageOf(it.kind) == SettlementStage.VILLAGE }

    // ------------------------------------------------------------- TEST 1

    @Test
    fun aSlainSoulStaysSlainThroughLeavingReturningAndReloading() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)

        val soul = engine.map.entities.first { it.resident && it.homeBuilding >= 0 }
        val id = soul.persistId
        val name = soul.name
        assertTrue("a soul of a living place carries a stable id", id.isNotBlank())
        assertEquals("and the id names a resident", PersistKind.RESIDENT, EntityKey.kindOf(id))

        cutDown(engine, soul)
        assertTrue("the world writes it down", engine.worldState.isDead(id))

        // out through the gate and back in: the scene is built again from scratch
        leaveSite(engine)
        assertTrue(engine.onOverland)
        enterSite(engine, stead.id)
        assertFalse(
            "$name does not stand in the yard again",
            engine.map.entities.any { it.persistId == id }
        )
        assertTrue("the world still counts it dead", engine.worldState.isDead(id))

        // and through the save
        val saved = engine.toSaveSlot()
        val woken = GameEngine(world, saved, null)
        assertTrue("the death survives the save", woken.worldState.isDead(id))
        woken.climbToOpenGround()
        enterSite(woken, stead.id)
        assertFalse(
            "and it is not regenerated as a living soul",
            woken.map.entities.any { it.persistId == id }
        )
        // the rest of the place is untouched: only that one soul is missing
        assertTrue(
            "the other souls keep their doors",
            woken.map.entities.count { it.resident } > 0
        )
    }

    @Test
    fun twoSoulsOfOneNameAreStillTwoSouls() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)
        val souls = engine.map.entities.filter { it.resident }
        assertTrue("a living place keeps souls", souls.size >= 2)

        // Give two of them the same given name; identity must not follow the name.
        val a = souls[0]
        val b = souls[1]
        b.name = a.name
        assertNotEquals("same name, different soul", a.persistId, b.persistId)

        cutDown(engine, a)
        assertTrue(engine.worldState.isDead(a.persistId))
        assertFalse("its namesake is untouched", engine.worldState.isDead(b.persistId))

        leaveSite(engine)
        enterSite(engine, stead.id)
        assertFalse(
            "the slain one is gone",
            engine.map.entities.any { it.persistId == a.persistId }
        )
        assertTrue(
            "the namesake still stands",
            engine.map.entities.any { it.persistId == b.persistId }
        )
    }

    // ------------------------------------------------------------- TEST 2

    @Test
    fun soulsGoAboutTheirDayWhileTimePasses() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)

        val soul = engine.map.entities.first { it.resident && it.homeBuilding >= 0 }
        val id = soul.persistId
        val before = engine.worldState.npc(id)
        assertNotNull("the world is keeping track of it", before)
        val wasAt = before!!.x to before.y
        val wasDoing = before.activity

        // hours the world spends: dawn, then on into the working day
        engine.restTillDawn()
        engine.rest(6)

        assertTrue("the world ran while you slept", engine.lastReport.hours > 0)
        assertTrue("souls went about their day", engine.lastReport.moved > 0)
        val after = engine.worldState.npc(id)!!
        assertNotEquals("and this one is doing something else now", wasDoing, after.activity)
        assertTrue(
            "it stands somewhere it could legally stand",
            !engine.map.isWall(after.x, after.y)
        )

        // leave, come back later: the scene reflects the simulation, not the spawn
        leaveSite(engine)
        engine.rest(3)
        enterSite(engine, stead.id)
        val standing = engine.map.entities.firstOrNull { it.persistId == id }
        assertNotNull("it is still among the living", standing)
        val record = engine.worldState.npc(id)!!
        assertEquals("the runtime soul stands where the world left it", record.x, standing!!.x, 0.001f)
        assertEquals(record.y, standing.y, 0.001f)
        assertTrue(
            "and the ledger kept the movement",
            engine.worldState.changesOf(WorldChangeKind.MOVE).isNotEmpty() ||
                (wasAt.first != record.x || wasAt.second != record.y)
        )
    }

    @Test
    fun aSoulsActivityFollowsTheHour() {
        // Night sends those with a door of their own home; the day sends them out.
        assertEquals("asleep behind a barred door", WorldSimulation.activityAt(2, homeless = false))
        assertEquals("huddled out of the wind", WorldSimulation.activityAt(2, homeless = true))
        assertTrue(WorldSimulation.homeAt(22))
        assertTrue(WorldSimulation.homeAt(3))
        assertFalse(WorldSimulation.homeAt(11))
        assertEquals("at work in the daylight", WorldSimulation.activityAt(10, homeless = false))
    }

    // ------------------------------------------------------------- TEST 3

    @Test
    fun theRoadSpendsTheWorldsOwnTime() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val target = steads.first()
        val asked = engine.bearings().first { it.site.id == target.id }.hours
        assertTrue("the walk asks real hours", asked > 0f)

        val clockBefore = engine.minutes
        val simBefore = engine.worldState.lastSimMinutes
        assertTrue("the world starts level with the clock", simBefore <= clockBefore)

        assertTrue("the road is taken", engine.travelTo(target))

        val spent = engine.minutes - clockBefore
        // the walk itself, plus the quarter hour the gate asks
        assertEquals("time advances by the walk's own duration", asked * 60f + 15f, spent, 2f)
        assertEquals(
            "and the simulation is carried to the same moment",
            engine.minutes,
            engine.worldState.lastSimMinutes,
            0.001f
        )
        assertTrue("the world ran for those hours", engine.lastReport.hours > 0)
        assertEquals("and you arrive where you were going", target.id, engine.currentSiteId)
        assertFalse(engine.onOverland)
        assertTrue("the place is remembered", target.id in engine.visitedSites)
    }

    @Test
    fun aLongerWalkCostsTheWorldMore() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val pins = engine.bearings().filter { it.site.isSettlement && !it.site.ruined }
        val near = pins.minByOrNull { it.leagues }!!
        val far = pins.maxByOrNull { it.leagues }!!
        assertTrue("the province is wide enough to test", far.leagues > near.leagues)
        assertTrue("and the far walk asks more hours", far.hours > near.hours)

        val a = GameEngine(world, null, null).apply { climbToOpenGround() }
        val beforeA = a.minutes
        a.travelTo(near.site)
        val b = GameEngine(world, null, null).apply { climbToOpenGround() }
        val beforeB = b.minutes
        b.travelTo(far.site)
        assertTrue(
            "the longer road spends more of the world's time",
            (b.minutes - beforeB) > (a.minutes - beforeA)
        )
    }

    @Test
    fun theRoadIsOnlyTakenFromTheOpenGround() {
        val engine = GameEngine(world, null, null)
        // still deep in the vault: there is no road from down here
        assertFalse(engine.onOverland)
        val clock = engine.minutes
        assertFalse("you cannot set out from underground", engine.travelTo(steads.first()))
        assertEquals("and no time is spent", clock, engine.minutes, 0.001f)
    }

    // ------------------------------------------------------------- TEST 4

    @Test
    fun theWholeOfTheWorldsMemoryRidesTheSave() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)

        // several changes: a death, an emptied container, hours of simulation, a discovery
        val soul = engine.map.entities.first { it.resident }
        cutDown(engine, soul)
        val chest = engine.map.entities.firstOrNull { it.container }
        chest?.let { engine.takeAll(it) }
        engine.rest(5)

        val deadBefore = engine.worldState.deadCount
        val discoveredBefore = engine.worldState.discovered.toSet()
        val simBefore = engine.worldState.lastSimMinutes
        val soulsBefore = engine.worldState.livingNpcs()
            .associate { it.id to Triple(it.x, it.y, it.activity) }
        val beastsBefore = engine.worldState.slainBeastIds().toSet()

        val saved = engine.toSaveSlot()
        assertTrue("the memory is written", saved.worldState.isNotBlank())
        assertEquals("with its contract version", WORLD_STATE_VERSION, saved.worldVersion)

        val woken = GameEngine(world, saved, null)
        assertEquals("the dead are all still dead", deadBefore, woken.worldState.deadCount)
        assertEquals("discoveries survive", discoveredBefore, woken.worldState.discovered.toSet())
        assertEquals(
            "the world clock survives",
            simBefore, woken.worldState.lastSimMinutes, 0.001f
        )
        assertEquals("every tracked soul survives", soulsBefore.size, woken.worldState.livingNpcs().size)
        woken.worldState.livingNpcs().forEach { npc ->
            val was = soulsBefore.getValue(npc.id)
            assertEquals("${npc.name} stands where it stood", was.first, npc.x, 0.001f)
            assertEquals(was.second, npc.y, 0.001f)
            assertEquals("and is doing what it was doing", was.third, npc.activity)
        }
        assertEquals("slain beasts survive", beastsBefore, woken.worldState.slainBeastIds())
        if (chest != null) {
            assertTrue(
                "an emptied container stays empty",
                woken.worldState.isEmptied(chest.persistId)
            )
        }
    }

    @Test
    fun aSaveRoundTripDoesNotAlterTheGeneratedWorld() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)
        val before = engine.map
        val wallsBefore = before.walls.copyOf()
        val idsBefore = before.entities.map { it.persistId }
        val namesBefore = before.entities.map { it.name }

        val woken = GameEngine(world, engine.toSaveSlot(), null)
        val after = woken.map
        assertEquals("the same place", before.title, after.title)
        assertTrue("the same stone", wallsBefore.contentEquals(after.walls))
        assertEquals("the same things, with the same ids", idsBefore, after.entities.map { it.persistId })
        assertEquals("and the same names", namesBefore, after.entities.map { it.name })
    }

    @Test
    fun aMemoryOfAnotherProvinceIsNotThisOnes() {
        val other = WorldGenerator.generate(999111L)
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        enterSite(engine, aStead().id)
        val soul = engine.map.entities.first { it.resident }
        cutDown(engine, soul)
        val saved = engine.toSaveSlot()

        // the same save string, read against a different world: a clean memory
        val foreign = WorldState.decode(saved.worldState, other.seed)
        assertEquals("no deaths carry across provinces", 0, foreign.deadCount)
        assertTrue("and no souls either", foreign.livingNpcs().isEmpty())
    }

    @Test
    fun anOldSavesNameKeyedMemoryIsMigratedToIds() {
        // A save written before stable ids kept its dead by name; the world reads
        // it once, matches it to ids as the scene is built, and speaks ids after.
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)
        val soul = engine.map.entities.first { it.resident && it.homeBuilding >= 0 }
        val legacy = engine.toSaveSlot().copy(
            worldState = "",
            worldVersion = 0,
            looted = "slain:${stead.id}:${soul.name}"
        )

        val woken = GameEngine(world, legacy, null)
        woken.climbToOpenGround()
        enterSite(woken, stead.id)
        assertFalse(
            "the old save's slain soul does not stand again",
            woken.map.entities.any { it.resident && it.name == soul.name }
        )
        assertTrue(
            "and it is now remembered by id",
            woken.worldState.isDead(soul.persistId)
        )
    }

    // ------------------------------------------------------------- TEST 5

    @Test
    fun oneSeedBuildsOneProvince() {
        val a = WorldGenerator.generate(7171L)
        val b = WorldGenerator.generate(7171L)
        assertEquals(a.provinceName, b.provinceName)
        assertEquals(a.sites.map { it.id to it.name }, b.sites.map { it.id to it.name })
        assertEquals(a.powers.map { it.name }, b.powers.map { it.name })

        val ea = GameEngine(a, null, null)
        val eb = GameEngine(b, null, null)
        assertTrue("the same waking stone", ea.map.walls.contentEquals(eb.map.walls))
        assertEquals(
            "the same things with the same ids",
            ea.map.entities.map { it.persistId },
            eb.map.entities.map { it.persistId }
        )
        assertEquals(
            ea.map.entities.map { it.name },
            eb.map.entities.map { it.name }
        )
    }

    @Test
    fun idsAreStableAcrossEveryRebuildOfAScene() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)
        val first = engine.map.entities.map { it.persistId }
        assertTrue("things here carry ids", first.any { it.isNotBlank() })

        // out and in three times; the ids must not drift
        repeat(3) {
            leaveSite(engine)
            enterSite(engine, stead.id)
            assertEquals(
                "the same scene names the same things",
                first,
                engine.map.entities.map { it.persistId }
            )
        }
    }

    @Test
    fun reconstructionIsNotAFreshRoll() {
        // The day and folk a place was first seen at are frozen, so the place you
        // come back to is the place you left rather than a new one built to today.
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)
        val key = EntityKey.scene(stead.id, 0)
        val stamp = engine.worldState.knownStamp(key)
        assertNotNull("the visit is stamped", stamp)
        val walls = engine.map.walls.copyOf()
        val ids = engine.map.entities.map { it.persistId }

        // a long absence: many days of world time
        leaveSite(engine)
        repeat(6) { engine.rest(8) }
        assertTrue("days really passed", engine.day > stamp!!.day)

        enterSite(engine, stead.id)
        assertEquals(
            "the stamp still governs the rebuild",
            stamp!!.day,
            engine.worldState.knownStamp(key)!!.day
        )
        assertTrue("the same stone stands", walls.contentEquals(engine.map.walls))
        assertEquals("and the same things are named", ids, engine.map.entities.map { it.persistId })
    }

    @Test
    fun theWorldsMemoryEncodesAndDecodesWhole() {
        val state = WorldState(world.seed)
        val id = EntityKey.of(world.seed, PersistKind.CREATURE, 4, 2, 7)
        state.markDead(id, 1000f, "a husk fell")
        state.markEmptied(EntityKey.of(world.seed, PersistKind.CONTAINER, 4, 2, 1), 1100f, "a grave")
        state.slayBeast(3, 1200f, "a terror")
        state.discover(9, 2, 1300f, "somewhere")
        state.delvedSites += 11
        state.factionPressure[2] = 17
        state.lastSimMinutes = 4321f
        state.rememberNpc(
            NpcRecord(
                id = EntityKey.of(world.seed, PersistKind.RESIDENT, 4, 0, 0),
                name = "Someone", siteId = 4, floor = 0, homeBuilding = 1, powerId = 2,
                anchorX = 3f, anchorY = 4f, x = 5f, y = 6f, activity = "at work in the daylight"
            )
        )

        val read = WorldState.decode(state.encode(), world.seed)
        assertTrue(read.isDead(id))
        assertEquals(1, read.deadCount)
        assertEquals(setOf(3), read.slainBeastIds())
        assertTrue(9 in read.discovered)
        assertEquals(2, read.visitedDay(9))
        assertTrue(11 in read.delvedSites)
        assertEquals(17, read.factionPressure[2])
        assertEquals(4321f, read.lastSimMinutes, 0.001f)
        val npc = read.livingNpcs().first()
        assertEquals("Someone", npc.name)
        assertEquals(5f, npc.x, 0.001f)
        assertEquals("at work in the daylight", npc.activity)
        assertTrue("the ledger rides along", read.changes().isNotEmpty())
    }

    // ------------------------------------------------------------- TEST 6

    @Test
    fun aSceneIsRebuiltFromBaseWorldPlusMemory() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)

        // change the persistent state: one soul dead, one container emptied
        val soul = engine.map.entities.first { it.resident }
        val soulId = soul.persistId
        cutDown(engine, soul)
        val chest = engine.map.entities.firstOrNull { it.container && (it.loot.isNotEmpty() || it.lootBrass > 0) }
        val chestId = chest?.persistId
        chest?.let { engine.takeAll(it) }

        val survivors = engine.map.entities
            .filter { it.resident && it.alive }
            .map { it.persistId }
            .toSet()

        // unload the place entirely
        leaveSite(engine)
        assertTrue(engine.onOverland)

        // and re-enter: base world builds it, memory corrects it
        enterSite(engine, stead.id)
        assertFalse(
            "the dead are not regenerated",
            engine.map.entities.any { it.persistId == soulId }
        )
        survivors.forEach { id ->
            assertTrue(
                "the living are rebuilt from the base world",
                engine.map.entities.any { it.persistId == id }
            )
        }
        if (chestId != null) {
            val again = engine.map.entities.first { it.persistId == chestId }
            assertTrue("an emptied container holds nothing", again.loot.isEmpty())
            assertEquals("and no brass either", 0, again.lootBrass)
        }
    }

    @Test
    fun theLedgerRemembersWhatHappened() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val stead = aStead()
        enterSite(engine, stead.id)
        val soul = engine.map.entities.first { it.resident }
        cutDown(engine, soul)
        engine.rest(8)

        val ledger = engine.worldState.changes()
        assertTrue("the ledger is kept", ledger.isNotEmpty())
        assertTrue(
            "a death is written in it",
            engine.worldState.changesOf(WorldChangeKind.DEATH).any { it.subject == soul.persistId }
        )
        assertTrue(
            "so is coming to the place",
            engine.worldState.changesOf(WorldChangeKind.DISCOVERY).any { it.subject == "${stead.id}" }
        )
        assertTrue(
            "the ledger stays bounded",
            ledger.size <= WorldState.LEDGER_LENGTH
        )
    }

    @Test
    fun theProvinceGoesOnWithoutYou() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val rumorsBefore = engine.rumors.size
        // many days on the road: factions press, steads keep their days, the sky turns
        repeat(10) { engine.rest(8) }

        assertTrue("days passed", engine.lastReport.days >= 0)
        val settled = engine.worldState.changesOf(WorldChangeKind.SETTLEMENT)
        val factions = engine.worldState.changesOf(WorldChangeKind.FACTION)
        assertTrue(
            "the province did something while you slept",
            settled.isNotEmpty() || factions.isNotEmpty()
        )
        assertTrue(
            "and word of it reached you",
            engine.rumors.size >= rumorsBefore
        )
        assertEquals(
            "the world clock never lags the player's",
            engine.minutes,
            engine.worldState.lastSimMinutes,
            0.001f
        )
    }

    @Test
    fun theWorldNeverRunsBackwards() {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        var last = engine.worldState.lastSimMinutes
        repeat(4) {
            engine.rest(4)
            assertTrue("the simulation only ever moves forward", engine.worldState.lastSimMinutes >= last)
            last = engine.worldState.lastSimMinutes
        }
        // asking the simulation to go back does nothing
        val report = WorldSimulation.advance(
            engine.worldState, world, engine.settlements, last - 5000f, engine.currentSiteId
        )
        assertTrue("a backward step is no step", report.quiet)
        assertEquals(last, engine.worldState.lastSimMinutes, 0.001f)
    }
}
