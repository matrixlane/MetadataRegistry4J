# 任务：AI 元数据推断

## 1. 后端基础设施

- [x] 1.1 新增 `web/inference/InferenceProperties`（`@ConfigurationProperties(prefix = "mdr.inference")`：enabled/provider/max-candidates/cli{preset,command,args,timeout-seconds,work-dir}）并在配置类上注册（原 `direct{base-url,api-key,model,timeout-seconds}` 段已随后续决定移除）
- [x] 1.2 新增 `@EnableAsync` 配置类与推断专用 `ThreadPoolTaskExecutor`（复用 `MdcTaskDecorator`）
- [x] 1.3 `application.yml` 追加 `mdr.inference.*` 配置块（env 可覆盖，默认值见 design.md D2/D4）

## 2. 推断提供方

- [x] 2.1 定义 `MetadataInferenceProvider` SPI（`id()`/`status()`/`infer(InferenceRequest)`）与 `InferenceRequest`/`InferenceCandidate`/`InferenceResult` DTO
- [x] 2.2 实现 `CliAgentProvider`：ProcessBuilder 参数数组不经 shell、提示词走 stdin、独立空工作目录、超时强杀、stdout 上限；`--version` 可用性探测（缓存）；claude/codex/kimi 预设 + 命令模板可覆盖；进程执行抽象为可注入的 `ProcessRunner` 以便测试
- [x] 2.3 实现 `DirectApiProvider`：JDK HttpClient POST `{baseUrl}/chat/completions`（OpenAI 兼容），Key 仅取 `MDR_LLM_API_KEY`（该提供方已随后续决定整体移除，此处保留历史记录）
- [x] 2.4 实现 JSON 宽容提取器：从 stdout 截取首个括号配平的 `{...}` 并反序列化为候选列表，失败时携带截断原文

## 3. 提示词与作业

- [x] 3.1 实现 `InferencePromptBuilder`：只读加载 Item 图（指称/定义/上下文/来源文档/DEC/VD/属性槽/注册信息），按所请求缺失字段、语种、候选数组装提示词，内嵌 ISO/IEC 11179-1:2023 §6.2–6.4 与 11179-31:2023 §5.5 写作要求与输出 JSON schema，超长字段截断
- [x] 3.2 实现 `InferenceJobService`：内存作业存储（ConcurrentHashMap + TTL 定时清理）、异步执行、取消（杀进程）、按创建者隔离
- [x] 3.3 实现 `InferenceController`：`GET /providers`、`POST /items/{itemId}/jobs`、`GET /jobs/{jobId}`、`DELETE /jobs/{jobId}`，统一 `ApiResponse` 信封与 springdoc 注解

## 4. 前端

- [x] 4.1 新增 `src/api/inference.ts`：providers/createJob/getJob/cancelJob + 轮询 helper（2s 间隔、整体超时 5min）
- [x] 4.2 新增 `components/inference/InferenceDialog.vue`：缺失字段勾选、语种/候选数选择、提供方状态展示、进度态、候选卡片（纯文本 + rationale）选择、「填入表单」回调
- [x] 4.3 `ItemFormDialog.vue` 指称/定义 tab 增加「AI 推断」按钮（提供方不可用时置灰并提示），候选填入对应编辑区
- [x] 4.4 i18n：zh-CN 与 en-US 文案

## 5. 测试与验证

- [x] 5.1 后端单测：提示词组装（字段取舍/截断/候选数约束）、JSON 宽容提取、作业生命周期与取消、提供方选择、CLI 提供方（ProcessRunner 假实现）
- [x] 5.2 前端 vitest：InferenceDialog 状态机（创建→轮询→成功/失败/取消）与候选填入
- [x] 5.3 构建验证：`mvn -q package -DskipTests=false` 后端全绿、`npm run build` 与 vitest 前端全绿
- [x] 5.4 手工验收：本机真实 CLI（claude/codex 之一）跑通「已知指称+上下文+来源文档 → 推断定义」全流程（需用户启动应用并登录操作；CLI 预设参数已本机实测核实）
- [x] 5.5 GitNexus：编辑前对相关符号做 impact 分析；提交前 `detect_changes --scope all` 无 partial/truncated
