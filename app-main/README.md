# 定时录音助手（Timed Recorder）

Android 定时录音、切片上传与结果推送工具 — V1.0。

## 文档

- [prd.md](prd.md) — 产品需求
- [codegen-guide.md](codegen-guide.md) — AI 代码生成指南（模块树、分阶段顺序、提示词）

## 环境

- Android Studio Panda 4 | 2025.3.4
- JDK 21
- minSdk 29 / compileSdk 34

## 打开项目

1. 用 Android Studio 打开本目录
2. 等待 Gradle Sync（首次需联网，会自动生成 `gradlew`）
3. Run Configuration 选择 **app**，运行到 Android 10+ 设备

## 模块结构

```text
app / core:* / feature:* / sync
```

详见 `codegen-guide.md` 第 2 节。

## 已实现功能（V1.0）

- Room 五表持久化（任务、文件、结果、消息、日志）
- DataStore 用户偏好（Base URL、API Key、轮询参数等）
- Retrofit 上传 / 结果查询 API
- 前台录音服务 + AlarmManager 定时 + 开机自启
- WorkManager 上传 / 轮询 / 清理
- Compose UI：首页、任务、文件、消息、设置、诊断、首次引导

## 首次使用

1. 完成首次启动引导并授予权限
2. 在 **设置** 中配置服务端 Base URL
3. 在 **任务** 中新增录音时间段
4. 到达计划时间后自动录音、切片、上传
