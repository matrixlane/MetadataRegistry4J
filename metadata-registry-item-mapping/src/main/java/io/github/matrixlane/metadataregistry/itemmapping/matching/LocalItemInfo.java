package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Simplified representation of a local registry Item for matching purposes.
 *
 * <p>Contains the fields from {@code Item}, {@code Designation}, and
 * {@code ScopedIdentifier} that the matching engine needs, extracted from
 * the JPA entity graph or constructed from any source.
 */
public class LocalItemInfo {

    private final UUID itemId;
    private final String name;
    private final List<String> identifiers;

    /**
     * @param itemId      the UUID of the Item
     * @param name        the display name (from the first Designation's Sign value, or empty string)
     * @param identifiers list of ScopedIdentifier identifier values (may be empty)
     */
    public LocalItemInfo(UUID itemId, String name, List<String> identifiers) {
        this.itemId = Objects.requireNonNull(itemId, "itemId must not be null");
        this.name = name != null ? name : "";
        this.identifiers = identifiers != null
                ? Collections.unmodifiableList(List.copyOf(identifiers))
                : List.of();
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public List<String> getIdentifiers() {
        return identifiers;
    }

    @Override
    public String toString() {
        return "LocalItemInfo{itemId=" + itemId + ", name='" + name + "', identifiers=" + identifiers + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalItemInfo that)) return false;
        return itemId.equals(that.itemId);
    }

    @Override
    public int hashCode() {
        return itemId.hashCode();
    }
}
