# MCP 协议支持设计稿（方案 A：应用内嵌 MCP Server）

- 日期：2026-11-16
- 状态：待用户确认
- 前置：本会话探索结论 + 用户确认的取向（方案 A、只读 + 受控写入、假设无误）
- 流程：superpowers-openspec-superpowers-workflow 第 4 步产物；确认后进入 OpenSpec 提案

## 1. 背景与目标

MetadataRegistry4j 是面向 ISO/IEC 11179-3:2023 的元数据注册中心（个人版免登录本机运行 / 团队版 JWT 多角色）。目标：让本机其它 AI Agent（首选 Kimi Code，任意 MCP 客户端均可接入）经 MCP 协议使用本项目的数据治理能力：

- **元数据访问**：Agent 查询权威注册库（WS/T 363/364 基准 + 用户自有条目），作为编码/分析/起草的上下文来源。
- **AI 驱动数据治理**：Agent 在既有校验与状态机约束下执行受控写入（起草、补全、提交、请求候选、回退）。

项目已有完整地基：60+ REST 控制器、`assistant`/`inference` AI 推断任务体系（ACP 常驻进程执行）、Spring Security 角色模型。MCP 层是薄适配，不新建业务语义。

## 2. 范围

### 2.1 首期交付（P1+P2 一次设计、分批实现）

| 批次 | 内容 |
|---|---|
| P1 只读 | 元数据搜索、条目详情、版本历史、数据字典浏览/导出、映射网络与影响面、注册流程布局 |
| P2 受控写入 | 起草条目（核心类型限定）、更新草稿、批量提交、请求候选、回退、运行推断任务、查询推断作业 |

### 2.2 明确不做（Out of Scope）

- 删除条目、注册状态推进（`administered-items/*/transitions/**`，治理裁决动作留给人）
- 系统管理面（账号、数据源、危险区域、出厂重置）
- 联邦（federation）与管理端点
- 对外公网部署、OAuth 授权服务器
- Resources / Prompts 能力面（P3 可选项，待 Kimi Code 对 resources/prompts 的支持实测后再定，见 §10）

## 3. 关键决策

| # | 决策 | 选择 | 理由 |
|---|---|---|---|
| D1 | 协议代际 | **Legacy（2025-11-25 修订版）server，Streamable HTTP 传输** | [MCP Java SDK 官方文档](https://java.sdk.modelcontextprotocol.io/latest/overview/)：modern（2026-07-28 无会话版）与 legacy 互不兼容（"a legacy client cannot call a modern server"）。Kimi Code 等现役客户端走 initialize 握手，属 legacy 阵营；server 必须选 legacy 才能对接 |
| D2 | 集成方式 | **应用内嵌**，`metadata-registry-web` 新增 `mcp` 顶层包 | 写 tools 进程内直接注入既有 Service，事务/校验/审计零损耗；随应用单进程启停，个人版启动器无感 |
| D3 | 依赖选型 | Spring AI `spring-ai-starter-mcp-server-webmvc`（legacy，Boot 3.5 对应 Spring AI 1.1.x）；若依赖冲突则降级为裸 `io.modelcontextprotocol.sdk:mcp` + `mcp-json-jackson2` + 自带 Servlet 传输 | Spring WebMVC 传输已迁入 Spring AI 2.0+（org.springframework.ai），SDK 本体不再提供 Spring 传输 starter；Boot 3.5.0 管理 Jackson 2.x，须选 jackson2 模块避免双 Jackson 共存 |
| D4 | 监听端口 | 独立端口，默认 **8082**（`mdr.mcp.port`），**默认绑定回环 127.0.0.1**（`mdr.mcp.host`） | 与 8081 业务面隔离；回环绑定把个人版暴露面收敛到本机 |
| D5 | 写路径铁律 | 写 tools 只调用既有 Service（如 `WorkspaceService.submit`、`DataElementService.create`），**绝不旁路裸 SQL** | 正交链闭环（§7.4.1）、状态机、幂等、提交记录等不变式由既有层天然保证（AGENTS.md 正交链完整性规则的延伸） |
| D6 | 工具粒度 | 语义级聚合（约 15 个 tools），不做端点级 1:1 映射 | MCP 最佳实践：少量、语义清晰、参数自描述，降低 Agent 误用率与上下文占用 |
| D7 | 删除与高危动作 | 不暴露 delete tools；不暴露注册状态推进 | 与本变更范围外的人工裁决原则一致 |

## 4. 架构设计

### 4.1 组件结构

```
metadata-registry-web (Spring Boot, 8081 业务端口)
├── mcp/                                 ← 新增顶层包
│   ├── McpServerConfig.java             # server 装配（开关、端口、能力声明）
│   ├── McpSecurityConfig.java           # MCP 端口专用 SecurityFilterChain
│   ├── McpIdentityResolver.java         # 身份解析（个人版隐式主体 / 团队版 Bearer JWT）
│   ├── tool/
│   │   ├── MetadataQueryTools.java      # P1 只读 tools
│   │   ├── DictionaryTools.java
│   │   ├── MappingTools.java
│   │   ├── DraftTools.java              # P2 写 tools（起草/更新）
│   │   ├── WorkspaceTools.java          # P2（提交/请求候选/回退）
│   │   └── InferenceTools.java          # P2（推断任务/作业查询）
│   ├── audit/McpToolAuditService.java   # 调用审计（who/when/tool/参数摘要）
│   └── dto/                             # MCP 专用请求/响应 DTO（Java records）
└── （既有 service/repository 不动）
```

### 4.2 运行与配置

新增 `application.yml` 配置段：

```yaml
mdr:
  mcp:
    enabled: ${MDR_MCP_ENABLED:true}      # 个人版默认开；团队版可显式关
    host: ${MDR_MCP_HOST:127.0.0.1}
    port: ${MDR_MCP_PORT:8082}
    write-tools-enabled: ${MDR_MCP_WRITE_ENABLED:true}   # P2 总开关（false 时 P2 tools 不注册）
```

- `enabled=false` 时 MCP server 完全不启动（同 `mdr.inference.enabled` 的既有语义）。
- `write-tools-enabled=false` 时只注册 P1 只读 tools——给"只让 Agent 查、不让 Agent 写"的部署形态。
- 初始化阶段（数据库未就绪）：MCP 端口可监听，但 tools 内部经既有 Service 访问数据库的行为与业务面一致（引导完成前数据库未初始化时按既有错误语义返回）。

### 4.3 请求处理链

```
Kimi Code (mcp.json: url=http://127.0.0.1:8082/mcp)
  → MCP 端口 FilterChain（仅作用于 8082）：
      1) 回环校验：非 127.0.0.1/::1 来源直接 403（双保险，即便 host 配错也不放行）
      2) 个人版：放行，身份 = 隐式唯一 SUPER_ADMIN（复用 PersonalEditionAuthenticationFilter 语义）
      3) 团队版：Authorization: Bearer <JWT> 复用 JwtService 校验，无效 401；
         角色映射：读 tools 需任意有效身份；写 tools 需 REGISTRAR（推断类沿用既有 REGISTRAR 约束）
  → MCP Java SDK legacy server（Streamable HTTP，协议协商 2025-11-25）
  → Tool 分发（@Tool 方法，按 write-tools-enabled 注册）
  → 复用既有 Service → 统一错误透传（BusinessException → tool 错误文本，保留 code）
  → McpToolAuditService 落审计
```

## 5. 工具面设计（v1）

命名规范：`mcp__mdr__<tool>`（Kimi Code 侧按 `mcp__mdr__*` 配置权限）。所有分页参数默认 `page=0, size=20, max size=100`。

### 5.1 P1 只读（8 个）

| tool | 参数（摘要） | 复用服务 | 说明 |
|---|---|---|---|
| `search-metadata` | q, type, status, contextId, namespaceId, exactMatch, identifier, page | `MetadataSearchService.search` | 统一搜索，类型白名单沿用既有 20 类 |
| `get-item-detail` | itemId | 各 ItemService.get + `ItemAdministrationService` | 返回条目全视图（含受管链：标识/版本/状态/指称/定义） |
| `get-item-versions` | itemId | `VersionHistoryController` 对应 service | 版本历史 |
| `browse-data-dictionary` | keyword, itemType, status, page | `DataDictionaryBrowserController.browse` 对应 service | 数据字典浏览 |
| `export-data-dictionary` | itemType, format=json/csv | `MetadataExportService.exportJsonToStream/exportCsvToStream` | 文本结果直接返回；Excel/PDF 不在 P1 |
| `get-mapping-network` | itemId, direction, depth | `MappingNetworkController` 对应 service | 映射网络 |
| `get-mapping-impact` | itemId | `MappingImpactController` 对应 service | 变更影响面分析 |
| `get-registration-process` | （无参） | `RegistrationProcessController` + layout | 标准注册流转规则与当前布局 |

### 5.2 P2 受控写入（7 个）

| tool | 参数（摘要） | 复用服务 | 约束 |
|---|---|---|---|
| `draft-item` | itemType（白名单见下）, payload（各类型 Request DTO 的 JSON） | 各类型 `*Service.create` | itemType 白名单：`ObjectClass, Property, DataElementConcept, ConceptualDomain, ValueDomain, DataElement, UnitOfMeasure, MeasureClass, Dimensionality`；创建即草稿态（初始 INCOMPLETE，既有语义），**不自动提交** |
| `update-draft` | itemId, payload | 各类型 `*Service.update` | 仅允许管理状态为 DRAFT/INCOMPLETE 且归属当前身份的条目（复用 `ActiveRegistrarService.assertCanModifyItem` 既有判定） |
| `submit-drafts` | itemIds[] | `WorkspaceService.submit` | 既有逐条校验与结果返回原样透传 |
| `request-candidate` | itemId, submissionInfo? | `WorkspaceService.requestCandidate` | 既有 INCOMPLETE+主管记录+归属校验 |
| `rollback-item` | itemId | `WorkspaceService.rollback` | 既有归属校验 |
| `run-inference-task` | taskId（complete-metadata/explain-selection/selection-chat）, itemId?, params | `AssistantJobService.createJob` | 沿用任务清单动态发现（GET /assistant/tasks 同数据源）；推断开关 `mdr.inference.enabled=false` 时返回"功能未启用" |
| `get-inference-job` | jobId | `AssistantJobService.getJob` | 仅创建者或 SUPER_ADMIN（既有语义） |

写 tools 统一行为：成功后返回含 `itemId`/`jobId` 与新状态的 JSON；失败透传既有业务错误码与中文消息（Agent 可据此修正重试）。全部写入在既有事务内完成，无新增表。

### 5.3 明确不暴露的工具

删除类（任何 `*Service.delete`）、`administered-items/*/transitions/**`、账号/数据源/系统管理、`federation/**`、`registration-process/layout` 的 PUT。

## 6. 安全设计

1. **网络层**：默认 `127.0.0.1:8082`；Filter 拒绝非回环来源（团队版如需局域网部署，显式配 `mdr.mcp.host` 并在文档中给出风险提示）。
2. **身份层**：个人版隐式主体；团队版 Bearer JWT（复用 `JwtService`，不引入新 token 体系）。Kimi Code 侧经 `bearerTokenEnvVar` 注入 token，token 不落 `mcp.json` 明文。
3. **授权层**：读 = 有效身份；写 = REGISTRAR（团队版）。写 tools 总开关 `write-tools-enabled`。
4. **审计层**：`McpToolAuditService` 记录每次调用（时间戳、身份、tool、参数摘要 SHA-256、结果状态、耗时）至应用日志（`logs/app-mcp.log` 独立 appender）；审计不写数据库（零 schema 变更原则）。
5. **审批层（客户端侧）**：建议用户在 Kimi Code 配置 `allow mcp__mdr__get-*/search-*` 只读通配 + 写工具逐个审批；文档给出推荐 `config.toml` 权限规则样例。

## 7. 错误处理与防御

- 业务错误：`BusinessException` → tool result error 文本，附 `ApiCodes` 码与中文消息（格式：`[code] message`），便于 Agent 程序化判断。
- 未知工具/参数：SDK 默认 4xx 透传。
- 超时：长耗时导出/推断走既有异步作业模式；MCP 调用本身设置合理上限，Kimi Code 侧可用 `toolTimeoutMs` 调节。
- 限流：MCP 端口 Filter 增加简单令牌桶（每身份 30 req/s，超出 429）——防止 Agent 循环误调用打爆数据库；实现用本地 `Bucket4j` 风格自研轻量桶（不引新依赖则 `Guava RateLimiter` 式自实现）。
- 幂等：`draft-item` 不内置幂等键，重复调用产生多条草稿（与 UI 行为一致）；Agent 侧重复调用属预期内，由人工审核兜底。文档明示此语义。

## 8. 测试策略（TDD）

1. **单元测试**：每个 tool 方法（Mock Service）：参数校验、错误透传、白名单拦截、`write-tools-enabled=false` 时 P2 不注册。
2. **安全集成测试**：MCP 端口 FilterChain——非回环 403、团队版无 token 401、错误 token 401、角色不足 403、个人版隐式身份放行。
3. **端到端冒烟**：真实启动个人版实例（隔离端口），用 MCP Java SDK **client**（legacy）以编程方式走通：initialize → tools/list → search-metadata → draft-item(DataElement) → run-inference-task(complete-metadata) → submit-drafts；断言库内落库与审计日志。
4. **真实 Kimi Code 验证**（验收步骤）：按推荐 `mcp.json` 在本机 Kimi Code 连接，人工核对 tools 列表与一次只读调用——验证 Kimi Code 协商通过（这是 D1 决策的最终实证）。
5. 回归：web 模块全量测试基线零新增失败；`openspec validate --all` 通过。

## 9. 验收标准

- 个人版本机：`mcp.json` 加一条 URL 配置后，Kimi Code 会话内可见 `mcp__mdr__*` 工具并调用成功。
- 只读 8 工具与写入 7 工具全部经端到端冒烟通过。
- 团队版：无 Bearer token 时所有 tool 调用 401；viewer 角色调用写工具 403。
- 全量回归零新增失败；审计日志完整可查。
- `README.md`（MCP 章节）、`docs/architecture/ARCHITECTURE.md`（受影响时）同步更新。

## 10. 风险与未决项

| 项 | 说明 | 处置 |
|---|---|---|
| R1 Kimi Code 协议协商 | Kimi Code 实际协商的协议修订号未在官方文档写明 | 端到端冒烟（§8.4）作最终实证；若其仅支持 2025-06-18，legacy server 向下兼容可覆盖 |
| R2 Spring AI 依赖对齐 | Spring AI 1.1.x 与 Boot 3.5.0/Jackson 2.x 的具体组合需实施时验证 | D3 已备降级路径（裸 SDK + Servlet 传输） |
| R3 Resources/Prompts | Kimi Code 对 MCP resources/prompts 的支持程度未在其文档说明 | P3 待实测；不影响本期 |
| R4 Agent 幻觉写入 | Agent 可能构造非法 payload 或循环调用 | 既有校验/状态机兜底 + 客户端审批 + 服务端限流 |
| R5 端口占用 | 8082 被其它程序占用 | 配置项覆盖 + 启动失败快速报错（绑定异常透出日志） |

## 11. OpenSpec 变更名（建议）

`add-mcp-server-support` —— 新能力（mcp-server），主 spec 预计新增 `mcp-server` 能力面（P1 只读需求 ~6 条 + P2 写入需求 ~7 条 + 安全需求 ~4 条）。
