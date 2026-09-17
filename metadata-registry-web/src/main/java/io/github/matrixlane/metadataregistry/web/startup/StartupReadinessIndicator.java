package io.github.matrixlane.metadataregistry.web.startup;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用启动就绪指示器。
 *
 * <p>{@link ApplicationReadyEvent} 在 {@code SpringApplication.run()} 全部阶段
 * （含所有 ApplicationRunner / CommandLineRunner 数据初始化）完成后发布，
 * 此前置位 ready。启动为单向过程，ready 一旦置位不再回退。
 *
 * <p>前端启动等待界面通过 {@code GET /api/v1/startup/status} 轮询该状态。
 */
@Component
public class StartupReadinessIndicator {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        ready.set(true);
    }

    public boolean isReady() {
        return ready.get();
    }
}
