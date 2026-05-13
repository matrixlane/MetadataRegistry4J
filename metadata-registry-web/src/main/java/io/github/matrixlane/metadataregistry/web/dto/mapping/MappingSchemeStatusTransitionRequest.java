package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for mapping scheme status transitions.
 *
 * <p>Supported transitions:
 * <ul>
 *   <li>{@link MappingSchemeStatus#PUBLISHED} — transition from DRAFT</li>
 *   <li>{@link MappingSchemeStatus#ARCHIVED} — transition from PUBLISHED</li>
 * </ul>
 */
public record MappingSchemeStatusTransitionRequest(

        @NotNull(message = "目标状态不能为空")
        MappingSchemeStatus to
) {
}
