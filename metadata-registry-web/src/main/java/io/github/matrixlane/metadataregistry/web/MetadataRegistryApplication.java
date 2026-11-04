package io.github.matrixlane.metadataregistry.web;

import io.github.matrixlane.metadataregistry.itemmapping.matching.MatchConfigurationProperties;
import io.github.matrixlane.metadataregistry.web.bootstrap.BootstrapPhaseInitializer;
import io.github.matrixlane.metadataregistry.web.config.FreshDatabaseInitializer;
import io.github.matrixlane.metadataregistry.web.config.LocalConfigBootstrapInitializer;
import io.github.matrixlane.metadataregistry.web.inference.InferenceProperties;
import io.github.matrixlane.metadataregistry.web.service.DatasourceConfigService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.github.matrixlane.metadataregistry")
@EnableConfigurationProperties({MatchConfigurationProperties.class, InferenceProperties.class})
@EnableScheduling
public class MetadataRegistryApplication {

    public static void main(String[] args) {
        // 在 Spring Boot 准备 Environment 之前迁移旧版数据源配置，确保
        // config.import 加载到的是正确位置的 application-local.yml；
        // 同时剥离旧格式配置中的行为开关键（flyway.enabled / ddl-auto）。
        DatasourceConfigService.migrateLegacyConfigIfNeeded();
        // 三个启动初始化器（均不在 META-INF/spring/*.imports 注册：Boot 3.5
        // repackage 不迁移应用自身的 META-INF/spring 目录，可执行 jar 内不可见）：
        // 1. LocalConfigBootstrapInitializer——把用户保存的 application-local.yml
        //    提到 profile 默认值之上（spring.config.import 做不到这一点）；
        // 2. BootstrapPhaseInitializer——在 LocalConfig 之后判定启动相位
        //    （init-phase-database-free：数据库配置未确认前零数据库行为），
        //    并注入关闭 Flyway 自动配置 / 恒定 ddl-auto=none / 连接池不快速失败
        //    等属性；必须在 LocalConfig 之后（指纹依赖其加载的本地配置）；
        // 3. FreshDatabaseInitializer——识别全新空库（SQLite/SQL Server/达梦），
        //    仅完整相探测，结果供数据库初始化引导使用。
        new SpringApplicationBuilder(MetadataRegistryApplication.class)
                .initializers(new LocalConfigBootstrapInitializer(),
                        new BootstrapPhaseInitializer(),
                        new FreshDatabaseInitializer())
                .run(args);
    }
}
