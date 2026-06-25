package com.owner.mindbody.polar

import android.content.Context
import com.owner.mindbody.data.DevicePreferences
import com.owner.mindbody.data.stream.PpiLiveBuffer
import com.owner.mindbody.data.storage.AppStorage
import com.owner.mindbody.data.sync.DeviceSyncManager
import com.owner.mindbody.util.AppLogger
import com.owner.mindbody.util.BlePermissionHelper
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourcesState
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarSensorSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
        private const val SNAPSHOT_TIMEOUT_MS = 30_000L
        private const val AUTO_CONNECT_SCAN_TIMEOUT_MS = 15_000L
        private const val AUTO_CONNECT_WATCHDOG_MS = 25_000L
        private const val STALE_GATT_CLEANUP_DELAY_MS = 1_000L

        // HR 流启动时如遇 BleServiceNotFound（GATT 服务尚未发现），最多重试 3 次
        private const val HR_STREAM_MAX_RETRIES = 3
        private const val HR_STREAM_RETRY_DELAY_MS = 3_000L

        // Polar 设备 epoch 为 2000-01-01T00:00:00Z，Unix epoch 为 1970-01-01T00:00:00Z。
        // 写入 DB / 上报服务端前须加此偏移（毫秒），使时间戳与手机端 System.currentTimeMillis() 同域。
        private const val POLAR_TO_UNIX_EPOCH_OFFSET_MS = 946_684_800_000L

        /**
         * 将 Polar 传感器纳秒时间戳转换为 Unix 毫秒时间戳。
         * @return Unix ms，或 null（当 timeStampNs == 0，表示设备未校时或不支持该字段）
         */
        fun polarSensorTimeToUnixMs(timeStampNs: ULong): Long? {
            if (timeStampNs == 0uL) return null
            // 先以 ULong 做除法，避免极大值 toLong() 时符号翻转
            val polarMs = (timeStampNs / 1_000_000uL).toLong()
            return polarMs + POLAR_TO_UNIX_EPOCH_OFFSET_MS
        }
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
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TEMPERATURE_DATA
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

    /** PPI 在线流环形缓冲区 — WorkManager 推流消费端从此取窗口。 */
    val ppiLiveBuffer = PpiLiveBuffer(maxSize = 600)

    /** 当前加速度幅值 (mg) — 从最新 AccSample 计算。 */
    private val _currentAccMagnitudeMg = MutableStateFlow<Int?>(null)
    val currentAccMagnitudeMg: StateFlow<Int?> = _currentAccMagnitudeMg.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel: StateFlow<Int?> = _batteryLevel.asStateFlow()

    // 电量校准参数：手表实际报告的 [inputMin, inputMax] 映射到显示 [0, 100]
    // 例如 Polar Loop 报告 50~100，则 inputMin=50, inputMax=100
    // 默认 0~100 不做校准
    private var batteryInputMin: Int = 0
    private var batteryInputMax: Int = 100

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
    private var accSampleRateHz: Long = 52
    private var ppiStreamJob: Job? = null
    private var scanJob: Job? = null
    private var autoConnectJob: Job? = null
    private var reconnectJob: Job? = null
    private var hrFeatureReady = false
    private var userInitiatedDisconnect = false
    /** 本进程内是否已尝试过启动自动连，避免反复扫描。 */
    private var autoConnectAttemptedThisSession = false

    /** 更新 UI 状态文案并写入运行日志缓冲。 */
    private fun setStatus(message: String) {
        _statusMessage.value = message
        AppLogger.i(TAG, message)
    }

    init {
        api.setApiCallback(this)
        deviceSyncManager.attachApi(api)
        scope.launch {
            devicePreferences.ftuDone.collect { _ftuDone.value = it }
        }
        scope.launch {
            devicePreferences.connectionMode.collect { _connectionMode.value = it }
        }
        scope.launch {
            devicePreferences.batteryInputMin.collect { batteryInputMin = it }
        }
        scope.launch {
            devicePreferences.batteryInputMax.collect { batteryInputMax = it }
        }
    }

    /** 设置电量校准参数并持久化。调用后需重新连接设备才能看到新值。 */
    fun setBatteryCalibration(inputMin: Int, inputMax: Int) {
        scope.launch {
            devicePreferences.setBatteryInputMin(inputMin)
            devicePreferences.setBatteryInputMax(inputMax)
        }
    }

    fun sdkVersion(): String = PolarBleApiDefaultImpl.versionInfo()

    fun searchForDevices() {
        autoConnectJob?.cancel()
        scanJob?.cancel()
        _scannedDevices.value = emptyList()
        setStatus("正在扫描 Polar 设备…")
        scanJob = scope.launch {
            try {
                api.searchForDevice()
                    .catch { e ->
                        AppLogger.e(TAG, "Scan error", e)
                        setStatus("扫描失败：${e.message}")
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
                        setStatus("已发现 ${_scannedDevices.value.size} 台设备")
                    }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Search failed", e)
                setStatus("扫描失败：${e.message}")
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
                AppLogger.e(TAG, "Connect failed", e)
                setStatus("连接失败：${e.message}")
            }
        }
    }

    fun connectSavedDevice(deviceId: String) = connectToDevice(deviceId)

    /**
     * APP 启动或蓝牙就绪后：扫描已保存设备并连接。
     * 常连接与短连接模式均会触发；会话内用户主动断开后不会再次自动连。
     *
     * @param force 为 true 时忽略「本进程已尝试」标记（如蓝牙从关到开）
     */
    fun tryAutoConnectSavedDevice(force: Boolean = false) {
        // 已有自动连在进行时合并重复触发，避免 cancel 导致 Startup 双入口互相打断
        if (autoConnectJob?.isActive == true) {
            AppLogger.d(TAG, "Auto-connect already in progress, skip duplicate trigger")
            return
        }
        autoConnectJob = scope.launch {
            tryAutoConnectInternal(force)
        }
    }

    private suspend fun tryAutoConnectInternal(force: Boolean) {
        if (!force && autoConnectAttemptedThisSession) return
        if (!BlePermissionHelper.hasAllPermissions(appContext)) return

        val state = _connectionState.value
        if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) return
        if (state == ConnectionState.BLE_OFF) return

        val savedId = devicePreferences.savedDeviceId.first()?.takeIf { it.isNotBlank() } ?: return

        setStatus("正在自动连接已保存设备…")
        scanJob?.cancel()

        try {
            // 先扫描目标设备（不更新设备页列表），超时则直连兜底
            try {
                withTimeout(AUTO_CONNECT_SCAN_TIMEOUT_MS) {
                    api.searchForDevice()
                        .catch { e -> AppLogger.w(TAG, "Auto-connect scan error", e) }
                        .first { info -> info.deviceId == savedId }
                }
            } catch (_: TimeoutCancellationException) {
                AppLogger.d(TAG, "Auto-connect scan timeout, trying direct connect")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Auto-connect scan failed", e)
            }

            if (_connectionState.value == ConnectionState.CONNECTED ||
                _connectionState.value == ConnectionState.CONNECTING
            ) {
                autoConnectAttemptedThisSession = true
                return
            }

            try {
                // 清理上次进程残留的 GATT 连接，使设备重新开始广播
                userInitiatedDisconnect = true
                try {
                    api.disconnectFromDevice(savedId)
                    delay(STALE_GATT_CLEANUP_DELAY_MS)
                } catch (_: Exception) {
                    // 未连接时忽略
                }

                userInitiatedDisconnect = false
                reconnectJob?.cancel()
                devicePreferences.saveDeviceId(savedId)
                api.connectToDevice(savedId)

                // 看门狗：SDK 完全静默（无任何回调）时，强制重置并触发 scheduleReconnect
                val connected = withTimeoutOrNull(AUTO_CONNECT_WATCHDOG_MS) {
                    if (_connectionState.value == ConnectionState.CONNECTED) return@withTimeoutOrNull true
                    connectionState.first {
                        it == ConnectionState.CONNECTING || it == ConnectionState.CONNECTED
                    }
                    if (_connectionState.value == ConnectionState.CONNECTED) return@withTimeoutOrNull true
                    connectionState.first {
                        it == ConnectionState.CONNECTED || it == ConnectionState.DISCONNECTED
                    }
                    _connectionState.value == ConnectionState.CONNECTED
                } ?: false

                if (!connected) {
                    AppLogger.w(TAG, "Auto-connect watchdog triggered: resetting state")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    scheduleReconnect(savedId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Auto-connect failed", e)
                setStatus("自动连接失败：${e.message}")
            }
            autoConnectAttemptedThisSession = true
        } catch (e: CancellationException) {
            AppLogger.d(TAG, "Auto-connect cancelled")
            throw e
        }
    }

    fun disconnect() {
        val id = _connectedDeviceId.value ?: return
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        stopHrStreaming()
        stopSkinTempStreaming()
        stopAccStreaming()
        stopPpiStreaming()
        HrStreamService.stop(appContext)
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
     * 短连接模式：连接设备，拿到第一条有效心率后断开，减少记录时刻偏差。
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

                val snapshot = currentHr.first { it != null && it > 0 }

                userInitiatedDisconnect = true
                val connectedId = _connectedDeviceId.value
                if (connectedId != null) {
                    stopHrStreaming()
                    api.disconnectFromDevice(connectedId)
                }

                snapshot
            }
        } catch (e: TimeoutCancellationException) {
            AppLogger.w(TAG, "Snapshot timed out: ${e.message}")
            setStatus("HR 快照超时，请靠近手环后重试")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Snapshot failed", e)
            setStatus("HR 快照失败：${e.message}")
            null
        }
    }

    fun shutdown() {
        // 先同步 flush 所有缓冲区，避免 scope.cancel() 杀死待处理的 flush 协程
        runBlocking {
            storage.flushAll()
        }
        stopHrStreaming()
        stopSkinTempStreaming()
        stopAccStreaming()
        stopPpiStreaming()
        api.shutDown()
        deviceSyncManager.shutdown()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    suspend fun checkFtuStatus(deviceId: String): Boolean {
        return try {
            val done = api.isFtuDone(deviceId)
            _ftuDone.value = done
            devicePreferences.setFtuDone(done)
            done
        } catch (e: Exception) {
            AppLogger.e(TAG, "FTU check failed", e)
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
            setStatus("首次配置完成")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "FTU failed", e)
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

    private fun scheduleReconnect(deviceId: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (_connectionMode.value == ConnectionMode.PERSISTENT &&
                !userInitiatedDisconnect &&
                _connectionState.value == ConnectionState.DISCONNECTED
            ) {
                setStatus("正在尝试重新连接…")
                connectToDevice(deviceId)
            }
        }
    }

    private fun startHrStreaming(deviceId: String) {
        if (hrStreamJob?.isActive == true) return
        hrStreamJob = scope.launch {
            // 外层 try-catch 捕获 BleServiceNotFound 等在 Flow 建立阶段（非 emit 阶段）
            // 抛出的异常。这类异常绕过 .catch 算子直接逃逸至协程，在 SupervisorJob 下
            // 若不捕获会触发线程 UncaughtExceptionHandler → 进程崩溃。
            // Polar Loop Gen 2 在 GATT 完成服务发现前调用 setCharacteristicNotify 即触发此异常。
            var lastException: Exception? = null
            for (attempt in 1..HR_STREAM_MAX_RETRIES) {
                try {
                    api.startHrStreaming(deviceId)
                        .catch { e ->
                            if (e is CancellationException) throw e
                            AppLogger.e(TAG, "HR stream error (attempt=$attempt)", e)
                            setStatus("心率流中断：${e.message}")
                        }
                        .collect { hrData ->
                            processHrData(hrData)
                        }
                    return@launch  // 正常结束（流关闭），不继续重试
                } catch (e: CancellationException) {
                    throw e  // 协程取消必须向上传播
                } catch (e: Exception) {
                    lastException = e
                    AppLogger.w(
                        TAG,
                        "HR stream setup failed (attempt=$attempt/$HR_STREAM_MAX_RETRIES): " +
                            "${e.javaClass.simpleName} – ${e.message}"
                    )
                    if (attempt < HR_STREAM_MAX_RETRIES) {
                        setStatus("心率服务初始化中，稍后重试…")
                        delay(HR_STREAM_RETRY_DELAY_MS)
                        // 若期间设备已断开，停止重试
                        if (_connectionState.value != ConnectionState.CONNECTED ||
                            _connectedDeviceId.value != deviceId
                        ) return@launch
                    }
                }
            }
            AppLogger.e(TAG, "HR streaming gave up after $HR_STREAM_MAX_RETRIES retries", lastException)
            setStatus("心率服务暂不可用，请确认设备佩戴后重连")
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
                    AppLogger.w(TAG, "Stop HR stream", e)
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
                        AppLogger.e(TAG, "Skin temp stream error", e)
                        setStatus("皮肤温度流中断：${e.message}")
                    }
                    .collect { data ->
                        processSkinTempData(data)
                    }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Skin temp settings failed", e)
                setStatus("皮肤温度启动失败：${e.message}")
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
                accSampleRateHz = settings.settings[PolarSensorSetting.SettingType.SAMPLE_RATE]
                    ?.firstOrNull()
                    ?.toLong()
                    ?.coerceAtLeast(1)
                    ?: 52L
                api.startAccStreaming(deviceId, settings)
                    .catch { e ->
                        AppLogger.e(TAG, "ACC stream error", e)
                        setStatus("加速度流中断：${e.message}")
                    }
                    .collect { data ->
                        processAccData(data)
                    }
            } catch (e: Exception) {
                AppLogger.w(TAG, "ACC settings failed", e)
                setStatus("加速度启动失败：${e.message}")
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
                        AppLogger.e(TAG, "PPI stream error", e)
                        setStatus("PPI 流中断：${e.message}")
                    }
                    .collect { data ->
                        processPpiData(data)
                    }
            } catch (e: Exception) {
                AppLogger.w(TAG, "PPI stream failed", e)
                setStatus("PPI 启动失败：${e.message}")
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

    // 注: Polar BLE SDK 8.0.0 的 PolarPpiSample 已暴露 timeStamp 纳秒传感器时间戳，
    // PPI 已优先使用传感器时间（见 processPpiData）。
    // PolarHrData / PolarTemperatureData / PolarAccelerometerData 仍未暴露传感器时间戳，
    // 这些数据暂时沿用 System.currentTimeMillis() 作为折中。
    private suspend fun processSkinTempData(data: com.polar.sdk.api.model.PolarTemperatureData) {
        val now = System.currentTimeMillis()
        for (sample in data.samples) {
            _currentSkinTemp.value = sample.temperature
            storage.skinTemp.saveSample(
                timestamp = now,
                temperatureC = sample.temperature
            )
        }
    }

    private suspend fun processAccData(data: com.polar.sdk.api.model.PolarAccelerometerData) {
        if (data.samples.isEmpty()) return
        val batchEndMs = System.currentTimeMillis()
        val intervalMs = 1000L / accSampleRateHz.coerceAtLeast(1)
        data.samples.forEachIndexed { i, sample ->
            val ts = batchEndMs - (data.samples.size - 1 - i) * intervalMs
            _currentAcc.value = AccSample(x = sample.x, y = sample.y, z = sample.z)
            // 计算幅值 magnitude(mg) = sqrt(x² + y² + z²) — 1g=1000mg 重力分量
            val mag = kotlin.math.sqrt(
                (sample.x * sample.x + sample.y * sample.y + sample.z * sample.z).toDouble()
            ).toInt()
            _currentAccMagnitudeMg.value = mag
            storage.acc.ingestSample(sample.x, sample.y, sample.z, ts)
        }
    }

    private suspend fun processPpiData(data: PolarPpiData) {
        for (sample in data.samples) {
            _latestPpi.value = sample
            // PolarPpiSample.timeStamp 是传感器纳秒级时间戳（每个心跳独立标记，2000-01-01 起）
            // 须转换为 Unix ms（+946684800000）才能与手机端时间域对齐。
            // timeStamp == 0 表示设备未校时或固件不支持，此时 fallback 到手机时间并告警。
            val timestamp = polarSensorTimeToUnixMs(sample.timeStamp)
                ?: run {
                    AppLogger.w(TAG, "PPI sample timeStamp=0, falling back to system time")
                    System.currentTimeMillis()
                }
            storage.ppi.saveSample(
                timestamp = timestamp,
                sample = sample
            )
            // 同步写入 Live Buffer 供推流 Worker 消费
            ppiLiveBuffer.push(
                timestampMs = timestamp,
                ppiMs = sample.ppi,
                hrBpm = sample.hr,
                blocker = sample.blockerBit,
                skinContactOk = sample.skinContactStatus,
                errorEstimateMs = sample.errorEstimate,
                accMagnitudeMg = _currentAccMagnitudeMg.value
            )
        }
    }

    private suspend fun processHrData(hrData: PolarHrData) {
        val now = System.currentTimeMillis()
        for (sample in hrData.samples) {
            val hr = sample.hr
            if (hr > 0) {
                _currentHr.value = hr
                val rr = sample.rrsMs.firstOrNull()
                storage.hr.saveSample(
                    timestamp = now,
                    bpm = hr,
                    rrMs = rr
                )
            }
        }
    }

    // --- PolarBleApiCallback ---

    override fun blePowerStateChanged(powered: Boolean) {
        AppLogger.i(TAG, "blePowerStateChanged powered=$powered")
        _connectionState.value = if (powered) ConnectionState.DISCONNECTED else ConnectionState.BLE_OFF
        if (!powered) {
            _connectedDeviceId.value = null
            stopHrStreaming()
            stopSkinTempStreaming()
            stopAccStreaming()
            stopPpiStreaming()
        } else {
            tryAutoConnectSavedDevice(force = true)
        }
    }

    override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
        _connectionState.value = ConnectionState.CONNECTING
        setStatus("正在连接 ${polarDeviceInfo.deviceId}…")
        AppLogger.i(TAG, "deviceConnecting id=${polarDeviceInfo.deviceId}")
    }

    override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
        _connectionState.value = ConnectionState.CONNECTED
        _connectedDeviceId.value = polarDeviceInfo.deviceId
        setStatus("已连接 ${polarDeviceInfo.deviceId}")
        AppLogger.i(TAG, "deviceConnected id=${polarDeviceInfo.deviceId} name=${polarDeviceInfo.name}")
        HrStreamService.start(appContext)
        hrFeatureReady = false
        scope.launch {
            // GATT 服务刚连接时 BLE 通道尚未稳定，延迟后再查 FTU
            delay(1_000L)
            var checked = false
            repeat(2) { attempt ->
                if (checked) return@repeat
                try {
                    val result = api.isFtuDone(polarDeviceInfo.deviceId)
                    _ftuDone.value = result
                    devicePreferences.setFtuDone(result)
                    AppLogger.d(TAG, "FTU status=$result id=${polarDeviceInfo.deviceId}")
                    checked = true
                } catch (e: Exception) {
                    if (attempt < 1) {
                        delay(1_000L)
                    } else {
                        AppLogger.w(
                            TAG,
                            "FTU check failed after retries (${e.javaClass.simpleName}), keeping cached value"
                        )
                    }
                }
            }
        }
    }

    override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDeviceId.value = null
        _currentHr.value = null
        _batteryLevel.value = null // 断开时清除电量，避免旧值残留
        hrFeatureReady = false
        deviceSyncManager.resetFeatureFlags()
        stopHrStreaming()
        stopSkinTempStreaming()
        stopAccStreaming()
        stopPpiStreaming()
        // 仅在真实断连时停服务；GATT 清理临时断（userInitiatedDisconnect=true）不停
        if (!userInitiatedDisconnect) {
            HrStreamService.stop(appContext)
        }
        setStatus("已断开 ${polarDeviceInfo.deviceId}")
        AppLogger.i(
            TAG,
            "deviceDisconnected id=${polarDeviceInfo.deviceId} userInitiated=$userInitiatedDisconnect"
        )
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
                scope.launch {
                    // 先同步设备时间，确保 PPI 帧 timeStamp 有效（基于正确的 Polar epoch）。
                    // 设备刚就绪时 GATT 可能尚未稳定，失败时退避重试后再启动流。
                    var synced = false
                    repeat(3) { attempt ->
                        if (synced) return@repeat
                        try {
                            api.setLocalTime(identifier, LocalDateTime.now())
                            AppLogger.d(
                                TAG,
                                "Device time synced before streaming id=$identifier (attempt=${attempt + 1})"
                            )
                            synced = true
                        } catch (e: Exception) {
                            if (attempt < 2) {
                                delay(500L)
                            } else {
                                AppLogger.w(
                                    TAG,
                                    "setLocalTime failed after 3 attempts (${e.javaClass.simpleName}), " +
                                        "PPI timestamps may fall back to system time",
                                    e
                                )
                            }
                        }
                    }
                    startSkinTempStreaming(identifier)
                    startAccStreaming(identifier)
                    startPpiStreaming(identifier)
                }
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
        AppLogger.d(TAG, "batteryLevelReceived id=$identifier rawLevel=$level " +
                "inputRange=[$batteryInputMin..$batteryInputMax]")
        // 应用校准：将手表报告的值域映射到 0~100
        val calibrated = if (batteryInputMin < batteryInputMax) {
            ((level - batteryInputMin).toFloat() / (batteryInputMax - batteryInputMin) * 100f)
                .toInt()
                .coerceIn(0, 100)
        } else {
            level.coerceIn(0, 100)
        }
        AppLogger.d(TAG, "batteryLevelReceived id=$identifier calibratedLevel=$calibrated")
        _batteryLevel.value = calibrated
    }

    override fun batteryChargingStatusReceived(identifier: String, chargingStatus: ChargeState) {
        AppLogger.d(TAG, "batteryChargingStatusReceived id=$identifier status=$chargingStatus")
    }

    override fun powerSourcesStateReceived(identifier: String, powerSourcesState: PowerSourcesState) {
        AppLogger.d(TAG, "powerSourcesStateReceived id=$identifier " +
                "batteryPresent=${powerSourcesState.batteryPresent} " +
                "wiredPower=${powerSourcesState.wiredExternalPowerConnected} " +
                "wirelessPower=${powerSourcesState.wirelessExternalPowerConnected}")
    }

    override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
        AppLogger.d(TAG, "DIS $uuid = $value")
    }

    // SDK 8.0 新增：以 key-value 形式返回设备信息（如固件版本、序列号等）
    override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
        AppLogger.d(TAG, "DIS ${disInfo.key} = ${disInfo.value}")
    }

    // SDK 8.0 新增：体温计数据回调（Loop 心率场景暂不使用，空实现即可）
    override fun htsNotificationReceived(identifier: String, data: PolarHealthThermometerData) {
        AppLogger.d(TAG, "HTS ${data.celsius}°C")
    }
}
