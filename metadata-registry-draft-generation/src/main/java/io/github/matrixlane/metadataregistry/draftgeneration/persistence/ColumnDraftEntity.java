package io.github.matrixlane.metadataregistry.draftgeneration.persistence;

import io.github.matrixlane.metadataregistry.draftgeneration.model.MappingConfidence;
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

import java.util.UUID;

/**
 * 列草稿实体。
 */
@Entity
@Table(name = "metadata_column_draft")
@Getter
@Setter
@NoArgsConstructor
public class ColumnDraftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "metadata_draft_id", nullable = false)
    private MetadataDraftEntity metadataDraft;

    @Column(nullable = false, length = 255)
    private String sourceColumnName;

    @Column(length = 255)
    private String suggestedColumnName;

    @Column(nullable = false, length = 100)
    private String sourceDataType;

    @Column(length = 100)
    private String mappedDataType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MappingConfidence mappingConfidence;

    @Column(length = 500)
    private String mappingReason;

    @Column(length = 500)
    private String remarks;

    private Integer columnSize;

    private Integer decimalDigits;

    private Boolean nullable;

    @Column(length = 500)
    private String defaultValue;

    private Boolean autoIncrement;

    @Column(length = 255)
    private String namingRuleApplied;

    public ColumnDraftEntity(String sourceColumnName, String suggestedColumnName,
                              String sourceDataType, String mappedDataType,
                              MappingConfidence mappingConfidence) {
        this.sourceColumnName = sourceColumnName;
        this.suggestedColumnName = suggestedColumnName;
        this.sourceDataType = sourceDataType;
        this.mappedDataType = mappedDataType;
        this.mappingConfidence = mappingConfidence;
    }
}

