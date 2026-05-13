package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.schemaextraction.javasource.JavaSourceParseException;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.javasource.*;
import io.github.matrixlane.metadataregistry.web.service.JavaSourceExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JavaSourceExtractionController.class)
@AutoConfigureMockMvc(addFilters = false)
class JavaSourceExtractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JavaSourceExtractionService extractionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final String TEST_CLASS = """
            package demo;
            /** A demo entity. */
            public class Demo {
                /** Name field. */
                private String name;
            }
            """;

    @Test
    void shouldExtractComments() throws Exception {
        var stats = new ExtractionStatisticsDto(1, 1, 1, 1.0, 1, 1, 1.0,
                0, 0, 0.0, 0, 0, 0.0, 0);
        var response = new JavaSourceExtractionResponse(List.of(), stats, List.of());
        when(extractionService.extract(any())).thenReturn(response);

        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("TEXT", TEST_CLASS, null);

        mockMvc.perform(post("/api/v1/java-source-extraction/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalFilesProcessed").doesNotExist());
    }

    @Test
    void shouldPreviewMapping() throws Exception {
        var response = new JavaSourceMappingPreviewResponse(
                List.of(), List.of(), List.of(), List.of(), List.of());
        when(extractionService.previewMapping(any())).thenReturn(response);

        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("TEXT", TEST_CLASS, null);

        mockMvc.perform(post("/api/v1/java-source-extraction/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRejectEmptyMode() throws Exception {
        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("", TEST_CLASS, null);

        mockMvc.perform(post("/api/v1/java-source-extraction/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void shouldReturnBadRequestOnJavaSourceParseException() throws Exception {
        when(extractionService.extract(any()))
                .thenThrow(new JavaSourceParseException("sourceContent is required for TEXT mode"));

        JavaSourceExtractionRequest request = new JavaSourceExtractionRequest("TEXT", TEST_CLASS, null);

        mockMvc.perform(post("/api/v1/java-source-extraction/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("sourceContent is required for TEXT mode"));
    }
}
