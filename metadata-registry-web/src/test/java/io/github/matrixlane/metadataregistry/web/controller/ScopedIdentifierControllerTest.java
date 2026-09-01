package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.ext.identification.VersionStatus;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierCreateRequest;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.VersionTransitionRequest;
import io.github.matrixlane.metadataregistry.web.service.ScopedIdentifierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScopedIdentifierController.class)
@AutoConfigureMockMvc(addFilters = false)
class ScopedIdentifierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScopedIdentifierService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID TEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NS_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID II_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void list_shouldReturnOk() throws Exception {
        ScopedIdentifierResponse resp = createResponse();
        Page<ScopedIdentifierResponse> page = new PageImpl<>(List.of(resp));
        when(service.list(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/scoped-identifiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].identifier").value("id-1"));
    }

    @Test
    void get_shouldReturnOk() throws Exception {
        when(service.get(TEST_ID)).thenReturn(createResponse());

        mockMvc.perform(get("/api/v1/scoped-identifiers/{id}", TEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identifier").value("id-1"));
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        ScopedIdentifierCreateRequest req = new ScopedIdentifierCreateRequest(
                NS_ID, "custom-id", "1.0.0", II_ID, "Initial");
        when(service.create(any())).thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/scoped-identifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identifier").value("id-1"));
    }

    @Test
    void create_shouldRejectInvalidVersion() throws Exception {
        ScopedIdentifierCreateRequest req = new ScopedIdentifierCreateRequest(
                NS_ID, "id", "bad-version", II_ID, null);

        mockMvc.perform(post("/api/v1/scoped-identifiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        ScopedIdentifierCreateRequest req = new ScopedIdentifierCreateRequest(
                NS_ID, "id", "2.0.0", II_ID, null);
        when(service.update(eq(TEST_ID), any())).thenReturn(createResponse());

        mockMvc.perform(put("/api/v1/scoped-identifiers/{id}", TEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void transition_shouldReturnOk() throws Exception {
        VersionTransitionRequest req = new VersionTransitionRequest(VersionStatus.PUBLISHED, "Ready");
        when(service.transition(eq(TEST_ID), any())).thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/scoped-identifiers/{id}/transition", TEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_shouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/v1/scoped-identifiers/{id}", TEST_ID))
                .andExpect(status().isOk());
    }

    @Test
    void history_shouldReturnOk() throws Exception {
        when(service.listHistory(TEST_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/scoped-identifiers/{id}/history", TEST_ID))
                .andExpect(status().isOk());
    }

    private ScopedIdentifierResponse createResponse() {
        return new ScopedIdentifierResponse(
                TEST_ID, NS_ID, "test-ns", "id-1", "1.0.0",
                VersionStatus.DRAFT, II_ID, "AdministeredItem", "Item Label",
                LocalDateTime.now(), null, null, null,
                null, null);
    }
}
