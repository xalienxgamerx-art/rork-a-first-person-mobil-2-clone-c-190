package com.rork.hollowmarch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.game.WorldSettings
import com.rork.hollowmarch.ui.theme.Ink
import kotlin.random.Random

/**
 * The forge's commission sheet: name a seed, choose how many years burn,
 * how full the chronicle runs, and how many peoples walk the province.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldForgeSheet(
    onDismiss: () -> Unit,
    onForge: (WorldSettings) -> Unit
) {
    var seedText by rememberSaveable { mutableStateOf("") }
    var years by rememberSaveable { mutableIntStateOf(400) }
    var maxEvents by rememberSaveable { mutableIntStateOf(220) }
    var peoples by rememberSaveable { mutableIntStateOf(0) }

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
            MonoText("FORGE A WORLD", color = Ink.Brass, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Name a seed, and decide how much history burns before you arrive.",
                color = Ink.Faded,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = seedText,
                onValueChange = { seedText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    MonoText("leave blank to roll", color = Ink.Dim, fontSize = 12.sp)
                },
                trailingIcon = {
                    Text(
                        text = "roll",
                        color = Ink.Brass,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .clickable {
                                seedText = Random.nextLong(100_000L, 999_999L).toString()
                            }
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ink.Parchment,
                    unfocusedTextColor = Ink.Parchment,
                    focusedBorderColor = Ink.Brass.copy(alpha = 0.6f),
                    unfocusedBorderColor = Ink.Hairline,
                    cursorColor = Ink.Brass
                )
            )
            MonoText(
                "a word or a number — the same word forges the same world",
                color = Ink.Dim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(10.dp))

            ForgeStepper(
                label = "Years of history",
                note = "no ceiling — as deep as your device can bear",
                value = years,
                range = 120..Int.MAX_VALUE,
                step = 60,
                display = { "$it yrs" },
                onChange = { years = it }
            )
            ForgeStepper(
                label = "Chronicle pages",
                note = "events written before history goes quiet · none is uncapped",
                value = maxEvents,
                range = 0..Int.MAX_VALUE,
                step = 20,
                display = { if (it == 0) "no cap" else "$it" },
                onChange = { maxEvents = it }
            )
            ForgeStepper(
                label = "Peoples",
                note = "how many cultures share the province · none rolls",
                value = peoples,
                range = 0..Int.MAX_VALUE,
                step = 1,
                display = { if (it == 0) "roll (3–5)" else "$it" },
                onChange = { peoples = it }
            )

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onForge(WorldSettings(seedText, years, maxEvents, peoples)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ink.Brass,
                    contentColor = Ink.Canvas
                )
            ) {
                Text(
                    text = "Forge the province",
                    fontSize = 15.sp,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@Composable
private fun ForgeStepper(
    label: String,
    note: String,
    value: Int,
    range: IntRange,
    step: Int,
    display: (Int) -> String,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Ink.Parchment, fontSize = 14.sp)
            MonoText(note, color = Ink.Dim, fontSize = 10.sp)
        }
        StepButton("−", enabled = value > range.first) {
            onChange((value - step).coerceAtLeast(range.first))
        }
        MonoText(
            display(value),
            color = Ink.Brass,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 76.dp)
                .padding(horizontal = 4.dp)
        )
        StepButton("+", enabled = value < range.last) {
            onChange((value + step).coerceAtMost(range.last))
        }
    }
}

/** A carved brass-framed tick, large enough for a thumb. */
@Composable
fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = symbol,
        color = if (enabled) Ink.Parchment else Ink.Hairline,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Ink.Overlay)
            .clickable(enabled = enabled, onClick = onClick)
            .wrapContentSize(Alignment.Center)
    )
}
