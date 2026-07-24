package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import io.github.matrixlane.metadataregistry.web.service.migration.MigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据库迁移接口。
 *
 * <p>导出在源端执行（源端已初始化，要求超级管理员）；导入在目标端执行
 * （目标端通常尚未初始化，允许匿名，同初始化向导的放行模式）。
 */
@RestController
@RequestMapping("/api/v1/migration")
@RequiredArgsConstructor
@Tag(name = "0. 系统管理 - 数据迁移", description = "跨数据库数据迁移接口")
public class MigrationController {

    private final MigrationService migrationService;
    private final SystemSettingService systemSettingService;

    @PostMapping("/export")
    @Operation(summary = "导出全部数据到迁移文件（开启只读维护状态）")
    public ApiResponse<Map<String, Object>> export() {
        guardWhenInitialized();
        return ApiResponse.ok(migrationService.exportData());
    }

    @PostMapping("/import")
    @Operation(summary = "从迁移文件导入（目标库须为空或 force 覆盖）")
    public ApiResponse<Map<String, Object>> importData(@RequestParam(defaultValue = "false") boolean force) {
        guardWhenInitialized();
        return ApiResponse.ok(migrationService.importData(force));
    }

    @PostMapping("/abort")
    @Operation(summary = "中止迁移：解除只读维护状态（原库数据不变）")
    public ApiResponse<Void> abort() {
        guardWhenInitialized();
        migrationService.abort();
        return ApiResponse.ok();
    }

    @GetMapping("/status")
    @Operation(summary = "迁移状态查询")
    public ApiResponse<Map<String, Object>> status() {
        guardWhenInitialized();
        return ApiResponse.ok(migrationService.status());
    }

    private void guardWhenInitialized() {
        if (!systemSettingService.isInitialized()) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (!isSuperAdmin) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "无权访问此资源");
        }
    }
}
