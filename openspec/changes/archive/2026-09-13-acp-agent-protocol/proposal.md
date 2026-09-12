# 提案：Coding Agent 接入切换为 ACP 协议

## Why

当前 CLI 一次性进程模式（`claude -p` / `codex exec -` / `kimi -p`）存在结构性缺陷：每次调用冷启动一个完整 Agent 进程（秒级开销）；结果为纯文本流，需从 stdout 里刮取 JSON，已被 codex 的提示词回显咬过一次（占位 `...` 候选事故）；多轮对话只能靠历史重注入模拟。

ACP（Agent Client Protocol，JSON-RPC 2.0 over stdio）是专为「应用 ↔ 本地 Agent 进程」设计的开放标准。本机三 Agent 的 ACP 路径已实测就绪：**kimi 原生 `kimi acp`**、**codex 经官方适配器 `@agentclientprotocol/codex-acp`**（initialize 握手已验证）、**claude 经 `@agentclientprotocol/claude-agent-acp`**（握手已验证）。用户决策：CLI 调用方式整体废弃，统一改为 ACP。

## What Changes

- 删除 `CliAgentProvider`（一次性进程 + stdout 刮取 + `{prompt}` 占位符机制），新增 `AcpAgentProvider`：每 Agent 一个常驻 ACP 进程（懒启动、崩溃后下次使用重建），每次作业 = `session/new`（隔离临时 cwd、无 MCP）→ `session/prompt` → 收集 `session/update` 的 `agent_message_chunk` 文本 → `session/close`。
- 可用性探测改为 ACP `initialize` 握手（替代 `<cmd> --version`），探测详情显示对端 `agentInfo`（名称/版本）。
- 内置 Agent 定义改为 ACP server 命令：claude→`claude-agent-acp`、codex→`codex-acp`、kimi→`kimi acp`；设置文件格式 v1→v2 迁移（丢弃内置条目的旧 CLI 参数覆盖，保留 enabled/displayName/defaultProvider）。
- 协作任务（complete-metadata / selection-chat）、作业模型（异步轮询/取消/TTL/创建者隔离）、调试追踪（prompt/rawOutput 捕获）、候选契约（CandidateJsonExtractor，含回显占位跳过）**全部不变**——仅传输层替换。
- 前端：Agent 条目 kind 由 `cli` 改 `acp`；设置卡片与调试侧栏无结构性改动。

## Impact

- 受影响代码：`web/inference/provider/**`（删 CliAgentProvider、增 ACP 客户端与 AcpAgentProvider）、`InferenceProviderRegistry`、设置迁移；前端 `api/inference.ts` kind 字面量与设置页显示。
- 依赖：无新 Maven/npm 依赖（Jackson 已在）；要求本机已全局安装 `@agentclientprotocol/codex-acp`、`@agentclientprotocol/claude-agent-acp`（已在用户机器验证）。
- 无 schema 变更；`/api/v1/**` 端点契约不变。
