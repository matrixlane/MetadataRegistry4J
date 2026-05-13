package io.github.matrixlane.metadataregistry.itemmapping.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingSubscriptionServiceTest {

    @Mock
    private MappingSubscriptionRepository repository;

    private MappingSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new MappingSubscriptionService(repository);
    }

    @Test
    void subscribeToScheme_shouldCreateSubscription() {
        String userId = "user-1";
        UUID schemeId = UUID.randomUUID();
        MappingSubscription expected = new MappingSubscription(userId, SubscriptionType.SCHEME, schemeId);

        when(repository.existsByUserIdAndSubscriptionTypeAndTargetId(userId, SubscriptionType.SCHEME, schemeId))
                .thenReturn(false);
        when(repository.save(any(MappingSubscription.class))).thenReturn(expected);

        MappingSubscription result = service.subscribeToScheme(userId, schemeId);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getSubscriptionType()).isEqualTo(SubscriptionType.SCHEME);
        assertThat(result.getTargetId()).isEqualTo(schemeId);
    }

    @Test
    void subscribeToScheme_alreadySubscribed_shouldThrow() {
        String userId = "user-1";
        UUID schemeId = UUID.randomUUID();

        when(repository.existsByUserIdAndSubscriptionTypeAndTargetId(userId, SubscriptionType.SCHEME, schemeId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.subscribeToScheme(userId, schemeId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Already subscribed");
    }

    @Test
    void subscribeToItem_shouldCreateSubscription() {
        String userId = "user-1";
        UUID itemId = UUID.randomUUID();
        MappingSubscription expected = new MappingSubscription(userId, SubscriptionType.ITEM, itemId);

        when(repository.existsByUserIdAndSubscriptionTypeAndTargetId(userId, SubscriptionType.ITEM, itemId))
                .thenReturn(false);
        when(repository.save(any(MappingSubscription.class))).thenReturn(expected);

        MappingSubscription result = service.subscribeToItem(userId, itemId);

        assertThat(result.getSubscriptionType()).isEqualTo(SubscriptionType.ITEM);
        assertThat(result.getTargetId()).isEqualTo(itemId);
    }

    @Test
    void unsubscribe_shouldDeleteSubscription() {
        String userId = "user-1";
        UUID targetId = UUID.randomUUID();
        MappingSubscription existing = new MappingSubscription(userId, SubscriptionType.SCHEME, targetId);

        when(repository.findByUserIdAndSubscriptionTypeAndTargetId(userId, SubscriptionType.SCHEME, targetId))
                .thenReturn(Optional.of(existing));

        service.unsubscribe(userId, SubscriptionType.SCHEME, targetId);

        verify(repository).delete(existing);
    }

    @Test
    void unsubscribe_notSubscribed_shouldDoNothing() {
        String userId = "user-1";
        UUID targetId = UUID.randomUUID();

        when(repository.findByUserIdAndSubscriptionTypeAndTargetId(userId, SubscriptionType.SCHEME, targetId))
                .thenReturn(Optional.empty());

        service.unsubscribe(userId, SubscriptionType.SCHEME, targetId);

        verify(repository, never()).delete(any());
    }

    @Test
    void getUserSubscriptions_shouldReturnList() {
        String userId = "user-1";
        MappingSubscription sub1 = new MappingSubscription(userId, SubscriptionType.SCHEME, UUID.randomUUID());
        MappingSubscription sub2 = new MappingSubscription(userId, SubscriptionType.ITEM, UUID.randomUUID());

        when(repository.findByUserId(userId)).thenReturn(List.of(sub1, sub2));

        List<MappingSubscription> result = service.getUserSubscriptions(userId);

        assertThat(result).hasSize(2);
    }

    @Test
    void findSchemeSubscribers_shouldReturnMatchingSubscriptions() {
        UUID schemeId = UUID.randomUUID();
        MappingSubscription sub = new MappingSubscription("user-1", SubscriptionType.SCHEME, schemeId);

        when(repository.findSchemeSubscriptionsByTargetIds(List.of(schemeId)))
                .thenReturn(List.of(sub));

        List<MappingSubscription> result = service.findSchemeSubscribers(List.of(schemeId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo("user-1");
    }

    @Test
    void findItemSubscribers_shouldReturnMatchingSubscriptions() {
        UUID itemId = UUID.randomUUID();
        MappingSubscription sub = new MappingSubscription("user-1", SubscriptionType.ITEM, itemId);

        when(repository.findItemSubscriptionsByTargetIds(List.of(itemId)))
                .thenReturn(List.of(sub));

        List<MappingSubscription> result = service.findItemSubscribers(List.of(itemId));

        assertThat(result).hasSize(1);
    }

    @Test
    void findItemSubscribers_emptyList_shouldReturnEmpty() {
        List<MappingSubscription> result = service.findItemSubscribers(List.of());

        assertThat(result).isEmpty();
        verify(repository, never()).findItemSubscriptionsByTargetIds(any());
    }
}
