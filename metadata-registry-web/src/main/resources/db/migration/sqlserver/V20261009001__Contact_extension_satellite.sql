-- 账号绑定卫星表归位（personal-no-login-registrar-default，详见 sqlite 同名脚本头注释）：
-- ext.contact_account_binding（contact_id UNIQUE 独立绑定表）收敛为 ext.contact 卫星表
-- （contact_id 即 PK，与 mdr.contact 按 id 1:1）。本脚本建表 + 整表搬迁；
-- 旧表由 V20261009004 删除（V20261009002 回填 ext.registrar 仍需读 binding.is_default）。
-- ext 表按既有惯例全限定 + INFORMATION_SCHEMA 守卫（Hibernate ddl-auto=update 可能已先行建表）。
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'contact'
)
BEGIN
    CREATE TABLE ext.contact (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        account_id UNIQUEIDENTIFIER NOT NULL
    );
    CREATE INDEX idx_ext_contact_account ON ext.contact (account_id);
END;

IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'contact_account_binding'
)
    EXEC(N'INSERT INTO ext.contact (id, account_id)
    SELECT contact_id, account_id FROM ext.contact_account_binding
    WHERE NOT EXISTS (SELECT 1 FROM ext.contact e WHERE e.id = contact_id)');
