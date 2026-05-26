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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WI-028-B: Configuration entity for identifier generation strategies.
 *
 * <p>Stores strategy configuration in the database, supporting versioning, auditing,
 * and (Namespace, Type) routing. The combination of namespace and identifierType
 * determines the routing priority.
 *
 * <p>Routing priority (highest to lowest):
 * <ol>
 *   <li>Namespace + Type specific strategy (most specific)</li>
 *   <li>Namespace default strategy (any type fallback)</li>
 *   <li>Global default strategy (fallback)</li>
 * </ol>
 *
 * <p>Strategy changes do NOT affect existing ScopedIdentifiers because each
 * ScopedIdentifier stores a snapshot of the effective strategy config ID.
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
     * WI-028-B: The identifier type this strategy applies to.
     * Use INTERNAL for the default/generic type.
     * NULL means this config applies to all types (namespace default fallback).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 32)
    private IdentifierType identifierType = IdentifierType.INTERNAL;

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
     * Historical versions remain for existing ScopedIdentifier references.
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
     * There should be exactly one global default per identifierType.
     */
    @Column(name = "is_global_default", nullable = false)
    private Boolean isGlobalDefault = false;

    /**
     * Sequence scope for SEQUENCE strategy.
     * PER_NAMESPACE: one sequence counter per namespace
     * PER_NAMESPACE_TYPE: one sequence counter per (namespace, identifierType)
     */
    @Column(name = "sequence_scope", nullable = false, length = 16)
    private String sequenceScope = "PER_NAMESPACE_TYPE";

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

    public IdentifierStrategyConfig(String strategyType, IdentifierType identifierType) {
        this.strategyType = strategyType;
        this.identifierType = identifierType != null ? identifierType : IdentifierType.INTERNAL;
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
