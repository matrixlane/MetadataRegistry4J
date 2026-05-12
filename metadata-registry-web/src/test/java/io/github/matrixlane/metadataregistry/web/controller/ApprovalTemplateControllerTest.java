package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalStepRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalTemplateRequest;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStep;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepAssigneeRule;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalTemplate;
import io.github.matrixlane.metadataregistry.web.entity.RejectStrategy;
import io.github.matrixlane.metadataregistry.web.service.ApprovalTemplateService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApprovalTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApprovalTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApprovalTemplateService approvalTemplateService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID TEMPLATE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void createTemplate_shouldReturnOk() throws Exception {
        ApprovalTemplateRequest request = createTemplateRequest();
        when(approvalTemplateService.createTemplate(any(), any())).thenReturn(createTemplate());

        mockMvc.perform(post("/api/v1/approval-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("2级审批模板"))
                .andExpect(jsonPath("$.data.stepCount").value(2));
    }

    @Test
    void updateTemplate_shouldReturnOk() throws Exception {
        ApprovalTemplateRequest request = createTemplateRequest();
        when(approvalTemplateService.updateTemplate(any(), any(), any())).thenReturn(createTemplate());

        mockMvc.perform(put("/api/v1/approval-templates/{id}", TEMPLATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void enableTemplate_shouldReturnOk() throws Exception {
        ApprovalTemplate template = createTemplate();
        template.setEnabled(true);
        template.setLocked(true);
        when(approvalTemplateService.enableTemplate(TEMPLATE_ID)).thenReturn(template);

        mockMvc.perform(post("/api/v1/approval-templates/{id}/enable", TEMPLATE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.locked").value(true));
    }

    @Test
    void disableTemplate_shouldReturnOk() throws Exception {
        ApprovalTemplate template = createTemplate();
        template.setEnabled(false);
        template.setLocked(true);
        when(approvalTemplateService.disableTemplate(TEMPLATE_ID)).thenReturn(template);

        mockMvc.perform(post("/api/v1/approval-templates/{id}/disable", TEMPLATE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void deleteTemplate_shouldReturnOk() throws Exception {
        doNothing().when(approvalTemplateService).deleteTemplate(TEMPLATE_ID);

        mockMvc.perform(delete("/api/v1/approval-templates/{id}", TEMPLATE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void getTemplate_shouldReturnOk() throws Exception {
        when(approvalTemplateService.getTemplate(TEMPLATE_ID)).thenReturn(createTemplate());

        mockMvc.perform(get("/api/v1/approval-templates/{id}", TEMPLATE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(TEMPLATE_ID.toString()))
                .andExpect(jsonPath("$.data.name").value("2级审批模板"));
    }

    @Test
    void listAllTemplates_shouldReturnOk() throws Exception {
        when(approvalTemplateService.listAllTemplates()).thenReturn(List.of(createTemplate()));

        mockMvc.perform(get("/api/v1/approval-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void listEnabledTemplates_shouldReturnOk() throws Exception {
        ApprovalTemplate template = createTemplate();
        template.setEnabled(true);
        when(approvalTemplateService.listEnabledTemplates()).thenReturn(List.of(template));

        mockMvc.perform(get("/api/v1/approval-templates/enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].enabled").value(true));
    }

    @Test
    void createTemplate_withInvalidRequest_shouldReturnUnprocessableEntity() throws Exception {
        ApprovalTemplateRequest request = new ApprovalTemplateRequest(
                "", null, 1, 999, null, List.of());

        mockMvc.perform(post("/api/v1/approval-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    // =========================================================================
    // Helper 方法
    // =========================================================================

    private ApprovalTemplateRequest createTemplateRequest() {
        return new ApprovalTemplateRequest(
                "2级审批模板",
                "标准2级串行审批流程",
                2,
                72,
                RejectStrategy.REJECT_ALL,
                List.of(
                        new ApprovalStepRequest(1, "一级审批", ApprovalStepAssigneeRule.BY_USER, "approver1", 72, null, null),
                        new ApprovalStepRequest(2, "二级审批", ApprovalStepAssigneeRule.BY_ROLE, "ADMIN", 168, null, null)
                ));
    }

    private ApprovalTemplate createTemplate() {
        ApprovalTemplate t = new ApprovalTemplate();
        t.setId(TEMPLATE_ID);
        t.setName("2级审批模板");
        t.setDescription("标准2级串行审批流程");
        t.setStepCount(2);
        t.setEnabled(false);
        t.setLocked(false);
        t.setDefaultTimeoutHours(72);
        t.setDefaultRejectStrategy(RejectStrategy.REJECT_ALL);
        t.setCreatedAt(LocalDateTime.now());

        ApprovalStep step1 = new ApprovalStep();
        step1.setId(UUID.randomUUID());
        step1.setTemplate(t);
        step1.setStepNumber(1);
        step1.setStepName("一级审批");
        step1.setAssigneeRule(ApprovalStepAssigneeRule.BY_USER);
        step1.setAssigneeValue("approver1");

        ApprovalStep step2 = new ApprovalStep();
        step2.setId(UUID.randomUUID());
        step2.setTemplate(t);
        step2.setStepNumber(2);
        step2.setStepName("二级审批");
        step2.setAssigneeRule(ApprovalStepAssigneeRule.BY_ROLE);
        step2.setAssigneeValue("ADMIN");

        t.setSteps(List.of(step1, step2));
        return t;
    }
}
