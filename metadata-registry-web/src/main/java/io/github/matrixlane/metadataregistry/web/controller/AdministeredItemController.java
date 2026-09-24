package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.ReferenceDocumentLinkRequest;
import io.github.matrixlane.metadataregistry.web.dto.registration.ApprovalResultResponse;
import io.github.matrixlane.metadataregistry.web.dto.registration.AvailableTransitionView;
import io.github.matrixlane.metadataregistry.web.dto.registration.TransitionApproveRequest;
import io.github.matrixlane.metadataregistry.web.dto.registration.TransitionExecuteRequest;
import io.github.matrixlane.metadataregistry.web.dto.registration.TransitionRejectRequest;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.github.matrixlane.metadataregistry.web.service.AdministeredItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/administered-items")
@RequiredArgsConstructor
@Tag(name = "8. 注册项生命周期", description = "AdministeredItem 列表与状态迁移")
public class AdministeredItemController {

    private final AdministeredItemService service;
    private final ActiveRegistrarService activeRegistrarService;

    @GetMapping
    @Operation(summary = "分页列出受管项",
            description = "可按 status 过滤；列出所有 ItemType（DataElement / DataElementConcept / ObjectClass / ...）的受管项")
    public ApiResponse<PageResponse<AdministeredItemResponse>> list(
            @RequestParam(required = false) RegistrationStatus status,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询受管项详情")
    public ApiResponse<AdministeredItemResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/transitions")
    @Operation(summary = "列出当前可用的迁移动作",
            description = "推进流程面板数据源：逐条给出目标状态、操作名、是否需审核、审批进度（已批准/通过人数）与当前用户权限（可否执行/可否批准/是否已批准）")
    public ApiResponse<List<AvailableTransitionView>> transitions(@PathVariable UUID id) {
        return ApiResponse.ok(service.availableTransitions(id, activeRegistrarService.currentAccountIdOrNull()));
    }

    @PostMapping("/{id}/transitions/execute")
    @Operation(summary = "执行无需审核的迁移",
            description = "按规则表校验迁移合法性与现行 ADMIN/REGISTRAR 角色权限后直接生效；需审核的迁移返回 1120，须经批准流程")
    public ApiResponse<AdministeredItemResponse> execute(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionExecuteRequest request) {
        return ApiResponse.ok(service.executeTransition(id, request.to(), request.note(),
                activeRegistrarService.currentAccountIdOrNull()));
    }

    @PostMapping("/{id}/transitions/approve")
    @Operation(summary = "批准需审核的迁移",
            description = "要求当前账号的活动 Registrar 身份归属该项注册机构且在审核人集合内；达到通过人数时迁移在同一事务内自动生效")
    public ApiResponse<ApprovalResultResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionApproveRequest request) {
        return ApiResponse.ok(service.approveTransition(id, request.to(),
                activeRegistrarService.currentAccountIdOrNull()));
    }

    @PostMapping("/{id}/transitions/reject")
    @Operation(summary = "驳回进行中的 RECORDED→QUALIFIED 申请",
            description = "要求当前账号为该项所属注册机构审核人集合内的活动 Registrar；驳回后管理状态变为 REJECTED，注册状态不变")
    public ApiResponse<AdministeredItemResponse> reject(
            @PathVariable UUID id,
            @RequestBody TransitionRejectRequest request) {
        return ApiResponse.ok(service.rejectTransition(id, request == null ? null : request.note(),
                activeRegistrarService.currentAccountIdOrNull()));
    }

    @PostMapping("/{id}/request-qualification")
    @Operation(summary = "主管申请合格",
            description = "注册状态为 RECORDED 且当前用户为该管理项主管时，置管理状态为 PROVISIONALLY_QUALIFIED 并启动审核流")
    public ApiResponse<AdministeredItemResponse> requestQualification(@PathVariable UUID id) {
        return ApiResponse.ok(service.requestQualification(id,
                activeRegistrarService.currentAccountIdOrNull()));
    }

    @PostMapping("/{id}/constraint-set")
    @Operation(summary = "为受管项设置约束集")
    public ApiResponse<AdministeredItemResponse> updateConstraintSet(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID constraintSetId) {
        return ApiResponse.ok(service.updateConstraintSet(id, constraintSetId));
    }

    @PostMapping("/{id}/reference-documents")
    @Operation(summary = "为受管项关联参考文档")
    public ApiResponse<AdministeredItemResponse> addReferenceDocuments(
            @PathVariable UUID id,
            @Valid @RequestBody ReferenceDocumentLinkRequest request) {
        return ApiResponse.ok(service.addReferenceDocuments(id, request.referenceDocumentIds()));
    }

    @DeleteMapping("/{id}/reference-documents")
    @Operation(summary = "为受管项移除参考文档关联")
    public ApiResponse<AdministeredItemResponse> removeReferenceDocuments(
            @PathVariable UUID id,
            @Valid @RequestBody ReferenceDocumentLinkRequest request) {
        return ApiResponse.ok(service.removeReferenceDocuments(id, request.referenceDocumentIds()));
    }
}
