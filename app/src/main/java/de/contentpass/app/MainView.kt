package de.contentpass.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import de.contentpass.app.ui.theme.ContentPassExampleTheme
import kotlinx.coroutines.launch

@Composable
fun MainView(viewModel: ExampleViewModel) {
    val isAuthenticated: Boolean by viewModel.isAuthenticated.observeAsState(false)
    val hasValidSubscription: Boolean by viewModel.hasValidSubscription.observeAsState(false)

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
    }
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

@Preview(
    showSystemUi = true
)
@Composable
fun DefaultPreview() {
    ContentPassExampleTheme {
        MainView(viewModel = ExampleViewModel(Application()))
    }
}
