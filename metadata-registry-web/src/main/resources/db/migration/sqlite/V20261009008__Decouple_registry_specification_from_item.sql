-- ============================================================================
-- V20261009008__Decouple_registry_specification_from_item.sql  (SQLite)
--
-- registry-specification-model-correction（2026-10-09）：RegistrySpecification
-- 脱离 Item 继承。SQLite 侧 Hibernate 从不为该 JOINED FK 生成声明式外键
-- （2026-10-09 核实两份历史库副本 foreign_key_list 均为空），无需重建表；
-- 本脚本仅做存量数据清理：注册规范的 designation/definition 子结构、注册链
-- 与 item 基行。全新库/未初始化库无注册规范数据，条件删除天然空转；幂等可重入。
-- 裸表名（SQLite profile 无 schema 概念）。
-- 注意（评审 B1）：designation_context_relation / designation_naming_convention
-- 两旧名表已被 V20261009005 删除，本脚本不得引用。
-- ============================================================================

-- 1. designation 现存关联子表行（先子后父）
DELETE FROM designation_context
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT rs.id FROM registry_specification rs)
);

DELETE FROM designation_definition_pairing
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT rs.id FROM registry_specification rs)
);

DELETE FROM designation_namespace
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT rs.id FROM registry_specification rs)
);

DELETE FROM naming_convention_conformance
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT rs.id FROM registry_specification rs)
);

-- 2. definition 关联子表行（definition_context + pairing 的 definition_id 侧）
DELETE FROM definition_context
WHERE definition_id IN (
    SELECT df.id FROM definition df
    WHERE df.item_id IN (SELECT rs.id FROM registry_specification rs)
);

DELETE FROM designation_definition_pairing
WHERE definition_id IN (
    SELECT df.id FROM definition df
    WHERE df.item_id IN (SELECT rs.id FROM registry_specification rs)
);

-- 3. designation / definition / slot / version_history 行
DELETE FROM designation
WHERE item_id IN (SELECT rs.id FROM registry_specification rs);

DELETE FROM definition
WHERE item_id IN (SELECT rs.id FROM registry_specification rs);

DELETE FROM slot
WHERE item_id IN (SELECT rs.id FROM registry_specification rs);

DELETE FROM version_history
WHERE item_id IN (SELECT rs.id FROM registry_specification rs);

-- 4. 注册链（旧版「注册标识」区块可能产生；spec 的 identified/administered 行同 id）
DELETE FROM registration_transition_approval
WHERE administered_item_id IN (SELECT rs.id FROM registry_specification rs);

DELETE FROM attached_item
WHERE administered_item_id IN (SELECT rs.id FROM registry_specification rs);

DELETE FROM administered_item
WHERE id IN (SELECT rs.id FROM registry_specification rs);

DELETE FROM scoped_identifier
WHERE identified_item_id IN (SELECT rs.id FROM registry_specification rs);

DELETE FROM registered_item
WHERE id IN (SELECT rs.id FROM registry_specification rs);

DELETE FROM identified_item
WHERE item_id IN (SELECT rs.id FROM registry_specification rs);

-- 5. item JOINED 基行（注册规范行本身保留）
DELETE FROM item
WHERE id IN (SELECT rs.id FROM registry_specification rs);
