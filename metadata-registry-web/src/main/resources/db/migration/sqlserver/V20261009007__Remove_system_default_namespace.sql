-- ============================================================================
-- V20261009007__Remove_system_default_namespace.sql  (SQL Server)
--
-- 移除 sys: 系统默认命名空间预置行（remove-system-default-namespace，2026-10-04
-- 用户裁定：ISO/IEC 11179-3:2023 无"系统默认/根命名空间"概念；实库核实其未 scope
-- 任何 ScopedIdentifier、无 Namespace 被识别、无任何业务数据承载）。按 scheme_reference
-- 定位目标行（shorthand_prefix 标准上可空可改，不作定位依据，同 design D1）。
--
-- 防御（D2）：被 scoped_identifier / identified_item 引用的命名空间跳过删除——
-- 残留行不再被代码特判，按"无关联注册机构"走外部命名空间路径，无破坏性。
-- 顺序（D3）：先子后父（关联表 → designation 子表 → designation → namespace
-- → item JOINED 根行）。mdr 表全限定避免受 default_schema 影响
-- （同 V20260930001/V20261008001 模式）。条件删除天然幂等，可重入。
-- ============================================================================

DROP TABLE IF EXISTS #sys_default_ns;

SELECT ns.id INTO #sys_default_ns
FROM mdr.namespace ns
WHERE ns.scheme_reference = 'urn:metadata-registry:system-default'
  AND NOT EXISTS (SELECT 1 FROM mdr.scoped_identifier si WHERE si.namespace_id = ns.id);

-- 1. Namespace → NamingConvention 关联（V20261009006 新表名）
DELETE FROM mdr.naming_convention_utilization
WHERE namespace_id IN (SELECT id FROM #sys_default_ns);

-- 2. Designation → Namespace 关联（namespace 侧）
DELETE FROM mdr.designation_namespace
WHERE namespace_id IN (SELECT id FROM #sys_default_ns);

-- 3. sys: 自身指称（designation.item_id）的子表行，先子后父
DELETE FROM mdr.designation_context
WHERE designation_id IN (
    SELECT d.id FROM mdr.designation d
    WHERE d.item_id IN (SELECT id FROM #sys_default_ns)
);

DELETE FROM mdr.designation_definition_pairing
WHERE designation_id IN (
    SELECT d.id FROM mdr.designation d
    WHERE d.item_id IN (SELECT id FROM #sys_default_ns)
);

DELETE FROM mdr.naming_convention_conformance
WHERE designation_id IN (
    SELECT d.id FROM mdr.designation d
    WHERE d.item_id IN (SELECT id FROM #sys_default_ns)
);

DELETE FROM mdr.designation_namespace
WHERE designation_id IN (
    SELECT d.id FROM mdr.designation d
    WHERE d.item_id IN (SELECT id FROM #sys_default_ns)
);

-- 4. 指称本体
DELETE FROM mdr.designation
WHERE item_id IN (SELECT id FROM #sys_default_ns);

-- 5. namespace 本体
DELETE FROM mdr.namespace
WHERE id IN (SELECT id FROM #sys_default_ns);

-- 6. item 根行（Namespace extends Item，JOINED 继承）
DELETE FROM mdr.item
WHERE id IN (SELECT id FROM #sys_default_ns);

DROP TABLE #sys_default_ns;

-- ============================================================================
-- 历史系统组织（固定 UUID …0001）：仅当无任何引用（namespace.naming_authority_id、
-- contact.organization_id）时清理（design D5）。有引用的存量行保留，但不再享受
-- 代码侧特殊保护（OrganizationService 过滤与删除保护已随本 change 移除）。
-- ============================================================================
IF EXISTS (SELECT 1 FROM mdr.organization WHERE id = '00000000-0000-0000-0000-000000000001')
   AND NOT EXISTS (SELECT 1 FROM mdr.namespace WHERE naming_authority_id = '00000000-0000-0000-0000-000000000001')
   AND NOT EXISTS (SELECT 1 FROM mdr.contact WHERE organization_id = '00000000-0000-0000-0000-000000000001')
BEGIN
    DELETE FROM mdr.organization_name WHERE organization_id = '00000000-0000-0000-0000-000000000001';
    DELETE FROM mdr.organization_email WHERE organization_id = '00000000-0000-0000-0000-000000000001';
    DELETE FROM mdr.organization_phone WHERE organization_id = '00000000-0000-0000-0000-000000000001';
    DELETE FROM mdr.organization WHERE id = '00000000-0000-0000-0000-000000000001';
END;
