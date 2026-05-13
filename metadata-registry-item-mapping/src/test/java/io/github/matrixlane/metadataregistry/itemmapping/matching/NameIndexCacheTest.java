package io.github.matrixlane.metadataregistry.itemmapping.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NameIndexCacheTest {

    private NameIndexCache cache;
    private UUID itemA;
    private UUID itemB;
    private UUID itemC;
    private List<LocalItemInfo> items;

    @BeforeEach
    void setUp() {
        cache = new NameIndexCache();
        itemA = UUID.randomUUID();
        itemB = UUID.randomUUID();
        itemC = UUID.randomUUID();

        items = List.of(
                new LocalItemInfo(itemA, "Patient Name", List.of("PAT001")),
                new LocalItemInfo(itemB, "Diagnosis Code", List.of("DIA001")),
                new LocalItemInfo(itemC, "患者姓名", List.of())
        );
    }

    @Test
    void buildIndex_shouldIndexAllItems() {
        cache.buildIndex(items);
        assertThat(cache.itemCount()).isEqualTo(3);
        assertThat(cache.nameCount()).isEqualTo(3);
    }

    @Test
    void buildIndex_shouldHandleEmptyList() {
        cache.buildIndex(List.of());
        assertThat(cache.itemCount()).isZero();
    }

    @Test
    void buildIndex_shouldHandleNull() {
        cache.buildIndex(null);
        assertThat(cache.itemCount()).isZero();
    }

    @Test
    void buildIndex_shouldClearPreviousIndex() {
        cache.buildIndex(items);
        assertThat(cache.itemCount()).isEqualTo(3);

        cache.buildIndex(List.of());
        assertThat(cache.itemCount()).isZero();
    }

    @Test
    void lookup_shouldFindByName() {
        cache.buildIndex(items);
        Set<UUID> ids = cache.lookup("Patient Name");
        assertThat(ids).containsExactly(itemA);
    }

    @Test
    void lookup_shouldBeCaseInsensitive() {
        cache.buildIndex(items);
        Set<UUID> ids = cache.lookup("patient name");
        assertThat(ids).containsExactly(itemA);
    }

    @Test
    void lookup_shouldHandleChinese() {
        cache.buildIndex(items);
        Set<UUID> ids = cache.lookup("患者姓名");
        assertThat(ids).containsExactly(itemC);
    }

    @Test
    void lookup_shouldReturnEmptyForUnknownName() {
        cache.buildIndex(items);
        assertThat(cache.lookup("Unknown")).isEmpty();
    }

    @Test
    void lookup_shouldReturnEmptyForNull() {
        cache.buildIndex(items);
        assertThat(cache.lookup(null)).isEmpty();
    }

    @Test
    void addItem_shouldAddToIndex() {
        cache.buildIndex(List.of());
        UUID newItem = UUID.randomUUID();
        cache.addItem(new LocalItemInfo(newItem, "New Item", List.of("NEW001")));

        assertThat(cache.itemCount()).isEqualTo(1);
        assertThat(cache.lookup("New Item")).containsExactly(newItem);
    }

    @Test
    void removeItem_shouldRemoveFromIndex() {
        cache.buildIndex(items);
        assertThat(cache.itemCount()).isEqualTo(3);

        cache.removeItem(itemA);
        assertThat(cache.itemCount()).isEqualTo(2);
        assertThat(cache.lookup("Patient Name")).isEmpty();
    }

    @Test
    void removeItem_shouldHandleNonExistent() {
        cache.buildIndex(items);
        cache.removeItem(UUID.randomUUID());
        assertThat(cache.itemCount()).isEqualTo(3);
    }

    @Test
    void clear_shouldRemoveAllEntries() {
        cache.buildIndex(items);
        cache.clear();
        assertThat(cache.itemCount()).isZero();
        assertThat(cache.nameCount()).isZero();
    }

    @Test
    void getCachedItemIds_shouldReturnAllIds() {
        cache.buildIndex(items);
        assertThat(cache.getCachedItemIds()).containsExactlyInAnyOrder(itemA, itemB, itemC);
    }

    @Test
    void preFilter_shouldReturnAll_whenNameIsShort() {
        cache.buildIndex(items);
        List<LocalItemInfo> filtered = cache.preFilter("P", items);
        assertThat(filtered).hasSize(items.size());
    }

    @Test
    void preFilter_shouldReturnMatching_whenBigramsMatch() {
        cache.buildIndex(items);
        // "Patient Name" has bigrams like "Pa", "at", "ti", etc.
        // "Patient" shares bigrams with "Patient Name"
        List<LocalItemInfo> filtered = cache.preFilter("Patient", items);
        assertThat(filtered).isNotEmpty();
        // Should include at least itemA
        assertThat(filtered).anyMatch(i -> i.getItemId().equals(itemA));
    }

    @Test
    void preFilter_shouldReturnAll_whenNoBigramMatch() {
        cache.buildIndex(items);
        // Completely different name
        List<LocalItemInfo> filtered = cache.preFilter("ZZZZZZZZZZ", items);
        assertThat(filtered).hasSize(items.size()); // Falls back to all
    }
}
