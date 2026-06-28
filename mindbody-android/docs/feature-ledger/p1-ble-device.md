# P1 — Polar BLE 与设备

路径均相对于 `app/src/main/java/com/owner/mindbody/`。返回 [`FEATURE-LEDGER.md`](../../FEATURE-LEDGER.md)。

---

### F-P1-001 Polar BLE 扫描/连接/断开
Plan: phase1-android-polar · 更新: 2026-06-23

- **目的** Polar Loop 扫描、连接、断开与状态流；冷启动自动连已保存设备。
- **入口** `MindBodyApplication.polarBleManager`；`tryAutoConnectSavedDevice()`
- **文件** `polar/PolarBleManager.kt`、`ui/device/AutoConnectEffect.kt`、`DeviceScreen.kt`、`DeviceViewModel.kt`
- **约定** SDK 8.0.0；扫描 15s 超时后直连；直连前 `disconnectFromDevice`+1s 清 GATT；25s 看门狗；蓝牙开→关→开时 `force=true` 重试；意外断联后 `scheduleReconnect` 先 `KeepAliveCoordinator.start(RECONNECTING)` 再 `connectToDevice`，避免无 WakeLock 时 GATT 握手被系统挂起。
- **验收** 冷启动可自动连；进程异常退出后重进可恢复；常连接断线约 3s 重连且 FGS 在重连前恢复；短连接用户断开后不重连。

> 2026-06-28 断联重连前重启 FGS + 心跳看门狗 (#ble-reconnect-keepalive) · 2026-06-23 残留 GATT 清理 + 25s 看门狗 (#ble-auto-connect-watchdog) · 2026-06-13 Phase 1 初始 (#phase1-android-polar)

---

### F-P1-002 FTU 首次使用配置
Plan: phase1-android-polar · 更新: 2026-06-13

- **目的** Loop 首次使用向导（性别、身高、体重、静息心率等）。
- **文件** `ui/ftu/FtuScreen.kt`、`ui/ftu/FtuViewModel.kt`、`PolarBleManager` FTU API
- **约定** FTU 完成状态持久化于 `DevicePreferences`。
- **验收** 未完成 FTU 时引导至 FTU 页。

> 2026-06-13 FTU 向导 (#phase1-android-polar)

---

### F-P1-003 实时心率流 + 前台服务
Plan: phase1-android-polar · 更新: 2026-06-28

- **目的** `startHrStreaming` 持续采集并落库，后台保活。
- **文件** `keepalive/KeepAliveCoordinator.kt`、`KeepAliveConfig.kt`、`RestartProtection.kt`、`keepalive/KeepAliveReason.kt`、`polar/PolarBleManager.kt`、`polar/HrStreamService.kt`、`util/PowerKeepAlive.kt`、`ui/device/DeviceScreen.kt`、`ui/heartrate/HeartRateScreen.kt`
- **约定** HR 经 `storage.hr.saveSample`→buffer；`KeepAliveCoordinator` 为 FGS 唯一启停入口；60s heartbeat 日志；`RestartProtection` 60s 内超 10 次启动冷却 5min；断联重连前以 `RECONNECTING` 重启 FGS；FGS 心跳连续 2 次 `ble=DISCONNECTED` 时 `reconnectNowIfIdle()` 补偿重连。
- **验收** 息屏后 FGS 常驻且 90s PPI 推流不中断；设备页卡片 FGS/BLE/心跳状态正确；意外断联后无需用户进心率页即可在约 3s 内恢复连接。

> 2026-06-28 断联重连 FGS + 心跳看门狗 (#ble-reconnect-keepalive) · 2026-06-26 KeepAliveCoordinator 统一启停 (#background-keepalive-coordinator) · 2026-06-13 HR 流 + FGS (#phase1-android-polar)

---

### F-P1-004 BLE 连接模式切换
Plan: phase1-android-polar · 更新: 2026-06-13

- **目的** 常连接（PERSISTENT）vs 短连接（ON_DEMAND）策略切换。
- **文件** `polar/PolarBleManager.kt`（`ConnectionMode`）、`ui/components/BleModeRadioGroup.kt`、`data/DevicePreferences.kt`
- **约定** 模式持久化 DataStore；设备页可切换。
- **验收** 两种模式行为符合 PRODUCT.md。

> 2026-06-13 连接模式切换 (#phase1-android-polar)

---

### F-P1-005 connectForSnapshot 短连接快照
Plan: phase1-android-polar · 更新: 2026-06-17

- **目的** 按需连接采集约 5 秒 HR 快照。
- **入口** `PolarBleManager.connectForSnapshot(deviceId): Int?`
- **文件** `polar/PolarBleManager.kt`（`SNAPSHOT_SAMPLE_DURATION_MS = 5000`）
- **约定** 短连接模式；返回 BPM 或 null；超时 30s；采集后主动断开。异常分类见 F-BUG-005。
- **验收** 超时与 Cancellation 正确区分。

> 2026-06-13 快照接口 (#phase1-android-polar)

---

### F-P1-006 心率页 UI 与统计曲线
Plan: phase1-android-polar · 更新: 2026-06-13

- **目的** 当前 BPM、今日统计、趋势图。
- **文件** `ui/heartrate/HeartRateScreen.kt`、`HeartRateViewModel.kt`、`ui/components/MindBodySplineChart.kt`
- **约定** 数据来自 `storage.hr`；图表可降采样，存储层全量；Hero 副文案为全页唯一 BLE 状态提示（四档）；已连接时 Hero 下显示手环电量。
- **验收** 展示样本数/平均/最低/最高；折线图最近样本；无页眉/Loop BLE Stream 角标。

> 2026-06-26 心率页头部精简 + 电量行 (#F-P1-006) · 2026-06-13 心率页 UI (#phase1-android-polar)

---

### F-P1-007 设置页（原设备页）
Plan: phase1-android-polar · 更新: 2026-06-26

- **目的** 扫描/连接/断开、FTU 入口、采集策略、心情提醒、后台保活；按系统设置标准分组展示。
- **入口** 底部导航「设置」Tab → `DeviceScreen`
- **文件** `ui/device/DeviceScreen.kt`、`ui/device/DeviceViewModel.kt`
- **约定** Section 分组 + 次级页（采集策略 / 心情提醒 / 后台保活）；主屏「偏好」区仅单行入口 + 摘要副标题。
- **验收** 底部 Tab 显示「设置」；点进次级页可完整配置；扫描结果仅在有设备时出现。

> 2026-06-26 重构为系统设置风格 + Tab 改名「设置」 (#settings-screen-redesign) · 2026-06-13 设备页 (#phase1-android-polar)

---

### F-P1-008 DevicePreferences DataStore
Plan: phase1-android-polar · 更新: 2026-06-25

- **目的** 已配对设备 ID、FTU 状态、BLE 模式、夜间断联/晨间重连时间等非 Room 偏好。
- **文件** `data/DevicePreferences.kt`
- **约定** 不经 AppStorage；`ble_bedtime_hour`/`ble_wake_hour` 默认 23/7。
- **验收** 修改断联/重连时间后 DataStore 持久化；Worker 读取最新小时。

> 2026-06-25 ble_bedtime_hour/ble_wake_hour (#ble-nightly-scheduler) · 2026-06-13 扩展 (#stream-appstorage)

---

### F-P1-009 在线传感器流全量落库
Plan: stream-entities · 更新: 2026-06-26

- **目的** 皮肤温度、ACC（分钟聚合）、PPI 在线流持久化到 Room。
- **入口** `PolarBleManager` → `processSkinTempData`/`processAccData`/`processPpiData`
- **文件** `SkinTempSampleEntity.kt`、`AccMinuteSummaryEntity.kt`、`PpiSampleEntity.kt`；`SkinTempRepository.kt`、`AccRepository.kt`、`PpiRepository.kt`、`EntitySampleBuffer.kt`
- **约定** ACC 原始 ~200Hz 仅内存聚合，每分钟一条 `acc_minute_summary`；断连时 `storage.flushAll()`。
- **验收** 常连接下三路流写入对应表；`COUNT(*)` 随采集增长。

> 2026-06-26 SDK Channel 关闭降为 WARN (#ble-stream-ce-fix) · 2026-06-13 三表 + Repository (#stream-entities)

---

### F-P1-010 设备离线数据同步落库
Plan: sync-device-manager · 更新: 2026-06-26

- **目的** 连接后从 Loop 拉取睡眠/活动/训练/24-7 样本等设备内汇总数据并落库。
- **入口** `PolarBleManager.bleSdkFeatureReady` → `DeviceSyncManager.onFeatureReady`
- **文件** `data/sync/DeviceSyncManager.kt`、`PolarDeviceDataMappers.kt`；8 张同步表 Entity/Dao；`DeviceSyncPreferences.kt`
- **约定** 启用 `FEATURE_POLAR_ACTIVITY_DATA`/`SLEEP_DATA`/`TRAINING_DATA`；增量同步日期存 DataStore；读写经 `app.storage.*`。
- **验收** 连接真机后 activity_day_summary、hr_247_samples、sleep_sessions 等有新行；默认回溯 7 天不重复拉。

> 2026-06-26 scheduleSync CE 重抛 (#ble-stream-ce-fix) · 2026-06-13 DeviceSyncManager + 8 表 v4 (#sync-device-manager)

---

### F-P1-011 开发者模式与运行日志
Plan: phase1-android-polar · 更新: 2026-06-25

- **目的** 隐藏解锁开发者模式；BLE/同步运行日志；Room 行数看板；PPI 窗口上传诊断（统计/条形图/时序色块）。
- **入口** 设置页连点版本 7 次 → 开发者卡片 → 运行日志/storage 看板/PPI 上传日志
- **文件** `util/AppLogger.kt`、`AppLogBuffer.kt`、`DeveloperPreferences.kt`、`StorageStatsRepository.kt`、`StorageStatsDao.kt`、`PpiWindowAttempt.kt`、`PpiUploadLogBuffer.kt`、`ui/developer/*`、`worker/PpiStreamWorker.kt`、`ui/device/DeviceScreen.kt`
- **约定** Logcat+环形缓冲 800 条；storage 看板刷新前 `flushAll()`；PPI 日志 200 条环形缓冲经 `tryStreamOnce` 写入。
- **验收** 未解锁无入口；四档级别筛选；13 表 COUNT；PPI 页 StatGrid+SKIP 分布+30 条色块；~90s 新增记录。

> 2026-06-25 日志级别筛选 (#developer-log-level-filter) · 2026-06-23 PPI 上传日志页 (#ppi-upload-log) · 2026-06-13 开发者模式 (#phase1-android-polar)

---

### F-P1-012 身心交织多指标曲线
Plan: mindbody-chart-overlay · 更新: 2026-06-15

- **目的** 心率页多指标时间轴：HR、皮温、HRV、运动上下文同图叠加。
- **入口** 底部导航「心率」→ `HeartRateScreen` → `MindBodySplineChart`
- **文件** `ui/components/MindBodySplineChart.kt`、`SplineChartUtils.kt`、`HeartRateViewModel.kt`、`data/HrvUtils.kt`
- **约定** 经 `storage` Repository 读取；1h/6h/24h 预设+拖动+scrubber；RMSSD 在 `Dispatchers.Default` 计算。
- **验收** HR/体温/HRV 三色曲线+运动色带；chips 切换；无数据优雅缺省；不 ANR。

> 2026-06-15 多指标曲线 + RMSSD 后台线程 (#mindbody-chart-overlay)

---

### F-P1-014 ACC 10 秒桶聚合（acc_minute_summary）
Plan: acc-10s-aggregator · 更新: 2026-06-19

- **目的** BLE 高频 ACC 按 10 秒桶聚合为 `avg_magnitude_mg`/`max_magnitude_mg`/`sample_count`；不保留 `acc_samples` 表。
- **入口** `PolarBleManager.processAccData()` → `AccRepository.ingestSample()`
- **文件** `data/AccRepository.kt`（`BUCKET_MS=10_000`）、`AppDatabase.kt`（`MIGRATION_7_8` v8）、`SyncManager.kt`
- **约定** 桶边界或 `flush()` 时 upsert 一行。
- **验收** Storage 看板约 8640 行/天；云端 `/series` 用 `avg_magnitude_mg`。

> 2026-06-19 10 秒桶聚合 (#acc-10s-aggregator)

---

### F-P1-015 BLE 夜间自动断联 / 晨间重连
Plan: ble-nightly-scheduler · 更新: 2026-06-25

- **目的** 每晚断 BLE 让 Loop 离线记录；次日重连并触发 `DeviceSyncManager` 拉 Sleep/Nightly Recharge。
- **入口** `MindBodyApplication.onCreate()` → `BleSchedulerWorker.scheduleNext`；设置页「夜间自动断联」
- **文件** `worker/BleSchedulerWorker.kt`、`DevicePreferences.kt`、`DeviceScreen.kt`（`BleScheduleCard`）、`DeviceViewModel.kt`、`MindBodyApplication.kt`、`PolarBleManager.kt`
- **约定** 默认 23:00/07:00；修改断联时间立即 REPLACE 重排 WorkManager；`ExistingWorkPolicy.KEEP` 避免重复排队。
- **验收** 整点前后自动断/连；时间 chip 可改且重启生效。

> 2026-06-25 断联/重连时间可配置 (#ble-nightly-scheduler) · 2026-06-20 BleSchedulerWorker (#ble-nightly-scheduler)

---

### F-P1-016 CDM 伴随设备关联保活
Plan: cdm-companion-keepalive · 更新: 2026-06-26

- **目的** `CompanionDeviceManager` 注册 Polar 为伴随设备，提升 OOM 优先级与 Android 12+ 后台 FGS 特权；不替代 `HrStreamService`+`PowerKeepAlive`。
- **入口** 设置 → 后台保活 → 手动「关联伴随设备」（连接成功不再自动弹出 CDM）
- **文件** `util/CompanionDeviceHelper.kt`、`DevicePreferences.kt`（`companionAssociationId`/`companionDeviceMac`）、`DeviceViewModel.kt`、`DeviceScreen.kt`、`HrStreamService.kt`、`AndroidManifest.xml`
- **约定** Polar SDK 仍负责 BLE；CDM 仅 OS 层；关联请求仅按 `Polar.*` 名称过滤，不按 MAC（已连接设备不广播会导致 CDM 无限扫描）；`setSingleDevice(false)` 展示设备列表；关联前若 GATT 已连接则先断开、完成/取消后自动重连。
- **验收** Android 8+ 可选伴随设备；设置页显示「已关联」；日志 `CDM verified` 或警告。

> 2026-06-26 CDM 关联状态改读 getAssociations（Polar deviceId 非 MAC）(#cdm-companion-keepalive) · 2026-06-26 CDM 关联移除 MAC 扫描过滤 (#cdm-companion-keepalive) · 2026-06-26 CDM 伴随设备关联 (#cdm-companion-keepalive)
