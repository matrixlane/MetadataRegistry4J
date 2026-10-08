-- ============================================================================
-- V20261009008__Decouple_registry_specification_from_item.sql  (SQL Server)
--
-- registry-specification-model-correction（2026-10-09）：ISO/IEC 11179-3:2023
-- 9.4.10 未声明 Registry_Specification 为 Item 子类（无 Direct superclass 小节），
-- 实体已由 extends Item 改为独立根实体 extends Entity。本迁移完成物理解绑：
--
-- 1) 删除 Hibernate JOINED 继承启动时生成的 FK（registry_specification → item）。
--    约束名由 Hibernate 命名策略生成（实库核实为 FK1c3c6293egus14tbd3hxi638a），
--    不可硬编码——按 sys.foreign_keys 动态解析；不存在则跳过（全新库从未有过）。
-- 2) 清理存量注册规范的 Item 结构（createDefault 产生：item 基行 + designation 行；
--    旧版设置页「注册标识」区块曾允许 registration，故覆盖 identified/administered/
--    scoped 链，按 FK 依赖序先子后父）。全新库（无注册规范数据）天然空转。
--
-- 注意（2026-10-09 评审 B1 修正）：designation_context_relation /
-- designation_naming_convention 两旧名表已被 V20261009005/09006 删除，本脚本
-- 不得引用（残留实例由后续清理处理，与本迁移无关）。
--
-- 顺序：先删 FK（否则 item 基行被引用删不掉），再按先子后父删除。
-- mdr 全限定（同 V20260930001/V20261009007 模式）；条件删除幂等可重入。
-- ============================================================================

-- 1. 删除 JOINED FK（动态解析，存在才删）
DECLARE @rs_fk NVARCHAR(128);
DECLARE @drop_fk_sql NVARCHAR(400);
DECLARE rs_fk_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT DISTINCT fk.name
    FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
    WHERE fk.parent_object_id = OBJECT_ID(N'mdr.registry_specification')
      AND fkc.referenced_object_id = OBJECT_ID(N'mdr.item');
OPEN rs_fk_cursor;
FETCH NEXT FROM rs_fk_cursor INTO @rs_fk;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @drop_fk_sql = N'ALTER TABLE mdr.registry_specification DROP CONSTRAINT ' + QUOTENAME(@rs_fk);
    EXEC(@drop_fk_sql);
    FETCH NEXT FROM rs_fk_cursor INTO @rs_fk;
END
CLOSE rs_fk_cursor;
DEALLOCATE rs_fk_cursor;

-- 2. designation 现存关联子表行（先子后父；designation_context_relation /
--    designation_naming_convention 为已删除旧表，不再引用——评审 B1）
DELETE FROM mdr.designation_context
WHERE designation_id IN (
    SELECT d.id FROM mdr.designation d
    WHERE d.item_id IN (SELECT rs.id FROM mdr.registry_specification rs)
);

DELETE FROM mdr.designation_definition_pairing
WHERE designation_id IN (
    SELECT d.id FROM mdr.designation d
    WHERE d.item_id IN (SELECT rs.id FROM mdr.registry_specification rs)
);

DELETE FROM mdr.designation_namespace
WHERE designation_id IN (
    SELECT d.id FROM mdr.designation d
    WHERE d.item_id IN (SELECT rs.id FROM mdr.registry_specification rs)
);

DELETE FROM mdr.naming_convention_conformance
WHERE designation_id IN (
    SELECT d.id FROM mdr.designation d
    WHERE d.item_id IN (SELECT rs.id FROM mdr.registry_specification rs)
);

-- 3. definition 关联子表行（definition_context + pairing 的 definition_id 侧——评审 M2）
DELETE FROM mdr.definition_context
WHERE definition_id IN (
    SELECT df.id FROM mdr.definition df
    WHERE df.item_id IN (SELECT rs.id FROM mdr.registry_specification rs)
);

DELETE FROM mdr.designation_definition_pairing
WHERE definition_id IN (
    SELECT df.id FROM mdr.definition df
    WHERE df.item_id IN (SELECT rs.id FROM mdr.registry_specification rs)
);

-- 4. designation / definition / slot 行（item_id 指向注册规范基行）。
--    version_history 自 V20260901015 起属 ext schema（AGENTS.md 分层规则）；
--    registration_transition_approval 同理（mdr-standard-compliance-cleanup 迁 ext，
--    mdr 下同名旧表为历史残留，不在本迁移范围）。
DELETE FROM mdr.designation
WHERE item_id IN (SELECT rs.id FROM mdr.registry_specification rs);

DELETE FROM mdr.definition
WHERE item_id IN (SELECT rs.id FROM mdr.registry_specification rs);

DELETE FROM mdr.slot
WHERE item_id IN (SELECT rs.id FROM mdr.registry_specification rs);

DELETE FROM ext.version_history
WHERE item_id IN (SELECT rs.id FROM mdr.registry_specification rs);

-- 5. 注册链（旧版「注册标识」区块可能产生；spec 的 identified/administered 行与其
--    identified_item.id / administered_item.id 同 id，直接用 spec id 集合收敛）
DELETE FROM ext.registration_transition_approval
WHERE administered_item_id IN (SELECT rs.id FROM mdr.registry_specification rs);

DELETE FROM mdr.attached_item
WHERE administered_item_id IN (SELECT rs.id FROM mdr.registry_specification rs);

DELETE FROM mdr.administered_item
WHERE id IN (SELECT rs.id FROM mdr.registry_specification rs);

DELETE FROM mdr.scoped_identifier
WHERE identified_item_id IN (SELECT rs.id FROM mdr.registry_specification rs);

DELETE FROM mdr.registered_item
WHERE id IN (SELECT rs.id FROM mdr.registry_specification rs);

DELETE FROM mdr.identified_item
WHERE item_id IN (SELECT rs.id FROM mdr.registry_specification rs);

-- 6. item JOINED 基行（注册规范行本身保留在 registry_specification，表 28 数据无损）
DELETE FROM mdr.item
WHERE id IN (SELECT rs.id FROM mdr.registry_specification rs);
