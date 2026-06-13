# MindBody 功能实现清单

> Agent **执行前必读**、**执行后必更新**。详细设计见 `.cursor/plans/`；本文件记录**施工级**已实现/待实现方案。
>
> 规则：[`feature-ledger.mdc`](rules/feature-ledger.mdc) · 优先级：[`project-priority.mdc`](rules/project-priority.mdc)

**最后全量更新**：2026-06-13

---

## 索引

| ID | 名称 | 状态 | Plan todo |
|----|------|------|-----------|
| F-P0-001 | HR 全量永久保存 | completed | completeness |
| F-P0-002 | SyncMeta 实体约定 | completed | sync-meta |
| F-P0-003 | Room 迁移框架 + WAL | completed | migration-framework |
| F-P0-004 | HrSampleBuffer 批量缓冲 | completed | batch-buffer |
| F-P0-005 | AppStorage 统一门面 | completed | storage-facade |
| F-P0-006 | SyncManager 同步占位 | completed | sync-reserved |
| F-P1-001 | Polar BLE 扫描/连接/断开 | completed | phase1-android-polar |
| F-P1-002 | FTU 首次使用配置 | completed | phase1-android-polar |
| F-P1-003 | 实时心率流 + 前台服务 | completed | phase1-android-polar |
| F-P1-004 | BLE 连接模式切换 | completed | phase1-android-polar |
| F-P1-005 | connectForSnapshot 短连接快照 | completed | phase1-android-polar |
| F-P1-006 | 心率页 UI 与统计曲线 | completed | phase1-android-polar |
| F-P1-007 | 设备页与配对引导 | completed | phase1-android-polar |
| F-P1-008 | DevicePreferences DataStore | completed | phase1-android-polar |
| F-P2-001 | Mood 实体与 Repository | pending | mood-entity |
| F-P2-002 | ValueEnergyGrid 四象限 | pending | value-energy-grid |
| F-P2-003 | DiaryInput 日记输入 | pending | diary-input |
| F-P2-004 | 心情记录页 | pending | mood-record-screen |
| F-P2-005 | 记录时 HR 快照关联 | pending | hr-snapshot |
| F-P2-006 | WorkManager 定时提醒 | pending | reminder |
| F-P2-007 | 心情历史列表 | pending | history-screen |
| F-P2-008 | 导航增加记录/历史页签 | pending | navigation |
| F-P3-001 | Server 数据模型扩展 | pending | server-models |
| F-P3-002 | Server REST API | pending | server-api |
| F-P3-003 | fusion_pipeline 融合 | pending | fusion-pipeline |
| F-P3-004 | 每日融合调度 | pending | daily-scheduler |
| F-P3-005 | manifest v6 Schema | pending | manifest-v6 |
| F-P3-006 | Android Retrofit 客户端 | pending | android-api |
| F-P3-007 | WorkManager 增量同步 | pending | sync-worker |
| F-P3-008 | daily_guidance_cache | pending | guidance-cache |
| F-P3-009 | 指导页 UI | pending | guidance-ui |
| F-P4-001 | 仪表页核心 | pending | dashboard-screen |
| F-P4-002 | 仪表 HR sparkline | pending | dashboard-hr-sparkline |
| F-P4-003 | 7 日双轴趋势 | pending | seven-day-trends |
| F-P4-004 | continuity_summary 展示 | pending | continuity-display |
| F-P4-005 | 记录页近 3 日 AI 摘要 | pending | record-page-summary |
| F-P4-006 | 仪表为默认首页 | pending | navigation-dashboard |
| F-P4-007 | 7 日闭环验收 | pending | e2e-polish |

---

## P0 — 统一存储核心

### F-P0-001 HR 全量永久保存

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P0 |
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
| 状态 | completed |
| 优先级 | P0 |
| Plan | 统一存储核心模块 / todo: sync-meta |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：所有 Room 实体共用同步元数据，便于 Phase 3 上报。
- **关键文件**：`data/local/SyncMeta.kt`、`data/local/HrSampleEntity.kt`（`@Embedded val sync: SyncMeta`）
- **调用约定**：新实体必须 `@Embedded sync: SyncMeta`；`SyncState` 为 PENDING/SYNCED/FAILED。
- **验收要点**：HrSampleEntity 无独立 `synced: Boolean` 字段。

#### 变更记录

- 2026-06-13：SyncMeta + HrSampleEntity 改造 (#sync-meta)

---

### F-P0-003 Room 迁移框架 + WAL

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P0 |
| Plan | 统一存储核心模块 / todo: migration-framework |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：schema 可演进且不丢数据。
- **关键文件**：`data/local/AppDatabase.kt`（v2、`MIGRATION_1_2`）、`app/build.gradle.kts`（KSP schema 路径）、`app/schemas/.../2.json`
- **调用约定**：**禁止** `fallbackToDestructiveMigration`；版本 +1 必须加 Migration。
- **验收要点**：WAL 已开启；exportSchema=true。

#### 变更记录

- 2026-06-13：v2 + MIGRATION_1_2 (#migration-framework)

---

### F-P0-004 HrSampleBuffer 批量缓冲

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P0 |
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
| 状态 | completed |
| 优先级 | P0 |
| Plan | 统一存储核心模块 / todo: storage-facade |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：功能层单一存储入口。
- **入口**：`MindBodyApplication.storage` → `AppStorage`
- **关键文件**：`data/storage/AppStorage.kt`、`MindBodyApplication.kt`
- **调用约定**：UI/ViewModel 用 `app.storage.hr`，禁止直接 `AppDatabase` / `HrRepository()`。
- **验收要点**：`HeartRateViewModel`、`DeviceViewModel` 经 storage 访问。

#### 变更记录

- 2026-06-13：AppStorage 门面 (#storage-facade)

---

### F-P0-006 SyncManager 同步占位

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P0 |
| Plan | 统一存储核心模块 / todo: sync-reserved |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：为 Phase 3 云端同步预留 DAO 契约与空实现。
- **关键文件**：`data/sync/SyncableDao.kt`、`data/sync/SyncManager.kt`、`data/local/HrSampleDao.kt`
- **调用约定**：DAO 实现 `getUnsynced` / `markSynced` / `markFailed`；SyncManager 当前占位。
- **验收要点**：`getUnsynced()` 可返回 PENDING 记录。

#### 变更记录

- 2026-06-13：SyncableDao + SyncManager 占位 (#sync-reserved)

---

## P1 — Polar BLE 与心率（Phase 1）

### F-P1-001 Polar BLE 扫描/连接/断开

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P1 |
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：Polar Loop 扫描、连接、断开与状态流。
- **入口**：`MindBodyApplication.polarBleManager`
- **关键文件**：`polar/PolarBleManager.kt`、`ui/device/DeviceScreen.kt`、`ui/device/DeviceViewModel.kt`
- **调用约定**：SDK 8.0.0；`ConnectionState` StateFlow 驱动 UI。
- **验收要点**：真机可扫描连接；断线常连接模式约 3s 自动重连。

#### 变更记录

- 2026-06-13：Phase 1 初始实现 (#phase1-android-polar)

---

### F-P1-002 FTU 首次使用配置

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P1 |
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
| 状态 | completed |
| 优先级 | P1 |
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：`startHrStreaming` 持续采集并落库，后台保活。
- **关键文件**：`polar/PolarBleManager.kt`、`polar/HrStreamService.kt`、`ui/heartrate/HeartRateScreen.kt`
- **调用约定**：HR 样本经 `hrRepository.saveSample` → buffer；心率页进入时启动前台服务。
- **验收要点**：实时 BPM 显示；样本写入 Room。

#### 变更记录

- 2026-06-13：HR 流 + 前台服务 (#phase1-android-polar)

---

### F-P1-004 BLE 连接模式切换

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P1 |
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：常连接（PERSISTENT）vs 短连接（ON_DEMAND）策略切换。
- **关键文件**：`PolarBleManager.kt`（`ConnectionMode`）、`ui/components/BleModeRadioGroup.kt`、`DevicePreferences`
- **调用约定**：模式持久化 DataStore；设备页可切换。
- **验收要点**：两种模式行为符合 PRODUCT.md 表格描述。

#### 变更记录

- 2026-06-13：连接模式切换 (#phase1-android-polar)

---

### F-P1-005 connectForSnapshot 短连接快照

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P1 |
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：按需连接采集约 5 秒 HR，供 Phase 2 心情记录关联快照。
- **入口**：`PolarBleManager.connectForSnapshot(deviceId): Int?`
- **关键文件**：`polar/PolarBleManager.kt`（`SNAPSHOT_SAMPLE_DURATION_MS = 5000`）
- **调用约定**：短连接模式下由 F-P2-005 调用；返回 BPM 或 null。
- **验收要点**：采集后主动断开；超时 30s。

#### 变更记录

- 2026-06-13：快照接口预留 (#phase1-android-polar)

---

### F-P1-006 心率页 UI 与统计曲线

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P1 |
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：当前 BPM、今日统计、趋势图。
- **关键文件**：`ui/heartrate/HeartRateScreen.kt`、`HeartRateViewModel.kt`、`ui/components/MindBodySplineChart.kt`
- **调用约定**：数据来自 `storage.hr`；图表显示层可降采样，存储层全量。
- **验收要点**：展示样本数/平均/最低/最高；折线图最近样本。

#### 变更记录

- 2026-06-13：心率页 UI (#phase1-android-polar)

---

### F-P1-007 设备页与配对引导

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P1 |
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：扫描列表、连接、FTU 入口、配对提示。
- **关键文件**：`ui/device/DeviceScreen.kt`、`DeviceViewModel.kt`
- **验收要点**：距离/单设备/恢复出厂等提示文案存在。

#### 变更记录

- 2026-06-13：设备页 (#phase1-android-polar)

---

### F-P1-008 DevicePreferences DataStore

| 字段 | 值 |
|------|-----|
| 状态 | completed |
| 优先级 | P1 |
| Plan | loop心情ai产品规划 / todo: phase1-android-polar |
| 最后更新 | 2026-06-13 |

#### 已实现方案

- **目的**：已配对设备 ID、FTU 状态、BLE 模式等非 Room 偏好。
- **关键文件**：`data/DevicePreferences.kt`
- **调用约定**：不经 AppStorage；由 Application 直接暴露。

#### 变更记录

- 2026-06-13：DataStore 偏好 (#phase1-android-polar)

---

## P2 — 心情记录移植（Phase 2）

### F-P2-001 Mood 实体与 Repository

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P2 |
| Plan | phase2心情记录移植 / todo: mood-entity |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：`MoodEntryEntity` + `MoodEntryDao` + `MIGRATION_2_3`，`AppStorage.mood`
- **预计文件**：`data/local/MoodEntryEntity.kt`、`MoodEntryDao.kt`、`MoodRepository.kt`；改造 `AppDatabase.kt`、`AppStorage.kt`
- **依赖**：F-P0-002 SyncMeta、F-P0-005 AppStorage
- **验收标准**：存储 4 步接入；字段 fact/coord_x/coord_y/occurred_at/hr_at_entry

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P2-002 ValueEnergyGrid 四象限

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P2 |
| Plan | phase2心情记录移植 / todo: value-energy-grid |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：移植 emotion `ValueEnergyGrid.tsx` → Compose，coord -4~+4
- **预计文件**：`ui/mood/ValueEnergyGrid.kt`
- **依赖**：F-P2-001
- **验收标准**：四象限点选同时决定 X/Y

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P2-003 DiaryInput 日记输入

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P2 |
| Plan | phase2心情记录移植 / todo: diary-input |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：移植 `DiaryInput.tsx` → Compose
- **预计文件**：`ui/mood/DiaryInput.kt`
- **依赖**：无（可与 F-P2-002 并行）
- **验收标准**：日记写入 `fact` 字段

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P2-004 心情记录页

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P2 |
| Plan | phase2心情记录移植 / todo: mood-record-screen |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：MoodRecordForm 组装 + ViewModel + `app.storage.mood` 提交
- **预计文件**：`ui/mood/MoodRecordScreen.kt`、`MoodViewModel.kt`
- **依赖**：F-P2-001、F-P2-002、F-P2-003
- **验收标准**：坐标+日记+保存全流程

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P2-005 记录时 HR 快照关联

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P2 |
| Plan | phase2心情记录移植 / todo: hr-snapshot |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：保存 mood 时写入 `hr_at_entry`（±5min 均值或 `connectForSnapshot`）
- **依赖**：F-P2-004、F-P1-005
- **验收标准**：有 HR 时有估计值；UI 标注「估计关联」

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P2-006 WorkManager 定时提醒

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P2 |
| Plan | phase2心情记录移植 / todo: reminder |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：替代 emotion daily-checkin；间隔提醒 + 静默时段
- **预计文件**：`worker/MoodReminderWorker.kt`、DataStore 设置
- **验收标准**：保存后仍按间隔提醒

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P2-007 心情历史列表

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P2 |
| Plan | phase2心情记录移植 / todo: history-screen |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：EntryHistoryPage 移植：列表、预览、编辑、删除
- **预计文件**：`ui/mood/MoodHistoryScreen.kt`
- **依赖**：F-P2-001
- **验收标准**：历史可编辑删除

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P2-008 导航增加记录/历史页签

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P2 |
| Plan | phase2心情记录移植 / todo: navigation |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：底部导航增加「记录」「历史」
- **预计文件**：`ui/navigation/AppNavigation.kt`、`FloatingIslandNav.kt`
- **依赖**：F-P2-004、F-P2-007
- **验收标准**：与心率/设备页整合

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

## P3 — 云端融合（Phase 3）

### F-P3-001 Server 数据模型扩展

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: server-models |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：devices、hr_samples、mood_entries、daily_fusion_jobs、daily_guidance 表
- **预计文件**：`server/backend/app/models.py`
- **依赖**：F-P2-001
- **验收标准**：模型与主 Plan API 设计一致

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P3-002 Server REST API

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: server-api |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：`/api/vitals/hr/batch`、`/api/mood/entries`、`/api/guidance/daily` 等
- **预计文件**：`server/backend/app/main.py`
- **依赖**：F-P3-001

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P3-003 fusion_pipeline 融合

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: fusion-pipeline |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：HR+情绪对齐预处理 + 百炼 Qwen JSON → daily_guidance
- **预计文件**：`server/backend/app/fusion_pipeline.py`
- **依赖**：F-P3-001、F-P3-002

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P3-004 每日融合调度

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: daily-scheduler |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：每日 22:30 触发；失败可重试
- **预计文件**：`server/backend/app/worker.py`
- **依赖**：F-P3-003

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P3-005 manifest v6 Schema

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: manifest-v6 |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：v6 JSON Schema + Kotlin `AiInsightManifest.kt`
- **参考**：emotion `aiInsightManifest.ts`

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P3-006 Android Retrofit 客户端

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: android-api |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：Retrofit + deviceId 鉴权
- **预计文件**：`mindbody-android/.../data/remote/`

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P3-007 WorkManager 增量同步

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: sync-worker |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：实现 `SyncManager`；HR 批量 + mood 上报
- **依赖**：F-P0-006、F-P3-006
- **预计文件**：`data/sync/SyncManager.kt`、`worker/SyncWorker.kt`

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P3-008 daily_guidance_cache

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: guidance-cache |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：Room 缓存云端指导 JSON；AppStorage 暴露
- **依赖**：F-P0-005、F-P3-006

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P3-009 指导页 UI

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P3 |
| Plan | phase3云端融合 / todo: guidance-ui |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：展示 guidance_primary、risk_level、mood_index
- **预计文件**：`ui/guidance/`
- **依赖**：F-P3-008

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

## P4 — 仪表与闭环（Phase 4）

### F-P4-001 仪表页核心

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P4 |
| Plan | phase4仪表与闭环 / todo: dashboard-screen |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：DashboardGuidanceCard、成长阶段、mood_index
- **参考**：emotion `DashboardPage.tsx`
- **依赖**：F-P3-009

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P4-002 仪表 HR sparkline

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P4 |
| Plan | phase4仪表与闭环 / todo: dashboard-hr-sparkline |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：仪表页 HR 折线 + 当日摘要
- **依赖**：F-P4-001、F-P1-006

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P4-003 7 日双轴趋势

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P4 |
| Plan | phase4仪表与闭环 / todo: seven-day-trends |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：mood_index + 静息 HR 双轴图
- **预计文件**：`ui/dashboard/SevenDayTrendChart.kt`

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P4-004 continuity_summary 展示

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P4 |
| Plan | phase4仪表与闭环 / todo: continuity-display |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：展示近 7 日跨日 continuity 摘要
- **依赖**：F-P3-003

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P4-005 记录页近 3 日 AI 摘要

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P4 |
| Plan | phase4仪表与闭环 / todo: record-page-summary |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：MoodRecordScreen 顶部近 3 日 key_insight 卡片
- **依赖**：F-P2-004、F-P3-008

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P4-006 仪表为默认首页

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P4 |
| Plan | phase4仪表与闭环 / todo: navigation-dashboard |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：底部导航增加「仪表」并设为默认 start destination
- **依赖**：F-P4-001

#### 变更记录

- 2026-06-13：清单初始录入（待实施）

---

### F-P4-007 7 日闭环验收

| 字段 | 值 |
|------|-----|
| 状态 | pending |
| 优先级 | P4 |
| Plan | phase4仪表与闭环 / todo: e2e-polish |
| 最后更新 | 2026-06-13 |

#### 待实施方案

- **目标**：连续 7 日使用后指导文案体现历史模式变化
- **依赖**：F-P4-001～F-P4-006 全部 completed
- **验收标准**：人工抽检 guidance_primary 非每日重复

#### 变更记录

- 2026-06-13：清单初始录入（待实施）
