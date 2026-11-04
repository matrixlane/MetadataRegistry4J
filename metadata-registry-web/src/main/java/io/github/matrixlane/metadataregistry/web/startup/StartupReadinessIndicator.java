package io.github.matrixlane.metadataregistry.web.startup;

import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapFailure;
import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapOutcome;
import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer;
import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapProgress;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用启动就绪指示器。
 *
 * <p>{@link ApplicationReadyEvent} 在 {@code SpringApplication.run()} 全部阶段
 * （含所有 ApplicationRunner / CommandLineRunner 数据初始化）完成后发布，
 * 此前置位"上下文就绪"。启动等待界面通过 {@code GET /api/v1/startup/status} 轮询。
 *
 * <p>init-phase-database-free 之后，最终就绪还取决于数据源就绪状态与引导结论：
 * <ul>
 *   <li>总开关关闭（集成测试等）：维持原行为——上下文就绪即 ready；</li>
 *   <li>数据源未就绪（向导配置数据库之前）：零数据库引导，上下文就绪即 ready；</li>
 *   <li>数据源就绪（完整相，或向导相已确认数据源）：数据库初始化引导成功才
 *       ready；失败则 ready=false 并经 {@link #failure()} 暴露原因，等待用户
 *       确认退出或重试。</li>
 * </ul>
 */
@Component
public class StartupReadinessIndicator {

    private final AtomicBoolean contextReady = new AtomicBoolean(false);
    private final Environment environment;
    private final BootstrapProgress progress;

    public StartupReadinessIndicator(Environment environment, BootstrapProgress progress) {
        this.environment = environment;
        this.progress = progress;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        contextReady.set(true);
    }

    public boolean isReady() {
        if (!contextReady.get()) {
            return false;
        }
        if (!bootstrapEnabled() || !datasourceReady()) {
            return true;
        }
        return progress.outcome().ready();
    }

    /** 数据源就绪态引导失败的原因；其余情形返回 null。 */
    public BootstrapFailure failure() {
        if (!bootstrapEnabled() || !datasourceReady()) {
            return null;
        }
        BootstrapOutcome outcome = progress.outcome();
        return outcome.ready() ? null : outcome.failure();
    }

    private boolean bootstrapEnabled() {
        return environment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_ENABLED, Boolean.class, false);
    }

    private boolean datasourceReady() {
        return environment.getProperty(
                BootstrapPhaseInitializer.PROPERTY_DATASOURCE_READY, Boolean.class, false);
    }
}
