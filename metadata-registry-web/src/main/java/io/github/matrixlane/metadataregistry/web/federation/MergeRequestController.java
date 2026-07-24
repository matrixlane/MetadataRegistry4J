package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 合并请求管理与评审端点（目标端）。
 */
@RestController
@RequestMapping("/api/v1/federation/merge-requests")
@RequiredArgsConstructor
@Tag(name = "联邦协作 - 合并请求", description = "合并请求生命周期与评审接口")
public class MergeRequestController {

    private final MergeRequestService mergeRequestService;
    private final MergeExecutor mergeExecutor;

    @GetMapping
    @Operation(summary = "合并请求列表")
    public ApiResponse<List<MergeRequestView>> list(@RequestParam(required = false) MergeRequestStatus status) {
        return ApiResponse.ok(mergeRequestService.list(status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "合并请求详情")
    public ApiResponse<MergeRequestView> get(@PathVariable UUID id) {
        return ApiResponse.ok(mergeRequestService.getView(id));
    }

    @GetMapping("/{id}/items")
    @Operation(summary = "合并请求条目（评审视图）")
    public ApiResponse<List<MergeRequestItemView>> items(@PathVariable UUID id) {
        return ApiResponse.ok(mergeRequestService.getItems(id));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交合并请求（DRAFT → SUBMITTED）")
    public ApiResponse<MergeRequestView> submit(@PathVariable UUID id,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(mergeRequestService.submit(id, user.username()));
    }

    @PostMapping("/{id}/start-review")
    @Operation(summary = "开始评审（SUBMITTED → UNDER_REVIEW）")
    public ApiResponse<MergeRequestView> startReview(@PathVariable UUID id) {
        return ApiResponse.ok(mergeRequestService.startReview(id));
    }

    @PostMapping("/{id}/items/{itemId}/decision")
    @Operation(summary = "条目裁决（接受/拒绝/链接既有）")
    public ApiResponse<MergeRequestItemView> decide(@PathVariable UUID id,
                                                    @PathVariable UUID itemId,
                                                    @RequestParam MergeItemResolution resolution,
                                                    @RequestParam(required = false) UUID linkedLocalItemId) {
        return ApiResponse.ok(mergeRequestService.decide(id, itemId, resolution, linkedLocalItemId));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "批准并入（事务性合并 + 生成回执）")
    public ApiResponse<Map<String, Object>> approve(@PathVariable UUID id,
                                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(mergeExecutor.execute(id, user.username()));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "拒绝合并请求")
    public ApiResponse<MergeRequestView> reject(@PathVariable UUID id,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(mergeRequestService.reject(id, user.username()));
    }

    @PostMapping("/import")
    @Operation(summary = "导入合并包文件（文件通道）")
    public ApiResponse<MergeRequestView> importBundle(@RequestParam String bundlePath,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(mergeRequestService.importBundle(Paths.get(bundlePath), user.username()));
    }

    @PostMapping("/receipts/apply")
    @Operation(summary = "应用合并回执（源端更新水位线与反向链接）")
    public ApiResponse<Map<String, Object>> applyReceipt(@RequestParam UUID nodeId,
                                                         @RequestBody String receiptJson) {
        return ApiResponse.ok(mergeRequestService.applyReceipt(nodeId, receiptJson));
    }
}
