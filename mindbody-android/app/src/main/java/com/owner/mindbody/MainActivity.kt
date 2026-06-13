package com.owner.mindbody

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import com.owner.mindbody.ui.navigation.AppNavigation
import com.owner.mindbody.ui.theme.MindBodyTheme
import com.owner.mindbody.util.BlePermissionHelper

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        requestBlePermissions()
        setContent {
            MindBodyTheme {
                AppNavigation()
            }
        }
    }

    private fun requestBlePermissions() {
        if (!BlePermissionHelper.hasAllPermissions(this)) {
            BlePermissionHelper.requestPermissions(this, PERMISSION_REQUEST)
        }
    }
}
