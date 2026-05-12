package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.service.IdentifierResolutionService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/identifiers")
@RequiredArgsConstructor
@Tag(name = "4. 标识管理 - 标识符解析", description = "标识符解析与反向查找")
public class IdentifierResolutionController {

    private final IdentifierResolutionService resolutionService;

    @GetMapping("/resolve")
    @Operation(summary = "解析标识符（全局）")
    public ApiResponse<ScopedIdentifierResponse> resolveGlobal(
            @RequestParam String identifier,
            @RequestParam(required = false) String version) {
        return resolutionService.resolveGlobally(identifier, version)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.ok(null));
    }

    @GetMapping("/namespaces/{namespaceId}/resolve")
    @Operation(summary = "解析标识符（指定命名空间）")
    public ApiResponse<ScopedIdentifierResponse> resolveInNamespace(
            @PathVariable UUID namespaceId,
            @RequestParam String identifier,
            @RequestParam(required = false) String version) {
        return resolutionService.resolve(namespaceId, identifier, version)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.ok(null));
    }

    @GetMapping("/identified-items/{identifiedItemId}")
    @Operation(summary = "反向查找：根据 IdentifiedItem 查询所有标识符")
    public ApiResponse<List<ScopedIdentifierResponse>> findByIdentifiedItem(
            @PathVariable UUID identifiedItemId) {
        return ApiResponse.ok(resolutionService.findByIdentifiedItem(identifiedItemId));
    }
}
