package io.github.matrixlane.metadataregistry.web.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.PropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全新空库启动引导（sqlite-empty-db-bootstrap + baseline-data-init-rebuild）。
 *
 * <p>SQLite 用真实 sqlite-jdbc 驱动做空库识别；临时目录隔离，不触碰真实数据库文件。
 * SQL Server/达梦经可覆写的探测接缝注入假实现，断言 fresh 属性注入与 schema 预建。</p>
 */
class FreshDatabaseInitializerTest {

    private final FreshDatabaseInitializer initializer = new FreshDatabaseInitializer();

    /** 服务端库探测接缝假实现：不触网，直接返回预设结果并记录 schema 预建入参。 */
    private static class StubGateway extends FreshDatabaseInitializer {
        Boolean hasUserTables;
        List<String> createdSchemas;

        @Override
        Boolean serverHasUserTables(String dialect, String url, String username, String password) {
            return hasUserTables;
        }

        @Override
        void createSqlServerSchemas(String url, String username, String password, List<String> schemas) {
            createdSchemas = schemas;
        }
    }

    private ConfigurableEnvironment environment(String url, Object... extraPairs) {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("spring.datasource.url", url);
        for (int i = 0; i < extraPairs.length; i += 2) {
            source.put((String) extraPairs[i], extraPairs[i + 1]);
        }
        env.getPropertySources().addFirst(new MapPropertySource("test", source));
        return env;
    }

    private PropertySource<?> freshSource(ConfigurableEnvironment env) {
        return env.getPropertySources().get("mdrFreshDatabase");
    }

    @Test
    void missingFileActivatesFreshBootstrapAndCreatesParentDirectory(@TempDir Path tempDir) {
        Path dbFile = tempDir.resolve("deep/nested/dir").resolve("metadata-registry.db");
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile);

        initializer.apply(env);

        assertThat(freshSource(env)).as("缺文件的全新库应注入引导属性").isNotNull();
        // 旧格式本地配置显式关闭的 Flyway 不得阻断全新库基线化
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(env.getProperty("spring.flyway.baseline-version"))
                .isEqualTo(FreshDatabaseInitializer.FRESH_BASELINE_VERSION);
        assertThat(env.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("true");
        assertThat(env.getProperty("spring.flyway.schemas")).isEqualTo("main");
        assertThat(env.getProperty("spring.flyway.default-schema")).isEqualTo("main");
        assertThat(dbFile.getParent()).as("父目录应先于数据源初始化创建").exists();
        assertThat(dbFile).doesNotExist();
    }

    @Test
    void existingDatabaseWithTablesIsNotTouched(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("existing.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE some_business_table (id INTEGER)");
        }
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile);

        initializer.apply(env);

        assertThat(freshSource(env)).as("既有库不得被重新基线化").isNull();
    }

    @Test
    void legacyUnhistoriedDatabaseKeepsCompatibilityPathWithWarning(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("legacy.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE identified_item (id INTEGER)");
        }
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile);

        initializer.apply(env);

        assertThat(freshSource(env)).as("无历史的旧库不得静默标为新基线").isNull();
    }

    @Test
    void emptyExistingFileIsFresh(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("empty.db");
        // sqlite-jdbc 首次连接即创建空文件（零用户表）
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            // 仅建立连接即创建文件
        }
        assertThat(dbFile).exists();
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile);
        initializer.apply(env);
        assertThat(freshSource(env)).isNotNull();
    }

    @Test
    void unsupportedJdbcUrlIsIgnored() {
        ConfigurableEnvironment env = environment("jdbc:h2:mem:unsupported");
        initializer.apply(env);
        assertThat(freshSource(env)).isNull();
    }

    @Test
    void sqlserverEmptyDatabaseInjectsFreshOverridesAndCreatesSchemas() {
        StubGateway stub = new StubGateway();
        stub.hasUserTables = false;
        ConfigurableEnvironment env = environment(
                "jdbc:sqlserver://localhost:1433;databaseName=fresh_db;integratedSecurity=true",
                "spring.flyway.schemas", "mdr,ext");

        stub.apply(env);

        assertThat(freshSource(env)).as("SQL Server 空库应注入引导属性").isNotNull();
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(env.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("true");
        // 基线版本动态扫描自 db/migration/sqlserver 目录最大版本号，不随迁移增加而过期
        assertThat(env.getProperty("spring.flyway.baseline-version"))
                .isEqualTo(FreshDatabaseInitializer.maxMigrationVersion("sqlserver"));
        // flyway schemas 维持 profile 配置（mdr,ext），引导层不覆盖
        assertThat(env.getProperty("spring.flyway.schemas")).isEqualTo("mdr,ext");
        assertThat(freshSource(env).getProperty("spring.flyway.schemas")).isNull();
        // fresh 时在 EMF 之前预建 mdr/ext schema（取 flyway schemas 配置值）
        assertThat(stub.createdSchemas).containsExactly("mdr", "ext");
    }

    @Test
    void sqlserverNonEmptyDatabaseIsNotTouched() {
        StubGateway stub = new StubGateway();
        stub.hasUserTables = true;
        ConfigurableEnvironment env = environment(
                "jdbc:sqlserver://localhost:1433;databaseName=existing_db;integratedSecurity=true");

        stub.apply(env);

        assertThat(freshSource(env)).as("既有库不得被重新基线化").isNull();
        assertThat(stub.createdSchemas).as("既有库不得预建 schema").isNull();
    }

    @Test
    void sqlserverProbeFailureKeepsDefaultStartupPath() {
        StubGateway stub = new StubGateway();
        stub.hasUserTables = null;
        ConfigurableEnvironment env = environment(
                "jdbc:sqlserver://unreachable:1433;databaseName=x;integratedSecurity=true");

        org.assertj.core.api.Assertions.assertThatCode(() -> stub.apply(env))
                .as("探测失败不阻断启动").doesNotThrowAnyException();
        assertThat(freshSource(env)).isNull();
        assertThat(stub.createdSchemas).isNull();
    }

    @Test
    void dmEmptyDatabaseInjectsBaselineWithoutSchemaCreation() {
        StubGateway stub = new StubGateway();
        stub.hasUserTables = false;
        ConfigurableEnvironment env = environment(
                "jdbc:dm://localhost:5236",
                "spring.datasource.username", "MDR",
                "spring.datasource.password", "secret");

        stub.apply(env);

        assertThat(freshSource(env)).as("达梦空库应注入引导属性").isNotNull();
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(env.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("true");
        assertThat(env.getProperty("spring.flyway.baseline-version"))
                .isEqualTo(FreshDatabaseInitializer.maxMigrationVersion("dm"));
        // 达梦用户即模式：无需预建 schema
        assertThat(stub.createdSchemas).isNull();
    }

    @Test
    void migrationVersionScanMatchesDirectoriesOnClasspath() {
        // 测试 classpath 即模块 target/classes：三方言迁移目录实扫
        assertThat(FreshDatabaseInitializer.maxMigrationVersion("sqlserver")).isEqualTo("20261028001");
        assertThat(FreshDatabaseInitializer.maxMigrationVersion("dm")).isEqualTo("20261028001");
        assertThat(FreshDatabaseInitializer.maxMigrationVersion("sqlite")).isEqualTo("20261028001");
        assertThat(FreshDatabaseInitializer.maxMigrationVersion("no-such-dialect")).isNull();
    }

    @Test
    void flywayDisabledIsIgnoredForExistingDatabase(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("existing.db");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE some_business_table (id INTEGER)");
        }
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile,
                "spring.flyway.enabled", false);
        initializer.apply(env);
        assertThat(freshSource(env)).as("Flyway 关闭的既有库不得被重新基线化").isNull();
    }

    @Test
    void flywayDisabledDoesNotBlockFreshBootstrap(@TempDir Path tempDir) {
        // 旧格式本地配置显式关闭的 Flyway 只针对既有漂移库；全新空库必须强制
        // 启用 Flyway 完成基线化（2026-10-20 修正，见 FreshDatabaseInitializer javadoc）。
        // 本地配置源模拟生产位置：systemEnvironment 之后（低于全新库引导覆盖）。
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + tempDir.resolve("fresh.db"));
        env.getPropertySources().addAfter(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource("localConfig", Map.of("spring.flyway.enabled", false)));
        initializer.apply(env);
        assertThat(freshSource(env)).as("全新库不得被 flyway.enabled=false 阻断基线化").isNotNull();
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("true");
    }

    @Test
    void freshBootstrapYieldsToHigherPrecedenceSources(@TempDir Path tempDir) {
        Path dbFile = tempDir.resolve("env.db");
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile);
        initializer.apply(env);
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        // 属性源插在 systemEnvironment 之后：命令行/环境变量等高优先级来源仍应胜出
        env.getPropertySources().addFirst(new MapPropertySource("commandLine",
                Map.of("spring.jpa.hibernate.ddl-auto", "validate")));
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    // ── init-phase-database-free 就绪形态 ─────────────────────────────

    @Test
    void datasourceNotReadySkipsProbingEntirely(@TempDir Path tempDir) {
        Path dbFile = tempDir.resolve("fresh.db");
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile,
                io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_ENABLED, true,
                io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, false);

        initializer.apply(env);

        // 零数据库行为：不注入任何引导属性，连"全新库"标记也不做
        assertThat(freshSource(env)).isNull();
        assertThat(env.getProperty(io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE)).isNull();
    }

    @Test
    void datasourceReadyFreshDatabaseOnlyMarksForBootstrap(@TempDir Path tempDir) {
        Path dbFile = tempDir.resolve("fresh.db");
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile,
                io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_ENABLED, true,
                io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, true);

        initializer.apply(env);

        // 新架构：只标记"待建表"，不再注入 ddl-auto / Flyway 覆盖
        assertThat(env.getProperty(io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE))
                .isEqualTo("true");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isNull();
        assertThat(env.getProperty("spring.flyway.baseline-version")).isNull();
        assertThat(dbFile.getParent()).as("父目录仍应先于数据源初始化创建").exists();
    }

    @Test
    void datasourceReadyExistingDatabaseIsNotTouched(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("existing.db");
        Files.createDirectories(dbFile.getParent());
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement st = conn.createStatement()) {
            st.execute("create table some_business_table (id integer)");
        }
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + dbFile,
                io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_ENABLED, true,
                io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, true);

        initializer.apply(env);

        assertThat(freshSource(env)).isNull();
    }

    @Test
    void sqlserverEmptyDatabaseWhenReadyOnlyMarksForBootstrap() {
        StubGateway stub = new StubGateway();
        stub.hasUserTables = false;
        ConfigurableEnvironment env = environment("jdbc:sqlserver://localhost:1433;databaseName=freshdb",
                io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_ENABLED, true,
                io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, true);

        stub.apply(env);

        assertThat(env.getProperty(io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE))
                .isEqualTo("true");
        assertThat(env.getProperty("spring.flyway.enabled")).isNull();
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isNull();
        // schema 预建仍由本初始化器负责（供引导建表使用）
        assertThat(stub.createdSchemas).contains("mdr", "ext");
    }
}



