package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.ReferenceDocumentLinkRequest;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.StateTransitionRequest;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/administered-items")
@RequiredArgsConstructor
@Tag(name = "8. 注册项生命周期", description = "AdministeredItem 列表与状态迁移")
public class AdministeredItemController {

    private final AdministeredItemService service;

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

    @PostMapping("/{id}/transition")
    @Operation(summary = "迁移注册状态",
            description = "按 ISO 11179-6:2023 标准生命周期校验合法迁移：INCOMPLETE → CANDIDATE → RECORDED → QUALIFIED → STANDARD ↔ PREFERRED_STANDARD → SUPERSEDED → RETIRED → HISTORICAL；为符合 ISO 11179-3:2023 第9章，支持 APPLICATION 状态")
    public ApiResponse<AdministeredItemResponse> transition(@PathVariable UUID id,
                                                            @Valid @RequestBody StateTransitionRequest request) {
        return ApiResponse.ok(service.transition(id, request.to(), request.administrativeNote()));
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
