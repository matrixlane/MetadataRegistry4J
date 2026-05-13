package io.github.matrixlane.metadataregistry.itemmapping.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing mapping subscriptions.
 *
 * <p>V1 supports in-app notifications only. Email/Webhook extensions
 * are reserved for future versions.
 */
@Service
@Transactional
public class MappingSubscriptionService {

    private final MappingSubscriptionRepository subscriptionRepository;

    public MappingSubscriptionService(MappingSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Subscribes a user to a mapping scheme.
     *
     * @param userId   the user identifier
     * @param schemeId the mapping scheme UUID
     * @return the created subscription
     */
    public MappingSubscription subscribeToScheme(String userId, UUID schemeId) {
        if (subscriptionRepository.existsByUserIdAndSubscriptionTypeAndTargetId(
                userId, SubscriptionType.SCHEME, schemeId)) {
            throw new IllegalArgumentException("Already subscribed to scheme: " + schemeId);
        }
        return subscriptionRepository.save(
                new MappingSubscription(userId, SubscriptionType.SCHEME, schemeId));
    }

    /**
     * Subscribes a user to a metadata item.
     *
     * @param userId the user identifier
     * @param itemId the metadata item UUID
     * @return the created subscription
     */
    public MappingSubscription subscribeToItem(String userId, UUID itemId) {
        if (subscriptionRepository.existsByUserIdAndSubscriptionTypeAndTargetId(
                userId, SubscriptionType.ITEM, itemId)) {
            throw new IllegalArgumentException("Already subscribed to item: " + itemId);
        }
        return subscriptionRepository.save(
                new MappingSubscription(userId, SubscriptionType.ITEM, itemId));
    }

    /**
     * Unsubscribes a user from a specific target.
     */
    public void unsubscribe(String userId, SubscriptionType type, UUID targetId) {
        Optional<MappingSubscription> existing = subscriptionRepository
                .findByUserIdAndSubscriptionTypeAndTargetId(userId, type, targetId);
        existing.ifPresent(subscriptionRepository::delete);
    }

    /**
     * Lists all subscriptions for a user.
     */
    @Transactional(readOnly = true)
    public List<MappingSubscription> getUserSubscriptions(String userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    /**
     * Finds subscribers for a list of scheme IDs.
     */
    @Transactional(readOnly = true)
    public List<MappingSubscription> findSchemeSubscribers(List<UUID> schemeIds) {
        if (schemeIds == null || schemeIds.isEmpty()) {
            return List.of();
        }
        return subscriptionRepository.findSchemeSubscriptionsByTargetIds(schemeIds);
    }

    /**
     * Finds subscribers for a list of item IDs.
     */
    @Transactional(readOnly = true)
    public List<MappingSubscription> findItemSubscribers(List<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        return subscriptionRepository.findItemSubscriptionsByTargetIds(itemIds);
    }
}
