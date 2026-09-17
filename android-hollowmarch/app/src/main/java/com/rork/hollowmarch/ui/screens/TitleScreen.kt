package com.rork.hollowmarch.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.R
import com.rork.hollowmarch.game.ActorClass
import com.rork.hollowmarch.game.DelverCreation
import com.rork.hollowmarch.game.TitleState
import com.rork.hollowmarch.game.WorldSettings
import com.rork.hollowmarch.ui.components.CharacterForgeSheet
import com.rork.hollowmarch.ui.components.EngravedRule
import com.rork.hollowmarch.ui.components.Glyph
import com.rork.hollowmarch.ui.components.MonoText
import com.rork.hollowmarch.ui.components.WoodcutIcon
import com.rork.hollowmarch.ui.components.WorldForgeSheet
import com.rork.hollowmarch.ui.theme.EngravedTitle
import com.rork.hollowmarch.ui.theme.Ink

/** The plate you meet first: a world that has already finished writing itself. */
@Composable
fun TitleScreen(
    state: TitleState,
    classes: List<ActorClass>,
    onContinue: () -> Unit,
    onForgeDelver: (DelverCreation) -> Unit,
    onForgeWorld: (WorldSettings) -> Unit,
    onChronicle: () -> Unit
) {
    var showForge by remember { mutableStateOf(false) }
    var showDelverForge by remember { mutableStateOf(false) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.title_plate),
                    contentDescription = "A sunken stone gate in a bog beneath a red moon",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.62f to Color.Transparent,
                                1f to Ink.Canvas
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = insets.calculateBottomPadding() + 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EngravedRule(Modifier.padding(bottom = 10.dp))
                Text(
                    text = "HOLLOWMARCH",
                    style = EngravedTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                EngravedRule(Modifier.padding(vertical = 10.dp))
                MonoText(
                    text = "Seed ${state.seedCode} · ${state.ageLabel}",
                    color = Ink.Faded,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                EngravedRule(Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ForgeStat("${state.yearsSimulated}", "yrs\nsimulated")
                    StatDivider()
                    ForgeStat("${state.peoples}", "peoples")
                    StatDivider()
                    ForgeStat("${state.powers}", "powers")
                    StatDivider()
                    ForgeStat("${state.ruins}", "ruins")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(58.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ForgeStat("${state.figures}", "lords &\nheroes")
                    StatDivider()
                    ForgeStat("${state.wars}", "wars")
                    StatDivider()
                    ForgeStat("${state.relics}", "relics")
                    StatDivider()
                    ForgeStat("${state.beasts}", "beasts")
                }

                EngravedRule(Modifier.padding(vertical = 12.dp))

                state.generationLog.forEach { line ->
                    MonoText(
                        text = line,
                        color = Ink.Parchment.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (state.continueLabel != null) onContinue() else showDelverForge = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink.Brass,
                        contentColor = Ink.Canvas
                    )
                ) {
                    WoodcutIcon(Glyph.TORCH, tint = Ink.Canvas, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = state.continueLabel ?: "Enter the sealed vault",
                        fontSize = 15.sp,
                        letterSpacing = 0.4.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showForge = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(3.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Parchment),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Ink.Hairline)
                    ) {
                        WoodcutIcon(Glyph.TRANSPORT, tint = Ink.Parchment, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Forge a world", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onChronicle,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(3.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Parchment),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Ink.Hairline)
                    ) {
                        WoodcutIcon(Glyph.JOURNAL, tint = Ink.Parchment, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("The Chronicle", fontSize = 13.sp)
                    }
                }
            }
        }

        if (showDelverForge) {
            CharacterForgeSheet(
                classes = classes,
                onDismiss = { showDelverForge = false },
                onForge = { creation ->
                    showDelverForge = false
                    onForgeDelver(creation)
                }
            )
        }

        if (showForge) {
            WorldForgeSheet(
                onDismiss = { showForge = false },
                onForge = { settings ->
                    showForge = false
                    onForgeWorld(settings)
                }
            )
        }
    }
}

@Composable
private fun ForgeStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MonoText(value, color = Ink.Parchment, fontSize = 21.sp)
        MonoText(
            label,
            color = Ink.Faded,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .background(Ink.Hairline)
    )
}
