package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.dto.version.FieldDiffEntry;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionHistoryEntryResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionSnapshotResponse;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionHistoryServiceTest {

    @Mock
    private VersionHistoryRepository repository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private VersionHistoryService service;

    private static final UUID ITEM_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void listVersions_shouldReturnEntriesInDescOrder() {
        VersionHistory v1 = VersionHistory.of(ITEM_ID, "DataElement", 1, "CREATE",
                "admin", LocalDateTime.of(2024, 1, 1, 10, 0),
                "{}", "Created");
        VersionHistory v2 = VersionHistory.of(ITEM_ID, "DataElement", 2, "UPDATE",
                "admin", LocalDateTime.of(2024, 1, 2, 10, 0),
                "{}", "Updated");
        when(repository.findByItemIdOrderByVersionNumberDesc(ITEM_ID))
                .thenReturn(List.of(v2, v1));

        List<VersionHistoryEntryResponse> result = service.listVersions(ITEM_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).versionNumber()).isEqualTo(2);
        assertThat(result.get(1).versionNumber()).isEqualTo(1);
    }

    @Test
    void diff_shouldDetectAddedField() {
        String base = """
                {"name":"old","id":"1"}
                """;
        String compare = """
                {"name":"old","id":"1","newField":"value"}
                """;
        setupDiffMocks(base, compare);

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);

        FieldDiffEntry added = result.fields().stream()
                .filter(f -> f.fieldName().equals("newField"))
                .findFirst()
                .orElseThrow();
        assertThat(added.type()).isEqualTo(FieldDiffEntry.DiffType.ADDED);
        assertThat(added.oldValue()).isNull();
        assertThat(added.newValue()).isEqualTo("value");
        assertThat(result.hasDifferences()).isTrue();
    }

    @Test
    void diff_shouldDetectRemovedField() {
        String base = """
                {"name":"old","id":"1","removed":"val"}
                """;
        String compare = """
                {"name":"old","id":"1"}
                """;
        setupDiffMocks(base, compare);

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);

        FieldDiffEntry removed = result.fields().stream()
                .filter(f -> f.fieldName().equals("removed"))
                .findFirst()
                .orElseThrow();
        assertThat(removed.type()).isEqualTo(FieldDiffEntry.DiffType.REMOVED);
        assertThat(removed.oldValue()).isEqualTo("val");
        assertThat(removed.newValue()).isNull();
        assertThat(result.hasDifferences()).isTrue();
    }

    @Test
    void diff_shouldDetectModifiedField() {
        String base = """
                {"name":"old","id":"1"}
                """;
        String compare = """
                {"name":"new","id":"1"}
                """;
        setupDiffMocks(base, compare);

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);

        FieldDiffEntry modified = result.fields().stream()
                .filter(f -> f.fieldName().equals("name"))
                .findFirst()
                .orElseThrow();
        assertThat(modified.type()).isEqualTo(FieldDiffEntry.DiffType.MODIFIED);
        assertThat(modified.oldValue()).isEqualTo("old");
        assertThat(modified.newValue()).isEqualTo("new");
        assertThat(result.hasDifferences()).isTrue();
    }

    @Test
    void diff_shouldDetectUnchangedField() {
        String base = """
                {"name":"same","id":"1"}
                """;
        String compare = """
                {"name":"same","id":"1"}
                """;
        setupDiffMocks(base, compare);

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);

        assertThat(result.fields()).hasSize(2);
        assertThat(result.fields()).allMatch(f -> f.type() == FieldDiffEntry.DiffType.UNCHANGED);
        assertThat(result.hasDifferences()).isFalse();
    }

    @Test
    void diff_shouldDetectNestedFieldChanges() {
        String base = """
                {"outer":{"inner":"a","keep":"x"}}
                """;
        String compare = """
                {"outer":{"inner":"b","keep":"x"}}
                """;
        setupDiffMocks(base, compare);

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);

        FieldDiffEntry nested = result.fields().stream()
                .filter(f -> f.fieldName().equals("outer.inner"))
                .findFirst()
                .orElseThrow();
        assertThat(nested.type()).isEqualTo(FieldDiffEntry.DiffType.MODIFIED);
        assertThat(nested.oldValue()).isEqualTo("a");
        assertThat(nested.newValue()).isEqualTo("b");
    }

    @Test
    void diff_shouldHandleEmptyDiff() {
        String base = "{}";
        String compare = "{}";
        setupDiffMocks(base, compare);

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);

        assertThat(result.fields()).isEmpty();
        assertThat(result.hasDifferences()).isFalse();
    }

    @Test
    void getSnapshot_shouldReturnSnapshot() {
        String snapshot = """
                {"name":"test"}
                """;
        VersionHistory vh = VersionHistory.of(ITEM_ID, "DataElement", 1, "CREATE",
                "admin", LocalDateTime.of(2024, 1, 1, 10, 0),
                snapshot, "Created");
        when(repository.findByItemIdAndVersionNumber(ITEM_ID, 1))
                .thenReturn(Optional.of(vh));

        VersionSnapshotResponse result = service.getSnapshot(ITEM_ID, 1);

        assertThat(result.versionNumber()).isEqualTo(1);
        assertThat(result.snapshot().get("name").asText()).isEqualTo("test");
    }

    @Test
    void computeDiff_shouldDetectArrayChanges() {
        String base = """
                {"tags":["a","b"]}
                """;
        String compare = """
                {"tags":["a","c"]}
                """;

        List<FieldDiffEntry> result = service.computeDiff(base, compare);

        FieldDiffEntry tags = result.stream()
                .filter(f -> f.fieldName().equals("tags"))
                .findFirst()
                .orElseThrow();
        assertThat(tags.type()).isEqualTo(FieldDiffEntry.DiffType.MODIFIED);
        @SuppressWarnings("unchecked")
        List<Object> oldList = (List<Object>) tags.oldValue();
        @SuppressWarnings("unchecked")
        List<Object> newList = (List<Object>) tags.newValue();
        assertThat(oldList).containsExactly("a", "b");
        assertThat(newList).containsExactly("a", "c");
    }

    private void setupDiffMocks(String baseJson, String compareJson) {
        VersionHistory base = VersionHistory.of(ITEM_ID, "DataElement", 1, "CREATE",
                "admin", LocalDateTime.of(2024, 1, 1, 10, 0),
                baseJson, "Created");
        VersionHistory compare = VersionHistory.of(ITEM_ID, "DataElement", 2, "UPDATE",
                "admin", LocalDateTime.of(2024, 1, 2, 10, 0),
                compareJson, "Updated");
        when(repository.findByItemIdAndVersionNumber(ITEM_ID, 1))
                .thenReturn(Optional.of(base));
        when(repository.findByItemIdAndVersionNumber(ITEM_ID, 2))
                .thenReturn(Optional.of(compare));
    }
}
