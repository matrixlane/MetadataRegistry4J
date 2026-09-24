package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemFlags;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemResponse;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemView;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitRequest;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitResult;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSummaryResponse;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.github.matrixlane.metadataregistry.web.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
    private ActiveRegistrarService activeRegistrarService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID ITEM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CONTACT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void summary_shouldReturnOk() throws Exception {
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());
        when(workspaceService.getSummary(any())).thenReturn(new WorkspaceSummaryResponse(1L, 2L, 3L));

        mockMvc.perform(get("/api/v1/workspace/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drafts").value(1))
                .andExpect(jsonPath("$.data.pending").value(2))
                .andExpect(jsonPath("$.data.rejected").value(3));
    }

    @Test
    void items_shouldReturnOk() throws Exception {
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());
        WorkspaceItemResponse row = new WorkspaceItemResponse(
                ITEM_ID, "DataElement", "测试项", RegistrationStatus.INCOMPLETE,
                "draft", "1.0.0", false,
                new WorkspaceItemFlags(true, false, false, false, false, false));
        when(workspaceService.listItems(eq(WorkspaceItemView.DRAFTS), any(), any()))
                .thenReturn(io.github.matrixlane.metadataregistry.web.common.PageResponse.of(
                        new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1)));

        mockMvc.perform(get("/api/v1/workspace/items")
                        .param("view", "DRAFTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].itemId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.data.content[0].administrativeStatus").value("draft"))
                .andExpect(jsonPath("$.data.content[0].flags.canSubmit").value(true));
    }

    @Test
    void submit_shouldReturnOk() throws Exception {
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());
        WorkspaceSubmitRequest request = new WorkspaceSubmitRequest(List.of(ITEM_ID),
                new SubmissionInfo(CONTACT_ID, null));
        when(workspaceService.submit(any(), any())).thenReturn(List.of(
                new WorkspaceSubmitResult(ITEM_ID, true, null, null)));

        mockMvc.perform(post("/api/v1/workspace/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemId").value(ITEM_ID.toString()))
                .andExpect(jsonPath("$.data[0].success").value(true));
    }

    @Test
    void requestCandidate_shouldReturnOk() throws Exception {
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/workspace/request-candidate/{itemId}", ITEM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmissionInfo(CONTACT_ID, null))))
                .andExpect(status().isOk());
    }

    @Test
    void requestCandidate_withoutBody_shouldReturnOk() throws Exception {
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/workspace/request-candidate/{itemId}", ITEM_ID))
                .andExpect(status().isOk());
    }

    @Test
    void rollback_shouldReturnOk() throws Exception {
        when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/workspace/rollback/{itemId}", ITEM_ID))
                .andExpect(status().isOk());
    }
}
