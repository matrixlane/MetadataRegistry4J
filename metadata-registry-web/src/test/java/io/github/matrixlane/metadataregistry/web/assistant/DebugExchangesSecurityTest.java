package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.auth.JwtAuthenticationFilter;
import io.github.matrixlane.metadataregistry.web.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 调试追踪端点角色限制：GET 显式限 REGISTRAR/SUPER_ADMIN（置于通用 GET
 * 规则之前），不依赖平台「GET 登录即可」默认值。
 */
@WebMvcTest(AssistantController.class)
@Import(SecurityConfig.class)
class DebugExchangesSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AssistantJobService jobService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UrlBasedCorsConfigurationSource corsConfigurationSource;

    @org.junit.jupiter.api.BeforeEach
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
    void get_withoutAuthentication_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/debug/exchanges"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    void get_withNonRegistrarRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/debug/exchanges"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REGISTRAR")
    void get_withRegistrarRole_isOk() throws Exception {
        when(jobService.recentExchanges()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/assistant/debug/exchanges"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"SUPER_ADMIN", "REGISTRAR"})
    void get_withRegistrarRole_isOk_evenWithSuperAdmin() throws Exception {
        when(jobService.recentExchanges()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/assistant/debug/exchanges"))
                .andExpect(status().isOk());
    }
}
