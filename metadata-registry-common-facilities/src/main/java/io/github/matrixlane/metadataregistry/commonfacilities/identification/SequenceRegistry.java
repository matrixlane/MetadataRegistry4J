package io.github.matrixlane.metadataregistry.commonfacilities.identification;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * WI-028-B: Tracks sequence state for SequenceGenerationStrategy.
 *
 * <p>Each (namespace, identifierType) combination has its own sequence counter.
 * The counter is incremented atomically when a new identifier is generated.
 *
 * <p>Thread-safety: Uses database-level locking (SELECT FOR UPDATE) to ensure
 * atomic increment in concurrent scenarios.
 */
@Entity
@Table(name = "identifier_sequence_registry",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seq_registry_namespace_type",
                columnNames = {"namespace_id", "identifier_type"}))
@Getter
@Setter
@NoArgsConstructor
public class SequenceRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * The namespace this sequence belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "namespace_id", nullable = false)
    private Namespace namespace;

    /**
     * The identifier type this sequence is for.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 32)
    private IdentifierType identifierType = IdentifierType.INTERNAL;

    /**
     * The current sequence value (the last allocated number).
     */
    @Column(name = "current_value", nullable = false)
    private Long currentValue = 0L;

    /**
     * How much to increment by on each allocation.
     */
    @Column(name = "increment_by", nullable = false)
    private Integer incrementBy = 1;

    /**
     * The starting value for this sequence.
     */
    @Column(name = "start_value", nullable = false)
    private Long startValue = 1L;

    /**
     * Zero-padding length (e.g., pad=6 → "000001").
     */
    @Column(name = "pad_length", nullable = false)
    private Integer padLength = 6;

    /**
     * Optional prefix prepended to the sequence number.
     * Example: "DE" with pad=6 → "DE000001"
     */
    @Column(name = "prefix", length = 32)
    private String prefix;

    /**
     * When the last allocation occurred.
     */
    @Column(name = "last_allocated_at", nullable = false)
    private LocalDateTime lastAllocatedAt;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_by", length = 128)
    private String modifiedBy;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    public SequenceRegistry(Namespace namespace, IdentifierType identifierType) {
        this.namespace = namespace;
        this.identifierType = identifierType != null ? identifierType : IdentifierType.INTERNAL;
        this.currentValue = 0L;
        this.incrementBy = 1;
        this.startValue = 1L;
        this.padLength = 6;
        this.lastAllocatedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Returns the next sequence value without incrementing.
     */
    public long peekNext() {
        return currentValue + incrementBy;
    }

    /**
     * Checks if incrementing would exceed pad length limit.
     * @param maxValue the maximum value representable with current pad length
     */
    public boolean wouldOverflow(long maxValue) {
        return peekNext() > maxValue;
    }

    /**
     * Maximum value representable with current pad length.
     * e.g., padLength=4 → maxValue=9999
     */
    public long getMaxValue() {
        long max = 0;
        for (int i = 0; i < padLength; i++) {
            max = max * 10 + 9;
        }
        return max;
    }

    /**
     * Formats the next value with zero-padding and optional prefix.
     * Example: prefix="DE", pad=6, next=42 → "DE000042"
     */
    public String formatNext() {
        long next = peekNext();
        String padded = String.format("%0" + padLength + "d", next);
        return (prefix != null ? prefix : "") + padded;
    }
}
