package com.rork.hollowmarch.game

/** Which hand holds what. */
enum class Hand(val slot: WearSlot) {
    RIGHT(WearSlot.RIGHT_HAND),
    LEFT(WearSlot.LEFT_HAND)
}

/**
 * What a body wears and holds: seventeen places, each bearing one thing. Hand-worn
 * arms fill the right hand first, then the left; a two-handed arm claims both and
 * forbids the left until it is given up. Rings fill the left ring before the
 * right; accessories take their four places in order. Everything else wears
 * where its shape says. Encoded whole for the save.
 */
class Equipment private constructor(private val slots: MutableMap<WearSlot, Item>) {

    /** True while a two-handed arm stands in the right hand and forbids the left. */
    var leftClaimed: Boolean = false
        private set

    fun worn(slot: WearSlot): Item? = slots[slot]

    fun isEmpty(): Boolean = slots.isEmpty()

    /** Everything worn or held, in wearing order, one entry per place. */
    fun all(): List<Pair<WearSlot, Item>> = WearSlot.entries.mapNotNull { slot ->
        slots[slot]?.let { slot to it }
    }

    fun items(): List<Item> = all().map { it.second }

    /** What the body bears in stone. Worn things weigh no more for being worn. */
    fun weight(): Float = items().sumOf { it.weight().toDouble() }.toFloat()

    /** The arm in a hand, if that hand holds one. A held torch burns as an arm too; a board is no arm. */
    fun weaponIn(hand: Hand): Item? =
        slots[hand.slot]?.takeIf {
            it.archetype.slot != ItemSlot.SHIELD &&
                (it.archetype.isWeapon || it.archetype.wear?.isHand == true)
        }

    /** The fighting arm: the right hand's, else the left's. */
    fun bestWeapon(): Item? = weaponIn(Hand.RIGHT) ?: weaponIn(Hand.LEFT)

    /** Where a thing sits on this body, or null when it does not. */
    fun slotOf(item: Item): WearSlot? =
        all().firstOrNull { it.second === item || it.second == item }?.first

    /**
     * Take a wearable thing onto the body: returns the place it came to rest and
     * whatever it displaced (empty when a place stood free). Null when the shape
     * is not made to be worn. A one-handed arm may be [prefer]red to a hand when
     * its bearer chooses; a long arm takes both hands whatever anyone prefers.
     */
    fun equip(item: Item, prefer: Hand? = null): Pair<WearSlot, List<Item>>? {
        val wear = item.archetype.wear ?: return null
        return when {
            item.archetype.slot == ItemSlot.SHIELD -> {
                // A board rides the left arm; a long arm that claimed both hands gives way.
                val displaced = mutableListOf<Item>()
                if (leftClaimed) {
                    slots.remove(WearSlot.RIGHT_HAND)?.let { displaced += it }
                    leftClaimed = false
                }
                slots.remove(WearSlot.LEFT_HAND)?.let { displaced += it }
                slots[WearSlot.LEFT_HAND] = item
                WearSlot.LEFT_HAND to displaced
            }
            wear.isHand && item.archetype.twoHanded -> {
                val displaced = listOfNotNull(
                    slots.remove(WearSlot.RIGHT_HAND),
                    slots.remove(WearSlot.LEFT_HAND)
                )
                slots[WearSlot.RIGHT_HAND] = item
                leftClaimed = true
                WearSlot.RIGHT_HAND to displaced
            }
            wear.isHand -> {
                if (leftClaimed) {
                    // A long arm stands in both hands: it gives way to the short one.
                    val displaced = listOfNotNull(slots.remove(WearSlot.RIGHT_HAND))
                    leftClaimed = false
                    slots[WearSlot.RIGHT_HAND] = item
                    WearSlot.RIGHT_HAND to displaced
                } else {
                    val chosen = prefer?.slot
                    if (chosen != null) {
                        // The bearer's choice outranks the fill order.
                        val displaced = listOfNotNull(slots.remove(chosen))
                        slots[chosen] = item
                        chosen to displaced
                    } else {
                    val free = listOf(WearSlot.RIGHT_HAND, WearSlot.LEFT_HAND)
                        .firstOrNull { slots[it] == null }
                    if (free != null) {
                        slots[free] = item
                        free to emptyList()
                    } else {
                        val displaced = listOfNotNull(slots.remove(WearSlot.RIGHT_HAND))
                        slots[WearSlot.RIGHT_HAND] = item
                        WearSlot.RIGHT_HAND to displaced
                    }
                    }
                }
            }
            wear.isRing -> {
                val free = listOf(WearSlot.LEFT_RING, WearSlot.RIGHT_RING)
                    .firstOrNull { slots[it] == null }
                if (free != null) {
                    slots[free] = item
                    free to emptyList()
                } else {
                    val displaced = listOfNotNull(slots.remove(WearSlot.LEFT_RING))
                    slots[WearSlot.LEFT_RING] = item
                    WearSlot.LEFT_RING to displaced
                }
            }
            wear.isAccessory -> {
                val free = ACCESSORIES.firstOrNull { slots[it] == null }
                if (free != null) {
                    slots[free] = item
                    free to emptyList()
                } else {
                    val displaced = listOfNotNull(slots.remove(WearSlot.ACCESSORY_0))
                    slots[WearSlot.ACCESSORY_0] = item
                    WearSlot.ACCESSORY_0 to displaced
                }
            }
            else -> {
                val displaced = listOfNotNull(slots.remove(wear))
                slots[wear] = item
                wear to displaced
            }
        }
    }

    /** Take a thing off the body; null when the place stands empty. */
    fun unequip(slot: WearSlot): Item? {
        val item = slots.remove(slot) ?: return null
        if (slot == WearSlot.RIGHT_HAND) leftClaimed = false
        return item
    }

    /** "SLOT=ITEM" entries apart; a mangled piece is dropped, not mourned. */
    fun encode(): String = all().joinToString(ENTRY) { (slot, item) ->
        "${slot.name}${FIELD}${item.encode()}"
    }

    companion object {
        private const val ENTRY = "\u001E"
        private const val FIELD = "\u001D"
        private val ACCESSORIES = listOf(
            WearSlot.ACCESSORY_0, WearSlot.ACCESSORY_1,
            WearSlot.ACCESSORY_2, WearSlot.ACCESSORY_3
        )

        fun empty(): Equipment = Equipment(mutableMapOf())

        /** Restore from [encode]; blank or broken strings wake bare, never broken. */
        fun fromEncoded(raw: String?): Equipment {
            val equipment = Equipment(mutableMapOf())
            raw?.split(ENTRY)?.forEach { entry ->
                if (entry.isBlank()) return@forEach
                val parts = entry.split(FIELD)
                if (parts.size != 2) return@forEach
                val slot = WearSlot.entries.firstOrNull { it.name == parts[0] } ?: return@forEach
                val item = Item.fromEncoded(parts[1]) ?: return@forEach
                if (item.archetype.wear == null) return@forEach
                equipment.slots[slot] = item
                if (slot == WearSlot.RIGHT_HAND && item.archetype.twoHanded) {
                    equipment.leftClaimed = true
                }
            }
            return equipment
        }
    }
}
