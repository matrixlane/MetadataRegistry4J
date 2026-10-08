-- ============================================================================
-- V20261009007__Remove_system_default_namespace.sql  (SQLite)
--
-- 移除 sys: 系统默认命名空间预置行（remove-system-default-namespace，2026-10-04
-- 用户裁定：ISO/IEC 11179-3:2023 无"系统默认/根命名空间"概念；实库核实其未 scope
-- 任何 ScopedIdentifier、无 Namespace 被识别、无任何业务数据承载）。按 scheme_reference
-- 定位目标行（shorthand_prefix 标准上可空可改，不作定位依据，同 design D1）。
--
-- 防御（D2）：被 scoped_identifier / identified_item 引用的命名空间跳过删除——
-- 残留行不再被代码特判，按"无关联注册机构"走外部命名空间路径，无破坏性。
-- 顺序（D3）：先子后父（关联表 → designation 子表 → designation → namespace
-- → item JOINED 根行）。条件删除天然幂等，可重入。
--
-- 命名规范关联表双名兼容：sqlite 方言未执行 V20261009006（Merge_renamed_tables），
-- 既有库保留旧表名（namespace_naming_convention / designation_naming_convention）；
-- 全新库/新代码由 Hibernate ddl-auto 按实体新名建表（naming_convention_utilization /
-- naming_convention_conformance）。四张候选表统一以 CREATE TABLE IF NOT EXISTS 骨架
-- 守卫（仅当表缺失时建空表，无副作用），DELETE 对空表不产生影响。
-- ============================================================================

DROP TABLE IF EXISTS temp.sys_default_ns;

CREATE TEMP TABLE sys_default_ns AS
SELECT ns.id AS id
FROM namespace ns
WHERE ns.scheme_reference = 'urn:metadata-registry:system-default'
  AND NOT EXISTS (SELECT 1 FROM scoped_identifier si WHERE si.namespace_id = ns.id);

-- 1. Namespace → NamingConvention 关联（新名 + 旧名双清）
CREATE TABLE IF NOT EXISTS naming_convention_utilization (
    namespace_id BLOB, naming_convention_id BLOB
);
DELETE FROM naming_convention_utilization
WHERE namespace_id IN (SELECT id FROM temp.sys_default_ns);

CREATE TABLE IF NOT EXISTS namespace_naming_convention (
    namespace_id BLOB, naming_convention_id BLOB
);
DELETE FROM namespace_naming_convention
WHERE namespace_id IN (SELECT id FROM temp.sys_default_ns);

-- 2. Designation → Namespace 关联（namespace 侧）
DELETE FROM designation_namespace
WHERE namespace_id IN (SELECT id FROM temp.sys_default_ns);

-- 3. sys: 自身指称（designation.item_id）的子表行，先子后父
DELETE FROM designation_context
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT id FROM temp.sys_default_ns)
);

DELETE FROM designation_definition_pairing
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT id FROM temp.sys_default_ns)
);

CREATE TABLE IF NOT EXISTS naming_convention_conformance (
    designation_id BLOB, naming_convention_id BLOB
);
DELETE FROM naming_convention_conformance
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT id FROM temp.sys_default_ns)
);

CREATE TABLE IF NOT EXISTS designation_naming_convention (
    designation_id BLOB, naming_convention_id BLOB
);
DELETE FROM designation_naming_convention
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT id FROM temp.sys_default_ns)
);

DELETE FROM designation_namespace
WHERE designation_id IN (
    SELECT d.id FROM designation d
    WHERE d.item_id IN (SELECT id FROM temp.sys_default_ns)
);

-- 4. 指称本体
DELETE FROM designation
WHERE item_id IN (SELECT id FROM temp.sys_default_ns);

-- 5. namespace 本体
DELETE FROM namespace
WHERE id IN (SELECT id FROM temp.sys_default_ns);

-- 6. item 根行（Namespace extends Item，JOINED 继承）
DELETE FROM item
WHERE id IN (SELECT id FROM temp.sys_default_ns);

DROP TABLE temp.sys_default_ns;

-- ============================================================================
-- 历史系统组织（固定 UUID …0001，sqlite 存 16 字节 BLOB）：仅当无任何引用
-- （namespace.naming_authority_id、contact.organization_id）时清理（design D5）。
-- 有引用的存量行保留，但不再享受代码侧特殊保护（OrganizationService 过滤与
-- 删除保护已随本 change 移除）。
-- ============================================================================
DELETE FROM organization_name
WHERE organization_id = x'00000000000000000000000000000001'
  AND NOT EXISTS (SELECT 1 FROM namespace WHERE naming_authority_id = x'00000000000000000000000000000001')
  AND NOT EXISTS (SELECT 1 FROM contact WHERE organization_id = x'00000000000000000000000000000001');

DELETE FROM organization_email
WHERE organization_id = x'00000000000000000000000000000001'
  AND NOT EXISTS (SELECT 1 FROM namespace WHERE naming_authority_id = x'00000000000000000000000000000001')
  AND NOT EXISTS (SELECT 1 FROM contact WHERE organization_id = x'00000000000000000000000000000001');

DELETE FROM organization_phone
WHERE organization_id = x'00000000000000000000000000000001'
  AND NOT EXISTS (SELECT 1 FROM namespace WHERE naming_authority_id = x'00000000000000000000000000000001')
  AND NOT EXISTS (SELECT 1 FROM contact WHERE organization_id = x'00000000000000000000000000000001');

DELETE FROM organization
WHERE id = x'00000000000000000000000000000001'
  AND NOT EXISTS (SELECT 1 FROM namespace WHERE naming_authority_id = x'00000000000000000000000000000001')
  AND NOT EXISTS (SELECT 1 FROM contact WHERE organization_id = x'00000000000000000000000000000001');
