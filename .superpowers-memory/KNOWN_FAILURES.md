# Known Failures

Use this file for repeated failure modes, environment pitfalls, process traps, and recurring misjudgments.

## Entry Template

```md
### Failure Pattern: <short-title>
- id: failure-YYYY-MM-DD-<slug>
- type: failure_pattern
- status: active
- confidence: verified
- last_updated: YYYY-MM-DD
- source:
- owner:
- review_after:

Trigger:

Symptom:

Likely cause:

How to detect:

Mitigation:
```

## Notes

- Prefer repeated or high-impact failures over one-off mistakes.
- If a failure is fully obsolete, mark it as `superseded` and explain why.
- Link to verification evidence when possible.
- Durable entries should always include `id`, `status`, `confidence`, `source`, `last_updated`, and `review_after`.
- Use `review_after` to force periodic re-checks of environment-sensitive failure patterns.

### Failure Pattern: MCP 工具在会话中缺失被误判为服务不可用
- id: failure-2026-09-13-mcp-missing-misdiagnosis
- type: failure_pattern
- status: active
- confidence: verified
- last_updated: 2026-09-13
- source: 2026-09-13 会话（milvus 未注册到 Kimi Code；gitnexus 日志报错为过期记录）
- owner: matrixlane
- review_after: 2026-12-13

Trigger:

当前会话中看不到某个已配置 MCP server（gitnexus、milvus 等）的工具。

Symptom:

Agent 直接降级到 CLI/grep/本地文件，或向用户报告"MCP 不可用"，未做诊断。用户侧表现为"又不行了"。

Likely cause:

三种已验证的真实原因，均非服务故障：(1) 该 server 从未注册到当前 Agent 宿主的 MCP 配置（各宿主配置独立：Kimi Code `~/.kimi-code/mcp.json`，Claude Code `~/.claude.json`，互不同步）；(2) 日志中的启动错误是过期记录（如包重装前），实际已恢复；(3) 配置修复后未重启会话——MCP 只在会话启动时加载。

How to detect:

1. 检查当前宿主的 MCP 配置文件是否有该 server 条目；2. `grep -i mcp ~/.kimi-code/logs/kimi-code.log` 查报错并核对时间戳；3. 向 server 进程手动发送 initialize JSON-RPC 握手实测；4. 验证依赖（可执行文件、源码路径、后端端口，如 Milvus `:9091/healthz`）。

Mitigation:

按"先诊断修复，再考虑降级"流程处理，详见用户级 `~/.agents/AGENTS.md` gitnexus 段"MCP 在当前会话不可用时"小节。修复配置后须开新会话生效。

### Failure Pattern: JPA @ManyToOne(optional=false) 遇 NULL 外键导致 findById 静默丢行
- id: failure-2026-09-13-eager-inner-join-null-fk
- type: failure_pattern
- status: active
- confidence: verified
- last_updated: 2026-09-13
- source: 值域「ValueDomain not found」事故（2026-09-13，data/_fix_vd_datatype.sql 修复记录）
- owner: matrixlane
- review_after: 2026-12-13

Trigger: 实体关联声明 optional=false（inner join 抓取），而保存路径允许该外键为 NULL。

Symptom: 列表可见、详情/删除 404「Xxx not found」，但数据库各行齐全。

Likely cause: 校验器与实体映射口径矛盾（validateCompleteness 注释误称「可选」），保存产出畸形行；inner join 使该行在 findById 中消失。

How to detect: 抓 Hibernate 实际 SQL（show-sql），检查必备关联的 join 类型；对比列表与 findById 行为差异。

Mitigation: 保存前完整性校验与实体可选性必须同源同口径；校验异常需映射为 400/422（已修 GlobalExceptionHandler）。

### Failure Pattern: Element Plus popover/drawer 半受控被内部机制强关或拦截页面
- id: failure-2026-09-13-ep-half-controlled-overlay
- type: failure_pattern
- status: active
- confidence: verified
- last_updated: 2026-09-13
- source: AI 候选浮层与调试侧栏三连修复（jsdom 全绿但浏览器必现，Playwright 实证）
- owner: matrixlane
- review_after: 2026-12-13

Trigger: el-popover/el-drawer 同时传 :visible + @update:visible + virtual-triggering（半受控），或非模态 drawer 未处理全屏包裹层。

Symptom: 生成中浮层被 ElTooltip 内置 hover/hideAfter 强关；drawer 非模态仍拦截全页点击（与页面按钮互斥）。

Likely cause: hasUpdateHandler 使 controlled=false，内部监听绑到 virtual-ref；非模态时 .el-drawer__wrapper 仍 fixed inset 0。

How to detect: jsdom 测不到（不执行 EP 原生监听），必须真实浏览器（Playwright）验证生成中移开鼠标、点击页面按钮等场景。

Mitigation: 浮层纯受控化（移除 update:visible 监听，自管理关闭+自实现 click-outside）；非模态 drawer 给包裹层 pointer-events:none、本体 auto。

### Failure Pattern: Windows 环境三连坑（.cmd shim / 端口保留区漂移 / sqlcmd 编码）
- id: failure-2026-09-13-windows-env-traps
- type: failure_pattern
- status: active
- confidence: verified
- last_updated: 2026-09-13
- source: 本会话探测全灭、8081 绑定失败、SQL 执行报语法错三起事件的处置记录
- owner: matrixlane
- review_after: 2026-12-13

Trigger: Java ProcessBuilder 启动 npm 全局 CLI；系统重启后绑定固定端口；sqlcmd 执行含中文注释的 UTF-8 SQL 文件。

Symptom: CreateProcess error=2/193（.cmd shim 起不了）；「Port 8081 was already in use」但 netstat 无监听；SQL 第 N 行语法错误。

Likely cause: CreateProcess 只补 .exe；Hyper-V/WinNAT 开机动态保留端口段（8081-8180 曾被罩）；sqlcmd 默认 ANSI 读文件。

How to detect: `where <cmd>` 看 shim 类型；`netsh interface ipv4 show excludedportrange protocol=tcp`；先用 -f 65001 试跑。

Mitigation: WindowsCommandResolver（PATH+PATHEXT 解析、.cmd/.bat 走 cmd /c、跳过无扩展名 shell shim）；`net stop winnat`→绑端口→`net start winnat`；sqlcmd 加 `-f 65001`。

### Failure Pattern: SQLite 64 表 join 上限——带 AdministeredItem 的条目 update/delete 必 500
- id: failure-2026-09-21-sqlite-64-table-join-limit
- type: environment_limitation
- status: active
- confidence: verified
- last_updated: 2026-09-22
- source: add-uom-measure-class-association 验证期发现；add-submitter-contact-identity 冒烟复现（`ActiveRegistrarService.assertCanModifyItem` → `AdministeredItemRepository.findByItem_Id` 全子类 join 超 64 表）
- owner: matrixlane
- review_after: 2026-12-21

Trigger: SQLite 环境对任何已归属注册机构的条目执行 update/delete（`assertCanModifyItem`/`assertCanModify` 触发 `findByItem_Id`）。

Symptom: 500 `[SQLITE_ERROR] at most 64 tables in a join`（Item 全子类 + 关联对象图单查询 join 超限）。SQL Server / 达梦无此限制。

Likely cause: Hibernate 对 `AdministeredItem`（RegisteredItem 子类）按 id 加载时把所有 Item 子类表与直接关联对象图拼进单条 join。

How to detect: 日志搜 `at most 64 tables in a join`。

Mitigation: SQLite 冒烟跳过条目 update/delete 路径（用单元测试覆盖）；根治须拆分实体继承映射或改 `findByItem_Id` 查询形状（单独 backlog）。
