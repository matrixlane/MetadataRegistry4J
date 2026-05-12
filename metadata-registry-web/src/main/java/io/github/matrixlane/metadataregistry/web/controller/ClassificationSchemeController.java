package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationImportResult;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationNodeRequest;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationNodeResponse;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationSchemeRequest;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationSchemeResponse;
import io.github.matrixlane.metadataregistry.web.dto.classification.ClassificationTreeNode;
import io.github.matrixlane.metadataregistry.web.dto.classification.MoveNodeRequest;
import io.github.matrixlane.metadataregistry.web.service.ClassificationImportExportService;
import io.github.matrixlane.metadataregistry.web.service.ClassificationSchemeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/classification-schemes")
@RequiredArgsConstructor
@Tag(name = "10. 分类方案", description = "Classification Scheme & Node 的 CRUD 与树形管理（ISO 11179-3 Clause 10）")
public class ClassificationSchemeController {

    private final ClassificationSchemeService service;
    private final ClassificationImportExportService importExportService;

    // ==================== Scheme Endpoints ====================

    @GetMapping
    @Operation(summary = "分页列出分类方案")
    public ApiResponse<PageResponse<ClassificationSchemeResponse>> listSchemes(
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(service.listSchemes(q, pageable)));
    }

    @GetMapping("/{schemeId}")
    @Operation(summary = "查询分类方案详情")
    public ApiResponse<ClassificationSchemeResponse> getScheme(@PathVariable UUID schemeId) {
        return ApiResponse.ok(service.getScheme(schemeId));
    }

    @PostMapping
    @Operation(summary = "新增分类方案")
    public ApiResponse<ClassificationSchemeResponse> createScheme(
            @Valid @RequestBody ClassificationSchemeRequest request) {
        return ApiResponse.ok(service.createScheme(request));
    }

    @PutMapping("/{schemeId}")
    @Operation(summary = "更新分类方案")
    public ApiResponse<ClassificationSchemeResponse> updateScheme(
            @PathVariable UUID schemeId,
            @Valid @RequestBody ClassificationSchemeRequest request) {
        return ApiResponse.ok(service.updateScheme(schemeId, request));
    }

    @DeleteMapping("/{schemeId}")
    @Operation(summary = "删除分类方案", description = "仅当方案下无节点时可删除")
    public ApiResponse<Void> deleteScheme(@PathVariable UUID schemeId) {
        service.deleteScheme(schemeId);
        return ApiResponse.ok();
    }

    // ==================== Node Endpoints ====================

    @GetMapping("/{schemeId}/nodes/children")
    @Operation(summary = "懒加载子节点", description = "根据 parentId 获取子节点；parentId 为空表示根节点")
    public ApiResponse<List<ClassificationTreeNode>> getChildren(
            @PathVariable UUID schemeId,
            @RequestParam(required = false) UUID parentId) {
        return ApiResponse.ok(service.getChildren(schemeId, parentId));
    }

    @GetMapping("/{schemeId}/nodes/search")
    @Operation(summary = "搜索节点", description = "按名称或编码模糊匹配")
    public ApiResponse<List<ClassificationNodeResponse>> searchNodes(
            @PathVariable UUID schemeId,
            @RequestParam String keyword) {
        return ApiResponse.ok(service.searchNodes(schemeId, keyword));
    }

    @GetMapping("/{schemeId}/nodes")
    @Operation(summary = "列出分类方案下所有节点（扁平列表）")
    public ApiResponse<List<ClassificationNodeResponse>> listNodes(@PathVariable UUID schemeId) {
        return ApiResponse.ok(service.listNodesByScheme(schemeId));
    }

    @GetMapping("/{schemeId}/nodes/tree")
    @Operation(summary = "获取分类方案节点树")
    public ApiResponse<List<ClassificationTreeNode>> getTree(@PathVariable UUID schemeId) {
        return ApiResponse.ok(service.getTree(schemeId));
    }

    @GetMapping("/{schemeId}/nodes/{nodeId}/tree")
    @Operation(summary = "获取指定节点为根的子树")
    public ApiResponse<List<ClassificationTreeNode>> getSubTree(
            @PathVariable UUID schemeId,
            @PathVariable UUID nodeId) {
        return ApiResponse.ok(service.getTreeByRoot(schemeId, nodeId));
    }

    @GetMapping("/{schemeId}/nodes/{nodeId}")
    @Operation(summary = "查询节点详情")
    public ApiResponse<ClassificationNodeResponse> getNode(
            @PathVariable UUID schemeId,
            @PathVariable UUID nodeId) {
        return ApiResponse.ok(service.getNode(nodeId));
    }

    @PostMapping("/{schemeId}/nodes")
    @Operation(summary = "新增分类节点")
    public ApiResponse<ClassificationNodeResponse> createNode(
            @PathVariable UUID schemeId,
            @Valid @RequestBody ClassificationNodeRequest request) {
        request.setSchemeId(schemeId);
        return ApiResponse.ok(service.createNode(request));
    }

    @PutMapping("/{schemeId}/nodes/{nodeId}")
    @Operation(summary = "更新分类节点")
    public ApiResponse<ClassificationNodeResponse> updateNode(
            @PathVariable UUID schemeId,
            @PathVariable UUID nodeId,
            @Valid @RequestBody ClassificationNodeRequest request) {
        return ApiResponse.ok(service.updateNode(nodeId, request));
    }

    @PutMapping("/{schemeId}/nodes/{nodeId}/move")
    @Operation(summary = "移动分类节点", description = "包含循环引用检测")
    public ApiResponse<ClassificationNodeResponse> moveNode(
            @PathVariable UUID schemeId,
            @PathVariable UUID nodeId,
            @Valid @RequestBody MoveNodeRequest request) {
        return ApiResponse.ok(service.moveNode(nodeId, request));
    }

    @DeleteMapping("/{schemeId}/nodes/{nodeId}")
    @Operation(summary = "删除分类节点", description = "仅当节点无子节点时可删除")
    public ApiResponse<Void> deleteNode(
            @PathVariable UUID schemeId,
            @PathVariable UUID nodeId) {
        service.deleteNode(nodeId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{schemeId}/nodes/{nodeId}/cascade")
    @Operation(summary = "级联删除分类节点", description = "删除节点及其所有后代")
    public ApiResponse<Void> deleteNodeCascade(
            @PathVariable UUID schemeId,
            @PathVariable UUID nodeId) {
        service.deleteNodeCascade(nodeId);
        return ApiResponse.ok();
    }

    // ==================== Import/Export Endpoints ====================

    @PostMapping(value = "/import/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "预校验导入文件", description = "两阶段提交之阶段一：解析并校验文件，不写入数据库")
    public ApiResponse<ClassificationImportResult> validateImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "schemeId", required = false) UUID targetSchemeId) {
        return ApiResponse.ok(importExportService.prevalidateImport(file, targetSchemeId));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "导入分类方案和节点", description = "两阶段提交之阶段二：校验通过后写入数据库")
    public ApiResponse<ClassificationImportResult> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "schemeId", required = false) UUID targetSchemeId) {
        return ApiResponse.ok(importExportService.importFromFile(file, targetSchemeId));
    }

    @GetMapping("/{schemeId}/export")
    @Operation(summary = "导出分类方案", description = "支持 Excel (.xlsx) 和 CSV 格式")
    public ResponseEntity<byte[]> exportScheme(
            @PathVariable UUID schemeId,
            @RequestParam(name = "format", defaultValue = "excel") String format) {
        byte[] data;
        String filename;
        MediaType contentType;

        if ("csv".equalsIgnoreCase(format)) {
            data = importExportService.exportToCsv(schemeId);
            filename = "classification-scheme-" + schemeId + ".csv";
            contentType = MediaType.parseMediaType("text/csv; charset=UTF-8");
        } else {
            data = importExportService.exportToExcel(schemeId);
            filename = "classification-scheme-" + schemeId + ".xlsx";
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(data);
    }
}
