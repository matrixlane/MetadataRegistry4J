package io.github.matrixlane.metadataregistry.web.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移 V20261009001（mdr schema 标准一致性清理，三方言）验证。
 *
 * <p>沿用 {@link RegistrationAuthorityPrimaryFlagMigrationTest} 的模式：核心用例以真实
 * Flyway API 在 SQLite 上重放——预置"迁移前"旧名表 + 非标准列（模拟缺 2026-09-01
 * 剥离批次的既有库），基线到 20261008002、target 钉到 20261009001，验证更名/删列/
 * 删表全部生效；SQL Server 版另做静态内容核对（sp_rename / ALTER SCHEMA TRANSFER /
 * 集合式约束清理），其真实库执行由部署期冒烟覆盖。</p>
 */
class MdrStandardComplianceCleanupMigrationTest {

    private static final String VERSION = "V20261009001";
    private static final String FLYWAY_VERSION = "20261009001";
    private static final String BASELINE = "20261008002";

    /** 迁移前期 fixture：旧名表（9 张）+ 待删表（4 张）。 */
    private static final String[] OLD_SCHEMA_DDL = {
            "CREATE TABLE conceptual_domain_dec (conceptual_domain_id blob, data_element_concept_id blob)",
            "CREATE TABLE designation_context_relation (id blob, acceptability varchar(16), context_id blob, designation_id blob)",
            "CREATE TABLE definition_context_relation (id blob, acceptability varchar(16), context_id blob, definition_id blob)",
            "CREATE TABLE item_mapping_subject (id blob, item_mapping_id blob, item_id blob, mapping_variable_name varchar(255))",
            "CREATE TABLE item_mapping_object (id blob, item_mapping_id blob, item_id blob, mapping_variable_name varchar(255))",
            "CREATE TABLE designation_naming_convention (designation_id blob, naming_convention_id blob)",
            "CREATE TABLE namespace_naming_convention (namespace_id blob, naming_convention_id blob)",
            "CREATE TABLE unit_of_measure_measure_class (unit_of_measure_id blob, measure_class_id blob)",
            "CREATE TABLE submission_record_registered_item (submission_record_id blob, registered_item_id blob)",
            "CREATE TABLE \"definition_service_test$test_item\" (id blob)",
            "CREATE TABLE \"designation_service_test$test_item\" (id blob)",
            "CREATE TABLE \"item_administration_service_test$test_item\" (id blob)",
            "CREATE TABLE value_meaning_permissible_value_meaning (value_meaning_id blob, permissible_value_meaning_id blob)",
            "CREATE TABLE namespace (id blob, identifier_input_mode varchar(32), manual_identifier_pattern varchar(255), version_input_mode varchar(32), versioning_policy varchar(32))",
            "CREATE TABLE scoped_identifier (id blob, identifier_creation_mode varchar(16), version_creation_mode varchar(16), version_status varchar(16), created_at datetime, published_at datetime, deprecated_at datetime, obsolete_at datetime)",
            "CREATE TABLE naming_convention (id blob, rules_json text)",
            "CREATE TABLE language_identification (id blob, enabled boolean)",
            "CREATE TABLE contact (id blob, account_id blob)",
            "CREATE TABLE data_type_scheme (id blob, reference varchar(255))",
            "CREATE TABLE slot (id blob, value varchar(255), value_datatype varchar(255))",
            "CREATE TABLE role (id blob, email_address varchar(255), phone_number varchar(255))",
            "CREATE TABLE designation (id blob, acceptability varchar(16))",
            "CREATE TABLE definition (id blob, acceptability varchar(16))",
            "CREATE TABLE submission_record (id blob, source varchar(32))",
            "CREATE TABLE registration_transition_rule (id blob, registration_authority_id blob)",
            "CREATE TABLE registration_transition_approval (id blob, administered_item_id blob)",
    };

    private static final List<String[]> RENAMES = List.of(
            new String[]{"conceptual_domain_dec", "data_element_concept_domain"},
            new String[]{"designation_context_relation", "designation_context"},
            new String[]{"definition_context_relation", "definition_context"},
            new String[]{"item_mapping_subject", "subject_mapping"},
            new String[]{"item_mapping_object", "object_mapping"},
            new String[]{"designation_naming_convention", "naming_convention_conformance"},
            new String[]{"namespace_naming_convention", "naming_convention_utilization"},
            new String[]{"unit_of_measure_measure_class", "unit_of_measure_class"},
            new String[]{"submission_record_registered_item", "registered_item_submission_record"});

    private static final String[][] DROPPED_COLUMNS = {
            {"namespace", "identifier_input_mode"}, {"namespace", "manual_identifier_pattern"},
            {"namespace", "version_input_mode"}, {"namespace", "versioning_policy"},
            {"scoped_identifier", "identifier_creation_mode"}, {"scoped_identifier", "version_creation_mode"},
            {"scoped_identifier", "version_status"}, {"scoped_identifier", "created_at"},
            {"scoped_identifier", "published_at"}, {"scoped_identifier", "deprecated_at"},
            {"scoped_identifier", "obsolete_at"},
            {"naming_convention", "rules_json"}, {"language_identification", "enabled"},
            {"contact", "account_id"}, {"data_type_scheme", "reference"},
            {"slot", "value"}, {"slot", "value_datatype"},
            {"role", "email_address"}, {"role", "phone_number"},
            {"designation", "acceptability"}, {"definition", "acceptability"},
            {"submission_record", "source"},
    };

    private static final String[] DROPPED_TABLES = {
            "definition_service_test$test_item", "designation_service_test$test_item",
            "item_administration_service_test$test_item", "value_meaning_permissible_value_meaning"};

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("三方言脚本齐备且同版本号（sqlite=Java 迁移类；sqlserver/dm 为 SQL 脚本）")
    void threeDialectScriptsShareVersion() throws Exception {
        for (String dialect : new String[]{"sqlserver", "dm"}) {
            String path = "db/migration/" + dialect + "/" + VERSION + "__Mdr_standard_compliance_cleanup.sql";
            assertThat(new ClassPathResource(path).exists()).as("%s 分支脚本应存在", dialect).isTrue();
            String merge = "db/migration/" + dialect + "/V20261009002__Merge_renamed_tables.sql";
            assertThat(new ClassPathResource(merge).exists()).as("%s 合并迁移应存在", dialect).isTrue();
        }
        assertThat(Class.forName("db.migration.sqlite.V20261009001__MdrStandardComplianceCleanup"))
                .as("sqlite 分支应为 Java 迁移（条件 DDL 无法用纯 SQL 表达）").isNotNull();
    }

    @Test
    @DisplayName("SQL Server 版静态核对：更名/迁 schema/集合式约束清理齐备")
    void sqlServerScriptStructure() throws Exception {
        String sql = new String(new ClassPathResource(
                "db/migration/sqlserver/" + VERSION + "__Mdr_standard_compliance_cleanup.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("sp_rename 'mdr.conceptual_domain_dec', 'data_element_concept_domain'")
                .contains("ALTER SCHEMA ext TRANSFER mdr.registration_transition_rule")
                .contains("ALTER SCHEMA ext TRANSFER mdr.registration_transition_approval")
                .contains("sys.default_constraints")
                .contains("sys.foreign_keys")
                .contains("sys.check_constraints")
                .contains("DROP COLUMN");
    }

    @Test
    @DisplayName("V20261009001(sqlite): 真实 Flyway 重放——更名/删列/删表全部生效且存量数据保留")
    void flywayReplayCleansSchema() throws Exception {
        Path dbFile = tempDir.resolve("existing-before-cleanup.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            for (String ddl : OLD_SCHEMA_DDL) {
                st.execute(ddl);
            }
            st.execute("INSERT INTO designation_context_relation (id, acceptability, context_id, designation_id) "
                    + "VALUES (randomblob(16), 'PREFERRED', randomblob(16), randomblob(16))");
            st.execute("INSERT INTO registration_transition_rule (id, registration_authority_id) "
                    + "VALUES (randomblob(16), randomblob(16))");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "", "")
                .locations("classpath:db/migration/sqlite")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion(BASELINE))
                .target(MigrationVersion.fromVersion(FLYWAY_VERSION))
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(result.migrations.stream().map(m -> m.version)).containsExactly(FLYWAY_VERSION);

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            List<String> tables = tables(st);
            for (String[] rename : RENAMES) {
                assertThat(tables).as("新名表 %s 应存在", rename[1]).contains(rename[1]);
                assertThat(tables).as("旧名表 %s 应不存在", rename[0]).doesNotContain(rename[0]);
            }
            for (String dropped : DROPPED_TABLES) {
                assertThat(tables).as("表 %s 应已删除", dropped).doesNotContain(dropped);
            }
            // transition 三表保持原位（SQLite 无 schema 概念，迁 ext 为 no-op）
            assertThat(tables).contains("registration_transition_rule", "registration_transition_approval");

            for (String[] dc : DROPPED_COLUMNS) {
                assertThat(columns(st, dc[0])).as("%s.%s 应已删除", dc[0], dc[1]).doesNotContain(dc[1]);
            }

            // 存量数据保留：更名后行数不变
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM designation_context")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM registration_transition_rule")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("V20261009001(sqlite): 新旧名并存（ddl-auto 预建）→ 按 id/全行去重合并后删旧表")
    void flywayReplayMergesWhenBothNamesExist() throws Exception {
        Path dbFile = tempDir.resolve("both-names-exist.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            // 新名表已由 ddl-auto 按实体建出并含既有数据
            st.execute("CREATE TABLE designation_context (id blob, acceptability varchar(16), context_id blob, designation_id blob)");
            st.execute("INSERT INTO designation_context (id) VALUES (X'01')");
            st.execute("CREATE TABLE subject_mapping (id blob, item_mapping_id blob, item_id blob, mapping_variable_name varchar(255))");
            st.execute("INSERT INTO subject_mapping (id, item_mapping_id, item_id) VALUES (X'0A', X'01', X'01')");
            // 旧名表残留数据：designation 按 id 去重（id=01 已存在跳过、id=02 并入）；
            // subject_mapping 旧行 id=0A 与新表同 id 跳过、id=0C 新行并入
            st.execute("CREATE TABLE designation_context_relation (id blob, acceptability varchar(16), context_id blob, designation_id blob)");
            st.execute("INSERT INTO designation_context_relation (id) VALUES (X'01')");
            st.execute("INSERT INTO designation_context_relation (id) VALUES (X'02')");
            st.execute("CREATE TABLE item_mapping_subject (id blob, item_mapping_id blob, item_id blob, mapping_variable_name varchar(255))");
            st.execute("INSERT INTO item_mapping_subject (id, item_mapping_id, item_id) VALUES (X'0A', X'01', X'01')");
            st.execute("INSERT INTO item_mapping_subject (id, item_mapping_id, item_id) VALUES (X'0C', X'02', X'02')");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "", "")
                .locations("classpath:db/migration/sqlite")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion(BASELINE))
                .target(MigrationVersion.fromVersion(FLYWAY_VERSION))
                .load();
        flyway.migrate();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            // 按 id 去重：id=01 已存在不重复插；id=02 并入 → 2 行
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM designation_context")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
            // 按 id 去重：id=0A 已存在不重复插；id=0C 并入 → 2 行
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM subject_mapping")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
            List<String> tables = tables(st);
            assertThat(tables).doesNotContain("designation_context_relation", "item_mapping_subject");
        }
    }

    @Test
    @DisplayName("V20261009001(sqlite): 二次启动 Flyway 不再重复应用（一次语义）")
    void secondFlywayRunIsNoOp() throws Exception {
        Path dbFile = tempDir.resolve("existing-restart.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            for (String ddl : OLD_SCHEMA_DDL) {
                st.execute(ddl);
            }
        }
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "", "")
                .locations("classpath:db/migration/sqlite")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion(BASELINE))
                .load();
        flyway.migrate();
        MigrateResult second = flyway.migrate();
        assertThat(second.migrationsExecuted).isZero();
    }

    private static List<String> tables(Statement st) throws Exception {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    private static List<String> columns(Statement st, String table) throws Exception {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                out.add(rs.getString("name"));
            }
        }
        return out;
    }
}
