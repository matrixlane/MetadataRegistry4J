package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassRequest;
import io.github.matrixlane.metadataregistry.web.dto.measureclass.MeasureClassResponse;
import io.github.matrixlane.metadataregistry.web.service.MeasureClassService;
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
@RequestMapping("/api/v1/measure-classes")
@RequiredArgsConstructor
@Tag(name = "9. 域 - 计量类", description = "MeasureClass 实体的 CRUD")
public class MeasureClassController {

    private final MeasureClassService service;

    @GetMapping
    @Operation(summary = "分页列出计量类")
    public ApiResponse<PageResponse<MeasureClassResponse>> list(@RequestParam(required = false) String q,
                                                                Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询计量类详情")
    public ApiResponse<MeasureClassResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增计量类", description = "同时维护计量类与维度的 0..* 关联")
    public ApiResponse<MeasureClassResponse> create(@Valid @RequestBody MeasureClassRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新计量类", description = "同时维护计量类与维度的 0..* 关联")
    public ApiResponse<MeasureClassResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody MeasureClassRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除计量类")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
