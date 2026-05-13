package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingSchemeStatusTransitionRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingVersionInfo;
import io.github.matrixlane.metadataregistry.web.service.MappingDiffService;
import io.github.matrixlane.metadataregistry.web.service.MappingSchemeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for mapping scheme CRUD and version lifecycle management (WI-011-2-3-1).
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>CRUD: list, get, create, update, delete</li>
 *   <li>Version lifecycle: publish, archive, create new version</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/mapping-schemes")
@RequiredArgsConstructor
@Tag(name = "11. 映射方案管理", description = "ISO/IEC 11179 Clause 11 — 映射方案 CRUD 与版本生命周期（WI-011-2-3-1）")
public class MappingSchemeController {

    private final MappingSchemeService service;
    private final MappingDiffService diffService;

    // ==================== CRUD ====================

    @GetMapping
    @Operation(summary = "分页列出映射方案",
            description = "可按关键词搜索（名称、源URI、目标URI、版本号）")
    public ApiResponse<PageResponse<MappingSchemeResponse>> list(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询映射方案详情")
    public ApiResponse<MappingSchemeResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "创建映射方案",
            description = "初始版本为 0.1.0，状态为 DRAFT")
    public ApiResponse<MappingSchemeResponse> create(
            @Valid @RequestBody MappingSchemeRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑映射方案",
            description = "仅允许编辑 DRAFT 状态的方案")
    public ApiResponse<MappingSchemeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody MappingSchemeRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除映射方案",
            description = "仅允许删除 DRAFT 状态的方案")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    // ==================== Version Lifecycle ====================

    @PostMapping("/{id}/transition")
    @Operation(summary = "迁移方案状态",
            description = "支持 DRAFT→PUBLISHED（发布）和 PUBLISHED→ARCHIVED（归档）")
    public ApiResponse<MappingSchemeResponse> transition(
            @PathVariable UUID id,
            @Valid @RequestBody MappingSchemeStatusTransitionRequest request) {
        return ApiResponse.ok(service.transitionStatus(id, request.to()));
    }

    @PostMapping("/{id}/new-version")
    @Operation(summary = "基于已发布方案创建新版本",
            description = "从 PUBLISHED 方案创建新 DRAFT 版本，版本号自动递增补丁号")
    public ApiResponse<MappingSchemeResponse> createNewVersion(@PathVariable UUID id) {
        return ApiResponse.ok(service.createNewVersion(id));
    }

    // ==================== Version Diff (WI-011-2-3-2) ====================

    @GetMapping("/{id}/versions")
    @Operation(summary = "获取方案版本 lineage",
            description = "返回该方案 lineage 下的所有版本（含祖先和后代），按创建时间从早到晚排序")
    public ApiResponse<List<MappingVersionInfo>> getVersions(@PathVariable UUID id) {
        return ApiResponse.ok(diffService.getVersionLineage(id));
    }

    @GetMapping("/{id}/diff")
    @Operation(summary = "对比两个版本映射方案差异",
            description = "选择 baseId（基准版本）和 compareId（对比版本），可选 mappingType 过滤，返回映射关系的增删改差异")
    public ApiResponse<MappingDiffResponse> diff(
            @PathVariable UUID id,
            @RequestParam UUID baseId,
            @RequestParam UUID compareId,
            @RequestParam(required = false) MappingType mappingType) {
        return ApiResponse.ok(diffService.computeDiff(baseId, compareId, mappingType));
    }
}
