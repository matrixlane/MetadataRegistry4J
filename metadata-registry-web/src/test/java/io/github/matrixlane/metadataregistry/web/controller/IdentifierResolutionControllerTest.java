package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersionStatus;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.service.IdentifierResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IdentifierResolutionController.class)
@AutoConfigureMockMvc(addFilters = false)
class IdentifierResolutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentifierResolutionService resolutionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID NS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID II_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SI_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    void resolveGlobal_shouldReturnOk() throws Exception {
        when(resolutionService.resolveGlobally("urn:test:123", null))
                .thenReturn(Optional.of(createResponse()));

        mockMvc.perform(get("/api/v1/identifiers/resolve")
                        .param("identifier", "urn:test:123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identifier").value("urn:test:123"));
    }

    @Test
    void resolveGlobal_shouldReturnNullWhenNotFound() throws Exception {
        when(resolutionService.resolveGlobally("unknown", null))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/identifiers/resolve")
                        .param("identifier", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void resolveInNamespace_shouldReturnOk() throws Exception {
        when(resolutionService.resolve(eq(NS_ID), eq("id-1"), eq("1.0.0")))
                .thenReturn(Optional.of(createResponse()));

        mockMvc.perform(get("/api/v1/identifiers/namespaces/{namespaceId}/resolve", NS_ID)
                        .param("identifier", "id-1")
                        .param("version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identifier").value("urn:test:123"));
    }

    @Test
    void findByIdentifiedItem_shouldReturnList() throws Exception {
        when(resolutionService.findByIdentifiedItem(II_ID))
                .thenReturn(List.of(createResponse()));

        mockMvc.perform(get("/api/v1/identifiers/identified-items/{identifiedItemId}", II_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].identifier").value("urn:test:123"));
    }

    private ScopedIdentifierResponse createResponse() {
        return new ScopedIdentifierResponse(
                SI_ID, NS_ID, "test-ns", "urn:test:123", "1.0.0",
                VersionStatus.PUBLISHED, II_ID, "AdministeredItem", "Item Label",
                LocalDateTime.now(), LocalDateTime.now(), null, null,
                null, null);
    }
}
