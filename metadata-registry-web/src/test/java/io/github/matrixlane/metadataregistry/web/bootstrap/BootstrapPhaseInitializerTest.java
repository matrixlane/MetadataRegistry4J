package io.github.matrixlane.metadataregistry.web.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapPhaseInitializerTest {

    @TempDir
    Path tempDir;

    private Path localConfig;
    private InitializationStateStore store;

    @BeforeEach
    void initPaths() {
        this.localConfig = tempDir.resolve("application-local.yml");
        this.store = new InitializationStateStore(tempDir.resolve("initialization-state.yml"));
        System.setProperty("metadata.registry.config.file", localConfig.toString());
    }

    private String originalConfigFileProperty;

    @BeforeEach
    void saveSystemProperty() {
        this.originalConfigFileProperty = System.getProperty("metadata.registry.config.file");
    }

    @AfterEach
    void restoreSystemProperty() {
        if (originalConfigFileProperty == null) {
            System.clearProperty("metadata.registry.config.file");
        } else {
            System.setProperty("metadata.registry.config.file", originalConfigFileProperty);
        }
    }

    private ConfigurableEnvironment environment(boolean enabled, String url) {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> source = new HashMap<>();
        source.put(BootstrapPhaseInitializer.PROPERTY_ENABLED, enabled);
        source.put("spring.datasource.url", url);
        source.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        source.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        source.put("spring.jpa.mapping-resources", "orm/orm-sqlite.xml");
        source.put("spring.flyway.locations", "classpath:db/migration/sqlite");
        env.getPropertySources().addFirst(new MapPropertySource("test", source));
        return env;
    }

    @Test
    void 总开关关闭时不注入任何属性() {
        ConfigurableEnvironment env = environment(false, "jdbc:sqlite:/x.db");
        new BootstrapPhaseInitializer().apply(env);
        assertThat(env.getPropertySources().get(BootstrapPhaseInitializer.PROPERTY_SOURCE_NAME)).isNull();
        assertThat(env.getProperty("spring.flyway.enabled")).isNull();
    }

    @Test
    void 向导相未确认数据源时引流内存库且标记未就绪() {
        ConfigurableEnvironment env = environment(true, "jdbc:sqlite:/x.db");
        new BootstrapPhaseInitializer(new BootstrapPhaseResolver(store)).apply(env);

        assertThat(env.getProperty(BootstrapPhaseInitializer.PROPERTY_PHASE)).isEqualTo("wizard");
        assertThat(env.getProperty(BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY)).isEqualTo("false");
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        assertThat(env.getProperty("spring.datasource.hikari.initialization-fail-timeout")).isEqualTo("-1");
        assertThat(env.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("0");
        assertThat(env.getProperty("spring.datasource.hikari.jdbc-url")).isEqualTo("jdbc:sqlite::memory:");
    }

    @Test
    void 向导相已确认数据源时不引流内存库且标记就绪() throws Exception {
        Files.writeString(localConfig, "spring: {}");
        BootstrapPhaseResolver resolver = new BootstrapPhaseResolver(store);
        ConfigurableEnvironment env = environment(true, "jdbc:sqlite:/x.db");
        // 向导第 2 步保存数据源并重启后的状态：已确认 + initialized=false + 指纹匹配
        store.write(InitializationState.fresh()
                .withDatasourceConfirmed(resolver.currentFingerprint(env)));

        new BootstrapPhaseInitializer(resolver).apply(env);

        assertThat(env.getProperty(BootstrapPhaseInitializer.PROPERTY_PHASE)).isEqualTo("wizard");
        assertThat(env.getProperty(BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY)).isEqualTo("true");
        assertThat(env.getProperty("spring.datasource.hikari.jdbc-url")).isNull();
        assertThat(env.getProperty("spring.datasource.hikari.minimum-idle")).isNull();
    }

    @Test
    void 完整相不注入空闲连接清零且标记就绪() throws Exception {
        Files.writeString(localConfig, "spring: {}");
        BootstrapPhaseResolver resolver = new BootstrapPhaseResolver(store);
        ConfigurableEnvironment env = environment(true, "jdbc:sqlite:/x.db");
        store.write(InitializationState.fresh()
                .withDatasourceConfirmed(resolver.currentFingerprint(env))
                .withInitialized(true));

        new BootstrapPhaseInitializer(resolver).apply(env);

        assertThat(env.getProperty(BootstrapPhaseInitializer.PROPERTY_PHASE)).isEqualTo("full");
        assertThat(env.getProperty(BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY)).isEqualTo("true");
        assertThat(env.getProperty("spring.datasource.hikari.minimum-idle")).isNull();
        assertThat(env.getProperty("spring.flyway.enabled")).isEqualTo("false");
    }

    @Test
    void 重复应用时属性源不叠加() {
        ConfigurableEnvironment env = environment(true, "jdbc:sqlite:/x.db");
        BootstrapPhaseInitializer initializer = new BootstrapPhaseInitializer(new BootstrapPhaseResolver(store));
        initializer.apply(env);
        initializer.apply(env);
        assertThat(env.getPropertySources().contains(BootstrapPhaseInitializer.PROPERTY_SOURCE_NAME)).isTrue();
        assertThat(env.getProperty(BootstrapPhaseInitializer.PROPERTY_PHASE)).isNotNull();
    }
}
