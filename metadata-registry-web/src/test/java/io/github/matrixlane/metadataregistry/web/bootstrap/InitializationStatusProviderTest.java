package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InitializationStatusProviderTest {

    @TempDir
    Path tempDir;

    private SystemSettingService systemSettingService;
    private InitializationStateStore store;

    @BeforeEach
    void setUp() {
        systemSettingService = mock(SystemSettingService.class);
        store = new InitializationStateStore(tempDir.resolve("initialization-state.yml"));
    }

    private StandardEnvironment enabledEnvironment() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                BootstrapPhaseInitializer.PROPERTY_ENABLED, true,
                BootstrapPhaseInitializer.PROPERTY_PHASE, "full")));
        return env;
    }

    @Test
    void 开关关闭时退化为数据库判定() {
        when(systemSettingService.isInitialized()).thenReturn(true);
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, new StandardEnvironment(), store);

        assertThat(provider.isInitialized()).isTrue();
        assertThat(provider.bootstrapEnabled()).isFalse();
    }

    @Test
    void 开启时以状态记录为准且不查询数据库() {
        store.write(InitializationState.fresh().withInitialized(true));
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, enabledEnvironment(), store);

        assertThat(provider.isInitialized()).isTrue();
        verifyNoInteractions(systemSettingService);
    }

    @Test
    void 开启时无状态记录视为未初始化且不查询数据库() {
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, enabledEnvironment(), store);

        assertThat(provider.isInitialized()).isFalse();
        verifyNoInteractions(systemSettingService);
    }

    @Test
    void 开启时版本取自状态记录且不查询数据库() {
        store.write(InitializationState.fresh()
                .withDatasourceConfirmed("fp")
                .withInitialized(true)
                .withEdition("TEAM"));
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, enabledEnvironment(), store);

        assertThat(provider.getEdition()).isEqualTo(DeploymentEdition.TEAM);
        verifyNoInteractions(systemSettingService);
    }

    @Test
    void 状态记录写入后同实例读取反映新值() {
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, enabledEnvironment(), store);
        assertThat(provider.isInitialized()).isFalse();

        store.write(InitializationState.fresh().withInitialized(true));
        assertThat(provider.isInitialized()).isTrue();
    }

    @Test
    void 已确认且指纹匹配时视为已确认() {
        StandardEnvironment env = datasourceEnvironment();
        String fingerprint = DatasourceFingerprint.compute(
                "SQLITE", "jdbc:sqlite:/x.db", "dialect", "mapping", "loc", "ds");
        store.write(InitializationState.fresh().withDatasourceConfirmed(fingerprint));
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, env, store);

        assertThat(provider.isDatasourceConfirmed()).isTrue();
        verifyNoInteractions(systemSettingService);
    }

    @Test
    void 已确认但指纹失配时不算确认() {
        StandardEnvironment env = datasourceEnvironment();
        store.write(InitializationState.fresh().withDatasourceConfirmed("stale-fingerprint"));
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, env, store);

        assertThat(provider.isDatasourceConfirmed()).isFalse();
    }

    @Test
    void 无状态记录或未确认数据源时不算确认() {
        StandardEnvironment env = datasourceEnvironment();
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, env, store);
        assertThat(provider.isDatasourceConfirmed()).isFalse();

        store.write(InitializationState.fresh());
        assertThat(provider.isDatasourceConfirmed()).isFalse();
    }

    @Test
    void 开关关闭时恒为不确认() {
        store.write(InitializationState.fresh().withDatasourceConfirmed("fp"));
        InitializationStatusProvider provider = new InitializationStatusProvider(
                systemSettingService, new StandardEnvironment(), store);

        assertThat(provider.isDatasourceConfirmed()).isFalse();
    }

    private StandardEnvironment datasourceEnvironment() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                BootstrapPhaseInitializer.PROPERTY_ENABLED, true,
                BootstrapPhaseInitializer.PROPERTY_PHASE, "wizard",
                "spring.datasource.url", "jdbc:sqlite:/x.db",
                "spring.jpa.properties.hibernate.dialect", "dialect",
                "spring.jpa.mapping-resources", "mapping",
                "spring.flyway.locations", "loc",
                "spring.jpa.properties.hibernate.default_schema", "ds")));
        return env;
    }
}
