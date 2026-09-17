package com.rork.hollowmarch.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.hollowmarch.game.GameViewModel
import com.rork.hollowmarch.ui.components.JournalPanel
import com.rork.hollowmarch.ui.components.MonoText
import com.rork.hollowmarch.ui.theme.Ink

/** The whole generated history of this seed, read from the title plate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChronicleScreen(viewModel: GameViewModel, onBack: () -> Unit) {
    val world = viewModel.world
    Scaffold(
        containerColor = Ink.Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = world.provinceName,
                            color = Ink.Parchment,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Serif,
                            letterSpacing = 1.5.sp
                        )
                        MonoText(
                            "Seed ${world.seedCode} · ${world.currentAge.name}, Year ${world.currentYear}",
                            color = Ink.Faded,
                            fontSize = 10.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to the title plate",
                            tint = Ink.Parchment
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink.Surface,
                    titleContentColor = Ink.Parchment
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.Canvas)
                .padding(padding)
        ) {
            JournalPanel(
                world = world,
                engine = viewModel.engine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp)
            )
        }
    }
}
