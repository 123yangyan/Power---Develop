---
name: Phase2心情记录移植
overview: 以 emotion **v3.7.0**（目录 emotion-2.1.0，package 版本 3.7.0）为真相源，将价值感×耗能坐标、日记输入、定时提醒与历史列表移植为 Jetpack Compose；经 AppStorage 接入 mood_entries，记录时刻关联本地 HR 快照。Wave 1 核心闭环已完成；Wave 2 对齐 v3.7 UX 见子 Plan phase2_对齐_emotion_v3.7。
todos:
  - id: mood-entity
    content: 新建 MoodEntryEntity + MoodEntryDao + MIGRATION_4_5（v4→v5），AppStorage 暴露 mood Repository
    status: completed
  - id: value-energy-grid
    content: 移植 ValueEnergyGrid 四象限点选为 Compose 组件（coord_x/coord_y -4~+4）
    status: completed
  - id: diary-input
    content: 移植 DiaryInput 日记输入组件（含 v3.7 Enter 列表续号）
    status: completed
  - id: mood-record-screen
    content: 记录页 MoodRecordViewport 组装（坐标+日记+提交），接入 ViewModel 经 app.storage.mood
    status: completed
  - id: hr-snapshot
    content: 保存 mood 时关联 HR 快照（查询 ±5min 样本均值，或短连接模式调用 connectForSnapshot）
    status: completed
  - id: reminder
    content: WorkManager 定时提醒 + strongPopup/snooze/逃避记录（对齐 daily-checkin-service）
    status: completed
  - id: history-screen
    content: 历史列表 EntryHistoryPage 移植（CoordMiniBadge、极性、分页跳转、daily index）
    status: completed
  - id: navigation
    content: 底部导航增加「记录」「历史」页签，与现有心率/设备页整合
    status: completed
  - id: wave-a-diary-continue
    content: Wave A1：diaryListContinue + DiaryInput Enter 续号
    status: completed
  - id: wave-a-daily-index
    content: Wave A2：dailyEntryIndex 记录页/历史页同日序号
    status: completed
  - id: wave-a-record-viewport
    content: Wave A3：MoodRecordViewport 布局
    status: completed
  - id: wave-a-settings-ui
    content: Wave A4：静默时段/strongPopup 可编辑 UI
    status: completed
  - id: wave-b-checkin-dialog
    content: Wave B1：MoodCheckInDialog + strongPopup 双通道
    status: completed
  - id: wave-b-snooze-avoidance
    content: Wave B2：Esc snooze + 逃避记录 + 20min 短间隔
    status: completed
  - id: wave-c-history-polish
    content: Wave C：CoordMiniBadge、极性/逃避样式、分页跳转
    status: completed
isProject: false
---

# Phase 2 — 心情记录移植

## 优先级与依赖

| 项 | 说明 |
|----|------|
| **优先级** | P2（见 [project-priority.mdc](../rules/project-priority.mdc)） |
| **真相源** | emotion **v3.7.0**（[`CHANGELOG.md`](../../emotion-2.1.0/emotion-2.1.0/CHANGELOG.md)；目录名 `emotion-2.1.0` 与 package 版本不一致） |
| **前置** | P0 统一存储核心 ✅、P1 BLE/心率 ✅ |
| **阻塞** | Phase 3 云端同步需本 Phase 的 `mood_entries` 本地实体就绪 |
| **父 Plan** | [loop心情ai产品规划_9d502bd3.plan.md](./loop心情ai产品规划_9d502bd3.plan.md) |
| **对齐子 Plan** | [phase2_对齐_emotion_v3.7_95d52584.plan.md](./phase2_对齐_emotion_v3.7_95d52584.plan.md) |

---

## 设计目标

- 在 Android App 内完成 emotion **v3.7 记录/历史/提醒 UX**，不依赖 Windows Electron
- 所有数据读写经 **`AppStorage`** 门面，遵循存储核心 4 步接入约定
- 每条心情记录可附带 **记录时刻 HR 估计值**（MindBody 扩展，emotion 无此列）

**不在 Phase 2 范围**：Dashboard 仪表、AI 洞察、JSON 备份、18:00 疲劳弹窗（v3.7 已移除）、legacy 字段 Room 列（Phase 3 导入再议）。

---

## 数据模型（Room）

`MoodEntryEntity`（对齐 emotion v3.7 **新记录写入子集**）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long PK | 自增 |
| `fact` | String | 日记正文；逃避记录为 `逃避记录` |
| `coord_x` | Int | 价值感 -4~+4 |
| `coord_y` | Int | 耗能度 -4~+4 |
| `occurred_at` | Long | 记录发生时间戳 |
| `hr_at_entry` | Int? | 关联 HR 快照（MindBody 扩展） |
| `sync` | SyncMeta | `@Embedded`，沿用 P0 约定 |

v3.7 新记录不写 `thought/body_tags/fatigue_check`；逃避记录识别：`fact==逃避记录 && coord(0,0)`。

**接入步骤（存储 4 步）：**
1. `MoodEntryEntity` + `SyncMeta`
2. `MoodEntryDao` implements `SyncableDao`
3. `AppDatabase` **v4→v5** + `MIGRATION_4_5`
4. `AppStorage.mood: MoodRepository`

---

## UI 移植对照（v3.7）

| emotion 源文件 | Android 目标 | 状态 |
|----------------|--------------|------|
| `ValueEnergyGrid.tsx` | `ui/mood/ValueEnergyGrid.kt` | ✅ |
| `DiaryInput.tsx` + `diaryListContinue.ts` | `DiaryInput.kt` + `DiaryListContinue.kt` | ✅ |
| `RecordViewportForm.tsx` | `ui/mood/MoodRecordViewport.kt` | ✅ |
| `MoodRecordForm.tsx` | `MoodRecordScreen.kt` + `MoodRecordViewModel.kt` | ✅ |
| `dailyEntryIndex.ts` | `ui/mood/DailyEntryIndex.kt` | ✅ |
| `EntryHistoryPage.tsx` + `historyRowPreview.ts` | `MoodHistoryScreen.kt` + `MoodHistoryRowBuilder.kt` | ✅ |
| `CoordMiniBadge.tsx` | `ui/mood/CoordMiniBadge.kt` | ✅ |
| `CheckInPanel.tsx` + `daily-checkin-service.ts` | `MoodCheckInActivity` + `MoodReminderWorker` | ✅ |
| `SettingsPage.tsx`（提醒节） | `MoodSettingsSection.kt` | ✅ |

参考路径：[emotion-2.1.0/emotion-2.1.0/src/renderer/src/](../../emotion-2.1.0/emotion-2.1.0/src/renderer/src/)

---

## HR 快照关联规则（MindBody 扩展）

1. 常连接：`storage.hr` 查询 `occurred_at ± 5 分钟` BPM 均值
2. 短连接：无样本时 `connectForSnapshot`
3. UI 标注「估计关联」，非医疗诊断

---

## 定时提醒（对齐 v3.7 daily-checkin-service）

```
shouldPrompt → 静默? → 距 lastReminderAt < effectiveInterval? → 跳过
effectiveInterval = 今日 snoozeCount>0 ? 20min : reminderIntervalMinutes
deliver = 通知 + (strongPopup ? CheckInActivity : 仅通知 deep link)
保存成功 → lastReminderAt=now（仍继续间隔提醒）
Esc/稍后 → 写逃避记录 + snoozeCount++ → 20min 短间隔
```

- WorkManager 15min 轮询；间隔逻辑 1–1440 分钟（DataStore）
- 设置：间隔、静默时段、通知开关、strongPopup（DataStore）

---

## 平台差异

| emotion | Android |
|---------|---------|
| 760×620 置顶窗 | `MoodCheckInActivity` 全屏 |
| setInterval 10–60s | WorkManager 15min + Worker 内判断 |
| 北京时间 | 系统时区 + 本地日历日序号 |
| HR 快照 | MindBody 独有 |

---

## 验收标准

1. 真机完成「点选 → 日记（Enter 续号）→ 保存 → 历史查看/编辑」
2. `hr_at_entry` 有值或明确为空
3. 提醒：静默不弹；strongPopup 可关；Esc 写逃避记录；20min snooze
4. 记录页/历史页显示同日序号
5. 所有 mood 读写经 `app.storage.mood`
6. `getUnsynced()` 为 Phase 3 就绪
