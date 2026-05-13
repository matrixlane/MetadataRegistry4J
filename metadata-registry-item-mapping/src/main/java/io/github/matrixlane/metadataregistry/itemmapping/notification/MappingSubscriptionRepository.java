package io.github.matrixlane.metadataregistry.itemmapping.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link MappingSubscription} entities.
 */
@Repository
public interface MappingSubscriptionRepository extends JpaRepository<MappingSubscription, UUID> {

    /**
     * Finds all subscriptions for a given user.
     */
    List<MappingSubscription> findByUserId(String userId);

    /**
     * Finds subscriptions by type and target ID.
     */
    List<MappingSubscription> findBySubscriptionTypeAndTargetId(SubscriptionType type, UUID targetId);

    /**
     * Finds a specific subscription by user, type, and target.
     */
    Optional<MappingSubscription> findByUserIdAndSubscriptionTypeAndTargetId(
            String userId, SubscriptionType type, UUID targetId);

    /**
     * Checks whether a subscription exists.
     */
    boolean existsByUserIdAndSubscriptionTypeAndTargetId(
            String userId, SubscriptionType type, UUID targetId);

    /**
     * Finds all scheme-level subscriptions for a list of scheme IDs.
     */
    @Query("SELECT s FROM MappingSubscription s WHERE s.subscriptionType = 'SCHEME' AND s.targetId IN :schemeIds")
    List<MappingSubscription> findSchemeSubscriptionsByTargetIds(@Param("schemeIds") List<UUID> schemeIds);

    /**
     * Finds all item-level subscriptions for a list of item IDs.
     */
    @Query("SELECT s FROM MappingSubscription s WHERE s.subscriptionType = 'ITEM' AND s.targetId IN :itemIds")
    List<MappingSubscription> findItemSubscriptionsByTargetIds(@Param("itemIds") List<UUID> itemIds);
}
