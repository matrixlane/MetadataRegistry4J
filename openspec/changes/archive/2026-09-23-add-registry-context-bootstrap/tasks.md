# Tasks: add-registry-context-bootstrap

## 1. 准备与影响分析

- [x] 1.1 按 AGENTS.md 对 `InitWizardService.setup`、`ContextTestDataInitializer.run` 运行 GitNexus `impact`（upstream），确认无 HIGH/CRITICAL 风险并记录调用方；UNKNOWN 需文本搜索核实

## 2. 核心服务（TDD）

- [x] 2.1 新增 `RegistryContextBootstrapService.ensureRegistryContext(RegistrationAuthority)`：`ra.getNamespace()` 为 null 抛 `IllegalStateException`；先写失败单测再实现，验证测试通过
- [x] 2.2 实现 AUTO 路径首次创建：固定主键 UUID（`...0002`）+ 4 条自指 TextLine（`SELF_CONTEXT_REFERENCE` + PREFERRED，中英文指称 `Registry Context`/`注册系统上下文`、忠实 8.2.2.2 原文的中英文定义）+ `applyDesignations/applyDefinitions` + `ensureAdministeredItem(entity, req, ra)`（identifier/version 传 null）+ `contextRepository.save` + `recordSubmission`；单测断言记录数、自指 relation 数、归属 RA、spec.context 回填
- [x] 2.3 实现幂等守卫：`existsById(固定 UUID)` 为真直接返回既有实体，不重复创建、不重复取号；单测二次调用数量与标识不变
- [x] 2.4 实现 MANUAL_ONLY 回退：命名空间 `identifierInputMode == MANUAL_ONLY` 时 request 显式传 identifier `registry-context`、version `1.0`；单测断言 scoped identifier 值与 MANUAL 生命周期标记；AUTO 类模式生成失败不回退（异常传播）
- [x] 2.5 实现 spec.context 回填且不覆盖既有值；单测覆盖"空则挂/已挂不覆盖"

## 3. 启动补建

- [x] 3.1 实现 `backfillIfInitialized()`：未初始化返回；已存在返回；取系统唯一 RA 经 `ensureRegistryContext` 补建；全程 try/catch + log warn；单测覆盖三种分支与 RA 无命名空间 warn 跳过
- [x] 3.2 新增 `RegistryContextBackfillRunner`（ApplicationRunner，`@Order(101)`，不挂 `app.seed.enabled`，仅委托 backfill）并给 `ContextTestDataInitializer` 加 `@Order(100)`；验证既有 `ContextTestDataInitializer` 单测仍通过且种子抑制顺序正确

## 4. 向导接线

- [x] 4.1 `InitWizardService.commit()` 在 `setInitialized(true)` 之后调用 bootstrap 服务（REQUIRES_NEW 独立事务 + try/catch log warn 降级）；验证 `InitWizardServiceIntegrationTest` 全绿
- [x] 4.2 `InitWizardServiceIntegrationTest` 增补：setup 后 Registry Context 不存在；commit 后存在、scoped identifier 命名空间 = RA 命名空间、标识/版本来自策略（如 `CTX.00001`/`1.0.0`）、spec.context 非空；abort 再 setup+commit 可重建且幂等

## 5. 端到端验证

- [x] 5.1 运行 web 模块全量单测与前端构建（前端零改动，构建须保持通过），全部通过
- [x] 5.2 编写并执行 `scratch/registry-context-smoke/smoke.sh`：全新临时库启动→向导 setup+commit→断言 context 固定 UUID 记录、`scoped_identifier`（`CTX.00001`/`1.0.0`/RA 命名空间）、4 条自指 relation、`registry_specification.context_id` 回填；重启断言幂等；已初始化副本库（清空 context 表）验证启动补建路径
- [x] 5.3 按 AGENTS.md 运行 `detect_changes --scope all`，确认无 partial/truncated
- [x] 5.5 默认命名规范正则规则允许指称含空格（方案 1，用户确认）：修改 `RegexNamingConventionRule.DEFAULT_REGEX` 加入 `\s` 并同步描述文案；验证含空格指称通过默认校验的用例（含在 `RegistryContextBootstrapServiceTest` 中）通过
- [x] 5.6 存量库 acceptability CHECK 约束修复（冒烟暴露的既有平台缺陷）：三方言迁移 `V20260923002` 重建 5 张表 CHECK 为 `lower(acceptability) in (标准小写值)`；SQLite 副本库随启动自动应用并验证写入成功
- [x] 5.7 创建点移至向导 commit 之后、由 Controller 组合独立调用（冒烟暴露：setup 事务内创建既不可见又会污染外层事务；REQUIRES_NEW 又撞 SQLite 单连接池死锁）；`backfillIfInitialized` 经 ObjectProvider 自注入代理保证原子性；`IdentifierStrategyLoader` 补 `@Order(90)`（冒烟暴露：补建原在策略装载前执行、标识回退 UUID）；`InitWizardServiceIntegrationTest` 断言对齐（setup 后不存在、commit+create 后存在）
- [x] 5.8 同步 README 默认数据说明（Registry Context 引导数据，含 commit 时机措辞）并复核 AGENTS.md 文档同步规则涉及文档
