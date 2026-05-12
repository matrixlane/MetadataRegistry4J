package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeRequest;
import io.github.matrixlane.metadataregistry.web.dto.datatype.DataTypeResponse;
import io.github.matrixlane.metadataregistry.web.service.DataTypeService;
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
@RequestMapping("/api/v1/data-types")
@RequiredArgsConstructor
@Tag(name = "6. 域 - 数据类型", description = "DataType 实体的 CRUD")
public class DataTypeController {

    private final DataTypeService service;

    @GetMapping
    @Operation(summary = "分页列出数据类型")
    public ApiResponse<PageResponse<DataTypeResponse>> list(@RequestParam(required = false) String q,
                                                            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询数据类型详情")
    public ApiResponse<DataTypeResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增数据类型")
    public ApiResponse<DataTypeResponse> create(@Valid @RequestBody DataTypeRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新数据类型")
    public ApiResponse<DataTypeResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody DataTypeRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据类型")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
