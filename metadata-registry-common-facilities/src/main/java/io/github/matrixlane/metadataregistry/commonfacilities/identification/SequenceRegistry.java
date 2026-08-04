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
 * Tracks sequence state for SequenceGenerationStrategy.
 *
 * <p>Each (namespace, scope key) pair has its own counter, incremented
 * atomically when a new identifier is generated. The scope key is composed of
 * the values of the segments referenced by a SEQUENCE segment's scopeRefs;
 * the reserved key {@code __GLOBAL__} expresses namespace-level counting
 * (the pre-scope behavior). A non-empty sentinel is used instead of NULL or
 * empty string so the unique constraint behaves identically on SQLite,
 * SQL Server and Dameng (Oracle-mode treats '' as NULL).
 *
 * <p>Thread-safety: Uses database-level locking (SELECT FOR UPDATE) to ensure
 * atomic increment in concurrent scenarios.
 */
@Entity
@Table(name = "identifier_sequence_registry",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seq_registry_ns_scope",
                columnNames = {"namespace_id", "scope_key"}))
@Getter
@Setter
@NoArgsConstructor
public class SequenceRegistry {

    /** Reserved scope key for namespace-level global counting. */
    public static final String GLOBAL_SCOPE_KEY = "__GLOBAL__";

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
     * Scope key composed of referenced segment values (e.g. "02.01" for
     * major/minor category scoped counting). {@value #GLOBAL_SCOPE_KEY} means
     * namespace-level global counting.
     */
    @Column(name = "scope_key", nullable = false, length = 512,
            columnDefinition = "VARCHAR(512) DEFAULT '__GLOBAL__'")
    private String scopeKey = GLOBAL_SCOPE_KEY;

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

    public SequenceRegistry(Namespace namespace) {
        this(namespace, GLOBAL_SCOPE_KEY);
    }

    public SequenceRegistry(Namespace namespace, String scopeKey) {
        this.namespace = namespace;
        this.scopeKey = scopeKey != null && !scopeKey.isBlank() ? scopeKey : GLOBAL_SCOPE_KEY;
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

