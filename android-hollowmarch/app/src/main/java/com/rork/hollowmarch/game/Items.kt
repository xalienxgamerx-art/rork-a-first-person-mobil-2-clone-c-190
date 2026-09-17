package com.rork.hollowmarch.game

import com.rork.hollowmarch.world.World
import kotlin.math.roundToInt
import kotlin.random.Random

/** The three ways the province's arms hurt: edged, crushing, and through the joints. */
enum class DamageType(val label: String) {
    SHARP("sharp"),
    BLUNT("blunt"),
    PIERCE("pierce")
}

/** What a thing is for; where each kind actually sits is its [ItemArchetype.wear]. */
enum class ItemSlot(val label: String) {
    WEAPON("weapon"),
    BODY("body"),
    TRINKET("trinket"),
    CONSUMABLE("consumable"),
    MISC("misc"),
    SHIELD("shield"),
    AMMUNITION("ammunition")
}

/** The seventeen places a body holds or bears a thing, in wearing order. */
enum class WearSlot(val label: String) {
    HEAD("head"),
    NECK("neck"),
    SHOULDERS("shoulders"),
    CHEST("chest"),
    WRISTS("wrists"),
    HANDS("hands"),
    RIGHT_HAND("right hand"),
    LEFT_HAND("left hand"),
    RIGHT_RING("right ring"),
    LEFT_RING("left ring"),
    LEGS("legs"),
    SHINS("shins"),
    FEET("feet"),
    ACCESSORY_0("accessory"),
    ACCESSORY_1("accessory"),
    ACCESSORY_2("accessory"),
    ACCESSORY_3("accessory");

    val isHand: Boolean get() = this == RIGHT_HAND || this == LEFT_HAND
    val isRing: Boolean get() = this == RIGHT_RING || this == LEFT_RING
    val isAccessory: Boolean get() = ordinal >= ACCESSORY_0.ordinal
}

/**
 * The province's materials, each with its own temper: weight, swing, worth, and
 * what it does best. Bog iron is crude but everywhere; steel is the smith's
 * pride; gold cannot hold an edge and does not care. The arcane three —
 * star-iron, spirit-silver, earth-bone — carry the mark of the otherwhere,
 * waiting for a later age to wake what sleeps in them.
 */
enum class Material(
    val label: String,
    val adjective: String,
    private val weightMult: Float,
    private val speedMult: Float,
    private val valueMult: Float,
    private val sharp: Float,
    private val blunt: Float,
    private val pierce: Float,
    val rarity: Int,
    /** The hue of the worked thing: what the blade takes when the light strikes it. */
    val tint: Int,
    val arcane: Boolean = false
) {
    BOG_IRON("bog iron", "Bog-Iron", 1.30f, 0.85f, 0.70f, 1.00f, 1.15f, 0.90f, 30, 0x9C7048),
    VERDIGRIS("verdigris bronze", "Verdigris", 1.00f, 1.05f, 1.00f, 1.10f, 0.90f, 1.00f, 25, 0x74A889),
    COPPER("copper", "Copper", 1.00f, 1.05f, 0.50f, 0.95f, 0.90f, 0.90f, 28, 0xC57F45),
    IRON("iron", "Iron", 1.15f, 0.95f, 0.90f, 1.05f, 1.10f, 1.00f, 20, 0xA2A6AB),
    LEAD("lead", "Lead", 1.70f, 0.65f, 0.60f, 0.60f, 1.40f, 0.70f, 12, 0x8A93A6),
    BRASS("temper brass", "Brass", 0.90f, 1.10f, 1.40f, 0.90f, 0.85f, 1.10f, 15, 0xD0AA52),
    BONE("barrow bone", "Bone", 0.60f, 1.20f, 0.60f, 1.15f, 0.70f, 0.85f, 15, 0xE8DDB9),
    ASHWOOD("ashwood", "Ashwood", 0.70f, 1.10f, 0.50f, 0.70f, 1.10f, 0.90f, 10, 0xB29468),
    STEEL("steel", "Steel", 0.95f, 1.05f, 1.80f, 1.25f, 1.00f, 1.15f, 8, 0xCDD3D8),
    SILVER("silver", "Silver", 0.90f, 1.05f, 2.40f, 1.05f, 0.80f, 1.10f, 5, 0xEAF0F3),
    GOLD("gold", "Gold", 1.35f, 0.85f, 4.00f, 0.55f, 0.75f, 0.60f, 2, 0xEBC94F),
    SALT_GLASS("salt-glass", "Salt-Glass", 0.55f, 1.15f, 1.60f, 0.85f, 0.50f, 1.30f, 3, 0xC2E4DE),
    GRAVE_SLATE("grave-slate", "Grave-Slate", 1.60f, 0.70f, 0.55f, 0.75f, 1.35f, 0.70f, 2, 0x75797F),
    STAR_IRON("star-iron", "Star-Iron", 0.75f, 1.15f, 3.50f, 1.35f, 0.90f, 1.25f, 2, 0x9A88D6, arcane = true),
    SPIRIT_SILVER("spirit-silver", "Spirit-Silver", 0.60f, 1.20f, 4.00f, 1.15f, 0.60f, 1.35f, 2, 0xB6D6EE, arcane = true),
    EARTH_BONE("earth-bone", "Earth-Bone", 1.40f, 0.85f, 4.50f, 1.20f, 1.30f, 1.10f, 2, 0xC2A06B, arcane = true);

    fun damageMult(type: DamageType): Float = when (type) {
        DamageType.SHARP -> sharp
        DamageType.BLUNT -> blunt
        DamageType.PIERCE -> pierce
    }

    fun weightFactor(): Float = weightMult
    fun speedFactor(): Float = speedMult
    fun valueFactor(): Float = valueMult

    companion object {
        /** Pick a material by its rarity weight, deterministically under the given rng. */
        fun roll(rng: Random): Material {
            val total = entries.sumOf { it.rarity }
            var roll = rng.nextInt(total)
            for (material in entries) {
                roll -= material.rarity
                if (roll < 0) return material
            }
            return entries.first()
        }
    }
}

/**
 * How well a piece was made, whatever it was made of: six makes from the shoddy
 * work of desperate hands to the legendary pieces no living smith could repeat.
 * The make sharpens the blade, hardens the guard, and sets the worth.
 */
enum class Quality(val label: String, val damageMult: Float, val armorMult: Float, val valueMult: Float) {
    SHODDY("shoddy", 0.70f, 0.55f, 0.45f),
    WORN("worn", 0.85f, 0.80f, 0.70f),
    HONEST("honest", 1.00f, 1.00f, 1.00f),
    FINE("fine", 1.15f, 1.25f, 1.60f),
    SUPERB("superb", 1.30f, 1.55f, 2.60f),
    LEGENDARY("legendary", 1.50f, 2.00f, 5.00f);

    companion object {
        private val WEIGHTS = listOf(
            SHODDY to 9, WORN to 12, HONEST to 12, FINE to 7, SUPERB to 3, LEGENDARY to 1
        )

        /** The workshop's roll: mostly shoddy and honest, a legend once in a lifetime. */
        fun roll(rng: Random): Quality {
            var pick = rng.nextInt(WEIGHTS.sumOf { it.second })
            WEIGHTS.forEach { (quality, weight) ->
                pick -= weight
                if (pick < 0) return quality
            }
            return HONEST
        }
    }
}

/**
 * How a piece of armor meets each harm, by its own kind: quilted cloth spreads
 * the maul and takes the edge; hardened leather turns the cut; mail turns the
 * point and the edge, never the maul; plate is proof against the edge and dent
 * hollow under it. Even tempers nothing — a ring guards nothing of itself.
 */
enum class ArmorTemper(val sharp: Float, val blunt: Float, val pierce: Float) {
    EVEN(1.0f, 1.0f, 1.0f),
    CLOTH(0.6f, 1.4f, 0.8f),
    LEATHER(1.2f, 0.7f, 0.9f),
    MAIL(1.3f, 0.7f, 1.3f),
    SCALE(1.2f, 0.9f, 1.2f),
    PLATE(1.6f, 0.7f, 1.3f);

    fun forType(type: DamageType): Float = when (type) {
        DamageType.SHARP -> sharp
        DamageType.BLUNT -> blunt
        DamageType.PIERCE -> pierce
    }
}

/**
 * A kind of thing the province makes: its shape, what it is for, and its honest
 * numbers before the material's temper is worked in.
 */
enum class ItemArchetype(
    val label: String,
    val slot: ItemSlot,
    val baseDamage: Int,
    val damageType: DamageType,
    val baseWeight: Float,
    val baseValue: Int,
    val twoHanded: Boolean,
    val speedFactor: Float = 1f,
    val baseArmor: Int = 0,
    val armorTemper: ArmorTemper = ArmorTemper.EVEN,
    val wear: WearSlot? = null
) {
    // Weapons — one in each hand, or a long arm that claims both
    BLADE("Blade", ItemSlot.WEAPON, 7, DamageType.SHARP, 2.0f, 20, false, wear = WearSlot.RIGHT_HAND),
    AXE("Axe", ItemSlot.WEAPON, 8, DamageType.SHARP, 2.6f, 18, false, wear = WearSlot.RIGHT_HAND),
    MACE("Mace", ItemSlot.WEAPON, 7, DamageType.BLUNT, 3.0f, 16, false, wear = WearSlot.RIGHT_HAND),
    SPEAR("Spear", ItemSlot.WEAPON, 7, DamageType.PIERCE, 2.2f, 18, true, wear = WearSlot.RIGHT_HAND),
    DAGGER("Dagger", ItemSlot.WEAPON, 4, DamageType.PIERCE, 0.8f, 12, false, 1.30f, wear = WearSlot.RIGHT_HAND),
    BOW("Bow", ItemSlot.WEAPON, 6, DamageType.PIERCE, 1.2f, 26, true, wear = WearSlot.RIGHT_HAND),
    CROSSBOW("Crossbow", ItemSlot.WEAPON, 6, DamageType.PIERCE, 2.6f, 30, true, wear = WearSlot.RIGHT_HAND),
    ARQUEBUS("Arquebus", ItemSlot.WEAPON, 8, DamageType.BLUNT, 3.4f, 44, true, wear = WearSlot.RIGHT_HAND),
    HAND_CANNON("Hand Cannon", ItemSlot.WEAPON, 9, DamageType.BLUNT, 4.2f, 38, true, wear = WearSlot.RIGHT_HAND),
    THREE_EYE_CANNON("Three-Eye Cannon", ItemSlot.WEAPON, 10, DamageType.BLUNT, 6.0f, 52, true, wear = WearSlot.RIGHT_HAND),
    SLING("Sling", ItemSlot.WEAPON, 2, DamageType.BLUNT, 0.3f, 6, false, wear = WearSlot.RIGHT_HAND),

    // Thrown arms — spent with the hand, gone into the air, gathered up again after
    THROWING_KNIFE("Throwing Knife", ItemSlot.WEAPON, 3, DamageType.PIERCE, 0.2f, 8, false, 1.20f, wear = WearSlot.RIGHT_HAND),
    FRANCISCA("Francisca", ItemSlot.WEAPON, 4, DamageType.SHARP, 0.5f, 10, false, 1.15f, wear = WearSlot.RIGHT_HAND),
    CHAKRAM("Chakram", ItemSlot.WEAPON, 3, DamageType.SHARP, 0.4f, 12, false, 1.20f, wear = WearSlot.RIGHT_HAND),
    SHURIKEN("Shuriken", ItemSlot.WEAPON, 2, DamageType.PIERCE, 0.1f, 6, false, 1.30f, wear = WearSlot.RIGHT_HAND),

    // Blunt arms — the crushing trades, from the hedgerow club to the ribbed mace
    CLUB("Club", ItemSlot.WEAPON, 5, DamageType.BLUNT, 1.4f, 4, false, 1.10f, wear = WearSlot.RIGHT_HAND),
    BLUDGEON("Bludgeon", ItemSlot.WEAPON, 6, DamageType.BLUNT, 2.2f, 8, false, 1.00f, wear = WearSlot.RIGHT_HAND),
    FLAIL("Flail", ItemSlot.WEAPON, 8, DamageType.BLUNT, 3.0f, 22, false, 0.95f, wear = WearSlot.RIGHT_HAND),
    FLANGED_MACE("Flanged Mace", ItemSlot.WEAPON, 8, DamageType.BLUNT, 3.2f, 24, false, 0.95f, wear = WearSlot.RIGHT_HAND),
    MORNING_STAR("Morning Star", ItemSlot.WEAPON, 9, DamageType.BLUNT, 3.6f, 26, false, 0.90f, wear = WearSlot.RIGHT_HAND),
    WAR_HAMMER("War Hammer", ItemSlot.WEAPON, 9, DamageType.BLUNT, 3.8f, 28, false, 0.85f, wear = WearSlot.RIGHT_HAND),
    SHESTOPYOR("Shestopyor", ItemSlot.WEAPON, 10, DamageType.BLUNT, 4.0f, 32, false, 0.80f, wear = WearSlot.RIGHT_HAND),
    QUARTERSTAFF("Quarterstaff", ItemSlot.WEAPON, 7, DamageType.BLUNT, 1.8f, 10, true, 1.10f, wear = WearSlot.RIGHT_HAND),

    // Sharp arms held in one hand — swords and cleaving axes of every country
    SHORTSWORD("Shortsword", ItemSlot.WEAPON, 6, DamageType.SHARP, 1.4f, 16, false, 1.15f, wear = WearSlot.RIGHT_HAND),
    FALCHION("Falchion", ItemSlot.WEAPON, 8, DamageType.SHARP, 1.8f, 20, false, 1.05f, wear = WearSlot.RIGHT_HAND),
    MESSER("Messer", ItemSlot.WEAPON, 8, DamageType.SHARP, 1.9f, 22, false, 1.05f, wear = WearSlot.RIGHT_HAND),
    SABRE("Sabre", ItemSlot.WEAPON, 7, DamageType.SHARP, 1.5f, 22, false, 1.15f, wear = WearSlot.RIGHT_HAND),
    SCIMITAR("Scimitar", ItemSlot.WEAPON, 7, DamageType.SHARP, 1.5f, 24, false, 1.15f, wear = WearSlot.RIGHT_HAND),
    ARMING_SWORD("Arming Sword", ItemSlot.WEAPON, 8, DamageType.SHARP, 1.7f, 28, false, 1.05f, wear = WearSlot.RIGHT_HAND),
    ULFBERHT("Ulfberht", ItemSlot.WEAPON, 9, DamageType.SHARP, 1.8f, 40, false, 1.05f, wear = WearSlot.RIGHT_HAND),
    BATTLE_AXE("Battle Axe", ItemSlot.WEAPON, 9, DamageType.SHARP, 2.8f, 26, false, 0.95f, wear = WearSlot.RIGHT_HAND),

    // Sharp long arms — great blades and polearms that claim both hands
    KATANA("Katana", ItemSlot.WEAPON, 9, DamageType.SHARP, 1.6f, 44, true, 1.10f, wear = WearSlot.RIGHT_HAND),
    BILL("Bill", ItemSlot.WEAPON, 11, DamageType.SHARP, 3.4f, 30, true, 0.80f, wear = WearSlot.RIGHT_HAND),
    DANE_AXE("Dane Axe", ItemSlot.WEAPON, 12, DamageType.SHARP, 3.6f, 34, true, 0.80f, wear = WearSlot.RIGHT_HAND),
    GLAIVE("Glaive", ItemSlot.WEAPON, 12, DamageType.SHARP, 3.2f, 32, true, 0.85f, wear = WearSlot.RIGHT_HAND),
    GUANDAO("Guandao", ItemSlot.WEAPON, 12, DamageType.SHARP, 3.5f, 34, true, 0.85f, wear = WearSlot.RIGHT_HAND),
    PUDAO("Pudao", ItemSlot.WEAPON, 11, DamageType.SHARP, 3.4f, 30, true, 0.80f, wear = WearSlot.RIGHT_HAND),
    SOVNYA("Sovnya", ItemSlot.WEAPON, 11, DamageType.SHARP, 3.3f, 32, true, 0.85f, wear = WearSlot.RIGHT_HAND),
    NAGINATA("Naginata", ItemSlot.WEAPON, 11, DamageType.SHARP, 3.0f, 32, true, 0.90f, wear = WearSlot.RIGHT_HAND),
    BARDICHE("Bardiche", ItemSlot.WEAPON, 13, DamageType.SHARP, 3.8f, 36, true, 0.75f, wear = WearSlot.RIGHT_HAND),
    WAR_SCYTHE("War Scythe", ItemSlot.WEAPON, 10, DamageType.SHARP, 2.8f, 22, true, 0.85f, wear = WearSlot.RIGHT_HAND),

    // Piercing arms — the point that finds the seam, in hand or at pole's length
    KNIFE("Knife", ItemSlot.WEAPON, 3, DamageType.PIERCE, 0.5f, 8, false, 1.35f, wear = WearSlot.RIGHT_HAND),
    RAPIER("Rapier", ItemSlot.WEAPON, 7, DamageType.PIERCE, 1.2f, 30, false, 1.20f, wear = WearSlot.RIGHT_HAND),
    HORSEMANS_PICK("Horseman's Pick", ItemSlot.WEAPON, 8, DamageType.PIERCE, 2.4f, 24, false, 0.95f, wear = WearSlot.RIGHT_HAND),
    ESTOC("Estoc", ItemSlot.WEAPON, 10, DamageType.PIERCE, 1.9f, 36, true, 0.95f, wear = WearSlot.RIGHT_HAND),
    PIKE("Pike", ItemSlot.WEAPON, 9, DamageType.PIERCE, 3.0f, 24, true, 0.80f, wear = WearSlot.RIGHT_HAND),
    POLEAXE("Poleaxe", ItemSlot.WEAPON, 12, DamageType.PIERCE, 3.8f, 38, true, 0.75f, wear = WearSlot.RIGHT_HAND),
    HALBERD("Halberd", ItemSlot.WEAPON, 12, DamageType.PIERCE, 3.6f, 38, true, 0.75f, wear = WearSlot.RIGHT_HAND),
    HARPOON("Harpoon", ItemSlot.WEAPON, 8, DamageType.PIERCE, 2.2f, 18, true, 0.90f, wear = WearSlot.RIGHT_HAND),
    TRIDENT("Trident", ItemSlot.WEAPON, 9, DamageType.PIERCE, 2.8f, 24, true, 0.85f, wear = WearSlot.RIGHT_HAND),
    BEC_DE_CORBIN("Bec de Corbin", ItemSlot.WEAPON, 11, DamageType.PIERCE, 3.6f, 34, true, 0.80f, wear = WearSlot.RIGHT_HAND),
    LANCE("Lance", ItemSlot.WEAPON, 11, DamageType.PIERCE, 3.2f, 30, true, 0.75f, wear = WearSlot.RIGHT_HAND),
    PLANCON_A_PICOT("Plançon à Picot", ItemSlot.WEAPON, 10, DamageType.PIERCE, 3.4f, 28, true, 0.80f, wear = WearSlot.RIGHT_HAND),

    // Armor — every place dressed, from quilted cloth to the smith's heavy plate.
    // The chest: wrap, gambeson, jerkin, and the mail and plate over them.
    WRAP("Funeral Wrap", ItemSlot.BODY, 0, DamageType.SHARP, 0.5f, 4, false, baseArmor = 0, armorTemper = ArmorTemper.CLOTH, wear = WearSlot.CHEST),
    GAMBESON("Padded Gambeson", ItemSlot.BODY, 0, DamageType.SHARP, 1.2f, 10, false, baseArmor = 1, armorTemper = ArmorTemper.CLOTH, wear = WearSlot.CHEST),
    JERKIN("Studded Jerkin", ItemSlot.BODY, 0, DamageType.SHARP, 2.0f, 14, false, baseArmor = 1, armorTemper = ArmorTemper.LEATHER, wear = WearSlot.CHEST),
    HAUBERGEON("Haubergeon", ItemSlot.BODY, 0, DamageType.SHARP, 3.4f, 24, false, baseArmor = 1, armorTemper = ArmorTemper.MAIL, wear = WearSlot.CHEST),
    HAUBERK("Hauberk", ItemSlot.BODY, 0, DamageType.SHARP, 4.5f, 30, false, baseArmor = 2, armorTemper = ArmorTemper.MAIL, wear = WearSlot.CHEST),
    BRIGANDINE("Brigandine", ItemSlot.BODY, 0, DamageType.SHARP, 3.6f, 32, false, baseArmor = 2, armorTemper = ArmorTemper.SCALE, wear = WearSlot.CHEST),
    CUIRASS("Plate Cuirass", ItemSlot.BODY, 0, DamageType.SHARP, 5.5f, 42, false, baseArmor = 3, armorTemper = ArmorTemper.PLATE, wear = WearSlot.CHEST),
    // The head: hoods and caps beneath rings and steel.
    HOOD("Hood", ItemSlot.BODY, 0, DamageType.SHARP, 0.3f, 6, false, baseArmor = 0, armorTemper = ArmorTemper.CLOTH, wear = WearSlot.HEAD),
    CAP("Cap", ItemSlot.BODY, 0, DamageType.SHARP, 0.8f, 10, false, baseArmor = 1, armorTemper = ArmorTemper.LEATHER, wear = WearSlot.HEAD),
    COIF("Mail Coif", ItemSlot.BODY, 0, DamageType.SHARP, 1.0f, 14, false, baseArmor = 1, armorTemper = ArmorTemper.MAIL, wear = WearSlot.HEAD),
    HELM("Helm", ItemSlot.BODY, 0, DamageType.SHARP, 1.8f, 22, false, baseArmor = 2, armorTemper = ArmorTemper.PLATE, wear = WearSlot.HEAD),
    BARBUTE("Barbute", ItemSlot.BODY, 0, DamageType.SHARP, 2.4f, 34, false, baseArmor = 3, armorTemper = ArmorTemper.PLATE, wear = WearSlot.HEAD),
    // The neck: mail that hangs and steel that locks.
    AVENTAIL("Mail Aventail", ItemSlot.BODY, 0, DamageType.SHARP, 0.9f, 16, false, baseArmor = 1, armorTemper = ArmorTemper.MAIL, wear = WearSlot.NECK),
    GORGET("Gorget", ItemSlot.BODY, 0, DamageType.SHARP, 1.1f, 20, false, baseArmor = 1, armorTemper = ArmorTemper.PLATE, wear = WearSlot.NECK),
    // The shoulders.
    MANTLE("Quilted Mantle", ItemSlot.BODY, 0, DamageType.SHARP, 0.7f, 6, false, baseArmor = 0, armorTemper = ArmorTemper.CLOTH, wear = WearSlot.SHOULDERS),
    SPAULDER("Spaulders", ItemSlot.BODY, 0, DamageType.SHARP, 1.9f, 18, false, baseArmor = 1, armorTemper = ArmorTemper.SCALE, wear = WearSlot.SHOULDERS),
    PAULDRON("Pauldron", ItemSlot.BODY, 0, DamageType.SHARP, 1.5f, 14, false, baseArmor = 1, armorTemper = ArmorTemper.PLATE, wear = WearSlot.SHOULDERS),
    // The wrists.
    BRACERS("Bracers", ItemSlot.BODY, 0, DamageType.SHARP, 0.7f, 9, false, baseArmor = 0, armorTemper = ArmorTemper.LEATHER, wear = WearSlot.WRISTS),
    SLEEVES("Mail Sleeves", ItemSlot.BODY, 0, DamageType.SHARP, 1.1f, 15, false, baseArmor = 1, armorTemper = ArmorTemper.MAIL, wear = WearSlot.WRISTS),
    VAMBRACE("Vambraces", ItemSlot.BODY, 0, DamageType.SHARP, 1.6f, 18, false, baseArmor = 1, armorTemper = ArmorTemper.PLATE, wear = WearSlot.WRISTS),
    // The hands.
    GLOVES("Gloves", ItemSlot.BODY, 0, DamageType.SHARP, 0.3f, 6, false, baseArmor = 0, armorTemper = ArmorTemper.LEATHER, wear = WearSlot.HANDS),
    MITTENS("Mail Mittens", ItemSlot.BODY, 0, DamageType.SHARP, 0.8f, 14, false, baseArmor = 1, armorTemper = ArmorTemper.MAIL, wear = WearSlot.HANDS),
    GAUNTLETS("Gauntlets", ItemSlot.BODY, 0, DamageType.SHARP, 1.2f, 16, false, baseArmor = 1, armorTemper = ArmorTemper.PLATE, wear = WearSlot.HANDS),
    // The legs.
    HOSE("Padded Hose", ItemSlot.BODY, 0, DamageType.SHARP, 0.8f, 7, false, baseArmor = 0, armorTemper = ArmorTemper.CLOTH, wear = WearSlot.LEGS),
    LEGGINGS("Leggings", ItemSlot.BODY, 0, DamageType.SHARP, 1.6f, 12, false, baseArmor = 1, armorTemper = ArmorTemper.LEATHER, wear = WearSlot.LEGS),
    CHAUSSES("Mail Chausses", ItemSlot.BODY, 0, DamageType.SHARP, 2.6f, 28, false, baseArmor = 2, armorTemper = ArmorTemper.MAIL, wear = WearSlot.LEGS),
    TASSETS("Tassets", ItemSlot.BODY, 0, DamageType.SHARP, 2.8f, 30, false, baseArmor = 2, armorTemper = ArmorTemper.PLATE, wear = WearSlot.LEGS),
    // The shins and the feet.
    SCALE_GREAVES("Scale Greaves", ItemSlot.BODY, 0, DamageType.SHARP, 1.7f, 17, false, baseArmor = 1, armorTemper = ArmorTemper.SCALE, wear = WearSlot.SHINS),
    GREAVES("Greaves", ItemSlot.BODY, 0, DamageType.SHARP, 1.4f, 14, false, baseArmor = 1, armorTemper = ArmorTemper.PLATE, wear = WearSlot.SHINS),
    SHOES("Padded Shoes", ItemSlot.BODY, 0, DamageType.SHARP, 0.4f, 5, false, baseArmor = 0, armorTemper = ArmorTemper.CLOTH, wear = WearSlot.FEET),
    BOOTS("Boots", ItemSlot.BODY, 0, DamageType.SHARP, 0.8f, 8, false, baseArmor = 0, armorTemper = ArmorTemper.LEATHER, wear = WearSlot.FEET),
    SABATONS("Sabatons", ItemSlot.BODY, 0, DamageType.SHARP, 1.4f, 16, false, baseArmor = 1, armorTemper = ArmorTemper.PLATE, wear = WearSlot.FEET),

    // Shields — boards and plates for the left arm, worked by the shieldwright.
    // Their true weight is the wright's own arithmetic, not the baseWeight here.
    BUCKLER("Buckler", ItemSlot.SHIELD, 0, DamageType.SHARP, 1.2f, 14, false, wear = WearSlot.LEFT_HAND),
    ROUND_SHIELD("Round Shield", ItemSlot.SHIELD, 0, DamageType.SHARP, 3.5f, 16, false, wear = WearSlot.LEFT_HAND),
    TARGE("Targe", ItemSlot.SHIELD, 0, DamageType.SHARP, 2.2f, 18, false, wear = WearSlot.LEFT_HAND),
    HEATER_SHIELD("Heater Shield", ItemSlot.SHIELD, 0, DamageType.SHARP, 3.2f, 22, false, wear = WearSlot.LEFT_HAND),
    KITE_SHIELD("Kite Shield", ItemSlot.SHIELD, 0, DamageType.SHARP, 4.5f, 26, false, wear = WearSlot.LEFT_HAND),
    ALMOND_SHIELD("Almond Shield", ItemSlot.SHIELD, 0, DamageType.SHARP, 3.8f, 24, false, wear = WearSlot.LEFT_HAND),
    HUNGARIAN_SHIELD("Hungarian Shield", ItemSlot.SHIELD, 0, DamageType.SHARP, 3.2f, 24, false, wear = WearSlot.LEFT_HAND),
    PAVISE("Pavise", ItemSlot.SHIELD, 0, DamageType.SHARP, 8.5f, 34, false, wear = WearSlot.LEFT_HAND),
    RONDACHE("Rondache", ItemSlot.SHIELD, 0, DamageType.SHARP, 4.8f, 30, false, wear = WearSlot.LEFT_HAND),
    ROTELLA("Rotella", ItemSlot.SHIELD, 0, DamageType.SHARP, 3.6f, 28, false, wear = WearSlot.LEFT_HAND),
    TOWER_SHIELD("Tower Shield", ItemSlot.SHIELD, 0, DamageType.SHARP, 9.5f, 40, false, wear = WearSlot.LEFT_HAND),

    // Trinkets — worn for worth alone, until the day enchantments come
    CHARM("Charm", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 12, false, wear = WearSlot.ACCESSORY_0),
    SIGNET("Signet", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 20, false, wear = WearSlot.LEFT_RING),
    BAND("Band", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 14, false, wear = WearSlot.LEFT_RING),
    SEAL_RING("Seal Ring", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 26, false, wear = WearSlot.LEFT_RING),
    GEM_RING("Gemmed Ring", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 34, false, wear = WearSlot.LEFT_RING),
    AMULET("Amulet", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 24, false, wear = WearSlot.NECK),
    TORC("Torc", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.3f, 30, false, wear = WearSlot.NECK),
    MEDALLION("Medallion", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 22, false, wear = WearSlot.ACCESSORY_0),
    TALISMAN("Talisman", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 18, false, wear = WearSlot.ACCESSORY_0),
    RELIC("Relic", ItemSlot.TRINKET, 0, DamageType.SHARP, 0.1f, 30, false, wear = WearSlot.ACCESSORY_0),

    // Ammunition — the shot every ranged arm spends, bundled and counted
    ARROW("Arrow", ItemSlot.AMMUNITION, 1, DamageType.PIERCE, 0.045f, 1, false),
    BOLT("Bolt", ItemSlot.AMMUNITION, 1, DamageType.PIERCE, 0.060f, 1, false),
    SLING_STONE("Sling Stone", ItemSlot.AMMUNITION, 1, DamageType.BLUNT, 0.055f, 1, false),
    SLING_LEAD_SHOT("Lead Glande", ItemSlot.AMMUNITION, 1, DamageType.BLUNT, 0.082f, 2, false),
    SLING_CLAY_SHOT("Clay Bullet", ItemSlot.AMMUNITION, 1, DamageType.BLUNT, 0.030f, 1, false),
    LEAD_BALL("Lead Ball", ItemSlot.AMMUNITION, 1, DamageType.PIERCE, 0.027f, 2, false),
    IRON_BALL("Iron Shot", ItemSlot.AMMUNITION, 1, DamageType.BLUNT, 0.063f, 3, false),
    STONE_BALL("Stone Ball", ItemSlot.AMMUNITION, 1, DamageType.BLUNT, 0.208f, 2, false),

    // Consumables
    REMEDY("Grey Remedy", ItemSlot.CONSUMABLE, 0, DamageType.SHARP, 0.2f, 15, false),
    // The torch: a burning cudgel, held in a hand like any short arm.
    TORCH("Pitch Torch", ItemSlot.CONSUMABLE, 3, DamageType.BLUNT, 0.5f, 6, false, 1.05f, wear = WearSlot.RIGHT_HAND);

    val isWeapon: Boolean get() = slot == ItemSlot.WEAPON
    val isArmor: Boolean get() = slot == ItemSlot.BODY
    val isShield: Boolean get() = slot == ItemSlot.SHIELD
    val isWearable: Boolean get() = wear != null
    /** The arms that leave the hand and must be gathered up again. */
    val isThrown: Boolean get() = this in THROWN_ARMS
    val stacks: Boolean get() = this == REMEDY || this == TORCH || slot == ItemSlot.AMMUNITION || isThrown

    companion object {
        /** Weapon archetypes a fighting soul might carry. */
        val WEAPONS = entries.filter { it.isWeapon }

        /** The thrown arms, spent from bundles and gathered up again after. */
        val THROWN_ARMS = setOf(THROWING_KNIFE, FRANCISCA, CHAKRAM, SHURIKEN)
    }
}

/**
 * One thing in someone's hands or on the floor: a shape worked in a material,
 * to a quality, in some culture's style. Stackables (remedies, torches) carry
 * a count; everything else is itself alone. A made piece bears its own [uid] —
 * the smith's mark that tells one blade from its twin, so mastery can ride a
 * single weapon and not its whole shape.
 */
data class Item(
    val archetype: ItemArchetype,
    val material: Material,
    val quality: Quality = Quality.HONEST,
    val cultureId: Int = -1,
    val count: Int = 1,
    val uid: Int = 0,
    /** The shieldwright's fields: the build, the iron on it, how it rides, the plank's measure. */
    val construction: ShieldConstruction? = null,
    val reinforcement: ShieldReinforcement? = null,
    val grip: ShieldGrip? = null,
    val dimensions: ShieldDimensions? = null,
    /** The arms-wright's roll for a ranged arm: which form this shape takes. */
    val rangedForm: RangedForm? = null,
    /** The worked head of an arrow or bolt, of some other metal than the shaft. */
    val headMaterial: Material? = null
) {
    /** What it weighs: a shield by the wright's own arithmetic, all else by shape and temper. */
    fun weight(): Float = if (archetype.slot == ItemSlot.SHIELD) {
        (Shields.massOf(this) * 100).roundToInt() / 100f
    } else {
        (archetype.baseWeight * material.weightFactor() * count).let { (it * 100).roundToInt() / 100f }
    }

    /** What a steady buyer would pay. */
    fun value(): Int =
        (archetype.baseValue * material.valueFactor() * quality.valueMult * (if (archetype.stacks) count else 1))
            .roundToInt().coerceAtLeast(1)

    /** The harm it does with its material's temper and its make worked in. */
    fun damage(): Int =
        (archetype.baseDamage * material.damageMult(archetype.damageType) * quality.damageMult)
            .roundToInt().coerceAtLeast(1)

    /** What it guards against a given harm: the piece's bulk, its own temper, the material's say, the make's. */
    fun armorValue(type: DamageType): Int =
        (archetype.baseArmor * archetype.armorTemper.forType(type) * material.damageMult(type) * quality.armorMult)
            .roundToInt()

    /** How quick it is in the hand: material temper times the shape's own quickness. */
    fun speedFactor(): Float = material.speedFactor() * archetype.speedFactor

    fun sameStack(other: Item): Boolean =
        archetype == other.archetype && material == other.material &&
            quality == other.quality && cultureId == other.cultureId

    /** The mark a made piece bears; 0 for the unmarked (stackables, old saves). */
    fun marked(): Item = if (uid != 0) this else copy(uid = ItemUids.take())

    /** Compact form for the save; every field is a stable enum name or a plain number. */
    fun encode(): String {
        val base = listOf(archetype.name, material.name, quality.name, "$cultureId", "$count", "$uid")
        if (archetype.slot != ItemSlot.SHIELD) {
            val extra = rangedForm?.name ?: headMaterial?.name
            return if (extra != null) (base + extra).joinToString(FIELD) else base.joinToString(FIELD)
        }
        val dims = Shields.dimensionsOf(this)
        return (base + listOf(
            (construction ?: ShieldConstruction.SOLID_WOOD).name,
            (reinforcement ?: ShieldReinforcement.NONE).name,
            (grip ?: ShieldGrip.CENTRAL).name,
            "${dims.width}",
            "${dims.height}",
            "${dims.thickness}"
        )).joinToString(FIELD)
    }

    companion object {
        private const val FIELD = "="

        /** Restore from [encode]; a mangled piece comes back null, never broken. */
        fun fromEncoded(raw: String?): Item? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(FIELD)
            if (parts.size !in 5..12) return null
            val archetype = ItemArchetype.entries.firstOrNull { it.name == parts[0] } ?: return null
            val material = Material.entries.firstOrNull { it.name == parts[1] } ?: return null
            val quality = Quality.entries.firstOrNull { it.name == parts[2] } ?: return null
            val cultureId = parts[3].toIntOrNull() ?: return null
            val count = (parts[4].toIntOrNull() ?: 1).coerceAtLeast(1)
            val uid = parts.getOrNull(5)?.toIntOrNull() ?: 0
            // A shield written before the shieldwright's fields wakes with honest defaults.
            var construction: ShieldConstruction? = null
            var reinforcement: ShieldReinforcement? = null
            var grip: ShieldGrip? = null
            var dimensions: ShieldDimensions? = null
            if (archetype.slot == ItemSlot.SHIELD) {
                construction = parts.getOrNull(6)
                    ?.let { name -> ShieldConstruction.entries.firstOrNull { it.name == name } }
                    ?: ShieldConstruction.SOLID_WOOD
                reinforcement = parts.getOrNull(7)
                    ?.let { name -> ShieldReinforcement.entries.firstOrNull { it.name == name } }
                    ?: ShieldReinforcement.NONE
                grip = parts.getOrNull(8)
                    ?.let { name -> ShieldGrip.entries.firstOrNull { it.name == name } }
                    ?: ShieldGrip.CENTRAL
                val width = parts.getOrNull(9)?.toFloatOrNull()
                val height = parts.getOrNull(10)?.toFloatOrNull()
                val thickness = parts.getOrNull(11)?.toFloatOrNull()
                dimensions = if (width != null && height != null && thickness != null) {
                    ShieldDimensions(width, height, thickness)
                } else {
                    null
                }
            }
            // A ranged arm written before the arms-wright's roll wakes in its default
            // form; a headed shaft written before heads keeps an honest iron point.
            val rangedForm = if (Ranged.isRanged(archetype)) {
                parts.getOrNull(6)?.let { name -> RangedForm.entries.firstOrNull { it.name == name } }
            } else null
            val headMaterial = if (archetype.slot == ItemSlot.AMMUNITION) {
                parts.getOrNull(6)?.let { name -> Material.entries.firstOrNull { it.name == name } }
            } else null
            return Item(
                archetype, material, quality, cultureId,
                if (archetype.stacks) count else 1, uid,
                construction, reinforcement, grip, dimensions,
                rangedForm, headMaterial
            )
        }
    }
}

/**
 * Which materials each people's country yields, drawn once from the seed: the
 * commons are everywhere, but steel, silver, lead and the stranger tempers
 * belong to the regions that hold them, and every smith favors what their land
 * gives best. The same world always starves the same regions and riches the
 * same others.
 */
class MaterialGeography(world: World) {

    private class Region(val available: List<Material>, val favored: Material)

    private val regions: Map<Int, Region>

    init {
        val map = mutableMapOf<Int, Region>()
        world.cultures.forEach { culture ->
            val rng = Random(world.seed * 7907L + 310 + culture.id * 977L)
            // Bog iron, bronze, copper, iron, bone and ashwood are everywhere; the
            // rare tempers depend on what the land itself holds.
            val available = listOf(
                Material.BOG_IRON, Material.VERDIGRIS, Material.COPPER, Material.IRON,
                Material.BONE, Material.ASHWOOD
            ) + listOfNotNull(
                if (rng.nextInt(5) < 3) Material.BRASS else null,
                if (rng.nextInt(3) < 2) Material.LEAD else null,
                if (rng.nextInt(4) < 3) Material.STEEL else null,
                if (rng.nextInt(4) == 0) Material.SILVER else null,
                if (rng.nextInt(5) == 0) Material.GOLD else null,
                if (rng.nextInt(4) == 0) Material.SALT_GLASS else null,
                if (rng.nextInt(6) == 0) Material.GRAVE_SLATE else null,
                if (rng.nextInt(8) == 0) Material.STAR_IRON else null,
                if (rng.nextInt(9) == 0) Material.SPIRIT_SILVER else null,
                if (rng.nextInt(10) == 0) Material.EARTH_BONE else null
            )
            map[culture.id] = Region(available, available[rng.nextInt(available.size)])
        }
        regions = map
    }

    /** What a region's smiths work in, the favored material most often. */
    fun roll(rng: Random, cultureId: Int): Material {
        if (cultureId < 0) return Material.roll(rng)
        val region = regions[cultureId] ?: return Material.roll(rng)
        val weights = region.available.map { if (it == region.favored) 3 else 1 }
        var pick = rng.nextInt(weights.sum())
        for (index in region.available.indices) {
            pick -= weights[index]
            if (pick < 0) return region.available[index]
        }
        return region.favored
    }

    /** Everything a region's hands can reach. */
    fun available(cultureId: Int): List<Material> =
        regions[cultureId]?.available ?: Material.entries.toList()

    /** The material a region's smiths love best. */
    fun favored(cultureId: Int): Material = regions[cultureId]?.favored ?: Material.BOG_IRON
}

/**
 * The province's own arms-lore: every culture works steel its own way, and the
 * same world always names the same blade the same way. Like the class roster,
 * the styles are drawn once from the seed and stand forever.
 */
class StyleRoster(world: World) {

    private val weaponStyles: Map<Pair<Int, ItemArchetype>, String>
    private val armorStyles: Map<Pair<Int, ItemArchetype>, String>
    private val trinketStyles: Map<Int, String>
    private val trinketPieceStyles: Map<Pair<Int, ItemArchetype>, String>

    init {
        val weapon = mutableMapOf<Pair<Int, ItemArchetype>, String>()
        val armor = mutableMapOf<Pair<Int, ItemArchetype>, String>()
        val trinket = mutableMapOf<Int, String>()
        val trinketPiece = mutableMapOf<Pair<Int, ItemArchetype>, String>()
        val cultures = world.cultures
        cultures.forEach { culture ->
            ItemArchetype.WEAPONS.forEach { archetype ->
                val rng = Random(world.seed * 104729L + 200 + culture.id * 131L + archetype.ordinal * 17L)
                val bank = WEAPON_BANKS[archetype] ?: WEAPON_BANKS.getValue(ItemArchetype.BLADE)
                weapon[culture.id to archetype] = bank[rng.nextInt(bank.size)]
            }
            // Every people words the armor of every place after their own smiths.
            ItemArchetype.entries.filter { it.slot == ItemSlot.BODY }.forEach { archetype ->
                val rng = Random(world.seed * 104729L + 300 + culture.id * 131L + archetype.ordinal * 17L)
                val bank = ARMOR_BANKS[archetype] ?: listOf(archetype.label)
                armor[culture.id to archetype] = bank[rng.nextInt(bank.size)]
            }
            // Rings, medals and relics carry a people's word for them too.
            TRINKET_BANKS.forEach { (archetype, bank) ->
                val rng = Random(world.seed * 104729L + 400 + culture.id * 131L + archetype.ordinal * 17L)
                trinketPiece[culture.id to archetype] = bank[rng.nextInt(bank.size)]
            }
            val rng = Random(world.seed * 104729L + 200 + culture.id * 131L + 997L)
            trinket[culture.id] = TRINKET_STYLES[rng.nextInt(TRINKET_STYLES.size)]
        }
        weaponStyles = weapon
        armorStyles = armor
        trinketStyles = trinket
        trinketPieceStyles = trinketPiece
    }

    /** The noun a culture's smiths give a shape: saber, kris, boar-spear, as the seed wills. */
    fun weaponNoun(archetype: ItemArchetype, cultureId: Int): String =
        weaponStyles[cultureId to archetype]
            ?: GENERIC_NOUNS[archetype]
            ?: archetype.label

    fun trinketNoun(cultureId: Int): String =
        trinketStyles[cultureId] ?: GENERIC_NOUNS.getValue(ItemArchetype.CHARM)

    /** The noun a culture's smiths give a piece of armor: byrnie, aketon, armet, as the seed wills. */
    fun armorNoun(archetype: ItemArchetype, cultureId: Int): String =
        armorStyles[cultureId to archetype]
            ?: GENERIC_NOUNS[archetype]
            ?: archetype.label

    /** The noun a culture gives a ring, medal or relic. */
    fun relicNoun(archetype: ItemArchetype, cultureId: Int): String =
        trinketPieceStyles[cultureId to archetype]
            ?: GENERIC_NOUNS[archetype]
            ?: archetype.label

    /** The full name of a thing: its material's adjective before its culture's word for it. */
    fun nameFor(item: Item): String = when {
        item.archetype.slot == ItemSlot.SHIELD -> shieldName(item)
        item.archetype.slot == ItemSlot.WEAPON ->
            "${item.material.adjective} ${weaponNoun(item.archetype, item.cultureId)}"
        item.archetype.slot == ItemSlot.BODY ->
            "${item.material.adjective} ${armorNoun(item.archetype, item.cultureId)}"
        item.archetype == ItemArchetype.CHARM ->
            "${item.material.adjective} ${trinketNoun(item.cultureId)}"
        item.archetype.slot == ItemSlot.TRINKET ->
            "${item.material.adjective} ${relicNoun(item.archetype, item.cultureId)}"
        else -> "${item.material.adjective} ${item.archetype.label}"
    }

    /** A shield's name: its make before its metal, and the iron riding it. */
    private fun shieldName(item: Item): String {
        val construction = item.construction ?: ShieldConstruction.SOLID_WOOD
        val reinforcement = item.reinforcement ?: ShieldReinforcement.NONE
        val builder = StringBuilder()
        construction.descriptor?.let { builder.append(it).append(' ') }
        builder.append(item.material.adjective).append(' ').append(item.archetype.label)
        if (reinforcement != ShieldReinforcement.NONE) {
            builder.append(" with ").append(reinforcement.label)
        }
        return builder.toString()
    }

    /** A chronicle line for the detail card: who made it, and how it sits in the hand. */
    fun flavorFor(item: Item): String {
        val temper = when {
            item.material == Material.EARTH_BONE -> "It remembers being something greater."
            item.material == Material.STAR_IRON -> "It fell burning, and cooled into an edge."
            item.material == Material.SPIRIT_SILVER -> "It gleams like the moon's far side."
            item.material.damageMult(DamageType.SHARP) >= 1.1f -> "It takes an edge that whispers."
            item.material.damageMult(DamageType.BLUNT) >= 1.1f -> "It lands like a door slamming."
            item.material.damageMult(DamageType.PIERCE) >= 1.1f -> "It finds the seams in things."
            else -> "It is honest work, no more."
        }
        val make = when (item.quality) {
            Quality.SHODDY -> "The make is shoddy; it will not outlive its owner."
            Quality.WORN -> "The make is worn and half-remembered."
            Quality.HONEST -> "The make is honest."
            Quality.FINE -> "The make is fine; someone loved this once."
            Quality.SUPERB -> "The make is superb — a master's work, kept for the grave."
            Quality.LEGENDARY -> "No smith alive could make this again."
        }
        return if (item.cultureId >= 0) "$make $temper" else "$temper $make"
    }

    companion object {
        private val WEAPON_BANKS = mapOf(
            ItemArchetype.BLADE to listOf("Saber", "Longknife", "Leafblade", "Warsword", "Kaskara"),
            ItemArchetype.AXE to listOf("Cleaver", "Hatchet", "Splitting-Axe", "Beard-Axe", "Woodcutter"),
            ItemArchetype.MACE to listOf("Maul", "Sledge", "Hammer", "Pestle", "Pounder"),
            ItemArchetype.SPEAR to listOf("Boar-Spear", "Skewer", "Ashlance", "Whale-Runner", "Thorn"),
            ItemArchetype.DAGGER to listOf("Stiletto", "Bodkin", "Fang", "Sliver", "Tusk"),
            ItemArchetype.BOW to listOf("Shortbow", "Warbow", "Reedbow", "Hornbow", "Sinelimb"),
            ItemArchetype.CROSSBOW to listOf("Crossbow", "Latch", "Goat's-Foot", "Winch-Bow", "Stone-Bow"),
            ItemArchetype.ARQUEBUS to listOf("Arquebus", "Hagbut", "Caliver", "Hackbut", "Thunder-Rod"),
            ItemArchetype.HAND_CANNON to listOf("Hand Cannon", "Petronel", "Bombard", "Thunder-Maker"),
            ItemArchetype.THREE_EYE_CANNON to listOf("Three-Eye Cannon", "San Yan Chong", "Triple-Throat", "Three Thunders"),
            ItemArchetype.SLING to listOf("Sling", "Fustibalus", "Shepherd's Cord", "Whirl-String"),
            ItemArchetype.THROWING_KNIFE to listOf("Throwing Knife", "Hurl-Blade", "Whisper", "Wand-Steel"),
            ItemArchetype.FRANCISCA to listOf("Francisca", "Hurl-Axe", "Sky-Axe", "Bearded Chip"),
            ItemArchetype.CHAKRAM to listOf("Chakram", "War-Ring", "Sky-Steel", "Rim-Blade"),
            ItemArchetype.SHURIKEN to listOf("Shuriken", "Throwing-Star", "Pin-Wheel", "Iron Petal"),
            ItemArchetype.CLUB to listOf("Cudgel", "Shillelagh", "Blackthorn", "Driftwood"),
            ItemArchetype.BLUDGEON to listOf("Bludgeon", "Cosh", "Knobkerrie", "Truncheon"),
            ItemArchetype.FLAIL to listOf("Flail", "Chain-Mace", "Threshing-Star", "Whipstock"),
            ItemArchetype.FLANGED_MACE to listOf("Flanged Mace", "Ribbed Star", "Cog-Mace", "Gearhead"),
            ItemArchetype.MORNING_STAR to listOf("Morning Star", "Hedgehog", "Scorpion", "Spiked Ball"),
            ItemArchetype.WAR_HAMMER to listOf("War Hammer", "Beetle", "Forge-Hammer", "Skullcracker"),
            ItemArchetype.SHESTOPYOR to listOf("Shestopyor", "Pernach", "Bulawa", "Six-Feather"),
            ItemArchetype.QUARTERSTAFF to listOf("Quarterstaff", "Waystaff", "Pilgrim's Staff", "Six-Foot"),
            ItemArchetype.SHORTSWORD to listOf("Shortsword", "Gladius", "Cinquedea", "Arming-Knife"),
            ItemArchetype.FALCHION to listOf("Falchion", "Chopper", "Bent Blade", "Malchus"),
            ItemArchetype.MESSER to listOf("Messer", "Long-Knife", "Kriegsmesser", "Haudegen"),
            ItemArchetype.SABRE to listOf("Sabre", "Szabla", "Shashka", "Talwar"),
            ItemArchetype.SCIMITAR to listOf("Scimitar", "Shamshir", "Kilij", "Crescent"),
            ItemArchetype.ARMING_SWORD to listOf("Arming Sword", "Broadsword", "Cross-Hilt", "Oakeshott"),
            ItemArchetype.ULFBERHT to listOf("Ulfberht", "Kingsword", "Frankish Steel", "Ing-Blade"),
            ItemArchetype.BATTLE_AXE to listOf("Battle Axe", "War-Axe", "Raven-Beak", "Broadhead"),
            ItemArchetype.KATANA to listOf("Katana", "Tachi", "Wakizashi", "Moon-Edge"),
            ItemArchetype.BILL to listOf("Bill", "Billhook", "Hedge-Hook", "English Bill"),
            ItemArchetype.DANE_AXE to listOf("Dane Axe", "Raven-Axe", "Haft-Blade", "Broad-Axe"),
            ItemArchetype.GLAIVE to listOf("Glaive", "Pole-Blade", "Reaper", "Guisarme"),
            ItemArchetype.GUANDAO to listOf("Guandao", "Reclining Moon", "Crescent Blade", "Spring-Blade"),
            ItemArchetype.PUDAO to listOf("Pudao", "Horse-Cutter", "Long Blade-Staff"),
            ItemArchetype.SOVNYA to listOf("Sovnya", "Curve-Hook", "Marsh-Blade"),
            ItemArchetype.NAGINATA to listOf("Naginata", "Ash-Cutter", "Pole-Glaive"),
            ItemArchetype.BARDICHE to listOf("Bardiche", "Bearded Pole-Axe", "Shoulder-Axe", "Wide-Edge"),
            ItemArchetype.WAR_SCYTHE to listOf("War Scythe", "Kosa", "Reaper's Pole", "Field-Edge"),
            ItemArchetype.KNIFE to listOf("Knife", "Skinning Knife", "Whittler", "Worksteel"),
            ItemArchetype.RAPIER to listOf("Rapier", "Smallsword", "Swept-Hilt", "Needle-Blade"),
            ItemArchetype.HORSEMANS_PICK to listOf("Horseman's Pick", "War Pick", "Crowbill", "Nadziak"),
            ItemArchetype.ESTOC to listOf("Estoc", "Tuck", "Mail-Needle", "Armor-Needle"),
            ItemArchetype.PIKE to listOf("Pike", "Sarissa", "Ash-Pike", "Wallspear"),
            ItemArchetype.POLEAXE to listOf("Poleaxe", "Three-Head", "Pole-Hammer", "Ravenbill"),
            ItemArchetype.HALBERD to listOf("Halberd", "Swiss Blade", "Thrust-Axe", "Hook-Blade"),
            ItemArchetype.HARPOON to listOf("Harpoon", "Whale-Iron", "Barbed Lance", "Fisher's Spear"),
            ItemArchetype.TRIDENT to listOf("Trident", "Leister", "Three-Tooth", "Fishing-Spear"),
            ItemArchetype.BEC_DE_CORBIN to listOf("Bec de Corbin", "Crow's Beak", "Polehammer", "Raven-Beak"),
            ItemArchetype.LANCE to listOf("Lance", "Couched Lance", "Knight's Ash", "Charging Pole"),
            ItemArchetype.PLANCON_A_PICOT to listOf("Plançon à Picot", "Piked Pole", "Iron-Shod Staff", "Pick-Pole")
        )

        private val TRINKET_STYLES = listOf("Charm", "Tally", "Ward", "Knot", "Reliquary", "Whistle")

        /** Every people words the armor of every place after their own smiths. */
        private val ARMOR_BANKS = mapOf(
            ItemArchetype.WRAP to listOf("Funeral Wrap", "Shroud", "Winding Cloth", "Mourner's Wrap", "Grave Linen"),
            ItemArchetype.GAMBESON to listOf("Padded Gambeson", "Aketon", "Arming Doublet", "Quilted Jack", "Pourpoint"),
            ItemArchetype.JERKIN to listOf("Studded Jerkin", "Cuirboulli", "Buff Coat", "Riding Jerkin", "Hide Harness"),
            ItemArchetype.HAUBERGEON to listOf("Haubergeon", "Byrnie", "Short Mail", "Ring Shirt", "Mail Coat"),
            ItemArchetype.HAUBERK to listOf("Hauberk", "Full Mail", "Chain Mail", "Weaved Rings", "Long Mail"),
            ItemArchetype.BRIGANDINE to listOf("Brigandine", "Coat of Plates", "Lame Coat", "Scaled Coat", "Riveted Jack"),
            ItemArchetype.CUIRASS to listOf("Plate Cuirass", "Breastplate", "Lorica", "Warplate", "Harness Chest"),
            ItemArchetype.HOOD to listOf("Hood", "Cowl", "Wimple", "Travelling Hood", "Ranger's Hood"),
            ItemArchetype.CAP to listOf("Cap", "Arming Cap", "Leather Skullcap", "Hunter's Cap", "Padded Cap"),
            ItemArchetype.COIF to listOf("Mail Coif", "Ring Coif", "Chain Hood", "Mail Hood", "Riveted Coif"),
            ItemArchetype.HELM to listOf("Helm", "Nasal Helm", "Kettle Hat", "Spangenhelm", "Sallet"),
            ItemArchetype.BARBUTE to listOf("Barbute", "Great Helm", "Close Helm", "Visored Helm", "Armet"),
            ItemArchetype.AVENTAIL to listOf("Mail Aventail", "Camail", "Curtain of Rings", "Hanging Mail", "Riveted Veil"),
            ItemArchetype.GORGET to listOf("Gorget", "Neck Plate", "Throat Guard", "Bevor", "Collar of Steel"),
            ItemArchetype.MANTLE to listOf("Quilted Mantle", "Cloak Mantle", "Shoulder Wrap", "Fur Mantle", "Traveller's Mantle"),
            ItemArchetype.SPAULDER to listOf("Spaulders", "Scale Shoulders", "Lame Shoulders", "Riveted Spaulders", "Shoulder Lames"),
            ItemArchetype.PAULDRON to listOf("Pauldron", "Shoulder Plate", "Plate Shoulder", "Knight's Pauldron", "War Pauldron"),
            ItemArchetype.BRACERS to listOf("Bracers", "Leather Bracers", "Archer's Bracers", "Wrist Wraps", "Cuffed Bracers"),
            ItemArchetype.SLEEVES to listOf("Mail Sleeves", "Ring Sleeves", "Chain Sleeves", "Sleeves of Mail", "Riveted Sleeves"),
            ItemArchetype.VAMBRACE to listOf("Vambraces", "Forearm Plates", "Steel Vambraces", "Arm Harness", "Splint Vambraces"),
            ItemArchetype.GLOVES to listOf("Gloves", "Leather Gloves", "Work Gloves", "Riding Gloves", "Hand Wraps"),
            ItemArchetype.MITTENS to listOf("Mail Mittens", "Ring Mittens", "Chain Mittens", "Mail Hands", "Hinged Mittens"),
            ItemArchetype.GAUNTLETS to listOf("Gauntlets", "Plate Gauntlets", "Fingered Gauntlets", "Steel Gauntlets", "War Gauntlets"),
            ItemArchetype.HOSE to listOf("Padded Hose", "Quilted Hose", "Arming Hose", "Woollen Hose", "Cloth Hose"),
            ItemArchetype.LEGGINGS to listOf("Leggings", "Leather Leggings", "Riding Leggings", "Hide Leggings", "Breeches of Hide"),
            ItemArchetype.CHAUSSES to listOf("Mail Chausses", "Ring Chausses", "Chain Chausses", "Mail Legs", "Riveted Chausses"),
            ItemArchetype.TASSETS to listOf("Tassets", "Thigh Plates", "Plate Skirt", "War Skirt", "Lame Tassets"),
            ItemArchetype.SCALE_GREAVES to listOf("Scale Greaves", "Scaled Shin Guards", "Lame Greaves", "Riveted Greaves", "Scale Shins"),
            ItemArchetype.GREAVES to listOf("Greaves", "Plate Greaves", "Steel Greaves", "Shin Plates", "War Greaves"),
            ItemArchetype.SHOES to listOf("Padded Shoes", "Woollen Shoes", "Arming Shoes", "Cloth Slippers", "Traveller's Shoes"),
            ItemArchetype.BOOTS to listOf("Boots", "Leather Boots", "Riding Boots", "Marching Boots", "Hide Boots"),
            ItemArchetype.SABATONS to listOf("Sabatons", "Plate Shoes", "Steel Sabatons", "Foot Plates", "War Sabatons")
        )

        /** Rings, medals and relics, worded by the people that wore them. */
        private val TRINKET_BANKS = mapOf(
            ItemArchetype.SIGNET to listOf("Signet", "Seal Ring", "Sovereign's Ring", "Signet of Office", "Lord's Ring"),
            ItemArchetype.BAND to listOf("Band", "Plain Band", "Betrothal Band", "Silver Hoop", "Worn Band"),
            ItemArchetype.SEAL_RING to listOf("Seal Ring", "Wax Seal Ring", "Chancery Seal", "Heirloom Seal", "Notary's Ring"),
            ItemArchetype.GEM_RING to listOf("Gemmed Ring", "Jeweled Ring", "Ruby Ring", "Stone-Set Ring", "Gilt Gem Ring"),
            ItemArchetype.MEDALLION to listOf("Medallion", "Commemorative Medal", "Order's Medallion", "Cast Medal", "Hero's Medallion"),
            ItemArchetype.TALISMAN to listOf("Talisman", "Ward Talisman", "Fetish", "Luck Piece", "Hex-Breaker"),
            ItemArchetype.RELIC to listOf("Relic", "Saint's Finger", "Holy Relic", "Reliquary Pendant", "Bone of the Martyr")
        )

        private val GENERIC_NOUNS = mapOf(
            ItemArchetype.BLADE to "Blade",
            ItemArchetype.AXE to "Axe",
            ItemArchetype.MACE to "Mace",
            ItemArchetype.SPEAR to "Spear",
            ItemArchetype.DAGGER to "Dagger",
            ItemArchetype.BOW to "Bow",
            ItemArchetype.CHARM to "Charm",
            ItemArchetype.SIGNET to "Signet"
        )
    }
}
