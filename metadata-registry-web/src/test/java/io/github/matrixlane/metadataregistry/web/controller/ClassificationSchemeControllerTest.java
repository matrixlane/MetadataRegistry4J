package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationNodeRequest;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationNodeResponse;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationSchemeResponse;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationTreeNode;
import io.github.matrixlane.metadataregistry.web.dto.classification.MoveNodeRequest;
import io.github.matrixlane.metadataregistry.web.service.ClassificationImportExportService;
import io.github.matrixlane.metadataregistry.web.service.ClassificationSchemeService;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClassificationSchemeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClassificationSchemeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClassificationSchemeService service;

    @MockBean
    private ClassificationImportExportService importExportService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID SCHEME_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID NODE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PARENT_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 16, 12, 0);

    private ClassificationSchemeResponse createSchemeResponse() {
        return ClassificationSchemeResponse.builder()
                .id(SCHEME_ID)
                .schemeName("Test Scheme")
                .description("Test Desc")
                .version("1.0")
                .status("DRAFT")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private ClassificationNodeResponse createNodeResponse() {
        return ClassificationNodeResponse.builder()
                .id(NODE_ID)
                .nodeCode("N01")
                .nodeName("Node1")
                .description("Desc1")
                .definition("Def1")
                .sortOrder(1)
                .parentId(null)
                .schemeId(SCHEME_ID)
                .schemeName("Test Scheme")
                .hasChildren(false)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private ClassificationNodeResponse createChildNodeResponse() {
        return ClassificationNodeResponse.builder()
                .id(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"))
                .nodeCode("N02")
                .nodeName("Node2")
                .sortOrder(2)
                .parentId(PARENT_ID)
                .schemeId(SCHEME_ID)
                .schemeName("Test Scheme")
                .hasChildren(false)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    // ==================== Scheme Endpoints ====================

    @Test
    void listSchemes_shouldReturnOk() throws Exception {
        Page<ClassificationSchemeResponse> page = new PageImpl<>(List.of(createSchemeResponse()));
        when(service.listSchemes(isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/classification-schemes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(SCHEME_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].schemeName").value("Test Scheme"))
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));
    }

    @Test
    void listSchemes_shouldPassKeyword() throws Exception {
        Page<ClassificationSchemeResponse> page = new PageImpl<>(List.of(createSchemeResponse()));
        when(service.listSchemes(eq("test"), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/classification-schemes").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].schemeName").value("Test Scheme"));
    }

    @Test
    void getScheme_shouldReturnOk() throws Exception {
        when(service.getScheme(SCHEME_ID)).thenReturn(createSchemeResponse());

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}", SCHEME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SCHEME_ID.toString()))
                .andExpect(jsonPath("$.data.schemeName").value("Test Scheme"));
    }

    @Test
    void createScheme_shouldReturnOk() throws Exception {
        ClassificationSchemeRequest req = new ClassificationSchemeRequest();
        req.setSchemeName("New Scheme");
        req.setDescription("New Desc");
        req.setVersion("1.0");
        req.setStatus("DRAFT");
        when(service.createScheme(any())).thenReturn(createSchemeResponse());

        mockMvc.perform(post("/api/v1/classification-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SCHEME_ID.toString()));
    }

    @Test
    void createScheme_shouldRejectEmptyName() throws Exception {
        ClassificationSchemeRequest req = new ClassificationSchemeRequest();
        req.setSchemeName("");

        mockMvc.perform(post("/api/v1/classification-schemes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateScheme_shouldReturnOk() throws Exception {
        ClassificationSchemeRequest req = new ClassificationSchemeRequest();
        req.setSchemeName("Updated");
        req.setDescription("Updated Desc");
        req.setVersion("2.0");
        req.setStatus("PUBLISHED");

        ClassificationSchemeResponse updated = createSchemeResponse();
        updated.setSchemeName("Updated");
        updated.setStatus("PUBLISHED");
        when(service.updateScheme(eq(SCHEME_ID), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/classification-schemes/{schemeId}", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schemeName").value("Updated"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void deleteScheme_shouldReturnOk() throws Exception {
        doNothing().when(service).deleteScheme(SCHEME_ID);

        mockMvc.perform(delete("/api/v1/classification-schemes/{schemeId}", SCHEME_ID))
                .andExpect(status().isOk());
    }

    // ==================== Node Endpoints ====================

    @Test
    void listNodes_shouldReturnOk() throws Exception {
        when(service.listNodesByScheme(SCHEME_ID)).thenReturn(List.of(createNodeResponse()));

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}/nodes", SCHEME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(NODE_ID.toString()))
                .andExpect(jsonPath("$.data[0].nodeCode").value("N01"));
    }

    @Test
    void getNode_shouldReturnOk() throws Exception {
        when(service.getNode(NODE_ID)).thenReturn(createNodeResponse());

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}/nodes/{nodeId}", SCHEME_ID, NODE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(NODE_ID.toString()))
                .andExpect(jsonPath("$.data.parentId").doesNotExist());
    }

    @Test
    void createNode_shouldReturnOk() throws Exception {
        ClassificationNodeRequest req = new ClassificationNodeRequest();
        req.setNodeCode("N02");
        req.setNodeName("Node2");
        req.setSortOrder(2);

        when(service.createNode(any())).thenReturn(createChildNodeResponse());

        mockMvc.perform(post("/api/v1/classification-schemes/{schemeId}/nodes", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCode").value("N02"));
    }

    @Test
    void createNode_shouldRejectEmptyCode() throws Exception {
        ClassificationNodeRequest req = new ClassificationNodeRequest();
        req.setNodeCode("");
        req.setNodeName("Node2");

        mockMvc.perform(post("/api/v1/classification-schemes/{schemeId}/nodes", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createNode_shouldRejectEmptyName() throws Exception {
        ClassificationNodeRequest req = new ClassificationNodeRequest();
        req.setNodeCode("N02");
        req.setNodeName("");

        mockMvc.perform(post("/api/v1/classification-schemes/{schemeId}/nodes", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createNode_shouldRejectOversizedCode() throws Exception {
        ClassificationNodeRequest req = new ClassificationNodeRequest();
        req.setNodeCode("a".repeat(256));
        req.setNodeName("Node2");

        mockMvc.perform(post("/api/v1/classification-schemes/{schemeId}/nodes", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateNode_shouldReturnOk() throws Exception {
        ClassificationNodeRequest req = new ClassificationNodeRequest();
        req.setNodeCode("N01-U");
        req.setNodeName("Node1-U");
        req.setSortOrder(10);

        ClassificationNodeResponse updated = createNodeResponse();
        updated.setNodeCode("N01-U");
        updated.setNodeName("Node1-U");
        updated.setSortOrder(10);
        when(service.updateNode(eq(NODE_ID), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/classification-schemes/{schemeId}/nodes/{nodeId}", SCHEME_ID, NODE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeCode").value("N01-U"))
                .andExpect(jsonPath("$.data.sortOrder").value(10));
    }

    @Test
    void moveNode_shouldReturnOk() throws Exception {
        MoveNodeRequest req = new MoveNodeRequest();
        req.setTargetParentId(PARENT_ID);

        ClassificationNodeResponse moved = createNodeResponse();
        moved.setParentId(PARENT_ID);
        when(service.moveNode(eq(NODE_ID), any())).thenReturn(moved);

        mockMvc.perform(put("/api/v1/classification-schemes/{schemeId}/nodes/{nodeId}/move", SCHEME_ID, NODE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(PARENT_ID.toString()));
    }

    @Test
    void moveNode_shouldRejectNullTarget() throws Exception {
        String invalidBody = "{}";

        mockMvc.perform(put("/api/v1/classification-schemes/{schemeId}/nodes/{nodeId}/move", SCHEME_ID, NODE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deleteNode_shouldReturnOk() throws Exception {
        doNothing().when(service).deleteNode(NODE_ID);

        mockMvc.perform(delete("/api/v1/classification-schemes/{schemeId}/nodes/{nodeId}", SCHEME_ID, NODE_ID))
                .andExpect(status().isOk());
    }

    @Test
    void deleteNodeCascade_shouldReturnOk() throws Exception {
        doNothing().when(service).deleteNodeCascade(NODE_ID);

        mockMvc.perform(delete("/api/v1/classification-schemes/{schemeId}/nodes/{nodeId}/cascade", SCHEME_ID, NODE_ID))
                .andExpect(status().isOk());
    }

    // ==================== Tree Endpoints ====================

    @Test
    void getTree_shouldReturnOk() throws Exception {
        ClassificationTreeNode treeNode = ClassificationTreeNode.builder()
                .id(NODE_ID)
                .label("Node1")
                .nodeCode("N01")
                .hasChildren(false)
                .children(Collections.emptyList())
                .build();
        when(service.getTree(SCHEME_ID)).thenReturn(List.of(treeNode));

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}/nodes/tree", SCHEME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(NODE_ID.toString()))
                .andExpect(jsonPath("$.data[0].label").value("Node1"))
                .andExpect(jsonPath("$.data[0].hasChildren").value(false));
    }

    @Test
    void getSubTree_shouldReturnOk() throws Exception {
        ClassificationTreeNode treeNode = ClassificationTreeNode.builder()
                .id(NODE_ID)
                .label("Node1")
                .nodeCode("N01")
                .hasChildren(true)
                .children(null)
                .build();
        when(service.getTreeByRoot(SCHEME_ID, NODE_ID)).thenReturn(List.of(treeNode));

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}/nodes/{nodeId}/tree", SCHEME_ID, NODE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(NODE_ID.toString()))
                .andExpect(jsonPath("$.data[0].hasChildren").value(true));
    }

    @Test
    void getChildren_shouldReturnRootNodes() throws Exception {
        ClassificationTreeNode treeNode = ClassificationTreeNode.builder()
                .id(NODE_ID)
                .label("Node1")
                .nodeCode("N01")
                .hasChildren(false)
                .children(Collections.emptyList())
                .build();
        when(service.getChildren(SCHEME_ID, null)).thenReturn(List.of(treeNode));

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}/nodes/children", SCHEME_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(NODE_ID.toString()));
    }

    @Test
    void getChildren_shouldReturnSubNodes() throws Exception {
        ClassificationTreeNode treeNode = ClassificationTreeNode.builder()
                .id(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"))
                .label("Node2")
                .nodeCode("N02")
                .hasChildren(false)
                .children(Collections.emptyList())
                .build();
        when(service.getChildren(SCHEME_ID, PARENT_ID)).thenReturn(List.of(treeNode));

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}/nodes/children", SCHEME_ID)
                        .param("parentId", PARENT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nodeCode").value("N02"));
    }

    // ==================== Search Endpoints ====================

    @Test
    void searchNodes_shouldReturnOk() throws Exception {
        when(service.searchNodes(SCHEME_ID, "test")).thenReturn(List.of(createNodeResponse()));

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}/nodes/search", SCHEME_ID)
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nodeName").value("Node1"));
    }

    // ==================== Error Handling ====================

    @Test
    void getScheme_shouldReturnNotFound() throws Exception {
        when(service.getScheme(SCHEME_ID))
                .thenThrow(new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                        io.github.matrixlane.metadataregistry.web.common.ApiCodes.NOT_FOUND, "Not found"));

        mockMvc.perform(get("/api/v1/classification-schemes/{schemeId}", SCHEME_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void deleteScheme_shouldReturnValidationErrorWhenHasNodes() throws Exception {
        doThrow(new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                        io.github.matrixlane.metadataregistry.web.common.ApiCodes.VALIDATION_FAILED, "Has nodes"))
                .when(service).deleteScheme(SCHEME_ID);

        mockMvc.perform(delete("/api/v1/classification-schemes/{schemeId}", SCHEME_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(422));
    }

    @Test
    void createNode_shouldReturnNotFoundWhenSchemeMissing() throws Exception {
        ClassificationNodeRequest req = new ClassificationNodeRequest();
        req.setNodeCode("N02");
        req.setNodeName("Node2");

        when(service.createNode(any()))
                .thenThrow(new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                        io.github.matrixlane.metadataregistry.web.common.ApiCodes.NOT_FOUND, "Scheme not found"));

        mockMvc.perform(post("/api/v1/classification-schemes/{schemeId}/nodes", SCHEME_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404));
    }
}
