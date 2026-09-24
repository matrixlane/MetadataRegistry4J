-- 移除"映射方案"与映射订阅（用户决策 2026-09-20）：
-- MappingScheme 非标准概念（标准模型为 Item_Mapping package），整体移除；
-- 映射订阅功能（SCHEME/ITEM）通知链路未接线，整体移除、后续独立立项。
-- 三表均无 JPA 实体与代码引用残留；item_mapping / fhir_element_mapping 仅删孤儿列。
-- SQLite 3.35+ 支持 DROP COLUMN；DROP INDEX 先于 DROP COLUMN 执行。
DROP TABLE IF EXISTS mapping_scheme;
DROP TABLE IF EXISTS mapping_subscription;
DROP INDEX IF EXISTS idx_fem_scheme;
ALTER TABLE item_mapping DROP COLUMN mapping_scheme_id;
ALTER TABLE fhir_element_mapping DROP COLUMN mapping_scheme_id;
