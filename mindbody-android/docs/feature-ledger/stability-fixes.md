# 稳定性修复

路径均相对于 `app/src/main/java/com/owner/mindbody/`。返回 [`FEATURE-LEDGER.md`](../../FEATURE-LEDGER.md)。

基于 PROJECT-ANALYSIS-REPORT 与用户反馈。条目格式从简，仅保留问题要点与修复文件。

---

### F-BUG-001 CoroutineScope 生命周期泄漏
来源: PROJECT-ANALYSIS #1-3 · 2026-06-17

- **问题** `HrSampleBuffer`、`EntitySampleBuffer`、`PolarBleManager`、`DeviceSyncManager` 各自创建 IO scope，`shutdown()` 未 cancel。
- **修复** 各 `shutdown()` 增加 `scope.cancel()`；`DeviceSyncManager` 新增 `shutdown()` cancel syncJob。
- **文件** `HrSampleBuffer.kt`、`EntitySampleBuffer.kt`、`PolarBleManager.kt`、`DeviceSyncManager.kt`

---

### F-BUG-002 Application 启动错误处理
来源: PROJECT-ANALYSIS #5a · 2026-06-17

- **问题** `MindBodyApplication.onCreate()` 无 try-catch，Room 失败直接崩溃。
- **修复** try-catch + `AppLogger` + Toast 提示重启。
- **文件** `MindBodyApplication.kt`

---

### F-BUG-003 传感器时间戳使用 SDK 时间
来源: PROJECT-ANALYSIS #4b · 2026-06-20

- **问题** HR/皮温/ACC 同批次 ts 不一致；PPI 未用 Polar epoch。
- **修复** 批次级 `now`；PPI `polarSensorTimeToUnixMs()` + `setLocalTime()` 校时；timeStamp==0 fallback WARN。
- **文件** `PolarBleManager.kt`

---

### F-BUG-004 SnoozeReceiver 结构化并发
来源: PROJECT-ANALYSIS #3b · 2026-06-17

- **问题** `MoodReminderSnoozeReceiver` 无超时，进程 kill 前逃避记录可能丢失。
- **修复** `withTimeout(10_000L)` 包裹异步逻辑。
- **文件** `MoodReminderSnoozeReceiver.kt`

---

### F-BUG-005 connectForSnapshot 异常分类
来源: PROJECT-ANALYSIS #5d · 2026-06-17

- **问题** 外层 `catch (Exception)` 吞噬 Timeout/Cancellation。
- **修复** 分离 TimeoutCancellationException / CancellationException(rethrow) / Exception。
- **文件** `PolarBleManager.kt`

---

### F-BUG-006 AppLogBuffer 锁内 StateFlow 更新优化
来源: PROJECT-ANALYSIS #6b · 2026-06-17

- **问题** `synchronized` 内 `deque.toList()` 阻塞并发 append。
- **修复** 锁内拷贝快照，锁外更新 StateFlow。
- **文件** `AppLogBuffer.kt`

---

### F-BUG-007 !! 强制解包替换
来源: PROJECT-ANALYSIS #4a,#12b · 2026-06-17

- **问题** `AccRepository`、`CoordMiniBadge` 多处 `!!`。
- **修复** 局部变量缓存；`Map.getValue(key)` 替代 `Map[key]!!`。
- **文件** `AccRepository.kt`、`CoordMiniBadge.kt`

---

### F-BUG-008 @Deprecated ReplaceWith 修正
来源: PROJECT-ANALYSIS #11a · 2026-06-17

- **问题** `ValueEnergyGrid` ReplaceWith 缺 `ActorStage` 参数。
- **修复** 完整 lambda 调用表达式。
- **文件** `ValueEnergyGrid.kt`

---

### F-BUG-009 进程异常退出后自动连卡死
来源: 用户反馈 · 2026-06-23

- **问题** 杀进程后 GATT 残留，UI 永久「正在自动连接…」。
- **修复** 直连前 `disconnectFromDevice`+1s；25s 看门狗未 CONNECTED 则重排 reconnect。
- **文件** `PolarBleManager.kt`（`tryAutoConnectInternal`）

---

### F-BUG-010 状态页主线程网络 + 切 Tab 误停前台服务
来源: 用户反馈 HyperOS 闪退 · 2026-06-25

- **问题** 主线程 OkHttp；切 Tab/`deviceDisconnected` 无条件 stop FGS。
- **修复** 轮询 `withContext(IO)`；仅非 CONNECTED 或非 userInitiatedDisconnect 时 stop；WakeLock try/catch。
- **文件** `PhysioStateViewModel.kt`、`HeartRateViewModel.kt`、`PolarBleManager.kt`、`HrStreamService.kt`

---

### F-BUG-011 BLE 断连时 CancellationException 误处理
来源: 用户反馈 · 2026-06-26

- **问题** 在线流 `.catch` 将 SDK Channel 关闭记 ERROR；`scheduleSync()` runCatching 误报 FAILED。
- **修复** `logOnlineStreamError()` WARN/ERROR 分级；显式重抛 Kotlin CE；scheduleSync try/catch 重抛 CE。
- **文件** `PolarBleManager.kt`、`DeviceSyncManager.kt`

---

### F-BUG-012 意外断联后自动重连静默失败
来源: 用户反馈 · 2026-06-28

- **问题** `deviceDisconnected` 停 FGS 后 `scheduleReconnect` 无 WakeLock，SDK GATT 握手被系统挂起，`deviceConnected` 回调丢失，需用户进心率页才重连。
- **修复** `scheduleReconnect` 在 `connectToDevice` 前 `KeepAliveCoordinator.start(RECONNECTING)`；FGS 心跳连续 2 次 `DISCONNECTED` 时 `reconnectNowIfIdle()` 兜底。
- **文件** `PolarBleManager.kt`、`HrStreamService.kt`、`KeepAliveReason.kt`

---
