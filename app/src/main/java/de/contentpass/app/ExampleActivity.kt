package de.contentpass.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import de.contentpass.app.ui.theme.ContentPassExampleTheme

class ExampleActivity : ComponentActivity() {
    private lateinit var viewModel: ExampleViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ExampleViewModel(this)
        viewModel.onActivityCreate(this)

        setContent {
            ContentPassExampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    MainView(viewModel)
                }
            }
        }
    }
}
