package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartAcceptedResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartCapabilityResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartStatusResponse;
import io.github.matrixlane.metadataregistry.web.service.DatasourceConfigService;
import io.github.matrixlane.metadataregistry.web.service.DatasourceRestartConflictException;
import io.github.matrixlane.metadataregistry.web.service.DatasourceRestartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DatasourceConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
class DatasourceConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DatasourceConfigService datasourceConfigService;

    @MockBean
    private DatasourceRestartService datasourceRestartService;

    @MockBean
    private InitializationStatusProvider statusProvider;

    @MockBean
    private JwtService jwtService;

    @Test
    void capabilityReturnsCurrentSupport() throws Exception {
        when(datasourceRestartService.capability("127.0.0.1"))
                .thenReturn(new DatasourceRestartCapabilityResponse(true, null));

        mockMvc.perform(get("/api/v1/datasource/restart-capability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.supported").value(true));
    }

    @Test
    void applyReturnsAcceptedAndRestartId() throws Exception {
        UUID id = UUID.randomUUID();
        when(datasourceRestartService.apply(any(), eq("127.0.0.1")))
                .thenReturn(new DatasourceRestartAcceptedResponse(id));

        mockMvc.perform(post("/api/v1/datasource/config/apply")
                        .remoteAddress("127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restartId": "%s",
                                  "config": {
                                    "vendor": "sqlite",
                                    "filePath": "target.db",
                                    "edition": "PERSONAL"
                                  }
                                }
                                """.formatted(id)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.restartId").value(id.toString()));
    }

    @Test
    void duplicateIdWithDifferentConfigReturnsHttp409() throws Exception {
        when(datasourceRestartService.apply(any(), any()))
                .thenThrow(new DatasourceRestartConflictException("重启标识已用于其他配置"));

        mockMvc.perform(post("/api/v1/datasource/config/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restartId": "00000000-0000-0000-0000-000000000001",
                                  "config": {"vendor": "sqlite", "filePath": "target.db"}
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void restartStatusReturnsOnlyPublicFields() throws Exception {
        UUID id = UUID.randomUUID();
        when(datasourceRestartService.status(eq(id), eq("127.0.0.1"))).thenReturn(
                new DatasourceRestartStatusResponse(id.toString(), "VERIFYING", "PENDING", null));

        mockMvc.perform(get("/api/v1/datasource/restarts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restartId").value(id.toString()))
                .andExpect(jsonPath("$.data.state").value("VERIFYING"))
                .andExpect(jsonPath("$.data.verification").value("PENDING"))
                .andExpect(jsonPath("$.data.expectedJdbcUrl").doesNotExist())
                .andExpect(jsonPath("$.data.backupPath").doesNotExist());
    }
}
