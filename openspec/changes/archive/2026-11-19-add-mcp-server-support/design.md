# Design: add-mcp-server-support

> 经用户确认的总体设计稿：`docs/superpowers/specs/2026-11-16-mcp-support-design.md`。本文档为 OpenSpec 设计工件，聚焦关键决策与取舍，细节以设计稿为准。

## Context

- 本项目为 Spring Boot 3.5.0 单体应用（`metadata-registry-web`，业务端口 8081），已有 60+ REST 控制器、`assistant`/`inference` AI 推断任务体系（异步作业 + ACP 常驻进程执行）、Spring Security 角色模型（个人版免登录隐式 SUPER_ADMIN / 团队版 JWT 多角色）。
- MCP（Model Context Protocol）客户端（Kimi Code 等）经 tools 调用外部能力；Kimi Code 官方文档确认其支持 stdio / HTTP（Streamable HTTP）/ SSE 三种连接与 `bearerTokenEnvVar` 凭证注入。
- 设计稿已确认范围：方案 A（应用内嵌）、只读 + 受控写入、15 个工具、默认回环绑定。

## Goals / Non-Goals

- **Goals**：MCP 面成为"薄适配层"——只读 8 工具 + 写入 7 工具全部复用既有 Service；安全与审计不弱化既有边界；零数据库 schema 变更。
- **Non-Goals**（设计级边界）：不做 MCP Resources/Prompts 能力面；不做协议级 OAuth；不做工具结果的流式大对象传输（导出工具仅返回 JSON/CSV 文本）；不引入数据库表落审计。

## Decisions

### D1：协议代际 = Legacy（2025-11-25），传输 = Streamable HTTP

[MCP Java SDK 官方文档](https://java.sdk.modelcontextprotocol.io/latest/overview/)明确：modern（2026-07-28 无会话版）与 legacy（2025-11-25 及更早，initialize 握手 + 会话）两代**互不兼容**（"a legacy client cannot call a modern server"）。Kimi Code 等现役客户端走握手流程，属 legacy 阵营。
- 备选：modern server → 被否，现役客户端无法连接。
- 备选：SSE（已弃用传输）→ 被否，Streamable HTTP 为 legacy 代际的现行首选。

### D2：集成形态 = 应用内嵌 `mcp` 包（独立端口 8082，默认绑定 127.0.0.1）

写工具进程内直接注入既有 Service，事务/校验/审计零损耗；随应用单进程启停，个人版启动器无感。
- 备选：独立 stdio 桥接进程 → 双进程维护、绕 REST 有损、写鉴权重做；保留为日后纯 stdio 场景的薄适配器（非本期）。

### D3：依赖 = 裸官方 MCP Java SDK 2.2.0（spike 结论，2026-11-19）

**Spring AI starter 路线弃用**：`spring-ai-starter-mcp-server-webmvc` 在 Maven Central 仅发布至 1.0.0，与 Boot 3.5 对齐不确定。改用官方 `io.modelcontextprotocol.sdk:mcp-core:2.2.0`——legacy `io.modelcontextprotocol.server.McpServer` + `HttpServletStreamableServerTransportProvider`（Servlet 传输在 mcp-core 内），自行装配 server 与双连接器；JSON 用 `mcp-json-jackson2` 与 Boot 3.5 的 Jackson 2.x 共存。
- 对齐处理：排除 SDK 传递的 `jakarta.servlet-api`（Tomcat 提供）；**保留** `reactor-core` 传递依赖——应用无 webflux，Boot BOM 不提供该依赖，排除即运行期 `NoClassDefFoundError: org/reactivestreams/Publisher`（spike 实测）。
- 编译验证已通过（`mvn compile`）；运行时兼容性以端到端冒烟（任务 7.1）为最终实证。

### D4：写路径铁律——只调既有 Service，绝不旁路

`draft-item`/`update-draft` 走各类型 `*Service.create/update`；`submit-drafts`/`request-candidate`/`rollback-item` 走 `WorkspaceService`；推断走 `AssistantJobService`。正交链闭环（ISO/IEC 11179-3 §7.4.1/§9.4.1.2）、状态机、提交记录、归属校验（`ActiveRegistrarService.assertCanModifyItem`）等不变式由既有层天然保证（AGENTS.md 正交链完整性规则的延伸）。

### D5：工具粒度 = 语义级聚合（15 个），命名 `mcp__mdr__<tool>`

不做端点级 1:1 映射；分页默认 `page=0, size=20, max 100`。理由：降低 Agent 误用率与上下文占用，Kimi Code 侧可用 `mcp__mdr__*` 通配配置权限。

### D6：安全模型——前置守卫 + 链后门禁双层过滤器（评审后定型）

实施初版为单一过滤器，评审 MAJOR-B1 指出：作为普通 Filter（security chain 之后执行），匿名非回环请求会先被全局链 401 而非 403、匿名业务端口访问 MCP 路径会先被 401 而非 404——spec 场景失真。定型为双层：
- **前置守卫 `McpEndpointGuardFilter`**（FilterRegistrationBean order=HIGHEST_PRECEDENCE，早于 springSecurityFilterChain 的 -100）：仅处理 MCP 路径——业务端口 404、非回环 403（复用 `FactoryResetService.isLoopbackAddressLiteral` 纯字面量判定），对匿名同样生效；
- **链后 `McpPortSecurityFilter`**（LOWEST_PRECEDENCE）：身份 401（个人版隐式 SUPER_ADMIN / 团队版 Bearer JWT 复用既有全局过滤器装配）、每身份 30 req/s 限流 429、写工具 REGISTRAR 门禁 403（JSON body 可重复读解析，解析失败按潜在写工具收紧）。`write-tools-enabled=false` 时 P2 工具不注册。

### D7：审计 = 独立日志 appender，零 schema 变更

每次调用记录时间戳、身份、工具名、参数 SHA-256 摘要、结果状态、耗时，至 `logs/app-mcp.log`。写数据库虽查询方便，但违反"零 schema 变更"且引入写路径依赖；日志 appender 为既有技术栈能力。

### D8：错误透传 = `[code] message`

`BusinessException` → 工具错误文本（附 `ApiCodes` 码与中文消息），Agent 可程序化判断重试；未知工具/参数走 SDK 默认 4xx。

## Risks / Trade-offs

- Kimi Code 实际协商的协议修订号未在官方文档写明 → legacy server 向下兼容 2025-06-18；端到端冒烟（SDK client 编程 + 真实 Kimi Code 连接）作最终实证，列入验收任务。
- Spring AI 1.1.x × Boot 3.5.0 × Jackson 2.x 组合风险 → 实施期依赖对齐 spike，冲突即切 D3 降级路径。
- Agent 幻觉写入/循环调用 → 既有校验与状态机兜底 + 客户端人工审批 + 服务端 30 req/s 限流（超限 429）。
- `draft-item` 不内置幂等键，重复调用产生多条草稿（与 UI 行为一致）→ 文档明示语义，人工审核兜底。
- 8082 端口占用 → 配置可覆盖；绑定异常快速报错透出日志。
- 初始化阶段（数据库未就绪）MCP 工具行为 → 与业务面一致：经既有 Service 自然报错，不新增特殊语义。

## Migration Plan

1. 依赖对齐 spike（Starter vs 裸 SDK）→ 定案。
2. TDD 实现 mcp 包（配置基座 → 安全链 → 只读工具 → 写入工具 → 审计 → 端到端冒烟）。
3. 验证：单元/安全集成/端到端冒烟/真实 Kimi Code 连接/web 全量回归零新增失败。
4. 文档：README MCP 章节 + 推荐 mcp.json/权限规则样例。
5. 回滚：`mdr.mcp.enabled=false`（或回滚版本）；MCP 面无数据库副作用，回滚无数据迁移负担。

## Open Questions

无（协议代际、依赖、范围、安全模型均已定案；Resources/Prompts 明确排除，不构成 deferrable unknown）。

## 评审记录（2026-11-19 双代理只读评审）

镜头 A（安全与正确性）1 BLOCKER + 5 MAJOR + 4 MINOR；镜头 B（架构与符合性）3 MAJOR + 5 MINOR。**全部核实处置**：

- **BLOCKER**：SDK 默认 `immediateExecution=false` 将工具调用调度到 Reactor boundedElastic 线程，SecurityContext ThreadLocal 丢失（审计恒 anonymous、写归属失败）→ `McpServerConfig` 显式 `immediateExecution(true)`；e2e 补归属断言（registration_authority_id = 当前活动 Registrar 机构）实证修复。
- **A-MAJOR**：AccessDeniedException 归入 [403] FORBIDDEN（原落入 500）；`registration: null` 可绕过闭环守卫 + 未强制初始 INCOMPLETE → `ensureRegistrationBlock` 统一合成/强制；限流桶 long 溢出与 key 拼接修复；写工具 body 解析失败按潜在写工具收紧。
- **B-MAJOR**：安全链拆分前置守卫（见 D6）；e2e 补 submit-drafts + 四表闭环 + 匿名 404；映射网络/影响面 DTO 转换收敛共享 `MappingDtoConverters`（REST 与 MCP 复用）。
- **实施期新缺陷（评审驱动 e2e 暴露）**：BLOCKER 修复后工具运行于请求线程，submit 归属校验如实触发——MCP 起草无 UI 的前端归属预填，产出条目无主管记录被拒。新增 `McpDraftOwnershipPrefill`：缺省时预填当前账号默认 Registrar 身份（退化为首个绑定联系人），与业务面 ItemFormDialog 预填语义对齐。
- **MINOR 全修**：审计摘要经过 ObjectMapper 序列化（可重放）；路径规范化折叠斜杠 + servletPath 兜底；listening 日志移至 after_start 并打印实际端口；SERVER_NAME=mdr 对齐文档；协议版本（SDK 固定支持 03-26/06-18/11-25，协商取客户端版本，e2e 实证 11-25）注释锁定。
