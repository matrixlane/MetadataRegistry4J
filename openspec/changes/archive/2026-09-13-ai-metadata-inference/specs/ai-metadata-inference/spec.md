# 能力规格：ai-metadata-inference

## ADDED Requirements

### Requirement: 推断提供方抽象与可用性探测

系统 SHALL 提供可插拔的元数据推断提供方接口，并内置两个实现：本地 Coding Agent CLI（首选）与 OpenAI 兼容直连（兜底）。系统 SHALL 提供 `GET /api/v1/inference/providers` 端点报告各提供方的可用性；CLI 提供方的可用性 MUST 通过对所配置命令执行版本探测获得，直连提供方的可用性 MUST 以服务端是否配置 API Key 判定。CLI 命令、参数与工作目录 MUST 只能来自服务端配置，请求体 MUST NOT 接受任何命令片段。

#### Scenario: 查询提供方可用性

- **WHEN** 已登录用户请求 `GET /api/v1/inference/providers`
- **THEN** 系统返回每个提供方的标识、显示名与可用状态；CLI 提供方在所配置命令不存在或探测超时时标记为不可用

#### Scenario: 功能整体关闭

- **WHEN** `mdr.inference.enabled=false` 时请求任一 `/api/v1/inference/**` 端点
- **THEN** 系统返回功能未启用的错误响应，不启动任何进程或外部调用

### Requirement: 本地 CLI 推断执行

系统 SHALL 通过 `ProcessBuilder` 以参数数组形式（不经 shell）启动本机 CLI 进程，提示词 MUST 经 stdin 传入。每次运行 MUST 在所配置工作目录下使用独立空目录作为进程工作目录；超过配置超时的进程 MUST 被强制终止；标准输出 MUST 设上限，超限视为失败。

#### Scenario: 成功执行 CLI 推断

- **WHEN** 推断作业以 CLI 提供方执行且 CLI 在超时内返回含合法 JSON 的输出
- **THEN** 系统从输出中提取首个括号配平的 JSON 对象并解析为候选列表

#### Scenario: CLI 超时

- **WHEN** CLI 进程运行超过 `mdr.inference.cli.timeout-seconds`
- **THEN** 系统强制终止该进程并将作业标记为失败，错误信息说明超时

#### Scenario: CLI 输出无法解析

- **WHEN** CLI 正常退出但输出中不存在可解析的 JSON 对象
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
