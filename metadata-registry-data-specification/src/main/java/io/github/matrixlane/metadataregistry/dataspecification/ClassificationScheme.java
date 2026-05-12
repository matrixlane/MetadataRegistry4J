package io.github.matrixlane.metadataregistry.dataspecification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
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
 * Classification Scheme (ISO 11179-3 Clause 10).
 * <p>
 * A Classification Scheme is a collection of categories used to classify administered items.
 * It consists of a set of ClassificationNodes organized in a hierarchical structure.
 * </p>
 */
@Entity
@Table(name = "classification_scheme")
@Getter
@Setter
@NoArgsConstructor
public class ClassificationScheme {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 255)
    private String schemeName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 50)
    private String version;

    @Column(length = 20)
    private String status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ClassificationScheme(String schemeName) {
        this.schemeName = schemeName;
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
