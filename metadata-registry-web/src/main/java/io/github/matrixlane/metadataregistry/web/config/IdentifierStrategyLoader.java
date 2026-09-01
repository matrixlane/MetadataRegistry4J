package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.ext.identification.IdentifierGenerationStrategyRegistry;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfigService;
import io.github.matrixlane.metadataregistry.ext.identification.SequenceGenerationStrategy;
import io.github.matrixlane.metadataregistry.ext.identification.TemplateGenerationStrategy;
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
            // 存量模板归一化提示：显式 scopeRefs 与 RANDOM 段已废止——生成期
            // 作用域由引擎按模板结构自动推导（显式 scopeRefs 被忽略），
            // 含 RANDOM 段的配置在下次编辑/发布时会被校验拒绝
            if (config.getConfigJson() != null) {
                if (config.getConfigJson().contains("\"scopeRefs\"")) {
                    log.warn("[IdentifierStrategyLoader] 命名空间策略配置含已废止的 scopeRefs，"
                            + "作用域将按模板结构自动推导: namespaceId={}",
                            config.getNamespace() != null ? config.getNamespace().getId() : null);
                }
                if (config.getConfigJson().contains("\"RANDOM\"")) {
                    log.warn("[IdentifierStrategyLoader] 命名空间策略配置含已移除的 RANDOM 段，"
                            + "重新发布前需调整模板: namespaceId={}",
                            config.getNamespace() != null ? config.getNamespace().getId() : null);
                }
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
