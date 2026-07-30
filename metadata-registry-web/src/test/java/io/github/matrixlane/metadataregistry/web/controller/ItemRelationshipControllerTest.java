package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DefinitionResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationDefinitionPairingResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.ItemRelationshipContextResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.ItemRelationshipResponse;
import io.github.matrixlane.metadataregistry.web.service.ItemRelationshipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemRelationshipController.class)
@AutoConfigureMockMvc(addFilters = false)
class ItemRelationshipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemRelationshipService itemRelationshipService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID ITEM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CTX_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void get_shouldReturnAggregatedRelationships() throws Exception {
        List<DesignationResponse.ContextResponse> contexts = List.of(
                new DesignationResponse.ContextResponse(CTX_ID, "临床", "PREFERRED"));
        ItemRelationshipResponse response = new ItemRelationshipResponse(
                List.of(new DesignationResponse(ITEM_ID, ITEM_ID, null, "zh-CN", "患者标识", null, null, null, null, Collections.emptyList(), contexts)),
                List.of(new DefinitionResponse(ITEM_ID, ITEM_ID, null, "zh-CN", "患者的唯一标识", null, null, Collections.emptyList())),
                List.of(new ItemRelationshipContextResponse(CTX_ID, "临床")),
                List.of(new DesignationDefinitionPairingResponse(ITEM_ID, ITEM_ID, "患者标识", ITEM_ID, "患者的唯一标识", CTX_ID, "临床", "PREFERRED")));

        when(itemRelationshipService.getItemRelationships(eq(ITEM_ID))).thenReturn(response);

        mockMvc.perform(get("/api/v1/items/{itemId}/designation-definition-relationships", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.designations[0].value").value("患者标识"))
                .andExpect(jsonPath("$.data.definitions[0].value").value("患者的唯一标识"))
                .andExpect(jsonPath("$.data.contexts[0].label").value("临床"))
                .andExpect(jsonPath("$.data.pairings[0].designationLabel").value("患者标识"));
    }
}
