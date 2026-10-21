package com.shohankhan.axiomcalc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.shohankhan.axiomcalc.ui.AxiomApp

/**
 * The single activity.
 *
 * Edge-to-edge is enabled and every screen receives the system insets through
 * the app Scaffold, so content never hides behind the status bar, navigation bar
 * gesture area, display cutout or the IME. No second activity, no fragment: all
 * navigation is Compose Navigation.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.consumeWindowInsets(WindowInsets.safeDrawing),
                color = MaterialTheme.colorScheme.background,
            ) {
                AxiomApp()
            }
        }
    }
}
