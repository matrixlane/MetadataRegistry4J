package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceResponse;
import io.github.matrixlane.metadataregistry.web.service.NamespaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NamespaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class NamespaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NamespaceService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID NS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RA_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final List<UUID> EMPTY_NC = List.of();

    @Test
    void list_shouldReturnOk() throws Exception {
        NamespaceResponse resp = createResponse();
        Page<NamespaceResponse> page = new PageImpl<>(List.of(resp));
        when(service.list(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/namespaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(NS_ID.toString()));
    }

    @Test
    void list_shouldPassKeyword() throws Exception {
        NamespaceResponse resp = createResponse();
        Page<NamespaceResponse> page = new PageImpl<>(List.of(resp));
        when(service.list(eq("test"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/namespaces").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(NS_ID.toString()));
    }

    @Test
    void get_shouldReturnOk() throws Exception {
        when(service.get(NS_ID)).thenReturn(createResponse());

        mockMvc.perform(get("/api/v1/namespaces/{id}", NS_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NS_ID.toString()))
                .andExpect(jsonPath("$.data.label").value("Test Namespace"));
    }

    @Test
    void create_shouldReturnCreated() throws Exception {
        NamespaceRequest req = new NamespaceRequest(RA_ID,
                List.of(new TextLine(UUID.randomUUID(), "Test Namespace", null, null)),
                null, null, null, null, EMPTY_NC, null, null, null, null, null, null, null, null, null, null, null);
        when(service.create(any())).thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/namespaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NS_ID.toString()));
    }

    @Test
    void create_shouldRejectEmptyDesignations() throws Exception {
        NamespaceRequest req = new NamespaceRequest(RA_ID, List.of(), null, null,
                null, null, EMPTY_NC, null, null, null, null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/namespaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        NamespaceRequest req = new NamespaceRequest(RA_ID,
                List.of(new TextLine(UUID.randomUUID(), "Updated", null, null)),
                null, null, null, null, EMPTY_NC, null, null, null, null, null, null, null, null, null, null, null);
        when(service.update(eq(NS_ID), any())).thenReturn(createResponse());

        mockMvc.perform(put("/api/v1/namespaces/{id}", NS_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NS_ID.toString()));
    }

    @Test
    void delete_shouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/v1/namespaces/{id}", NS_ID))
                .andExpect(status().isOk());
    }

    private NamespaceResponse createResponse() {
        return new NamespaceResponse(
                NS_ID, "Test Namespace", RA_ID, "Test RA",
                RA_ID,
                List.of(), List.of(),
                null, null, null, EMPTY_NC, null, null, null, null, null, null, null, null, null, null, null,
                "OWN");
    }
}
