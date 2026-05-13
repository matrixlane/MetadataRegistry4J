package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.notification.SubscriptionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating a mapping subscription.
 */
public record SubscriptionRequest(
        @NotBlank String userId,
        @NotNull SubscriptionType subscriptionType,
        @NotNull UUID targetId
) {
}
