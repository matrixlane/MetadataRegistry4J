package io.github.matrixlane.metadataregistry.web;

import io.github.matrixlane.metadataregistry.itemmapping.matching.MatchConfigurationProperties;
import io.github.matrixlane.metadataregistry.web.inference.InferenceProperties;
import io.github.matrixlane.metadataregistry.web.service.DatasourceConfigService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.github.matrixlane.metadataregistry")
@EnableConfigurationProperties({MatchConfigurationProperties.class, InferenceProperties.class})
@EnableScheduling
public class MetadataRegistryApplication {

    public static void main(String[] args) {
        // 在 Spring Boot 准备 Environment 之前迁移旧版数据源配置，确保
        // config.import 加载到的是正确位置的 application-local.yml。
        DatasourceConfigService.migrateLegacyConfigIfNeeded();
        SpringApplication.run(MetadataRegistryApplication.class, args);
    }
}
