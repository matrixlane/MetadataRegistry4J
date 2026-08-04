package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.BackfillResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.NamespaceStrategyView;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.PresetResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.PreviewRequest;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.PreviewResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.SaveTemplateRequest;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.StrategyConfigResponse;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.ValidateFormatRequest;
import io.github.matrixlane.metadataregistry.web.dto.identifierstrategy.IdentifierStrategyDtos.ValidateFormatResponse;
import io.github.matrixlane.metadataregistry.web.service.identifier.IdentifierStrategyAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Segment-template identifier strategy administration: query, draft, publish,
 * disable, preview, format validation, presets and sequence backfill.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "4. 标识管理 - 段模板策略", description = "命名空间段模板标识策略的配置与版本化")
public class IdentifierStrategyAdminController {

    private final IdentifierStrategyAdminService service;

    @GetMapping("/namespaces/{namespaceId}/identifier-strategy")
    @Operation(summary = "查询命名空间的标识策略（已发布 + 草稿 + 历史）")
    public ApiResponse<NamespaceStrategyView> view(@PathVariable UUID namespaceId) {
        return ApiResponse.ok(service.view(namespaceId));
    }

    @PostMapping("/namespaces/{namespaceId}/identifier-strategy/draft")
    @Operation(summary = "保存段模板草稿")
    public ApiResponse<StrategyConfigResponse> saveDraft(@PathVariable UUID namespaceId,
                                                         @Valid @RequestBody SaveTemplateRequest request) {
        return ApiResponse.ok(service.saveDraft(namespaceId, request.configJson()));
    }

    @PostMapping("/namespaces/{namespaceId}/identifier-strategy/publish")
    @Operation(summary = "校验并发布段模板（新版本生效，历史标识不回改）")
    public ApiResponse<StrategyConfigResponse> publish(@PathVariable UUID namespaceId,
                                                       @Valid @RequestBody SaveTemplateRequest request) {
        return ApiResponse.ok(service.publish(namespaceId, request.configJson()));
    }

    @PostMapping("/namespaces/{namespaceId}/identifier-strategy/drafts/{configId}/publish")
    @Operation(summary = "发布既有草稿")
    public ApiResponse<StrategyConfigResponse> publishDraft(@PathVariable UUID namespaceId,
                                                            @PathVariable Long configId) {
        return ApiResponse.ok(service.publishDraft(namespaceId, configId));
    }

    @PostMapping("/namespaces/{namespaceId}/identifier-strategy/disable")
    @Operation(summary = "停用当前已发布策略")
    public ApiResponse<Void> disable(@PathVariable UUID namespaceId) {
        service.disable(namespaceId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/namespaces/{namespaceId}/identifier-strategy/preview")
    @Operation(summary = "预览试生成（不产生序号分配）")
    public ApiResponse<PreviewResponse> preview(@PathVariable UUID namespaceId,
                                                @Valid @RequestBody PreviewRequest request) {
        return ApiResponse.ok(service.preview(request.configJson(), request.itemType(), request.variables()));
    }

    @PostMapping("/namespaces/{namespaceId}/identifier-strategy/validate")
    @Operation(summary = "按模板派生正则校验标识格式并反解析")
    public ApiResponse<ValidateFormatResponse> validateFormat(@PathVariable UUID namespaceId,
                                                              @Valid @RequestBody ValidateFormatRequest request) {
        return ApiResponse.ok(service.validateFormat(request.configJson(), request.identifier()));
    }

    @PostMapping("/namespaces/{namespaceId}/identifier-strategy/backfill")
    @Operation(summary = "序号回填：按既有标识对齐各作用域计数器")
    public ApiResponse<BackfillResponse> backfill(@PathVariable UUID namespaceId) {
        return ApiResponse.ok(service.backfill(namespaceId));
    }

    @GetMapping("/identifier-strategy/presets")
    @Operation(summary = "段模板预设库（WS/T 363 等）")
    public ApiResponse<List<PresetResponse>> presets() {
        return ApiResponse.ok(service.presets());
    }
}
