package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingHierarchyRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingResponse;
import io.github.matrixlane.metadataregistry.web.service.ItemMappingCrudService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemMappingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ItemMappingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ItemMappingCrudService crudService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    // ── Helpers ─────────────────────────────────────────────────────

    private ItemMappingResponse response(UUID id, MappingDegree degree) {
        return new ItemMappingResponse(
                id,
                degree,
                "desc",
                "a = b",
                "EBNF",
                List.of(new ItemMappingResponse.EndpointRef(UUID.randomUUID(), "year"),
                        new ItemMappingResponse.EndpointRef(UUID.randomUUID(), "month")),
                List.of(new ItemMappingResponse.EndpointRef(UUID.randomUUID(), "isodate")),
                RegistrationStatus.RECORDED);
    }

    private ItemMappingRequest validRequest() {
        return new ItemMappingRequest(
                List.of(new ItemMappingRequest.Endpoint(UUID.randomUUID(), "year"),
                        new ItemMappingRequest.Endpoint(UUID.randomUUID(), "month")),
                List.of(new ItemMappingRequest.Endpoint(UUID.randomUUID(), "isodate")),
                MappingDegree.SAME_AS,
                "desc", "a = b", "EBNF", null);
    }

    // ── Tests ───────────────────────────────────────────────────────

    @Test
    @WithMockUser
    void list_shouldReturnPage() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudService.list(eq(0), eq(20), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new PageImpl<>(List.of(response(id, MappingDegree.SAME_AS))));

        mockMvc.perform(get("/api/v1/item-mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id", is(id.toString())))
                .andExpect(jsonPath("$.data.content[0].degree", is("same as")))
                .andExpect(jsonPath("$.data.content[0].subjects", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].subjects[0].variableName", is("year")))
                .andExpect(jsonPath("$.data.content[0].objects", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].registrationStatus", is("RECORDED")));
    }

    @Test
    @WithMockUser
    void list_withDegreeFilter_shouldPassThrough() throws Exception {
        when(crudService.list(eq(0), eq(20), eq(null), eq(null), eq(null),
                eq(MappingDegree.DERIVED_FROM)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/item-mappings").param("degree", "derived from"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(0)));
    }

    @Test
    @WithMockUser
    void create_shouldReturnResponse() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudService.create(any(ItemMappingRequest.class)))
                .thenReturn(response(id, MappingDegree.DERIVED_FROM));

        mockMvc.perform(post("/api/v1/item-mappings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(id.toString())))
                .andExpect(jsonPath("$.data.degree", is("derived from")));
    }

    @Test
    @WithMockUser
    void create_withoutDegree_shouldFailValidation() throws Exception {
        ItemMappingRequest bad = new ItemMappingRequest(
                List.of(), List.of(), null, null, null, null, null);

        mockMvc.perform(post("/api/v1/item-mappings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    void create_withEmptySubjects_shouldFailValidation() throws Exception {
        ItemMappingRequest bad = new ItemMappingRequest(
                List.of(),
                List.of(new ItemMappingRequest.Endpoint(UUID.randomUUID(), null)),
                MappingDegree.SAME_AS, null, null, null, null);

        mockMvc.perform(post("/api/v1/item-mappings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    void get_shouldReturnResponse() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudService.get(id)).thenReturn(response(id, MappingDegree.SAME_AS));

        mockMvc.perform(get("/api/v1/item-mappings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(id.toString())));
    }

    @Test
    @WithMockUser
    void update_shouldReturnResponse() throws Exception {
        UUID id = UUID.randomUUID();
        when(crudService.update(eq(id), any(ItemMappingRequest.class)))
                .thenReturn(response(id, MappingDegree.SEMANTICALLY_EQUIVALENT));

        mockMvc.perform(put("/api/v1/item-mappings/{id}", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.degree", is("semantically equivalent")));
    }

    @Test
    @WithMockUser
    void delete_shouldReturnOk() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(crudService).delete(id);

        mockMvc.perform(delete("/api/v1/item-mappings/{id}", id).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getHierarchy_shouldReturnSupersetAndSubsets() throws Exception {
        UUID id = UUID.randomUUID();
        UUID superset = UUID.randomUUID();
        when(crudService.getHierarchy(id))
                .thenReturn(Map.of("supersetMappingId", superset, "subsetMappingIds", List.of()));

        mockMvc.perform(get("/api/v1/item-mappings/{id}/hierarchy", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supersetMappingId", is(superset.toString())))
                .andExpect(jsonPath("$.data.subsetMappingIds.length()", is(0)));
    }

    @Test
    @WithMockUser
    void putHierarchy_shouldReturnUpdated() throws Exception {
        UUID id = UUID.randomUUID();
        UUID superset = UUID.randomUUID();
        when(crudService.putHierarchy(eq(id), any(ItemMappingHierarchyRequest.class)))
                .thenReturn(Map.of("supersetMappingId", superset, "subsetMappingIds", List.of()));

        mockMvc.perform(put("/api/v1/item-mappings/{id}/hierarchy", id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ItemMappingHierarchyRequest(superset))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supersetMappingId", is(superset.toString())));
    }
}
