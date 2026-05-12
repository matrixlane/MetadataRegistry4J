package io.github.matrixlane.metadataregistry.dataspecification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Classification Node (ISO 11179-3 Clause 10).
 * <p>
 * A node within a Classification Scheme. Nodes form a hierarchical tree structure
 * via the parent reference. Root nodes have a null parent.
 * </p>
 */
@Entity
@Table(name = "classification_node")
@Getter
@Setter
@NoArgsConstructor
public class ClassificationNode {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 255)
    private String nodeCode;

    @Column(nullable = false, length = 255)
    private String nodeName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String definition;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "parent_id")
    private UUID parentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", nullable = false)
    private ClassificationScheme classificationScheme;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ClassificationNode(String nodeCode, String nodeName, ClassificationScheme scheme) {
        this.nodeCode = nodeCode;
        this.nodeName = nodeName;
        this.classificationScheme = scheme;
    }

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
