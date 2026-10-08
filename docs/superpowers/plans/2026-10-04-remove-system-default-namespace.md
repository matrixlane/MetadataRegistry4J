# 实施计划：remove-system-default-namespace

日期：2026-10-04
来源：OpenSpec change `remove-system-default-namespace`（proposal / design / specs / tasks 已确认）

## 执行策略

- 在 repo-local worktree `.worktrees/remove-system-default-namespace` 中实现。
- TDD 顺序：先让测试表达新行为（改造/删除测试 → 红），再删主代码（绿）；迁移脚本先行并以 scratch 库冒烟验证。
- 全程不触碰真实 `metadata_registry` 库；冒烟使用 scratch 副本库。

## 步骤

1. **迁移脚本（三方言）**：编写 `V20261004001__Remove_system_default_namespace.sql`（sqlserver/sqlite/dm）。SQL Server 冒烟：新建 scratch 库 → 应用全量迁移链 → 插入 sys: 夹具行（namespace + designation + 命名空间 NamingAuthority 指向的组织）→ 执行新迁移脚本 → 断言删除；再验证无 sys: 行时脚本幂等。SQLite 冒烟同法（文件库）。dm 无实例，脚本审查 + 语法对齐 sqlserver 版。
2. **后端主代码**：删 `SystemDefaultNamespaceInitializer` → 按编译错误清单修复 `StaticDataPresetService`、`InitWizardService`、`ItemAdministrationService`、`NamespaceRepository`、`OrganizationService`、注释/Javadoc。
3. **后端测试**：删 `SystemDefaultNamespaceInitializerTest`；改造 `NamespaceGateIntegrationTest`、`ItemAdministrationServiceTest`、`InitWizardServiceIntegrationTest`、`FactoryResetServiceTest`、`DataGovernanceReadinessServiceTest`。
4. **验证（后端）**：`mvn -pl metadata-registry-web -am test` 全绿。
5. **前端**：`ItemRegistrationSection.vue` / `.spec.ts` / `ItemFormDialog.vue` 清理；跑前端相关测试。
6. **文档**：db-baseline README/头部注释、README.md、ARCHITECTURE.md 同步检查。
7. **收尾验证**：迁移冒烟（第 1 步）+ 全量测试 + `openspec validate` + `node .gitnexus/run.cjs detect-changes --scope all --repo .`。

## 风险应对

- 迁移删除顺序按实库外键确认（先子后父）；防御检查跳过有 scoped_identifier 引用的 sys: 行。
- 编译错误驱动确保无遗漏引用点。
