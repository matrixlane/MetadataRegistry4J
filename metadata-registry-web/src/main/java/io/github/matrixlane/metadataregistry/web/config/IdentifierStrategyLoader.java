package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierGenerationStrategyRegistry;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.SequenceGenerationStrategy;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.TemplateGenerationStrategy;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.service.SequenceRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentifierStrategyLoader implements CommandLineRunner {

    private final IdentifierStrategyConfigRepository configRepository;
    private final SequenceRegistryService sequenceRegistryService;
    private final IdentifierStrategyConfigService strategyConfigService;

    @Override
    public void run(String... args) {
        log.info("[IdentifierStrategyLoader] Initializing strategies and setting sequence provider");
        strategyConfigService.setSequenceProvider(sequenceRegistryService);
        refreshRegistry();
    }

    public void refreshRegistry() {
        IdentifierGenerationStrategyRegistry registry = IdentifierGenerationStrategyRegistry.getInstance();
        registry.clearCache();

        registry.register(new SequenceGenerationStrategy());
        registry.register(new TemplateGenerationStrategy());

        List<IdentifierStrategyConfig> activeConfigs = configRepository.findAllActiveConfigs();
        log.info("[IdentifierStrategyLoader] Found {} active strategy configs", activeConfigs.size());

        for (IdentifierStrategyConfig config : activeConfigs) {
            if (!config.isEffective()) {
                continue;
            }
            if (config.getIsGlobalDefault()) {
                registry.setGlobalDefault(config);
                log.info("[IdentifierStrategyLoader] Loaded global default strategy: strategy={}",
                        config.getStrategyType());
            } else if (config.getNamespace() != null) {
                registry.setNamespaceConfig(config.getNamespace().getId(), config);
                log.info("[IdentifierStrategyLoader] Loaded namespace strategy: namespaceId={}, strategy={}",
                        config.getNamespace().getId(), config.getStrategyType());
            }
        }
    }
}
