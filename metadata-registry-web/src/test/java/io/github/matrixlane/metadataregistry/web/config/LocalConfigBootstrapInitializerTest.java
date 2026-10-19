package io.github.matrixlane.metadataregistry.web.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.PropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地保存配置（application-local.yml）启动期加载（sqlite-empty-db-bootstrap）。
 *
 * <p>通过 {@code metadata.registry.config.file} 系统属性指向临时文件，
 * 验证优先级：命令行/环境变量 &gt; 本地配置 &gt; profile 默认（config data）。
 */
class LocalConfigBootstrapInitializerTest {

    private static final String OVERRIDE_KEY = "metadata.registry.config.file";

    private final LocalConfigBootstrapInitializer initializer = new LocalConfigBootstrapInitializer();

    @Test
    void localConfigOverridesProfileDefaultsButYieldsToHigherPrecedence(@TempDir Path tempDir) throws IOException {
        Path local = tempDir.resolve("application-local.yml");
        Files.writeString(local, """
                spring:
                  datasource:
                    url: ${DB_URL:jdbc:sqlite:C:/chosen/app.db}
                  flyway:
                    schemas: ${FLYWAY_SCHEMAS:main}
                """);
        String original = System.getProperty(OVERRIDE_KEY);
        System.setProperty(OVERRIDE_KEY, local.toString());
        try {
            StandardEnvironment env = new StandardEnvironment();
            // 模拟 profile 默认（config data，低优先级）
            env.getPropertySources().addLast(new MapPropertySource("profileDefaults",
                    Map.of("spring.datasource.url", "${DB_URL:jdbc:sqlite:./data/default.db}")));
            initializer.apply(env);

            // 本地配置覆盖 profile 默认（占位符在绑定时解析）
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:sqlite:C:/chosen/app.db");
            assertThat(env.getProperty("spring.flyway.schemas")).isEqualTo("main");
            // 属性源位于 systemEnvironment 之后、config data 之前
            PropertySource<?> source = env.getPropertySources().get("mdr-local-config");
            assertThat(source).isNotNull();

            // 环境变量/命令行等高优先级来源仍可覆盖本地配置
            env.getPropertySources().addFirst(new MapPropertySource("commandLine",
                    Map.of("spring.datasource.url", "jdbc:sqlite:from-env.db")));
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:sqlite:from-env.db");
        } finally {
            if (original == null) {
                System.clearProperty(OVERRIDE_KEY);
            } else {
                System.setProperty(OVERRIDE_KEY, original);
            }
        }
    }

    @Test
    void missingFileIsIgnored(@TempDir Path tempDir) {
        String original = System.getProperty(OVERRIDE_KEY);
        System.setProperty(OVERRIDE_KEY, tempDir.resolve("nonexistent.yml").toString());
        try {
            StandardEnvironment env = new StandardEnvironment();
            initializer.apply(env);
            assertThat(env.getPropertySources().get("mdr-local-config")).isNull();
        } finally {
            if (original == null) {
                System.clearProperty(OVERRIDE_KEY);
            } else {
                System.setProperty(OVERRIDE_KEY, original);
            }
        }
    }
}
