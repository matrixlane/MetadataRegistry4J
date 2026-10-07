-- 收尾清理（personal-no-login-registrar-default，详见 sqlite 同名脚本头注释）：
-- 1. ext.contact_account_binding 整表已由 ext.contact（V20261009001）承接，此处删除；
-- 2. 存量 PERSONAL 库清零强制改密/临时密码标志（个人版免登录，隐式管理员会话不触发
--    强制改密拦截）；团队库不动。system_setting 位于 ext schema（同 orm 登记）。
IF EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.TABLES
    WHERE TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'contact_account_binding'
)
    EXEC(N'DROP TABLE ext.contact_account_binding');

IF EXISTS (SELECT 1 FROM ext.system_setting WHERE setting_key = 'system.edition' AND setting_value = 'PERSONAL')
    EXEC(N'UPDATE ext.account
    SET force_password_reset = 0, temp_password_used = 0
    WHERE EXISTS (SELECT 1 FROM ext.system_setting
                   WHERE setting_key = ''system.edition'' AND setting_value = ''PERSONAL'')');
