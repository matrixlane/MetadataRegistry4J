package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigRequest;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
}
