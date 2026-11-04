package io.github.matrixlane.metadataregistry.web.bootstrap;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 新架构运行模式（init-phase-database-free 总开关）。供启动期各 Runner 判断：
 * 开启时数据库初始化由 {@link DatabaseBootstrapService} 统一执行，其余 Runner
 * MUST 跳过，避免同一动作执行两次。
 */
@Component
public class BootstrapMode {

    private final Environment environment;

    public BootstrapMode(Environment environment) {
        this.environment = environment;
    }

    public boolean enabled() {
        return environment.getProperty(BootstrapPhaseInitializer.PROPERTY_ENABLED, Boolean.class, false);
    }

    public BootstrapPhase phase() {
        return phaseOf(environment);
    }

    /**
     * 数据源是否就绪（连接池指向真实库）：完整相恒真；向导相以
     * {@link BootstrapPhaseInitializer#PROPERTY_DATASOURCE_READY} 为准
     * （状态记录已确认且指纹一致时为真）。
     */
    public boolean datasourceReady() {
        return environment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, Boolean.class, false);
    }

    /** 从环境读取相位的静态判定（供不持有本组件的调用方使用）。 */
    public static BootstrapPhase phaseOf(Environment environment) {
        return "full".equalsIgnoreCase(
                environment.getProperty(BootstrapPhaseInitializer.PROPERTY_PHASE, ""))
                ? BootstrapPhase.FULL
                : BootstrapPhase.WIZARD;
    }
}
