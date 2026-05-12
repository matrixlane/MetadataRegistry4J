package io.github.matrixlane.metadataregistry.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MetadataRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetadataRegistryApplication.class, args);
    }
}
