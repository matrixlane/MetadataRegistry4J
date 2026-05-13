package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeStatusTransitionRequest;
import io.github.matrixlane.metadataregistry.web.service.MappingSchemeService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MappingSchemeController.class)
@AutoConfigureMockMvc(addFilters = false)
class MappingSchemeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MappingSchemeService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID SCHEME_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 13, 9, 0);

    private MappingSchemeResponse createResponse() {
        return new MappingSchemeResponse(
                SCHEME_ID,
                "Test Scheme",
                "http://source.example.com",
                "http://target.example.com",
                "Test description",
                "0.1.0",
                MappingSchemeStatus.DRAFT,
                null,
                NOW,
                NOW
        );
    }

    @Test
    void list_shouldReturnOk() throws Exception {
        Page<MappingSchemeResponse> page = new PageImpl<>(List.of(createResponse()));
        when(service.list(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/mapping-schemes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(SCHEME_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].name").value("Test Scheme"))
                .andExpect(jsonPath("$.data.content[0].version").value("0.1.0"))
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));
    }

    @Test
    void list_shouldPassKeyword() throws Exception {
        Page<MappingSchemeResponse> page = new PageImpl<>(List.of(createResponse()));
        when(service.list(eq("test"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/mapping-schemes").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Test Scheme"));
    }

    @Test
    void get_shouldReturnOk() throws Exception {
        when(service.get(SCHEME_ID)).thenReturn(createResponse());

        mockMvc.perform(get("/api/v1/mapping-schemes/{id}", SCHEME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SCHEME_ID.toString()))
                .andExpect(jsonPath("$.data.name").value("Test Scheme"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void create_shouldReturnOk() throws Exception {
        MappingSchemeRequest req = new MappingSchemeRequest(
                "New Scheme", "http://src", "http://dst", "desc");
        when(service.create(any())).thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/mapping-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SCHEME_ID.toString()));
    }

    @Test
    void create_shouldRejectEmptyName() throws Exception {
        MappingSchemeRequest req = new MappingSchemeRequest(
                "", "http://src", "http://dst", null);

        mockMvc.perform(post("/api/v1/mapping-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_shouldRejectEmptySourceUri() throws Exception {
        MappingSchemeRequest req = new MappingSchemeRequest(
                "Name", "", "http://dst", null);

        mockMvc.perform(post("/api/v1/mapping-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        MappingSchemeRequest req = new MappingSchemeRequest(
                "Updated", "http://new-src", "http://new-dst", null);
        MappingSchemeResponse updated = new MappingSchemeResponse(
                SCHEME_ID, "Updated", "http://new-src", "http://new-dst",
                null, "0.1.0", MappingSchemeStatus.DRAFT, null, NOW, NOW);
        when(service.update(eq(SCHEME_ID), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/mapping-schemes/{id}", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated"))
                .andExpect(jsonPath("$.data.sourceUri").value("http://new-src"));
    }

    @Test
    void delete_shouldReturnOk() throws Exception {
        doNothing().when(service).delete(SCHEME_ID);

        mockMvc.perform(delete("/api/v1/mapping-schemes/{id}", SCHEME_ID))
                .andExpect(status().isOk());
    }

    // ==================== Lifecycle Tests ====================

    @Test
    void transition_publish_shouldReturnOk() throws Exception {
        MappingSchemeStatusTransitionRequest req = new MappingSchemeStatusTransitionRequest(
                MappingSchemeStatus.PUBLISHED);
        MappingSchemeResponse published = new MappingSchemeResponse(
                SCHEME_ID, "Test", "http://s", "http://t",
                null, "0.1.0", MappingSchemeStatus.PUBLISHED, null, NOW, NOW);
        when(service.transitionStatus(eq(SCHEME_ID), eq(MappingSchemeStatus.PUBLISHED)))
                .thenReturn(published);

        mockMvc.perform(post("/api/v1/mapping-schemes/{id}/transition", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void transition_archive_shouldReturnOk() throws Exception {
        MappingSchemeStatusTransitionRequest req = new MappingSchemeStatusTransitionRequest(
                MappingSchemeStatus.ARCHIVED);
        MappingSchemeResponse archived = new MappingSchemeResponse(
                SCHEME_ID, "Test", "http://s", "http://t",
                null, "0.1.0", MappingSchemeStatus.ARCHIVED, null, NOW, NOW);
        when(service.transitionStatus(eq(SCHEME_ID), eq(MappingSchemeStatus.ARCHIVED)))
                .thenReturn(archived);

        mockMvc.perform(post("/api/v1/mapping-schemes/{id}/transition", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void transition_shouldRejectNullTarget() throws Exception {
        String invalidBody = "{}";

        mockMvc.perform(post("/api/v1/mapping-schemes/{id}/transition", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createNewVersion_shouldReturnOk() throws Exception {
        UUID newId = UUID.randomUUID();
        MappingSchemeResponse newVersion = new MappingSchemeResponse(
                newId, "Test", "http://s", "http://t",
                null, "0.1.1", MappingSchemeStatus.DRAFT, SCHEME_ID, NOW, NOW);
        when(service.createNewVersion(SCHEME_ID)).thenReturn(newVersion);

        mockMvc.perform(post("/api/v1/mapping-schemes/{id}/new-version", SCHEME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("0.1.1"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.parentId").value(SCHEME_ID.toString()));
    }
}
