package io.github.matrixlane.metadataregistry.web.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {
        "io.github.matrixlane.metadataregistry.commonfacilities",
        "io.github.matrixlane.metadataregistry.dataspecification",
        "io.github.matrixlane.metadataregistry.web.auth"
})
@EnableJpaRepositories(basePackages = {
        "io.github.matrixlane.metadataregistry.web.repository",
        "io.github.matrixlane.metadataregistry.web.auth"
})
public class JpaConfig {
}
