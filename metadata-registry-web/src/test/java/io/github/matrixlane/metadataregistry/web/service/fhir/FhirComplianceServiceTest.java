package io.github.matrixlane.metadataregistry.web.service.fhir;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.*;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.fhir.FhirResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FhirComplianceServiceTest {

    @Mock FhirResourceRepository resourceRepository;
    @Mock FhirElementRepository elementRepository;
    @Mock AdministeredItemRepository administeredItemRepository;

    private FhirComplianceService service;

    private UUID patientResourceId;
    private UUID rootElementId;
    private UUID nameElementId;
    private UUID genderElementId;
    private UUID birthDateElementId;
    private UUID photoElementId;
    private UUID localItemId;
    private FhirResource patientResource;

    @BeforeEach
    void setUp() {
        service = new FhirComplianceService(resourceRepository, elementRepository, administeredItemRepository);
        patientResourceId = UUID.randomUUID();
        rootElementId = UUID.randomUUID();
        nameElementId = UUID.randomUUID();
        genderElementId = UUID.randomUUID();
        birthDateElementId = UUID.randomUUID();
        photoElementId = UUID.randomUUID();
        localItemId = UUID.randomUUID();

        patientResource = new FhirResource();
        patientResource.setId(patientResourceId);
        patientResource.setName("Patient");
        patientResource.setUrl("http://hl7.org/fhir/StructureDefinition/Patient");
        patientResource.setFhirVersion("R4");
    }

    @Test
    void checkCompliance_shouldThrowWhenResourceNotFound() {
        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkCompliance(patientResourceId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void checkCompliance_shouldThrowWhenResourceHasNoElements() {
        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId)).thenReturn(List.of());
        assertThatThrownBy(() -> service.checkCompliance(patientResourceId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no elements");
    }

    @Test
    void checkCompliance_shouldGenerateCorrectCoverageMetrics() {
        // Setup: Patient resource with 4 child elements
        // - name (mapped)
        // - gender (mapped)
        // - birthDate (unmapped, required min=1)
        // - photo (unmapped, optional min=0)
        FhirElement root = createElement(rootElementId, null, "Patient", null, false, 1);
        FhirElement name = createElement(nameElementId, root, "Patient.name", "string", true, 0);
        name.setAdministeredItemId(localItemId);
        FhirElement gender = createElement(genderElementId, root, "Patient.gender", "code", true, 0);
        gender.setAdministeredItemId(localItemId);
        FhirElement birthDate = createElement(birthDateElementId, root, "Patient.birthDate", "date", false, 1);
        FhirElement photo = createElement(photoElementId, root, "Patient.photo", "Attachment", false, 0);

        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId))
                .thenReturn(List.of(root, name, gender, birthDate, photo));
        when(elementRepository.findAllMappedAdministeredItemIds()).thenReturn(List.of(localItemId));

        AdministeredItem localItem = createAdministeredItem(localItemId, "PatientName");
        when(administeredItemRepository.findAllById(any())).thenReturn(List.of(localItem));

        ComplianceReportResponse report = service.checkCompliance(patientResourceId);

        assertThat(report).isNotNull();
        assertThat(report.getResourceId()).isEqualTo(patientResourceId);
        assertThat(report.getStatus()).isEqualTo("DRAFT");
        assertThat(report.getDisclaimer()).isNotNull();

        CoverageMetrics metrics = report.getCoverageMetrics();
        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalElements()).isEqualTo(4); // excluding root
        assertThat(metrics.getCoveredElements()).isEqualTo(2);
        assertThat(metrics.getUncoveredElements()).isEqualTo(2);
        assertThat(metrics.getCoveragePercentage()).isEqualTo(50.0);
        assertThat(metrics.getRequiredElementCount()).isEqualTo(1); // birthDate
        assertThat(metrics.getCoveredRequiredCount()).isEqualTo(0);
        assertThat(metrics.getRequiredCoveragePercentage()).isEqualTo(0.0);
    }

    @Test
    void checkCompliance_shouldClassifyUncoveredElements() {
        FhirElement root = createElement(rootElementId, null, "Patient", null, false, 1);
        FhirElement name = createElement(nameElementId, root, "Patient.name", "string", true, 0);
        name.setAdministeredItemId(localItemId);
        FhirElement birthDate = createElement(birthDateElementId, root, "Patient.birthDate", "date", false, 1);

        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId))
                .thenReturn(List.of(root, name, birthDate));
        when(elementRepository.findAllMappedAdministeredItemIds()).thenReturn(List.of());
        when(administeredItemRepository.findAllById(any())).thenReturn(List.of());

        ComplianceReportResponse report = service.checkCompliance(patientResourceId);

        assertThat(report.getUncoveredElements()).hasSize(1);
        ElementGap gap = report.getUncoveredElements().get(0);
        assertThat(gap.getFhirPath()).isEqualTo("Patient.birthDate");
        assertThat(gap.isRequired()).isTrue();
        assertThat(gap.getPriority()).isEqualTo("HIGH");
    }

    @Test
    void checkCompliance_shouldIncludeCoveredElementsWithTypeCompatibility() {
        FhirElement root = createElement(rootElementId, null, "Patient", null, false, 1);
        FhirElement name = createElement(nameElementId, root, "Patient.name", "string", true, 0);
        name.setAdministeredItemId(localItemId);

        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId))
                .thenReturn(List.of(root, name));
        when(elementRepository.findAllMappedAdministeredItemIds()).thenReturn(List.of());
        when(administeredItemRepository.findAllById(any())).thenReturn(List.of());

        ComplianceReportResponse report = service.checkCompliance(patientResourceId);

        assertThat(report.getCoveredElements()).hasSize(1);
        ElementCoverage coverage = report.getCoveredElements().get(0);
        assertThat(coverage.getFhirPath()).isEqualTo("Patient.name");
        assertThat(coverage.getTypeCompatible()).isTrue(); // string compatible
    }

    @Test
    void checkCompliance_shouldIncludeWarningsForLowCoverage() {
        FhirElement root = createElement(rootElementId, null, "Patient", null, false, 1);
        FhirElement name = createElement(nameElementId, root, "Patient.name", "string", false, 0);
        FhirElement gender = createElement(genderElementId, root, "Patient.gender", "code", false, 0);
        FhirElement birthDate = createElement(birthDateElementId, root, "Patient.birthDate", "date", false, 1);

        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId))
                .thenReturn(List.of(root, name, gender, birthDate));
        when(elementRepository.findAllMappedAdministeredItemIds()).thenReturn(List.of());
        when(administeredItemRepository.findAllById(any())).thenReturn(List.of());

        ComplianceReportResponse report = service.checkCompliance(patientResourceId);

        assertThat(report.getWarnings()).isNotEmpty();
        assertThat(report.getWarnings()).anyMatch(w -> w.contains("覆盖率低于50%"));
        assertThat(report.getWarnings()).anyMatch(w -> w.contains("必填元素"));
    }

    @Test
    void getReport_shouldReturnCachedReport() {
        // First generate a report
        FhirElement root = createElement(rootElementId, null, "Patient", null, false, 1);
        FhirElement name = createElement(nameElementId, root, "Patient.name", "string", true, 0);
        name.setAdministeredItemId(localItemId);

        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId))
                .thenReturn(List.of(root, name));
        when(elementRepository.findAllMappedAdministeredItemIds()).thenReturn(List.of());
        when(administeredItemRepository.findAllById(any())).thenReturn(List.of());

        ComplianceReportResponse report = service.checkCompliance(patientResourceId);
        UUID reportId = report.getReportId();

        // Retrieve it
        ComplianceReportResponse cached = service.getReport(reportId);
        assertThat(cached).isNotNull();
        assertThat(cached.getReportId()).isEqualTo(reportId);
    }

    @Test
    void getReport_shouldThrowWhenNotFound() {
        UUID randomId = UUID.randomUUID();
        assertThatThrownBy(() -> service.getReport(randomId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- helpers ----

    private FhirElement createElement(UUID id, FhirElement parent, String path, String type, boolean mapped, int min) {
        FhirElement e = new FhirElement();
        e.setId(id);
        e.setResource(patientResource);
        e.setParent(parent);
        e.setPath(path);
        e.setElementPath(path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path);
        e.setElementType(type);
        e.setMin(min);
        e.setMax("1");
        e.setFhirVersion("R4");
        e.setImportedAt(LocalDateTime.now());
        if (mapped) {
            e.setAdministeredItemId(localItemId);
        }
        return e;
    }

    private AdministeredItem createAdministeredItem(UUID id, String nameHint) {
        Item item = new Item() {};
        AdministeredItem ai = new AdministeredItem(item);
        ai.setId(id);
        return ai;
    }
}
