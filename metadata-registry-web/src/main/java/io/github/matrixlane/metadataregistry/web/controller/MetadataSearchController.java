package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.administereditem.AdministeredItemResponse;
import io.github.matrixlane.metadataregistry.web.service.MetadataSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for unified metadata search and browsing.
 *
 * <p>Provides a single endpoint to search across all metadata entity types,
 * supporting keyword search, identifier exact match (smart-item-picker),
 * entity type filtering, namespace scoping, and registration status filtering
 * with pagination.
 */
@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
@Tag(name = "0. 数据字典浏览器", description = "统一元数据浏览与搜索（WI-015a，smart-item-picker 增强）")
public class MetadataSearchController {

    /** 类型白名单：与 MetadataSearchService.typeToTable 对齐，未列入的 type 会让 typeFilter 子句返回 1=0。 */
    private static final List<String> SUPPORTED_TYPES = List.of(
            "DataElement", "DataElementConcept", "ConceptualDomain", "ValueDomain",
            "ObjectClass", "Property", "DataType", "DataTypeScheme",
            "UnitOfMeasure", "MeasureClass", "Dimensionality",
            "Namespace", "ConstraintSet", "ReferenceDocument",
            "ClassificationScheme", "Context",
            "Organization", "Individual", "Contact", "DocumentType"
    );

    private final MetadataSearchService searchService;

    @GetMapping("/search")
    @Operation(summary = "统一元数据搜索",
            description = "跨所有实体类型搜索，支持关键词（名称/标识符/定义）、实体类型（多选用英文逗号分隔）、注册状态、上下文、可接受性、命名空间限定、精确 identifier 匹配（NS.ID 或当前 namespace）。分页返回 AdministeredItemResponse。")
    public ApiResponse<PageResponse<AdministeredItemResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(required = false) UUID contextId,
            @RequestParam(required = false) List<Acceptability> acceptability,
            @RequestParam(required = false) UUID namespaceId,
            @RequestParam(required = false, defaultValue = "false") boolean exactMatch,
            @RequestParam(required = false) String identifier,
            @RequestParam(required = false) String namespaceShorthand,
            @ParameterObject
            @PageableDefault(size = 20, sort = "creationDateTime", direction = Sort.Direction.DESC)
            Pageable pageable) {
        String sanitizedType = sanitizeType(type);
        return ApiResponse.ok(
                PageResponse.of(searchService.search(q, sanitizedType, status, contextId, acceptability,
                        namespaceId, exactMatch, identifier, namespaceShorthand, pageable)));
    }

    /**
     * 白名单过滤：仅保留 SUPPORTED_TYPES 内的英文枚举名。
     * 调用方传入英文逗号分隔多值时按单个拆分过滤后拼回。
     */
    private String sanitizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String[] parts = type.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String t = parts[i].trim();
            if (SUPPORTED_TYPES.contains(t)) {
                if (sb.length() > 0) sb.append(',');
                sb.append(t);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}