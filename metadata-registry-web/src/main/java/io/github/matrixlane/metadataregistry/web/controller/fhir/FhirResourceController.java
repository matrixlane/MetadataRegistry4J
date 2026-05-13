package io.github.matrixlane.metadataregistry.web.controller.fhir;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirCodeSystemResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirElementResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirImportResult;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirResourceResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.FhirValueSetResponse;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirImportService;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fhir")
@RequiredArgsConstructor
@Tag(name = "10. FHIR 资源管理", description = "FHIR Resource/Element 导入与浏览 API")
public class FhirResourceController {

    private final FhirImportService importService;
    private final FhirResourceService resourceService;

    @PostMapping("/import/directory")
    @Operation(summary = "从 FHIR 官方包目录导入")
    public ApiResponse<FhirImportResult> importFromDirectory(@RequestParam String packageDir) {
        return ApiResponse.ok(importService.importFromDirectory(packageDir));
    }

    @PostMapping("/import/file")
    @Operation(summary = "从单个 JSON 文件导入")
    public ApiResponse<FhirImportResult> importSingleFile(@RequestParam String filePath) {
        return ApiResponse.ok(importService.importSingleFile(filePath));
    }

    @GetMapping("/resources")
    @Operation(summary = "分页列出已导入的 FHIR Resource")
    public ApiResponse<PageResponse<FhirResourceResponse>> listResources(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String name,
            Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(resourceService.listResources(kind, name, pageable)));
    }

    @GetMapping("/resources/{id}")
    @Operation(summary = "查询 FHIR Resource 基本信息")
    public ApiResponse<FhirResourceResponse> getResource(@PathVariable UUID id) {
        return ApiResponse.ok(resourceService.getResource(id));
    }

    @GetMapping("/resources/{id}/tree")
    @Operation(summary = "查询 FHIR Resource 完整 Element 树")
    public ApiResponse<FhirResourceResponse> getResourceTree(@PathVariable UUID id) {
        return ApiResponse.ok(resourceService.getResourceWithTree(id));
    }

    @GetMapping("/resources/{id}/elements/roots")
    @Operation(summary = "查询 Resource 根级 Element")
    public ApiResponse<List<FhirElementResponse>> getRootElements(@PathVariable UUID id) {
        return ApiResponse.ok(resourceService.getRootElements(id));
    }

    @GetMapping("/elements/{elementId}/children")
    @Operation(summary = "查询 Element 的子节点")
    public ApiResponse<List<FhirElementResponse>> getElementChildren(@PathVariable UUID elementId) {
        return ApiResponse.ok(resourceService.getElementChildren(elementId));
    }

    @GetMapping("/value-sets")
    @Operation(summary = "分页列出已导入的 ValueSet")
    public ApiResponse<PageResponse<FhirValueSetResponse>> listValueSets(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(resourceService.listValueSets(pageable)));
    }

    @GetMapping("/code-systems")
    @Operation(summary = "分页列出已导入的 CodeSystem")
    public ApiResponse<PageResponse<FhirCodeSystemResponse>> listCodeSystems(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(resourceService.listCodeSystems(pageable)));
    }

    @GetMapping("/kinds")
    @Operation(summary = "列出所有可用的 Resource kind 值")
    public ApiResponse<List<String>> listResourceKinds() {
        return ApiResponse.ok(List.of("resource", "complex-type", "primitive-type", "logical"));
    }
}
