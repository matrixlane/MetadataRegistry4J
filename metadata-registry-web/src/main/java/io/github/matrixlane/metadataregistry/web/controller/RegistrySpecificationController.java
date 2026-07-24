package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationRequest;
import io.github.matrixlane.metadataregistry.web.dto.registries.RegistrySpecificationResponse;
import io.github.matrixlane.metadataregistry.web.service.RegistrySpecificationService;
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
@RequestMapping("/api/v1/registry-specifications")
@RequiredArgsConstructor
@Tag(name = "注册通用设施 - 注册规范", description = "RegistrySpecification 实体的 CRUD")
public class RegistrySpecificationController {

    private final RegistrySpecificationService service;

    @GetMapping
    @Operation(summary = "分页列出注册规范")
    public ApiResponse<PageResponse<RegistrySpecificationResponse>> list(@RequestParam(required = false) String q,
                                                                         Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询注册规范详情")
    public ApiResponse<RegistrySpecificationResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增注册规范")
    public ApiResponse<RegistrySpecificationResponse> create(@Valid @RequestBody RegistrySpecificationRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新注册规范")
    public ApiResponse<RegistrySpecificationResponse> update(@PathVariable UUID id,
                                                             @Valid @RequestBody RegistrySpecificationRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除注册规范")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
