package io.github.matrixlane.metadataregistry.web.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * 让 Flyway 在 JPA ddl-auto 之后执行（配合 spring.jpa.defer-datasource-initialization）。
 *
 * <p>Spring Boot 3.5 的 DatabaseInitializationDependencyConfigurer 在
 * defer-datasource-initialization=true 时把 entityManagerFactory 与 flyway 互相登记
 * depends-on（两者都被探测为"数据库初始化方"），直接启动会报循环依赖。本后处理器在
 * Boot 的依赖登记之后运行，摘除 entityManagerFactory 对 flyway 的 depends-on、保留
 * flyway 对 entityManagerFactory 的依赖，从而固定顺序：
 *
 * <ol>
 *   <li>Hibernate ddl-auto 建表（全新库因此 schema 非空）；</li>
 *   <li>Flyway 以 baseline-on-migrate 基线化到既有最大版本号，只应用其后的
 *       带幂等守卫的新迁移（既有库表由 ddl-auto 历史创建，老迁移不可重放）。</li>
 * </ol>
 *
 * <p>仅在 defer-datasource-initialization 开启时生效；关闭时保持 Boot 默认
 * 顺序（Flyway 先于 JPA）。
 */
@Configuration
@ConditionalOnProperty(name = "spring.jpa.defer-datasource-initialization", havingValue = "true")
public class FlywayAfterJpaConfig {

    @Bean
    static BeanFactoryPostProcessor flywayAfterEntityManagerFactoryDependency() {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                if (!beanFactory.containsBeanDefinition("entityManagerFactory")) {
                    return;
                }
                BeanDefinition entityManagerFactory = beanFactory.getBeanDefinition("entityManagerFactory");
                String[] dependsOn = entityManagerFactory.getDependsOn();
                if (dependsOn == null) {
                    return;
                }
                String[] filtered = Arrays.stream(dependsOn)
                        .filter(name -> !name.equals("flyway")
                                && !name.equals("flywayInitializer")
                                && !name.equals("flywayMigrationInitializer"))
                        .toArray(String[]::new);
                entityManagerFactory.setDependsOn(filtered);
            }
        };
    }
}
