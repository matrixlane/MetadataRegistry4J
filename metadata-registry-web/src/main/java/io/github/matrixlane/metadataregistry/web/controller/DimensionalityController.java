package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityRequest;
import io.github.matrixlane.metadataregistry.web.dto.dimensionality.DimensionalityResponse;
import io.github.matrixlane.metadataregistry.web.service.DimensionalityService;
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
@RequestMapping("/api/v1/dimensionalities")
@RequiredArgsConstructor
@Tag(name = "9. 域 - 维度", description = "Dimensionality 实体的 CRUD")
public class DimensionalityController {

    private final DimensionalityService service;

    @GetMapping
    @Operation(summary = "分页列出维度")
    public ApiResponse<PageResponse<DimensionalityResponse>> list(@RequestParam(required = false) String q,
                                                                  Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询维度详情")
    public ApiResponse<DimensionalityResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增维度")
    public ApiResponse<DimensionalityResponse> create(@Valid @RequestBody DimensionalityRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新维度")
    public ApiResponse<DimensionalityResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody DimensionalityRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除维度")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
