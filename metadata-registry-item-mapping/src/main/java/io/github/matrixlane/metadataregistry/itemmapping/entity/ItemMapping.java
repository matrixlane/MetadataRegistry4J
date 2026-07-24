package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a mapping relationship between two administered items or
 * between an administered item and an external resource.
 *
 * <p>This is the core entity for ISO/IEC 11179-3:2023 Clause 11 — Item Mapping.
 * A mapping consists of:
 * <ul>
 *   <li>A subject (source) — either an internal {@code AdministeredItem}
 *       or an external URI</li>
 *   <li>An object (target) — either an internal {@code AdministeredItem}
 *       or an external URI</li>
 *   <li>A mapping type that classifies the relationship</li>
 * </ul>
 *
 * <p>MVP: Mapping always resolves to the latest version of {@code AdministeredItem}
 * (no version pinning). Version binding will be evaluated for V1.
 */
@Entity
@Table(name = "item_mapping")
@Getter
@Setter
@NoArgsConstructor
public class ItemMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The subject (source) of the mapping relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "subject_id", nullable = false)
    private ItemMappingSubject subject;

    /**
     * The object (target) of the mapping relationship.
     */
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "object_id", nullable = false)
    private ItemMappingObject object;

    /**
     * The type of mapping relationship.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", nullable = false, length = 32)
    private MappingType mappingType;

    /**
     * Human-readable description of this mapping (optional).
     */
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * Transformation rule for converting between subject and object
     * (e.g. a formula, code snippet, or reference to a transformation spec).
     * Relevant for DERIVED, AGGREGATED, and SUBDIVIDED mappings.
     */
    @Column(name = "transformation_rule", length = 4000)
    private String transformationRule;

    /**
     * The mapping scheme this mapping belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "mapping_scheme_id")
    private MappingScheme mappingScheme;

    /**
     * Timestamp when this mapping was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Creates a new ItemMapping with the current timestamp.
     *
     * @param subject     the subject (source) reference
     * @param object      the object (target) reference
     * @param mappingType the type of mapping relationship
     */
    public ItemMapping(ItemMappingSubject subject, ItemMappingObject object, MappingType mappingType) {
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.object = Objects.requireNonNull(object, "object must not be null");
        this.mappingType = Objects.requireNonNull(mappingType, "mappingType must not be null");
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Creates a new ItemMapping with an explicit description.
     *
     * @param subject     the subject (source) reference
     * @param object      the object (target) reference
     * @param mappingType the type of mapping relationship
     * @param description optional human-readable description
     */
    public ItemMapping(ItemMappingSubject subject, ItemMappingObject object,
                       MappingType mappingType, String description) {
        this(subject, object, mappingType);
        this.description = description;
    }

    /**
     * Creates a new ItemMapping with scheme and transformation rule.
     *
     * @param subject           the subject (source) reference
     * @param object            the object (target) reference
     * @param mappingType       the type of mapping relationship
     * @param description       optional human-readable description
     * @param transformationRule optional transformation rule
     * @param mappingScheme      the mapping scheme this mapping belongs to
     */
    public ItemMapping(ItemMappingSubject subject, ItemMappingObject object,
                       MappingType mappingType, String description,
                       String transformationRule, MappingScheme mappingScheme) {
        this(subject, object, mappingType, description);
        this.transformationRule = transformationRule;
        this.mappingScheme = mappingScheme;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemMapping that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "ItemMapping{" +
                "id=" + id +
                ", mappingType=" + mappingType +
                ", createdAt=" + createdAt +
                '}';
    }
}

