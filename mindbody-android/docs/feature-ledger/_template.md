# 新增条目模板

功能代码合并后，在对应 Phase 分清单追加条目，并更新 [`FEATURE-LEDGER.md`](../../FEATURE-LEDGER.md) 索引一行。

```markdown
### F-Px-xxx 功能名称
Plan: plan-todo-id · 更新: YYYY-MM-DD

- **目的** …
- **入口** …
- **文件** …（相对 `com/owner/mindbody/`）
- **约定** …
- **验收** …

> YYYY-MM-DD：摘要 (#plan-todo-id)
```

**新 ID**：`F-P{优先级}-{序号}`，同级内递增；跨 Phase 特例（如 `F-P2-UI-001`）沿用既有编号。**禁止**为未实现功能预填条目。
