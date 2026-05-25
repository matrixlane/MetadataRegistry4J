package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.readiness.ReadinessChecklist;
import io.github.matrixlane.metadataregistry.web.dto.readiness.ReadinessChecklistResponse;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.service.DataGovernanceReadinessService;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.UUID;

/**
 * WI-028-C Phase 3: REST API for Data Governance Readiness
 *
 * <p>Endpoint: {@code GET /api/readiness/{namespaceId}}
 *
 * <p>返回指定 Namespace 的就绪状态清单，引导用户补齐缺失的前置条件。
 */
@RestController
@RequestMapping("/api/readiness")
@RequiredArgsConstructor
@Tag(name = "数据治理就绪检查", description = "WI-028-C: Namespace 就绪状态检查")
public class ReadinessController {

    private final DataGovernanceReadinessService readinessService;
    private final NamespaceRepository namespaceRepository;

    @GetMapping("/{namespaceId}")
    @Operation(summary = "检查 Namespace 就绪状态",
            description = "返回就绪检查清单，包含缺失的前置条件和引导建议")
    public ApiResponse<ReadinessChecklistResponse> checkReadiness(
            @PathVariable UUID namespaceId,
            Authentication authentication) {

        UUID userId = authentication != null
                ? extractUserId(authentication)
                : null;

        ReadinessChecklist checklist = readinessService.checkReadiness(userId, namespaceId);

        // 构建引导建议
        var guidance = buildGuidance(checklist);

        // 获取 Namespace 名称
        String nsName = null;
        if (checklist.namespaceExists()) {
            Namespace ns = namespaceRepository.findById(namespaceId).orElse(null);
            if (ns != null) {
                nsName = ItemNamingHelper.preferredDesignation(ns);
            }
        }

        return ApiResponse.ok(ReadinessChecklistResponse.from(namespaceId, checklist, nsName, guidance));
    }

    @GetMapping("/system")
    @Operation(summary = "检查系统全局就绪状态",
            description = "检查 RegistrationAuthority 是否已初始化（系统级）")
    public ApiResponse<ReadinessChecklistResponse> checkSystemReadiness() {
        ReadinessChecklist checklist = readinessService.checkSystemReadiness();
        var guidance = new ArrayList<String>();
        if (!checklist.registrationAuthorityExists()) {
            guidance.add("请通过 /api/v1/registration-authorities 接口创建第一个注册机构");
            guidance.add("注册机构创建后，才能进行元数据项的注册流程");
        }
        return ApiResponse.ok(ReadinessChecklistResponse.from(null, checklist, "系统全局", guidance));
    }

    private UUID extractUserId(Authentication authentication) {
        // 从 JWT principal 中提取 userId
        // 当前 authentication.name 即为 userId（UUID 字符串）
        try {
            return UUID.fromString(authentication.getName());
        } catch (Exception e) {
            return null;
        }
    }

    private java.util.List<String> buildGuidance(ReadinessChecklist checklist) {
        var guidance = new ArrayList<String>();
        if (!checklist.registrationAuthorityExists()) {
            guidance.add("请先创建 RegistrationAuthority（注册机构）");
        }
        if (!checklist.namespaceExists()) {
            guidance.add("Namespace 不存在，请通过 /api/v1/namespaces 创建");
        } else if (!checklist.namespaceActive()) {
            guidance.add("Namespace 未激活，请关联 RegistrationAuthority");
        }
        if (!checklist.namingConventionValid()) {
            guidance.add("当前 Namespace 要求强制命名规范，请检查标识符格式是否符合NamingConvention要求");
        }
        if (!checklist.userHasPermission()) {
            guidance.add("当前用户无权在指定 Namespace 下创建元数据项，请联系管理员");
        }
        return guidance;
    }
}