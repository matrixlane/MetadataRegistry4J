package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.startup.StartupReadinessIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StartupController.class)
@AutoConfigureMockMvc(addFilters = false)
class StartupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StartupReadinessIndicator readinessIndicator;

    // @WebMvcTest 会把 servlet Filter（JwtAuthenticationFilter）扫入上下文，
    // 需补齐其构造依赖 JwtService，与 InitWizardControllerTest 同款处理
    @MockBean
    private JwtService jwtService;

    @Test
    void statusShouldReturnNotReadyWhileStarting() throws Exception {
        when(readinessIndicator.isReady()).thenReturn(false);

        mockMvc.perform(get("/api/v1/startup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ready").value(false));
    }

    @Test
    void statusShouldReturnReadyAfterStartupCompleted() throws Exception {
        when(readinessIndicator.isReady()).thenReturn(true);

        mockMvc.perform(get("/api/v1/startup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ready").value(true));
    }
}
