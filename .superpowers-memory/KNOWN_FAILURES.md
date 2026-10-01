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

### Failure Pattern: mdr.* 自然键列缺 UNIQUE 约束 + JPA findBy* 隐含唯一假设
- id: failure-2026-09-30-natural-key-without-unique-constraint
- type: failure_pattern
- status: active
- confidence: verified
- last_updated: 2026-09-30
- source: 2026-09-25 23:59 启动崩溃事故链（`NonUniqueResultException` 终止 Spring 上下文刷新，后端 503）；根治见 openspec 变更 `enforce-language-identification-uniqueness`（迁移 `V20260926001__Enforce_language_identifier_unique.sql` + `LanguageIdentifierResolver` + 8 个调用点迁移）
- owner: matrixlane
- review_after: 2026-12-30

Trigger: 某张注册表把自然键列（业务上唯一的标识列）只用主键约束保护，同时用 Spring Data JPA 派生方法按该列查询并返回 `Optional<实体>`／单值——如 `findByXxx(...)` 返回 `Optional`。任何绕过应用层的写入（脚本、外部同步、手工 SQL、并发 upsert 竞态）产生重复行即触发。

Symptom: 两级放大。(1) 查询期：Hibernate 抛 `NonUniqueResultException: query did not return a unique result`。(2) 若该查询发生在 `ApplicationRunner`／`CommandLineRunner`／`@PostConstruct` 期，异常会终止 Spring 上下文刷新 → 内嵌 Tomcat 优雅停机 → 后端 503，业务**全面**不可用，而非"某功能报错"。数据本身看起来完全正常（表里有 22 条合法语言行，只是多了一条重复的）。

Likely cause: 实体层的 `Optional` 返回值把"自然键唯一"写成了隐含假设，DB 层却只有主键约束，二者没有一处强制。`Optional<T>` 的类型签名给了编译器与评审一个"至多一行"的假信号，`findByXxx(...).orElseGet(...)`／`.orElseThrow(...)` 读起来像普通的"不存在则创建"逻辑，不会有人想到多行分支。

How to detect: (1) 审计派生方法——`grep -rn "findBy" --include=*.java <模块>/src/main/java` 找返回 `Optional`／单值的按自然键查询，逐个核对对应列是否有 UNIQUE 约束（`PRAGMA index_list` / `sys.indexes`）。(2) 运行期搜 `NonUniqueResultException`。(3) 库级体检：按自然键 `GROUP BY` 找 `HAVING COUNT(*) > 1` 的组。(4) 启动期故障优先怀疑播种/初始化 Runner 抛异常，而非 Runner 自身逻辑。

Mitigation: 两道防线，缺一不可——**DB 侧**用迁移施加 `CREATE UNIQUE INDEX`（SQLite 不支持 `ALTER TABLE ADD CONSTRAINT`，三方言统一走索引路线），并让迁移自带幂等 dedup（按自然键分组保留 id 最小行 → 外键引用表重定向 → 删多余行 → 建索引），不能依赖人工预处理；**应用侧**把所有自然键解析收敛到统一防御式入口（`LanguageIdentifierResolver.resolve`），0 行→empty／1 行→该行／多行→取 id 字典序最小行 + WARN（含标识符、行数、保留行 id）而不抛异常，让历史脏数据不阻塞业务。注意多行保留行必须用 **UUID 文本字典序**而非 `UUID.compareTo`：后者按有符号 64 位比较 `mostSigBits`/`leastSigBits`，两枚 id 符号位不同时与 SQL `MIN(id)`（大端无符号字节序）结论相反。

Backlog（同类表审计清单，尚未处理，按用户"只修报告的"原则留待报告再修）：

| 表 | 自然键列 | 现状 |
| --- | --- | --- |
| `mdr.namespace` | `shorthand_prefix` | 仅主键约束 + `findByShorthandPrefix` 返回 `Optional` |
| `mdr.account` | `username` | 仅主键约束；登录/查找按 `username` 走 `Optional` |
| `mdr.organization` | 组织标识列 | 仅主键约束 |
| `mdr.registration_authority` | 注册机构标识列 | 仅主键约束 |
| `mdr.identifier_strategy_config` | `strategy_type`（+作用域） | 仅主键约束；已有"写 UUID 实为空操作"的相关遗留 |

后续同类修复应复用本次模式：迁移（D1/D2 决策）+ 统一防御式解析器 + 全部调用点迁移，并补一条"`findAllBy*` 多行集成测试"（注意：测试 profile 关闭 Flyway，由 Hibernate `ddl-auto` 建表，唯一索引不会自动出现，因此集成测试**能**造重复行；用 `PRAGMA index_list` 断言该前提，schema 变了会给出可读失败）。
