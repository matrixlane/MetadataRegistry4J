package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainRequest;
import io.github.matrixlane.metadataregistry.web.dto.conceptualdomain.ConceptualDomainResponse;
import io.github.matrixlane.metadataregistry.web.service.ConceptualDomainService;
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
@RequestMapping("/api/v1/conceptual-domains")
@RequiredArgsConstructor
@Tag(name = "6. 域 - 概念域", description = "ConceptualDomain 实体的 CRUD（含 Described / LocalEnumerated 子类型）")
public class ConceptualDomainController {

    private final ConceptualDomainService service;

    @GetMapping
    @Operation(summary = "分页列出概念域")
    public ApiResponse<PageResponse<ConceptualDomainResponse>> list(@RequestParam(required = false) String q,
                                                                    Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询概念域详情")
    public ApiResponse<ConceptualDomainResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增概念域", description = "type 字段决定子类型，缺省为 DESCRIBED")
    public ApiResponse<ConceptualDomainResponse> create(@Valid @RequestBody ConceptualDomainRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新概念域", description = "类型在创建后不可变更")
    public ApiResponse<ConceptualDomainResponse> update(@PathVariable UUID id,
                                                        @Valid @RequestBody ConceptualDomainRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除概念域")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
