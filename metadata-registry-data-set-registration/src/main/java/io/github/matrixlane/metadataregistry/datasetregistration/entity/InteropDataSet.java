package io.github.matrixlane.metadataregistry.datasetregistration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 互联互通数据集 (Interoperability Data Set).
 *
 * <p>Represents a dataset template imported from the national health information
 * interoperability standard (互联互通标准). Each dataset (e.g. HDSB01.01)
 * contains multiple data elements that define the standard's data model.
 *
 * <p>MVP tech debt: Flat table stored directly without ISO 11179 Item hierarchy.
 * See WI-013-1 trio review condition #5.
 */
@Entity
@Table(name = "interop_data_set")
@Getter
@Setter
@NoArgsConstructor
public class InteropDataSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Dataset code, e.g. "HDSB01.01".
     */
    @Column(name = "code", length = 100)
    private String code;

    /**
     * Dataset name, e.g. "卫生服务数据集".
     */
    @Column(name = "name", length = 500)
    private String name;

    /**
     * Version identifier of the dataset template.
     */
    @Column(name = "version", length = 50)
    private String version;

    /**
     * Original source file name from which this dataset was imported.
     */
    @Column(name = "source_file_name", length = 500)
    private String sourceFileName;

    /**
     * Timestamp when this dataset was first imported.
     */
    @Column(name = "imported_at")
    private LocalDateTime importedAt;

    /**
     * Timestamp of the last update.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Number of data elements in this dataset (denormalized for fast queries).
     */
    @Column(name = "element_count")
    private Integer elementCount;

    @PrePersist
    protected void onCreate() {
        importedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
