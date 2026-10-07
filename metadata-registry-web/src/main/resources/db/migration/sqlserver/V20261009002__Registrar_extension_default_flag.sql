-- 默认注册人员标志卫星表（personal-no-login-registrar-default，详见 sqlite 同名 Java 迁移头注释）：
-- 建 ext.registrar（1:1 按 id）+ 存量三级回填（active_registrar_id → 默认绑定 Registrar →
-- 账号首个 Registrar，每账号至多一默认）。account 全限定 ext（同 orm 登记）。
-- ②③ 取账号维度最小 contact_id 用 ROW_NUMBER()（T-SQL 不允许对 UNIQUEIDENTIFIER 用
-- MIN/MAX——该类型无 < 比较运算符）；②③ 依赖 ext.contact_account_binding 表，整段以
-- INFORMATION_SCHEMA 存在性守卫包壳（全新库该表不存在时安全跳过）。
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'registrar'
)
    CREATE TABLE ext.registrar (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        is_default BIT NOT NULL DEFAULT 0
    );

-- ① 原 active_registrar_id（列于 V20261009003 才删除，此处仍在；列缺失时整批不编译故放 EXEC）
IF COL_LENGTH('ext.account', 'active_registrar_id') IS NOT NULL
    EXEC(N'INSERT INTO ext.registrar (id, is_default)
    SELECT a.active_registrar_id, 1
      FROM ext.account a
     WHERE a.active_registrar_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM ext.contact_account_binding b
                    WHERE b.contact_id = a.active_registrar_id
                      AND b.account_id = a.id)
       AND NOT EXISTS (SELECT 1 FROM ext.registrar r WHERE r.id = a.active_registrar_id)');

-- ② 原默认绑定对应的 Registrar（该账号未被 ① 覆盖者；同账号多默认存量取 contact_id 最小者）
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'contact_account_binding'
)
    EXEC(N'INSERT INTO ext.registrar (id, is_default)
SELECT pick.contact_id, 1
  FROM (
        SELECT b.account_id, b.contact_id,
               ROW_NUMBER() OVER (PARTITION BY b.account_id ORDER BY b.contact_id) AS rn
          FROM ext.contact_account_binding b
         WHERE b.is_default = 1
           AND EXISTS (SELECT 1 FROM mdr.registrar reg WHERE reg.id = b.contact_id)
           AND NOT EXISTS (SELECT 1 FROM ext.account a
                            WHERE a.id = b.account_id
                              AND a.active_registrar_id = b.contact_id)
           AND NOT EXISTS (SELECT 1 FROM ext.contact_account_binding b3
                            JOIN ext.registrar r3 ON r3.id = b3.contact_id AND r3.is_default = 1
                           WHERE b3.account_id = b.account_id)
       ) pick
 WHERE pick.rn = 1
   AND NOT EXISTS (SELECT 1 FROM ext.registrar r WHERE r.id = pick.contact_id)');

-- ③ 账号首个 Registrar（该账号尚无任一默认时；contact_id 最小者）
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'contact_account_binding'
)
    EXEC(N'INSERT INTO ext.registrar (id, is_default)
SELECT first_reg.contact_id, 1
  FROM (
        SELECT b.account_id, b.contact_id,
               ROW_NUMBER() OVER (PARTITION BY b.account_id ORDER BY b.contact_id) AS rn
          FROM ext.contact_account_binding b
         WHERE EXISTS (SELECT 1 FROM mdr.registrar reg WHERE reg.id = b.contact_id)
       ) first_reg
 WHERE first_reg.rn = 1
   AND NOT EXISTS (SELECT 1 FROM ext.contact_account_binding b2
                    JOIN ext.registrar r2 ON r2.id = b2.contact_id AND r2.is_default = 1
                   WHERE b2.account_id = first_reg.account_id)
   AND NOT EXISTS (SELECT 1 FROM ext.registrar r WHERE r.id = first_reg.contact_id)');
