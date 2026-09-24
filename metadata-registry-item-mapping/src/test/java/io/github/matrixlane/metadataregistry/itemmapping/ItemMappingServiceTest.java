package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.event.MappingChangedEvent;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingHierarchyRepository;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService.EndpointBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemMappingServiceTest {

    @Mock
    private ItemMappingRepository mappingRepository;

    @Mock
    private MappingHierarchyRepository hierarchyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ItemMappingService service;

    @BeforeEach
    void setUp() {
        service = new ItemMappingService(mappingRepository, hierarchyRepository, eventPublisher);
    }

    // ---- createMapping ----

    @Test
    void createMapping_shouldThrowWhenDegreeIsNull() {
        assertThatThrownBy(() -> service.createMapping(
                List.of(new EndpointBinding(mock(Item.class), null)),
                List.of(new EndpointBinding(mock(Item.class), null)),
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("degree must not be null");
    }

    @Test
    void createMapping_shouldThrowWhenRulePresentButNotationMissing() {
        assertThatThrownBy(() -> service.createMapping(
                List.of(new EndpointBinding(mock(Item.class), null)),
                List.of(new EndpointBinding(mock(Item.class), null)),
                MappingDegree.DERIVED_FROM, null, "year := 2024", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transformationRuleNotation is required");
    }

    @Test
    void createMapping_shouldThrowWhenRulePresentButNotationBlank() {
        assertThatThrownBy(() -> service.createMapping(
                List.of(new EndpointBinding(mock(Item.class), null)),
                List.of(new EndpointBinding(mock(Item.class), null)),
                MappingDegree.DERIVED_FROM, null, "year := 2024", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transformationRuleNotation is required");
    }

    @Test
    void createMapping_shouldThrowWhenSubjectBindingsEmpty() {
        assertThatThrownBy(() -> service.createMapping(
                List.of(),
                List.of(new EndpointBinding(mock(Item.class), null)),
                MappingDegree.SAME_AS, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one subject binding is required");
    }

    @Test
    void createMapping_shouldThrowWhenSubjectBindingsNull() {
        assertThatThrownBy(() -> service.createMapping(
                null,
                List.of(new EndpointBinding(mock(Item.class), null)),
                MappingDegree.SAME_AS, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one subject binding is required");
    }

    @Test
    void createMapping_shouldThrowWhenObjectBindingsEmpty() {
        assertThatThrownBy(() -> service.createMapping(
                List.of(new EndpointBinding(mock(Item.class), null)),
                List.of(),
                MappingDegree.SAME_AS, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one object binding is required");
    }

    @Test
    void createMapping_shouldThrowWhenSubjectBindingItemNull() {
        assertThatThrownBy(() -> service.createMapping(
                List.of(new EndpointBinding(null, "v")),
                List.of(new EndpointBinding(mock(Item.class), null)),
                MappingDegree.SAME_AS, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject binding item must not be null");
    }

    @Test
    void createMapping_shouldThrowWhenObjectBindingItemNull() {
        assertThatThrownBy(() -> service.createMapping(
                List.of(new EndpointBinding(mock(Item.class), null)),
                List.of(new EndpointBinding(null, "v")),
                MappingDegree.SAME_AS, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Object binding item must not be null");
    }

    @Test
    void createMapping_shouldSaveMappingAndPublishEvent() {
        Item subjectItem = mock(Item.class);
        Item objectItem = mock(Item.class);

        when(mappingRepository.save(any(ItemMapping.class))).thenAnswer(invocation -> {
            ItemMapping m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        ItemMapping result = service.createMapping(
                List.of(new EndpointBinding(subjectItem, "year")),
                List.of(new EndpointBinding(objectItem, "isodate")),
                MappingDegree.SAME_AS, "same date semantics",
                "isodate := year", "EBNF");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getDegree()).isEqualTo(MappingDegree.SAME_AS);
        assertThat(result.getDescription()).isEqualTo("same date semantics");
        assertThat(result.getTransformationRule()).isEqualTo("isodate := year");
        assertThat(result.getTransformationRuleNotation()).isEqualTo("EBNF");

        assertThat(result.getSubjects()).hasSize(1);
        assertThat(result.getSubjects().get(0).getItem()).isEqualTo(subjectItem);
        assertThat(result.getSubjects().get(0).getMappingVariableName()).isEqualTo("year");
        assertThat(result.getSubjects().get(0).getItemMapping()).isEqualTo(result);

        assertThat(result.getObjects()).hasSize(1);
        assertThat(result.getObjects().get(0).getItem()).isEqualTo(objectItem);
        assertThat(result.getObjects().get(0).getMappingVariableName()).isEqualTo("isodate");

        verify(mappingRepository).save(any(ItemMapping.class));

        ArgumentCaptor<MappingChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(MappingChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getChangeType()).isEqualTo("CREATED");
        assertThat(eventCaptor.getValue().getMappingId()).isEqualTo(result.getId());
    }

    @Test
    void createMapping_withMultipleBindings_shouldCreateAllBindings() {
        Item subjectItem1 = mock(Item.class);
        Item subjectItem2 = mock(Item.class);
        Item objectItem1 = mock(Item.class);
        Item objectItem2 = mock(Item.class);

        when(mappingRepository.save(any(ItemMapping.class))).thenAnswer(invocation -> {
            ItemMapping m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        ItemMapping result = service.createMapping(
                List.of(new EndpointBinding(subjectItem1, "year"),
                        new EndpointBinding(subjectItem2, "month")),
                List.of(new EndpointBinding(objectItem1, "isodate"),
                        new EndpointBinding(objectItem2, "displaydate")),
                MappingDegree.SEMANTICALLY_EQUIVALENT, null, null, null);

        assertThat(result.getSubjects()).hasSize(2);
        assertThat(result.getSubjects())
                .extracting(ItemMappingSubject::getItem)
                .containsExactly(subjectItem1, subjectItem2);
        assertThat(result.getSubjects())
                .extracting(ItemMappingSubject::getMappingVariableName)
                .containsExactly("year", "month");

        assertThat(result.getObjects()).hasSize(2);
        assertThat(result.getObjects())
                .extracting(ItemMappingObject::getItem)
                .containsExactly(objectItem1, objectItem2);
        assertThat(result.getObjects())
                .extracting(ItemMappingObject::getMappingVariableName)
                .containsExactly("isodate", "displaydate");

        verify(eventPublisher).publishEvent(any(MappingChangedEvent.class));
    }

    @Test
    void createMapping_shouldAllowNullRuleAndNotation() {
        when(mappingRepository.save(any(ItemMapping.class))).thenAnswer(invocation -> {
            ItemMapping m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        ItemMapping result = service.createMapping(
                List.of(new EndpointBinding(mock(Item.class), null)),
                List.of(new EndpointBinding(mock(Item.class), null)),
                MappingDegree.RELATED_TO, null, null, null);

        assertThat(result.getDegree()).isEqualTo(MappingDegree.RELATED_TO);
        assertThat(result.getTransformationRule()).isNull();
        assertThat(result.getTransformationRuleNotation()).isNull();
        verify(eventPublisher).publishEvent(any(MappingChangedEvent.class));
    }

    // ---- updateMapping ----

    @Test
    void updateMapping_shouldReplaceAllBindings() {
        ItemMapping mapping = new ItemMapping();
        mapping.setId(UUID.randomUUID());
        mapping.setDegree(MappingDegree.RELATED_TO);
        Item oldSubjectItem = mock(Item.class);
        Item oldObjectItem = mock(Item.class);
        mapping.getSubjects().add(new ItemMappingSubject(mapping, oldSubjectItem, "oldSub"));
        mapping.getObjects().add(new ItemMappingObject(mapping, oldObjectItem, "oldObj"));

        Item newSubjectItem1 = mock(Item.class);
        Item newSubjectItem2 = mock(Item.class);
        Item newObjectItem = mock(Item.class);

        when(mappingRepository.save(any(ItemMapping.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ItemMapping result = service.updateMapping(
                mapping, MappingDegree.SEMANTICALLY_EQUIVALENT,
                List.of(new EndpointBinding(newSubjectItem1, "newSub1"),
                        new EndpointBinding(newSubjectItem2, "newSub2")),
                List.of(new EndpointBinding(newObjectItem, "newObj")),
                "updated desc", "rule", "notation");

        assertThat(result.getDegree()).isEqualTo(MappingDegree.SEMANTICALLY_EQUIVALENT);
        assertThat(result.getDescription()).isEqualTo("updated desc");
        assertThat(result.getTransformationRule()).isEqualTo("rule");
        assertThat(result.getTransformationRuleNotation()).isEqualTo("notation");

        // Old bindings cleared, new bindings in place
        assertThat(result.getSubjects()).hasSize(2);
        assertThat(result.getSubjects())
                .extracting(ItemMappingSubject::getItem)
                .containsExactly(newSubjectItem1, newSubjectItem2);
        assertThat(result.getSubjects())
                .extracting(ItemMappingSubject::getMappingVariableName)
                .containsExactly("newSub1", "newSub2");
        assertThat(result.getSubjects())
                .extracting(ItemMappingSubject::getItemMapping)
                .containsOnly(mapping);

        assertThat(result.getObjects()).hasSize(1);
        assertThat(result.getObjects().get(0).getItem()).isEqualTo(newObjectItem);
        assertThat(result.getObjects().get(0).getMappingVariableName()).isEqualTo("newObj");

        verify(mappingRepository).save(mapping);

        ArgumentCaptor<MappingChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(MappingChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getChangeType()).isEqualTo("MODIFIED");
    }

    @Test
    void updateMapping_shouldThrowWhenDegreeIsNull() {
        ItemMapping mapping = new ItemMapping();
        assertThatThrownBy(() -> service.updateMapping(
                mapping, null,
                List.of(new EndpointBinding(mock(Item.class), null)),
                List.of(new EndpointBinding(mock(Item.class), null)),
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("degree must not be null");
    }

    @Test
    void updateMapping_shouldThrowWhenSubjectBindingsEmpty() {
        ItemMapping mapping = new ItemMapping();
        assertThatThrownBy(() -> service.updateMapping(
                mapping, MappingDegree.SAME_AS,
                List.of(),
                List.of(new EndpointBinding(mock(Item.class), null)),
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one subject binding is required");
    }

    @Test
    void updateMapping_shouldThrowWhenObjectBindingsEmpty() {
        ItemMapping mapping = new ItemMapping();
        assertThatThrownBy(() -> service.updateMapping(
                mapping, MappingDegree.SAME_AS,
                List.of(new EndpointBinding(mock(Item.class), null)),
                List.of(),
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one object binding is required");
    }

    // ---- deleteMapping ----

    @Test
    void deleteMapping_shouldThrowWhenNotFound() {
        UUID mappingId = UUID.randomUUID();
        when(mappingRepository.findById(mappingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMapping(mappingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mapping not found");
    }

    @Test
    void deleteMapping_shouldCleanHierarchyAndDeleteWhenExists() {
        UUID mappingId = UUID.randomUUID();
        ItemMapping mapping = new ItemMapping();
        mapping.setId(mappingId);
        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));

        service.deleteMapping(mappingId);

        verify(hierarchyRepository).deleteBySupersetMappingId(mappingId);
        verify(hierarchyRepository).deleteBySubsetMappingId(mappingId);
        verify(mappingRepository).deleteById(mappingId);

        ArgumentCaptor<MappingChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(MappingChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getChangeType()).isEqualTo("DELETED");
        assertThat(eventCaptor.getValue().getMappingId()).isEqualTo(mappingId);
    }

    // ---- 查询 ----

    @Test
    void getOutgoingMappings_shouldReturnMappings() {
        UUID itemId = UUID.randomUUID();
        ItemMapping mapping = new ItemMapping();
        when(mappingRepository.findBySubjects_Item_Id(eq(itemId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mapping)));

        List<ItemMapping> results = service.getOutgoingMappings(itemId);

        assertThat(results).containsExactly(mapping);
        verify(mappingRepository).findBySubjects_Item_Id(eq(itemId), any(Pageable.class));
    }

    @Test
    void getIncomingMappings_shouldReturnMappings() {
        UUID itemId = UUID.randomUUID();
        ItemMapping mapping = new ItemMapping();
        when(mappingRepository.findByObjects_Item_Id(eq(itemId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mapping)));

        List<ItemMapping> results = service.getIncomingMappings(itemId);

        assertThat(results).containsExactly(mapping);
        verify(mappingRepository).findByObjects_Item_Id(eq(itemId), any(Pageable.class));
    }

    @Test
    void getAllMappingsForItem_shouldCombineOutgoingAndIncoming() {
        UUID itemId = UUID.randomUUID();
        ItemMapping outgoing = new ItemMapping();
        ItemMapping incoming = new ItemMapping();
        when(mappingRepository.findBySubjects_Item_Id(eq(itemId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(outgoing)));
        when(mappingRepository.findByObjects_Item_Id(eq(itemId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(incoming)));

        List<ItemMapping> results = service.getAllMappingsForItem(itemId);

        assertThat(results).containsExactlyInAnyOrder(outgoing, incoming);
    }
}
