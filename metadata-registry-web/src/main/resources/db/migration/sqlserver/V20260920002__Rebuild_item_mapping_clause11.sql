-- Item_Mapping 重建（详见 sqlite 同名脚本头注释）。
-- 幂等：先动态删除引用这三表的全部外键（Hibernate ddl-auto 生成的 FK 命名不可预测，
--   不先删除会报 3725/3726）；DROP 用 IF EXISTS；CREATE 用 IF NOT EXISTS 批包裹——
--   兼容"Hibernate 已先行增量改表"的残留态，重跑可收敛到统一定义。
DECLARE @fkName nvarchar(128), @fkSchema nvarchar(128), @fkTable nvarchar(128), @dropSql nvarchar(1000);
DECLARE fkCursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT fk.name, sch.name, t.name
    FROM sys.foreign_keys fk
    JOIN sys.tables t ON fk.parent_object_id = t.object_id
    JOIN sys.schemas sch ON t.schema_id = sch.schema_id
    WHERE fk.referenced_object_id IN (OBJECT_ID('mdr.item_mapping'), OBJECT_ID('mdr.item_mapping_subject'), OBJECT_ID('mdr.item_mapping_object'));
OPEN fkCursor;
FETCH NEXT FROM fkCursor INTO @fkName, @fkSchema, @fkTable;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @dropSql = N'ALTER TABLE ' + QUOTENAME(@fkSchema) + N'.' + QUOTENAME(@fkTable)
                 + N' DROP CONSTRAINT ' + QUOTENAME(@fkName);
    EXEC sp_executesql @dropSql;
    FETCH NEXT FROM fkCursor INTO @fkName, @fkSchema, @fkTable;
END
CLOSE fkCursor;
DEALLOCATE fkCursor;

DROP TABLE IF EXISTS mdr.item_mapping_subject;
DROP TABLE IF EXISTS mdr.item_mapping_object;
DROP TABLE IF EXISTS mdr.item_mapping;
DROP TABLE IF EXISTS mdr.mapping_hierarchy;

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping')
BEGIN
    CREATE TABLE mdr.item_mapping (
        degree varchar(32) NOT NULL CHECK (degree IN ('related to','same as','semantically equivalent','semantically similar','derived from','has derivation','broader than','narrower than','child of','parent of','contains','contained in','instance of','has instance')),
        description varchar(2000) NULL,
        transformation_rule varchar(4000) NULL,
        transformation_rule_notation varchar(255) NULL,
        id uniqueidentifier NOT NULL,
        CONSTRAINT pk_item_mapping PRIMARY KEY (id)
    );
END

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping_subject')
BEGIN
    CREATE TABLE mdr.item_mapping_subject (
        id uniqueidentifier NOT NULL,
        item_mapping_id uniqueidentifier NOT NULL,
        item_id uniqueidentifier NOT NULL,
        mapping_variable_name varchar(255) NULL,
        CONSTRAINT pk_item_mapping_subject PRIMARY KEY (id),
        CONSTRAINT uk_ims_mapping UNIQUE (item_mapping_id)
    );
END

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='item_mapping_object')
BEGIN
    CREATE TABLE mdr.item_mapping_object (
        id uniqueidentifier NOT NULL,
        item_mapping_id uniqueidentifier NOT NULL,
        item_id uniqueidentifier NOT NULL,
        mapping_variable_name varchar(255) NULL,
        CONSTRAINT pk_item_mapping_object PRIMARY KEY (id),
        CONSTRAINT uk_imo_mapping UNIQUE (item_mapping_id)
    );
END

IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mdr' AND TABLE_NAME='mapping_hierarchy')
BEGIN
    CREATE TABLE mdr.mapping_hierarchy (
        id uniqueidentifier NOT NULL,
        superset_mapping_id uniqueidentifier NOT NULL,
        subset_mapping_id uniqueidentifier NOT NULL,
        CONSTRAINT pk_mapping_hierarchy PRIMARY KEY (id),
        CONSTRAINT uk_mh_subset UNIQUE (subset_mapping_id)
    );
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='idx_im_degree' AND object_id=OBJECT_ID('mdr.item_mapping')) CREATE INDEX idx_im_degree ON mdr.item_mapping (degree);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='idx_ims_item' AND object_id=OBJECT_ID('mdr.item_mapping_subject')) CREATE INDEX idx_ims_item ON mdr.item_mapping_subject (item_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='idx_imo_item' AND object_id=OBJECT_ID('mdr.item_mapping_object')) CREATE INDEX idx_imo_item ON mdr.item_mapping_object (item_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='idx_mh_superset' AND object_id=OBJECT_ID('mdr.mapping_hierarchy')) CREATE INDEX idx_mh_superset ON mdr.mapping_hierarchy (superset_mapping_id);
