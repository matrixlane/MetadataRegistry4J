package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscription;
import io.github.matrixlane.metadataregistry.itemmapping.notification.MappingSubscriptionService;
import io.github.matrixlane.metadataregistry.itemmapping.notification.SubscriptionType;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.SubscriptionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for mapping subscription management (WI-011-3).
 *
 * <p>V1: In-app notification subscriptions only.
 */
@RestController
@RequestMapping("/api/v1/mappings/subscriptions")
@RequiredArgsConstructor
@Tag(name = "11. 映射订阅管理", description = "ISO/IEC 11179 Clause 11 — 映射变更订阅与通知（WI-011-3）")
public class MappingSubscriptionController {

    private final MappingSubscriptionService subscriptionService;

    @PostMapping
    @Operation(summary = "创建映射订阅",
            description = "按映射方案或元数据项订阅变更通知。")
    public ApiResponse<MappingSubscription> subscribe(
            @Valid @RequestBody SubscriptionRequest request) {

        MappingSubscription sub = switch (request.subscriptionType()) {
            case SCHEME -> subscriptionService.subscribeToScheme(request.userId(), request.targetId());
            case ITEM -> subscriptionService.subscribeToItem(request.userId(), request.targetId());
        };
        return ApiResponse.ok(sub);
    }

    @DeleteMapping
    @Operation(summary = "取消映射订阅")
    public ApiResponse<Void> unsubscribe(
            @RequestParam String userId,
            @RequestParam SubscriptionType subscriptionType,
            @RequestParam UUID targetId) {

        subscriptionService.unsubscribe(userId, subscriptionType, targetId);
        return ApiResponse.ok();
    }

    @GetMapping
    @Operation(summary = "查询用户订阅列表")
    public ApiResponse<List<MappingSubscription>> getUserSubscriptions(
            @RequestParam String userId) {

        return ApiResponse.ok(subscriptionService.getUserSubscriptions(userId));
    }
}
