# acp-agent-protocol Specification

## Purpose
TBD - created by archiving change acp-agent-protocol. Update Purpose after archive.

## Requirements

### Requirement: ACP 协议接入

系统 SHALL 以 ACP（Agent Client Protocol，JSON-RPC 2.0 over stdio，NDJSON 线格式）调用本地 Coding Agent：每 Agent 维护一个常驻 ACP 进程（懒启动、崩溃后下次使用重建、应用关闭时销毁）；每次协作作业 SHALL 经 `session/new`（隔离临时工作目录、空 MCP 列表）→ `session/prompt`（单文本块）→ 累积 `session/update` 中 `agent_message_chunk` 文本 → `session/prompt` 响应完成（记录 stopReason）→ 尽力 `session/close` 的流程执行。作业取消 SHALL 发送 `session/cancel` 通知；超时沿用既有作业超时配置。对端反向请求（权限请求、文件读写等）SHALL 以防卫性 JSON-RPC 错误应答，不挂起连接。

#### Scenario: ACP 作业执行

- **WHEN** Registrar 发起任一协作作业（complete-metadata / selection-chat）
- **THEN** 系统经常驻 ACP 进程创建会话并提示，结果为 agent_message_chunk 累积文本，经任务 parseOutput 定型；调试追踪中 prompt/rawOutput 照常可见

#### Scenario: 进程崩溃重建

- **WHEN** 某 Agent 的常驻 ACP 进程意外退出
- **THEN** 该 Agent 的下一次探测或作业自动重建进程并重新握手，不污染其它 Agent

### Requirement: ACP 握手探测

Agent 可用性探测 SHALL 通过 ACP `initialize` 握手完成（最小 clientCapabilities，10s 超时）：成功为可用，探测详情含对端 `agentInfo` 名称与版本；失败详情含错误摘要。MUST NOT 再使用 `<命令> --version` 探测。

#### Scenario: 探测显示对端版本

- **WHEN** SUPER_ADMIN 在 AI 辅助设置中触发重新探测
- **THEN** 可用 Agent 的状态详情显示其 ACP agentInfo（如 `Codex 1.10.0`）

### Requirement: 内置 Agent 定义与设置迁移

内置 Agent 的启动命令 SHALL 为 ACP server 命令：claude→`claude-agent-acp`、codex→`codex-acp`、kimi→`kimi acp`。设置文件格式版本升级为 2：加载 v1 文件时 MUST 丢弃内置条目的 command/args 覆盖并保留 enabled/displayName/defaultProvider；自定义条目的 command/args 解释为 ACP server 启动命令。Agent 视图 kind 为 `acp`。

#### Scenario: v1 设置迁移

- **WHEN** 存在含旧 CLI 参数（如 `exec --skip-git-repo-check -`、`-p --output-format text`）的 v1 inference-agents.yml
- **THEN** 加载后内置条目使用 ACP 默认命令，启用状态与默认提供方保持不变，下次保存写入 v2

### Requirement: CLI 模式废弃

`CliAgentProvider`、`{prompt}` 占位符机制、stdout 文本刮取路径 MUST 删除；协作任务输入校验、提示词组装、候选解析（含回显占位跳过）、调试追踪、作业生命周期与权限语义 MUST 保持不变；`/api/v1/**` 端点契约不变。

#### Scenario: 任务层行为不变

- **WHEN** 以 ACP 传输执行 complete-metadata
- **THEN** 候选解析、每字段候选数上限、降级标注等行为与 CLI 模式时期完全一致（由既有测试锁定）
