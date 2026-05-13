package io.github.matrixlane.metadataregistry.itemmapping.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a user's subscription to mapping changes.
 *
 * <p>Users can subscribe by mapping scheme or by individual metadata item.
 * When a relevant change occurs, an in-app notification is generated.
 *
 * <p>V1: In-app notifications only. Email/Webhook extensions are reserved for future versions.
 */
@Entity
@Table(name = "mapping_subscription")
@Getter
@Setter
@NoArgsConstructor
public class MappingSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * User identifier of the subscriber.
     * V1 uses simple string IDs; future versions may reference a User entity.
     */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * Type of subscription: by scheme or by item.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_type", nullable = false, length = 16)
    private SubscriptionType subscriptionType;

    /**
     * The target ID: either a mapping scheme UUID or an item UUID.
     */
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    /**
     * When the subscription was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MappingSubscription(String userId, SubscriptionType subscriptionType, UUID targetId) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.subscriptionType = Objects.requireNonNull(subscriptionType, "subscriptionType must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
        this.createdAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MappingSubscription that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }
}
