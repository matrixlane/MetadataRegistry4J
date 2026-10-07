-- mdr.registration_authority.is_primary 迁出至 ext 卫星表（move-ra-primary-to-ext，详见 sqlite 同名脚本头注释）：
-- 建 ext.registration_authority（1:1 按 id）→ 数据搬迁（INSERT SELECT）→ 删 mdr 列。
-- 既有库已应用 V20261005001（列存在），守卫保证只搬一次、删一次；幂等搬运不丢既有行。
-- ext 表按既有惯例全限定 + INFORMATION_SCHEMA 守卫（Hibernate ddl-auto=update 可能已先行建表）；
-- mdr 表同样全限定以避免受 default_schema 影响（同 V20260930001/V20260919001 模式）。

-- 0. ext schema 兜底（V20260901014 已建，此处仅作幂等守卫）
IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name = 'ext')
    EXEC('CREATE SCHEMA ext');

-- 1. ext.registration_authority 卫星表
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'registration_authority'
)
BEGIN
    CREATE TABLE ext.registration_authority (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        is_primary BIT NOT NULL DEFAULT 0
    );
END;

-- 2. 数据搬迁：mdr.registration_authority.is_primary -> ext.registration_authority。
--    放 EXEC 内（列不存在时整批不编译）；NOT EXISTS 守卫兼容失败重跑残留（防 PK 冲突）。
IF COL_LENGTH('mdr.registration_authority', 'is_primary') IS NOT NULL
    EXEC('INSERT INTO ext.registration_authority (id, is_primary)
          SELECT m.id, m.is_primary FROM mdr.registration_authority m
          WHERE NOT EXISTS (SELECT 1 FROM ext.registration_authority e WHERE e.id = m.id)');

-- 3. 删除 mdr 的非标准列。
--    列上的默认约束（V20261005001 内联 DEFAULT 或 Hibernate ddl-auto 都会生成
--    DF__registrat__is_pr__* 具名约束）必须先显式删除，否则 5074 依赖错误（真实库实测）。
IF COL_LENGTH('mdr.registration_authority', 'is_primary') IS NOT NULL
BEGIN
    DECLARE @dropDefaultSql NVARCHAR(MAX);
    SELECT @dropDefaultSql = N'ALTER TABLE mdr.registration_authority DROP CONSTRAINT ' + QUOTENAME(dc.name)
    FROM sys.default_constraints dc
    JOIN sys.tables t ON t.object_id = dc.parent_object_id
    JOIN sys.schemas s ON s.schema_id = t.schema_id
    JOIN sys.columns c ON c.default_object_id = dc.object_id
    WHERE s.name = 'mdr' AND t.name = 'registration_authority' AND c.name = 'is_primary';
    IF @dropDefaultSql IS NOT NULL EXEC(@dropDefaultSql);

    EXEC('ALTER TABLE mdr.registration_authority DROP COLUMN is_primary');
END;
