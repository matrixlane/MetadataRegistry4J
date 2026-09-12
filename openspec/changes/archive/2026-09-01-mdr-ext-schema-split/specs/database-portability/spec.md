## ADDED Requirements

### Requirement: MDR/EXT schema 物理隔离
在 SQL Server 与达梦(team profile)上,标准模型表 SHALL 归 `mdr` schema,扩展表(23 张:ext 模块 10、account、mapping_scheme、mapping_subscription、fhir_element_mapping、interop_mapping、item_link、merge_request、merge_request_item、namespace_permission、registry_node、sync_watermark、system_setting、version_history)SHALL 归 `ext` schema。EXT schema 归属 MUST 仅通过部署配置(team profile 的 JPA mapping-resources,即 orm-team.xml)指定,实体注解 MUST NOT 硬编码 schema。SQLite(local/personal/测试)MUST NOT 使用 schema,所有表留主库,行为与拆分前一致。item_mapping/item_mapping_subject/item_mapping_object 三表 SHALL 归 `mdr`(标准第 11 章内容,用户 2026-09-01 决策)。

#### Scenario: SQLite 路径零变化
- **WHEN** 应用以 local/personal/测试 profile 启动
- **THEN** 不加载 orm-team.xml,全部表解析到 SQLite 主库,启动与读写行为与拆分前一致

#### Scenario: team profile EXT 表归 ext schema
- **WHEN** 应用以 team profile 启动(SQL Server)
- **THEN** 23 张 EXT 表的 ORM 解析结果为 `ext.<table>`,ddl-auto 不会在 `mdr` schema 重复建这些表

#### Scenario: 存量库迁移幂等
- **GIVEN** SQL Server 存量库的 23 张扩展表位于 `mdr` schema
- **WHEN** 执行迁移 V20260901014
- **THEN** 这些表经 `ALTER SCHEMA ext TRANSFER` 迁入 `ext` schema,约束/索引/数据无损;重复执行不报错、不重复迁移

#### Scenario: 原生 SQL 访问 EXT 表
- **WHEN** team profile 下执行引用 EXT 表的原生 SQL(JdbcTemplate/nativeQuery)
- **THEN** SQL 使用显式 `ext.` 前缀,查询正常返回(不依赖连接默认 schema)
