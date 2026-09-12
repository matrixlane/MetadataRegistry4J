# 任务：AI 数据治理协作助手（任务框架泛化）

## 1. 阶段一：作业内核泛化（外部行为不变）

- [x] 1.1 定义 `AssistantTask` SPI（id/inputSpec/outputKind/buildPrompt/parseOutput）、`TaskContext`/`Selection` 模型与输出种类枚举（CANDIDATES/TEXT/FINDINGS）
- [x] 1.2 作业内核泛化：`AssistantJobService`（taskId + 多态 result；复用 inferenceExecutor、注册表解析、取消、TTL、创建者隔离）
- [x] 1.3 `complete-metadata` 任务实现：复用 `InferencePromptBuilder` 与 `CandidateJsonExtractor`；`/api/v1/inference/**` 改为委托该任务的兼容外观（响应结构不变）
- [x] 1.4 兼容锁定：既有 inference 测试（18 例作业 + 提取器 + prompt builder）全绿；新增「响应结构不变式」兼容测试

## 2. 阶段二：划选解释

- [x] 2.1 `explain-selection` 任务：输入校验（selection.text 必填≤2000）、提示词组装（选段+所在文本+项目元数据摘要+ISO 11179 概念要点；无项目时降级并标注）、TEXT 输出清洗
- [x] 2.2 `web/assistant/` 控制器：`GET /tasks`、`POST /jobs`、`GET/DELETE /jobs/{jobId}`（默认沿用 REGISTRAR 写权限规则）
- [x] 2.3 前端 `api/assistant.ts`（tasks/jobs + 轮询复用）与任务入口由清单驱动
- [x] 2.4 划词交互组件（只读文本容器 mouseup 选区 → 「解释所选」浮动按钮；输入框内不启用）+ 解释面板（选段回显、Agent 选择复用 providers/切换/历史模式、结果转义渲染）
- [x] 2.5 扩展审计实体 `AiGenerationRecord`（metadata-registry-ext，关联 Item/AdministeredItem；taskId/providerId/Agent 显示名/生成时间/操作者/结果种类/输出原文/动作类型；索引约束走实体注解，不写 Flyway；放置遵循 MDR/EXT schema 拆分约定：**登记入 `orm/orm-team.xml` 并同步 `OrmTeamXmlTest`**）+ 端点 `POST /api/v1/assistant/generation-records`、`GET .../generation-records?itemId=`（**均限 REGISTRAR/SUPER_ADMIN**；GET 须在 SecurityConfig 显式追加规则并置于通用 GET 规则之前）
- [x] 2.6 「存入注释」双轨写入：确认后（a）解释纯文本（不含来源行）带回既有编辑表单保存路径写入 explanatoryComment（依赖 2.8）；（b）保存成功后创建 AI 生成记录（审计元数据，2.5）；记录创建失败不回滚注释，前端提示并允许单独重试
- [x] 2.7 挂载点首批：ItemFormDialog 指称/定义 tab 展示文本、数据元详情展示；i18n 中英文（UI 文案禁用「审计元数据」「核心/扩展」等设计术语，用「生成来源」「AI 生成记录」等自然表述）
- [x] 2.8 explanatoryComment 受保护写路径：首批挂载点所涉项目类型的编辑请求 DTO 纳入 explanatoryComment、保存映射（`ItemAdministrationService` 保存路径）承接该字段、前端编辑表单增加注释编辑入口；保存走既有版本快照机制，不新增绕过权限的写端点

- [x] 2.9 调试追踪后端：`AssistantJob` 捕获 prompt/rawOutput（内存、随 TTL 清理）+ `GET /api/v1/assistant/debug/exchanges`（创建者隔离、SUPER_ADMIN 全览、倒序 50 条）+ SecurityConfig 显式规则（限 REGISTRAR/SUPER_ADMIN，置于通用 GET 之前）
- [x] 2.10 调试侧栏前端：`api/assistant.ts` 调试方法 + `AiDebugDrawer`（任务/Agent/状态、提示词与响应全文、复制、打开期间 4s 轮询）+ 全局快捷键 Ctrl+Shift+D（仅 REGISTRAR/SUPER_ADMIN 注册）+ i18n 中英文

## 3. 测试与验证

- [x] 3.1 后端单测：任务 SPI 校验、explain-selection 输入/提示词/降级标注、统一作业生命周期与越权、inference 兼容、`AiGenerationRecord` 创建/按项目查询/权限（含 GET 的角色限制）、explanatoryComment 保存与版本快照
- [x] 3.2 前端 vitest：划词组件、解释面板状态机、存入注释回填、任务清单驱动渲染
- [x] 3.3 构建：后端 inference/assistant 测试全绿 + 编译；前端 vitest + build
- [x] 3.4 GitNexus：编辑前 impact、收尾 detect_changes（无 partial/truncated）

