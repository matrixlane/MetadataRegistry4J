package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.auth.JwtAuthenticationFilter;
import io.github.matrixlane.metadataregistry.web.auth.SecurityConfig;
import io.github.matrixlane.metadataregistry.web.inference.InferenceController;
import io.github.matrixlane.metadataregistry.web.inference.InferenceJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限矩阵（ai-assist-ux-revamp D2）：AI 使用类端点仅 REGISTRAR——无 Registrar 身份的
 * SUPER_ADMIN 一律 403；GET settings 限 REGISTRAR、PUT settings 限 SUPER_ADMIN。
 */
@WebMvcTest({AssistantController.class, AiAssistSettingsController.class, InferenceController.class})
@Import(SecurityConfig.class)
class AiAssistPermissionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssistantJobService jobService;
    @MockBean
    private AiAssistSettingsService settingsService;
    @MockBean
    private InferenceJobService inferenceJobService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private UrlBasedCorsConfigurationSource corsConfigurationSource;

    @BeforeEach
    void stubJwtFilter() throws Exception {
        // mock 过滤器默认吞掉链路；这里显式放行，让授权规则继续生效
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<jakarta.servlet.FilterChain>getArgument(2)
                    .doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminWithoutRegistrarRole_isForbiddenOnAiUsageEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/tasks"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/assistant/debug/exchanges"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/assistant/jobs/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/inference/providers"))
                .andExpect(status().isForbidden());
        mockMvc.perform(postJson("/api/v1/inference/items/" + UUID.randomUUID() + "/jobs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN", "REGISTRAR"})
    void superAdminWithRegistrarRole_passes() throws Exception {
        when(jobService.tasks()).thenReturn(List.of());
        when(inferenceJobService.availableProviderOptions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/assistant/tasks"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inference/providers"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "REGISTRAR")
    void registrar_passesAiUsageAndReadsSettings() throws Exception {
        when(jobService.tasks()).thenReturn(List.of());
        when(settingsService.current()).thenReturn(AiAssistSettings.empty());

        mockMvc.perform(get("/api/v1/assistant/tasks"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assistant/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void putSettings_superAdmin_ok_butRegistrar_forbidden() throws Exception {
        when(settingsService.current()).thenReturn(AiAssistSettings.empty());
        String body = "{\"candidateCount\":3,\"hotkeys\":{\"aiDebug.toggle\":\"Ctrl+Shift+D\"}}";

        mockMvc.perform(put("/api/v1/assistant/settings")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assistant/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REGISTRAR")
    void putSettings_registrar_isForbidden() throws Exception {
        String body = "{\"candidateCount\":3,\"hotkeys\":{}}";

        mockMvc.perform(put("/api/v1/assistant/settings")
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.RequestBuilder postJson(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url)
                .contentType("application/json")
                .content("{\"fields\":[\"definition\"]}");
    }
}
