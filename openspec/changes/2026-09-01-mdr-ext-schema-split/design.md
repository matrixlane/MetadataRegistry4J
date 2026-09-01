# 设计:MDR/EXT schema 拆分

## 1. 现状

- team profile:`hibernate.default_schema=mdr` + `db/schema-init.sql`(sqlserver 建 mdr schema;dm 跳过,用户即模式)。全部表混居 `mdr`。
- local/personal/测试:SQLite,无 schema,flyway 关,ddl-auto=update。
- 启动顺序:flyway 先于 ddl-auto=update(实测确认)。

## 2. 机制选型

候选:
- **A. 实体注解 `@Table(schema="ext")`**:简单但 SQLite 无法容忍 schema 限定名(sqlite-jdbc 把 `ext.table` 解析为 attached database,报 unknown database),违背"SQLite 不用 schema"决策。否决。
- **B. per-profile orm.xml(选定)**:team 加载 `orm-team.xml` 覆盖 23 个 EXT 实体的表 schema;SQLite profile 不加载,实体注解保持无 schema。完全满足"SQLite 零变化"。代价:维护一份实体清单 XML。
- **C. SQLite ATTACH 模拟 schema**:连接级状态,连接池下脆弱。否决。

选定 B。orm.xml 为合并模式(不写 `xml-mapping-metadata-complete`),只覆盖 `<table>` 的 schema 属性,其余映射继承注解。

## 3. orm-team.xml 清单(25 实体,含 V20260901015 补迁 2)

| 模块 | 类 | 表 |
|---|---|---|
| ext | ClassificationScheme | classification_scheme |
| ext | ClassificationNode | classification_node |
| ext | IdentifierStrategyConfig | identifier_strategy_config |
| ext | SequenceRegistry | identifier_sequence_registry |
| ext | IdentifierVersionHistory | identifier_version_history |
| ext | ScopedIdentifierLifecycle | scoped_identifier_lifecycle |
| ext | NamespaceIdentifierSettings | namespace_identifier_settings |
| ext | ContactAccountBinding | contact_account_binding |
| ext | LanguageGovernance | language_governance |
| ext | NamingConventionRules | naming_convention_rules |
| common-facilities | Account | account |
| data-set-registration | InteropDataSet | interop_data_set |
| data-set-registration | InteropDataElement | interop_data_element |
| item-mapping | MappingScheme | mapping_scheme |
| item-mapping | MappingSubscription | mapping_subscription |
| item-mapping | FhirElementMapping | fhir_element_mapping |
| item-mapping | InteropMapping | interop_mapping |
| web | ItemLink | item_link |
| web | MergeRequest | merge_request |
| web | MergeRequestItem | merge_request_item |
| web | NamespacePermission | namespace_permission |
| web | RegistryNode | registry_node |
| web | SyncWatermark | sync_watermark |
| web | SystemSetting | system_setting |
| web | VersionHistory | version_history |

派生/集合表(@ElementCollection @CollectionTable、@JoinTable)默认随主表 schema:MDR 侧的(language_variant 等)不动;EXT 实体无集合表(逐一核对)。

## 4. schema-init.sql

```sql
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'mdr') EXEC('CREATE SCHEMA mdr')
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'ext') EXEC('CREATE SCHEMA ext')
```

达梦:"用户即模式",EXT 模式须由 DBA 预建(CREATE USER/SCHEMA)并对应用用户授 SELECT/INSERT/UPDATE/DELETE;文档写入部署说明。应用启动不尝试在 dm 建 EXT。

## 5. 迁移 V20260901014

### sqlserver

逐表(23 张,存在才迁):
```sql
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='<t>')
    ALTER SCHEMA ext TRANSFER mdr.<t>;
```
TRANSFER 保留约束/索引/触发器;幂等。注意:MDR 表对 EXT 表的 FK(若有)跨 schema 合法,无需处理;EXT→MDR 同理。

### dm

`CREATE SCHEMA`/授权前置由 DBA 完成。脚本逐表守卫:
```sql
-- 伪码:若 MDR.<T> 存在且 EXT.<T> 不存在
CREATE TABLE EXT.<T> AS SELECT * FROM MDR.<T>;
-- 重建 PK/UK/索引(逐表显式 DDL)
DROP TABLE MDR.<T>;
```
PL/SQL 块 + EXCEPTION WHEN OTHERS THEN NULL;EXT 模式不存在时整表跳过(异常吞掉),启动日志可复查。约束重建 DDL 按各表 @Table/@Index 逐一核对生成。

### sqlite

```sql
-- 占位:SQLite 无 schema,EXT 表留 main,无操作。
```

## 6. 原生 SQL 核查点

`DatabaseEnvironment.qualify()` 只套 `mdr`。逐一排查:
- `@Query(nativeQuery=true)` 与 JdbcTemplate/Statement 直写 SQL 中引用 23 张 EXT 表的,team 方言下改显式 `ext.` 前缀(或经 DatabaseEnvironment 扩展 extQualify());
- JPQL 无需处理(经实体映射,orm.xml 已覆盖)。

## 7. 验证

1. 全模块编译 + 测试 1487 全绿(测试环境不加载 orm.xml,行为不变,绿即证明 SQLite 路径零回归);
2. 编译期核对 orm-team.xml 23 个 class 全限定名存在(写一次性校验测试:解析 XML→Class.forName,防类名漂移);
3. detect_changes 无 partial/truncated;
4. team profile 的 sqlserver/dm 实测由用户部署时验证(本地无实例)。

## 7.1 补遗(2026-09-01,V20260901015)

- 盘点遗漏:data-set-registration 模块 InteropDataSet/InteropDataElement 两实体补入 orm-team.xml 并迁 ext;
- 顺带物理删除阶段 2 已删功能的无实体孤儿表(approval_*/notification_*/data_quality_task/export_task/definition_audit_log/fhir_code_system/fhir_element/fhir_resource/fhir_value_set/designation_context/definition_context/definition_namespace)与 3 张测试垃圾表(*_service_test$test_item);均经实体源码核实无引用;
- definition_bak_20260901/designation_bak_20260901 含数据,删除与否留待用户决策。

## 8. 不做的事

- 不迁 Account 类到 ext 模块(仅归 schema;迁模块另议);
- 不细分 MDR_RCF/MDR_DSR/X_*(用户决策 MDR/EXT 两 schema);
- 不动 item-mapping 三表(归 MDR,用户 2026-09-01 决策);
- 不清理 HTE_*/HT_* 历史遗留表与已删功能残留表(生产库 DBA 事项,备份/临时表约定不入正式 schema)。
