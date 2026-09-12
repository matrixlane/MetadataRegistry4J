# 设计：字段级 AI 生成普及（含新建模式）

## Context

已交付（未归档）：`ai-assist-ux-revamp` 的字段级 AI 生成（行内按钮 + AiCandidatePopover：点击生成/悬停回看/进行中恢复/防重入/行级缓存），`acp-agent-protocol` 的 ACP 传输。当前两个硬限制：前端 `canAiGenerate = isRegistrarUser && isEditing`（新建无按钮）；后端 `CompleteMetadataTask.inputSpec.itemIdRequired=true`（提示词只能来自库中条目）。

## Decisions

### D1：门控放开 = 一处改动、全部对象受益

`canAiGenerate` 改为仅 `isRegistrarUser`（不再要求 isEditing）。ItemFormDialog 是所有注册对象共用的编辑对话框，指称/定义 tab 的行内按钮随之普及到全部列表视图——**本变更不逐页接入**，页面级差异（如某类型无指称 tab）本来就没有按钮挂载点。

### D2：complete-metadata 草稿模式

inputSpec：`itemIdRequired: false`；`params` 声明新增 `draft`。校验规则：

- `itemId` 与 `draft` 至少其一；两者同给时 **itemId 优先**（编辑态以库内数据为准，draft 忽略并记录 debug 日志）。
- `draft` 形状：`{itemType?: string, designations?: [{language?, value}], definitions?: [{language?, value}]}`；至少一个非空文本行，否则参数错误；单值 ≤2000 字符截断（与既有 FIELD_TRUNCATE_LIMIT 一致）。
- 提示词：有 itemId 走既有 `build(itemId,…)`；否则 `buildDraft(draft, fields, language, candidateCount)`——「已知元数据」段由草稿组装（条目类型/指称/定义，空段省略），写作规则、推断任务段、JSON 输出契约与既有完全一致。草稿模式**没有**库内上下文/来源文档/注册信息，属预期降级（前端可在浮层注明「基于当前表单内容生成」，不做服务端 degraded 标记——与 selection-chat 的降级语义区分：草稿是用户主动提供的内容，非缺省状态。决定：不加 degraded，理由写入注释）。
- `TaskContext.params` 透传 draft（Map 结构），任务实现负责形状校验与容错（非法行跳过）。

### D3：前端新建模式载荷

- 新建模式点击「AI 生成」：收集当前表单中该 tab 已填的指称/定义行（含其它行，不只目标行——目标行内容也作为草稿上下文）+ 条目类型（对话框的 itemType）作为 `params.draft`；`itemId` 缺省。
- 编辑模式照旧 `itemId`（不随请求发 draft）。
- 行级缓存/悬停回看/进行中恢复/防重入逻辑与模式无关，天然复用；缓存 key 在新建模式用行 clientKey（无行 id）。

### D4：架构原则（写入 openspec/specs/system.md）

在 system.md 增设「AI 辅助」小节，原则条文：

> **AI 生成普及原则**：凡提供指称（designation）或定义（definition）编辑能力的界面，MUST 在编辑行内提供字段级「AI 生成」入口（新建与编辑模式均含），统一复用 ItemFormDialog/AiCandidatePopover 机制（点击生成、悬停回看、行级缓存、防重入）；候选数与默认提供方等配置一律前置到「系统设置 → AI 辅助」，运行时不做配置类选择。新增编辑界面 MUST 默认遵循，偏离须在对应 OpenSpec 变更中显式说明。

## Risks / Trade-offs

- [草稿内容为空/极少时生成质量低] → 校验至少一行非空；前端在目标行与同行已有内容均空时禁用按钮 tooltip 提示先填写（简化：后端校验兜底，前端不额外禁用，错误经浮层错误态呈现）。
- [draft 与库内数据不一致] → itemId 优先原则消除歧义。
- [草稿含未保存的敏感内容出域] → 与编辑态同语义（都发给本地 Agent），Registrar 已有预期；调试侧栏照常可见提示词。
- [inputSpec 契约放宽影响任务清单消费者] → 前端任务入口由清单驱动但字段级入口不读 inputSpec，无影响；API 层向后兼容（itemId 仍接受）。

## Migration Plan

1. 后端：draft 参数 + 校验 + buildDraft + 单测。
2. 前端：门控放开 + 新建载荷 + 测试。
3. 文档：system.md AI 辅助小节。
回滚：纯增量，分别可退。
