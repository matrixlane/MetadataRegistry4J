package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DefinitionRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DefinitionResponse;
import io.github.matrixlane.metadataregistry.web.service.DefinitionService;
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
@RequestMapping("/api/v1/items/{itemId}/definitions")
@RequiredArgsConstructor
@Tag(name = "定义管理", description = "Definition 独立 CRUD，集成操作日志")
public class DefinitionController {

    private final DefinitionService definitionService;

    @GetMapping
    @Operation(summary = "列出某注册项的所有定义")
    public ApiResponse<List<DefinitionResponse>> list(@PathVariable UUID itemId) {
        return ApiResponse.ok(definitionService.listByItemId(itemId));
    }

    @PostMapping
    @Operation(summary = "新建定义")
    public ApiResponse<DefinitionResponse> create(@PathVariable UUID itemId,
                                                   @Valid @RequestBody DefinitionRequest request) {
        return ApiResponse.ok(definitionService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询定义详情")
    public ApiResponse<DefinitionResponse> get(@PathVariable UUID itemId, @PathVariable UUID id) {
        return ApiResponse.ok(definitionService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新定义")
    public ApiResponse<DefinitionResponse> update(@PathVariable UUID itemId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody DefinitionRequest request) {
        return ApiResponse.ok(definitionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除定义")
    public ApiResponse<Void> delete(@PathVariable UUID itemId, @PathVariable UUID id) {
        definitionService.delete(id);
        return ApiResponse.ok();
    }
}
