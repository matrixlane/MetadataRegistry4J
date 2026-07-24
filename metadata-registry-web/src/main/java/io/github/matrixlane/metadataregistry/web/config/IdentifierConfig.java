package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentifierConfig {

    @Bean
    public IdentifierStrategyConfigService identifierStrategyConfigService() {
        return new IdentifierStrategyConfigService();
    }
}
