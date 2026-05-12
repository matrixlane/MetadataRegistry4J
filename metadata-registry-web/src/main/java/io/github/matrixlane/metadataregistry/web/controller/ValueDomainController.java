package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.valuedomain.ValueDomainResponse;
import io.github.matrixlane.metadataregistry.web.service.ValueDomainService;
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
@RequestMapping("/api/v1/value-domains")
@RequiredArgsConstructor
@Tag(name = "6. 域 - 值域", description = "ValueDomain 实体的 CRUD（含 Described / LocalEnumerated 子类型，附带 PermissibleValue 子表管理）")
public class ValueDomainController {

    private final ValueDomainService service;

    @GetMapping
    @Operation(summary = "分页列出值域")
    public ApiResponse<PageResponse<ValueDomainResponse>> list(@RequestParam(required = false) String q,
                                                               Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询值域详情")
    public ApiResponse<ValueDomainResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增值域", description = "type 决定子类型；当 type=LOCAL_ENUMERATED 时，permissibleValues 字段被使用")
    public ApiResponse<ValueDomainResponse> create(@Valid @RequestBody ValueDomainRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新值域")
    public ApiResponse<ValueDomainResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody ValueDomainRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除值域")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
