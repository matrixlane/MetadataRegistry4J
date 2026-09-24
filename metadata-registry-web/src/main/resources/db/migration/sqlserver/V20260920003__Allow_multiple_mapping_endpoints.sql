-- 忠实标准模型：一条映射可有 1..* 主体与 1..* 客体（详见 sqlite 同名脚本）。
-- uk_ims_mapping/uk_imo_mapping 由 V20260920002 以内联 UNIQUE 约束创建，
-- 须用 ALTER TABLE DROP CONSTRAINT（SQL Server 不允许 DROP INDEX 删除约束索引）。
-- 幂等：逐对象存在性守卫；兼容索引以普通索引形态存在的情况。
IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name='uk_ims_mapping' AND parent_object_id=OBJECT_ID('mdr.item_mapping_subject')) ALTER TABLE mdr.item_mapping_subject DROP CONSTRAINT uk_ims_mapping;
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name='uk_ims_mapping' AND object_id=OBJECT_ID('mdr.item_mapping_subject') AND is_unique_constraint=0) DROP INDEX uk_ims_mapping ON mdr.item_mapping_subject;
IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name='uk_imo_mapping' AND parent_object_id=OBJECT_ID('mdr.item_mapping_object')) ALTER TABLE mdr.item_mapping_object DROP CONSTRAINT uk_imo_mapping;
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name='uk_imo_mapping' AND object_id=OBJECT_ID('mdr.item_mapping_object') AND is_unique_constraint=0) DROP INDEX uk_imo_mapping ON mdr.item_mapping_object;
