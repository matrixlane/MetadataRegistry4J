package io.github.matrixlane.metadataregistry.web.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据库初始化引导的启动期入口（init-phase-database-free）。
 *
 * <p>总开关开启且数据源就绪（完整相，或向导相已确认数据源）时调用
 * {@link DatabaseBootstrapService#bootstrap()}；数据源未就绪（向导配置数据库
 * 之前）与关闭时什么都不做。其余数据库 Runner（修复/预置/策略/回填）通过
 * {@link BootstrapMode} 在同一情形下跳过——数据库动作因此只有引导这一条路径。
 */
@Component
@Order(1)
@Slf4j
public class DatabaseBootstrapRunner implements ApplicationRunner {

    private final BootstrapMode mode;
    private final DatabaseBootstrapService bootstrapService;

    public DatabaseBootstrapRunner(BootstrapMode mode, DatabaseBootstrapService bootstrapService) {
        this.mode = mode;
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!mode.enabled() || !mode.datasourceReady()) {
            return;
        }
        BootstrapOutcome outcome = bootstrapService.bootstrap();
        if (!outcome.ready()) {
            log.error("数据库初始化引导失败：{}（{}）——应用保持运行，请在界面查看原因或重试",
                    outcome.failure().message(), outcome.failure().code());
        }
    }
}
