# 设计：推断 Agent 自动探测、选择与时用切换

## Context

`ai-metadata-inference` 已交付：`CliAgentProvider`（单 Bean、静态预设 claude/codex/kimi，提示词 stdin 或 `{prompt}` 占位符）、`InferenceJobService`（内存作业、按创建者隔离）、`GET /api/v1/inference/providers` 与作业端点；前端 `InferenceDialog` 已有提供方 radio。（注：原交付中的 `DirectApiProvider` 直连提供方已按后续决定整体移除。）项目已有「界面改配置写 `~/.metadata-registry/application-local.yml`（SnakeYAML 合并、重启生效）」的先例（`DatasourceConfigService`）；`/api/v1/system/**` 已被 SecurityConfig 限定 SUPER_ADMIN；系统设置页 `SystemSettings.vue`（`/system-settings`）采用 el-card 分区。

约束延续：不新增数据库表（三方言迁移规则）、不新增第三方依赖、进程执行安全约束（参数数组不经 shell、独立空工作目录、超时强杀、stdout 上限）不变。

## Goals / Non-Goals

**Goals:**

- 配置界面**自动探测**本机可用 Coding Agent（已知 claude/codex/kimi + 自定义），显示版本与可用性。
- 用户（SUPER_ADMIN）**勾选启用**需要的 Agent、设定默认提供方、可覆盖命令/参数、可增删自定义 Agent；设置**持久化**并在保存后**立即生效**（不重启）。
- 注册人员在实际推断时可从「已启用且可用」的 Agent 中**任意切换**重新生成，对话框保留各次结果供对比挑选。
- 兼容既有静态配置：未保存过设置时，行为与现状一致（`mdr.inference.cli.preset` 等作为默认值）。

**Non-Goals:**

- 不持久化推断历史/评估结果（对比仅在对话框会话内）。
- 不做 Agent 的自动安装/升级。
- 多实例部署的配置同步（单实例本地文件即可）。

## Decisions

### D1：运行时注册表 + 独立设置文件，而非写 application-local.yml

设置存 `~/.metadata-registry/inference-agents.yml`（SnakeYAML 读写，风格对齐 `DatasourceConfigService`），由 `InferenceAgentSettingsService` 在启动时加载、保存时写入并**立即替换内存态**（AtomicReference）。

- 备选：合并写入 `application-local.yml` —— 该文件经 `spring.config.import` 绑定 `@ConfigurationProperties`，重启才生效，无法满足「立即生效」；且双通道绑定同一前缀易混淆。独立文件由本服务独占读写，无此问题。
- 文件内容：`version`、`defaultProvider`、`agents`（id → {enabled, displayName?, command?, args?}，内置 id 仅存覆盖项，自定义条目存完整定义）。

### D2：提供方实例由注册表按 Agent 定义动态构建

`CliAgentProvider` 从单例 @Component 改为普通类，由 `InferenceProviderRegistry` 按「内置定义 ⊕ 持久化覆盖 ⊕ 自定义条目」构建：每个 Agent 一个实例，`id()` 即 Agent id（`claude`/`codex`/`kimi`/`custom-*`）。`InferenceJobService` 改为注入注册表解析提供方；`provider` 请求字段接受任一**已启用** Agent id；未指定时用设置的 `defaultProvider`（无设置时回退 `mdr.inference.provider` 旧语义：`cli`→`mdr.inference.cli.preset` 所指的内置 Agent，无匹配时回退 claude）。注：OpenAI 兼容直连（direct）提供方已按后续决定从注册表与设置面整体移除，不再作为 `direct` 条目管理（若将来恢复另行处置）。

### D3：探测与缓存

探测 = 对命令执行 `--version`（含 `{prompt}` 的命令退化为 `<可执行文件> --version`），3s 超时，结果缓存 60s；`POST /api/v1/system/inference/agents/probe` 强制刷新。探测只读、不产生副作用。

### D4：端点划分与权限

- `/api/v1/system/inference/agents`（GET 合并设置+探测、PUT 保存）与 `/probe`（POST 刷新）：落 `/api/v1/system/**`，既有规则自动限定 SUPER_ADMIN，零 SecurityConfig 改动。保存即生效。
- `GET /api/v1/inference/providers`（推断对话框用，登录即可）：只返回**已启用且探测可用**的提供方（id/displayName/isDefault）。
- 作业响应 `InferenceJobResponse` 增加 `providerId`/`providerDisplayName`，前端对比历史据此标注每个候选出自哪个 Agent。

### D5：前端交互

- `SystemSettings.vue` 新增「AI 推断（Coding Agent）」卡片：探测表格（名称/命令/版本/状态 tag/启用 switch/默认 radio）、自定义 Agent 编辑对话框（id/名称/命令/参数，参数一行一个或空格分隔——选定一种并注明）、「重新探测」「保存」按钮；保存成功提示立即生效。
- `InferenceDialog.vue`：提供方 radio 数据源改为新 providers 响应；默认选中 isDefault，否则记忆上次使用（localStorage）；结果态保留选择器 + 「重新生成」；对话框会话内维护运行历史（Agent、时间、候选），可在历史间切换查看，从任一历史项「填入表单」。

## Risks / Trade-offs

- [设置文件损坏导致启动失败] → 加载失败时记日志并回退到「无持久化设置」（静态配置语义），不阻断启动。
- [非 SUPER_ADMIN 的 REGISTRAR 可任意选用已启用 Agent 发起推断] → 符合需求（用时切换）；CLI 命令本身只能由 SUPER_ADMIN 在设置中定义，请求体仍不接受命令片段。
- [自定义 Agent 命令被设为恶意程序] → 仅 SUPER_ADMIN 可配置；与既有「CLI 命令仅来自服务端配置」边界一致。
- [探测缓存导致刚安装的 Agent 显示不可用] → 提供「重新探测」按钮强制刷新；缓存仅 60s。
- [enabled=false 的 Agent 仍被旧作业引用] → 作业执行期持有实例引用，不受后续设置变更影响；新作业只解析当前已启用集合。

## Migration Plan

纯增量。无持久化设置文件时行为与现状一致；保存一次后由设置文件接管。回滚 = 删除 `inference-agents.yml` 并回退代码。

## Open Questions

- 自定义 Agent 的参数在 UI 中的录入格式（数组 vs 空格分隔字符串）：实现时按数组存储，UI 用多行文本（一行一个参数）编辑。


## 变更记录（2026-09：acp-agent-protocol）

本地 Coding Agent 调用方式由「CLI 一次性调用」（`--version` 探测、`{prompt}` 占位符、stdout 文本刮取）整体切换为 **ACP 协议**（JSON-RPC 2.0 over stdio/NDJSON，每 Agent 常驻进程懒启动 + initialize 握手，每作业 session/new→prompt→累积 agent_message_chunk→close）。
