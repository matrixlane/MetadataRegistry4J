package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleOption;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleRequest;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleResponse;
import io.github.matrixlane.metadataregistry.web.service.RoleService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "3. 主体 - 角色", description = "Role 实体的 CRUD")
public class RoleController {

    private final RoleService service;

    @GetMapping
    @Operation(summary = "分页列出角色（q 按 role_name 忽略大小写包含匹配）")
    public ApiResponse<PageResponse<RoleResponse>> list(@RequestParam(required = false) String q,
                                                        Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.list(q, pageable)));
    }

    @GetMapping("/options")
    @Operation(summary = "列出所有角色（不分页，用于下拉框）")
    public ApiResponse<List<RoleOption>> options() {
        return ApiResponse.ok(service.options());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询角色详情")
    public ApiResponse<RoleResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @Operation(summary = "新增角色")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新角色")
    public ApiResponse<RoleResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody RoleRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色（被联系人引用时拒绝）")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
