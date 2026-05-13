package io.github.matrixlane.metadataregistry.web.controller.fhir;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.ComplianceCheckRequest;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.ComplianceReportResponse;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.ProfileSuggestionRequest;
import io.github.matrixlane.metadataregistry.web.dto.fhir.compliance.ProfileSuggestionResponse;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirComplianceService;
import io.github.matrixlane.metadataregistry.web.service.fhir.FhirProfileSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for FHIR compliance check and Profile suggestion generation (WI-012-3).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/fhir/compliance/check} — run gap analysis</li>
 *   <li>{@code GET /api/v1/fhir/compliance/report/{id}} — retrieve report</li>
 *   <li>{@code POST /api/v1/fhir/profile/generate} — generate draft Profile</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/fhir")
@RequiredArgsConstructor
@Tag(name = "14. FHIR 合规检查与 Profile 建议", description = "WI-012-3 — 差距分析与 Profile 草稿生成")
public class FhirComplianceController {

    private final FhirComplianceService complianceService;
    private final FhirProfileSuggestionService profileSuggestionService;

    @PostMapping("/compliance/check")
    @Operation(summary = "执行 FHIR 合规差距分析",
            description = "对比本地已映射数据元与目标 FHIR Resource 的 Element 清单，生成差距报告。")
    public ApiResponse<ComplianceReportResponse> checkCompliance(
            @Valid @RequestBody ComplianceCheckRequest request) {
        ComplianceReportResponse report = complianceService.checkCompliance(
                request.getResourceId(), request.getSchemeId());
        return ApiResponse.ok(report);
    }

    @GetMapping("/compliance/report/{id}")
    @Operation(summary = "获取合规差距分析报告",
            description = "通过报告 ID 查询已生成的差距分析报告。")
    public ApiResponse<ComplianceReportResponse> getReport(
            @Parameter(description = "报告 ID", required = true)
            @PathVariable UUID id) {
        return ApiResponse.ok(complianceService.getReport(id));
    }

    @PostMapping("/profile/generate")
    @Operation(summary = "生成 FHIR Profile 建议草稿",
            description = "基于差距分析结果生成 StructureDefinition 草稿（differential 约束）。明确标注为草稿，需人工审核。")
    public ApiResponse<ProfileSuggestionResponse> generateProfile(
            @Valid @RequestBody ProfileSuggestionRequest request) {
        ProfileSuggestionResponse response = profileSuggestionService.generateProfile(request);
        return ApiResponse.ok(response);
    }
}
