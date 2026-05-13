package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory index cache for local item names.
 *
 * <p>Maps normalized (lowercase) names to sets of item IDs, enabling fast
 * candidate pre-filtering during matching. This reduces the number of
 * items that need full similarity calculation.
 *
 * <p>Thread-safe using {@link ConcurrentHashMap}.
 *
 * <p>MVP implementation: flat mapping of exact normalized name → item IDs.
 * For V1, this could be extended with an inverted index for fuzzy name lookup.
 */
public class NameIndexCache {

    private final Map<String, Set<UUID>> nameToItemIds = new ConcurrentHashMap<>();
    private final Set<UUID> cachedItemIds = ConcurrentHashMap.newKeySet();

    /**
     * Builds or rebuilds the index from a list of local items.
     *
     * <p>Clears any existing index before building.
     *
     * @param items the list of local items to index
     */
    public void buildIndex(List<LocalItemInfo> items) {
        nameToItemIds.clear();
        cachedItemIds.clear();

        if (items == null || items.isEmpty()) {
            return;
        }

        for (LocalItemInfo item : items) {
            String normalizedName = NameNormalizer.normalizeName(item.getName());
            if (!normalizedName.isEmpty()) {
                nameToItemIds.computeIfAbsent(normalizedName, k -> ConcurrentHashMap.newKeySet())
                        .add(item.getItemId());
            }
            cachedItemIds.add(item.getItemId());
        }
    }

    /**
     * Adds a single item to the index.
     *
     * @param item the item to add
     */
    public void addItem(LocalItemInfo item) {
        if (item == null) return;
        String normalizedName = NameNormalizer.normalizeName(item.getName());
        if (!normalizedName.isEmpty()) {
            nameToItemIds.computeIfAbsent(normalizedName, k -> ConcurrentHashMap.newKeySet())
                    .add(item.getItemId());
        }
        cachedItemIds.add(item.getItemId());
    }

    /**
     * Removes a single item from the index.
     *
     * @param itemId the ID of the item to remove
     */
    public void removeItem(UUID itemId) {
        cachedItemIds.remove(itemId);
        // O(n) scan to remove from all name entries — acceptable for MVP
        nameToItemIds.values().forEach(set -> set.remove(itemId));
    }

    /**
     * Looks up item IDs by exact normalized name.
     *
     * @param name the name to look up (will be normalized internally)
     * @return set of matching item IDs, or empty set if no match
     */
    public Set<UUID> lookup(String name) {
        if (name == null) return Set.of();
        String normalized = NameNormalizer.normalizeName(name);
        return nameToItemIds.getOrDefault(normalized, Collections.emptySet());
    }

    /**
     * Pre-filters candidate items for a given name, returning items that share
     * at least one character bigram with the input name.
     *
     * <p>This is a broader pre-filter for name similarity matching that can
     * significantly reduce the candidate set. Falls back to all cached items
     * if the name is too short.
     *
     * @param name         the name to pre-filter for
     * @param allCandidates the full list of candidates (used when name is too short)
     * @return a pre-filtered list of candidates
     */
    public List<LocalItemInfo> preFilter(String name, List<LocalItemInfo> allCandidates) {
        String normalized = NameNormalizer.normalizeName(name);
        if (normalized == null || normalized.length() < 2) {
            return allCandidates;
        }

        Set<String> bigrams = SimilarityCalculator.generateBigrams(normalized);
        if (bigrams.isEmpty()) {
            return allCandidates;
        }

        // Collect all item IDs that match any name bigram
        // For MVP, this iterates nameToItemIds — in V1, use an inverted bigram index
        Set<UUID> matchedIds = new HashSet<>();
        for (Map.Entry<String, Set<UUID>> entry : nameToItemIds.entrySet()) {
            String indexedName = entry.getKey();
            Set<String> indexedBigrams = SimilarityCalculator.generateBigrams(indexedName);
            for (String bigram : bigrams) {
                if (indexedBigrams.contains(bigram)) {
                    matchedIds.addAll(entry.getValue());
                    break;
                }
            }
        }

        if (matchedIds.isEmpty()) {
            return allCandidates;
        }

        return allCandidates.stream()
                .filter(item -> matchedIds.contains(item.getItemId()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the set of all cached item IDs.
     */
    public Set<UUID> getCachedItemIds() {
        return Collections.unmodifiableSet(cachedItemIds);
    }

    /**
     * Clears the entire index.
     */
    public void clear() {
        nameToItemIds.clear();
        cachedItemIds.clear();
    }

    /**
     * Returns the number of distinct names in the index.
     */
    public int nameCount() {
        return nameToItemIds.size();
    }

    /**
     * Returns the number of cached items.
     */
    public int itemCount() {
        return cachedItemIds.size();
    }
}
