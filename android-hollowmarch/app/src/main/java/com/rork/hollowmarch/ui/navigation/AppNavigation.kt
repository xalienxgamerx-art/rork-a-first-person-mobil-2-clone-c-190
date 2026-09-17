package com.rork.hollowmarch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rork.hollowmarch.game.GameViewModel
import com.rork.hollowmarch.ui.screens.ChronicleScreen
import com.rork.hollowmarch.ui.screens.PlayScreen
import com.rork.hollowmarch.ui.screens.TitleScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val gameViewModel: GameViewModel = viewModel()
    val titleState by gameViewModel.title.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = "title"
    ) {
        composable("title") {
            TitleScreen(
                state = titleState,
                classes = gameViewModel.classRoster.classes,
                onContinue = {
                    gameViewModel.startExpedition(resume = true)
                    navController.navigate("play")
                },
                onForgeDelver = { creation ->
                    gameViewModel.startExpedition(resume = false, creation = creation)
                    navController.navigate("play")
                },
                onForgeWorld = { gameViewModel.forgeNewWorld(it) },
                onChronicle = { navController.navigate("chronicle") }
            )
        }
        composable("play") {
            PlayScreen(
                viewModel = gameViewModel,
                onLeave = { navController.popBackStack("title", inclusive = false) }
            )
        }
        composable("chronicle") {
            ChronicleScreen(
                viewModel = gameViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
