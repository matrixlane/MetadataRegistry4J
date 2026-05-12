package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementResponse;
import io.github.matrixlane.metadataregistry.web.service.DataElementService;
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
@RequestMapping("/api/v1/data-elements")
@RequiredArgsConstructor
@Tag(name = "7. 数据元", description = "DataElement 实体的 CRUD（核心数据元）")
public class DataElementController {

    private final DataElementService service;

    @GetMapping
    @Operation(summary = "分页列出数据元")
    public ApiResponse<PageResponse<DataElementResponse>> list(@RequestParam(required = false) String q,
                                                               Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询数据元详情")
    public ApiResponse<DataElementResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增数据元")
    public ApiResponse<DataElementResponse> create(@Valid @RequestBody DataElementRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新数据元")
    public ApiResponse<DataElementResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody DataElementRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除数据元")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
