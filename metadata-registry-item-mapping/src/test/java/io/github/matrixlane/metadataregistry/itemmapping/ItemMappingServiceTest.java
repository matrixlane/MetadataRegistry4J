package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemMappingServiceTest {

    @Mock
    private ItemMappingRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ItemMappingService service;

    @BeforeEach
    void setUp() {
        service = new ItemMappingService(repository, eventPublisher);
    }

    // ---- Mutual Exclusion Tests ----

    @Test
    void areMutuallyExclusive_equivalentAndDerived_shouldBeExclusive() {
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.EQUIVALENT, MappingType.DERIVED))
                .isTrue();
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.DERIVED, MappingType.EQUIVALENT))
                .isTrue();
    }

    @Test
    void areMutuallyExclusive_equivalentAndAggregated_shouldBeExclusive() {
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.EQUIVALENT, MappingType.AGGREGATED))
                .isTrue();
    }

    @Test
    void areMutuallyExclusive_equivalentAndSubdivided_shouldBeExclusive() {
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.EQUIVALENT, MappingType.SUBDIVIDED))
                .isTrue();
    }

    @Test
    void areMutuallyExclusive_derivedAndAggregated_shouldBeExclusive() {
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.DERIVED, MappingType.AGGREGATED))
                .isTrue();
    }

    @Test
    void areMutuallyExclusive_aggregatedAndSubdivided_shouldBeExclusive() {
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.AGGREGATED, MappingType.SUBDIVIDED))
                .isTrue();
    }

    @Test
    void areMutuallyExclusive_sameType_shouldNotBeExclusive() {
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.EQUIVALENT, MappingType.EQUIVALENT))
                .isFalse();
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.DERIVED, MappingType.DERIVED))
                .isFalse();
    }

    @Test
    void areMutuallyExclusive_derivedAndSubdivided_shouldNotBeExclusive() {
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.DERIVED, MappingType.SUBDIVIDED))
                .isFalse();
        assertThat(ItemMappingService.areMutuallyExclusive(MappingType.SUBDIVIDED, MappingType.DERIVED))
                .isFalse();
    }

    // ---- Cycle Detection Tests ----

    @Test
    void detectCycle_shouldNotThrowWhenNoCycle() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        ItemMappingSubject subA = ItemMappingSubject.forItem(a);
        ItemMappingObject objB = ItemMappingObject.forItem(b);
        ItemMapping ab = new ItemMapping(subA, objB, MappingType.DERIVED);

        ItemMappingSubject subB = ItemMappingSubject.forItem(b);
        ItemMappingObject objC = ItemMappingObject.forItem(c);
        ItemMapping bc = new ItemMapping(subB, objC, MappingType.DERIVED);

        when(repository.findAll()).thenReturn(List.of(ab, bc));

        // Adding a→c is safe (no cycle)
        service.detectCycle(a, c);
    }

    @Test
    void detectCycle_shouldThrowWhenCycleDetected() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        // Existing: a→b, b→c
        ItemMappingSubject subA = ItemMappingSubject.forItem(a);
        ItemMappingObject objB = ItemMappingObject.forItem(b);
        ItemMapping ab = new ItemMapping(subA, objB, MappingType.DERIVED);

        ItemMappingSubject subB = ItemMappingSubject.forItem(b);
        ItemMappingObject objC = ItemMappingObject.forItem(c);
        ItemMapping bc = new ItemMapping(subB, objC, MappingType.DERIVED);

        when(repository.findAll()).thenReturn(List.of(ab, bc));

        // Trying to add c→a should throw (cycle: a→b→c→a)
        assertThatThrownBy(() -> service.detectCycle(c, a))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void detectCycle_shouldIgnoreEquivalentMappings() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        // Equivalent mapping a↔b should not count as directed edge
        ItemMappingSubject subA = ItemMappingSubject.forItem(a);
        ItemMappingObject objB = ItemMappingObject.forItem(b);
        ItemMapping ab = new ItemMapping(subA, objB, MappingType.EQUIVALENT);

        when(repository.findAll()).thenReturn(List.of(ab));

        // Adding b→a would be a cycle if EQUIVALENT counted, but it doesn't
        service.detectCycle(b, a);
    }

    // ---- Validations ----

    @Test
    void createItemMapping_shouldSaveMappingWhenValid() {
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();

        ItemMapping savedMapping = new ItemMapping(
                ItemMappingSubject.forItem(subjectId),
                ItemMappingObject.forItem(objectId),
                MappingType.EQUIVALENT
        );
        when(repository.findByItemId(any())).thenReturn(List.of());
        when(repository.save(any())).thenReturn(savedMapping);

        ItemMapping result = service.createItemMapping(subjectId, objectId, MappingType.EQUIVALENT);

        assertThat(result.getMappingType()).isEqualTo(MappingType.EQUIVALENT);
        verify(repository).save(any());
    }

    @Test
    void createItemToExternalMapping_shouldSaveMapping() {
        UUID subjectId = UUID.randomUUID();
        String externalUri = "https://example.org/fhir/Patient/123";

        ItemMapping savedMapping = new ItemMapping(
                ItemMappingSubject.forItem(subjectId),
                ItemMappingObject.forExternalUri(externalUri),
                MappingType.DERIVED
        );
        when(repository.save(any())).thenReturn(savedMapping);

        ItemMapping result = service.createItemToExternalMapping(
                subjectId, externalUri, MappingType.DERIVED);

        assertThat(result.getMappingType()).isEqualTo(MappingType.DERIVED);
        verify(repository).save(any());
    }

    @Test
    void createExternalToItemMapping_shouldSaveMapping() {
        UUID objectId = UUID.randomUUID();
        String externalUri = "https://example.org/fhir/Observation/456";

        ItemMapping savedMapping = new ItemMapping(
                ItemMappingSubject.forExternalUri(externalUri),
                ItemMappingObject.forItem(objectId),
                MappingType.SUBDIVIDED
        );
        when(repository.save(any())).thenReturn(savedMapping);

        ItemMapping result = service.createExternalToItemMapping(
                externalUri, objectId, MappingType.SUBDIVIDED);

        assertThat(result.getMappingType()).isEqualTo(MappingType.SUBDIVIDED);
        verify(repository).save(any());
    }

    @Test
    void createItemMapping_shouldValidateMutualExclusion() {
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();

        // Existing equivalent mapping between same pair
        ItemMappingSubject sub = ItemMappingSubject.forItem(subjectId);
        ItemMappingObject obj = ItemMappingObject.forItem(objectId);
        ItemMapping existing = new ItemMapping(sub, obj, MappingType.EQUIVALENT);
        existing.getSubject().setId(UUID.randomUUID()); // Simulate persisted entity
        existing.getObject().setId(UUID.randomUUID());

        when(repository.findByItemId(subjectId)).thenReturn(List.of(existing));

        // Try to add DERIVED which is mutually exclusive with EQUIVALENT
        assertThatThrownBy(() -> service.createItemMapping(subjectId, objectId, MappingType.DERIVED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void deleteMapping_shouldThrowWhenNotFound() {
        UUID mappingId = UUID.randomUUID();
        when(repository.findById(mappingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMapping(mappingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mapping not found");
    }

    @Test
    void deleteMapping_shouldDeleteWhenExists() {
        UUID mappingId = UUID.randomUUID();
        ItemMapping mapping = new ItemMapping(
                ItemMappingSubject.forItem(UUID.randomUUID()),
                ItemMappingObject.forItem(UUID.randomUUID()),
                MappingType.EQUIVALENT
        );
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        service.deleteMapping(mappingId);

        verify(repository).deleteById(mappingId);
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void createItemMapping_shouldPublishEvent() {
        UUID subjectId = UUID.randomUUID();
        UUID objectId = UUID.randomUUID();

        ItemMapping savedMapping = new ItemMapping(
                ItemMappingSubject.forItem(subjectId),
                ItemMappingObject.forItem(objectId),
                MappingType.EQUIVALENT
        );
        when(repository.findByItemId(any())).thenReturn(List.of());
        when(repository.save(any())).thenReturn(savedMapping);

        service.createItemMapping(subjectId, objectId, MappingType.EQUIVALENT);

        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void getOutgoingMappings_shouldReturnMappings() {
        UUID itemId = UUID.randomUUID();
        when(repository.findBySubjectItemId(itemId)).thenReturn(List.of());

        List<ItemMapping> results = service.getOutgoingMappings(itemId);

        assertThat(results).isEmpty();
        verify(repository).findBySubjectItemId(itemId);
    }

    @Test
    void getAllMappingsForItem_shouldReturnAll() {
        UUID itemId = UUID.randomUUID();
        when(repository.findByItemId(itemId)).thenReturn(List.of());

        List<ItemMapping> results = service.getAllMappingsForItem(itemId);

        assertThat(results).isEmpty();
        verify(repository).findByItemId(itemId);
    }
}
