-- ============================================================================
-- V20261023001__Drop_mdr_registration_transition_orphans.sql  (SQL Server)
--
-- drop-mdr-registration-transition-orphans（2026-10-23）：registration_transition_*
-- 三表是应用自有的注册状态流转审批工作流（ISO/IEC 11179 各分部均无此概念），按
-- mdr-standard-compliance 规范 MUST 仅存于 ext schema。V20261009005 的迁移守卫
-- （ext 同名表已存在则跳过 ALTER SCHEMA TRANSFER）使部分既有库——ddl-auto 先于
-- Flyway 按 orm-team.xml 建出 ext 表——残留 mdr 空壳副本，且 ddl-auto 把
-- ext.registration_transition_rule_approver 的外键误连到 mdr 副本。
--
-- 本迁移在确认 ext 正式副本存在后：
--   1. 拆除 ext.registration_transition_rule_approver 上误指向
--      mdr.registration_transition_rule 的外键，改指 ext.registration_transition_rule；
--   2. 删除 mdr schema 下三张孤儿副本（仅当副本为空——有数据则拒绝删除）。
--
-- 数据安全守卫：仅当 mdr 与 ext 副本并存（双副本）时执行；ext 副本缺失时一律
-- 跳过——绝不删除唯一副本。全守卫、幂等可重入；全新库空转。
-- ============================================================================

-- 变量须在批首声明（DECLARE 不置于 IF 块内）；动态 SQL 先拼入变量再 EXEC，
-- 本机 SQL Server 对 EXEC('...' + 函数(@var)) 直接拼接形式报语法错误
DECLARE @miswired_fk nvarchar(128);
DECLARE @drop_fk_sql nvarchar(400);

IF EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'registration_transition_rule')
    AND EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'registration_transition_rule')
BEGIN
    -- 1. 外键改指 ext 副本（守卫：仅当误指向 mdr 时才拆；Hibernate 生成的约束名
    --    不保证稳定，按 sys.foreign_keys 动态解析）
    SELECT @miswired_fk = name FROM sys.foreign_keys
     WHERE parent_object_id = OBJECT_ID('ext.registration_transition_rule_approver')
       AND referenced_object_id = OBJECT_ID('mdr.registration_transition_rule');
    IF @miswired_fk IS NOT NULL
    BEGIN
        SET @drop_fk_sql = 'ALTER TABLE ext.registration_transition_rule_approver DROP CONSTRAINT ' + QUOTENAME(@miswired_fk);
        EXEC(@drop_fk_sql);
    END

    IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                   WHERE parent_object_id = OBJECT_ID('ext.registration_transition_rule_approver')
                     AND referenced_object_id = OBJECT_ID('ext.registration_transition_rule'))
        ALTER TABLE ext.registration_transition_rule_approver
            ADD CONSTRAINT fk_ext_rt_rule_approver_rule
            FOREIGN KEY (rule_id) REFERENCES ext.registration_transition_rule (id);

    -- 2. 删除 mdr 孤儿副本（空表守卫：非空拒绝删除，防数据损失）
    IF NOT EXISTS (SELECT 1 FROM mdr.registration_transition_rule_approver)
        DROP TABLE mdr.registration_transition_rule_approver;
    IF NOT EXISTS (SELECT 1 FROM mdr.registration_transition_approval)
        DROP TABLE mdr.registration_transition_approval;
    IF NOT EXISTS (SELECT 1 FROM mdr.registration_transition_rule)
        DROP TABLE mdr.registration_transition_rule;
END
