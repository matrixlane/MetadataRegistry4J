package io.github.matrixlane.metadataregistry.commonfacilities.identification;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records version lifecycle events for a ScopedIdentifier.
 *
 * <p>MVP scope: stores metadata only (who, when, version, status, summary).
 * Full snapshot / diff deferred to V1.
 */
@Entity
@Table(name = "identifier_version_history")
@Getter
@Setter
@NoArgsConstructor
public class IdentifierVersionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "scoped_identifier_id", nullable = false)
    private ScopedIdentifier scopedIdentifier;

    @Column(name = "version", length = 32)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "version_status", length = 16)
    private VersionStatus versionStatus;

    @Column(name = "operator", length = 128)
    private String operator;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "change_summary", length = 1024)
    private String changeSummary;

    public IdentifierVersionHistory(ScopedIdentifier scopedIdentifier,
                                    String version,
                                    VersionStatus versionStatus,
                                    String operator,
                                    String changeSummary) {
        this.scopedIdentifier = scopedIdentifier;
        this.version = version;
        this.versionStatus = versionStatus;
        this.operator = operator;
        this.timestamp = LocalDateTime.now();
        this.changeSummary = changeSummary;
    }
}

