# 能力规格：ai-governance-assistant

## ADDED Requirements

### Requirement: 协作任务框架

系统 SHALL 提供协作任务抽象：每个任务具有唯一 id、显示名、输入规格（是否需要关联项目、是否要求划选文本、支持的参数）与输出种类（CANDIDATES / TEXT / FINDINGS）。系统 SHALL 提供 `GET /api/v1/assistant/tasks` 返回当前可用任务清单，供前端动态渲染任务入口。新增任务 MUST 仅通过新增任务实现完成，不得改动作业内核。

#### Scenario: 查询任务清单

- **WHEN** 已登录用户请求 `GET /api/v1/assistant/tasks`
- **THEN** 系统返回任务清单，至少包含 complete-metadata 与 explain-selection，每项含输入规格与输出种类

#### Scenario: 请求未知任务

- **WHEN** 以不存在的 taskId 创建协作作业
- **THEN** 系统返回参数校验错误，不创建作业

### Requirement: 统一协作作业

系统 SHALL 以统一异步作业执行任意协作任务：`POST /api/v1/assistant/jobs` 创建并立即返回作业标识；`GET /api/v1/assistant/jobs/{jobId}` 返回状态与按任务输出种类定型的结果；`DELETE` 取消。作业 MUST 仅对创建者（或 SUPER_ADMIN）可见；作业状态 MUST 仅存内存并设存活期清理。提供方解析（显式指定或默认、仅已启用可用）MUST 与推断作业一致。

#### Scenario: 创建并轮询协作作业

- **WHEN** 具有 REGISTRAR 角色的用户以合法 taskId 与输入创建作业
- **THEN** 系统立即返回作业标识并异步执行；完成后结果按该任务的输出种类返回

#### Scenario: 越权访问他人协作作业

- **WHEN** 非创建者且非 SUPER_ADMIN 查询或取消他人协作作业
- **THEN** 系统按资源不存在或无权访问处理，不泄露作业内容

### Requirement: 划选文本输入

协作作业 SHALL 支持可选的划选输入：field（所在字段）、text（选段原文，explain-selection 任务必填且非空，长度上限 2000 字符）、surroundingText（所在完整文本，超长截断）、language 与起止偏移。提示词组装 MUST 以选段原文与周围文本为准；起止偏移仅用于展示溯源。

#### Scenario: 带选段创建解释作业

- **WHEN** 用户对某数据元的定义文本划选一句并以 explain-selection 创建作业
- **THEN** 作业输入包含该选段原文、所在字段 definition 与项目标识，提示词包含选段、所在完整定义与该项目相关元数据摘要

#### Scenario: 选段为空被拒绝

- **WHEN** explain-selection 作业的 selection.text 为空或缺失
- **THEN** 系统返回参数校验错误

### Requirement: 划选解释任务

系统 SHALL 内置 explain-selection 任务：结合关联项目的既有元数据（指称、定义、上下文、来源文档摘要）与 ISO/IEC 11179 概念体系，以简体中文解释所选文本的含义与依据。输出种类为 TEXT。无关联项目时 MUST 降级为通用术语文境解释，且响应中标注该降级。

#### Scenario: 项目语境下的解释

- **WHEN** 解释作业关联了具备指称与来源文档的项目
- **THEN** 提示词包含该项目上下文，解释结果引用相关概念并说明依据

#### Scenario: 无项目关联的降级解释

- **WHEN** 解释作业未关联任何项目
- **THEN** 系统仍执行解释，结果中标注为通用解释（未关联注册项目）

### Requirement: 解释结果的展示与可选落库

解释结果 SHALL 仅做展示（纯文本或经 HTML 转义的受限 Markdown），单段输出、不提供多候选变体；多候选仅适用于 CANDIDATES 类任务（如编辑元数据时的 complete-metadata 补全）。系统 SHALL 提供「存入注释」交互：经用户确认后，（a）解释**纯文本**（不含来源标注行）经受保护的编辑保存路径写入关联 AdministeredItem 的 explanatoryComment（该路径对 explanatoryComment 的支持见「explanatoryComment 受保护写路径」需求），不得新增绕过权限的写端点；（b）来源标注（Agent、时间、任务等）SHALL 作为审计元数据写入扩展的 AI 生成记录，MUST NOT 内嵌进 explanatoryComment 或其他核心元数据字段。

> 变更记录（ai-assist-ux-revamp D7）：划词解释的编辑表单/详情页展示挂载（SelectionExplain/ExplainPanel）已由浮动 AI 对话窗取代，「存入注释」**前端交互随之退役**；后端 explain-selection 任务、explanatoryComment 受保护写路径与 AI 生成记录端点全部保留（后续任务的来源记录写路径复用）。

#### Scenario: 解释仅展示不落库

- **WHEN** 解释作业完成且用户未触发存入注释
- **THEN** 不产生任何对元数据实体的写操作

#### Scenario: 存入注释的双轨写入（前端交互已退役，保留为后端写路径记录）

- **WHEN** 用户对解释结果触发存入注释并确认
- **THEN** 系统先经表单保存路径将解释纯文本写入 explanatoryComment 并按既有版本历史机制记录；保存成功后创建一条含 Agent/时间/任务的 AI 生成记录（扩展审计元数据），核心元数据字段中不出现来源标注

#### Scenario: 来源记录写入失败（前端交互已退役，保留为后端写路径记录）

- **WHEN** 解释纯文本已成功写入 explanatoryComment，但随后 AI 生成记录创建失败
- **THEN** 系统不回滚已写入的注释；前端提示来源记录写入失败，并允许用户仅重试记录写入而不重复写注释

### Requirement: explanatoryComment 受保护写路径

系统 SHALL 为 AdministeredItem 的 explanatoryComment 提供编辑能力：该字段 MUST 纳入首批挂载点所涉项目类型的编辑请求契约与保存映射，复用既有受保护保存路径与权限规则，MUST NOT 新增绕过权限的写端点；保存 SHALL 按既有版本历史机制记录快照。

#### Scenario: 编辑并保存注释

- **WHEN** 具有 REGISTRAR 角色的用户在编辑表单中修改 explanatoryComment 并保存
- **THEN** 系统经既有保存路径持久化该字段，版本历史记录一次 UPDATE 快照

### Requirement: AI 生成审计记录（扩展）

系统 SHALL 在 metadata-registry-ext 模块提供 AI 生成记录实体（satellite，关联 AdministeredItem/Item），字段至少含：taskId、providerId/Agent 显示名、生成时间、操作者、结果种类、AI 输出原文、动作类型。该实体 SHALL 由 Hibernate 依据实体元数据管理建表（索引/约束用实体注解，不写 Flyway 脚本），实体放置 SHALL 遵循 MDR/EXT schema 拆分约定（不硬编码 schema；作为 EXT 实体 MUST 登记入 team profile 的 `orm-team.xml` 并同步漂移校验测试）。系统 SHALL 提供 `POST /api/v1/assistant/generation-records`（创建）与 `GET /api/v1/assistant/generation-records?itemId=`（按项目查询），两端点均 MUST 限 REGISTRAR/SUPER_ADMIN：AI 生成相关能力不对其它角色开放，其它角色只访问经审核流程发布的权威结果；GET 路径须在 SecurityConfig 显式追加授权规则，不得依赖平台「GET 登录即可」默认值。「审计元数据」「核心/扩展」为内部设计术语，界面文案 MUST NOT 使用（用「生成来源」「AI 生成记录」等自然表述）。

#### Scenario: 创建并回查生成记录

- **WHEN** 存入注释动作完成后，以 itemId 查询生成记录
- **THEN** 返回的记录包含所用 Agent、生成时间、任务 id、AI 输出原文与动作类型 SAVED_TO_EXPLANATORY_COMMENT

#### Scenario: 审计记录不污染核心元数据

- **WHEN** 读取该项目的核心元数据（designations/definitions/explanatoryComment 等）
- **THEN** 其中不包含 Agent 名、生成时间等来源标注内容

#### Scenario: 非注册人员读取生成记录被拒

- **WHEN** 既非 REGISTRAR 也非 SUPER_ADMIN 的登录用户请求 `GET /api/v1/assistant/generation-records`
- **THEN** 系统返回无权访问错误

### Requirement: AI 交互调试追踪

系统 SHALL 在作业内核记录每次协作作业发送给提供方的提示词原文与收到的响应原文（仅存内存，随作业 TTL 清理，不落库）。系统 SHALL 提供 `GET /api/v1/assistant/debug/exchanges` 返回最近交互列表（作业 id、任务 id、关联项目 id、提供方 id/显示名、状态、提示词、响应原文、错误、创建/完成时间），按创建时间倒序、上限 50 条；普通用户仅见本人创建的作业，SUPER_ADMIN 可见全部。该端点 MUST 限 REGISTRAR/SUPER_ADMIN（SecurityConfig 显式规则，不得依赖平台 GET 默认值）。系统 SHALL 提供调试侧栏：具有权限的用户在界面按下 Ctrl+Shift+D 开合右侧抽屉，打开期间周期性刷新，以纯文本展示提示词与响应全文并支持复制；入口 MUST NOT 对其它角色渲染。调试追踪 MUST NOT 改变既有 `/api/v1/inference/**` 与 `/api/v1/assistant/jobs/**` 的响应结构。

#### Scenario: 作业执行时记录提示词与响应

- **WHEN** 任一协作作业执行完成（成功或失败）
- **THEN** 该作业的调试记录中含发送给提供方的提示词原文；成功时含 AI 响应原文，失败时含错误信息

#### Scenario: 调试列表创建者隔离

- **WHEN** REGISTRAR 查询调试交互列表
- **THEN** 仅返回其本人创建的作业交互；SUPER_ADMIN 查询时返回全部

#### Scenario: 非注册人员访问调试端点被拒

- **WHEN** 不具备 REGISTRAR 业务角色的登录用户（含仅 SUPER_ADMIN 身份者）请求 `GET /api/v1/assistant/debug/exchanges`
- **THEN** 系统返回无权访问错误

#### Scenario: 快捷键开合调试侧栏

- **WHEN** 具备 REGISTRAR 业务角色的用户在界面按下调试侧栏快捷键（默认 Ctrl+Shift+D，绑定可经 AI 辅助设置配置，见 ai-assist-ux-revamp「快捷键可配置」）
- **THEN** 调试侧栏打开（再次按下关闭），展示最近交互的提示词与响应全文，打开期间自动周期刷新

### Requirement: 既有补全功能兼容

既有 `/api/v1/inference/**` 端点与已交付的推断对话框行为 MUST 保持不变；其实现 SHALL 委托给 complete-metadata 任务执行。

#### Scenario: 既有端点行为不变

- **WHEN** 前端以既有契约调用 `/api/v1/inference/items/{itemId}/jobs` 并轮询
- **THEN** 请求/响应结构与候选语义与泛化前完全一致
