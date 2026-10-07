package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * personal-no-login-registrar-default 四步收尾（SQLite，V20261009001~0004 合并）：
 *
 * <ol>
 *   <li>建 ext_contact（contact_id 主键 + account_id），从 contact_account_binding 搬迁；</li>
 *   <li>建 ext_registrar（registrar_id 主键 + is_default），三级回填默认注册人员
 *       （account.active_registrar_id → 默认绑定 registrar → 账号首个 registrar，
 *       每账号至多一个默认，账号级互斥）；</li>
 *   <li>删 account.active_registrar_id 列；</li>
 *   <li>删 contact_account_binding 表；存量个人库清零 force_password_reset/
 *       temp_password_used（无密码概念）。</ol>
 *
 * <p>Java 迁移原因（同 V20260922001 先例）：本应用 Hibernate ddl-auto 先于 Flyway，
 * SQLite 纯 SQL 无法表达条件 DDL——个人版存量库可能从未建出 active_registrar_id 列，
 * 全新库的默认档也可能没有 contact_account_binding 表，全部步骤按
 * pragma_table_info / sqlite_master 守卫，缺失即跳过，保证任意基座可启动。
 * 三方言中仅 SQLite 走 Java；SQL Server / 达梦由同版本 SQL 脚本承载。
 */
public class V20261009001__Contact_and_registrar_satellites extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            step1ContactExtension(st);
            step2RegistrarExtension(st);
            step3DropActiveRegistrarColumn(st);
            step4DropBindingAndResetPersonalFlags(st);
        }
    }

    /** 1. ext_contact：账号绑定卫星（contact_id 即主键，与 mdr.contact 按 id 1:1）。 */
    private void step1ContactExtension(Statement st) throws Exception {
        if (!tableExists(st, "ext_contact")) {
            st.execute("CREATE TABLE ext_contact (id BLOB NOT NULL PRIMARY KEY,"
                    + " account_id BLOB NOT NULL)");
            st.execute("CREATE INDEX idx_ext_contact_account ON ext_contact (account_id)");
        }
        if (tableExists(st, "contact_account_binding")) {
            st.execute("INSERT INTO ext_contact (id, account_id)"
                    + " SELECT contact_id, account_id FROM contact_account_binding b"
                    + " WHERE NOT EXISTS (SELECT 1 FROM ext_contact e WHERE e.id = b.contact_id)");
        }
    }

    /** 2. ext_registrar：默认注册人员标志 + 三级回填（每账号至多一默认）。 */
    private void step2RegistrarExtension(Statement st) throws Exception {
        if (!tableExists(st, "ext_registrar")) {
            st.execute("CREATE TABLE ext_registrar (id BLOB NOT NULL PRIMARY KEY,"
                    + " is_default BOOLEAN NOT NULL DEFAULT 0)");
        }
        // ① 原 active_registrar_id（列于 step3 才删除，此处仍在；列缺失的库跳过）
        if (columnExists(st, "account", "active_registrar_id")) {
            st.execute("INSERT INTO ext_registrar (id, is_default)"
                    + " SELECT a.active_registrar_id, 1 FROM account a"
                    + " WHERE a.active_registrar_id IS NOT NULL"
                    + "   AND EXISTS (SELECT 1 FROM contact_account_binding b"
                    + "                WHERE b.contact_id = a.active_registrar_id"
                    + "                  AND b.account_id = a.id)"
                    + "   AND NOT EXISTS (SELECT 1 FROM ext_registrar r"
                    + "                    WHERE r.id = a.active_registrar_id)");
        }
        if (tableExists(st, "contact_account_binding")) {
            // ② 原默认绑定对应的 Registrar（未被 ① 覆盖的账号；同账号多默认存量取 contact_id 最小者）
            st.execute("INSERT INTO ext_registrar (id, is_default)"
                    + " SELECT pick.contact_id, 1 FROM ("
                    + "        SELECT b.account_id, MIN(b.contact_id) AS contact_id"
                    + "          FROM contact_account_binding b"
                    + "         WHERE b.is_default = 1"
                    + "           AND EXISTS (SELECT 1 FROM registrar reg WHERE reg.id = b.contact_id)"
                    + "           AND NOT EXISTS (SELECT 1 FROM account a"
                    + "                            WHERE a.id = b.account_id"
                    + "                              AND a.active_registrar_id = b.contact_id)"
                    + "           AND NOT EXISTS (SELECT 1 FROM contact_account_binding b3"
                    + "                             JOIN ext_registrar r3 ON r3.id = b3.contact_id"
                    + "                            WHERE b3.account_id = b.account_id AND r3.is_default = 1)"
                    + "         GROUP BY b.account_id) pick"
                    + " WHERE NOT EXISTS (SELECT 1 FROM ext_registrar r"
                    + "                    WHERE r.id = pick.contact_id)");
            // ③ 账号首个 Registrar（该账号尚无任一默认时；contact_id 最小者）
            st.execute("INSERT INTO ext_registrar (id, is_default)"
                    + " SELECT first_reg.contact_id, 1 FROM ("
                    + "        SELECT b.account_id, MIN(b.contact_id) AS contact_id"
                    + "          FROM contact_account_binding b"
                    + "         WHERE EXISTS (SELECT 1 FROM registrar reg WHERE reg.id = b.contact_id)"
                    + "         GROUP BY b.account_id) first_reg"
                    + " WHERE NOT EXISTS (SELECT 1 FROM contact_account_binding b2"
                    + "                     JOIN ext_registrar r2 ON r2.id = b2.contact_id"
                    + "                                        AND r2.is_default = 1"
                    + "                    WHERE b2.account_id = first_reg.account_id)"
                    + "   AND NOT EXISTS (SELECT 1 FROM ext_registrar r"
                    + "                    WHERE r.id = first_reg.contact_id)");
        }
    }

    /** 3. 删除 account.active_registrar_id 缓存列（列缺失的库为 no-op）。 */
    private void step3DropActiveRegistrarColumn(Statement st) throws Exception {
        if (columnExists(st, "account", "active_registrar_id")) {
            st.execute("ALTER TABLE account DROP COLUMN active_registrar_id");
        }
    }

    /** 4. 删 contact_account_binding 表；存量个人库清零密码标志（无登录概念）。 */
    private void step4DropBindingAndResetPersonalFlags(Statement st) throws Exception {
        if (tableExists(st, "contact_account_binding")) {
            st.execute("DROP TABLE contact_account_binding");
        }
        if (isPersonalEdition(st)) {
            st.execute("UPDATE account SET force_password_reset = 0, temp_password_used = 0"
                    + " WHERE EXISTS (SELECT 1 FROM system_setting"
                    + "                WHERE setting_key = 'system.edition'"
                    + "                  AND setting_value = 'PERSONAL')");
        }
    }

    private boolean isPersonalEdition(Statement st) throws Exception {
        if (!tableExists(st, "system_setting")) {
            return false;
        }
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM system_setting"
                + " WHERE setting_key = 'system.edition' AND setting_value = 'PERSONAL'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean tableExists(Statement st, String table) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean columnExists(Statement st, String table, String column) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM pragma_table_info('" + table + "') WHERE name='" + column + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }
}
