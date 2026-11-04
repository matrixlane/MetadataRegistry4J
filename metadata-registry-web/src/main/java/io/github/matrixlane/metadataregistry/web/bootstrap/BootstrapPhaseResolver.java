package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.service.DatasourceConfigService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

/**
 * 启动相位判定。
 *
 * <p>在 {@code main()} 初始化器层（上下文刷新之前）运行，结论供全部数据库相关
 * 行为引用。判定依据：
 *
 * <ol>
 *   <li>本地初始化状态记录（{@link InitializationStateStore}）；</li>
 *   <li>数据源配置是否存在（{@code DatasourceConfigService.localConfigPath()}）；</li>
 *   <li>当前有效配置指纹与记录指纹是否一致；</li>
 *   <li>状态记录中的 initialized 标志——为 {@code false} 时无论指纹是否匹配
 *       都留在向导相（未初始化的部署不存在任何账号，升入完整相会被登录墙锁死）。</li>
 * </ol>
 *
 * <p>唯一的数据库读取例外：状态记录缺失且配置存在（既有部署升级）时，向数据库
 * 确认一次初始化状态并写入状态记录；此后不再以数据库作为该判据。数据库不可达时
 * 按向导相处理且不写状态记录（下次启动重试）。
 */
@Slf4j
public class BootstrapPhaseResolver {

    private final InitializationStateStore store;

    public BootstrapPhaseResolver() {
        this(new InitializationStateStore());
    }

    BootstrapPhaseResolver(InitializationStateStore store) {
        this.store = store;
    }

    /**
     * 判定启动相位。{@code configDrift} 表示"配置已确认但指纹失配"（被手工改动）。
     */
    public PhaseResolution resolve(ConfigurableEnvironment env) {
        Optional<InitializationState> existing = store.read();
        boolean configExists = Files.exists(DatasourceConfigService.localConfigPath());
        if (existing.isEmpty()) {
            if (!configExists) {
                return new PhaseResolution(BootstrapPhase.WIZARD, null, false, false);
            }
            Optional<LegacyState> legacy = readLegacyState(env);
            if (legacy.isEmpty()) {
                log.warn("初始化状态记录缺失且数据库不可达：按向导相启动，不写状态记录");
                return new PhaseResolution(BootstrapPhase.WIZARD, null, false, false);
            }
            InitializationState written = InitializationState.fresh()
                    .withDatasourceConfirmed(currentFingerprint(env))
                    .withInitialized(legacy.get().initialized())
                    .withEdition(legacy.get().edition());
            store.write(written);
            log.info("既有部署一次性引导完成：initialized={} edition={}，状态记录已写入 {}",
                    legacy.get().initialized(), legacy.get().edition(), store.path());
            // 既有库未完成初始化时不得借一次性引导直接进入完整相——那会把向导
            // 锁在登录墙外（完整相安全规则要求已认证主体，而未初始化的部署
            // 尚不存在任何账号）。未初始化一律留向导相。
            BootstrapPhase phase = legacy.get().initialized()
                    ? BootstrapPhase.FULL : BootstrapPhase.WIZARD;
            return new PhaseResolution(phase, written, false, true);
        }
        InitializationState state = existing.get();
        if (!configExists) {
            InitializationState updated = new InitializationState(
                    state.schemaVersion(), false, null,
                    state.initialized(), state.edition(), Instant.now());
            store.write(updated);
            log.info("数据源配置缺失：初始化状态记录的 datasourceConfigured 已重置为 false");
            return new PhaseResolution(BootstrapPhase.WIZARD, updated, false, false);
        }
        if (!state.datasourceConfigured()) {
            return new PhaseResolution(BootstrapPhase.WIZARD, state, false, false);
        }
        boolean drift = !currentFingerprint(env).equals(state.datasourceFingerprint());
        if (!state.initialized()) {
            // 向导未完成（含第 2 步保存数据源后自动重启的中间态）：无论指纹是否
            // 匹配都留在向导相。此处若误升完整相，安全规则会要求已认证主体，而
            // 未初始化的部署尚无任何账号，向导将被永久锁死。
            if (drift) {
                log.warn("数据源配置与状态记录指纹不一致：按向导相启动，提示重新确认");
            }
            return new PhaseResolution(BootstrapPhase.WIZARD, state, drift, false);
        }
        if (drift) {
            log.warn("数据源配置与状态记录指纹不一致：按向导相启动，提示重新确认");
            return new PhaseResolution(BootstrapPhase.WIZARD, state, true, false);
        }
        return new PhaseResolution(BootstrapPhase.FULL, state, false, false);
    }

    /**
     * 当前有效数据源配置的指纹（已对占位符求值）。
     *
     * <p>输入仅取 saveConfig 对全部方言都落盘的键（方言、URL、Hibernate 方言、
     * ORM 映射资源、Flyway 迁移目录、Hibernate 默认 schema），保证"保存时计算"
     * 与"启动时计算"完全一致；Flyway 的 schemas/default-schema 由 profile 派生、
     * 不独立漂移，故不入指纹。
     */
    public String currentFingerprint(ConfigurableEnvironment env) {
        String url = env.getProperty("spring.datasource.url");
        String vendor = DatabaseVendor.resolve(null, url).name();
        return DatasourceFingerprint.compute(
                vendor,
                url,
                env.getProperty("spring.jpa.properties.hibernate.dialect"),
                env.getProperty("spring.jpa.mapping-resources"),
                env.getProperty("spring.flyway.locations"),
                env.getProperty("spring.jpa.properties.hibernate.default_schema"));
    }

    /**
     * 既有部署一次性引导的数据库读取接缝。返回 empty 表示数据库不可达或查询失败。
     */
    protected Optional<LegacyState> readLegacyState(ConfigurableEnvironment env) {
        String url = env.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        DatabaseVendor vendor = DatabaseVendor.resolve(null, url);
        String schemaProperty = env.getProperty("spring.jpa.properties.hibernate.default_schema");
        String table = switch (vendor) {
            case SQLITE -> "system_setting";
            case SQLSERVER -> qualify(schemaProperty, "mdr") + ".system_setting";
            case DM -> qualify(schemaProperty, "MDR") + ".system_setting";
        };
        String driver = env.getProperty("spring.datasource.driver-class-name");
        String user = env.getProperty("spring.datasource.username");
        String password = env.getProperty("spring.datasource.password");
        try {
            if (driver != null && !driver.isBlank()) {
                Class.forName(driver);
            }
            try (Connection conn = DriverManager.getConnection(
                    url, user, password == null ? "" : password);
                 PreparedStatement ps = conn.prepareStatement(
                         "select setting_key, setting_value from " + table
                                 + " where setting_key in ('system.initialized','system.edition')");
                 ResultSet rs = ps.executeQuery()) {
                boolean initialized = false;
                String edition = null;
                while (rs.next()) {
                    String key = rs.getString(1);
                    String value = rs.getString(2);
                    if ("system.initialized".equals(key)) {
                        initialized = "true".equalsIgnoreCase(value);
                    } else if ("system.edition".equals(key)) {
                        edition = value;
                    }
                }
                return Optional.of(new LegacyState(initialized, edition));
            }
        } catch (Exception e) {
            log.warn("既有部署一次性引导读取数据库失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String qualify(String schemaProperty, String fallback) {
        return schemaProperty == null || schemaProperty.isBlank() ? fallback : schemaProperty;
    }

    /** 一次性引导从数据库读到的既有状态。 */
    public record LegacyState(boolean initialized, String edition) {
    }

    /** 相位判定结论。 */
    public record PhaseResolution(BootstrapPhase phase, InitializationState state,
                                  boolean configDrift, boolean legacyBootstrapped) {
    }
}
