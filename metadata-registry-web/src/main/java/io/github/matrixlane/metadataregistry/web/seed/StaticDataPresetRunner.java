package io.github.matrixlane.metadataregistry.web.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 静态预置数据启动期执行器（wizard-static-preset-bootstrap）。
 *
 * <p>在 Flyway 迁移之后、{@code IdentifierStrategyLoader}（@Order(90)）之前
 * 执行 {@link StaticDataPresetService#presetAll()}，保证"数据库设置完成"（重启）
 * 后全部静态预置数据就绪。幂等，可随每次启动安全执行。</p>
 *
 * <p>顺序正确性依赖 Spring Boot ≥ 3.4 的 Runner 统一排序模型（ApplicationRunner
 * 与 CommandLineRunner 按 @Order 混排）；本项目 Boot 3.5.0 满足。</p>
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class StaticDataPresetRunner implements ApplicationRunner {

    private final StaticDataPresetService staticDataPresetService;

    @Override
    public void run(ApplicationArguments args) {
        staticDataPresetService.presetAll();
    }
}
