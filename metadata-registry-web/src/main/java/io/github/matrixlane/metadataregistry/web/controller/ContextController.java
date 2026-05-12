package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextRequest;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextResponse;
import io.github.matrixlane.metadataregistry.web.service.ContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contexts")
@RequiredArgsConstructor
@Tag(name = "上下文", description = "Context 实体的 CRUD")
public class ContextController {

    private final ContextService service;

    @GetMapping
    @Operation(summary = "分页查询上下文列表")
    public ApiResponse<PageResponse<ContextResponse>> list(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/options")
    @Operation(summary = "列出所有上下文（不分页，用于下拉框）")
    @Transactional(readOnly = true)
    public ApiResponse<List<ContextOption>> options(@RequestParam(required = false) String q) {
        List<ContextOption> options = service.options(q).stream()
                .map(c -> new ContextOption(c.id(), c.label()))
                .toList();
        return ApiResponse.ok(options);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个上下文")
    public ApiResponse<ContextResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "创建上下文")
    public ApiResponse<ContextResponse> create(
            @Valid @RequestBody ContextRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新上下文")
    public ApiResponse<ContextResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ContextRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除上下文")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    public record ContextOption(UUID id, String label) {
    }
}
