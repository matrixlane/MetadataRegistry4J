package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApprovalRecordResponse;
import io.github.matrixlane.metadataregistry.web.dto.approval.ApproveRequest;
import io.github.matrixlane.metadataregistry.web.dto.approval.RejectRequest;
import io.github.matrixlane.metadataregistry.web.entity.ApprovalRecord;
import io.github.matrixlane.metadataregistry.web.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "审批工作流", description = "1级审批：提交、通过、退回、重新提交、历史查询")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/submit/{administeredItemId}")
    @Operation(summary = "提交审批",
            description = "将元数据项提交至审批队列，创建 PENDING 审批记录")
    public ApiResponse<ApprovalRecordResponse> submitForApproval(@PathVariable UUID administeredItemId) {
        return ApiResponse.ok(toResponse(approvalService.submitForApproval(administeredItemId)));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "审批通过",
            description = "审批人批准该审批请求，自动触发 ISO 状态迁移至 STANDARD")
    public ApiResponse<ApprovalRecordResponse> approve(@PathVariable UUID id,
                                                       @Valid @RequestBody ApproveRequest request) {
        return ApiResponse.ok(toResponse(approvalService.approve(id, request.opinion())));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "审批退回",
            description = "审批人退回该审批请求，必须填写退回意见；ISO 状态保持不变")
    public ApiResponse<ApprovalRecordResponse> reject(@PathVariable UUID id,
                                                      @Valid @RequestBody RejectRequest request) {
        return ApiResponse.ok(toResponse(approvalService.reject(id, request.opinion())));
    }

    @PostMapping("/resubmit/{administeredItemId}")
    @Operation(summary = "重新提交审批",
            description = "上次审批被退回后，修改元数据项并重新提交审批")
    public ApiResponse<ApprovalRecordResponse> resubmit(@PathVariable UUID administeredItemId) {
        return ApiResponse.ok(toResponse(approvalService.resubmit(administeredItemId)));
    }

    @GetMapping("/history/{administeredItemId}")
    @Operation(summary = "查询审批历史",
            description = "获取指定管理项的全部审批记录，按提交时间倒序排列")
    public ApiResponse<List<ApprovalRecordResponse>> getApprovalHistory(@PathVariable UUID administeredItemId) {
        List<ApprovalRecordResponse> responses = approvalService.getApprovalHistory(administeredItemId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(responses);
    }

    private ApprovalRecordResponse toResponse(ApprovalRecord record) {
        return new ApprovalRecordResponse(
                record.getId(),
                record.getAdministeredItemId(),
                record.getSubmitter(),
                record.getSubmittedAt(),
                record.getApprover(),
                record.getApprovedAt(),
                record.getOpinion(),
                record.getResult());
    }
}
