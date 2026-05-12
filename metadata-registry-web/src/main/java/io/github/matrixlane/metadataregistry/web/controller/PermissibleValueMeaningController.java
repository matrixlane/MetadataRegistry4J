package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.permissiblevaluemeaning.PermissibleValueMeaningRequest;
import io.github.matrixlane.metadataregistry.web.dto.permissiblevaluemeaning.PermissibleValueMeaningResponse;
import io.github.matrixlane.metadataregistry.web.service.PermissibleValueMeaningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/permissible-value-meanings")
@RequiredArgsConstructor
@Tag(name = "8. 许可值含义", description = "PermissibleValueMeaning 的 CRUD（关联 ValueMeaning 与 PermissibleValue）")
public class PermissibleValueMeaningController {

    private final PermissibleValueMeaningService service;

    @GetMapping
    @Operation(summary = "按许可值列出含义关联")
    public ApiResponse<List<PermissibleValueMeaningResponse>> list(
            @RequestParam UUID permissibleValueId) {
        return ApiResponse.ok(service.listByPermissibleValue(permissibleValueId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询详情")
    public ApiResponse<PermissibleValueMeaningResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新建关联")
    public ApiResponse<PermissibleValueMeaningResponse> create(
            @Valid @RequestBody PermissibleValueMeaningRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新关联")
    public ApiResponse<PermissibleValueMeaningResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PermissibleValueMeaningRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除关联")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
