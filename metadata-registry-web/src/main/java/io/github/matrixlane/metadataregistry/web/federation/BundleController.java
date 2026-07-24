package io.github.matrixlane.metadataregistry.web.federation;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 合并包构建端点（源端）。
 */
@RestController
@RequestMapping("/api/v1/federation/bundles")
@RequiredArgsConstructor
@Tag(name = "联邦协作 - 合并包", description = "合并包构建接口")
public class BundleController {

    private final FederationBundleService bundleService;

    @PostMapping
    @Operation(summary = "构建合并包（含增量计算，登记 DRAFT 合并请求）")
    public ApiResponse<MergeRequestView> create(@RequestParam UUID nodeId,
                                                @RequestParam UUID targetNamespaceId) {
        return ApiResponse.ok(bundleService.createBundle(nodeId, targetNamespaceId));
    }
}
