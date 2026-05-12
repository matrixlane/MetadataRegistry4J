package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApproveRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.RejectRequest;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalRecord;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalResult;
import io.github.matrixlane.metadataregistry.web.service.ApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID ITEM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID RECORD_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void submitForApproval_shouldReturnOk() throws Exception {
        when(approvalService.submitForApproval(ITEM_ID)).thenReturn(createPendingRecord());

        mockMvc.perform(post("/api/v1/approvals/submit/{administeredItemId}", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(RECORD_ID.toString()))
                .andExpect(jsonPath("$.data.result").value("PENDING"));
    }

    @Test
    void approve_shouldReturnOk() throws Exception {
        ApprovalRecord approved = createApprovedRecord();
        when(approvalService.approve(eq(RECORD_ID), any())).thenReturn(approved);

        ApproveRequest request = new ApproveRequest("同意发布");

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.result").value("APPROVED"))
                .andExpect(jsonPath("$.data.opinion").value("同意发布"))
                .andExpect(jsonPath("$.data.approver").value("approver"));
    }

    @Test
    void reject_shouldReturnOk() throws Exception {
        ApprovalRecord rejected = createRejectedRecord();
        when(approvalService.reject(eq(RECORD_ID), any())).thenReturn(rejected);

        RejectRequest request = new RejectRequest("名称不规范");

        mockMvc.perform(post("/api/v1/approvals/{id}/reject", RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.result").value("REJECTED"))
                .andExpect(jsonPath("$.data.opinion").value("名称不规范"));
    }

    @Test
    void resubmit_shouldReturnOk() throws Exception {
        when(approvalService.resubmit(ITEM_ID)).thenReturn(createPendingRecord());

        mockMvc.perform(post("/api/v1/approvals/resubmit/{administeredItemId}", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.result").value("PENDING"));
    }

    @Test
    void getApprovalHistory_shouldReturnOk() throws Exception {
        when(approvalService.getApprovalHistory(ITEM_ID))
                .thenReturn(List.of(createRejectedRecord(), createPendingRecord()));

        mockMvc.perform(get("/api/v1/approvals/history/{administeredItemId}", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].result").value("REJECTED"))
                .andExpect(jsonPath("$.data[1].result").value("PENDING"));
    }

    @Test
    void approve_withOpinionTooLong_shouldReturnBadRequest() throws Exception {
        ApproveRequest request = new ApproveRequest("a".repeat(2001));

        mockMvc.perform(post("/api/v1/approvals/{id}/approve", RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    private ApprovalRecord createPendingRecord() {
        ApprovalRecord record = new ApprovalRecord();
        record.setId(RECORD_ID);
        record.setAdministeredItemId(ITEM_ID);
        record.setSubmitter("submitter");
        record.setSubmittedAt(LocalDateTime.now());
        record.setResult(ApprovalResult.PENDING);
        return record;
    }

    private ApprovalRecord createApprovedRecord() {
        ApprovalRecord record = createPendingRecord();
        record.setApprover("approver");
        record.setApprovedAt(LocalDateTime.now());
        record.setOpinion("同意发布");
        record.setResult(ApprovalResult.APPROVED);
        return record;
    }

    private ApprovalRecord createRejectedRecord() {
        ApprovalRecord record = createPendingRecord();
        record.setId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        record.setApprover("approver");
        record.setApprovedAt(LocalDateTime.now());
        record.setOpinion("名称不规范");
        record.setResult(ApprovalResult.REJECTED);
        return record;
    }
}
