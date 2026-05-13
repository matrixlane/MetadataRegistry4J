package io.github.matrixlane.metadataregistry.web.dto.interopemapping;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for manually remapping an interop element to a different local item.
 */
public record ManualMapRequest(

        @NotNull
        UUID localItemId
) {
}
