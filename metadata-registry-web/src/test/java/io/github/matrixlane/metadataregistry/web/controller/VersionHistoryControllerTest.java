package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.matrixlane.metadataregistry.web.auth.JwtService;
import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.github.matrixlane.metadataregistry.web.dto.version.FieldDiffEntry;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionHistoryEntryResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionSnapshotResponse;
import io.github.matrixlane.metadataregistry.web.service.VersionHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VersionHistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class VersionHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VersionHistoryService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppProperties appProperties;

    private static final UUID ITEM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void listVersions_shouldReturnOk() throws Exception {
        when(service.listVersions(ITEM_ID)).thenReturn(List.of(
                new VersionHistoryEntryResponse(1L, 2, "UPDATE", "admin",
                        LocalDateTime.of(2024, 1, 2, 10, 0), "Updated"),
                new VersionHistoryEntryResponse(2L, 1, "CREATE", "admin",
                        LocalDateTime.of(2024, 1, 1, 10, 0), "Created")
        ));

        mockMvc.perform(get("/api/v1/items/{id}/versions", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].versionNumber").value(2))
                .andExpect(jsonPath("$.data[1].versionNumber").value(1));
    }

    @Test
    void diff_shouldReturnDiffResult() throws Exception {
        VersionDiffResponse diff = new VersionDiffResponse(
                ITEM_ID.toString(), "DataElement", 1, 2,
                LocalDateTime.of(2024, 1, 1, 10, 0),
                LocalDateTime.of(2024, 1, 2, 10, 0),
                "admin", "admin",
                List.of(new FieldDiffEntry("name", "name", FieldDiffEntry.DiffType.MODIFIED, "old", "new")),
                true
        );
        when(service.diff(eq(ITEM_ID), eq(1), eq(2))).thenReturn(diff);

        mockMvc.perform(get("/api/v1/items/{id}/versions/{versionId}/diff", ITEM_ID, 1)
                        .param("compareVersionId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDifferences").value(true))
                .andExpect(jsonPath("$.data.fields[0].fieldName").value("name"))
                .andExpect(jsonPath("$.data.fields[0].type").value("MODIFIED"));
    }

    @Test
    void getSnapshot_shouldReturnSnapshot() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ObjectNode snapshot = om.createObjectNode();
        snapshot.put("name", "test");
        VersionSnapshotResponse resp = new VersionSnapshotResponse(
                1L, 1, "CREATE", "admin",
                LocalDateTime.of(2024, 1, 1, 10, 0),
                "Created", snapshot
        );
        when(service.getSnapshot(ITEM_ID, 1)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/items/{id}/versions/{versionId}/snapshot", ITEM_ID, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNumber").value(1))
                .andExpect(jsonPath("$.data.snapshot.name").value("test"));
    }

    @Test
    void listVersions_shouldReturnEmptyList_whenNoVersions() throws Exception {
        when(service.listVersions(ITEM_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/items/{id}/versions", ITEM_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void diff_shouldReturnNoDifferences_whenIdenticalVersions() throws Exception {
        VersionDiffResponse diff = new VersionDiffResponse(
                ITEM_ID.toString(), "DataElement", 1, 2,
                LocalDateTime.of(2024, 1, 1, 10, 0),
                LocalDateTime.of(2024, 1, 2, 10, 0),
                "admin", "admin",
                Collections.emptyList(),
                false
        );
        when(service.diff(eq(ITEM_ID), eq(1), eq(2))).thenReturn(diff);

        mockMvc.perform(get("/api/v1/items/{id}/versions/{versionId}/diff", ITEM_ID, 1)
                        .param("compareVersionId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDifferences").value(false))
                .andExpect(jsonPath("$.data.fields").isEmpty());
    }

    @Test
    void diff_shouldShowAllDiffTypes() throws Exception {
        VersionDiffResponse diff = new VersionDiffResponse(
                ITEM_ID.toString(), "DataElement", 1, 2,
                LocalDateTime.of(2024, 1, 1, 10, 0),
                LocalDateTime.of(2024, 1, 2, 10, 0),
                "admin", "admin",
                List.of(
                        new FieldDiffEntry("addedField", "addedField", FieldDiffEntry.DiffType.ADDED, null, "newVal"),
                        new FieldDiffEntry("removedField", "removedField", FieldDiffEntry.DiffType.REMOVED, "oldVal", null),
                        new FieldDiffEntry("modifiedField", "modifiedField", FieldDiffEntry.DiffType.MODIFIED, "old", "new"),
                        new FieldDiffEntry("unchangedField", "unchangedField", FieldDiffEntry.DiffType.UNCHANGED, "same", "same")
                ),
                true
        );
        when(service.diff(eq(ITEM_ID), eq(1), eq(2))).thenReturn(diff);

        mockMvc.perform(get("/api/v1/items/{id}/versions/{versionId}/diff", ITEM_ID, 1)
                        .param("compareVersionId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasDifferences").value(true))
                .andExpect(jsonPath("$.data.fields[0].type").value("ADDED"))
                .andExpect(jsonPath("$.data.fields[1].type").value("REMOVED"))
                .andExpect(jsonPath("$.data.fields[2].type").value("MODIFIED"))
                .andExpect(jsonPath("$.data.fields[3].type").value("UNCHANGED"));
    }
}
