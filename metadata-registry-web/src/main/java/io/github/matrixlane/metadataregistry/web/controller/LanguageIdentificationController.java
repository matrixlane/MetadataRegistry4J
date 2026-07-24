package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageRequest;
import io.github.matrixlane.metadataregistry.web.dto.language.LanguageResponse;
import io.github.matrixlane.metadataregistry.web.service.LanguageIdentificationService;
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
@RequestMapping("/api/v1/languages")
@RequiredArgsConstructor
@Tag(name = "1. 字典 - 语言标识", description = "多语言 designation/definition 引用的语言字典")
public class LanguageIdentificationController {

    private final LanguageIdentificationService service;

    @GetMapping
    @Operation(summary = "分页列出语言")
    public ApiResponse<PageResponse<LanguageResponse>> list(@RequestParam(required = false) String q,
                                                            @RequestParam(required = false) Boolean enabled,
                                                            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, enabled, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询语言详情")
    public ApiResponse<LanguageResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增语言")
    public ApiResponse<LanguageResponse> create(@Valid @RequestBody LanguageRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新语言")
    public ApiResponse<LanguageResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody LanguageRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除语言")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
