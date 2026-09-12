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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 生成来源记录端点角色限制：GET 显式限 REGISTRAR/SUPER_ADMIN（置于通用 GET
 * 规则之前），POST 沿用平台默认写权限。
 */
@WebMvcTest(AiGenerationRecordController.class)
@Import(SecurityConfig.class)
class GenerationRecordsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiGenerationRecordService recordService;

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
        mockMvc.perform(get("/api/v1/assistant/generation-records")
                        .param("itemId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    void get_withNonRegistrarRole_isForbidden() throws Exception {
        when(recordService.listByItem(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/assistant/generation-records")
                        .param("itemId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REGISTRAR")
    void get_withRegistrarRole_isOk() throws Exception {
        when(recordService.listByItem(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/assistant/generation-records")
                        .param("itemId", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "READER")
    void post_withNonRegistrarRole_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/assistant/generation-records")
                        .contentType("application/json")
                        .content("{\"itemId\":\"" + UUID.randomUUID() + "\",\"taskId\":\"explain-selection\","
                                + "\"outputKind\":\"TEXT\",\"rawOutput\":\"x\",\"actionType\":\"SAVED_TO_EXPLANATORY_COMMENT\"}"))
                .andExpect(status().isForbidden());
    }
}
