package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingHierarchy;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingHierarchyRepository;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingHierarchyRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingResponse;
import io.github.matrixlane.metadataregistry.web.repository.RegisteredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemMappingCrudServiceTest {

    @Mock
    private ItemMappingRepository mappingRepository;
    @Mock
    private MappingHierarchyRepository hierarchyRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private RegisteredItemRepository registeredItemRepository;
    @Mock
    private ItemMappingService mappingService;
    @Mock
    private ItemAdministrationService itemAdministrationService;

    @InjectMocks
    private ItemMappingCrudService crudService;

    private UUID subjectItemId;
    private UUID subject2ItemId;
    private UUID objectItemId;

    @BeforeEach
    void setUp() {
        subjectItemId = UUID.randomUUID();
        subject2ItemId = UUID.randomUUID();
        objectItemId = UUID.randomUUID();
    }

    private ItemMappingRequest request(MappingDegree degree) {
        return new ItemMappingRequest(
                List.of(new ItemMappingRequest.Endpoint(subjectItemId, "year"),
                        new ItemMappingRequest.Endpoint(subject2ItemId, "month")),
                List.of(new ItemMappingRequest.Endpoint(objectItemId, "isodate")),
                degree,
                "desc", "isodate = year, month", "EBNF", null);
    }

    private ItemMapping persistedMapping(UUID id, MappingDegree degree) {
        ItemMapping mapping = new ItemMapping();
        mapping.setId(id);
        mapping.setDegree(degree);
        mapping.setDescription("desc");
        mapping.setTransformationRule("isodate = year, month");
        mapping.setTransformationRuleNotation("EBNF");
        return mapping;
    }

    /** 单行 varargs 时 javac 会把 Object[] 推断为元素类型，用显式数组绕过。 */
    private static List<Object[]> rows(Object[]... rows) {
        return Arrays.asList(rows);
    }

    private void stubRows(UUID id) {
        when(mappingRepository.findSubjectRowsByMappingIds(List.of(id)))
                .thenReturn(rows(new Object[]{id, subjectItemId, "year"},
                        new Object[]{id, subject2ItemId, "month"}));
        when(mappingRepository.findObjectRowsByMappingIds(List.of(id)))
                .thenReturn(rows(new Object[]{id, objectItemId, "isodate"}));
    }

    // ---- 创建 ----

    @Test
    void create_shouldResolveItemsAndDelegate() {
        UUID id = UUID.randomUUID();
        when(itemRepository.existsItemById(any())).thenReturn(true);
        Item ref = org.mockito.Mockito.mock(Item.class);
        when(itemRepository.getReferenceById(any())).thenReturn(ref);

        ItemMapping saved = persistedMapping(id, MappingDegree.DERIVED_FROM);
        when(mappingService.createMapping(any(), any(), eq(MappingDegree.DERIVED_FROM),
                eq("desc"), eq("isodate = year, month"), eq("EBNF")))
                .thenReturn(saved);
        when(mappingRepository.findById(id)).thenReturn(Optional.of(saved));
        stubRows(id);
        when(registeredItemRepository.findRegistrationStatusRowsByItemIds(anyCollection()))
                .thenReturn(rows(new Object[]{id, RegistrationStatus.RECORDED}));

        ItemMappingResponse response = crudService.create(request(MappingDegree.DERIVED_FROM));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.degree()).isEqualTo(MappingDegree.DERIVED_FROM);
        assertThat(response.subjects()).hasSize(2);
        assertThat(response.subjects().get(0).itemId()).isEqualTo(subjectItemId);
        assertThat(response.subjects().get(0).variableName()).isEqualTo("year");
        assertThat(response.subjects().get(1).variableName()).isEqualTo("month");
        assertThat(response.objects()).hasSize(1);
        assertThat(response.objects().get(0).variableName()).isEqualTo("isodate");
        assertThat(response.registrationStatus()).isEqualTo(RegistrationStatus.RECORDED);
        verify(itemAdministrationService, never()).ensureAdministeredItem(any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemMappingService.EndpointBinding>> subjectsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(mappingService).createMapping(subjectsCaptor.capture(), any(),
                eq(MappingDegree.DERIVED_FROM), any(), any(), any());
        assertThat(subjectsCaptor.getValue()).hasSize(2);
        assertThat(subjectsCaptor.getValue().get(0).variableName()).isEqualTo("year");
    }

    @Test
    void create_withRegistration_shouldInvokeEnsureAdministeredItem() {
        UUID id = UUID.randomUUID();
        when(itemRepository.existsItemById(any())).thenReturn(true);
        Item ref = org.mockito.Mockito.mock(Item.class);
        when(itemRepository.getReferenceById(any())).thenReturn(ref);

        ItemMappingRequest req = new ItemMappingRequest(
                List.of(new ItemMappingRequest.Endpoint(subjectItemId, null)),
                List.of(new ItemMappingRequest.Endpoint(objectItemId, null)),
                MappingDegree.SAME_AS, null, null, null,
                new io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest(
                        null, null, null, null, null));
        ItemMapping saved = persistedMapping(id, MappingDegree.SAME_AS);
        when(mappingService.createMapping(any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);
        when(mappingRepository.findById(id)).thenReturn(Optional.of(saved));
        stubRows(id);

        crudService.create(req);

        verify(itemAdministrationService).ensureAdministeredItem(eq(saved), any());
    }

    @Test
    void create_withMissingSubjectItem_shouldThrowNotFound() {
        when(itemRepository.existsItemById(subjectItemId)).thenReturn(false);

        assertThatThrownBy(() -> crudService.create(request(MappingDegree.SAME_AS)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withEmptySubjects_shouldThrowBusiness() {
        ItemMappingRequest req = new ItemMappingRequest(
                List.of(),
                List.of(new ItemMappingRequest.Endpoint(objectItemId, null)),
                MappingDegree.SAME_AS, null, null, null, null);

        assertThatThrownBy(() -> crudService.create(req))
                .isInstanceOf(BusinessException.class);
    }

    // ---- 查询 ----

    @Test
    void list_withDegreeFilter_shouldUseDerivedQuery() {
        UUID id = UUID.randomUUID();
        ItemMapping mapping = persistedMapping(id, MappingDegree.CONTAINS);
        when(mappingRepository.findByDegree(eq(MappingDegree.CONTAINS), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mapping)));
        stubRows(id);

        Page<ItemMappingResponse> page = crudService.list(0, 20, null, null, null,
                MappingDegree.CONTAINS);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).degree()).isEqualTo(MappingDegree.CONTAINS);
        assertThat(page.getContent().get(0).subjects()).hasSize(2);
    }

    @Test
    void get_missingMapping_shouldThrowNotFound() {
        UUID id = UUID.randomUUID();
        when(mappingRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crudService.get(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- 删除 ----

    @Test
    void delete_shouldDelegateToDomainService() {
        UUID id = UUID.randomUUID();
        when(mappingRepository.findById(id))
                .thenReturn(Optional.of(persistedMapping(id, MappingDegree.SAME_AS)));

        crudService.delete(id);

        verify(mappingService).deleteMapping(id);
    }

    // ---- 层级 ----

    @Test
    void putHierarchy_withSelfReference_shouldReject() {
        UUID id = UUID.randomUUID();
        when(mappingRepository.findById(id))
                .thenReturn(Optional.of(persistedMapping(id, MappingDegree.SAME_AS)));

        assertThatThrownBy(() -> crudService.putHierarchy(id,
                new ItemMappingHierarchyRequest(id)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自身");
    }

    @Test
    void putHierarchy_withCycle_shouldReject() {
        UUID id = UUID.randomUUID();
        UUID up = UUID.randomUUID();
        UUID top = UUID.randomUUID();
        when(mappingRepository.findById(id))
                .thenReturn(Optional.of(persistedMapping(id, MappingDegree.SAME_AS)));
        when(mappingRepository.existsById(up)).thenReturn(true);
        MappingHierarchy h1 = org.mockito.Mockito.mock(MappingHierarchy.class);
        MappingHierarchy h2 = org.mockito.Mockito.mock(MappingHierarchy.class);
        ItemMapping mUp = persistedMapping(up, MappingDegree.SAME_AS);
        ItemMapping mTop = persistedMapping(top, MappingDegree.SAME_AS);
        ItemMapping mId = persistedMapping(id, MappingDegree.SAME_AS);
        when(hierarchyRepository.findBySubsetMappingId(up)).thenReturn(Optional.of(h1));
        when(h1.getSupersetMapping()).thenReturn(mTop);
        when(hierarchyRepository.findBySubsetMappingId(top)).thenReturn(Optional.of(h2));
        when(h2.getSupersetMapping()).thenReturn(mId);

        assertThatThrownBy(() -> crudService.putHierarchy(id,
                new ItemMappingHierarchyRequest(up)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("循环");
    }

    @Test
    void putHierarchy_clearSuperset_shouldDeleteAndReturnEmpty() {
        UUID id = UUID.randomUUID();
        when(mappingRepository.findById(id))
                .thenReturn(Optional.of(persistedMapping(id, MappingDegree.SAME_AS)));
        when(hierarchyRepository.findBySubsetMappingId(id)).thenReturn(Optional.empty());
        when(hierarchyRepository.findBySupersetMappingId(id)).thenReturn(List.of());

        Map<String, Object> result = crudService.putHierarchy(id,
                new ItemMappingHierarchyRequest(null));

        verify(hierarchyRepository).deleteBySubsetMappingId(id);
        assertThat(result.get("supersetMappingId")).isNull();
        assertThat(result.get("subsetMappingIds")).isEqualTo(List.of());
    }
}
