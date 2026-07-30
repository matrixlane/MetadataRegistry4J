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
 * REST controller for the Data Dictionary Browser.
 * <p>
 * Provides a browsable endpoint at {@code /api/v1/data-dictionary/browser}
 * that delegates to the unified metadata search service. This endpoint is
 * used by the smoke test and the frontend Data Dictionary Browser view
 * (WI-015a).
 */
@RestController
@RequestMapping("/api/v1/data-dictionary")
@RequiredArgsConstructor
@Tag(name = "0. 数据字典浏览器", description = "数据字典浏览端点（委托至 metadata/search）")
public class DataDictionaryBrowserController {

    private final MetadataSearchService searchService;

    @GetMapping("/browser")
    @Operation(summary = "数据字典浏览",
            description = "统一浏览所有注册的元数据项。参数与行为同 /api/v1/metadata/search。")
    public ApiResponse<PageResponse<AdministeredItemResponse>> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(required = false) UUID contextId,
            @RequestParam(required = false) List<Acceptability> acceptability,
            @ParameterObject
            @PageableDefault(size = 20, sort = "creationDateTime", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(
                PageResponse.of(searchService.search(q, type, status, contextId, acceptability, pageable)));
    }
}