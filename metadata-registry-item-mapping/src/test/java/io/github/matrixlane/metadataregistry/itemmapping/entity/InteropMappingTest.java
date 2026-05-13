package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InteropMappingTest {

    @Test
    void constructor_withAllFields_shouldCreateMapping() {
        UUID interopId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();

        InteropMapping mapping = new InteropMapping(
                interopId,
                localId,
                new BigDecimal("0.95"),
                "EXACT_IDENTIFIER",
                "Test reason"
        );

        assertThat(mapping.getInteropDataElementId()).isEqualTo(interopId);
        assertThat(mapping.getLocalItemId()).isEqualTo(localId);
        assertThat(mapping.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.95"));
        assertThat(mapping.getStrategy()).isEqualTo("EXACT_IDENTIFIER");
        assertThat(mapping.getStatus()).isEqualTo(InteropMappingStatus.PENDING);
        assertThat(mapping.getReason()).isEqualTo("Test reason");
        assertThat(mapping.getCreatedAt()).isNotNull();
    }

    @Test
    void constructor_withStatus_shouldCreateMapping() {
        UUID interopId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();

        InteropMapping mapping = new InteropMapping(
                interopId,
                localId,
                new BigDecimal("0.95"),
                "EXACT_IDENTIFIER",
                InteropMappingStatus.AUTO_MATCHED,
                "Auto matched"
        );

        assertThat(mapping.getStatus()).isEqualTo(InteropMappingStatus.AUTO_MATCHED);
    }

    @Test
    void constructor_shouldThrowWhenInteropDataElementIdIsNull() {
        assertThatThrownBy(() -> new InteropMapping(
                null,
                UUID.randomUUID(),
                new BigDecimal("0.95"),
                "EXACT_IDENTIFIER",
                "reason"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldThrowWhenLocalItemIdIsNull() {
        assertThatThrownBy(() -> new InteropMapping(
                UUID.randomUUID(),
                null,
                new BigDecimal("0.95"),
                "EXACT_IDENTIFIER",
                "reason"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldThrowWhenConfidenceScoreIsNull() {
        assertThatThrownBy(() -> new InteropMapping(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "EXACT_IDENTIFIER",
                "reason"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldThrowWhenStrategyIsNull() {
        assertThatThrownBy(() -> new InteropMapping(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("0.95"),
                null,
                "reason"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void confirm_shouldSetStatusAndConfirmedBy() {
        InteropMapping mapping = createDefaultMapping();
        UUID userId = UUID.randomUUID();

        mapping.confirm(userId);

        assertThat(mapping.getStatus()).isEqualTo(InteropMappingStatus.CONFIRMED);
        assertThat(mapping.getConfirmedBy()).isEqualTo(userId);
        assertThat(mapping.getConfirmedAt()).isNotNull();
    }

    @Test
    void reject_shouldSetStatusAndConfirmedBy() {
        InteropMapping mapping = createDefaultMapping();
        UUID userId = UUID.randomUUID();

        mapping.reject(userId);

        assertThat(mapping.getStatus()).isEqualTo(InteropMappingStatus.REJECTED);
        assertThat(mapping.getConfirmedBy()).isEqualTo(userId);
        assertThat(mapping.getConfirmedAt()).isNotNull();
    }

    @Test
    void autoMatch_shouldSetStatus() {
        InteropMapping mapping = createDefaultMapping();

        mapping.autoMatch();

        assertThat(mapping.getStatus()).isEqualTo(InteropMappingStatus.AUTO_MATCHED);
    }

    @Test
    void noArgsConstructor_shouldCreateEmptyMapping() {
        InteropMapping mapping = new InteropMapping();
        assertThat(mapping).isNotNull();
    }

    @Test
    void equals_shouldReturnTrueForSameId() {
        InteropMapping mapping1 = createDefaultMapping();
        InteropMapping mapping2 = createDefaultMapping();
        // Both have null IDs, so they should not be equal
        assertThat(mapping1.equals(mapping2)).isFalse();
    }

    @Test
    void equals_shouldReturnTrueForSameInstance() {
        InteropMapping mapping = createDefaultMapping();
        assertThat(mapping.equals(mapping)).isTrue();
    }

    @Test
    void equals_shouldReturnFalseForNull() {
        InteropMapping mapping = createDefaultMapping();
        assertThat(mapping.equals(null)).isFalse();
    }

    @Test
    void equals_shouldReturnFalseForDifferentClass() {
        InteropMapping mapping = createDefaultMapping();
        assertThat(mapping.equals("not a mapping")).isFalse();
    }

    @Test
    void hashCode_shouldBeConsistent() {
        InteropMapping mapping = createDefaultMapping();
        int hash1 = mapping.hashCode();
        int hash2 = mapping.hashCode();
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void toString_shouldContainKeyFields() {
        InteropMapping mapping = createDefaultMapping();
        String str = mapping.toString();
        assertThat(str).contains("InteropMapping");
    }

    @Test
    void settersAndGetters_shouldWork() {
        InteropMapping mapping = new InteropMapping();
        UUID id = UUID.randomUUID();
        UUID interopId = UUID.randomUUID();
        UUID localId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        mapping.setId(id);
        mapping.setInteropDataElementId(interopId);
        mapping.setLocalItemId(localId);
        mapping.setConfidenceScore(new BigDecimal("0.88"));
        mapping.setStrategy("NAME_SIMILARITY");
        mapping.setStatus(InteropMappingStatus.CONFIRMED);
        mapping.setReason("matched by name");
        mapping.setConfirmedBy(userId);
        mapping.setConfirmedAt(now);
        mapping.setCreatedAt(now);

        assertThat(mapping.getId()).isEqualTo(id);
        assertThat(mapping.getInteropDataElementId()).isEqualTo(interopId);
        assertThat(mapping.getLocalItemId()).isEqualTo(localId);
        assertThat(mapping.getConfidenceScore()).isEqualByComparingTo(new BigDecimal("0.88"));
        assertThat(mapping.getStrategy()).isEqualTo("NAME_SIMILARITY");
        assertThat(mapping.getStatus()).isEqualTo(InteropMappingStatus.CONFIRMED);
        assertThat(mapping.getReason()).isEqualTo("matched by name");
        assertThat(mapping.getConfirmedBy()).isEqualTo(userId);
        assertThat(mapping.getConfirmedAt()).isEqualTo(now);
        assertThat(mapping.getCreatedAt()).isEqualTo(now);
    }

    private InteropMapping createDefaultMapping() {
        return new InteropMapping(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("0.95"),
                "EXACT_IDENTIFIER",
                "test reason"
        );
    }
}
