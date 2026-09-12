# 设计：AI 元数据推断

## Context

MetadataRegistry4j 是 Spring Boot 3.3.4（Java 17）多模块后端 + Vue 3 前端。数据元（`DataElement`）是薄实体，定义/指称/来源文档等挂在 `Item`→`Designation`/`Definition`/`ReferenceDocument` 继承关联结构上；写入统一走 `DataElementService.update()` → `ItemAdministrationService.applyDesignations/applyDefinitions()`，含版本快照。

当前代码库中：**没有**任何外部进程调用（ProcessBuilder）、LLM SDK、`@EnableAsync`、任务轮询/SSE 基础设施；前端 axios 默认超时 30s。安全上 `/api/v1/**` 的 GET 需登录、写操作需 REGISTRAR/SUPER_ADMIN。配置惯例为 `application.yml` + `${ENV:default}`，并自动加载 `${user.home}/.metadata-registry/application-local.yml`。

利益相关方：注册人员（使用推断生成定义/指称）、系统管理员（配置提供方与开关）、部署方（决定启用 CLI 还是直连，或整体关闭）。

## Goals / Non-Goals

**Goals:**

- 应用内一键对元数据项目发起推断：以项目**已知**元数据为输入，产出**缺失**的定义/指称候选（每字段可多个，附理由）。
- 首选对接**本机 Coding Agent CLI**（Claude Code / Codex / Kimi Code CLI），认证完全由 CLI 自身承载，应用不保存任何厂商 API Key。
- 提供 OpenAI 兼容直连作为兜底提供方（Key 仅存服务端环境变量）。
- 推断为异步作业：前端轮询状态，可取消；作业状态存内存，零数据库表、零 Flyway 迁移。
- 候选只**填入编辑表单**，由人确认后经既有保存路径落库（版本历史不变）。
- 提示词内嵌 ISO/IEC 11179-1:2023 §6.2–6.3 定义构成规则（单一概念、属+种差、不得循环定义等）与 §6.4 命名原则（命名细则见 11179-31:2023 §5.5）的写作要求。

  > 更正记录（2026-09-08）：早期稿误引 ISO/IEC 11179-4（定义构成）与 11179-5（命名原则）。本地权威库（Milvus `anythingllm_metadata_registry` / `ISO/IEC 11179 标准原文目录`）仅含 2023 版第 1、3、6、30–33、35 部分，无 -4/-5；按 2023 版原文核实：定义构成规则在 **11179-1:2023 §6.2–6.3**（含 6.3.3 属+种差、6.3.4 数据定义细则），命名原则在 **11179-1:2023 §6.4**，命名细则在 **11179-31:2023 §5.5**。

**Non-Goals:**

- 分类建议、数据元概念/值域推荐（v2，需要向提示词注入分类方案节点清单）。
- 推断历史/采纳率持久化（涉及三方言迁移，另案处理）。
- 多用户并发的作业隔离与配额管理（本期作业仅存内存、按登录用户记录创建者，仅创建者可见）。
- 对 CLI 输出做流式（SSE/WebSocket）推送（轮询已满足体验）。

## Decisions

### D1：以进程执行对接本地 CLI，而非内嵌 SDK 或 MCP/ACP

采用 `ProcessBuilder` 直接启动本机 CLI 进程。**参数以数组传递、不经 shell**（杜绝命令注入）；**提示词一律经 stdin 传入**（避开 Windows 命令行 ~32k 长度上限与引号转义问题）。

- 备选：内嵌各厂商 SDK / spring-ai —— 仍需 API Key，违背本变更初衷，仅作兜底（见 D4）。
- 备选：MCP/ACP 协议对接 —— 生态尚不成熟、引入长驻连接与协议栈复杂度，对「发提示词拿 JSON」这一单次请求-响应场景过度设计。

### D2：CLI 命令抽象为「预设 + 可覆盖模板」

内置三个预设（均可被 `mdr.inference.cli.command`/`args` 整体覆盖，以适配任何同类 CLI）。提示词默认走 **stdin**；若 args 中包含 `{prompt}` 占位符，则替换为实际提示词作为命令行参数传入、不再写 stdin（为不支持 stdin 的 CLI 兜底；此方式受操作系统命令行长度上限约束，Windows 约 32k，超大提示词应优先选用 stdin 型 CLI）：

| 预设 | 命令 | 提示词通道 | 实测版本 |
|---|---|---|---|
| `claude` | `claude -p --output-format text` | stdin | Claude Code 2.1.259 ✅ |
| `codex` | `codex exec -` | stdin（`-` 即读 stdin） | codex-cli 0.153.0 ✅ |
| `kimi` | `kimi -p {prompt}` | 命令行参数（kimi `-p` 强制要求参数，不支持 stdin） | Kimi Code CLI 0.41.0 ✅ |

可用性探测对含 `{prompt}` 的命令退化为 `<可执行文件> --version`，避免把占位符或悬空参数传给 CLI。

**可用性探测**：`GET /api/v1/inference/providers` 时对该命令执行 `--version` 探测（3s 超时，结果缓存 60s），不存在/未认证时前端按钮置灰并提示。

### D3：沙箱与资源约束

每次运行在 `mdr.inference.cli.work-dir`（默认 `${java.io.tmpdir}/mdr-inference`）下创建**独立空目录**作为进程 cwd，使 Agent 的文件视野与仓库隔离；超时（默认 180s）`destroyForcibly()` 强杀进程树；stdout 上限 1MB，超限即失败。

### D4：提供方 SPI + 直连兜底

`MetadataInferenceProvider` 接口（`id()`/`status()`/`infer(request)`）。`CliAgentProvider` 为主；`DirectApiProvider` 用 JDK `HttpClient` POST `{baseUrl}/chat/completions`（OpenAI 兼容，可指向 OpenAI/DeepSeek/Kimi/Qwen 等），Key 仅取 `MDR_LLM_API_KEY`。不新增任何第三方依赖。

> 变更记录：`DirectApiProvider` 及 `mdr.inference.direct.*` 配置已按后续决定从代码中整体移除（连同 `InferenceAgentSettings` 的 `direct` 条目）；`MetadataInferenceProvider` SPI 保留，直连能力若将来恢复另行处置。

### D5：异步作业存内存，不建表

新增 `@EnableAsync` 配置类 + 专用 `ThreadPoolTaskExecutor`（复用既有但闲置的 `MdcTaskDecorator` 传递日志上下文）。作业对象（id、itemId、创建者、状态、候选、错误、时间戳）存 `ConcurrentHashMap`，TTL 30 分钟由 `@Scheduled` 清理。理由：推断结果是瞬态草稿，采纳后的结果经既有表单保存路径落库，无需作业持久化；同时规避 SQLite/SQL Server/达梦三方言迁移成本。

### D6：严格 JSON 输出契约 + 宽容解析

提示词要求 Agent **只输出一个 JSON 对象**：

```json
{
  "candidates": [
    {"field": "definition", "language": "zh-CN", "text": "...", "rationale": "..."},
    {"field": "designation", "language": "en-US", "context": "...", "text": "...", "rationale": "..."}
  ]
}
```

解析端从 stdout 中提取**首个括号配平的 `{...}`** 片段再反序列化（容忍 CLI 在头尾附加的日志/包装文本）；无法解析时作业置 FAILED 并回传输出片段（截断至 2k）便于排查。

### D7：候选经既有保存路径落库

推断服务**只读** Item 图组装提示词、只返回候选 DTO；采纳候选用前端把候选文本填入 `ItemFormDialog` 的指称/定义编辑区，用户确认后走 `PUT /api/v1/data-elements/{id}`（`DataElementService.update` → `ItemAdministrationService.apply*`，含版本快照）。不新增任何写端点，保证注册流程、版本历史、关联完整性校验（`MetadataObjectAssociationService.validateCompleteness`）全部不变。

### D8：安全边界

- 新端点挂 `/api/v1/inference/**`：GET 需登录，POST/DELETE 需 REGISTRAR/SUPER_ADMIN（既有 `SecurityConfig` 规则自动覆盖，零改动）。
- CLI 命令/参数/工作目录**只能来自服务端配置**，请求体不接受任何命令片段，杜绝以请求注入命令。
- 作业按创建者隔离：`GET/DELETE /jobs/{id}` 仅作业创建者（或 SUPER_ADMIN）可见/可操作。
- 前端候选在填入表单前做纯文本处理（不渲染 HTML），防止 Agent 输出携带的标记注入页面。

## Risks / Trade-offs

- [CLI 未安装/未登录导致功能不可用] → providers 探测 + 前端置灰与明确提示；可切换直连兜底或整体关闭（`mdr.inference.enabled=false`）。
- [CLI 以应用启动用户身份运行，权限过大] → 独立空工作目录 + 超时常驻上限；文档要求以普通用户身份运行应用，不要以管理员/root 运行。
- [Agent 输出不稳定，JSON 解析失败] → 宽容提取 + 失败作业回传原始输出片段；提示词中给出 schema 与「仅输出 JSON」的强约束。
- [内存作业在应用重启后丢失] → 可接受：推断是可重试的瞬态操作，前端对失效作业提示重新发起。
- [元数据内容经 CLI 发往其模型后端] → 与直接调 API 暴露面相同；涉密部署应整体关闭本功能。配置说明中明示。
- [长提示词（大项目）推高 CLI 耗时/费用] → 提示词组装设字段截断上限（如来源文档标题/标识符含全文摘录上限），候选数上限 5。

## Migration Plan

纯增量：新增代码与配置，无 schema 变更、无既有行为变更。回滚 = 移除 `mdr.inference.*` 配置（或设 `enabled=false`）并回退代码；无数据迁移需要回退。

## Open Questions

- ~~各 CLI 预设的精确非交互参数~~ 已在本机实测核实并落入 D2 表格（claude 2.1.259 / codex 0.153.0 / kimi 0.41.0）；kimi 需 `{prompt}` 参数占位符机制（已实现）。
- 定义/指称候选是否需要携带 acceptability（首选/接纳）建议：本期由用户在表单内自行选择，Agent 不代拟。


## 变更记录（2026-09：acp-agent-protocol）

本地 Coding Agent 调用方式由「CLI 一次性调用」（`--version` 探测、`{prompt}` 占位符、stdout 文本刮取）整体切换为 **ACP 协议**（JSON-RPC 2.0 over stdio/NDJSON，每 Agent 常驻进程懒启动 + initialize 握手，每作业 session/new→prompt→累积 agent_message_chunk→close）。
