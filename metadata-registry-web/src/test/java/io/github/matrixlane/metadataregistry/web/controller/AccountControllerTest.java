package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountController;
import io.github.matrixlane.metadataregistry.web.auth.AuthService;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountUpdateRequest;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    @MockBean
    private SystemSettingService systemSettingService;

    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        when(systemSettingService.isInitialized()).thenReturn(true);
    }

    private UsernamePasswordAuthenticationToken createAuthToken(UUID id) {
        AuthenticatedUser principal = new AuthenticatedUser(id, "admin", SystemRole.SUPER_ADMIN);
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }

    @Test
    @DisplayName("更新账户：更新自己账户时，若试图禁用自己，应抛出 BusinessException 异常并返回 BadRequest")
    void update_ownAccountDisable_shouldThrowBusinessException() throws Exception {
        AccountUpdateRequest request = new AccountUpdateRequest(null, false, "newPassword123");

        UsernamePasswordAuthenticationToken auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(put("/api/v1/accounts/{id}", currentUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("不能禁用自己的账号"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("删除账户：超级管理员删除自己账户时，应抛出 BusinessException 异常并返回 BadRequest")
    void delete_ownAccount_shouldThrowBusinessException() throws Exception {
        UsernamePasswordAuthenticationToken auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(delete("/api/v1/accounts/{id}", currentUserId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("不能删除自己的账号"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("审核与启用：超级管理员禁用自己账户时，应抛出 BusinessException 异常并返回 BadRequest")
    void audit_ownAccountDisable_shouldThrowBusinessException() throws Exception {
        UsernamePasswordAuthenticationToken auth = createAuthToken(currentUserId);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mockMvc.perform(put("/api/v1/accounts/{id}/audit", currentUserId)
                            .param("enabled", "false"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("不能禁用自己的账号"));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
