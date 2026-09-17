package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * The marksmanship ledger: speeds honest to the draw weight and the powder,
 * the cone of doubt, harm from momentum, each arm fed by its own shot, the
 * cycle of draw and cock and charge, finite quivers, and what the save keeps.
 */
class RangedTest {

    private val world = WorldGenerator.generate(424242L)

    @Before
    fun resetUids() = ItemUids.reset()

    // ------------------------------------------------------------- helpers

    private fun engine(): GameEngine = GameEngine(world, null, null).apply { climbToOpenGround() }

    private fun bow(form: RangedForm = RangedForm.SHORTBOW): Item =
        Item(ItemArchetype.BOW, Material.ASHWOOD, Quality.HONEST, rangedForm = form).marked()

    private fun arrows(count: Int = 20, head: Material = Material.IRON): Item =
        Item(ItemArchetype.ARROW, Material.ASHWOOD, Quality.HONEST, count = count, headMaterial = head)

    private fun GameEngine.stepFor(seconds: Float) {
        var left = seconds
        while (left > 0f) {
            update(0.05f)
            left -= 0.05f
        }
    }

    private fun GameEngine.wield(item: Item) {
        inventory.remove(item)
        equipment.equip(item)
    }

    private fun GameEngine.spawnEnemy(dist: Float, hp: Int = 40, speed: Float = 0f): Entity {
        map.entities.removeAll { it.kind == EntityKind.ENEMY }
        val enemy = Entity(
            x = camera.x + camera.dirX * dist,
            y = camera.y + camera.dirY * dist,
            spriteId = Sprites.HUSK, kind = EntityKind.ENEMY, height = 1.0f,
            name = "husk", hp = hp, maxHp = hp, speed = speed
        )
        map.entities += enemy
        return enemy
    }

    private fun GameEngine.fireBow(hold: Float = 1.2f) {
        strike()
        stepFor(hold)
        strike()
    }

    // ------------------------------------------------------------- physics

    @Test
    fun bowSpeedsHonestToTheDrawWeight() {
        val mass = 0.05f
        val reed = Ranged.launchSpeedMps(bow(RangedForm.REED_BOW), mass, 1f, 10)
        val war = Ranged.launchSpeedMps(bow(RangedForm.WARBOW), mass, 1f, 10)
        assertTrue("the heavier steel drives the shot faster", war > reed)
        val halfDrawn = Ranged.launchSpeedMps(bow(RangedForm.WARBOW), mass, 0.4f, 10)
        assertTrue("a hasty half draw loses speed", halfDrawn < war)
    }

    @Test
    fun powderSpeedsAreCappedAndThrownSteelToo() {
        val cannon = Item(ItemArchetype.HAND_CANNON, Material.IRON, Quality.HONEST).marked()
        assertEquals(280f, Ranged.launchSpeedMps(cannon, 0.005f, 1f, 10), 0.01f)
        val star = Item(ItemArchetype.SHURIKEN, Material.STEEL, Quality.HONEST)
        assertEquals(20f, Ranged.launchSpeedMps(star, 0f, 1f, 50), 0.01f)
    }

    @Test
    fun spreadDoubtsTheHastyHand() {
        val arm = bow()
        val settled = Ranged.spreadDegrees(arm, 1f, 10, 0, 0, false)
        assertTrue(Ranged.spreadDegrees(arm, 0.3f, 10, 0, 0, false) > settled)
        assertTrue(Ranged.spreadDegrees(arm, 1f, 10, 0, 0, true) > settled)
        assertTrue(Ranged.spreadDegrees(arm, 1f, 40, 30, 10, false) < settled)
    }

    @Test
    fun harmComesFromMomentumAndTemper() {
        val fast = Ranged.impactDamage(0.03f, 60f, AmmoKind.ARROW, Material.IRON, Quality.HONEST)
        val slow = Ranged.impactDamage(0.03f, 20f, AmmoKind.ARROW, Material.IRON, Quality.HONEST)
        assertTrue("speed is harm", fast > slow)
        val heavy = Ranged.impactDamage(0.13f, 60f, AmmoKind.STONE_BALL, null, Quality.HONEST)
        assertTrue("mass is harm", heavy > fast)
        val steel = Ranged.impactDamage(0.03f, 60f, AmmoKind.ARROW, Material.STEEL, Quality.HONEST)
        val bone = Ranged.impactDamage(0.03f, 60f, AmmoKind.ARROW, Material.BONE, Quality.HONEST)
        assertTrue("temper is harm", steel >= bone)
    }

    @Test
    fun arrowMassWeighsTheHead() {
        assertTrue(Ranged.ammoMassKg(arrows(head = Material.STEEL)) > Ranged.ammoMassKg(arrows(head = Material.BONE)))
        assertTrue(Ranged.ammoMassKg(arrows()) > 0.02f)
    }

    @Test
    fun eachArmFeedsOnItsOwnShot() {
        fun accepts(weapon: ItemArchetype, shot: ItemArchetype): Boolean =
            Ranged.accepts(Item(weapon, Material.IRON, Quality.HONEST), Item(shot, Material.IRON, Quality.HONEST))
        assertTrue(accepts(ItemArchetype.BOW, ItemArchetype.ARROW))
        assertFalse(accepts(ItemArchetype.BOW, ItemArchetype.BOLT))
        assertTrue(accepts(ItemArchetype.CROSSBOW, ItemArchetype.BOLT))
        assertFalse(accepts(ItemArchetype.CROSSBOW, ItemArchetype.ARROW))
        assertTrue(accepts(ItemArchetype.SLING, ItemArchetype.SLING_LEAD_SHOT))
        assertTrue(accepts(ItemArchetype.SLING, ItemArchetype.SLING_STONE))
        assertFalse(accepts(ItemArchetype.SLING, ItemArchetype.ARROW))
        assertTrue(accepts(ItemArchetype.ARQUEBUS, ItemArchetype.LEAD_BALL))
        assertFalse(accepts(ItemArchetype.ARQUEBUS, ItemArchetype.IRON_BALL))
        assertTrue(accepts(ItemArchetype.HAND_CANNON, ItemArchetype.IRON_BALL))
        assertTrue(accepts(ItemArchetype.THREE_EYE_CANNON, ItemArchetype.STONE_BALL))
        assertFalse(accepts(ItemArchetype.HAND_CANNON, ItemArchetype.LEAD_BALL))
    }

    @Test
    fun theWorkOfCockingAndChargingHasItsHours() {
        val arbalest = Item(ItemArchetype.CROSSBOW, Material.ASHWOOD, Quality.HONEST, rangedForm = RangedForm.ARBALEST)
        assertEquals(5.5f, Ranged.cycleSeconds(arbalest, RangedPhaseKind.RELOAD), 0.01f)
        val three = Item(ItemArchetype.THREE_EYE_CANNON, Material.IRON, Quality.HONEST).marked()
        assertEquals(22f, Ranged.cycleSeconds(three, RangedPhaseKind.RELOAD), 0.01f)
        assertEquals(0f, Ranged.cycleSeconds(bow(), RangedPhaseKind.IDLE), 0.01f)
        assertEquals(3, Ranged.capacity(three))
        assertEquals(1, Ranged.capacity(arbalest))
    }

    @Test
    fun aBundleRemembersItsMakeThroughTheSave() {
        val restored = Item.fromEncoded(arrows(head = Material.STEEL).encode())
        assertNotNull(restored)
        assertEquals(Material.STEEL, restored!!.headMaterial)
        val restoredBow = Item.fromEncoded(bow(RangedForm.LONGBOW).encode())
        assertNotNull(restoredBow)
        assertEquals(RangedForm.LONGBOW, restoredBow!!.rangedForm)
    }

    // ------------------------------------------------------------- the bow

    @Test
    fun aBowDrawsThenLoosesAndTheQuiverPays() {
        val game = engine()
        game.wield(bow())
        game.inventory.add(arrows(10))
        val target = game.spawnEnemy(3f)
        game.stepFor(0.1f)
        val quiverBefore = game.inventory.countOf(ItemArchetype.ARROW)

        game.strike()
        assertEquals(RangedPhaseKind.DRAW, game.rangedPhase)
        assertEquals(0, game.projectiles.size)
        game.stepFor(1.2f)
        assertEquals(1f, game.drawFraction, 0.01f)
        game.strike()
        assertEquals(RangedPhaseKind.IDLE, game.rangedPhase)
        assertEquals(quiverBefore - 1, game.inventory.countOf(ItemArchetype.ARROW))
        assertEquals(1, game.projectiles.size)

        var guard = 0
        while (game.projectiles.isNotEmpty() && guard < 400) {
            game.update(0.05f)
            guard++
        }
        assertTrue("the arrow finds its mark", target.hp < target.maxHp)
        val ledger = game.combatTrail.last()
        assertEquals(StrikeOutcome.HIT, ledger.outcome)
        assertTrue(ledger.impactSpeedMps > 0f)
        assertTrue(ledger.flightDistance > 0f)
    }

    @Test
    fun anEmptyQuiverLoosesNothing() {
        val game = engine()
        game.wield(bow())
        game.strike()
        assertEquals(RangedPhaseKind.IDLE, game.rangedPhase)
        assertEquals(0, game.projectiles.size)
        assertTrue(game.log.any { it.text.contains("bare") })
    }

    @Test
    fun aLoosedShotFallsToEarthAndIsGatheredAgain() {
        val game = engine()
        game.wield(bow())
        game.inventory.add(arrows(3))
        val before = game.inventory.countOf(ItemArchetype.ARROW)
        game.fireBow()
        var guard = 0
        while (game.projectiles.isNotEmpty() && guard < 600) {
            game.update(0.05f)
            guard++
        }
        assertTrue("the shot is spent from the air", game.projectiles.isEmpty())
        assertTrue(
            "what lands in the wilds can be picked back up",
            game.groundItems.any { it.item.archetype == ItemArchetype.ARROW }
        )
        // gravity saw it the whole way: nothing is left flying after 30 seconds
        assertTrue(guard < 600)
        assertEquals(before - 1, game.inventory.countOf(ItemArchetype.ARROW))
    }

    // ------------------------------------------------------------- crossbow

    @Test
    fun theCrossbowCocksBeforeItSpeaks() {
        val game = engine()
        game.wield(Item(ItemArchetype.CROSSBOW, Material.ASHWOOD, Quality.HONEST).marked())
        game.inventory.add(Item(ItemArchetype.BOLT, Material.ASHWOOD, Quality.HONEST, count = 4, headMaterial = Material.IRON))
        game.spawnEnemy(3f)

        game.strike()
        assertEquals(RangedPhaseKind.RELOAD, game.rangedPhase)
        assertEquals(0, game.projectiles.size)
        game.stepFor(3.4f)
        assertEquals(RangedPhaseKind.IDLE, game.rangedPhase)
        assertEquals("one bolt laid on the rail", 3, game.inventory.countOf(ItemArchetype.BOLT))

        game.strike()
        assertEquals(1, game.projectiles.size)
        game.stepFor(1.2f)
        game.strike()
        assertEquals("slack again, it wants the stirrup", RangedPhaseKind.RELOAD, game.rangedPhase)
    }

    // ------------------------------------------------------------- gunpowder

    private fun loadedCannon(game: GameEngine): Item {
        val cannon = Item(ItemArchetype.THREE_EYE_CANNON, Material.IRON, Quality.HONEST).marked()
        game.wield(cannon)
        game.inventory.add(Item(ItemArchetype.IRON_BALL, Material.IRON, Quality.HONEST, count = 5))
        game.spawnEnemy(4f)
        game.strike()
        game.stepFor(23f)
        return cannon
    }

    @Test
    fun theThreeEyeSpeaksThreeTimesThenWantsACharge() {
        val game = engine()
        val cannon = loadedCannon(game)
        assertEquals(3, game.loadedShots(cannon))
        assertEquals(2, game.inventory.countOf(ItemArchetype.IRON_BALL))

        repeat(3) {
            game.strike()
            assertTrue(game.muzzleFlash > 0f)
            game.stepFor(1.7f)
        }
        assertEquals(0, game.loadedShots(cannon))
        assertEquals(2, game.inventory.countOf(ItemArchetype.IRON_BALL))
        game.strike()
        assertEquals(RangedPhaseKind.RELOAD, game.rangedPhase)
    }

    @Test
    fun thePanStaysColdWithoutShot() {
        val game = engine()
        game.wield(Item(ItemArchetype.HAND_CANNON, Material.IRON, Quality.HONEST).marked())
        game.strike()
        assertEquals(0, game.projectiles.size)
        assertEquals(RangedPhaseKind.IDLE, game.rangedPhase)
        assertTrue(game.log.any { it.text.contains("none") })
    }

    @Test
    fun theThunderOfPowderWakesTheQuietDead() {
        val game = engine()
        loadedCannon(game)
        val sleeper = game.spawnEnemy(8f)
        assertEquals(Detection.UNAWARE, sleeper.detection)
        game.strike()
        game.stepFor(0.2f)
        assertNotEquals(Detection.UNAWARE, sleeper.detection)
    }

    @Test
    fun theMuzzleFlashItselfHarmsNothing() {
        val game = engine()
        loadedCannon(game)
        // a body beside the muzzle, never in the line of the barrel
        val bystander = Entity(
            x = game.camera.x + game.camera.dirY * 1.5f,
            y = game.camera.y - game.camera.dirX * 1.5f,
            spriteId = Sprites.HUSK, kind = EntityKind.ENEMY, height = 1.0f,
            name = "husk", hp = 40, maxHp = 40, speed = 0f
        )
        game.map.entities += bystander
        game.strike()
        var guard = 0
        while (game.projectiles.isNotEmpty() && guard < 600) {
            game.update(0.05f)
            guard++
        }
        assertEquals(bystander.maxHp, bystander.hp)
    }

    // ------------------------------------------------------------- thrown

    @Test
    fun theThrownSteelLeavesTheHandAndLands() {
        val game = engine()
        game.equipment.equip(Item(ItemArchetype.THROWING_KNIFE, Material.IRON, Quality.HONEST, count = 3).marked(), Hand.RIGHT)
        game.spawnEnemy(2.5f)
        game.strike()
        assertEquals(1, game.projectiles.size)
        assertEquals(2, game.equipment.weaponIn(Hand.RIGHT)?.count)
        assertEquals(ItemArchetype.THROWING_KNIFE, game.projectiles.first().ammoItem.archetype)
        var guard = 0
        while (game.projectiles.isNotEmpty() && guard < 200) {
            game.update(0.05f)
            guard++
        }
        assertTrue(
            "a thrown steel can be picked back up",
            game.groundItems.any { it.item.archetype == ItemArchetype.THROWING_KNIFE }
        )
    }

    // ------------------------------------------------------------- the province

    @Test
    fun theProvinceDrawsOnYou() {
        val game = engine()
        val archer = game.spawnEnemy(6f, hp = 60)
        archer.equipment = Equipment.empty().also { it.equip(bow()) }
        archer.ammoCount = 10
        archer.detection = Detection.AWARE
        val before = game.vitality
        var sawShot = false
        repeat(200) {
            game.update(0.05f)
            if (game.projectiles.any { !it.fromPlayer }) sawShot = true
        }
        assertTrue("the archer loosed", sawShot)
        assertTrue("the quiver is honest and finite", archer.ammoCount < 10)
        assertTrue("an arrow arrived", game.vitality < before)
        assertEquals(StrikeOutcome.HIT, game.combatTrail.last().outcome)
    }

    @Test
    fun anArcherOutOfArrowsClosesForTheMelee() {
        val game = engine()
        val archer = game.spawnEnemy(6f, speed = 0.9f)
        archer.equipment = Equipment.empty().also { it.equip(bow()) }
        archer.ammoCount = 0
        archer.detection = Detection.AWARE
        game.stepFor(2f)
        val dist = MapFactory.distance(archer.x, archer.y, game.camera.x, game.camera.y)
        assertTrue("no arrows, no standing off", dist < 5.4f)
        assertTrue(game.projectiles.isEmpty())
    }

    @Test
    fun aFallenArcherSpillsItsQuiver() {
        val game = engine()
        game.wield(bow())
        game.inventory.add(arrows(10))
        val archer = game.spawnEnemy(3f, hp = 1)
        archer.equipment = Equipment.empty().also { it.equip(bow()) }
        archer.ammoCount = 7
        // it never gets the shot off before the arrow finds it
        archer.attackCooldown = 60f
        game.fireBow()
        var guard = 0
        while (guard < 400 && !archer.loot.any { it.archetype == ItemArchetype.ARROW }) {
            game.update(0.05f)
            guard++
        }
        assertTrue(archer.loot.any { it.archetype == ItemArchetype.ARROW && it.count == 7 })
        assertEquals(1, game.kills)
    }

    @Test
    fun theRNGRollsAWholeQuiverDeterministically() {
        val a = Ranged.rollAmmo(Random(9), AmmoKind.ARROW, 3, 12)
        val b = Ranged.rollAmmo(Random(9), AmmoKind.ARROW, 3, 12)
        assertEquals(a.encode(), b.encode())
        assertEquals(12, a.count)
        assertNotNull(a.headMaterial)
    }
}
