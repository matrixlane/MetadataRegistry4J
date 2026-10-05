package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityDeletionPreview;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityResponse;
import io.github.matrixlane.metadataregistry.web.service.RegistrationAuthorityDeletionService;
import io.github.matrixlane.metadataregistry.web.service.RegistrationAuthorityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegistrationAuthorityController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegistrationAuthorityControllerTest {

    private static final String BASE_URL = "/api/v1/registration-authorities";
    private static final UUID RA_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ACCOUNT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegistrationAuthorityService service;

    @MockBean
    private RegistrationAuthorityDeletionService deletionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    @Test
    void deletionPreview_returnsCounts() throws Exception {
        RegistrationAuthorityDeletionPreview preview = new RegistrationAuthorityDeletionPreview(
                RA_ID, 5, 3, 2, 1, 4, 20);
        when(deletionService.preview(RA_ID)).thenReturn(preview);

        mockMvc.perform(get(BASE_URL + "/{id}/deletion-preview", RA_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationAuthorityId").value(RA_ID.toString()))
                .andExpect(jsonPath("$.data.administeredItemCount").value(5))
                .andExpect(jsonPath("$.data.submissionRecordCount").value(3))
                .andExpect(jsonPath("$.data.registrarCount").value(2))
                .andExpect(jsonPath("$.data.namespaceCount").value(1))
                .andExpect(jsonPath("$.data.stewardshipRecordCount").value(4))
                .andExpect(jsonPath("$.data.transitionRuleCount").value(20));
    }

    @Test
    void deletionPreview_notFoundReturns404() throws Exception {
        when(deletionService.preview(RA_ID)).thenThrow(new ResourceNotFoundException("RegistrationAuthority", RA_ID));

        mockMvc.perform(get(BASE_URL + "/{id}/deletion-preview", RA_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_passesAuthenticatedPrincipalToService() throws Exception {
        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                "0000", "RA", null, null, null, List.of()
        );
        RegistrationAuthorityResponse response = new RegistrationAuthorityResponse(
                RA_ID, "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                null, null, null, null, null, null, List.of(), List.of(), true);
        when(service.create(any(RegistrationAuthorityRequest.class), eq(ACCOUNT_ID), eq(SystemRole.SUPER_ADMIN)))
                .thenReturn(response);

        AuthenticatedUser principal = new AuthenticatedUser(ACCOUNT_ID, "admin", SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        try {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(RA_ID.toString()));
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(service).create(any(RegistrationAuthorityRequest.class), eq(ACCOUNT_ID), eq(SystemRole.SUPER_ADMIN));
    }

    @Test
    void create_missingInternationalCodeDesignatorReturns400() throws Exception {
        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                null, "RA", null, null, null, List.of()
        );

        AuthenticatedUser principal = new AuthenticatedUser(ACCOUNT_ID, "admin", SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        try {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void update_passesAuthenticatedPrincipalToService() throws Exception {
        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                "0000", "RA", null, null, null, List.of()
        );
        RegistrationAuthorityResponse response = new RegistrationAuthorityResponse(
                RA_ID, "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                null, null, null, null, null, null, List.of(), List.of(), true);
        when(service.update(eq(RA_ID), any(RegistrationAuthorityRequest.class), eq(SystemRole.SUPER_ADMIN)))
                .thenReturn(response);

        AuthenticatedUser principal = new AuthenticatedUser(ACCOUNT_ID, "admin", SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put(BASE_URL + "/{id}", RA_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(RA_ID.toString()));
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(service).update(eq(RA_ID), any(RegistrationAuthorityRequest.class), eq(SystemRole.SUPER_ADMIN));
    }

    @Test
    void delete_passesAuthenticatedPrincipalToService() throws Exception {
        AuthenticatedUser principal = new AuthenticatedUser(ACCOUNT_ID, "admin", SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete(BASE_URL + "/{id}", RA_ID))
                    .andExpect(status().isOk());
        } finally {
            SecurityContextHolder.clearContext();
        }

        verify(service).delete(RA_ID, SystemRole.SUPER_ADMIN);
    }
}
