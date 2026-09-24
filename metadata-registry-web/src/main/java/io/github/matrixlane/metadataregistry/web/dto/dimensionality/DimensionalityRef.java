package io.github.matrixlane.metadataregistry.web.dto.dimensionality;

import java.util.UUID;

/**
 * Dimensionality 引用( dimensionality_measure_class)。
 */
public record DimensionalityRef(UUID id, String label) {
}
