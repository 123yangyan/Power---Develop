# Changelog

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

