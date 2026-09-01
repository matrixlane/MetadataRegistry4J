package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionHistoryEntryResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionSnapshotResponse;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // 取消用户主目录 application-local.yml 导入并显式锁定内存 SQLite,
        // 使本测试与宿主机数据源配置无关(导入的配置优先级高于非 profile 默认值)。
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:version-history-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@ActiveProfiles("test")
class VersionHistoryIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private VersionHistoryRepository repository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SystemSettingService systemSettingService;

    private static final UUID ITEM_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        systemSettingService.setInitialized(true);

        Account user = new Account();
        user.setId(UUID.randomUUID());
        user.setUsername("admin");
        user.setSysRole(SystemRole.SUPER_ADMIN);
        String token = jwtService.generateToken(user);

        restTemplate.getRestTemplate().getInterceptors().clear();
        restTemplate.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + token);
            return execution.execute(request, body);
        });

        repository.deleteAllByItemId(ITEM_ID);

        VersionHistory v1 = VersionHistory.of(ITEM_ID, "DataElement", 1, "CREATE",
                "admin", LocalDateTime.of(2024, 1, 1, 10, 0),
                "{\"name\":\"Original\",\"type\":\"string\"}", "Created DataElement");
        VersionHistory v2 = VersionHistory.of(ITEM_ID, "DataElement", 2, "UPDATE",
                "editor", LocalDateTime.of(2024, 1, 2, 14, 30),
                "{\"name\":\"Updated\",\"type\":\"string\",\"description\":\"added\"}", "Updated name and added description");
        VersionHistory v3 = VersionHistory.of(ITEM_ID, "DataElement", 3, "UPDATE",
                "editor", LocalDateTime.of(2024, 1, 3, 9, 0),
                "{\"name\":\"Updated\",\"type\":\"integer\"}", "Changed type");

        repository.save(v1);
        repository.save(v2);
        repository.save(v3);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAllByItemId(ITEM_ID);
    }

    @Test
    void listVersions_shouldReturnVersionHistoryTimeline() {
        ResponseEntity<ApiResponse<List<VersionHistoryEntryResponse>>> response = restTemplate.exchange(
                "/api/v1/items/{id}/versions",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                ITEM_ID);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        List<VersionHistoryEntryResponse> versions = response.getBody().getData();
        assertThat(versions).hasSize(3);
        // Should be in descending order by version number
        assertThat(versions.get(0).versionNumber()).isEqualTo(3);
        assertThat(versions.get(1).versionNumber()).isEqualTo(2);
        assertThat(versions.get(2).versionNumber()).isEqualTo(1);
        assertThat(versions.get(0).changeType()).isEqualTo("UPDATE");
        assertThat(versions.get(0).changedBy()).isEqualTo("editor");
        assertThat(versions.get(2).changeType()).isEqualTo("CREATE");
        assertThat(versions.get(2).changedBy()).isEqualTo("admin");
    }

    @Test
    void diff_shouldDetectFieldLevelChanges() {
        ResponseEntity<ApiResponse<VersionDiffResponse>> response = restTemplate.exchange(
                "/api/v1/items/{id}/versions/{versionId}/diff?compareVersionId={compareVersionId}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                ITEM_ID, 1, 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        VersionDiffResponse diff = response.getBody().getData();
        assertThat(diff.hasDifferences()).isTrue();
        assertThat(diff.baseVersion()).isEqualTo(1);
        assertThat(diff.compareVersion()).isEqualTo(2);

        // name changed from Original to Updated
        var nameDiff = diff.fields().stream()
                .filter(f -> f.fieldName().equals("name"))
                .findFirst()
                .orElseThrow();
        assertThat(nameDiff.type().name()).isEqualTo("MODIFIED");
        assertThat(nameDiff.oldValue()).isEqualTo("Original");
        assertThat(nameDiff.newValue()).isEqualTo("Updated");

        // description was added
        var descDiff = diff.fields().stream()
                .filter(f -> f.fieldName().equals("description"))
                .findFirst()
                .orElseThrow();
        assertThat(descDiff.type().name()).isEqualTo("ADDED");
        assertThat(descDiff.oldValue()).isNull();
        assertThat(descDiff.newValue()).isEqualTo("added");
    }

    @Test
    void diff_shouldShowNoDifferences_whenVersionsAreIdentical() {
        // Compare version 2 with itself
        ResponseEntity<ApiResponse<VersionDiffResponse>> response = restTemplate.exchange(
                "/api/v1/items/{id}/versions/{versionId}/diff?compareVersionId={compareVersionId}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                ITEM_ID, 2, 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        VersionDiffResponse diff = response.getBody().getData();
        assertThat(diff.hasDifferences()).isFalse();
    }

    @Test
    void getSnapshot_shouldReturnCompleteSnapshot() {
        ResponseEntity<ApiResponse<VersionSnapshotResponse>> response = restTemplate.exchange(
                "/api/v1/items/{id}/versions/{versionId}/snapshot",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                ITEM_ID, 2);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        VersionSnapshotResponse snapshot = response.getBody().getData();
        assertThat(snapshot.versionNumber()).isEqualTo(2);
        assertThat(snapshot.changeType()).isEqualTo("UPDATE");
        assertThat(snapshot.changedBy()).isEqualTo("editor");
        assertThat(snapshot.snapshot().get("name").asText()).isEqualTo("Updated");
        assertThat(snapshot.snapshot().get("type").asText()).isEqualTo("string");
        assertThat(snapshot.snapshot().get("description").asText()).isEqualTo("added");
    }

    @Test
    void getSnapshot_shouldReturn404_whenVersionNotFound() {
        ResponseEntity<ApiResponse<Object>> response = restTemplate.exchange(
                "/api/v1/items/{id}/versions/{versionId}/snapshot",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                ITEM_ID, 999);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void diff_shouldHandleVeryLongTextField() {
        // Save a version with very long text
        String longText = "a".repeat(3000);
        VersionHistory vLong = VersionHistory.of(ITEM_ID, "DataElement", 4, "UPDATE",
                "editor", LocalDateTime.now(),
                "{\"name\":\"Updated\",\"definition\":\"" + longText + "\"}", "Long text update");
        repository.save(vLong);

        ResponseEntity<ApiResponse<VersionDiffResponse>> response = restTemplate.exchange(
                "/api/v1/items/{id}/versions/{versionId}/diff?compareVersionId={compareVersionId}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                ITEM_ID, 3, 4);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        VersionDiffResponse diff = response.getBody().getData();
        assertThat(diff.hasDifferences()).isTrue();

        var defDiff = diff.fields().stream()
                .filter(f -> f.fieldName().equals("definition"))
                .findFirst()
                .orElseThrow();
        assertThat(defDiff.type().name()).isEqualTo("ADDED");
        assertThat(defDiff.newValue()).isEqualTo(longText);
    }
}
