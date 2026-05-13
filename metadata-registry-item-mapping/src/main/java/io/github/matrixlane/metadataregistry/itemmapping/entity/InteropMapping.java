package io.github.matrixlane.metadataregistry.itemmapping.entity;

import io.github.matrixlane.metadataregistry.itemmapping.enums.InteropMappingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores a match result between an interoperability data element and a local Item.
 *
 * <p>Each record represents one mapping suggestion produced by the matching
 * engine (WI-013-2a). The confidence score, strategy, and status are preserved
 * for audit and calibration purposes.
 *
 * <p>Schema aligns with the {@code interop_mapping} table defined in WI-013-2a.
 */
@Entity
@Table(name = "interop_mapping")
@Getter
@Setter
@NoArgsConstructor
public class InteropMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Reference to the interoperability data element being mapped.
     */
    @Column(name = "interop_data_element_id", nullable = false)
    private UUID interopDataElementId;

    /**
     * Reference to the local ISO 11179 Item that the interop element maps to.
     */
    @Column(name = "local_item_id", nullable = false)
    private UUID localItemId;

    /**
     * Confidence score produced by the matching engine, range [0.00, 1.00].
     */
    @Column(name = "confidence_score", precision = 3, scale = 2, nullable = false)
    private BigDecimal confidenceScore;

    /**
     * The matching strategy that produced this result (e.g. EXACT_IDENTIFIER, NAME_SIMILARITY).
     */
    @Column(name = "strategy", length = 50, nullable = false)
    private String strategy;

    /**
     * Current lifecycle status of this mapping.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InteropMappingStatus status = InteropMappingStatus.PENDING;

    /**
     * Human-readable reason / score breakdown from the matching engine.
     */
    @Column(name = "reason", length = 1000)
    private String reason;

    /**
     * User ID of the person who confirmed or rejected this mapping.
     */
    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    /**
     * Timestamp when the mapping was confirmed or rejected.
     */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * Timestamp when this record was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Constructors ─────────────────────────────────────────────────

    /**
     * Creates a new InteropMapping with the current timestamp.
     */
    public InteropMapping(UUID interopDataElementId,
                          UUID localItemId,
                          BigDecimal confidenceScore,
                          String strategy,
                          InteropMappingStatus status,
                          String reason) {
        this.interopDataElementId = Objects.requireNonNull(interopDataElementId,
                "interopDataElementId must not be null");
        this.localItemId = Objects.requireNonNull(localItemId,
                "localItemId must not be null");
        this.confidenceScore = Objects.requireNonNull(confidenceScore,
                "confidenceScore must not be null");
        this.strategy = Objects.requireNonNull(strategy,
                "strategy must not be null");
        this.status = status != null ? status : InteropMappingStatus.PENDING;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Creates a new InteropMapping with default PENDING status.
     */
    public InteropMapping(UUID interopDataElementId,
                          UUID localItemId,
                          BigDecimal confidenceScore,
                          String strategy,
                          String reason) {
        this(interopDataElementId, localItemId, confidenceScore, strategy,
                InteropMappingStatus.PENDING, reason);
    }

    // ── Lifecycle helpers ────────────────────────────────────────────

    /**
     * Marks this mapping as confirmed by the given user.
     */
    public void confirm(UUID userId) {
        this.status = InteropMappingStatus.CONFIRMED;
        this.confirmedBy = userId;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Marks this mapping as rejected by the given user.
     */
    public void reject(UUID userId) {
        this.status = InteropMappingStatus.REJECTED;
        this.confirmedBy = userId;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Auto-matches this mapping (high confidence).
     */
    public void autoMatch() {
        this.status = InteropMappingStatus.AUTO_MATCHED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InteropMapping that)) return false;
        if (id == null || that.id == null) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? 0 : id.hashCode();
    }

    @Override
    public String toString() {
        return "InteropMapping{" +
                "id=" + id +
                ", interopDataElementId=" + interopDataElementId +
                ", localItemId=" + localItemId +
                ", confidenceScore=" + confidenceScore +
                ", strategy='" + strategy + '\'' +
                ", status=" + status +
                '}';
    }
}
