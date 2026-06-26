# Power — Develop

身心状态 App（MindBody）及相关后端、参考代码的 monorepo。

- **主产品**：[`mindbody-android/`](mindbody-android/) — Polar Loop + 心情记录 + AI 指导（Android）
- **后端**：[`server/`](server/) — FastAPI + 百炼融合 Pipeline（Phase 3）
- **规划**： [`.cursor/plans/`](.cursor/plans/) · [`.cursor/rules/`](.cursor/rules/)

远程仓库：[123yangyan/Power---Develop](https://github.com/123yangyan/Power---Develop)

**mindbody-android 功能清单**（仅已实现）：[`FEATURE-LEDGER.md`](mindbody-android/FEATURE-LEDGER.md)（总索引）· [分清单](mindbody-android/docs/feature-ledger/)

---

## Git 与版本发布

### 首次推送（只需一次）

```powershell
cd d:\OneDrive\Desktop\test\owner

# 登录 GitHub（按提示在浏览器完成授权）
gh auth login

# 推送 main 分支
git push -u origin main
```

### Cursor 斜杠命令

在 **Agent 聊天框**输入 `/`，选择：

| 命令 | 作用 |
|------|------|
| **`/release [版本] [说明]`** | 正式发版：更新版本号 + tag + 推 GitHub |
| **`/push [commit message]`** | 日常提交并推送（不发版） |

示例：

```text
/release 0.2.0 Phase 2 心情记录首版
/push feat: 完成 mood 实体
```

Skill 文件：`.cursor/skills/release/`、`.cursor/skills/push/`

### 日常改动推送

```powershell
.\scripts\push.ps1 -Message "feat: 描述本次改动"
```

### 版本发布（自动改 versionName / versionCode、打 tag、推送）

```powershell
.\scripts\release.ps1 -Version "0.2.0" -Message "Phase 2 心情记录首版"
```

脚本会：

1. 更新根目录 `VERSION` 与 `mindbody-android/app/build.gradle.kts`
2. 创建 commit + 注解 tag（如 `v0.2.0`）
3. 推送到 `origin/main` 并推送 tag

推送 `v*` tag 后，GitHub Actions 会自动编译 Debug APK（见 [`.github/workflows/release-tag.yml`](.github/workflows/release-tag.yml)）。

仅本地打 tag、不推送：

```powershell
.\scripts\release.ps1 -Version "0.2.0" -SkipPush
```

---

## 仓库说明

| 目录 | 说明 |
|------|------|
| `mindbody-android/` | 主 Android App |
| `server/` | 后端服务 |
| `polar-ble-sdk-8.0.0/` | Polar SDK 本地参考 |
| `emotion-2.1.0/` | emotion 参考（含独立 git，默认不纳入 monorepo） |
| `app-main/` | 其他 Android 实验项目 |

---

*版本见根目录 [`VERSION`](VERSION) 文件。*
