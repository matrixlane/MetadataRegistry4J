package io.github.matrixlane.metadataregistry.web.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.matrixlane.metadataregistry.web.service.DatasourceConfigService;

/**
 * 本地保存配置（application-local.yml）启动期加载（sqlite-empty-db-bootstrap）。
 *
 * <p>用户保存的数据源配置必须优先于各 profile 默认值生效，但不能用
 * {@code spring.config.import} 实现：profile 特定文档内声明的 import 不生效，
 * 而声明在 application.yml 中又会被 profile 文档覆盖（profile 文档优先级更高）。
 * 本初始化器在上下文刷新前显式解析该 YAML，并把属性源插入到
 * {@code systemEnvironment} 之后、一切 config data 之前：
 *
 * <pre>
 *   命令行 &gt; 系统属性/环境变量 &gt; application-local.yml（本类） &gt; profile 默认 &gt; application.yml
 * </pre>
 *
 * <p>与 {@link FreshDatabaseInitializer} 配合：本类保证"保存的配置重启后生效"，
 * FreshDatabaseInitializer 再对全新空库叠加基线化覆盖（两者键不冲突）。
 * 文件不存在时静默跳过；解析失败仅告警，保持启动（与 optional: import 语义一致）。
 * 路径解析与 {@link DatasourceConfigService} 共享（支持
 * {@code metadata.registry.config.file} 系统属性覆盖，便于测试）。
 */
@Slf4j
public class LocalConfigBootstrapInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        apply(applicationContext.getEnvironment());
    }

    /**
     * 加载本地配置并注入高优先级属性源。单独抽出便于测试直接调用。
     */
    public void apply(ConfigurableEnvironment environment) {
        Path localConfig = DatasourceConfigService.localConfigPath();
        if (!Files.exists(localConfig)) {
            return;
        }
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources;
        try {
            sources = loader.load("mdr-local-config", new FileSystemResource(localConfig));
        } catch (IOException e) {
            log.warn("本地配置 {} 解析失败（保持忽略）: {}", localConfig, e.getMessage());
            return;
        }
        if (sources.isEmpty()) {
            return;
        }
        // 逐个插到 systemEnvironment 之后：后插入者更靠前（更高优先级），
        // 多文档时维持文档顺序（第一文档优先级最高，与 config data 行为一致）。
        for (int i = sources.size() - 1; i >= 0; i--) {
            environment.getPropertySources().addAfter(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, sources.get(i));
        }
        log.info("已加载本地配置 {}（{} 个文档），优先级高于 profile 默认值", localConfig, sources.size());
    }
}
