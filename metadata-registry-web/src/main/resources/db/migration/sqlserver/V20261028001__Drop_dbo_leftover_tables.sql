-- dbo 遗留表清理（cleanup-dbo-leftover-tables）
-- 2026-05 未限定表名时代落入 dbo 的扩展/孤儿表；2026-09-01 mdr/ext 拆分（V20260901014/0015）
-- 只守卫 TABLE_SCHEMA='mdr'，dbo 残留至今。依据 AGENTS.md 数据模型分层规则：dbo 不得承载业务表。
-- 本机 2026-10-28 实测 12 张全部 0 行；外键仅存在于 fhir 孤儿集合内部，按依赖序删除即可。
-- 幂等：逐表存在性守卫；interop_mapping/version_history 三重守卫
-- （dbo 副本存在 AND ext 副本存在 AND dbo 副本 0 行，不满足跳过归 DBA 事项）。

-- 零、修复 flyway_schema_history 的 installed_on 默认值（部分既有库该默认值曾被弄丢，
-- Flyway 写历史行省略 installed_on 列、依赖 DEFAULT；缺失则报 515，本迁移自身也无法记录）
IF NOT EXISTS (SELECT 1 FROM sys.default_constraints
               WHERE parent_object_id = OBJECT_ID('ext.flyway_schema_history')
                 AND COL_NAME(parent_object_id, parent_column_id) = 'installed_on')
    ALTER TABLE ext.flyway_schema_history ADD DEFAULT (GETDATE()) FOR installed_on;

-- 一、无实体孤儿表（8 张，阶段 2 已删功能残留，存在即删；先子表后父表）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_binding') DROP TABLE dbo.fhir_binding;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_element') DROP TABLE dbo.fhir_element;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_resource') DROP TABLE dbo.fhir_resource;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_value_set') DROP TABLE dbo.fhir_value_set;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_code_system') DROP TABLE dbo.fhir_code_system;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='data_quality_task') DROP TABLE dbo.data_quality_task;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='notification_preference') DROP TABLE dbo.notification_preference;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='notification_record') DROP TABLE dbo.notification_record;

-- 二、已移除功能的残留（mapping 两表功能已于 V20260920001 从 ext 删除）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='mapping_scheme') DROP TABLE dbo.mapping_scheme;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='mapping_subscription') DROP TABLE dbo.mapping_subscription;

-- 三、活表的 dbo 死副本（三重守卫；行数经 sys.partitions+OBJECT_ID 判定，避免编译期引用不存在表）
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='interop_mapping')
   AND EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='interop_mapping')
   AND NOT EXISTS (SELECT 1 FROM sys.partitions WHERE object_id = OBJECT_ID('dbo.interop_mapping') AND index_id IN (0,1) AND rows > 0)
    DROP TABLE dbo.interop_mapping;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='version_history')
   AND EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='version_history')
   AND NOT EXISTS (SELECT 1 FROM sys.partitions WHERE object_id = OBJECT_ID('dbo.version_history') AND index_id IN (0,1) AND rows > 0)
    DROP TABLE dbo.version_history;
