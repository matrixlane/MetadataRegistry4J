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
        // 切换方言必须同步 ORM 映射：SQLite 下 orm-team.xml 的 ext 模式无法建表
        assertThat(written).contains("mapping-resources: orm/orm-sqlite.xml");
        // 路径含空格时 SnakeYAML 需要加引号才能保持标量完整
        assertThat(written).contains("app.db");
    }

    @Test
    void saveConfigServerVendorReplacesStaleSqliteFlywaySchemasWithMdrExt(@TempDir Path tempDir) throws IOException {
        // 先落一份 SQLite 配置（模拟"从 SQLite 切换"时旧值残留的场景）
        DatasourceConfigService sqliteSpy = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doAnswer(invocation -> null).when(sqliteSpy)
                .testConnection(org.mockito.ArgumentMatchers.any(DatasourceConfigRequest.class));
        sqliteSpy.saveConfig(new DatasourceConfigRequest(
                "sqlite", null, null, null, tempDir.resolve("old.db").toString(),
                null, null, null, "PERSONAL"));
        assertThat(Files.readString(localConfig)).contains("schemas: ${FLYWAY_SCHEMAS:main}");

        // 切到 SQL Server：main 必须被替换为 mdr,ext——沿用会让 Flyway 在服务端库的
        // [main] 找不到历史表，从头重放不可重放的老迁移（2026-10-27 真实事故）
        DatasourceConfigService serverSpy = org.mockito.Mockito.spy(service);
        org.mockito.Mockito.doAnswer(invocation -> null).when(serverSpy)
                .testConnection(org.mockito.ArgumentMatchers.any(DatasourceConfigRequest.class));
        serverSpy.saveConfig(new DatasourceConfigRequest(
                "sqlserver", "localhost", 1433, "appdb", null,
                null, null, null, "PERSONAL"));

        String written = Files.readString(localConfig);
        assertThat(written).doesNotContain("FLYWAY_SCHEMAS:main");
        assertThat(written).doesNotContain("FLYWAY_DEFAULT_SCHEMA:main");
        assertThat(written).contains("schemas: ${FLYWAY_SCHEMAS:mdr,ext}");
        // flyway_schema_history 非 ISO/IEC 11179 内容，依分层规则落 ext（AGENTS.md）
        assertThat(written).contains("default-schema: ${FLYWAY_DEFAULT_SCHEMA:ext}");
        assertThat(written).contains("mapping-resources: orm/orm-team.xml");
        assertThat(written).contains("locations: ${DB_VENDOR:classpath:db/migration/sqlserver}");
    }

    @Test
    void mappingResourcesFollowVendor() {
        assertThat(service.mappingResourcesFor(DatabaseVendor.SQLITE)).isEqualTo("orm/orm-sqlite.xml");
        assertThat(service.mappingResourcesFor(DatabaseVendor.SQLSERVER)).isEqualTo("orm/orm-team.xml");
        assertThat(service.mappingResourcesFor(DatabaseVendor.DM)).isEqualTo("orm/orm-team.xml");
    }

    @Test
    void sanitizeLegacyBehaviorKeysStripsDdlAutoButKeepsFlywaySwitch(@TempDir Path tempDir) throws IOException {
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
        // ddl-auto 必须剥离：update 在新优先级下会覆盖 personal profile 的 none
        assertThat(sanitized).doesNotContain("ddl-auto");
        // flyway.enabled 刻意保留：既有"无 Flyway 历史且 schema 漂移"的库依赖它关闭
        // Flyway 才能启动（V20260919003 等迁移假设 ddl-auto 建表结构，重放即失败）；
        // 全新空库由 FreshDatabaseInitializer 强制启用 Flyway，不受其影响。
        assertThat(sanitized).contains("enabled:");
        assertThat(sanitized).contains("false");
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
