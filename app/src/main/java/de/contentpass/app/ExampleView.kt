package de.contentpass.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import de.contentpass.app.ui.theme.ContentPassExampleTheme
import kotlinx.coroutines.launch


@Composable
fun ExampleView(viewModel: ExampleViewModel, navController: NavController) {
    val isAuthenticated: Boolean by viewModel.isAuthenticated.observeAsState(false)
    val hasValidSubscription: Boolean by viewModel.hasValidSubscription.observeAsState(false)
    val impressionTries: Int by viewModel.impressionTries.observeAsState(0)
    val impressionSuccesses: Int by viewModel.impressionSuccesses.observeAsState(0)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
    ) {
        Text(text = "Is authenticated: $isAuthenticated")
        Text(text = "Has valid subscription: $hasValidSubscription")

        PaddingValues(Dp(8.0F))

        Row {
            LoginButton(viewModel)

            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier.padding(Dp(16F))
            ) { Text("Logout") }
        }

        PaddingValues(Dp(8.0F))

        Text(text = "Count impression tries: $impressionTries")
        Text(text = "Count impression successes: $impressionSuccesses")
        ImpressionButton(viewModel)

        DashboardButton(navController)
    }
}

@Composable
fun DashboardButton(navController: NavController) {
    val action: () -> Unit = {
        navController.navigate("web")
    }

    return Button(
        onClick = action,
        modifier = Modifier.padding(
            Dp(16F)
        )
    ) { Text("Open Dashboard") }
}

@Composable
fun LoginButton(viewModel: ExampleViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity

    val action: () -> Unit = {
        coroutineScope.launch {
            viewModel.login(activity)
        }
    }
    return Button(
        onClick = action,
        modifier = Modifier.padding(
            Dp(16F)
        )
    ) { Text("Login") }
}

@Composable
fun ImpressionButton(viewModel: ExampleViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity

    return Button(
        onClick = { coroutineScope.launch { viewModel.countImpression(activity) } }
    ) { Text("Count impression") }
}

@Preview(
    showSystemUi = true
)
@Composable
fun DefaultPreview() {
    ContentPassExampleTheme {
        MainView(viewModel = ExampleViewModel(Application()))
    }
}
