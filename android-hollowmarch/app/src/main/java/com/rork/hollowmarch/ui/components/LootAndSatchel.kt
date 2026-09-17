package com.rork.hollowmarch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.game.DamageType
import com.rork.hollowmarch.game.Entity
import com.rork.hollowmarch.game.Equipment
import com.rork.hollowmarch.game.Inventory
import com.rork.hollowmarch.game.Item
import com.rork.hollowmarch.game.ItemArchetype
import com.rork.hollowmarch.game.ItemSlot
import com.rork.hollowmarch.game.SatchelSort
import com.rork.hollowmarch.game.Shields
import com.rork.hollowmarch.game.StyleRoster
import com.rork.hollowmarch.game.sortSatchel
import com.rork.hollowmarch.game.WearSlot
import com.rork.hollowmarch.ui.theme.Ink
import java.util.Locale

/** Stone to one decimal, the same in every land: no comma ever lies about weight. */
internal fun stoneLabel(value: Float): String = String.format(Locale.US, "%.1f", value)

/** The numbers a steady buyer would ask after. */
private fun itemSummary(item: Item): String {
    val parts = mutableListOf(item.quality.label)
    if (item.archetype.isWeapon) parts += "${item.damage()} ${item.archetype.damageType.label} harm"
    if (item.archetype.baseArmor > 0) {
        parts += "turns ${item.armorValue(DamageType.SHARP)}/${item.armorValue(DamageType.BLUNT)}/${item.armorValue(DamageType.PIERCE)}"
    }
    if (item.archetype.slot == ItemSlot.SHIELD) {
        parts += "guards ${Shields.coverageHalfAngle(item).toInt()}°"
    }
    if (item.count > 1) parts += "×${item.count}"
    parts += "${stoneLabel(item.weight())} stone"
    parts += "worth ${item.value()}"
    return parts.joinToString(" · ")
}

/** The loot sheet: what the dead wore and what they carried, item by item or all at once. */
@Composable
fun LootPanel(
    entity: Entity,
    styleRoster: StyleRoster,
    modifier: Modifier = Modifier,
    onTake: (Item) -> Unit,
    onTakeEquipped: (WearSlot) -> Unit,
    onTakeAll: () -> Unit
) {
    // Snapshots per composition: the engine's lists may shrink beneath us,
    // and a LazyColumn must never index a list that is moving.
    val spoils = entity.loot.toList()
    val worn = entity.equipment?.all() ?: emptyList()
    val empty = spoils.isEmpty() && worn.isEmpty() && entity.lootBrass == 0
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "The ${entity.name.replaceFirstChar { it.uppercase() }}",
            color = Ink.Brass,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
        MonoText(
            text = when {
                empty -> "Nothing left but dust."
                else -> buildString {
                    append(spoils.size + worn.size)
                    append(if (spoils.size + worn.size == 1) " thing" else " things")
                    if (entity.lootBrass > 0) append(" · ${entity.lootBrass} brass")
                }
            },
            color = Ink.Faded,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        EngravedRule(Modifier.padding(vertical = 10.dp))
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
        ) {
            if (worn.isNotEmpty()) {
                item {
                    MonoText(
                        "What it wore — tap to strip it",
                        color = Ink.Dim,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            items(worn.size) { index ->
                val (slot, item) = worn[index]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTakeEquipped(slot) }
                        .padding(vertical = 7.dp)
                ) {
                    Text(
                        text = styleRoster.nameFor(item),
                        color = Ink.Parchment,
                        fontSize = 14.sp
                    )
                    MonoText(
                        "worn · ${slot.label} · ${itemSummary(item)}",
                        color = Ink.Faded,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                EngravedRule()
            }
            if (spoils.isNotEmpty()) {
                item {
                    MonoText(
                        "What it carried",
                        color = Ink.Dim,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            items(spoils.size) { index ->
                val item = spoils[index]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTake(item) }
                        .padding(vertical = 7.dp)
                ) {
                    Text(
                        text = styleRoster.nameFor(item),
                        color = Ink.Parchment,
                        fontSize = 14.sp
                    )
                    MonoText(
                        itemSummary(item),
                        color = Ink.Faded,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    MonoText(styleRoster.flavorFor(item), color = Ink.Dim, fontSize = 10.sp)
                }
                EngravedRule()
            }
        }
        if (!empty) {
            Button(
                onClick = onTakeAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink.Brass,
                    contentColor = Ink.Canvas
                )
            ) {
                Text("Take everything")
            }
        }
    }
}

/** The satchel: what you wear, everything you carry, weighed against what your back will bear. */
@Composable
fun SatchelPanel(
    inventory: Inventory,
    equipment: Equipment,
    styleRoster: StyleRoster,
    load: Float,
    capacity: Float,
    modifier: Modifier = Modifier,
    onUse: (Item) -> Unit,
    onDrop: (Item) -> Unit,
    onEquip: (Item) -> Unit,
    onUnequip: (WearSlot) -> Unit,
    weaponLore: ((Item) -> String?)? = null
) {
    var tab by remember { mutableIntStateOf(0) }
    var sort by remember { mutableStateOf(SatchelSort.CARRIED) }
    var selected by remember { mutableStateOf<Item?>(null) }
    var selectedSlot by remember { mutableStateOf<WearSlot?>(null) }
    val tabs = listOf("Worn", "All", "Arms", "Armor", "Trinkets", "Goods")

    // Snapshots per composition: the satchel shifts beneath us the moment a thing
    // is donned, and a list mid-draw must never be the engine's own.
    val carried = inventory.all.toList()
    val filtered = when (tab) {
        2 -> carried.filter { it.archetype.isWeapon }
        3 -> carried.filter { it.archetype.isArmor }
        4 -> carried.filter { it.archetype.slot == ItemSlot.TRINKET }
        5 -> carried.filter { it.archetype.slot == ItemSlot.CONSUMABLE }
        else -> carried
    }
    val shown = sortSatchel(filtered, sort)

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "The Satchel",
            color = Ink.Brass,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(6.dp)
                .background(Ink.Hairline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((load / capacity).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(if (load > capacity) Ink.Blood else Ink.Brass)
            )
        }
        MonoText(
            text = "${stoneLabel(load)} of ${stoneLabel(capacity)} stone borne" +
                if (load > capacity) " — the seams strain" else "",
            color = if (load > capacity) Ink.Blood else Ink.Faded,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = tab == index,
                    onClick = {
                        tab = index
                        selected = null
                        selectedSlot = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Ink.Brass.copy(alpha = 0.24f),
                        activeContentColor = Ink.Brass,
                        activeBorderColor = Ink.Brass.copy(alpha = 0.6f),
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = Ink.Faded,
                        inactiveBorderColor = Ink.Hairline
                    ),
                    icon = {}
                ) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tab != 0) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SatchelSort.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = sort == option,
                        onClick = { sort = option },
                        shape = SegmentedButtonDefaults.itemShape(index, SatchelSort.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Ink.Brass.copy(alpha = 0.24f),
                            activeContentColor = Ink.Brass,
                            activeBorderColor = Ink.Brass.copy(alpha = 0.6f),
                            inactiveContainerColor = Color.Transparent,
                            inactiveContentColor = Ink.Faded,
                            inactiveBorderColor = Ink.Hairline
                        ),
                        icon = {}
                    ) {
                        Text(option.label, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (tab == 0) {
            WornLedger(
                equipment = equipment,
                styleRoster = styleRoster,
                selectedSlot = selectedSlot,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                onSelect = { slot ->
                    selectedSlot = if (selectedSlot == slot) null else slot
                    selected = null
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
            ) {
                if (shown.isEmpty()) {
                    item {
                        MonoText(
                            "Nothing of that kind on you.",
                            color = Ink.Dim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                }
                items(shown.size) { index ->
                    val item = shown.getOrNull(index) ?: return@items
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected == item) Ink.Surface else Color.Transparent)
                            .clickable {
                                selected = if (selected == item) null else item
                                selectedSlot = null
                            }
                            .padding(horizontal = 4.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = styleRoster.nameFor(item),
                            color = Ink.Parchment,
                            fontSize = 14.sp
                        )
                        MonoText(itemSummary(item), color = Ink.Faded, fontSize = 11.sp)
                    }
                    EngravedRule()
                }
            }
        }
        val slot = selectedSlot
        if (slot != null) {
            equipment.worn(slot)?.let { item ->
                ItemDetailCard(
                    item = item,
                    styleRoster = styleRoster,
                    onUnequip = { onUnequip(slot) },
                    lore = weaponLore?.invoke(item)
                )
            }
        }
        selected?.let { item ->
            ItemDetailCard(
                item = item,
                styleRoster = styleRoster,
                onUse = if (item.archetype == ItemArchetype.REMEDY || item.archetype == ItemArchetype.TORCH) {
                    { onUse(item) }
                } else null,
                onEquip = if (item.archetype.isWearable) {
                    {
                        onEquip(item)
                        selected = null
                    }
                } else null,
                onDrop = { onDrop(item) },
                lore = weaponLore?.invoke(item)
            )
        }
    }
}

/** The equipment ledger: all eighteen places, worn pieces named, empty places dimmed. */
@Composable
private fun WornLedger(
    equipment: Equipment,
    styleRoster: StyleRoster,
    selectedSlot: WearSlot?,
    modifier: Modifier = Modifier,
    onSelect: (WearSlot) -> Unit
) {
    LazyColumn(
        modifier = modifier
    ) {
        items(WearSlot.entries.size) { index ->
            val slot = WearSlot.entries[index]
            val item = equipment.worn(slot)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selectedSlot == slot) Ink.Surface else Color.Transparent)
                    .clickable(enabled = item != null) { onSelect(slot) }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                MonoText(
                    text = slot.label.uppercase(),
                    color = Ink.Dim,
                    fontSize = 9.sp
                )
                if (item != null) {
                    Text(
                        text = styleRoster.nameFor(item),
                        color = Ink.Parchment,
                        fontSize = 13.sp
                    )
                    MonoText(itemSummary(item), color = Ink.Faded, fontSize = 10.sp)
                } else {
                    MonoText("— nothing worn —", color = Ink.Hairline, fontSize = 12.sp)
                }
            }
            EngravedRule()
        }
    }
}

@Composable
private fun ItemDetailCard(
    item: Item,
    styleRoster: StyleRoster,
    onUse: (() -> Unit)? = null,
    onEquip: (() -> Unit)? = null,
    onUnequip: (() -> Unit)? = null,
    onDrop: (() -> Unit)? = null,
    lore: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.Surface)
            .padding(12.dp)
    ) {
        Text(
            text = styleRoster.nameFor(item),
            color = Ink.Parchment,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        MonoText(
            text = buildString {
                append(item.quality.label)
                append(" ")
                append(item.material.label)
                if (item.archetype.isWeapon) {
                    append(" · ${item.damage()} ${item.archetype.damageType.label} harm")
                    append(" · swing ${stoneLabel(item.speedFactor())}")
                }
                if (item.archetype.baseArmor > 0) {
                    append(" · turns ${item.armorValue(DamageType.SHARP)} sharp, " +
                        "${item.armorValue(DamageType.BLUNT)} blunt, ${item.armorValue(DamageType.PIERCE)} pierce")
                }
                append(" · ${stoneLabel(item.weight())} stone · worth ${item.value()}")
                if (item.cultureId >= 0) append(" · wrought in another people's style")
            },
            color = Ink.Faded,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
        MonoText(
            styleRoster.flavorFor(item),
            color = Ink.Dim,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (lore != null) {
            MonoText(
                lore,
                color = Ink.Verdigris.copy(alpha = 0.9f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onUse != null) {
                val useLabel = when (item.archetype) {
                    ItemArchetype.REMEDY -> "Drink"
                    else -> "Light"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Ink.Brass.copy(alpha = 0.2f))
                        .clickable(onClick = onUse)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MonoText(useLabel, color = Ink.Brass, fontSize = 12.sp)
                }
            }
            if (onEquip != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Ink.Brass.copy(alpha = 0.2f))
                        .clickable(onClick = onEquip)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MonoText(
                        when {
                            item.archetype.slot == ItemSlot.SHIELD -> "Take up"
                            item.archetype.wear?.isHand == true -> "Wield"
                            else -> "Wear"
                        },
                        color = Ink.Brass,
                        fontSize = 12.sp
                    )
                }
            }
            if (onUnequip != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Ink.Brass.copy(alpha = 0.2f))
                        .clickable(onClick = onUnequip)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MonoText("Take off", color = Ink.Brass, fontSize = 12.sp)
                }
            }
            if (onDrop != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Ink.Canvas)
                        .clickable(onClick = onDrop)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MonoText("Set down", color = Ink.Parchment, fontSize = 12.sp)
                }
            }
        }
    }
}
