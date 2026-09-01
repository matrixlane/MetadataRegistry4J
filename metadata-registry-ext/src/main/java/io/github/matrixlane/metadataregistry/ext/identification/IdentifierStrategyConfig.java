package io.github.matrixlane.metadataregistry.ext.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
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
import java.util.UUID;

/**
 * Configuration entity for identifier generation strategies.
 *
 * <p>Stores strategy configuration in the database, supporting versioning and
 * auditing. Each strategy is bound to a single Namespace, or marked as the
 * global default fallback.
 *
 * <p>Resolution order (highest to lowest):
 * <ol>
 *   <li>Namespace-specific strategy (most specific)</li>
 *   <li>Global default strategy (fallback)</li>
 * </ol>
 *
 * <p>Strategy changes do NOT retroactively affect identifiers already generated
 * under a previous strategy version. Historical versions remain queryable for
 * auditability (lookup by {@code namespace_id, created_at}).
 */
@Entity
@Table(name = "identifier_strategy_config")
@Getter
@Setter
@NoArgsConstructor
public class IdentifierStrategyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /**
     * The type of generation strategy.
     * Values: UUID, URN, SEQUENCE, TEMPLATE
     */
    @Column(name = "strategy_type", nullable = false, length = 32)
    private String strategyType;

    /**
     * The namespace this strategy is bound to.
     * NULL means this is the global default strategy.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "namespace_id")
    private Namespace namespace;

    /**
     * Strategy-specific JSON configuration.
     * Examples:
     * <ul>
     *   <li>UUID: {@code {}}</li>
     *   <li>URN: {@code {"nsPrefix":"sh1y","includeDate":false}}</li>
     *   <li>Sequence: {@code {"prefix":"DE","start":1,"pad":6}}</li>
     *   <li>Template: {@code {"template":"{prefix}-{yyyy}{MM}-{seq:4}","prefix":"CO"}}</li>
     * </ul>
     */
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    /**
     * Version number for immutability tracking.
     * When a strategy is modified, a new version is created with incremented version number.
     * Historical versions remain for auditability.
     */
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    /**
     * Strategy status: DRAFT, PUBLISHED, DISABLED
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StrategyStatus status = StrategyStatus.DRAFT;

    /**
     * TRUE if this is the global default fallback strategy.
     * There should be exactly one global default.
     */
    @Column(name = "is_global_default", nullable = false)
    private Boolean isGlobalDefault = false;

    /**
     * When this strategy version became/becomes effective.
     */
    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    /**
     * When this strategy version ceased/ceases to be effective.
     * NULL means currently effective.
     */
    @Column(name = "effective_until")
    private LocalDateTime effectiveUntil;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_by", length = 128)
    private String modifiedBy;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    // --- Constructor helpers ---

    public IdentifierStrategyConfig(String strategyType) {
        this.strategyType = strategyType;
        this.effectiveFrom = LocalDateTime.now();
    }

    /**
     * Marks this strategy version as published and closes the effective window
     * of any previous version.
     */
    public void publish() {
        this.status = StrategyStatus.PUBLISHED;
        this.effectiveFrom = LocalDateTime.now();
    }

    /**
     * Disables this strategy configuration.
     */
    public void disable() {
        this.status = StrategyStatus.DISABLED;
        this.effectiveUntil = LocalDateTime.now();
    }

    /**
     * Check if this configuration is currently effective.
     */
    public boolean isEffective() {
        LocalDateTime now = LocalDateTime.now();
        return status == StrategyStatus.PUBLISHED
                && (effectiveFrom == null || !effectiveFrom.isAfter(now))
                && (effectiveUntil == null || effectiveUntil.isAfter(now));
    }

    public enum StrategyStatus {
        DRAFT,
        PUBLISHED,
        DISABLED
    }
}

