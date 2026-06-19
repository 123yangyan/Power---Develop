# Changelog

## v0.4.0 (2026-06-20)

Phase 2 收官 + Phase 3 初始集成

## 亮点
- F-P1-012：身心交织多指标曲线 — HR/体温/HRV 三色叠加、RMSSD 觉察指标、左右拖动 scrubber、1h/6h/24h 预设
- F-P1-014：ACC 10 秒桶聚合（avg/max magnitude）；移除原始 acc_samples 全链路；Room v8 迁移
- F-P2-013：情绪角色化 UI v4 — 主记录 4 人 ActorStage 场景 A/B + 探查窗 9 皮克斯 3×3 抽屉
- F-P2-006：WorkManager 提醒 FullScreenIntent BAL 合规 + 一次性精确重排；锁屏穿透探查；snooze 20min 逃避记录
- F-P3-001：SyncManager ActivityDay/NightlyRecharge ts=0 修复，云端步数/睡眠时间线恢复正常
- F-P3-002：DeviceScreen 实时展示设备离线同步状态（IDLE → SYNCING → SUCCESS / FAILED）
- F-BUG-001~008：全量稳定性修复 — CoroutineScope 泄漏、!! 强解包、SnoozeReceiver 超时保护、ANR、AppLogBuffer 锁优化
- 僵尸 Worker 防护：SyncWorker/PruneDataWorker 2 分钟超时回收 + 异常主动报告失败

## 变更范围
- mindbody-android：MindBodySplineChart、AccRepository（10s聚合）、EmotionRole/ActorStage（v4 UI）、MoodReminderDeliver/Scheduler（BAL+精确排）、SyncManager（ts修复）、DeviceViewModel/DeviceScreen（同步状态UI）、8 项稳定性 BUG 修复、SyncWorker/PruneDataWorker 超时保护
- Room schema：v6（roleId） → v7（acc_minute_summary） → v8（删 acc_samples）

## 验收
- 心率页可见 HR/体温/HRV 三色曲线与运动色带；触摸/拖动显示时间点数值
- 记录页场景 A 2×2 首发角色；场景 B 胶囊贴键盘 + 句尾微型标签
- 提醒后台无 BAL blocked logcat；锁屏亮屏弹探查窗
- 云端 activity_day_summary / nightly_recharge ts 为正确 UTC 毫秒
- 设备连接后卡片展示「正在拉取」→「已同步」状态

## 已知限制
- Phase 3 云端融合 Pipeline（HR+情绪融合、每日调度）待下一里程碑
- Phase 4 仪表页、7 日历史尚未开发

All notable minor releases (via /release). Patch pushes are tagged but not listed here.

## v0.3.0 (2026-06-13)

Phase 2 收官：BLE 自动连与开发者运行日志

## 亮点
- F-P1-001：冷启动自动扫描并连接已保存 Polar 手环；修复 blePowerStateChanged 与 AutoConnectEffect 双入口协程 cancel 导致自动连失败
- F-P1-011：开发者模式（设备页连点版本号 7 次解锁）+ 运行日志页（复制全部 / 清空 / 自动滚底）
- 修复运行日志 LazyColumn 重复 key 导致连接后进入日志页闪退

## 变更范围
- mindbody-android：PolarBleManager、AutoConnectEffect、AppLogger/AppLogBuffer、DeveloperLogScreen、DeviceScreen、DeviceViewModel

## 验收
- 有已保存设备时冷启动约 15s 内自动连上
- 开发者日志页在 BLE 连接后不再闪退
- 运行日志可复制全部文本

## 已知限制
- Phase 3 云端同步尚未开始
- FTU check 偶发 null 待后续排查

