package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptRequest;
import io.github.matrixlane.metadataregistry.web.dto.dec.DataElementConceptResponse;
import io.github.matrixlane.metadataregistry.web.service.DataElementConceptService;
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
@RequestMapping("/api/v1/data-element-concepts")
@RequiredArgsConstructor
@Tag(name = "5. 概念 - 数据元概念", description = "DataElementConcept (DEC) 实体的 CRUD")
public class DataElementConceptController {

    private final DataElementConceptService service;

    @GetMapping
    @Operation(summary = "分页列出 DEC")
    public ApiResponse<PageResponse<DataElementConceptResponse>> list(@RequestParam(required = false) String q,
                                                                      Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询 DEC 详情")
    public ApiResponse<DataElementConceptResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增 DEC")
    public ApiResponse<DataElementConceptResponse> create(@Valid @RequestBody DataElementConceptRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新 DEC")
    public ApiResponse<DataElementConceptResponse> update(@PathVariable UUID id,
                                                          @Valid @RequestBody DataElementConceptRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除 DEC")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
