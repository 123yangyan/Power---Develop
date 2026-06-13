# 定时录音助手 — 代码生成指南（codegen-guide）

> 本文档配合 `prd.md` 使用，指导 AI 或开发者按 **Now in Android（NIA）简化范式** 分阶段生成代码。  
> 架构参考：[Now in Android](https://github.com/android/nowinandroid)（学分层与工程规范，不抄业务与全量模块）。

---

## 1. 生成原则

| 原则 | 说明 |
|------|------|
| PRD 是需求真相 | 功能、字段、接口以 `prd.md` 为准 |
| NIA 是写法参考 | Repository + Flow、ViewModel + UiState、Hilt、Room、WorkManager |
| 简化模块化 | 不做 `feature:api/impl` 双模块；V1.0 单 `:feature:*` 即可 |
| 分阶段生成 | 先数据层 → 后台任务 → UI；避免一次生成全项目 |
| 固定技术栈 | minSdk 29 / compileSdk 34 / JDK 21 / Compose / Hilt |

**禁止生成（V1.0）：** NIA 的 changelist 同步、demo/prod flavor、Firebase、Navigation3 复杂自适应、账号登录体系。

---

## 2. 模块树

```text
timed-recorder/                          # 项目根（本仓库 app-main）
├── app/                                 # Application、导航壳、Manifest、DI 入口
├── core/
│   ├── model/                           # 纯 Kotlin 模型（表实体、DTO、Ui 无关类型）
│   ├── common/                          # Dispatchers、Result、扩展函数、常量
│   ├── database/                        # Room：Dao、Entity、Database、Migration
│   ├── datastore/                       # DataStore：Base URL、API Key、轮询参数、deviceId
│   ├── network/                         # Retrofit：AudioApi、DTO、OkHttp 拦截器
│   ├── data/                            # Repository 接口与实现、Mapper
│   └── designsystem/                    # Theme、通用 Compose 组件、图标
├── feature/
│   ├── home/                            # PRD 9.1 首页 / 控制台
│   ├── schedule/                        # PRD 9.2 录音任务列表 + 新增/编辑
│   ├── files/                           # PRD 9.7 本地文件 + 9.8 上传状态
│   ├── results/                         # PRD 9.5 云端处理结果
│   ├── messages/                        # PRD 9.6 消息中心 + 系统通知触发
│   └── settings/                        # PRD 9.10 设置 + 9.9 诊断
├── sync/                                # 后台：UploadWorker、PollWorker、CleanupWorker、BootReceiver
├── gradle/libs.versions.toml            # 版本目录（单一真相源）
├── settings.gradle.kts
├── build.gradle.kts
├── prd.md
└── codegen-guide.md                     # 本文件
```

### 2.1 模块依赖关系（箭头 = implementation）

```text
app
 ├── feature:home | schedule | files | results | messages | settings
 ├── sync
 └── core:designsystem

feature:*
 ├── core:data
 ├── core:designsystem
 ├── core:model
 └── core:common

sync
 ├── core:data
 ├── core:common
 └── core:model

core:data
 ├── core:database
 ├── core:datastore
 ├── core:network
 ├── core:model
 └── core:common

core:database / network / datastore → core:model
core:designsystem → core:common（可选）
```

---

## 3. 包名约定

根包名：`com.timedrecorder`

| 模块 | 包路径示例 |
|------|-----------|
| app | `com.timedrecorder` |
| core:model | `com.timedrecorder.core.model` |
| core:database | `com.timedrecorder.core.database` |
| core:data | `com.timedrecorder.core.data.repository` |
| feature:home | `com.timedrecorder.feature.home` |
| sync | `com.timedrecorder.sync` |

---

## 4. 文件生成顺序（共 8 阶段）

按顺序生成；**每一阶段完成后应能编译通过**（允许空实现 / TODO）。

### 阶段 0 — 工程骨架（已完成）

- [x] `settings.gradle.kts`、`build.gradle.kts`、`gradle/libs.versions.toml`
- [x] 各模块 `build.gradle.kts`、Manifest、占位源码
- [x] `app`：`MainActivity`、`RecorderApp` 底部导航、Hilt Application
- [x] `core:designsystem`：`RecorderTheme`
- [x] 各 `feature:*`：占位 Screen（可运行空壳 App）
- [ ] 用 Android Studio 打开并 Sync（首次需联网；若无 `gradlew` 由 Studio 自动生成）

### 阶段 1 — 模型与数据库（PRD §13）

**目标：** Room 可写入/读出，暂无 UI。

| 顺序 | 文件 | 说明 |
|------|------|------|
| 1.1 | `core/model/.../ScheduleTask.kt` | 对应 task_schedule |
| 1.2 | `core/model/.../AudioFile.kt` | 对应 audio_file |
| 1.3 | `core/model/.../ProcessResult.kt` | 对应 process_result |
| 1.4 | `core/model/.../MessageItem.kt` | 对应 message_center |
| 1.5 | `core/model/.../AppLogEntry.kt` | 对应 app_log |
| 1.6 | `core/model/.../UploadStatus.kt` 等 enum | 上传/处理/录音状态 |
| 1.7 | `core/database/.../entity/*Entity.kt` | Room Entity + 与 model 的 Mapper |
| 1.8 | `core/database/.../dao/*Dao.kt` | 五张表的 Dao |
| 1.9 | `core/database/.../RecorderDatabase.kt` | `@Database` + `RoomDatabase` |
| 1.10 | `core/database/.../di/DatabaseModule.kt` | Hilt 提供 Database / Dao |

**验收：** 仪器测试或单元测试插入一条 `ScheduleTask` 并能 Flow 读出。

### 阶段 2 — 设置与网络（PRD §14.0、§14.1–14.2）

| 顺序 | 文件 | 说明 |
|------|------|------|
| 2.1 | `core/datastore/.../UserPreferences.kt` | Base URL、apiKey、轮询间隔/次数、切片时长等 |
| 2.2 | `core/datastore/.../PreferencesDataSource.kt` | DataStore 读写 |
| 2.3 | `core/network/.../AudioApiService.kt` | upload、getResult、batchResult |
| 2.4 | `core/network/.../dto/*Dto.kt` | 与 PRD JSON 一致；字段用 `fileId` 非 `Id` |
| 2.5 | `core/network/.../RetrofitModule.kt` | 动态 Base URL + Bearer 拦截器 |
| 2.6 | `core/common/.../DeviceIdProvider.kt` | 首次启动 UUID 持久化 |

**验收：** 给定 MockWebServer，upload 与 getResult 解析正确。

### 阶段 3 — Repository 层（PRD §9.3–9.5）

| 顺序 | 文件 | 说明 |
|------|------|------|
| 3.1 | `core/data/.../ScheduleRepository.kt` | 任务 CRUD + 重叠校验 |
| 3.2 | `core/data/.../AudioFileRepository.kt` | 文件记录 + `observe*` Flow |
| 3.3 | `core/data/.../UploadRepository.kt` | 入队、重试、更新 upload_status |
| 3.4 | `core/data/.../ResultRepository.kt` | 轮询结果、写入 process_result |
| 3.5 | `core/data/.../MessageRepository.kt` | 消息列表、已读 |
| 3.6 | `core/data/.../LogRepository.kt` | 诊断日志写入 |
| 3.7 | `core/data/.../di/DataModule.kt` | Hilt 绑定接口 → 实现 |

**验收：** Repository 单测：Fake Dao + Fake Api，验证 Flow 发射与状态更新。

### 阶段 4 — 录音与定时（PRD §9.3、§12）— NIA 无，按 PRD 自写

| 顺序 | 文件 | 说明 |
|------|------|------|
| 4.1 | `sync/.../schedule/ScheduleAlarmManager.kt` | AlarmManager 注册开始/结束 |
| 4.2 | `sync/.../record/RecordService.kt` | 前台 Service + MediaRecorder |
| 4.3 | `sync/.../record/AudioSliceWriter.kt` | 按 slice_duration 切片命名 |
| 4.4 | `sync/.../record/RecordingStateHolder.kt` | idle/recording/… 状态 |
| 4.5 | `sync/.../BootReceiver.kt` | 开机恢复闹钟与服务 |
| 4.6 | `sync/.../RecordServiceController.kt` | 对外 start/stop 封装 |

**验收：** 手动触发 Service，生成 m4a 文件并写入 `audio_file` 表。

### 阶段 5 — 后台 Worker（PRD §9.4、§7 轮询）

| 顺序 | 文件 | 说明 |
|------|------|------|
| 5.1 | `sync/.../worker/UploadWorker.kt` | 队列上传，失败重试 ≤3 次 |
| 5.2 | `sync/.../worker/PollResultWorker.kt` | 30s × 10 次轮询 |
| 5.3 | `sync/.../worker/CleanupWorker.kt` | 保留天数清理 |
| 5.4 | `sync/.../worker/WorkScheduler.kt` | 统一 enqueue / 约束（网络） |
| 5.5 | `sync/.../notification/AlertNotifier.kt` | alertFlag / riskLevel / keywords 触发通知 |

**验收：** 断网 → 恢复后自动上传；模拟 result 返回后通知弹出。

### 阶段 6 — UI 基础设施

| 顺序 | 文件 | 说明 |
|------|------|------|
| 6.1 | `core/designsystem/.../Theme.kt` | Material3 + 深色模式 |
| 6.2 | `core/designsystem/.../RecorderTopAppBar.kt` 等 | 通用组件 |
| 6.3 | `app/.../RecorderApp.kt` | Compose 根 + NavHost |
| 6.4 | `app/.../navigation/TopLevelDestination.kt` | 底部 Tab 路由 |
| 6.5 | `app/.../MainActivity.kt` | 单 Activity |

### 阶段 7 — Feature 界面（按 PRD 页面清单 §15）

每个 feature 模块标准结构：

```text
feature/xxx/
├── XxxViewModel.kt          # stateIn + UiState sealed interface
├── XxxScreen.kt             # 纯 Compose，状态上浮
├── XxxUiState.kt
└── navigation/XxxNavigation.kt
```

| 顺序 | 模块 | PRD |
|------|------|-----|
| 7.1 | feature:schedule | §9.2 |
| 7.2 | feature:home | §9.1 |
| 7.3 | feature:files | §9.7、§9.8 |
| 7.4 | feature:results | §9.5 |
| 7.5 | feature:messages | §9.6 |
| 7.6 | feature:settings | §9.10、§9.9 |

**ViewModel 范式（对齐 NIA）：**

```kotlin
// UiState：sealed interface，Loading / Success / Error
// ViewModel：combine(repository.flow) → map → stateIn(WhileSubscribed(5_000))
// 用户操作：fun onRetryUpload(id) { viewModelScope.launch { repo.retry(id) } }
```

### 阶段 8 — 首次启动与验收（PRD §16.1、§18）

| 顺序 | 文件 | 说明 |
|------|------|------|
| 8.1 | `feature:settings/.../OnboardingScreen.kt` | 隐私说明 + 权限 + 白名单引导 |
| 8.2 | `app/.../AndroidManifest.xml` | 权限、Service、Receiver 完整声明 |
| 8.3 | 各模块 `*Test.kt` | 核心 Repository / ViewModel 测试 |

---

## 5. 关键文件清单速查

| PRD 需求 | 主要落地文件 |
|----------|-------------|
| 多时间段配置 | `ScheduleRepository`、`feature/schedule/*` |
| 定时录音 | `ScheduleAlarmManager`、`RecordService` |
| 切片 m4a | `AudioSliceWriter` |
| 上传队列 | `UploadRepository`、`UploadWorker` |
| 结果轮询 | `ResultRepository`、`PollResultWorker` |
| 异常通知 | `AlertNotifier`、`MessageRepository` |
| 文件清理 | `CleanupWorker` |
| 诊断页 | `feature/settings/DiagnosticScreen.kt` |
| Base URL / API Key | `PreferencesDataSource`、`SettingsScreen` |

---

## 6. 提示词模板

复制以下模板，把 `{占位符}` 换成实际内容后发给 AI。

### 6.1 全局上下文（每轮对话粘贴一次）

```text
【项目】定时录音助手 V1.0（Android）
【需求文档】prd.md、codegen-guide.md
【架构】参考 Now in Android 简化版：Repository 暴露 Flow；ViewModel + sealed UiState + stateIn；Hilt 注入；Room 为读侧真相源
【技术栈】Kotlin、Compose、Room、Retrofit、WorkManager、Hilt、DataStore；minSdk 29，compileSdk 34，JDK 21
【包名】com.timedrecorder
【禁止】NIA changelist 同步、demo/prod flavor、Firebase、登录体系、feature api/impl 双模块
【输出】只生成指定文件；保持与已有模块依赖一致；关键逻辑加中文注释
```

### 6.2 阶段 1 示例 — 生成 Room 层

```text
{粘贴 6.1 全局上下文}

【当前阶段】阶段 1 — 模型与数据库
【PRD 章节】§13 数据结构
【模块】core:database、core:model
【任务】生成 ScheduleTaskEntity、AudioFileEntity、ProcessResultEntity、MessageEntity、AppLogEntity 及对应 Dao、RecorderDatabase、DatabaseModule
【规则】
- Entity 字段名与 prd.md §13 一致
- 对外暴露 model 类型，Entity 不泄漏到 feature 层
- 读操作返回 Flow
- 提供 asExternalModel() 扩展函数
【已有文件】见 codegen-guide.md 模块树；Gradle 骨架已存在
```

### 6.3 阶段 4 示例 — 录音 Service

```text
{粘贴 6.1 全局上下文}

【当前阶段】阶段 4 — 录音与定时
【PRD 章节】§9.3、§12.1、§12.2
【模块】sync
【任务】生成 RecordService（前台服务）、AudioSliceWriter、ScheduleAlarmManager
【规则】
- 音频格式 m4a/aac，默认 5 分钟切片
- 文件命名：{deviceIdShort}_local_{yyyyMMdd}_{HHmmss}_{seq}.m4a
- 每个切片完成后写入 audio_file 并触发 UploadWorker
- 常驻通知「录音服务运行中」
- 关键步骤写中文注释
```

### 6.4 阶段 7 示例 — Feature 界面

```text
{粘贴 6.1 全局上下文}

【当前阶段】阶段 7.2 — 首页
【PRD 章节】§9.1、§16.2
【模块】feature:home
【任务】生成 HomeViewModel、HomeUiState、HomeScreen
【规则】
- UiState：Loading / Success(含 recordingState、todaySchedules、recentUploads、recentResults、recentMessages)
- 从 ScheduleRepository、AudioFileRepository、MessageRepository 组合 Flow
- Screen 无业务逻辑，事件回调到 ViewModel
- 使用 core:designsystem 的 Theme 与组件
```

### 6.5 修 Bug / 单文件修改

```text
{粘贴 6.1 全局上下文}

【任务类型】修复 / 增量修改
【文件】{具体路径}
【现象】{描述问题}
【约束】不改动无关模块；不引入新依赖；保持现有架构风格
```

---

## 7. Android Studio 打开步骤

1. 用 **Android Studio Panda 4 | 2025.3.4** 打开本项目根目录
2. 信任 Gradle，等待 Sync（需 JDK 21）
3. Run Configuration 选 **app**
4. 连接 Android 10+ 真机或模拟器运行

若 Sync 失败，检查：

- JDK 是否为 17+（推荐 21）
- 网络 / Maven 镜像
- `gradle/wrapper/gradle-wrapper.properties` 中 Gradle 版本与 AGP 是否匹配

---

## 8. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V1.0 | 2026-05-23 | 初始版本：模块树、8 阶段生成顺序、提示词模板、Gradle 骨架 |
