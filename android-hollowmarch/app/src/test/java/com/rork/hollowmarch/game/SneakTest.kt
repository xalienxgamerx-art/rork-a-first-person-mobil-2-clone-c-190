package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SneakTest {

    private val world = WorldGenerator.generate(424242L)

    /**
     * The delver wakes at the bottom of the vault now; the climb out ends on the
     * open ground, then walk in the vault's door, take its stair down, and stand
     * on a stretch of floor that runs out ahead — these tests place things at +8.
     */
    private fun engine(): GameEngine {
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val door = engine.map.portals.first { it.targetSiteId == world.vaultSiteId }
        engine.camera.x = door.x
        engine.camera.y = door.y
        engine.usePortal()
        val down = engine.map.portals.first { it.down }
        engine.camera.x = down.x
        engine.camera.y = down.y
        engine.usePortal()
        // the waking room was always clear of the dead; keep this floor clear too,
        // so each test's own foe is the only watcher in reach
        engine.map.entities.removeAll { it.kind == EntityKind.ENEMY }
        val map = engine.map
        var placed = false
        for (y in 2 until map.height - 2) {
            var runStart = -1
            for (x in 1 until map.width) {
                if (map.walls[y * map.width + x] == 0) {
                    if (runStart < 0) runStart = x
                } else {
                    if (runStart >= 0 && x - runStart >= 9) {
                        engine.camera.x = runStart + 0.5f
                        engine.camera.y = y + 0.5f
                        placed = true
                        break
                    }
                    runStart = -1
                }
            }
            if (placed) break
        }
        return engine
    }

    @Test
    fun theCrouchMakesYouHarderToSee() {
        val stats = StatBlock.balanced()
        assertTrue(Derived.stealthScore(stats, null, true) > Derived.stealthScore(stats, null, false))
        val trained = Growth.fromEncoded("1\u001F0\u001F0\u001FSTEALTH=8\u001F")
        assertTrue(Derived.stealthScore(stats, trained) > Derived.stealthScore(stats, null))
        // The watcher's side of the contest: Awareness grows its eye.
        assertTrue(Derived.detectionScore(stats, trained) > Derived.detectionScore(stats, null))
    }

    @Test
    fun theContestHasAReadableRadius() {
        // More sight widens the eye; more quiet narrows it; both ends are clamped.
        assertTrue(Derived.detectionRadius(20f, 0f) > Derived.detectionRadius(6f, 0f))
        assertTrue(Derived.detectionRadius(6f, 40f) < Derived.detectionRadius(6f, 0f))
        assertEquals(0.8f, Derived.detectionRadius(0f, 100f), 0.01f)
        assertEquals(14f, Derived.detectionRadius(100f, 0f), 0.01f)
    }

    @Test
    fun crouchedInTheDarkYouAreNobody() {
        val engine = engine()
        engine.torch = 0.05f
        val foe = MapFactory.rollEnemy(Random(9L), 2f, 2f, 2, null)
        foe.x = engine.camera.x + 6f
        foe.y = engine.camera.y
        engine.map.entities += foe

        engine.toggleCrouch()
        assertTrue(engine.crouched)
        engine.update(0.5f)

        assertEquals(Detection.UNAWARE, foe.detection)
        assertEquals(DetectionState.HIDDEN, engine.detectionState())
        // Staying unseen is its own lesson.
        assertTrue(engine.growth.progressFraction(Skill.STEALTH) > 0f)
    }

    @Test
    fun standingInTheirSightYouAreSomebody() {
        val engine = engine()
        engine.torch = 0.05f
        val foe = MapFactory.rollEnemy(Random(9L), 2f, 2f, 2, null)
        foe.x = engine.camera.x + 6f
        foe.y = engine.camera.y
        engine.map.entities += foe

        engine.update(0.5f)

        assertEquals(Detection.AWARE, foe.detection)
        assertEquals(DetectionState.SEEN, engine.detectionState())
    }

    @Test
    fun aBlowFromBehindTheDarkness() {
        val engine = engine()
        engine.torch = 0.05f
        val foe = MapFactory.rollEnemy(Random(11L), 2f, 2f, 2, null)
        foe.x = engine.camera.x + 1.4f
        foe.y = engine.camera.y
        engine.map.entities += foe
        engine.camera.angle = 0f
        engine.toggleCrouch()
        val before = foe.hp

        // the blow may find air first; the dark does not steady the hand, only the victim's eye
        var guard = 0
        while (foe.hp >= before && guard++ < 40) {
            engine.strikeCooldown = 0f
            engine.fatigue = engine.maxFatigue
            engine.strike()
        }

        assertTrue("a sneak blow lands", foe.hp < before)
        // It hits far harder than an honest swing: at least double the floor.
        assertTrue("${before - foe.hp} damage", before - foe.hp >= 2 * (2 + engine.stats[Attr.MIGHT]))
        assertEquals(Detection.AWARE, foe.detection)
        assertTrue(engine.growth.progressFraction(Skill.STEALTH) > 0f)
        assertTrue("the dead keep nothing", foe.alive || before - foe.hp > 0)
    }

    @Test
    fun pocketsAreForTheCrouched() {
        val engine = engine()
        val foe = MapFactory.rollEnemy(Random(13L), 2f, 2f, 2, null)
        foe.x = engine.camera.x + 1.0f
        foe.y = engine.camera.y
        engine.map.entities += foe

        // Standing, the fingers stay at your sides.
        assertFalse(engine.pickpocket())
        assertEquals(Detection.UNAWARE, foe.detection)

        engine.toggleCrouch()
        val brassBefore = engine.brass
        val satchelBefore = engine.inventory.all.size
        val woke = !engine.pickpocket()

        if (woke) {
            assertEquals(Detection.AWARE, foe.detection)
        } else {
            assertTrue(engine.brass > brassBefore || engine.inventory.all.size > satchelBefore)
            assertEquals(Detection.UNAWARE, foe.detection)
        }
        assertTrue("a picked pocket is not a murdered man", foe.alive)
        assertTrue(engine.growth.progressFraction(Skill.SLEIGHT_OF_HAND) > 0f)
    }

    @Test
    fun crouchingSinksTheEye() {
        val engine = engine()
        assertEquals(0.5f, engine.camera.eye, 0.001f)

        engine.toggleCrouch()
        repeat(30) { engine.update(0.05f) }
        assertTrue("eye sank to ${engine.camera.eye}", engine.camera.eye < 0.35f)

        engine.toggleCrouch()
        repeat(30) { engine.update(0.05f) }
        assertTrue("eye rose to ${engine.camera.eye}", engine.camera.eye > 0.45f)
    }

    @Test
    fun theSearchingWalkSoftAndTheAwareStrike() {
        val engine = engine()
        val foe = MapFactory.rollEnemy(Random(15L), 2f, 2f, 2, null)
        foe.x = engine.camera.x + 8f
        foe.y = engine.camera.y
        engine.map.entities += foe
        foe.detection = Detection.SEARCHING

        // A searcher closes at six tenths of its pace, not more.
        val before = foe.x
        engine.update(0.1f)
        val closed = before - foe.x
        assertTrue("$closed per tick", closed < foe.speed * 0.1f)

        // An aware creature closes at its full stride.
        foe.x = before
        foe.detection = Detection.AWARE
        engine.update(0.1f)
        assertTrue(foe.x < before)
    }
}
