package io.github.matrixlane.metadataregistry.itemmapping.notification;

/**
 * Type of mapping subscription.
 */
public enum SubscriptionType {
    /** Subscribe to all changes within a mapping scheme. */
    SCHEME,
    /** Subscribe to changes related to a specific metadata item. */
    ITEM
}
