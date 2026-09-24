package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.web.controller.ContextController;
import io.github.matrixlane.metadataregistry.web.service.ContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 写操作网关矩阵（add-submitter-contact-identity）：POST/PUT/PATCH/DELETE /api/v1/**
 * 放行 SUPER_ADMIN / REGISTRAR / SUBMITTER；无任何业务身份的账号写操作 403、读操作放行。
 */
@WebMvcTest(ContextController.class)
@Import(SecurityConfig.class)
class SubmitterWriteAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContextService contextService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UrlBasedCorsConfigurationSource corsConfigurationSource;

    @BeforeEach
    void stubJwtFilter() throws Exception {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<jakarta.servlet.FilterChain>getArgument(2)
                    .doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private String jsonBody() {
        return "{\"identifier\":\"CTX-1\",\"names\":[],\"definitions\":[],\"designations\":[]}";
    }

    @Test
    @WithMockUser(roles = "USER")
    void plainUser_writeForbidden_readAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/contexts/options")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/contexts").contentType(MediaType.APPLICATION_JSON).content(jsonBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/contexts/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonBody()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/contexts/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"USER", "SUBMITTER"})
    void submitter_writePassesGateway() throws Exception {
        // 网关放行即返回非 403（业务层校验由后续流程负责）
        MvcResult post = mockMvc.perform(post("/api/v1/contexts")
                        .contentType(MediaType.APPLICATION_JSON).content(jsonBody()))
                .andReturn();
        assertThat(post.getResponse().getStatus()).isNotEqualTo(403);

        MvcResult delete = mockMvc.perform(delete("/api/v1/contexts/" + UUID.randomUUID()))
                .andReturn();
        assertThat(delete.getResponse().getStatus()).isNotEqualTo(403);
    }

    @Test
    @WithMockUser(roles = {"USER", "REGISTRAR"})
    void registrar_stillAllowed() throws Exception {
        MvcResult post = mockMvc.perform(post("/api/v1/contexts")
                        .contentType(MediaType.APPLICATION_JSON).content(jsonBody()))
                .andReturn();
        assertThat(post.getResponse().getStatus()).isNotEqualTo(403);
    }
}
