package de.contentpass.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun MainView(viewModel: ExampleViewModel) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "example") {
        composable("example") { ExampleView(viewModel, navController) }
        composable("web") {
            val context = LocalContext.current
            AndroidView(factory = { viewModel.openDashboard(context) })
        }
    }
}
