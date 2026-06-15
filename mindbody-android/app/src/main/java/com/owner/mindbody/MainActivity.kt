package com.owner.mindbody

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.owner.mindbody.ui.navigation.AppNavigation
import com.owner.mindbody.ui.theme.MindBodyTheme

class MainActivity : ComponentActivity() {

    private var navigationTarget by mutableStateOf<String?>(null)

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val ROUTE_HEART_RATE = "heart_rate"
        const val ROUTE_MOOD_RECORD = "mood_record"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        navigationTarget = intent?.getStringExtra(EXTRA_NAVIGATE_TO)
        setContent {
            MindBodyTheme {
                AppNavigation(initialRoute = navigationTarget)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navigationTarget = intent.getStringExtra(EXTRA_NAVIGATE_TO)
    }
}
