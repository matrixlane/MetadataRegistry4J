package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseResolver.LegacyState;
import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseResolver.PhaseResolution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapPhaseResolverTest {

    @TempDir
    Path tempDir;

    private Path localConfig;
    private InitializationStateStore store;

    /** 一次性引导接缝假实现：避免真实数据库连接。 */
    private static class StubResolver extends BootstrapPhaseResolver {
        Optional<LegacyState> legacy = Optional.empty();
        int legacyCalls;

        StubResolver(InitializationStateStore store) {
            super(store);
        }

        @Override
        protected Optional<LegacyState> readLegacyState(ConfigurableEnvironment env) {
            legacyCalls++;
            return legacy;
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void initPaths() {
        this.localConfig = tempDir.resolve("application-local.yml");
        this.store = new InitializationStateStore(tempDir.resolve("initialization-state.yml"));
        // 隔离：所有用例的本地配置都指向临时目录，避免读到真实用户配置
        System.setProperty("metadata.registry.config.file", localConfig.toString());
    }

    private String originalConfigFileProperty;

    @org.junit.jupiter.api.BeforeEach
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

    private void pointLocalConfigAtTemp() {
        System.setProperty("metadata.registry.config.file", localConfig.toString());
    }

    private ConfigurableEnvironment environment(String url, String... extraPairs) {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> source = new HashMap<>();
        source.put("spring.datasource.url", url);
        source.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        source.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        source.put("spring.jpa.mapping-resources", "orm/orm-sqlite.xml");
        source.put("spring.flyway.locations", "classpath:db/migration/sqlite");
        source.put("spring.flyway.schemas", "main");
        source.put("spring.flyway.default-schema", "main");
        for (int i = 0; i + 1 < extraPairs.length; i += 2) {
            source.put(extraPairs[i], extraPairs[i + 1]);
        }
        env.getPropertySources().addFirst(new MapPropertySource("test", source));
        return env;
    }

    private String fingerprintOf(ConfigurableEnvironment env, BootstrapPhaseResolver resolver) {
        return resolver.currentFingerprint(env);
    }

    @Test
    void 无状态记录且无配置时进入向导相() {
        StubResolver resolver = new StubResolver(store);
        PhaseResolution resolution = resolver.resolve(environment("jdbc:sqlite:/x.db"));
        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.WIZARD);
        assertThat(resolver.legacyCalls).isZero();
    }

    @Test
    void 无状态记录有配置且既有库已初始化时一次性引导为完整相并写状态记录() throws Exception {
        pointLocalConfigAtTemp();
        Files.writeString(localConfig, "spring: {}");
        StubResolver resolver = new StubResolver(store);
        resolver.legacy = Optional.of(new LegacyState(true, "PERSONAL"));
        ConfigurableEnvironment env = environment("jdbc:sqlite:/x.db");

        PhaseResolution resolution = resolver.resolve(env);

        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.FULL);
        assertThat(resolution.legacyBootstrapped()).isTrue();
        assertThat(store.read()).isPresent().get()
                .satisfies(state -> {
                    assertThat(state.datasourceConfigured()).isTrue();
                    assertThat(state.initialized()).isTrue();
                    assertThat(state.edition()).isEqualTo("PERSONAL");
                    assertThat(state.datasourceFingerprint())
                            .isEqualTo(fingerprintOf(env, resolver));
                });
    }

    @Test
    void 无状态记录有配置且既有库未初始化时一次性引导写状态记录但仍留向导相() throws Exception {
        pointLocalConfigAtTemp();
        Files.writeString(localConfig, "spring: {}");
        StubResolver resolver = new StubResolver(store);
        resolver.legacy = Optional.of(new LegacyState(false, null));
        ConfigurableEnvironment env = environment("jdbc:sqlite:/x.db");

        PhaseResolution resolution = resolver.resolve(env);

        // 既有库未初始化 = 向导未完成：不得升入完整相（完整相登录墙会锁死向导）
        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.WIZARD);
        assertThat(store.read()).isPresent().get()
                .satisfies(state -> assertThat(state.initialized()).isFalse());
    }

    @Test
    void 无状态记录有配置但数据库不可达时不写状态记录且进入向导相() throws Exception {
        pointLocalConfigAtTemp();
        Files.writeString(localConfig, "spring: {}");
        StubResolver resolver = new StubResolver(store);
        resolver.legacy = Optional.empty();

        PhaseResolution resolution = resolver.resolve(environment("jdbc:sqlite:/x.db"));

        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.WIZARD);
        assertThat(resolution.legacyBootstrapped()).isFalse();
        assertThat(store.read()).isEmpty();
    }

    @Test
    void 有状态记录但配置缺失时重置确认标记并进入向导相() throws Exception {
        pointLocalConfigAtTemp();
        store.write(InitializationState.fresh()
                .withDatasourceConfirmed("fp")
                .withInitialized(true)
                .withEdition("PERSONAL"));

        PhaseResolution resolution = new StubResolver(store).resolve(environment("jdbc:sqlite:/x.db"));

        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.WIZARD);
        assertThat(store.read()).isPresent().get()
                .satisfies(state -> {
                    assertThat(state.datasourceConfigured()).isFalse();
                    // 已初始化事实保留，仅确认标记随配置缺失重置
                    assertThat(state.initialized()).isTrue();
                });
    }

    @Test
    void 状态记录未确认数据源时进入向导相() throws Exception {
        pointLocalConfigAtTemp();
        Files.writeString(localConfig, "spring: {}");
        store.write(InitializationState.fresh());

        PhaseResolution resolution = new StubResolver(store).resolve(environment("jdbc:sqlite:/x.db"));

        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.WIZARD);
    }

    @Test
    void 指纹失配时进入向导相并标记配置漂移() throws Exception {
        pointLocalConfigAtTemp();
        Files.writeString(localConfig, "spring: {}");
        store.write(InitializationState.fresh().withDatasourceConfirmed("stale-fingerprint"));

        PhaseResolution resolution = new StubResolver(store).resolve(environment("jdbc:sqlite:/x.db"));

        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.WIZARD);
        assertThat(resolution.configDrift()).isTrue();
    }

    @Test
    void 已确认且指纹匹配时进入完整相() throws Exception {
        pointLocalConfigAtTemp();
        Files.writeString(localConfig, "spring: {}");
        StubResolver resolver = new StubResolver(store);
        ConfigurableEnvironment env = environment("jdbc:sqlite:/x.db");
        store.write(InitializationState.fresh()
                .withDatasourceConfirmed(fingerprintOf(env, resolver))
                .withInitialized(true));

        PhaseResolution resolution = resolver.resolve(env);

        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.FULL);
        assertThat(resolution.configDrift()).isFalse();
        assertThat(resolution.legacyBootstrapped()).isFalse();
    }

    @Test
    void 已确认数据源但初始化未完成且指纹匹配时仍留向导相() throws Exception {
        pointLocalConfigAtTemp();
        Files.writeString(localConfig, "spring: {}");
        StubResolver resolver = new StubResolver(store);
        ConfigurableEnvironment env = environment("jdbc:sqlite:/x.db");
        // 事故回归（2026-10-27）：向导第 2 步保存数据源后自动重启，状态记录为
        // datasourceConfigured=true + initialized=false + 指纹匹配；旧实现误升
        // 完整相，未初始化的部署无任何账号，向导被登录墙永久锁死。
        store.write(InitializationState.fresh()
                .withDatasourceConfirmed(fingerprintOf(env, resolver)));

        PhaseResolution resolution = resolver.resolve(env);

        assertThat(resolution.phase()).isEqualTo(BootstrapPhase.WIZARD);
        assertThat(resolution.configDrift()).isFalse();
        assertThat(resolution.legacyBootstrapped()).isFalse();
    }

    @Test
    void 完整相不触发一次性引导读取() throws Exception {
        pointLocalConfigAtTemp();
        Files.writeString(localConfig, "spring: {}");
        StubResolver resolver = new StubResolver(store);
        ConfigurableEnvironment env = environment("jdbc:sqlite:/x.db");
        store.write(InitializationState.fresh()
                .withDatasourceConfirmed(fingerprintOf(env, resolver)));

        resolver.resolve(env);

        assertThat(resolver.legacyCalls).isZero();
    }
}
