package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.service.RegistryContextBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动期补建 Registry Context。
 *
 * <p>Registry Context 是注册系统固有数据（不是测试种子），因此不挂
 * {@code app.seed.enabled} 开关；仅在系统已初始化且 Registry Context 缺失时
 * 执行补建，避免在未配置标识/版本策略的未初始化系统启动期提前创建。
 *
 * <p>顺序为 101：先于 {@link IdentifierStrategyLoader}（90，装载标识策略注册表）之后
 * 执行，确保补建时按命名空间策略生成标识（而非 UUID 回退）；也排在
 * {@link ContextTestDataInitializer}（100）之后，确保测试种子按"表非空即跳过"
 * 逻辑执行后，再判断是否需要补建系统默认上下文。
 */
@Component
@Order(101)
@RequiredArgsConstructor
public class RegistryContextBackfillRunner implements ApplicationRunner {

    private final RegistryContextBootstrapService registryContextBootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        registryContextBootstrapService.backfillIfInitialized();
    }
}
