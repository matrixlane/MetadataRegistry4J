package io.github.matrixlane.metadataregistry.web.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 启动相位属性注入（初始化阶段数据库依赖延后）。
 *
 * <p>在上下文刷新之前完成相位判定并注入属性，驱动全部数据库相关行为：
 *
 * <ul>
 *   <li>始终（启用时）：{@code spring.flyway.enabled=false}（迁移由引导步骤显式执行）、
 *       {@code spring.jpa.hibernate.ddl-auto=none}（建表由引导步骤执行）、
 *       {@code spring.datasource.hikari.initialization-fail-timeout=-1}
 *       （连接池不快速失败，数据库不可达不摧毁上下文）；</li>
 *   <li>数据源未就绪（向导相且状态记录尚未确认数据源，或指纹漂移）：连接池引流
 *       到一次性内存库 {@code jdbc:sqlite::memory:}、{@code minimum-idle=0}
 *       ——零数据库行为，仅用于向导配置数据库之前的那段流程；</li>
 *   <li>数据源就绪（完整相，或向导相已确认且指纹一致）：保留真实连接池，由
 *       {@code DatabaseBootstrapRunner} 在启动期完成建表/迁移/预置——
 *       向导步骤 3-6 依赖真实数据库写业务数据。</li>
 * </ul>
 *
 * <p>就绪与否经 {@link #PROPERTY_DATASOURCE_READY} 发布，下游
 * （引导 Runner、全新库识别、schema 预建）统一以它为准，不再各自按相位名猜测。
 *
 * <p>属性源插在 systemEnvironment 之后：环境变量与命令行仍可覆盖，profile 默认
 * 被让位。注册顺序必须在 {@code LocalConfigBootstrapInitializer} 之后（指纹依赖
 * 其加载的本地配置）。
 *
 * <p>总开关 {@code app.bootstrap.enabled}（默认 {@code false}，个人版/团队版
 * profile 显式开启）：关闭时本初始化器不做任何事——集成测试等自行管理 schema
 * 的环境因此不受新机制影响。
 */
@Slf4j
public class BootstrapPhaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String PROPERTY_ENABLED = "app.bootstrap.enabled";
    public static final String PROPERTY_PHASE = "app.bootstrap.phase";
    public static final String PROPERTY_FRESH_DATABASE = "app.bootstrap.fresh-database";
    /**
     * 数据源就绪：连接池指向真实库（完整相恒真；向导相仅在状态记录已确认数据源
     * 且指纹一致时为真）。为假时连接池被引到一次性内存库，任何代码不得访问数据库。
     */
    public static final String PROPERTY_DATASOURCE_READY = "app.bootstrap.datasource-ready";
    /** 全新库建表 DDL 脚本路径（EMF 创建期写出，引导期执行）。 */
    public static final String PROPERTY_SCHEMA_SCRIPT = "app.bootstrap.schema-script";
    public static final String PROPERTY_SOURCE_NAME = "mdrBootstrapPhase";

    private final BootstrapPhaseResolver resolver;

    public BootstrapPhaseInitializer() {
        this(new BootstrapPhaseResolver());
    }

    BootstrapPhaseInitializer(BootstrapPhaseResolver resolver) {
        this.resolver = resolver;
    }

    /** 建表 DDL 脚本的默认路径：与初始化状态记录同目录，随用户目录一起管理。 */
    public static java.nio.file.Path defaultSchemaScriptPath() {
        return InitializationStateStore.defaultPath().getParent().resolve("schema-create.sql");
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        apply(applicationContext.getEnvironment());
    }

    /** 注入相位属性；返回判定结论便于测试断言。 */
    public BootstrapPhaseResolver.PhaseResolution apply(ConfigurableEnvironment environment) {
        if (!environment.getProperty(PROPERTY_ENABLED, Boolean.class, false)) {
            return null;
        }
        BootstrapPhaseResolver.PhaseResolution resolution = resolver.resolve(environment);
        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put(PROPERTY_PHASE, resolution.phase().name().toLowerCase(Locale.ROOT));
        overrides.put("spring.flyway.enabled", "false");
        overrides.put("spring.jpa.hibernate.ddl-auto", "none");
        overrides.put("spring.datasource.hikari.initialization-fail-timeout", "-1");
        // 数据源就绪判定：完整相恒就绪；向导相以状态记录"已确认 + 指纹一致"为准。
        // 就绪后向导步骤 3-6 需要真实数据库（写注册规范/机构/命名空间/管理员），
        // 此时不得再引流内存库——引流只用于"尚未配置数据源"的零数据库向导段。
        boolean datasourceReady = resolution.phase() == BootstrapPhase.FULL
                || (resolution.state() != null
                    && resolution.state().datasourceConfigured()
                    && !resolution.configDrift());
        overrides.put(PROPERTY_DATASOURCE_READY, String.valueOf(datasourceReady));
        if (!datasourceReady) {
            overrides.put("spring.datasource.hikari.minimum-idle", "0");
            // Hibernate 6 在 EMF 构建期为 bulk 变更探测临时表能力
            // （PersistentTableStrategy）必取一次物理连接——即便零数据库向导段
            // 不该有任何数据库行为，这一探测也会让"真实库路径不可写"时启动
            // 直接失败。该阶段没有任何代码路径真正使用数据库，因此把连接池引到
            // 一次性内存库：真实 URL 仍保留在环境中（当前数据源展示/指纹/连接
            // 测试都读环境而非连接池），仅物理连接指向内存库。
            overrides.put("spring.datasource.hikari.jdbc-url", "jdbc:sqlite::memory:");
            overrides.put("spring.datasource.hikari.driver-class-name", "org.sqlite.JDBC");
        }
        // 替换同名属性源，保证重复调用（测试）时结论一致
        environment.getPropertySources().remove(PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addAfter(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(PROPERTY_SOURCE_NAME, overrides));
        log.info("启动相位={}（configDrift={}, legacyBootstrapped={}），数据库相关属性已注入",
                resolution.phase(), resolution.configDrift(), resolution.legacyBootstrapped());
        return resolution;
    }
}
