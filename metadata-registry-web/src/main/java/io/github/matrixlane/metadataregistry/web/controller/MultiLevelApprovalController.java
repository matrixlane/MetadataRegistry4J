package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalProcessInstanceResponse;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalRecordResponse;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalStepRecordResponse;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApproveRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.RejectRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.StartApprovalRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.TransferStepRequest;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalProcessInstance;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalStepRecord;
import io.github.matrixlane.metadataregistry.web.service.MultiLevelApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/multi-level-approvals")
@RequiredArgsConstructor
@Tag(name = "多级审批工作流", description = "2级及以上串行审批：启动、通过、退回、转派、查询")
public class MultiLevelApprovalController {

    private final MultiLevelApprovalService multiLevelApprovalService;

    @PostMapping("/start")
    @Operation(summary = "启动多级审批",
            description = "基于审批模板启动多级串行审批流程，创建第一步待审批记录")
    public ApiResponse<ApprovalProcessInstanceResponse> startApproval(
            @Valid @RequestBody StartApprovalRequest request) {
        ApprovalProcessInstance instance = multiLevelApprovalService.startApproval(
                request.administeredItemId(), request.templateId());
        return ApiResponse.ok(toResponse(instance));
    }

    @PostMapping("/steps/{stepRecordId}/approve")
    @Operation(summary = "审批通过当前步骤",
            description = "当前步骤通过后自动推进到下一级，最后一级通过后触发 ISO 状态迁移")
    public ApiResponse<ApprovalStepRecordResponse> approveStep(
            @PathVariable UUID stepRecordId,
            @Valid @RequestBody ApproveRequest request) {
        ApprovalStepRecord record = multiLevelApprovalService.approveStep(
                stepRecordId, request.opinion());
        return ApiResponse.ok(toResponse(record));
    }

    @PostMapping("/steps/{stepRecordId}/reject")
    @Operation(summary = "审批退回当前步骤",
            description = "当前步骤退回触发整体退回，流程实例标记为 REJECTED")
    public ApiResponse<ApprovalStepRecordResponse> rejectStep(
            @PathVariable UUID stepRecordId,
            @Valid @RequestBody RejectRequest request) {
        ApprovalStepRecord record = multiLevelApprovalService.rejectStep(
                stepRecordId, request.opinion());
        return ApiResponse.ok(toResponse(record));
    }

    @PostMapping("/steps/{stepRecordId}/transfer")
    @Operation(summary = "超时转派",
            description = "将当前待审批步骤转派给新的审批人，重置超时计时")
    public ApiResponse<ApprovalStepRecordResponse> transferStep(
            @PathVariable UUID stepRecordId,
            @Valid @RequestBody TransferStepRequest request) {
        ApprovalStepRecord record = multiLevelApprovalService.transferStep(
                stepRecordId, request.newAssignee(), request.newAssigneeRole());
        return ApiResponse.ok(toResponse(record));
    }

    @PostMapping("/resubmit")
    @Operation(summary = "重新提交审批",
            description = "上次审批被退回后，可重新提交；不传 templateId 则复用上次模板")
    public ApiResponse<ApprovalProcessInstanceResponse> resubmit(
            @RequestParam UUID administeredItemId,
            @RequestParam(required = false) UUID templateId) {
        ApprovalProcessInstance instance = multiLevelApprovalService.resubmit(
                administeredItemId, templateId);
        return ApiResponse.ok(toResponse(instance));
    }

    @GetMapping("/process/{processInstanceId}")
    @Operation(summary = "查询流程实例详情")
    public ApiResponse<ApprovalProcessInstanceResponse> getProcessInstance(
            @PathVariable UUID processInstanceId) {
        ApprovalProcessInstance instance = multiLevelApprovalService.getProcessInstance(processInstanceId);
        return ApiResponse.ok(toResponse(instance));
    }

    @GetMapping("/process/{processInstanceId}/steps")
    @Operation(summary = "查询流程步骤记录")
    public ApiResponse<List<ApprovalStepRecordResponse>> getStepRecords(
            @PathVariable UUID processInstanceId) {
        List<ApprovalStepRecordResponse> responses = multiLevelApprovalService.getStepRecords(processInstanceId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(responses);
    }

    @GetMapping("/history/{administeredItemId}")
    @Operation(summary = "查询审批历史")
    public ApiResponse<List<ApprovalProcessInstanceResponse>> getProcessHistory(
            @PathVariable UUID administeredItemId) {
        List<ApprovalProcessInstanceResponse> responses = multiLevelApprovalService
                .getProcessHistory(administeredItemId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(responses);
    }

    @GetMapping("/pending/{administeredItemId}")
    @Operation(summary = "查询当前待审批流程")
    public ApiResponse<ApprovalProcessInstanceResponse> getPendingProcess(
            @PathVariable UUID administeredItemId) {
        return multiLevelApprovalService.getPendingProcessInstance(administeredItemId)
                .map(this::toResponse)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.ok(null));
    }

    private ApprovalProcessInstanceResponse toResponse(ApprovalProcessInstance instance) {
        List<ApprovalStepRecordResponse> stepResponses = instance.getStepRecords().stream()
                .map(this::toResponse)
                .toList();

        return new ApprovalProcessInstanceResponse(
                instance.getId(),
                instance.getTemplate().getId(),
                instance.getTemplate().getName(),
                instance.getAdministeredItemId(),
                instance.getSubmitter(),
                instance.getCurrentStep(),
                instance.getStatus(),
                instance.getCreatedAt(),
                instance.getCompletedAt(),
                stepResponses);
    }

    private ApprovalStepRecordResponse toResponse(ApprovalStepRecord record) {
        return new ApprovalStepRecordResponse(
                record.getId(),
                record.getProcessInstance().getId(),
                record.getStepNumber(),
                record.getStepName(),
                record.getAssigneeRule(),
                record.getAssigneeValue(),
                record.getApprover(),
                record.getAssignedAt(),
                record.getApprovedAt(),
                record.getOpinion(),
                record.getStatus(),
                record.getTimeoutAt());
    }
}
