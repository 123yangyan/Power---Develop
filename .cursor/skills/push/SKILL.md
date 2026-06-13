---
name: push
description: >-
  Commit and push current changes to Power-Develop GitHub via scripts/push.ps1.
  Use when user invokes /push or asks to 推送/提交并推送 without releasing.
disable-model-invocation: true
---

# /push — 日常推送

提交当前改动并推送到 `main`，**不**改版本号、**不**打 tag。

远程仓库：`https://github.com/123yangyan/Power---Develop`

## Parse（解析用户输入）

接受 `/push [commit message...]`：

| 输入 | 行为 |
|------|------|
| `/push` | `git status` / `git diff`，草拟 commit message，确认后执行 |
| `/push feat: 完成 mood 实体` | 直接用给定 message |
| `/push fix: 修复 BLE 重连` | 同上 |

## 执行流程

1. **定位仓库根**：`d:\OneDrive\Desktop\test\owner`
2. **预检**：
   - 确认有改动可提交；无改动则告知用户
   - 不提交 `.env`、keystore 等敏感文件
3. **执行**（PowerShell）：
   ```powershell
   .\scripts\push.ps1 -Message "feat: 描述改动"
   ```
4. **回报**：commit hash、分支、远程 URL

## commit message 格式

- `feat:` 新功能
- `fix:` 修复
- `docs:` 文档
- `chore:` 工具/配置

建议关联 Plan todo：`feat: MoodEntryEntity (#mood-entity)`

## 与 /release 区别

| | /push | /release |
|---|-------|----------|
| 改 VERSION | 否 | 是 |
| 打 tag | 否 | 是 |
| 脚本 | push.ps1 | release.ps1 |

需要发版时用 `/release`，不要用 `/push`。

## 禁止

- `git push --force` 到 main
- 修改 git config
