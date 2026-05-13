package io.github.matrixlane.metadataregistry.web.controller.fhir;

import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.*;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirComplianceService;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirProfileSuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FhirComplianceController.class)
@AutoConfigureMockMvc(addFilters = false)
class FhirComplianceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private FhirComplianceService complianceService;
    @MockBean private FhirProfileSuggestionService profileSuggestionService;
    @MockBean private JwtService jwtService;
    @MockBean private AppProperties appProperties;

    private static final UUID RID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPORT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void checkCompliance_shouldReturnReport() throws Exception {
        ComplianceReportResponse report = buildReport();
        when(complianceService.checkCompliance(RID, null)).thenReturn(report);

        String json = "{\"resourceId\":\"" + RID + "\"}";
        mockMvc.perform(post("/api/v1/fhir/compliance/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.data.resourceName").value("Patient"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.coverageMetrics.coveragePercentage").value(50.0));
    }

    @Test
    void getReport_shouldReturnReport() throws Exception {
        ComplianceReportResponse report = buildReport();
        when(complianceService.getReport(REPORT_ID)).thenReturn(report);

        mockMvc.perform(get("/api/v1/fhir/compliance/report/{id}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.data.resourceName").value("Patient"));
    }

    @Test
    void getReport_shouldReturn404WhenNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(complianceService.getReport(unknown)).thenThrow(new ResourceNotFoundException("ComplianceReport", unknown));

        mockMvc.perform(get("/api/v1/fhir/compliance/report/{id}", unknown))
                .andExpect(status().isNotFound());
    }

    @Test
    void generateProfile_shouldReturnProfile() throws Exception {
        ProfileSuggestionResponse profile = ProfileSuggestionResponse.builder()
                .profileId(UUID.randomUUID())
                .resourceId(RID)
                .status("DRAFT")
                .profileJson("{\"resourceType\":\"StructureDefinition\"}")
                .warnings(List.of("Draft warning"))
                .build();
        when(profileSuggestionService.generateProfile(any())).thenReturn(profile);

        String json = "{\"resourceId\":\"" + RID + "\",\"includeExtensions\":true}";
        mockMvc.perform(post("/api/v1/fhir/profile/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.profileJson").isNotEmpty())
                .andExpect(jsonPath("$.data.disclaimer").exists());
    }

    private ComplianceReportResponse buildReport() {
        return ComplianceReportResponse.builder()
                .reportId(REPORT_ID)
                .resourceId(RID)
                .resourceName("Patient")
                .resourceUrl("http://hl7.org/fhir/StructureDefinition/Patient")
                .generatedAt(LocalDateTime.now())
                .status("DRAFT")
                .coverageMetrics(CoverageMetrics.builder()
                        .totalElements(4)
                        .coveredElements(2)
                        .uncoveredElements(2)
                        .localExtraFields(0)
                        .coveragePercentage(50.0)
                        .requiredElementCount(1)
                        .coveredRequiredCount(0)
                        .requiredCoveragePercentage(0.0)
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
                                .fhirPath("Patient.birthDate")
                                .elementType("date")
                                .required(true)
                                .priority("HIGH")
                                .build()
                ))
                .localExtraFields(List.of())
                .warnings(List.of("映射覆盖率低于50%"))
                .build();
    }
}
