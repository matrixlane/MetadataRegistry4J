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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全新 SQLite 空库启动引导（sqlite-empty-db-bootstrap）。
 *
 * <p>真实 sqlite-jdbc 驱动做空库识别；临时目录隔离，不触碰真实数据库文件。
 */
class FreshDatabaseInitializerTest {

    private final FreshDatabaseInitializer initializer = new FreshDatabaseInitializer();

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
    void nonSqliteUrlIsIgnored() {
        ConfigurableEnvironment env = environment(
                "jdbc:sqlserver://localhost:1433;databaseName=metadata_registry");
        initializer.apply(env);
        assertThat(freshSource(env)).isNull();
    }

    @Test
    void flywayDisabledIsIgnored(@TempDir Path tempDir) {
        ConfigurableEnvironment env = environment("jdbc:sqlite:" + tempDir.resolve("x.db"),
                "spring.flyway.enabled", false);
        initializer.apply(env);
        assertThat(freshSource(env)).isNull();
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
}
