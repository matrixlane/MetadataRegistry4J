package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.config.FreshDatabaseInitializer;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.config.LegacyLanguageSchemaRepairer;
import io.github.matrixlane.metadataregistry.web.seed.RegistryContextBackfillRunner;
import io.github.matrixlane.metadataregistry.web.seed.StaticDataPresetService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.FlywayHistoryRelocator;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.List;

/**
 * 数据库初始化引导（init-phase-database-free）。
 *
 * <p>把分散在启动期的数据库动作收敛为单一、有序、可捕获、可重复调用的入口：
 * 连接校验 → 结构创建（仅全新库）→ 迁移/基线化 → 旧库修复 → 静态预置 →
 * 标识符策略装载 → 治理数据回填。任一步失败即记录阶段与原因并短路后续步骤；
 * 应用保持运行，由 {@link BootstrapProgress} 向就绪指示器与失败态查询提供结论。
 *
 * <p>启动期（{@code DatabaseBootstrapRunner}）与运行期"重试初始化"复用本入口，
 * 因此"启动能过、重试不能过"的差异不可能存在。
 *
 * <p>失败信息的 message MUST NOT 包含数据库口令或完整连接串
 * （{@link #sanitizeForReporting}）。
 */
@Service
@Slf4j
public class DatabaseBootstrapService {

    private final Environment environment;
    private final DataSource dataSource;
    private final DatabaseEnvironment databaseEnvironment;
    private final LegacyLanguageSchemaRepairer legacyLanguageSchemaRepairer;
    private final StaticDataPresetService staticDataPresetService;
    private final IdentifierStrategyLoader identifierStrategyLoader;
    private final RegistryContextBackfillRunner registryContextBackfillRunner;
    private final FlywayHistoryRelocator flywayHistoryRelocator;
    private final BootstrapProgress progress;

    public DatabaseBootstrapService(Environment environment,
                                    DataSource dataSource,
                                    DatabaseEnvironment databaseEnvironment,
                                    LegacyLanguageSchemaRepairer legacyLanguageSchemaRepairer,
                                    StaticDataPresetService staticDataPresetService,
                                    IdentifierStrategyLoader identifierStrategyLoader,
                                    RegistryContextBackfillRunner registryContextBackfillRunner,
                                    FlywayHistoryRelocator flywayHistoryRelocator,
                                    BootstrapProgress progress) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.databaseEnvironment = databaseEnvironment;
        this.legacyLanguageSchemaRepairer = legacyLanguageSchemaRepairer;
        this.staticDataPresetService = staticDataPresetService;
        this.identifierStrategyLoader = identifierStrategyLoader;
        this.registryContextBackfillRunner = registryContextBackfillRunner;
        this.flywayHistoryRelocator = flywayHistoryRelocator;
        this.progress = progress;
    }

    /**
     * 执行完整引导序列。幂等可重入：重复调用会重跑全部步骤（各步骤自身幂等）。
     * 任一步失败即包装为 {@link BootstrapException} 短路，应用保持运行。
     */
    public synchronized BootstrapOutcome bootstrap() {
        BootstrapOutcome outcome;
        try {
            checkConnection();
            if (isFreshDatabase()) {
                createSchema();
            }
            migrate();
            runStep(BootstrapStage.REPAIR, legacyLanguageSchemaRepairer::repairIfNeeded);
            runStep(BootstrapStage.PRESET, staticDataPresetService::presetAll);
            runStep(BootstrapStage.STRATEGY, identifierStrategyLoader::loadStrategies);
            runStep(BootstrapStage.BACKFILL, registryContextBackfillRunner::backfill);
            outcome = BootstrapOutcome.success();
            progress.record(outcome);
            log.info("数据库初始化引导完成");
        } catch (BootstrapException e) {
            outcome = BootstrapOutcome.failed(e.failure());
            progress.record(outcome);
            log.error("数据库初始化引导失败（阶段 {}，原因 {}）：{}",
                    e.failure().stage(), e.failure().code(), e.failure().message());
        }
        persistFailure(outcome);
        return outcome;
    }

    /** 当前引导结论（未执行时为 NOT_STARTED 失败态）。 */
    public BootstrapOutcome currentOutcome() {
        return progress.outcome();
    }

    /**
     * 失败信息落盘（~/.metadata-registry/restart/bootstrap-failure.json），
     * 供离线排查；引导成功时清除。脱敏规则与接口一致，不含口令或完整连接串。
     */
    private void persistFailure(BootstrapOutcome outcome) {
        // 仅新架构启用时落盘；关闭时（集成测试等自行管理 schema 的环境）不触碰用户目录
        if (!environment.getProperty(BootstrapPhaseInitializer.PROPERTY_ENABLED, Boolean.class, false)) {
            return;
        }
        try {
            Path dir = InitializationStateStore.defaultPath().getParent().resolve("restart");
            Files.createDirectories(dir);
            Path file = dir.resolve("bootstrap-failure.json");
            if (outcome.ready()) {
                Files.deleteIfExists(file);
                return;
            }
            BootstrapFailure failure = outcome.failure();
            String json = ("{\"stage\":\"%s\",\"code\":\"%s\",\"message\":\"%s\",\"updatedAt\":\"%s\"}")
                    .formatted(failure.stage(), escape(failure.code()),
                            escape(failure.message()), java.time.Instant.now());
            Files.writeString(file, json, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 落盘失败不改变引导结论
            log.warn("引导失败信息落盘失败: {}", e.getMessage());
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 委托步骤的统一异常包装：原始异常映射为对应阶段的失败原因。 */
    private void runStep(BootstrapStage stage, Runnable step) {
        try {
            step.run();
        } catch (BootstrapException e) {
            throw e;
        } catch (Exception e) {
            throw newBootstrapException(stage, stage.name() + "_FAILED", e);
        }
    }

    private void checkConnection() {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(5)) {
                throw newBootstrapException(BootstrapStage.CONNECTION, "CONNECTION_FAILED",
                        "数据库连接不可用");
            }
        } catch (BootstrapException e) {
            throw e;
        } catch (Exception e) {
            throw newBootstrapException(BootstrapStage.CONNECTION, "CONNECTION_FAILED", e);
        }
    }

    private boolean isFreshDatabase() {
        return environment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_FRESH_DATABASE, Boolean.class, false);
    }

    /**
     * 全新库建表：执行实体工厂创建期经 JPA 标准脚本生成写出的 DDL 文件。
     *
     * <p>BootstrapPhaseInitializer 注入 {@code scripts.action=create} 与
     * create-target：EMF 创建时把当前实体模型的建表 DDL 写入该文件（不访问
     * 数据库、不执行），本步骤在引导期读取并逐条执行——与 ddl-auto=update
     * 同一 Hibernate 方言生成器，产出一致，且"先全部建表、后外键约束"的顺序
     * 天然安全。EMF 恒为 ddl-auto=none，因此本步骤是建表的唯一路径；失败仅
     * 记录原因，不会摧毁上下文。
     */
    private void createSchema() {
        Path script = schemaScriptPath();
        try {
            if (!Files.exists(script) || Files.size(script) == 0) {
                throw new IllegalStateException("建表脚本缺失或为空：" + script);
            }
            try (Connection conn = dataSource.getConnection()) {
                // 连接池配置 auto-commit=false 时，DDL 不显式提交会在连接关闭时
                // 整体回滚——Flyway 随后看到的是空库而从头重放不可重放的历史迁移。
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(true);
                try {
                    ScriptUtils.executeSqlScript(conn,
                            new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            }
            log.info("全新库结构已创建（DDL 出自实体元数据脚本 {}）", script);
        } catch (BootstrapException e) {
            throw e;
        } catch (Exception e) {
            throw newBootstrapException(BootstrapStage.SCHEMA, "SCHEMA_FAILED", e);
        }
    }

    private Path schemaScriptPath() {
        String configured = environment.getProperty(BootstrapPhaseInitializer.PROPERTY_SCHEMA_SCRIPT);
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        return BootstrapPhaseInitializer.defaultSchemaScriptPath();
    }

    /** 迁移/基线化：程序化 Flyway（自动配置已关闭）；SQL Server 附带历史表搬迁。 */
    protected void migrate() {
        try {
            FluentConfiguration configuration = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(environment.getProperty(
                            "spring.flyway.locations", "classpath:db/migration/sqlite"))
                    .baselineOnMigrate(true)
                    .baselineVersion(resolveBaselineVersion());
            String defaultSchema = environment.getProperty("spring.flyway.default-schema");
            if (defaultSchema != null && !defaultSchema.isBlank()) {
                configuration.defaultSchema(defaultSchema);
            }
            List<String> schemas = Binder.get(environment)
                    .bind("spring.flyway.schemas", Bindable.listOf(String.class))
                    .orElse(List.of());
            if (!schemas.isEmpty()) {
                configuration.schemas(schemas.toArray(new String[0]));
            }
            Flyway flyway = configuration.load();
            flyway.migrate();
            if (databaseEnvironment.vendor() == io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor.SQLSERVER) {
                Connection conn = DataSourceUtils.getConnection(dataSource);
                try {
                    flywayHistoryRelocator.relocate(flyway, conn);
                } finally {
                    DataSourceUtils.releaseConnection(conn, dataSource);
                }
            }
        } catch (BootstrapException e) {
            throw e;
        } catch (Exception e) {
            throw newBootstrapException(BootstrapStage.MIGRATION, "MIGRATION_FAILED", e);
        }
    }

    /**
     * 基线版本：全新库必须落在共同基线（FRESH_BASELINE_VERSION）——否则
     * application.yml 的既有库默认基线（20260918002）会让全新库重放整段
     * 不可在空库执行的历史迁移（如 SQLite 不支持的 DROP COLUMN）；
     * 既有库维持配置的基线（兼容链）。
     */
    private String resolveBaselineVersion() {
        if (isFreshDatabase()) {
            return FreshDatabaseInitializer.FRESH_BASELINE_VERSION;
        }
        return environment.getProperty(
                "spring.flyway.baseline-version", FreshDatabaseInitializer.FRESH_BASELINE_VERSION);
    }

    private BootstrapException newBootstrapException(BootstrapStage stage, String code, Exception cause) {
        return new BootstrapException(
                new BootstrapFailure(stage, code, stage.describe() + "：" + sanitizeForReporting(cause.getMessage())),
                cause);
    }

    private BootstrapException newBootstrapException(BootstrapStage stage, String code, String message) {
        return new BootstrapException(new BootstrapFailure(stage, code, message), null);
    }

    /**
     * 面向用户与接口的错误描述脱敏：遮蔽口令/用户名参数并截断长度，
     * MUST NOT 泄漏数据库口令或完整连接串。
     */
    static String sanitizeForReporting(String detail) {
        if (detail == null) {
            return "";
        }
        String masked = detail
                .replaceAll("(?i)(password=)[^;\\s]+", "$1***")
                .replaceAll("(?i)(user=)[^;\\s]+", "$1***")
                .replaceAll("(?i)(username=)[^;\\s]+", "$1***")
                .replaceAll("(?i)(user ')[^']+(')", "$1***$2")
                .replaceAll("(?i)(login ')[^']+(')", "$1***$2");
        return masked.length() > 300 ? masked.substring(0, 300) : masked;
    }

    /** 引导步骤失败：携带阶段与原因，短路后续步骤。 */
    static class BootstrapException extends RuntimeException {
        private final BootstrapFailure failure;

        BootstrapException(BootstrapFailure failure, Throwable cause) {
            super(failure.message(), cause);
            this.failure = failure;
        }

        BootstrapFailure failure() {
            return failure;
        }
    }
}
