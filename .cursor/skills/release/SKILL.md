---
name: release
description: >-
  Minor release via scripts/release.ps1: bump minor (0.2.x->0.3.0), rich
  CHANGELOG/tag notes, push to Power-Develop GitHub. Use for /release or 发版/大版本.
disable-model-invocation: true
---

# /release — 大版本（minor）发布

Phase 里程碑发版：**minor + 1、patch 归零**（如 `0.2.3` → `0.3.0`），写入 `CHANGELOG.md`、打带长说明的 tag、推送到 GitHub。

远程仓库：`https://github.com/123yangyan/Power---Develop`

## 版本规则

| 字段 | 行为 |
|------|------|
| `VERSION` | 第二位 +1、第三位归零：`x.y.z` → `x.(y+1).0` |
| `versionName` | 与 `VERSION` 同步 |
| `versionCode` | +1 |
| tag | `v{x.y.0}`，annotation 含完整 Notes |
| commit 前缀 | `release: v0.3.0 - ...` |
| `CHANGELOG.md` | 追加结构化条目（日期、摘要、Notes） |

未指定版本时，脚本从当前 `VERSION` 自动计算下一 minor。

## Parse（解析用户输入）

接受 `/release [version] [message...]`：

| 输入 | 行为 |
|------|------|
| `/release` | 读 `VERSION`，建议下一 minor，草拟 Message + Notes，确认后执行 |
| `/release Phase 3 启动` | 自动 minor bump + 给定 message；Agent 补全 Notes |
| `/release 0.3.0 Phase 3 启动` | 版本已定；草拟 Notes 后执行 |
| `/release 0.3.0 Phase 3 启动 --skip-push` | 仅本地 commit + tag |

## 执行流程

1. **定位仓库根**：`d:\OneDrive\Desktop\test\owner`
2. **预检**（必须）：
   - `git remote get-url origin` 为 Power---Develop
   - 读 `VERSION` 与 `mindbody-android/app/build.gradle.kts`
   - `git tag -l "v{version}"` 确认 tag 不存在
   - 检查 staged 文件不含 `.env`、keystore
   - **草拟丰富 Notes**（见下文），向用户展示摘要后执行
3. **执行**（PowerShell）：
   ```powershell
   .\scripts\release.ps1 -Message "Phase 3 kickoff" -Notes @"
   ## 亮点
   - F-P3-001: ...
   ## 变更范围
   - mindbody-android: ...
   ## 验收
   - ...
   "@
   ```
   指定版本：
   ```powershell
   .\scripts\release.ps1 -Version "0.3.0" -Message "Phase 3 kickoff" -Notes "..."
   ```
   仅本地：`.\scripts\release.ps1 -Message "..." -Notes "..." -SkipPush`
4. **验证并回报**：
   - 旧版本 → 新版本、`versionCode`
   - `git log -1 --oneline`、`git tag -l "v*"` 最新项
   - `CHANGELOG.md` 新增段落
   - GitHub Compare：`compare/v{上一minor}...v{新}`（如 `v0.2.0...v0.3.0`）
   - Actions：`https://github.com/123yangyan/Power---Develop/actions`
   - 若 PRODUCT.md 与行为不一致，提醒同步

## Notes 内容来源（Agent 必须汇总）

发版 Notes 应比 push 更丰富，建议从以下来源整理：

1. **自上一 minor tag 以来的 patch tags**：`git log v0.2.0..HEAD --oneline`
2. **`FEATURE-LEDGER.md`**：本 Phase 新增/更新的 `F-Px-xxx` 条目
3. **对应 Plan**：完成的 todo 列表（如 `phase3云端融合`）
4. **用户可见变化**：入口、交互、数据模型变更
5. **已知限制 / 下一 Phase 依赖**

Notes 用 Markdown 分段（`## 亮点`、`## 变更`、`## 验收`），写入 tag 与 `CHANGELOG.md`。

## 版本号建议

| 场景 | versionName |
|------|-------------|
| Phase 里程碑收官 | 自动 minor：`0.2.x` → `0.3.0` |
| 明确指定 | `-Version "0.3.0"` |
| 预发布 | `-Version "0.3.0-beta1"` |

发版 message 建议引用 Plan：`Phase 3 云端融合首版 (#phase3-cloud)`

## 与 /push 区别

| | /push | /release |
|---|-------|----------|
| bump | patch +1 | minor +1, patch → 0 |
| tag 说明 | 单行 | 多行 Notes |
| CHANGELOG | 不写 | 写 |
| 频率 | 每次日常提交 | Phase / 里程碑 |

日常改动先 `/push`；Phase 收官时 `/release` 汇总并升 minor。

## 禁止

- `git push --force` 到 main
- 修改 git config
- 未经用户要求 `git commit --amend` 已推送 commit

## 更多示例

见 [examples.md](examples.md)
