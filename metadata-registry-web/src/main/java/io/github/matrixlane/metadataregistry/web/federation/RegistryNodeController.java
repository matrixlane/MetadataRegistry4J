package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 联邦节点管理接口（仅超级管理员，见 SecurityConfig）。
 */
@RestController
@RequestMapping("/api/v1/federation/nodes")
@RequiredArgsConstructor
@Tag(name = "联邦协作 - 节点管理", description = "联邦节点登记与管理接口")
public class RegistryNodeController {

    private final RegistryNodeService service;

    @GetMapping
    @Operation(summary = "联邦节点列表")
    public ApiResponse<List<RegistryNodeView>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @Operation(summary = "登记联邦节点")
    public ApiResponse<RegistryNodeView> create(@Valid @RequestBody RegistryNodeRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新联邦节点")
    public ApiResponse<RegistryNodeView> update(@PathVariable UUID id,
                                                @Valid @RequestBody RegistryNodeRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除联邦节点")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
