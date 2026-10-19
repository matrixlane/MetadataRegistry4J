package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigView;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据源配置落盘约束：SQLite 无认证概念，username/password 一律不得写入
 * application-local.yml。事故背景：数据源表单在 SQLite 模式下隐藏凭据输入框，
 * 但隐藏只是不渲染，浏览器自动填充进表单模型的值（2026-08-07 曾把应用 admin
 * 登录口令当作数据源凭据）仍会随保存请求提交并落盘。
 */
class DatasourceConfigServiceTest {

    private DatasourceConfigService service;
    private Path localConfig;
    private String originalConfig;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        // 每个测试使用独立临时文件，通过包级构造器注入，避免静态字段初始化顺序污染真实配置。
        localConfig = tempDir.resolve("application-local.yml");
        originalConfig = Files.exists(localConfig) ? Files.readString(localConfig) : null;
        service = new DatasourceConfigService(
                mock(SystemSettingService.class), mock(DatabaseEnvironment.class), localConfig);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (originalConfig != null) {
            Files.writeString(localConfig, originalConfig);
        } else {
            Files.deleteIfExists(localConfig);
        }
    }

    @Test
    void saveConfigSqliteMustNotPersistCredentials(@TempDir Path tempDir) throws IOException {
        // 预置已被污染的配置：datasource 下残留旧凭据
        Files.createDirectories(localConfig.getParent());
        Files.writeString(localConfig, """
                spring:
                  datasource:
                    url: jdbc:sqlite:./data/metadata-registry.db
                    username: admin
                    password: leaked-secret
                """);

        Path dbFile = tempDir.resolve("test.db");
        DatasourceConfigRequest request = new DatasourceConfigRequest(
                "sqlite", null, null, null,
                dbFile.toString().replace('\\', '/'),
                "admin", "leaked-secret", null, "PERSONAL");

        service.saveConfig(request);

        String written = Files.readString(localConfig);
        assertThat(written).as("SQLite 保存不得写入 username").doesNotContain("username");
        assertThat(written).as("SQLite 保存不得写入 password").doesNotContain("password");
        assertThat(written).contains("jdbc:sqlite:");
    }

    private DatasourceConfigView viewFor(DatabaseVendor vendor, String url) {
        DatabaseEnvironment env = mock(DatabaseEnvironment.class);
        when(env.vendor()).thenReturn(vendor);
        DatasourceConfigService s = new DatasourceConfigService(
                mock(SystemSettingService.class), env, localConfig);
        ReflectionTestUtils.setField(s, "currentUrl", url);
        return s.currentConfig();
    }

    @Test
    void currentConfigParsesSqlserverUrlWithIntegratedSecurity() {
        DatasourceConfigView view = viewFor(DatabaseVendor.SQLSERVER,
                "jdbc:sqlserver://db.example.com:14330;databaseName=mdr_prod;encrypt=true;trustServerCertificate=true;integratedSecurity=true");

        assertThat(view.vendor()).isEqualTo("sqlserver");
        assertThat(view.host()).isEqualTo("db.example.com");
        assertThat(view.port()).isEqualTo(14330);
        assertThat(view.database()).isEqualTo("mdr_prod");
        assertThat(view.hasCredentials()).isFalse();
    }

    @Test
    void currentConfigParsesSqlserverUrlWithCredentials() {
        DatasourceConfigView view = viewFor(DatabaseVendor.SQLSERVER,
                "jdbc:sqlserver://localhost;databaseName=metadata_registry;encrypt=true;trustServerCertificate=true");

        assertThat(view.host()).isEqualTo("localhost");
        assertThat(view.port()).isNull();
        assertThat(view.database()).isEqualTo("metadata_registry");
        assertThat(view.hasCredentials()).isTrue();
    }

    @Test
    void currentConfigParsesSqliteUrl() {
        DatasourceConfigView view = viewFor(DatabaseVendor.SQLITE,
                "jdbc:sqlite:./data/metadata-registry.db");

        assertThat(view.vendor()).isEqualTo("sqlite");
        assertThat(view.filePath()).isEqualTo("./data/metadata-registry.db");
        assertThat(view.host()).isNull();
        assertThat(view.hasCredentials()).isFalse();
    }

    @Test
    void currentConfigParsesDmUrl() {
        DatasourceConfigView view = viewFor(DatabaseVendor.DM, "jdbc:dm://192.168.1.10:5237");

        assertThat(view.vendor()).isEqualTo("dm");
        assertThat(view.host()).isEqualTo("192.168.1.10");
        assertThat(view.port()).isEqualTo(5237);
    }

    @Test
    void saveConfigSqliteExpandsHomePrefix() throws IOException {
        DatasourceConfigRequest request = new DatasourceConfigRequest(
                "sqlite", null, null, null,
                "~/.metadata-registry/data/metadata-registry.db",
                null, null, null, "PERSONAL");

        service.saveConfig(request);

        String written = Files.readString(localConfig);
        Path expected = Paths.get(System.getProperty("user.home"))
                .resolve(".metadata-registry/data/metadata-registry.db");
        assertThat(written)
                .as("~/ 前缀应展开为用户主目录并规整为绝对路径")
                .contains("jdbc:sqlite:" + expected);
    }

    @Test
    void saveConfigSqliteWritesMainFlywaySchemaAndEnvWrappedValues(@TempDir Path tempDir) throws IOException {
        Path dbFile = tempDir.resolve("nested dir with space").resolve("app.db");
        DatasourceConfigRequest request = new DatasourceConfigRequest(
                "sqlite", null, null, null,
                dbFile.toString(),
                null, null, null, "PERSONAL");

        service.saveConfig(request);

        String written = Files.readString(localConfig);
        // SQLite 下 Flyway 必须显式落到 main，否则重启后 Flyway 无法在 SQLite 定位历史表
        assertThat(written).contains("schemas: ${FLYWAY_SCHEMAS:main}");
        assertThat(written).contains("default-schema: ${FLYWAY_DEFAULT_SCHEMA:main}");
        // 保存值以 ${ENV:值} 落盘：重启后对 profile 默认生效，环境变量仍可覆盖
        assertThat(written).contains("url: ${DB_URL:jdbc:sqlite:");
        assertThat(written).contains("driver-class-name: ${DB_DRIVER:org.sqlite.JDBC}");
        assertThat(written).contains("dialect: ${HIBERNATE_DIALECT:org.hibernate.community.dialect.SQLiteDialect}");
        assertThat(written).contains("locations: ${DB_VENDOR:classpath:db/migration/sqlite}");
        // 路径含空格时 SnakeYAML 需要加引号才能保持标量完整
        assertThat(written).contains("app.db");
    }

    @Test
    void sanitizeLegacyBehaviorKeysStripsLegacySwitches(@TempDir Path tempDir) throws IOException {
        Path legacy = tempDir.resolve("application-local.yml");
        Files.writeString(legacy, """
                spring:
                  datasource:
                    url: jdbc:sqlite:./data/metadata-registry.db
                  flyway:
                    enabled: false
                    locations: classpath:db/migration/sqlite
                  jpa:
                    hibernate:
                      ddl-auto: update
                """);

        DatasourceConfigService.sanitizeLegacyBehaviorKeys(legacy);

        String sanitized = Files.readString(legacy);
        assertThat(sanitized).doesNotContain("enabled:");
        assertThat(sanitized).doesNotContain("ddl-auto");
        // 数据源连接键原样保留
        assertThat(sanitized).contains("url: jdbc:sqlite:./data/metadata-registry.db");
        assertThat(sanitized).contains("locations: classpath:db/migration/sqlite");
    }

    @Test
    void sanitizeLegacyBehaviorKeysKeepsCurrentFormatUntouched(@TempDir Path tempDir) throws IOException {
        Path current = tempDir.resolve("application-local.yml");
        String body = """
                spring:
                  datasource:
                    url: ${DB_URL:jdbc:sqlite:./data/metadata-registry.db}
                  flyway:
                    locations: ${DB_VENDOR:classpath:db/migration/sqlite}
                    schemas: ${FLYWAY_SCHEMAS:main}
                """;
        Files.writeString(current, body);

        DatasourceConfigService.sanitizeLegacyBehaviorKeys(current);

        assertThat(Files.readString(current)).isEqualTo(body);
    }
}
