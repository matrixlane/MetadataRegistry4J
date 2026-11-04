package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.config.LocalConfigBootstrapInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 复现现场：saveConfig 落盘的 application-local.yml 经 LocalConfigBootstrapInitializer
 * 加载后，spring.datasource.url 必须可解析（真实进程验证曾观察到"url not specified"）。
 */
class LocalConfigUrlResolutionProbeTest {

    @TempDir
    Path tempDir;

    @Test
    void 保存的配置加载后数据源地址可解析() throws Exception {
        Path config = tempDir.resolve("application-local.yml");
        Files.writeString(config, """
                spring:
                  datasource:
                    url: ${DB_URL:jdbc:sqlite:E:\\\\data\\\\app.db}
                    driver-class-name: ${DB_DRIVER:org.sqlite.JDBC}
                  jpa:
                    mapping-resources: orm/orm-sqlite.xml
                    properties:
                      hibernate:
                        default_schema: ${DB_DEFAULT_SCHEMA:}
                        dialect: ${HIBERNATE_DIALECT:org.hibernate.community.dialect.SQLiteDialect}
                  flyway:
                    locations: ${DB_VENDOR:classpath:db/migration/sqlite}
                """);

        String originalConfigFileProperty = System.getProperty("metadata.registry.config.file");
        System.setProperty("metadata.registry.config.file", config.toString());
        StandardEnvironment environment = new StandardEnvironment();
        try {
            new LocalConfigBootstrapInitializer().apply(environment);

            String url = environment.getProperty("spring.datasource.url");
            assertThat(url).isNotBlank();
            assertThat(url).isEqualTo("jdbc:sqlite:E:\\\\data\\\\app.db");

            BootstrapPhaseResolver resolver = new BootstrapPhaseResolver(
                    new InitializationStateStore(tempDir.resolve("initialization-state.yml")));
            String fingerprint = resolver.currentFingerprint(environment);
            assertThat(fingerprint).isEqualTo(DatasourceFingerprint.compute(
                    "SQLITE",
                    "jdbc:sqlite:E:\\\\data\\\\app.db",
                    "org.hibernate.community.dialect.SQLiteDialect",
                    "orm/orm-sqlite.xml",
                    "classpath:db/migration/sqlite",
                    ""));
        } finally {
            if (originalConfigFileProperty == null) {
                System.clearProperty("metadata.registry.config.file");
            } else {
                System.setProperty("metadata.registry.config.file", originalConfigFileProperty);
            }
        }
    }
}
