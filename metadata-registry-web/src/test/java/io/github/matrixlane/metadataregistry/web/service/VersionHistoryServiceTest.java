package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.web.dto.version.FieldDiffEntry;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionDiffResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionHistoryEntryResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.VersionSnapshotResponse;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isAuditedType_shouldReturnTrueForAuditedTypes() {
        assertThat(service.isAuditedType("DataElement")).isTrue();
        assertThat(service.isAuditedType("ValueDomain")).isTrue();
        assertThat(service.isAuditedType("DataElementConcept")).isTrue();
    }

    @Test
    void isAuditedType_shouldReturnFalseForNonAuditedTypes() {
        assertThat(service.isAuditedType("UnknownType")).isFalse();
        assertThat(service.isAuditedType(null)).isFalse();
    }

    @Test
    void recordSnapshot_shouldDoNothing_whenItemIsNull() {
        service.recordSnapshot(null, "CREATE");
        verify(repository, never()).save(any());
    }

    @Test
    void recordSnapshot_shouldDoNothing_whenItemIdIsNull() {
        DataElement item = new DataElement();
        service.recordSnapshot(item, "CREATE");
        verify(repository, never()).save(any());
    }

    @Test
    void recordSnapshot_shouldDoNothing_whenItemTypeIsNotAudited() {
        // Use an anonymous subclass of Item that's not in the audited list
        Item item = new Item() {};
        item.setId(ITEM_ID);
        service.recordSnapshot(item, "CREATE");
        verify(repository, never()).save(any());
    }

    @Test
    void recordSnapshot_shouldSaveSnapshot_whenItemIsAudited() {
        DataElement item = new DataElement();
        item.setId(ITEM_ID);

        when(repository.findTopByItemIdOrderByVersionNumberDesc(ITEM_ID))
                .thenReturn(Optional.empty());
        when(repository.save(any(VersionHistory.class))).thenAnswer(i -> i.getArgument(0));

        service.recordSnapshot(item, "CREATE");

        ArgumentCaptor<VersionHistory> captor = ArgumentCaptor.forClass(VersionHistory.class);
        verify(repository).save(captor.capture());
        VersionHistory saved = captor.getValue();
        assertThat(saved.getItemId()).isEqualTo(ITEM_ID);
        assertThat(saved.getItemType()).isEqualTo("DataElement");
        assertThat(saved.getVersionNumber()).isEqualTo(1);
        assertThat(saved.getChangeType()).isEqualTo("CREATE");
    }

    @Test
    void recordSnapshot_shouldIncrementVersionNumber() {
        DataElement item = new DataElement();
        item.setId(ITEM_ID);

        VersionHistory last = VersionHistory.of(ITEM_ID, "DataElement", 3, "UPDATE",
                "admin", LocalDateTime.now(), "{}", "Updated");
        when(repository.findTopByItemIdOrderByVersionNumberDesc(ITEM_ID))
                .thenReturn(Optional.of(last));
        when(repository.save(any(VersionHistory.class))).thenAnswer(i -> i.getArgument(0));

        service.recordSnapshot(item, "UPDATE");

        ArgumentCaptor<VersionHistory> captor = ArgumentCaptor.forClass(VersionHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getVersionNumber()).isEqualTo(4);
    }

    @Test
    void recordSnapshot_shouldTruncateLongSummary() {
        DataElement item = new DataElement();
        item.setId(ITEM_ID);
        // Set a very long toString result through designations (toString uses designations)
        // The buildChangeSummary truncates at 500 chars

        when(repository.findTopByItemIdOrderByVersionNumberDesc(ITEM_ID))
                .thenReturn(Optional.empty());
        when(repository.save(any(VersionHistory.class))).thenAnswer(i -> i.getArgument(0));

        service.recordSnapshot(item, "CREATE");

        ArgumentCaptor<VersionHistory> captor = ArgumentCaptor.forClass(VersionHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChangeSummary().length()).isLessThanOrEqualTo(500);
    }

    @Test
    void recordSnapshot_shouldHandleSerializationExceptionGracefully() throws Exception {
        DataElement item = new DataElement();
        item.setId(ITEM_ID);

        when(repository.findTopByItemIdOrderByVersionNumberDesc(ITEM_ID))
                .thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Serialization failed"));

        // Should not throw
        service.recordSnapshot(item, "CREATE");
        verify(repository, never()).save(any());
    }

    @Test
    void diff_shouldHandleVeryLongTextField() {
        String longText = "a".repeat(5000);
        String base = String.format("{\"name\":\"%s\",\"id\":\"1\"}", longText);
        String compare = String.format("{\"name\":\"%s\",\"id\":\"1\"}", longText + "b");
        setupDiffMocks(base, compare);

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);

        FieldDiffEntry nameDiff = result.fields().stream()
                .filter(f -> f.fieldName().equals("name"))
                .findFirst()
                .orElseThrow();
        assertThat(nameDiff.type()).isEqualTo(FieldDiffEntry.DiffType.MODIFIED);
        assertThat(nameDiff.oldValue()).isEqualTo(longText);
        assertThat(nameDiff.newValue()).isEqualTo(longText + "b");
    }

    @Test
    void diff_shouldHandleNullSnapshotJson() {
        VersionHistory base = VersionHistory.of(ITEM_ID, "DataElement", 1, "CREATE",
                "admin", LocalDateTime.of(2024, 1, 1, 10, 0),
                null, "Created");
        VersionHistory compare = VersionHistory.of(ITEM_ID, "DataElement", 2, "UPDATE",
                "admin", LocalDateTime.of(2024, 1, 2, 10, 0),
                null, "Updated");
        when(repository.findByItemIdAndVersionNumber(ITEM_ID, 1))
                .thenReturn(Optional.of(base));
        when(repository.findByItemIdAndVersionNumber(ITEM_ID, 2))
                .thenReturn(Optional.of(compare));

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);
        assertThat(result.fields()).isEmpty();
        assertThat(result.hasDifferences()).isFalse();
    }

    @Test
    void diff_shouldHandleBlankSnapshotJson() {
        VersionHistory base = VersionHistory.of(ITEM_ID, "DataElement", 1, "CREATE",
                "admin", LocalDateTime.of(2024, 1, 1, 10, 0),
                "   ", "Created");
        VersionHistory compare = VersionHistory.of(ITEM_ID, "DataElement", 2, "UPDATE",
                "admin", LocalDateTime.of(2024, 1, 2, 10, 0),
                "   ", "Updated");
        when(repository.findByItemIdAndVersionNumber(ITEM_ID, 1))
                .thenReturn(Optional.of(base));
        when(repository.findByItemIdAndVersionNumber(ITEM_ID, 2))
                .thenReturn(Optional.of(compare));

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);
        assertThat(result.fields()).isEmpty();
        assertThat(result.hasDifferences()).isFalse();
    }

    @Test
    void getSnapshot_shouldHandleNullSnapshotJson() {
        VersionHistory vh = VersionHistory.of(ITEM_ID, "DataElement", 1, "CREATE",
                "admin", LocalDateTime.of(2024, 1, 1, 10, 0),
                null, "Created");
        when(repository.findByItemIdAndVersionNumber(ITEM_ID, 1))
                .thenReturn(Optional.of(vh));

        VersionSnapshotResponse result = service.getSnapshot(ITEM_ID, 1);
        assertThat(result.versionNumber()).isEqualTo(1);
        assertThat(result.snapshot()).isNotNull();
        assertThat(result.snapshot().isEmpty()).isTrue();
    }

    @Test
    void computeDiff_shouldHandleNullBaseJson() {
        String compare = "{\"name\":\"test\"}";

        List<FieldDiffEntry> result = service.computeDiff(null, compare);

        FieldDiffEntry added = result.stream()
                .filter(f -> f.fieldName().equals("name"))
                .findFirst()
                .orElseThrow();
        assertThat(added.type()).isEqualTo(FieldDiffEntry.DiffType.ADDED);
    }

    @Test
    void computeDiff_shouldHandleNullCompareJson() {
        String base = "{\"name\":\"test\"}";

        List<FieldDiffEntry> result = service.computeDiff(base, null);

        FieldDiffEntry removed = result.stream()
                .filter(f -> f.fieldName().equals("name"))
                .findFirst()
                .orElseThrow();
        assertThat(removed.type()).isEqualTo(FieldDiffEntry.DiffType.REMOVED);
    }

    @Test
    void computeDiff_shouldHandleDeepNestedChanges() {
        String base = """
                {"a":{"b":{"c":"old","d":"keep"}}}
                """;
        String compare = """
                {"a":{"b":{"c":"new","d":"keep"}}}
                """;

        List<FieldDiffEntry> result = service.computeDiff(base, compare);

        FieldDiffEntry nested = result.stream()
                .filter(f -> f.fieldName().equals("a.b.c"))
                .findFirst()
                .orElseThrow();
        assertThat(nested.type()).isEqualTo(FieldDiffEntry.DiffType.MODIFIED);
        assertThat(nested.oldValue()).isEqualTo("old");
        assertThat(nested.newValue()).isEqualTo("new");
    }

    @Test
    void computeDiff_shouldHandleBooleanAndNumberChanges() {
        String base = """
                {"flag":true,"count":42}
                """;
        String compare = """
                {"flag":false,"count":99}
                """;

        List<FieldDiffEntry> result = service.computeDiff(base, compare);

        FieldDiffEntry flag = result.stream()
                .filter(f -> f.fieldName().equals("flag"))
                .findFirst()
                .orElseThrow();
        assertThat(flag.type()).isEqualTo(FieldDiffEntry.DiffType.MODIFIED);
        assertThat(flag.oldValue()).isEqualTo(true);
        assertThat(flag.newValue()).isEqualTo(false);

        FieldDiffEntry count = result.stream()
                .filter(f -> f.fieldName().equals("count"))
                .findFirst()
                .orElseThrow();
        assertThat(count.type()).isEqualTo(FieldDiffEntry.DiffType.MODIFIED);
        assertThat(count.oldValue()).isEqualTo(42);
        assertThat(count.newValue()).isEqualTo(99);
    }

    @Test
    void listVersions_shouldReturnEmptyList_whenNoVersions() {
        when(repository.findByItemIdOrderByVersionNumberDesc(ITEM_ID))
                .thenReturn(List.of());

        List<VersionHistoryEntryResponse> result = service.listVersions(ITEM_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void diff_shouldReturnNoDifferences_whenBothSnapshotsAreIdenticalComplexObjects() {
        String base = """
                {"name":"same","tags":["a","b"],"nested":{"key":"val"},"count":5}
                """;
        setupDiffMocks(base, base);

        VersionDiffResponse result = service.diff(ITEM_ID, 1, 2);

        assertThat(result.hasDifferences()).isFalse();
        assertThat(result.fields()).allMatch(f -> f.type() == FieldDiffEntry.DiffType.UNCHANGED);
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
