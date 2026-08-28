package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.web.dto.dec.ConceptualDomainKindLabel;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptResponse;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainType;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementConceptRepository;
import io.github.matrixlane.metadataregistry.web.repository.ObjectClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖 ISO/IEC 11179-31:2023 §7.3.2 Data_Element_Concept 与 §7.4.2 Conceptual_Domain 的多对多关联重建。
 */
@ExtendWith(MockitoExtension.class)
class DataElementConceptServiceTest {

    @Mock private DataElementConceptRepository repository;
    @Mock private ObjectClassRepository objectClassRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private ConceptualDomainRepository conceptualDomainRepository;
    @Mock private ConceptContentApplier conceptContentApplier;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private VersionHistoryService versionHistoryService;

    @InjectMocks
    private DataElementConceptService service;

    @Test
    void create_withConceptualDomainIds_attachesDomains() {
        UUID decId = UUID.randomUUID();
        UUID cd1Id = UUID.randomUUID();
        UUID cd2Id = UUID.randomUUID();

        when(repository.save(any(DataElementConcept.class))).thenAnswer(inv -> {
            DataElementConcept e = inv.getArgument(0);
            e.setId(decId);
            return e;
        });

        ConceptualDomain cd1 = newConceptualDomain(cd1Id, ConceptualDomainType.DESCRIBED);
        ConceptualDomain cd2 = newConceptualDomain(cd2Id, ConceptualDomainType.LOCAL_ENUMERATED);
        when(conceptualDomainRepository.findById(cd1Id)).thenReturn(Optional.of(cd1));
        when(conceptualDomainRepository.findById(cd2Id)).thenReturn(Optional.of(cd2));

        service.create(new DataElementConceptRequest(
                null, null, null,
                List.of(cd1Id, cd2Id),
                List.of(), List.of(), null));

        ArgumentCaptor<List<ConceptualDomain>> captor = ArgumentCaptor.forClass(List.class);
        verify(conceptualDomainRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ConceptualDomain::getId)
                .containsExactlyInAnyOrder(cd1Id, cd2Id);
        assertThat(cd1.getDataElementConcepts()).hasSize(1);
        assertThat(cd2.getDataElementConcepts()).hasSize(1);
    }

    @Test
    void create_withEmptyConceptualDomainIds_doesNotTouchDomains() {
        when(repository.save(any(DataElementConcept.class))).thenAnswer(inv -> {
            DataElementConcept e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        service.create(new DataElementConceptRequest(
                null, null, null, List.of(),
                List.of(), List.of(), null));

        verify(conceptualDomainRepository, never()).findById(any(UUID.class));
        verify(conceptualDomainRepository, never()).saveAll(any());
    }

    @Test
    void toResponse_mapsConceptualDomainLabelsWithKind() {
        UUID decId = UUID.randomUUID();
        UUID cdId = UUID.randomUUID();

        DataElementConcept dec = new DataElementConcept();
        dec.setId(decId);
        ConceptualDomain cd = newConceptualDomain(cdId, ConceptualDomainType.REFERENCE_ENUMERATED);
        dec.getConceptualDomains().add(cd);
        cd.getDataElementConcepts().add(dec);

        when(repository.findById(decId)).thenReturn(Optional.of(dec));

        DataElementConceptResponse response = service.get(decId);

        assertThat(response.conceptualDomainIds()).containsExactly(cdId);
        assertThat(response.conceptualDomainLabels()).hasSize(1);
        ConceptualDomainKindLabel only = response.conceptualDomainLabels().get(0);
        assertThat(only.id()).isEqualTo(cdId);
        assertThat(only.type()).isEqualTo(ConceptualDomainType.REFERENCE_ENUMERATED);
    }

    @Test
    void delete_clearsManyToManyLinksBeforeRemovingEntity() {
        UUID decId = UUID.randomUUID();
        UUID cdId = UUID.randomUUID();

        DataElementConcept dec = new DataElementConcept();
        dec.setId(decId);
        ConceptualDomain cd = newConceptualDomain(cdId, ConceptualDomainType.DESCRIBED);
        dec.getConceptualDomains().add(cd);
        cd.getDataElementConcepts().add(dec);

        when(repository.findById(decId)).thenReturn(Optional.of(dec));

        service.delete(decId);

        assertThat(cd.getDataElementConcepts()).isEmpty();
        verify(conceptualDomainRepository, times(1)).saveAll(any());
        verify(repository).deleteById(decId);
    }

    private static ConceptualDomain newConceptualDomain(UUID id, ConceptualDomainType kind) {
        ConceptualDomain cd = switch (kind) {
            case DESCRIBED -> new io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain();
            case LOCAL_ENUMERATED -> new io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain();
            case REFERENCE_ENUMERATED -> new ReferenceEnumeratedConceptualDomain();
        };
        cd.setId(id);
        return cd;
    }

    // 供 ObjectClass / Property 路径占位；本期测试主要聚焦 conceptualDomainIds 分支，不深入到 objectClass / property。
    @SuppressWarnings("unused")
    private static void unused(ObjectClass oc, Property prop) {
        oc.setId(UUID.randomUUID());
        prop.setId(UUID.randomUUID());
    }

}
