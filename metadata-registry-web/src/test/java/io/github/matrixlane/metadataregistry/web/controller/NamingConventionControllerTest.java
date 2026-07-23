
package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.namingconvention.NamingConventionRequest;
import io.github.matrixlane.metadataregistry.web.dto.namingconvention.NamingConventionResponse;
import io.github.matrixlane.metadataregistry.web.service.NamingConventionService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NamingConventionController.class)
@AutoConfigureMockMvc(addFilters = false)
class NamingConventionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NamingConventionService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID NC_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID LANG_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Test
    void list_shouldReturnOk() throws Exception {
        NamingConventionResponse resp = createResponse();
        Page<NamingConventionResponse> page = new PageImpl<>(List.of(resp));
        when(service.list(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/naming-conventions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(NC_ID.toString()));
    }

    @Test
    void get_shouldReturnOk() throws Exception {
        when(service.get(NC_ID)).thenReturn(createResponse());

        mockMvc.perform(get("/api/v1/naming-conventions/{id}", NC_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NC_ID.toString()))
                .andExpect(jsonPath("$.data.scopeRule").value("scope rule"));
    }

    @Test
    void create_shouldReturnOk() throws Exception {
        NamingConventionRequest req = new NamingConventionRequest(
                "scope rule",
                "authority rule",
                List.of(new TextLine(LANG_ID, "命名规范", null, null)),
                null,
                null);
        when(service.create(any())).thenReturn(createResponse());

        mockMvc.perform(post("/api/v1/naming-conventions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NC_ID.toString()));
    }

    @Test
    void create_shouldRejectEmptyScopeRule() throws Exception {
        NamingConventionRequest req = new NamingConventionRequest(
                "",
                null,
                List.of(new TextLine(LANG_ID, "命名规范", null, null)),
                null,
                null);

        mockMvc.perform(post("/api/v1/naming-conventions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_shouldReturnOk() throws Exception {
        NamingConventionRequest req = new NamingConventionRequest(
                "updated",
                null,
                List.of(new TextLine(LANG_ID, "命名规范", null, null)),
                null,
                null);
        when(service.update(eq(NC_ID), any())).thenReturn(createResponse());

        mockMvc.perform(put("/api/v1/naming-conventions/{id}", NC_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NC_ID.toString()));
    }

    @Test
    void delete_shouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/v1/naming-conventions/{id}", NC_ID))
                .andExpect(status().isOk());
    }

    private NamingConventionResponse createResponse() {
        return new NamingConventionResponse(
                NC_ID,
                "命名规范",
                "scope rule",
                "authority rule",
                List.of(),
                List.of(),
                null);
    }
}
