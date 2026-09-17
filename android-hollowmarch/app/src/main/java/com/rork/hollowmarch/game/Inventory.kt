package com.rork.hollowmarch.game

/**
 * What you carry: items worked in the province's materials, stacked where shapes
 * allow, weighed in stone against what your back will bear. Encoded whole for the save.
 */
class Inventory private constructor(private val items: MutableList<Item>) {

    val all: List<Item> get() = items

    fun isEmpty(): Boolean = items.isEmpty()

    /** The whole load in stone, stackables counted together. */
    fun weight(): Float = items.sumOf { it.weight().toDouble() }.toFloat()

    fun countOf(archetype: ItemArchetype): Int =
        items.filter { it.archetype == archetype }.sumOf { it.count }

    /** Add a thing; stackables join their kin, everything else stands alone. */
    fun add(item: Item) {
        if (!item.archetype.stacks) {
            items += item.copy(count = 1)
            return
        }
        val kin = items.indexOfFirst { it.sameStack(item) }
        if (kin >= 0) items[kin] = items[kin].copy(count = items[kin].count + item.count)
        else items += item
    }

    /** Remove [count] of a shape; returns false when you hold fewer than that. */
    fun remove(item: Item, count: Int = 1): Boolean {
        if (item.archetype.stacks) {
            if (countOf(item.archetype) < count) return false
            var left = count
            var index = items.indexOfFirst { it.sameStack(item) }
            while (left > 0 && index >= 0) {
                val held = items[index]
                if (held.count > left) {
                    items[index] = held.copy(count = held.count - left)
                    return true
                }
                left -= held.count
                items.removeAt(index)
                index = items.indexOfFirst { it.sameStack(item) }
            }
            return left == 0
        }
        // A made thing stands alone: take the very piece you hold, or one cut the same.
        val exact = items.indexOfFirst { it === item }
        val index = if (exact >= 0) exact else items.indexOfFirst { it.sameStack(item) }
        if (index < 0) return false
        items.removeAt(index)
        return true
    }

    /** Drink a remedy: the first stack loses one. False when the satchel is bare. */
    fun useRemedy(): Boolean = removeOne(ItemArchetype.REMEDY)

    /** Pull a fresh torch from the bundle; false when none are left. */
    fun useTorch(): Boolean = removeOne(ItemArchetype.TORCH)

    private fun removeOne(archetype: ItemArchetype): Boolean {
        val index = items.indexOfFirst { it.archetype == archetype }
        if (index < 0) return false
        val held = items[index]
        if (held.count <= 1) items.removeAt(index) else items[index] = held.copy(count = held.count - 1)
        return true
    }

    /** Compact form for the save; a mangled piece is dropped, not mourned. */
    fun encode(): String = items.joinToString(ENTRY) { it.encode() }

    companion object {
        private const val ENTRY = "\u001E"

        /** Restore from [encode]; blank or broken strings wake an empty satchel. */
        fun fromEncoded(raw: String?): Inventory {
            val items = raw?.split(ENTRY)
                ?.mapNotNull { Item.fromEncoded(it) }
                ?.toMutableList()
                ?: mutableListOf()
            return Inventory(items)
        }

        /** What you wake with: someone else's sword, a grey remedy, a bundle of torches. */
        fun starterKit(): Inventory {
            val inventory = Inventory(mutableListOf())
            inventory.add(Item(ItemArchetype.BLADE, Material.BOG_IRON, Quality.WORN).marked())
            inventory.add(Item(ItemArchetype.REMEDY, Material.VERDIGRIS, count = 2))
            inventory.add(Item(ItemArchetype.TORCH, Material.ASHWOOD, count = 2))
            return inventory
        }
    }
}

/** A thing dropped on the floor where you stood, waiting to be taken back up. */
data class GroundItem(val x: Float, val y: Float, val item: Item) {

    /** "x\u001Dy\u001Ditem" — ground entries ride the save beside the inventory. */
    fun encode(): String = listOf("$x", "$y", item.encode()).joinToString(FIELD)

    companion object {
        private const val FIELD = "\u001D"

        fun fromEncoded(raw: String?): GroundItem? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split(FIELD)
            if (parts.size != 3) return null
            val x = parts[0].toFloatOrNull() ?: return null
            val y = parts[1].toFloatOrNull() ?: return null
            val item = Item.fromEncoded(parts[2]) ?: return null
            return GroundItem(x, y, item)
        }
    }
}
