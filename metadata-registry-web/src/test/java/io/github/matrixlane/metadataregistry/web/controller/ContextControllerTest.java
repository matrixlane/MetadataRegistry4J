package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextResponse;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextUsageAssociation;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextUsageGroup;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextUsageItem;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextUsageResponse;
import io.github.matrixlane.metadataregistry.web.service.ContextService;
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

@WebMvcTest(ContextController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContextService contextService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID CONTEXT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ITEM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID DESIGNATION_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void usages_shouldReturnGroupedUsages() throws Exception {
        ContextUsageAssociation association = new ContextUsageAssociation(
                "DESIGNATION", DESIGNATION_ID, "患者标识", null, null, "PREFERRED");
        ContextUsageItem item = new ContextUsageItem(ITEM_ID, "患者标识", List.of(association));
        ContextUsageGroup group = new ContextUsageGroup("DataElement", "数据元", List.of(item));
        ContextUsageResponse response = new ContextUsageResponse(List.of(group), false);

        when(contextService.findUsages(eq(CONTEXT_ID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/contexts/{id}/usages", CONTEXT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups[0].itemTypeLabel").value("数据元"))
                .andExpect(jsonPath("$.data.groups[0].items[0].itemLabel").value("患者标识"))
                .andExpect(jsonPath("$.data.groups[0].items[0].associations[0].associationType").value("DESIGNATION"))
                .andExpect(jsonPath("$.data.truncated").value(false));
    }

    @Test
    void getDefault_shouldReturnRegistryContext() throws Exception {
        UUID registryContextId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ContextResponse response = new ContextResponse(
                registryContextId, "Registry Context", List.of(), List.of(), null);

        when(contextService.getDefault()).thenReturn(response);

        mockMvc.perform(get("/api/v1/contexts/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(registryContextId.toString()))
                .andExpect(jsonPath("$.data.label").value("Registry Context"));
    }
}
