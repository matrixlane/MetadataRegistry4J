# Proposal: add-mcp-server-support

## Why

本项目（ISO/IEC 11179-3 元数据注册中心）目前只能通过 Web UI 与 REST API 由人直接操作。随着本机 AI Agent（Kimi Code、Claude Code 等）成为日常开发工具，让这些 Agent 直接访问权威注册库（WS/T 363/364 基准 + 用户自有条目）、并在既有校验约束下执行受控治理动作（起草、补全、提交），是把"数据治理能力"变成"Agent 可用工具"的关键一步。MCP（Model Context Protocol）是这类对接的事实标准协议。

## What Changes

- 应用内嵌 MCP Server（legacy 2025-11-25 协议代际，Streamable HTTP 传输），独立端口默认 8082、默认绑定回环 127.0.0.1，配置项 `mdr.mcp.enabled`（总开关）与 `mdr.mcp.write-tools-enabled`（写工具开关）。
- 新增 **8 个只读工具**：统一元数据搜索、条目详情（含受管链全视图）、版本历史、数据字典浏览、数据字典导出（JSON/CSV 文本）、映射网络、映射影响面、注册流程规则与布局。
- 新增 **7 个受控写入工具**：起草条目（9 类核心类型白名单）、更新草稿（仅 DRAFT/INCOMPLETE 且归属当前身份）、批量提交、请求候选、回退、运行推断任务（复用既有 assistant 任务清单）、查询推断作业。
- 写路径铁律：所有写工具只调用既有 Service（如 `WorkspaceService.submit`、各类型 `*Service.create/update`），绝不旁路裸 SQL；正交链闭环、状态机、幂等、提交记录等不变式由既有层天然保证。
- 不暴露：任何删除工具、注册状态推进（transitions）、系统管理面（账号/数据源/危险区域/出厂重置）、联邦端点。
- 安全：MCP 端口专用过滤器链——非回环来源 403；个人版隐式唯一 SUPER_ADMIN 身份；团队版 Bearer JWT（复用既有 JwtService），读工具需有效身份、写工具需 REGISTRAR 角色；每身份简单限流（30 req/s，超限 429）。
- 审计：每次工具调用记录（时间戳、身份、工具名、参数 SHA-256 摘要、结果状态、耗时）至独立日志 appender，零数据库 schema 变更。
- 业务错误透传：既有 `BusinessException` 以 `[code] message` 文本返回工具结果，Agent 可程序化重试。
- 明确不做：Resources/Prompts 能力面（P3 待 Kimi Code 客户端支持实测）、OAuth 授权服务器、对外公网部署形态。

## Capabilities

### New Capabilities

- `mcp-server`: MCP 协议服务端能力——内嵌 server 的生命周期与开关、工具注册面（8 只读 + 7 写入及各自参数/约束）、MCP 端口安全模型（回环绑定、身份与角色映射、限流）、调用审计、错误透传与"不暴露面"边界。

### Modified Capabilities

（无。本变更不改动任何既有能力的 spec 级行为；底层工作空间、推断、状态机等语义完全复用既有 spec。）

## Impact

- **后端（metadata-registry-web）**：新增 `mcp` 顶层包（server 装配、端口安全过滤器链、身份解析、7 个工具类、审计服务、DTO）；`application.yml` 增加 `mdr.mcp.*` 配置段；pom 引入 Spring AI MCP server WebMVC starter（冲突时降级裸 MCP Java SDK + Servlet 传输，Jackson 2.x 对齐）。
- **API**：不改动任何既有 `/api/v1/**` 端点契约；MCP 面为全新独立端口。
- **前端**：无。
- **数据库**：零 schema 变更、零迁移脚本。
- **文档**：README.md 新增 MCP 章节（配置示例、Kimi Code mcp.json 与权限规则推荐）；架构变化时同步 ARCHITECTURE.md。
- **风险**：Kimi Code 实际协议协商版本未在官方文档写明（端到端冒烟作最终实证）；Spring AI 1.1.x 与 Boot 3.5.0/Jackson 2.x 组合需实施时验证（已备降级路径）。
