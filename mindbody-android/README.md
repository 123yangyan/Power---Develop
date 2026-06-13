# 身心状态 Android App — Phase 1

Polar Loop 实时心率采集 + 本地 Room 存储 + FTU 首次配置引导。

## 功能（Phase 1）

- Polar BLE SDK 8.0.0 集成（JitPack）
- 蓝牙权限申请与设备扫描/连接
- Polar Loop **FTU 首次使用配置**向导
- 实时心率显示（BPM）
- 今日心率统计与简易折线图
- Room 本地 `hr_samples` 存储（自动清理 24h 前数据）
- 前台服务保持后台心率采集

## 环境要求

- Android Studio Ladybug 或更新版本
- JDK 17+
- **真机**（模拟器无法测试 BLE）
- Polar Loop 手环

## 打开与运行

1. Android Studio → Open → 选择 `mindbody-android` 目录
2. 等待 Gradle Sync 完成
3. 连接 Android 真机，开启开发者模式与 USB 调试
4. Run `app`

若首次 Sync 失败，在终端执行：

```bash
cd mindbody-android
gradle wrapper
./gradlew :app:assembleDebug
```

## 使用流程

1. 打开 App，授予蓝牙与通知权限
2. 进入 **设备** 页 → 点击 **扫描** → 选择你的 Loop
3. 若 FTU 未完成 → 点击 **完成首次配置** → 填写身体数据
4. 返回 **心率** 页查看实时 BPM 与今日曲线

## 配对注意事项（Polar Loop）

- 手机与手环距离 **1 米以内**
- 每台 Loop 只绑定 **一台手机**
- 换机或删除手机配对后需 **恢复出厂** 才能重新配对
- FTU 未完成时手环 LED 会显示「搜索」动画

## 项目结构

```
app/src/main/java/com/owner/mindbody/
├── polar/           PolarBleManager, HrStreamService
├── data/            HrRepository, DevicePreferences
├── data/local/      Room 实体与 DAO
├── ui/heartrate/    心率页
├── ui/device/       设备连接页
├── ui/ftu/          首次配置页
└── ui/navigation/   底部导航
```

## 下一阶段（Phase 2）

- 移植 emotion 心情记录（坐标 + 日记）
- 记录时刻关联本地 HR 快照

## 相关文档

- [PRODUCT.md](./PRODUCT.md) — 产品说明与路线图
- [FEATURE-LEDGER.md](./FEATURE-LEDGER.md) — **已实现功能**施工清单（Agent 执行 mindbody-android 任务前必读）
- [`.cursor/plans/`](../.cursor/plans/) — Phase 待做特性设计
