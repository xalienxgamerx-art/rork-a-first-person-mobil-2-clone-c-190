package com.rork.hollowmarch.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every weapon its own picture; every blade the hue of its metal. */
class WeaponSpriteTest {

    @Test
    fun everyWeaponWearsItsOwnShape() {
        val ids = ItemArchetype.WEAPONS.map { Sprites.forWeapon(it) }
        assertEquals(
            "each archetype its own sprite",
            ItemArchetype.WEAPONS.size,
            ids.toSet().size
        )
        ids.forEach { assertTrue("sprite $it in the sheet", it in 19 until Sprites.COUNT) }
    }

    @Test
    fun theOldSheetStaysPut() {
        assertEquals(0, Sprites.HUSK)
        assertEquals(8, Sprites.HELD_SWORD)
        assertEquals(11, Sprites.ITEM)
        assertEquals(18, Sprites.PILGRIM)
        assertTrue(Sprites.COUNT >= 63)
    }

    @Test
    fun everyWeaponSpriteIsPainted() {
        Sprites.ensureBuilt()
        val shapes = HashSet<Int>()
        for (weapon in ItemArchetype.WEAPONS) {
            val sprite = Sprites[Sprites.forWeapon(weapon)]
            val opaque = sprite.px.count { it ushr 24 != 0 }
            assertTrue("$weapon was left blank", opaque > 40)
            shapes += sprite.px.contentHashCode()
        }
        assertEquals("every shape distinct", ItemArchetype.WEAPONS.size, shapes.size)
    }

    @Test
    fun materialsKeepTheirOwnHues() {
        val hues = Material.entries.map { it.tint }
        assertEquals("each material its own hue", Material.entries.size, hues.toSet().size)
        hues.forEach { assertTrue("hue $it sane", it in 0x101010..0xFFFFFF) }
    }

    @Test
    fun tintingTakesTheBladeAndSparesTheGrip() {
        Sprites.ensureBuilt()
        val id = Sprites.forWeapon(ItemArchetype.BLADE)
        val white = Sprites.tinted(id, 0xFFFFFF)
        assertSame("white changes nothing", Sprites[id], white)
        assertSame("the same metal is painted once", Sprites.tinted(id, Material.GOLD.tint), Sprites.tinted(id, Material.GOLD.tint))

        val gold = Sprites.tinted(id, Material.GOLD.tint)
        val iron = Sprites.tinted(id, Material.IRON.tint)
        assertFalse("gold differs from iron", gold.px.contentEquals(iron.px))

        // some metal pixel takes the gold: red clearly over blue
        val goldish = gold.px.any { p ->
            (p ushr 24) == 0xFF && ((p shr 16) and 0xFF) > ((p and 0xFF) + 40)
        }
        assertTrue("the steel reads gold", goldish)

        // tinting opens no hole and spills none
        val base = Sprites[id]
        for (i in gold.px.indices) {
            val wasEmpty = (base.px[i] ushr 24) == 0
            assertEquals(
                "alpha kept at $i",
                if (wasEmpty) 0 else 0xFF,
                gold.px[i] ushr 24
            )
        }
    }

    @Test
    fun dropsShowTheirWork() {
        val sword = Item(ItemArchetype.BLADE, Material.GOLD)
        val drop = Sprites.forDrop(sword)
        assertNotSame("a blade is no nameless bundle", Sprites[Sprites.ITEM], drop)
        assertSame(
            "the blade wears its metal",
            Sprites.tinted(Sprites.forWeapon(ItemArchetype.BLADE), Material.GOLD.tint),
            drop
        )
        assertSame(
            "a torch is known",
            Sprites[Sprites.HELD_TORCH],
            Sprites.forDrop(Item(ItemArchetype.TORCH, Material.ASHWOOD))
        )
        assertSame(
            "a remedy stays a bundle",
            Sprites[Sprites.ITEM],
            Sprites.forDrop(Item(ItemArchetype.REMEDY, Material.IRON))
        )
    }
}
