# P3 — 云端融合与同步

路径均相对于 `app/src/main/java/com/owner/mindbody/`。返回 [`FEATURE-LEDGER.md`](../../FEATURE-LEDGER.md)。

---

### F-P3-001 SyncManager ts 修复（ActivityDay/NightlyRecharge）
Plan: fix-ts-zero · 更新: 2026-06-24

- **目的** 修复 `ActivityDaySummaryEntity`、`NightlyRechargeEntity`、`SleepSessionEntity` 上传时 `ts=0L` 导致查询为空的 bug。
- **入口** `data/sync/SyncManager.kt` → `entityToMap()` 的 `map["ts"]`；B 组 `runB()` 缺 timestamp 行 `mapNotNull` 跳过。
- **文件** `data/sync/SyncManager.kt`（`parseDateToEpochMs(date: String): Long`）
- **约定** ActivityDay/NightlyRecharge 用 `parseDateToEpochMs(entity.date)`；Sleep 用 `sleepStartTimeMs ?: sleepEndTimeMs`，皆 null 则跳过。
- **验收** 云端 `ts` 非 0；看板运动/睡眠趋势有数据。

> 2026-06-24 Sleep ts 修复 (#sleep-dashboard-fix) · 2026-06-19 parseDateToEpochMs (#fix-ts-zero)

---

### F-P3-002 DeviceScreen 设备离线同步状态 UI
Plan: add-devicesync-ui · 更新: 2026-06-19

- **目的** 连接后展示 `DeviceSyncManager` IDLE/SYNCING/SUCCESS/FAILED 及失败原因。
- **入口** `DeviceScreen.kt` 手环 `PremiumCard` 内，`CONNECTED` 时显示
- **文件** `DeviceViewModel.kt`（`deviceSyncStatus`/`deviceSyncError` StateFlow）；`DeviceScreen.kt`（`DeviceSyncStatusRow`）
- **约定** `DeviceSyncStatusRow(syncStatus, errorMsg, modifier)` 独立 Composable。
- **验收** 「等待同步」→「正在拉取…」→「已同步」；失败红色显示错误。

> 2026-06-19 DeviceSyncStatusRow (#add-devicesync-ui)

---

### F-P3-003 睡眠数据拉取与云端上传修复
Plan: sleep-upload-fix · 更新: 2026-06-25

- **目的** 修复夜间睡眠无法上云：占位行缺 timestamp、游标过早推进、REPLACE 覆盖、无效行反复 WARN。
- **入口** `DeviceSyncManager.syncSleepData()` → `mapSleep()` → `SleepRepository.upsertAllMerge()` → `SyncManager.runB("sleep_sessions")`
- **文件** `PolarDeviceDataMappers.kt`、`DeviceSyncManager.kt`、`SleepRepository.kt`、`SleepSessionDao.kt`、`SyncManager.kt`、`SyncPreferences.kt`（`normalizeBaseUrl`）、`DeviceScreen.kt`
- **约定** Server URL 须带协议；晨间 BLE 重连后重拉最近 3 天睡眠；缺 timestamp 占位行删除。
- **验收** storage 看板 sleep 有 `sleepStartTimeMs`/`sleepEndTimeMs`；`Upload sleep_sessions: inserted>0`；无重复 Skip WARN。

> 2026-06-25 睡眠 3 天滚动重拉 + 合并 upsert (#sleep-upload-fix)

---

### F-P3-004 实时 PPI 推流管道
Plan: p1a~p1e（实时生理状态检测系统） · 更新: 2026-06-23

> 原误标 `F-P1-002`，已重编号为 F-P3-004（云端 PPI 窗口上传）。

- **目的** 设备端 HRV 轻量计算 + PPI 环形缓冲 + 90s 主路径推流 `POST /api/vitals/stream/ppi-window`；15min WorkManager 兜底。
- **入口** `HrStreamService` 90s 协程循环 → `PpiStreamWorker.tryStreamOnce(sinceMs=lastWindowEndMs)`；`MindBodyApplication.onCreate()` 注册 15min 周期 Work
- **文件** `data/HrvOnDevice.kt`、`data/stream/PpiLiveBuffer.kt`、`data/sync/SyncApiClient.kt`（`postPpiWindow`）、`worker/PpiStreamWorker.kt`、`polar/HrStreamService.kt`、`polar/PolarBleManager.kt`（`processPpiData`→`ppiLiveBuffer.push`）、`MindBodyApplication.kt`
- **约定** 质量过滤 `!blocker && skinContactOk && errorEstimateMs<=50 && ppiMs in 300..2000`；门控 `n_clean>=25` 且 coverage≥45%；`lastWindowEndMs` 仅在非 `SKIPPED_EARLY_GATE` 时推进；`drainWindowAtomic` 原子读。
- **验收** BLE 连接+同步开启后 ~90s 上传；开发者 PPI 日志无时间空洞；服务端 ACCEPTED。

> 2026-06-23 窗口游标+原子 drain (#ppi-upload-log-gap-fix) · 2026-06-23 90s 主路径+15min 兜底 (#ppi推流90秒循环)
