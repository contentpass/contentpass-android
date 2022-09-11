package de.contentpass.lib

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView

typealias ContentPassDashboardView = ComposeView

@Composable
fun ContentPassDashboard(webView: WebView) {
    AndroidView(
        factory = {
            webView
        },
        modifier = Modifier.fillMaxSize()
    )
}