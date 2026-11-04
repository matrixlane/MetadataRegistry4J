package io.github.matrixlane.metadataregistry.web.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQLite 默认库路径迁至用户目录（baseline-data-init-rebuild / D4）的解析实测。
 *
 * <p>profile 默认值写为 {@code ${DB_URL:jdbc:sqlite:${user.home}/.metadata-registry/...}}
 * 嵌套占位符形态；本测试以 {@link StandardEnvironment}（含 systemProperties 属性源）
 * + 真实 classpath yml 验证：默认值内的 {@code ${user.home}} 会被嵌套展开，解析结果
 * 为指向用户目录的绝对路径。若 Spring 行为变化导致不展开，本测试立即变红，
 * 提示回退到 DatasourceConfigService.resolveSqliteFilePath 层预处理方案。</p>
 */
class SqliteDefaultUrlResolutionTest {

    private static final String EXPECTED_SUFFIX = "/.metadata-registry/data/metadata-registry.db";

    private String resolveFromClasspathYml(String resourcePath) throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(resourcePath, new ClassPathResource(resourcePath));
        assertThat(sources).as("%s 应含属性源", resourcePath).isNotEmpty();

        StandardEnvironment env = new StandardEnvironment();
        for (PropertySource<?> source : sources) {
            env.getPropertySources().addFirst(source);
        }
        return env.getProperty("spring.datasource.url");
    }

    private void assertUserHomeDefault(String url, String source) {
        assertThat(url).as("%s 解析出的 URL", source).startsWith("jdbc:sqlite:");
        String rawPath = url.substring("jdbc:sqlite:".length());
        assertThat(rawPath).as("%s 默认值不得残留未展开的占位符", source).doesNotContain("${");
        Path path = Paths.get(rawPath);
        assertThat(path.isAbsolute()).as("%s 解析结果须为绝对路径: %s", source, path).isTrue();
        assertThat(path.toString().replace('\\', '/'))
                .as("%s 默认库文件须位于用户目录", source)
                .endsWith(EXPECTED_SUFFIX);
        assertThat(path.startsWith(Paths.get(System.getProperty("user.home"))))
                .as("%s 默认库文件须在 user.home 之下: %s", source, path)
                .isTrue();
    }

    @Test
    void personalProfileDefaultUrlResolvesToUserHomePath() throws IOException {
        assertUserHomeDefault(resolveFromClasspathYml("application-personal.yml"), "application-personal.yml");
    }

    @Test
    void baseProfileDefaultUrlResolvesToUserHomePath() throws IOException {
        assertUserHomeDefault(resolveFromClasspathYml("application.yml"), "application.yml");
    }

    @Test
    void dbUrlEnvironmentVariableStillOverridesNestedDefault() {
        // 占位符优先级语义：DB_URL 显式提供时默认值（含嵌套 user.home）整体不生效。
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",
                java.util.Map.of("spring.datasource.url",
                        "${DB_URL:jdbc:sqlite:${user.home}/.metadata-registry/data/metadata-registry.db}")));
        System.setProperty("DB_URL", "jdbc:sqlite:C:/chosen/override.db");
        try {
            assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:sqlite:C:/chosen/override.db");
        } finally {
            System.clearProperty("DB_URL");
        }
    }
}
