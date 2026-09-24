package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.assistant.dto.AiAssistSettingsRequest;
import io.github.matrixlane.metadataregistry.web.assistant.dto.AiAssistSettingsResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 辅助设置端点。安全由 SecurityConfig 显式规则覆盖：
 * GET/PUT 任何登录用户可用（应用设置，add-workspace-administrative-status
 * 自系统设置迁至右上角应用设置入口）；规则置于通用规则之前。
 *
 * <p>设置持久化到 {@code ~/.metadata-registry/ai-assist.yml}，保存即生效。
 */
@RestController
@RequestMapping("/api/v1/assistant/settings")
@RequiredArgsConstructor
@Tag(name = "12. AI 元数据推断", description = "AI 辅助设置（生成偏好与快捷键）")
public class AiAssistSettingsController {

    private final AiAssistSettingsService settingsService;

    @GetMapping
    @Operation(summary = "查询 AI 辅助设置（候选数与快捷键，含默认值合并）")
    public ApiResponse<AiAssistSettingsResponse> getSettings() {
        return ApiResponse.ok(view());
    }

    @PutMapping
    @Operation(summary = "保存 AI 辅助设置（候选数 1-5、快捷键绑定格式校验，保存即生效）")
    public ApiResponse<AiAssistSettingsResponse> saveSettings(@Valid @RequestBody AiAssistSettingsRequest request) {
        AiAssistSettings.validate(request.candidateCount(), request.hotkeys());
        settingsService.save(new AiAssistSettings(AiAssistSettings.FORMAT_VERSION,
                request.candidateCount(), request.hotkeys()));
        return ApiResponse.ok(view());
    }

    private AiAssistSettingsResponse view() {
        AiAssistSettings current = settingsService.current();
        return new AiAssistSettingsResponse(current.candidateCountOrDefault(), current.hotkeysOrDefault());
    }
}
