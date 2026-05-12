package io.github.matrixlane.metadataregistry.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "io.github.matrixlane.metadataregistry.commonfacilities",
        "io.github.matrixlane.metadataregistry.dataspecification",
        "io.github.matrixlane.metadataregistry.web.auth"
})
@EnableJpaRepositories(basePackages = {
        "io.github.matrixlane.metadataregistry.web.repository",
        "io.github.matrixlane.metadataregistry.web.auth"
})
public class MetadataRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetadataRegistryApplication.class, args);
    }
}
