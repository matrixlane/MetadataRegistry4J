-- ============================================================================
-- V20261009010__Align_registration_status_check.sql  (SQL Server)
--
-- repair-administered-item-closure（2026-10-09）：registration_status 的 CHECK
-- 约束对齐 ISO/IEC 11179-3:2023 表 30（9.6.2）/ ISO/IEC 11179-6:2023 表 1（4.3.2）
-- 的十个标准状态。
--
-- 【阻断项】实测真实库（事务内 INSERT 后 ROLLBACK）：
--     PREFERRED_STANDARD: REJECTED
-- 现约束仍为历史错误方向的旧值集（缺 CANDIDATE / PREFERRED_STANDARD，多出标准中
-- 不存在的 SUBMITTED / PREFERRED）。反向纠偏脚本 V20260901001 三方言均在，却从未
-- 进入 flyway_schema_history——项目 baseline-version=20260918002 将版本号 ≤ 基线的
-- 迁移跳过；而方向错误的 V20260517001 在基线建立前已执行并加了 CHECK，约束就此
-- 定型。故本次新起版本号（不可复用 V20260901001，复用等于不执行）。
--
-- 步骤：
--   1) 动态解析 registration_state 上的 CHECK 约束名并全部删除——约束名由
--      Hibernate 命名策略生成（实库核实为 CK__registrat__regis__7EF6D905），
--      不可硬编码；
--   2) 存量旧值归位（SUBMITTED→CANDIDATE、PREFERRED→PREFERRED_STANDARD），幂等；
--   3) 按标准十值集重建。
--
-- 顺序要点（2026-10-09 验证期修正）：归位 UPDATE MUST 排在删约束之后。
-- 旧 CHECK 的允许集里没有 CANDIDATE / PREFERRED_STANDARD，先改数据会被旧约束
-- 自身拒绝（CHECK constraint failed / 违反 CHECK 约束），形成死锁。
-- 正确次序是"先松绑、再改值、再收紧"。
--
-- 书写形式：实体用 @Enumerated(EnumType.STRING)，应用写入的是枚举常量名
-- （PREFERRED_STANDARD）。但读取侧须容忍标准书写形式 "Preferred Standard"
-- （含空格）与任意大小写——CaseInsensitiveEnumPersistenceTest 即以 "Standard"
-- 直写直读。故 CHECK 以 UPPER(REPLACE(_, ' ')) 归一后比对，两种书写形式均放行。
--
-- mdr 全限定；动态解析 + 条件执行，幂等可重入。
-- ============================================================================

-- 1. 动态删除全部 CHECK 约束（必须先于归位 UPDATE，见文件头"顺序要点"）
DECLARE @ck NVARCHAR(128);
DECLARE @drop_sql NVARCHAR(400);
DECLARE ck_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT cc.name
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID(N'mdr.registration_state');
OPEN ck_cursor;
FETCH NEXT FROM ck_cursor INTO @ck;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @drop_sql = N'ALTER TABLE mdr.registration_state DROP CONSTRAINT ' + QUOTENAME(@ck);
    EXEC(@drop_sql);
    FETCH NEXT FROM ck_cursor INTO @ck;
END
CLOSE ck_cursor;
DEALLOCATE ck_cursor;

-- 2. 存量旧值归位（此时已无 CHECK 约束，可自由改值）
UPDATE mdr.registration_state SET registration_status = 'CANDIDATE'
 WHERE registration_status = 'SUBMITTED';

UPDATE mdr.registration_state SET registration_status = 'PREFERRED_STANDARD'
 WHERE registration_status = 'PREFERRED';

-- 3. 按标准十值集重建（无 CHECK 约束时才加，重跑即 no-op）
IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'mdr.registration_state')
)
BEGIN
    ALTER TABLE mdr.registration_state ADD CONSTRAINT CK_registration_state_status
    CHECK (UPPER(REPLACE(registration_status, '_', ' ')) IN (
        'INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED', 'STANDARD',
        'PREFERRED STANDARD', 'SUPERSEDED', 'RETIRED', 'HISTORICAL', 'APPLICATION'
    ));
END

-- 管理状态索引重建（表未被重建，索引不受影响；此处显式声明以保持脚本自洽）
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'mdr.registration_state')
      AND name = N'idx_registration_state_administrative_status'
)
BEGIN
    CREATE INDEX idx_registration_state_administrative_status
        ON mdr.registration_state (administrative_status);
END