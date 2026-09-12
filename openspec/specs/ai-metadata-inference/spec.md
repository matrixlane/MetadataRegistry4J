# ai-metadata-inference Specification

## Purpose
TBD - created by archiving change ai-metadata-inference. Update Purpose after archive.

## Requirements

### Requirement: 推断提供方抽象与可用性探测

系统 SHALL 维护一个推断 Agent 注册表：内置已知 Agent（claude、codex、kimi）与用户自定义 Agent 各自构成独立提供方（提供方 id 即 Agent id），经 ACP（Agent Client Protocol）常驻进程调用（2026-09 acp-agent-protocol：CLI 一次性进程模式已废弃）。每个 Agent 条目 SHALL 具有启用状态；仅**已启用**的 Agent 可被推断作业选用。系统 SHALL 提供 `GET /api/v1/inference/providers` 端点，仅返回**已启用且探测可用**的提供方及其标识、显示名与默认标记；Agent 可用性 MUST 通过 ACP `initialize` 握手探测获得（10s 超时）。Agent 的启动命令、参数与工作目录 MUST 只能来自服务端配置（静态配置或 SUPER_ADMIN 保存的设置），请求体 MUST NOT 接受任何命令片段。

#### Scenario: 查询提供方可用性

- **WHEN** 已登录用户请求 `GET /api/v1/inference/providers`
- **THEN** 系统返回已启用 Agent 的标识、显示名与可用状态；握手失败的 Agent 标记为不可用且不出现在可选列表中

#### Scenario: 查询可用提供方

- **WHEN** 已登录用户请求 `GET /api/v1/inference/providers`
- **THEN** 系统仅返回已启用且探测可用的提供方列表，并标注默认提供方；被禁用或探测失败的 Agent 不出现在列表中

#### Scenario: 选用被禁用的 Agent

- **WHEN** 推断作业请求指定一个已禁用或不存在的 Agent id
- **THEN** 系统拒绝该请求并返回参数校验错误

#### Scenario: 功能整体关闭

- **WHEN** `mdr.inference.enabled=false` 时请求任一 `/api/v1/inference/**` 端点
- **THEN** 系统返回功能未启用的错误响应，不启动任何进程或外部调用

### Requirement: 本地 CLI 推断执行

（2026-09 acp-agent-protocol：CLI 一次性进程模式已废弃，执行语义由 ACP 承载。）系统 SHALL 经 ACP（JSON-RPC 2.0 over stdio，NDJSON 线格式）以常驻进程调用本地 Coding Agent：`initialize` 握手后，每次作业经 `session/new`（独立临时工作目录、空 MCP 列表）→ `session/prompt`（单文本块）→ 累积 `session/update` 中 `agent_message_chunk` 文本 → 尽力 `session/close`。超过配置超时的作业 MUST 发送 `session/cancel` 并标记失败；进程崩溃 MUST 在下次使用时自动重建。

#### Scenario: 成功执行 CLI 推断

- **WHEN** 推断作业以 ACP 提供方执行且 Agent 在超时内返回
- **THEN** 系统将累积的 agent_message_chunk 文本交由任务输出解析器处理（候选任务提取括号配平的 JSON 对象，跳过提示词回显的占位对象）

#### Scenario: CLI 超时

- **WHEN** `session/prompt` 超过 `mdr.inference.cli.timeout-seconds`
- **THEN** 系统发送 `session/cancel`、将作业标记为失败，错误信息说明超时

#### Scenario: CLI 输出无法解析

- **WHEN** Agent 正常返回但文本中不存在可解析的 JSON 对象
- **THEN** 作业标记为失败，并回传截断后的原始输出片段供排查

### Requirement: 提示词组装

系统 SHALL 以目标项目的既有元数据组装结构化提示词：指称（含语种、上下文、来源文档）、定义、数据元概念、值域、属性槽与注册信息；并 SHALL 指明请求推断的缺失字段、目标语种与候选数量。提示词 MUST 内嵌符合 ISO/IEC 11179-1:2023 §6.2–6.3 定义构成规则与 §6.4 命名原则（命名细则见 ISO/IEC 11179-31:2023 §5.5）的写作要求，并 MUST 要求 Agent 仅输出符合给定 schema 的单个 JSON 对象（每字段候选含 text 与 rationale，指称候选可含 language 与 context）。超长字段 MUST 按上限截断。

#### Scenario: 已知元数据进入提示词

- **WHEN** 对已具备来源文档、上下文与指称、但缺少定义的数据元发起定义推断
- **THEN** 提示词包含该数据元的指称文本、语种、上下文名称、来源文档标题/标识符，以及数据元概念与值域的名称（如存在），且不包含为空的字段段落

#### Scenario: 候选数量约束

- **WHEN** 请求候选数为 3
- **THEN** 提示词要求每个字段给出至多 3 个候选，且解析结果中每字段候选数不超过 3

### Requirement: 异步推断作业

系统 SHALL 以异步作业执行推断：`POST /api/v1/inference/items/{itemId}/jobs` 创建作业并立即返回作业标识；`GET /api/v1/inference/jobs/{jobId}` 返回作业状态（RUNNING/SUCCEEDED/FAILED/CANCELLED）与候选结果；`DELETE /api/v1/inference/jobs/{jobId}` 取消作业并终止对应进程。作业状态 MUST 仅存于内存并设存活期清理，MUST NOT 新增数据库表。作业 MUST 仅对创建者（或 SUPER_ADMIN）可见与可操作。

#### Scenario: 创建并轮询作业

- **WHEN** 具有 REGISTRAR 角色的用户对存在的项目创建推断作业
- **THEN** 系统立即返回作业标识且作业异步执行；轮询状态在完成后返回候选列表

#### Scenario: 取消作业

- **WHEN** 作业创建者对运行中的作业执行取消
- **THEN** 系统终止对应进程，作业状态变为 CANCELLED

#### Scenario: 越权访问他人作业

- **WHEN** 非创建者且非 SUPER_ADMIN 的用户查询或取消他人作业
- **THEN** 系统返回资源不存在或无权访问的错误，不泄露作业内容

### Requirement: 候选采纳经既有保存路径

推断服务 MUST 只读取项目数据并返回候选，MUST NOT 直接修改任何元数据实体。前端 SHALL 将用户选中的候选填入编辑表单，由用户确认后经既有 `PUT /api/v1/data-elements/{id}` 保存路径落库。候选在前端 MUST 以纯文本方式展示，不得渲染为 HTML。

#### Scenario: 选择候选并保存

- **WHEN** 用户在候选对话框中选中一个定义候选并确认填入
- **THEN** 候选文本进入编辑表单的定义编辑区，保存后版本历史记录一次 UPDATE 快照

#### Scenario: 不采纳任何候选

- **WHEN** 用户关闭候选对话框而未选择
- **THEN** 表单内容不发生任何变化，不产生任何写请求

### Requirement: Agent 设置的持久化与立即生效

系统 SHALL 将 Agent 启用集合、默认提供方、命令/参数覆盖及自定义 Agent 定义持久化到用户主目录下的独立设置文件（`~/.metadata-registry/inference-agents.yml`），在应用启动时加载，在保存后**立即生效**（无需重启）。设置文件缺失或损坏时系统 MUST 回退到静态配置（`mdr.inference.*`）语义且不得阻断启动。未保存过设置时，系统行为 MUST 与仅静态配置时一致。

#### Scenario: 保存设置立即生效

- **WHEN** SUPER_ADMIN 保存设置（启用 kimi、设为默认）
- **THEN** 设置写入设置文件；此后 `GET /api/v1/inference/providers` 立即反映新启用集合与默认标记，无需重启应用

#### Scenario: 重启后设置保留

- **WHEN** 应用重启且设置文件存在
- **THEN** 注册表按设置文件恢复启用集合、默认提供方与自定义 Agent

#### Scenario: 设置文件损坏

- **WHEN** 设置文件内容无法解析
- **THEN** 系统记录警告日志并回退到静态配置语义，应用正常启动

### Requirement: Agent 自动探测与重新探测

系统 SHALL 提供 SUPER_ADMIN 专用的设置读取端点，返回每个已知与自定义 Agent 的合并视图（显示名、命令、参数、启用状态、默认标记、探测可用性与版本信息）。系统 SHALL 提供重新探测端点，强制刷新探测缓存并返回最新探测结果。探测 MUST 只读，不得执行除版本查询外的任何命令。

#### Scenario: 查看探测结果

- **WHEN** SUPER_ADMIN 请求 `GET /api/v1/system/inference/agents`
- **THEN** 系统返回所有内置与自定义 Agent 的合并视图；对本机已安装的 Agent 显示可用与版本，对未安装的显示不可用及原因

#### Scenario: 重新探测

- **WHEN** SUPER_ADMIN 请求 `POST /api/v1/system/inference/agents/probe`
- **THEN** 系统绕过缓存重新执行版本探测并返回更新后的合并视图

#### Scenario: 非管理员访问设置端点

- **WHEN** 非 SUPER_ADMIN 用户请求 `/api/v1/system/inference/**` 任一端点
- **THEN** 系统返回无权访问错误

### Requirement: 用时切换 Agent 与结果对比

前端推断对话框 SHALL 允许用户在取得一次推断结果后，改选其他已启用且可用的 Agent 重新生成，并 SHALL 在本次对话框会话内保留各次运行的结果（标注所用 Agent 与时间），允许用户在历史运行间切换查看并从任一运行中选择候选填入表单。

#### Scenario: 换 Agent 重新生成

- **WHEN** 用户在结果态改选另一个可用 Agent 并触发重新生成
- **THEN** 系统以新 Agent 创建新作业，对话框保留上一次结果并将其标注为对应 Agent 的历史运行

#### Scenario: 从历史运行中采纳候选

- **WHEN** 用户切换到较早的历史运行并选中其中一个候选填入表单
- **THEN** 该候选文本进入编辑表单对应编辑区，且该对话框内其余历史运行不丢失
