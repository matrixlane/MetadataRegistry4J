package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 初始化状态提供者。
 *
 * <p>"系统是否已初始化"与"部署版本"的判定入口。{@code app.bootstrap.enabled=false}
 * （集成测试等自行管理 schema 的环境）时退化为 {@link SystemSettingService} 的数据库
 * 判定，维持变更前行为；开启时以本地初始化状态记录为权威来源——向导相因此
 * 零数据库查询（init-phase-database-free 的"状态不来自数据库"决策）。
 *
 * <p>状态记录按 mtime 缓存（见 {@link InitializationStateStore}），逐请求调用
 * 不会重复解析文件。
 */
@Component
public class InitializationStatusProvider {

    private final SystemSettingService systemSettingService;
    private final InitializationStateStore stateStore;
    private final Environment environment;
    private final boolean bootstrapEnabled;
    private final BootstrapPhase phase;

    @org.springframework.beans.factory.annotation.Autowired
    public InitializationStatusProvider(SystemSettingService systemSettingService,
                                        Environment environment) {
        this(systemSettingService, environment, new InitializationStateStore());
    }

    /** 显式状态存储的构造（测试与特殊装配用）。 */
    public InitializationStatusProvider(SystemSettingService systemSettingService,
                                        Environment environment,
                                        InitializationStateStore stateStore) {
        this.systemSettingService = systemSettingService;
        this.stateStore = stateStore;
        this.environment = environment;
        this.bootstrapEnabled = environment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_ENABLED, Boolean.class, false);
        this.phase = parsePhase(environment.getProperty(BootstrapPhaseInitializer.PROPERTY_PHASE));
    }

    private static BootstrapPhase parsePhase(String value) {
        return "full".equalsIgnoreCase(value) ? BootstrapPhase.FULL : BootstrapPhase.WIZARD;
    }

    public boolean bootstrapEnabled() {
        return bootstrapEnabled;
    }

    public BootstrapPhase phase() {
        return phase;
    }

    public boolean isInitialized() {
        if (!bootstrapEnabled) {
            return systemSettingService.isInitialized();
        }
        return stateStore.read().map(InitializationState::initialized).orElse(false);
    }

    /**
     * 当前生效数据源是否已确认：状态记录 {@code datasourceConfigured=true} 且
     * 记录指纹与当前有效配置指纹一致（指纹算法与键集和
     * {@link BootstrapPhaseResolver#currentFingerprint} 相同）。
     *
     * <p>向导相第 2 步保存数据源并自动重启成功后即处于该状态——此时"保持当前
     * 数据源并继续"必须直接放行（restartRequired=false），否则每次点击下一步
     * 都会再触发一轮重启，向导永远走不出第 2 步（2026-10-27 事故）。
     * 配置漂移（指纹失配）不算已确认：走重启事务重新确认。
     *
     * <p>总开关关闭（集成测试等无状态记录环境）恒返回 false，维持旧行为
     * （confirm-current 一律创建重启事务）。
     */
    public boolean isDatasourceConfirmed() {
        if (!bootstrapEnabled) {
            return false;
        }
        InitializationState state = stateStore.read().orElse(null);
        if (state == null || !state.datasourceConfigured()) {
            return false;
        }
        String current = new BootstrapPhaseResolver()
                .currentFingerprint((ConfigurableEnvironment) environment);
        return current.equals(state.datasourceFingerprint());
    }

    /**
     * 当前部署版本；未初始化（或旧部署未写入）时返回 null。
     */
    public DeploymentEdition getEdition() {
        if (!bootstrapEnabled) {
            return systemSettingService.getEdition();
        }
        return stateStore.read()
                .map(InitializationState::edition)
                .filter(value -> value != null && !value.isBlank())
                .map(DeploymentEdition::valueOf)
                .orElse(null);
    }
}
