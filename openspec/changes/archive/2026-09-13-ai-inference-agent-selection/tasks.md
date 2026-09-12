# 任务：推断 Agent 自动探测、选择与时用切换

## 1. 设置持久化与注册表

- [x] 1.1 新增 `InferenceAgentSettings`（defaultProvider、agents: id→{enabled, displayName?, command?, args?}、version）与 `InferenceAgentSettingsService`：读写 `~/.metadata-registry/inference-agents.yml`（SnakeYAML，风格对齐 `DatasourceConfigService`）；启动加载、保存即写文件并立即替换内存态；文件缺失/损坏回退静态配置且不阻断启动
- [x] 1.2 新增 `InferenceProviderRegistry`：内置 claude/codex/kimi 定义 ⊕ 持久化覆盖 ⊕ 自定义条目，按 Agent 构建提供方实例；`list()`（合并视图含探测状态）、`resolveEnabled(id)`、探测缓存（60s）与强制刷新
- [x] 1.3 重构 `CliAgentProvider` 为可按 Agent 定义实例化的普通类（保留 stdin/`{prompt}` 占位符/超时/1MB 上限/独立工作目录语义；探测命令对含占位符者退化为 `<exe> --version`）（原并存的 `DirectApiProvider` 已随后续决定整体移除）

## 2. 作业与端点

- [x] 2.1 `InferenceJobService` 改经注册表解析提供方：仅已启用可用；未指定时用设置默认提供方（无设置回退 `mdr.inference.provider` 旧语义，cli→claude）；`InferenceJobResponse` 增加 providerId/providerDisplayName
- [x] 2.2 `GET /api/v1/inference/providers` 改为只返回已启用且可用的提供方（含 isDefault）
- [x] 2.3 新增 `/api/v1/system/inference/` 控制器（自动落入 SUPER_ADMIN 规则）：`GET /agents`（设置+探测合并视图）、`PUT /agents`（保存并立即生效）、`POST /agents/probe`（强制刷新探测）

## 3. 前端

- [x] 3.1 `api/inference.ts`：新增 getAgents/saveAgents/probeAgents；providers 响应类型加 isDefault；作业响应类型加 providerId/providerDisplayName
- [x] 3.2 `SystemSettings.vue` 新增「AI 推断（Coding Agent）」卡片：探测表格（名称/命令/版本/状态/启用 switch/默认 radio）、自定义 Agent 编辑对话框（id/名称/命令/参数，参数多行文本一行一个）、重新探测、保存（提示立即生效）
- [x] 3.3 `InferenceDialog.vue`：提供方列表改用新 providers 响应，默认选 isDefault 否则记忆上次（localStorage）；结果态可改选 Agent「重新生成」；会话内运行历史（Agent+时间+候选）可切换查看，从任一历史填入表单
- [x] 3.4 i18n zh-CN / en-US

## 4. 测试与验证

- [x] 4.1 后端单测：设置读写与损坏回退、注册表合并/覆盖/启用过滤、providers 端点语义、作业默认提供方解析与禁用拒绝
- [x] 4.2 前端 vitest：设置卡片（探测渲染/勾选/保存/自定义 Agent）、对话框切换重跑与历史对比
- [x] 4.3 构建验证：后端 inference 相关测试全绿 + 编译通过；前端 vitest + build 通过
- [x] 4.4 GitNexus：改动符号编辑前 impact；收尾 detect_changes（无 partial/truncated）
