package com.rork.hollowmarch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.game.ActorClass
import com.rork.hollowmarch.game.Attr
import com.rork.hollowmarch.game.CharacterForge
import com.rork.hollowmarch.game.DelverCreation
import com.rork.hollowmarch.game.Derived
import com.rork.hollowmarch.game.StatBlock
import com.rork.hollowmarch.ui.theme.Ink

/**
 * The delver's commission sheet: distribute a fixed pool across the eight
 * attributes, choose a class from the province's own roster, and descend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterForgeSheet(
    classes: List<ActorClass>,
    onDismiss: () -> Unit,
    onForge: (DelverCreation) -> Unit
) {
    var allocated by remember { mutableStateOf(mapOf<Attr, Int>()) }
    var classKey by remember { mutableStateOf<String?>(null) }
    val remaining = CharacterForge.remaining(allocated)
    val chosen = classes.firstOrNull { it.key == classKey }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        contentColor = Ink.Parchment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            MonoText("RAISE YOUR DELVER", color = Ink.Brass, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Distribute the pool across the eight attributes, then take up a calling.",
                color = Ink.Faded,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonoText(
                    if (remaining == 0) "the pool is spent" else "$remaining points unspent",
                    color = if (remaining == 0) Ink.Verdigris else Ink.Brass,
                    fontSize = 12.sp
                )
                MonoText(
                    "health ${Derived.maxVitality(StatBlock(allocated))} · " +
                        "will ${Derived.maxMagicka(StatBlock(allocated))}",
                    color = Ink.Dim,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(6.dp))

            Attr.entries.forEach { attr ->
                val value = allocated[attr] ?: StatBlock.BASE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(attr.label, color = Ink.Parchment, fontSize = 13.sp)
                        MonoText(attr.governs, color = Ink.Dim, fontSize = 9.sp)
                    }
                    StepButton("−", enabled = CharacterForge.canRefund(allocated, attr)) {
                        allocated = allocated + (attr to (value - 1))
                    }
                    MonoText(
                        "$value",
                        color = if (value > StatBlock.BASE) Ink.Brass else Ink.Faded,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(min = 40.dp)
                            .padding(horizontal = 2.dp)
                    )
                    StepButton("+", enabled = CharacterForge.canSpend(allocated, attr)) {
                        allocated = allocated + (attr to (value + 1))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            MonoText("A CALLING", color = Ink.Brass, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            classes.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { actorClass ->
                        val selected = actorClass.key == classKey
                        Text(
                            text = actorClass.name,
                            color = if (selected) Ink.Canvas else Ink.Parchment,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (selected) Ink.Brass else Ink.Overlay)
                                .clickable { classKey = actorClass.key }
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                                .wrapContentSize(Alignment.CenterStart)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
            }

            chosen?.let { cls ->
                val favours = cls.weights.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .joinToString(", ") { it.key.label }
                MonoText("${cls.name} — favours $favours", color = Ink.Faded, fontSize = 11.sp)
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    onForge(
                        DelverCreation(
                            stats = allocated,
                            classKey = classKey ?: ""
                        )
                    )
                },
                enabled = classKey != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink.Brass,
                    contentColor = Ink.Canvas,
                    disabledContainerColor = Ink.Hairline,
                    disabledContentColor = Ink.Dim
                )
            ) {
                Text(
                    text = "Begin the descent",
                    fontSize = 15.sp,
                    letterSpacing = 0.4.sp
                )
            }
            Text(
                text = "Unspent points are lost to the dark.",
                color = Ink.Dim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
