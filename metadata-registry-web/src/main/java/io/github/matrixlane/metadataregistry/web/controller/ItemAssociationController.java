package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.association.AssociationMutationRequest;
import io.github.matrixlane.metadataregistry.web.dto.association.ItemAssociationsResponse;
import io.github.matrixlane.metadataregistry.web.dto.association.StandardAssociationType;
import io.github.matrixlane.metadataregistry.web.service.ItemAssociationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 四实体（数据元 / 数据元概念 / 概念域 / 值域）标准关联的查询与维护端点。
 * 关联集合与方向以 ISO/IEC 11179-31:2023 为准（见 {@link StandardAssociationType}）。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "8. 实体关联", description = "数据元 / 数据元概念 / 概念域 / 值域之间的标准双向关联")
public class ItemAssociationController {

    private final ItemAssociationService service;

    @GetMapping("/items/{itemId}/associations")
    @Operation(summary = "列出焦点实体的全部标准关联（正向 + 反向）")
    public ApiResponse<ItemAssociationsResponse> list(@PathVariable UUID itemId) {
        return ApiResponse.ok(service.listAssociations(itemId));
    }

    @PostMapping("/associations/{relationType}")
    @Operation(summary = "建立关联（source/target 角色由关联类型固定）")
    public ApiResponse<ItemAssociationsResponse> associate(@PathVariable String relationType,
                                                           @Valid @RequestBody AssociationMutationRequest request) {
        return ApiResponse.ok(service.associate(StandardAssociationType.fromPath(relationType), request));
    }

    @DeleteMapping("/associations/{relationType}")
    @Operation(summary = "解除关联（source/target 角色由关联类型固定）")
    public ApiResponse<ItemAssociationsResponse> dissociate(@PathVariable String relationType,
                                                            @Valid @RequestBody AssociationMutationRequest request) {
        return ApiResponse.ok(service.dissociate(StandardAssociationType.fromPath(relationType), request));
    }
}
