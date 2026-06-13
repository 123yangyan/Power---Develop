# MindBody Android — 功能实现清单

> **范围**：仅 [`mindbody-android/`](.) 工程内**已落地**功能。待做特性见 [`.cursor/plans/`](../.cursor/plans/)，**不在此预填**。
>
> Agent **执行 mindbody-android 相关任务前必读**；**功能实现完成后追加或更新本清单**。
>
> 规则：[`feature-ledger.mdc`](../.cursor/rules/feature-ledger.mdc) · 产品说明：[`PRODUCT.md`](PRODUCT.md)

**最后更新**：2026-06-13

---

## 索引（已实现 28 条）

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
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：Polar Loop 扫描、连接、断开与状态流。
- **入口**：`MindBodyApplication.polarBleManager`
- **关键文件**：`polar/PolarBleManager.kt`、`ui/device/DeviceScreen.kt`、`ui/device/DeviceViewModel.kt`
- **调用约定**：SDK 8.0.0；`ConnectionState` StateFlow 驱动 UI。
- **验收要点**：真机可扫描连接；常连接模式断线约 3s 自动重连。

#### 变更记录

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
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：`startHrStreaming` 持续采集并落库，后台保活。
- **关键文件**：`polar/PolarBleManager.kt`、`polar/HrStreamService.kt`、`ui/heartrate/HeartRateScreen.kt`
- **调用约定**：HR 样本经 `storage.hr.saveSample` → buffer；心率页进入时启动前台服务。
- **验收要点**：实时 BPM 显示；样本写入 Room。

#### 变更记录

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

## P2 — 心情记录

### F-P2-001 mood_entries 实体与 Repository

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: mood-entity |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：本地持久化心情记录，为 Phase 3 同步就绪。
- **入口**：`AppStorage.mood` → `MoodRepository`
- **关键文件**：`data/local/MoodEntryEntity.kt`、`MoodEntryDao.kt`；`data/MoodRepository.kt`；`AppDatabase` v5 + `MIGRATION_4_5`
- **调用约定**：实体含 `@Embedded sync: SyncMeta`；DAO 实现 `SyncableDao`；功能层经 `app.storage.mood` 读写。
- **验收要点**：`getUnsynced()` 可返回 PENDING 记录；迁移不丢 v4 数据。

#### 变更记录

- 2026-06-13：mood_entries 表 + Repository (#mood-entity)

---

### F-P2-002 ValueEnergyGrid 四象限点选

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: value-energy-grid |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：移植 emotion 价值感×耗能坐标点选。
- **关键文件**：`ui/mood/ValueEnergyGrid.kt`、`ui/mood/MoodQuadrant.kt`
- **调用约定**：点击映射 coord -4~+4；象限名与 emotion 一致。
- **验收要点**：落点与坐标显示一致；四象限背景可见。

#### 变更记录

- 2026-06-13：Compose 网格组件 (#value-energy-grid)

---

### F-P2-003 DiaryInput 日记输入

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: diary-input |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：多行日记输入 + v3.7 Enter 列表续号。
- **关键文件**：`ui/mood/DiaryInput.kt`、`DiaryListContinue.kt`
- **验收要点**：有序/无序列表 Enter 续号；记录页 scrollable 固定高度滚动。

#### 变更记录

- 2026-06-13：DiaryInput 组件 (#diary-input)
- 2026-06-13：v3.7 列表续号 (#wave-a-diary-continue)

---

### F-P2-004 记录页 MoodRecordScreen

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: mood-record-screen |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：组装坐标+日记+保存，经 ViewModel 写 `storage.mood`。
- **入口**：底部导航「记录」→ `MoodRecordScreen`
- **关键文件**：`MoodRecordScreen.kt`、`MoodRecordViewport.kt`、`MoodRecordViewModel.kt`、`MoodSettingsSection.kt`
- **调用约定**：`MoodRecordViewport` 共用 page/popup/modal；显示今日序号与上次记录时间。
- **验收要点**：真机可完成点选→写日记→保存。

#### 变更记录

- 2026-06-13：记录页 + ViewModel (#mood-record-screen)

---

### F-P2-005 保存时 HR 快照关联

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: hr-snapshot |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：记录时刻关联本地 HR 估计值。
- **关键文件**：`MoodRecordViewModel.resolveHrSnapshot()`；复用 `HrRepository.getHrNearTimestamp`、`PolarBleManager.connectForSnapshot`
- **调用约定**：常连接先查 ±5min 均值；短连接无样本时 `connectForSnapshot`；无手环时 `hr_at_entry` 为 null。
- **验收要点**：历史页展示「估计关联」标注；非医疗诊断文案存在。

#### 变更记录

- 2026-06-13：HR 快照逻辑 (#hr-snapshot)

---

### F-P2-006 WorkManager 定时提醒

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: reminder |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：替代 emotion daily-checkin-service，定时提醒记录。
- **关键文件**：`worker/MoodReminderWorker.kt`、`MoodReminderDeliver.kt`、`MoodReminderScheduler.kt`；`MoodCheckInActivity.kt`；`data/MoodPreferences.kt`
- **调用约定**：间隔 1–1440 分钟；静默可编辑；strongPopup 全屏 CheckIn；Esc/稍后写逃避记录 + 20min snooze；测试提醒 30s/60s。
- **验收要点**：`MindBodyApplication.onCreate` 注册周期任务；静默时段不弹通知。

#### 变更记录

- 2026-06-13：WorkManager + MoodPreferences (#reminder)

---

### F-P2-007 历史列表 MoodHistoryScreen

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: history-screen |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：列表、预览、编辑、删除。
- **入口**：底部导航「历史」→ `MoodHistoryScreen`
- **关键文件**：`MoodHistoryScreen.kt`、`MoodHistoryRowBuilder.kt`、`CoordMiniBadge.kt`、`DailyEntryIndex.kt`
- **调用约定**：极性/逃避卡片样式；CoordMiniBadge；页码按钮 + 跳转；同日 `(i/total)`。
- **验收要点**：保存后在历史可见；编辑/删除生效。

#### 变更记录

- 2026-06-13：历史页 (#history-screen)

---

### F-P2-008 底部导航记录/历史页签

| 字段 | 值 |
|------|-----|
| Plan | phase2心情记录移植 / todo: navigation |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：主导航整合记录与历史。
- **关键文件**：`ui/navigation/AppNavigation.kt`；`ui/components/FloatingIslandNav.kt`（`mood_record` / `mood_history`）
- **调用约定**：通知 deep link `MainActivity.EXTRA_NAVIGATE_TO=mood_record`。
- **验收要点**：五页签可切换；FTU 页仍隐藏底栏。

#### 变更记录

- 2026-06-13：导航扩展 (#navigation)

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
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：对齐 daily-checkin-service 双通道提醒与 Esc snooze。
- **入口**：`MoodReminderDeliver` → 通知 + `MoodCheckInActivity`
- **关键文件**：`MoodCheckInActivity.kt`、`MoodCheckInConstants.kt`；`MoodRecordViewModel.recordSnooze()`
- **调用约定**：逃避记录 `fact=逃避记录, coord(0,0)`；snoozeCount 触发 20min 短间隔。
- **验收要点**：强弹窗可关；稍后写入逃避记录。

#### 变更记录

- 2026-06-13：CheckIn + snooze (#wave-b-checkin-dialog)

---

### F-P2-012 历史 CoordMiniBadge 与分页增强

| 字段 | 值 |
|------|-----|
| Plan | phase2 对齐 emotion v3.7 / todo: wave-c-history-polish |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：历史列表视觉与分页对齐 emotion v3.7。
- **关键文件**：`CoordMiniBadge.kt`、`MoodHistoryRowBuilder.kt`；`MoodHistoryScreen` pager
- **验收要点**：极性着色、逃避 badge、页码跳转。

#### 变更记录

- 2026-06-13：历史 polish (#wave-c-history-polish)

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
