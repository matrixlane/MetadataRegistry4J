package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisResult;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MappingImpactController.class)
@AutoConfigureMockMvc(addFilters = false)
class MappingImpactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImpactAnalysisService impactAnalysisService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    @Test
    void analyzeItemImpact_shouldReturnOk() throws Exception {
        UUID itemId = UUID.randomUUID();
        ImpactAnalysisResult result = new ImpactAnalysisResult(
                itemId, "UPDATED", List.of(), 0, false);

        when(impactAnalysisService.analyzeItemChange(eq(itemId), any()))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/mappings/impact/item")
                        .param("itemId", itemId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.changedItemId").value(itemId.toString()));
    }

    @Test
    void analyzeMappingImpact_shouldReturnOk() throws Exception {
        UUID mappingId = UUID.randomUUID();
        ImpactAnalysisResult result = new ImpactAnalysisResult(
                mappingId, "DELETED", List.of(), 0, false);

        when(impactAnalysisService.analyzeMappingChange(eq(mappingId), any()))
                .thenReturn(result);

        mockMvc.perform(get("/api/v1/mappings/impact/mapping")
                        .param("mappingId", mappingId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.changedItemId").value(mappingId.toString()));
    }
}
