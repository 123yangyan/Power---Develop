# MindBody Android — 功能实现清单

> **范围**：仅 [`mindbody-android/`](.) 工程内**已落地**功能。待做特性见 [`.cursor/plans/`](../.cursor/plans/)，**不在此预填**。
>
> Agent **执行 mindbody-android 相关任务前必读**；**功能实现完成后追加或更新本清单**。
>
> 规则：[`feature-ledger.mdc`](../.cursor/rules/feature-ledger.mdc) · 产品说明：[`PRODUCT.md`](PRODUCT.md)

**最后更新**：2026-06-23

---

## 索引（已实现 38 条 + 稳定性修复 9 条）

| ID | 名称 | Plan todo |
|----|------|-----------|
| F-P0-001 | HR 全量永久保存 | completeness |
| F-P0-002 | SyncMeta 实体约定 | sync-meta |
| F-P0-003 | Room 迁移框架 + WAL | migration-framework |
| F-P0-004 | HrSampleBuffer 批量缓冲 | batch-buffer |
| F-P0-005 | AppStorage 统一门面 | storage-facade |
| F-P0-006 | SyncManager 同步占位 | sync-reserved |
| F-P1-001 | Polar BLE 扫描/连接/断开 | phase1-android-polar |
| F-P1-002 | FTU 首次使用配置 | phase1-android-polar |
| F-P1-003 | 实时心率流 + 前台服务 | phase1-android-polar |
| F-P1-004 | BLE 连接模式切换 | phase1-android-polar |
| F-P1-005 | connectForSnapshot 短连接快照 | phase1-android-polar |
| F-P1-006 | 心率页 UI 与统计曲线 | phase1-android-polar |
| F-P1-007 | 设备页与配对引导 | phase1-android-polar |
| F-P1-008 | DevicePreferences DataStore | phase1-android-polar |
| F-P1-009 | 在线传感器流全量落库 | stream-entities |
| F-P1-010 | 设备离线数据同步落库 | sync-device-manager |
| F-P1-011 | 开发者模式与运行日志 | phase1-android-polar |
| F-P1-012 | 身心交织多指标曲线 | mindbody-chart-overlay |
| F-P1-014 | ACC 10 秒桶聚合（acc_minute_summary） | acc-10s-aggregator |
| F-P1-015 | BLE 夜间自动断联 / 晨间重连 | ble-nightly-scheduler |
| F-P2-001 | mood_entries 实体与 Repository | mood-entity |
| F-P2-002 | ValueEnergyGrid 四象限点选 | value-energy-grid |
| F-P2-003 | DiaryInput 日记输入 | diary-input |
| F-P2-004 | 记录页 MoodRecordScreen | mood-record-screen |
| F-P2-005 | 保存时 HR 快照关联 | hr-snapshot |
| F-P2-006 | WorkManager 定时提醒 | reminder |
| F-P2-007 | 历史列表 MoodHistoryScreen | history-screen |
| F-P2-008 | 底部导航记录/历史页签 | navigation |
| F-P2-009 | emotion v3.7 日记续号与 RecordViewport | wave-a-diary-continue |
| F-P2-010 | 同日序号 dailyEntryIndex | wave-a-daily-index |
| F-P2-011 | 强弹窗 CheckIn + snooze/逃避记录 | wave-b-checkin-dialog |
| F-P2-012 | 历史 CoordMiniBadge 与分页增强 | wave-c-history-polish |
| F-P2-013 | 情绪角色化 UI v4（探查抽屉 + 沉浸记录） | emotion-ui-v4 |
| F-P3-001 | SyncManager ts 修复（ActivityDay/NightlyRecharge） | fix-ts-zero |
| F-P3-002 | DeviceScreen 设备离线同步状态 UI | add-devicesync-ui |

路径均相对于 `app/src/main/java/com/owner/mindbody/`。

---

## P0 — 统一存储核心

### F-P0-001 HR 全量永久保存

| 字段 | 值 |
|------|-----|
| Plan | 统一存储核心模块 / todo: completeness |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：心率样本永久保留，禁止自动删除。
- **入口**：`HrRepository.saveSample()` → `HrSampleBuffer`
- **关键文件**：`data/HrRepository.kt`
- **调用约定**：`deleteOlderThan` 保留于 DAO 但**不得**在 save 流程中调用。
- **验收要点**：连续采集 24h+ 后 `COUNT(*)` 持续增长；无 cutoff 逻辑。

#### 变更记录

- 2026-06-13：去掉 24h 自动删除 (#completeness)

---

### F-P0-002 SyncMeta 实体约定

| 字段 | 值 |
|------|-----|
| Plan | 统一存储核心模块 / todo: sync-meta |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：Room 实体共用同步元数据约定。
- **关键文件**：`data/local/SyncMeta.kt`、`data/local/HrSampleEntity.kt`（`@Embedded val sync: SyncMeta`）
- **调用约定**：新实体必须 `@Embedded sync: SyncMeta`；`SyncState` 为 PENDING/SYNCED/FAILED。
- **验收要点**：HrSampleEntity 无独立 `synced: Boolean` 字段。

#### 变更记录

- 2026-06-13：SyncMeta + HrSampleEntity 改造 (#sync-meta)

---

### F-P0-003 Room 迁移框架 + WAL

| 字段 | 值 |
|------|-----|
| Plan | 统一存储核心模块 / todo: migration-framework |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：schema 可演进且不丢数据。
- **关键文件**：`data/local/AppDatabase.kt`（v4、`MIGRATION_1_2`/`2_3`/`3_4`）、`app/build.gradle.kts`（KSP schema）
- **调用约定**：**禁止** `fallbackToDestructiveMigration`；版本 +1 必须加 Migration。
- **验收要点**：WAL 已开启；exportSchema=true。

#### 变更记录

- 2026-06-13：v4 + MIGRATION_2_3/3_4（手环全量数据落库）

---

### F-P0-004 HrSampleBuffer 批量缓冲

| 字段 | 值 |
|------|-----|
| Plan | 统一存储核心模块 / todo: batch-buffer |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：高频 HR 写入合并为批量事务，避免 UI 卡顿。
- **关键文件**：`data/HrSampleBuffer.kt`、`data/HrRepository.kt`（投递 buffer + `flush()`）
- **调用约定**：save 走 buffer；App 退出/断连时 `storage.flushAll()`。
- **验收要点**：`HrSampleDao.insertAll` 批量写入；DAO 有分页查询接口。

#### 变更记录

- 2026-06-13：批量缓冲 + flush (#batch-buffer)

---

### F-P0-005 AppStorage 统一门面

| 字段 | 值 |
|------|-----|
| Plan | 统一存储核心模块 / todo: storage-facade |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：功能层单一存储入口。
- **入口**：`MindBodyApplication.storage` → `AppStorage`
- **关键文件**：`data/storage/AppStorage.kt`、`MindBodyApplication.kt`
- **调用约定**：UI/ViewModel 用 `app.storage.hr`，禁止直接 `AppDatabase` / `HrRepository()`。
- **验收要点**：`HeartRateViewModel`、`DeviceViewModel` 经 storage 访问；扩展后含 `skinTemp`/`acc`/`ppi`/`activityDay`/`sleep`/`training` 等。

#### 变更记录

- 2026-06-13：AppStorage 门面 (#storage-facade)

---

### F-P0-006 SyncManager 同步占位

| 字段 | 值 |
|------|-----|
| Plan | 统一存储核心模块 / todo: sync-reserved |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：预留 DAO 同步契约与空实现（后续云端同步再实现）。
- **关键文件**：`data/sync/SyncableDao.kt`、`data/sync/SyncManager.kt`、`data/local/HrSampleDao.kt`
- **调用约定**：DAO 实现 `getUnsynced` / `markSynced` / `markFailed`；SyncManager 当前占位。
- **验收要点**：`getUnsynced()` 可返回 PENDING 记录。

#### 变更记录

- 2026-06-13：SyncableDao + SyncManager 占位 (#sync-reserved)

---

## P1 — Polar BLE 与心率

### F-P1-001 Polar BLE 扫描/连接/断开

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-23 |

#### 已实现方案

- **目的**：Polar Loop 扫描、连接、断开与状态流；冷启动自动连已保存设备。
- **入口**：`MindBodyApplication.polarBleManager`；启动自动连 `tryAutoConnectSavedDevice()`
- **关键文件**：`polar/PolarBleManager.kt`、`ui/device/AutoConnectEffect.kt`、`ui/device/DeviceScreen.kt`、`ui/device/DeviceViewModel.kt`
- **调用约定**：SDK 8.0.0；`ConnectionState` StateFlow 驱动 UI；`AppNavigation` 挂载 `AutoConnectEffect` 统一请求 BLE 权限并触发自动连；`blePowerStateChanged` 与 `AutoConnectEffect` 重复触发时合并（不 cancel 进行中的 job）；扫描 15s 超时后直连兜底；直连前 `disconnectFromDevice` + 1s 清理残留 GATT；直连后 25s 看门狗（SDK 静默时重置并 `scheduleReconnect`）；蓝牙从关到开时 `force=true` 重试；协程被取消时不消耗「本进程已尝试」标记。
- **验收要点**：真机冷启动可自动连已保存设备；进程异常退出后重进无需划掉后台即可恢复连接；无 `StandaloneCoroutine was cancelled` 导致的假失败；常连接模式断线约 3s 自动重连；短连接模式启动也自动连但用户断开后不重连。

#### 变更记录

- 2026-06-23：进程异常退出后自动连卡死修复 — 残留 GATT 清理 + 25s 看门狗 (#ble-auto-connect-watchdog)
- 2026-06-13：修复启动双入口 cancel 导致自动连失败 (#phase1-android-polar)
- 2026-06-13：启动自动扫描连接已保存设备 (#phase1-android-polar)
- 2026-06-13：Phase 1 初始实现 (#phase1-android-polar)

---

### F-P1-002 FTU 首次使用配置

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：Loop 首次使用向导（性别、身高、体重、静息心率等）。
- **关键文件**：`ui/ftu/FtuScreen.kt`、`ui/ftu/FtuViewModel.kt`、`PolarBleManager` FTU API
- **调用约定**：FTU 完成状态持久化于 `DevicePreferences`。
- **验收要点**：未完成 FTU 时引导至 FTU 页。

#### 变更记录

- 2026-06-13：FTU 向导 (#phase1-android-polar)

---

### F-P1-003 实时心率流 + 前台服务

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-24 |

#### 已实现方案

- **目的**：`startHrStreaming` 持续采集并落库，后台保活。
- **关键文件**：`polar/PolarBleManager.kt`、`polar/HrStreamService.kt`、`util/PowerKeepAlive.kt`、`ui/device/DeviceScreen.kt`、`ui/heartrate/HeartRateScreen.kt`
- **调用约定**：HR 样本经 `storage.hr.saveSample` → buffer；BLE 连接成功或心率页进入时启动前台服务；`HrStreamService` 持有 `PARTIAL_WAKE_LOCK` 抗 Doze；设备页「后台保活」卡片引导电池优化豁免与小米自启动设置。
- **验收要点**：实时 BPM 显示；样本写入 Room；息屏后前台服务通知常驻且 90s PPI 推流循环不中断；设备页可跳转电池优化与自启动设置。

#### 变更记录

- 2026-06-24：息屏保活加固 — `HrStreamService` 加 partial wake lock；`PowerKeepAlive` 电池豁免+小米自启动引导；设备页后台保活卡片 (#background-keepalive)
- 2026-06-13：HR 流 + 前台服务 (#phase1-android-polar)

---

### F-P1-004 BLE 连接模式切换

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：常连接（PERSISTENT）vs 短连接（ON_DEMAND）策略切换。
- **关键文件**：`polar/PolarBleManager.kt`（`ConnectionMode`）、`ui/components/BleModeRadioGroup.kt`、`data/DevicePreferences.kt`
- **调用约定**：模式持久化 DataStore；设备页可切换。
- **验收要点**：两种模式行为符合 PRODUCT.md 描述。

#### 变更记录

- 2026-06-13：连接模式切换 (#phase1-android-polar)

---

### F-P1-005 connectForSnapshot 短连接快照

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：按需连接采集约 5 秒 HR 快照。
- **入口**：`PolarBleManager.connectForSnapshot(deviceId): Int?`
- **关键文件**：`polar/PolarBleManager.kt`（`SNAPSHOT_SAMPLE_DURATION_MS = 5000`）
- **调用约定**：短连接模式下调用；返回 BPM 或 null；采集后主动断开。
- **验收要点**：超时 30s。

#### 变更记录

- 2026-06-13：快照接口 (#phase1-android-polar)

---

### F-P1-006 心率页 UI 与统计曲线

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：当前 BPM、今日统计、趋势图。
- **关键文件**：`ui/heartrate/HeartRateScreen.kt`、`ui/heartrate/HeartRateViewModel.kt`、`ui/components/MindBodySplineChart.kt`
- **调用约定**：数据来自 `storage.hr`；图表显示层可降采样，存储层全量。
- **验收要点**：展示样本数/平均/最低/最高；折线图最近样本。

#### 变更记录

- 2026-06-13：心率页 UI (#phase1-android-polar)

---

### F-P1-007 设备页与配对引导

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：扫描列表、连接、FTU 入口、配对提示。
- **关键文件**：`ui/device/DeviceScreen.kt`、`ui/device/DeviceViewModel.kt`
- **验收要点**：距离/单设备/恢复出厂等提示文案存在。

#### 变更记录

- 2026-06-13：设备页 (#phase1-android-polar)

---

### F-P1-008 DevicePreferences DataStore

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：已配对设备 ID、FTU 状态、BLE 模式等非 Room 偏好。
- **关键文件**：`data/DevicePreferences.kt`
- **调用约定**：不经 AppStorage；由 `MindBodyApplication` 直接暴露。

#### 变更记录

- 2026-06-13：扩展多 Repository + deviceSync (#stream-appstorage)

---

### F-P1-009 在线传感器流全量落库

| 字段 | 值 |
|------|-----|
| Plan | 手环全量数据落库 / todo: stream-entities |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：皮肤温度、ACC（分钟聚合）、PPI 在线流持久化到 Room。
- **入口**：`PolarBleManager` → `processSkinTempData` / `processAccData` / `processPpiData`
- **关键文件**：`data/local/SkinTempSampleEntity.kt`、`AccMinuteSummaryEntity.kt`、`PpiSampleEntity.kt`；`data/SkinTempRepository.kt`、`AccRepository.kt`、`PpiRepository.kt`、`EntitySampleBuffer.kt`
- **调用约定**：ACC 原始 ~200Hz 仅内存聚合，每分钟一条 `acc_minute_summary`；断连时 `storage.flushAll()` 落盘。
- **验收要点**：常连接模式下三路流写入对应表；`COUNT(*)` 随采集增长。

#### 变更记录

- 2026-06-13：在线流三表 + Repository (#stream-entities)

---

### F-P1-010 设备离线数据同步落库

| 字段 | 值 |
|------|-----|
| Plan | 手环全量数据落库 / todo: sync-device-manager |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：连接后从 Loop 拉取睡眠/活动/训练/24-7 样本等设备内汇总数据并落库。
- **入口**：`PolarBleManager.bleSdkFeatureReady` → `DeviceSyncManager.onFeatureReady`
- **关键文件**：`data/sync/DeviceSyncManager.kt`、`PolarDeviceDataMappers.kt`；8 张同步表 Entity/Dao；`DeviceSyncPreferences.kt`
- **调用约定**：启用 `FEATURE_POLAR_ACTIVITY_DATA` / `SLEEP_DATA` / `TRAINING_DATA`；增量同步日期存 DataStore；读写经 `app.storage.*`。
- **验收要点**：连接真机后 activity_day_summary、hr_247_samples、sleep_sessions 等有新行；重复连接不重复拉已同步日期（默认回溯 7 天）。

#### 变更记录

- 2026-06-13：DeviceSyncManager + 8 表 v4 (#sync-device-manager)

---

### F-P1-011 开发者模式与运行日志

| 字段 | 值 |
|------|-----|
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-23 |

#### 已实现方案

- **目的**：隐藏解锁开发者模式，集中展示 App 内 BLE/同步运行日志，支持复制；并提供 Room 各表行数看板验证落库；**PPI 窗口上传诊断**记录每次 ~90s 推流尝试及跳过/失败原因，页面含全量统计看板（已上传/未上传/上传率）、SKIP 原因分布条形图、最近 30 条窗口时序色块图。
- **入口**：设备页连点版本信息 7 次 → 开发者卡片 →「运行日志」/「storage 看板」/「PPI 上传日志」
- **关键文件**：`util/AppLogger.kt`、`util/AppLogBuffer.kt`、`data/DeveloperPreferences.kt`、`data/StorageStatsRepository.kt`、`data/local/StorageStatsDao.kt`、`data/stream/PpiWindowAttempt.kt`、`data/stream/PpiUploadLogBuffer.kt`、`ui/developer/DeveloperLogScreen.kt`、`ui/developer/DeveloperStorageScreen.kt`、`ui/developer/PpiUploadLogScreen.kt`、`ui/developer/PpiUploadLogViewModel.kt`、`worker/PpiStreamWorker.kt`、`ui/device/DeviceScreen.kt`
- **调用约定**：`AppLogger` 同时写 Logcat 与环形缓冲（800 条）；storage 看板经 `app.storage.storageStats.loadStats()` 统计，刷新前先 `flushAll()`；PPI 上传日志由 `PpiStreamWorker.tryStreamOnce(sinceMs=…)` 写入 `MindBodyApplication.ppiUploadLogBuffer`（200 条环形缓冲），记录 `nRaw/nClean/覆盖率/SkipReason/服务端响应`；早期门控跳过（同步关闭/BLE/预热/URL）也会 peek buffer 写入窗口样本数；读写仍经 `AppStorage` 门面。
- **验收要点**：未解锁无入口；日志页可复制全部/清空；storage 看板显示 13 张表行数与最近更新时间；PPI 上传日志页顶部展示 4 格统计（全部/已上传/未上传/上传率）、未上传 SKIP 原因分布（条数+占比条形图）、最近 30 条窗口时序色块（绿/黄/红）；下方保留 monospace 明细列表；未上传显示具体 SkipReason；连接手环并开启同步后每 ~90s 新增一条记录；预热期采集的数据在首次 drain 时一并纳入窗口（无时间空洞）。

#### 变更记录

- 2026-06-23：PPI 上传日志页重设计 — 全量统计 StatGrid、SKIP 原因分布条形图、最近 30 条时序色块图 (#ppi-upload-log-dashboard)
- 2026-06-23：PPI 上传日志窗口游标修复 — `HrStreamService` 维护 `lastWindowEndMs`（初始=服务启动时刻），`PpiLiveBuffer.drainWindowAtomic` 原子读，消除预热期与上传耗时造成的数据空洞 (#ppi-upload-log-gap-fix)
- 2026-06-23：PPI 上传日志诊断页（200 条环形缓冲 + SkipReason + JSON 复制）(#ppi-upload-log)
- 2026-06-14：开发者 storage 看板（13 表 COUNT + 刷新）(#developer-storage-dashboard)
- 2026-06-13：修复运行日志 LazyColumn 重复 key 闪退 (#phase1-android-polar)
- 2026-06-13：开发者模式 + 运行日志页 (#phase1-android-polar)

---

### F-P1-012 身心交织多指标曲线

| 字段 | 值 |
|------|-----|
| Plan | 身心交织曲线多指标整合 / todo: mindbody-chart-overlay |
| 最后更新 | 2026-06-15 |

#### 已实现方案

- **目的**：心率页从单一 HR 曲线升级为可交互的多指标时间轴，用同图叠加方式观察心率、皮肤体温、HRV 与运动上下文。
- **入口**：底部导航「心率」→ `HeartRateScreen` → `MindBodySplineChart`
- **关键文件**：`ui/components/MindBodySplineChart.kt`、`ui/components/SplineChartUtils.kt`、`ui/heartrate/HeartRateViewModel.kt`、`data/HrvUtils.kt`；范围读取经 `data/*Repository.kt` 与对应 DAO 暴露。
- **调用约定**：功能层只通过 `MindBodyApplication.storage` 的 Repository 读取；图表默认今日范围，支持 1h/6h/24h 预设、左右拖动平移、触摸竖线 scrubber；HRV 由 PPI/RR 间期客户端在 `Dispatchers.Default` 计算 RMSSD，仅作觉察指标。
- **验收要点**：心率页可见 HR/体温/HRV 三色曲线与运动色带；chips 可切换显隐；触摸/拖动显示时间点与数值；无某类传感器数据时优雅缺省；大量 PPI 数据下滑动图表不阻塞主线程。

#### 变更记录

- 2026-06-15：多指标叠加曲线 + RMSSD + 今日历史滑动 + scrubber (#mindbody-chart-overlay)
- 2026-06-15：RMSSD 计算移至后台线程并改为线性滑窗，修复心率页 ANR (#fix-hr-anr)

---

## P2 — 心情记录

### F-P2-001 mood_entries 实体与 Repository

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: mood-entity |
| 最后更新 | 2026-06-14 |

#### 已实现方案

- **目的**：本地持久化心情记录，为 Phase 3 同步就绪。
- **入口**：`AppStorage.mood` → `MoodRepository`
- **关键文件**：`data/local/MoodEntryEntity.kt`、`MoodEntryDao.kt`；`data/MoodRepository.kt`；`AppDatabase` v6 + `MIGRATION_4_5` + `MIGRATION_5_6`（roleId）
- **调用约定**：实体含 `@Embedded sync: SyncMeta`；DAO 实现 `SyncableDao`；功能层经 `app.storage.mood` 读写。
- **验收要点**：`getUnsynced()` 可返回 PENDING 记录；迁移不丢 v4 数据。

#### 变更记录

- 2026-06-13：mood_entries 表 + Repository (#mood-entity)
- 2026-06-14：增加 roleId 字段 v6 迁移 (#emotion-ui-v4)

---

### F-P2-002 ValueEnergyGrid 四象限点选

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: value-energy-grid |
| 最后更新 | 2026-06-14 |

#### 已实现方案

- **目的**：移植 emotion 价值感×耗能坐标点选（**记录主路径已退役**）。
- **关键文件**：`ui/mood/ValueEnergyGrid.kt`（`@Deprecated`）、`ui/mood/MoodQuadrant.kt`
- **调用约定**：新记录使用 `ActorStage` / `EmotionRole`；历史无 `roleId` 条目仍通过 `CoordMiniBadge` 只读展示 coord。
- **验收要点**：记录页不再出现四象限网格。

#### 变更记录

- 2026-06-13：Compose 网格组件 (#value-energy-grid)
- 2026-06-14：记录主路径退役，角色化 UI 替代 (#binary-visual-refactor)

---

### F-P2-003 DiaryInput 日记输入

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: diary-input |
| 最后更新 | 2026-06-15 |

#### 已实现方案

- **目的**：多行日记输入 + v3.7 Enter 列表续号。
- **关键文件**：`ui/mood/DiaryInput.kt`、`DiaryListContinue.kt`
- **验收要点**：有序/无序列表 Enter 续号；MODAL 等场景 scrollable + heightIn；场景 B 用 fillHeight 占满父 Box 内部滚动；中文输入法连续输入不重复提交文字。

#### 变更记录

- 2026-06-13：DiaryInput 组件 (#diary-input)
- 2026-06-13：v3.7 列表续号 (#wave-a-diary-continue)
- 2026-06-14：场景 B fillHeight 模式，避免 verticalScroll 与 weight 冲突 (#keyboard-crash-fix)
- 2026-06-15：稳定 TextFieldValue 本地状态，修复 IME 重复输入 (#fix-diary-input)

---

### F-P2-004 记录页 MoodRecordScreen

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: mood-record-screen |
| 最后更新 | 2026-06-14 |

#### 已实现方案

- **目的**：二分法记录页 —— 场景 A `ActorStage` 4 人极速指认 / 场景 B 键盘沉浸倾诉。
- **入口**：底部导航「记录」→ `MoodRecordScreen`；提醒设置 → 底部导航「设备」→ `DeviceMoodReminderSection`
- **关键文件**：`MoodRecordScreen.kt`、`MoodRecordViewport.kt`、`ActorStage.kt`、`EmotionCapsuleToolbar.kt`、`DiaryInput.kt`、`MoodRecordViewModel.kt`
- **调用约定**：场景 A 点 4 首发角色且无日记 → `quickCaptureRole` 一键保存；有日记或进入写作 → 场景 B 胶囊 4+➕ + 日记句尾微型标签；提醒设置在 `DeviceScreen`；进入场景 B 后 LaunchedEffect 延迟聚焦，禁止在 DiaryInput 挂载前 requestFocus。
- **验收要点**：记录页默认 2×2 舞台无设置区；点「想倾诉更多」不闪退、键盘弹起；场景 B Column 统一 imePadding；键盘可见时隐藏免责声明。

#### 变更记录

- 2026-06-13：记录页 + ViewModel (#mood-record-screen)
- 2026-06-14：二分法视觉重构 — ActorStage + 设置迁至设备页 (#binary-visual-refactor)
- 2026-06-14：键盘 focus 时序 + Scene B IME 布局修复 (#keyboard-crash-fix)

---

### F-P2-005 保存时 HR 快照关联

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: hr-snapshot |
| 最后更新 | 2026-06-15 |

#### 已实现方案

- **目的**：记录时刻关联本地 HR 估计值。
- **关键文件**：`MoodRecordViewModel.resolveHrSnapshot()`；复用 `HrRepository.getHrNearTimestamp`、`PolarBleManager.connectForSnapshot`
- **调用约定**：常连接先查 ±2min 均值；短连接无样本时 `connectForSnapshot` 取第一条有效 HR；无手环时 `hr_at_entry` 为 null。
- **验收要点**：历史页展示「估计关联」标注；非医疗诊断文案存在。

#### 变更记录

- 2026-06-13：HR 快照逻辑 (#hr-snapshot)
- 2026-06-15：缩小本地窗口并减少短连接快照滞后 (#fix-hr-snapshot)

---

### F-P2-006 WorkManager 定时提醒

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: reminder |
| 最后更新 | 2026-06-15 |

#### 已实现方案

- **目的**：替代 emotion daily-checkin-service，定时提醒记录。
- **关键文件**：`worker/MoodReminderWorker.kt`、`MoodReminderDeliver.kt`、`MoodReminderScheduler.kt`；`MoodCheckInActivity.kt`；`data/MoodPreferences.kt`
- **调用约定**：间隔 1–1440 分钟；静默可编辑；周期 Work 保留为兜底，`mood_reminder_exact` 一次性 Work 按上次提醒 + 有效间隔精确重排；**strongPopup 一律经 FullScreenIntent 通知投递**（禁止 Worker 内 `startActivity`，targetSdk 35 BAL 合规）；`EXTRA_SOFT_DISMISS` 经 PendingIntent 传递；未开通知则不 post；Esc/稍后写逃避记录 + 20min snooze。
- **验收要点**：后台/测试提醒无 `Background activity launch blocked` logcat；锁屏 FullScreenIntent 亮屏；设备页可提示开启全屏通知权限。

#### 变更记录

- 2026-06-13：WorkManager + MoodPreferences (#reminder)
- 2026-06-14：强弹窗与弱通知拆分，移除 fullScreenIntent 与旧通知文案 (#emotion-ui-v4)
- 2026-06-14：恢复 FullScreenIntent + 锁屏探查 + 通知栏「稍后」快捷操作 (#lockscreen-probe)
- 2026-06-14：探查 UX 修复 — 前台 soft dismiss 分流、设置区/写作区布局分离 (#probe-ux-fix)
- 2026-06-14：BAL 修复 — 移除 Worker 直接 launch，统一通知通道 + FSI 能力检测 (#bal-fix)
- 2026-06-15：新增一次性精确重排，减少 15min 轮询漂移 (#fix-reminder-timing)

---

### F-P2-007 历史列表 MoodHistoryScreen

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: history-screen |
| 最后更新 | 2026-06-15 |

#### 已实现方案

- **目的**：列表、预览、编辑、删除。
- **入口**：底部导航「历史」→ `MoodHistoryScreen`
- **关键文件**：`MoodHistoryScreen.kt`、`MoodHistoryRowBuilder.kt`、`CoordMiniBadge.kt`、`DailyEntryIndex.kt`
- **调用约定**：极性/逃避卡片样式；CoordMiniBadge/RoleMiniBadge；页码按钮 + 跳转；同日 `(i/total)`；日期头含年份；长日记默认 3 行并可展开。
- **验收要点**：保存后在历史可见；编辑/删除生效。

#### 变更记录

- 2026-06-13：历史页 (#history-screen)
- 2026-06-15：历史卡片信息层级优化，心率高亮、长文展开、日期加年份 (#optimize-history-ui)

---

### F-P2-008 底部导航记录/历史页签

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: navigation |
| 最后更新 | 2026-06-14 |

#### 已实现方案

- **目的**：主导航整合记录与历史。
- **关键文件**：`ui/navigation/AppNavigation.kt`；`ui/components/FloatingIslandNav.kt`（`mood_record` / `mood_history`）
- **调用约定**：通知 deep link `MainActivity.EXTRA_NAVIGATE_TO=mood_record`；记录页键盘可见时 NavHost 底栏 padding 降为 0，避免三重挤压。
- **验收要点**：五页签可切换；FTU 页仍隐藏底栏；记录页键盘弹起内容区不被底栏占位叠加。

#### 变更记录

- 2026-06-13：导航扩展 (#navigation)
- 2026-06-14：记录页 IME 可见时取消 NavHost 88dp 底 padding (#keyboard-crash-fix)

---

### F-P2-009 emotion v3.7 日记续号与 RecordViewport

| 字段 | 值 |
|------|-----|
| Plan | phase2 对齐 emotion v3.7 / todo: wave-a-diary-continue, wave-a-record-viewport |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：对齐 emotion v3.7 记录页布局与日记交互。
- **关键文件**：`DiaryListContinue.kt`、`MoodRecordViewport.kt`、`MoodSettingsSection.kt`
- **验收要点**：Enter 列表续号；RecordViewport 信息架构（日期/序号/坐标/日记/保存）。

#### 变更记录

- 2026-06-13：v3.7 记录 UX (#wave-a-diary-continue)

---

### F-P2-010 同日序号 dailyEntryIndex

| 字段 | 值 |
|------|-----|
| Plan | phase2 对齐 emotion v3.7 / todo: wave-a-daily-index |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：记录页「今日第 N 条」与历史 `(i/total)`。
- **关键文件**：`DailyEntryIndex.kt`；`MoodRecordViewModel`、`MoodHistoryViewModel`
- **验收要点**：按本地日历日分组序号正确。

#### 变更记录

- 2026-06-13：dailyEntryIndex (#wave-a-daily-index)

---

### F-P2-011 强弹窗 CheckIn + snooze/逃避记录

| 字段 | 值 |
|------|-----|
| Plan | phase2 对齐 emotion v3.7 / todo: wave-b-checkin-dialog, wave-b-snooze-avoidance |
| 最后更新 | 2026-06-14 |

#### 已实现方案

- **目的**：对齐 daily-checkin-service 双通道提醒；PRD v4 场景 A 半屏 Bottom Sheet 极速盲操。
- **入口**：`MoodReminderDeliver`（FullScreenIntent 通知）→ `MoodCheckInActivity`（强弹窗）；弱通知 → `MainActivity` 记录页
- **关键文件**：`MoodCheckInActivity.kt`（透明主题 + 锁屏穿透）；`MobileCheckInDrawer.kt`、`PriorityDock.kt`；`MoodCheckInConstants.kt`；`MoodRecordViewModel.quickCaptureRole()`；`MoodReminderDeliver.kt`；`MoodReminderSnoozeReceiver.kt`
- **调用约定**：strongPopup → **仅** FullScreenIntent 通知拉起 Activity（Worker 禁止 direct launch）；soft dismiss 经 `EXTRA_SOFT_DISMISS`；API 34+ `canUseFullScreenIntent` 未授权时仍发 Heads-up，设备页提示开权限。
- **验收要点**：无 BAL 拦截 logcat；锁屏自动弹 Sheet；点击 Heads-up 进入探查。

#### 变更记录

- 2026-06-13：CheckIn + snooze (#wave-b-checkin-dialog)
- 2026-06-14：强弹窗不再并发旧版系统通知 (#emotion-ui-v4)
- 2026-06-14：锁屏极速探查 — FullScreenIntent + setShowWhenLocked + Keyguard 解锁引导 (#lockscreen-probe)
- 2026-06-14：探查 UX — soft dismiss、Sheet 全展开、记一笔回栈 (#probe-ux-fix)
- 2026-06-14：BAL 合规 — soft dismiss 改 Intent extra，移除后台 startActivity (#bal-fix)

---

### F-P2-012 历史 CoordMiniBadge 与分页增强

| 字段 | 值 |
|------|-----|
| Plan | phase2 对齐 emotion v3.7 / todo: wave-c-history-polish |
| 最后更新 | 2026-06-15 |

#### 已实现方案

- **目的**：历史列表视觉与分页对齐 emotion v3.7；支持角色图标展示。
- **关键文件**：`RoleMiniBadge.kt`、`CoordMiniBadge.kt`、`MoodHistoryRowBuilder.kt`；`MoodHistoryScreen` pager
- **验收要点**：有 roleId 显示角色微缩图；无 roleId 回退象限 badge；极性着色、逃避 badge、页码跳转；历史卡片中角色图标更突出。

#### 变更记录

- 2026-06-13：历史 polish (#wave-c-history-polish)
- 2026-06-15：RoleMiniBadge 支持可配置图标尺寸 (#optimize-history-ui)

---

### F-P2-013 情绪角色化 UI v4（探查抽屉 + 沉浸记录）

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / PRD v4.0 情绪日记 UI 优化 |
| 最后更新 | 2026-06-14 |

#### 已实现方案

- **目的**：18 角色二分法 UI —— 主动记录页场景 A/B + 探查窗 9 皮克斯。
- **入口**：定时提醒 → `MoodCheckInActivity` + `MobileCheckInDrawer`（9 人 3×3 不变）；主动记录 → `MoodRecordScreen`（4 人 ActorStage）
- **关键文件**：`ActorStage.kt`、`EmotionRole.kt`（`recordPageStageLineup`）、`EmotionCapsuleToolbar.kt`、`DiaryInput.kt`、`MobileCheckInDrawer.kt`、`MoodRecordViewport.kt`
- **调用约定**：记录页首发 4 人（心流/内耗/麻木/焦焦）；探查窗首发 9 皮克斯；coord 静默写入不变。
- **验收要点**：记录页场景 A 2×2 + 展开 18 人；场景 B 胶囊贴键盘 + 句尾微型标签；探查窗仍为 9+9；Android 14/15 键盘弹起无 Insets 高度 0 崩溃。

#### 变更记录

- 2026-06-14：PRD v4.0 情绪角色化 UI (#emotion-ui-v4)
- 2026-06-14：记录页写作优先 UX：角色大网格改 BottomSheet，折叠条 + 继续写作 (#emotion-ui-v4)
- 2026-06-14：新增焦焦/迷茫/羞羞 PNG 图标，角色扩至 16 人 (#emotion-ui-v4)
- 2026-06-14：探查弹窗首发改为皮克斯 9 人 3×3，「更多」仅扩展角色 (#emotion-ui-v4)
- 2026-06-14：二分法视觉重构 — 记录页 ActorStage 4 人 + 设置迁设备页 (#binary-visual-refactor)
- 2026-06-14：键盘 focus 时序 + IME 布局加固；MainActivity adjustResize (#keyboard-crash-fix)

---

## 稳定性修复 (2026-06-17)

基于全量代码分析报告的 8 项 Bug 修复。

### F-BUG-001 CoroutineScope 生命周期泄漏

| 字段 | 值 |
|------|-----|
| 来源 | PROJECT-ANALYSIS-REPORT.md / 问题 #1-3 |
| 最后更新 | 2026-06-17 |

#### 已修复内容

- **问题**：`HrSampleBuffer`、`EntitySampleBuffer`、`PolarBleManager`、`DeviceSyncManager` 各自创建 `CoroutineScope(SupervisorJob()+Dispatchers.IO)`，`shutdown()` 从未 cancel scope，长时间运行后内存增长。
- **修复**：
  - `HrSampleBuffer.shutdown()` → 增加 `scope.cancel()`
  - `EntitySampleBuffer.shutdown()` → 增加 `scope.cancel()`
  - `PolarBleManager.shutdown()` → 增加 `scope.cancel()` + 调用 `deviceSyncManager.shutdown()`
  - `DeviceSyncManager` → 新增 `shutdown()` 方法，cancel `syncJob` 和 `scope`
- **关键文件**：`data/HrSampleBuffer.kt`、`data/EntitySampleBuffer.kt`、`polar/PolarBleManager.kt`、`data/sync/DeviceSyncManager.kt`

---

### F-BUG-002 Application 启动错误处理

| 字段 | 值 |
|------|-----|
| 来源 | PROJECT-ANALYSIS-REPORT.md / 问题 #5a |
| 最后更新 | 2026-06-17 |

#### 已修复内容

- **问题**：`MindBodyApplication.onCreate()` 无 try-catch，Room DB 创建失败直接崩溃。
- **修复**：用 try-catch 包裹初始化逻辑，catch 中通过 `AppLogger` 记录错误并通过 Toast 提示用户重启。
- **关键文件**：`MindBodyApplication.kt`

---

### F-BUG-003 传感器时间戳使用 SDK 时间

| 字段 | 值 |
|------|-----|
| 来源 | PROJECT-ANALYSIS-REPORT.md / 问题 #4b |
| 最后更新 | 2026-06-20 |

#### 已修复内容（分两阶段完成）

**阶段一（2026-06-17）**：
- batch-local `now`：将 `System.currentTimeMillis()` 从每样本调用提取为批次级变量，消除同批次各样本时间戳不一致的问题。适用于 HR、皮温、ACC 三路流（SDK 未暴露这三路的传感器级时间戳）。

**阶段二（2026-06-20）**：
- **PPI 传感器时间**：`PolarPpiSample.timeStamp`（Polar epoch 纳秒）已正确转换为 Unix ms 写入 DB。  
  转换公式：`Unix ms = (timeStamp / 1_000_000) + 946_684_800_000`（Polar 2000-01-01 → Unix 1970-01-01 偏移）。  
  `timeStamp == 0` 时 fallback 手机时间并输出 `WARN` 日志，不崩溃。
- **工具函数**：`PolarBleManager.polarSensorTimeToUnixMs(timeStampNs: ULong): Long?`（companion object 内）。
- **连接校时**：`FEATURE_POLAR_ONLINE_STREAMING` 就绪时在协程中调用 `api.setLocalTime(identifier, LocalDateTime.now())`，保证 PPI 帧 timeStamp 有效；失败时告警并继续流（不阻断）。`DeviceSyncManager.syncAll()` 内保留定期重校，两者互补。
- **关键文件**：`polar/PolarBleManager.kt`（`companion object`、`processPpiData`、`bleSdkFeatureReady`）

---

### F-BUG-004 SnoozeReceiver 结构化并发

| 字段 | 值 |
|------|-----|
| 来源 | PROJECT-ANALYSIS-REPORT.md / 问题 #3b |
| 最后更新 | 2026-06-17 |

#### 已修复内容

- **问题**：`MoodReminderSnoozeReceiver` 中 `CoroutineScope(Dispatchers.IO).launch` 无超时控制，进程被 kill 前逃避记录可能丢失。
- **修复**：使用 `withTimeout(10_000L)` 包裹异步逻辑，10 秒内未完成则自动取消。
- **关键文件**：`worker/MoodReminderSnoozeReceiver.kt`

---

### F-BUG-005 connectForSnapshot 异常分类

| 字段 | 值 |
|------|-----|
| 来源 | PROJECT-ANALYSIS-REPORT.md / 问题 #5d |
| 最后更新 | 2026-06-17 |

#### 已修复内容

- **问题**：`connectForSnapshot()` 外层 `catch (e: Exception)` 吞噬所有异常类型，TimeoutCancellationException 和 CancellationException 未正确区分处理。
- **修复**：分离三种异常处理 —— `TimeoutCancellationException`（超时，记录警告）、`CancellationException`（rethrow）、其他 `Exception`（记录错误），不同异常给出不同 status message。
- **关键文件**：`polar/PolarBleManager.kt`

---

### F-BUG-006 AppLogBuffer 锁内 StateFlow 更新优化

| 字段 | 值 |
|------|-----|
| 来源 | PROJECT-ANALYSIS-REPORT.md / 问题 #6b |
| 最后更新 | 2026-06-17 |

#### 已修复内容

- **问题**：`AppLogBuffer.append()` 在 `synchronized` 锁内做 `deque.toList()` 全量复制（最多 800 条），阻塞所有并发的 append 调用者。
- **修复**：将 `_entries.value = deque.toList()` 移到 `synchronized` 块外部，先在锁内完成 deque 操作并拷贝快照，释放锁后再更新 StateFlow。
- **关键文件**：`util/AppLogBuffer.kt`

---

### F-BUG-007 !! 强制解包替换

| 字段 | 值 |
|------|-----|
| 来源 | PROJECT-ANALYSIS-REPORT.md / 问题 #4a, #12b |
| 最后更新 | 2026-06-17 |

#### 已修复内容

- **问题**：`AccRepository.AccMinuteAggregator` 和 `CoordMiniBadge` 中多处 `!!` 强制解包，重构时易引入 NPE。
- **修复**：
  - `AccRepository.kt` → 用局部变量 `currentStart` 缓存可空值，消除两处 `!!`
  - `CoordMiniBadge.kt` → 用 `Map.getValue(key)` 替代 `Map[key]!!`（均为编译期确定的常量 key，`getValue` 更符合"必须存在"的语义）
- **关键文件**：`data/AccRepository.kt`、`ui/mood/CoordMiniBadge.kt`

---

### F-BUG-008 @Deprecated ReplaceWith 修正

| 字段 | 值 |
|------|-----|
| 来源 | PROJECT-ANALYSIS-REPORT.md / 问题 #11a |
| 最后更新 | 2026-06-17 |

#### 已修复内容

- **问题**：`ValueEnergyGrid` 的 `@Deprecated` 注解 `ReplaceWith` 表达式引用 `ActorStage` 但缺少参数，IDE 自动替换会编译失败。
- **修复**：修正 `ReplaceWith` 表达式为含 lambda 参数的完整调用 `ActorStage(onStageRoleTap = { role -> /* handle role selection */ })`。
- **关键文件**：`ui/mood/ValueEnergyGrid.kt`

---

### F-BUG-009 进程异常退出后自动连卡死

| 字段 | 值 |
|------|-----|
| 来源 | 用户反馈 / 冷启动 BLE 卡死 |
| 最后更新 | 2026-06-23 |

#### 已修复内容

- **问题**：App 崩溃或被系统杀进程时 `shutdown()` 未执行，OS 层 GATT 残留；设备停止广播，下次冷启动扫描超时后直连无 SDK 回调，UI 永久卡在「正在自动连接已保存设备…」。
- **修复**：
  - 直连前 `disconnectFromDevice(savedId)` + 1s delay，清理残留 GATT 并恢复设备广播。
  - 直连后 25s 看门狗：若未进入 `CONNECTED`，强制 `DISCONNECTED` 并 `scheduleReconnect`。
- **关键文件**：`polar/PolarBleManager.kt`（`tryAutoConnectInternal`）

---

## 新增条目模板（功能完成后追加）

```markdown
### F-P2-001 功能名称

| 字段 | 值 |
|------|-----|
| Plan | phase2... / todo: xxx |
| 最后更新 | YYYY-MM-DD |

#### 已实现方案
- **目的**：
- **入口**：
- **关键文件**：
- **调用约定**：
- **验收要点**：

#### 变更记录
- YYYY-MM-DD：摘要 (#plan-todo-id)
```

新 ID：`F-P{优先级}-{序号}`，序号在同级内递增；**仅在实际代码合并后**才写入清单。

---

## P3 — 云端融合与展示修复

### F-P3-001 SyncManager ts 修复（ActivityDay/NightlyRecharge）

| 字段 | 值 |
|------|-----|
| Plan | 生理数据落库与展示修复 / todo: fix-ts-zero |
| 最后更新 | 2026-06-24 |

#### 已实现方案
- **目的**：修复 `ActivityDaySummaryEntity`、`NightlyRechargeEntity` 与 `SleepSessionEntity` 上传云端时 `ts=0L` 导致时间窗查询为空的 bug。
- **入口**：`data/sync/SyncManager.kt` → `entityToMap()` 的 `map["ts"]` 赋值块；B 组 `runB()` 对缺 timestamp 的行 `mapNotNull` 跳过。
- **关键文件**：`data/sync/SyncManager.kt`（`parseDateToEpochMs(date: String): Long`）
- **调用约定**：`ActivityDaySummaryEntity` / `NightlyRechargeEntity` 用 `parseDateToEpochMs(entity.date)`；`SleepSessionEntity` 用 `sleepStartTimeMs ?: sleepEndTimeMs`，两者皆 null 时抛 `IllegalArgumentException` 跳过上传。
- **验收要点**：云端 `activity_day_summary` / `nightly_recharge` 的 `ts` 为 UTC 零时 epoch；`sleep_sessions` 的 `ts` 为睡眠起始毫秒（非 0）；看板「运动活动」步数与「睡眠分析」时长趋势有数据。

#### 变更记录
- 2026-06-24：`SleepSessionEntity` 的 `ts` 改为 `sleepStartTimeMs ?: sleepEndTimeMs`，缺 timestamp 时跳过上传；配合服务端睡眠看板 `/series` 与 Polar phases 解析修复 (#sleep-dashboard-fix)
- 2026-06-19：`ActivityDaySummaryEntity`/`NightlyRechargeEntity` 的 `ts` 从 `0L` 改为 `parseDateToEpochMs(entity.date)` (#fix-ts-zero)

---

### F-P3-002 DeviceScreen 设备离线同步状态 UI

| 字段 | 值 |
|------|-----|
| Plan | 生理数据落库与展示修复 / todo: add-devicesync-ui |
| 最后更新 | 2026-06-19 |

#### 已实现方案
- **目的**：在设备连接后，让用户可见 `DeviceSyncManager` 的 IDLE/SYNCING/SUCCESS/FAILED 状态及失败原因。
- **入口**：`ui/device/DeviceScreen.kt` → 手环 `PremiumCard` 内，`status?.let` 块之后。
- **关键文件**：
  - `ui/device/DeviceViewModel.kt`：新增 `deviceSyncStatus: StateFlow<DeviceSyncStatus>` 和 `deviceSyncError: StateFlow<String?>` 两个 StateFlow。
  - `ui/device/DeviceScreen.kt`：新增 `DeviceSyncStatusRow` Composable；仅在 `CONNECTED` 时显示。
- **调用约定**：`DeviceSyncStatusRow(syncStatus, errorMsg, modifier)` — 独立 Composable，图标+颜色+文本随状态变化。
- **验收要点**：手环连接后卡片底部出现「等待同步」→「正在拉取设备数据…」→「设备数据已同步」状态文字；失败时红色显示错误信息。

#### 变更记录
- 2026-06-19：DeviceViewModel 新增两个 StateFlow；DeviceScreen 新增 DeviceSyncStatusRow 组件 (#add-devicesync-ui)

---

### F-P1-014 ACC 10 秒桶聚合（acc_minute_summary）

| 字段 | 值 |
|------|-----|
| Plan | ACC 10 秒聚合 / todo: acc-10s-* |
| 最后更新 | 2026-06-19 |

#### 已实现方案
- **目的**：BLE 高频 ACC 在 Android 端按 10 秒桶聚合为 `avg_magnitude_mg` / `max_magnitude_mg` / `sample_count`，上传 `acc_minute_summary`；不保留原始 `acc_samples` 表。
- **入口**：`polar/PolarBleManager.kt` → `processAccData()` → `AccRepository.ingestSample()`。
- **关键文件**：
  - `data/AccRepository.kt`：`BUCKET_MS = 10_000L`，`AccMinuteAggregator`
  - `data/local/AppDatabase.kt`：`MIGRATION_7_8` 删除 `acc_samples` 表，version 8
  - `data/sync/SyncManager.kt`：仅同步 `acc_minute_summary`
- **调用约定**：每条样本插值 ts 后喂入聚合器；桶边界或 `flush()` 时 upsert 一行。
- **验收要点**：Storage 看板「加速度10秒汇总」行数约 8640/天；云端 `acc_minute_summary` 的 `/series` 使用 `avg_magnitude_mg`。

#### 变更记录
- 2026-06-19：10 秒桶聚合；移除 acc_samples 全链路 (#acc-10s-aggregator)

---

### F-P1-015 BLE 夜间自动断联 / 晨间重连

| 字段 | 值 |
|------|-----|
| Plan | P1 BLE 管理扩展 / todo: ble-nightly-scheduler |
| 最后更新 | 2026-06-20 |

#### 已实现方案
- **目的**：每晚 22:00 自动断开 BLE，让 Polar Loop 进入离线夜间记录；次日 08:00 自动重连并触发 `DeviceSyncManager` 拉取 Sleep / Nightly Recharge 等离线数据。
- **入口**：`MindBodyApplication.onCreate()` → `BleSchedulerWorker.scheduleNext(ACTION_DISCONNECT, KEEP)` 启动链式调度。
- **关键文件**：
  - `worker/BleSchedulerWorker.kt`：`ACTION_DISCONNECT` / `ACTION_RECONNECT`；`delayUntilNextHour()` 计算本地时区整点延迟；Worker 执行后 `scheduleNext()` 自我链接下一任务。
  - `MindBodyApplication.kt`：应用启动时用 `ExistingWorkPolicy.KEEP` 注册首次断开任务，避免覆盖已排队 work。
  - `polar/PolarBleManager.kt`：断开复用 `disconnect()`；重连复用 `tryAutoConnectSavedDevice(force=true)`。
- **调用约定**：默认就寝 22:00、起床 08:00（常量 `DEFAULT_BEDTIME_HOUR` / `DEFAULT_WAKE_HOUR`）；`BleSchedulerWorker.cancel(context)` 可取消整条链。
- **验收要点**：22:00 前后若 BLE 已连接则自动断开；08:00 前后自动扫描并重连已保存设备；重连后设备页可见离线同步状态变化；应用重启不重复排队（KEEP）。

#### 变更记录
- 2026-06-20：新增 BleSchedulerWorker 链式 OneTimeWork 调度 (#ble-nightly-scheduler)

---

### F-P2-UI-001 UI 全盘重构：日记本设计哲学

| 字段 | 值 |
|------|-----|
| Plan | UI 全盘重构设计 / plan: ui_全盘重构设计_8a8fc0e6 |
| 最后更新 | 2026-06-20 |

#### 已实现方案
- **目的**：将 App 从"工业监控器"风格改造为"文艺身心日记本"，三原则：结论先行/数据退居幕后、留白构建层次/禁止硬边框、色彩=生理状态。
- **Design Tokens 重建**：
  - `ui/theme/Color.kt`：全局底色改为 `AppBackground #F2F2F7`（Apple iOS 灰）+ `CardWhite #FFFFFF`；新增 6档状态语义色（CalmTeal/OceanBlue/StressAmber/AnxietyRose/HighAlertRed/BaselineTeal）+ `AmbientShadow`。
  - `ui/theme/Shape.kt`：新增 `NarrativeCard 32dp`（叙事级）、`DataCard 24dp`（数据级）、`Button/Badge CircleShape`；保留旧别名兼容。
  - `ui/theme/Theme.kt`：`surface` 改为 `CardWhite`，`outline` 改为 `AmbientShadow`。
- **三大标准组件**（`ui/components/`）：
  - `HeroIndicator.kt`（组件 C）：径向渐变底色 + 中心结论文字 + 外轨道呼吸动画（`animateFloatAsState` 1.2s 循环）。
  - `NarrativeCard.kt`（组件 A）：32dp 圆角、20dp 内边距、顶部彩色 Badge、行高 24sp、无图禁区；配套 `NarrativeBody` / `NarrativeCaption`。
  - `MicroGrid.kt`（组件 B）：2×2/2×3 网格、0.5dp Alpha10% 分割线、每格 16dp Sparkline 无坐标轴。
- **状态色映射**：`ui/physio/StateColors.kt`，`stateLabel → StateColorToken(accentColor, surfaceColor, zhLabel, description)`。
- **新「状态」Tab**（`ui/physio/`）：
  - `PhysioStateScreen.kt`：Head(20%) HeroIndicator + Middle(50%) NarrativeCard(LLM/基线进度) + Bottom(30%) HRV 6格 MicroGrid。
  - `PhysioStateViewModel.kt`：30s 轮询 `/api/vitals/stream/status`，结果写入 `AppStorage.updatePhysioState()`，不建 Room Entity。
  - `FeedbackHistoryListScreen.kt` + `FeedbackHistoryCard.kt`：完整 LLM 历史列表，含用户响应标签 / "现在记录" 跳转。
- **现有页面视觉重构**：
  - `HeartRateScreen.kt`：HeroIndicator(BPM 呼吸环) + NarrativeCard(皮温体感) + MicroGrid(今日统计) + 折叠 SplineChart。
  - `SensorsScreen.kt`：HeroIndicator(合加速度) + NarrativeCard(运动/PPI 质量) + MicroGrid(全指标 8格)。
  - `DeviceScreen.kt`：新增 `StreamAnalysisCard`（NarrativeCard 24dp），显示当前状态/基线进度/最近推流时间，仅 BLE 连接时展示。
- **导航 6 Tab**：`FloatingIslandNav.kt` 新增「状态」Tab（`MonitorHeart` 图标），图标 20dp，Island 水平 padding 缩减适配 6 项；`AppNavigation.kt` 新增 `physio_state` / `feedback_history` 路由。
- **AppStorage 门面扩展**：`AppStorage.kt` 新增 `latestPhysioState: Flow<PhysioStateSummary?>` 与 `feedbackHistory: Flow<List<LlmFeedbackEntry>>`，配套 `updatePhysioState()` / `updateFeedbackHistory()` 写入方法。
- **关键文件**：
  - `ui/theme/Color.kt`, `Shape.kt`, `Theme.kt`
  - `ui/components/HeroIndicator.kt`, `NarrativeCard.kt`, `MicroGrid.kt`
  - `ui/physio/StateColors.kt`, `PhysioStateScreen.kt`, `PhysioStateViewModel.kt`
  - `ui/physio/FeedbackHistoryListScreen.kt`, `FeedbackHistoryCard.kt`
  - `ui/heartrate/HeartRateScreen.kt`（重构）
  - `ui/sensors/SensorsScreen.kt`（重构）
  - `ui/device/DeviceScreen.kt`（追加 StreamAnalysisCard）
  - `ui/components/FloatingIslandNav.kt`（6 Tab）
  - `ui/navigation/AppNavigation.kt`（新路由）
  - `data/PhysioStateSummary.kt`（数据模型）
  - `data/storage/AppStorage.kt`（门面扩展）
- **调用约定**：`PhysioStateViewModel.startPolling()` / `stopPolling()` 由 Screen `DisposableEffect` 管理；其他页面从 `AppStorage.latestPhysioState` 读取无需手动轮询。
- **验收要点**：FloatingIslandNav 显示 6 个 Tab；状态页三区域布局正确；心率页 Hero 呼吸动画可见；皮温改为叙事文字卡；传感器页三区域布局正确；设备页 BLE 连接后出现推流状态卡。

#### 变更记录
- 2026-06-20：全盘 UI 重构，日记本设计哲学落地 (plan-todo: ui-redesign-all)

---

### F-P1-002 — 实时 PPI 推流管道（Android Phase 1）

**Plan todo**：p1a ~ p1e
**实现日期**：2026-06-21

#### 已实现方案

- **On-device HRV 轻量计算**：`data/HrvOnDevice.kt` — 纯 Kotlin，`rmssd()` / `sdnn()` / `pnn50()`，零三方依赖。
- **PPI 环形缓冲区**：`data/stream/PpiLiveBuffer.kt` — `ReentrantLock` 线程安全；`drainWindowAtomic(sinceMs)` 单次加锁返回全量样本 + 清洗 RR；`drainWindow` / `drainSamples` 委托原子读；质量过滤：`!blocker && skinContactOk && errorEstimateMs <= 50 && ppiMs in 300..2000`；容量 600，溢出时丢弃最旧 1/3。
- **PolarBleManager 挂接**：`polar/PolarBleManager.kt` `processPpiData()` 额外调用 `ppiLiveBuffer.push()`（含 `errorEstimate`）；`ppiLiveBuffer` 作为公开属性暴露供 Worker 消费。
- **推流 HTTP 客户端**：`data/sync/SyncApiClient.kt` 新增 `postPpiWindow(PpiWindowPayload): PpiWindowResult`，`POST /api/vitals/stream/ppi-window`；新增 `registerFcmToken()` 和 `reportNotificationResponse()` 方法。
- **推流主路径（90s）**：`polar/HrStreamService.kt` — 前台服务 `onStartCommand` 启动协程循环，每 **90 秒**调用 `PpiStreamWorker.tryStreamOnce(app, sinceMs=lastWindowEndMs)`；`lastWindowEndMs` 初始为 `serviceStartedAtMs`，仅在非 `SKIPPED_EARLY_GATE` 时推进至当前时刻，避免固定 lookback 与上传耗时造成窗口空洞；`startStreamLoopIfNeeded()` 幂等，避免重复 `onStartCommand` 叠多个循环。
- **推流共享逻辑**：`worker/PpiStreamWorker.kt` — `companion object.tryStreamOnce(sinceMs)` 抽取上传核心；返回 `StreamAttemptResult`（`SKIPPED_EARLY_GATE` / `SKIPPED_DATA` / ACCEPTED / FAILED）；质量门控 `n_clean >= 25` 且 `n_clean/n_raw >= 45%`（与服务端 stream_routes / HeartPy 二次门控对齐）。
- **推流兜底（15min）**：`PpiStreamWorker` 仍注册 WorkManager 15 分钟周期（Service 被杀或未启动时保底）；兜底窗口回溯 **120 秒**；`scheduleRepeating()` 在 `MindBodyApplication.onCreate()` 注册。
- **关键文件**：
  - `data/HrvOnDevice.kt`
  - `data/stream/PpiLiveBuffer.kt`
  - `data/sync/SyncApiClient.kt`
  - `worker/PpiStreamWorker.kt`
  - `polar/HrStreamService.kt`（90s 推流循环）
  - `polar/PolarBleManager.kt`（修改）
  - `MindBodyApplication.kt`（修改）

#### 变更记录
- 2026-06-23：PPI 窗口游标 + 原子 drain — `lastWindowEndMs` 覆盖预热期；`drainWindowAtomic` 消除 coverage 竞态 (#ppi-upload-log-gap-fix)
- 2026-06-23：PPI 清洗规则优化 — errorEstimate 过滤、RR 下界 300ms、门控 n_clean≥25 + coverage≥50% (plan: PPI清洗优化)
- 2026-06-23：推流主路径改为 HrStreamService 90s 协程循环，WorkManager 15min 兜底 (plan: PPI推流90秒循环)

---

### F-P5-001 — ntfy 推送通知（Android Phase 5，原 FCM/MQTT 已替换）

**Plan todo**：p5a ~ p5b
**实现日期**：2026-06-21（FCM）；2026-06-23（MQTT）；2026-06-23（ntfy 替换 MQTT）

#### 已实现方案

- **通知渠道 + 展示**：`notification/PhysioNotificationManager.kt` — 单例 `object`；`ensureChannel()` 幂等建渠道（`physio_feedback`，高优先级）；`show(context, notificationId, stateLabel, message)` 展示带 BigText 的状态通知，含三个操作按钮（本地通知，情绪记录提醒等仍使用）。
  - 按钮 1「记录心情」→ `MainActivity`（`nav_target=mood_record`）
  - 按钮 2「稍后提醒」→ `PhysioNotificationReceiver.ACTION_SNOOZE`（BroadcastReceiver + 服务端回报）
  - 按钮 3「今天不再」→ `PhysioNotificationReceiver.ACTION_DISMISS`（BroadcastReceiver + 服务端回报）
- **ntfy 推送（服务端 → 手机）**：ECS `push_service.py` 经 `httpx` POST 到 `https://ntfy.sh/{ntfy_topic_prefix}-{device_id}`（默认 `mindbody-83f94020`）；手机安装 **ntfy F-Droid 版**，设置 → WebSocket 模式（`wss://ntfy.sh:443`，TLS 绕过运营商对明文 WS 的拦截），订阅 Device 页显示的 Topic。
- **Device 页 Topic 展示**：`ui/device/DeviceViewModel.ntfyTopic` 从 `SyncPreferences.deviceId` 派生；`DeviceScreen` 开发者选项「云端同步」卡片末尾展示只读 Topic + 复制按钮。
- **前台服务**：`polar/HrStreamService.kt` — 仅维持 PPI 90s 推流循环，不再维护 MQTT 长连接。
- **操作回报 Receiver**：`notification/PhysioNotificationReceiver.kt` — 处理 SNOOZE / DISMISS 广播，IO 协程回报 `reportNotificationResponse()`。
- **Manifest 注册**：`HrStreamService`（connectedDevice FGS）+ `PhysioNotificationReceiver`。
- **Application 初始化**：`MindBodyApplication.onCreate()` 调用 `PhysioNotificationManager.ensureChannel(this)`。

#### 激活 ntfy 前置步骤（需用户操作）

1. 手机安装 [ntfy F-Droid 版](https://f-droid.org/packages/io.heckel.ntfy/) → 设置 → 推送服务 → **WebSocket**（不用 FCM）。
2. 电池管理 → ntfy → **无限制**；多任务后台加锁。
3. MindBody 设备页（开发者模式）复制 **ntfy 推送 Topic**，在 ntfy App 点 + 订阅。
4. ECS `.env`：`NTFY_SERVER=https://ntfy.sh`、`NTFY_TOPIC_PREFIX=mindbody`；`docker compose restart api`。
5. 测试：`curl -d "测试" -H "Title: 生理状态提醒" https://ntfy.sh/mindbody-{device_id}`

#### 关键文件

- `notification/PhysioNotificationManager.kt`
- `notification/PhysioNotificationReceiver.kt`
- `polar/HrStreamService.kt`（PPI 推流）
- `polar/PolarBleManager.kt`（BLE 连接后启动 FGS）
- `data/sync/SyncApiClient.kt`（reportNotificationResponse）
- `data/sync/SyncPreferences.kt`（deviceId → ntfy topic）
- `ui/device/DeviceViewModel.kt`（ntfyTopic + 复制）
- `ui/device/DeviceScreen.kt`（Topic 展示）
- `AndroidManifest.xml`（HrStreamService + PhysioNotificationReceiver）
- `MindBodyApplication.kt`（ensureChannel）
- 服务端：`server/backend/app/services/push_service.py`、`server/backend/app/config.py`

#### 变更记录
- 2026-06-23：MQTT 替换为 ntfy（删除 PhysioMqttSubscriber / HiveMQ；服务端 httpx POST）(F-P5-001)
- 2026-06-23：MQTT 改 WebSocket 经 nginx `/mqtt` 反代（已废弃）(F-P5-001)
- 2026-06-23：FCM 替换为 MQTT（已废弃）(F-P5-001)
- 2026-06-21：FCM 启动时主动拉 token 并注册（已废弃）(F-P5-001)
- 2026-06-21：实时生理状态检测系统 Android Phase 1 + Phase 5 落地 (plan: 实时生理状态检测系统)
