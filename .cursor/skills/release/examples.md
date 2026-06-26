# /release 用法示例

## 示例 1：只输入 /release（自动 minor bump）

当前 `VERSION` 为 `0.2.3`：

```
/release
```

Agent 应：

1. 建议下一版本 `0.3.0`
2. 汇总 `v0.2.0` 以来 patch tags 与 FEATURE-LEDGER 总索引及分清单
3. 草拟 Message + Notes，用户确认后执行：

```powershell
.\scripts\release.ps1 -Message "Phase 3 云端融合首版" -Notes @"
## 亮点
- F-P3-001: HR 上报 Pipeline
## 变更
- server: 新增 fusion endpoint
## 验收
- 离线 mood 可上报并在服务端可见
"@
```

## 示例 2：指定版本与说明

```
/release 0.3.0 Phase 3 云端融合首版
```

```powershell
.\scripts\release.ps1 -Version "0.3.0" -Message "Phase 3 云端融合首版" -Notes "..."
```

## 示例 3：本地 tag 不推送

```
/release 0.3.0-beta1 内部测试 --skip-push
```

```powershell
.\scripts\release.ps1 -Version "0.3.0-beta1" -Message "内部测试" -Notes "CI only" -SkipPush
```

## 与 /push 的配合

```
# 日常：0.2.0 -> 0.2.1 -> 0.2.2（每次 /push 打 patch tag）
/push feat: mood 提醒 UI
/push fix: BLE 重连

# 里程碑：0.2.2 -> 0.3.0（/release 写 CHANGELOG + 长 Notes）
/release Phase 3 启动
```

GitHub 上查看：

- 小版本链：`compare/v0.2.0...v0.2.2`
- 大版本跨度：`compare/v0.2.0...v0.3.0`

## 发版后

- tag `v0.3.0` 推送后 GitHub Actions 自动编译 Debug APK
- 产物在 Actions → Release Tag → android-build → mindbody-debug-apk
- `CHANGELOG.md` 在仓库根目录可浏览历史 minor 发布
