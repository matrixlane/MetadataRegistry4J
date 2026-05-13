package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.itemmapping.quality.MappingQualityMetrics;
import io.github.matrixlane.metadataregistry.itemmapping.quality.MappingQualityReport;
import io.github.matrixlane.metadataregistry.itemmapping.quality.MappingQualityService;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MappingQualityController.class)
@AutoConfigureMockMvc(addFilters = false)
class MappingQualityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MappingQualityService mappingQualityService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    @Test
    void getQualityReport_shouldReturnOk() throws Exception {
        UUID schemeId = UUID.randomUUID();
        MappingQualityMetrics metrics = new MappingQualityMetrics(
                80.0, 90.0, 100.0, 95.0, 90.0, 10, 8, 10, 0);
        MappingQualityReport report = new MappingQualityReport(
                schemeId, "Test Scheme", metrics,
                List.of(), List.of(), List.of("Good quality"), "2024-01-01T00:00:00");

        when(mappingQualityService.generateReport(eq(schemeId))).thenReturn(report);

        mockMvc.perform(get("/api/v1/mapping-schemes/{schemeId}/quality/report", schemeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.schemeId").value(schemeId.toString()))
                .andExpect(jsonPath("$.data.metrics.overallScore").value(90.0));
    }
}
