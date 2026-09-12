# 提案：AI 数据治理协作助手（任务框架泛化）

## Why

`ai-metadata-inference` 解决了「补全缺失元数据」一个场景，但 AI 与人的协作不止于此：注册人员还需要让 AI **解释**一个上下文/指称/定义（或其中划选的一段）、按 ISO/IEC 11179-1:2023 §6.2–6.3 定义构成规则**评审**定义质量、**翻译**、命名建议、分类建议等。这些场景共享同一底座——本地 Agent 接入层（探测/启用/切换）、异步作业、人机确认闭环——差异只在提示词模板、输入（可能含划选文本）、输出契约与结果处置。若每个场景各自造轮子，接入层被反复复制且体验割裂。本变更把架构泛化为「协作任务框架」：已交付的元数据补全成为框架中的一种任务，新场景以任务插件方式加入。

## What Changes

- 新增**协作任务抽象**（`AssistantTask` SPI）：任务 id、显示名、输入要求（是否需要划选文本等）、输出种类（候选列表 / 解释文本 / 评审意见）、提示词模板与输出校验；新场景 = 新增一个任务实现，不改作业内核。
- **作业内核泛化**：作业增加 taskId 与多态结果（候选/文本/意见列表），统一创建/轮询/取消/创建者隔离/内存存储；既有 `/api/v1/inference/**` 端点行为不变（委托给 `complete-metadata` 任务，已交付 UI 不受影响）。
- 新增 `/api/v1/assistant/**`：任务清单端点（驱动前端动态渲染入口，无需硬编码任务）+ 统一协作作业端点。
- 新增**划选解释**场景：在指称/定义/上下文等文本展示区划词划句 → 「解释所选」→ AI 结合该项目的元数据上下文解释选段；解释可一键「存入注释」：纯文本写入 `explanatoryComment`（复用既有受保护保存机制——该字段当前无写路径，随本变更补齐），来源标注（Agent/时间/任务）作为**审计元数据**写入 ext 模块的 AI 生成记录（扩展实体，不进核心元数据）。
- 任务清单初始包含：`complete-metadata`（既有补全）、`explain-selection`（划选解释）；`review-definition`（11179-1:2023 §6.2–6.3 规则评审）、`translate`、`classification-suggestion` 等作为框架验证后的后续任务占位。

## Capabilities

### New Capabilities

- `ai-governance-assistant`: AI 与人协作完成数据治理的通用任务框架：可扩展的协作任务注册、统一异步作业、划选文本解释等任务形态，以及解释结果的展示与可选落库。

### Modified Capabilities

（无需求级变更。`ai-metadata-inference` 的行为不变，其实现迁入本框架作为 `complete-metadata` 任务。）

## Impact

- **后端**：`web/inference/` 的作业内核（注册表/执行器/内存作业/取消）泛化复用；新增 `web/assistant/`（任务 SPI、任务上下文组装、清单与作业控制器）；`metadata-registry-ext` 新增 `AiGenerationRecord` 审计实体（新表由 Hibernate ddl-auto 管理；作为 EXT 实体登记入 `orm-team.xml` 并同步 `OrmTeamXmlTest`，遵循 MDR/EXT schema 拆分约定）；补齐 `explanatoryComment` 的受保护写路径（请求 DTO、保存映射、前端编辑入口）。既有 inference 端点保留为兼容外观。不新增第三方依赖。
- **安全**：全部协作端点（含解释等只读任务）的写操作沿用 REGISTRAR/SUPER_ADMIN 规则，不放宽——已确认：每次调用消耗 CLI 配额，且 AI 生成能力整体限注册人员，其它角色只访问经审核流程发布的权威结果；AI 生成记录的读取同样限 REGISTRAR/SUPER_ADMIN（`GET /api/v1/assistant/**` 需在 SecurityConfig 显式追加规则，覆盖平台 GET 仅登录的默认值）。解释落库走受保护写路径，权限不变。
- **前端**：新增划词交互组件与解释展示面板；任务入口由任务清单端点驱动；既有 InferenceDialog 保持可用，后续可迁移到通用任务对话框。
- **前置依赖**：本变更构建在 `ai-metadata-inference` 与 `ai-inference-agent-selection` 已交付的 Agent 接入层之上。
