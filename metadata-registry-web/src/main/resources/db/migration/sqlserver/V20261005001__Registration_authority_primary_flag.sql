-- registration_authority 新增 is_primary 标志（authority-contact-governance，详见 sqlite 同名脚本头注释）：
-- 存量行回填 false；主要机构由启动调和按 id 字典序最小补标（幂等）。
-- mdr 表按既有惯例全限定；Hibernate ddl-auto=update 在应用启动时已可能提前建列，
-- 加存在性守卫避免重复列错误（同 V20260924002 模式）。
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE object_id = OBJECT_ID('mdr.registration_authority') AND name = 'is_primary')
    ALTER TABLE mdr.registration_authority ADD is_primary BIT NOT NULL DEFAULT 0;
