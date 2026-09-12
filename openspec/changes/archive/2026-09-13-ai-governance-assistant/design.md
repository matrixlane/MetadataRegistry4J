# 设计：AI 数据治理协作助手（任务框架泛化）

## Context

已交付（未归档）：`ai-metadata-inference`（补全场景：提示词组装、候选 JSON 契约、内存作业、InferenceDialog）与 `ai-inference-agent-selection`（多 Agent 注册表、探测、设置持久化、用时切换与历史对比）。二者构成稳定的 **Agent 接入层**：`InferenceProviderRegistry` + `inferenceExecutor` + 内存作业 + 按创建者隔离。

新需求是场景泛化：补全只是协作的一种形态；解释（含划词选段）、评审、翻译、命名/分类建议等都将出现。共性：同一接入层 + 异步作业 + 人机确认闭环；差异：输入（整项目 vs 划选片段）、提示词模板、输出契约（候选数组 vs 自由文本 vs 结构化意见）、结果处置（填表单 vs 展示 vs 建议清单）。

约束延续：零 Flyway 迁移（新增表结构由 ddl-auto 依实体注解管理，见 D4）、零新第三方依赖、进程执行安全约束不变。`AdministeredItem.explanatoryComment` 字段已存在，但**目前没有任何写入路径**（请求 DTO 不含该字段、前端表单无编辑入口，仅出现在导出中）；承接解释落库需先补齐该字段的受保护写路径（见 D4 与 spec「explanatoryComment 受保护写路径」需求）。

## Goals / Non-Goals

**Goals:**

- 任务插件化：新协作场景 = 新增 `AssistantTask` 实现；作业内核、接入层、安全边界零改动。
- 统一异步作业模型：多态结果（候选/文本/意见），创建/轮询/取消/隔离语义与现有一致。
- 划选解释端到端：划词 → 结合项目上下文生成解释 → 展示 → 可选存入 explanatoryComment。
- 既有补全功能零回归：端点、UI、行为不变。

**Non-Goals:**

- 任务执行历史/评估数据持久化（另案，涉及三方言迁移）。
- 对话式多轮交互（本期单次请求-响应；任务实现内部可自由组织提示词）。
- 评审/翻译/分类建议任务的具体实现（框架就位后各自单独立项）。
- 流式输出（SSE）。

## Decisions

### D1：`AssistantTask` SPI 与输出种类

```java
interface AssistantTask {
    String id();                 // complete-metadata / explain-selection / ...
    TaskInputSpec inputSpec();   // 是否需要 itemId、是否允许/要求 selection、支持的 params
    OutputKind outputKind();     // CANDIDATES | TEXT | FINDINGS
    String buildPrompt(TaskContext ctx);
    Object parseOutput(String raw);  // 按 outputKind 校验/结构化；TEXT 可做轻量清洗
}
```

`TaskContext` 携带：itemId（可选）、selection（可选：field/selectedText/surroundingText/language/起止偏移）、params、调用方信息。任务实现各自决定提示词；`complete-metadata` 复用现有 `InferencePromptBuilder`；`explain-selection` 的提示词注入：选段原文、所在字段与项目上下文（指称/定义/来源文档摘要）、ISO/IEC 11179 相关概念要点（概念引用的具体条款在实现时经 Milvus 检索 2023 版原文核实后写入模板，不凭记忆引用），要求输出简体中文解释（可含术语表与依据条款引用）。

### D2：统一作业内核，inference 端点保留为兼容外观

`AssistantJobService` 泛化自 `InferenceJobService`：作业对象加 `taskId`、`selection`、多态 `result`；复用同一 `inferenceExecutor`、注册表解析（含默认提供方）、取消杀进程、TTL 清理、创建者隔离。新端点：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/assistant/tasks` | 任务清单（id、显示名、inputSpec、outputKind），前端据此动态渲染入口 |
| POST | `/api/v1/assistant/jobs` | 体：{taskId, itemId?, selection?, params?, provider?} → {jobId} |
| GET/DELETE | `/api/v1/assistant/jobs/{jobId}` | 状态 + 多态 result / 取消 |

既有 `/api/v1/inference/**` 保留：其创建作业内部转为 `complete-metadata` 任务调用，响应结构不变。已交付 UI 零改动。

### D3：划选输入模型

`selection = {field: "designation"|"definition"|"context"|..., text: 选段原文（必填，长度上限如 2000）, surroundingText?: 所在完整文本（上限截断）, language?, startOffset?, endOffset?}`。偏移量仅供溯源展示，不进提示词核心；提示词以原文+周围文本为准（避免偏移与内容不一致导致误导）。`explain-selection` 任务要求 selection.text 非空；无 itemId 时退化为通用术语文境解释（允许，但有关联项目时解释质量更高）。

### D4：解释结果处置——展示为主；可选落库 = 核心注释 + 扩展审计记录分离

解释结果默认仅展示（前端侧栏/气泡，纯文本或经转义的受限 Markdown）。用户可点「存入注释」，一次确认完成两件分离的写入：

1. **核心注释**：解释**纯文本**（不含任何来源标注行）带回既有编辑表单，经既有保存路径写入 `explanatoryComment`（REGISTRAR 权限；该字段当前无写路径，需先补齐——见 spec「explanatoryComment 受保护写路径」需求）；
2. **审计元数据（扩展，非核心）**：来源信息（taskId、Agent/providerId、生成时间、操作者、结果种类、AI 输出原文、动作类型如 `SAVED_TO_EXPLANATORY_COMMENT`）写入 `metadata-registry-ext` 的新卫星实体 `AiGenerationRecord`，关联到对应 AdministeredItem/Item。**来源标注一律不内嵌进核心元数据字段**。

**写入顺序与失败处理**：两次写入非原子（前端两次调用），规定顺序为先落注释、保存成功后再创建生成记录；生成记录创建失败时**不回滚**已落注释，前端提示「注释已保存，来源记录写入失败」并允许仅重试记录写入。取舍理由：生成记录是注释的附属溯源信息，允许短暂滞后；反过来先建记录再落注释会在注释保存失败时留下无对应内容的空溯源，更不可取。

`AiGenerationRecord` 为新增表：依数据库可移植性规则由 Hibernate ddl-auto 依据实体元数据管理（索引/约束写实体注解，不写 Flyway）；实体放置遵循进行中的 MDR/EXT schema 拆分约定（参照 `openspec/changes/2026-09-01-mdr-ext-schema-split/design.md`，不硬编码 `@Table(schema=...)`）：作为 EXT 实体 MUST 登记入 `orm/orm-team.xml` 并同步 `OrmTeamXmlTest` 漂移校验，否则 team profile（SQL Server/达梦）下该表会被建入默认 schema——与 2026-09-02 EXT 表误入 mdr 的事故同因。新增端点：`POST /api/v1/assistant/generation-records`（创建）与 `GET /api/v1/assistant/generation-records?itemId=`（按项目查询），**均限 REGISTRAR/SUPER_ADMIN**（2026-09-08 用户确认：AI 生成相关能力——含生成记录的读取——不对其它角色开放；其它角色只消费经审核流程发布的权威结果）。注意 `GET /api/v1/**` 平台默认仅要求登录，故须在 SecurityConfig 为该 GET 路径显式追加 REGISTRAR/SUPER_ADMIN 规则并置于通用 GET 规则之前。端点支撑注册人员对各 Agent 产出的回溯评估。

**术语边界**：「审计元数据」「核心/扩展」等分类是设计语言，仅存在于设计文档与代码结构层面；前端界面文案 MUST NOT 出现这些术语，一律用自然表述（如「生成来源：claude · 2026-09-06 14:03」「AI 生成记录」）。

### D5：权限维持 REGISTRAR（已确认）

`/api/v1/assistant/**` 沿用默认规则（POST 需 REGISTRAR/SUPER_ADMIN），含解释等只读任务——已确认不放宽：每次调用消耗 CLI 配额，协作场景限定注册人员角色。2026-09-08 用户进一步确认：AI 生成相关能力整体限 REGISTRAR/SUPER_ADMIN，包括生成记录的**读取**（`GET /api/v1/assistant/**` 不按平台 GET 惯例放开，见 D4 的 SecurityConfig 显式规则要求）；其它角色原则上只访问经审核流程发布的权威元数据结果。

### D6：前端交互

- 新增可复用 `SelectionExplain` 交互：在指称/定义/上下文等文本容器上监听 `mouseup`，有选区时浮出「解释所选」按钮 → 打开解释面板（popover/抽屉）：显示选段、任务参数（可附加问题说明）、Agent 选择（复用 providers 端点与切换/历史交互模式）、结果展示与「存入注释」。
- 入口挂载点（首批）：`ItemFormDialog` 的指称/定义 tab 文本区、数据元列表的详情展示。文本容器以指令/组件包装，后续页面渐进接入。
- `api/assistant.ts`：tasks/jobs 接口 + 轮询复用 inference 的 poll 模式。

### D7：调试追踪（开发辅助）

- 作业内核在执行点捕获提示词原文与 AI 响应原文：`AssistantJob` 新增 `prompt`/`rawOutput` 两字段，仅存内存、随作业 TTL 一并清理，**不落库**（调试数据，非来源记录；持久化评估另案）。
- 新端点 `GET /api/v1/assistant/debug/exchanges`：返回当前用户最近交互（SUPER_ADMIN 返回全部），按创建时间倒序、上限 50 条；字段含 jobId、taskId、itemId、提供方 id/显示名、状态、prompt、rawOutput、error、时间戳。MUST 限 REGISTRAR/SUPER_ADMIN（SecurityConfig 显式规则置于通用 GET 规则之前，同 D4 先例）。
- 前端全局快捷键 `Ctrl+Shift+D` 开合右侧调试抽屉（el-drawer）：打开期间每 4s 轮询该端点；条目展示任务、Agent、状态、提示词全文与响应原文（`<pre>` 纯文本，禁 v-html）并提供复制按钮。入口仅对 REGISTRAR/SUPER_ADMIN 注册。
- inference 兼容外观的响应结构不变；调试数据仅经新端点暴露，不回灌既有作业响应。

## Risks / Trade-offs

- [泛化过早、抽象不当] → 以两个真实任务（complete-metadata 迁移 + explain-selection）验证 SPI 形状；评审/翻译只留接口占位不实现。
- [解释质量依赖上下文注入] → 提示词携带项目指称/定义/来源文档摘要；无项目关联时明确降级为通用解释并在 UI 标注。
- [划词交互与既有编辑器冲突（MultilingualTextEditor 输入框内选区）] → 首批只在只读/展示容器挂载划词，输入框内不启用；交互先例验证后再扩展。
- [统一内核改动影响已交付补全功能] → `complete-metadata` 迁移以既有 InferenceJobService 测试（18 例）全绿为准入；端点响应结构不变式由兼容测试锁定。
- [解释文本含 Markdown/HTML 注入风险] → 纯文本或转义后渲染，与候选展示同一约束。

## Migration Plan

分阶段落地，每阶段独立可交付：
1. 内核泛化 + `complete-metadata` 迁移（外部行为不变，测试锁定）。
2. `explain-selection` 任务 + 划词 UX + 解释面板 + 可选落库。
3. 后续任务（评审/翻译/分类）各自立项复用框架。

回滚：阶段 1 纯内部重构可整体回退；阶段 2 为纯增量。

## Open Questions（均已答复，2026-09-06）

- ~~只读任务是否放宽到全部登录用户~~ → **不放宽**：全部协作任务（含解释）维持 REGISTRAR/SUPER_ADMIN。
- ~~解释结果是否需要多候选变体~~ → **不需要**：解释单段输出；多候选仅用于编辑元数据时的 CANDIDATES 类任务（供 REGISTRAR 参考选择）。
- ~~「存入注释」是否附来源标注~~ → **附带，但作为审计元数据**：来源信息（Agent/时间/任务）写入 ext 模块的 AI 生成记录（扩展），不内嵌进 explanatoryComment 等核心元数据字段；explanatoryComment 只存解释纯文本。


## 变更记录（2026-09：acp-agent-protocol）

本地 Coding Agent 调用方式由「CLI 一次性调用」（`--version` 探测、`{prompt}` 占位符、stdout 文本刮取）整体切换为 **ACP 协议**（JSON-RPC 2.0 over stdio/NDJSON，每 Agent 常驻进程懒启动 + initialize 握手，每作业 session/new→prompt→累积 agent_message_chunk→close）。
