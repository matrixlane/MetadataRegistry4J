package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.FlywayHistoryRelocator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Spring 上下文接线测试（钉死两类回归）:
 * <ul>
 *   <li>BFPP 必须能在真实 Boot 自动配置下把 {@code flywayHistoryRelocator}
 *       并入 {@code FlywayMigrationInitializer} 的 dependsOn——回归:曾按字面量
 *       {@code flywayMigrationInitializer}(Boot 3.5 实际 bean 名为
 *       {@code flywayInitializer})查找导致静默 no-op;</li>
 *   <li>{@code spring.flyway.default-schema} 属性绑定路径有效。</li>
 * </ul>
 * 数据源为内存 SQLite、迁移位置指向不存在目录（Flyway 无迁移可跑,不触发表结构）;
 * 搬运器以 SQLSERVER 环境 bean 注入,探测阶段对 SQLite 元数据返回空 → no-op。
 */
class FlywayHistoryRelocationWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
            .withUserConfiguration(WiringConfig.class)
            .withPropertyValues(
                    "spring.flyway.default-schema=main",
                    "spring.flyway.locations=classpath:db/migration/none");

    @Test
    @DisplayName("flywayInitializer dependsOn 含搬运器 + default-schema 绑定生效")
    void flywayInitializerDependsOnRelocator() {
        contextRunner.run(context -> {
            if (context.getStartupFailure() != null) {
                System.out.println("DEFS: "
                        + java.util.Arrays.toString(context.getSourceApplicationContext().getBeanDefinitionNames()));
            }
            assertThat(context).hasSingleBean(Flyway.class);
            assertThat(context.getBean(Flyway.class).getConfiguration().getDefaultSchema())
                    .isEqualTo("main");

            String[] initializerNames = context.getBeanNamesForType(FlywayMigrationInitializer.class);
            assertThat(initializerNames).hasSize(1);
            assertThat(context.getSourceApplicationContext().getBeanFactory()
                    .getBeanDefinition(initializerNames[0]).getDependsOn())
                    .as("FlywayMigrationInitializer[%s] 应先创建 flywayHistoryRelocator", initializerNames[0])
                    .contains("flywayHistoryRelocator");

            assertThat(context).hasSingleBean(FlywayHistoryRelocator.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(FlywayHistoryRelocationConfig.class)
    static class WiringConfig {

        @Bean
        DataSource dataSource() {
            SQLiteDataSource dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite::memory:");
            return dataSource;
        }

        @Bean
        DatabaseEnvironment databaseEnvironment() {
            return new DatabaseEnvironment("SQLSERVER", "", null, null);
        }

        @Bean
        FlywayHistoryRelocator flywayHistoryRelocator(DataSource dataSource,
                DatabaseEnvironment databaseEnvironment,
                org.springframework.beans.factory.ObjectProvider<Flyway> flyway) {
            return new FlywayHistoryRelocator(dataSource, databaseEnvironment, flyway);
        }
    }
}
