package com.rork.hollowmarch.game

import kotlin.random.Random

/** What the dead carried and what the living stashed, rolled from class, level and culture. */
object Loot {

    /** The shapes each calling trusts: a warden swings a blade, a skulk a dagger. */
    private val CLASS_WEAPONS = mapOf(
        "warden" to listOf(ItemArchetype.BLADE, ItemArchetype.ARMING_SWORD, ItemArchetype.FALCHION, ItemArchetype.AXE),
        "skulk" to listOf(ItemArchetype.DAGGER, ItemArchetype.KNIFE, ItemArchetype.MESSER, ItemArchetype.SHORTSWORD),
        "caller" to listOf(ItemArchetype.DAGGER, ItemArchetype.KNIFE, ItemArchetype.QUARTERSTAFF),
        "cantor" to listOf(ItemArchetype.MACE, ItemArchetype.FLANGED_MACE, ItemArchetype.SHESTOPYOR, ItemArchetype.BLADE),
        "wayfarer" to listOf(ItemArchetype.SPEAR, ItemArchetype.PIKE, ItemArchetype.NAGINATA, ItemArchetype.BOW, ItemArchetype.HARPOON),
        "bulwark" to listOf(ItemArchetype.MACE, ItemArchetype.WAR_HAMMER, ItemArchetype.MORNING_STAR, ItemArchetype.BATTLE_AXE, ItemArchetype.HALBERD),
        "hedgewitch" to listOf(ItemArchetype.DAGGER, ItemArchetype.CLUB, ItemArchetype.MESSER),
        "ascetic" to listOf(ItemArchetype.QUARTERSTAFF, ItemArchetype.CLUB, ItemArchetype.DAGGER)
    )

    /** The callings that keep a board on the arm. */
    private val SHIELD_CLASSES = setOf("warden", "bulwark", "cantor")

    /** What the dead wear, from rags to the grave-goods of the well-armed. */
    private val ARMOR_TIERS = listOf(
        // Rags and padding: what the poor are buried in.
        listOf(
            ItemArchetype.WRAP, ItemArchetype.HOOD, ItemArchetype.BOOTS, ItemArchetype.MANTLE,
            ItemArchetype.SHOES, ItemArchetype.HOSE, ItemArchetype.BRACERS, ItemArchetype.GLOVES
        ),
        // Leather and the first rings of mail: the working soldier's dress.
        listOf(
            ItemArchetype.JERKIN, ItemArchetype.GAMBESON, ItemArchetype.CAP, ItemArchetype.LEGGINGS,
            ItemArchetype.COIF, ItemArchetype.HAUBERGEON, ItemArchetype.AVENTAIL, ItemArchetype.MITTENS,
            ItemArchetype.SLEEVES, ItemArchetype.SPAULDER, ItemArchetype.SCALE_GREAVES
        ),
        // Riveted lames and the smith's heavy rings.
        listOf(
            ItemArchetype.HAUBERK, ItemArchetype.CHAUSSES, ItemArchetype.BRIGANDINE, ItemArchetype.HELM,
            ItemArchetype.GAUNTLETS, ItemArchetype.GREAVES, ItemArchetype.PAULDRON
        ),
        // Plate: the grave-goods of the well-armed.
        listOf(
            ItemArchetype.CUIRASS, ItemArchetype.BARBUTE, ItemArchetype.GORGET,
            ItemArchetype.VAMBRACE, ItemArchetype.TASSETS, ItemArchetype.SABATONS
        )
    )

    /** Every wearable piece, for graves that hold a whole outfit. */
    val ARMOR_POOL = ARMOR_TIERS.flatten()

    private val TRINKETS = listOf(
        ItemArchetype.CHARM, ItemArchetype.SIGNET, ItemArchetype.BAND,
        ItemArchetype.SEAL_RING, ItemArchetype.GEM_RING, ItemArchetype.AMULET,
        ItemArchetype.TORC, ItemArchetype.MEDALLION, ItemArchetype.TALISMAN, ItemArchetype.RELIC
    )

    /** A fighter's kit: a favored weapon, sometimes armor, sometimes a trinket. */
    fun kit(
        rng: Random,
        klass: ActorClass?,
        level: Int,
        cultureId: Int,
        geography: MaterialGeography? = null
    ): List<Item> {
        val items = mutableListOf<Item>()
        val favored = klass?.let { CLASS_WEAPONS[it.key] }
        val weapon = favored?.get(rng.nextInt(favored.size))
            ?: ItemArchetype.WEAPONS[rng.nextInt(ItemArchetype.WEAPONS.size)]
        items += Item(weapon, rollMaterial(rng, cultureId, geography), Quality.roll(rng), cultureId).marked()
        // An archer carries the quiver its arm asks for; a thrown arm carries no shot.
        Ranged.kindOf(weapon)?.let { kind ->
            Ranged.ammoOf(kind).firstOrNull()?.let { shot ->
                items += Ranged.rollAmmo(rng, shot, cultureId, 8 + rng.nextInt(10))
            }
        }
        // A fighter of the shield callings sometimes bears a board with the arm;
        // never with a long arm that would claim the hand the board wants.
        if (klass != null && klass.key in SHIELD_CLASSES && !weapon.twoHanded && rng.nextInt(3) == 0) {
            items += Shields.roll(rng, level, cultureId, geography)
        }
        if (rng.nextInt(2) == 0) {
            val tier = (rng.nextInt(6).coerceAtMost(level / 3 + 1)).coerceIn(0, ARMOR_TIERS.lastIndex)
            val pool = ARMOR_TIERS[tier]
            items += Item(pool[rng.nextInt(pool.size)], rollMaterial(rng, cultureId, geography), Quality.roll(rng), cultureId).marked()
        }
        if (rng.nextInt(4) == 0) {
            items += Item(TRINKETS[rng.nextInt(TRINKETS.size)], rollMaterial(rng, cultureId, geography), Quality.HONEST, cultureId).marked()
        }
        return items
    }

    /** What the region yields, when the geography speaks; the province's own odds otherwise. */
    private fun rollMaterial(rng: Random, cultureId: Int, geography: MaterialGeography?): Material =
        geography?.roll(rng, cultureId) ?: Material.roll(rng)

    /** A handful of coins on the dead: a little more the bigger they were. */
    fun brass(rng: Random, level: Int): Int = 1 + rng.nextInt(3) + level / 2

    /** What a grave or urn holds: a possession of the buried, and a few coins. */
    fun containerContents(
        rng: Random,
        depth: Int,
        underground: Boolean,
        cultureId: Int = -1,
        geography: MaterialGeography? = null
    ): Pair<List<Item>, Int> {
        val items = mutableListOf<Item>()
        repeat(1 + rng.nextInt(2)) {
            items += when (rng.nextInt(8)) {
                0, 1 -> Item(
                    ItemArchetype.WEAPONS[rng.nextInt(ItemArchetype.WEAPONS.size)],
                    rollMaterial(rng, cultureId, geography), Quality.roll(rng)
                ).marked()
                2 -> Item(ItemArchetype.REMEDY, Material.VERDIGRIS, count = 1 + rng.nextInt(2))
                3 -> Item(ItemArchetype.TORCH, Material.ASHWOOD, count = 1 + rng.nextInt(2))
                4, 5, 6 -> Item(
                    TRINKETS[rng.nextInt(TRINKETS.size)],
                    rollMaterial(rng, cultureId, geography), Quality.roll(rng)
                ).marked()
                else -> Item(
                    ARMOR_POOL[rng.nextInt(ARMOR_POOL.size)],
                    rollMaterial(rng, cultureId, geography), Quality.roll(rng)
                ).marked()
            }
        }
        val coins = 2 + rng.nextInt(4) + depth + (if (underground) rng.nextInt(3) else 0)
        return Pair(items, coins)
    }
}
