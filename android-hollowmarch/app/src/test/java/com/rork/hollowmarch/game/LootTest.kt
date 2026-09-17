package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LootTest {

    private fun warden(): ActorClass =
        GameEngine(WorldGenerator.generate(4242L), null, null).roster.byKey("warden")
            ?: error("the province keeps a warden")

    /** The delver wakes on the open ground now; walk in the vault's door. */
    private fun descendOne(engine: GameEngine) {
        val portal = if (engine.onOverland) {
            engine.map.portals.first { it.targetSiteId == engine.world.vaultSiteId }
        } else {
            engine.map.portals.first { it.down }
        }
        engine.camera.x = portal.x
        engine.camera.y = portal.y
        engine.usePortal()
    }

    @Test
    fun theSameRollsMakeTheSameKit() {
        val klass = warden()
        val a = Loot.kit(Random(77L), klass, 3, 5)
        val b = Loot.kit(Random(77L), klass, 3, 5)
        // The same rolls cut the same kit; each made piece still bears its own mark.
        assertEquals(a.map { it.copy(uid = 0) }, b.map { it.copy(uid = 0) })
        a.zip(b).forEach { (x, y) ->
            if (!x.archetype.stacks) {
                assertTrue("every made piece is marked", x.uid > 0)
                assertNotEquals(x.uid, y.uid)
            }
        }

        val c = Loot.containerContents(Random(91L), 2, underground = true)
        val d = Loot.containerContents(Random(91L), 2, underground = true)
        assertEquals(c.first.map { it.copy(uid = 0) }, d.first.map { it.copy(uid = 0) })
    }

    @Test
    fun aWardenDiesWithWardensArms() {
        val klass = warden()
        repeat(30) { i ->
            val rng = Random(1000L + i)
            val kit = Loot.kit(rng, klass, 3, 5)
            assertTrue(kit.isNotEmpty())
            val weapon = kit.first()
            assertTrue(weapon.archetype.isWeapon)
            // The warden's calling trusts swords and cleaving arms of every country.
            assertTrue(
                "warden trusts wardens' arms, got ${weapon.archetype}",
                weapon.archetype in setOf(
                    ItemArchetype.BLADE, ItemArchetype.ARMING_SWORD, ItemArchetype.FALCHION, ItemArchetype.AXE
                )
            )
            // Every piece is whole: positive weight, positive worth, a culture's style.
            kit.forEach { item ->
                assertTrue(item.weight() > 0f)
                assertTrue(item.value() >= 1)
                assertEquals(5, item.cultureId)
            }
            assertTrue(Loot.brass(rng, 3) > 0)
        }
    }

    @Test
    fun everyArmoredPlaceCanTurnUpInTheGraves() {
        // No piece of the armory is missing from what graves and corpses may hold.
        ItemArchetype.entries.filter { it.slot == ItemSlot.BODY }.forEach { piece ->
            assertTrue("$piece missing from the graves", piece in Loot.ARMOR_POOL)
        }
        // Trinkets are money, not armor; they ride the trinket rolls instead.
        ItemArchetype.entries.filter { it.slot == ItemSlot.TRINKET }.forEach { trinket ->
            assertFalse("$trinket is not armor", trinket in Loot.ARMOR_POOL)
        }
    }

    @Test
    fun aCulturelessCorpseStillCarriesSomething() {
        val kit = Loot.kit(Random(5L), null, 2, -1)
        assertTrue(kit.first().archetype.isWeapon)
        assertEquals(-1, kit.first().cultureId)
    }

    @Test
    fun gravesAndUrnsHoldCoinsAndPossessions() {
        repeat(20) { i ->
            val (items, coins) = Loot.containerContents(Random(300L + i), 2, underground = true)
            assertTrue(items.isNotEmpty())
            assertTrue(coins > 0)
            items.forEach { item ->
                assertTrue(item.value() >= 1)
                assertTrue(item.count >= 1)
            }
        }
    }

    @Test
    fun aCorpseKeepsWhatItCarried() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val floors = SiteGen.floorCount(world, world.site(world.vaultSiteId))
        var found = engine.map.entities.firstOrNull {
            it.kind == EntityKind.ENEMY && it.alive && it.spriteId != Sprites.HOUND && it.loot.isNotEmpty()
        }
        while (found == null && engine.depth < floors) {
            descendOne(engine)
            found = engine.map.entities.firstOrNull {
                it.kind == EntityKind.ENEMY && it.alive && it.spriteId != Sprites.HOUND && it.loot.isNotEmpty()
            }
        }
        assertNotNull("the vault keeps dressed dead", found)
        val target = found!!
        val kitSize = target.loot.size
        val wornSize = target.equipment?.items()?.size ?: 0
        val brassBefore = engine.brass

        // A body near your feet: its spoils are yours to lift.
        engine.camera.x = target.x + 0.4f
        engine.camera.y = target.y
        target.alive = false

        val lootable = engine.lootableHere()
        assertNotNull(lootable)
        assertEquals(target, lootable)

        // One thing at a time, then the rest.
        val first = target.loot.first()
        engine.takeItem(target, first)
        assertTrue(engine.inventory.all.contains(first))
        assertEquals(kitSize - 1, target.loot.size)

        val had = engine.inventory.all.size
        val taken = engine.takeAll(target)
        assertEquals(kitSize - 1 + wornSize, taken)
        assertEquals(had + taken, engine.inventory.all.size)
        assertTrue(engine.brass >= brassBefore)
        assertTrue(target.loot.isEmpty())
        assertEquals(0, target.lootBrass)
        assertTrue(target.equipment?.isEmpty() != false)
    }

    @Test
    fun anEmptiedGraveStaysEmptyWhenYouComeBack() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val floors = SiteGen.floorCount(world, world.site(world.vaultSiteId))
        var containers = engine.map.entities.filter { it.container }
        while (containers.isEmpty() && engine.depth < floors) {
            descendOne(engine)
            containers = engine.map.entities.filter { it.container }
        }
        assertTrue("the vault keeps its dead and their urns", containers.isNotEmpty())
        containers.forEach { engine.takeAll(it) }
        assertTrue(containers.all { it.loot.isEmpty() && it.lootBrass == 0 })

        val restored = GameEngine(world, engine.toSaveSlot(), null)
        val restoredContainers = restored.map.entities.filter { it.container }
        assertEquals(containers.size, restoredContainers.size)
        assertTrue(restoredContainers.all { it.loot.isEmpty() && it.lootBrass == 0 })
    }

    @Test
    fun theSameSeedRollsTheSameSpoils() {
        val world = WorldGenerator.generate(4242L)
        val a = GameEngine(world, null, null).apply { climbToOpenGround() }
        val b = GameEngine(world, null, null).apply { climbToOpenGround() }
        assertEquals(a.map.entities.size, b.map.entities.size)
        a.map.entities.zip(b.map.entities).forEach { (first, second) ->
            assertEquals(first.loot, second.loot)
            assertEquals(first.lootBrass, second.lootBrass)
        }
    }

    @Test
    fun aHoundKeepsOnlyItsTeethAndWhatItAte() {
        val world = WorldGenerator.generate(4242L)
        val engine = GameEngine(world, null, null).apply { climbToOpenGround() }
        val floors = SiteGen.floorCount(world, world.site(world.vaultSiteId))
        var hound = engine.map.entities.firstOrNull {
            it.kind == EntityKind.ENEMY && it.alive && it.spriteId == Sprites.HOUND
        }
        while (hound == null && engine.depth < floors) {
            descendOne(engine)
            hound = engine.map.entities.firstOrNull {
                it.kind == EntityKind.ENEMY && it.alive && it.spriteId == Sprites.HOUND
            }
        }
        assertNotNull(hound)
        assertTrue(hound!!.loot.isEmpty())
        assertTrue(hound.lootBrass >= 0)
    }
}
