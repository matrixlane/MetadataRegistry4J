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

-- 2. 数据搬迁：mdr.registration_authority.is_primary -> ext.registration_authority（放 EXEC 内，列不存在时整批不编译）
IF COL_LENGTH('mdr.registration_authority', 'is_primary') IS NOT NULL
    EXEC('INSERT INTO ext.registration_authority (id, is_primary)
          SELECT id, is_primary FROM mdr.registration_authority');

-- 3. 删除 mdr 的非标准列
IF COL_LENGTH('mdr.registration_authority', 'is_primary') IS NOT NULL
    EXEC('ALTER TABLE mdr.registration_authority DROP COLUMN is_primary');
