package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingSchemeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a mapping scheme — a versioned container for item mappings.
 *
 * <p>Each scheme has a name, source/target URIs, description, semantic version
 * number, and a lifecycle status. Versions are tracked via a parent reference
 * that links a new draft to its published predecessor.
 *
 * <p>Per ISO/IEC 11179-3:2023 Clause 11 — Item Mapping.
 *
 * <h3>Version Lifecycle</h3>
 * <pre>
 * Initial version: 0.1.0 (DRAFT)
 * On publish:      version stays same, status becomes PUBLISHED
 * On new version:  patch incremented, status becomes DRAFT, parentId = published version
 * On archive:      status becomes ARCHIVED (read-only)
 * </pre>
 *
 * <h3>Unique Constraints</h3>
 * <ul>
 *   <li>{@code (parent_id, version)} — ensures no duplicate versions for the same lineage</li>
 * </ul>
 */
@Entity
@Table(name = "mapping_scheme", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"parent_id", "version"},
                name = "uq_mapping_scheme_parent_version")
})
@Getter
@Setter
@NoArgsConstructor
public class MappingScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Human-readable name of the mapping scheme.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * URI of the source standard (e.g. a FHIR CodeSystem URL).
     */
    @Column(name = "source_uri", nullable = false, length = 2048)
    private String sourceUri;

    /**
     * URI of the target standard (e.g. a FHIR ValueSet URL).
     */
    @Column(name = "target_uri", nullable = false, length = 2048)
    private String targetUri;

    /**
     * Optional human-readable description of the scheme's purpose.
     */
    @Column(name = "description", length = 4000)
    private String description;

    /**
     * Semantic version number (e.g. {@code 0.1.0}, {@code 0.1.1}).
     * Auto-generated: initial is {@code 0.1.0}; creating a new version
     * increments the patch component.
     */
    @Column(name = "version", nullable = false, length = 32)
    private String version;

    /**
     * Current lifecycle status of this scheme version.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MappingSchemeStatus status;

    /**
     * Reference to the parent scheme version this one was derived from.
     * {@code null} for the initial version of a scheme.
     *
     * <p>Together with {@link #version} forms a unique constraint
     * {@code uq_mapping_scheme_parent_version}.
     */
    @Column(name = "parent_id")
    private UUID parentId;

    /**
     * Timestamp when this scheme version was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the last modification.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Creates a new mapping scheme with initial version {@code 0.1.0}
     * and status {@link MappingSchemeStatus#DRAFT}.
     *
     * @param name       the scheme name (required)
     * @param sourceUri  the source URI (required)
     * @param targetUri  the target URI (required)
     * @param description optional description
     */
    public MappingScheme(String name, String sourceUri, String targetUri, String description) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.sourceUri = Objects.requireNonNull(sourceUri, "sourceUri must not be null");
        this.targetUri = Objects.requireNonNull(targetUri, "targetUri must not be null");
        this.description = description;
        this.version = "0.1.0";
        this.status = MappingSchemeStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Returns the next patch version based on the current version string.
     * E.g. {@code 0.1.0} → {@code 0.1.1}, {@code 1.2.3} → {@code 1.2.4}.
     *
     * @return the incremented version string
     */
    public String nextPatchVersion() {
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        return major + "." + minor + "." + (patch + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MappingScheme that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "MappingScheme{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", status=" + status +
                '}';
    }
}

