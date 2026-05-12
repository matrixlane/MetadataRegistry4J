package io.github.matrixlane.metadataregistry.datasetregistration.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 互联互通数据元 (Interoperability Data Element) — 原始字段副本.
 *
 * <p>Stores the raw fields from an interoperability standard dataset template,
 * preserving the original values for traceability. Additionally carries the
 * ISO 11179 mapping target references for the alignment engine (WI-013-2).
 *
 * <p>The combination {@code (data_set_id, de_identifier)} is unique per the
 * trio review decision (condition #2).
 *
 * <p>MVP tech debt: Flat table stored directly without ISO 11179 Item hierarchy.
 * See WI-013-1 trio review condition #5.
 */
@Entity
@Table(
        name = "interop_data_element",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_interop_de_dataset_identifier",
                columnNames = {"data_set_id", "de_identifier"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class InteropDataElement {

    /** Mapping status of this data element to ISO 11179 concepts. */
    public enum MappingStatus {
        /** Not yet mapped to any ISO 11179 Item. */
        UNMAPPED,
        /** Successfully mapped to a single ISO 11179 Item. */
        MAPPED,
        /** Multiple candidate mappings found; requires manual resolution. */
        CONFLICT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Dataset reference ──────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_set_id", nullable = false)
    private InteropDataSet dataSet;

    // ── Interoperability standard fields (原始字段副本) ─────────────

    /**
     * Data element identifier (数据元标识符), e.g. "DE01.01.001.00".
     * Part of the composite unique key with {@code dataSet}.
     */
    @Column(name = "de_identifier", length = 100, nullable = false)
    private String deIdentifier;

    /**
     * Data element name (数据元名称).
     */
    @Column(name = "de_name", length = 500)
    private String deName;

    /**
     * Definition (定义) — the semantic meaning of this data element.
     */
    @Column(name = "de_definition", columnDefinition = "TEXT")
    private String deDefinition;

    /**
     * Data type (数据类型), e.g. "字符型", "数值型", "日期型".
     */
    @Column(name = "data_type", length = 100)
    private String dataType;

    /**
     * Representation format (表示格式), e.g. "N..20", "AN..100", "D8".
     * Combined with {@link #dataType} to form the value domain representation.
     */
    @Column(name = "representation_format", length = 100)
    private String representationFormat;

    /**
     * Permissible values / value domain (允许值/值域).
     * Stored as raw text (JSON or plain text) for MVP.
     */
    @Column(name = "permissible_values", columnDefinition = "TEXT")
    private String permissibleValues;

    /**
     * Whether this data element is required (必填标识).
     */
    @Column(name = "required")
    private Boolean required;

    // ── ISO 11179 mapping fields (预留) ────────────────────────────

    /**
     * Reference to the local ISO 11179 Item that this data element maps to.
     * Populated by the alignment engine (WI-013-2).
     */
    @Column(name = "mapped_item_id")
    private UUID mappedItemId;

    /**
     * Current mapping status.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mapping_status", length = 20)
    private MappingStatus mappingStatus = MappingStatus.UNMAPPED;

    // ── Audit timestamps ───────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
