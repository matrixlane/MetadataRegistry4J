package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApproveRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.RejectRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.StartApprovalRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.TransferStepRequest;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalProcessInstance;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalProcessStatus;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepAssigneeRule;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepRecord;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepStatus;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalTemplate;
import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;
import io.github.matrixlane.metadataregistry.web.service.MultiLevelApprovalService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MultiLevelApprovalController.class)
@AutoConfigureMockMvc(addFilters = false)
class MultiLevelApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MultiLevelApprovalService multiLevelApprovalService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID ITEM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TEMPLATE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID INSTANCE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID STEP_RECORD_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Test
    void startApproval_shouldReturnOk() throws Exception {
        StartApprovalRequest request = new StartApprovalRequest(ITEM_ID, TEMPLATE_ID);
        when(multiLevelApprovalService.startApproval(ITEM_ID, TEMPLATE_ID))
                .thenReturn(createPendingInstance());

        mockMvc.perform(post("/api/v1/multi-level-approvals/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(INSTANCE_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.currentStep").value(1));
    }

    @Test
    void approveStep_shouldReturnOk() throws Exception {
        ApproveRequest request = new ApproveRequest("同意");
        when(multiLevelApprovalService.approveStep(eq(STEP_RECORD_ID), any()))
                .thenReturn(createApprovedStepRecord());

        mockMvc.perform(post("/api/v1/multi-level-approvals/steps/{stepRecordId}/approve", STEP_RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approver").value("approver1"));
    }

    @Test
    void rejectStep_shouldReturnOk() throws Exception {
        RejectRequest request = new RejectRequest("名称不规范");
        when(multiLevelApprovalService.rejectStep(eq(STEP_RECORD_ID), any()))
                .thenReturn(createRejectedStepRecord());

        mockMvc.perform(post("/api/v1/multi-level-approvals/steps/{stepRecordId}/reject", STEP_RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.opinion").value("名称不规范"));
    }

    @Test
    void transferStep_shouldReturnOk() throws Exception {
        TransferStepRequest request = new TransferStepRequest("newApprover", null);
        when(multiLevelApprovalService.transferStep(eq(STEP_RECORD_ID), eq("newApprover"), isNull()))
                .thenReturn(createTransferredStepRecord());

        mockMvc.perform(post("/api/v1/multi-level-approvals/steps/{stepRecordId}/transfer", STEP_RECORD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("TRANSFERRED"))
                .andExpect(jsonPath("$.data.assigneeValue").value("newApprover"));
    }

    @Test
    void resubmit_shouldReturnOk() throws Exception {
        when(multiLevelApprovalService.resubmit(ITEM_ID, TEMPLATE_ID))
                .thenReturn(createPendingInstance());

        mockMvc.perform(post("/api/v1/multi-level-approvals/resubmit")
                        .param("administeredItemId", ITEM_ID.toString())
                        .param("templateId", TEMPLATE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getProcessInstance_shouldReturnOk() throws Exception {
        when(multiLevelApprovalService.getProcessInstance(INSTANCE_ID))
                .thenReturn(createPendingInstance());

        mockMvc.perform(get("/api/v1/multi-level-approvals/process/{processInstanceId}", INSTANCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(INSTANCE_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void getStepRecords_shouldReturnOk() throws Exception {
        when(multiLevelApprovalService.getStepRecords(INSTANCE_ID))
                .thenReturn(List.of(createPendingStepRecord(), createApprovedStepRecord()));

        mockMvc.perform(get("/api/v1/multi-level-approvals/process/{processInstanceId}/steps", INSTANCE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getProcessHistory_shouldReturnOk() throws Exception {
        when(multiLevelApprovalService.getProcessHistory(ITEM_ID))
                .thenReturn(List.of(createRejectedInstance(), createPendingInstance()));

        mockMvc.perform(get("/api/v1/multi-level-approvals/history/{administeredItemId}", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void getPendingProcess_shouldReturnOk() throws Exception {
        when(multiLevelApprovalService.getPendingProcessInstance(ITEM_ID))
                .thenReturn(java.util.Optional.of(createPendingInstance()));

        mockMvc.perform(get("/api/v1/multi-level-approvals/pending/{administeredItemId}", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void startApproval_withInvalidRequest_shouldReturnUnprocessableEntity() throws Exception {
        StartApprovalRequest request = new StartApprovalRequest(null, TEMPLATE_ID);

        mockMvc.perform(post("/api/v1/multi-level-approvals/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    // =========================================================================
    // Helper 方法
    // =========================================================================

    private ApprovalTemplate createTemplate() {
        ApprovalTemplate t = new ApprovalTemplate();
        t.setId(TEMPLATE_ID);
        t.setName("2级审批模板");
        t.setStepCount(2);
        t.setEnabled(true);
        t.setLocked(true);
        t.setDefaultTimeoutHours(72);
        t.setDefaultRejectStrategy(RejectStrategy.REJECT_ALL);
        return t;
    }

    private ApprovalProcessInstance createPendingInstance() {
        ApprovalProcessInstance i = new ApprovalProcessInstance();
        i.setId(INSTANCE_ID);
        i.setTemplate(createTemplate());
        i.setAdministeredItemId(ITEM_ID);
        i.setSubmitter("submitter");
        i.setCurrentStep(1);
        i.setStatus(ApprovalProcessStatus.PENDING);
        i.setCreatedAt(LocalDateTime.now());
        return i;
    }

    private ApprovalProcessInstance createRejectedInstance() {
        ApprovalProcessInstance i = createPendingInstance();
        i.setId(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"));
        i.setStatus(ApprovalProcessStatus.REJECTED);
        i.setCompletedAt(LocalDateTime.now());
        return i;
    }

    private ApprovalStepRecord createPendingStepRecord() {
        ApprovalStepRecord r = new ApprovalStepRecord();
        r.setId(STEP_RECORD_ID);
        r.setProcessInstance(createPendingInstance());
        r.setStepNumber(1);
        r.setStepName("一级审批");
        r.setAssigneeRule(ApprovalStepAssigneeRule.BY_USER);
        r.setAssigneeValue("approver1");
        r.setAssignedAt(LocalDateTime.now());
        r.setStatus(ApprovalStepStatus.PENDING);
        r.setTimeoutAt(LocalDateTime.now().plusHours(72));
        return r;
    }

    private ApprovalStepRecord createApprovedStepRecord() {
        ApprovalStepRecord r = createPendingStepRecord();
        r.setApprover("approver1");
        r.setApprovedAt(LocalDateTime.now());
        r.setOpinion("同意");
        r.setStatus(ApprovalStepStatus.APPROVED);
        return r;
    }

    private ApprovalStepRecord createRejectedStepRecord() {
        ApprovalStepRecord r = createPendingStepRecord();
        r.setApprover("approver1");
        r.setApprovedAt(LocalDateTime.now());
        r.setOpinion("名称不规范");
        r.setStatus(ApprovalStepStatus.REJECTED);
        return r;
    }

    private ApprovalStepRecord createTransferredStepRecord() {
        ApprovalStepRecord r = createPendingStepRecord();
        r.setAssigneeValue("newApprover");
        r.setStatus(ApprovalStepStatus.TRANSFERRED);
        return r;
    }
}
