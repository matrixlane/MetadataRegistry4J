package io.github.matrixlane.metadataregistry.web.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 让 {@code FlywayHistoryRelocator} 在 Flyway 迁移之前执行（配合
 * {@link FlywayAfterJpaConfig} 固定的 EMF → Flyway 顺序）。
 *
 * <p>Spring Boot 没有为"Flyway 之前的自定义数据库步骤"提供顺序挂钩；本后处理器
 * 在 Boot 的依赖登记之后运行，把 {@code flywayHistoryRelocator} 并入
 * {@code FlywayMigrationInitializer}（Boot 3.5 的 bean 名为
 * {@code flywayInitializer}，经 {@link #getBeanNamesForType 类型查找}而非字面量，
 * 避免 Boot 升版后静默漂移）的 dependsOn——其 {@code afterPropertiesSet}
 * 触发 {@code flyway.migrate()}，从而保证 schema 历史表搬迁先于 Flyway 的
 * 任何探测：否则既有非空库会被 re-baseline 并重放全部基线后迁移。
 *
 * <p>flyway 关闭（如 local profile，{@code spring.flyway.enabled=false}）时
 * {@code FlywayMigrationInitializer} bean 不存在，自然跳过。
 */
@Configuration
public class FlywayHistoryRelocationConfig {

    @Bean
    static BeanFactoryPostProcessor flywayHistoryRelocationDependency() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                for (String initializerName : beanFactory
                        .getBeanNamesForType(FlywayMigrationInitializer.class, false, false)) {
                    BeanDefinition initializer = beanFactory.getBeanDefinition(initializerName);
                    Set<String> dependencies = new LinkedHashSet<>();
                    dependencies.add("flywayHistoryRelocator");
                    String[] dependsOn = initializer.getDependsOn();
                    if (dependsOn != null) {
                        dependencies.addAll(Arrays.asList(dependsOn));
                    }
                    initializer.setDependsOn(dependencies.toArray(String[]::new));
                }
            }
        };
    }
}
