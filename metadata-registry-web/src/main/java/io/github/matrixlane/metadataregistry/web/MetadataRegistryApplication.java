package io.github.matrixlane.metadataregistry.web;

import io.github.matrixlane.metadataregistry.itemmapping.matching.MatchConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.github.matrixlane.metadataregistry")
@EnableConfigurationProperties(MatchConfigurationProperties.class)
@EnableScheduling
public class MetadataRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetadataRegistryApplication.class, args);
    }
}
