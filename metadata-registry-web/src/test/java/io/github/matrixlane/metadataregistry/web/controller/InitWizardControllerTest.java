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
            mockMvc.perform(post("/api/v1/init-wizard/setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        verify(initWizardService).setup(any(InitWizardSetupRequest.class), any());
    }
}
