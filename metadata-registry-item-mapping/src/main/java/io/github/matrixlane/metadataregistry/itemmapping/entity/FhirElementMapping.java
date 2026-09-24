package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a mapping relationship between a local {@code AdministeredItem}
 * and a FHIR R4 Element.
 *
 * <p>Per WI-012-2 — Local metadata to FHIR Element mapping.
 * This entity builds upon the WI-011 generic mapping framework by adding
 * FHIR-specific fields ({@code fhirPath}, {@code fhirResourceType},
 * {@code fhirEquivalence}) while keeping the local item reference as a
 * semantic UUID.
 *
 * <p>MVP design decisions (per review conditions):
 * <ul>
 *   <li>Uses semantic reference ({@code fhirPath} String), not FhirElement FK</li>
 *   <li>One-to-many mappings use {@code mappingVariable} for component naming</li>
 * </ul>
 */
@Entity
@Table(
    name = "fhir_element_mapping",
    indexes = {
        @Index(name = "idx_fem_local_item", columnList = "local_item_id"),
        @Index(name = "idx_fem_fhir_path", columnList = "fhir_path"),
        @Index(name = "idx_fem_resource_type", columnList = "fhir_resource_type"),
        @Index(name = "idx_fem_local_fhir", columnList = "local_item_id, fhir_path")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class FhirElementMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Reference to the local {@code AdministeredItem} being mapped.
     */
    @Column(name = "local_item_id", nullable = false)
    private UUID localItemId;

    /**
     * FHIR Element path, e.g. {@code Patient.name.given}.
     * Semantic reference — MVP does not use FhirElement FK.
     */
    @Column(name = "fhir_path", nullable = false, length = 500)
    private String fhirPath;

    /**
     * FHIR Resource type, e.g. {@code Patient}, {@code Observation}.
     * Used for filtering and ConceptMap group organisation.
     */
    @Column(name = "fhir_resource_type", nullable = false, length = 50)
    private String fhirResourceType;

    /**
     * FHIR R4 ConceptMap equivalence code.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "fhir_equivalence", nullable = false, length = 20)
    private FhirMappingEquivalence fhirEquivalence;

    /**
     * Cardinality type of this mapping.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_type", nullable = false, length = 16)
    private FhirMappingType mappingType;

    /**
     * Mapping variable name for one-to-many mappings.
     * E.g. {@code family}, {@code given} when a local "name" field
     * maps to both {@code Patient.name.family} and {@code Patient.name.given}.
     * Null for one-to-one mappings.
     */
    @Column(name = "mapping_variable", length = 100)
    private String mappingVariable;

    /**
     * Human-readable description of this mapping.
     */
    @Column(name = "description", length = 2000)
    private String description;

    /**
     * User ID of the person who created this mapping.
     */
    @Column(name = "created_by")
    private UUID createdBy;

    /**
     * User ID of the person who last updated this mapping.
     */
    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Timestamp when this mapping was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last modification.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking version for concurrent update control.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // ── Constructors ──────────────────────────────────────────────────────

    /**
     * Creates a new one-to-one FHIR element mapping.
     *
     * @param localItemId       the local AdministeredItem UUID
     * @param fhirPath          the FHIR Element path
     * @param fhirResourceType  the FHIR Resource type
     * @param fhirEquivalence   the FHIR equivalence code
     */
    public FhirElementMapping(UUID localItemId, String fhirPath,
                               String fhirResourceType,
                               FhirMappingEquivalence fhirEquivalence) {
        this(localItemId, fhirPath, fhirResourceType, fhirEquivalence,
             FhirMappingType.ONE_TO_ONE, null, null);
    }

    /**
     * Creates a new FHIR element mapping with full details.
     *
     * @param localItemId       the local AdministeredItem UUID
     * @param fhirPath          the FHIR Element path
     * @param fhirResourceType  the FHIR Resource type
     * @param fhirEquivalence   the FHIR equivalence code
     * @param mappingType       the cardinality type
     * @param mappingVariable   the variable name for one-to-many (nullable)
     * @param description       human-readable description (nullable)
     */
    public FhirElementMapping(UUID localItemId, String fhirPath,
                               String fhirResourceType,
                               FhirMappingEquivalence fhirEquivalence,
                               FhirMappingType mappingType,
                               String mappingVariable,
                               String description) {
        this.localItemId = Objects.requireNonNull(localItemId,
                "localItemId must not be null");
        this.fhirPath = requireNonBlank(fhirPath, "fhirPath");
        this.fhirResourceType = requireNonBlank(fhirResourceType, "fhirResourceType");
        this.fhirEquivalence = Objects.requireNonNull(fhirEquivalence,
                "fhirEquivalence must not be null");
        this.mappingType = Objects.requireNonNull(mappingType,
                "mappingType must not be null");
        this.mappingVariable = mappingVariable;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.version = 0L;
    }

    // ── Business helpers ──────────────────────────────────────────────────

    /**
     * Updates the mutable fields of this mapping.
     *
     * @param fhirPath         new FHIR path (null to keep current)
     * @param fhirEquivalence  new equivalence (null to keep current)
     * @param mappingType      new mapping type (null to keep current)
     * @param mappingVariable  new variable (null to keep current)
     * @param description      new description (null to keep current)
     * @param updatedBy        user making the update
     */
    public void update(String fhirPath, FhirMappingEquivalence fhirEquivalence,
                       FhirMappingType mappingType, String mappingVariable,
                       String description, UUID updatedBy) {
        if (fhirPath != null && !fhirPath.isBlank()) {
            this.fhirPath = fhirPath;
        }
        if (fhirEquivalence != null) {
            this.fhirEquivalence = fhirEquivalence;
        }
        if (mappingType != null) {
            this.mappingType = mappingType;
        }
        if (mappingVariable != null) {
            this.mappingVariable = mappingVariable.isBlank() ? null : mappingVariable;
        }
        if (description != null) {
            this.description = description.isBlank() ? null : description;
        }
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Returns true if this is a one-to-many mapping.
     */
    public boolean isOneToMany() {
        return mappingType == FhirMappingType.ONE_TO_MANY;
    }

    /**
     * Returns the display name for this mapping, combining variable and path.
     */
    public String getDisplayName() {
        if (mappingVariable != null && !mappingVariable.isBlank()) {
            return mappingVariable + " → " + fhirPath;
        }
        return fhirPath;
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FhirElementMapping that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "FhirElementMapping{" +
                "id=" + id +
                ", localItemId=" + localItemId +
                ", fhirPath='" + fhirPath + '\'' +
                ", fhirResourceType='" + fhirResourceType + '\'' +
                ", mappingType=" + mappingType +
                '}';
    }
}
