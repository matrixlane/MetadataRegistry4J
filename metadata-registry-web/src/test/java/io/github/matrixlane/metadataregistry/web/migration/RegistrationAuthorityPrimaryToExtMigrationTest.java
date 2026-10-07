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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移 V20261008001（三方言：mdr.registration_authority.is_primary 迁出至
 * ext 卫星表）验证。
 *
 * <p>沿用 {@link RegistrationAuthorityPrimaryFlagMigrationTest} 的迁移测试模式：
 * 预置"迁移前"的 registration_authority 表（含 is_primary 列，模拟已应用
 * V20261005001 的既有库），基线到 20261005001、target 钉到 20261008001，由 Flyway
 * 应用 sqlite 分支脚本——与生产 personal 库（baseline-on-migrate）同一条执行路径。
 * SQL Server / 达梦分支无可用的容器化实例，按项目惯例留待部署实测，此处仅做
 * 版本一致性（三方言同版本号）与内容静态核对。</p>
 */
class RegistrationAuthorityPrimaryToExtMigrationTest {

    private static final String VERSION = "V20261008001";
    /** Flyway API 的版本号不含 "V" 前缀。 */
    private static final String FLYWAY_VERSION = "20261008001";
    /** 迁移前既有库版本：V20261005001 已为 registration_authority 加上 is_primary 列。 */
    private static final String BASELINE = "20261005001";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("三方言脚本齐备且同版本号（sqlite/sqlserver/dm 各一份，均搬迁至 ext 卫星表）")
    void threeDialectScriptsShareVersion() throws Exception {
        for (String dialect : new String[]{"sqlite", "sqlserver", "dm"}) {
            String path = "db/migration/" + dialect + "/" + VERSION + "__Move_ra_primary_to_ext.sql";
            var resource = new ClassPathResource(path);
            assertThat(resource.exists()).as("%s 分支脚本应存在", dialect).isTrue();
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).as("%s 分支脚本应搬运 is_primary 并删除 mdr 列", dialect)
                    .containsIgnoringCase("is_primary")
                    .containsIgnoringCase("registration_authority");
        }
    }

    @Test
    @DisplayName("V20261008001(sqlite): 真实 Flyway 重放——mdr 列已删、ext 卫星表存在且数据搬全")
    void flywayReplayMovesPrimaryToExt() throws Exception {
        Path dbFile = tempDir.resolve("existing-before-migration.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        UUID primaryId = UUID.randomUUID();
        UUID normalId = UUID.randomUUID();

        // 预置"迁移前"的既有库：registration_authority 已含 is_primary 列（V20261005001
        // 之后的状态），且已有存量行（一主一非主）
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE registration_authority ("
                    + "id blob not null, is_primary boolean not null default false, primary key (id))");
            st.execute("INSERT INTO registration_authority (id, is_primary) VALUES ('"
                    + primaryId + "', true)");
            st.execute("INSERT INTO registration_authority (id, is_primary) VALUES ('"
                    + normalId + "', false)");
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
            // 1. mdr 列已删
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(registration_authority)")) {
                while (rs.next()) {
                    assertThat(rs.getString("name")).as("mdr 列应已删除").isNotEqualTo("is_primary");
                }
            }

            // 2. ext 卫星表存在：id 主键非空 + is_primary NOT NULL DEFAULT false
            Map<String, String[]> columns = new HashMap<>();
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(ext_registration_authority)")) {
                while (rs.next()) {
                    columns.put(rs.getString("name"), new String[]{
                            rs.getString("type").toLowerCase(),
                            String.valueOf(rs.getInt("notnull")),
                            rs.getString("pk")});
                }
            }
            assertThat(columns).as("ext 卫星表应已创建").containsKeys("id", "is_primary");
            assertThat(columns.get("id")[0]).isEqualTo("blob");
            assertThat(columns.get("id")[1]).isEqualTo("1");
            assertThat(columns.get("id")[2]).isEqualTo("1");
            assertThat(columns.get("is_primary")[0]).isEqualTo("boolean");
            assertThat(columns.get("is_primary")[1]).isEqualTo("1");

            // 3. 数据搬全：两行均落入卫星表且标志值保持
            Map<String, Boolean> moved = new HashMap<>();
            try (ResultSet rs = st.executeQuery("SELECT id, is_primary FROM ext_registration_authority")) {
                while (rs.next()) {
                    moved.put(rs.getString(1), rs.getBoolean(2));
                }
            }
            assertThat(moved).hasSize(2);
            assertThat(moved.get(primaryId.toString())).isTrue();
            assertThat(moved.get(normalId.toString())).isFalse();

            // 4. 卫星表新行默认 false
            st.execute("INSERT INTO ext_registration_authority (id) VALUES ('" + UUID.randomUUID() + "')");
            int trues = 0;
            int falses = 0;
            try (ResultSet rs = st.executeQuery("SELECT is_primary FROM ext_registration_authority")) {
                while (rs.next()) {
                    if (rs.getBoolean(1)) {
                        trues++;
                    } else {
                        falses++;
                    }
                }
            }
            assertThat(trues).isEqualTo(1);
            assertThat(falses).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("V20261008001(sqlite): 二次启动 Flyway 不再重复应用（一次语义，幂等无改动）")
    void secondFlywayRunIsNoOp() throws Exception {
        Path dbFile = tempDir.resolve("existing-restart.db");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE registration_authority ("
                    + "id blob not null, is_primary boolean not null default false, primary key (id))");
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
