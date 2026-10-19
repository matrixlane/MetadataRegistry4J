package io.github.matrixlane.metadataregistry.web;

import io.github.matrixlane.metadataregistry.itemmapping.matching.MatchConfigurationProperties;
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
        // 两个启动初始化器（均不在 META-INF/spring/*.imports 注册：Boot 3.5
        // repackage 不迁移应用自身的 META-INF/spring 目录，可执行 jar 内不可见）：
        // 1. LocalConfigBootstrapInitializer——把用户保存的 application-local.yml
        //    提到 profile 默认值之上（spring.config.import 做不到这一点）；
        // 2. FreshDatabaseInitializer——环境准备完毕、上下文未刷新，可在任何
        //    DDL 前识别全新 SQLite 空库并注入基线化属性。
        new SpringApplicationBuilder(MetadataRegistryApplication.class)
                .initializers(new LocalConfigBootstrapInitializer(), new FreshDatabaseInitializer())
                .run(args);
    }
}
