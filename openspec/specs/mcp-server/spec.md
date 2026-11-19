# mcp-server Specification

## Purpose
`mcp-server` 能力定义元数据注册系统作为 MCP（Model Context Protocol）服务端对外提供数据治理能力的契约：服务端生命周期与开关、只读与受控写入工具面及其约束、MCP 端口安全模型、调用审计与错误语义，使 AI Agent 客户端（如 Kimi Code）能够安全地访问注册库元数据并在既有治理规则内执行受控写入。

## Requirements

### Requirement: 服务端生命周期与开关

系统 MUST 内嵌 MCP 服务端，监听独立于业务端口的 MCP 端口（默认 8082），默认绑定回环地址 127.0.0.1。MCP 服务端 MUST 使用 legacy 协议代际（2025-11-25 修订）与 Streamable HTTP 传输，使走 initialize 握手的 MCP 客户端可完成协议协商。配置项 `mdr.mcp.enabled=false` 时 MCP 服务端 MUST NOT 启动；`mdr.mcp.write-tools-enabled=false` 时受控写入工具 MUST NOT 出现在工具清单中。MCP 端口的监听地址与端口 MUST 可经配置覆盖，绑定失败时系统 MUST 记录可读错误日志。

#### Scenario: 总开关关闭时服务端不启动
- **WHEN** 配置 `mdr.mcp.enabled=false` 启动应用
- **THEN** MCP 端口无监听，业务端口正常服务

#### Scenario: 写工具开关关闭时清单只含只读工具
- **WHEN** 配置 `mdr.mcp.write-tools-enabled=false` 启动应用且 MCP 客户端请求工具清单
- **THEN** 返回的工具清单 MUST NOT 包含任何受控写入工具，只读工具完整可用

#### Scenario: 客户端完成协议握手
- **WHEN** 兼容 MCP legacy 代际的客户端向 MCP 端口发起 initialize 握手
- **THEN** 握手成功并返回服务端能力声明与工具清单

### Requirement: 只读元数据访问工具

系统 MUST 提供 8 个只读工具：`search-metadata`（统一元数据搜索）、`get-item-detail`（条目详情含标识、版本、注册状态、指称与定义）、`get-item-versions`（版本历史）、`browse-data-dictionary`（数据字典浏览）、`export-data-dictionary`（数据字典导出，仅 JSON/CSV 文本形态）、`get-mapping-network`（映射网络）、`get-mapping-impact`（映射影响面）、`get-registration-process`（注册流程规则与布局）。搜索与浏览工具 MUST 支持分页参数（默认第 0 页、每页 20 条，每页上限 100）。搜索结果 MUST 与业务面统一搜索语义一致：支持关键词、实体类型过滤（限定既有类型白名单）、注册状态过滤、上下文与命名空间限定、精确标识符匹配。条目详情 MUST 返回受管链完整视图（标识、版本、注册状态）与内容链字段（指称、定义）。

#### Scenario: 关键词搜索返回分页结果
- **WHEN** 客户端调用 `search-metadata` 传入关键词与分页参数
- **THEN** 返回与业务面统一搜索一致的分页条目列表，包含标识、指称、注册状态

#### Scenario: 类型过滤超出白名单
- **WHEN** 客户端调用 `search-metadata` 传入类型白名单之外的实体类型
- **THEN** 该类型过滤条件 MUST NOT 产生匹配结果（等效空结果），MUST NOT 返回白名单外类型的数据

#### Scenario: 数据字典导出返回文本
- **WHEN** 客户端调用 `export-data-dictionary` 指定 JSON 或 CSV 格式
- **THEN** 返回对应格式的数据字典文本内容；MUST NOT 返回二进制文件流

### Requirement: 受控写入工具与边界

系统 MUST 提供 7 个受控写入工具：`draft-item`（起草条目）、`update-draft`（更新草稿）、`submit-drafts`（批量提交草稿）、`request-candidate`（请求成为候选）、`rollback-item`（回退管理项）、`run-inference-task`（运行推断任务）、`get-inference-job`（查询推断作业）。`draft-item` 的条目类型参数 MUST 限定白名单：ObjectClass、Property、DataElementConcept、ConceptualDomain、ValueDomain、DataElement、UnitOfMeasure、MeasureClass、Dimensionality；白名单外的类型 MUST 被拒绝。起草的条目 MUST 处于未提交草稿语义（初始注册状态 Incomplete、不出现在已注册列表），MUST NOT 因起草动作自动进入提交流程。`update-draft` MUST 仅允许管理状态为草稿/未完成且归属当前调用身份的条目。批量提交 MUST 沿用既有逐条校验语义并返回逐条结果。推断任务 MUST 复用既有协作任务清单（complete-metadata、explain-selection、selection-chat）；推断功能未启用时 MUST 返回"功能未启用"错误。

#### Scenario: 起草核心类型条目
- **WHEN** 客户端调用 `draft-item` 传入白名单内类型与合法字段
- **THEN** 系统创建条目，初始注册状态为 Incomplete，条目归属当前身份，不出现在已注册列表

#### Scenario: 起草白名单外类型
- **WHEN** 客户端调用 `draft-item` 传入白名单外条目类型
- **THEN** 调用被拒绝并返回参数校验错误，MUST NOT 产生任何数据库写入

#### Scenario: 更新非归属草稿
- **WHEN** 客户端调用 `update-draft` 更新归属另一身份的草稿
- **THEN** 调用被拒绝（等效业务面归属校验），MUST NOT 修改该条目

#### Scenario: 推断功能未启用
- **WHEN** 推断功能开关关闭时客户端调用 `run-inference-task`
- **THEN** 返回"功能未启用"错误，MUST NOT 创建推断作业

### Requirement: 不暴露面边界

MCP 工具面 MUST NOT 暴露以下能力：任何条目删除工具；注册状态推进（治理裁决流转）端点；系统管理面（账号管理、数据源配置、危险区域、恢复出厂设置）；注册流程布局的修改；联邦（federation）端点。经 MCP 无法触达上述能力的任何等价语义。

#### Scenario: 工具清单不含删除与状态推进
- **WHEN** 客户端请求工具清单
- **THEN** 清单 MUST NOT 包含名称或语义等效于条目删除、注册状态推进、系统管理、联邦的工具

### Requirement: MCP 端口安全模型

MCP 端口的访问控制 MUST 独立于业务端口规则：来源地址非回环（127.0.0.1 或 ::1）的请求 MUST 被拒绝（403），与监听地址配置无关（双保险）。个人版部署 MUST 以隐式唯一管理员身份处理 MCP 调用，无需凭证。团队版部署 MUST 要求 `Authorization: Bearer` 凭证：缺失或无效凭证 MUST 返回 401；只读工具要求任意有效身份，受控写入工具 MUST 要求 REGISTRAR 角色，角色不足 MUST 返回 403。同一身份的调用频率超过每秒 30 次时系统 MUST 返回 429。

#### Scenario: 非回环来源被拒绝
- **WHEN** 来自非回环地址的请求到达 MCP 端口
- **THEN** 返回 403，MUST NOT 进入 MCP 协议处理

#### Scenario: 团队版缺失凭证
- **WHEN** 团队版部署收到无 `Authorization` 头的 MCP 请求
- **THEN** 返回 401

#### Scenario: 角色不足调用写入工具
- **WHEN** 团队版中具有有效身份但无 REGISTRAR 角色的调用方执行受控写入工具
- **THEN** 返回 403，MUST NOT 产生任何写入

#### Scenario: 超限流阈值
- **WHEN** 同一身份在 1 秒内向 MCP 端口发起超过 30 次调用
- **THEN** 超限部分 MUST 返回 429

### Requirement: 调用审计

系统 MUST 记录每次 MCP 工具调用：时间戳、调用身份、工具名、参数摘要（SHA-256）、结果状态与耗时。审计记录 MUST 写入独立日志（与业务日志分离的 appender），MUST NOT 写入数据库（零 schema 变更）。审计记录 MUST NOT 包含明文参数中的敏感内容（口令、令牌）。

#### Scenario: 写入调用产生审计记录
- **WHEN** 任意受控写入工具调用完成（无论成败）
- **THEN** 独立日志中出现一条含身份、工具名、参数摘要、结果状态与耗时的记录

### Requirement: 错误透传语义

MCP 工具调用触发的业务校验失败 MUST 以 `[错误码] 中文消息` 文本形态返回工具错误结果，错误码 MUST 与业务面既有错误码一致，使调用方可以程序化识别并修正重试。系统错误（未捕获异常）MUST 返回通用错误文本，MUST NOT 泄露内部堆栈或敏感配置。

#### Scenario: 业务校验失败透传
- **WHEN** 客户端调用 `submit-drafts` 提交一个非草稿状态的条目
- **THEN** 工具返回与业务面一致的 `[错误码] 消息` 错误文本，逐条结果语义不变

#### Scenario: 系统错误不泄露内部细节
- **WHEN** 工具执行中发生未捕获异常
- **THEN** 调用方收到通用错误文本，响应中 MUST NOT 包含堆栈、连接串或内部路径
