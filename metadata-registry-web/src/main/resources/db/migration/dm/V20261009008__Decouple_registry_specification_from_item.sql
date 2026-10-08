-- ============================================================================
-- V20261009008__Decouple_registry_specification_from_item.sql  (达梦 DM)
--
-- registry-specification-model-correction（2026-10-09）：RegistrySpecification
-- 脱离 Item 继承。逻辑同 SQL Server 版：
-- 1) 删除 Hibernate JOINED FK（ALL_CONSTRAINTS 动态解析；删除后回查，仍存留则报错
--    而非吞异常——评审 M4）；
-- 2) 清理注册规范的 designation/definition 子结构、注册链与 item 基行（先子后父）。
-- 全新库天然空转；幂等可重入。MDR. 全限定（同 V20260930001 模式）。
-- 无达梦实例，以脚本审查 + sqlite/sqlserver 冒烟代替实测（项目惯例）。
-- 注意（评审 B1）：DESIGNATION_CONTEXT_RELATION / DESIGNATION_NAMING_CONVENTION
-- 两旧名表已被 V20261009005/09006 删除，本脚本不得引用。
-- ============================================================================

-- 1. 删除 JOINED FK（动态解析 + 删除后回查校验）
DECLARE
    v_left NUMBER;
BEGIN
    FOR r IN (
        SELECT ac.constraint_name
        FROM all_constraints ac
        WHERE ac.owner = 'MDR'
          AND ac.table_name = 'REGISTRY_SPECIFICATION'
          AND ac.constraint_type = 'R'
          AND ac.r_constraint_name IN (
              SELECT constraint_name FROM all_constraints
              WHERE owner = 'MDR' AND table_name = 'ITEM' AND constraint_type = 'P'
          )
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE MDR.REGISTRY_SPECIFICATION DROP CONSTRAINT ' || r.constraint_name;
    END LOOP;
    SELECT COUNT(*) INTO v_left
    FROM all_constraints
    WHERE owner = 'MDR'
      AND table_name = 'REGISTRY_SPECIFICATION'
      AND constraint_type = 'R'
      AND r_constraint_name IN (
          SELECT constraint_name FROM all_constraints
          WHERE owner = 'MDR' AND table_name = 'ITEM' AND constraint_type = 'P'
      );
    IF v_left > 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'V20261009008: FK REGISTRY_SPECIFICATION->ITEM 删除后仍存留 ' || v_left || ' 条');
    END IF;
EXCEPTION WHEN OTHERS THEN
    RAISE;
END;
/

-- 2. designation 现存关联子表行（先子后父）
DELETE FROM MDR.DESIGNATION_CONTEXT
WHERE designation_id IN (
    SELECT d.id FROM MDR.DESIGNATION d
    WHERE d.item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs)
);

DELETE FROM MDR.DESIGNATION_DEFINITION_PAIRING
WHERE designation_id IN (
    SELECT d.id FROM MDR.DESIGNATION d
    WHERE d.item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs)
);

DELETE FROM MDR.DESIGNATION_NAMESPACE
WHERE designation_id IN (
    SELECT d.id FROM MDR.DESIGNATION d
    WHERE d.item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs)
);

DELETE FROM MDR.NAMING_CONVENTION_CONFORMANCE
WHERE designation_id IN (
    SELECT d.id FROM MDR.DESIGNATION d
    WHERE d.item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs)
);

-- 3. definition 关联子表行（definition_context + pairing 的 definition_id 侧）
DELETE FROM MDR.DEFINITION_CONTEXT
WHERE definition_id IN (
    SELECT df.id FROM MDR.DEFINITION df
    WHERE df.item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs)
);

DELETE FROM MDR.DESIGNATION_DEFINITION_PAIRING
WHERE definition_id IN (
    SELECT df.id FROM MDR.DEFINITION df
    WHERE df.item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs)
);

-- 4. designation / definition / slot 行。version_history / registration_transition_approval
--    自 mdr-standard-compliance-cleanup 起属 EXT schema（mdr 同名旧表为历史残留）。
DELETE FROM MDR.DESIGNATION
WHERE item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

DELETE FROM MDR.DEFINITION
WHERE item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

DELETE FROM MDR.SLOT
WHERE item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

DELETE FROM EXT.VERSION_HISTORY
WHERE item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

-- 5. 注册链（spec 的 identified/administered 行与其父表行同 id，直接用 spec id 集合）
DELETE FROM EXT.REGISTRATION_TRANSITION_APPROVAL
WHERE administered_item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

DELETE FROM MDR.ATTACHED_ITEM
WHERE administered_item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

DELETE FROM MDR.ADMINISTERED_ITEM
WHERE id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

DELETE FROM MDR.SCOPED_IDENTIFIER
WHERE identified_item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

DELETE FROM MDR.REGISTERED_ITEM
WHERE id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

DELETE FROM MDR.IDENTIFIED_ITEM
WHERE item_id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);

-- 6. item JOINED 基行（注册规范行本身保留）
DELETE FROM MDR.ITEM
WHERE id IN (SELECT rs.id FROM MDR.REGISTRY_SPECIFICATION rs);
