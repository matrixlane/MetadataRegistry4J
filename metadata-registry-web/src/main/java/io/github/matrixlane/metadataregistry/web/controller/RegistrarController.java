package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarResponse;
import io.github.matrixlane.metadataregistry.web.service.RegistrarService;
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
@RequestMapping("/api/v1/registrars")
@RequiredArgsConstructor
@Tag(name = "2. 注册管理 - 注册人员", description = "Registrar 实体的 CRUD")
public class RegistrarController {

    private final RegistrarService service;

    @GetMapping
    @Operation(summary = "分页列出注册人员")
    public ApiResponse<PageResponse<RegistrarResponse>> list(@RequestParam(required = false) String q,
                                                             Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询注册人员详情")
    public ApiResponse<RegistrarResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增注册人员")
    public ApiResponse<RegistrarResponse> create(@Valid @RequestBody RegistrarRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新注册人员")
    public ApiResponse<RegistrarResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody RegistrarRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除注册人员")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
