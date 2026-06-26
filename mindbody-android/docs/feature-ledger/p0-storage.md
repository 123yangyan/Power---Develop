# P0 — 统一存储核心

路径均相对于 `app/src/main/java/com/owner/mindbody/`。返回 [`FEATURE-LEDGER.md`](../../FEATURE-LEDGER.md)。

---

### F-P0-001 HR 全量永久保存
Plan: completeness · 更新: 2026-06-13

- **目的** 心率样本永久保留，禁止自动删除。
- **入口** `HrRepository.saveSample()` → `HrSampleBuffer`
- **文件** `data/HrRepository.kt`
- **约定** `deleteOlderThan` 保留于 DAO 但**不得**在 save 流程中调用。
- **验收** 连续采集 24h+ 后 `COUNT(*)` 持续增长；无 cutoff 逻辑。

> 2026-06-13 去掉 24h 自动删除 (#completeness)

---

### F-P0-002 SyncMeta 实体约定
Plan: sync-meta · 更新: 2026-06-13

- **目的** Room 实体共用同步元数据约定。
- **文件** `data/local/SyncMeta.kt`、`data/local/HrSampleEntity.kt`（`@Embedded val sync: SyncMeta`）
- **约定** 新实体必须 `@Embedded sync: SyncMeta`；`SyncState` 为 PENDING/SYNCED/FAILED。
- **验收** HrSampleEntity 无独立 `synced: Boolean` 字段。

> 2026-06-13 SyncMeta + HrSampleEntity 改造 (#sync-meta)

---

### F-P0-003 Room 迁移框架 + WAL
Plan: migration-framework · 更新: 2026-06-13

- **目的** schema 可演进且不丢数据。
- **文件** `data/local/AppDatabase.kt`（v4、`MIGRATION_1_2`/`2_3`/`3_4`）、`app/build.gradle.kts`（KSP schema）
- **约定** **禁止** `fallbackToDestructiveMigration`；版本 +1 必须加 Migration。
- **验收** WAL 已开启；exportSchema=true。

> 2026-06-13 v4 + MIGRATION_2_3/3_4 (#migration-framework)

---

### F-P0-004 HrSampleBuffer 批量缓冲
Plan: batch-buffer · 更新: 2026-06-13

- **目的** 高频 HR 写入合并为批量事务，避免 UI 卡顿。
- **文件** `data/HrSampleBuffer.kt`、`data/HrRepository.kt`（投递 buffer + `flush()`）
- **约定** save 走 buffer；App 退出/断连时 `storage.flushAll()`。
- **验收** `HrSampleDao.insertAll` 批量写入；DAO 有分页查询接口。

> 2026-06-13 批量缓冲 + flush (#batch-buffer)

---

### F-P0-005 AppStorage 统一门面
Plan: storage-facade · 更新: 2026-06-13

- **目的** 功能层单一存储入口。
- **入口** `MindBodyApplication.storage` → `AppStorage`
- **文件** `data/storage/AppStorage.kt`、`MindBodyApplication.kt`
- **约定** UI/ViewModel 用 `app.storage.hr`，禁止直接 `AppDatabase` / `HrRepository()`。
- **验收** `HeartRateViewModel`、`DeviceViewModel` 经 storage 访问；扩展后含 `skinTemp`/`acc`/`ppi`/`activityDay`/`sleep`/`training` 等。

> 2026-06-13 AppStorage 门面 (#storage-facade)

---

### F-P0-006 SyncManager 同步占位
Plan: sync-reserved · 更新: 2026-06-13

- **目的** 预留 DAO 同步契约与空实现（后续云端同步再实现）。
- **文件** `data/sync/SyncableDao.kt`、`data/sync/SyncManager.kt`、`data/local/HrSampleDao.kt`
- **约定** DAO 实现 `getUnsynced` / `markSynced` / `markFailed`；SyncManager 当前占位。
- **验收** `getUnsynced()` 可返回 PENDING 记录。

> 2026-06-13 SyncableDao + SyncManager 占位 (#sync-reserved)
