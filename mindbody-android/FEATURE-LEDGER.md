# MindBody Android — 功能实现清单（总索引）

> **范围**：仅 [`mindbody-android/`](.) 工程内**已落地**功能。待做特性见 [`.cursor/plans/`](../.cursor/plans/)，**不在此预填**。
>
> **分清单目录**：[`docs/feature-ledger/`](docs/feature-ledger/) · 模板：[`_template.md`](docs/feature-ledger/_template.md)
>
> 规则：[`feature-ledger.mdc`](../.cursor/rules/feature-ledger.mdc) · 产品：[`PRODUCT.md`](PRODUCT.md)

**最后更新**：2026-06-26 · **已实现 41 条 + 稳定性 11 条**

---

## Agent 工作流

1. **读本索引** → 匹配 `F-Px-xxx` / 关键词 → 打开对应**分清单**（勿加载全部分文件）
2. **执行任务** → 遵守分清单中的入口、约定、验收要点
3. **功能落地后** → 更新分清单条目 + 本表一行摘要；同步 Plan todo；必要时提醒更新 PRODUCT.md

路径均相对于 `app/src/main/java/com/owner/mindbody/`。

---

## 全局索引

| ID | 名称 | 分清单 | 一句话摘要 | Plan todo |
|----|------|--------|-----------|-----------|
| F-P0-001 | HR 全量永久保存 | [p0](docs/feature-ledger/p0-storage.md) | save 流程禁止 deleteOlderThan | completeness |
| F-P0-002 | SyncMeta 实体约定 | [p0](docs/feature-ledger/p0-storage.md) | 新实体 @Embedded sync: SyncMeta | sync-meta |
| F-P0-003 | Room 迁移框架 + WAL | [p0](docs/feature-ledger/p0-storage.md) | 禁止 fallbackToDestructiveMigration | migration-framework |
| F-P0-004 | HrSampleBuffer 批量缓冲 | [p0](docs/feature-ledger/p0-storage.md) | 高频 HR 批量写入 + flushAll | batch-buffer |
| F-P0-005 | AppStorage 统一门面 | [p0](docs/feature-ledger/p0-storage.md) | UI 仅经 app.storage 读写 | storage-facade |
| F-P0-006 | SyncManager 同步占位 | [p0](docs/feature-ledger/p0-storage.md) | SyncableDao 契约占位 | sync-reserved |
| F-P1-001 | Polar BLE 扫描/连接/断开 | [p1](docs/feature-ledger/p1-ble-device.md) | 冷启动自动连 + GATT 清理看门狗 | phase1-android-polar |
| F-P1-002 | FTU 首次使用配置 | [p1](docs/feature-ledger/p1-ble-device.md) | Loop 首次向导持久化 | phase1-android-polar |
| F-P1-003 | 实时心率流 + 前台服务 | [p1](docs/feature-ledger/p1-ble-device.md) | KeepAliveCoordinator + FGS | phase1-android-polar |
| F-P1-004 | BLE 连接模式切换 | [p1](docs/feature-ledger/p1-ble-device.md) | PERSISTENT vs ON_DEMAND | phase1-android-polar |
| F-P1-005 | connectForSnapshot 短连接快照 | [p1](docs/feature-ledger/p1-ble-device.md) | 5s HR 快照，30s 超时 | phase1-android-polar |
| F-P1-006 | 心率页 UI 与统计曲线 | [p1](docs/feature-ledger/p1-ble-device.md) | BPM + 今日统计 + 折线 | phase1-android-polar |
| F-P1-007 | 设置页（原设备页） | [p1](docs/feature-ledger/p1-ble-device.md) | 主屏单行入口 + 3 次级设置页 | phase1-android-polar |
| F-P1-008 | DevicePreferences DataStore | [p1](docs/feature-ledger/p1-ble-device.md) | 非 Room 偏好含夜间 BLE 时间 | phase1-android-polar |
| F-P1-009 | 在线传感器流全量落库 | [p1](docs/feature-ledger/p1-ble-device.md) | 皮温/ACC/PPI 在线流 Room | stream-entities |
| F-P1-010 | 设备离线数据同步落库 | [p1](docs/feature-ledger/p1-ble-device.md) | DeviceSyncManager 8 表 | sync-device-manager |
| F-P1-011 | 开发者模式与运行日志 | [p1](docs/feature-ledger/p1-ble-device.md) | 日志/storage/PPI 上传诊断 | phase1-android-polar |
| F-P1-012 | 身心交织多指标曲线 | [p1](docs/feature-ledger/p1-ble-device.md) | HR/皮温/HRV 同图叠加 | mindbody-chart-overlay |
| F-P1-014 | ACC 10 秒桶聚合 | [p1](docs/feature-ledger/p1-ble-device.md) | acc_minute_summary 无 acc_samples | acc-10s-aggregator |
| F-P1-015 | BLE 夜间断联/晨间重连 | [p1](docs/feature-ledger/p1-ble-device.md) | BleSchedulerWorker 链式调度 | ble-nightly-scheduler |
| F-P1-016 | CDM 伴随设备关联保活 | [p1](docs/feature-ledger/p1-ble-device.md) | CompanionDeviceManager OS 保活 | cdm-companion-keepalive |
| F-P2-001 | mood_entries 实体与 Repository | [p2](docs/feature-ledger/p2-mood.md) | SyncMeta + SyncableDao | mood-entity |
| F-P2-002 | ValueEnergyGrid 四象限点选 | [p2](docs/feature-ledger/p2-mood.md) | 已退役，历史只读 | value-energy-grid |
| F-P2-003 | DiaryInput 日记输入 | [p2](docs/feature-ledger/p2-mood.md) | v3.7 列表续号 | diary-input |
| F-P2-004 | 记录页 MoodRecordScreen | [p2](docs/feature-ledger/p2-mood.md) | ActorStage 二分法记录 | mood-record-screen |
| F-P2-005 | 保存时 HR 快照关联 | [p2](docs/feature-ledger/p2-mood.md) | ±2min 或 connectForSnapshot | hr-snapshot |
| F-P2-006 | WorkManager 定时提醒 | [p2](docs/feature-ledger/p2-mood.md) | FSI 强弹窗 BAL 合规 | reminder |
| F-P2-007 | 历史列表 MoodHistoryScreen | [p2](docs/feature-ledger/p2-mood.md) | 列表编辑删除分页 | history-screen |
| F-P2-008 | 底部导航记录/历史页签 | [p2](docs/feature-ledger/p2-mood.md) | mood_record/history 路由 | navigation |
| F-P2-009 | 日记续号与 RecordViewport | [p2](docs/feature-ledger/p2-mood.md) | emotion v3.7 对齐 | wave-a-diary-continue |
| F-P2-010 | 同日序号 dailyEntryIndex | [p2](docs/feature-ledger/p2-mood.md) | 今日第 N 条 | wave-a-daily-index |
| F-P2-011 | 强弹窗 CheckIn + snooze | [p2](docs/feature-ledger/p2-mood.md) | FSI 探查 + 逃避记录 | wave-b-checkin-dialog |
| F-P2-012 | 历史 CoordMiniBadge 增强 | [p2](docs/feature-ledger/p2-mood.md) | 角色/象限 badge 分页 | wave-c-history-polish |
| F-P2-013 | 情绪角色化 UI v4 | [p2](docs/feature-ledger/p2-mood.md) | 4 人舞台 + 9 人探查 | emotion-ui-v4 |
| F-P2-014 | 日时间轴「时间」Tab | [p2](docs/feature-ledger/p2-mood.md) | 训练/心情/焦虑反馈纵向轴 | timeline-screen |
| F-P3-001 | SyncManager ts 修复 | [p3](docs/feature-ledger/p3-sync-cloud.md) | ActivityDay/Sleep ts 非 0 | fix-ts-zero |
| F-P3-002 | DeviceScreen 同步状态 UI | [p3](docs/feature-ledger/p3-sync-cloud.md) | DeviceSyncStatusRow | add-devicesync-ui |
| F-P3-003 | 睡眠拉取与云端上传修复 | [p3](docs/feature-ledger/p3-sync-cloud.md) | 3 天滚动重拉 + 合并 upsert | sleep-upload-fix |
| F-P3-004 | 实时 PPI 推流管道 | [p3](docs/feature-ledger/p3-sync-cloud.md) | 90s 推流 + 15min 兜底 | p1a~p1e |
| F-P2-UI-001 | UI 全盘重构（日记本） | [p4](docs/feature-ledger/p4-ui-physio-push.md) | Hero/Narrative/MicroGrid + 5 Tab | ui-redesign-all |
| F-P5-001 | ntfy 推送通知 | [p4](docs/feature-ledger/p4-ui-physio-push.md) | ntfy WebSocket + 本地告警 | p5a~p5b |

### 稳定性修复

| ID | 名称 | 分清单 | 一句话摘要 |
|----|------|--------|-----------|
| F-BUG-001 | CoroutineScope 泄漏 | [bugs](docs/feature-ledger/stability-fixes.md) | shutdown 增加 scope.cancel |
| F-BUG-002 | Application 启动错误处理 | [bugs](docs/feature-ledger/stability-fixes.md) | onCreate try-catch + Toast |
| F-BUG-003 | 传感器时间戳 SDK 时间 | [bugs](docs/feature-ledger/stability-fixes.md) | PPI Polar epoch + setLocalTime |
| F-BUG-004 | SnoozeReceiver 超时 | [bugs](docs/feature-ledger/stability-fixes.md) | withTimeout 10s |
| F-BUG-005 | connectForSnapshot 异常分类 | [bugs](docs/feature-ledger/stability-fixes.md) | Timeout/CE/Exception 分离 |
| F-BUG-006 | AppLogBuffer 锁优化 | [bugs](docs/feature-ledger/stability-fixes.md) | 锁外更新 StateFlow |
| F-BUG-007 | !! 强制解包替换 | [bugs](docs/feature-ledger/stability-fixes.md) | getValue 替代 !! |
| F-BUG-008 | ReplaceWith 修正 | [bugs](docs/feature-ledger/stability-fixes.md) | ValueEnergyGrid Deprecated |
| F-BUG-009 | 自动连卡死 | [bugs](docs/feature-ledger/stability-fixes.md) | GATT 清理 + 25s 看门狗 |
| F-BUG-010 | 主线程网络/误停 FGS | [bugs](docs/feature-ledger/stability-fixes.md) | IO 轮询 + 条件 stop FGS |
| F-BUG-011 | CE 误处理 | [bugs](docs/feature-ledger/stability-fixes.md) | 在线流/scheduleSync 重抛 CE |

---

## 变更记录

- 2026-06-26：F-P1-007 设置次级页 — 采集策略/心情提醒/后台保活迁入独立界面，主屏仅保留单行入口
- 2026-06-26：F-P1-006 心率页头部精简（去页眉/角标、统一 BLE 四档文案、Hero 下电量行）
- 2026-06-26：F-P2-UI-001 状态页指标区拆分 + HRV 五档动态色
- 2026-06-26：F-P2-UI-001 传感器 Tab 合并至状态页；底部导航 5 Tab
- 2026-06-26：清单分层重构（总索引 + 6 分文件）；PPI 推流重编号 F-P3-004
