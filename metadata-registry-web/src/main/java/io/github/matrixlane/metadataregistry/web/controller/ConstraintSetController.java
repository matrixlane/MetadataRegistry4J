package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.constraintset.ConstraintSetRequest;
import io.github.matrixlane.metadataregistry.web.dto.constraintset.ConstraintSetResponse;
import io.github.matrixlane.metadataregistry.web.service.ConstraintSetService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/constraint-sets")
@RequiredArgsConstructor
@Tag(name = "注册通用设施 - 约束集", description = "ConstraintSet 实体的 CRUD")
public class ConstraintSetController {

    private final ConstraintSetService service;

    @GetMapping
    @Operation(summary = "分页列出约束集")
    public ApiResponse<PageResponse<ConstraintSetResponse>> list(@RequestParam(required = false) String q,
                                                                 Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询约束集详情")
    public ApiResponse<ConstraintSetResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增约束集")
    public ApiResponse<ConstraintSetResponse> create(@Valid @RequestBody ConstraintSetRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新约束集")
    public ApiResponse<ConstraintSetResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody ConstraintSetRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除约束集")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
