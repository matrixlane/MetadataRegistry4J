package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.IdentifierVersionHistoryResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierCreateRequest;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.VersionTransitionRequest;
import io.github.matrixlane.metadataregistry.web.service.ScopedIdentifierService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scoped-identifiers")
@RequiredArgsConstructor
@Tag(name = "4. 标识管理 - ScopedIdentifier", description = "标识符生命周期与版本管理")
public class ScopedIdentifierController {

    private final ScopedIdentifierService service;

    @GetMapping
    @Operation(summary = "分页列出标识符")
    public ApiResponse<PageResponse<ScopedIdentifierResponse>> list(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询标识符详情")
    public ApiResponse<ScopedIdentifierResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增标识符")
    public ApiResponse<ScopedIdentifierResponse> create(
            @Valid @RequestBody ScopedIdentifierCreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新标识符")
    public ApiResponse<ScopedIdentifierResponse> update(@PathVariable UUID id,
                                                        @Valid @RequestBody ScopedIdentifierCreateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/transition")
    @Operation(summary = "版本状态流转")
    public ApiResponse<ScopedIdentifierResponse> transition(
            @PathVariable UUID id,
            @Valid @RequestBody VersionTransitionRequest request) {
        return ApiResponse.ok(service.transition(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除标识符")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "查询版本历史")
    public ApiResponse<List<IdentifierVersionHistoryResponse>> history(@PathVariable UUID id) {
        return ApiResponse.ok(service.listHistory(id));
    }
}
