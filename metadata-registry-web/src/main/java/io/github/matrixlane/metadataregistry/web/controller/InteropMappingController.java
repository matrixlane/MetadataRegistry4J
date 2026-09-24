package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataElement;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataElementRepository;
import io.github.matrixlane.metadataregistry.itemmapping.entity.InteropMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import io.github.matrixlane.metadataregistry.itemmapping.service.InteropMappingResultService;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.interopemapping.BatchOperationResponse;
import io.github.matrixlane.metadataregistry.web.dto.interopemapping.InteropMappingListRequest;
import io.github.matrixlane.metadataregistry.web.dto.interopemapping.InteropMappingResponse;
import io.github.matrixlane.metadataregistry.web.dto.interopemapping.ManualMapRequest;
import io.github.matrixlane.metadataregistry.web.dto.interopemapping.QualityScoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
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

import java.util.UUID;

/**
 * REST controller for interoperability mapping result management (WI-013-2b).
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Listing and filtering mapping suggestions</li>
 *   <li>Manual confirm, reject, and remap</li>
 *   <li>Undo individual and batch operations</li>
 *   <li>Batch confirm/reject with transaction rollback</li>
 *   <li>Alignment quality score</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/interop-mappings")
@RequiredArgsConstructor
@Tag(name = "互联互通匹配结果管理", description = "匹配建议查看、确认、调整、批量操作与质量评估（WI-013-2b）")
public class InteropMappingController {

    private final InteropMappingResultService interopMappingResultService;
    private final InteropDataElementRepository interopDataElementRepository;

    // ── List & Filter ───────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "分页列出匹配建议",
            description = "按数据集展示，支持按状态、置信度区间筛选")
    public ApiResponse<PageResponse<InteropMappingResponse>> list(
            @RequestParam @Parameter(description = "数据集ID") UUID datasetId,
            @RequestParam(required = false) @Parameter(description = "匹配状态") InteropMappingStatus status,
            @RequestParam(required = false) @Parameter(description = "最小置信度") java.math.BigDecimal minConfidence,
            @RequestParam(required = false) @Parameter(description = "最大置信度") java.math.BigDecimal maxConfidence,
            @ParameterObject Pageable pageable) {

        var page = interopMappingResultService.listMappings(datasetId, status, minConfidence, maxConfidence, pageable);
        return ApiResponse.ok(PageResponse.of(page, this::toResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询匹配详情")
    public ApiResponse<InteropMappingResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(interopMappingResultService.getMapping(id)));
    }

    // ── Manual Operations ───────────────────────────────────────────

    @PutMapping("/{id}/confirm")
    @Operation(summary = "确认匹配建议",
            description = "将 PENDING 状态转为 CONFIRMED")
    public ApiResponse<InteropMappingResponse> confirm(
            @PathVariable UUID id,
            @RequestParam UUID userId) {
        return ApiResponse.ok(toResponse(interopMappingResultService.confirmMapping(id, userId)));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "拒绝匹配建议",
            description = "将 PENDING 状态转为 REJECTED")
    public ApiResponse<InteropMappingResponse> reject(
            @PathVariable UUID id,
            @RequestParam UUID userId) {
        return ApiResponse.ok(toResponse(interopMappingResultService.rejectMapping(id, userId)));
    }

    @PutMapping("/{id}/manual-map")
    @Operation(summary = "手动指定本地元数据项",
            description = "将映射指向另一个本地数据项")
    public ApiResponse<InteropMappingResponse> manualMap(
            @PathVariable UUID id,
            @Valid @RequestBody ManualMapRequest request,
            @RequestParam UUID userId) {
        return ApiResponse.ok(toResponse(interopMappingResultService.manualMap(id, request.localItemId(), userId)));
    }

    @DeleteMapping("/{id}/undo")
    @Operation(summary = "撤销上次操作",
            description = "恢复到上一个状态")
    public ApiResponse<InteropMappingResponse> undo(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(interopMappingResultService.undoMapping(id)));
    }

    @PutMapping("/{id}/mark-unmatched")
    @Operation(summary = "标记为未匹配",
            description = "表示无对应本地项")
    public ApiResponse<InteropMappingResponse> markUnmatched(@PathVariable UUID id) {
        return ApiResponse.ok(toResponse(interopMappingResultService.markUnmatched(id)));
    }

    // ── Batch Operations ────────────────────────────────────────────

    @GetMapping("/batch/pending-count")
    @Operation(summary = "统计待确认条目数",
            description = "用于批量操作前的二次确认对话框")
    public ApiResponse<Long> countPending(@RequestParam UUID datasetId) {
        return ApiResponse.ok(interopMappingResultService.countPendingByDataset(datasetId));
    }

    @PostMapping("/batch-confirm")
    @Operation(summary = "批量确认",
            description = "按数据集批量接受所有 PENDING 建议，全事务回滚")
    public ApiResponse<BatchOperationResponse> batchConfirm(
            @RequestParam UUID datasetId,
            @RequestParam UUID userId) {
        long count = interopMappingResultService.countPendingByDataset(datasetId);
        UUID batchId = interopMappingResultService.batchConfirm(datasetId, userId);
        return ApiResponse.ok(new BatchOperationResponse(batchId, (int) count, "CONFIRM"));
    }

    @PostMapping("/batch-reject")
    @Operation(summary = "批量拒绝",
            description = "按数据集批量拒绝所有 PENDING 建议，全事务回滚")
    public ApiResponse<BatchOperationResponse> batchReject(
            @RequestParam UUID datasetId,
            @RequestParam UUID userId) {
        long count = interopMappingResultService.countPendingByDataset(datasetId);
        UUID batchId = interopMappingResultService.batchReject(datasetId, userId);
        return ApiResponse.ok(new BatchOperationResponse(batchId, (int) count, "REJECT"));
    }

    @PostMapping("/batch-undo")
    @Operation(summary = "撤销批量操作",
            description = "回滚整个批量操作")
    public ApiResponse<BatchOperationResponse> undoBatch(
            @RequestParam UUID batchOperationId) {
        int count = interopMappingResultService.undoBatchOperation(batchOperationId);
        return ApiResponse.ok(new BatchOperationResponse(batchOperationId, count, "UNDO"));
    }

    // ── Quality Score ───────────────────────────────────────────────

    @GetMapping("/quality-score")
    @Operation(summary = "对齐质量评分",
            description = "计算数据集级别的匹配率、待确认率、未匹配率与综合评分")
    public ApiResponse<QualityScoreResponse> qualityScore(@RequestParam UUID datasetId) {
        var result = interopMappingResultService.calculateQualityScore(datasetId);

        double matchedRate = result.totalCount() > 0
                ? (double) result.matchedCount() / result.totalCount() * 100
                : 0.0;
        double pendingRate = result.totalCount() > 0
                ? (double) result.pendingCount() / result.totalCount() * 100
                : 0.0;
        double unmatchedRate = result.totalCount() > 0
                ? (double) result.unmatchedCount() / result.totalCount() * 100
                : 0.0;

        return ApiResponse.ok(new QualityScoreResponse(
                result.datasetId(),
                result.totalCount(),
                result.matchedCount(),
                result.pendingCount(),
                result.unmatchedCount(),
                result.score(),
                matchedRate,
                pendingRate,
                unmatchedRate
        ));
    }

    // ── Empty State ─────────────────────────────────────────────────

    @GetMapping("/empty-check")
    @Operation(summary = "空状态检测",
            description = "检测数据集是否为空或本地元数据是否不足")
    public ApiResponse<EmptyStateResponse> emptyCheck(@RequestParam UUID datasetId) {
        boolean isEmpty = interopMappingResultService.isDatasetEmpty(datasetId);
        boolean hasEmptyLocal = interopMappingResultService.hasEmptyLocalMetadata(datasetId);

        String message = null;
        if (isEmpty) {
            message = "暂无匹配任务，建议先完成数据元导入（WI-013-1）后再执行对齐。";
        } else if (hasEmptyLocal) {
            message = "检测到本地元数据较少，建议先完善本地元数据再执行对齐。";
        }

        return ApiResponse.ok(new EmptyStateResponse(isEmpty, hasEmptyLocal, message));
    }

    public record EmptyStateResponse(boolean isEmpty, boolean hasEmptyLocalMetadata, String message) {
    }

    // ── Mapping ─────────────────────────────────────────────────────

    private InteropMappingResponse toResponse(InteropMapping mapping) {
        // 界面禁显 UUID：数据集元素侧以「标识符 | 名称」呈现；本地条目名由前端经
        // metadata search 反查（localItemId 是受管条目 UUID，不在此处展示）。
        String dataElementDisplay = interopDataElementRepository.findById(mapping.getInteropDataElementId())
                .map(el -> {
                    String name = el.getDeName();
                    if (name == null || name.isBlank()) {
                        return el.getDeIdentifier();
                    }
                    return el.getDeIdentifier() + " | " + name;
                })
                .orElse(null);
        return new InteropMappingResponse(
                mapping.getId(),
                mapping.getInteropDataElementId(),
                dataElementDisplay,
                mapping.getLocalItemId(),
                null, // localItemName — 前端经 metadata search 反查业务标识/指称
                mapping.getConfidenceScore(),
                mapping.getStrategy(),
                mapping.getStatus(),
                mapping.getReason(),
                mapping.getConfirmedBy(),
                mapping.getConfirmedAt(),
                mapping.getBatchOperationId(),
                mapping.getVersion(),
                mapping.getCreatedAt()
        );
    }
}
