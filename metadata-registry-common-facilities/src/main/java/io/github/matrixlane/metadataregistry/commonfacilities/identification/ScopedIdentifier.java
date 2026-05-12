package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ISO/IEC 11179-3:2023, Clause 7 — Scoped Identifier
 *
 * <p>Associates a business identifier with a namespace and an identified item.
 * The {@code id} field is the technical primary key; {@code identifier} is the
 * business identifier whose value is immutable once assigned.
 */
@StandardReference("ISO/IEC 11179-3:2023, Clause 7")
@Entity
@Table(name = "scoped_identifier",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scoped_identifier_ns_identifier",
                columnNames = {"namespace_id", "identifier"}))
@Getter
@Setter
@NoArgsConstructor
public class ScopedIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Business identifier — immutable once assigned.
     */
    @Column(name = "identifier", nullable = false, length = 255)
    private String identifier;

    @Column(name = "version", length = 32)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_status", length = 16)
    private VersionStatus versionStatus = VersionStatus.DRAFT;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deprecated_at")
    private LocalDateTime deprecatedAt;

    @Column(name = "obsolete_at")
    private LocalDateTime obsoleteAt;

    @ManyToOne
    @JoinColumn(name = "namespace_id")
    private Namespace namespace;

    @ManyToOne
    @JoinColumn(name = "identified_item_id")
    private IdentifiedItem identifiedItem;
}
