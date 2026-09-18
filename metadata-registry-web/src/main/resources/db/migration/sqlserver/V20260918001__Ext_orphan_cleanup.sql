-- ext schema 孤儿表物理清理(V20260901015 漏网):item_classification、sequence_registry。
-- 两表均无 JPA 实体与代码引用,为历史改名/功能下线残留;存在于部分存量库。
-- 幂等:逐表守卫。
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='item_classification') DROP TABLE ext.item_classification;
IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ext' AND TABLE_NAME='sequence_registry') DROP TABLE ext.sequence_registry;
