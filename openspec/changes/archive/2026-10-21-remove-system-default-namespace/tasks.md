# Tasks: remove-system-default-namespace

## 1. 数据迁移（三方言）

- [x] 1.1 新增 `metadata-registry-web/src/main/resources/db/migration/sqlserver/V20261004001__Remove_system_default_namespace.sql`：按 `scheme_reference = 'urn:metadata-registry:system-default'` 定位存量 sys: 命名空间；`scoped_identifier` 无引用时先删 designation 子行（含 designation 外键关联表，按实库外键确认）与 `namespace_naming_convention` 行，再删 namespace 行；随后清理无引用（namespace.naming_authority_id、contact.organization_id）的历史系统组织（…0001）及其 organization_name/email/phone 子行。验证：在含 sys: 行的 SQL Server 测试库上执行后，namespace 表无 system-default 行、无引用 …0001 组织被清除；重复执行不报错。
- [x] 1.2 新增 sqlite 方言同名迁移（UUID BLOB 字面量按方言惯例），验证同 1.1。
- [x] 1.3 新增 dm（达梦）方言同名迁移，验证同 1.1（无达梦实例时以脚本审查 + sqlite/sqlserver 冒烟代替）。
- [x] 1.4 迁移冒烟修复（源自分支 `feat/remove-system-default-namespace` 会话，重做版已并入 `V20261009007`）：JOINED 继承基表 `item` 行必须随 `namespace` 子表同删，遗留孤儿基行导致 Hibernate 多态装载 NPE（`EntityKey.persister is null`）；三方言脚本均已含 `item` 基行及 definition/slot/item_mapping_* 子行清理与 `identified_item`/`scoped_identifier` 引用防御。

## 2. 后端主代码移除

- [x] 2.1 删除 `seed/SystemDefaultNamespaceInitializer.java` 整类；`mvn -pl metadata-registry-web compile` 通过（编译错误清单即为后续修改点）。
- [x] 2.2 `seed/StaticDataPresetService.java`：移除 `presetAll()` 中 sys: 初始化步骤与字段，更新类 Javadoc 步骤序。
- [x] 2.3 `service/InitWizardService.java`：删除 `systemDefaultNamespaceInitializer` 字段与 setup() 3.6 步调用，更新 abort() 注释。
- [x] 2.4 `service/ItemAdministrationService.java`：`enforceRaNamespacePrecondition` 外部命名空间判定简化为 `registrationAuthority == null`；`resolveDefaultNamespace` 改用 `findFirstByRegistrationAuthority_Id`。
- [x] 2.5 `repository/NamespaceRepository.java`：删除 `findFirstByRegistrationAuthority_IdAndSchemeReferenceNot` 与 `findByShorthandPrefix`，新增 `findFirstByRegistrationAuthority_Id`；编译通过。
- [x] 2.6 `service/OrganizationService.java`：删除 `SYSTEM_ORGANIZATION_ID` 三处引用（导入、列表过滤、删除保护）。
- [x] 2.7 清理注释残留：`FactoryResetService`、`application.yml`、`Namespace.java`（common-facilities）Javadoc 示例中性化。

## 3. 后端测试改造

- [x] 3.1 删除 `seed/SystemDefaultNamespaceInitializerTest.java`。
- [x] 3.2 `NamespaceGateIntegrationTest`：删除 `createDataElement_systemDefaultNamespace_blocked` 用例；`mvn -pl metadata-registry-web test -Dtest=NamespaceGateIntegrationTest` 通过。
- [x] 3.3 `ItemAdministrationServiceTest`：scheme=system-default 的用例（约 510/514、670、791、896 行）改造为验证"无 RA 命名空间按外部命名空间处理"；finder mock 切换到 `findFirstByRegistrationAuthority_Id`；该测试类通过。
- [x] 3.4 `InitWizardServiceIntegrationTest`：sys: NamingAuthority 挂接断言改为"setup 后 namespace 表仅含向导创建的命名空间"；测试类通过。
- [x] 3.5 `FactoryResetServiceTest`、`DataGovernanceReadinessServiceTest`：注释/描述更新；相关测试类通过。

## 4. 前端清理

- [x] 4.1 `metadata-registry-ui/src/components/ItemRegistrationSection.vue`：删除 `SYSTEM_DEFAULT_SCHEME` 常量、`gateBlocked` 判定、默认推导中的 schemeReference 排除条件；`ItemFormDialog.vue` 注释更新。
- [x] 4.2 `ItemRegistrationSection.spec.ts`：删除/改造 system-default 用例；前端测试通过。

## 5. 文档与基线同步

- [x] 5.1 `db-baseline/README.md` 与两个基线 SQL 头部注释移除 sys: 表述。
- [x] 5.2 按签入前文档同步规则检查 `README.md`、`docs/architecture/ARCHITECTURE.md` 中 sys: 相关内容并更新。

## 6. 集成验证

- [x] 6.1 `mvn -pl metadata-registry-web -am test` 全量运行：1486 用例，失败 7/错误 4 均为 HEAD 既有基线（WorkspaceServiceIntegrationTest 与 SegmentTemplateStrategyIntegrationTest SQLITE_LOCKED 家族，实施注记已用 stash 对照验证与本变更无关），零新增失败。
- [x] 6.2 迁移冒烟：含 sys: 存量库启动应用，Flyway 执行后 namespace 表无 system-default 行；全新库向导初始化完成后 namespace 表恰好 1 行（注册机构自有命名空间）；未初始化且预置就绪的系统 `getWizardState` 返回 READY。
- [x] 6.3 `openspec validate --change remove-system-default-namespace` 通过；`openspec status --change remove-system-default-namespace` 全部 artifact 就绪。
- [x] 6.4 `node .gitnexus/run.cjs detect-changes --scope all --repo .` 提交前图谱变更分析通过。

## 实施注记（2026-10-08）

- **迁移版本号**：tasks 原文定名 `V20261004001`；实施时三方言最新已发布版本为 `V20261009006`（Flyway 默认 out-of-order=false，低版本号会被既有库忽略），故实际采用 **`V20261009007__Remove_system_default_namespace.sql`** 保证任何已应用 09006 的库继续执行。
- **范围外测试修复**：`MdrStandardComplianceCleanupMigrationTest.secondFlywayRunIsNoOp` 原以无 target 重放全部迁移，被本变更 09007 波及（fixture 库 schema 由 Hibernate 演进、缺 `scheme_reference` 列等非本变更职责的列）；已按该测试只验证本变更版本的语义补 `.target(FLYWAY_VERSION)`。
- **额外波及测试**（tasks 未列、编译所迫）：`StaticDataPresetServiceTest`、`InitWizardServiceOrderTest`、`OrganizationServiceTest`（删系统组织过滤/保护断言）、`RegistrationAuthorityDeletionServiceIntegrationTest`（sys: 夹具改为普通独立命名空间，字段 sysNsId→orphanTargetNsId）。
- **既有测试失败（对照实验确认与本变更无关）**：`WorkspaceServiceIntegrationTest`（6 失败 3 错）与 `SegmentTemplateStrategyIntegrationTest`（SQLITE_LOCKED 并发）在 git stash 回退全部 tracked 改动后于 HEAD 复现同样失败，属其他在途工作的既有问题。
- **sqlite 命名规范关联表双名兼容**：sqlite 方言未执行 V20261009006（Merge_renamed_tables），既有库为旧表名（namespace_naming_convention/designation_naming_convention）、新库为实体新名（naming_convention_utilization/naming_convention_conformance）；09007 sqlite 脚本对四张候选表以 CREATE TABLE IF NOT EXISTS 骨架守卫 + DELETE 兼容两态。
