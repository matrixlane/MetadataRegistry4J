package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.auth.AuthService;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardStatusResponse;
import io.github.matrixlane.metadataregistry.web.service.InitWizardService;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InitWizardController.class)
@AutoConfigureMockMvc(addFilters = false)
class InitWizardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InitWizardService initWizardService;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    @MockBean
    private SystemSettingService systemSettingService;

    // SecurityConfig 构造依赖个人版自动认证过滤器（切片中无其合作 bean，Mock 之）
    @MockBean
    private io.github.matrixlane.metadataregistry.web.auth.PersonalEditionAuthenticationFilter personalEditionAuthenticationFilter;

    @Test
    void getStatus_shouldReturnStatusFromService() throws Exception {
        InitWizardStatusResponse response = new InitWizardStatusResponse(false, null);
        when(initWizardService.getStatus()).thenReturn(response);

        mockMvc.perform(get("/api/v1/init-wizard/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.initialized").value(false))
                .andExpect(jsonPath("$.data.edition").doesNotExist());
    }

    @Test
    void setup_shouldCallServiceSetup() throws Exception {
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "TEAM", "Test Authority", "Test Authority English", "test@test.com",
                "0000", "RA", List.of("zh-CN"),  // registrationAuthorityIcd, organizationIdentifier, documentLanguages
                true, true,
                null, null, null, null, null, null,
                true,
                null, null, null, null,
                null, null  // adminIndividualName, adminRoleName
        );

        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), "admin", SystemRole.SUPER_ADMIN
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            // setup 现在按版本分支：TEAM 版返回向导会话（establishSessionAfterInitialization）
            when(initWizardService.getStatus())
                    .thenReturn(new InitWizardStatusResponse(false, "TEAM"));
            when(authService.establishSessionAfterInitialization(any()))
                    .thenReturn(io.github.matrixlane.metadataregistry.web.auth.dto.LoginResponse.of(
                            "wizard-token", 3600,
                            new io.github.matrixlane.metadataregistry.web.auth.dto.AccountProfileResponse(
                                    UUID.randomUUID(), "admin",
                                    io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole.SUPER_ADMIN,
                                    true, java.time.LocalDateTime.now(), null,
                                    null, true, true, false, true, false, false)));
            mockMvc.perform(post("/api/v1/init-wizard/setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        verify(initWizardService).setup(any(InitWizardSetupRequest.class), any());
    }

    @Test
    void setup_personalEditionReturnsNoToken() throws Exception {
        // 个人版免登录：setup 响应 data=null（不签发向导会话令牌）
        InitWizardSetupRequest request = new InitWizardSetupRequest(
                "PERSONAL", "Test Authority", "Test Authority English", "test@test.com",
                "0000", "RA", List.of("zh-CN"),
                true, true,
                null, null, null, null, null, null,
                true,
                null, null, null, null,
                null, null
        );
        when(initWizardService.getStatus())
                .thenReturn(new InitWizardStatusResponse(false, "PERSONAL"));

        mockMvc.perform(post("/api/v1/init-wizard/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService, never()).establishSessionAfterInitialization(any());
    }

    @Test
    void commit_personalEditionAcceptsEmptyBody() throws Exception {
        // 个人版 commit 无密码概念：无请求体也接受（版本分支在 service 内完成）
        mockMvc.perform(post("/api/v1/init-wizard/commit"))
                .andExpect(status().isOk());

        verify(initWizardService).commit(isNull());
    }
}
