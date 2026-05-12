package io.github.matrixlane.metadataregistry.itemmapping.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the subject (source) side of an {@link ItemMapping}.
 *
 * <p>Each subject record points to either an internal {@code AdministeredItem}
 * (via {@code itemId}) or an external resource (via {@code externalUri}),
 * but never both. This mutual exclusivity is enforced at the application
 * layer and by a CHECK constraint at the database level.
 *
 * <p>Per ISO/IEC 11179-3:2023 Clause 11 — Item Mapping.
 */
@Entity
@Table(name = "item_mapping_subject")
@Getter
@Setter
@NoArgsConstructor
public class ItemMappingSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * References {@code AdministeredItem.id}. Mutually exclusive with
     * {@code externalUri} — exactly one must be non-null.
     */
    @Column(name = "item_id")
    private UUID itemId;

    /**
     * URI of an external resource. Mutually exclusive with {@code itemId} —
     * exactly one must be non-null.
     */
    @Column(name = "external_uri", length = 2048)
    private String externalUri;

    /**
     * Validates that exactly one of {@code itemId} and {@code externalUri}
     * is non-null. This is the application-level enforcement of the
     * mutual exclusivity constraint.
     *
     * @return {@code true} if exactly one reference is provided
     */
    public boolean isReferenceMutuallyExclusive() {
        return (itemId == null) != (externalUri == null);
    }

    /**
     * Creates a subject referencing an internal administered item.
     *
     * @param itemId the UUID of the AdministeredItem
     * @return a new ItemMappingSubject
     * @throws IllegalArgumentException if itemId is null
     */
    public static ItemMappingSubject forItem(UUID itemId) {
        if (itemId == null) {
            throw new IllegalArgumentException("itemId must not be null");
        }
        ItemMappingSubject subject = new ItemMappingSubject();
        subject.itemId = itemId;
        return subject;
    }

    /**
     * Creates a subject referencing an external resource.
     *
     * @param externalUri the URI of the external resource
     * @return a new ItemMappingSubject
     * @throws IllegalArgumentException if externalUri is null or blank
     */
    public static ItemMappingSubject forExternalUri(String externalUri) {
        if (externalUri == null || externalUri.isBlank()) {
            throw new IllegalArgumentException("externalUri must not be null or blank");
        }
        ItemMappingSubject subject = new ItemMappingSubject();
        subject.externalUri = externalUri;
        return subject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemMappingSubject that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "ItemMappingSubject{" +
                "id=" + id +
                ", itemId=" + itemId +
                ", externalUri='" + externalUri + '\'' +
                '}';
    }
}
