---
name: push
description: >-
  Patch release via scripts/push.ps1: commit, bump patch (0.2.0->0.2.1), tag,
  push to Power-Develop GitHub. Use when user invokes /push or asks to 推送/小版本.
disable-model-invocation: true
---

# /push — 小版本（patch）推送

每次推送 = **补丁版本**：自动 `patch + 1`（如 `0.2.0` → `0.2.1`）、打 tag、推送到 `main`。

远程仓库：`https://github.com/123yangyan/Power---Develop`

## 版本规则

| 字段 | 行为 |
|------|------|
| `VERSION` | 第三位 +1：`x.y.z` → `x.y.(z+1)` |
| `versionName` | 与 `VERSION` 同步 |
| `versionCode` | +1 |
| tag | `v{x.y.z}`（如 `v0.2.1`） |
| commit 前缀 | `patch: v0.2.1 - ...` |

## Parse（解析用户输入）

接受 `/push [commit message...]`：

| 输入 | 行为 |
|------|------|
| `/push` | `git status` / `git diff`，草拟 message，确认后执行 |
| `/push feat: 完成 mood 实体` | 直接用给定 message |
| `/push fix: 修复 BLE 重连` | 同上 |

## 执行流程

1. **定位仓库根**：`d:\OneDrive\Desktop\test\owner`
2. **预检**（必须）：
   - 确认有改动可提交；无改动则告知用户（**不会**空 bump 版本）
   - 读 `VERSION` 与 `mindbody-android/app/build.gradle.kts`，向用户说明将 bump 到何版本
   - `git tag -l "v{下一版本}"` 确认 tag 不存在
   - 不提交 `.env`、keystore 等敏感文件
3. **执行**（PowerShell）：
   ```powershell
   .\scripts\push.ps1 -Message "feat: 描述改动"
   ```
4. **回报**：
   - 旧版本 → 新版本、`versionCode`
   - commit hash、tag 名
   - GitHub Compare 链接：`compare/v{旧}...v{新}`

## commit message 格式

message 传给脚本时**不要**带 `patch:` 前缀（脚本自动加）：

- `feat: 新功能`
- `fix: 修复`
- `docs: 文档`
- `chore: 工具/配置`

建议关联 Plan todo：`feat: MoodEntryEntity (#mood-entity)`

## 与 /release 区别

| | /push（小版本） | /release（大版本） |
|---|----------------|-------------------|
| 版本 bump | patch +1：`0.2.0→0.2.1` | minor +1、patch 归零：`0.2.3→0.3.0` |
| 改 VERSION | 是 | 是 |
| 打 tag | 是 | 是 |
| CHANGELOG | 否 | 是（写入 `CHANGELOG.md`） |
| 说明丰富度 | 单行 commit/tag | 多行 Notes + CHANGELOG |
| 脚本 | `push.ps1` | `release.ps1` |
| 典型场景 | 日常功能、修复、文档 | Phase 里程碑、功能集收官 |

Phase 里程碑用 `/release`；日常迭代用 `/push`。

## 禁止

- `git push --force` 到 main
- 修改 git config
