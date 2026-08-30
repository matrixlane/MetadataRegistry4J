package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.association.MetadataAssociationValidationException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.association.AssociationMutationRequest;
import io.github.matrixlane.metadataregistry.web.dto.association.ItemAssociationEntry;
import io.github.matrixlane.metadataregistry.web.dto.association.ItemAssociationsResponse;
import io.github.matrixlane.metadataregistry.web.dto.association.StandardAssociationType;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementConceptRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ItemAssociationService} 单元测试。
 * 锁定 ISO/IEC 11179-31:2023 四实体标准关联的方向与多重性：
 * data_element_meaning (7.6.3.2)、data_element_domain (7.6.3.1)、
 * value_domain_meaning (7.4.4.1)、data_element_concept_domain (7.2.3.4)。
 */
@ExtendWith(MockitoExtension.class)
class ItemAssociationServiceTest {

    @Mock private DataElementRepository dataElementRepository;
    @Mock private DataElementConceptRepository dataElementConceptRepository;
    @Mock private ConceptualDomainRepository conceptualDomainRepository;
    @Mock private ValueDomainRepository valueDomainRepository;
    @Mock private AdministeredItemRepository administeredItemRepository;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private VersionHistoryService versionHistoryService;

    @InjectMocks
    private ItemAssociationService service;

    private static <T extends io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Entity> T withId(T entity, UUID id) {
        entity.setId(id);
        return entity;
    }

    @Test
    void list_forDataElement_returnsOutgoingDecAndVd() {
        UUID deId = UUID.randomUUID();
        DataElement de = withId(new DataElement(), deId);
        DataElementConcept dec = withId(new DataElementConcept(), UUID.randomUUID());
        ValueDomain vd = withId(new DescribedValueDomain(), UUID.randomUUID());
        de.setDataElementConcept(dec);
        de.setValueDomain(vd);
        when(dataElementRepository.findById(deId)).thenReturn(Optional.of(de));

        ItemAssociationsResponse res = service.listAssociations(deId);

        assertThat(res.itemType()).isEqualTo("DataElement");
        assertThat(res.associations()).hasSize(2);
        assertThat(res.associations()).anySatisfy(e -> {
            assertThat(e.relationType()).isEqualTo("DATA_ELEMENT_MEANING");
            assertThat(e.direction()).isEqualTo(ItemAssociationEntry.OUTGOING);
            assertThat(e.itemType()).isEqualTo("DataElementConcept");
            assertThat(e.itemId()).isEqualTo(dec.getId());
        });
        assertThat(res.associations()).anySatisfy(e -> {
            assertThat(e.relationType()).isEqualTo("DATA_ELEMENT_DOMAIN");
            assertThat(e.direction()).isEqualTo(ItemAssociationEntry.OUTGOING);
            assertThat(e.itemType()).isEqualTo("ValueDomain");
            assertThat(e.itemId()).isEqualTo(vd.getId());
        });
    }

    @Test
    void list_forDec_returnsOutgoingCdsAndIncomingDes() {
        UUID decId = UUID.randomUUID();
        DataElementConcept dec = withId(new DataElementConcept(), decId);
        ConceptualDomain cd = withId(new DescribedConceptualDomain(), UUID.randomUUID());
        dec.getConceptualDomains().add(cd);
        DataElement de = withId(new DataElement(), UUID.randomUUID());
        when(dataElementConceptRepository.findById(decId)).thenReturn(Optional.of(dec));
        when(dataElementRepository.findByDataElementConcept_Id(decId)).thenReturn(List.of(de));

        ItemAssociationsResponse res = service.listAssociations(decId);

        assertThat(res.itemType()).isEqualTo("DataElementConcept");
        assertThat(res.associations()).hasSize(2);
        assertThat(res.associations()).anySatisfy(e -> {
            assertThat(e.relationType()).isEqualTo("DATA_ELEMENT_CONCEPT_DOMAIN");
            assertThat(e.direction()).isEqualTo(ItemAssociationEntry.OUTGOING);
            assertThat(e.itemId()).isEqualTo(cd.getId());
        });
        assertThat(res.associations()).anySatisfy(e -> {
            assertThat(e.relationType()).isEqualTo("DATA_ELEMENT_MEANING");
            assertThat(e.direction()).isEqualTo(ItemAssociationEntry.INCOMING);
            assertThat(e.itemId()).isEqualTo(de.getId());
        });
    }

    @Test
    void list_forValueDomain_returnsOutgoingCdAndIncomingDes() {
        UUID vdId = UUID.randomUUID();
        LocalEnumeratedValueDomain vd = withId(new LocalEnumeratedValueDomain(), vdId);
        LocalEnumeratedConceptualDomain cd = withId(new LocalEnumeratedConceptualDomain(), UUID.randomUUID());
        vd.setLocalEnumeratedConceptualDomain(cd);
        DataElement de = withId(new DataElement(), UUID.randomUUID());
        when(valueDomainRepository.findById(vdId)).thenReturn(Optional.of(vd));
        when(dataElementRepository.findByValueDomain_Id(vdId)).thenReturn(List.of(de));

        ItemAssociationsResponse res = service.listAssociations(vdId);

        assertThat(res.itemType()).isEqualTo("ValueDomain");
        assertThat(res.associations()).hasSize(2);
        assertThat(res.associations()).anySatisfy(e -> {
            assertThat(e.relationType()).isEqualTo("VALUE_DOMAIN_MEANING");
            assertThat(e.direction()).isEqualTo(ItemAssociationEntry.OUTGOING);
            assertThat(e.itemId()).isEqualTo(cd.getId());
        });
        assertThat(res.associations()).anySatisfy(e -> {
            assertThat(e.relationType()).isEqualTo("DATA_ELEMENT_DOMAIN");
            assertThat(e.direction()).isEqualTo(ItemAssociationEntry.INCOMING);
            assertThat(e.itemId()).isEqualTo(de.getId());
        });
    }

    @Test
    void list_forConceptualDomain_returnsOutgoingDecsAndIncomingVdsAcrossSubtypes() {
        UUID cdId = UUID.randomUUID();
        DescribedConceptualDomain cd = withId(new DescribedConceptualDomain(), cdId);
        DataElementConcept dec = withId(new DataElementConcept(), UUID.randomUUID());
        cd.getDataElementConcepts().add(dec);
        DescribedValueDomain dvd = withId(new DescribedValueDomain(), UUID.randomUUID());
        LocalEnumeratedValueDomain levd = withId(new LocalEnumeratedValueDomain(), UUID.randomUUID());
        when(conceptualDomainRepository.findById(cdId)).thenReturn(Optional.of(cd));
        when(valueDomainRepository.findDescribedByConceptualDomainId(cdId)).thenReturn(List.of(dvd));
        when(valueDomainRepository.findLocalEnumeratedByConceptualDomainId(cdId)).thenReturn(List.of(levd));
        when(valueDomainRepository.findReferenceEnumeratedByConceptualDomainId(cdId)).thenReturn(List.of());

        ItemAssociationsResponse res = service.listAssociations(cdId);

        assertThat(res.itemType()).isEqualTo("ConceptualDomain");
        assertThat(res.associations()).hasSize(3);
        assertThat(res.associations()).anySatisfy(e -> {
            assertThat(e.relationType()).isEqualTo("DATA_ELEMENT_CONCEPT_DOMAIN");
            assertThat(e.direction()).isEqualTo(ItemAssociationEntry.OUTGOING);
            assertThat(e.itemId()).isEqualTo(dec.getId());
        });
        assertThat(res.associations().stream()
                .filter(e -> e.relationType().equals("VALUE_DOMAIN_MEANING")
                        && e.direction().equals(ItemAssociationEntry.INCOMING)))
                .hasSize(2);
    }

    @Test
    void list_unknownItem_throwsNotFound() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.listAssociations(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void associate_dataElementMeaning_setsDecOnDe() {
        UUID deId = UUID.randomUUID();
        UUID decId = UUID.randomUUID();
        DataElement de = withId(new DataElement(), deId);
        DataElementConcept dec = withId(new DataElementConcept(), decId);
        when(dataElementRepository.findById(deId)).thenReturn(Optional.of(de));
        when(dataElementConceptRepository.findById(decId)).thenReturn(Optional.of(dec));

        service.associate(StandardAssociationType.DATA_ELEMENT_MEANING,
                new AssociationMutationRequest(deId, decId));

        assertThat(de.getDataElementConcept()).isSameAs(dec);
        verify(dataElementRepository).save(de);
        verify(versionHistoryService).recordSnapshot(de, "UPDATE");
    }

    @Test
    void dissociate_dataElementDomain_clearsVdOnDe() {
        UUID deId = UUID.randomUUID();
        UUID vdId = UUID.randomUUID();
        DataElement de = withId(new DataElement(), deId);
        ValueDomain vd = withId(new DescribedValueDomain(), vdId);
        de.setValueDomain(vd);
        when(dataElementRepository.findById(deId)).thenReturn(Optional.of(de));
        when(valueDomainRepository.findById(vdId)).thenReturn(Optional.of(vd));

        service.dissociate(StandardAssociationType.DATA_ELEMENT_DOMAIN,
                new AssociationMutationRequest(deId, vdId));

        assertThat(de.getValueDomain()).isNull();
        verify(dataElementRepository).save(de);
    }

    @Test
    void associate_valueDomainMeaning_enforcesSubtypeConsistency() {
        UUID vdId = UUID.randomUUID();
        UUID cdId = UUID.randomUUID();
        LocalEnumeratedValueDomain vd = withId(new LocalEnumeratedValueDomain(), vdId);
        DescribedConceptualDomain cd = withId(new DescribedConceptualDomain(), cdId);
        when(valueDomainRepository.findById(vdId)).thenReturn(Optional.of(vd));
        when(conceptualDomainRepository.findById(cdId)).thenReturn(Optional.of(cd));

        assertThatThrownBy(() -> service.associate(StandardAssociationType.VALUE_DOMAIN_MEANING,
                new AssociationMutationRequest(vdId, cdId)))
                .isInstanceOf(MetadataAssociationValidationException.class);
    }

    @Test
    void associate_decConceptDomain_linksBothSides() {
        UUID decId = UUID.randomUUID();
        UUID cdId = UUID.randomUUID();
        DataElementConcept dec = withId(new DataElementConcept(), decId);
        ConceptualDomain cd = withId(new DescribedConceptualDomain(), cdId);
        when(dataElementConceptRepository.findById(decId)).thenReturn(Optional.of(dec));
        when(conceptualDomainRepository.findById(cdId)).thenReturn(Optional.of(cd));

        service.associate(StandardAssociationType.DATA_ELEMENT_CONCEPT_DOMAIN,
                new AssociationMutationRequest(decId, cdId));

        assertThat(cd.getDataElementConcepts()).contains(dec);
        assertThat(dec.getConceptualDomains()).contains(cd);
        verify(conceptualDomainRepository).save(cd);
    }

    @Test
    void dissociate_decConceptDomain_unlinksBothSides() {
        UUID decId = UUID.randomUUID();
        UUID cdId = UUID.randomUUID();
        DataElementConcept dec = withId(new DataElementConcept(), decId);
        ConceptualDomain cd = withId(new DescribedConceptualDomain(), cdId);
        cd.getDataElementConcepts().add(dec);
        dec.getConceptualDomains().add(cd);
        when(dataElementConceptRepository.findById(decId)).thenReturn(Optional.of(dec));
        when(conceptualDomainRepository.findById(cdId)).thenReturn(Optional.of(cd));

        service.dissociate(StandardAssociationType.DATA_ELEMENT_CONCEPT_DOMAIN,
                new AssociationMutationRequest(decId, cdId));

        assertThat(cd.getDataElementConcepts()).isEmpty();
        assertThat(dec.getConceptualDomains()).isEmpty();
        verify(conceptualDomainRepository).save(cd);
    }
}
