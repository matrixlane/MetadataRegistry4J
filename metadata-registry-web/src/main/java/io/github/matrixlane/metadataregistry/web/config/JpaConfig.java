package io.github.matrixlane.metadataregistry.web.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {
        "io.github.matrixlane.metadataregistry"
})
@EnableJpaRepositories(basePackages = {
        "io.github.matrixlane.metadataregistry"
})
public class JpaConfig {
}
