package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.documenttype.DocumentTypeRequest;
import io.github.matrixlane.metadataregistry.web.dto.documenttype.DocumentTypeResponse;
import io.github.matrixlane.metadataregistry.web.service.DocumentTypeService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/document-types")
@RequiredArgsConstructor
@Tag(name = "4. 主体 - 文档类型", description = "DocumentType 字典维护")
public class DocumentTypeController {

    private final DocumentTypeService service;

    @GetMapping
    @Operation(summary = "分页列出文档类型")
    public ApiResponse<PageResponse<DocumentTypeResponse>> list(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询文档类型详情")
    public ApiResponse<DocumentTypeResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增文档类型")
    public ApiResponse<DocumentTypeResponse> create(@Valid @RequestBody DocumentTypeRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新文档类型")
    public ApiResponse<DocumentTypeResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody DocumentTypeRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档类型")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
