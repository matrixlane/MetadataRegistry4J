-- 孤儿表物理清理(V20260901015 漏网):item_classification、sequence_registry。
-- 两表均无 JPA 实体与代码引用,为历史残留;无 schema,直删。
DROP TABLE IF EXISTS item_classification;
DROP TABLE IF EXISTS sequence_registry;
