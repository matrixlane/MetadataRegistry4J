package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceApplyRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartAcceptedResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartCapabilityResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartStatusResponse;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import io.github.matrixlane.metadataregistry.web.startup.StartupReadinessIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatasourceRestartServiceTest {

    private static final String OLD_CONFIG = """
            spring:
              datasource:
                url: jdbc:sqlserver://localhost:1433;databaseName=old_db
                username: old-user
                password: old-secret
            """;

    @TempDir
    Path tempDir;

    private Path controlDir;
    private Path localConfig;
    private DatasourceConfigService configService;
    private DatabaseEnvironment databaseEnvironment;
    private Environment environment;
    private StartupReadinessIndicator readinessIndicator;
    private ObjectMapper objectMapper;
    private DatasourceRestartService service;

    @BeforeEach
    void setUp() throws Exception {
        controlDir = tempDir.resolve("restart");
        Files.createDirectories(controlDir);
        localConfig = tempDir.resolve("application-local.yml");
        databaseEnvironment = mock(DatabaseEnvironment.class);
        configService = new DatasourceConfigService(
                mock(SystemSettingService.class), databaseEnvironment, localConfig);
        environment = mock(Environment.class);
        readinessIndicator = mock(StartupReadinessIndicator.class);
        objectMapper = new ObjectMapper();
        when(environment.getProperty("spring.profiles.active")).thenReturn("personal");
        when(environment.getProperty("MDR_RESTART_SUPERVISED")).thenReturn("true");
        when(environment.getProperty("MDR_RESTART_CONTROL_DIR")).thenReturn(controlDir.toString());
        writeLiveSupervisorRecord();
        service = new DatasourceRestartService(
                configService, databaseEnvironment, environment, objectMapper,
                readinessIndicator);
    }

    /** 能力判定要求常驻启动器真实存活，这里用当前测试进程冒充启动器。 */
    private void writeLiveSupervisorRecord() throws Exception {
        ProcessHandle current = ProcessHandle.current();
        String commandLine = current.info().commandLine().orElse("java");
        String first = commandLine.split("\\s+")[0];
        String processName = first.substring(Math.max(first.lastIndexOf('\\'), first.lastIndexOf('/')) + 1);
        if (processName.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")) {
            processName = processName.substring(0, processName.length() - 4);
        }
        Files.writeString(controlDir.resolve("supervisor.pid.json"),
                "{\"pid\":" + current.pid() + ",\"processName\":\"" + processName + "\"}");
    }

    @Test
    void capabilityRequiresPersonalSupervisedLoopbackAndWritableDirectory() {
        DatasourceRestartCapabilityResponse supported = service.capability("127.0.0.1");
        assertThat(supported.supported()).isTrue();
        assertThat(supported.reasonCode()).isNull();

        when(environment.getProperty("spring.profiles.active")).thenReturn("team");
        assertThat(service.capability("127.0.0.1").supported()).isFalse();
        when(environment.getProperty("spring.profiles.active")).thenReturn("personal");
        when(environment.getProperty("MDR_RESTART_SUPERVISED")).thenReturn("false");
        assertThat(service.capability("127.0.0.1").supported()).isFalse();
        when(environment.getProperty("MDR_RESTART_SUPERVISED")).thenReturn("true");
        assertThat(service.capability("192.168.1.20").supported()).isFalse();
        assertThat(service.capability("localhost").supported()).isFalse();
        assertThat(service.capability("::1").supported()).isTrue();
        assertThat(service.capability("::ffff:127.0.0.1").supported()).isTrue();
    }

    @Test
    void applyWritesBackupRequestAndStatus() throws Exception {
        Files.writeString(localConfig, OLD_CONFIG);
        UUID id = UUID.randomUUID();
        Path sqlite = tempDir.resolve("new data.db");

        DatasourceRestartAcceptedResponse response = service.apply(
                applyRequest(id, sqlite, "PERSONAL"), "127.0.0.1");

        assertThat(response.restartId()).isEqualTo(id);
        JsonNode request = readControlJson("restart-request.json");
        assertThat(request.get("restartId").asText()).isEqualTo(id.toString());
        assertThat(request.get("expectedVendor").asText()).isEqualTo("sqlite");
        assertThat(request.get("expectedJdbcUrl").asText())
                .isEqualTo("jdbc:sqlite:" + sqlite.toAbsolutePath().normalize());
        assertThat(request.get("previousConfigMissing").asBoolean()).isFalse();
        assertThat(request.get("fingerprint").asText()).hasSize(64);
        assertThat(request.toString()).doesNotContain("old-secret");
        Path backup = Path.of(request.get("backupPath").asText());
        assertThat(Files.readString(backup)).isEqualTo(OLD_CONFIG);
        JsonNode status = readControlJson("restart-status.json");
        assertThat(status.get("state").asText()).isEqualTo("PENDING");
        assertThat(status.get("fingerprint").asText()).isEqualTo(request.get("fingerprint").asText());
    }

    @Test
    void applyRejectsWhenEnvironmentVariableWouldOverrideTheNewTarget() throws Exception {
        Files.writeString(localConfig, OLD_CONFIG);
        when(environment.getProperty("DB_URL"))
                .thenReturn("jdbc:sqlserver://localhost:1433;databaseName=other_db");

        assertThatThrownBy(() -> service.apply(
                applyRequest(UUID.randomUUID(), tempDir.resolve("new.db"), "PERSONAL"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(422);
                    assertThat(ex.getMessage()).contains("DB_URL");
                });
        assertThat(Files.readString(localConfig)).isEqualTo(OLD_CONFIG);
        assertThat(Files.exists(controlDir.resolve("restart-request.json"))).isFalse();
    }

    @Test
    void applyReclaimsLockLeftBehindByDeadProcess() throws Exception {
        Files.writeString(localConfig, OLD_CONFIG);
        Files.writeString(controlDir.resolve("restart.lock"), "999999999");

        DatasourceRestartAcceptedResponse response = service.apply(
                applyRequest(UUID.randomUUID(), tempDir.resolve("new.db"), "PERSONAL"), "127.0.0.1");

        assertThat(response.restartId()).isNotNull();
        assertThat(Files.exists(controlDir.resolve("restart-request.json"))).isTrue();
    }

    @Test
    void applyKeepsRequestFileOfRecoveryFailedTransactionForDiagnostics() throws Exception {
        Files.writeString(localConfig, OLD_CONFIG);
        Files.writeString(controlDir.resolve("restart-request.json"),
                "{\"restartId\":\"11111111-1111-1111-1111-111111111111\",\"fingerprint\":\""
                        + "a".repeat(64) + "\",\"expectedVendor\":\"sqlite\","
                        + "\"expectedJdbcUrl\":\"jdbc:sqlite:x.db\",\"backupPath\":\"x\","
                        + "\"previousConfigMissing\":true,\"createdAt\":\"2026-10-24T00:00:00Z\"}");
        Files.writeString(controlDir.resolve("restart-status.json"),
                "{\"restartId\":\"11111111-1111-1111-1111-111111111111\",\"state\":\"RECOVERY_FAILED\","
                        + "\"reasonCode\":\"ROLLBACK_TIMEOUT\",\"fingerprint\":\"" + "a".repeat(64)
                        + "\",\"updatedAt\":\"2026-10-24T00:00:00Z\"}");

        service.apply(applyRequest(UUID.randomUUID(), tempDir.resolve("new.db"), "PERSONAL"), "127.0.0.1");

        assertThat(Files.exists(controlDir.resolve("restart-request.json"))).isTrue();
    }

    @Test
    void statusIsPendingWhenOnlyTheRequestFileExists() throws Exception {
        Files.writeString(localConfig, OLD_CONFIG);
        UUID id = UUID.randomUUID();
        service.apply(applyRequest(id, tempDir.resolve("new.db"), "PERSONAL"), "127.0.0.1");
        Files.delete(controlDir.resolve("restart-status.json"));

        DatasourceRestartStatusResponse status = service.status(id);

        assertThat(status.state()).isEqualTo("PENDING");
        assertThat(status.verification()).isEqualTo("PENDING");
    }

    @Test
    void capabilityIsDisabledWhenTheSupervisorIsGone() throws Exception {
        Files.delete(controlDir.resolve("supervisor.pid.json"));

        DatasourceRestartCapabilityResponse capability = service.capability("127.0.0.1");

        assertThat(capability.supported()).isFalse();
        assertThat(capability.reasonCode()).isEqualTo("SUPERVISOR_REQUIRED");
    }

    @Test
    void applyRejectsRemoteBeforeChangingConfig() throws Exception {
        Files.writeString(localConfig, OLD_CONFIG);
        assertThatThrownBy(() -> service.apply(
                applyRequest(UUID.randomUUID(), tempDir.resolve("new.db"), "PERSONAL"), "10.0.0.8"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(403));
        assertThat(Files.readString(localConfig)).isEqualTo(OLD_CONFIG);
        assertThat(Files.exists(controlDir.resolve("restart-request.json"))).isFalse();
    }

    @Test
    void applyRejectsNonPersonalRequestBeforeChangingConfig() throws Exception {
        Files.writeString(localConfig, OLD_CONFIG);
        assertThatThrownBy(() -> service.apply(
                applyRequest(UUID.randomUUID(), tempDir.resolve("new.db"), "TEAM"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        assertThat(Files.readString(localConfig)).isEqualTo(OLD_CONFIG);
    }

    @Test
    void sameIdAndSameConfigIsIdempotent() {
        UUID id = UUID.randomUUID();
        DatasourceApplyRequest request = applyRequest(id, tempDir.resolve("same.db"), "PERSONAL");
        service.apply(request, "127.0.0.1");
        service.apply(request, "127.0.0.1");

        assertThat(readControlJsonUnchecked("restart-request.json").get("restartId").asText())
                .isEqualTo(id.toString());
    }

    @Test
    void sameIdWithDifferentConfigReturnsConflictAndKeepsFirstTarget() {
        UUID id = UUID.randomUUID();
        Path first = tempDir.resolve("first.db");
        service.apply(applyRequest(id, first, "PERSONAL"), "127.0.0.1");

        assertThatThrownBy(() -> service.apply(
                applyRequest(id, tempDir.resolve("second.db"), "PERSONAL"), "127.0.0.1"))
                .isInstanceOf(DatasourceRestartConflictException.class);
        assertThat(readControlJsonUnchecked("restart-request.json").get("expectedJdbcUrl").asText())
                .endsWith("first.db");
    }

    @Test
    void differentIdWhilePendingReturnsConflict() {
        service.apply(applyRequest(UUID.randomUUID(), tempDir.resolve("first.db"), "PERSONAL"), "127.0.0.1");

        assertThatThrownBy(() -> service.apply(
                applyRequest(UUID.randomUUID(), tempDir.resolve("second.db"), "PERSONAL"), "127.0.0.1"))
                .isInstanceOf(DatasourceRestartConflictException.class);
    }

    @Test
    void requestWriteFailureRestoresPreviousConfigAndDoesNotExit() throws Exception {
        Files.writeString(localConfig, OLD_CONFIG);
        useSpyThatCreatesRequestDirectoryAfterSave();

        assertThatThrownBy(() -> service.apply(
                applyRequest(UUID.randomUUID(), tempDir.resolve("new.db"), "PERSONAL"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        assertThat(Files.readString(localConfig)).isEqualTo(OLD_CONFIG);
    }

    @Test
    void requestWriteFailureRemovesNewConfigWhenNoneExisted() {
        useSpyThatCreatesRequestDirectoryAfterSave();

        assertThatThrownBy(() -> service.apply(
                applyRequest(UUID.randomUUID(), tempDir.resolve("new.db"), "PERSONAL"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        assertThat(Files.exists(localConfig)).isFalse();
    }

    @Test
    void verifyingStatusChecksActualVendorAndJdbcUrl() throws Exception {
        UUID id = UUID.randomUUID();
        Path sqlite = tempDir.resolve("target.db");
        service.apply(applyRequest(id, sqlite, "PERSONAL"), "127.0.0.1");
        writeState("VERIFYING", null);
        when(readinessIndicator.isReady()).thenReturn(true);
        when(databaseEnvironment.vendor()).thenReturn(DatabaseVendor.SQLITE);
        String expectedUrl = "jdbc:sqlite:" + sqlite.toAbsolutePath().normalize();
        when(environment.getProperty("spring.datasource.url")).thenReturn(expectedUrl);

        DatasourceRestartStatusResponse matched = service.status(id);
        assertThat(matched.state()).isEqualTo("VERIFYING");
        assertThat(matched.verification()).isEqualTo("MATCHED");
        assertThat(objectMapper.writeValueAsString(matched))
                .doesNotContain("expectedJdbcUrl", "backupPath", "fingerprint", "password");

        when(environment.getProperty("spring.datasource.url")).thenReturn("jdbc:sqlite:other.db");
        assertThat(service.status(id).verification()).isEqualTo("MISMATCHED");
        when(databaseEnvironment.vendor()).thenReturn(DatabaseVendor.SQLSERVER);
        when(environment.getProperty("spring.datasource.url")).thenReturn(expectedUrl);
        assertThat(service.status(id).verification()).isEqualTo("MISMATCHED");
    }

    @Test
    void statusRejectsNonLoopbackAddress() {
        assertThatThrownBy(() -> service.status(UUID.randomUUID(), "10.0.0.8"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(403));
    }

    @Test
    void terminalAndUnknownStatusesFollowContract() {
        UUID id = UUID.randomUUID();
        service.apply(applyRequest(id, tempDir.resolve("target.db"), "PERSONAL"), "127.0.0.1");
        writeState("SUCCEEDED", null);
        DatasourceRestartStatusResponse succeeded = service.status(id);
        assertThat(succeeded.state()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.verification()).isEqualTo("MATCHED");

        assertThatThrownBy(() -> service.status(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private DatasourceApplyRequest applyRequest(UUID id, Path sqlite, String edition) {
        return new DatasourceApplyRequest(id, new DatasourceConfigRequest(
                "sqlite", null, null, null, sqlite.toString(),
                null, null, null, edition));
    }

    private void useSpyThatCreatesRequestDirectoryAfterSave() {
        DatasourceConfigService spyService = spy(configService);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            Files.createDirectories(controlDir.resolve("restart-request.json"));
            return null;
        }).when(spyService).saveConfig(any());
        service = new DatasourceRestartService(
                spyService, databaseEnvironment, environment, objectMapper,
                readinessIndicator);
    }

    private JsonNode readControlJson(String name) throws IOException {
        return objectMapper.readTree(Files.readString(controlDir.resolve(name)));
    }

    private JsonNode readControlJsonUnchecked(String name) {
        try {
            return readControlJson(name);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void writeState(String state, String reasonCode) {
        try {
            JsonNode request = readControlJson("restart-request.json");
            var status = objectMapper.createObjectNode()
                    .put("restartId", request.get("restartId").asText())
                    .put("state", state)
                    .put("reasonCode", reasonCode)
                    .put("fingerprint", request.get("fingerprint").asText())
                    .put("updatedAt", "2026-10-24T00:00:00Z");
            Files.writeString(controlDir.resolve("restart-status.json"),
                    objectMapper.writeValueAsString(status));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
