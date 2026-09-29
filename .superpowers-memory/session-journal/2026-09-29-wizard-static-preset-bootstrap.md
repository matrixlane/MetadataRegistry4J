# 2026-09-29 wizard-static-preset-bootstrap（初始化向导静态预置统一 + 语言中文显示）

**状态：已归档（2026-09-29）** → `openspec/changes/archive/2026-09-29-wizard-static-preset-bootstrap`，主 spec 已合并：system-initialization +4/~1、language-identification-edit +3（openspec validate 61/61）。设计稿 `docs/superpowers/specs/2026-09-29-init-wizard-static-preset-design.md`、实施计划 `docs/superpowers/plans/2026-09-29-wizard-static-preset-bootstrap.md`。遗留 backlog：R8（getWizardState READY 不可达、MigrationService 导入未接 presetAll——既有缺陷）。运行库已由设计机制自愈（22+22、zh-CN=简体中文、全局默认策略 1 行）。

## 起因

现场缺陷：SQL Server 库 `ext.language_identification` 0 行（语言名称/地区名称全空），语言页两列与所有语言下拉无中文名称。根因：向导 `setup()` 的 `clearBusinessDataOnly()` 清空除 3 张保留表外全部 113 张业务表（`DatabaseCleaner.clearTables` 的参数是**保留表清单**），把恢复出厂刚重放好的语言名称又抹掉，而 Flyway 不重跑已应用迁移。当天先打了止血补丁（setup 后 `languagePresetSeeder.reapplyPreset()`），用户随后要求"根本上解决"：数据库设置完成后立即预置全部静态数据，之后步骤只做用户数据；所有语言下拉显示中文名称；当前库不做临时修复。

## 方案（用户确认）

方案 A：启动期统一预置。新增 `StaticDataPresetService.presetAll()`（幂等，五步骤：重放语言预置 SQL 清单 → 系统组织 + `sys:` 命名空间 → `identifier_strategy_config` 全局默认行 → 占位超级管理员 → 每 RA 标准流转规则调和，末尾 `identifierStrategyLoader.refreshRegistry()` 双保险），由启动期 `StaticDataPresetRunner`（@Order(10)，先于 @Order(90) 的 IdentifierStrategyLoader）/ 恢复出厂 / 向导 setup 清理后三处复用。决策：zh-CN=「简体中文」（新增迁移 + 预置重放双通道）；Registry Context 仍归 commit 后派生数据；演示数据独立开关 `app.seed.demo-data.enabled` 默认 false；OpenSpec 变更 `wizard-static-preset-bootstrap`（spec delta：system-initialization + language-identification-edit）。

## 关键实现事实（下次别再踩）

- **`identifier_strategy_config` 的 `identifier_type`/`sequence_scope` 列已于 V20260727001 移除**——INSERT 必须按当前实体列集合（id 为 Long 自增、strategy_type、namespace_id、config_json、version、status、is_global_default、created_by、created_at、effective_from、effective_until、modified_by、modified_at）；该表在 `DatabaseEnvironment.EXT_TABLES` 白名单内，裸 SQL 走 `qualifyAuto` 路由 ext schema。
- **占位管理员的 NO_PASSWORD 是明文哨兵**（AuthService/InitWizardService 用 `"NO_PASSWORD".equals(hash)` 判定），不能 `passwordEncoder.encode`。
- 预置重放脚本清单 = `V20260919003` + `db/preset/<vendor>/localize-language-names.sql`（后者是 V20260929001 迁移的"活副本"，两通道语义 MUST 一致；出厂/向导兜底重放 003 会重新插入「中文」，必须靠第二条语句修正回「简体中文」）。
- 删除 `LanguageInitializer`（22 条语言代码硬编码双数据源，语言代码唯一源 = SQL 脚本）、`DataInitializer`（并入 presetAll）、`RegistrationProcessSeedInitializer`（并入 presetAll）；`SystemDefaultNamespaceInitializer` 去掉 Runner/条件装配语义，由统一入口编排。
- 向导 `clearBusinessDataOnly()` 保留集 += `language_identification`/`ext_language_identification`（按裸表名匹配，两 schema 同名表都被保留）。
- 前端（子代理交付）：向导文档语言删除硬编码 22 标签数组改 API 驱动；MultilingualTextEditor/DesignationEditor/DefinitionEditor/RegistrySpecificationSettings/ItemFormDialog 下拉 `label = languageName || languageIdentifier`；`dict.ts` 补字段；LanguageList placeholder 去掉"自动回填"过时文案。已知副作用：`buildAiDraft` 现在把中文名发给后端 AI 上下文（candidate 回程仍按标识符解析，无破坏）。

## 验证

- 后端 web 全量 1330 tests：20 个未绿全部集中在三个被 registrar-three-elements WIP 修改的类（InitWizardServiceIntegrationTest 5、RegistrarServiceTest 6、WorkspaceServiceIntegrationTest 9）——与基线对照（InitWizard 基线 6 个，现 5 个且无新增失败点）、失败不随 `app.seed.demo-data.enabled` 开关变化、WorkspaceService.java 本身带未提交 WIP 改动，判定为既有 WIP 红，非本变更引入。FactoryResetLanguagePresetIntegrationTest（WIP 未提交文件）断言从「中文」更新为「简体中文」后通过。
- 新增 StaticDataPresetServiceTest（步骤 InOrder、幂等插入、占位管理员跳过/写入）4 用例；LanguagePresetSeederTest 断言更新 + 新增"旧「中文」经重放修正为「简体中文」"用例。
- SQLite 迁移与预置脚本在 scratch 库实测；SQL Server 迁移在 scratch 库（mdr_scratch_ln，已删）实测 zh-CN→简体中文；**dm 未实测**（无实例，沿用先例）。
- 前端 vitest 266/266（+4 新）、vue-tsc、build 全过。
- openspec validate 通过；detect_changes 无 partial/truncated。

## 状态

14/14 任务全勾，**待用户 `continue-review` → `continue-archive`**。当前运行库（SQL Server metadata_registry）**按用户要求未做任何修复**——重启后端并重新跑一遍初始化向导（或恢复出厂）即由新机制自动写入语言名称；届时 V20260929001 迁移也会随启动应用到该库。注意：工作区 registrar-three-elements 的 WIP 红（上述 20 个）需在提交前由该变更的主人处理。

## 重启交付记录（2026-09-29 02:22）

- 按用户要求重新打包并重启：dist→static 同步后 `-Dmaven.test.skip=true package`（当时另一活动会话正在改 InitWizardServiceIntegrationTest，test 编译处于中间态，故跳测试编译；主代码此前已全量验证）。
- **Flyway 校验坑**：当前库在 09-26 曾应用 `20260926001`（enforce-language-identification-uniqueness，当时从 worktree 构建运行），而主树一直没有该迁移文件——只要本地出现更高版本迁移（本次 V20260929001），校验即失败（本地最新低于已应用缺失版本时 Flyway 容忍，超过即报错）。处理：从 `.worktrees/enforce-language-identification-uniqueness` 原样复制三方言 V20260926001 文件进主树（与已应用 checksum 一致）。**该 worktree 已完成归档提交但尚未合并回主树**，后续需随该变更归档一并合并，否则主树与任何应用过 26001 的库不兼容。
- 重启结果（设计机制自动生效，非手工修复）：V20260929001 已应用到当前库；`presetAll()` 补入 22 条语言名称（zh-CN=简体中文）；`identifier_strategy_config` 全局默认行首次写入（1 行）。后端 :8181 正常，static 已含新前端构建。

## 评审轮（2026-09-29，双独立代理：后端+前端）

结论：骨架无 BLOCKER 级架构问题（事务/连接同一性经 JpaTransactionManager 绑定论证且有既有实证；ApplicationRunner/CommandLineRunner 在 Boot 3.5 统一 Runner 排序下 @Order 混排生效；六份 SQL 方言风格一致、dm UPDATE 语法合法）。修复 7 项，登记 2 项既有遗留。

**修复（已落地并验证）**：
1. **BLOCKER（前端评审）**：`/api/v1/languages` 不在未初始化白名单（SecurityConfig 的 permitAll 无该路径、InitializationWizardInterceptor 未放行）→ 向导无账号态真实请求 401/499，fetch 回落空列表、预填 zh-CN 显示原始标签；e2e 用 mock 掩盖了该问题。修复：两处白名单仅放行 GET 精确路径（不开放 CRUD）。
2. `abort()` 静态预置半缺失：只保留语言两表、清掉组织/sys: 命名空间/全局策略且不重放 → 补 `presetAll()`。
3. `SystemDefaultNamespaceInitializer` catch-all 吞异常 → 用户触发的出厂/向导场景"返回成功但半缺失"；已改 fail-fast（失败由调用方事务回滚）。
4. 下拉 label 改「中文名（BCP 47 标识符）」组合（标识符重新可见）；向导去 allow-create（防中文输入污染字典）；向导"注册规范默认语言"API 化；请求带 enabled=true。
5. `ItemFormDialog` AI 草稿恢复发标识符（发中文名会导致回程 resolveLanguageId 静默回退默认语言），新增回归用例钉住。
6. 卫生：`splitStatements` 改 `\R` 去 `\r`（仓库 autocrlf 且无 .gitattributes）；删 `FactoryResetService` 未用字段；过期 `{@link LanguageInitializer}` 修掉；全局策略 INSERT 加并发冲突兜底。

**登记 backlog（非本变更引入，不处理）**：`getWizardState()` 把 sys: 计入 nsCount 致 READY 不可达（既有）；`MigrationService` 导入清空未接 presetAll（既有缺口，重启自愈）；全局默认策略写 UUID 实为空操作且永久占据 is_global_default=1（继承自 V20260527001 语义）。

**评审确认无问题项**：事务半提交窗口（同连接同事务，有 FactoryResetLanguagePresetIntegrationTest 历史事故反证）、幂等性五步逐条、删除三类无编译引用残留、SQL 六份文件一致性、数据源切换路径已由统一入口覆盖。
