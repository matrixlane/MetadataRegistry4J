# 变更提案:MDR/EXT schema 拆分(mdr-ext-schema-split)

## Why

阶段 1 恢复标准一致性、阶段 2 完成代码级扩展剥离(ext 模块 + 3 个卫星实体 + web 内扩展实体)后,所有表在 SQL Server/达梦上仍物理混居单一 `mdr` schema(`hibernate.default_schema=mdr`)。用户既定决策:

1. **标准模型 schema 用 MDR、扩展用 EXT**,物理隔离;
2. **SQLite 不用 schema、不加表名前缀**,仅约定 EXT 表名不得与 MDR 重名(现状已满足,无重名);
3. item-mapping 三表(item_mapping/item_mapping_subject/item_mapping_object)按用户 2026-09-01 决策**归 MDR**(标准第 11 章内容),不随扩展剥离。

审计报告 5.1 曾建议 MDR_RCF/MDR_DSR/X_* 多 schema 细分,用户最终决策简化为 MDR/EXT 两 schema,本变更以此为准(5.1 细粒度方案作废,如需日后再分)。

## What Changes

### 1. EXT 实体清单(23 个,归 EXT schema)

- **ext 模块(10)**:ClassificationScheme、ClassificationNode(第 10 章重建后回 MDR)、IdentifierStrategyConfig、SequenceRegistry(identifier_sequence_registry)、IdentifierVersionHistory、ScopedIdentifierLifecycle、NamespaceIdentifierSettings、ContactAccountBinding、LanguageGovernance、NamingConventionRules
- **common-facilities(1)**:Account(IAM 扩展,类不迁模块,仅归 schema)
- **item-mapping(4)**:MappingScheme、MappingSubscription、FhirElementMapping、InteropMapping
- **web(8)**:ItemLink、MergeRequest、MergeRequestItem、NamespacePermission、RegistryNode、SyncWatermark、SystemSetting、VersionHistory

其余全部实体(common-facilities 标准类、data-specification 全部、item-mapping 的 ItemMapping/ItemMappingSubject/ItemMappingObject)归 MDR,由 `hibernate.default_schema=mdr` 覆盖,实体注解保持无 schema。

### 2. 技术机制(不动实体注解)

- team profile 新增 `spring.jpa.mapping-resources: classpath:orm/orm-team.xml`;该 orm.xml 对上述 23 个实体逐一 `<table schema="ext"/>` 覆盖(合并模式,其余映射继承注解)。
- SQLite profile(local/personal/测试)不加载 orm.xml,行为与现状完全一致(无 schema)。
- `db/schema-init.sql` 增加 ext schema 创建(SQL Server);达梦"用户即模式",EXT 模式需 DBA 预建并授权,文档说明。
- 派生后果:team 上 ddl-auto=update 将按 orm.xml 把 EXT 表识别在 ext schema,不会在 mdr 重复建表。

### 3. 迁移 V20260901014(既有库搬家)

- **sqlserver**:`ALTER SCHEMA ext TRANSFER mdr.<table>` 逐表迁移(约束/索引随表走),INFORMATION_SCHEMA 守卫幂等。
- **dm**:守卫式搬家(CREATE TABLE AS SELECT + 约束/索引重建 + DROP),PL/SQL 守卫 + EXCEPTION 容错;EXT 模式不存在时整体跳过并文档提示。
- **sqlite**:占位注释(无 schema,无操作)。

### 4. 原生 SQL 核查

`DatabaseEnvironment.qualify()` 只知单一 schema;所有引用 EXT 表的原生 SQL/JPQL 逐一核查,JDBC 原生 SQL 中的 EXT 表改显式 `ext.` 前缀。

### 5. 文档与记忆

- 审计报告 5.1/5.2 标注:细粒度方案被 MDR/EXT 两 schema 决策取代,本变更落地。
- 完成后更新记忆 `mdr-schema-naming-rules`。

## Capabilities

无 spec 级能力增删(纯物理存储重组;DTO/API/前端零改动)。

## Impact

- **受影响模块**:metadata-registry-web(配置、orm.xml、迁移脚本);实体类零改动。
- **受影响环境**:team profile 的 SQL Server/达梦部署(首次启动执行搬家迁移);local/personal SQLite 零影响。
- **风险**:达梦 EXT 模式需 DBA 预建;搬家迁移窗口期需停写。回退:迁移前备份,反向 TRANSFER。
