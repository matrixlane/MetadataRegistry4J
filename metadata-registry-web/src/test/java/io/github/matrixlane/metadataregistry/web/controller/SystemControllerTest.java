package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.system.FactoryResetRequest;
import io.github.matrixlane.metadataregistry.web.service.FactoryResetService;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FactoryResetService factoryResetService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    @MockBean
    private SystemSettingService systemSettingService;

    @BeforeEach
    void setUp() {
        when(systemSettingService.isInitialized()).thenReturn(true);
    }

    // =========================================================================
    // 1. 正常调用
    // =========================================================================

    @Test
    @DisplayName("恢复出厂设置：管理员提交正确密码应返回成功")
    void factoryReset_asAdmin_shouldReturnOk() throws Exception {
        UUID adminId = UUID.randomUUID();
        FactoryResetRequest request = new FactoryResetRequest("Admin@123");
        doNothing().when(factoryResetService).executeFactoryReset(any(UUID.class), eq("Admin@123"));

        UsernamePasswordAuthenticationToken auth = createAdminAuth(adminId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(post("/api/v1/system/factory-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.message").value("OK"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        verify(factoryResetService).executeFactoryReset(eq(adminId), eq("Admin@123"));
    }

    // =========================================================================
    // 2. 密码为空
    // =========================================================================

    @Test
    @DisplayName("恢复出厂设置：密码为空应返回 422")
    void factoryReset_blankPassword_shouldReturn422() throws Exception {
        UUID adminId = UUID.randomUUID();
        FactoryResetRequest request = new FactoryResetRequest("");

        UsernamePasswordAuthenticationToken auth = createAdminAuth(adminId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(post("/api/v1/system/factory-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // =========================================================================
    // 3. 密码错误（service 层抛异常）
    // =========================================================================

    @Test
    @DisplayName("恢复出厂设置：密码错误时 service 抛出 BusinessException")
    void factoryReset_wrongPassword_shouldPropagateException() throws Exception {
        UUID adminId = UUID.randomUUID();
        FactoryResetRequest request = new FactoryResetRequest("wrongPassword");
        doThrow(new BusinessException(ApiCodes.UNAUTHORIZED, "密码验证失败"))
                .when(factoryResetService).executeFactoryReset(any(UUID.class), eq("wrongPassword"));

        UsernamePasswordAuthenticationToken auth = createAdminAuth(adminId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(post("/api/v1/system/factory-reset")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ApiCodes.UNAUTHORIZED))
                    .andExpect(jsonPath("$.message").value("密码验证失败"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private UsernamePasswordAuthenticationToken createAdminAuth(UUID id) {
        AuthenticatedUser principal = new AuthenticatedUser(id, "admin", SystemRole.SUPER_ADMIN);
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }
}
