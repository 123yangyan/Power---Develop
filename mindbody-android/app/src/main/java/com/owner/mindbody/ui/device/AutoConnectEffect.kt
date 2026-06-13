package com.owner.mindbody.ui.device

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.util.BlePermissionHelper

/**
 * APP 启动时请求 BLE 权限，并在权限就绪后触发已保存设备的自动扫描连接。
 */
@Composable
fun AutoConnectEffect() {
    val context = LocalContext.current
    val app = context.applicationContext as MindBodyApplication

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            app.polarBleManager.tryAutoConnectSavedDevice()
        }
    }

    LaunchedEffect(Unit) {
        if (BlePermissionHelper.hasAllPermissions(context)) {
            app.polarBleManager.tryAutoConnectSavedDevice()
        } else {
            permissionLauncher.launch(BlePermissionHelper.requiredPermissions())
        }
    }
}
