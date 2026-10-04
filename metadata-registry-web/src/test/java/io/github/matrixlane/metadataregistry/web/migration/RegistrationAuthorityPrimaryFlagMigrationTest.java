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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移 V20261005001（三方言：registration_authority 新增 is_primary 标志）验证。
 *
 * <p>设计说明：沿用 {@link LanguageNameSeedMigrationTest} 的迁移测试模式（仓库无
 * Testcontainers 依赖）。核心用例不走手工切分语句，而是以真实 Flyway API 重放：
 * 预置一张"迁移前"的 registration_authority 表（模拟既有库，无 is_primary 列），
 * 基线到 20261001001（生产既有库当前版本）、target 钉到 20261005001，由 Flyway
 * 应用 sqlite 分支脚本——与生产 personal 库（baseline-on-migrate）同一条执行路径。
 * SQL Server / 达梦分支无可用的容器化实例，按项目惯例留待部署实测，此处仅做
 * 版本一致性（三方言同版本号）与内容（is_primary 列定义）静态核对。</p>
 */
class RegistrationAuthorityPrimaryFlagMigrationTest {

    private static final String VERSION = "V20261005001";
    /** Flyway API 的版本号不含 "V" 前缀。 */
    private static final String FLYWAY_VERSION = "20261005001";
    private static final String BASELINE = "20261001001";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("三方言脚本齐备且同版本号（sqlite/sqlserver/dm 各一份）")
    void threeDialectScriptsShareVersion() throws Exception {
        for (String dialect : new String[]{"sqlite", "sqlserver", "dm"}) {
            String path = "db/migration/" + dialect + "/" + VERSION + "__Registration_authority_primary_flag.sql";
            var resource = new ClassPathResource(path);
            assertThat(resource.exists()).as("%s 分支脚本应存在", dialect).isTrue();
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).as("%s 分支脚本应定义 is_primary 列", dialect)
                    .containsIgnoringCase("is_primary");
        }
    }

    @Test
    @DisplayName("V20261005001(sqlite): 真实 Flyway 重放——既有库加列 NOT NULL DEFAULT false，存量行回填 false")
    void flywayReplayAddsNotNullColumnWithFalseDefault() throws Exception {
        Path dbFile = tempDir.resolve("existing-before-migration.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        // 预置"迁移前"的既有库：registration_authority 无 is_primary 列，且已有存量行
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE registration_authority (id blob not null, primary key (id))");
            st.execute("INSERT INTO registration_authority (id) VALUES (randomblob(16))");
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
        assertThat(result.migrations.stream().map(m -> m.version))
                .containsExactly(FLYWAY_VERSION);

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            // 列元数据：NOT NULL + DEFAULT false
            boolean found = false;
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(registration_authority)")) {
                while (rs.next()) {
                    if ("is_primary".equals(rs.getString("name"))) {
                        found = true;
                        assertThat(rs.getString("type").toLowerCase()).isEqualTo("boolean");
                        assertThat(rs.getInt("notnull")).isEqualTo(1);
                        assertThat(rs.getString("dflt_value")).isIn("false", "0");
                    }
                }
            }
            assertThat(found).as("is_primary 列应已添加").isTrue();

            // 存量行回填 false
            try (ResultSet rs = st.executeQuery("SELECT is_primary FROM registration_authority")) {
                while (rs.next()) {
                    assertThat(rs.getBoolean(1)).isFalse();
                }
            }

            // 新行默认 false；显式 true 可写入
            st.execute("INSERT INTO registration_authority (id) VALUES (randomblob(16))");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO registration_authority (id, is_primary) VALUES (randomblob(16), ?)")) {
                ps.setBoolean(1, true);
                ps.executeUpdate();
            }
            int falses = 0;
            int trues = 0;
            try (ResultSet rs = st.executeQuery("SELECT is_primary FROM registration_authority")) {
                while (rs.next()) {
                    if (rs.getBoolean(1)) {
                        trues++;
                    } else {
                        falses++;
                    }
                }
            }
            assertThat(falses).isEqualTo(2);
            assertThat(trues).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("V20261005001(sqlite): 二次启动 Flyway 不再重复应用（一次语义，幂等无改动）")
    void secondFlywayRunIsNoOp() throws Exception {
        Path dbFile = tempDir.resolve("existing-restart.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE registration_authority (id blob not null, primary key (id))");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "", "")
                .locations("classpath:db/migration/sqlite")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion(BASELINE))
                .target(MigrationVersion.fromVersion(FLYWAY_VERSION))
                .load();

        flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(second.migrationsExecuted).isEqualTo(0);
    }
}
