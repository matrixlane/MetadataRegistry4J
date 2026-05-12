package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.referencedocument.ReferenceDocumentRequest;
import io.github.matrixlane.metadataregistry.web.dto.referencedocument.ReferenceDocumentResponse;
import io.github.matrixlane.metadataregistry.web.service.ReferenceDocumentService;
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
@RequestMapping("/api/v1/reference-documents")
@RequiredArgsConstructor
@Tag(name = "4. 主体 - 参考文献", description = "ReferenceDocument 实体的 CRUD")
public class ReferenceDocumentController {

    private final ReferenceDocumentService service;

    @GetMapping
    @Operation(summary = "分页列出参考文献")
    public ApiResponse<PageResponse<ReferenceDocumentResponse>> list(@RequestParam(required = false) String q,
                                                                     Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询参考文献详情")
    public ApiResponse<ReferenceDocumentResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增参考文献")
    public ApiResponse<ReferenceDocumentResponse> create(@Valid @RequestBody ReferenceDocumentRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新参考文献")
    public ApiResponse<ReferenceDocumentResponse> update(@PathVariable UUID id,
                                                         @Valid @RequestBody ReferenceDocumentRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除参考文献")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
