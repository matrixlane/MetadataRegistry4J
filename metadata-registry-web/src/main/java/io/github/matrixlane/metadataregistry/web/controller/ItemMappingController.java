package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingHierarchyRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingRequest;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ItemMappingResponse;
import io.github.matrixlane.metadataregistry.web.service.ItemMappingCrudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 条目映射 REST API（Item_Mapping 包）。
 *
 * <p>映射条目本身是 Item 子类：创建时可携带注册请求经统一注册管线
 * 建立受管条目外壳；注册状态变更复用统一条目注册端点。
 */
@RestController
@RequestMapping("/api/v1/item-mappings")
@RequiredArgsConstructor
@Tag(name = "11. 条目映射管理", description = "Item_Mapping CRUD 与映射层级")
public class ItemMappingController {

    private final ItemMappingCrudService crudService;

    @GetMapping
    @Operation(summary = "分页查询条目映射", description = "支持按主体条目、客体条目、映射度过滤。")
    public ApiResponse<Page<ItemMappingResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) UUID subjectItemId,
            @RequestParam(required = false) UUID objectItemId,
            @RequestParam(required = false) MappingDegree degree) {
        return ApiResponse.ok(crudService.list(page, size, sort, subjectItemId, objectItemId, degree));
    }

    @PostMapping
    @Operation(summary = "创建条目映射", description = "subject/object 必须引用已存在条目；携带 registration 时同步建立受管条目外壳。")
    public ApiResponse<ItemMappingResponse> create(@Valid @RequestBody ItemMappingRequest request) {
        return ApiResponse.ok(crudService.create(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按标识获取条目映射")
    public ApiResponse<ItemMappingResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(crudService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新条目映射")
    public ApiResponse<ItemMappingResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody ItemMappingRequest request) {
        return ApiResponse.ok(crudService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除条目映射", description = "同时清理其映射层级关系。")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        crudService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/hierarchy")
    @Operation(summary = "查询映射层级", description = "返回该映射的上层（superset）与全部下层（subset）。")
    public ApiResponse<Map<String, Object>> getHierarchy(@PathVariable UUID id) {
        return ApiResponse.ok(crudService.getHierarchy(id));
    }

    @PutMapping("/{id}/hierarchy")
    @Operation(summary = "设置映射层级", description = "设置/替换该映射的上层映射；supersetMappingId 为 null 时清除上层关系。")
    public ApiResponse<Map<String, Object>> putHierarchy(
            @PathVariable UUID id,
            @RequestBody(required = false) ItemMappingHierarchyRequest request) {
        return ApiResponse.ok(crudService.putHierarchy(id, request));
    }
}
