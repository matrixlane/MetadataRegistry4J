package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.assistant.dto.GenerationRecordRequest;
import io.github.matrixlane.metadataregistry.web.assistant.dto.GenerationRecordResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 生成来源记录端点。POST 沿用平台默认（REGISTRAR/SUPER_ADMIN）；GET 已在
 * SecurityConfig 显式限定 REGISTRAR/SUPER_ADMIN（AI 生成相关能力不对其它角色开放，
 * 置于通用 GET 规则之前）。
 */
@RestController
@RequestMapping("/api/v1/assistant/generation-records")
@RequiredArgsConstructor
@Tag(name = "12. AI 元数据推断", description = "AI 生成来源记录（任务、Agent、时间、操作者、动作）")
public class AiGenerationRecordController {

    private final AiGenerationRecordService service;

    @PostMapping
    @Operation(summary = "创建生成来源记录")
    public ApiResponse<GenerationRecordResponse> create(@Valid @RequestBody GenerationRecordRequest request) {
        return ApiResponse.ok(service.create(request, currentUsername()));
    }

    @GetMapping
    @Operation(summary = "按项目查询生成来源记录（新在前）")
    public ApiResponse<List<GenerationRecordResponse>> listByItem(@RequestParam UUID itemId) {
        return ApiResponse.ok(service.listByItem(itemId));
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser user) {
            return user.username();
        }
        return auth.getName();
    }
}
