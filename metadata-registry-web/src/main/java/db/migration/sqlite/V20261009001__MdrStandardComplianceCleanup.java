package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * mdr schema 标准一致性清理（mdr-standard-compliance-cleanup，SQLite 分支）。
 *
 * <p>与 SQL Server/达梦版 V20261009001 + V20261009002 同语义（合并为单个 Java 迁移）：
 * 删测试残留表与冗余映射表；9 张表按 ISO/IEC 11179-3/31 标准名更名（含旧名数据并入）；
 * 删 16 个非标准列。transition 三表迁 ext 在 SQLite 为 no-op（无 schema 概念）。
 *
 * <p>Java 迁移原因：本应用 Hibernate ddl-auto 先于 Flyway 执行（FlywayAfterJpaConfig），
 * 既有库可能已被实体模型按新名建表，纯 SQL 无法表达"旧名存在才更名、并存则合并去重"
 * 的条件 DDL，故按 sqlite_master / pragma_table_info 做全幂等守卫。</p>
 */
public class V20261009001__MdrStandardComplianceCleanup extends BaseJavaMigration {

    /** 旧名 → 新名；值为 true 表示表带 id 主键（按 id 去重合并），否则全行去重。 */
    private static final Map<String, String> RENAMES = new LinkedHashMap<>();
    static {
        RENAMES.put("conceptual_domain_dec", "data_element_concept_domain");
        RENAMES.put("designation_context_relation", "designation_context");
        RENAMES.put("definition_context_relation", "definition_context");
        RENAMES.put("item_mapping_subject", "subject_mapping");
        RENAMES.put("item_mapping_object", "object_mapping");
        RENAMES.put("designation_naming_convention", "naming_convention_conformance");
        RENAMES.put("namespace_naming_convention", "naming_convention_utilization");
        RENAMES.put("unit_of_measure_measure_class", "unit_of_measure_class");
        RENAMES.put("submission_record_registered_item", "registered_item_submission_record");
    }

    private static final String[] DROPPED_TABLES = {
            "definition_service_test$test_item", "designation_service_test$test_item",
            "item_administration_service_test$test_item", "value_meaning_permissible_value_meaning"};

    /** 表 → 待删列。 */
    private static final Map<String, String[]> DROPPED_COLUMNS = new LinkedHashMap<>();
    static {
        DROPPED_COLUMNS.put("namespace", new String[]{"identifier_input_mode", "manual_identifier_pattern",
                "version_input_mode", "versioning_policy"});
        DROPPED_COLUMNS.put("scoped_identifier", new String[]{"identifier_creation_mode", "version_creation_mode",
                "version_status", "created_at", "published_at", "deprecated_at", "obsolete_at"});
        DROPPED_COLUMNS.put("naming_convention", new String[]{"rules_json"});
        DROPPED_COLUMNS.put("language_identification", new String[]{"enabled"});
        DROPPED_COLUMNS.put("contact", new String[]{"account_id"});
        DROPPED_COLUMNS.put("data_type_scheme", new String[]{"reference"});
        DROPPED_COLUMNS.put("slot", new String[]{"value", "value_datatype"});
        DROPPED_COLUMNS.put("role", new String[]{"email_address", "phone_number"});
        DROPPED_COLUMNS.put("designation", new String[]{"acceptability"});
        DROPPED_COLUMNS.put("definition", new String[]{"acceptability"});
        DROPPED_COLUMNS.put("submission_record", new String[]{"source"});
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        for (String table : DROPPED_TABLES) {
            if (tableExists(conn, table)) {
                execute(conn, "DROP TABLE \"" + table + "\"");
            }
        }
        for (Map.Entry<String, String> e : RENAMES.entrySet()) {
            mergeOrRename(conn, e.getKey(), e.getValue());
        }
        // 子表回填：内联值先入子表再删列（幂等去重；本库审计为空、老快照库非空）
        backfillChild(conn, "slot", "value", "slot_value");
        backfillChild(conn, "role", "email_address", "role_email");
        backfillChild(conn, "role", "phone_number", "role_phone");
        for (Map.Entry<String, String[]> e : DROPPED_COLUMNS.entrySet()) {
            for (String col : e.getValue()) {
                if (tableExists(conn, e.getKey()) && columnExists(conn, e.getKey(), col)) {
                    execute(conn, "ALTER TABLE " + e.getKey() + " DROP COLUMN " + col);
                }
            }
        }
    }

    /**
     * 新名表存在 → 按两表公共列（有 id 以 id 去重，否则全行去重）把旧表数据并入后删旧表；
     * 新名表缺失 → RENAME TO。两表皆无 → no-op。
     */
    private static void mergeOrRename(Connection conn, String oldName, String newName) throws SQLException {
        if (!tableExists(conn, oldName)) {
            return;
        }
        if (!tableExists(conn, newName)) {
            execute(conn, "ALTER TABLE " + oldName + " RENAME TO " + newName);
            return;
        }
        java.util.List<String> cols = commonColumns(conn, oldName, newName);
        if (cols.isEmpty()) {
            throw new SQLException("旧表 " + oldName + " 与新表 " + newName + " 无公共列,无法合并");
        }
        String colList = String.join(", ", cols);
        String mergeSql;
        if (cols.contains("id")) {
            mergeSql = "INSERT INTO " + newName + " (" + colList + ") SELECT " + colList
                    + " FROM " + oldName + " o WHERE NOT EXISTS"
                    + " (SELECT 1 FROM " + newName + " n WHERE n.id = o.id)";
        } else {
            StringBuilder predicates = new StringBuilder();
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) {
                    predicates.append(" AND ");
                }
                predicates.append("n.").append(cols.get(i)).append(" IS o.").append(cols.get(i));
            }
            mergeSql = "INSERT INTO " + newName + " (" + colList + ") SELECT " + colList
                    + " FROM " + oldName + " o WHERE NOT EXISTS"
                    + " (SELECT 1 FROM " + newName + " n WHERE " + predicates + ")";
        }
        execute(conn, mergeSql);
        execute(conn, "DROP TABLE " + oldName);
    }

    private static java.util.List<String> commonColumns(Connection conn, String a, String b) throws SQLException {
        java.util.List<String> cols = new java.util.ArrayList<>(java.util.Arrays.asList(columns(conn, a)));
        cols.retainAll(java.util.Arrays.asList(columns(conn, b)));
        return cols;
    }

    /** 把主表内联列的非空值回填进多值子表（幂等去重），随后由统一流程删列。 */
    private static void backfillChild(Connection conn, String table, String column, String childTable)
            throws SQLException {
        if (!tableExists(conn, table) || !tableExists(conn, childTable) || !columnExists(conn, table, column)) {
            return;
        }
        execute(conn, "INSERT INTO " + childTable + " (" + table + "_id, value)"
                + " SELECT o.id, o." + column + " FROM " + table + " o"
                + " WHERE o." + column + " IS NOT NULL"
                + " AND NOT EXISTS (SELECT 1 FROM " + childTable + " v"
                + " WHERE v." + table + "_id = o.id AND v.value = o." + column + ")");
    }

    private static boolean tableExists(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM pragma_table_info('" + table + "')"
                     + " WHERE name='" + column + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private static String[] columns(Connection conn, String table) throws SQLException {
        java.util.List<String> cols = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM pragma_table_info('" + table + "')")) {
            while (rs.next()) {
                cols.add(rs.getString(1));
            }
        }
        return cols.toArray(new String[0]);
    }

    private static void execute(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }
}
