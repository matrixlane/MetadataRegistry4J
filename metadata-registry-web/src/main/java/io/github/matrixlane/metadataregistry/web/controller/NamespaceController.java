package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceResponse;
import io.github.matrixlane.metadataregistry.web.service.NamespaceService;
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
@RequestMapping("/api/v1/namespaces")
@RequiredArgsConstructor
@Tag(name = "3. 注册管理 - 命名空间", description = "Namespace 实体的 CRUD")
public class NamespaceController {

    private final NamespaceService service;

    @GetMapping
    @Operation(summary = "分页列出命名空间")
    public ApiResponse<PageResponse<NamespaceResponse>> list(@RequestParam(required = false) String q,
                                                             Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询命名空间详情")
    public ApiResponse<NamespaceResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增命名空间")
    public ApiResponse<NamespaceResponse> create(@Valid @RequestBody NamespaceRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新命名空间")
    public ApiResponse<NamespaceResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody NamespaceRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除命名空间")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
