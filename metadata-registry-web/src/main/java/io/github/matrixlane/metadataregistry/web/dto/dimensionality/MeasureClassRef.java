package io.github.matrixlane.metadataregistry.web.dto.dimensionality;

import java.util.UUID;

/**
 * MeasureClass 引用（dimensionality_measure_class 关联）。
 */
public record MeasureClassRef(UUID id, String label) {
}
