package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataSearchServiceTest {

    @Mock
    private AdministeredItemRepository repository;

    @InjectMocks
    private MetadataSearchService searchService;

    private UUID aiId;
    private AdministeredItem testItem;
    private AdministeredItemResponse expectedResponse;

    @BeforeEach
    void setUp() {
        aiId = UUID.randomUUID();

        // Set up a mock item with Designation and ScopedIdentifier
        LanguageIdentification lang = new LanguageIdentification();
        lang.setId(UUID.randomUUID());
        lang.setLanguageIdentifier("zh-CN");

        Designation des = new Designation();
        des.setSign(Sign.of("患者姓名"));
        des.setLanguage(lang);

        Definition def = new Definition();
        def.setText(Text.of("患者姓名的定义"));

        Item item = new TestItem();
        item.setId(UUID.randomUUID());
        item.setDesignations(List.of(des));
        item.setDefinitions(List.of(def));

        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifier("DE-PATIENT-NAME");
        si.setVersion("1.0");

        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(RegistrationStatus.STANDARD);
        state.setEffectiveDate(LocalDateTime.now());

        testItem = new AdministeredItem(item);
        testItem.setId(aiId);
        testItem.setScopedIdentifiers(List.of(si));
        testItem.setRegistrationState(state);
        testItem.setCreationDateTime(LocalDateTime.now());
        testItem.setLastChangeDateTime(LocalDateTime.now());
    }

    @Test
    void search_withKeyword_shouldReturnResults() {
        Page<AdministeredItem> page = new PageImpl<>(List.of(testItem));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<AdministeredItemResponse> result = searchService.search(
                "患者", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        AdministeredItemResponse res = result.getContent().get(0);
        assertThat(res.itemId()).isNotNull();
        assertThat(res.registrationStatus()).isEqualTo(RegistrationStatus.STANDARD);
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void search_withTypeFilter_shouldCallRepositoryWithSpec() {
        Page<AdministeredItem> page = new PageImpl<>(List.of(testItem));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<AdministeredItemResponse> result = searchService.search(
                null, "DataElement", null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void search_withStatusFilter_shouldCallRepositoryWithSpec() {
        Page<AdministeredItem> page = new PageImpl<>(List.of(testItem));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<AdministeredItemResponse> result = searchService.search(
                null, null, RegistrationStatus.STANDARD, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        AdministeredItemResponse res = result.getContent().get(0);
        assertThat(res.registrationStatus()).isEqualTo(RegistrationStatus.STANDARD);
    }

    @Test
    void search_withCombinedFilters_shouldReturnFilteredResults() {
        Page<AdministeredItem> page = new PageImpl<>(List.of(testItem));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<AdministeredItemResponse> result = searchService.search(
                "patient", "DataElement", RegistrationStatus.STANDARD, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withKeywordNoMatch_shouldReturnEmpty() {
        Page<AdministeredItem> page = Page.empty();
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<AdministeredItemResponse> result = searchService.search(
                "ZZZ_NONEXISTENT", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void search_withNullKeyword_shouldReturnAll() {
        Page<AdministeredItem> page = new PageImpl<>(List.of(testItem));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<AdministeredItemResponse> result = searchService.search(
                null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withBlankKeyword_shouldReturnAll() {
        Page<AdministeredItem> page = new PageImpl<>(List.of(testItem));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<AdministeredItemResponse> result = searchService.search(
                "   ", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void search_withPagination_shouldRespectPageSize() {
        Page<AdministeredItem> page = new PageImpl<>(List.of(testItem));
        when(repository.findAll(any(Specification.class), eq(PageRequest.of(0, 10))))
                .thenReturn(page);

        Page<AdministeredItemResponse> result = searchService.search(
                "患者", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();
        verify(repository).findAll(any(Specification.class), eq(PageRequest.of(0, 10)));
    }

    /**
     * Concrete Item subclass for testing.
     */
    static class TestItem extends Item {
        @Override
        public String toString() {
            if (getDesignations() != null && !getDesignations().isEmpty()) {
                return getDesignations().get(0).toString();
            }
            return super.toString();
        }
    }
}
