package com.owner.mindbody.util

import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import java.util.regex.Pattern

/**
 * CompanionDeviceManager 封装：将 Polar 手环注册为系统「伴随设备」，
 * 提升后台进程优先级并获取 Android 12+ 后台 FGS 启动特权。
 *
 * API 26 以下自动 no-op；Polar SDK 仍负责实际 BLE 通信。
 */
object CompanionDeviceHelper {

    private const val TAG = "CompanionDevice"
    private const val UNKNOWN_ASSOCIATION_ID = -1
    private const val POLAR_NAME_PATTERN = "Polar.*"

    data class CompanionAssociation(
        val id: Int,
        val macAddress: String,
    )

    data class VerificationResult(
        val valid: Boolean,
        val macAddress: String? = null,
        val associationId: Int = 0,
        val message: String? = null,
    )

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun isBackgroundPrivilegeSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * 发起 CDM 关联请求；成功时在 [onDeviceFound] 中返回系统 chooser 的 [IntentSender]。
     */
    fun associate(
        activity: Activity,
        deviceMac: String?,
        onDeviceFound: (IntentSender) -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        if (!isSupported()) {
            onFailure("CDM 需要 Android 8.0+")
            return
        }
        val manager = activity.getSystemService(CompanionDeviceManager::class.java)
        if (manager == null) {
            onFailure("CompanionDeviceManager 不可用")
            return
        }
        val filterBuilder = BluetoothLeDeviceFilter.Builder()
            .setNamePattern(Pattern.compile(POLAR_NAME_PATTERN))
        normalizeMacAddress(deviceMac)?.let { mac ->
            filterBuilder.setScanFilter(
                ScanFilter.Builder()
                    .setDeviceAddress(mac)
                    .build()
            )
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filterBuilder.build())
            .setSingleDevice(!deviceMac.isNullOrBlank())
            .build()
        manager.associate(
            request,
            object : CompanionDeviceManager.Callback() {
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    onDeviceFound(chooserLauncher)
                }

                override fun onFailure(error: CharSequence?) {
                    AppLogger.w(TAG, "associate onFailure: $error")
                    onFailure(error?.toString())
                }
            },
            null,
        )
    }

    fun getAssociations(context: Context): List<CompanionAssociation> {
        if (!isSupported()) return emptyList()
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.myAssociations.map { info ->
                CompanionAssociation(
                    id = info.id,
                    macAddress = normalizeMacAddress(info.deviceMacAddress?.toString()) ?: "",
                )
            }.filter { it.macAddress.isNotBlank() }
        } else {
            @Suppress("DEPRECATION")
            manager.associations.map { mac ->
                CompanionAssociation(
                    id = UNKNOWN_ASSOCIATION_ID,
                    macAddress = normalizeMacAddress(mac) ?: mac,
                )
            }
        }
    }

    fun isDeviceAssociated(context: Context, deviceId: String?): Boolean {
        val mac = normalizeMacAddress(deviceId) ?: return false
        return getAssociations(context).any { assoc ->
            macEquals(assoc.macAddress, mac)
        }
    }

    fun findAssociation(context: Context, deviceId: String?): CompanionAssociation? {
        val mac = normalizeMacAddress(deviceId) ?: return null
        return getAssociations(context).firstOrNull { assoc -> macEquals(assoc.macAddress, mac) }
    }

    fun verifyAssociation(context: Context, deviceId: String?): VerificationResult {
        if (!isSupported()) {
            return VerificationResult(valid = true, message = "CDM 不可用，跳过校验")
        }
        if (deviceId.isNullOrBlank()) {
            return VerificationResult(valid = false, message = "未保存设备 ID")
        }
        val mac = normalizeMacAddress(deviceId)
            ?: return VerificationResult(valid = false, message = "设备 ID 格式无效: $deviceId")
        val association = findAssociation(context, deviceId)
        return if (association != null) {
            VerificationResult(
                valid = true,
                macAddress = mac,
                associationId = association.id.coerceAtLeast(0),
                message = "已关联伴随设备",
            )
        } else {
            VerificationResult(
                valid = false,
                macAddress = mac,
                message = "设备 $mac 未注册为伴随设备，后台优先级可能较低",
            )
        }
    }

    fun disassociate(context: Context, associationId: Int, deviceMac: String? = null): Boolean {
        if (!isSupported()) return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && associationId >= 0) {
                manager.disassociate(associationId)
            } else {
                val mac = normalizeMacAddress(deviceMac) ?: return false
                @Suppress("DEPRECATION")
                manager.disassociate(mac)
            }
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "disassociate failed: ${e.message}")
            false
        }
    }

    /**
     * Polar deviceId 通常为无冒号 MAC；CDM 需要 `AA:BB:CC:DD:EE:FF` 格式。
     */
    fun normalizeMacAddress(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()
            .replace(":", "")
            .replace("-", "")
            .uppercase()
        if (cleaned.length != 12 || !cleaned.all { it in '0'..'9' || it in 'A'..'F' }) {
            return null
        }
        return cleaned.chunked(2).joinToString(":")
    }

    private fun macEquals(a: String, b: String): Boolean {
        val na = normalizeMacAddress(a) ?: return false
        val nb = normalizeMacAddress(b) ?: return false
        return na.equals(nb, ignoreCase = true)
    }
}
