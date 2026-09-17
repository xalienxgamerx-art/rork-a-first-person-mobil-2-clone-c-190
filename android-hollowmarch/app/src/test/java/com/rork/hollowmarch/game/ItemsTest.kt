package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.WorldGenerator
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemsTest {

    @Test
    fun materialsKeepTheirTemper() {
        // Grave-slate is a slow crushing ruin; salt-glass pierces and shatters.
        assertTrue(Material.GRAVE_SLATE.weightFactor() > Material.BONE.weightFactor())
        assertTrue(Material.GRAVE_SLATE.damageMult(DamageType.BLUNT) > 1.2f)
        assertTrue(Material.SALT_GLASS.damageMult(DamageType.PIERCE) > 1.2f)
        assertTrue(Material.SALT_GLASS.damageMult(DamageType.BLUNT) < 0.7f)
        assertTrue(Material.BONE.damageMult(DamageType.SHARP) > 1.0f)
        assertTrue(Material.BONE.speedFactor() > Material.BOG_IRON.speedFactor())
        assertTrue(Material.BRASS.valueFactor() > Material.ASHWOOD.valueFactor())
        // Every material knows all three harms and keeps them in a sane band.
        Material.entries.forEach { material ->
            DamageType.entries.forEach { type ->
                val mult = material.damageMult(type)
                assertTrue("$material/$type", mult in 0.4f..1.5f)
            }
            assertTrue(material.weightFactor() in 0.4f..1.8f)
            assertTrue(material.speedFactor() in 0.6f..1.3f)
        }
    }

    @Test
    fun theLedgerGrowsMetalsRealAndOtherwise() {
        // Steel is the smith's pride: sharper and lighter than honest iron, and dearer.
        assertTrue(Material.STEEL.damageMult(DamageType.SHARP) > Material.IRON.damageMult(DamageType.SHARP))
        assertTrue(Material.STEEL.weightFactor() < Material.IRON.weightFactor())
        assertTrue(Material.STEEL.valueFactor() > Material.IRON.valueFactor())
        // Copper is the cheap beginning; lead the cheap crushing ruin.
        assertTrue(Material.COPPER.valueFactor() < Material.IRON.valueFactor())
        assertTrue(Material.LEAD.damageMult(DamageType.BLUNT) > Material.GOLD.damageMult(DamageType.BLUNT))
        assertTrue(Material.LEAD.weightFactor() > Material.GOLD.weightFactor())
        // Gold is magnificent on a ledger and useless in a fight — priciest of
        // the worldly metals; the arcane three are priced beyond ledgers.
        assertEquals(
            Material.entries.filter { !it.arcane }.maxOf { it.valueFactor() },
            Material.GOLD.valueFactor(),
            0f
        )
        assertTrue(Material.GOLD.damageMult(DamageType.SHARP) < 0.7f)

        // The arcane three carry the mark, and no one else does.
        assertEquals(
            setOf(Material.STAR_IRON, Material.SPIRIT_SILVER, Material.EARTH_BONE),
            Material.entries.filter { it.arcane }.toSet()
        )
        Material.entries.forEach { material ->
            assertTrue("${material.label} weighs nothing", material.weightFactor() > 0f)
            assertTrue("${material.label} is worthless", material.valueFactor() > 0f)
        }

        // Arcane pieces speak their own line on the detail card.
        val world = WorldGenerator.generate(77L)
        val roster = StyleRoster(world)
        assertTrue(
            roster.flavorFor(Item(ItemArchetype.MACE, Material.EARTH_BONE, Quality.HONEST))
                .contains("remembers")
        )
        assertTrue(
            roster.flavorFor(Item(ItemArchetype.BLADE, Material.STAR_IRON, Quality.HONEST))
                .contains("fell")
        )
        assertTrue(
            roster.flavorFor(Item(ItemArchetype.DAGGER, Material.SPIRIT_SILVER, Quality.HONEST))
                .contains("moon")
        )

        // Names flow as always: the material's adjective before the people's word.
        val culture = world.cultures.first().id
        assertTrue(
            roster.nameFor(Item(ItemArchetype.BLADE, Material.STEEL, Quality.HONEST, culture))
                .startsWith("Steel ")
        )
        assertTrue(
            roster.nameFor(Item(ItemArchetype.HAUBERK, Material.SPIRIT_SILVER, Quality.HONEST, culture))
                .startsWith("Spirit-Silver ")
        )
        assertEquals(
            "Earth-Bone Plate Cuirass",
            roster.nameFor(Item(ItemArchetype.CUIRASS, Material.EARTH_BONE, cultureId = -1))
        )
        assertEquals(
            "Star-Iron Blade",
            roster.nameFor(Item(ItemArchetype.BLADE, Material.STAR_IRON, cultureId = -1))
        )
    }

    @Test
    fun aWeaponsNumbersComeFromItsShapeAndItsTemper() {
        val slate = Item(ItemArchetype.MACE, Material.GRAVE_SLATE, Quality.HONEST, -1)
        val bone = Item(ItemArchetype.MACE, Material.BONE, Quality.HONEST, -1)
        assertTrue(slate.damage() > bone.damage())
        assertTrue(slate.weight() > bone.weight())
        assertTrue(bone.speedFactor() > slate.speedFactor())

        val honest = Item(ItemArchetype.BLADE, Material.VERDIGRIS, Quality.HONEST, -1)
        val fine = Item(ItemArchetype.BLADE, Material.VERDIGRIS, Quality.FINE, -1)
        val worn = Item(ItemArchetype.BLADE, Material.VERDIGRIS, Quality.WORN, -1)
        assertTrue(fine.damage() > honest.damage())
        assertTrue(honest.damage() > worn.damage())
        assertTrue(fine.value() > honest.value() * 1.3)
        assertTrue(worn.value() < honest.value())

        // Archetypes carry their own harm: the mace crushes, the dagger pierces.
        assertEquals(DamageType.BLUNT, ItemArchetype.MACE.damageType)
        assertEquals(DamageType.PIERCE, ItemArchetype.DAGGER.damageType)
        assertTrue(ItemArchetype.SPEAR.twoHanded)
        assertTrue(!ItemArchetype.BLADE.twoHanded)
        // Nothing in the province starts with no damage on a weapon.
        ItemArchetype.WEAPONS.forEach { archetype ->
            assertTrue(
                "$archetype",
                Item(archetype, Material.VERDIGRIS).damage() > 0
            )
        }
    }

    @Test
    fun stackablesCountTogetherAndWeaponsStandAlone() {
        val remedies = Item(ItemArchetype.REMEDY, Material.VERDIGRIS, count = 4)
        assertEquals(4, remedies.count)
        assertEquals(remedies.weight() / 4, Item(ItemArchetype.REMEDY, Material.VERDIGRIS).weight(), 0.001f)
        assertTrue(remedies.value() > Item(ItemArchetype.REMEDY, Material.VERDIGRIS).value())
        assertTrue(remedies.sameStack(Item(ItemArchetype.REMEDY, Material.VERDIGRIS, count = 9)))
        assertFalse(remedies.sameStack(Item(ItemArchetype.TORCH, Material.VERDIGRIS, count = 9)))

        val blade = Item(ItemArchetype.BLADE, Material.VERDIGRIS)
        // Stacking is a property of the shape: remedies and torches stack, blades never do.
        assertFalse(ItemArchetype.BLADE.stacks)
        assertTrue(ItemArchetype.REMEDY.stacks)
        assertTrue(ItemArchetype.TORCH.stacks)
        assertFalse(blade.sameStack(Item(ItemArchetype.BLADE, Material.BONE)))
    }

    @Test
    fun sameWorldNamesTheSameBladeTheSameWay() {
        val worldA = com.rork.hollowmarch.world.WorldGenerator.generate(4242L)
        val worldB = com.rork.hollowmarch.world.WorldGenerator.generate(4242L)
        val rosterA = StyleRoster(worldA)
        val rosterB = StyleRoster(worldB)

        val blade = Item(ItemArchetype.BLADE, Material.VERDIGRIS, Quality.HONEST, worldA.cultures.first().id)
        assertEquals(rosterA.nameFor(blade), rosterB.nameFor(blade))
        assertTrue(rosterA.nameFor(blade).startsWith("Verdigris "))

        // Different cultures name the same shape differently; every name is complete.
        val names = worldA.cultures.map { culture ->
            rosterA.nameFor(Item(ItemArchetype.BLADE, Material.VERDIGRIS, Quality.HONEST, culture.id))
        }
        assertTrue(names.isNotEmpty())
        names.forEach { name ->
            assertTrue(name.isNotBlank())
            assertTrue(name.startsWith("Verdigris"))
        }

        // A trinket wears a culture's word for it too, and unnamed things fall back gracefully.
        val charm = Item(ItemArchetype.CHARM, Material.BONE, Quality.HONEST, worldA.cultures.first().id)
        assertTrue(rosterA.nameFor(charm).startsWith("Bone "))
        val orphan = Item(ItemArchetype.BLADE, Material.BOG_IRON, cultureId = -1)
        assertEquals("Bog-Iron Blade", rosterA.nameFor(orphan))
        assertTrue(rosterA.flavorFor(Item(ItemArchetype.BLADE, Material.VERDIGRIS, Quality.FINE)).isNotBlank())
    }

    @Test
    fun materialRollsRespectRarityAndQualityRollsStayInBounds() {
        val rng = Random(9L)
        val seen = mutableSetOf<Material>()
        repeat(800) { seen += Material.roll(rng) }
        // Common things appear, and every material can turn up in the fullness of time.
        assertTrue(seen.contains(Material.BOG_IRON))
        assertEquals(Material.entries.size, seen.size)

        val qualities = mutableSetOf<Quality>()
        repeat(600) { qualities += Quality.roll(rng) }
        assertEquals(Quality.entries.size, qualities.size)
    }

    @Test
    fun theMakeRunsFromShoddyToLegendary() {
        assertEquals(6, Quality.entries.size)
        assertEquals("shoddy", Quality.entries.first().label)
        assertEquals("legendary", Quality.entries.last().label)
        // Harm, guard and worth all climb with the make.
        Quality.entries.zipWithNext().forEach { (low, high) ->
            val lowBlade = Item(ItemArchetype.BLADE, Material.VERDIGRIS, low)
            val highBlade = Item(ItemArchetype.BLADE, Material.VERDIGRIS, high)
            assertTrue("$low harm vs $high", lowBlade.damage() < highBlade.damage())
            assertTrue("$low worth vs $high", low.valueMult < high.valueMult)
            assertTrue("$low guard vs $high", low.armorMult < high.armorMult)
        }
        // The legendary make is rarer than the honest one, drawn from the same rng.
        val rng = Random(11L)
        val tally = mutableMapOf<Quality, Int>()
        repeat(2000) { val q = Quality.roll(rng); tally[q] = (tally[q] ?: 0) + 1 }
        assertTrue(
            "legendary ${tally[Quality.LEGENDARY]} vs honest ${tally[Quality.HONEST]}",
            (tally[Quality.LEGENDARY] ?: 0) < (tally[Quality.HONEST] ?: 0)
        )
    }

    @Test
    fun armorGuardsByItsTemperAndItsMake() {
        // Salt-glass turns the point and shatters under the maul.
        val glass = Item(ItemArchetype.HAUBERK, Material.SALT_GLASS, Quality.HONEST)
        assertTrue(glass.armorValue(DamageType.PIERCE) > glass.armorValue(DamageType.BLUNT))
        // Bog iron shrugs the maul better than brittle glass.
        val iron = Item(ItemArchetype.HAUBERK, Material.BOG_IRON, Quality.HONEST)
        assertTrue(iron.armorValue(DamageType.BLUNT) > glass.armorValue(DamageType.BLUNT))
        // The make hardens the guard.
        assertTrue(
            Item(ItemArchetype.HELM, Material.BONE, Quality.LEGENDARY).armorValue(DamageType.SHARP) >
                Item(ItemArchetype.HELM, Material.BONE, Quality.SHODDY).armorValue(DamageType.SHARP)
        )
        // Every worn piece guards; the total is the sum of what covers the body.
        val pieces = listOf(
            Item(ItemArchetype.HELM, Material.BONE, Quality.HONEST),
            Item(ItemArchetype.HAUBERK, Material.BONE, Quality.HONEST),
            Item(ItemArchetype.BOOTS, Material.BONE, Quality.HONEST)
        )
        assertEquals(
            pieces.sumOf { it.armorValue(DamageType.SHARP) },
            Derived.armorSoak(pieces, DamageType.SHARP)
        )
        // Non-armor shapes guard nothing.
        assertEquals(0, Item(ItemArchetype.BLADE, Material.GRAVE_SLATE, Quality.LEGENDARY).armorValue(DamageType.SHARP))
    }

    @Test
    fun everyWearableKnowsItsPlace() {
        assertEquals(17, WearSlot.entries.size)
        // Two-handed arms claim both hands; the rest take one.
        assertTrue(ItemArchetype.SPEAR.twoHanded)
        assertTrue(ItemArchetype.BOW.twoHanded)
        assertTrue(ItemArchetype.entries.filter { it.isWeapon }.all { it.wear?.isHand == true })
        // Chest pieces guard the chest; rings ring; charms hang.
        assertEquals(WearSlot.CHEST, ItemArchetype.HAUBERK.wear)
        assertEquals(WearSlot.HEAD, ItemArchetype.HELM.wear)
        assertEquals(WearSlot.FEET, ItemArchetype.BOOTS.wear)
        assertEquals(WearSlot.LEFT_RING, ItemArchetype.SIGNET.wear)
        assertEquals(WearSlot.ACCESSORY_0, ItemArchetype.CHARM.wear)
        assertEquals(WearSlot.NECK, ItemArchetype.AMULET.wear)
        // Remedies are not made to be worn; the torch rides the hand and burns blunt.
        assertNull(ItemArchetype.REMEDY.wear)
        assertEquals(WearSlot.RIGHT_HAND, ItemArchetype.TORCH.wear)
        assertEquals(DamageType.BLUNT, ItemArchetype.TORCH.damageType)
        assertTrue(ItemArchetype.TORCH.wear?.isHand == true)
    }

    @Test
    fun theArmsRosterRunsFromClubToHalberd() {
        // Every long arm claims both hands; every short one takes a single hand.
        val twoHanded = setOf(
            ItemArchetype.SPEAR, ItemArchetype.BOW, ItemArchetype.QUARTERSTAFF, ItemArchetype.KATANA,
            ItemArchetype.BILL, ItemArchetype.DANE_AXE, ItemArchetype.GLAIVE, ItemArchetype.GUANDAO,
            ItemArchetype.PUDAO, ItemArchetype.SOVNYA, ItemArchetype.NAGINATA, ItemArchetype.BARDICHE,
            ItemArchetype.WAR_SCYTHE, ItemArchetype.BEC_DE_CORBIN, ItemArchetype.ESTOC, ItemArchetype.PIKE,
            ItemArchetype.POLEAXE, ItemArchetype.HALBERD, ItemArchetype.HARPOON, ItemArchetype.TRIDENT,
            ItemArchetype.LANCE, ItemArchetype.PLANCON_A_PICOT,
            // The ranged arms of the two-hand craft: steel bent by limb, crank or charge.
            ItemArchetype.CROSSBOW, ItemArchetype.ARQUEBUS,
            ItemArchetype.HAND_CANNON, ItemArchetype.THREE_EYE_CANNON
        )
        ItemArchetype.WEAPONS.forEach { weapon ->
            assertEquals("$weapon hands", weapon in twoHanded, weapon.twoHanded)
            assertTrue("$weapon wears a hand", weapon.wear?.isHand == true)
            assertTrue("$weapon harms", weapon.baseDamage > 0)
        }
        // Every harm is dealt by several shapes at least.
        DamageType.entries.forEach { type ->
            assertTrue(type.label, ItemArchetype.WEAPONS.count { it.damageType == type } >= 5)
        }
        // The torch is not a soldier's arm, but it is held, and it burns.
        assertFalse(ItemArchetype.TORCH.isWeapon)
        assertTrue(Item(ItemArchetype.TORCH, Material.ASHWOOD).damage() > 0)

        // Culture nouns exist for every shape; a cultureless piece falls back to its label.
        val world = WorldGenerator.generate(77L)
        val roster = StyleRoster(world)
        ItemArchetype.WEAPONS.forEach { weapon ->
            val item = Item(weapon, Material.BONE, Quality.HONEST, world.cultures.first().id)
            assertTrue("${roster.nameFor(item)}", roster.nameFor(item).startsWith("Bone "))
        }
        val orphan = Item(ItemArchetype.HALBERD, Material.BOG_IRON, cultureId = -1)
        assertEquals("Bog-Iron Halberd", roster.nameFor(orphan))
    }

    @Test
    fun theArmoryDressesEveryPlaceAndKnowsItsHarms() {
        // Every body-piece carries a temper of its own: cloth, leather, mail, scale or plate.
        val pieces = ItemArchetype.entries.filter { it.slot == ItemSlot.BODY }
        assertTrue(pieces.size >= 30)
        pieces.forEach { piece ->
            assertTrue("$piece untempered", piece.armorTemper != ArmorTemper.EVEN)
            assertTrue("$piece heavy for nothing", piece.baseWeight > 0f)
            assertTrue("$piece worth nothing", piece.baseValue > 0)
        }

        // Each temper has its own opinion of each harm.
        assertTrue("plate turns the edge", ArmorTemper.PLATE.forType(DamageType.SHARP) > ArmorTemper.PLATE.forType(DamageType.BLUNT))
        assertTrue("cloth spreads the maul", ArmorTemper.CLOTH.forType(DamageType.BLUNT) > ArmorTemper.CLOTH.forType(DamageType.SHARP))
        assertTrue("mail takes the point", ArmorTemper.MAIL.forType(DamageType.PIERCE) > ArmorTemper.MAIL.forType(DamageType.BLUNT))
        assertTrue("leather turns the cut", ArmorTemper.LEATHER.forType(DamageType.SHARP) > ArmorTemper.LEATHER.forType(DamageType.PIERCE))

        // Same bulk, same make, same material: the temper decides what a piece turns.
        val gambeson = Item(ItemArchetype.GAMBESON, Material.BOG_IRON, Quality.HONEST)
        val haubergeon = Item(ItemArchetype.HAUBERGEON, Material.BOG_IRON, Quality.HONEST)
        val gorget = Item(ItemArchetype.GORGET, Material.BOG_IRON, Quality.HONEST)
        // Plate turns the edge past mail, and mail past quilted cloth.
        assertTrue(gorget.armorValue(DamageType.SHARP) > haubergeon.armorValue(DamageType.SHARP))
        assertTrue(gorget.armorValue(DamageType.SHARP) > gambeson.armorValue(DamageType.SHARP))
        // Cloth spreads the maul past steel and rings alike.
        assertTrue(gambeson.armorValue(DamageType.BLUNT) > gorget.armorValue(DamageType.BLUNT))
        assertTrue(gambeson.armorValue(DamageType.BLUNT) > haubergeon.armorValue(DamageType.BLUNT))
        // And the point finds cloth sooner than steel.
        val glassGorget = Item(ItemArchetype.GORGET, Material.SALT_GLASS, Quality.HONEST)
        val glassGambeson = Item(ItemArchetype.GAMBESON, Material.SALT_GLASS, Quality.HONEST)
        assertTrue(glassGorget.armorValue(DamageType.PIERCE) > glassGambeson.armorValue(DamageType.PIERCE))

        // Every place has something made for it: armor for each body place, and the
        // right ring and the spare accessory places fill by order from their kin.
        val dressed = ItemArchetype.entries.mapNotNull { it.wear }.toSet()
        val byFillOrder = setOf(WearSlot.RIGHT_RING, WearSlot.ACCESSORY_1, WearSlot.ACCESSORY_2, WearSlot.ACCESSORY_3)
        WearSlot.entries.filter { !it.isHand && it !in byFillOrder }.forEach { slot ->
            assertTrue("$slot undressed", slot in dressed)
        }

        // Trinkets stay money, not armor: no temper, honest worth, a sensible place.
        ItemArchetype.entries.filter { it.slot == ItemSlot.TRINKET }.forEach { trinket ->
            assertEquals(ArmorTemper.EVEN, trinket.armorTemper)
            assertTrue("$trinket worthless", trinket.baseValue > 0)
            assertTrue(
                "$trinket place",
                trinket.wear?.isRing == true || trinket.wear?.isAccessory == true || trinket.wear == WearSlot.NECK
            )
        }
        assertTrue(ItemArchetype.entries.count { it.wear?.isRing == true } >= 4)
        assertTrue(ItemArchetype.entries.count { it.wear?.isAccessory == true } >= 4)

        // Culture words for armor and trinkets; the cultureless fall back to labels.
        val world = WorldGenerator.generate(77L)
        val roster = StyleRoster(world)
        val culture = world.cultures.first().id
        pieces.forEach { piece ->
            val item = Item(piece, Material.BONE, Quality.HONEST, culture)
            assertTrue("${roster.nameFor(item)}", roster.nameFor(item).startsWith("Bone "))
        }
        ItemArchetype.entries.filter { it.slot == ItemSlot.TRINKET }.forEach { trinket ->
            val item = Item(trinket, Material.BRASS, Quality.HONEST, culture)
            assertTrue("${roster.nameFor(item)}", roster.nameFor(item).startsWith("Brass "))
        }
        assertEquals("Bog-Iron Plate Cuirass", roster.nameFor(Item(ItemArchetype.CUIRASS, Material.BOG_IRON, cultureId = -1)))
        assertEquals("Brass Gemmed Ring", roster.nameFor(Item(ItemArchetype.GEM_RING, Material.BRASS, cultureId = -1)))
    }

    @Test
    fun itemsSurviveASaveRoundTrip() {
        val items = listOf(
            Item(ItemArchetype.BLADE, Material.SALT_GLASS, Quality.FINE, 3),
            Item(ItemArchetype.REMEDY, Material.VERDIGRIS, Quality.HONEST, -1, 6),
            Item(ItemArchetype.HAUBERK, Material.BOG_IRON, Quality.WORN, 1),
            Item(ItemArchetype.CHARM, Material.BONE, Quality.HONEST, 2),
            Item(ItemArchetype.BLADE, Material.STAR_IRON, Quality.FINE, 2),
            Item(ItemArchetype.MACE, Material.EARTH_BONE, Quality.HONEST, -1),
            Item(ItemArchetype.DAGGER, Material.SPIRIT_SILVER, Quality.SUPERB, 4)
        )
        val encoded = items.joinToString("\u001E") { it.encode() }
        val restored = encoded.split("\u001E").mapNotNull { Item.fromEncoded(it) }
        assertEquals(items, restored)
        // A blade refuses to come back stacked, whatever the string claimed.
        val stackedBlade = Item.fromEncoded("BLADE=BRASS=FINE=2=7")
        assertNotNull(stackedBlade)
        assertEquals(1, stackedBlade!!.count)
    }

    @Test
    fun mangledEncodesWakeNull() {
        assertNull(Item.fromEncoded(null))
        assertNull(Item.fromEncoded(""))
        assertNull(Item.fromEncoded("nonsense"))
        assertNull(Item.fromEncoded("BLADE=NO_SUCH_MATERIAL=FINE=1=1"))
        assertNull(Item.fromEncoded("NO_SHAPE=BRASS=FINE=1=1"))
        assertNull(Item.fromEncoded("BLADE=BRASS=NO_MAKE=1=1"))
    }

    @Test
    fun everyPeopleWorksWhatTheirLandGives() {
        val world = WorldGenerator.generate(4242L)
        val geography = MaterialGeography(world)
        val again = MaterialGeography(world)

        world.cultures.forEach { culture ->
            // The same world yields the same regions, always.
            assertEquals(geography.favored(culture.id), again.favored(culture.id))
            assertEquals(geography.available(culture.id), again.available(culture.id))
            assertTrue("${culture.id} holds something", geography.available(culture.id).size >= 2)
        }

        // Rolls stay inside the region's pool, and favor what the land favors.
        val culture = world.cultures.first()
        val pool = geography.available(culture.id).toSet()
        val rng = Random(99L)
        val tally = mutableMapOf<Material, Int>()
        repeat(300) {
            val material = geography.roll(rng, culture.id)
            assertTrue(material in pool)
            tally[material] = (tally[material] ?: 0) + 1
        }
        assertEquals(geography.favored(culture.id), tally.maxByOrNull { it.value }!!.key)

        // A cultureless roll falls back to the province's own rarity.
        assertNotNull(geography.roll(rng, -1))
    }

    @Test
    fun lootRespectsTheRegionMaterial() {
        val world = WorldGenerator.generate(4242L)
        val geography = MaterialGeography(world)
        val culture = world.cultures.first()
        val pool = geography.available(culture.id).toSet()
        val rng = Random(4242L)

        repeat(12) {
            Loot.kit(rng, null, 2, culture.id, geography).forEach { item ->
                assertTrue("${item.material} outside the region", item.material in pool)
            }
        }
    }
}
