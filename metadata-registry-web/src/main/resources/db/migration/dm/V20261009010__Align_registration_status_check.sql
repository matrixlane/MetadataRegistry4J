-- ============================================================================
-- V20261009010__Align_registration_status_check.sql  (达梦 DM8)
--
-- repair-administered-item-closure（2026-10-09）：registration_status 的 CHECK
-- 约束对齐 ISO/IEC 11179-3:2023 表 30（9.6.2）/ ISO/IEC 11179-6:2023 表 1（4.3.2）
-- 的十个标准状态。与 SQL Server 版同源，差异为全限定 schema 写法。
--
-- 【未实测】按项目既有惯例，达梦方言迁移长期未在真实达梦实例上验证
-- （见 .superpowers-memory/LEARNING_BACKLOG.md）。本脚本语法对齐 SQL Server 版，
-- 实际应用前需在达梦实例上先行验证。
--
-- 步骤：动态删 CHECK → 存量旧值归位 → 按标准十值集重建。
--
-- 顺序要点（与 SQL Server / SQLite 版一致）：归位 MUST 排在删约束之后。
-- 旧 CHECK 允许集里没有 CANDIDATE / PREFERRED_STANDARD，先改数据会被旧约束自身拒绝。
--
-- 书写形式：UPPER(REPLACE(_, ' ')) 归一，使枚举常量名与标准书写形式均放行。
-- ============================================================================

-- 1. 动态删除全部 CHECK 约束（达梦约束名同为本系统生成，不可硬编码）
DECLARE
    ck_name   VARCHAR(128);
    drop_sql  VARCHAR(512);
BEGIN
    FOR ck_rec IN (
        SELECT c.CONSTRAINT_NAME AS cn
        FROM USER_CONSTRAINTS c
        WHERE c.CONSTRAINT_TYPE = 'C'
          AND c.TABLE_NAME = 'REGISTRATION_STATE'
    ) LOOP
        ck_name := ck_rec.cn;
        drop_sql := 'ALTER TABLE MDR.REGISTRATION_STATE DROP CONSTRAINT "' || ck_name || '"';
        EXECUTE IMMEDIATE drop_sql;
    END LOOP;
END;
/

-- 2. 存量旧值归位（此时已无 CHECK 约束）
UPDATE MDR.REGISTRATION_STATE SET REGISTRATION_STATUS = 'CANDIDATE'
 WHERE REGISTRATION_STATUS = 'SUBMITTED';

UPDATE MDR.REGISTRATION_STATE SET REGISTRATION_STATUS = 'PREFERRED_STANDARD'
 WHERE REGISTRATION_STATUS = 'PREFERRED';

-- 3. 按标准十值集重建
ALTER TABLE MDR.REGISTRATION_STATE
    ADD CONSTRAINT CK_REGISTRATION_STATE_STATUS
    CHECK (UPPER(REPLACE(REGISTRATION_STATUS, '_', ' ')) IN (
        'INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED', 'STANDARD',
        'PREFERRED STANDARD', 'SUPERSEDED', 'RETIRED', 'HISTORICAL', 'APPLICATION'
    ));
/

-- 4. 管理状态索引
CREATE INDEX IDX_REGISTRATION_STATE_ADMINISTRATIVE_STATUS
    ON MDR.REGISTRATION_STATE (ADMINISTRATIVE_STATUS);