package io.github.matrixlane.metadataregistry.web.service.fhir;

import io.github.matrixlane.metadataregistry.itemmapping.entity.FhirElementMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.FhirElementMappingRepository;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirConceptMapExportResult;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirValidationError;
import org.hl7.fhir.r4.model.ConceptMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirElementMappingExportServiceTest {

    @Mock
    private FhirElementMappingRepository mappingRepository;

    private FhirElementMappingExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new FhirElementMappingExportService(mappingRepository);
    }

    @Test
    void exportAllMappings_empty_shouldReturnFailure() {
        when(mappingRepository.findAll()).thenReturn(Collections.emptyList());

        FhirConceptMapExportResult result = exportService.exportAllMappings();

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("No FHIR element mappings found");
    }

    @Test
    void exportByResourceType_empty_shouldReturnFailure() {
        when(mappingRepository.findByFhirResourceType("Patient"))
                .thenReturn(Collections.emptyList());

        FhirConceptMapExportResult result = exportService.exportByResourceType("Patient");

        assertThat(result.success()).isFalse();
    }

    @Test
    void exportByScheme_empty_shouldReturnFailure() {
        UUID schemeId = UUID.randomUUID();
        when(mappingRepository.findByMappingSchemeId(schemeId))
                .thenReturn(Collections.emptyList());

        FhirConceptMapExportResult result = exportService.exportByScheme(schemeId);

        assertThat(result.success()).isFalse();
    }

    @Test
    void doExport_singleMapping_shouldProduceValidConceptMap() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.given", "Patient",
                FhirMappingEquivalence.EQUIVALENT,
                FhirMappingType.ONE_TO_ONE, null, "Given name", null
        );

        FhirConceptMapExportResult result = exportService.doExport(List.of(mapping), "test");

        assertThat(result.success()).isTrue();
        assertThat(result.mappingCount()).isEqualTo(1);
        assertThat(result.elementCount()).isEqualTo(1);
        assertThat(result.conceptMapJson()).contains("ConceptMap");
        assertThat(result.conceptMapJson()).contains("Patient.name.given");
        assertThat(result.conceptMapJson()).contains(itemId.toString());
    }

    @Test
    void doExport_multipleResourceTypes_shouldGroupByResourceType() {
        UUID item1 = UUID.randomUUID();
        UUID item2 = UUID.randomUUID();

        FhirElementMapping m1 = new FhirElementMapping(
                item1, "Patient.name.given", "Patient", FhirMappingEquivalence.EQUIVALENT);
        FhirElementMapping m2 = new FhirElementMapping(
                item2, "Observation.valueString", "Observation", FhirMappingEquivalence.EQUAL);

        FhirConceptMapExportResult result = exportService.doExport(List.of(m1, m2), "test");

        assertThat(result.success()).isTrue();
        assertThat(result.mappingCount()).isEqualTo(2);
        assertThat(result.elementCount()).isEqualTo(2);

        // Should contain both resource types
        assertThat(result.conceptMapJson()).contains("Patient");
        assertThat(result.conceptMapJson()).contains("Observation");
    }

    @Test
    void doExport_oneToMany_shouldIncludeExtension() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.family", "Patient",
                FhirMappingEquivalence.EQUIVALENT,
                FhirMappingType.ONE_TO_MANY, "family", "Family name", null
        );

        FhirConceptMapExportResult result = exportService.doExport(List.of(mapping), "test");

        assertThat(result.success()).isTrue();
        assertThat(result.conceptMapJson()).contains("family");
        assertThat(result.conceptMapJson()).contains("mapping-variable");
    }

    @Test
    void doExport_shouldValidateWithoutFatalErrors() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.birthDate", "Patient", FhirMappingEquivalence.EQUAL);

        FhirConceptMapExportResult result = exportService.doExport(List.of(mapping), "test");

        assertThat(result.success()).isTrue();
        // Validation may have warnings but should not have fatal errors for a valid ConceptMap
        if (!result.validationErrors().isEmpty()) {
            assertThat(result.validationErrors())
                    .extracting(FhirValidationError::severity)
                    .doesNotContain("fatal");
        }
    }

    @Test
    void buildConceptMap_shouldSetMetadata() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.gender", "Patient", FhirMappingEquivalence.EQUIVALENT);

        ConceptMap conceptMap = exportService.buildConceptMap(List.of(mapping), "Patient");

        assertThat(conceptMap.getId()).contains("Patient");
        assertThat(conceptMap.getUrl()).contains("Patient");
        assertThat(conceptMap.getName()).isNotNull();
        assertThat(conceptMap.getStatus()).isEqualTo(org.hl7.fhir.r4.model.Enumerations.PublicationStatus.DRAFT);
        assertThat(conceptMap.getDescription()).contains("ISO/IEC 11179");
    }

    @Test
    void buildConceptMap_shouldCreateCorrectGroupStructure() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.identifier.system", "Patient", FhirMappingEquivalence.EQUIVALENT);

        ConceptMap conceptMap = exportService.buildConceptMap(List.of(mapping), "Patient");

        assertThat(conceptMap.getGroup()).hasSize(1);
        ConceptMap.ConceptMapGroupComponent group = conceptMap.getGroup().get(0);
        assertThat(group.getTarget()).contains("StructureDefinition/Patient");
        assertThat(group.getElement()).hasSize(1);

        ConceptMap.SourceElementComponent element = group.getElement().get(0);
        assertThat(element.getCode()).isEqualTo(itemId.toString());

        ConceptMap.TargetElementComponent target = element.getTarget().get(0);
        assertThat(target.getCode()).isEqualTo("Patient.identifier.system");
        assertThat(target.getEquivalence()).isEqualTo(org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence.EQUIVALENT);
    }

    @Test
    void buildConceptMap_withComment_shouldIncludeComment() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.name.text", "Patient",
                FhirMappingEquivalence.RELATEDTO,
                FhirMappingType.ONE_TO_ONE, null, "Full text name", null);

        ConceptMap conceptMap = exportService.buildConceptMap(List.of(mapping), "Patient");

        ConceptMap.TargetElementComponent target = conceptMap.getGroup().get(0).getElement().get(0).getTarget().get(0);
        assertThat(target.getComment()).isEqualTo("Full text name");
    }

    @Test
    void doExport_equivalenceWider_shouldMapCorrectly() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.address.city", "Patient", FhirMappingEquivalence.WIDER);

        FhirConceptMapExportResult result = exportService.doExport(List.of(mapping), "test");

        assertThat(result.success()).isTrue();
        assertThat(result.conceptMapJson()).contains("wider");
    }

    @Test
    void doExport_equivalenceNarrower_shouldMapCorrectly() {
        UUID itemId = UUID.randomUUID();
        FhirElementMapping mapping = new FhirElementMapping(
                itemId, "Patient.address.line", "Patient", FhirMappingEquivalence.NARROWER);

        FhirConceptMapExportResult result = exportService.doExport(List.of(mapping), "test");

        assertThat(result.success()).isTrue();
        assertThat(result.conceptMapJson()).contains("narrower");
    }
}
