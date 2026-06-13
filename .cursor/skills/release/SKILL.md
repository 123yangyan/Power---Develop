---
name: release
description: >-
  Power-Develop version release via scripts/release.ps1. Use when user invokes
  /release or asks to 发版/发布/tag version/push release to GitHub.
disable-model-invocation: true
---

# /release — 版本发布

Power-Develop monorepo 正式发版：更新版本号、打 tag、推送到 GitHub。

远程仓库：`https://github.com/123yangyan/Power---Develop`

## Parse（解析用户输入）

接受 `/release [version] [message...]`：

| 输入 | 行为 |
|------|------|
| `/release` | 读 `VERSION`，建议下一版本，确认后执行 |
| `/release 0.2.0` | 版本已定；message 由 Agent 根据 diff/Plan 草拟 |
| `/release 0.2.0 Phase 2 首版` | 版本 + message 齐全，预检后直接执行 |
| `/release 0.2.0 msg --skip-push` | 仅本地 commit + tag，不推送 |

## 执行流程

1. **定位仓库根**：`d:\OneDrive\Desktop\test\owner`（含 `.git` 与 `scripts/release.ps1`）
2. **预检**（必须）：
   - `git remote get-url origin` 为 Power---Develop
   - 读 `VERSION` 与 `mindbody-android/app/build.gradle.kts` 的 versionName/versionCode
   - `git tag -l "v{version}"` 确认 tag 不存在
   - 检查 staged 文件不含 `.env`、keystore
   - 若有未提交改动，询问用户是否先 `/push` 或一并纳入发版
3. **执行**（PowerShell）：
   ```powershell
   .\scripts\release.ps1 -Version "0.2.0" -Message "Phase 2 mood port"
   ```
   仅本地：`.\scripts\release.ps1 -Version "0.2.0" -Message "..." -SkipPush`
4. **验证并回报**：
   - `git log -1 --oneline`、`git tag -l "v*"` 最新项
   - versionCode 变化
   - GitHub Actions：`https://github.com/123yangyan/Power---Develop/actions`
   - 若 PRODUCT.md 与行为不一致，提醒同步文档

## 版本号建议

| 场景 | versionName |
|------|-------------|
| Phase 里程碑 | `0.2.0`（Phase 2 首版） |
| 补丁修复 | `0.1.1` |
| 不确定 | 读 VERSION 递增 patch/minor，AskQuestion 确认 |

发版 message 建议引用 Plan：`release: v0.2.0 - Phase 2 (#phase2-emotion-port)`

## 禁止

- `git push --force` 到 main
- 修改 git config
- 未经用户要求 `git commit --amend` 已推送 commit

## 更多示例

见 [examples.md](examples.md)
