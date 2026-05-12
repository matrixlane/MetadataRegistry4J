package io.github.matrixlane.metadataregistry.web.controller;

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

/**
 * REST controller for unified metadata search and browsing.
 * <p>
 * Provides a single endpoint to search across all metadata entity types,
 * supporting keyword search, entity type filtering, and registration
 * status filtering with pagination.
 */
@RestController
@RequestMapping("/api/v1/metadata")
@RequiredArgsConstructor
@Tag(name = "0. 数据字典浏览器", description = "统一元数据浏览与搜索（WI-015a）")
public class MetadataSearchController {

    private final MetadataSearchService searchService;

    @GetMapping("/search")
    @Operation(summary = "统一元数据搜索",
            description = "跨所有实体类型搜索，支持关键词（名称/标识符/定义）、实体类型、注册状态过滤。分页返回 AdministeredItemResponse。")
    public ApiResponse<PageResponse<AdministeredItemResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) RegistrationStatus status,
            @ParameterObject
            @PageableDefault(size = 20, sort = "creationDateTime", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(
                PageResponse.of(searchService.search(q, type, status, pageable)));
    }
}
