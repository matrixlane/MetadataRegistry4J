package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.ItemRelationshipResponse;
import io.github.matrixlane.metadataregistry.web.service.ItemRelationshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/items/{itemId}/designation-definition-relationships")
@RequiredArgsConstructor
@Tag(name = "注册项关系", description = "聚合指称、定义、上下文与配对关系")
public class ItemRelationshipController {

    private final ItemRelationshipService itemRelationshipService;

    @GetMapping
    @Operation(summary = "查询注册项的指称-定义-上下文-配对关系")
    public ApiResponse<ItemRelationshipResponse> get(@PathVariable UUID itemId) {
        return ApiResponse.ok(itemRelationshipService.getItemRelationships(itemId));
    }
}
