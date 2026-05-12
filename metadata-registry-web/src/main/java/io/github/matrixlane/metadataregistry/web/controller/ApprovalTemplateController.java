package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalStepRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalStepResponse;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalTemplateRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalTemplateResponse;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStep;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalTemplate;
import io.github.matrixlane.metadataregistry.web.service.ApprovalTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approval-templates")
@RequiredArgsConstructor
@Tag(name = "审批流程模板", description = "多级审批流程模板的创建、配置、启用/禁用管理")
public class ApprovalTemplateController {

    private final ApprovalTemplateService approvalTemplateService;

    @PostMapping
    @Operation(summary = "创建审批模板",
            description = "创建多级审批流程模板，包含步骤定义；默认未启用")
    public ApiResponse<ApprovalTemplateResponse> createTemplate(
            @Valid @RequestBody ApprovalTemplateRequest request) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setName(request.name());
        template.setDescription(request.description());
        template.setStepCount(request.stepCount());
        template.setDefaultTimeoutHours(request.defaultTimeoutHours());
        template.setDefaultRejectStrategy(request.defaultRejectStrategy());

        List<ApprovalStep> steps = request.steps().stream()
                .map(s -> {
                    ApprovalStep step = new ApprovalStep();
                    step.setStepNumber(s.stepNumber());
                    step.setStepName(s.stepName());
                    step.setAssigneeRule(s.assigneeRule());
                    step.setAssigneeValue(s.assigneeValue());
                    step.setTimeoutHours(s.timeoutHours());
                    step.setRejectStrategy(s.rejectStrategy());
                    step.setDescription(s.description());
                    return step;
                })
                .toList();

        ApprovalTemplate result = approvalTemplateService.createTemplate(template, steps);
        return ApiResponse.ok(toResponse(result));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新审批模板", description = "仅未锁定的模板可更新")
    public ApiResponse<ApprovalTemplateResponse> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody ApprovalTemplateRequest request) {
        ApprovalTemplate template = new ApprovalTemplate();
        template.setName(request.name());
        template.setDescription(request.description());
        template.setStepCount(request.stepCount());
        template.setDefaultTimeoutHours(request.defaultTimeoutHours());
        template.setDefaultRejectStrategy(request.defaultRejectStrategy());

        List<ApprovalStep> steps = request.steps().stream()
                .map(s -> {
                    ApprovalStep step = new ApprovalStep();
                    step.setStepNumber(s.stepNumber());
                    step.setStepName(s.stepName());
                    step.setAssigneeRule(s.assigneeRule());
                    step.setAssigneeValue(s.assigneeValue());
                    step.setTimeoutHours(s.timeoutHours());
                    step.setRejectStrategy(s.rejectStrategy());
                    step.setDescription(s.description());
                    return step;
                })
                .toList();

        ApprovalTemplate result = approvalTemplateService.updateTemplate(id, template, steps);
        return ApiResponse.ok(toResponse(result));
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用模板", description = "启用后模板自动锁定（只读）")
    public ApiResponse<ApprovalTemplateResponse> enableTemplate(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(approvalTemplateService.enableTemplate(id)));
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用模板", description = "禁用模板（不解锁）")
    public ApiResponse<ApprovalTemplateResponse> disableTemplate(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(approvalTemplateService.disableTemplate(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板", description = "仅未锁定的模板可删除")
    public ApiResponse<Void> deleteTemplate(@PathVariable UUID id) {
        approvalTemplateService.deleteTemplate(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模板详情")
    public ApiResponse<ApprovalTemplateResponse> getTemplate(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(approvalTemplateService.getTemplate(id)));
    }

    @GetMapping
    @Operation(summary = "查询全部模板")
    public ApiResponse<List<ApprovalTemplateResponse>> listAllTemplates() {
        List<ApprovalTemplateResponse> responses = approvalTemplateService.listAllTemplates()
                .stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(responses);
    }

    @GetMapping("/enabled")
    @Operation(summary = "查询已启用模板")
    public ApiResponse<List<ApprovalTemplateResponse>> listEnabledTemplates() {
        List<ApprovalTemplateResponse> responses = approvalTemplateService.listEnabledTemplates()
                .stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(responses);
    }

    private ApprovalTemplateResponse toResponse(ApprovalTemplate template) {
        List<ApprovalStepResponse> stepResponses = template.getSteps().stream()
                .map(s -> new ApprovalStepResponse(
                        s.getId(),
                        s.getTemplate().getId(),
                        s.getStepNumber(),
                        s.getStepName(),
                        s.getAssigneeRule(),
                        s.getAssigneeValue(),
                        s.getTimeoutHours(),
                        s.getRejectStrategy(),
                        s.getDescription()))
                .toList();

        return new ApprovalTemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getStepCount(),
                template.getEnabled(),
                template.getLocked(),
                template.getDefaultTimeoutHours(),
                template.getDefaultRejectStrategy(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                stepResponses);
    }
}
