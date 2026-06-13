package com.owner.mindbody.polar

import android.content.Context
import android.util.Log
import com.owner.mindbody.data.DevicePreferences
import com.owner.mindbody.data.storage.AppStorage
import com.owner.mindbody.data.sync.DeviceSyncManager
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpiData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

enum class ConnectionState {
    BLE_OFF,
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/** BLE 连接策略：常连接持续采集，或短连接按需快照。 */
enum class ConnectionMode {
    PERSISTENT,
    ON_DEMAND
}

data class ScannedDevice(
    val deviceId: String,
    val name: String,
    val rssi: Int
)

/** 三轴加速度最新样本（单位：millig）。 */
data class AccSample(val x: Int, val y: Int, val z: Int)

/**
 * Polar BLE SDK 封装：负责连接、FTU、心率流采集与本地持久化。
 */
class PolarBleManager(
    context: Context,
    private val storage: AppStorage,
    private val devicePreferences: DevicePreferences,
    private val deviceSyncManager: DeviceSyncManager
) : PolarBleApiCallback() {

    companion object {
        private const val TAG = "PolarBleManager"
        private const val RECONNECT_DELAY_MS = 3_000L
        private const val SNAPSHOT_SAMPLE_DURATION_MS = 5_000L
        private const val SNAPSHOT_TIMEOUT_MS = 30_000L
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val api: PolarBleApi = PolarBleApiDefaultImpl.defaultImplementation(
        appContext,
        setOf(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA
        )
    )

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceId = MutableStateFlow<String?>(null)
    val connectedDeviceId: StateFlow<String?> = _connectedDeviceId.asStateFlow()

    private val _currentHr = MutableStateFlow<Int?>(null)
    val currentHr: StateFlow<Int?> = _currentHr.asStateFlow()

    /** 当前皮肤温度（摄氏度），来自 Loop 在线流。 */
    private val _currentSkinTemp = MutableStateFlow<Float?>(null)
    val currentSkinTemp: StateFlow<Float?> = _currentSkinTemp.asStateFlow()

    /** 当前三轴加速度最新样本。 */
    private val _currentAcc = MutableStateFlow<AccSample?>(null)
    val currentAcc: StateFlow<AccSample?> = _currentAcc.asStateFlow()

    /** 最新 PPI（心跳间期）样本。 */
    private val _latestPpi = MutableStateFlow<PolarPpiData.PolarPpiSample?>(null)
    val latestPpi: StateFlow<PolarPpiData.PolarPpiSample?> = _latestPpi.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    private val _ftuDone = MutableStateFlow(false)
    val ftuDone: StateFlow<Boolean> = _ftuDone.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _connectionMode = MutableStateFlow(ConnectionMode.PERSISTENT)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()

    private var hrStreamJob: Job? = null
    private var skinTempStreamJob: Job? = null
    private var accStreamJob: Job? = null
    private var ppiStreamJob: Job? = null
    private var scanJob: Job? = null
    private var reconnectJob: Job? = null
    private var hrFeatureReady = false
    private var userInitiatedDisconnect = false

    init {
        api.setApiCallback(this)
        deviceSyncManager.attachApi(api)
        scope.launch {
            devicePreferences.ftuDone.collect { _ftuDone.value = it }
        }
        scope.launch {
            devicePreferences.connectionMode.collect { _connectionMode.value = it }
        }
    }

    fun sdkVersion(): String = PolarBleApiDefaultImpl.versionInfo()

    fun searchForDevices() {
        scanJob?.cancel()
        _scannedDevices.value = emptyList()
        _statusMessage.value = "正在扫描 Polar 设备…"
        scanJob = scope.launch {
            try {
                api.searchForDevice()
                    .catch { e ->
                        Log.e(TAG, "Scan error", e)
                        _statusMessage.value = "扫描失败：${e.message}"
                    }
                    .collect { info ->
                        val device = ScannedDevice(
                            deviceId = info.deviceId,
                            name = info.name.ifBlank { info.deviceId },
                            rssi = info.rssi
                        )
                        _scannedDevices.value = (_scannedDevices.value + device)
                            .distinctBy { it.deviceId }
                            .sortedByDescending { it.rssi }
                        _statusMessage.value = "已发现 ${_scannedDevices.value.size} 台设备"
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _statusMessage.value = "扫描失败：${e.message}"
            }
        }
    }

    fun connectToDevice(deviceId: String) {
        scope.launch {
            try {
                userInitiatedDisconnect = false
                reconnectJob?.cancel()
                devicePreferences.saveDeviceId(deviceId)
                api.connectToDevice(deviceId)
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed", e)
                _statusMessage.value = "连接失败：${e.message}"
            }
        }
    }

    fun connectSavedDevice(deviceId: String) = connectToDevice(deviceId)

    fun disconnect() {
        val id = _connectedDeviceId.value ?: return
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        stopHrStreaming()
        stopSkinTempStreaming()
        stopAccStreaming()
        stopPpiStreaming()
        api.disconnectFromDevice(id)
    }

    fun setConnectionMode(mode: ConnectionMode) {
        scope.launch {
            devicePreferences.setConnectionMode(mode)
        }
        if (mode == ConnectionMode.ON_DEMAND && _connectionState.value == ConnectionState.CONNECTED) {
            disconnect()
        }
    }

    /**
     * 短连接模式：连接设备，采集约 5 秒心率样本后断开，返回平均 BPM。
     * Phase 2 记录心情时关联 HR 快照使用。
     */
    suspend fun connectForSnapshot(deviceId: String): Int? {
        return try {
            withTimeout(SNAPSHOT_TIMEOUT_MS) {
                if (_connectionState.value != ConnectionState.CONNECTED ||
                    _connectedDeviceId.value != deviceId
                ) {
                    userInitiatedDisconnect = false
                    reconnectJob?.cancel()
                    devicePreferences.saveDeviceId(deviceId)
                    api.connectToDevice(deviceId)
                    connectionState.first { it == ConnectionState.CONNECTED }
                    delay(1_000)
                }

                val samples = collectHrSamples(SNAPSHOT_SAMPLE_DURATION_MS)
                val average = samples.takeIf { it.isNotEmpty() }?.average()?.toInt()

                userInitiatedDisconnect = true
                val connectedId = _connectedDeviceId.value
                if (connectedId != null) {
                    stopHrStreaming()
                    api.disconnectFromDevice(connectedId)
                }

                average
            }
        } catch (e: Exception) {
            Log.e(TAG, "Snapshot failed", e)
            _statusMessage.value = "HR 快照失败：${e.message}"
            null
        }
    }

    fun shutdown() {
        stopHrStreaming()
        stopSkinTempStreaming()
        stopAccStreaming()
        stopPpiStreaming()
        api.shutDown()
    }

    suspend fun checkFtuStatus(deviceId: String): Boolean {
        return try {
            val done = api.isFtuDone(deviceId)
            _ftuDone.value = done
            devicePreferences.setFtuDone(done)
            done
        } catch (e: Exception) {
            Log.e(TAG, "FTU check failed", e)
            false
        }
    }

    suspend fun performFirstTimeUse(
        deviceId: String,
        config: PolarFirstTimeUseConfig
    ): Result<Unit> {
        return try {
            api.doFirstTimeUse(deviceId, config)
            devicePreferences.setFtuDone(true)
            _ftuDone.value = true
            _statusMessage.value = "首次配置完成"
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "FTU failed", e)
            Result.failure(e)
        }
    }

    /** 使用合理默认值构建 FTU 配置（用户可在 UI 中覆盖关键字段） */
    fun buildDefaultFtuConfig(
        gender: PolarFirstTimeUseConfig.Gender,
        birthDate: LocalDate,
        heightCm: Float,
        weightKg: Float,
        restingHr: Int
    ): PolarFirstTimeUseConfig {
        val age = LocalDate.now().year - birthDate.year
        val maxHr = (220 - age).coerceIn(
            PolarFirstTimeUseConfig.MAX_HEART_RATE_MIN,
            PolarFirstTimeUseConfig.MAX_HEART_RATE_MAX
        )
        return PolarFirstTimeUseConfig(
            gender = gender,
            birthDate = birthDate,
            height = heightCm,
            weight = weightKg,
            maxHeartRate = maxHr,
            restingHeartRate = restingHr.coerceIn(
                PolarFirstTimeUseConfig.RESTING_HEART_RATE_MIN,
                PolarFirstTimeUseConfig.RESTING_HEART_RATE_MAX
            ),
            vo2Max = 40,
            trainingBackground = 30,
            typicalDay = PolarFirstTimeUseConfig.TypicalDay.MOSTLY_SITTING,
            sleepGoalMinutes = 8 * 60,
            deviceTime = LocalDateTime.now().atOffset(ZoneOffset.UTC).withNano(0).toString()
        )
    }

    private suspend fun collectHrSamples(durationMs: Long): List<Int> {
        val samples = mutableListOf<Int>()
        val deadline = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < deadline) {
            _currentHr.value?.takeIf { it > 0 }?.let { samples.add(it) }
            delay(500)
        }
        return samples
    }

    private fun scheduleReconnect(deviceId: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (_connectionMode.value == ConnectionMode.PERSISTENT &&
                !userInitiatedDisconnect &&
                _connectionState.value == ConnectionState.DISCONNECTED
            ) {
                _statusMessage.value = "正在尝试重新连接…"
                connectToDevice(deviceId)
            }
        }
    }

    private fun startHrStreaming(deviceId: String) {
        if (hrStreamJob?.isActive == true) return
        hrStreamJob = scope.launch {
            api.startHrStreaming(deviceId)
                .catch { e ->
                    Log.e(TAG, "HR stream error", e)
                    _statusMessage.value = "心率流中断：${e.message}"
                }
                .collect { hrData ->
                    processHrData(hrData)
                }
        }
    }

    private fun stopHrStreaming() {
        hrStreamJob?.cancel()
        hrStreamJob = null
        val deviceId = _connectedDeviceId.value
        if (deviceId != null) {
            scope.launch {
                try {
                    api.stopHrStreaming(deviceId)
                } catch (e: Exception) {
                    Log.w(TAG, "Stop HR stream", e)
                } finally {
                    storage.hr.flush()
                }
            }
        } else {
            scope.launch {
                storage.hr.flush()
            }
        }
        _currentHr.value = null
    }

    /** 启动皮肤温度在线流（需 FEATURE_POLAR_ONLINE_STREAMING 就绪）。 */
    private fun startSkinTempStreaming(deviceId: String) {
        if (skinTempStreamJob?.isActive == true) return
        skinTempStreamJob = scope.launch {
            try {
                val settings = api.requestStreamSettings(
                    deviceId,
                    PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE
                ).maxSettings()
                api.startSkinTemperatureStreaming(deviceId, settings)
                    .catch { e ->
                        Log.e(TAG, "Skin temp stream error", e)
                        _statusMessage.value = "皮肤温度流中断：${e.message}"
                    }
                    .collect { data ->
                        processSkinTempData(data)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Skin temp settings failed", e)
                _statusMessage.value = "皮肤温度启动失败：${e.message}"
            }
        }
    }

    /** 停止皮肤温度流并清空当前读数。 */
    private fun stopSkinTempStreaming() {
        skinTempStreamJob?.cancel()
        skinTempStreamJob = null
        scope.launch { storage.skinTemp.flush() }
        _currentSkinTemp.value = null
    }

    /** 启动加速度在线流（需 FEATURE_POLAR_ONLINE_STREAMING 就绪）。 */
    private fun startAccStreaming(deviceId: String) {
        if (accStreamJob?.isActive == true) return
        accStreamJob = scope.launch {
            try {
                val settings = api.requestStreamSettings(
                    deviceId,
                    PolarBleApi.PolarDeviceDataType.ACC
                ).maxSettings()
                api.startAccStreaming(deviceId, settings)
                    .catch { e ->
                        Log.e(TAG, "ACC stream error", e)
                        _statusMessage.value = "加速度流中断：${e.message}"
                    }
                    .collect { data ->
                        processAccData(data)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "ACC settings failed", e)
                _statusMessage.value = "加速度启动失败：${e.message}"
            }
        }
    }

    /** 停止加速度流并清空当前读数。 */
    private fun stopAccStreaming() {
        accStreamJob?.cancel()
        accStreamJob = null
        scope.launch { storage.acc.flush() }
        _currentAcc.value = null
    }

    /** 启动 PPI 在线流（无需额外 settings）。 */
    private fun startPpiStreaming(deviceId: String) {
        if (ppiStreamJob?.isActive == true) return
        ppiStreamJob = scope.launch {
            try {
                api.startPpiStreaming(deviceId)
                    .catch { e ->
                        Log.e(TAG, "PPI stream error", e)
                        _statusMessage.value = "PPI 流中断：${e.message}"
                    }
                    .collect { data ->
                        processPpiData(data)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "PPI stream failed", e)
                _statusMessage.value = "PPI 启动失败：${e.message}"
            }
        }
    }

    /** 停止 PPI 流并清空当前读数。 */
    private fun stopPpiStreaming() {
        ppiStreamJob?.cancel()
        ppiStreamJob = null
        scope.launch { storage.ppi.flush() }
        _latestPpi.value = null
    }

    private suspend fun processSkinTempData(data: com.polar.sdk.api.model.PolarTemperatureData) {
        for (sample in data.samples) {
            _currentSkinTemp.value = sample.temperature
            storage.skinTemp.saveSample(
                timestamp = System.currentTimeMillis(),
                temperatureC = sample.temperature
            )
        }
    }

    private suspend fun processAccData(data: com.polar.sdk.api.model.PolarAccelerometerData) {
        val now = System.currentTimeMillis()
        for (sample in data.samples) {
            _currentAcc.value = AccSample(x = sample.x, y = sample.y, z = sample.z)
            storage.acc.ingestSample(sample.x, sample.y, sample.z, now)
        }
    }

    private suspend fun processPpiData(data: PolarPpiData) {
        for (sample in data.samples) {
            _latestPpi.value = sample
            storage.ppi.saveSample(
                timestamp = System.currentTimeMillis(),
                sample = sample
            )
        }
    }

    private suspend fun processHrData(hrData: PolarHrData) {
        for (sample in hrData.samples) {
            val hr = sample.hr
            if (hr > 0) {
                _currentHr.value = hr
                val rr = sample.rrsMs.firstOrNull()
                storage.hr.saveSample(
                    timestamp = System.currentTimeMillis(),
                    bpm = hr,
                    rrMs = rr
                )
            }
        }
    }

    // --- PolarBleApiCallback ---

    override fun blePowerStateChanged(powered: Boolean) {
        _connectionState.value = if (powered) ConnectionState.DISCONNECTED else ConnectionState.BLE_OFF
        if (!powered) {
            _connectedDeviceId.value = null
            stopHrStreaming()
            stopSkinTempStreaming()
            stopAccStreaming()
            stopPpiStreaming()
        }
    }

    override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
        _connectionState.value = ConnectionState.CONNECTING
        _statusMessage.value = "正在连接 ${polarDeviceInfo.deviceId}…"
    }

    override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        _connectionState.value = ConnectionState.CONNECTED
        _connectedDeviceId.value = polarDeviceInfo.deviceId
        _statusMessage.value = "已连接 ${polarDeviceInfo.deviceId}"
        hrFeatureReady = false
        scope.launch {
            checkFtuStatus(polarDeviceInfo.deviceId)
        }
    }

    override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceId.value = null
        _currentHr.value = null
        hrFeatureReady = false
        deviceSyncManager.resetFeatureFlags()
        stopHrStreaming()
        stopSkinTempStreaming()
        stopAccStreaming()
        stopPpiStreaming()
        _statusMessage.value = "已断开 ${polarDeviceInfo.deviceId}"
        if (_connectionMode.value == ConnectionMode.PERSISTENT && !userInitiatedDisconnect) {
            scheduleReconnect(polarDeviceInfo.deviceId)
        }
    }

    override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
        when (feature) {
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR -> {
                hrFeatureReady = true
                startHrStreaming(identifier)
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING -> {
                startSkinTempStreaming(identifier)
                startAccStreaming(identifier)
                startPpiStreaming(identifier)
            }
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA -> {
                deviceSyncManager.onFeatureReady(identifier, feature)
            }
            else -> Unit
        }
    }

    override fun batteryLevelReceived(identifier: String, level: Int) {
        _batteryLevel.value = level
    }

    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
        Log.d(TAG, "DIS $uuid = $value")
    }

    // SDK 8.0 新增：以 key-value 形式返回设备信息（如固件版本、序列号等）
    override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
        Log.d(TAG, "DIS ${disInfo.key} = ${disInfo.value}")
    }

    // SDK 8.0 新增：体温计数据回调（Loop 心率场景暂不使用，空实现即可）
    override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) {
        Log.d(TAG, "HTS ${data.celsius}°C")
    }
}
