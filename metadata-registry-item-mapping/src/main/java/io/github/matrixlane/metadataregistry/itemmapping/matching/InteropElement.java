package io.github.matrixlane.metadataregistry.itemmapping.matching;

import java.util.Objects;
import java.util.UUID;

/**
 * A simplified representation of an interop data element for matching purposes.
 *
 * <p>Contains only the fields needed by the matching engine, extracted from
 * the JPA entity {@code InteropDataElement} or constructed from any source.
 */
public class InteropElement {

    private final UUID id;
    private final String identifier;
    private final String name;

    /**
     * @param id         the unique ID of this interop data element
     * @param identifier the business identifier (e.g. "DE01.01.001.00")
     * @param name       the display name (e.g. "患者姓名", "Patient Name")
     */
    public InteropElement(UUID id, String identifier, String name) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.identifier = identifier;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "InteropElement{id=" + id + ", identifier='" + identifier + '\'' + ", name='" + name + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InteropElement that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
