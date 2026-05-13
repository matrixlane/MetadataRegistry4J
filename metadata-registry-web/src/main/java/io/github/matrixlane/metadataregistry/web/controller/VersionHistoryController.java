package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionHistoryEntryResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionSnapshotResponse;
import io.github.matrixlane.metadataregistry.web.service.VersionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/items/{id}/versions")
@RequiredArgsConstructor
@Tag(name = "版本历史", description = "元数据项版本历史查看与差异对比")
public class VersionHistoryController {

    private final VersionHistoryService versionHistoryService;

    @GetMapping
    @Operation(summary = "列出版本历史")
    public ApiResponse<List<VersionHistoryEntryResponse>> listVersions(
            @Parameter(description = "元数据项 ID") @PathVariable UUID id) {
        return ApiResponse.ok(versionHistoryService.listVersions(id));
    }

    @GetMapping("/{versionId}/diff")
    @Operation(summary = "两个版本间的字段级差异对比")
    public ApiResponse<VersionDiffResponse> diff(
            @Parameter(description = "元数据项 ID") @PathVariable UUID id,
            @Parameter(description = "基准版本号") @PathVariable Integer versionId,
            @Parameter(description = "对比版本号") @RequestParam Integer compareVersionId) {
        return ApiResponse.ok(versionHistoryService.diff(id, versionId, compareVersionId));
    }

    @GetMapping("/{versionId}/snapshot")
    @Operation(summary = "获取某一版本的完整快照")
    public ApiResponse<VersionSnapshotResponse> getSnapshot(
            @Parameter(description = "元数据项 ID") @PathVariable UUID id,
            @Parameter(description = "版本号") @PathVariable Integer versionId) {
        return ApiResponse.ok(versionHistoryService.getSnapshot(id, versionId));
    }
}
