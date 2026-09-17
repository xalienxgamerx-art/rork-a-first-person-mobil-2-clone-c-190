package com.rork.hollowmarch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.game.Bearing
import com.rork.hollowmarch.ui.theme.Ink
import kotlin.math.roundToInt

private val Parchment = Color(0xFF473B29)
private val ParchmentInk = Color(0xFFCBB98F)

/**
 * Bearings in place of a map: every pin the word has given you and every place
 * you have stood, each with its compass bearing, its distance, and the walk it asks.
 */
@Composable
fun BearingsPanel(
    province: String,
    bearings: List<Bearing>,
    weatherNote: String,
    modifier: Modifier = Modifier
) {
    val pins = bearings.filter { !it.visited }
    val stood = bearings.filter { it.visited }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        MonoText(
            "BEARINGS IN ${province.uppercase()}",
            color = Ink.Faded,
            fontSize = 10.sp
        )
        MonoText(
            "no map rides with you — trust the bearing and the sky",
            color = Ink.Dim,
            fontSize = 10.sp
        )
        EngravedRule(Modifier.padding(vertical = 8.dp))

        LazyColumn(
            modifier = Modifier
                .heightIn(max = 380.dp)
                .background(Parchment.copy(alpha = 0.35f))
                .border(1.dp, Ink.Hairline)
        ) {
            if (pins.isNotEmpty()) {
                item { SectionLabel("THE WORD HAS NAMED") }
                items(pins, key = { "pin${it.site.id}" }) { pinToRow(it) }
            }
            if (stood.isNotEmpty()) {
                item { SectionLabel("WHERE YOU HAVE STOOD") }
                items(stood, key = { "stood${it.site.id}" }) { pinToRow(it) }
            }
            if (pins.isEmpty() && stood.isEmpty()) {
                item {
                    MonoText(
                        "Nothing yet is named to you. Walk, and listen.",
                        color = Ink.Faded,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        EngravedRule(Modifier.padding(vertical = 8.dp))
        MonoText(
            "A league of road asks half an hour of the walking. The land asks its own price.",
            color = Ink.Dim,
            fontSize = 10.sp
        )
        if (weatherNote.isNotBlank()) {
            MonoText(
                weatherNote,
                color = Ink.Dim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun pinToRow(bearing: Bearing) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Ink.Canvas)
                .border(1.dp, Ink.Brass.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = bearing.compass,
                color = Ink.Brass,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = bearing.site.name,
                color = if (bearing.visited) Ink.Faded else Ink.Parchment,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            MonoText(
                steadLine(bearing),
                color = Ink.Faded,
                fontSize = 10.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            MonoText(
                "${(bearing.leagues * 10).roundToInt() / 10f} lg",
                color = Ink.Parchment,
                fontSize = 11.sp
            )
            MonoText(
                walkTime(bearing.hours),
                color = Ink.Verdigris,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    MonoText(
        label,
        color = Ink.Brass.copy(alpha = 0.8f),
        fontSize = 10.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink.Canvas.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

/** A pin's line under its name: the stage it stands at, its folk, and where the word came from. */
private fun steadLine(bearing: Bearing): String {
    val kind = bearing.stage.ifBlank { bearing.site.kind.label }
    val folk = if (bearing.folk >= 0) " · ${bearing.folk} souls" else ""
    return "$kind$folk · ${bearing.source}"
}

/** The road's own arithmetic: hours become days when the walk is long. */
private fun walkTime(hours: Float): String = when {
    hours < 1f -> "under an hour's walk"
    hours < 24f -> "${hours.roundToInt()}h walk"
    else -> "${(hours / 24f).roundToInt()}d walk"
}
