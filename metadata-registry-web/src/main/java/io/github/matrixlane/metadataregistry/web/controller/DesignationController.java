package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationResponse;
import io.github.matrixlane.metadataregistry.web.service.DesignationService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/items/{itemId}/designations")
@RequiredArgsConstructor
@Tag(name = "指称管理", description = "Designation 独立 CRUD，集成命名规范与冲突检测")
public class DesignationController {

    private final DesignationService designationService;

    @GetMapping
    @Operation(summary = "列出某注册项的所有指称")
    public ApiResponse<List<DesignationResponse>> list(@PathVariable UUID itemId) {
        return ApiResponse.ok(designationService.listByItemId(itemId));
    }

    @PostMapping
    @Operation(summary = "新建指称")
    public ApiResponse<DesignationResponse> create(@PathVariable UUID itemId,
                                                   @Valid @RequestBody DesignationRequest request) {
        return ApiResponse.ok(designationService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询指称详情")
    public ApiResponse<DesignationResponse> get(@PathVariable UUID itemId, @PathVariable UUID id) {
        return ApiResponse.ok(designationService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新指称")
    public ApiResponse<DesignationResponse> update(@PathVariable UUID itemId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody DesignationRequest request) {
        return ApiResponse.ok(designationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除指称")
    public ApiResponse<Void> delete(@PathVariable UUID itemId, @PathVariable UUID id) {
        designationService.delete(id);
        return ApiResponse.ok();
    }
}
