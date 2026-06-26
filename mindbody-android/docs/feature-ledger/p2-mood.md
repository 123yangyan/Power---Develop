# P2 — 心情记录

路径均相对于 `app/src/main/java/com/owner/mindbody/`。返回 [`FEATURE-LEDGER.md`](../../FEATURE-LEDGER.md)。

---

### F-P2-001 mood_entries 实体与 Repository
Plan: mood-entity · 更新: 2026-06-14

- **目的** 本地持久化心情记录，为 Phase 3 同步就绪。
- **入口** `AppStorage.mood` → `MoodRepository`
- **文件** `MoodEntryEntity.kt`、`MoodEntryDao.kt`、`MoodRepository.kt`；`AppDatabase` v6 + `MIGRATION_4_5`/`5_6`（roleId）
- **约定** `@Embedded sync: SyncMeta`；DAO 实现 `SyncableDao`；经 `app.storage.mood` 读写。
- **验收** `getUnsynced()` 返回 PENDING；迁移不丢 v4 数据。

> 2026-06-14 roleId v6 (#emotion-ui-v4) · 2026-06-13 mood_entries (#mood-entity)

---

### F-P2-002 ValueEnergyGrid 四象限点选
Plan: value-energy-grid · 更新: 2026-06-14

- **目的** emotion 价值感×耗能坐标点选（**记录主路径已退役**）。
- **文件** `ui/mood/ValueEnergyGrid.kt`（`@Deprecated`）、`MoodQuadrant.kt`
- **约定** 新记录用 `ActorStage`/`EmotionRole`；历史无 `roleId` 仍用 `CoordMiniBadge` 只读展示。
- **验收** 记录页不再出现四象限网格。

> 2026-06-14 主路径退役 (#binary-visual-refactor) · 2026-06-13 Compose 网格 (#value-energy-grid)

---

### F-P2-003 DiaryInput 日记输入
Plan: diary-input · 更新: 2026-06-15

- **目的** 多行日记输入 + v3.7 Enter 列表续号。
- **文件** `ui/mood/DiaryInput.kt`、`DiaryListContinue.kt`
- **验收** 列表 Enter 续号；场景 B fillHeight 内部滚动；中文 IME 不重复提交。

> 2026-06-15 TextFieldValue 稳定 (#fix-diary-input) · 2026-06-13 DiaryInput (#diary-input)

---

### F-P2-004 记录页 MoodRecordScreen
Plan: mood-record-screen · 更新: 2026-06-14

- **目的** 二分法记录页 — 场景 A `ActorStage` 4 人极速指认 / 场景 B 键盘沉浸倾诉。
- **入口** 底部导航「记录」→ `MoodRecordScreen`；提醒设置 → 设备页 `DeviceMoodReminderSection`
- **文件** `MoodRecordScreen.kt`、`MoodRecordViewport.kt`、`ActorStage.kt`、`EmotionCapsuleToolbar.kt`、`DiaryInput.kt`、`MoodRecordViewModel.kt`
- **约定** 场景 A 无日记 → `quickCaptureRole` 一键保存；场景 B 延迟聚焦，禁止 DiaryInput 挂载前 requestFocus。
- **验收** 默认 2×2 舞台；「想倾诉更多」不闪退；键盘可见时隐藏免责声明。

> 2026-06-14 二分法视觉重构 (#binary-visual-refactor) · 2026-06-13 记录页 (#mood-record-screen)

---

### F-P2-005 保存时 HR 快照关联
Plan: hr-snapshot · 更新: 2026-06-15

- **目的** 记录时刻关联本地 HR 估计值。
- **文件** `MoodRecordViewModel.resolveHrSnapshot()`；`HrRepository.getHrNearTimestamp`、`PolarBleManager.connectForSnapshot`
- **约定** 常连接查 ±2min 均值；短连接无样本时 `connectForSnapshot`；无手环时 `hr_at_entry` 为 null。
- **验收** 历史页「估计关联」标注；非医疗诊断文案。

> 2026-06-15 缩小本地窗口 (#fix-hr-snapshot) · 2026-06-13 HR 快照 (#hr-snapshot)

---

### F-P2-006 WorkManager 定时提醒
Plan: reminder · 更新: 2026-06-15

- **目的** 替代 emotion daily-checkin-service，定时提醒记录。
- **文件** `worker/MoodReminderWorker.kt`、`MoodReminderDeliver.kt`、`MoodReminderScheduler.kt`；`MoodCheckInActivity.kt`；`MoodPreferences.kt`
- **约定** 间隔 1–1440 分钟；`mood_reminder_exact` 精确重排；**strongPopup 仅经 FullScreenIntent**（禁止 Worker 内 startActivity）；Esc/稍后写逃避记录+20min snooze。
- **验收** 无 BAL 拦截 logcat；锁屏 FSI 亮屏；设备页可提示全屏通知权限。

> 2026-06-15 精确重排 (#fix-reminder-timing) · 2026-06-14 BAL/FSI 合规 (#bal-fix) · 2026-06-13 WorkManager (#reminder)

---

### F-P2-007 历史列表 MoodHistoryScreen
Plan: history-screen · 更新: 2026-06-15

- **目的** 列表、预览、编辑、删除。
- **入口** 记录 Tab →「历史记录」分段 → `MoodHistoryContent`
- **文件** `MoodHistoryScreen.kt`（`MoodHistoryContent`）、`MoodHistoryRowBuilder.kt`；`MoodRecordScreen.kt` 分段切换
- **约定** 极性/逃避卡片；CoordMiniBadge/RoleMiniBadge；页码+跳转；同日 `(i/total)`；长日记 3 行可展开。
- **验收** 保存后在历史可见；编辑/删除生效。

> 2026-06-15 卡片层级优化 (#optimize-history-ui) · 2026-06-13 历史页 (#history-screen)

---

### F-P2-008 底部导航记录/历史页签
Plan: navigation · 更新: 2026-06-14

- **目的** 主导航整合记录与历史。
- **文件** `ui/navigation/AppNavigation.kt`；`ui/components/FloatingIslandNav.kt`（`mood_record`/`mood_history`）
- **约定** 通知 deep link `EXTRA_NAVIGATE_TO=mood_record`；记录页键盘可见时 NavHost 底栏 padding 降为 0。
- **验收** 五页签可切换；FTU 页隐藏底栏。

> 2026-06-14 IME padding 修复 (#keyboard-crash-fix) · 2026-06-13 导航扩展 (#navigation)

---

### F-P2-009 emotion v3.7 日记续号与 RecordViewport
Plan: wave-a-diary-continue · 更新: 2026-06-13

- **目的** 对齐 emotion v3.7 记录页布局与日记交互。
- **文件** `DiaryListContinue.kt`、`MoodRecordViewport.kt`、`MoodSettingsSection.kt`
- **验收** Enter 列表续号；RecordViewport 信息架构（日期/序号/坐标/日记/保存）。

> 2026-06-13 v3.7 记录 UX (#wave-a-diary-continue)

---

### F-P2-010 同日序号 dailyEntryIndex
Plan: wave-a-daily-index · 更新: 2026-06-13

- **目的** 记录页「今日第 N 条」与历史 `(i/total)`。
- **文件** `DailyEntryIndex.kt`；`MoodRecordViewModel`、`MoodHistoryViewModel`
- **验收** 按本地日历日分组序号正确。

> 2026-06-13 dailyEntryIndex (#wave-a-daily-index)

---

### F-P2-011 强弹窗 CheckIn + snooze/逃避记录
Plan: wave-b-checkin-dialog · 更新: 2026-06-14

- **目的** 双通道提醒；PRD v4 场景 A 半屏 Bottom Sheet 极速盲操。
- **入口** `MoodReminderDeliver`（FSI）→ `MoodCheckInActivity`；弱通知 → `MainActivity`
- **文件** `MoodCheckInActivity.kt`、`MobileCheckInDrawer.kt`、`PriorityDock.kt`、`MoodCheckInConstants.kt`、`MoodRecordViewModel.quickCaptureRole()`、`MoodReminderDeliver.kt`、`MoodReminderSnoozeReceiver.kt`
- **约定** strongPopup 仅 FSI 拉起 Activity；`EXTRA_SOFT_DISMISS`；API 34+ `canUseFullScreenIntent` 检测。
- **验收** 无 BAL logcat；锁屏自动弹 Sheet。

> 2026-06-14 BAL/锁屏探查 (#bal-fix) · 2026-06-13 CheckIn + snooze (#wave-b-checkin-dialog)

---

### F-P2-012 历史 CoordMiniBadge 与分页增强
Plan: wave-c-history-polish · 更新: 2026-06-15

- **目的** 历史列表视觉与分页对齐 emotion v3.7；支持角色图标。
- **文件** `RoleMiniBadge.kt`、`CoordMiniBadge.kt`、`MoodHistoryRowBuilder.kt`；`MoodHistoryScreen` pager
- **验收** 有 roleId 显示角色微缩图；无 roleId 回退象限 badge；页码跳转。

> 2026-06-15 RoleMiniBadge 可配置尺寸 (#optimize-history-ui) · 2026-06-13 历史 polish (#wave-c-history-polish)

---

### F-P2-013 情绪角色化 UI v4（探查抽屉 + 沉浸记录）
Plan: emotion-ui-v4 · 更新: 2026-06-14

- **目的** 18 角色二分法 UI — 主动记录场景 A/B + 探查窗 9 皮克斯。
- **入口** 提醒 → `MoodCheckInActivity`+`MobileCheckInDrawer`（9 人 3×3）；主动记录 → `MoodRecordScreen`（4 人 ActorStage）
- **文件** `ActorStage.kt`、`EmotionRole.kt`、`EmotionCapsuleToolbar.kt`、`DiaryInput.kt`、`MobileCheckInDrawer.kt`、`MoodRecordViewport.kt`
- **约定** 记录页首发 4 人；探查窗 9 皮克斯；coord 静默写入不变。
- **验收** 场景 A 2×2+展开 18 人；场景 B 胶囊贴键盘；Android 14/15 键盘无 Insets 崩溃。

> 2026-06-14 PRD v4.0 情绪角色化 (#emotion-ui-v4)

---

### F-P2-014 日时间线 Timeline
Plan: timeline-screen · 更新: 2026-06-26

- **目的** 以纵向时间轴展示单日里程碑：训练区间、心情记录、AI 身心反馈；日汇总含步数、平均心率、焦虑事件次数。
- **入口** 记录 Tab →「历史记录」分段 · 底部 Tab「时间」→ `TimelineScreen`
- **文件** `ui/timeline/TimelineViewModel.kt`、`TimelineScreen.kt`；`AppNavigation.kt`；`FloatingIslandNav.kt`；`MoodRecordScreen.kt`（含历史分段）
- **约定** 数据经 `AppStorage` 聚合（training / mood / feedbackHistory / activityMinute / hr247 / hr）；睡眠暂显示 `—`；不新增 Room 实体。
- **验收** 周历切换日期；事件按时间升序；训练卡展示时长与区间 HR；心情卡展示角色与 HR 芯片；反馈卡展示焦虑指数与 LLM 摘要。

> 2026-06-26 首版时间线 (#timeline-screen)
