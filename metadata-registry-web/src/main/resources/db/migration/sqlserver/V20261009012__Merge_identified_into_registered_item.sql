-- ============================================================================
-- V20261009012__Merge_identified_into_registered_item.sql  (SQL Server)
--
-- repair-administered-item-reference-documents-ui：受管链物理表合并。
--
-- 【背景】ISO/IEC 11179-3:2023 §9.4.1.1 声明 Registered_Item 的 Direct superclass 是
-- Identified_Item(§7.3.1)，二者是**两层**。本迁移只收敛**物理表**，**不改类层次**：
-- IdentifiedItem 由 @Entity 降为 @MappedSuperclass（见
-- openspec/changes/administered-item-reference-documents-ui/design.md D6），
-- 实体继承守护测试 StandardInheritanceConformanceTest 的白名单与条款引用一字不改。
--
-- 【步骤】
--   1) registered_item 增 item_id 列（承接原 identified_item.item_id）；
--   2) 由 identified_item 回填（两表 id 同值，继承链要求 PK/FK 同名 id）；
--   3) scoped_identifier.identified_item_id 的外键改指 registered_item；
--   4) 删除 identified_item 表。
--
-- 【幂等】第 1 步判存（列不存在才加），第 2 步按 NULL 回填，
--         第 3 步先删旧 FK 再建新 FK，第 4 步判存。可重入。
--
-- 【语义后果（有意的产品决策，已记入 OpenSpec）】Registered_Item 是抽象类
-- （§9.4.1.2：shall be instantiated as either Administered_Item or Attached_Item），
-- 合并后任何带 Scoped_Identifier 的条目必然同时具备受管项实例——「已标识但未注册」
-- 这一 §7.4.1(0..1) 允许的中间态不再可物理表达。
--
-- 【回滚】本迁移不可逆（删表）。回滚需从备份恢复 identified_item 与
--         scoped_identifier 的旧外键。执行前务必备份。
-- ============================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;

-- ① registered_item 增 item_id
--    注意：① 与 ② 之间必须 GO 分批——SQL Server 在单批次内先绑定全部语句，
--    ② 的 UPDATE 引用尚不存在的 item_id 会在编译期报错并使整批（含 ①）不执行。
IF COL_LENGTH('mdr.registered_item', 'item_id') IS NULL
    ALTER TABLE mdr.registered_item ADD item_id UNIQUEIDENTIFIER NULL;
GO

-- ② 由 identified_item 回填
IF OBJECT_ID(N'mdr.identified_item', N'U') IS NOT NULL
BEGIN
    UPDATE mdr.registered_item
       SET item_id = ii.item_id
      FROM mdr.identified_item ii
     WHERE ii.id = mdr.registered_item.id
       AND mdr.registered_item.item_id IS NULL;

    -- 兜底：registered_item 有行而 identified_item 无对应行时，用 scoped_identifier 反查 item_id
    UPDATE mdr.registered_item
       SET item_id = (
            SELECT MIN(si.identified_item_id) FROM mdr.scoped_identifier si
             WHERE si.identified_item_id = mdr.registered_item.id)
     WHERE item_id IS NULL;
END
GO

-- ③ scoped_identifier 外键改指 registered_item
--    只处理 scoped_identifier 上的 FK；registered_item.id → identified_item 的 FK
--    随 ④ 删表一并消失，不在此处干预。
DECLARE @fk NVARCHAR(128);
DECLARE @drop_sql NVARCHAR(400);
DECLARE fk_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT DISTINCT fk.name
      FROM sys.foreign_keys fk
      JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
     WHERE fk.referenced_object_id = OBJECT_ID(N'mdr.identified_item')
       AND fk.parent_object_id = OBJECT_ID(N'mdr.scoped_identifier');
OPEN fk_cursor;
FETCH NEXT FROM fk_cursor INTO @fk;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @drop_sql = N'ALTER TABLE mdr.scoped_identifier DROP CONSTRAINT ' + QUOTENAME(@fk);
    EXEC(@drop_sql);
    FETCH NEXT FROM fk_cursor INTO @fk;
END
CLOSE fk_cursor;
DEALLOCATE fk_cursor;
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                WHERE parent_object_id = OBJECT_ID(N'mdr.scoped_identifier')
                  AND referenced_object_id = OBJECT_ID(N'mdr.registered_item'))
BEGIN
    ALTER TABLE mdr.scoped_identifier
        ADD CONSTRAINT FK_scoped_identifier_registered_item
        FOREIGN KEY (identified_item_id) REFERENCES mdr.registered_item(id);
END
GO

-- registered_item.item_id → item 的外键（原由 identified_item.item_id 承担）
IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
                WHERE parent_object_id = OBJECT_ID(N'mdr.registered_item')
                  AND referenced_object_id = OBJECT_ID(N'mdr.item'))
BEGIN
    ALTER TABLE mdr.registered_item
        ADD CONSTRAINT FK_registered_item_item
        FOREIGN KEY (item_id) REFERENCES mdr.item(id);
END
GO

-- ④ 删旧表
--    先动态删除**所有**指向 identified_item 的外键（子表名取自 sys.foreign_keys，
--    不硬编码），否则 DROP TABLE 会被这些 FK 阻断。
GO
DECLARE @child NVARCHAR(300);
DECLARE @fkname NVARCHAR(128);
DECLARE @sql NVARCHAR(800);
DECLARE fk_loop CURSOR LOCAL FAST_FORWARD FOR
    SELECT OBJECT_SCHEMA_NAME(fk.parent_object_id) + N'.' + OBJECT_NAME(fk.parent_object_id),
           fk.name
      FROM sys.foreign_keys fk
     WHERE fk.referenced_object_id = OBJECT_ID(N'mdr.identified_item');
OPEN fk_loop;
FETCH NEXT FROM fk_loop INTO @child, @fkname;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'ALTER TABLE ' + @child + N' DROP CONSTRAINT ' + QUOTENAME(@fkname);
    EXEC(@sql);
    FETCH NEXT FROM fk_loop INTO @child, @fkname;
END
CLOSE fk_loop;
DEALLOCATE fk_loop;
GO

IF OBJECT_ID(N'mdr.identified_item', N'U') IS NOT NULL
    DROP TABLE mdr.identified_item;
GO

-- ---- 落地校验 ----
SELECT 'registered_item.item_id 未回填', COUNT(*) FROM mdr.registered_item WHERE item_id IS NULL;
SELECT 'registered_item', COUNT(*) FROM mdr.registered_item;
SELECT 'scoped_identifier', COUNT(*) FROM mdr.scoped_identifier;
SELECT 'identified_item 是否已删除',
       CASE WHEN OBJECT_ID(N'mdr.identified_item', N'U') IS NULL THEN 'YES' ELSE 'NO' END;