package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualRequest;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualResponse;
import io.github.matrixlane.metadataregistry.web.service.IndividualService;
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
@RequestMapping("/api/v1/individuals")
@RequiredArgsConstructor
@Tag(name = "2. 主体 - 个人", description = "Individual 实体的 CRUD")
public class IndividualController {

    private final IndividualService service;

    @GetMapping
    @Operation(summary = "分页列出个人")
    public ApiResponse<PageResponse<IndividualResponse>> list(@RequestParam(required = false) String q,
                                                              Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询个人详情")
    public ApiResponse<IndividualResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增个人")
    public ApiResponse<IndividualResponse> create(@Valid @RequestBody IndividualRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新个人")
    public ApiResponse<IndividualResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody IndividualRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除个人")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
