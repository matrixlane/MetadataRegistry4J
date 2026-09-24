package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.ApproverMode;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.QuorumType;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.registration.RegistrationProcessResponse;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RegistrationProcessController} 测试：流程定义只读端点（登录可读）。
 */
@WebMvcTest(RegistrationProcessController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegistrationProcessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegistrationProcessService registrationProcessService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    @Test
    void get_shouldReturnCurrentProcess() throws Exception {
        UUID authorityId = UUID.randomUUID();
        when(registrationProcessService.getCurrentProcess()).thenReturn(
                new RegistrationProcessResponse(authorityId, List.of(
                        new RegistrationProcessResponse.TransitionRuleView(
                                RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED,
                                null, true, ApproverMode.ALL_REGISTRARS, QuorumType.ANY_ONE,
                                null, List.of(), true))));

        mockMvc.perform(get("/api/v1/registration-process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorityId").value(authorityId.toString()))
                .andExpect(jsonPath("$.data.transitions[0].from").value("RECORDED"))
                .andExpect(jsonPath("$.data.transitions[0].to").value("QUALIFIED"))
                .andExpect(jsonPath("$.data.transitions[0].requiresApproval").value(true))
                .andExpect(jsonPath("$.data.transitions[0].quorumType").value("ANY_ONE"));
    }
}
