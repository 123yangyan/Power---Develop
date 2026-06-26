# P4 — UI 重构 · 生理状态 · 推送

路径均相对于 `app/src/main/java/com/owner/mindbody/`。返回 [`FEATURE-LEDGER.md`](../../FEATURE-LEDGER.md)。

---

### F-P2-UI-001 UI 全盘重构：日记本设计哲学
Plan: ui-redesign-all · 更新: 2026-06-26

- **目的** 「工业监控器」→「文艺身心日记本」：结论先行、留白无硬边框、色彩=生理状态。
- **Design Tokens** `ui/theme/Color.kt`（AppBackground/6档状态色）、`Shape.kt`（NarrativeCard 32dp/DataCard 24dp）、`Theme.kt`
- **标准组件** `HeroIndicator.kt`（呼吸动画）、`NarrativeCard.kt`、`MicroGrid.kt`（2×2/2×3 Sparkline）
- **状态 Tab** `PhysioStateScreen.kt`（Hero+叙事卡+HRV 6格）；`PhysioStateViewModel.kt`（30s 轮询 `/api/vitals/stream/status`→`AppStorage`）；`FeedbackHistoryListScreen.kt`
- **页面重构** `HeartRateScreen.kt`、`SensorsScreen.kt`、`DeviceScreen.kt`（`StreamAnalysisCard`）；6 Tab `FloatingIslandNav.kt`；路由 `physio_state`/`feedback_history`
- **门面** `AppStorage.kt`：`latestPhysioState`、`feedbackHistory` Flow + update 方法
- **约定** `PhysioStateViewModel.startPolling()`/`stopPolling()` 由 Screen `DisposableEffect` 管理。
- **验收** 6 Tab；状态页三区域；心率 Hero 呼吸动画；设备页 BLE 连接后推流状态卡。

> 2026-06-26 状态 Tab 历史条形卡 (#F-P2-UI-001) · 2026-06-20 全盘 UI 重构 (#ui-redesign-all)

---

### F-P5-001 ntfy 推送通知
Plan: p5a~p5b（实时生理状态检测系统） · 更新: 2026-06-26

- **目的** 生理状态告警推送（原 FCM/MQTT 已替换为 ntfy）；本地通知含记录/稍后/今天不再三按钮。
- **ntfy 路径** 服务端 `push_service.py` POST `https://ntfy.sh/{prefix}-{device_id}`；手机 ntfy F-Droid WebSocket 模式订阅 Device 页 Topic
- **文件** `notification/PhysioNotificationManager.kt`、`PhysioNotificationReceiver.kt`、`polar/HrStreamService.kt`、`SyncApiClient.kt`（`reportNotificationResponse`）、`SyncPreferences.kt`、`DeviceViewModel.kt`（`ntfyTopic`）、`DeviceScreen.kt`、`PhysioStateViewModel.kt`（`maybeNotifyAlert`）；服务端 `push_service.py`、`config.py`
- **约定** `ensureChannel()` 于 `MindBodyApplication.onCreate()`；告警态 `elevated`/`anxious`/`high_anxiety` 且标签变化时本地 `show()`；Deep Link `EXTRA_NAVIGATE_TO`。
- **验收** ntfy 订阅后 curl 测试可达；通知按钮跳转正确；SNOOZE/DISMISS 回报服务端。

**激活前置（用户操作）**：安装 ntfy F-Droid → WebSocket 模式 → 电池无限制 → 订阅 Device 页 Topic → ECS `NTFY_SERVER`/`NTFY_TOPIC_PREFIX` 配置。

> 2026-06-26 本地 show() + Deep Link 修复 (#F-P5-001) · 2026-06-23 MQTT→ntfy (#F-P5-001) · 2026-06-21 实时生理状态 Phase 5 (#实时生理状态检测系统)
