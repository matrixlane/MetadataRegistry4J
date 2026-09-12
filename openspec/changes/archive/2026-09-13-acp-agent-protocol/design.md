# 设计：Coding Agent 接入切换为 ACP 协议

## Context

本机 ACP 就绪矩阵（已实测 initialize 握手）：kimi 0.41.0 原生 `kimi acp`；codex 0.154.0 经 `@agentclientprotocol/codex-acp@1.10.0`（`dist/index.js`，Node 入口）；claude 2.1.267 经 `@agentclientprotocol/claude-agent-acp@0.75.1`。线格式为 **NDJSON**（每行一个 JSON-RPC 2.0 对象；实测单行 initialize 即发即回）。`@agentclientprotocol/sdk` 是 TS 实现，仅作协议参考，不进 Java 进程。

## Decisions

### D1：AcpAgentProvider —— 常驻进程 + 每作业一会话

`MetadataInferenceProvider` 接口不变（status/probe/infer/cancel），实现替换：

- **进程生命周期**：每 Agent 一个常驻 ACP 进程（懒启动于首次 infer/probe）；`initialize` 握手成功后才可用；进程意外退出 → 下次使用时重建；应用关闭时 `@PreDestroy` 销毁全部。同一进程内按 sessionId 支持并发作业（ACP 原生多会话），不做串行化（如个别适配器有并发问题再回退串行）。
- **作业执行**：`session/new`（cwd=独立临时目录，沿用工作目录隔离约定；`mcpServers: []`）→ `session/prompt`（`prompt: [{type:"text", text: <提示词>}]`）→ 累积 `session/update` 通知中 `sessionUpdate=="agent_message_chunk"` 的文本 → `session/prompt` 响应到达（含 `stopReason`）即完成 → 尽力 `session/close`（失败忽略）。结果文本 = 累积的 assistant 文本，交给任务 `parseOutput`（契约不变）。
- **取消**：作业取消 → 发 `session/cancel` 通知；5s 内未终止则销毁该 Agent 进程（影响该进程上其它作业，记录日志；常态下单作业不会走到这步）。
- **超时**：沿用 `mdr.inference.cli.timeout-seconds`（配置键保留、注释更新为 ACP 语义；不改键名避免配置断裂）；超时 = `session/cancel` + 作业失败。
- **错误映射**：JSON-RPC error / 进程崩溃 / 握手失败 → `InferenceException`，消息含对端错误摘要；探测/执行日志记录 Agent id 与阶段。

### D2：精简 ACP 客户端（NDJSON over stdio）

新增 `AcpConnection`（provider 包内）：封装 ProcessBuilder 进程 + 守护读线程按行解析；请求-响应按 `id` 配对（`ConcurrentHashMap<Long, CompletableFuture<JsonNode>>`）；通知（无 id）路由给按 sessionId 注册的监听器；写线程安全（单行加锁写）。JSON 用既有 Jackson。只实现客户端子集：`initialize`、`session/new`、`session/prompt`、`session/cancel`（通知）、`session/close`（尽力）；忽略 `agent_thought_chunk`、tool_call、plan 等其它 update 类型；对端反向请求（如 `session/request_permission`、`fs/read_text_file`）一律回 JSON-RPC 方法未找到错误（我们声明的 clientCapabilities 不含 fs/终端能力，正常 Agent 不应发起；防御性应答避免对端挂起）。

### D3：内置 Agent 定义与设置迁移（v1→v2）

```java
BUILTINS = [
  ("claude", "Claude Code (ACP)", "claude-agent-acp", []),
  ("codex",  "Codex CLI (ACP)",  "codex-acp",        []),
  ("kimi",   "Kimi CLI (ACP)",   "kimi",             ["acp"])
]
```

- 设置文件 `inference-agents.yml` 格式版本 1→2：加载 v1 时**丢弃内置条目的 command/args 覆盖**（旧 CLI 参数在 ACP 下无意义），保留 enabled/displayName；自定义条目原样保留（其 command 现在解释为 ACP server 命令，UI 文案说明）；下次保存写 v2。`defaultProvider` 保留。
- 自定义 Agent 的 command/args 语义改为「ACP server 启动命令」；UI 添加/编辑表单的占位提示同步修改。
- 视图 `InferenceAgentView.kind` 由 `"cli"` 改 `"acp"`；前端类型同步。
- `WindowsCommandResolver` 保留（适配器的 `codex-acp.cmd`/`claude-agent-acp.cmd` 仍需 .cmd 包装）；`{prompt}` 占位符机制删除。
- 已知环境事项：本机 npm 全局 bin 未生成 `codex-acp`/`claude-agent-acp` shim 时的处置 = 重装两包生成 shim，或自定义条目用 `node <dist/index.js 绝对路径>`（WindowsCommandResolver 对 node.exe 直解析）。部署时为用户修复 shim。

### D4：探测 = initialize 握手

`probe()`：启动临时 ACP 进程 → 10s 内完成 `initialize`（clientCapabilities 最小集：`fs` 全 false、无 terminal）→ 读 `agentInfo.name@version` 作为详情 → 销毁进程。不再执行 `<cmd> --version`。探测失败消息含握手错误摘要。

### D5：删除清单

- `CliAgentProvider` 与其测试、`PROMPT_PLACEHOLDER`、stdin 提示词写入路径（`ProcessRunner.stdin()` 接口保留以兼容 fake，但 ACP 实现不再需要调用方写提示词入 stdin——AcpConnection 内部仍用 stdin 写 NDJSON 帧）。
- 文档/注释/i18n 中「CLI 一次性调用」「--version 探测」等过时表述；openspec 未归档变更（ai-metadata-inference / ai-inference-agent-selection / ai-governance-assistant / ai-assist-ux-revamp）中加变更记录注明传输层已切换 ACP。

### D6：不变式（回归锚点）

- 端点：`/api/v1/inference/**`、`/api/v1/assistant/**` 契约与权限（REGISTRAR）不变。
- 任务层：complete-metadata / selection-chat 的输入校验、提示词组装、输出解析（CandidateJsonExtractor 含回显跳过、TEXT 清洗）不变——ACP 不再回显提示词，占位跳过逻辑作为防御保留。
- 调试追踪：prompt/rawOutput 捕获、exchanges 端点不变。
- 作业语义：异步、轮询、取消、TTL、创建者隔离不变。

## Risks / Trade-offs

- [适配器并发会话兼容性未知] → ACP 规范支持多会话；若某适配器串行报错，回退为进程级互斥锁（预留 `serialized` 开关）。
- [常驻进程内存驻留] → 每 Agent 一个进程，量级与 CLI 常驻相当；崩溃自动重建。
- [Node 入口依赖] → codex/claude 适配器是 Node 程序，比原生 exe 启动慢一点；常驻模式摊销。
- [会话泄漏] → 作业结束尽力 close；进程级 TTL 由作业清理保证，进程本身常驻属预期。
- [老设置文件语义漂移] → v1→v2 迁移有测试锁定。

## Migration Plan

1. ACP 客户端 + AcpAgentProvider + 注册表/探测/迁移，任务层不动，既有测试锁定任务行为。
2. 前端 kind 字面量与表单提示同步。
3. 部署：修复 npm shim、迁移用户设置文件、重打包重启、以真实作业验证三 Agent。
回滚：本变更独立提交，可整体 revert 回 CLI 模式（用户已明确废弃 CLI，回滚仅为兜底）。
