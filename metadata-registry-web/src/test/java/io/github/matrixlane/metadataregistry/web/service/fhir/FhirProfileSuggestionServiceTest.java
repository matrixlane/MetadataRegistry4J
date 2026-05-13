package io.github.matrixlane.metadataregistry.web.service.fhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.*;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirElement;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
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
class FhirProfileSuggestionServiceTest {

    @Mock FhirResourceRepository resourceRepository;
    @Mock FhirElementRepository elementRepository;
    @Mock FhirComplianceService complianceService;

    private FhirProfileSuggestionService service;
    private ObjectMapper objectMapper;

    private UUID patientResourceId;
    private UUID reportId;
    private FhirResource patientResource;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new FhirProfileSuggestionService(resourceRepository, elementRepository, complianceService, objectMapper);
        patientResourceId = UUID.randomUUID();
        reportId = UUID.randomUUID();

        patientResource = new FhirResource();
        patientResource.setId(patientResourceId);
        patientResource.setName("Patient");
        patientResource.setUrl("http://hl7.org/fhir/StructureDefinition/Patient");
        patientResource.setType("Patient");
        patientResource.setKind("resource");
        patientResource.setFhirVersion("R4");
    }

    @Test
    void generateProfile_shouldProduceDraftProfile() throws Exception {
        ComplianceReportResponse report = createMockReport();
        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(complianceService.checkCompliance(patientResourceId)).thenReturn(report);
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId)).thenReturn(List.of());

        ProfileSuggestionRequest request = ProfileSuggestionRequest.builder()
                .resourceId(patientResourceId)
                .includeExtensions(true)
                .build();

        ProfileSuggestionResponse response = service.generateProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("DRAFT");
        assertThat(response.getProfileJson()).isNotBlank();
        assertThat(response.getDisclaimer()).contains("草稿");

        // Verify it's valid JSON and contains StructureDefinition fields
        var json = objectMapper.readTree(response.getProfileJson());
        assertThat(json.get("resourceType").asText()).isEqualTo("StructureDefinition");
        assertThat(json.get("status").asText()).isEqualTo("draft");
        assertThat(json.get("derivation").asText()).isEqualTo("constraint");
        assertThat(json.has("differential")).isTrue();
    }

    @Test
    void generateProfile_shouldUseExistingReportWhenProvided() throws Exception {
        ComplianceReportResponse report = createMockReport();
        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(complianceService.getReport(reportId)).thenReturn(report);
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId)).thenReturn(List.of());

        ProfileSuggestionRequest request = ProfileSuggestionRequest.builder()
                .resourceId(patientResourceId)
                .reportId(reportId)
                .includeExtensions(false)
                .build();

        ProfileSuggestionResponse response = service.generateProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.getProfileJson()).isNotBlank();
    }

    @Test
    void generateProfile_shouldIncludeMustSupportForUncoveredRequired() throws Exception {
        ComplianceReportResponse report = ComplianceReportResponse.builder()
                .reportId(reportId)
                .resourceId(patientResourceId)
                .coverageMetrics(CoverageMetrics.builder().coveragePercentage(50.0).build())
                .uncoveredElements(List.of(
                        ElementGap.builder()
                                .fhirPath("Patient.birthDate")
                                .elementType("date")
                                .required(true)
                                .shortDescription("Birth date")
                                .build()
                ))
                .coveredElements(List.of())
                .localExtraFields(List.of())
                .build();

        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(complianceService.checkCompliance(patientResourceId)).thenReturn(report);
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId)).thenReturn(List.of());

        ProfileSuggestionRequest request = ProfileSuggestionRequest.builder()
                .resourceId(patientResourceId)
                .build();

        ProfileSuggestionResponse response = service.generateProfile(request);
        var json = objectMapper.readTree(response.getProfileJson());
        var diff = json.get("differential");

        assertThat(diff.isArray()).isTrue();
        boolean foundMustSupport = false;
        for (var elem : diff) {
            if ("Patient.birthDate".equals(elem.get("path").asText())) {
                assertThat(elem.get("mustSupport").asBoolean()).isTrue();
                assertThat(elem.get("min").asInt()).isEqualTo(1);
                foundMustSupport = true;
            }
        }
        assertThat(foundMustSupport).isTrue();
    }

    @Test
    void generateProfile_shouldIncludeExtensionSuggestions() throws Exception {
        ComplianceReportResponse report = ComplianceReportResponse.builder()
                .reportId(reportId)
                .resourceId(patientResourceId)
                .coverageMetrics(CoverageMetrics.builder().coveragePercentage(60.0).build())
                .uncoveredElements(List.of())
                .coveredElements(List.of())
                .localExtraFields(List.of(
                        LocalExtraField.builder()
                                .localItemId(UUID.randomUUID())
                                .localItemName("患者来源")
                                .localItemType("string")
                                .suggestedExtensionUrl("http://example.org/fhir/StructureDefinition/patient-source")
                                .build()
                ))
                .build();

        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(complianceService.checkCompliance(patientResourceId)).thenReturn(report);
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId)).thenReturn(List.of());

        ProfileSuggestionRequest request = ProfileSuggestionRequest.builder()
                .resourceId(patientResourceId)
                .includeExtensions(true)
                .build();

        ProfileSuggestionResponse response = service.generateProfile(request);

        assertThat(response.getSuggestedExtensions()).isNotNull();
        assertThat(response.getSuggestedExtensions()).hasSize(1);
        ExtensionSuggestion ext = response.getSuggestedExtensions().get(0);
        assertThat(ext.getLocalItemName()).isEqualTo("患者来源");
        assertThat(ext.getReason()).contains("Extension");
    }

    @Test
    void generateProfile_shouldIncludeWarnings() throws Exception {
        ComplianceReportResponse report = ComplianceReportResponse.builder()
                .reportId(reportId)
                .resourceId(patientResourceId)
                .coverageMetrics(CoverageMetrics.builder().coveragePercentage(30.0).build())
                .uncoveredElements(List.of())
                .coveredElements(List.of())
                .localExtraFields(List.of())
                .build();

        when(resourceRepository.findById(patientResourceId)).thenReturn(Optional.of(patientResource));
        when(complianceService.checkCompliance(patientResourceId)).thenReturn(report);
        when(elementRepository.findByResourceIdOrderByElementPath(patientResourceId)).thenReturn(List.of());

        ProfileSuggestionRequest request = ProfileSuggestionRequest.builder()
                .resourceId(patientResourceId)
                .build();

        ProfileSuggestionResponse response = service.generateProfile(request);

        assertThat(response.getWarnings()).isNotEmpty();
        assertThat(response.getWarnings()).anyMatch(w -> w.contains("草稿"));
        assertThat(response.getWarnings()).anyMatch(w -> w.contains("覆盖率较低"));
    }

    private ComplianceReportResponse createMockReport() {
        return ComplianceReportResponse.builder()
                .reportId(reportId)
                .resourceId(patientResourceId)
                .coverageMetrics(CoverageMetrics.builder()
                        .totalElements(4)
                        .coveredElements(2)
                        .uncoveredElements(1)
                        .localExtraFields(1)
                        .coveragePercentage(50.0)
                        .requiredElementCount(1)
                        .coveredRequiredCount(1)
                        .requiredCoveragePercentage(100.0)
                        .build())
                .typeCompatibilitySummary(TypeCompatibilitySummary.builder()
                        .totalChecked(2)
                        .compatibleCount(2)
                        .incompatibleCount(0)
                        .unknownCount(0)
                        .build())
                .coveredElements(List.of(
                        ElementCoverage.builder()
                                .fhirPath("Patient.name")
                                .elementType("string")
                                .typeCompatible(true)
                                .build()
                ))
                .uncoveredElements(List.of(
                        ElementGap.builder()
                                .fhirPath("Patient.photo")
                                .elementType("Attachment")
                                .required(false)
                                .build()
                ))
                .localExtraFields(List.of())
                .build();
    }
}
