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
        const val EXTRA_NAVIGATE_TO = "navigate_to"
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
        requestBlePermissions()
        val initialRoute = intent?.getStringExtra(EXTRA_NAVIGATE_TO)
        setContent {
            MindBodyTheme {
                AppNavigation(initialRoute = initialRoute)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 从通知点击进入时重新创建 Activity 即可；若已在栈顶则 onCreate 不重复调用
    }

    private fun requestBlePermissions() {
        if (!BlePermissionHelper.hasAllPermissions(this)) {
            BlePermissionHelper.requestPermissions(this, PERMISSION_REQUEST)
        }
    }
}
