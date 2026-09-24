<!-- openspec:start -->
# superpowers-openspec-team-skills 流程强制启用规则

本项目默认采用 Superpowers + OpenSpec + Superpowers 端到端方式推进功能开发。当任务涉及以下内容时，**必须**先使用 `superpowers-openspec-superpowers-workflow` 完成需求澄清、OpenSpec 提案、设计、规范与任务拆分，经确认后再进入实现，完成后归档：

1. 新增业务功能或新 API / 新模块
2. 数据模型、元模型、注册项结构变更
3. 与 ISO/IEC 11179 标准对应关系有关的需求澄清或改动
4. 跨模块/跨版本的 federation、merge request、导入导出机制变更
5. 涉及 SQLite / SQL Server / 达梦 多种方言的 schema 或迁移脚本变更

## 豁免场景（无需 OpenSpec）

- 纯 bug 修复、测试补充、日志/注释/文案调整
- 不影响业务行为的代码重构（如方法重命名、提取工具类）
- 配置项默认值、环境变量、依赖版本号的微调
- 用户明确说“先直接实现，不用走 OpenSpec”

## 执行要求

- 若任务命中强制范围，先调用 `superpowers-openspec-superpowers-workflow` 技能；各 Agent 按自身支持的方式激活（Kimi Code CLI / Codex 按技能名调用，Claude Code 使用对应 slash command）
- 在启动实现前，向用户确认 OpenSpec 产出（proposal / design / specs / tasks）已完成
- 如用户对范围有异议，以用户当次明确指令为准
<!-- openspec:end -->

# 标准依据表述规则

涉及 ISO/IEC 11179 标准字段的解释性说明（UI placeholder、字段提示、文档描述等），**必须忠实还原标准文档中的原文文字说明**（可翻译为中文，但不得概括、扩写或自行发挥）；不得引用标准章节/条款号作为说明内容。表述前须核对标准原文（优先 Milvus 检索，回退 `ISO/IEC 11179 标准原文目录` 目录下 PDF）。

# 文档同步规则

每次签入代码前，检查变更是否影响以下文档，如受影响必须同步更新：

- `README.md` — 模块清单、技术栈、页面清单、默认账号等面向使用者的信息
- `docs/architecture/ARCHITECTURE.md` — 模块结构、功能区域、核心执行流程（该文件由 GitNexus 生成，内容大幅变化时应重新生成而非手工改写）

# 多 Agent 项目级上下文

本仓库使用多种 Coding Agent 开发，项目级规则以本文件（`AGENTS.md`）为唯一事实源：

| Agent | 项目级上下文入口 |
| --- | --- |
| Kimi Code CLI / Codex / Cursor / OpenCode | 直接读取本文件 |
| Claude Code | `CLAUDE.md` 通过 `@AGENTS.md` 导入本文件 |

修改项目级规则时只编辑本文件；`CLAUDE.md` 仅保留导入声明，不复制规则正文。

# Agent 登录系统规则

Coding Agent 需要登录系统（API 冒烟、E2E、手工验证）时，**必须自己创建一个专用 Admin 测试账户**，严禁使用超级管理员应急码（`emergency-code.txt`）登录。

- 依据：应急码登录会把真实库 `force_password_reset` 置 1（用户下次正常登录被强制改密弹窗阻塞），且旧应急码一次性作废重生（2026-09-21 事故，见 `.superpowers-memory/session-journal/2026-09-21-dimensionality-management.md` 事故记录）。
- 冒烟/验证一律优先用独立临时实例或副本库（`scratch/` 模式）。
- 应急码仅在用户本人遗忘管理员密码、且用户明确知情同意时才能使用；用后立即恢复 `force_password_reset` 原值并告知用户。
