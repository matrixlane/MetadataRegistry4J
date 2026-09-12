# AI 辅助数据治理：ACP 接入、字段级生成普及、有效日期强制

日期：2026-09-13（会话跨 2026-09-06 至 09-13）

## What Changed

- **AI 辅助六连变更**（均 OpenSpec 先行、未归档）：`ai-metadata-inference`（本地 Coding Agent 补全元数据）、`ai-inference-agent-selection`（Agent 探测/启用/默认/自定义 + 系统设置卡片）、`ai-governance-assistant`（任务框架 AssistantTask/统一作业内核、划词解释、AiGenerationRecord、调试侧栏）、`ai-assist-ux-revamp`（更名 AI 生成、权限收紧仅 REGISTRAR、设置子页化、快捷键可配置、浮动对话窗 selection-chat、字段级生成按钮、运行时免配置）、`acp-agent-protocol`（**CLI 一次性调用整体废弃**，改 ACP 常驻进程：`AcpConnection` NDJSON 客户端 + `AcpAgentProvider`；内置命令 claude-agent-acp / codex-acp / kimi acp；探测=initialize 握手；设置 v1→v2 迁移）、`ai-generate-everywhere`（字段级生成普及全部共用 ItemFormDialog 的对象 + 新建草稿 draft 模式）。
- **有效日期强制**（`enforce-valid-period`，ISO/IEC 11179-31:2023 表 5/表 2）：值域/概念域 validPeriod.start 保存前必填；16+16 行既有数据回填（全部落占位基准日 2026-01-01，lifecycle 链未取到值）；前端条目级有效期必填+预填当日；federation 导入兜底补 start。
- **修复的线上 bug**：codex `--skip-git-repo-check`；Windows `.cmd` shim 无法被 ProcessBuilder 启动（WindowsCommandResolver）；extractor 抓提示词回显占位候选；ACP `protocolVersion` 误传字符串 "1"（schema 要求 number）；`NamespaceService.applyStrategyConfig` 把 `{}` 段模板静默发布（数据+代码双修）；值域缺 dataType 产生 findById 不可加载行（validator 与实体 optional=false 口径矛盾）；`MetadataObjectAssociationException` 无全局映射落 500；EP popover 半受控强关/点击重复发请求/生成中悬停不恢复（三连前端交互修复）；调试侧栏非模态化+宽度可调。
- 部署形态定型：`~/.metadata-registry/`（inference-agents.yml v2、ai-assist.yml、application-local.yml 数据源）；端口固定 8081（winnat 抢占处置过一遍）；前端 dist 同步 static 重打包重启。

## Decisions

- AI 使用=仅 REGISTRAR（业务角色），配置=SUPER_ADMIN；JWT 增加 registrar claim（旧令牌需重登录）。
- 直连模型 API（direct）整体移除，日后另行立项，不混在本地 Agent 配置里。
- 非流程决定性选项（Agent/候选数/快捷键）一律前置系统设置（交互原则 D8，已写入 design 与 system.md）。
- valid_period/datatype 约束只落应用层校验，不改数据库列可空性（三方言 ddl-auto 行为分歧）。
- 回填占位基准日 2026-01-01，RA 可经编辑界面修正。

## Verification

- 后端各轮目标包全绿（最终 inference/assistant 139、关联校验/值域/概念域 64、dataset/migration 19）；前端 vitest 最终 140/140、build 过。
- 真实握手/冒烟：三 Agent ACP initialize + PINEAPPLE 端到端；valid_period API 冒烟（缺 start 422 带条款、带 start 200、冒烟数据已清）。
- 本机调试令牌（`~/.metadata-registry/application-local.yml` 的 jwt.secret + admin id 铸 HS512）用于无口令 API 验证，方法留档本日记。
- 既有基线失败（与改动无关，stash 对照确认）：CaseInsensitiveEnumPersistenceTest×2、ItemSortMvcIntegrationTest×2、ResetDatabaseHelperTest、NamespaceGateIntegrationTest×4（加载用户级 application-local.yml 的 orm-team 映射所致）。

## Next Step

- **全部 256 个工作区文件未 git commit**——下次第一件事：按 OpenSpec 变更分组提交（提交前跑 detect_changes，排除 `_*.txt`/logs/data 杂物；static 构建产物随前端变更提交）。
- 未归档六个半变更（含 enforce-valid-period）待归档；归档后 openspec/specs 才能反映 AI 辅助与校验能力。
- 遗留人工验收：真实 CLI→ACP 全流程 UI 实测（新建值域有效期、字段级生成、对话窗、调试侧栏）。
