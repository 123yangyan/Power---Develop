# /release 用法示例

## 示例 1：只输入 /release

```
/release
```

Agent 应：

1. 读 `VERSION`（当前如 `0.1.0`）
2. 建议下一版本（如 Phase 2 完成 → `0.2.0`）
3. 用户确认后执行 `.\scripts\release.ps1 -Version "0.2.0" -Message "..."`

## 示例 2：指定版本与说明

```
/release 0.2.0 Phase 2 心情记录首版
```

Agent 预检后直接：

```powershell
.\scripts\release.ps1 -Version "0.2.0" -Message "Phase 2 心情记录首版"
```

## 示例 3：本地 tag 不推送

```
/release 0.2.0-beta1 内部测试 --skip-push
```

```powershell
.\scripts\release.ps1 -Version "0.2.0-beta1" -Message "内部测试" -SkipPush
```

## 发版后

- tag `v0.2.0` 推送后 GitHub Actions 自动编译 Debug APK
- 产物在 Actions → Release Tag → android-build → mindbody-debug-apk
