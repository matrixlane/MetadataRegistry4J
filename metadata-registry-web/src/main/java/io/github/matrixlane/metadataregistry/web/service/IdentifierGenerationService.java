package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierGenerationStrategy;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.UrnGenerationStrategy;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.UuidGenerationStrategy;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configurable identifier generation service.
 *
 * <p>Supports UUID and URN strategies. Strategy selection is organisation-level;
 * historical identifiers remain immutable.
 */
@Service
public class IdentifierGenerationService {

    private final Map<String, IdentifierGenerationStrategy> strategies = new ConcurrentHashMap<>();

    private volatile String activeType = "UUID";

    public IdentifierGenerationService() {
        strategies.put("UUID", new UuidGenerationStrategy());
        strategies.put("URN", new UrnGenerationStrategy());
    }

    /**
     * Generates a new identifier using the currently active strategy.
     */
    public String generate(Namespace namespace) {
        IdentifierGenerationStrategy strategy = strategies.get(activeType);
        if (strategy == null) {
            throw new BusinessException("Unknown identifier generation strategy: " + activeType);
        }
        return strategy.generate(namespace);
    }

    /**
     * Switches the active strategy (affects newly created identifiers only).
     */
    public void setActiveStrategy(String type) {
        if (!strategies.containsKey(type)) {
            throw new BusinessException("Unsupported strategy: " + type);
        }
        this.activeType = type;
    }

    public String getActiveStrategyType() {
        return activeType;
    }
}
