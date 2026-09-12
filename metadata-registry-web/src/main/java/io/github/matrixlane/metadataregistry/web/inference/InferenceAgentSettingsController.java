package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceAgentSettingsRequest;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceAgentView;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceAgentsView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推断 Agent 设置端点（SUPER_ADMIN）。安全由既有 SecurityConfig 覆盖
 * （{@code /api/v1/system/**} 自动限定 SUPER_ADMIN，零配置改动）。
 *
 * <p>GET 返回合并视图（内置定义 ⊕ 持久化覆盖 ⊕ 自定义条目，含探测状态）；
 * PUT 校验后保存到 {@code ~/.metadata-registry/inference-agents.yml} 并立即生效
 * （注册表按设置引用变化自动重建）；POST /probe 强制刷新全部 Agent 探测缓存。
 */
@RestController
@RequestMapping("/api/v1/system/inference")
@RequiredArgsConstructor
@Tag(name = "0. 系统管理", description = "系统级运维管理接口")
public class InferenceAgentSettingsController {

    private static final Set<String> BUILTIN_IDS = Set.of("claude", "codex", "kimi");

    private final InferenceAgentSettingsService settingsService;
    private final InferenceProviderRegistry registry;

    @GetMapping("/agents")
    @Operation(summary = "查询推断 Agent 合并设置与探测状态")
    public ApiResponse<InferenceAgentsView> getAgents() {
        return ApiResponse.ok(view(registry.list()));
    }

    @PutMapping("/agents")
    @Operation(summary = "保存推断 Agent 设置（立即生效）")
    public ApiResponse<InferenceAgentsView> saveAgents(@Valid @RequestBody InferenceAgentSettingsRequest request) {
        validate(request);
        settingsService.save(toSettings(request));
        // 保存后设置引用变化，注册表在下次访问时自动重建并重新探测
        return ApiResponse.ok(view(registry.list()));
    }

    @PostMapping("/agents/probe")
    @Operation(summary = "强制刷新全部推断 Agent 的可用性探测")
    public ApiResponse<InferenceAgentsView> probeAgents() {
        return ApiResponse.ok(view(registry.probeAll()));
    }

    private InferenceAgentsView view(List<InferenceAgentView> agents) {
        return new InferenceAgentsView(registry.defaultProviderId(), agents);
    }

    /** 保存前校验：自定义条目必须带 command；默认提供方必须对应已启用条目。 */
    private void validate(InferenceAgentSettingsRequest request) {
        if (request.agents() != null) {
            for (InferenceAgentSettingsRequest.AgentItem item : request.agents()) {
                String id = item.id().trim();
                boolean builtin = BUILTIN_IDS.contains(id);
                if (!builtin && (item.command() == null || item.command().isBlank())) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "自定义推断 Agent 必须提供 command：" + id);
                }
            }
        }
        String defaultId = request.defaultProvider();
        if (defaultId != null && !defaultId.isBlank() && !isEnabledIn(request, defaultId.trim())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "默认提供方必须对应已启用的 Agent：" + defaultId.trim());
        }
    }

    /** 请求落地后该 id 是否处于启用状态：条目显式 enabled 优先，未列出的内置条目默认启用。 */
    private boolean isEnabledIn(InferenceAgentSettingsRequest request, String id) {
        if (request.agents() != null) {
            for (InferenceAgentSettingsRequest.AgentItem item : request.agents()) {
                if (item.id().trim().equals(id)) {
                    return item.enabled() == null || item.enabled();
                }
            }
        }
        return BUILTIN_IDS.contains(id);
    }

    private InferenceAgentSettings toSettings(InferenceAgentSettingsRequest request) {
        Map<String, InferenceAgentSettings.AgentSetting> agents = new LinkedHashMap<>();
        if (request.agents() != null) {
            for (InferenceAgentSettingsRequest.AgentItem item : request.agents()) {
                String id = item.id().trim();
                agents.put(id, new InferenceAgentSettings.AgentSetting(item.enabled(),
                        blankToNull(item.displayName()), blankToNull(item.command()), copyArgs(item.args())));
            }
        }
        String defaultProvider = request.defaultProvider() == null || request.defaultProvider().isBlank()
                ? null : request.defaultProvider().trim();
        return new InferenceAgentSettings(InferenceAgentSettings.FORMAT_VERSION, defaultProvider, agents);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> copyArgs(List<String> args) {
        return args == null || args.isEmpty() ? null : List.copyOf(args);
    }
}
