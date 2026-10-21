# 2026-10-20/21 legacy-language-schema-repair 会话记录

## 起因

用户初始化向导第 5 步报"服务器内部错误"（前一轮已定位为后端进程退出，重启恢复）。用户随即要求"彻底解决 2. 语言的相关问题，根据最新代码修订 Schema"——指日志中语言预置 22+22 条全部因 `SQLITE_CONSTRAINT_NOTNULL (language_id)` 跳过的遗留问题。

## 流程

Superpowers→OpenSpec→Superpowers 全流程：探索（PRAGMA 实证三代漂移形态）→ 范围裁定（用户选 A+B：启动期修复器 + 本机空库重建）→ 设计稿（docs/superpowers/specs/2026-10-20-）→ OpenSpec 变更 `legacy-language-schema-repair`（proposal/design/2×spec/tasks，validate 通过）→ 用户确认 tasks → 实现计划（docs/superpowers/plans/）→ TDD → 验证 → 本记录。未完成：变更未归档（待用户 continue-review/archive）；`.superpowers-memory` 更新未签入（工作区有并行变更 administered-item-reference-documents-ui 的未签入修改，签入需分组）。

## 关键实证

- 本机漂移库：111 表、无 flyway 历史、仅 account+identifier_strategy_config 两行、8 张语言表全空。
- 漂移签名：`language_id NOT NULL UNIQUE` + `language_name` + `territory_name`（另有 seeder 测试模拟的纯 language_id 代、baseline 快照的 enabled 代）。
- 修复器对真实副本端到端自愈（8082 旁路，logs/bypass-stdout.log）。
- 实施期发现并修复关联缺陷：FreshDatabaseInitializer 入口早退使 `flyway.enabled=false` 阻断全新库基线化（与 javadoc/配置注释/dd8d9dbe 提交说明三处矛盾）；修正为先 FRESH 识别再查开关，测试 flywayDisabledIsIgnored 改既有库语义 + 新增 flywayDisabledDoesNotBlockFreshBootstrap。

## 教训/模式

- 工作区并行变更时：全量测试基线用对方文档化基线比对（本次 1554 用例 7F+4E 逐项一致）。
- Windows 策略拦截复合删除命令：拆单条 + `cmd /c del`。
- PowerShell 多行字符串坑：行编辑脚本用单引号 here-string + .Replace，避免引号转义。
- 中文断言乱码先查"期望值口径"再查编码：本次实为 localize-language-names.sql 把「中文」修正为「简体中文」（既有 LanguagePresetSeederTest 有先例）。
- GitNexus CLI 在本机不可用（LadybugDB 原生二进制），MCP detect_changes 可用但索引滞后——按 AGENTS.md 用文本搜索兜底 UNKNOWN。
