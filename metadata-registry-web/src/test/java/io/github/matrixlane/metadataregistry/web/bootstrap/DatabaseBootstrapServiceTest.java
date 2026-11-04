package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.config.LegacyLanguageSchemaRepairer;
import io.github.matrixlane.metadataregistry.web.seed.RegistryContextBackfillRunner;
import io.github.matrixlane.metadataregistry.web.seed.StaticDataPresetService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.FlywayHistoryRelocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseBootstrapServiceTest {

    @TempDir
    Path tempDir;

    private Environment environment;
    private DataSource dataSource;
    private Connection connection;
    private LegacyLanguageSchemaRepairer repairer;
    private StaticDataPresetService presetService;
    private IdentifierStrategyLoader strategyLoader;
    private RegistryContextBackfillRunner backfillRunner;
    private FlywayHistoryRelocator historyRelocator;
    private BootstrapProgress progress;
    private DatabaseBootstrapService service;

    @BeforeEach
    void setUp() throws Exception {
        environment = new StandardEnvironment();
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);
        DatabaseEnvironment databaseEnvironment = mock(DatabaseEnvironment.class);
        repairer = mock(LegacyLanguageSchemaRepairer.class);
        presetService = mock(StaticDataPresetService.class);
        strategyLoader = mock(IdentifierStrategyLoader.class);
        backfillRunner = mock(RegistryContextBackfillRunner.class);
        historyRelocator = mock(FlywayHistoryRelocator.class);
        progress = new BootstrapProgress();
        service = newStubService();
    }

    /** 迁移步骤接缝：Flyway 需要真实数据库元数据，单测中以空实现替代。 */
    private DatabaseBootstrapService newStubService() {
        return new DatabaseBootstrapService(environment, dataSource,
                mock(DatabaseEnvironment.class), repairer, presetService, strategyLoader,
                backfillRunner, historyRelocator, progress) {
            @Override
            protected void migrate() {
                // 单测不验证 Flyway 本身
            }
        };
    }

    private void setFresh(boolean fresh) {
        Map<String, Object> source = new HashMap<>();
        source.put(BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE, fresh);
        ((StandardEnvironment) environment).getPropertySources()
                .addFirst(new MapPropertySource("test", source));
    }

    @Test
    void 全部步骤按序执行且结论为就绪() throws Exception {
        setFresh(false);
        BootstrapOutcome outcome = service.bootstrap();

        assertThat(outcome.ready()).isTrue();
        assertThat(progress.outcome().ready()).isTrue();
        var ordered = inOrder(repairer, presetService, strategyLoader, backfillRunner);
        ordered.verify(repairer).repairIfNeeded();
        ordered.verify(presetService).presetAll();
        ordered.verify(strategyLoader).loadStrategies();
        ordered.verify(backfillRunner).backfill();
    }

    @Test
    void 连接失败短路全部后续步骤() throws Exception {
        when(dataSource.getConnection()).thenThrow(new RuntimeException("connection refused"));

        BootstrapOutcome outcome = service.bootstrap();

        assertThat(outcome.ready()).isFalse();
        assertThat(outcome.failure().stage()).isEqualTo(BootstrapStage.CONNECTION);
        assertThat(outcome.failure().code()).isEqualTo("CONNECTION_FAILED");
        verify(repairer, never()).repairIfNeeded();
        verify(presetService, never()).presetAll();
        verify(strategyLoader, never()).loadStrategies();
        verify(backfillRunner, never()).backfill();
    }

    @Test
    void 预置失败短路策略与回填但保留已执行的前序() {
        setFresh(false);
        org.mockito.Mockito.doThrow(new RuntimeException("STUBBED_PRESET_FAILURE_MARKER"))
                .when(presetService).presetAll();

        BootstrapOutcome outcome = service.bootstrap();

        assertThat(outcome.ready()).isFalse();
        assertThat(outcome.failure().stage()).isEqualTo(BootstrapStage.PRESET);
        verify(repairer).repairIfNeeded();
        verify(strategyLoader, never()).loadStrategies();
        verify(backfillRunner, never()).backfill();
    }

    @Test
    void 全新库时执行建表脚本() throws Exception {
        Path script = tempDir.resolve("schema-create.sql");
        Files.writeString(script, "create table demo_table (id integer primary key);");
        Map<String, Object> source = new HashMap<>();
        source.put(BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE, true);
        source.put(BootstrapPhaseInitializer.PROPERTY_SCHEMA_SCRIPT, script.toString());
        ((StandardEnvironment) environment).getPropertySources()
                .addFirst(new MapPropertySource("test", source));
        Statement statement = mock(Statement.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getAutoCommit()).thenReturn(true);

        BootstrapOutcome outcome = service.bootstrap();

        assertThat(outcome.ready()).isTrue();
        verify(statement).execute("create table demo_table (id integer primary key)");
    }

    @Test
    void 建表脚本缺失时报结构创建失败() {
        Map<String, Object> source = new HashMap<>();
        source.put(BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE, true);
        source.put(BootstrapPhaseInitializer.PROPERTY_SCHEMA_SCRIPT,
                tempDir.resolve("missing.sql").toString());
        ((StandardEnvironment) environment).getPropertySources()
                .addFirst(new MapPropertySource("test", source));

        BootstrapOutcome outcome = service.bootstrap();

        assertThat(outcome.ready()).isFalse();
        assertThat(outcome.failure().stage()).isEqualTo(BootstrapStage.SCHEMA);
        assertThat(outcome.failure().code()).isEqualTo("SCHEMA_FAILED");
    }

    @Test
    void 错误描述遮蔽口令与用户名() {
        String sanitized = DatabaseBootstrapService.sanitizeForReporting(
                "Login failed for user 'sa'; password=Secret123; jdbc:sqlserver://db:1433");
        assertThat(sanitized).doesNotContain("Secret123");
        assertThat(sanitized).contains("password=***");
        assertThat(sanitized).contains("user '***'");
    }
}
