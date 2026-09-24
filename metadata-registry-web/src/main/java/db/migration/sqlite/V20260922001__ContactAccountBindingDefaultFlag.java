package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * contact_account_binding 增加 is_default 标志（add-submitter-contact-identity）：
 * 每账号至多一条为 true 的默认联系人身份，作为 SubmissionRecord 默认预填来源。
 *
 * <p>Java 迁移原因：本应用 Hibernate ddl-auto 先于 Flyway 执行（FlywayAfterJpaConfig），
 * 既有库可能已被实体模型加上该列，故需按 pragma_table_info 做幂等守卫——
 * 纯 SQL 的 SQLite 迁移无法表达条件 DDL。
 */
public class V20260922001__ContactAccountBindingDefaultFlag extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        boolean columnExists;
        try (Statement st = context.getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM pragma_table_info('contact_account_binding')"
                             + " WHERE name='is_default'")) {
            columnExists = rs.next() && rs.getInt(1) > 0;
        }
        if (!columnExists) {
            try (Statement st = context.getConnection().createStatement()) {
                st.execute("ALTER TABLE contact_account_binding"
                        + " ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT 0");
            }
        }
    }
}
