package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespacePermissionRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespacePermissionResponse;
import io.github.matrixlane.metadataregistry.web.service.NamespacePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/namespaces")
@RequiredArgsConstructor
@Tag(name = "3. 注册管理 - 命名空间权限", description = "NamespacePermission 实体的 CRUD")
public class NamespacePermissionController {

    private final NamespacePermissionService service;

    @GetMapping("/{id}/permissions")
    @Operation(summary = "列出命名空间的访问权限配置列表")
    public ApiResponse<List<NamespacePermissionResponse>> getPermissions(@PathVariable UUID id) {
        return ApiResponse.ok(service.getPermissions(id));
    }

    @PostMapping("/{id}/permissions")
    @Operation(summary = "新增或更新命名空间的访问权限配置")
    public ApiResponse<NamespacePermissionResponse> savePermission(
            @PathVariable UUID id,
            @Valid @RequestBody NamespacePermissionRequest request) {
        return ApiResponse.ok(service.savePermission(id, request));
    }

    @DeleteMapping("/permissions/{permissionId}")
    @Operation(summary = "删除指定的命名空间访问权限配置")
    public ApiResponse<Void> deletePermission(@PathVariable UUID permissionId) {
        service.deletePermission(permissionId);
        return ApiResponse.ok();
    }
}
