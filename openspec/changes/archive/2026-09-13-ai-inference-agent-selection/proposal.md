# 提案：推断 Agent 自动探测、选择与时用切换

## Why

`ai-metadata-inference` 变更已让应用能调用本机 Coding Agent 推断缺失元数据，但当前提供方只能通过 `application.yml`/环境变量静态指定单一 CLI 预设：用户无法直观看到本机装了哪些 Agent、无法勾选启用哪些、也无法在一次推断中方便地换用不同 Agent 来对比生成质量。不同 Agent（Claude Code / Codex / Kimi Code CLI 等）对同一元数据的推断质量有差异，用户需要在实际使用中切换并自行评估。

## What Changes

- 新增**运行时 Agent 注册表**：内置 claude/codex/kimi 三个已知 Agent 定义 + 用户自定义 Agent（命令/参数可配），每个 Agent 是独立推断提供方（id 即 Agent id）。
- 新增**自动探测**：对每个已知/自定义 Agent 执行 `--version` 探测（3s 超时、60s 缓存），报告可用性与版本；配置界面可手动触发重新探测。
- 新增**设置持久化**：启用集合、默认提供方、命令/参数覆盖与自定义 Agent 保存到 `~/.metadata-registry/inference-agents.yml`（独立文件，复用 SnakeYAML 合并读写风格），保存后**立即生效无需重启**；`mdr.inference.enabled` 仍为部署级总开关。
- 新增系统设置端点（`/api/v1/system/inference/**`，SUPER_ADMIN）：读取设置（含探测结果合并）、保存设置、重新探测。
- 既有 `GET /api/v1/inference/providers` 改为只返回**已启用且可用**的提供方（含默认标记）；推断作业的 `provider` 字段现接受任一已启用 Agent id，作业响应记录实际使用的提供方。
- 前端：系统设置页新增「AI 推断」卡片（探测结果表格、启用勾选、默认选择、自定义 Agent 增删、保存/重新探测）；推断对话框支持**用时切换 Agent 重新生成**并保留各次结果供对比挑选。

## Capabilities

### New Capabilities

（无。以下需求并入既有能力。）

### Modified Capabilities

- `ai-metadata-inference`: 「推断提供方抽象与可用性探测」需求扩展为多 Agent 注册表 + 启用管理；新增 Agent 设置持久化、自动探测、用时切换与结果对比需求。

## Impact

- **后端**：`web/inference/` 新增 `InferenceProviderRegistry`、`InferenceAgentSettingsService`、设置 DTO 与 `/api/v1/system/inference/` 控制器；`CliAgentProvider` 由单例 Bean 改为注册表按 Agent 定义实例化；`InferenceJobService` 经注册表解析提供方。不新增数据库表/迁移/第三方依赖；不改 SecurityConfig（`/api/v1/system/**` 已有 SUPER_ADMIN 规则）。
- **前端**：`SystemSettings.vue` 新增卡片、`api/inference.ts` 增加设置接口、`InferenceDialog.vue` 支持切换重跑与运行历史对比；i18n 中英文。
- **部署**：无 schema 变更；既有 `mdr.inference.cli.*` 静态配置继续生效（作为未持久化设置时的默认值），平滑兼容。
