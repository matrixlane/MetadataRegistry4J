package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeSchemeResponse;
import io.github.matrixlane.metadataregistry.web.service.DataTypeSchemeService;
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
@RequestMapping("/api/v1/data-type-schemes")
@RequiredArgsConstructor
@Tag(name = "6. 域 - 数据类型方案", description = "DataTypeScheme 实体的 CRUD")
public class DataTypeSchemeController {

    private final DataTypeSchemeService service;

    @GetMapping
    @Operation(summary = "分页列出数据类型方案")
    public ApiResponse<PageResponse<DataTypeSchemeResponse>> list(@RequestParam(required = false) String q,
                                                                  Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询数据类型方案详情")
    public ApiResponse<DataTypeSchemeResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增数据类型方案")
    public ApiResponse<DataTypeSchemeResponse> create(@Valid @RequestBody DataTypeSchemeRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新数据类型方案")
    public ApiResponse<DataTypeSchemeResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody DataTypeSchemeRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据类型方案")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
